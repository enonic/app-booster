package com.enonic.app.booster;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreconditionsTest
{
    @Mock
    HttpServletRequest request;

    @Test
    void preconditions()
    {

        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getRequestURI() ).thenReturn( "/site/repo/master" );
        when( request.getHeader( "Authorization" ) ).thenReturn( null );
        when( request.isRequestedSessionIdValid() ).thenReturn( false );

        Preconditions preconditions = new Preconditions();
        assertFalse( preconditions.check( request ).bypass() );
    }

    @Test
    void preconditions_extra()
    {
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getRequestURI() ).thenReturn( "/site/repo/master" );
        when( request.getHeader( "Authorization" ) ).thenReturn( null );
        when( request.isRequestedSessionIdValid() ).thenReturn( false );

        Preconditions preconditions = new Preconditions( request -> Preconditions.Result.PROCEED );
        assertFalse( preconditions.check( request ).bypass() );
    }

    @Test
    void preconditions_extra_bypass()
    {
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getRequestURI() ).thenReturn( "/site/repo/master" );
        when( request.getHeader( "Authorization" ) ).thenReturn( null );
        when( request.isRequestedSessionIdValid() ).thenReturn( false );

        Preconditions preconditions = new Preconditions( request -> Preconditions.Result.SILENT_BYPASS );
        assertTrue( preconditions.check( request ).bypass() );
    }

    @Test
    void preconditions_http()
    {
        when( request.getScheme() ).thenReturn( "http" );
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getRequestURI() ).thenReturn( "/site/repo/master" );
        when( request.getHeader( "Authorization" ) ).thenReturn( null );
        when( request.isRequestedSessionIdValid() ).thenReturn( false );

        Preconditions preconditions = new Preconditions();
        assertFalse( preconditions.check( request ).bypass() );
    }

    @Test
    void preconditions_head()
    {
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "HEAD" );
        when( request.getRequestURI() ).thenReturn( "/site/repo/master" );
        when( request.getHeader( "Authorization" ) ).thenReturn( null );
        when( request.isRequestedSessionIdValid() ).thenReturn( false );

        Preconditions preconditions = new Preconditions();
        assertFalse( preconditions.check( request ).bypass() );
    }

    @Test
    void preconditions_wss()
    {
        when( request.getScheme() ).thenReturn( "wss" );

        Preconditions preconditions = new Preconditions();
        assertTrue( preconditions.check( request ).bypass() );
    }

    @Test
    void preconditions_post()
    {
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "POST" );

        Preconditions preconditions = new Preconditions();
        assertTrue( preconditions.check( request ).bypass() );
    }

    @Test
    void preconditions_session()
    {
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getRequestURI() ).thenReturn( "/site/repo/master" );
        when( request.getHeader( "Authorization" ) ).thenReturn( null );
        when( request.isRequestedSessionIdValid() ).thenReturn( true );

        Preconditions preconditions = new Preconditions();
        assertTrue( preconditions.check( request ).bypass() );
    }

    @Test
    void preconditions_authorization()
    {
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getRequestURI() ).thenReturn( "/site/repo/master" );
        when( request.getHeader( "Authorization" ) ).thenReturn( "Bearer: ..." );

        Preconditions preconditions = new Preconditions();
        assertTrue( preconditions.check( request ).bypass() );
    }

    @Test
    void preconditions_service()
    {
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getRequestURI() ).thenReturn( "/site/repo/master/_/image" );

        Preconditions preconditions = new Preconditions();
        assertTrue( preconditions.check( request ).bypass() );
    }

    @Test
    void no_license()
    {
        final Preconditions.LicensePrecondition noLicense = new Preconditions.LicensePrecondition( () -> false );
        final Preconditions.Result result = noLicense.check( null );
        assertTrue( result.bypass() );
        assertEquals( "LICENSE",result.detail() );
    }

    @Test
    void with_license()
    {
        final Preconditions.LicensePrecondition noLicense = new Preconditions.LicensePrecondition( () -> true );
        assertFalse( noLicense.check( null ).bypass() );
    }
}
