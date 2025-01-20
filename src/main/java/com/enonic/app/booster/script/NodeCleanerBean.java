package com.enonic.app.booster.script;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enonic.app.booster.query.BoosterQueryBuilder;
import com.enonic.app.booster.query.Value;
import com.enonic.app.booster.storage.BoosterContext;
import com.enonic.xp.node.DeleteNodeParams;
import com.enonic.xp.node.FindNodesByQueryResult;
import com.enonic.xp.node.NodeHit;
import com.enonic.xp.node.NodeHits;
import com.enonic.xp.node.NodeId;
import com.enonic.xp.node.NodeNotFoundException;
import com.enonic.xp.node.NodeQuery;
import com.enonic.xp.node.NodeService;
import com.enonic.xp.node.RefreshMode;
import com.enonic.xp.node.UpdateNodeParams;
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

    public void invalidateProjects( final List<String> projects )
    {
        if ( projects.isEmpty() )
        {
            return;
        }
        invalidateByQuery( Map.of( "project", Value.Multiple.of( projects ) ) );
    }

    public void invalidateContent( final String project, final String contentId )
    {
        invalidateByQuery( Map.of( "project", Value.Single.of( project ), "contentId", Value.Single.of( contentId ) ) );
    }

    public void invalidateSite( final String project, final String siteId )
    {
        invalidateByQuery( Map.of( "project", Value.Single.of( project ), "siteId", Value.Single.of( siteId ) ) );
    }

    public void invalidateDomain( final String domain )
    {
        invalidateByQuery( Map.of( "domain", Value.Single.of( domain ) ) );
    }

    public void invalidatePathPrefix( final String domain, final String path )
    {
        invalidateByQuery( Map.of( "domain", Value.Single.of( domain ), "path", Value.PathPrefix.of( path ) ) );
    }

    public void invalidateAll()
    {
        invalidateByQuery( Map.of() );
    }

    public void purgeAll()
    {
        final Instant now = Instant.now();
        BoosterContext.runInContext( () -> {
            final NodeQuery query = BoosterQueryBuilder.queryNodes( Map.of(), now, true, 10_000 );

            process( query, this::delete );
        } );
    }

    public int getProjectCacheSize( final String project )
    {
        return getSize( Map.of( "project", Value.Single.of( project ) ) );
    }

    public int getSiteCacheSize( final String project, final String siteId )
    {
        return getSize( Map.of( "project", Value.Single.of( project ), "siteId", Value.Single.of( siteId ) ) );
    }

    public int getContentCacheSize( final String project, final String contentId )
    {
        return getSize( Map.of( "project", Value.Single.of( project ), "contentId", Value.Single.of( contentId ) ) );
    }

    private int getSize( final Map<String, Value> fields )
    {
        final Instant now = Instant.now();
        FindNodesByQueryResult nodesToInvalidate = BoosterContext.callInContext( () -> {

            final NodeQuery query = BoosterQueryBuilder.queryNodes( fields, now, false, 0 );
            return nodeService.findByQuery( query );
        } );
        return (int) Math.max( 0, Math.min( nodesToInvalidate.getTotalHits(), Integer.MAX_VALUE ) );
    }

    private void invalidateByQuery( final Map<String, Value> fields )
    {
        final Instant now = Instant.now();
        BoosterContext.runInContext( () -> {
            final NodeQuery query = BoosterQueryBuilder.queryNodes( fields, now, false, 10_000 );
            process( query, ( n ) -> setInvalidatedTime( n, now ) );
        } );
    }

    private void process( final NodeQuery query, final Consumer<NodeId> op )
    {
        FindNodesByQueryResult nodesToInvalidate = nodeService.findByQuery( query );

        long hits = nodesToInvalidate.getHits();
        LOG.debug( "Found {} nodes total to be processed", nodesToInvalidate.getTotalHits() );

        while ( hits > 0 )
        {
            final NodeHits nodeHits = nodesToInvalidate.getNodeHits();
            for ( NodeHit nodeHit : nodeHits )
            {
                op.accept( nodeHit.getNodeId() );
            }
            LOG.debug( "Processed nodes {}", nodeHits.getSize() );

            nodeService.refresh( RefreshMode.SEARCH );
            nodesToInvalidate = nodeService.findByQuery( query );

            hits = nodesToInvalidate.getHits();
        }
    }

    private void setInvalidatedTime( final NodeId nodeId, final Instant invalidatedTime )
    {
        try
        {
            nodeService.update( UpdateNodeParams.create()
                                    .id( nodeId )
                                    .editor( editor -> editor.data.setInstant( "invalidatedTime", invalidatedTime ) )
                                    .build() );
        }
        catch ( NodeNotFoundException e )
        {
            LOG.debug( "Node for invalidate was already deleted", e );
        }
    }

    private void delete( final NodeId nodeId )
    {
        nodeService.delete( DeleteNodeParams.create().nodeId( nodeId ).build() );
    }
}
