package com.enonic.app.booster;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enonic.app.booster.concurrent.Collapser;
import com.enonic.app.booster.servlet.CachingResponseWrapper;
import com.enonic.app.booster.servlet.RequestAttributes;
import com.enonic.app.booster.servlet.RequestURL;
import com.enonic.app.booster.servlet.RequestUtils;
import com.enonic.app.booster.servlet.ResponseFreshness;
import com.enonic.app.booster.storage.NodeCacheStore;
import com.enonic.xp.annotation.Order;
import com.enonic.xp.portal.PortalRequest;
import com.enonic.xp.project.ProjectName;
import com.enonic.xp.trace.Trace;
import com.enonic.xp.trace.Tracer;
import com.enonic.xp.web.filter.OncePerRequestFilter;

import static java.util.Objects.requireNonNullElseGet;

@Component(immediate = true, service = Filter.class, property = {"connector=xp"}, configurationPid = "com.enonic.app.booster")
@Order(-190)
@WebFilter("/site/*")
public class BoosterRequestFilter
    extends OncePerRequestFilter
{
    private static final Logger LOG = LoggerFactory.getLogger( BoosterRequestFilter.class );

    private final NodeCacheStore cacheStore;

    private volatile BoosterConfigParsed config;

    private final Collapser<CacheItem> requestCollapser = new Collapser<>();

    private final BoosterLicenseService licenseService;

    @Activate
    public BoosterRequestFilter( @Reference final NodeCacheStore cacheStore, @Reference final BoosterLicenseService licenseService )
    {
        this.cacheStore = cacheStore;
        this.licenseService = licenseService;
    }

    @Activate
    @Modified
    public void activate( final BoosterConfig config )
    {
        this.config = BoosterConfigParsed.parse( config );
    }

    @Override
    protected void doHandle( final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain )
        throws Exception
    {
        final Preconditions preconditions =
            new Preconditions( new Preconditions.LicensePrecondition( licenseService::isValidLicense )::check );
        final Preconditions.Result preconditionResult = preconditions.check( request );
        if ( preconditionResult.bypass() )
        {
            if ( preconditionResult.detail() != null )
            {
                writeCacheStatusHeader( response, BoosterCacheStatus.bypass( preconditionResult.detail() ) );
            }
            chain.doFilter( request, response );
            return;
        }

        // Full URL is used, so scheme, domain and port are included.
        // Query String is also included with all parameters (there is an option to exclude some of them in config)
        final RequestURL requestUrl = RequestUtils.buildRequestURL( request, config.excludeQueryParams() );

        final String fullUrl = requestUrl.url();
        final String cacheKey = cacheStore.generateCacheKey( fullUrl );

        LOG.debug( "Normalized URL of request {} with key {}", fullUrl, cacheKey );

        final Trace trace = Tracer.newTrace( "booster.fromCache" );
        if ( trace != null )
        {
            trace.put( "cacheKey", cacheKey );
            trace.put( "url", fullUrl );
        }
        final CacheStatusCode cacheStatusCode = Tracer.traceEx( trace, () -> {
            final CacheStatusCode statusCode = tryWriteFromCache( request, response, cacheKey );
            traceStatus( trace, statusCode.name() );
            return statusCode;
        } );

        if ( cacheStatusCode == CacheStatusCode.HIT )
        {
            return;
        }

        final boolean stale = cacheStatusCode == CacheStatusCode.STALE;

        // response is very likely cacheable if stored value exists, we can collapse requests (wait for one request to do rendering)
        final Collapser.Latch<CacheItem> latch = stale ? requestCollapser.latch( cacheKey ) : null;

        final CacheItem[] cacheHolder = new CacheItem[1];
        try
        {
            if ( latch != null )
            {
                cacheHolder[0] = latch.get();
                if ( cacheHolder[0] != null )
                {
                    LOG.debug( "Cached response generated by another request. Use collapsed request result {}", cacheKey );

                    new CachedResponseWriter( request, res -> writeHeaders( res, BoosterCacheStatus.collapsed() ) ).write( response,
                                                                                                                           cacheHolder[0] );
                    return;
                }
            }

            LOG.debug( "Processing request with cache key {}", cacheKey );

            final StoreConditions storeConditions = new StoreConditions( new StoreConditions.PortalRequestConditions()::check,
                                                                         new StoreConditions.SiteConfigConditions(
                                                                             config.excludeQueryParams() )::check,
                                                                         new StoreConditions.ContentTypePreconditions(
                                                                             config.cacheMimeTypes() )::check );

            final CachingResponseWrapper cachingResponse = new CachingResponseWrapper( request, response, storeConditions::check,
                                                                                       res -> writeHeaders( res, stale
                                                                                           ? BoosterCacheStatus.stale()
                                                                                           : BoosterCacheStatus.miss() ) );
            try (cachingResponse)
            {
                chain.doFilter( request, cachingResponse );
            }

            LOG.debug( "Response received for cache key {}. Can be stored: {}", cacheKey, cachingResponse.isStore() );

            if ( cachingResponse.isStore() )
            {
                Tracer.trace( "booster.updateCache", () -> {

                    final CacheMeta cacheMeta = createCacheMeta( request, requestUrl );
                    final ResponseFreshness freshness = cachingResponse.getFreshness();

                    final BoosterSiteConfig config = BoosterSiteConfig.getSiteConfig( RequestAttributes.getPortalRequest( request ) );

                    final Integer fallbackTTL =
                        config.componentTTL != null && RequestUtils.isComponentRequest( request ) ? config.componentTTL : config.defaultTTL;

                    cacheHolder[0] =
                        new CacheItem( cachingResponse.getStatus(), cachingResponse.getContentType(), cachingResponse.getCachedHeaders(),
                                       freshness.time(), freshness.expiresTime( fallbackTTL ), freshness.age(), null,
                                       cachingResponse.getSize(), cachingResponse.getEtag(), cachingResponse.getCachedGzipBody(),
                                       cachingResponse.getCachedBrBody().orElse( null ) );
                    cacheStore.put( cacheKey, cacheHolder[0], cacheMeta );
                } );
            }
            else if ( stale )
            {
                Tracer.trace( "booster.updateCache", () -> {
                    // Evacuate item from cache immediately if it is no longer cacheable
                    // to prevent needless request collapsing
                    cacheStore.remove( cacheKey );
                } );
            }
        }
        finally

        {
            if ( latch != null )
            {
                latch.unlock( cacheHolder[0] );
            }
        }
    }

    private CacheStatusCode tryWriteFromCache( final HttpServletRequest request, final HttpServletResponse response, final String cacheKey )
        throws IOException
    {
        final CacheItem inCache = cacheStore.get( cacheKey );
        if ( inCache == null )
        {
            LOG.debug( "No cached response found {}", cacheKey );
            return CacheStatusCode.MISS;
        }
        final CacheItem valid = checkStale( inCache );
        if ( valid != null )
        {
            LOG.debug( "Writing directly from cache {}", cacheKey );

            new CachedResponseWriter( request, res -> writeHeaders( res, BoosterCacheStatus.hit() ) ).write( response, valid );
            return CacheStatusCode.HIT;
        }
        else
        {
            LOG.debug( "Cached response is stale {}", cacheKey );
            return CacheStatusCode.STALE;
        }
    }

    private void writeHeaders( final HttpServletResponse response, final BoosterCacheStatus cacheStatus )
    {
        writeCacheStatusHeader( response, cacheStatus );
        writeVaryContentEncodingHeader( response );
        writeOverrideHeaders( response );
    }

    private void writeCacheStatusHeader( final HttpServletResponse response, final BoosterCacheStatus cacheStatus )
    {
        if ( !config.disableCacheStatusHeader() && cacheStatus != null )
        {
            response.setHeader( "Cache-Status", cacheStatus.toString() );
        }
    }

    private void writeVaryContentEncodingHeader( final HttpServletResponse response )
    {
        // We may send compressed and uncompressed response, so we need to Vary on Accept-Encoding
        // Make sure we don't set the header twice - Jetty also can set this header sometimes

        if ( response.getHeaders( "Vary" ).stream().noneMatch( s -> s.toLowerCase( Locale.ROOT ).contains( "accept-encoding" ) ) )
        {
            response.addHeader( "Vary", "Accept-Encoding" );
        }
    }

    private void writeOverrideHeaders( final HttpServletResponse response )
    {
        config.overrideHeaders().forEach( ( name, value ) -> response.setHeader( name.toLowerCase( Locale.ROOT ), value ) );
    }

    private CacheItem checkStale( final CacheItem stored )
    {
        if ( stored.invalidatedTime() != null )
        {
            return null;
        }

        final Instant expireTime =
            requireNonNullElseGet( stored.expireTime(), () -> stored.cachedTime().plus( config.cacheTtlSeconds(), ChronoUnit.SECONDS ) );
        if ( expireTime.isBefore( Instant.now() ) )
        {
            return null;
        }
        else
        {
            return stored;
        }
    }

    private static CacheMeta createCacheMeta( final HttpServletRequest request, RequestURL requestUrl )
    {
        final PortalRequest portalRequest = RequestAttributes.getPortalRequest( request );

        final String project;
        if ( portalRequest.getRepositoryId() != null )
        {
            final ProjectName projectName = ProjectName.from( portalRequest.getRepositoryId() );
            project = projectName != null ? projectName.toString() : null;
        }
        else
        {
            project = null;
        }

        final String siteId = portalRequest.getSite() != null ? portalRequest.getSite().getId().toString() : null;
        final String contentId;
        final String contentPath;
        if ( portalRequest.getContent() != null )
        {
            contentId = portalRequest.getContent().getId().toString();
            contentPath = portalRequest.getContent().getPath().toString();
        }
        else
        {
            contentId = null;
            contentPath = null;
        }
        return new CacheMeta( requestUrl.url(), requestUrl.domain(), requestUrl.path(), project, siteId, contentId, contentPath );
    }

    private static void traceStatus( final Trace trace, final String status )
    {
        if ( trace != null )
        {
            trace.put( "status", status );
        }
    }

    enum CacheStatusCode
    {
        MISS, HIT, STALE
    }
}
