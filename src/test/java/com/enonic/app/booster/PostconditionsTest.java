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
class PostconditionsTest
{
    @Mock
    HttpServletRequest request;

    @Mock
    CachingResponse response;

    @Test
    public void postconditions()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getCachedHeaders() ).thenReturn( Map.of() );
        when( response.getContentType() ).thenReturn( "text/html" );

        Postconditions postconditions = new Postconditions( ( request, response ) -> true );
        assertTrue( postconditions.check( request, response ) );
    }

    @Test
    public void postconditions_head()
    {
        when( request.getMethod() ).thenReturn( "HEAD" );

        Postconditions postconditions = new Postconditions();
        assertFalse( postconditions.check( request, response ) );
    }

    @Test
    public void postconditions_session()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( mock( HttpSession.class ) );

        Postconditions postconditions = new Postconditions();
        assertFalse( postconditions.check( request, response ) );
    }

    @Test
    public void postconditions_500()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 500 );

        Postconditions postconditions = new Postconditions();
        assertFalse( postconditions.check( request, response ) );
    }

    @Test
    public void postconditions_content_type_non_html()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getContentType() ).thenReturn( "application/octet-stream" );

        Postconditions postconditions = new Postconditions();
        assertFalse( postconditions.check( request, response ) );
    }

    @Test
    public void postconditions_vary()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getContentType() ).thenReturn( "text/html" );
        when( response.getCachedHeaders() ).thenReturn( Map.of( "vary", List.of( "Accept" ) ) );

        Postconditions postconditions = new Postconditions();
        assertFalse( postconditions.check( request, response ) );
    }

    @Test
    public void postconditions_content_encoding()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getContentType() ).thenReturn( "text/html" );
        when( response.getCachedHeaders() ).thenReturn( Map.of( "content-encoding", List.of( "br" ) ) );

        Postconditions postconditions = new Postconditions();
        assertFalse( postconditions.check( request, response ) );
    }


    @Test
    public void postconditions_expires()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getContentType() ).thenReturn( "text/html" );
        when( response.containsHeader( "Expires" ) ).thenReturn( true );

        Postconditions postconditions = new Postconditions( ( request, response ) -> true );
        assertFalse( postconditions.check( request, response ) );
    }

    @Test
    public void postconditions_cache_control_private()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getContentType() ).thenReturn( "text/html" );
        when( response.getHeaders( "Cache-Control" ) ).thenReturn( List.of( "private" ) );

        Postconditions postconditions = new Postconditions();
        assertFalse( postconditions.check( request, response ) );
    }

    @Test
    public void postconditions_cache_control_no_store()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getContentType() ).thenReturn( "text/html" );
        when( response.getHeaders( "Cache-Control" ) ).thenReturn( List.of( "no-store" ) );

        Postconditions postconditions = new Postconditions();
        assertFalse( postconditions.check( request, response ) );
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

        Postconditions postconditions = new Postconditions( custom );
        assertFalse( postconditions.check( request, response ) );
        verify( custom ).apply( request, response );
    }

    @Test
    public void postconditions_portal()
    {
        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "master" ) );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );
        assertTrue( new Postconditions.PortalRequestConditions().check( request, response ) );
    }

    @Test
    public void postconditions_portal_no_request()
    {
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( null );
        assertFalse( new Postconditions.PortalRequestConditions().check( request, response ) );
    }

    @Test
    public void postconditions_portal_preview()
    {
        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.PREVIEW );
        portalRequest.setBranch( Branch.from( "master" ) );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );

        assertFalse( new Postconditions.PortalRequestConditions().check( request, response ) );
    }

    @Test
    public void postconditions_portal_draft()
    {
        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "draft" ) );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );

        assertFalse( new Postconditions.PortalRequestConditions().check( request, response ) );
    }

    @Test
    public void postconditions_siteConfig()
    {
        final SiteConfig siteConfig =
            SiteConfig.create().config( new PropertyTree() ).application( ApplicationKey.from( "com.enonic.app.booster" ) ).build();
        SiteConfigService siteConfigService = mock();
        when( siteConfigService.execute( request ) ).thenReturn( siteConfig );

        final BoosterConfigParsed config = new BoosterConfigParsed( 0, Set.of(), false, false, 1, Set.of(), null );

        final Postconditions.SiteConfigConditions siteConfigConditions =
            new Postconditions.SiteConfigConditions( config, siteConfigService );
        assertTrue( siteConfigConditions.check( request, response ) );
    }

    @Test
    public void postconditions_siteConfig_disabled()
    {
        final PropertyTree data = new PropertyTree();
        data.setBoolean( "disable", true );
        final SiteConfig siteConfig =
            SiteConfig.create().config( data ).application( ApplicationKey.from( "com.enonic.app.booster" ) ).build();
        SiteConfigService siteConfigService = mock();
        when( siteConfigService.execute( request ) ).thenReturn( siteConfig );

        final BoosterConfigParsed config = new BoosterConfigParsed( 0, Set.of(), false, false, 1, Set.of(), null );

        final Postconditions.SiteConfigConditions siteConfigConditions =
            new Postconditions.SiteConfigConditions( config, siteConfigService );
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
        SiteConfigService siteConfigService = mock();
        when( siteConfigService.execute( request ) ).thenReturn( siteConfig );

        final BoosterConfigParsed config = new BoosterConfigParsed( 0, Set.of(), false, false, 1, Set.of(), null );

        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "draft" ) );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );
        portalRequest.setSite( Site.create().path( "/site" ).build() );
        portalRequest.setContentPath( ContentPath.create().addElement( "site" ).addElement( "b" ).build() );
        final Postconditions.SiteConfigConditions siteConfigConditions =
            new Postconditions.SiteConfigConditions( config, siteConfigService );
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
        SiteConfigService siteConfigService = mock();
        when( siteConfigService.execute( request ) ).thenReturn( siteConfig );

        final BoosterConfigParsed config = new BoosterConfigParsed( 0, Set.of(), false, false, 1, Set.of(), null );

        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "draft" ) );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );
        portalRequest.setSite( Site.create().path( "/site" ).build() );
        portalRequest.setContentPath( ContentPath.create().addElement( "site" ).addElement( "a" ).build() );
        final Postconditions.SiteConfigConditions siteConfigConditions =
            new Postconditions.SiteConfigConditions( config, siteConfigService );
        assertTrue( siteConfigConditions.check( request, response ) );
    }
}
