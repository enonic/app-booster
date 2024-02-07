package com.enonic.app.booster;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;

import com.enonic.app.booster.io.BytesWriter;
import com.enonic.app.booster.storage.NodeCacheStore;
import com.enonic.xp.annotation.Order;
import com.enonic.xp.app.ApplicationKey;
import com.enonic.xp.branch.Branch;
import com.enonic.xp.data.PropertySet;
import com.enonic.xp.data.PropertyTree;
import com.enonic.xp.portal.PortalRequest;
import com.enonic.xp.portal.RenderMode;
import com.enonic.xp.site.Site;
import com.enonic.xp.site.SiteConfig;
import com.enonic.xp.web.filter.OncePerRequestFilter;

@Component(immediate = true, service = Filter.class, property = {"connector=xp"}, configurationPid = "com.enonic.app.booster")
@Order(-190)
@WebFilter("/site/*")
public class BoosterRequestFilter
    extends OncePerRequestFilter
{
    private static final Logger LOG = LoggerFactory.getLogger( BoosterRequestFilter.class );

    private final NodeCacheStore cacheStore;

    private volatile long cacheTtlSeconds = Long.MAX_VALUE;

    private volatile List<String> excludeQueryParams = List.of();

    @Activate
    public BoosterRequestFilter( @Reference final NodeCacheStore cacheStore )
    {
        this.cacheStore = cacheStore;
    }

    @Activate
    @Modified
    public void activate( final BoosterConfig config )
    {
        cacheTtlSeconds =
            ( config.cacheTtl() == null || config.cacheTtl().isBlank() ) ? Long.MAX_VALUE : Duration.parse( config.cacheTtl() ).toSeconds();
        excludeQueryParams = Arrays.stream( config.excludeQueryParams().split( "," ) ).map( String::trim ).collect( Collectors.toList() );
    }

    @Override
    protected void doHandle( final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain )
        throws Exception
    {
        // Browsers use GET then they visit pages. We don't want to cache anything else
        // If path contains /_/ it is a controller request. We don't cache them here at least for now.
        // Authorization header indicates, that request is personalized. Don't cache even if later response has cache-control 'public' to information leak do to misconfiguration
        final String scheme = request.getScheme();
        final String method = request.getMethod();

        final String requestURI = request.getRequestURI();
        final boolean hasAuthorization = request.getHeader( "Authorization" ) != null;
        final boolean validSession = request.isRequestedSessionIdValid();
        if ( !( scheme.equals( "http" ) || scheme.equals( "https" ) ) || !"GET".equals( method ) || requestURI.contains( "/_/" ) ||
            hasAuthorization || validSession )
        {
            LOG.debug( "Bypassing request. scheme {} method {}, uri {}, has Authorization {}, validSession {}", scheme, method, requestURI,
                       hasAuthorization, validSession );

            chain.doFilter( request, response );
            return;
        }

        // Full URL is used, so scheme, host and port are included.
        // Query String is also included with all parameters (by default)
        final String fullUrl = getFullURL( request );

        final String cacheKey = cacheStore.generateCacheKey( fullUrl );

        LOG.debug( "Normalized URL of reqest {}", fullUrl );

        CacheItem cached = cacheStore.get( cacheKey );

        if ( cached != null && cached.cachedTime.plus( cacheTtlSeconds, ChronoUnit.SECONDS ).isBefore( Instant.now() ) )
        {
            LOG.debug( "Cached response is found but stale" );
            cached = null;
        }

        // Report cache HIT or MISS. Header is not present if cache is not used
        response.setHeader( "x-booster-cache", cached != null ? "HIT" : "MISS" );

        if ( cached != null )
        {
            LOG.debug( "Found in cache" );
            ResponseWriter.writeCached( request, response, cached );
            return;
        }

        LOG.debug( "Not found in cache. Processing request" );

        final CachingResponseWrapper cachingResponse = new CachingResponseWrapper( response );
        chain.doFilter( request, cachingResponse );

        LOG.debug( "Response received" );

        // responses with session are not cached - because session is bound to a specific user
        if ( request.getSession( false ) != null )
        {
            LOG.debug( "Not cacheable because Session is created" );
            return;
        }

        // responses with status code other than 200 are not cached. This is for the initial implementation.
        final int responseStatus = cachingResponse.getStatus();
        if ( responseStatus != 200 )
        {
            LOG.debug( "Not cacheable status code {}", responseStatus );
            return;
        }

        // responses with cookies are not cached because cookies are usually set for a specific user
        if ( cachingResponse.withCookies )
        {
            LOG.debug( "Not cacheable because cookies exist in response" );
            return;
        }

        // We may cache responses with Vary header, but it is quite a bit of work to parse and check it
        if ( cachingResponse.headers.containsKey( "vary" ) )
        {
            LOG.debug( "Not cacheable because of Vary header in response" );
            return;
        }

        // something very sneaky is going on here. Page controller returns compressed data! We better of not caching it for now (because we apply compression ourselves)
        if ( cachingResponse.headers.containsKey( "content-encoding" ) )
        {
            LOG.debug( "Not cacheable because of pre-set content-encoding in response" );
            return;
        }

        // only cache html responses. This is for the initial implementation
        final String responseContentType = cachingResponse.getContentType();
        if ( !responseContentType.contains( "text/html" ) && !responseContentType.contains( "text/xhtml" ) )
        {
            LOG.debug( "Not cacheable because of incompatible content-type {}", responseContentType );
            return;
        }

        // Check if there are cache headers in the response that prevent caching
        // NOTE: some of them actually say DO cache. But parsing and checking them all is quite a bit of work
        final Collection<String> cacheControl = cachingResponse.getHeaders( "cache-control" );
        if ( cacheControl != null )
        {
            LOG.debug( "Evaluating cache-control headers in response {}", cacheControl );
            if ( cacheControl.stream()
                .anyMatch(
                    s -> s.contains( "no-cache" ) || s.contains( "no-store" ) || s.contains( "max-age" ) || s.contains( "s-maxage" ) ||
                        s.contains( "private" ) ) )
            {
                LOG.debug( "Not cacheable because of cache-control headers in response" );
                return;
            }
        }
        // Expires header is often used to indicate that response must not be cached
        // It can also be used to indicate that response could be cached, but it is not reliable
        // We better of not caching responses with Expires header
        if ( cachingResponse.getHeader( "expires" ) != null )
        {
            LOG.debug( "Not cacheable because of expires header in response" );
            return;
        }

        // only cache in LIVE mode, and only master branch
        final PortalRequest portalRequest = (PortalRequest) request.getAttribute( PortalRequest.class.getName() );
        if ( portalRequest == null )
        {
            LOG.debug( "Not cacheable because response was not generated by site engine" );
            return;
        }
        // site must have booster application
        final Site site = portalRequest.getSite();
        if ( site == null )
        {
            LOG.debug( "Not cacheable because site is not set in portal request" );
            return;
        }

        final SiteConfig boosterConfig = site.getSiteConfigs().get( ApplicationKey.from( "com.enonic.app.booster" ) );
        if ( boosterConfig == null )
        {
            LOG.debug( "Not cacheable because site does not have booster application" );
            return;
        }

        final PropertyTree config = boosterConfig.getConfig();
        if ( Boolean.TRUE.equals( config.getBoolean( "disable" ) ) )
        {
            LOG.debug( "Not cacheable because booster is disabled for the site" );
            return;
        }

        for ( PropertySet patternNode : config.getSets( "patterns" ) )
        {
            String pattern = patternNode.getString( "pattern" );
            boolean invert = Boolean.TRUE.equals( patternNode.getBoolean( "invert" ) );

            if ( !matchesUrlPattern( pattern, invert, request.getPathInfo(), request.getParameterMap() ) )
            {
                LOG.debug( "Not cacheable because of pattern {}, invert {}", pattern, invert );
                return;
            }
        }

        final RenderMode renderMode = portalRequest.getMode();
        if ( RenderMode.LIVE != renderMode )
        {
            LOG.debug( "Not cacheable because site engine render mode is {}", renderMode );
            return;
        }
        final Branch requestBranch = portalRequest.getBranch();
        if ( !requestBranch.equals( Branch.from( "master" ) ) )
        {
            LOG.debug( "Not cacheable because of site engine branch is {}", requestBranch );
            return;
        }
        cacheStore.put( cacheKey, fullUrl, cachingResponse.getContentType(), cachingResponse.headers, portalRequest.getRepositoryId().toString(),
                        BytesWriter.of( cachingResponse.body ) );
    }


    static class CachingResponseWrapper
        extends HttpServletResponseWrapper
    {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();

        final Map<String, String[]> headers = new LinkedHashMap<>();

        boolean withCookies;

        public CachingResponseWrapper( final HttpServletResponse response )
        {
            super( response );
        }

        @Override
        public void setHeader( final String name, final String value )
        {
            super.setHeader( name, value );
            headers.put( name.toLowerCase( Locale.ROOT ), new String[]{value} );
        }

        @Override
        public void addCookie( final Cookie cookie )
        {
            super.addCookie( cookie );
            withCookies = true;
        }

        @Override
        public ServletOutputStream getOutputStream()
            throws IOException
        {
            return new ServletOutputStreamWrapper( super.getOutputStream() )
            {

                @Override
                public void write( final int b )
                    throws IOException
                {
                    super.write( b );
                    body.write( b );
                }

                @Override
                public void write( final byte[] b, final int off, final int len )
                    throws IOException
                {
                    super.write( b, off, len );
                    body.write( b, off, len );
                }
            };
        }
    }

    public String getFullURL( final HttpServletRequest request )
    {
        // rebuild the URL from the request
        final String scheme = request.getScheme();
        final String serverName = ( request.getServerName().endsWith( "." )
            ? request.getServerName().substring( 0, request.getServerName().length() - 1 )
            : request.getServerName() ).toLowerCase( Locale.ROOT );
        final int serverPort = request.getServerPort();
        final String path = request.getRequestURI().toLowerCase( Locale.ROOT );

        final var params = request.getParameterMap();// we only support GET requests, no POST data can sneak in.

        final String queryString = normalizedQueryParams( params );

        final StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append( scheme ).append( "://" ).append( serverName );
        if ( !( ( "http".equals( scheme ) && serverPort == 80 ) || ( "https".equals( scheme ) && serverPort == 443 ) ) )
        {
            urlBuilder.append( ":" ).append( serverPort );
        }
        urlBuilder.append( path );
        if ( !queryString.isEmpty() )
        {
            urlBuilder.append( "?" ).append( queryString );
        }

        return urlBuilder.toString();
    }

    private boolean matchesUrlPattern( final String pattern, boolean invert, final String relativePath, final Map<String, String[]> params )
    {
        final boolean patternHasQueryParameters = pattern.contains( "\\?" );
        final boolean patternMatches = Pattern.compile( pattern )
            .matcher( patternHasQueryParameters ? relativePath + "?" + normalizedQueryParams( params ) : relativePath )
            .matches();
        return invert != patternMatches;
    }

    private String normalizedQueryParams( final Map<String, String[]> params )
    {
        if ( params.isEmpty() )
        {
            return "";
        }

        final Escaper urlEscaper = UrlEscapers.urlFormParameterEscaper();
        return params.entrySet()
            .stream()
            .filter( entry -> !excludeQueryParams.contains( entry.getKey() ) )
            .sorted( Map.Entry.comparingByKey() )
            .flatMap( entry -> Arrays.stream( entry.getValue() )
                .map( value -> urlEscaper.escape( entry.getKey() ) + "=" + urlEscaper.escape( value ) ) )
            .collect( Collectors.joining( "&" ) );
    }
}
