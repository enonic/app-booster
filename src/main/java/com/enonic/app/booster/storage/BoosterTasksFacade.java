package com.enonic.app.booster.storage;

import java.security.MessageDigest;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.Callable;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enonic.app.booster.utils.MessageDigests;
import com.enonic.xp.data.PropertyTree;
import com.enonic.xp.page.DescriptorKey;
import com.enonic.xp.project.ProjectName;
import com.enonic.xp.task.SubmitTaskParams;
import com.enonic.xp.task.TaskId;
import com.enonic.xp.task.TaskService;

@Component(service = BoosterTasksFacade.class)
public class BoosterTasksFacade
{
    private static final Logger LOG = LoggerFactory.getLogger( BoosterTasksFacade.class );

    private final TaskService taskService;

    @Activate
    public BoosterTasksFacade( @Reference final TaskService taskService )
    {
        this.taskService = taskService;
    }

    public TaskId invalidateAll()
    {
        return invalidate( Collections.emptyList() );
    }

    public TaskId invalidate( final Collection<ProjectName> projects )
    {
        return logWarn( () -> {
            final String taskName = "com.enonic.app.booster:invalidate~" + generateNameSuffix( projects );
            // This does not precisely prevent duplicate tasks, but prevents hundreds simultaneous tasks
            if ( taskAlreadyExists( taskName ) )
            {
                LOG.debug( "Same task is already running" );
                return null;
            }

            final PropertyTree data = new PropertyTree();
            if ( !projects.isEmpty() )
            {
                data.addStrings( "project", projects.stream().map( Objects::toString ).toArray( String[]::new ) );
            }
            final TaskId taskId = taskService.submitTask( SubmitTaskParams.create()
                                                              .descriptorKey( DescriptorKey.from( "com.enonic.app.booster:invalidate" ) )
                                                              .name( taskName )
                                                              .data( data )
                                                              .build() );
            LOG.debug( "Cleanup task submitted {}", taskId );
            return taskId;
        } );
    }

    private static String generateNameSuffix( final Collection<ProjectName> projects )
    {
        if ( projects.isEmpty() )
        {
            return "all";
        }
        if ( projects.size() == 1 )
        {
            return projects.iterator().next().toString();
        }
        final MessageDigest digest = MessageDigests.sha256();
        projects.stream().map( ProjectName::toString ).sorted().map( String::getBytes ).forEach( digest::update );
        return HexFormat.of().formatHex( digest.digest(), 0, 16 );
    }

    private boolean taskAlreadyExists( final String taskName )
    {
        return taskService.getAllTasks().stream().filter( ti -> taskName.equals( ti.getName() ) ) // lookup for specific name
            .anyMatch( ti -> !ti.isDone() ); // only WAITING and RUNNING are considered submitted, others are done
    }

    private TaskId logWarn( final Callable<TaskId> callable )
    {
        try
        {
            return callable.call();
        }
        catch ( Exception e )
        {
            LOG.warn( "Task could not be submitted ", e );
            return null;
        }
    }
}
