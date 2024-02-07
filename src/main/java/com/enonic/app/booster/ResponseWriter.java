package com.enonic.app.booster;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponseWriter
{
    private static final Logger LOG = LoggerFactory.getLogger( ResponseWriter.class );

    private static final Set<String> NOT_MODIFIED_HEADERS = Set.of( "cache-control", "content-location", "expires", "vary" );
    // Date, ETag are not in the list, because they are not controlled by Content controllers

    public static void writeCached( final HttpServletRequest request, final HttpServletResponse response, final CacheItem cached )
        throws IOException
    {
        final boolean supportsGzip = supportsGzip( request );

        response.setContentType( cached.contentType() );

        final String eTagValue = "\"" + cached.etag() + ( supportsGzip ? "-gzip" : "" ) + "\"";

        final boolean notModified = eTagValue.equals( request.getHeader( "If-None-Match" ) );

        copyHeaders( response, cached, notModified );

        response.addHeader( "vary", "Accept-Encoding" );
        response.setHeader( "etag", eTagValue );

        if ( notModified )
        {
            LOG.debug( "Returning 304 Not Modified" );
            response.setStatus( 304 );
            return;
        }

        if ( supportsGzip )
        {
            LOG.debug( "Request accepts gzip. Writing gzipped response body from cache" );
            // Headers will tell Jetty to not apply compression, as it is done already
            response.setHeader( "Content-Encoding", "gzip" );
            response.setContentLength( cached.gzipData().size() );
            cached.gzipData().writeTo( response.getOutputStream() );
        }
        else
        {
            // we don't store decompressed data in cache as it is mostly waste of space
            // we can recreate uncompressed response from compressed data
            LOG.debug( "Request does not accept gzip. Writing plain response body from cache" );
            response.setContentLength( cached.contentLength() );

            new GZIPInputStream( cached.gzipData().openStream() ).transferTo( response.getOutputStream() );
        }
    }

    private static void copyHeaders( final HttpServletResponse res, final CacheItem cached, final boolean notModified )
    {
        for ( var o : cached.headers().entrySet() )
        {
            if ( notModified && !NOT_MODIFIED_HEADERS.contains( o.getKey() ) )
            {
                continue;
            }
            final String[] strings = o.getValue();
            for ( String string : strings )
            {
                res.addHeader( o.getKey(), string );
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
}
