package com.enonic.app.booster;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.enonic.xp.app.ApplicationKey;
import com.enonic.xp.portal.PortalRequest;
import com.enonic.xp.project.Project;
import com.enonic.xp.project.ProjectName;
import com.enonic.xp.project.ProjectService;
import com.enonic.xp.repository.RepositoryId;
import com.enonic.xp.site.Site;
import com.enonic.xp.site.SiteConfig;
import com.enonic.xp.site.SiteConfigs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteConfigServiceTest
{

    @Mock
    ProjectService projectService;

    @Mock
    HttpServletRequest request;

    @InjectMocks
    SiteConfigService siteConfigService;

    private final static ApplicationKey APPLICATION_KEY = ApplicationKey.from( "com.enonic.app.booster" );

    @BeforeEach
    public void setUp()
    {
        // Setup common mocking behavior here, if any
    }

    @Test
    public void whenPortalRequestIsNull_thenReturnNull()
    {
        // Mocking behavior: when request.getAttribute returns null
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( null );

        // Execute the method to test
        SiteConfig result = siteConfigService.execute( request );

        // Verify the outcome
        assertNull( result, "Expected result to be null when PortalRequest is null." );
    }

    @Test
    public void whenSiteIsNullAndProjectDoesNotExist_thenReturnNull()
    {
        // Setup for this specific scenario
        PortalRequest portalRequest = mock( PortalRequest.class );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );
        when( portalRequest.getRepositoryId() ).thenReturn( RepositoryId.from( "com.enonic.cms.repo" ) );

        ProjectName projectName = ProjectName.from( "repo" );
        when( projectService.get( projectName ) ).thenReturn( null );

        // Execute the method to test
        SiteConfig result = siteConfigService.execute( request );

        // Verify the outcome
        assertNull( result, "Expected result to be null when site is null and project does not exist." );
    }


    @Test
    public void whenSiteIsPresent_thenRetrieveSiteConfig()
    {
        PortalRequest portalRequest = mock( PortalRequest.class );
        Site site = mock( Site.class );
        SiteConfigs siteConfigs = mock( SiteConfigs.class );
        SiteConfig expectedSiteConfig = mock( SiteConfig.class );

        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );
        when( portalRequest.getSite() ).thenReturn( site );
        when( site.getSiteConfigs() ).thenReturn( siteConfigs );
        when( siteConfigs.get( APPLICATION_KEY ) ).thenReturn( expectedSiteConfig );

        SiteConfig result = siteConfigService.execute( request );

        assertNotNull( result, "Expected non-null SiteConfig when site is present." );
        assertEquals( expectedSiteConfig, result, "Expected retrieved SiteConfig to match the mock." );
    }

    @Test
    public void whenSiteIsNullAndRepositoryIsNotACmsProject_thenReturnNull()
    {
        PortalRequest portalRequest = mock( PortalRequest.class );

        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );
        when( portalRequest.getRepositoryId() ).thenReturn( RepositoryId.from( "com.enonic.cms.repo" ) );
        when( portalRequest.getSite() ).thenReturn( null );
        // Assuming the logic for determining a CMS project is encapsulated within the service method.
        // This might need adjustment based on actual implementation.

        SiteConfig result = siteConfigService.execute( request );

        assertNull( result, "Expected result to be null when repository is not a CMS project." );
    }

    @Test
    public void whenSiteIsNullRepositoryIsACmsProjectAndProjectDoesNotExist_thenReturnNull()
    {
        PortalRequest portalRequest = mock( PortalRequest.class );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );
        when( portalRequest.getRepositoryId() ).thenReturn( RepositoryId.from( "com.enonic.cms.repo" ) );
        when( portalRequest.getSite() ).thenReturn( null );
        ProjectName projectName = ProjectName.from( "repo" );

        when( projectService.get( projectName ) ).thenReturn( null );

        SiteConfig result = siteConfigService.execute( request );

        assertNull( result, "Expected result to be null when project does not exist." );
    }

    @Test
    public void whenSiteIsNullRepositoryIsACmsProjectAndProjectExists_thenRetrieveSiteConfig()
    {
        PortalRequest portalRequest = mock( PortalRequest.class );
        Project project = mock( Project.class );
        SiteConfigs siteConfigs = mock( SiteConfigs.class );
        SiteConfig expectedSiteConfig = mock( SiteConfig.class );

        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );
        when( portalRequest.getRepositoryId() ).thenReturn( RepositoryId.from( "com.enonic.cms.repo" ) );
        when( portalRequest.getSite() ).thenReturn( null );
        ProjectName projectName = ProjectName.from( "repo" );
        when( projectService.get( projectName ) ).thenReturn( project );
        when( project.getSiteConfigs() ).thenReturn( siteConfigs );
        when( siteConfigs.get( APPLICATION_KEY ) ).thenReturn( expectedSiteConfig );

        SiteConfig result = siteConfigService.execute( request );

        assertNotNull( result, "Expected non-null SiteConfig when project exists." );
        assertEquals( expectedSiteConfig, result, "Expected retrieved SiteConfig to match the mock." );
    }

    @Test
    public void whenRepositoryIsNotACmsProjectDueToNoProjectFound_thenReturnNull()
    {
        PortalRequest portalRequest = mock( PortalRequest.class );
        when( request.getAttribute( PortalRequest.class.getName() ) ).thenReturn( portalRequest );
        when( portalRequest.getRepositoryId() ).thenReturn( RepositoryId.from( "com.enonic.noncms.repo" )  );
        when( portalRequest.getSite() ).thenReturn( null );

        SiteConfig result = siteConfigService.execute( request );

        assertNull( result,
                    "Expected result to be null when no corresponding project is found, implying repository is not a CMS project." );
    }
}
