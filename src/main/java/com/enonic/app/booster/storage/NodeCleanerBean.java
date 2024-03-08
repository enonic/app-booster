package com.enonic.app.booster.storage;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enonic.app.booster.BoosterConfigService;
import com.enonic.xp.branch.Branch;
import com.enonic.xp.context.ContextAccessor;
import com.enonic.xp.context.ContextBuilder;
import com.enonic.xp.data.ValueFactory;
import com.enonic.xp.node.DeleteNodeParams;
import com.enonic.xp.node.FindNodesByQueryResult;
import com.enonic.xp.node.Node;
import com.enonic.xp.node.NodeHit;
import com.enonic.xp.node.NodeHits;
import com.enonic.xp.node.NodeId;
import com.enonic.xp.node.NodeQuery;
import com.enonic.xp.node.NodeService;
import com.enonic.xp.node.RefreshMode;
import com.enonic.xp.node.UpdateNodeParams;
import com.enonic.xp.project.ProjectName;
import com.enonic.xp.query.expr.CompareExpr;
import com.enonic.xp.query.expr.FieldExpr;
import com.enonic.xp.query.expr.FieldOrderExpr;
import com.enonic.xp.query.expr.LogicalExpr;
import com.enonic.xp.query.expr.OrderExpr;
import com.enonic.xp.query.expr.QueryExpr;
import com.enonic.xp.query.expr.ValueExpr;
import com.enonic.xp.query.filter.BooleanFilter;
import com.enonic.xp.query.filter.ExistsFilter;
import com.enonic.xp.query.filter.RangeFilter;
import com.enonic.xp.query.filter.ValueFilter;
import com.enonic.xp.script.bean.BeanContext;
import com.enonic.xp.script.bean.ScriptBean;

import static java.util.Objects.requireNonNullElse;

public class NodeCleanerBean
    implements ScriptBean
{
    private static final Logger LOG = LoggerFactory.getLogger( NodeCleanerBean.class );

    private NodeService nodeService;

    private Supplier<BoosterConfigService> configService;

    private static volatile Instant LAST_CHECKED_CACHE;

    @Override
    public void initialize( final BeanContext beanContext )
    {
        this.nodeService = beanContext.getService( NodeService.class ).get();
        this.configService = beanContext.getService( BoosterConfigService.class );
    }

    public void invalidateProjects( final List<String> projects )
    {
        if ( projects.isEmpty() )
        {
            return;
        }
        invalidateByQuery( Map.of( "project", Multiple.of( projects ) ) );
    }

    public void invalidateContent( final String project, final String contentId )
    {
        invalidateByQuery( Map.of( "project", Single.of( project ), "contentId", Single.of( contentId ) ) );
    }

    public void invalidateSite( final String project, final String siteId )
    {
        invalidateByQuery( Map.of( "project", Single.of( project ), "siteId", Single.of( siteId ) ) );
    }

    public void invalidateDomain( final String domain )
    {
        invalidateByQuery( Map.of( "domain", Single.of( domain ) ) );
    }

    public void invalidatePathPrefix( final String domain, final String path )
    {
        invalidateByQuery( Map.of( "domain", Single.of( domain ), "path", PathPrefix.of( path ) ) );
    }

    public void invalidateAll()
    {
        invalidateByQuery( Map.of() );
    }

    public void purgeAll()
    {
        final Instant now = Instant.now();
        BoosterContext.runInContext( () -> {
            final NodeQuery query = queryAllNodes( now );

            process( query, this::delete );
        } );
    }

    public void deleteExcessNodes()
    {
        final int cacheSize = configService.get().getConfig().cacheSize();
        final Instant now = Instant.now();

        BoosterContext.runInContext( () -> {
            LOG.debug( "Delete old nodes" );
            final NodeQuery query = queryAllNodes( now );
            FindNodesByQueryResult nodesToDelete = nodeService.findByQuery( query );

            long diff = nodesToDelete.getTotalHits() - cacheSize;

            while ( diff > 0 )
            {
                final NodeHits nodeHits = nodesToDelete.getNodeHits();
                for ( int i = 0; i < diff; i++ )
                {
                    delete( nodeHits.get( i ).getNodeId() );
                }
                nodeService.refresh( RefreshMode.SEARCH );
                nodesToDelete = nodeService.findByQuery( query );
                diff = nodesToDelete.getTotalHits() - cacheSize;
            }

        } );
    }

    public List<String> findScheduledForInvalidation( final List<String> projects )
    {
        final Instant now = Instant.now().truncatedTo( ChronoUnit.SECONDS );

        return BoosterContext.callInContext( () -> {
            final Node scheduledParentNode = nodeService.getByPath( BoosterContext.SCHEDULED_PARENT_NODE );

            final Instant lastCheckedStored =
                requireNonNullElse( scheduledParentNode.data().getInstant( "lastChecked" ), scheduledParentNode.getTimestamp() );

            final Instant lastChecked = requireNonNullElse( LAST_CHECKED_CACHE, lastCheckedStored );

            final NodeQuery.Builder nodeQueryBuilder = NodeQuery.create().size( 0 );
            final ValueExpr nowValue = ValueExpr.instant( now.toString() );
            final ValueExpr lastCheckedValue = ValueExpr.instant( lastChecked.toString() );

            nodeQueryBuilder.query( QueryExpr.from( LogicalExpr.or(
                LogicalExpr.and( CompareExpr.gt( FieldExpr.from( "publish.from" ), lastCheckedValue ),
                                 CompareExpr.lte( FieldExpr.from( "publish.from" ), nowValue ) ),
                LogicalExpr.and( CompareExpr.gt( FieldExpr.from( "publish.to" ), lastCheckedValue ),
                                 CompareExpr.lte( FieldExpr.from( "publish.to" ), nowValue ) ) ) ) );

            final NodeQuery nodeQuery = nodeQueryBuilder.build();

            final List<String> filteredProjects = projects.stream()
                .filter( name -> ContextBuilder.from( ContextAccessor.current() )
                    .branch( Branch.from( "master" ) )
                    .repositoryId( ProjectName.from( name ).getRepoId() )
                    .build()
                    .callWith( () -> nodeService.findByQuery( nodeQuery ).getTotalHits() > 0 ) )
                .toList();

            LAST_CHECKED_CACHE = now;
            if ( lastCheckedStored.plus( 1, ChronoUnit.HOURS ).isBefore( now ) )
            {
                nodeService.update( UpdateNodeParams.create()
                                        .id( scheduledParentNode.id() )
                                        .editor( editor -> editor.data.setInstant( "lastChecked", now ) )
                                        .build() );
            }
            return filteredProjects;
        } );
    }

    public int getProjectCacheSize( final String project )
    {
        return getSize( Map.of( "project", Single.of( project ) ) );
    }

    public int getSiteCacheSize( final String project, final String siteId )
    {
        return getSize( Map.of( "project", Single.of( project ), "siteId", Single.of( siteId ) ) );
    }

    public int getContentCacheSize( final String project, final String contentId )
    {
        return getSize( Map.of( "project", Single.of( project ), "contentId", Single.of( contentId ) ) );
    }

    private int getSize( final Map<String, Value> fields )
    {
        final Instant now = Instant.now();
        FindNodesByQueryResult nodesToInvalidate = BoosterContext.callInContext( () -> {

            final NodeQuery query = queryNodes( fields, now, false, 0 );
            return nodeService.findByQuery( query );
        } );
        return (int) Math.max( 0, Math.min( nodesToInvalidate.getTotalHits(), Integer.MAX_VALUE ) );
    }

    private void invalidateByQuery( final Map<String, Value> fields )
    {
        final Instant now = Instant.now();
        BoosterContext.runInContext( () -> {
            final NodeQuery query = queryNodes( fields, now, false, 10_000 );
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

    private void setInvalidatedTime( final NodeId nodeId, final Instant cutOffTime )
    {
        nodeService.update(
            UpdateNodeParams.create().id( nodeId ).editor( editor -> editor.data.setInstant( "invalidatedTime", cutOffTime ) ).build() );
    }

    private void delete( final NodeId nodeId )
    {
        nodeService.delete( DeleteNodeParams.create().nodeId( nodeId ).build() );
    }

    private NodeQuery queryAllNodes( final Instant cutOffTime )
    {
        return queryNodes( Map.of(), cutOffTime, true, 10_000 );
    }

    private NodeQuery queryNodes( final Map<String, Value> fields, final Instant cutOffTime, final boolean includeInvalidated, int size )
    {
        final NodeQuery.Builder builder = NodeQuery.create();
        builder.parent( BoosterContext.CACHE_PARENT_NODE );

        for ( Map.Entry<String, Value> entry : fields.entrySet() )
        {
            final Value value = entry.getValue();
            if ( value instanceof Multiple multiple )
            {
                builder.addQueryFilter( ValueFilter.create().fieldName( entry.getKey() ).addValues( multiple.values ).build() );
            }
            else if ( value instanceof PathPrefix pathPrefix )
            {
                final QueryExpr queryExpr = QueryExpr.from(
                    LogicalExpr.or( CompareExpr.eq( FieldExpr.from( entry.getKey() ), ValueExpr.string( pathPrefix.value ) ),
                                    CompareExpr.like( FieldExpr.from( entry.getKey() ), ValueExpr.string( pathPrefix.value + "/*" ) ) ) );
                builder.query( queryExpr );
            }
            else if ( value instanceof Single single )
            {
                builder.addQueryFilter(
                    ValueFilter.create().fieldName( entry.getKey() ).addValue( ValueFactory.newString( single.value ) ).build() );
            }
            else
            {
                throw new IllegalArgumentException( "Unknown value type: " + value );
            }
        }

        if ( !includeInvalidated )
        {
            builder.addQueryFilter(
                BooleanFilter.create().mustNot( ExistsFilter.create().fieldName( "invalidatedTime" ).build() ).build() );
        }
        else
        {
            builder.addOrderBy( FieldOrderExpr.create( "invalidatedTime", OrderExpr.Direction.ASC ) );
        }

        if ( cutOffTime != null )
        {
            builder.addQueryFilter( RangeFilter.create().fieldName( "cachedTime" ).lt( ValueFactory.newDateTime( cutOffTime ) ).build() );
        }
        builder.addOrderBy( FieldOrderExpr.create( "cachedTime", OrderExpr.Direction.ASC ) ).size( size );

        return builder.build();
    }

    sealed interface Value
        permits Single, Multiple, PathPrefix
    {
    }

    static final class Single
        implements Value
    {
        String value;

        Single( final String value )
        {
            this.value = value;
        }

        static Single of( final String value )
        {
            return new Single( value );
        }
    }

    static final class Multiple
        implements Value
    {
        Collection<String> values;

        Multiple( final Collection<String> values )
        {
            this.values = values;
        }

        static Multiple of( final Collection<String> values )
        {
            return new Multiple( values );
        }
    }

    static final class PathPrefix
        implements Value
    {
        PathPrefix( final String value )
        {
            this.value = value;
        }

        String value;

        static PathPrefix of( final String value )
        {
            return new PathPrefix( value );
        }
    }
}
