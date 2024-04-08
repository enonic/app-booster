package com.enonic.app.booster.storage;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Callable;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enonic.app.booster.BoosterConfig;
import com.enonic.app.booster.BoosterConfigParsed;
import com.enonic.xp.app.ApplicationKey;
import com.enonic.xp.data.PropertyTree;
import com.enonic.xp.page.DescriptorKey;
import com.enonic.xp.project.ProjectName;
import com.enonic.xp.task.SubmitTaskParams;
import com.enonic.xp.task.TaskId;
import com.enonic.xp.task.TaskService;

@Component(service = BoosterTasksFacade.class, configurationPid = "com.enonic.app.booster")
public class BoosterTasksFacade
{
    private static final Logger LOG = LoggerFactory.getLogger( BoosterTasksFacade.class );

    private final TaskService taskService;


    private volatile BoosterConfigParsed config;

    @Activate
    public BoosterTasksFacade( @Reference final TaskService taskService )
    {
        this.taskService = taskService;
    }

    @Activate
    @Modified
    public void activate( final BoosterConfig config )
    {
        this.config = BoosterConfigParsed.parse( config );
    }

    public TaskId invalidateProjects( Collection<ProjectName> projects )
    {
        return logError( () -> {
            if ( projects.isEmpty() )
            {
                LOG.debug( "No projects specified to invalidate" );
                return null;
            }
            final PropertyTree data = new PropertyTree();
            data.addStrings( "project", projects.stream().map( Objects::toString ).toArray( String[]::new ) );
            final TaskId taskId = taskService.submitTask(
                SubmitTaskParams.create().descriptorKey( DescriptorKey.from( "com.enonic.app.booster:invalidate" ) ).data( data ).build() );
            LOG.debug( "Cleanup task submitted {}", taskId );
            return taskId;
        } );
    }

    public TaskId invalidateApp( final ApplicationKey app )
    {
        return logError( () -> {
            final PropertyTree data = new PropertyTree();
            data.addString( "app", app.toString() );
            final TaskId taskId = taskService.submitTask(
                SubmitTaskParams.create().descriptorKey( DescriptorKey.from( "com.enonic.app.booster:invalidate-app" ) ).data( data ).build() );
            LOG.debug( "Cleanup by app task submitted {}", taskId );
            return taskId;
        } );
    }


    public TaskId invalidateAll()
    {
        return logError( () -> {
            final PropertyTree data = new PropertyTree();
            final TaskId taskId = taskService.submitTask(
                SubmitTaskParams.create().descriptorKey( DescriptorKey.from( "com.enonic.app.booster:invalidate" ) ).data( data ).build() );
            LOG.debug( "Cleanup all task submitted {}", taskId );
            return taskId;
        } );
    }


    public TaskId enforceLimit()
    {
        return logError( () -> {
            final PropertyTree data = new PropertyTree();
            data.setLong( "cacheSize", (long) config.cacheSize() );
            final TaskId taskId = taskService.submitTask( SubmitTaskParams.create()
                                                              .descriptorKey( DescriptorKey.from( "com.enonic.app.booster:enforce-limit" ) )
                                                              .data( data )
                                                              .build() );
            LOG.debug( "Capped task submitted {}", taskId );
            return taskId;
        } );
    }

    public TaskId purgeAll()
    {
        return logError( () -> {
            final TaskId taskId = taskService.submitTask( SubmitTaskParams.create()
                                                              .descriptorKey( DescriptorKey.from( "com.enonic.app.booster:purge-all" ) )
                                                              .data( new PropertyTree() )
                                                              .build() );
            LOG.debug( "Purge all task submitted {}", taskId );
            return taskId;
        } );
    }

    private TaskId logError( final Callable<TaskId> callable )
    {
        try
        {
            return callable.call();
        }
        catch ( Exception e )
        {
            LOG.error( "Task could not be submitted ", e );
            return null;
        }
    }
}
