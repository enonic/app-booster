package com.enonic.app.booster;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
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

import com.google.common.io.ByteSource;

import com.enonic.xp.annotation.Order;
import com.enonic.xp.branch.Branch;
import com.enonic.xp.context.Context;
import com.enonic.xp.context.ContextAccessor;
import com.enonic.xp.context.ContextBuilder;
import com.enonic.xp.data.PropertySet;
import com.enonic.xp.data.PropertyTree;
import com.enonic.xp.node.CreateNodeParams;
import com.enonic.xp.node.Node;
import com.enonic.xp.node.NodeId;
import com.enonic.xp.node.NodeNotFoundException;
import com.enonic.xp.node.NodePath;
import com.enonic.xp.node.NodeService;
import com.enonic.xp.node.UpdateNodeParams;
import com.enonic.xp.portal.PortalRequest;
import com.enonic.xp.portal.RenderMode;
import com.enonic.xp.repository.CreateRepositoryParams;
import com.enonic.xp.repository.RepositoryId;
import com.enonic.xp.repository.RepositoryService;
import com.enonic.xp.security.RoleKeys;
import com.enonic.xp.security.auth.AuthenticationInfo;
import com.enonic.xp.util.BinaryReference;
import com.enonic.xp.web.filter.OncePerRequestFilter;

@Component(immediate = true, service = Filter.class, property = {"connector=xp"}, configurationPid = "com.enonic.app.booster")
@Order(-4000)
@WebFilter("/site/*")
public class BoosterApp
    extends OncePerRequestFilter
{
    private static final Logger LOG = LoggerFactory.getLogger( BoosterApp.class );

    private final NodeService nodeService;

    private volatile long cacheTtlSeconds = Long.MAX_VALUE;

    private volatile List<String> excludeQueryParams = List.of();

    @Activate
    public BoosterApp( @Reference final RepositoryService repositoryService, @Reference final NodeService nodeService )
    {
        this.nodeService = nodeService;
        try
        {
            LOG.debug( "Creating repository for booster app cache" );
            repositoryService.createRepository( CreateRepositoryParams.create().repositoryId( RepositoryId.from( "booster" ) ).build() );
        }
        catch ( Exception e )
        {
            LOG.debug( "Repository is probably already exists", e );
        }
    }

    @Activate
    @Modified
    public void activate( final BoosterConfig config )
    {
        cacheTtlSeconds =
            ( config.cacheTtl() == null || config.cacheTtl().isBlank() ) ? Long.MAX_VALUE : Duration.parse( config.cacheTtl() ).toSeconds();
        excludeQueryParams = Arrays.stream( config.excludeQueryParams().split( "," ) ).map( String::trim ).collect( Collectors.toList() );
    }

    private static class CacheItem
    {
        final String url;

        final String contentType;

        final ByteArrayOutputStream gzipData;

        final Map<String, Object> headers;

        final Instant cachedTime;

        final int contentLength;

        CacheItem( final String url, final String contentType, final Map<String, Object> headers, final Instant cachedTime,
                   final ByteArrayOutputStream data )
        {
            this.url = url;
            this.contentType = contentType;
            this.contentLength = data.size();
            this.headers = headers;
            this.cachedTime = cachedTime;
            this.gzipData = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream( gzipData ))
            {
                data.writeTo( gzipOutputStream );
            }
            catch ( IOException e )
            {
                throw new UncheckedIOException( e );
            }
        }

        public CacheItem( final String url, final String contentType, final Map<String, Object> headers, final Instant cachedTime,
                          final int contentLength, final ByteArrayOutputStream gzipData )
        {
            this.url = url;
            this.contentType = contentType;
            this.headers = headers;
            this.cachedTime = cachedTime;
            this.contentLength = contentLength;
            this.gzipData = gzipData;
        }
    }

    @Override
    protected void doHandle( final HttpServletRequest req, final HttpServletResponse res, final FilterChain chain )
        throws Exception
    {
        // Browsers use GET then they visit pages. We don't want to cache anything else
        // If path contains /_/ it is a controller request. We don't cache them here at least for now.
        // Authorization header indicates, that request is personalized. Don't cache even if later response has cache-control 'public' to information leak do to misconfiguration
        final String method = req.getMethod();
        final String requestURI = req.getRequestURI();
        final boolean hasAuthorization = req.getHeader( "Authorization" ) != null;
        final boolean validSession = req.isRequestedSessionIdValid();
        if ( !"GET".equals( method ) || requestURI.contains( "/_/" ) || hasAuthorization || validSession )
        {
            LOG.debug( "Bypassing request. method {}, uri {}, has Authorization {}, validSession {}", method, requestURI,
                       hasAuthorization, validSession );

            chain.doFilter( req, res );
            return;
        }

        // Full URL is used, so scheme, host and port are included.
        // Query String is also included with all parameters (by default)
        final String fullUrl = getFullURL( req );

        LOG.debug( "Normalized URL of reqest {}", fullUrl );
        final String sha256 = sha256( fullUrl );
        //final CacheItem cached = cache.get( sha256 );

        final NodeId nodeId = NodeId.from( sha256 );
        CacheItem cached = runWithAdminRole( () -> {
            LOG.debug( "Accessing cached response {}", nodeId );

            final Node node;
            try
            {
                node = nodeService.getById( nodeId );
            }
            catch ( NodeNotFoundException e )
            {
                LOG.debug( "Cached node not found {}", nodeId );
                return null;
            }

            final var headers = node.data().getSet( "headers" ).toMap();
            final String contentType = node.data().getString( "contentType" );
            final int contentLength = node.data().getLong( "contentLength" ).intValue();
            final String url = node.data().getString( "url" );
            final Instant cachedTime = node.data().getInstant( "cachedTime" );
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final ByteSource body;
            try
            {
                body = nodeService.getBinary( nodeId, BinaryReference.from( "data.gzip" ) );
            }
            catch ( NodeNotFoundException e )
            {
                LOG.warn( "Cached node does not have response body attachment {}", nodeId );
                return null;
            }

            body.copyTo( outputStream );
            return new CacheItem( url, contentType, headers, cachedTime, contentLength, outputStream );
        } );

        if ( cached != null && cached.cachedTime.plus( cacheTtlSeconds, ChronoUnit.SECONDS ).isBefore( Instant.now() ) )
        {
            LOG.debug( "Cached response is found but stale" );
            cached = null;
        }

        // Report cache HIT or MISS. Header is not present if cache is not used
        res.setHeader( "X-Booster-Cache", cached != null ? "HIT" : "MISS" );

        if ( cached != null )
        {
            LOG.debug( "Found in cache" );

            res.setContentType( cached.contentType );

            copyHeaders( res, cached );

            if ( supportsGzip( req ) )
            {
                LOG.debug( "Request accepts gzip. Writing gzipped response body from cache" );
                // Headers will tell Jetty to not apply compression, as it is done already
                res.setHeader( "Content-Encoding", "gzip" );
                res.addHeader( "Vary", "Accept-Encoding" );
                res.setContentLength( cached.gzipData.size() );
                cached.gzipData.writeTo( res.getOutputStream() );
            }
            else
            {
                // we don't store decompressed data in cache as it is mostly waste of space
                // we can recreate uncompressed response from compressed data
                LOG.debug( "Request does not accept gzip. Writing plain response body from cache" );
                res.setContentLength( cached.contentLength );
                new GZIPInputStream( new ByteArrayInputStream( cached.gzipData.toByteArray() ) ).transferTo( res.getOutputStream() );
            }

            return;
        }

        LOG.debug( "Not found in cache. Processing request" );
        final CachingResponseWrapper servletResponse = new CachingResponseWrapper( res );
        chain.doFilter( req, servletResponse );
        LOG.debug( "Response received" );
        // responses with status code other than 200 are not cached. This is for the initial implementation.
        final int responseStatus = servletResponse.getStatus();
        if ( responseStatus != 200 )
        {
            LOG.debug( "Not cacheable status code {}", responseStatus );
            return;
        }

        // responses with session are not cached - because session is bound to a specific user
        if ( req.getSession( false ) != null )
        {
            LOG.debug( "Not cacheable because Session is created" );
            return;
        }

        // responses with cookies are not cached because cookies are usually set for a specific user
        if ( servletResponse.withCookies )
        {
            LOG.debug( "Not cacheable because cookies exist in response" );
            return;
        }

        // We may cache responses with Vary header, but it is quite a bit of work to parse and check it
        if ( servletResponse.headers.containsKey( "vary" )) {
            LOG.debug( "Not cacheable because of Vary header in response" );
            return;
        }

        // only cache html responses. This is for the initial implementation
        final String responseContentType = servletResponse.getContentType();
        if ( !responseContentType.contains( "text/html" ) && !responseContentType.contains( "text/xhtml" ) )
        {
            LOG.debug( "Not cacheable because of incompatible content-type {}", responseContentType );
            return;
        }

        // something very sneaky is going on here. Page controller returns compressed data! We better of not caching it for now (because we apply compression ourselves)
        if ( servletResponse.headers.containsKey( "content-encoding" ) )
        {
            LOG.debug( "Not cacheable because of pre-set content-encoding in response" );
            return;
        }

        // Check if there are cache headers in the response that prevent caching
        // NOTE: some of them actually say DO cache. But parsing and checking them all is quite a bit of work
        final Collection<String> cacheControl = servletResponse.getHeaders( "cache-control" );
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
        if ( servletResponse.getHeader( "expires" ) != null )
        {
            LOG.debug( "Not cacheable because of expires header in response" );
            return;
        }

        // only cache in LIVE mode, ando only master branch
        final PortalRequest portalRequest = (PortalRequest) req.getAttribute( PortalRequest.class.getName() );
        if ( portalRequest == null )
        {
            LOG.debug( "Not cacheable because response was not generated by site engine" );
            return;
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

        /*final CacheItem cachedItem =
            new CacheItem( sha256, servletResponse.getContentType(), servletResponse.headers, servletResponse.baos );*/
        runWithAdminRole( () -> {
            final PropertyTree data = new PropertyTree();
            data.setString( "url", fullUrl );
            data.setString( "contentType", servletResponse.getContentType() );
            data.setLong( "contentLength", (long) servletResponse.baos.size() );
            data.setBinaryReference( "gzipData", BinaryReference.from( "data.gzip" ) );
            data.setString( "repo", portalRequest.getRepositoryId().toString() );
            data.setInstant( "cachedTime", Instant.now() );
            final PropertySet headersPropertyTree = data.newSet();

            servletResponse.headers.forEach( headersPropertyTree::setString );

            data.setSet( "headers", headersPropertyTree );
            if ( nodeService.nodeExists( nodeId ) )
            {
                LOG.debug( "Updating existing cache node {}", nodeId );
                ByteArrayOutputStream gzipData = new ByteArrayOutputStream();
                try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream( gzipData ))
                {
                    servletResponse.baos.writeTo( gzipOutputStream );
                }

                nodeService.update( UpdateNodeParams.create()
                                        .attachBinary( BinaryReference.from( "data.gzip" ),
                                                       ByteSource.wrap( servletResponse.baos.toByteArray() ) )
                                        .id( nodeId )
                                        .editor( editor -> {
                                            editor.data = data;

                                        } )
                                        .attachBinary( BinaryReference.from( "data.gzip" ), ByteSource.wrap( gzipData.toByteArray() ) )
                                        .build() );
                return null;
            }
            else
            {
                LOG.debug( "Creating new cache node {}", nodeId );
                ByteArrayOutputStream gzipData = new ByteArrayOutputStream();
                try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream( gzipData ))
                {
                    servletResponse.baos.writeTo( gzipOutputStream );
                }
                final Node node = nodeService.create( CreateNodeParams.create()
                                                          .parent( NodePath.ROOT )
                                                          .setNodeId( nodeId )
                                                          .data( data )
                                                          .attachBinary( BinaryReference.from( "data.gzip" ),
                                                                         ByteSource.wrap( gzipData.toByteArray() ) )
                                                          .name( sha256 )
                                                          .build() );
                return null;
            }
        } );
        //cache.put( sha256, cachedItem );
    }

    private static void copyHeaders( final HttpServletResponse res, final CacheItem cached )
    {
        for ( var o : cached.headers.entrySet() )
        {
            if ( o.getValue() instanceof String )
            {
                res.setHeader( o.getKey(), (String) o.getValue() );
            }
            else if ( o.getValue() instanceof Collection )
            {
                int i = 0;
                for ( String s : (Collection<String>) o.getValue() )
                {
                    if ( i == 0 )
                    {
                        res.setHeader( o.getKey(), s );
                    }
                    else
                    {
                        res.addHeader( o.getKey(), s );
                    }
                    i++;
                }
            }
        }
    }

    private static boolean supportsGzip( final HttpServletRequest req )
    {
        final Enumeration<String> acceptEncodingHeaders = req.getHeaders( "Accept-Encoding" );
        if ( acceptEncodingHeaders != null )
        {
            while ( acceptEncodingHeaders.hasMoreElements() )
            {
                String acceptEncoding = acceptEncodingHeaders.nextElement();
                if ( acceptEncoding.contains( "gzip" ) )
                {
                    return true;
                }
            }
        }
        return false;
    }

    public static class CachingResponseWrapper
        extends HttpServletResponseWrapper
    {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();


        final Map<String, String> headers = new LinkedHashMap<>();

        boolean withCookies;

        public CachingResponseWrapper( final HttpServletResponse response )
        {
            super( response );
        }

        @Override
        public void addHeader( final String name, final String value )
        {
            super.addHeader( name, value );
        }

        @Override
        public void setIntHeader( final String name, final int value )
        {
            super.setIntHeader( name, value );
        }

        @Override
        public void addIntHeader( final String name, final int value )
        {
            super.addIntHeader( name, value );
        }

        @Override
        public void setHeader( final String name, final String value )
        {
            headers.put( name.toLowerCase( Locale.ROOT ), value );
            super.setHeader( name, value );
        }

        @Override
        public void addCookie( final Cookie cookie )
        {
            withCookies = true;
            super.addCookie( cookie );
        }

        @Override
        public PrintWriter getWriter()
            throws IOException
        {
            return super.getWriter();
        }

        @Override
        public ServletOutputStream getOutputStream()
            throws IOException
        {
            final ServletOutputStream orig = super.getOutputStream();
            return new ServletOutputStream()
            {
                @Override
                public boolean isReady()
                {
                    return orig.isReady();
                }

                @Override
                public void setWriteListener( final WriteListener writeListener )
                {
                    orig.setWriteListener( writeListener );
                }

                @Override
                public void write( final int b )
                    throws IOException
                {
                    baos.write( b );
                    orig.write( b );
                }

                @Override
                public void write( final byte[] b, final int off, final int len )
                    throws IOException
                {
                    baos.write( b, off, len );
                    orig.write( b, off, len );
                }
            };
        }
    }

    public String getFullURL( final HttpServletRequest request )
    {
        final StringBuffer urlBuilder = request.getRequestURL();

        final String queryString = request.getQueryString();

        if ( queryString != null )
        {
            final String[] params = queryString.split( "&" );
            final List<String> filteredParams =
                Arrays.stream( params ).filter( p -> !excludeQueryParams.contains( p.split( "=" )[0] ) ).collect( Collectors.toList() );
            if ( !filteredParams.isEmpty() )
            {
                urlBuilder.append( '?' ).append( String.join( "&", filteredParams ) );
            }
        }

        return urlBuilder.toString();
    }

    public static String sha256( String value )
    {
        try
        {
            return HexFormat.of().formatHex( MessageDigest.getInstance( "SHA-256" ).digest( value.getBytes( StandardCharsets.UTF_8 ) ) );
        }
        catch ( NoSuchAlgorithmException e )
        {
            throw new AssertionError( e );
        }
    }

    private <T> T runWithAdminRole( final Callable<T> callable )
    {
        final Context context = ContextAccessor.current();
        final AuthenticationInfo authenticationInfo =
            AuthenticationInfo.copyOf( context.getAuthInfo() ).principals( RoleKeys.ADMIN ).build();
        return ContextBuilder.from( context )
            .authInfo( authenticationInfo )
            .repositoryId( RepositoryId.from( "booster" ) )
            .branch( Branch.from( "master" ) )
            .build()
            .callWith( callable );
    }
}
