package com.enonic.app.booster.storage;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.enonic.xp.app.ApplicationKey;
import com.enonic.xp.page.DescriptorKey;
import com.enonic.xp.project.ProjectName;
import com.enonic.xp.task.SubmitTaskParams;
import com.enonic.xp.task.TaskId;
import com.enonic.xp.task.TaskInfo;
import com.enonic.xp.task.TaskService;
import com.enonic.xp.task.TaskState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentCaptor.captor;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoosterTasksFacadeTest
{
    @Mock
    TaskService taskService;

    @Test
    void invalidate()
    {
        BoosterTasksFacade boosterTasksFacade = new BoosterTasksFacade( taskService );

        boosterTasksFacade.invalidate( Set.of( ProjectName.from( "proj1" ), ProjectName.from( "proj2" ) ) );

        final ArgumentCaptor<SubmitTaskParams> captor = captor();
        verify( taskService ).submitTask( captor.capture() );

        final SubmitTaskParams params = captor.getValue();
        assertThat( params.getDescriptorKey() ).isEqualTo( DescriptorKey.from( "com.enonic.app.booster:invalidate" ) );
        assertThat( params.getName() ).isEqualTo( "com.enonic.app.booster:invalidate~f256ebccd4d9f771cd3e50b4938eeab4" );
        assertThat( params.getData().getStrings( "project" ) ).containsExactlyInAnyOrder( "proj1", "proj2" );
    }


    @Test
    void invalidate_task_exists()
    {
        BoosterTasksFacade boosterTasksFacade = new BoosterTasksFacade( taskService );

        when( taskService.getAllTasks() ).thenReturn( Collections.singletonList( TaskInfo.create()
                                                                                     .name(
                                                                                         "com.enonic.app.booster:invalidate~f256ebccd4d9f771cd3e50b4938eeab4" )
                                                                                     .id( TaskId.from( "id" ) )
                                                                                     .application( ApplicationKey.from( "app" ) )
                                                                                     .startTime( Instant.now() )
                                                                                     .state( TaskState.WAITING )
                                                                                     .build() ) );

        boosterTasksFacade.invalidate( Set.of( ProjectName.from( "proj1" ), ProjectName.from( "proj2" ) ) );

        verifyNoMoreInteractions( taskService );
    }

    @Test
    void invalidate_one()
    {
        BoosterTasksFacade boosterTasksFacade = new BoosterTasksFacade( taskService );

        boosterTasksFacade.invalidate( List.of( ProjectName.from( "proj1" ) ) );

        final ArgumentCaptor<SubmitTaskParams> captor = captor();
        verify( taskService ).submitTask( captor.capture() );

        final SubmitTaskParams params = captor.getValue();
        assertEquals( DescriptorKey.from( "com.enonic.app.booster:invalidate" ), params.getDescriptorKey() );
        assertEquals( "com.enonic.app.booster:invalidate~proj1", params.getName() );
        assertThat( params.getData().getStrings( "project" ) ).containsExactly( "proj1" );
    }

    @Test
    void invalidate_all()
    {
        BoosterTasksFacade boosterTasksFacade = new BoosterTasksFacade( taskService );

        boosterTasksFacade.invalidate( Collections.emptyList() );

        final ArgumentCaptor<SubmitTaskParams> captor = captor();
        verify( taskService ).submitTask( captor.capture() );

        final SubmitTaskParams params = captor.getValue();
        assertEquals( DescriptorKey.from( "com.enonic.app.booster:invalidate" ), params.getDescriptorKey() );
        assertThat( params.getName() ).startsWith( "com.enonic.app.booster:invalidate~all" );
        assertFalse( params.getData().hasProperty( "project" ) );
    }
}
