package com.enonic.app.booster;

import java.io.IOException;
import java.time.Instant;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResponseWriter
{
    private static final Logger LOG = LoggerFactory.getLogger( ResponseWriter.class );

    private static final Set<String> NOT_MODIFIED_HEADERS = Set.of( "cache-control", "content-location", "expires", "vary" );

    private static final Set<String> CONTROLLED_HEADERS = Set.of( "age", "x-booster-cache", "etag", "content-type", "content-length" );

    private final BoosterConfigParsed config;

    public ResponseWriter( BoosterConfigParsed config )
    {
        this.config = config;
    }

    public void writeCached( final HttpServletRequest request, final HttpServletResponse response, final CacheItem cached )
        throws IOException
    {
        response.setContentType( cached.contentType() );

        final boolean supportsGzip = supportsGzip( request );
        final boolean supportsBrotli = cached.brotliData() != null && supportsBrotli( request );

        final String eTagValue = "\"" + cached.etag() + ( supportsBrotli ? "-br" : ( supportsGzip ? "-gzip" : "" ) ) + "\"";

        final boolean notModified = eTagValue.equals( request.getHeader( "If-None-Match" ) );

        copyHeaders( response, cached, notModified );

        if ( config.preventDownstreamCaching() )
        {
            LOG.debug( "Prevent downstream caching" );
            response.setHeader( "Cache-Control", "no-store" );
        }

        response.addHeader( "Vary", "Accept-Encoding" );
        response.setHeader( "Etag", eTagValue );
        response.setIntHeader( "Age", (int) ( Instant.now().getEpochSecond() - cached.cachedTime().getEpochSecond() ) );
        if ( notModified )
        {
            LOG.debug( "Returning 304 Not Modified" );
            response.sendError( 304 );
            return;
        }

        if ( supportsBrotli )
        {
            LOG.debug( "Request accepts brotli. Set correct gzip headers" );
            // Headers will tell Jetty to not apply compression, as it is done already
            response.setHeader( "Content-Encoding", "br" );
            response.setContentLength( cached.brotliData().size() );
        }
        else if ( supportsGzip )
        {
            LOG.debug( "Request accepts gzip. Set correct gzip headers" );
            // Headers will tell Jetty to not apply compression, as it is done already
            response.setHeader( "Content-Encoding", "gzip" );
            response.setContentLength( cached.gzipData().size() );
        }
        else
        {
            // we don't store decompressed data in cache as it is mostly waste of space
            // we can recreate uncompressed response from compressed data
            LOG.debug( "Request does not accept gzip. Set correct headers" );
            response.setContentLength( cached.contentLength() );
        }

        final boolean headRequest = request.getMethod().equalsIgnoreCase( "HEAD" );
        if ( !headRequest )
        {
            LOG.debug( "Writing cached response body" );
            if ( supportsBrotli )
            {
                cached.brotliData().writeTo( response.getOutputStream() );
            }
            if ( supportsGzip )
            {
                cached.gzipData().writeTo( response.getOutputStream() );
            }
            else
            {
                new GZIPInputStream( cached.gzipData().openStream() ).transferTo( response.getOutputStream() );
            }
        }
        else
        {
            LOG.debug( "Request method is HEAD. Don't write cached body" );

            // Make sure Jetty does not try to calculate content-length response header for HEAD request
            response.flushBuffer();
        }
    }

    private static void copyHeaders( final HttpServletResponse res, final CacheItem cached, final boolean notModified )
    {
        for ( var entry : cached.headers().entrySet() )
        {
            final String headerName = entry.getKey();
            if ( CONTROLLED_HEADERS.contains( headerName ) )
            {
                continue;
            }
            if ( notModified && !NOT_MODIFIED_HEADERS.contains( headerName ) )
            {
                continue;
            }
            entry.getValue().forEach( string -> res.addHeader( headerName, string ) );
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

    private static boolean supportsBrotli( final HttpServletRequest req )
    {
        final Enumeration<String> acceptEncodingHeaders = req.getHeaders( "Accept-Encoding" );
        if ( acceptEncodingHeaders != null )
        {
            while ( acceptEncodingHeaders.hasMoreElements() )
            {
                String acceptEncoding = acceptEncodingHeaders.nextElement();
                if ( acceptEncoding.contains( "br" ) )
                {
                    return true;
                }
            }
        }
        return false;
    }

}
