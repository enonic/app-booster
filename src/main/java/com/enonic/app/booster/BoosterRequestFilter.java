package com.enonic.app.booster;

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

import com.enonic.app.booster.io.ByteSupply;
import com.enonic.app.booster.servlet.CachingResponseWrapper;
import com.enonic.app.booster.storage.NodeCacheStore;
import com.enonic.xp.annotation.Order;
import com.enonic.xp.portal.PortalRequest;
import com.enonic.xp.web.filter.OncePerRequestFilter;

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

    private final SiteConfigService siteConfigService;

    @Activate
    public BoosterRequestFilter( @Reference final NodeCacheStore cacheStore, @Reference final SiteConfigService siteConfigService )
    {
        this.cacheStore = cacheStore;
        this.siteConfigService = siteConfigService;
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
        final Conditions conditions = new Conditions( config );

        if ( !conditions.checkPreconditions( request ) )
        {
            chain.doFilter( request, response );
            return;
        }

        // Full URL is used, so scheme, host and port are included.
        // Query String is also included with all parameters (there is an option to exclude some of them in config)
        final String fullUrl = UrlUtils.buildFullURL( request, config.excludeQueryParams() );

        final String cacheKey = cacheStore.generateCacheKey( fullUrl );

        LOG.debug( "Normalized URL of request {} with key {}", fullUrl, cacheKey );

        final CacheItem stored = cacheStore.get( cacheKey );
        final CacheItem cached;
        final Instant now = Instant.now();
        if ( stored != null )
        {
            if ( stored.cachedTime().plus( config.cacheTtlSeconds(), ChronoUnit.SECONDS ).isBefore( now ) )
            {
                LOG.debug( "Cached response {} is found but stale", cacheKey );
                cached = null;
            }
            else if ( stored.invalidatedTime() != null )
            {
                LOG.debug( "Cached response {} is found but invalidated", cacheKey );
                cached = null;
            }
            else
            {
                cached = stored;
            }
        }
        else
        {
            cached = null;
        }

        if ( !config.disableXBoosterCacheHeader() )
        {
            // Report cache HIT or MISS. Header is not present if cache is not used
            response.setHeader( "X-Booster-Cache", cached != null ? "HIT" : "MISS" );
        }

        // log request headers and their values
/*        final Enumeration<String> headerNames = request.getHeaderNames();
        while ( headerNames.hasMoreElements() ) {
            String headerName = headerNames.nextElement();
            LOG.info("Header: {} Value: {}", headerName, request.getHeader(headerName));
        }*/

        // We may send compressed and uncompressed response, so we need to Vary on Accept-Encoding
        // Make sure we don't set the header twice - Jetty also can set this header sometimes
        if ( response.getHeaders( "Vary" ).stream().noneMatch( s -> s.toLowerCase( Locale.ROOT ).contains( "accept-encoding" ) ) )
        {
            response.addHeader( "Vary", "Accept-Encoding" );
        }

        if ( cached != null )
        {
            LOG.debug( "Cached response {} is found. Writing directly from cache", cacheKey );
            new ResponseWriter( config ).writeCached( request, response, cached );
            return;
        }

        // response is very likely cacheable, we can collapse requests (wait for one request to do rendering)
        final Collapser.Latch<CacheItem> latch = stored != null ? requestCollapser.latch( cacheKey ) : null;

        CacheItem newCached = null;
        try
        {
            if ( latch != null )
            {
                newCached = latch.get();
                if ( newCached != null )
                {
                    LOG.debug( "Cached response {} generated by another request. Use collapsed request result", cacheKey );
                    new ResponseWriter( config ).writeCached( request, response, newCached );
                    return;
                }
            }

            LOG.debug( "Processing request with cache key {}", cacheKey );

            final CachingResponseWrapper cachingResponse = new CachingResponseWrapper( response );
            try (cachingResponse)
            {
                chain.doFilter( request, cachingResponse );
            }
            catch ( Exception e )
            {
                cacheStore.remove( cacheKey );
                throw e;
            }

            LOG.debug( "Response received key cache  {}", cacheKey );

            if ( conditions.checkPostconditions( request, () -> siteConfigService.execute( request ), cachingResponse ) )
            {
                final PortalRequest portalRequest = (PortalRequest) request.getAttribute( PortalRequest.class.getName() );
                final CacheMeta cacheMeta = createCacheMeta( portalRequest );

                newCached = new CacheItem( fullUrl, cachingResponse.getContentType(), cachingResponse.getCachedHeaders(), now, null,
                                           cachingResponse.getSize(), cachingResponse.getEtag(),
                                           ByteSupply.of( cachingResponse.getCachedGzipBody() ),
                                           ByteSupply.of( cachingResponse.getCachedBrBody() ) );
                cacheStore.put( cacheKey, newCached, cacheMeta );
            }
            else
            {
                if ( stored != null )
                {
                    // Evacuate item from cache immediately if it is no longer cacheable
                    // This prevents request collapsing
                    cacheStore.remove( cacheKey );
                }
            }

        }
        finally
        {
            if ( latch != null )
            {
                latch.unlock( newCached );
            }
        }
    }

    private static CacheMeta createCacheMeta( final PortalRequest portalRequest )
    {
        final String project = portalRequest.getRepositoryId() != null
            ? portalRequest.getRepositoryId().toString().substring( "com.enonic.cms.".length() )
            : null;
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
        return new CacheMeta( project, siteId, contentId, contentPath );
    }
}
