package com.enonic.app.booster;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.encoder.BrotliOutputStream;

import com.enonic.app.booster.io.ByteSupply;
import com.enonic.app.booster.servlet.RequestUtils;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CachedResponseWriterTest
{
    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    static
    {
        Brotli4jLoader.ensureAvailability();
    }

    @Test
    void write304()
        throws Exception
    {
        final CachedResponseWriter writer;
        try (MockedStatic<RequestUtils> requestUtils = mockStatic( RequestUtils.class ))
        {
            when( request.getMethod() ).thenReturn( "GET" );
            when( request.getHeader( "If-None-Match" ) ).thenReturn( "\"etag\"" );
            requestUtils.when( () -> RequestUtils.acceptEncoding( request ) ).thenReturn( RequestUtils.AcceptEncoding.UNSPECIFIED );
            writer = new CachedResponseWriter( request, r -> {} );
        }

        writer.write( response, newCacheItem() );
        verify( response ).setHeader( "ETag", "\"etag\"" );
        verify( response ).addHeader( "vary", "Accept-Language" );
        verify( response ).addHeader( "cache-control", "max-age=60" );
        verify( response ).setIntHeader( eq( "Age" ), anyInt() );
        verify( response ).setStatus( 304 );
        verify( response ).flushBuffer();
        verifyNoMoreInteractions( response );
    }

    @Test
    void writeHead()
        throws Exception
    {
        final CachedResponseWriter writer;
        try (MockedStatic<RequestUtils> requestUtils = mockStatic( RequestUtils.class ))
        {
            when( request.getMethod() ).thenReturn( "HEAD" );
            requestUtils.when( () -> RequestUtils.acceptEncoding( request ) ).thenReturn( RequestUtils.AcceptEncoding.UNSPECIFIED );
            writer = new CachedResponseWriter( request, r -> {} );
        }
        writer.write( response, newCacheItem() );
        verify( response ).setContentType( "text/xhtml" );
        verify( response ).setHeader( "ETag", "\"etag\"" );
        verify( response ).addHeader( "vary", "Accept-Language" );
        verify( response ).addHeader( "cache-control", "max-age=60" );
        verify( response ).setIntHeader( eq( "Age" ), anyInt() );
        verify( response ).setContentLength( 12 );
        verify( response ).setStatus( 200 );
        verify( response ).flushBuffer();
        verifyNoMoreInteractions( response );
    }

    @Test
    void write()
        throws Exception
    {
        final BoosterConfigParsed config =
            BoosterConfigParsed.parse( mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() ) );
        final CachedResponseWriter writer;
        try (MockedStatic<RequestUtils> requestUtils = mockStatic( RequestUtils.class ))
        {
            when( request.getMethod() ).thenReturn( "GET" );
            requestUtils.when( () -> RequestUtils.acceptEncoding( request ) ).thenReturn( RequestUtils.AcceptEncoding.UNSPECIFIED );
            writer = new CachedResponseWriter( request, r -> response.setHeader( "Cache-Status", "Booster; hit" ) );
        }
        when( response.getOutputStream() ).thenReturn( mock( ServletOutputStream.class ) );
        writer.write( response, newCacheItem() );
        verify( response ).setContentType( "text/xhtml" );
        verify( response ).setHeader( "Cache-Status", "Booster; hit" );
        verify( response ).setHeader( "ETag", "\"etag\"" );
        verify( response ).addHeader( "vary", "Accept-Language" );
        verify( response ).addHeader( "cache-control", "max-age=60" );
        verify( response ).setIntHeader( eq( "Age" ), anyInt() );
        verify( response ).setContentLength( 12 );
        verify( response ).setStatus( 200 );
        verify( response ).getOutputStream();
        verifyNoMoreInteractions( response );
    }

    @Test
    void write_no_booster_header()
        throws Exception
    {
        final CachedResponseWriter writer;
        try (MockedStatic<RequestUtils> requestUtils = mockStatic( RequestUtils.class ))
        {
            when( request.getMethod() ).thenReturn( "GET" );
            requestUtils.when( () -> RequestUtils.acceptEncoding( request ) ).thenReturn( RequestUtils.AcceptEncoding.UNSPECIFIED );
            writer = new CachedResponseWriter( request, r -> {});
        }
        when( response.getOutputStream() ).thenReturn( mock( ServletOutputStream.class ) );
        writer.write( response, newCacheItem() );
        verify( response ).setContentType( "text/xhtml" );
        verify( response ).setHeader( "ETag", "\"etag\"" );
        verify( response ).addHeader( "vary", "Accept-Language" );
        verify( response ).addHeader( "cache-control", "max-age=60" );
        verify( response ).setIntHeader( eq( "Age" ), anyInt() );
        verify( response ).setContentLength( 12 );
        verify( response ).setStatus( 200 );
        verify( response ).getOutputStream();
        verifyNoMoreInteractions( response );
    }

    @Test
    void write_brotli()
        throws Exception
    {
        final CachedResponseWriter writer;
        try (MockedStatic<RequestUtils> requestUtils = mockStatic( RequestUtils.class ))
        {
            when( request.getMethod() ).thenReturn( "GET" );

            requestUtils.when( () -> RequestUtils.acceptEncoding( request ) ).thenReturn( RequestUtils.AcceptEncoding.BROTLI );
            writer = new CachedResponseWriter( request, r -> {} );
        }
        when( response.getOutputStream() ).thenReturn( mock( ServletOutputStream.class ) );
        writer.write( response, newCacheItem() );
        verify( response ).setContentType( "text/xhtml" );
        verify( response ).setHeader( "ETag", "\"etag-br\"" );
        verify( response ).setHeader( "Content-Encoding", "br" );
        verify( response ).addHeader( "vary", "Accept-Language" );
        verify( response ).addHeader( "cache-control", "max-age=60" );
        verify( response ).setIntHeader( eq( "Age" ), anyInt() );
        verify( response ).setContentLength( 16 );
        verify( response ).setStatus( 200 );
        verify( response ).getOutputStream();
        verifyNoMoreInteractions( response );
    }

    @Test
    void write_gzip()
        throws Exception
    {
        final CachedResponseWriter writer;
        try (MockedStatic<RequestUtils> requestUtils = mockStatic( RequestUtils.class ))
        {
            when( request.getMethod() ).thenReturn( "GET" );
            requestUtils.when( () -> RequestUtils.acceptEncoding( request ) ).thenReturn( RequestUtils.AcceptEncoding.GZIP );
            writer = new CachedResponseWriter( request, r -> {} );
        }
        when( response.getOutputStream() ).thenReturn( mock( ServletOutputStream.class ) );
        writer.write( response, newCacheItem() );
        verify( response ).setContentType( "text/xhtml" );
        verify( response ).setHeader( "ETag", "\"etag-gzip\"" );
                verify( response ).setHeader( "Content-Encoding", "gzip" );
        verify( response ).addHeader( "vary", "Accept-Language" );
        verify( response ).addHeader( "cache-control", "max-age=60" );
        verify( response ).setIntHeader( eq( "Age" ), anyInt() );
        verify( response ).setContentLength( 32 );
        verify( response ).setStatus( 200 );
        verify( response ).getOutputStream();
        verifyNoMoreInteractions( response );
    }

    CacheItem newCacheItem()
        throws IOException
    {
        final String data = "Hello World!";

        final ByteArrayOutputStream baosBrotli = new ByteArrayOutputStream();
        try (BrotliOutputStream osBr = new BrotliOutputStream( baosBrotli ))
        {
            osBr.write( data.getBytes( StandardCharsets.UTF_8 ) );
        }

        final ByteArrayOutputStream baosGzip = new ByteArrayOutputStream();
        try (GZIPOutputStream osBr = new GZIPOutputStream( baosGzip ))
        {
            osBr.write( data.getBytes( StandardCharsets.UTF_8 ) );
        }

        final Map<String, List<String>> headers =
            Map.of( "x-booster-cache", List.of( "ignored" ), "vary", List.of( "Accept-Language" ), "cache-control",
                    List.of( "max-age=60" ) );

        return new CacheItem( 200, "text/xhtml", headers, Instant.EPOCH, null, null, null, data.length(), "etag", ByteSupply.of( baosGzip ),
                              ByteSupply.of( baosBrotli ) );
    }
}
