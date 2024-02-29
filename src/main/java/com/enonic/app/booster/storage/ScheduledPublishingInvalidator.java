package com.enonic.app.booster.storage;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

import com.enonic.app.booster.BoosterConfig;
import com.enonic.app.booster.BoosterConfigParsed;
import com.enonic.xp.branch.Branch;
import com.enonic.xp.context.ContextAccessor;
import com.enonic.xp.context.ContextBuilder;
import com.enonic.xp.node.MultiRepoNodeQuery;
import com.enonic.xp.node.Node;
import com.enonic.xp.node.NodeQuery;
import com.enonic.xp.node.NodeService;
import com.enonic.xp.node.SearchTarget;
import com.enonic.xp.node.SearchTargets;
import com.enonic.xp.project.Project;
import com.enonic.xp.project.ProjectName;
import com.enonic.xp.project.ProjectService;
import com.enonic.xp.query.expr.CompareExpr;
import com.enonic.xp.query.expr.FieldExpr;
import com.enonic.xp.query.expr.LogicalExpr;
import com.enonic.xp.query.expr.QueryExpr;
import com.enonic.xp.query.expr.ValueExpr;

@Component(immediate = true, configurationPid = "com.enonic.app.booster")
public class ScheduledPublishingInvalidator
{
    private final ScheduledExecutorService executorService;

    private final BoosterTasksFacade boosterTasksFacade;

    private final NodeService nodeService;

    private final ProjectService projectService;

    private volatile BoosterConfigParsed config;

    private ConcurrentMap<Instant, Set<ProjectTime>> projectTimes = new ConcurrentHashMap<>();

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
        BoosterContext.runInContext( () -> {
            final SearchTargets.Builder builder = SearchTargets.create();
            for ( Project p : projectService.list() )
            {
                builder.add( SearchTarget.create()
                                 .branch( Branch.from( "master" ) )
                                 .repositoryId( p.getName().getRepoId() )
                                 .principalKeys( ContextAccessor.current().getAuthInfo().getPrincipals() )
                                 .build() );
            }

            ValueExpr now = ValueExpr.instant( Instant.now().toString() );
            NodeQuery nodeQuery = NodeQuery.create()
                .query( QueryExpr.from( LogicalExpr.or( CompareExpr.gte( FieldExpr.from( "publish.to" ), now ),
                                                        CompareExpr.gte( FieldExpr.from( "publish.from" ), now ) ) ) )
                .size( NodeQuery.ALL_RESULTS_SIZE_FLAG )
                .build();

            MultiRepoNodeQuery query = new MultiRepoNodeQuery( builder.build(), nodeQuery );
            final var projectTimeStream = nodeService.findByQuery( query ).getNodeHits().stream().flatMap( hit -> {
                final var instants = ContextBuilder.from( ContextAccessor.current() )
                    .branch( hit.getBranch() )
                    .repositoryId( hit.getRepositoryId() )
                    .build()
                    .callWith( () -> {
                        final Node node = nodeService.getById( hit.getNodeId() );
                        return Stream.of( node.data().getInstant( "publish.from" ), node.data().getInstant( "publish.to" ) );
                    } );

                return instants.filter( Objects::nonNull )
                    .map( time -> new ProjectTime( ProjectName.from( hit.getRepositoryId() ), time ) );
            } ).toList();

            for ( ProjectTime projectTime : projectTimeStream )
            {
                projectTimes.compute( projectTime.time(), ( k, v ) -> {
                    if ( v == null )
                    {
                        return Set.of( projectTime );
                    }
                    else
                    {
                        return Stream.concat( v.stream(), Stream.of( projectTime ) ).collect( Collectors.toSet() );
                    }
                } );
            }
        } );

    }

    private record ProjectTime (ProjectName projectName, Instant time) {}
}
