package com.enonic.app.booster.storage;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enonic.xp.data.ValueFactory;
import com.enonic.xp.node.DeleteNodeParams;
import com.enonic.xp.node.FindNodesByQueryResult;
import com.enonic.xp.node.NodeHit;
import com.enonic.xp.node.NodeHits;
import com.enonic.xp.node.NodePath;
import com.enonic.xp.node.NodeQuery;
import com.enonic.xp.node.NodeService;
import com.enonic.xp.node.RefreshMode;
import com.enonic.xp.query.expr.FieldOrderExpr;
import com.enonic.xp.query.expr.OrderExpr;
import com.enonic.xp.query.filter.RangeFilter;
import com.enonic.xp.query.filter.ValueFilter;
import com.enonic.xp.script.bean.BeanContext;
import com.enonic.xp.script.bean.ScriptBean;

public class NodeCleanerBean
    implements ScriptBean
{
    private static final Logger LOG = LoggerFactory.getLogger( NodeCleanerBean.class );

    private NodeService nodeService;

    @Override
    public void initialize( final BeanContext beanContext )
    {
        this.nodeService = beanContext.getService( NodeService.class ).get();
    }

    public void cleanUpRepos( List<String> repos )
    {
        Instant cutOffTime = Instant.now();

        BoosterContext.runInContext( () -> {
            LOG.debug( "Invalidating cache for repositories {}", repos );
            final NodeQuery query = createQuery( repos, cutOffTime );

            nodeService.refresh( RefreshMode.SEARCH );
            FindNodesByQueryResult nodesToDelete = nodeService.findByQuery( query );

            long hits = nodesToDelete.getHits();
            LOG.debug( "Found {} nodes total to be deleted", nodesToDelete.getTotalHits() );

            while ( hits > 0 )
            {
                final NodeHits nodeHits = nodesToDelete.getNodeHits();
                for ( NodeHit nodeHit : nodeHits )
                {
                    nodeService.delete( DeleteNodeParams.create().nodeId( nodeHit.getNodeId() ).build() );
                }
                LOG.debug( "Deleted {} nodes", nodeHits.getSize() );

                nodeService.refresh( RefreshMode.SEARCH );
                nodesToDelete = nodeService.findByQuery( query );

                hits = nodesToDelete.getHits();
            }
            LOG.debug( "Done invalidating cache for repositories {}", repos );
        } );
    }

    public void invalidateAll()
    {
        Instant cutOffTime = Instant.now();
        BoosterContext.runInContext( () -> {
            LOG.debug( "Invalidating all" );
            final NodeQuery query = createQuery( List.of(), cutOffTime );

            nodeService.refresh( RefreshMode.SEARCH );
            FindNodesByQueryResult nodesToDelete = nodeService.findByQuery( query );

            long hits = nodesToDelete.getHits();
            LOG.debug( "Found {} nodes total to be deleted", nodesToDelete.getTotalHits() );

            while ( hits > 0 )
            {
                final NodeHits nodeHits = nodesToDelete.getNodeHits();
                for ( NodeHit nodeHit : nodeHits )
                {
                    nodeService.delete( DeleteNodeParams.create().nodeId( nodeHit.getNodeId() ).build() );
                }
                LOG.debug( "Deleted {} nodes", nodeHits.getSize() );

                nodeService.refresh( RefreshMode.SEARCH );
                nodesToDelete = nodeService.findByQuery( query );

                hits = nodesToDelete.getHits();
            }
            LOG.debug( "Done invalidating all" );
        } );

    }

    public void deleteExcessNodes(int cacheSize)
    {
        BoosterContext.runInContext( () -> {
            LOG.debug( "Delete old nodes" );
            final NodeQuery query = queryOldNodes( );
            FindNodesByQueryResult nodesToDelete = nodeService.findByQuery( query );

            final long diff = nodesToDelete.getTotalHits() - cacheSize;
            if (diff <= 0) {
                LOG.debug( "Fewer nodes than maximum allowed" );
                return;
            }

            final NodeHits nodeHits = nodesToDelete.getNodeHits();
            for ( int i = 0; i < diff; i++ )
            {
                nodeService.delete( DeleteNodeParams.create().nodeId( nodeHits.get( i ).getNodeId() ).build() );
            }
        });
    }

    private NodeQuery queryOldNodes(  )
    {
        final NodeQuery.Builder builder = NodeQuery.create();

        builder.parent( NodePath.ROOT ).addOrderBy( FieldOrderExpr.create( "cachedTime", OrderExpr.Direction.ASC ) ).size( 10_000 );

        return builder.build();
    }

    private NodeQuery createQuery( Collection<String> repos, Instant cutOffTime )
    {
        final NodeQuery.Builder builder = NodeQuery.create();
        builder.parent( NodePath.ROOT );
        if ( !repos.isEmpty() )
        {
            builder.addQueryFilter( ValueFilter.create().fieldName( "repo" ).addValues( repos ).build() );
        }

        builder.addQueryFilter( RangeFilter.create().fieldName( "cachedTime" ).lt( ValueFactory.newDateTime( cutOffTime ) ).build() );

        builder.addOrderBy( FieldOrderExpr.create( "cachedTime", OrderExpr.Direction.ASC ) ).size( 10_000 );

        return builder.build();
    }


}
