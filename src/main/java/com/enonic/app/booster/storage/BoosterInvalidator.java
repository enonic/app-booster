package com.enonic.app.booster.storage;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import com.enonic.xp.data.PropertyTree;
import com.enonic.xp.event.Event;
import com.enonic.xp.event.EventListener;
import com.enonic.xp.page.DescriptorKey;
import com.enonic.xp.project.ProjectConstants;
import com.enonic.xp.project.ProjectName;
import com.enonic.xp.repository.RepositoryId;
import com.enonic.xp.task.SubmitTaskParams;
import com.enonic.xp.task.TaskId;
import com.enonic.xp.task.TaskService;

@Component(immediate = true, configurationPid = "com.enonic.app.booster")
public class BoosterInvalidator
    implements EventListener
{
    private static final Logger LOG = LoggerFactory.getLogger( BoosterInvalidator.class );

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool( 1 );

    private final TaskService taskService;

    private volatile Set<ProjectName> projects = new HashSet<>();

    private volatile BoosterConfigParsed config;

    synchronized void addProject( Collection<ProjectName> projects )
    {
        projects.addAll( projects );
    }

    synchronized Set<ProjectName> exchange()
    {
        final Set<ProjectName> result = projects;
        projects = new HashSet<>();
        return result;
    }

    @Activate
    public BoosterInvalidator( @Reference TaskService taskService )
    {
        this.taskService = taskService;
        executorService.scheduleWithFixedDelay( () -> doCleanUp( exchange() ), 10, 10, TimeUnit.SECONDS );
        executorService.scheduleWithFixedDelay( this::doCapped, 10, 10, TimeUnit.SECONDS );
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
        if ( !event.isLocalOrigin() )
        {
            return;
        }

        final String type = event.getType();

        if ( type.equals( "application" ) && event.getData().get( "eventType" ).equals( "STARTED" ) &&
            config.appList().contains( event.getData().get( "applicationKey" ) ) )
        {
            doPurgeAll();
            return;
        }

        Set<ProjectName> repos = new HashSet<>();
        if ( type.startsWith( "repository." ) )
        {
            final String repo = (String) event.getData().get( "id" );
            if ( repo != null && repo.startsWith( ProjectConstants.PROJECT_REPO_ID_PREFIX ) )
            {
                LOG.debug( "Adding repo {} to cleanup list due to event {}", repo, event.getType() );
                repos.add( ProjectName.from( RepositoryId.from( repo ) ) );
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

                        final ProjectName project = ProjectName.from( repo );
                        final boolean added = repos.add( project );
                        if ( added )
                        {
                            LOG.debug( "Added project {} to cleanup list due to event {}", project, event.getType() );
                        }
                    }
                }
            }
        }

        addProject( repos );
    }

    private void doCleanUp( Collection<ProjectName> projects )
    {
        try
        {
            if ( projects.isEmpty() )
            {
                return;
            }
            final PropertyTree data = new PropertyTree();
            data.addStrings( "project", projects.stream().map( Objects::toString ).toArray( String[]::new ) );
            final TaskId taskId = taskService.submitTask(
                SubmitTaskParams.create().descriptorKey( DescriptorKey.from( "com.enonic.app.booster:invalidate" ) ).data( data ).build() );
            LOG.debug( "Cleanup task submitted {}", taskId );
        }
        catch ( Exception e )
        {
            LOG.error( "Task could not be submitted ", e );
        }
    }

    private void doCapped()
    {
        try
        {
            final PropertyTree data = new PropertyTree();
            data.setLong( "cacheSize", (long) config.cacheSize() );
            final TaskId taskId = taskService.submitTask( SubmitTaskParams.create()
                                                              .descriptorKey( DescriptorKey.from( "com.enonic.app.booster:enforce-limit" ) )
                                                              .data( data )
                                                              .build() );
            LOG.debug( "Capped task submitted {}", taskId );
        }
        catch ( Exception e )
        {
            LOG.error( "Task could not be submitted ", e );
        }
    }

    private void doPurgeAll()
    {
        try
        {
            final TaskId taskId = taskService.submitTask( SubmitTaskParams.create()
                                                              .descriptorKey( DescriptorKey.from( "com.enonic.app.booster:purge-all" ) )
                                                              .data( new PropertyTree() )
                                                              .build() );
            LOG.debug( "Purge all task submitted {}", taskId );
        }
        catch ( Exception e )
        {
            LOG.error( "Task could not be submitted ", e );
        }
    }
}
