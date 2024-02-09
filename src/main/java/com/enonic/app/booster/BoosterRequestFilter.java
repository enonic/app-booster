package com.enonic.app.booster;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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

    @Activate
    public BoosterRequestFilter( @Reference final NodeCacheStore cacheStore )
    {
        this.cacheStore = cacheStore;
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

        LOG.debug( "Normalized URL of request {}", fullUrl );

        final String cacheKey = cacheStore.generateCacheKey( fullUrl );

        final CacheItem stored = cacheStore.get( cacheKey );
        final CacheItem cached;
        if ( stored != null )
        {
            if ( stored.cachedTime().plus( config.cacheTtlSeconds(), ChronoUnit.SECONDS ).isBefore( Instant.now() ) )
            {
                LOG.debug( "Cached response is found but stale" );
                cached = null;
            }
            else if ( stored.invalidatedTime() != null )
            {
                LOG.debug( "Cached response is found but invalidated" );
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

        if ( cached != null )
        {
            LOG.debug( "Found in cache" );
            new ResponseWriter( config ).writeCached( request, response, cached );
            return;
        }

        LOG.debug( "Processing request" );

        final CachingResponseWrapper cachingResponse = new CachingResponseWrapper( response );
        chain.doFilter( request, cachingResponse );

        LOG.debug( "Response received" );

        if ( !conditions.checkPostconditions( request, cachingResponse ) )
        {
            return;
        }

        final PortalRequest portalRequest = (PortalRequest) request.getAttribute( PortalRequest.class.getName() );

        cacheStore.put( cacheKey, fullUrl, cachingResponse.getContentType(), cachingResponse.getCachedHeaders(),
                        portalRequest.getRepositoryId().toString(), ByteSupply.of( cachingResponse.getCachedBody() ) );
    }
}
