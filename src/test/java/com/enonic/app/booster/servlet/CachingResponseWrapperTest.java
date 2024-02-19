package com.enonic.app.booster.servlet;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aayushatharva.brotli4j.decoder.BrotliInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CachingResponseWrapperTest
{

    @Mock
    HttpServletResponse response;

    @Mock
    ServletOutputStream servletOutputStream;

    @Test
    void outputBody()
        throws Exception
    {
        when( response.getOutputStream() ).thenReturn( servletOutputStream );

        final CachingResponseWrapper wrapper = new CachingResponseWrapper( response );
        try (wrapper)
        {
            wrapper.getOutputStream().write( "Hello, World".getBytes( StandardCharsets.UTF_8 ) );
            wrapper.getOutputStream().write( '!' );
        }

        final InOrder inOrder = inOrder( servletOutputStream );
        inOrder.verify( servletOutputStream ).write( "Hello, World".getBytes( StandardCharsets.UTF_8 ), 0, 12 );
        inOrder.verify( servletOutputStream ).write( '!' );

        assertEquals( 13, wrapper.getSize() );
        assertEquals( "Hello, World!", new String(
            new GZIPInputStream( new ByteArrayInputStream( wrapper.getCachedGzipBody().toByteArray() ) ).readAllBytes() ) );
        assertEquals( "Hello, World!", new String(
            new BrotliInputStream( new ByteArrayInputStream( wrapper.getCachedBrBody().toByteArray() ) ).readAllBytes() ) );
        assertEquals( "dffd6021bb2bd5b0af676290809ec3a5", wrapper.getEtag() );
    }

    @Test
    void headers_add()
        throws Exception
    {
        final CachingResponseWrapper wrapper = new CachingResponseWrapper( response );
        try (wrapper)
        {
            wrapper.addHeader( "a", "1" );
            wrapper.addHeader( "a", "2" );
            wrapper.addDateHeader( "b", 36000L );
            wrapper.addIntHeader( "b", 4 );
        }
        assertEquals( Map.of( "a", List.of( "1", "2" ), "b", List.of( "Thu, 1 Jan 1970 00:00:36 GMT", "4" ) ), wrapper.getCachedHeaders() );
    }

    @Test
    void headers_set()
        throws Exception
    {
        final CachingResponseWrapper wrapper = new CachingResponseWrapper( response );
        try (wrapper)
        {
            wrapper.addHeader( "a", "1" );
            wrapper.setHeader( "a", "2" );
            wrapper.setDateHeader( "b", 36000L );
            wrapper.setIntHeader( "c", 4 );
        }
        assertEquals( Map.of( "a", List.of( "2" ), "b", List.of( "Thu, 1 Jan 1970 00:00:36 GMT" ), "c", List.of( "4" ) ),
                      wrapper.getCachedHeaders() );
    }

    @Test
    void headers_unset()
        throws Exception
    {
        final CachingResponseWrapper wrapper = new CachingResponseWrapper( response );
        try (wrapper)
        {
            wrapper.addHeader( "a", "1" );
            wrapper.addHeader( "a", "2" );
            wrapper.setHeader( "a", null );
        }
        assertEquals( Map.of(), wrapper.getCachedHeaders() );
    }
}
