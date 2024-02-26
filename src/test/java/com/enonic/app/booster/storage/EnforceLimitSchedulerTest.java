package com.enonic.app.booster.storage;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentCaptor.captor;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EnforceLimitSchedulerTest
{
    @Mock
    BoosterTasksFacade boosterTasksFacade;

    @Mock
    ScheduledExecutorService scheduledExecutorService;

    @Test
    void enforceLimit()
    {
        EnforceLimitScheduler enforceLimitScheduler = new EnforceLimitScheduler( boosterTasksFacade, scheduledExecutorService );
        final ArgumentCaptor<Runnable> captor = captor();
        verify( scheduledExecutorService ).scheduleWithFixedDelay( captor.capture(), eq( 10L ), eq( 10L ), eq( TimeUnit.SECONDS ) );

        captor.getValue().run();
        verify( boosterTasksFacade ).enforceLimit();
    }
}
