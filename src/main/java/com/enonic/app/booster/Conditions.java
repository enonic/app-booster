package com.enonic.app.booster;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enonic.app.booster.servlet.CachingResponse;
import com.enonic.app.booster.servlet.RequestUtils;
import com.enonic.xp.branch.Branch;
import com.enonic.xp.content.ContentPath;
import com.enonic.xp.data.PropertySet;
import com.enonic.xp.data.PropertyTree;
import com.enonic.xp.portal.PortalRequest;
import com.enonic.xp.portal.RenderMode;
import com.enonic.xp.site.Site;
import com.enonic.xp.site.SiteConfig;

public class Conditions
{

    List<BiFunction<HttpServletRequest, CachingResponse, Boolean>> extraPostconditions;

    @SafeVarargs
    public Conditions( BiFunction<HttpServletRequest, CachingResponse, Boolean>... extraPostconditions )
    {
        this.extraPostconditions = List.of( extraPostconditions );
    }

    private static final Logger LOG = LoggerFactory.getLogger( Conditions.class );

    public boolean checkPreconditions( final HttpServletRequest request )
    {
        // Anything else except http and https (like ws, wss) we bypass.
        final String scheme = request.getScheme();
        if ( !scheme.equals( "http" ) && !scheme.equals( "https" ) )
        {
            LOG.debug( "Bypassing request with scheme {}", scheme );
            return false;
        }

        // Browsers use only GET then they visit pages. We also allow HEAD requests but only for the case that we can serve response from cache.
        final String method = request.getMethod();
        if ( !"GET".equalsIgnoreCase( method ) && !"HEAD".equalsIgnoreCase( method ) )
        {
            LOG.debug( "Bypassing request with method {}", method );
            return false;
        }

        // Authorization header indicates that request is personalized.
        // Don't cache even if later response has cache-control 'public' to information leak do to misconfiguration.
        final boolean hasAuthorization = request.getHeader( "Authorization" ) != null;
        if ( hasAuthorization )
        {
            LOG.debug( "Bypassing request with Authorization" );
            return false;
        }

        // Session means that request is personalized.
        final boolean validSession = request.isRequestedSessionIdValid();
        if ( validSession )
        {
            LOG.debug( "Bypassing request with valid session" );
            return false;
        }

        // If path contains /_/ it is a controller request. We don't cache them here at least for now.
        final String requestURI = request.getRequestURI();
        if ( requestURI.contains( "/_/" ) )
        {
            LOG.debug( "Bypassing request with uri {}", requestURI );
            return false;
        }
        return true;
    }

    public boolean checkPostconditions( final HttpServletRequest request, final CachingResponse response )
    {
        // In case of HEAD method there might be an empty body. We definitely don't want to cache it.
        // In fact, we don't want to cache responses for requests with anything except GET method.
        if ( !"GET".equalsIgnoreCase( request.getMethod() ) )
        {
            LOG.debug( "Not caching response for request with method {}", request.getMethod() );
            return false;
        }

        // responses with session are not cached - because session is bound to a specific user
        if ( request.getSession( false ) != null )
        {
            LOG.debug( "Not cacheable because Session is created" );
            return false;
        }

        // responses with status code other than 200 are not cached. This is for the initial implementation.
        final int responseStatus = response.getStatus();
        if ( responseStatus != 200 )
        {
            LOG.debug( "Not cacheable status code {}", responseStatus );
            return false;
        }

        // only cache html responses. This is for the initial implementation
        final String responseContentType = response.getContentType();
        if ( responseContentType == null ||
            !( responseContentType.contains( "text/html" ) || responseContentType.contains( "text/xhtml" ) ) )
        {
            LOG.debug( "Not cacheable because of incompatible content-type {}", responseContentType );
            return false;
        }

        // We may cache responses with Vary header, but it is quite a bit of work to parse and check it
        if ( response.getCachedHeaders().containsKey( "vary" ) )
        {
            LOG.debug( "Not cacheable because of Vary header in response" );
            return false;
        }

        // something very sneaky is going on here. Page controller returns compressed data! We better of not caching it for now (because we apply compression ourselves)
        if ( response.getCachedHeaders().containsKey( "content-encoding" ) )
        {
            LOG.debug( "Not cacheable because of pre-set content-encoding in response" );
            return false;
        }

        // Expires header is often used to indicate that response must not be cached
        // It can also be used to indicate that response could be cached, but it is not reliable
        // We better of not caching responses with Expires header
        if ( response.containsHeader( "Expires" ) )
        {
            LOG.debug( "Not cacheable because of expires header in response" );
            return false;
        }

        // Check if there are cache headers in the response that prevent caching
        final Collection<String> cacheControl = response.getHeaders( "Cache-Control" );
        if ( cacheControl != null )
        {
            LOG.debug( "Evaluating cache-control headers in response {}", cacheControl );
            if ( cacheControl.stream().anyMatch( s -> s.contains( "no-store" ) || s.contains( "private" ) ) )
            {
                LOG.debug( "Not cacheable because of cache-control headers in response" );
                return false;
            }
        }

        // Check if there are extra postconditions
        for ( BiFunction<HttpServletRequest, CachingResponse, Boolean> extraPostcondition : extraPostconditions )
        {
            if ( !extraPostcondition.apply( request, response ) )
            {
                return false;
            }
        }

        return true;
    }


    public static class PortalRequestConditions
    {
        public boolean check( HttpServletRequest request, CachingResponse response )
        {
            final PortalRequest portalRequest = (PortalRequest) request.getAttribute( PortalRequest.class.getName() );
            if ( portalRequest == null )
            {
                LOG.debug( "Not cacheable because response was not generated by site engine" );
                return false;
            }

            final RenderMode renderMode = portalRequest.getMode();
            if ( RenderMode.LIVE != renderMode )
            {
                LOG.debug( "Not cacheable because site engine render mode is {}", renderMode );
                return false;
            }

            // only cache in LIVE mode, and only master branch
            final Branch requestBranch = portalRequest.getBranch();
            if ( !requestBranch.equals( Branch.from( "master" ) ) )
            {
                LOG.debug( "Not cacheable because of site engine branch is {}", requestBranch );
                return false;
            }
            return true;
        }
    }

    public static class SiteConfigConditions
    {
        private final BoosterConfigParsed config;

        private final Supplier<SiteConfig> siteConfigSupplier;

        public SiteConfigConditions( final BoosterConfigParsed config, Supplier<SiteConfig> siteConfigSupplier )
        {
            this.config = config;
            this.siteConfigSupplier = siteConfigSupplier;
        }

        private static final ConcurrentMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

        public boolean check( HttpServletRequest request, CachingResponse response )
        {
            final SiteConfig siteConfig = siteConfigSupplier.get();

            // site must have booster application
            if ( siteConfig == null )
            {
                LOG.debug( "Not cacheable because site does not have site with booster application" );
                return false;
            }

            final PropertyTree boosterConfig = siteConfig.getConfig();
            if ( Boolean.TRUE.equals( boosterConfig.getBoolean( "disable" ) ) )
            {
                LOG.debug( "Not cacheable because booster is disabled for the site" );
                return false;
            }

            final PortalRequest portalRequest = (PortalRequest) request.getAttribute( PortalRequest.class.getName() );

            for ( PropertySet patternNode : boosterConfig.getSets( "patterns" ) )
            {
                String pattern = patternNode.getString( "pattern" );
                boolean invert = Boolean.TRUE.equals( patternNode.getBoolean( "invert" ) );

                if ( !matchesUrlPattern( pattern, invert, siteRelativePath( portalRequest.getSite(), portalRequest.getContentPath() ),
                                         request.getParameterMap() ) )
                {
                    LOG.debug( "Not cacheable because of pattern {}, invert {}", pattern, invert );
                    return false;
                }
            }
            return true;
        }

        private boolean matchesUrlPattern( final String pattern, boolean invert, final String relativePath,
                                           final Map<String, String[]> params )
        {
            final boolean patternHasQueryParameters = pattern.contains( "\\?" );
            final boolean patternMatches = PATTERN_CACHE.computeIfAbsent( pattern, Pattern::compile )
                .matcher( patternHasQueryParameters ? relativePath + "?" +
                    RequestUtils.normalizedQueryParams( params, config.excludeQueryParams() ) : relativePath )
                .matches();
            return invert != patternMatches;
        }

        private static String siteRelativePath( final Site site, final ContentPath contentPath )
        {
            if ( site == null )
            {
                return contentPath.toString();
            }
            else if ( site.getPath().equals( contentPath ) )
            {
                return "/";
            }
            else
            {
                return contentPath.toString().substring( site.getPath().toString().length() );
            }
        }
    }
}

