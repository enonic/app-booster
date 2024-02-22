package com.enonic.app.booster;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enonic.app.booster.servlet.RequestUtils;


public final class CachedResponseWriter
{
    private static final Logger LOG = LoggerFactory.getLogger( CachedResponseWriter.class );

    final RequestUtils.AcceptEncoding acceptEncoding;

    final String expectEtag;

    final boolean writeBody;

    final BoosterConfigParsed config;

    public CachedResponseWriter( final HttpServletRequest request, final BoosterConfigParsed config )
    {
        this.acceptEncoding = RequestUtils.acceptEncoding( request );
        this.expectEtag = request.getHeader( "If-None-Match" );
        this.writeBody = request.getMethod().equalsIgnoreCase( "GET" );
        this.config = config;
    }

    /**
     * The only headers that are allowed to be copied from the cached response when returning 304 Not Modified.
     */
    private static final Set<String> NOT_MODIFIED_HEADERS = Set.of( "cache-control", "content-location", "expires", "vary" );

    /**
     * Headers that are controlled by the cache writer and should not be copied from the cached response.
     */
    private static final Set<String> OVERRIDE_HEADERS = Set.of( "age", "x-booster-cache", "etag", "content-type", "content-length" );

    public void write( final HttpServletResponse response, final CacheItem cached )
        throws IOException
    {
        final String eTagValue = switch ( acceptEncoding )
        {
            case BROTLI -> "\"" + cached.etag() + "-br" + "\"";
            case GZIP -> "\"" + cached.etag() + "-gzip" + "\"";
            default -> "\"" + cached.etag() + "\"";
        };

        final boolean notModified = eTagValue.equals( expectEtag );

        copyHeaders( response, cached.headers(), notModified );

        if ( !config.disableXBoosterCacheHeader() )
        {
            response.setHeader( "X-Booster-Cache", "HIT" );
        }
        response.setHeader( "ETag", eTagValue );
        response.setIntHeader( "Age", (int) Math.max( 0, Math.min( ChronoUnit.SECONDS.between( cached.cachedTime(), Instant.now() ),
                                                                   Integer.MAX_VALUE ) ) );

        if ( notModified )
        {
            LOG.debug( "Returning 304 Not Modified" );
            response.setStatus( 304 );
            response.flushBuffer();
            return;
        }
        else
        {
            response.setContentType( cached.contentType() );
            response.setStatus( 200 );
        }

        switch ( acceptEncoding )
        {
            case BROTLI ->
            {
                LOG.debug( "Request accepts brotli" );
                // Headers will tell Jetty to not apply compression, as it is done already
                response.setHeader( "Content-Encoding", "br" );
                response.setContentLength( cached.brotliData().size() );
            }
            case GZIP ->
            {
                LOG.debug( "Request accepts gzip" );
                // Headers will tell Jetty to not apply compression, as it is done already
                response.setHeader( "Content-Encoding", "gzip" );
                response.setContentLength( cached.gzipData().size() );
            }
            default ->
            {
                LOG.debug( "Request does not accept brotli or gzip" );
                response.setContentLength( cached.contentLength() );
            }
        }

        if ( writeBody )
        {
            LOG.debug( "Writing cached response body" );

            switch ( acceptEncoding )
            {
                case BROTLI -> cached.brotliData().writeTo( response.getOutputStream() );
                case GZIP -> cached.gzipData().writeTo( response.getOutputStream() );
                default ->
                {
                    try (InputStream in = new GZIPInputStream( cached.gzipData().openStream() ))
                    {
                        in.transferTo( response.getOutputStream() );
                    }
                }
            }
        }
        else
        {
            LOG.debug( "Request method is HEAD. Don't write cached body" );
            // Make sure Jetty does not try to calculate content-length response header for HEAD request
            response.flushBuffer();
        }
    }

    private void copyHeaders( final HttpServletResponse response, final Map<String, ? extends Collection<String>> headers,
                              final boolean notModified )
    {
        headers.entrySet()
            .stream()
            .filter( entry -> !OVERRIDE_HEADERS.contains( entry.getKey() ) )
            .filter( entry -> config.overrideCacheControlHeader() == null || !entry.getKey().equals( "cache-control" ) )
            .filter( entry -> !notModified || NOT_MODIFIED_HEADERS.contains( entry.getKey() ) )
            .forEach( entry -> entry.getValue().forEach( value -> response.addHeader( entry.getKey(), value ) ) );
    }
}
