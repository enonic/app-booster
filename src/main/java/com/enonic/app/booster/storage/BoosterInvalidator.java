package com.enonic.app.booster.storage;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enonic.app.booster.BoosterConfig;
import com.enonic.app.booster.BoosterConfigParsed;
import com.enonic.xp.app.ApplicationKey;
import com.enonic.xp.event.Event;
import com.enonic.xp.event.EventListener;
import com.enonic.xp.index.IndexService;
import com.enonic.xp.project.ProjectConstants;
import com.enonic.xp.project.ProjectName;
import com.enonic.xp.repository.RepositoryId;

@Component(immediate = true, configurationPid = "com.enonic.app.booster")
public class BoosterInvalidator
    implements EventListener
{
    private static final Logger LOG = LoggerFactory.getLogger( BoosterInvalidator.class );

    private final ScheduledExecutorService executorService;

    private final IndexService indexService;

    private final BoosterTasksFacade boosterTasksFacade;

    private volatile BoosterConfigParsed config;

    private volatile Set<ProjectName> projects = new HashSet<>();

    @Activate
    public BoosterInvalidator( @Reference final BoosterTasksFacade boosterTasksFacade, @Reference final IndexService indexService )
    {
        this( boosterTasksFacade, indexService, Executors.newSingleThreadScheduledExecutor() );
    }

    BoosterInvalidator( final BoosterTasksFacade boosterTasksFacade, final IndexService indexService,
                        final ScheduledExecutorService executorService )
    {
        this.boosterTasksFacade = boosterTasksFacade;
        this.executorService = executorService;
        this.indexService = indexService;
        this.executorService.scheduleWithFixedDelay( () -> boosterTasksFacade.invalidateProjects( exchange() ), 10, 10, TimeUnit.SECONDS );
    }

    @Deactivate
    public void deactivate()
    {
        executorService.shutdownNow();
    }

    @Activate
    @Modified
    public void activate( final BoosterConfig config )
    {
        this.config = BoosterConfigParsed.parse( config );
    }

    @Override
    public void onEvent( final Event event )
    {
        final String type = event.getType();

        if ( type.equals( "application.cluster" ) && event.getData().get( "eventType" ).equals( "installed" ) )
        {
            if ( indexService.isMaster() )
            {
                if ( config.appsForceInvalidateOnInstall().contains( event.getData().get( "key" ) ) )
                {
                    boosterTasksFacade.invalidateAll();
                }
                else
                {
                    boosterTasksFacade.invalidateApp( ApplicationKey.from( (String) event.getData().get( "key" ) ) );
                }
            }
            return;
        }

        if ( !event.isLocalOrigin() )
        {
            return;
        }

        Set<ProjectName> projects = new HashSet<>();
        if ( type.startsWith( "repository." ) )
        {
            final String repo = (String) event.getData().get( "id" );
            if ( repo != null && repo.startsWith( ProjectConstants.PROJECT_REPO_ID_PREFIX ) )
            {
                LOG.debug( "Adding repo {} to cleanup list due to event {}", repo, event.getType() );
                projects.add( ProjectName.from( RepositoryId.from( repo ) ) );
            }
        }

        if ( type.equals( "node.pushed" ) || type.equals( "node.deleted" ) )
        {
            final List<Map<String, Object>> nodes = (List<Map<String, Object>>) event.getData().get( "nodes" );
            for ( Map<String, Object> node : nodes )
            {
                if ( "master".equals( node.get( "branch" ) ) )
                {
                    final String repo = node.get( "repo" ).toString();
                    if ( repo.startsWith( ProjectConstants.PROJECT_REPO_ID_PREFIX ) )
                    {

                        final ProjectName project = ProjectName.from( RepositoryId.from( repo ) );
                        final boolean added = projects.add( project );
                        if ( added )
                        {
                            LOG.debug( "Added project {} to cleanup list due to event {}", project, event.getType() );
                        }
                    }
                }
            }
        }
        addProjects( projects );
    }

    synchronized void addProjects( Collection<ProjectName> projects )
    {
        this.projects.addAll( projects );
    }

    synchronized Set<ProjectName> exchange()
    {
        final Set<ProjectName> result = projects;
        projects = new HashSet<>();
        return result;
    }
}
