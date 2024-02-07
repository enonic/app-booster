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
import com.enonic.xp.data.PropertyTree;
import com.enonic.xp.event.Event;
import com.enonic.xp.event.EventListener;
import com.enonic.xp.page.DescriptorKey;
import com.enonic.xp.project.ProjectConstants;
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

    private volatile Set<String> repos = new HashSet<>();

    private volatile BoosterConfigParsed config;

    synchronized void addRepos( Collection<String> repo )
    {
        repos.addAll( repo );
    }

    synchronized Set<String> exchange()
    {
        final Set<String> result = repos;
        repos = new HashSet<>();
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

        Set<String> repos = new HashSet<>();
        if ( type.startsWith( "repository." ) )
        {
            final String repo = (String) event.getData().get( "id" );
            if ( repo != null && repo.startsWith( ProjectConstants.PROJECT_REPO_ID_PREFIX ) )
            {
                LOG.debug( "Adding repo {} to cleanup list due to event {}", repo, event.getType() );
                repos.add( repo );
            }
        }

        if ( type.equals( "application" ) && event.getData().get( "eventType" ).equals( "STARTED" ) &&
            config.appList().contains( event.getData().get( "applicationKey" ) ) )
        {
            doInvalidateAll();
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

                        final boolean added = repos.add( repo );
                        if ( added )
                        {
                            LOG.debug( "Added repo {} to cleanup list due to event {}", repo, event.getType() );
                        }
                    }
                }
            }
        }

        addRepos( repos );
    }

    private void doCleanUp( Collection<String> repos )
    {
        try
        {
            if ( repos.isEmpty() )
            {
                return;
            }
            final PropertyTree data = new PropertyTree();
            data.addStrings( "repos", repos );
            final TaskId taskId = taskService.submitTask( SubmitTaskParams.create()
                                                              .descriptorKey(
                                                                  DescriptorKey.from( "com.enonic.app.booster:booster-node-cleaner" ) )
                                                              .data( data )
                                                              .build() );
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
                                                              .descriptorKey(
                                                                  DescriptorKey.from( "com.enonic.app.booster:booster-capped" ) )
                                                              .data( data )
                                                              .build() );
            LOG.debug( "Capped task submitted {}", taskId );
        }
        catch ( Exception e )
        {
            LOG.error( "Task could not be submitted ", e );
        }
    }

    private void doInvalidateAll()
    {
        try
        {
            final TaskId taskId = taskService.submitTask( SubmitTaskParams.create()
                                                              .descriptorKey(
                                                                  DescriptorKey.from( "com.enonic.app.booster:booster-invalidate-all" ) )
                                                              .data( new PropertyTree() )
                                                              .build() );
            LOG.debug( "Invalidate all task submitted {}", taskId );
        }
        catch ( Exception e )
        {
            LOG.error( "Task could not be submitted ", e );
        }
    }
}
