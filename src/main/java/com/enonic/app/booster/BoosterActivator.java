package com.enonic.app.booster;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enonic.xp.repository.CreateRepositoryParams;
import com.enonic.xp.repository.RepositoryService;

@Component(immediate = true)
public class BoosterActivator
{
    private static final Logger LOG = LoggerFactory.getLogger( BoosterRequestFilter.class );

    @Activate
    public BoosterActivator( @Reference final RepositoryService repositoryService )
    {
        try
        {
            LOG.debug( "Creating repository for booster app cache" );
            repositoryService.createRepository( CreateRepositoryParams.create().repositoryId( BoosterContext.REPOSITORY_ID ).build() );
        }
        catch ( Exception e )
        {
            LOG.debug( "Repository is probably already exists", e );
        }

    }
}
