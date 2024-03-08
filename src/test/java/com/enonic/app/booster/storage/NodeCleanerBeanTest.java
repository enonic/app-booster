package com.enonic.app.booster.storage;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.enonic.xp.data.PropertyTree;
import com.enonic.xp.data.Value;
import com.enonic.xp.node.DeleteNodeParams;
import com.enonic.xp.node.EditableNode;
import com.enonic.xp.node.FindNodesByQueryResult;
import com.enonic.xp.node.Node;
import com.enonic.xp.node.NodeHit;
import com.enonic.xp.node.NodeId;
import com.enonic.xp.node.NodeQuery;
import com.enonic.xp.node.NodeService;
import com.enonic.xp.node.RefreshMode;
import com.enonic.xp.node.UpdateNodeParams;
import com.enonic.xp.query.expr.CompareExpr;
import com.enonic.xp.query.expr.LogicalExpr;
import com.enonic.xp.query.filter.ValueFilter;
import com.enonic.xp.script.bean.BeanContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentCaptor.captor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeCleanerBeanTest
{
    @Mock
    NodeService nodeService;

    NodeCleanerBean nodeCleanerBean;

    @BeforeEach
    void setUp()
    {
        final BeanContext beanContext = mock( BeanContext.class );
        when( beanContext.getService( NodeService.class ) ).thenReturn( () -> nodeService );
        nodeCleanerBean = new NodeCleanerBean();
        nodeCleanerBean.initialize( beanContext );
    }

    @Test
    void invalidateAll()
    {
        verifyBasicInvalidate( nodeCleanerBean::invalidateAll );
    }

    private NodeQuery verifyBasicInvalidate( Runnable runnable )
    {
        when( nodeService.findByQuery( any( NodeQuery.class ) ) ).thenReturn( FindNodesByQueryResult.create()
                                                                                  .addNodeHit( NodeHit.create()
                                                                                                   .nodeId( NodeId.from( "node1" ) )
                                                                                                   .build() )
                                                                                  .hits( 1 )
                                                                                  .totalHits( 1 )
                                                                                  .build() )
            .thenReturn( FindNodesByQueryResult.create().hits( 0 ).totalHits( 0 ).build() );

        runnable.run();

        final ArgumentCaptor<NodeQuery> findByQueryCaptor = captor();
        final ArgumentCaptor<UpdateNodeParams> updateCaptor = captor();
        verify( nodeService, times( 2 ) ).findByQuery( findByQueryCaptor.capture() );
        final NodeQuery nodeQuery = findByQueryCaptor.getValue();
        assertEquals( "/cache", nodeQuery.getParent().toString() );
        assertEquals( 10000, nodeQuery.getSize() );

        verify( nodeService ).update( updateCaptor.capture() );
        final UpdateNodeParams updateNodeParams = updateCaptor.getValue();
        assertEquals( "node1", updateNodeParams.getId().toString() );

        Node node = Node.create().data( new PropertyTree() ).build();
        final EditableNode toBeEdited = new EditableNode( node );
        updateNodeParams.getEditor().edit( toBeEdited );
        assertNotNull( toBeEdited.data.getInstant( "invalidatedTime" ) );
        return nodeQuery;
    }

    @Test
    void invalidateProjects()
    {
        final NodeQuery nodeQuery = verifyBasicInvalidate( () -> nodeCleanerBean.invalidateProjects( List.of( "project1", "project2" ) ) );
        assertThat( nodeQuery.getQueryFilters().stream().filter( f -> f instanceof ValueFilter ) ).map( f -> (ValueFilter) f )
            .anySatisfy( filter -> {
                assertThat( filter.getFieldName() ).isEqualTo( "project" );
                assertThat( filter.getValues() ).map( Value::toString ).containsExactlyInAnyOrder( "project1", "project2" );
            } );
    }

    @Test
    void invalidateContent()
    {
        final NodeQuery nodeQuery = verifyBasicInvalidate( () -> nodeCleanerBean.invalidateContent( "project1", "content1" ) );
        assertThat( nodeQuery.getQueryFilters().stream().filter( f -> f instanceof ValueFilter ) ).map( f -> (ValueFilter) f )
            .anySatisfy( filter -> {
                assertThat( filter.getFieldName() ).isEqualTo( "project" );
                assertThat( filter.getValues() ).map( Value::toString ).containsExactly( "project1" );
            } )
            .anySatisfy( filter -> {
                assertThat( filter.getFieldName() ).isEqualTo( "contentId" );
                assertThat( filter.getValues() ).map( Value::toString ).containsExactly( "content1" );
            } );
    }

    @Test
    void invalidateSite()
    {
        final NodeQuery nodeQuery = verifyBasicInvalidate( () -> nodeCleanerBean.invalidateSite( "project1", "site1" ) );
        assertThat( nodeQuery.getQueryFilters().stream().filter( f -> f instanceof ValueFilter ) ).map( f -> (ValueFilter) f )
            .anySatisfy( filter -> {
                assertThat( filter.getFieldName() ).isEqualTo( "project" );
                assertThat( filter.getValues() ).map( Value::toString ).containsExactly( "project1" );
            } )
            .anySatisfy( filter -> {
                assertThat( filter.getFieldName() ).isEqualTo( "siteId" );
                assertThat( filter.getValues() ).map( Value::toString ).containsExactly( "site1" );
            } );
    }

    @Test
    void invalidateDomain()
    {
        final NodeQuery nodeQuery = verifyBasicInvalidate( () -> nodeCleanerBean.invalidateDomain( "example.com" ) );
        assertThat( nodeQuery.getQueryFilters().stream().filter( f -> f instanceof ValueFilter ) ).map( f -> (ValueFilter) f )
            .anySatisfy( filter -> {
                assertThat( filter.getFieldName() ).isEqualTo( "domain" );
                assertThat( filter.getValues() ).map( Value::toString ).containsExactly( "example.com" );
            } );
    }

    @Test
    void invalidatePathPrefix()
    {
        final NodeQuery nodeQuery = verifyBasicInvalidate( () -> nodeCleanerBean.invalidatePathPrefix( "example.com", "/path" ) );

        assertThat( nodeQuery.getQueryFilters().stream().filter( f -> f instanceof ValueFilter ) ).map( f -> (ValueFilter) f )
            .anySatisfy( filter -> {
                assertThat( filter.getFieldName() ).isEqualTo( "domain" );
                assertThat( filter.getValues() ).map( Value::toString ).containsExactly( "example.com" );
            } );

        final LogicalExpr constraint = (LogicalExpr) nodeQuery.getQuery().getConstraint();
        assertEquals( LogicalExpr.Operator.OR, constraint.getOperator() );
        assertThat( List.of( constraint.getLeft(), constraint.getRight() ) ).anySatisfy( operand -> {
            assertThat( operand ).isInstanceOf( CompareExpr.class );
            var op = ( (CompareExpr) operand );
            assertThat( op.getOperator() ).isEqualTo( CompareExpr.Operator.EQ );
            assertThat( op.getField().getFieldPath() ).isEqualTo( "path" );
            assertThat( op.getFirstValue().getValue().asString() ).isEqualTo( "/path" );

        } ).anySatisfy( operand -> {
            assertThat( operand ).isInstanceOf( CompareExpr.class );
            var op = ( (CompareExpr) operand );
            assertThat( op.getOperator() ).isEqualTo( CompareExpr.Operator.LIKE );
            assertThat( op.getField().getFieldPath() ).isEqualTo( "path" );
            assertThat( op.getFirstValue().getValue().asString() ).isEqualTo( "/path/*" );
        } );
    }

    @Test
    void purgeAll()
    {
        when( nodeService.findByQuery( any( NodeQuery.class ) ) ).thenReturn( FindNodesByQueryResult.create()
                                                                                  .addNodeHit( NodeHit.create()
                                                                                                   .nodeId( NodeId.from( "node1" ) )
                                                                                                   .build() )
                                                                                  .hits( 1 )
                                                                                  .totalHits( 1 )
                                                                                  .build() )
            .thenReturn( FindNodesByQueryResult.create().hits( 0 ).totalHits( 0 ).build() );

        nodeCleanerBean.purgeAll();

        verify( nodeService, times( 2 ) ).findByQuery( any( NodeQuery.class ) );

        final ArgumentCaptor<DeleteNodeParams> captor = captor();
        verify( nodeService ).delete( captor.capture() );
        assertThat( captor.getValue().getNodeId() ).asString().isEqualTo( "node1" );
    }

    @Test
    void deleteExcessNodes()
    {
        when( nodeService.findByQuery( any( NodeQuery.class ) ) ).thenReturn( FindNodesByQueryResult.create()
                                                                                  .addNodeHit( NodeHit.create()
                                                                                                   .nodeId( NodeId.from( "node1" ) )
                                                                                                   .build() )
                                                                                  .addNodeHit( NodeHit.create()
                                                                                                   .nodeId( NodeId.from( "node2" ) )
                                                                                                   .build() )
                                                                                  .hits( 2 )
                                                                                  .totalHits( 2 )
                                                                                  .build() )
            .thenReturn( FindNodesByQueryResult.create()
                             .addNodeHit( NodeHit.create().nodeId( NodeId.from( "node1" ) ).build() )
                             .hits( 1 )
                             .totalHits( 1 )
                             .build() );

        nodeCleanerBean.deleteExcessNodes( 1 );

        verify( nodeService, times( 2 ) ).findByQuery( any( NodeQuery.class ) );
        verify( nodeService ).refresh( RefreshMode.SEARCH );
        final ArgumentCaptor<DeleteNodeParams> captor = captor();
        verify( nodeService ).delete( captor.capture() );
        assertThat( captor.getValue().getNodeId() ).asString().isEqualTo( "node1" );
        verifyNoMoreInteractions( nodeService );
    }

    @Test
    void getProjectCacheSize()
    {
        when( nodeService.findByQuery( any( NodeQuery.class ) ) ).thenReturn(
            FindNodesByQueryResult.create().hits( 2 ).totalHits( 2 ).build() );
        final int projectCacheSize = nodeCleanerBean.getProjectCacheSize( "project1" );
        verify( nodeService ).findByQuery( any( NodeQuery.class ) );

        final ArgumentCaptor<NodeQuery> findByQueryCaptor = captor();
        verify( nodeService, times( 1 ) ).findByQuery( findByQueryCaptor.capture() );
        final NodeQuery nodeQuery = findByQueryCaptor.getValue();
        assertEquals( "/cache", nodeQuery.getParent().toString() );
        assertEquals( 0, nodeQuery.getSize() );

        assertThat( nodeQuery.getQueryFilters().stream().filter( f -> f instanceof ValueFilter ) ).map( f -> (ValueFilter) f )
            .anySatisfy( filter -> {
                assertThat( filter.getFieldName() ).isEqualTo( "project" );
                assertThat( filter.getValues() ).map( Value::toString ).containsExactly( "project1" );
            } );

        assertEquals( 2, projectCacheSize );
    }

    @Test
    void getContentCacheSize()
    {
        when( nodeService.findByQuery( any( NodeQuery.class ) ) ).thenReturn(
            FindNodesByQueryResult.create().hits( 2 ).totalHits( 2 ).build() );
        final int contentCacheSize = nodeCleanerBean.getContentCacheSize( "project1", "content1" );
        verify( nodeService ).findByQuery( any( NodeQuery.class ) );

        final ArgumentCaptor<NodeQuery> findByQueryCaptor = captor();
        verify( nodeService, times( 1 ) ).findByQuery( findByQueryCaptor.capture() );
        final NodeQuery nodeQuery = findByQueryCaptor.getValue();
        assertEquals( "/cache", nodeQuery.getParent().toString() );
        assertEquals( 0, nodeQuery.getSize() );

        assertThat( nodeQuery.getQueryFilters().stream().filter( f -> f instanceof ValueFilter ) ).map( f -> (ValueFilter) f )
            .anySatisfy( filter -> {
                assertThat( filter.getFieldName() ).isEqualTo( "project" );
                assertThat( filter.getValues() ).map( Value::toString ).containsExactly( "project1" );
            } )
            .anySatisfy( filter -> {
                assertThat( filter.getFieldName() ).isEqualTo( "contentId" );
                assertThat( filter.getValues() ).map( Value::toString ).containsExactly( "content1" );
            } );

        assertEquals( 2, contentCacheSize );
    }

    @Test
    void getSiteCacheSize()
    {
        when( nodeService.findByQuery( any( NodeQuery.class ) ) ).thenReturn(
            FindNodesByQueryResult.create().hits( 2 ).totalHits( 2 ).build() );
        final int siteCacheSize = nodeCleanerBean.getSiteCacheSize( "project1", "site1" );
        verify( nodeService ).findByQuery( any( NodeQuery.class ) );

        final ArgumentCaptor<NodeQuery> findByQueryCaptor = captor();
        verify( nodeService, times( 1 ) ).findByQuery( findByQueryCaptor.capture() );
        final NodeQuery nodeQuery = findByQueryCaptor.getValue();
        assertEquals( "/cache", nodeQuery.getParent().toString() );
        assertEquals( 0, nodeQuery.getSize() );

        assertThat( nodeQuery.getQueryFilters().stream().filter( f -> f instanceof ValueFilter ) ).map( f -> (ValueFilter) f )
            .anySatisfy( filter -> {
                assertThat( filter.getFieldName() ).isEqualTo( "project" );
                assertThat( filter.getValues() ).map( Value::toString ).containsExactly( "project1" );
            } )
            .anySatisfy( filter -> {
                assertThat( filter.getFieldName() ).isEqualTo( "siteId" );
                assertThat( filter.getValues() ).map( Value::toString ).containsExactly( "site1" );
            } );

        assertEquals( 2, siteCacheSize );
    }

    @Test
    void findScheduledForInvalidation()
    {
        final PropertyTree data = new PropertyTree();
        data.setInstant( "lastChecked", Instant.now().minus( 1, ChronoUnit.DAYS ) );
        when( nodeService.getByPath( any() ) ).thenReturn(
            Node.create().id( NodeId.from( "someId" ) ).parentPath( BoosterContext.SCHEDULED_PARENT_NODE ).data( data ).build() );

        when( nodeService.findByQuery( any( NodeQuery.class ) ) ).thenReturn(
                FindNodesByQueryResult.create().hits( 0 ).totalHits( 0 ).build() )
            .thenReturn( FindNodesByQueryResult.create().hits( 0 ).totalHits( 2 ).build() );

        final List<String> scheduled = nodeCleanerBean.findScheduledForInvalidation( List.of( "project1", "project2" ) );

        assertThat( scheduled ).containsExactly( "project2" );

        verify( nodeService ).getByPath( BoosterContext.SCHEDULED_PARENT_NODE );

        verify( nodeService, times( 2 ) ).findByQuery( any( NodeQuery.class ) );

        final ArgumentCaptor<UpdateNodeParams> captor = captor();
        verify( nodeService).update( captor.capture() );
        assertEquals( "someId", captor.getValue().getId().toString() );
    }

}
