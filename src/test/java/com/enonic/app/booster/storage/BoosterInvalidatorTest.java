package com.enonic.app.booster.storage;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.enonic.app.booster.BoosterConfig;
import com.enonic.xp.app.ApplicationKey;
import com.enonic.xp.event.Event;
import com.enonic.xp.index.IndexService;
import com.enonic.xp.project.ProjectName;

import static org.mockito.ArgumentCaptor.captor;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoosterInvalidatorTest
{
    @Mock
    BoosterTasksFacade boosterTasksFacade;

    @Mock
    IndexService indexService;

    @Mock
    ScheduledExecutorService scheduledExecutorService;


    @Test
    void application_installed_event_invalidate_all()
    {
        when( indexService.isMaster() ).thenReturn( true );
        BoosterInvalidator boosterInvalidator = new BoosterInvalidator( boosterTasksFacade, indexService );
        final BoosterConfig boosterConfig = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        when( boosterConfig.appsForceInvalidateOnInstall() ).thenReturn( "somekey" );
        boosterInvalidator.activate( boosterConfig );

        boosterInvalidator.onEvent(
            Event.create( "application.cluster" ).value( "eventType", "installed" ).value( "key", "somekey" ).build() );

        verify( boosterTasksFacade ).invalidateAll();
    }

    @Test
    void application_installed_not_master()
    {
        when( indexService.isMaster() ).thenReturn( false );
        BoosterInvalidator boosterInvalidator = new BoosterInvalidator( boosterTasksFacade, indexService );
        final BoosterConfig boosterConfig = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        when( boosterConfig.appsForceInvalidateOnInstall() ).thenReturn( "somekey" );
        boosterInvalidator.activate( boosterConfig );

        boosterInvalidator.onEvent(
            Event.create( "application.cluster" ).value( "eventType", "installed" ).value( "key", "somekey" ).build() );

        verifyNoInteractions( boosterTasksFacade );
    }
    @Test
    void application_installed_event_invalidate_app()
    {
        when( indexService.isMaster() ).thenReturn( true );
        BoosterInvalidator boosterInvalidator = new BoosterInvalidator( boosterTasksFacade, indexService );
        final BoosterConfig boosterConfig = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        when( boosterConfig.appsForceInvalidateOnInstall() ).thenReturn( "someotherkey" );
        boosterInvalidator.activate( boosterConfig );

        boosterInvalidator.onEvent(
            Event.create( "application.cluster" ).value( "eventType", "installed" ).value( "key", "somekey" ).build() );

        verify( boosterTasksFacade ).invalidateApp( ApplicationKey.from( "somekey" ));
    }

    @Test
    void repository_events_invalidate_projects()
    {
        BoosterInvalidator boosterInvalidator = new BoosterInvalidator( boosterTasksFacade, indexService, scheduledExecutorService );
        final BoosterConfig boosterConfig = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        boosterInvalidator.activate( boosterConfig );

        boosterInvalidator.onEvent(
            Event.create( "repository.update" ).value( "id", "com.enonic.cms.repo1" ).value( "applicationKey", "somekey" ).build() );
        boosterInvalidator.onEvent( Event.create( "repository.delete" ).value( "id", "com.enonic.cms.repo2" ).build() );

        final ArgumentCaptor<Runnable> captor = captor();
        verify( scheduledExecutorService ).scheduleWithFixedDelay( captor.capture(), eq( 10L ), eq( 10L ), eq( TimeUnit.SECONDS ) );

        captor.getValue().run();
        verify( boosterTasksFacade ).invalidateProjects( eq( Set.of( ProjectName.from( "repo1" ), ProjectName.from( "repo2" ) ) ) );
    }

    @Test
    void node_events_invalidate_projects()
    {
        BoosterInvalidator boosterInvalidator = new BoosterInvalidator( boosterTasksFacade, indexService, scheduledExecutorService );
        final BoosterConfig boosterConfig = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        boosterInvalidator.activate( boosterConfig );

        boosterInvalidator.onEvent( Event.create( "node.pushed" )
                                        .value( "nodes", List.of( Map.of( "repo", "com.enonic.cms.repo1", "branch", "master" ) ) )
                                        .build() );


        boosterInvalidator.onEvent( Event.create( "node.deleted" ).value( "nodes", List.of( Map.of( "repo", "com.enonic.cms.repo2", "branch", "master" ) ) ).build() );

        final ArgumentCaptor<Runnable> captor = captor();
        verify( scheduledExecutorService ).scheduleWithFixedDelay( captor.capture(), eq( 10L ), eq( 10L ), eq( TimeUnit.SECONDS ) );

        captor.getValue().run();
        verify( boosterTasksFacade ).invalidateProjects( eq( Set.of( ProjectName.from( "repo1" ), ProjectName.from( "repo2" ) ) ) );
    }
}
