package com.enonic.app.booster;

import javax.servlet.http.HttpServletRequest;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.enonic.xp.app.ApplicationKey;
import com.enonic.xp.portal.PortalRequest;
import com.enonic.xp.project.Project;
import com.enonic.xp.project.ProjectName;
import com.enonic.xp.project.ProjectService;
import com.enonic.xp.site.Site;
import com.enonic.xp.site.SiteConfig;
import com.enonic.xp.site.SiteConfigs;

@Component(service = SiteConfigService.class)
public class SiteConfigService
{
    private static final ApplicationKey APPLICATION_KEY = ApplicationKey.from( "com.enonic.app.booster" );

    private final ProjectService projectService;

    @Activate
    public SiteConfigService( @Reference final ProjectService projectService )
    {
        this.projectService = projectService;
    }

    public SiteConfig execute( final HttpServletRequest request )
    {
        final PortalRequest portalRequest = (PortalRequest) request.getAttribute( PortalRequest.class.getName() );

        if ( portalRequest == null )
        {
            return null;
        }
        final Site site = portalRequest.getSite();
        final SiteConfigs siteConfigs;
        if ( site != null )
        {
            siteConfigs = site.getSiteConfigs();
        }
        else
        {
            final ProjectName projectName = ProjectName.from( portalRequest.getRepositoryId() );
            if (projectName == null )
            {
                // repository is not a cms project
                return null;
            }
            final Project project = this.projectService.get( projectName );
            if ( project != null )
            {
                siteConfigs = project.getSiteConfigs();
            }
            else
            {
                return null;
            }
        }

        return siteConfigs.get( APPLICATION_KEY );
    }
}
