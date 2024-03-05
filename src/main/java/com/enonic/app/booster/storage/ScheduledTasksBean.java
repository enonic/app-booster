package com.enonic.app.booster.storage;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enonic.xp.branch.Branch;
import com.enonic.xp.context.ContextAccessor;
import com.enonic.xp.context.ContextBuilder;
import com.enonic.xp.node.Node;
import com.enonic.xp.node.NodeQuery;
import com.enonic.xp.node.NodeService;
import com.enonic.xp.node.UpdateNodeParams;
import com.enonic.xp.project.Project;
import com.enonic.xp.project.ProjectService;
import com.enonic.xp.query.expr.CompareExpr;
import com.enonic.xp.query.expr.FieldExpr;
import com.enonic.xp.query.expr.LogicalExpr;
import com.enonic.xp.query.expr.QueryExpr;
import com.enonic.xp.query.expr.ValueExpr;
import com.enonic.xp.script.bean.BeanContext;
import com.enonic.xp.script.bean.ScriptBean;

public class ScheduledTasksBean
    implements ScriptBean
{
    private static final Logger LOG = LoggerFactory.getLogger( NodeCleanerBean.class );

    private NodeService nodeService;

    private ProjectService projectService;

    private BoosterTasksFacade boosterTasksFacade;

    @Override
    public void initialize( final BeanContext beanContext )
    {
        this.nodeService = beanContext.getService( NodeService.class ).get();
        this.projectService = beanContext.getService( ProjectService.class ).get();
        this.boosterTasksFacade = beanContext.getService( BoosterTasksFacade.class ).get();
    }

    public void execute()
    {
        boosterTasksFacade.enforceLimit();
        scheduledPublishingInvalidate();
    }

    private void scheduledPublishingInvalidate()
    {
        final Instant now = Instant.now().truncatedTo( ChronoUnit.SECONDS );

        BoosterContext.runInContext( () -> {
            final Node scheduledParentNode = nodeService.getByPath( BoosterContext.SCHEDULED_PARENT_NODE );
            final Instant lastChecked = Objects.requireNonNullElse( scheduledParentNode.data().getInstant( "lastChecked" ), Instant.EPOCH );

            final NodeQuery.Builder nodeQueryBuilder = NodeQuery.create().size( 0 );
            final ValueExpr nowValue = ValueExpr.instant( now.toString() );
            final ValueExpr lastCheckedValue = ValueExpr.instant( lastChecked.toString() );

            nodeQueryBuilder.query( QueryExpr.from( LogicalExpr.or(
                LogicalExpr.and( CompareExpr.gt( FieldExpr.from( "publish.from" ), lastCheckedValue ),
                                 CompareExpr.lte( FieldExpr.from( "publish.from" ), nowValue ) ),
                LogicalExpr.and( CompareExpr.gt( FieldExpr.from( "publish.to" ), lastCheckedValue ),
                                 CompareExpr.lte( FieldExpr.from( "publish.to" ), nowValue ) ) ) ) );

            final NodeQuery nodeQuery = nodeQueryBuilder.build();

            final var projects = projectService.list()
                .stream()
                .map( Project::getName )
                .filter( name -> ContextBuilder.from( ContextAccessor.current() )
                    .branch( Branch.from( "master" ) )
                    .repositoryId( name.getRepoId() )
                    .build()
                    .callWith( () -> nodeService.findByQuery( nodeQuery ).getTotalHits() > 0 ) )
                .toList();

            boosterTasksFacade.invalidate( projects );

            nodeService.update( UpdateNodeParams.create()
                                    .id( scheduledParentNode.id() )
                                    .editor( editor -> editor.data.setInstant( "lastChecked", now ) )
                                    .build() );
        } );
    }

}
