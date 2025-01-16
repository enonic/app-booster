package com.enonic.app.booster.storage;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enonic.app.booster.BoosterConfig;
import com.enonic.app.booster.BoosterConfigParsed;
import com.enonic.app.booster.concurrent.ThreadFactoryImpl;
import com.enonic.xp.app.ApplicationKey;
import com.enonic.xp.event.Event;
import com.enonic.xp.event.EventListener;
import com.enonic.xp.index.IndexService;
import com.enonic.xp.project.ProjectConstants;
import com.enonic.xp.project.ProjectName;
import com.enonic.xp.repository.RepositoryId;
import com.enonic.xp.task.TaskId;

@Component(immediate = true, configurationPid = "com.enonic.app.booster")
public class BoosterInvalidator
    implements EventListener
{
    private static final Logger LOG = LoggerFactory.getLogger( BoosterInvalidator.class );

    private final ScheduledExecutorService executorService;

    private final IndexService indexService;

    private final BoosterTasksFacade boosterTasksFacade;

    private final BoosterProjectMatchers boosterProjectMatchers;

    private volatile BoosterConfigParsed config;

    // permits null. null = all projects
    private volatile Set<ProjectName> projects = new HashSet<>();

    @Activate
    public BoosterInvalidator( final BundleContext context, @Reference final BoosterTasksFacade boosterTasksFacade,
                               @Reference final IndexService indexService, @Reference final BoosterProjectMatchers boosterProjectMatchers )
    {
        this( boosterTasksFacade, indexService, boosterProjectMatchers, Executors.newSingleThreadScheduledExecutor( new ThreadFactoryImpl(
            context.getBundle().getSymbolicName() + "-" + context.getBundle().getBundleId() + "-invalidator-%d" ) ) );
    }

    BoosterInvalidator( final BoosterTasksFacade boosterTasksFacade, final IndexService indexService,
                        final BoosterProjectMatchers boosterProjectMatchers, final ScheduledExecutorService executorService )
    {
        this.boosterTasksFacade = boosterTasksFacade;
        this.executorService = executorService;
        this.indexService = indexService;
        this.boosterProjectMatchers = boosterProjectMatchers;

        this.executorService.scheduleWithFixedDelay( this::invalidatePublished, 10, 10, TimeUnit.SECONDS );
    }

    @Activate
    @Modified
    public void activate( final BoosterConfig config )
    {
        this.config = BoosterConfigParsed.parse( config );
    }

    @Deactivate
    public void deactivate()
    {
        executorService.shutdownNow();
    }

    @Override
    public void onEvent( final Event event )
    {
        final String type = event.getType();

        if ( type.equals( "application.cluster" ) && event.getData().get( "eventType" ).equals( "installed" ) )
        {
            final ApplicationKey applicationKey = ApplicationKey.from( (String) event.getData().get( "key" ) );
            try
            {
                executorService.schedule( () -> invalidateApp( applicationKey ), 0, TimeUnit.SECONDS );
            }
            catch ( Exception e )
            {
                LOG.debug( "Could not invalidate cache for app {}", applicationKey, e );
            }
            return;
        }

        if ( !event.isLocalOrigin() )
        {
            return;
        }

        final Set<ProjectName> projects = new HashSet<>();
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

    private synchronized void addProjects( Collection<ProjectName> projects )
    {
        this.projects.addAll( projects );
    }

    private synchronized Set<ProjectName> poll()
    {
        final Set<ProjectName> result = projects;
        projects = new HashSet<>();
        return result;
    }

    private void invalidateApp( ApplicationKey applicationKey )
    {
        try
        {
            if ( indexService.isMaster() )
            {
                if ( config.appsForceInvalidateOnInstall().contains( applicationKey.getName() ) )
                {
                    doInvalidate( Collections.singleton( null ) );
                }
                else
                {
                    final List<ProjectName> toInvalidate = boosterProjectMatchers.findByAppForInvalidation( List.of( applicationKey ) );
                    doInvalidate( toInvalidate );
                }
            }
        }
        catch ( Exception e )
        {
            LOG.warn( "Could not invalidate cache for app {}", applicationKey, e );
        }
    }

    private void invalidatePublished()
    {
        try
        {
            final Set<ProjectName> toInvalidate = new HashSet<>();
            if ( indexService.isMaster() )
            {
                toInvalidate.addAll( boosterProjectMatchers.findScheduledForInvalidation() );
            }
            toInvalidate.addAll( this.poll() );
            doInvalidate( toInvalidate );
        }
        catch ( Exception e )
        {
            LOG.warn( "Could not invalidate cache", e );
        }
    }

    private void doInvalidate( final Collection<ProjectName> toInvalidate )
    {
        if ( toInvalidate.isEmpty() )
        {
            return;
        }
        final TaskId taskId;

        if ( toInvalidate.contains( null ) )
        {
            taskId = boosterTasksFacade.invalidateAll();
        }
        else
        {
            taskId = boosterTasksFacade.invalidate( toInvalidate );
        }

        if ( taskId == null )
        {
            LOG.debug( "Task was not submitted. Adding back projects for later invalidation" );
            addProjects( toInvalidate );
        }
    }
}
