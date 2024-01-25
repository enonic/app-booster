package com.enonic.xp.booster;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

import com.enonic.xp.annotation.Order;
import com.enonic.xp.branch.Branch;
import com.enonic.xp.context.ContextAccessor;
import com.enonic.xp.home.HomeDir;
import com.enonic.xp.portal.PortalRequest;
import com.enonic.xp.portal.RenderMode;
import com.enonic.xp.web.filter.OncePerRequestFilter;

@Component(immediate = true, service = Filter.class, property = {"connector=xp"})
@Order(-4000)
@WebFilter("/site/*")
public class BoosterApp
    extends OncePerRequestFilter
{
    private final Map<String, CacheItem> cache = new ConcurrentHashMap<>();

    private final Path cacheFolder = HomeDir.get().toPath().resolve( "work" ).resolve( "cache" ).resolve( "booster" );

    @Activate
    public BoosterApp( )
    {
    }

    private static class CacheItem
    {
        final String url;

        final String contentType;

        final ByteArrayOutputStream gzipData;

        final Map<String, List<String>> headers;

        final int contentSize;

        CacheItem( final String url, final String contentType, final Map<String, List<String>> headers, final ByteArrayOutputStream data )
        {
            this.url = url;
            this.contentType = contentType;
            this.contentSize = data.size();
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

        final CacheItem cached =  cache.get( fullUrl );

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
                res.setContentLength( cached.contentSize );
                new GZIPInputStream( new ByteArrayInputStream( cached.gzipData.toByteArray() ) ).transferTo( res.getOutputStream() );
            }

            return;
        }

        final CachingResponseWrapper servletResponse = new CachingResponseWrapper( res );
        chain.doFilter( req, servletResponse );

        // responses with status code other than 200 are not cached. This is for the initial implementation.
        if (servletResponse.getStatus() != 200 )
        {
            return;
        }

        // responses with session are not cached - because session is bound to a specific user
        if (req.getSession(false) != null) {
            return;
        }

        // responses with cookies are not cached because cookies are usually set for a specific user
        if (servletResponse.withCookies) {
            return;
        }

        // only cache html responses. This is for the initial implementation
        if ( !servletResponse.getContentType().contains( "text/html" ) && !servletResponse.getContentType().contains( "text/xhtml" ) )
        {
            return;
        }

        // something very sneaky is going on here. Page controller returns compressed data! We better of not caching it for now (because we apply compression ourselves)
        if (servletResponse.headers.containsKey( "content-encoding" )) {
            return;
        }

        // Check if there are cache headers in the response that prevent caching
        // NOTE: some of them actually say DO cache. But parsing and checking them all is quite a bit of work
        final Collection<String> cacheControl = servletResponse.getHeaders( "cache-control" );
        if ( cacheControl != null )
        {
            if ( cacheControl.stream().anyMatch( s -> s.contains( "no-cache" ) || s.contains( "no-store" ) || s.contains( "max-age" ) || s.contains( "s-maxage" ) || s.contains( "private" )) )
            {
                return;
            }
        }
        // Expires header is often used to indicate that response must not be cached
        // It can also be used to indicate that response could be cached, but it is not reliable
        // We better of not caching responses with Expires header
        if (servletResponse.getHeader( "expires" ) != null) {
            return;
        }

        // only cache in LIVE mode, ando only master branch
        final PortalRequest portalRequest = (PortalRequest) req.getAttribute( PortalRequest.class.getName() );
        if ( portalRequest == null || RenderMode.LIVE != portalRequest.getMode() ||
            !portalRequest.getBranch().equals( Branch.from( "master" ) ) )
        {
            return;
        }

        final CacheItem cachedItem = new CacheItem( fullUrl, servletResponse.getContentType(), servletResponse.headers, servletResponse.baos );
        cache.put( fullUrl, cachedItem );
    }

    private static void copyHeaders( final HttpServletResponse res, final CacheItem cached )
    {
        cached.headers.forEach( ( name, values ) -> {
            for ( int i = 0; i < values.size(); i++ )
            {
                if ( i == 0 )
                {
                    res.setHeader( name, values.get( 0 ) );
                }
                else
                {
                    res.addHeader( name, values.get( i ) );
                }
            }
        } );
    }

    private static boolean supportsGzip( final HttpServletRequest req )
    {
        boolean gzip = false;
        final Enumeration<String> acceptEncodingHeaders = req.getHeaders( "Accept-Encoding" );
        if (acceptEncodingHeaders == null) {
            gzip = false;
        } else {
            while ( acceptEncodingHeaders.hasMoreElements() )
            {
                String acceptEncoding = acceptEncodingHeaders.nextElement();
                if ( acceptEncoding.contains( "gzip" ) )
                {
                    gzip = true;
                    break;
                }
            }
        }
        return gzip;
    }

    public static class CachingResponseWrapper
        extends HttpServletResponseWrapper
    {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();


        final Map<String, List<String>> headers = new LinkedHashMap<>();

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
            headers.put( name.toLowerCase( Locale.ROOT ), new ArrayList<>( List.of( value ) ) );
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

    public static MessageDigest sha512()
    {
        try
        {
            return MessageDigest.getInstance( "SHA-512" );
        }
        catch ( NoSuchAlgorithmException e )
        {
            throw new AssertionError( e );
        }
    }
}
