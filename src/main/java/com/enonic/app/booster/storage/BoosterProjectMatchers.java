package com.enonic.app.booster.storage;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.enonic.xp.app.ApplicationKey;
import com.enonic.xp.branch.Branch;
import com.enonic.xp.context.ContextAccessor;
import com.enonic.xp.context.ContextBuilder;
import com.enonic.xp.node.Node;
import com.enonic.xp.node.NodeQuery;
import com.enonic.xp.node.NodeService;
import com.enonic.xp.node.UpdateNodeParams;
import com.enonic.xp.project.Project;
import com.enonic.xp.project.ProjectName;
import com.enonic.xp.project.ProjectService;
import com.enonic.xp.query.expr.CompareExpr;
import com.enonic.xp.query.expr.FieldExpr;
import com.enonic.xp.query.expr.LogicalExpr;
import com.enonic.xp.query.expr.QueryExpr;
import com.enonic.xp.query.expr.ValueExpr;

import static java.util.Objects.requireNonNullElse;

@Component(service = BoosterProjectMatchers.class)
public class BoosterProjectMatchers
{
    private static volatile Instant LAST_CHECKED_CACHE;

    private final NodeService nodeService;

    private final ProjectService projectService;

    @Activate
    public BoosterProjectMatchers( @Reference final NodeService nodeService, @Reference final ProjectService projectService )
    {
        this.nodeService = nodeService;
        this.projectService = projectService;
    }

    public List<ProjectName> findByAppForInvalidation( final List<ApplicationKey> app )
    {
        return BoosterContext.callInContext( () -> {
            final List<String> projects = projectService.list().stream().map( Project::getName ).map( Object::toString ).toList();

            final NodeQuery.Builder nodeQueryBuilder = NodeQuery.create().size( 0 );

            final NodeQuery nodeQuery = nodeQueryBuilder.query( QueryExpr.from(
                CompareExpr.in( FieldExpr.from( "data.siteConfig.applicationkey" ),
                                app.stream().map( ApplicationKey::getName ).map( ValueExpr::string ).toList() ) ) ).build();
            return projects.stream()
                .filter( name -> ContextBuilder.from( ContextAccessor.current() )
                    .branch( Branch.from( "master" ) )
                    .repositoryId( ProjectName.from( name ).getRepoId() )
                    .build()
                    .callWith( () -> nodeService.findByQuery( nodeQuery ).getTotalHits() > 0 ) )
                .map( ProjectName::from )
                .toList();
        } );
    }

    public List<ProjectName> findScheduledForInvalidation()
    {
        final Instant now = Instant.now().truncatedTo( ChronoUnit.SECONDS );

        return BoosterContext.callInContext( () -> {
            final List<String> projects = projectService.list().stream().map( Project::getName ).map( Object::toString ).toList();

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
            return filteredProjects.stream().map( ProjectName::from ).toList();
        } );
    }
}
