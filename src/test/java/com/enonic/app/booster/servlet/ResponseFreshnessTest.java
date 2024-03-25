package com.enonic.app.booster.servlet;

import java.time.Instant;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResponseFreshnessTest
{
    @Test
    void expiresTime()
    {
        final HttpServletResponse response = mock( HttpServletResponse.class );
        when( response.getHeader( "Date" ) ).thenReturn( "Thu, 01 Jan 1970 00:00:01 GMT" );
        when( response.getHeader( "Age" ) ).thenReturn( "10" );
        when( response.getHeaders( "Cache-Control" ) ).thenReturn( List.of( "max-age=3600" ) );
        final ResponseFreshness freshness = ResponseFreshness.build( response );
        final Instant expiresTime = freshness.expiresTime( null );
        assertNotNull( expiresTime );
        assertEquals( 3591, expiresTime.getEpochSecond() );
    }

    @Test
    void expiresTime_s_maxage()
    {
        final HttpServletResponse response = mock( HttpServletResponse.class );
        when( response.getHeader( "Date" ) ).thenReturn( "Thu, 01 Jan 1970 00:00:01 GMT" );
        when( response.getHeaders( "Cache-Control" ) ).thenReturn( List.of( "max-age=3600", "s-maxage=10" ) );
        final ResponseFreshness freshness = ResponseFreshness.build( response );
        final Instant expiresTime = freshness.expiresTime( null );
        assertNotNull( expiresTime );
        assertEquals( 11, expiresTime.getEpochSecond() );
    }

    @Test
    void expiresTime_fallback()
    {
        final HttpServletResponse response = mock( HttpServletResponse.class );
        when( response.getHeader( "Date" ) ).thenReturn( "Thu, 01 Jan 1970 00:00:01 GMT" );
        when( response.getHeaders( "Cache-Control" ) ).thenReturn( List.of(  ) );
        final ResponseFreshness freshness = ResponseFreshness.build( response );
        final Instant expiresTime = freshness.expiresTime( 10 );
        assertNotNull( expiresTime );
        assertEquals( 11, expiresTime.getEpochSecond() );
    }

    @Test
    void notCacheable_public()
    {
        final HttpServletResponse response = mock( HttpServletResponse.class );
        when( response.getHeaders( "Cache-Control" ) ).thenReturn( List.of( "public" ) );
        assertFalse( ResponseFreshness.build( response ).notCacheable() );
    }

    @Test
    void notCacheable_private()
    {
        final HttpServletResponse response = mock( HttpServletResponse.class );
        when( response.getHeaders( "Cache-Control" ) ).thenReturn( List.of( "private", "public" ) );
        assertTrue( ResponseFreshness.build( response ).notCacheable() );
    }

    @Test
    void notCacheable_noCache()
    {
        final HttpServletResponse response = mock( HttpServletResponse.class );
        when( response.getHeaders( "Cache-Control" ) ).thenReturn( List.of( "no-cache" ) );
        assertTrue( ResponseFreshness.build( response ).notCacheable() );
    }

    @Test
    void notCacheable_noStore()
    {
        final HttpServletResponse response = mock( HttpServletResponse.class );
        when( response.getHeaders( "Cache-Control" ) ).thenReturn( List.of( "no-store" ) );
        assertTrue( ResponseFreshness.build( response ).notCacheable() );

    }
}
