package com.enonic.app.booster.storage;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enonic.app.booster.BoosterConfig;
import com.enonic.app.booster.BoosterConfigParsed;
import com.enonic.app.booster.concurrent.ThreadFactoryImpl;
import com.enonic.app.booster.query.BoosterQueryBuilder;
import com.enonic.xp.node.DeleteNodeParams;
import com.enonic.xp.node.FindNodesByQueryResult;
import com.enonic.xp.node.NodeHits;
import com.enonic.xp.node.NodeQuery;
import com.enonic.xp.node.NodeService;
import com.enonic.xp.node.RefreshMode;
import com.enonic.xp.trace.Tracer;

@Component(immediate = true, configurationPid = "com.enonic.app.booster")
public class BoosterScavenger
{
    private static final Logger LOG = LoggerFactory.getLogger( BoosterScavenger.class );

    private final NodeService nodeService;

    private final ScheduledExecutorService executorService;

    private volatile BoosterConfigParsed config;

    @Activate
    public BoosterScavenger( final BundleContext context, @Reference final NodeService nodeService )
    {
        this( nodeService, Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryImpl( context.getBundle().getSymbolicName() + "-" + context.getBundle().getBundleId() + "-scavenge-%d" ) ) );
    }

    public BoosterScavenger( final NodeService nodeService, final ScheduledExecutorService executorService )
    {
        this.nodeService = nodeService;
        this.executorService = executorService;

        this.executorService.scheduleWithFixedDelay( this::scavenge, 1, 60, TimeUnit.SECONDS );
    }

    @Activate
    @Modified
    public void activate( final BoosterConfig config )
    {
        this.config = BoosterConfigParsed.parse( config );
    }

    @Deactivate
    public void deactivate()
    {
        executorService.shutdownNow();
    }

    public void scavenge()
    {
        final int cacheSize = config.cacheSize();
        final Instant now = Instant.now();
        Tracer.trace( "booster.scavenge", () -> BoosterContext.runInContext( () -> {
            final NodeQuery query = BoosterQueryBuilder.queryNodes( Map.of(), now, true, 10_000 );
            FindNodesByQueryResult nodesToDelete = nodeService.findByQuery( query );

            long diff = nodesToDelete.getTotalHits() - cacheSize;

            while ( diff > 0 )
            {
                final NodeHits nodeHits = nodesToDelete.getNodeHits();
                for ( int i = 0; i < diff; i++ )
                {
                    nodeService.delete( DeleteNodeParams.create().nodeId( nodeHits.get( i ).getNodeId() ).build() );
                }
                LOG.debug( "Scavenger deleted {} nodes", diff );
                nodeService.refresh( RefreshMode.SEARCH );
                nodesToDelete = nodeService.findByQuery( query );
                diff = nodesToDelete.getTotalHits() - cacheSize;
            }
        } ) );

    }
}
