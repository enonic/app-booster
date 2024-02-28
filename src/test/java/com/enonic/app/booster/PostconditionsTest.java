package com.enonic.app.booster;

import java.util.List;
import java.util.Map;
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
import com.enonic.xp.site.SiteConfigs;

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
    public void postconditions_vary()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
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

        final BoosterConfigParsed config =
            BoosterConfigParsed.parse( mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() ) );

        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "draft" ) );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );
        portalRequest.setSite( Site.create().path( "/site" ).siteConfigs( SiteConfigs.from( siteConfig ) ).build() );

        final Postconditions.SiteConfigConditions siteConfigConditions = new Postconditions.SiteConfigConditions( config );
        assertTrue( siteConfigConditions.check( request, response ) );
    }

    @Test
    public void postconditions_siteConfig_disabled()
    {
        final PropertyTree data = new PropertyTree();
        data.setBoolean( "disable", true );
        final SiteConfig siteConfig =
            SiteConfig.create().config( data ).application( ApplicationKey.from( "com.enonic.app.booster" ) ).build();

        final BoosterConfigParsed config =
            BoosterConfigParsed.parse( mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() ) );

        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "draft" ) );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );
        portalRequest.setSite( Site.create().path( "/site" ).siteConfigs( SiteConfigs.from( siteConfig ) ).build() );

        final Postconditions.SiteConfigConditions siteConfigConditions = new Postconditions.SiteConfigConditions( config );
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

        final BoosterConfigParsed config =
            BoosterConfigParsed.parse( mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() ) );

        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "draft" ) );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );
        portalRequest.setSite( Site.create().path( "/site" ).siteConfigs( SiteConfigs.from( siteConfig ) ).build() );
        portalRequest.setContentPath( ContentPath.create().addElement( "site" ).addElement( "b" ).build() );
        final Postconditions.SiteConfigConditions siteConfigConditions = new Postconditions.SiteConfigConditions( config );
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

        final BoosterConfigParsed config =
            BoosterConfigParsed.parse( mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() ) );

        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "draft" ) );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );
        portalRequest.setSite( Site.create().path( "/site" ).siteConfigs( SiteConfigs.from( siteConfig ) ).build() );
        portalRequest.setContentPath( ContentPath.create().addElement( "site" ).addElement( "a" ).build() );
        final Postconditions.SiteConfigConditions siteConfigConditions = new Postconditions.SiteConfigConditions( config );
        assertTrue( siteConfigConditions.check( request, response ) );
    }

    @Test
    void postconditions_contentType_missing()
    {
        when( response.getContentType() ).thenReturn( null );

        final BoosterConfigParsed config =
            BoosterConfigParsed.parse( mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() ) );
        final var postconditions = new Postconditions.ContentTypePreconditions( config );

        assertFalse( postconditions.check( request, response ) );
    }

    @Test
    void postconditions_contentType_unsupported()
    {
        when( response.getContentType() ).thenReturn( "application/octet-stream" );

        final BoosterConfigParsed config =
            BoosterConfigParsed.parse( mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() ) );
        final var postconditions = new Postconditions.ContentTypePreconditions( config );

        assertFalse( postconditions.check( request, response ) );
    }

    @Test
    void postconditions_contentType_supported()
    {
        when( response.getContentType() ).thenReturn( "text/html" );

        final BoosterConfigParsed config =
            BoosterConfigParsed.parse( mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() ) );
        final var postconditions = new Postconditions.ContentTypePreconditions( config );

        assertTrue( postconditions.check( request, response ) );
    }

}
