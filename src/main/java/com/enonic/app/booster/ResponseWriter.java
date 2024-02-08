package com.enonic.app.booster;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
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

    private ResponseWriter()
    {
    }

    public static void writeCached( final HttpServletRequest request, final HttpServletResponse response, final CacheItem cached, boolean preventDownstreamCaching )
        throws IOException
    {
        response.setContentType( cached.contentType() );

        final boolean supportsGzip = supportsGzip( request );
        final String eTagValue = "\"" + cached.etag() + ( supportsGzip ? "-gzip" : "" ) + "\"";

        final boolean notModified = eTagValue.equals( request.getHeader( "If-None-Match" ) );

        copyHeaders( response, cached, notModified );

        if ( preventDownstreamCaching)
        {
            LOG.debug( "Prevent downstream caching" );
            response.setHeader( "Cache-Control", "no-store" );
        }

        response.addHeader( "Vary", "Accept-Encoding" );
        response.setHeader( "Etag", eTagValue );
        response.setIntHeader( "Age", (int) ( Instant.now().getEpochSecond() - cached.cachedTime().getEpochSecond()) );
        if ( notModified )
        {
            LOG.debug( "Returning 304 Not Modified" );
            response.sendError( 304 );
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
}
