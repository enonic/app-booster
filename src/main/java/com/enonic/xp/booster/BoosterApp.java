package com.enonic.xp.booster;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
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
import org.osgi.service.component.annotations.Reference;

import com.google.common.io.ByteSource;

import com.enonic.xp.annotation.Order;
import com.enonic.xp.branch.Branch;
import com.enonic.xp.context.Context;
import com.enonic.xp.context.ContextAccessor;
import com.enonic.xp.context.ContextBuilder;
import com.enonic.xp.data.PropertySet;
import com.enonic.xp.data.PropertyTree;
import com.enonic.xp.home.HomeDir;
import com.enonic.xp.node.CreateNodeParams;
import com.enonic.xp.node.Node;
import com.enonic.xp.node.NodeEditor;
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

@Component(immediate = true, service = Filter.class, property = {"connector=xp"})
@Order(-4000)
@WebFilter("/site/*")
public class BoosterApp
    extends OncePerRequestFilter
{
    private final Map<String, CacheItem> cache = new ConcurrentHashMap<>();

    private final Path cacheFolder = HomeDir.get().toPath().resolve( "work" ).resolve( "cache" ).resolve( "booster" );

    private final NodeService nodeService;
    @Activate
    public BoosterApp( @Reference final RepositoryService repositoryService, @Reference final NodeService nodeService, final BoosterConfig config )
    {
        this.nodeService = nodeService;
        try
        {
            repositoryService.createRepository( CreateRepositoryParams.create().repositoryId( RepositoryId.from( "booster" ) ).build() );
        }
        catch ( Exception e )
        {
            System.out.println(e.getMessage());
        }
    }

    private static class CacheItem
    {
        final String url;

        final String contentType;

        final ByteArrayOutputStream gzipData;

        final Map<String, Object> headers;

        final int contentLength;

        CacheItem( final String url, final String contentType, final Map<String, Object> headers, final ByteArrayOutputStream data )
        {
            this.url = url;
            this.contentType = contentType;
            this.contentLength = data.size();
            this.headers = headers;
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

        public CacheItem( final String url, final String contentType, final Map<String, Object> headers, final int contentLength,
                          final ByteArrayOutputStream gzipData)
        {
            this.url = url;
            this.contentType = contentType;
            this.headers = headers;
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
        if ( !"GET".equals( req.getMethod() ) || req.getRequestURI().contains( "/_/" ) || req.getHeader( "Authorization" ) != null ||
            ContextAccessor.current().getAuthInfo().isAuthenticated() || req.isRequestedSessionIdValid() )
        {
            chain.doFilter( req, res );
            return;
        }

        // Full URL is used, so scheme, host and port are included.
        // Query String is also included with all parameters (by default)
        final String fullUrl = getFullURL( req );

        final String sha256 = sha256( fullUrl );
        //final CacheItem cached = cache.get( sha256 );

        final NodeId nodeId = NodeId.from( sha256 );
        final CacheItem cached = runWithAdminRole( () -> {

            try
            {
                final Node node = nodeService.getById( nodeId );
                if ( node == null )
                {
                    return null;
                }
                final var headers = node.data().getSet( "headers" ).toMap();
                final String contentType = node.data().getString( "contentType" );
                final int contentLength = node.data().getLong( "contentLength" ).intValue();
                final String url = node.data().getString( "url" );
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                final ByteSource body = nodeService.getBinary( nodeId, BinaryReference.from( "data.gzip" ) );
                if ( body == null )
                {
                    return null;
                }
                body.copyTo( outputStream );
                return new CacheItem( url, contentType,  headers, contentLength, outputStream );
            }
            catch ( NodeNotFoundException e )
            {
                return null;
            }
        } );

        // Report cache HIT or MISS. Header is not present if cache is not used
        res.setHeader( "X-Booster-Cache", cached != null ? "HIT" : "MISS" );

        if ( cached != null )
        {
            res.setContentType( cached.contentType );

            copyHeaders( res, cached );

            if ( supportsGzip( req ) )
            {
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
                res.setContentLength( cached.contentLength );
                new GZIPInputStream( new ByteArrayInputStream( cached.gzipData.toByteArray() ) ).transferTo( res.getOutputStream() );
            }

            return;
        }

        final CachingResponseWrapper servletResponse = new CachingResponseWrapper( res );
        chain.doFilter( req, servletResponse );

        // responses with status code other than 200 are not cached. This is for the initial implementation.
        if ( servletResponse.getStatus() != 200 )
        {
            return;
        }

        // responses with session are not cached - because session is bound to a specific user
        if ( req.getSession( false ) != null )
        {
            return;
        }

        // responses with cookies are not cached because cookies are usually set for a specific user
        if ( servletResponse.withCookies )
        {
            return;
        }

        // only cache html responses. This is for the initial implementation
        if ( !servletResponse.getContentType().contains( "text/html" ) && !servletResponse.getContentType().contains( "text/xhtml" ) )
        {
            return;
        }

        // something very sneaky is going on here. Page controller returns compressed data! We better of not caching it for now (because we apply compression ourselves)
        if ( servletResponse.headers.containsKey( "content-encoding" ) )
        {
            return;
        }

        // Check if there are cache headers in the response that prevent caching
        // NOTE: some of them actually say DO cache. But parsing and checking them all is quite a bit of work
        final Collection<String> cacheControl = servletResponse.getHeaders( "cache-control" );
        if ( cacheControl != null )
        {
            if ( cacheControl.stream()
                .anyMatch(
                    s -> s.contains( "no-cache" ) || s.contains( "no-store" ) || s.contains( "max-age" ) || s.contains( "s-maxage" ) ||
                        s.contains( "private" ) ) )
            {
                return;
            }
        }
        // Expires header is often used to indicate that response must not be cached
        // It can also be used to indicate that response could be cached, but it is not reliable
        // We better of not caching responses with Expires header
        if ( servletResponse.getHeader( "expires" ) != null )
        {
            return;
        }

        // only cache in LIVE mode, ando only master branch
        final PortalRequest portalRequest = (PortalRequest) req.getAttribute( PortalRequest.class.getName() );
        if ( portalRequest == null || RenderMode.LIVE != portalRequest.getMode() ||
            !portalRequest.getBranch().equals( Branch.from( "master" ) ) )
        {
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

            final PropertySet headersPropertyTree = data.newSet();

            servletResponse.headers.forEach( headersPropertyTree::setString );

            data.setSet( "headers", headersPropertyTree);
            if (nodeService.nodeExists( nodeId)) {
                return null;
            } else {
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
            if (o.getValue() instanceof String) {
                res.setHeader( o.getKey(), (String) o.getValue() );
            } else if (o.getValue() instanceof Collection) {
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
            urlBuilder.append( '?' ).append( queryString );
        }

        return urlBuilder.toString();
    }

    public static String sha256(String value)
    {
        try
        {
            return HexFormat.of().formatHex( MessageDigest.getInstance( "SHA-256" ).digest( value.getBytes( StandardCharsets.UTF_8 )) );
        }
        catch ( NoSuchAlgorithmException e )
        {
            throw new AssertionError( e );
        }
    }

    private <T> T runWithAdminRole( final Callable<T> callable )
    {
        final Context context = ContextAccessor.current();
        final AuthenticationInfo authenticationInfo = AuthenticationInfo.copyOf( context.getAuthInfo() ).
            principals( RoleKeys.ADMIN ).
            build();
        return ContextBuilder.from( context ).
            authInfo( authenticationInfo ).
            repositoryId( RepositoryId.from( "booster" ) ).
            branch( Branch.from( "master" ) ).
            build().
            callWith( callable );
    }
}
