package com.enonic.app.booster;

import java.time.Instant;
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
import com.enonic.app.booster.servlet.RequestAttributes;
import com.enonic.app.booster.servlet.ResponseFreshness;
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
class StoreConditionsTest
{
    @Mock
    HttpServletRequest request;

    @Mock
    CachingResponse response;

    @Test
    public void storeConditions()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getCachedHeaders() ).thenReturn( Map.of() );
        when( response.getFreshness() ).thenReturn( freshFreshness() );

        StoreConditions storeConditions = new StoreConditions( ( request, response ) -> true );
        assertTrue( storeConditions.check( request, response ) );
    }

    @Test
    public void storeConditions_head()
    {
        when( request.getMethod() ).thenReturn( "HEAD" );

        StoreConditions storeConditions = new StoreConditions();
        assertFalse( storeConditions.check( request, response ) );
    }

    @Test
    public void storeConditions_session()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( mock( HttpSession.class ) );

        StoreConditions storeConditions = new StoreConditions();
        assertFalse( storeConditions.check( request, response ) );
    }

    @Test
    public void storeConditions_500()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 500 );

        StoreConditions storeConditions = new StoreConditions();
        assertFalse( storeConditions.check( request, response ) );
    }


    @Test
    public void storeConditions_vary()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getCachedHeaders() ).thenReturn( Map.of( "vary", List.of( "Accept" ) ) );

        StoreConditions storeConditions = new StoreConditions();
        assertFalse( storeConditions.check( request, response ) );
    }

    @Test
    public void storeConditions_content_encoding()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getCachedHeaders() ).thenReturn( Map.of( "content-encoding", List.of( "br" ) ) );

        StoreConditions storeConditions = new StoreConditions();
        assertFalse( storeConditions.check( request, response ) );
    }


    @Test
    public void storeConditions_expires()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.containsHeader( "Expires" ) ).thenReturn( true );

        StoreConditions storeConditions = new StoreConditions( ( request, response ) -> true );
        assertFalse( storeConditions.check( request, response ) );
    }

    @Test
    public void storeConditions_cache_control_private()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getFreshness() ).thenReturn( new ResponseFreshness( null, null, false, true, false, Instant.now(), null ) );

        StoreConditions storeConditions = new StoreConditions();
        assertFalse( storeConditions.check( request, response ) );
    }

    @Test
    public void storeConditions_custom()
    {
        when( request.getMethod() ).thenReturn( "GET" );
        when( request.getSession( false ) ).thenReturn( null );
        when( response.getStatus() ).thenReturn( 200 );
        when( response.getFreshness() ).thenReturn( freshFreshness() );

        final BiFunction<HttpServletRequest, CachingResponse, Boolean> custom = mock();
        when( custom.apply( any(), any() ) ).thenReturn( false );

        StoreConditions storeConditions = new StoreConditions( custom );
        assertFalse( storeConditions.check( request, response ) );
        verify( custom ).apply( request, response );
    }

    @Test
    public void storeConditions_portal()
    {
        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "master" ) );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );
        assertTrue( new StoreConditions.PortalRequestConditions().check( request, response ) );
    }

    @Test
    public void storeConditions_portal_no_request()
    {
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( null );
        assertFalse( new StoreConditions.PortalRequestConditions().check( request, response ) );
    }

    @Test
    public void storeConditions_portal_preview()
    {
        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.PREVIEW );
        portalRequest.setBranch( Branch.from( "master" ) );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );

        assertFalse( new StoreConditions.PortalRequestConditions().check( request, response ) );
    }

    @Test
    public void storeConditions_portal_draft()
    {
        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "draft" ) );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );

        assertFalse( new StoreConditions.PortalRequestConditions().check( request, response ) );
    }

    @Test
    public void storeConditions_siteConfig()
    {
        final SiteConfig siteConfig =
            SiteConfig.create().config( new PropertyTree() ).application( ApplicationKey.from( "com.enonic.app.booster" ) ).build();

        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "draft" ) );
        portalRequest.setContentPath( ContentPath.from( "/site" ) );
        portalRequest.setSite( Site.create().path( "/site" ).siteConfigs( SiteConfigs.from( siteConfig ) ).build() );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );

        final StoreConditions.SiteConfigConditions siteConfigConditions = new StoreConditions.SiteConfigConditions( Set.of() );
        assertTrue( siteConfigConditions.check( request, response ) );
    }

    @Test
    public void storeConditions_siteConfig_disabled()
    {
        final PropertyTree data = new PropertyTree();
        data.setBoolean( "disable", true );
        final SiteConfig siteConfig =
            SiteConfig.create().config( data ).application( ApplicationKey.from( "com.enonic.app.booster" ) ).build();

        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "master" ) );
        portalRequest.setSite( Site.create().path( "/site" ).siteConfigs( SiteConfigs.from( siteConfig ) ).build() );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );

        final StoreConditions.SiteConfigConditions siteConfigConditions = new StoreConditions.SiteConfigConditions( Set.of() );
        assertFalse( siteConfigConditions.check( request, response ) );
    }

    @Test
    public void storeConditions_siteConfig_0ttl()
    {
        final PropertyTree data = new PropertyTree();
        data.setString( "defaultTTL", "0" );
        final SiteConfig siteConfig =
            SiteConfig.create().config( data ).application( ApplicationKey.from( "com.enonic.app.booster" ) ).build();

        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "master" ) );
        portalRequest.setSite( Site.create().path( "/site" ).siteConfigs( SiteConfigs.from( siteConfig ) ).build() );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );

        final StoreConditions.SiteConfigConditions siteConfigConditions = new StoreConditions.SiteConfigConditions( Set.of() );
        assertFalse( siteConfigConditions.check( request, response ) );
    }

    @Test
    public void storeConditions_siteConfig_0ttl_component_override()
    {
        final PropertyTree data = new PropertyTree();
        data.setString( "componentTTL", "0" );
        final SiteConfig siteConfig =
            SiteConfig.create().config( data ).application( ApplicationKey.from( "com.enonic.app.booster" ) ).build();

        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "master" ) );
        portalRequest.setSite( Site.create().path( "/site" ).siteConfigs( SiteConfigs.from( siteConfig ) ).build() );
        when( request.getRequestURI() ).thenReturn( "/site/_/component/main/0" );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );

        final StoreConditions.SiteConfigConditions siteConfigConditions = new StoreConditions.SiteConfigConditions( Set.of() );
        assertFalse( siteConfigConditions.check( request, response ) );
    }

    @Test
    public void storeConditions_siteConfig_pattern_does_not_match()
    {
        final PropertyTree data = new PropertyTree();

        final PropertySet patterns = data.addSet( "patterns" );
        patterns.addString( "pattern", "/a/?.*" );

        final SiteConfig siteConfig =
            SiteConfig.create().config( data ).application( ApplicationKey.from( "com.enonic.app.booster" ) ).build();

        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "draft" ) );
        portalRequest.setSite( Site.create().path( "/site" ).siteConfigs( SiteConfigs.from( siteConfig ) ).build() );
        portalRequest.setContentPath( ContentPath.create().addElement( "site" ).addElement( "b" ).build() );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );

        final StoreConditions.SiteConfigConditions siteConfigConditions = new StoreConditions.SiteConfigConditions( Set.of() );
        assertFalse( siteConfigConditions.check( request, response ) );
    }

    @Test
    public void storeConditions_siteConfig_pattern_matches()
    {
        final PropertyTree data = new PropertyTree();

        final PropertySet patterns = data.addSet( "patterns" );
        patterns.addString( "pattern", "/a/?.*" );

        final SiteConfig siteConfig =
            SiteConfig.create().config( data ).application( ApplicationKey.from( "com.enonic.app.booster" ) ).build();

        final PortalRequest portalRequest = new PortalRequest();
        portalRequest.setMode( RenderMode.LIVE );
        portalRequest.setBranch( Branch.from( "draft" ) );
        portalRequest.setSite( Site.create().path( "/site" ).siteConfigs( SiteConfigs.from( siteConfig ) ).build() );
        portalRequest.setContentPath( ContentPath.create().addElement( "site" ).addElement( "a" ).build() );

        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );

        final StoreConditions.SiteConfigConditions siteConfigConditions = new StoreConditions.SiteConfigConditions( Set.of() );
        assertTrue( siteConfigConditions.check( request, response ) );
    }

    @Test
    void storeConditions_contentType_missing()
    {
        when( response.getContentType() ).thenReturn( null );

        final BoosterConfigParsed config =
            BoosterConfigParsed.parse( mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() ) );
        final var storeConditions = new StoreConditions.ContentTypePreconditions( Set.of( "text/html" ) );

        assertFalse( storeConditions.check( request, response ) );
    }

    @Test
    void storeConditions_contentType_unsupported()
    {
        when( response.getContentType() ).thenReturn( "application/octet-stream" );

        final var storeConditions = new StoreConditions.ContentTypePreconditions( Set.of( "text/html" ) );

        assertFalse( storeConditions.check( request, response ) );
    }

    @Test
    void storeConditions_contentType_supported()
    {
        when( response.getContentType() ).thenReturn( "text/html" );

        final var storeConditions = new StoreConditions.ContentTypePreconditions( Set.of( "text/html" ) );

        assertTrue( storeConditions.check( request, response ) );
    }

    @Test
    void storeConditions_userAgent_facebookBot()
    {
        when( request.getHeader( "User-Agent" ) ).thenReturn( "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)" );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( null );

        final var storeConditions = new StoreConditions.UserAgentConditions( Set.of( "facebookexternalhit", "linkedinbot", "twitterbot" ) );

        assertFalse( storeConditions.check( request, response ) );
    }

    @Test
    void storeConditions_userAgent_linkedInBot()
    {
        when( request.getHeader( "User-Agent" ) ).thenReturn( "LinkedInBot/1.0 (compatible; Mozilla/5.0; Apache-HttpClient +http://www.linkedin.com)" );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( null );

        final var storeConditions = new StoreConditions.UserAgentConditions( Set.of( "facebookexternalhit", "linkedinbot", "twitterbot" ) );

        assertFalse( storeConditions.check( request, response ) );
    }

    @Test
    void storeConditions_userAgent_normalBrowser()
    {
        when( request.getHeader( "User-Agent" ) ).thenReturn( "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( null );

        final var storeConditions = new StoreConditions.UserAgentConditions( Set.of( "facebookexternalhit", "linkedinbot", "twitterbot" ) );

        assertTrue( storeConditions.check( request, response ) );
    }

    @Test
    void storeConditions_userAgent_null()
    {
        when( request.getHeader( "User-Agent" ) ).thenReturn( null );

        final var storeConditions = new StoreConditions.UserAgentConditions( Set.of( "facebookexternalhit", "linkedinbot", "twitterbot" ) );

        assertTrue( storeConditions.check( request, response ) );
    }

    @Test
    void storeConditions_userAgent_empty()
    {
        when( request.getHeader( "User-Agent" ) ).thenReturn( "" );

        final var storeConditions = new StoreConditions.UserAgentConditions( Set.of( "facebookexternalhit", "linkedinbot", "twitterbot" ) );

        assertTrue( storeConditions.check( request, response ) );
    }

    @Test
    void storeConditions_userAgent_caseInsensitive()
    {
        when( request.getHeader( "User-Agent" ) ).thenReturn( "FACEBOOKEXTERNALHIT/1.1" );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( null );

        final var storeConditions = new StoreConditions.UserAgentConditions( Set.of( "facebookexternalhit", "linkedinbot", "twitterbot" ) );

        assertFalse( storeConditions.check( request, response ) );
    }

    @Test
    void storeConditions_userAgent_usesEmptySiteConfig()
    {
        when( request.getHeader( "User-Agent" ) ).thenReturn( "facebookexternalhit/1.1" );

        final PortalRequest portalRequest = new PortalRequest();
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );

        final var storeConditions = new StoreConditions.UserAgentConditions( Set.of( "facebookexternalhit", "linkedinbot", "twitterbot" ) );

        // Should fall back to default exclude list when site config is null or empty
        assertFalse( storeConditions.check( request, response ) );
    }

    private static ResponseFreshness freshFreshness()
    {
        return new ResponseFreshness( null, null, false, false, false, Instant.now(), null );
    }
}
