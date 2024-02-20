package com.enonic.app.booster;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreconditionsTest
{
    @Mock
    HttpServletRequest request;

    @Test
    public void preconditions()
    {

        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.isRequestedSessionIdValid() ).thenReturn( false );
        when( request.getHeader( "Authorization" ) ).thenReturn( null );
        when( request.getRequestURI() ).thenReturn( "/site/repo/master" );

        Preconditions preconditions = new Preconditions();
        assertTrue( preconditions.check( request ) );
    }

    @Test
    public void preconditions_http()
    {
        when( request.getScheme() ).thenReturn( "http" );
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.isRequestedSessionIdValid() ).thenReturn( false );
        when( request.getHeader( "Authorization" ) ).thenReturn( null );
        when( request.getRequestURI() ).thenReturn( "/site/repo/master" );

        Preconditions preconditions = new Preconditions();
        assertTrue( preconditions.check( request ) );
    }

    @Test
    public void preconditions_head()
    {
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "HEAD" );
        when( request.isRequestedSessionIdValid() ).thenReturn( false );
        when( request.getHeader( "Authorization" ) ).thenReturn( null );
        when( request.getRequestURI() ).thenReturn( "/site/repo/master" );

        Preconditions preconditions = new Preconditions();
        assertTrue( preconditions.check( request ) );
    }

    @Test
    public void preconditions_wss()
    {
        when( request.getScheme() ).thenReturn( "wss" );

        Preconditions preconditions = new Preconditions();
        assertFalse( preconditions.check( request ) );
    }

    @Test
    public void preconditions_post()
    {
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "POST" );

        Preconditions preconditions = new Preconditions();
        assertFalse( preconditions.check( request ) );
    }

    @Test
    public void preconditions_session()
    {
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.isRequestedSessionIdValid() ).thenReturn( true );
        when( request.getHeader( "Authorization" ) ).thenReturn( null );

        Preconditions preconditions = new Preconditions();
        assertFalse( preconditions.check( request ) );
    }

    @Test
    public void preconditions_authorization()
    {
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getHeader( "Authorization" ) ).thenReturn( "Bearer: ..." );

        Preconditions preconditions = new Preconditions();
        assertFalse( preconditions.check( request ) );
    }

    @Test
    public void preconditions_service()
    {
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.isRequestedSessionIdValid() ).thenReturn( false );
        when( request.getHeader( "Authorization" ) ).thenReturn( null );
        when( request.getRequestURI() ).thenReturn( "/site/repo/master/_/image" );

        Preconditions preconditions = new Preconditions();
        assertFalse( preconditions.check( request ) );
    }
}
