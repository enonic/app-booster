package com.enonic.app.booster.storage;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.enonic.app.booster.BoosterConfig;
import com.enonic.xp.event.Event;
import com.enonic.xp.page.DescriptorKey;
import com.enonic.xp.task.SubmitTaskParams;
import com.enonic.xp.task.TaskService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.captor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoosterInvalidatorTest
{
    @Mock
    TaskService taskService;

    @Test
    void application_started_event_purge_all()
    {
        BoosterInvalidator boosterInvalidator = new BoosterInvalidator( taskService );
        final BoosterConfig boosterConfig = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        when( boosterConfig.appsInvalidateCacheOnStart() ).thenReturn( "somekey" );
        boosterInvalidator.activate( boosterConfig );

        boosterInvalidator.onEvent(
            Event.create( "application" ).value( "eventType", "STARTED" ).value( "applicationKey", "somekey" ).build() );

        final ArgumentCaptor<SubmitTaskParams> captor = captor();

        verify( taskService ).submitTask( captor.capture() );

        captor.getValue();
        assertEquals( DescriptorKey.from( "com.enonic.app.booster:purge-all" ), captor.getValue().getDescriptorKey() );
    }

    @Test
    void application_started_event_not_configured()
    {
        BoosterInvalidator boosterInvalidator = new BoosterInvalidator( taskService );
        final BoosterConfig boosterConfig = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        boosterInvalidator.activate( boosterConfig );

        boosterInvalidator.onEvent(
            Event.create( "application" ).value( "eventType", "STARTED" ).value( "applicationKey", "somekey" ).build() );

        verifyNoInteractions( taskService );
    }

    @Test
    @Disabled
    void repository_event_invalidate_projects() throws Exception
    {
        BoosterInvalidator boosterInvalidator = new BoosterInvalidator( taskService );
        final BoosterConfig boosterConfig = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        boosterInvalidator.activate( boosterConfig );

        boosterInvalidator.onEvent(
            Event.create( "repository.update" ).value( "id", "com.enonic.cms.repo1" ).value( "applicationKey", "somekey" ).build() );
        boosterInvalidator.onEvent( Event.create( "repository.delete" ).value( "id", "com.enonic.cms.repo2" ).build() );

        final ArgumentCaptor<SubmitTaskParams> captor = captor();

        Thread.sleep( 10000 );
        verify( taskService ).submitTask( captor.capture() );

        final SubmitTaskParams params = captor.getValue();
        assertEquals( DescriptorKey.from( "com.enonic.app.booster:invalidate" ), params.getDescriptorKey() );
        assertThat( params.getData().getStrings( "project" ) ).containsExactly( "repo1", "repo2" );
    }
}
