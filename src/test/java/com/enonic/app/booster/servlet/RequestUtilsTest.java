package com.enonic.app.booster.servlet;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestUtilsTest
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
        final RequestURL requestURL = RequestUtils.buildRequestURL( request, Set.of( "a" ) );

        assertEquals( "https://example.com./path?b=v3&b=v4", requestURL.url() );
        assertEquals( "example.com.", requestURL.domain() );
        assertEquals( "/path", requestURL.path() );
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
        final RequestURL requestURL = RequestUtils.buildRequestURL( request, Set.of() );

        assertEquals( "http://example.com:8080/", requestURL.url() );
        assertEquals( "example.com", requestURL.domain() );
        assertEquals( "/", requestURL.path() );
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
        final RequestURL requestURL = RequestUtils.buildRequestURL( request, Set.of() );

        assertEquals( "http://example.com:8443/", requestURL.url() );
        assertEquals( "example.com", requestURL.domain() );
        assertEquals( "/", requestURL.path() );
    }

    @Test
    void normalizedQueryParams()
    {

        final String result =
            RequestUtils.normalizedQueryParams( Map.of( "b", new String[]{"v 4", "v 3"}, "a ", new String[]{"v 2", "v 1"} ), Set.of() );
        assertEquals( "a+=v+2&a+=v+1&b=v+4&b=v+3", result );
    }

    @Test
    void normalizedQueryParams_exclude()
    {
        final String result =
            RequestUtils.normalizedQueryParams( Map.of( "b", new String[]{"v4", "v3"}, "a", new String[]{"v2", "v1"} ), Set.of( "a" ) );
        assertEquals( "b=v4&b=v3", result );
    }

    @Test
    void normalizedQueryParams_empty()
    {
        final String result = RequestUtils.normalizedQueryParams( Map.of(), Set.of() );
        assertEquals( "", result );
    }

    @Test
    void acceptEncoding_empty_is_unspecified()
    {
        HttpServletRequest request = mock( HttpServletRequest.class );
        when( request.getHeaders( "Accept-Encoding" ) ).thenReturn( Collections.enumeration( List.of() ) );
        final RequestUtils.AcceptEncoding acceptEncoding = RequestUtils.acceptEncoding( request );

        assertEquals( RequestUtils.AcceptEncoding.UNSPECIFIED, acceptEncoding );
    }

    @Test
    void acceptEncoding_null_is_unspecified()
    {
        HttpServletRequest request = mock( HttpServletRequest.class );
        when( request.getHeaders( "Accept-Encoding" ) ).thenReturn( null );
        final RequestUtils.AcceptEncoding acceptEncoding = RequestUtils.acceptEncoding( request );

        assertEquals( RequestUtils.AcceptEncoding.UNSPECIFIED, acceptEncoding );
    }

    @Test
    void acceptEncoding_br_preferred()
    {
        HttpServletRequest request = mock( HttpServletRequest.class );
        when( request.getHeaders( "Accept-Encoding" ) ).thenReturn( Collections.enumeration( List.of("gzip", "br") ) );
        final RequestUtils.AcceptEncoding acceptEncoding = RequestUtils.acceptEncoding( request );

        assertEquals( RequestUtils.AcceptEncoding.BROTLI, acceptEncoding );
    }

    @Test
    void acceptEncoding_gzip()
    {
        HttpServletRequest request = mock( HttpServletRequest.class );
        when( request.getHeaders( "Accept-Encoding" ) ).thenReturn( Collections.enumeration( List.of("br;q=0, gzip") ) );
        final RequestUtils.AcceptEncoding acceptEncoding = RequestUtils.acceptEncoding( request );

        assertEquals( RequestUtils.AcceptEncoding.GZIP, acceptEncoding );
    }

    @Test
    void acceptEncoding_prefer_none_unspecified()
    {
        HttpServletRequest request = mock( HttpServletRequest.class );
        when( request.getHeaders( "Accept-Encoding" ) ).thenReturn( Collections.enumeration( List.of("br;q=0, gzip;q=0") ) );
        final RequestUtils.AcceptEncoding acceptEncoding = RequestUtils.acceptEncoding( request );

        assertEquals( RequestUtils.AcceptEncoding.UNSPECIFIED, acceptEncoding );
    }

}
