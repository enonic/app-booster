package com.enonic.app.booster;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.enonic.app.booster.servlet.CachingResponse;
import com.enonic.xp.app.ApplicationKey;
import com.enonic.xp.branch.Branch;
import com.enonic.xp.content.ContentPath;
import com.enonic.xp.data.PropertySet;
import com.enonic.xp.data.PropertyTree;
import com.enonic.xp.portal.PortalRequest;
import com.enonic.xp.portal.RenderMode;
import com.enonic.xp.site.Site;
import com.enonic.xp.site.SiteConfig;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConditionsTest
{
    @Mock
    HttpServletRequest request;

    @Mock
    CachingResponse response;

    @Test
    public void preconditions()
    {

        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.isRequestedSessionIdValid() ).thenReturn( false );
        when( request.getHeader( "Authorization" ) ).thenReturn( null );
        when( request.getRequestURI() ).thenReturn( "/site/repo/master" );

        Conditions conditions = new Conditions();
        assertTrue( conditions.checkPreconditions( request ) );
    }

    @Test
    public void preconditions_http()
    {
        when( request.getScheme() ).thenReturn( "http" );
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.isRequestedSessionIdValid() ).thenReturn( false );
        when( request.getHeader( "Authorization" ) ).thenReturn( null );
        when( request.getRequestURI() ).thenReturn( "/site/repo/master" );

        Conditions conditions = new Conditions();
        assertTrue( conditions.checkPreconditions( request ) );
    }

    @Test
    public void preconditions_head()
    {
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "HEAD" );
        when( request.isRequestedSessionIdValid() ).thenReturn( false );
        when( request.getHeader( "Authorization" ) ).thenReturn( null );
        when( request.getRequestURI() ).thenReturn( "/site/repo/master" );

        Conditions conditions = new Conditions();
        assertTrue( conditions.checkPreconditions( request ) );
    }

    @Test
    public void preconditions_wss()
    {
        when( request.getScheme() ).thenReturn( "wss" );

        Conditions conditions = new Conditions();
        assertFalse( conditions.checkPreconditions( request ) );
    }

    @Test
    public void preconditions_post()
    {
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "POST" );

        Conditions conditions = new Conditions();
        assertFalse( conditions.checkPreconditions( request ) );
    }

    @Test
    public void preconditions_session()
    {
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.isRequestedSessionIdValid() ).thenReturn( true );
        when( request.getHeader( "Authorization" ) ).thenReturn( null );

        Conditions conditions = new Conditions();
        assertFalse( conditions.checkPreconditions( request ) );
    }

    @Test
    public void preconditions_authorization()
    {
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getHeader( "Authorization" ) ).thenReturn( "Bearer: ..." );

        Conditions conditions = new Conditions();
        assertFalse( conditions.checkPreconditions( request ) );
    }

    @Test
    public void preconditions_service()
    {
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.isRequestedSessionIdValid() ).thenReturn( false );
        when( request.getHeader( "Authorization" ) ).thenReturn( null );
        when( request.getRequestURI() ).thenReturn( "/site/repo/master/_/image" );

        Conditions conditions = new Conditions();
        assertFalse( conditions.checkPreconditions( request ) );
    }

    @Test
    public void postconditions()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getCachedHeaders() ).thenReturn( Map.of() );
        when( response.getContentType() ).thenReturn( "text/html" );

        Conditions conditions = new Conditions( ( request, response ) -> true );
        assertTrue( conditions.checkPostconditions( request, response ) );
    }

    @Test
    public void postconditions_head()
    {
        when( request.getMethod() ).thenReturn( "HEAD" );

        Conditions conditions = new Conditions();
        assertFalse( conditions.checkPostconditions( request, response ) );
    }

    @Test
    public void postconditions_session()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( mock( HttpSession.class ) );

        Conditions conditions = new Conditions();
        assertFalse( conditions.checkPostconditions( request, response ) );
    }

    @Test
    public void postconditions_500()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 500 );

        Conditions conditions = new Conditions();
        assertFalse( conditions.checkPostconditions( request, response ) );
    }

    @Test
    public void postconditions_content_type_non_html()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getContentType() ).thenReturn( "application/octet-stream" );

        Conditions conditions = new Conditions();
        assertFalse( conditions.checkPostconditions( request, response ) );
    }

    @Test
    public void postconditions_vary()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getContentType() ).thenReturn( "text/html" );
        when( response.getCachedHeaders() ).thenReturn( Map.of( "vary", List.of( "Accept" ) ) );

        Conditions conditions = new Conditions();
        assertFalse( conditions.checkPostconditions( request, response ) );
    }

    @Test
    public void postconditions_content_encoding()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getContentType() ).thenReturn( "text/html" );
        when( response.getCachedHeaders() ).thenReturn( Map.of( "content-encoding", List.of( "br" ) ) );

        Conditions conditions = new Conditions();
        assertFalse( conditions.checkPostconditions( request, response ) );
    }


    @Test
    public void postconditions_expires()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getContentType() ).thenReturn( "text/html" );
        when( response.containsHeader( "Expires" ) ).thenReturn( true );

        Conditions conditions = new Conditions( ( request, response ) -> true );
        assertFalse( conditions.checkPostconditions( request, response ) );
    }

    @Test
    public void postconditions_cache_control_private()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getContentType() ).thenReturn( "text/html" );
        when( response.getHeaders( "Cache-Control" ) ).thenReturn( List.of( "private" ) );

        Conditions conditions = new Conditions();
        assertFalse( conditions.checkPostconditions( request, response ) );
    }

    @Test
    public void postconditions_cache_control_no_store()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getContentType() ).thenReturn( "text/html" );
        when( response.getHeaders( "Cache-Control" ) ).thenReturn( List.of( "no-store" ) );

        Conditions conditions = new Conditions();
        assertFalse( conditions.checkPostconditions( request, response ) );
    }


    @Test
    public void postconditions_custom()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getContentType() ).thenReturn( "text/html" );

        final BiFunction<HttpServletRequest, CachingResponse, Boolean> custom = mock();
        when( custom.apply( any(), any() ) ).thenReturn( false );

        Conditions conditions = new Conditions( custom );
        assertFalse( conditions.checkPostconditions( request, response ) );
        verify( custom ).apply( request, response );
    }

    @Test
    public void postconditions_portal()
    {
        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "master" ) );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );
        assertTrue( new Conditions.PortalRequestConditions().check( request, response ) );
    }

    @Test
    public void postconditions_portal_no_request()
    {
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( null );
        assertFalse( new Conditions.PortalRequestConditions().check( request, response ) );
    }

    @Test
    public void postconditions_portal_preview()
    {
        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.PREVIEW );
        portalRequest.setBranch( Branch.from( "master" ) );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );

        assertFalse( new Conditions.PortalRequestConditions().check( request, response ) );
    }

    @Test
    public void postconditions_portal_draft()
    {
        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "draft" ) );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );

        assertFalse( new Conditions.PortalRequestConditions().check( request, response ) );
    }

    @Test
    public void postconditions_siteConfig()
    {
        final SiteConfig siteConfig =
            SiteConfig.create().config( new PropertyTree() ).application( ApplicationKey.from( "com.enonic.app.booster" ) ).build();

        final BoosterConfigParsed config = new BoosterConfigParsed( 0, Set.of(), false, 1, Set.of(), null );

        final Conditions.SiteConfigConditions siteConfigConditions = new Conditions.SiteConfigConditions( config, () -> siteConfig );
        assertTrue( siteConfigConditions.check( request, response ) );
    }

    @Test
    public void postconditions_siteConfig_disabled()
    {
        final PropertyTree data = new PropertyTree();
        data.setBoolean( "disable", true );
        final SiteConfig siteConfig =
            SiteConfig.create().config( data ).application( ApplicationKey.from( "com.enonic.app.booster" ) ).build();

        final BoosterConfigParsed config = new BoosterConfigParsed( 0, Set.of(), false, 1, Set.of(), null );

        final Conditions.SiteConfigConditions siteConfigConditions = new Conditions.SiteConfigConditions( config, () -> siteConfig );
        assertFalse( siteConfigConditions.check( request, response ) );
    }

    @Test
    public void postconditions_siteConfig_pattern_does_not_match()
    {
        final PropertyTree data = new PropertyTree();

        final PropertySet patterns = data.addSet( "patterns" );
        patterns.addString( "pattern", "/a/?.*" );

        final SiteConfig siteConfig =
            SiteConfig.create().config( data ).application( ApplicationKey.from( "com.enonic.app.booster" ) ).build();

        final BoosterConfigParsed config = new BoosterConfigParsed( 0, Set.of(), false, 1, Set.of(), null );

        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "draft" ) );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );
        portalRequest.setSite( Site.create().path( "/site" ).build() );
        portalRequest.setContentPath( ContentPath.create().addElement( "site" ).addElement( "b" ).build() );
        final Conditions.SiteConfigConditions siteConfigConditions = new Conditions.SiteConfigConditions( config, () -> siteConfig );
        assertFalse( siteConfigConditions.check( request, response ) );
    }

    @Test
    public void postconditions_siteConfig_pattern_matches()
    {
        final PropertyTree data = new PropertyTree();

        final PropertySet patterns = data.addSet( "patterns" );
        patterns.addString( "pattern", "/a/?.*" );

        final SiteConfig siteConfig =
            SiteConfig.create().config( data ).application( ApplicationKey.from( "com.enonic.app.booster" ) ).build();

        final BoosterConfigParsed config = new BoosterConfigParsed( 0, Set.of(), false, 1, Set.of(), null );

        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "draft" ) );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );
        portalRequest.setSite( Site.create().path( "/site" ).build() );
        portalRequest.setContentPath( ContentPath.create().addElement( "site" ).addElement( "a" ).build() );
        final Conditions.SiteConfigConditions siteConfigConditions = new Conditions.SiteConfigConditions( config, () -> siteConfig );
        assertTrue( siteConfigConditions.check( request, response ) );
    }
}
