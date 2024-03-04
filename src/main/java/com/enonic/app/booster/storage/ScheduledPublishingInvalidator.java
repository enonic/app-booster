package com.enonic.app.booster.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enonic.app.booster.BoosterConfig;
import com.enonic.app.booster.BoosterConfigParsed;
import com.enonic.app.booster.utils.MessageDigests;
import com.enonic.xp.branch.Branch;
import com.enonic.xp.context.ContextAccessor;
import com.enonic.xp.context.ContextBuilder;
import com.enonic.xp.data.PropertyTree;
import com.enonic.xp.node.CreateNodeParams;
import com.enonic.xp.node.DeleteNodeParams;
import com.enonic.xp.node.MultiRepoNodeHit;
import com.enonic.xp.node.MultiRepoNodeQuery;
import com.enonic.xp.node.Node;
import com.enonic.xp.node.NodeId;
import com.enonic.xp.node.NodeIdExistsException;
import com.enonic.xp.node.NodeQuery;
import com.enonic.xp.node.NodeService;
import com.enonic.xp.node.NodeVersionId;
import com.enonic.xp.node.SearchTarget;
import com.enonic.xp.node.SearchTargets;
import com.enonic.xp.node.UpdateNodeParams;
import com.enonic.xp.project.Project;
import com.enonic.xp.project.ProjectName;
import com.enonic.xp.project.ProjectService;
import com.enonic.xp.query.expr.CompareExpr;
import com.enonic.xp.query.expr.FieldExpr;
import com.enonic.xp.query.expr.FunctionExpr;
import com.enonic.xp.query.expr.LogicalExpr;
import com.enonic.xp.query.expr.QueryExpr;
import com.enonic.xp.query.expr.ValueExpr;
import com.enonic.xp.repository.RepositoryId;

@Component(immediate = true, configurationPid = "com.enonic.app.booster")
public class ScheduledPublishingInvalidator
{
    private static final Logger LOG = LoggerFactory.getLogger( ScheduledPublishingInvalidator.class );

    private final ScheduledExecutorService executorService;

    private final BoosterTasksFacade boosterTasksFacade;

    private final NodeService nodeService;

    private final ProjectService projectService;

    private volatile BoosterConfigParsed config;


    @Activate
    public ScheduledPublishingInvalidator( @Reference final ProjectService projectService, @Reference final NodeService nodeService,
                                           @Reference final BoosterTasksFacade boosterTasksFacade )
    {
        this( projectService, nodeService, boosterTasksFacade, Executors.newSingleThreadScheduledExecutor() );
    }

    ScheduledPublishingInvalidator( final ProjectService projectService, final NodeService nodeService,
                                    final BoosterTasksFacade boosterTasksFacade, final ScheduledExecutorService executorService )
    {
        this.projectService = projectService;
        this.nodeService = nodeService;
        this.boosterTasksFacade = boosterTasksFacade;
        this.executorService = executorService;
        this.executorService.scheduleWithFixedDelay( this::check, 10, 10, TimeUnit.SECONDS );
    }

    @Deactivate
    public void deactivate()
    {
        executorService.shutdownNow();
    }

    @Activate
    @Modified
    public void activate( final BoosterConfig config )
    {
        this.config = BoosterConfigParsed.parse( config );
    }

    private void check()
    {
        logError( () -> BoosterContext.runInContext( () -> {
            final SearchTargets.Builder builder = SearchTargets.create();
            for ( Project p : projectService.list() )
            {
                builder.add( SearchTarget.create()
                                 .branch( Branch.from( "master" ) )
                                 .repositoryId( p.getName().getRepoId() )
                                 .principalKeys( ContextAccessor.current().getAuthInfo().getPrincipals() )
                                 .build() );
            }

            final Node scheduledParentNode = nodeService.getByPath( BoosterContext.SCHEDULED_PARENT_NODE );
            final Instant lastChecked = scheduledParentNode.data().getInstant( "lastChecked" );
            final Instant now = Instant.now().truncatedTo( java.time.temporal.ChronoUnit.SECONDS );

            NodeQuery.Builder nodeQuery = NodeQuery.create().size( NodeQuery.ALL_RESULTS_SIZE_FLAG );

            ValueExpr nowValue = ValueExpr.instant( now.toString() );
            if (lastChecked == null)
            {
                nodeQuery.query( QueryExpr.from( LogicalExpr.or( CompareExpr.lte( FieldExpr.from( "publish.from" ), nowValue ),
                                                                 CompareExpr.lte( FieldExpr.from( "publish.to" ), nowValue ) ) ) );
            } else {
                ValueExpr lastCheckedValue = ValueExpr.instant( lastChecked.toString() );

                nodeQuery.query( QueryExpr.from( LogicalExpr.or( LogicalExpr.and( CompareExpr.gt( FieldExpr.from( "publish.from" ), lastCheckedValue ), CompareExpr.lte( FieldExpr.from( "publish.from" ), nowValue ) ),
                                                                 LogicalExpr.and( CompareExpr.gt( FieldExpr.from( "publish.to" ), lastCheckedValue ), CompareExpr.lte( FieldExpr.from( "publish.to" ), nowValue ) ) )));
            }

            MultiRepoNodeQuery query = new MultiRepoNodeQuery( builder.build(), nodeQuery.build() );
            Set<ProjectName> projects = new HashSet<>();

            nodeService.findByQuery( query )
                .getNodeHits()
                .stream()
                .flatMap( this::hitToScheduledPublishingRecordStream )
                .forEach(
                    record -> projects.add( ProjectName.from( record.repositoryId() ) ) );

            boosterTasksFacade.invalidate( projects );

            nodeService.update( UpdateNodeParams.create().id( scheduledParentNode.id() ).editor( editor -> {
                editor.data.setInstant( "lastChecked", now );
            } ).build() );
        } ) );
    }

    private Stream<ScheduledPublishingRecord> hitToScheduledPublishingRecordStream( final MultiRepoNodeHit hit )
    {
        final NodeId nodeId = hit.getNodeId();
        final Node node = ContextBuilder.from( ContextAccessor.current() )
            .branch( hit.getBranch() )
            .repositoryId( hit.getRepositoryId() )
            .build()
            .callWith( () -> nodeService.getById( nodeId ) );
        if ( node != null )
        {

            final Instant publishFrom = node.data().getInstant( "publish.from" );
            final Instant publishTo = node.data().getInstant( "publish.to" );

            final RepositoryId repositoryId = hit.getRepositoryId();
            final NodeVersionId nodeVersionId = node.getNodeVersionId();
            List<ScheduledPublishingRecord> records = new ArrayList<>();

            if ( publishFrom != null )
            {
                records.add( new ScheduledPublishingRecord( repositoryId, nodeVersionId, publishFrom, "publish.from", nodeId ) );
            }
            if ( publishTo != null )
            {
                records.add( new ScheduledPublishingRecord( repositoryId, nodeVersionId, publishTo, "publish.to", nodeId ) );
            }
            return records.stream();
        }
        return Stream.of();
    }

    record ScheduledPublishingRecord(RepositoryId repositoryId, NodeVersionId nodeVersionId, Instant time, String type, NodeId nodeId)
    {
    }

    private void logError( final Runnable runnable )
    {
        try
        {
            runnable.run();
        }
        catch ( Exception e )
        {
            LOG.error( "Task could not be submitted ", e );
        }
    }
}
