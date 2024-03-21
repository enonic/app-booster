package com.enonic.app.booster;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enonic.app.booster.servlet.RequestUtils;

import static java.util.Objects.requireNonNullElse;


public final class CachedResponseWriter
{
    private static final Logger LOG = LoggerFactory.getLogger( CachedResponseWriter.class );

    final RequestUtils.AcceptEncoding acceptEncoding;

    final String expectEtag;

    final boolean writeBody;

    final Consumer<HttpServletResponse> beforeWrite;

    public CachedResponseWriter( final HttpServletRequest request, final Consumer<HttpServletResponse> beforeWrite )
    {
        this.acceptEncoding = RequestUtils.acceptEncoding( request );
        this.expectEtag = request.getHeader( "If-None-Match" );
        this.writeBody = request.getMethod().equalsIgnoreCase( "GET" );
        this.beforeWrite = beforeWrite;
    }

    /**
     * The only headers that are allowed to be copied from the cached response when returning 304 Not Modified.
     */
    private static final Set<String> NOT_MODIFIED_HEADERS = Set.of( "cache-control", "content-location", "expires", "vary" );

    /**
     * Headers that are controlled by the cache writer and should not be copied from the cached response.
     */
    private static final Set<String> DON_NOT_COPY_HEADERS =
        Set.of( "age", "x-booster-cache", "etag", "content-type", "content-length", "connection" );

    public void write( final HttpServletResponse response, final CacheItem cached )
        throws IOException
    {
        String etagSuffix = switch ( acceptEncoding )
        {
            case BROTLI -> "-br";
            case GZIP -> "-gzip";
            case UNSPECIFIED -> "";
        };
        final String eTag = "\"" + cached.etag() + etagSuffix + "\"";

        final boolean notModified = eTag.equals( expectEtag );

        copyHeaders( response, cached.headers(), notModified );

        beforeWrite.accept( response );

        response.setHeader( "ETag", eTag );

        response.setIntHeader( "Age", (int) Math.max( 0, Math.min( ChronoUnit.SECONDS.between( cached.cachedTime(), Instant.now() ),
                                                                   Integer.MAX_VALUE ) ) + requireNonNullElse( cached.age(), 0 ) );

        if ( notModified )
        {
            LOG.debug( "Returning 304 Not Modified" );
            response.setStatus( 304 );
            response.flushBuffer();
            return;
        }

        response.setContentType( cached.contentType() );
        response.setStatus( cached.status() );

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
            .filter( entry -> !notModified || NOT_MODIFIED_HEADERS.contains( entry.getKey() ) )
            .filter( entry -> !DON_NOT_COPY_HEADERS.contains( entry.getKey() ) )
            .forEach( entry -> entry.getValue().forEach( value -> response.addHeader( entry.getKey(), value ) ) );
    }
}
