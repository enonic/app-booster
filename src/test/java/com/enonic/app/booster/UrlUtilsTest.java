package com.enonic.app.booster;

import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UrlUtilsTest
{

    @Test
    void buildFullURL()
    {
        HttpServletRequest request = mock( HttpServletRequest.class );
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getServerName() ).thenReturn( "Example.com." );
        when( request.getServerPort() ).thenReturn( 443 );
        when( request.getRequestURI() ).thenReturn( "/Path" );
        when( request.getParameterMap() ).thenReturn( Map.of( "a", new String[]{"v1", "v2"}, "b", new String[]{"v3", "v4"} ) );
        final String fullURL = UrlUtils.buildFullURL( request, Set.of( "a" ) );

        assertEquals( "https://example.com/path?b=v3&b=v4", fullURL );
    }

    @Test
    void buildFullURL_http_custom_port()
    {
        HttpServletRequest request = mock( HttpServletRequest.class );
        when( request.getScheme() ).thenReturn( "http" );
        when( request.getServerName() ).thenReturn( "example.com" );
        when( request.getServerPort() ).thenReturn( 8080 );
        when( request.getRequestURI() ).thenReturn( "/" );
        when( request.getParameterMap() ).thenReturn( Map.of() );
        final String fullURL = UrlUtils.buildFullURL( request, Set.of() );

        assertEquals( "http://example.com:8080/", fullURL );
    }

    @Test
    void buildFullURL_https_custom_port()
    {
        HttpServletRequest request = mock( HttpServletRequest.class );
        when( request.getScheme() ).thenReturn( "http" );
        when( request.getServerName() ).thenReturn( "example.com" );
        when( request.getServerPort() ).thenReturn( 8443 );
        when( request.getRequestURI() ).thenReturn( "/" );
        when( request.getParameterMap() ).thenReturn( Map.of() );
        final String fullURL = UrlUtils.buildFullURL( request, Set.of() );

        assertEquals( "http://example.com:8443/", fullURL );
    }

    @Test
    void normalizedQueryParams()
    {

        final String result =
            UrlUtils.normalizedQueryParams( Map.of( "b", new String[]{"v 4", "v 3"}, "a ", new String[]{"v 2", "v 1"} ), Set.of() );
        assertEquals( "a+=v+2&a+=v+1&b=v+4&b=v+3", result );
    }

    @Test
    void normalizedQueryParams_exclude()
    {
        final String result =
            UrlUtils.normalizedQueryParams( Map.of( "b", new String[]{"v4", "v3"}, "a", new String[]{"v2", "v1"} ), Set.of( "a" ) );
        assertEquals( "b=v4&b=v3", result );
    }

    @Test
    void normalizedQueryParams_empty()
    {
        final String result = UrlUtils.normalizedQueryParams( Map.of(), Set.of() );
        assertEquals( "", result );
    }
}
