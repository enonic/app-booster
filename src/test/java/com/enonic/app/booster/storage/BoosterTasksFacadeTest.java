package com.enonic.app.booster.storage;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.enonic.app.booster.BoosterConfig;
import com.enonic.xp.page.DescriptorKey;
import com.enonic.xp.project.ProjectName;
import com.enonic.xp.task.SubmitTaskParams;
import com.enonic.xp.task.TaskService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.captor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        boosterTasksFacade.activate( mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() ) );

        boosterTasksFacade.invalidateProjects( List.of( ProjectName.from( "proj1" ), ProjectName.from( "proj2" ) ) );

        final ArgumentCaptor<SubmitTaskParams> captor = captor();
        verify( taskService ).submitTask( captor.capture() );

        final SubmitTaskParams params = captor.getValue();
        assertEquals( DescriptorKey.from( "com.enonic.app.booster:invalidate" ), params.getDescriptorKey() );
        assertThat( params.getData().getStrings( "project" ) ).containsExactly( "proj1", "proj2" );
    }

    @Test
    void enforceLimit()
    {
        BoosterTasksFacade boosterTasksFacade = new BoosterTasksFacade( taskService );
        final BoosterConfig boosterConfig = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        when( boosterConfig.cacheSize() ).thenReturn( 10 );
        boosterTasksFacade.activate( boosterConfig );

        boosterTasksFacade.enforceLimit();

        final ArgumentCaptor<SubmitTaskParams> captor = captor();
        verify( taskService ).submitTask( captor.capture() );

        final SubmitTaskParams params = captor.getValue();
        assertEquals( DescriptorKey.from( "com.enonic.app.booster:enforce-limit" ), params.getDescriptorKey() );
        assertEquals( 10, params.getData().getLong( "cacheSize" ));
    }

    @Test
    void purgeAll()
    {
        BoosterTasksFacade boosterTasksFacade = new BoosterTasksFacade( taskService );
        boosterTasksFacade.activate( mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() ) );

        boosterTasksFacade.purgeAll();

        final ArgumentCaptor<SubmitTaskParams> captor = captor();
        verify( taskService ).submitTask( captor.capture() );

        final SubmitTaskParams params = captor.getValue();
        assertEquals( DescriptorKey.from( "com.enonic.app.booster:purge-all" ), params.getDescriptorKey() );

    }
}
