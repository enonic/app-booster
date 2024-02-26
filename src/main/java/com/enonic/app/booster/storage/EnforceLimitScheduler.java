package com.enonic.app.booster.storage;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Component(immediate = true)
public class EnforceLimitScheduler
{
    private final ScheduledExecutorService executorService;

    @Activate
    public EnforceLimitScheduler( @Reference final BoosterTasksFacade boosterTasksFacade )
    {
        this( boosterTasksFacade, Executors.newSingleThreadScheduledExecutor() );

    }

    EnforceLimitScheduler( final BoosterTasksFacade boosterTasksFacade, final ScheduledExecutorService executorService )
    {
        this.executorService = executorService;
        this.executorService.scheduleWithFixedDelay( boosterTasksFacade::enforceLimit, 10, 10, TimeUnit.SECONDS );
    }

    @Deactivate
    public void deactivate()
    {
        executorService.shutdownNow();
    }
}
