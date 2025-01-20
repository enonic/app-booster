package com.enonic.app.booster.storage;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.enonic.xp.app.ApplicationKey;
import com.enonic.xp.data.PropertyTree;
import com.enonic.xp.node.FindNodesByQueryResult;
import com.enonic.xp.node.Node;
import com.enonic.xp.node.NodeId;
import com.enonic.xp.node.NodeQuery;
import com.enonic.xp.node.NodeService;
import com.enonic.xp.node.UpdateNodeParams;
import com.enonic.xp.project.Project;
import com.enonic.xp.project.ProjectName;
import com.enonic.xp.project.ProjectService;
import com.enonic.xp.project.Projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.captor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoosterProjectMatchersTest
{
    @Mock
    ProjectService projectService;

    @Mock
    NodeService nodeService;

    @InjectMocks
    BoosterProjectMatchers boosterProjectMatchers;

    @Test
    void findScheduledForInvalidation()
    {
        when( projectService.list() ).thenReturn( Projects.from( List.of( Project.create().name( ProjectName.from( "project1" ) ).build(),
                                                                          Project.create()
                                                                              .name( ProjectName.from( "project2" ) )
                                                                              .build() ) ) );

        final PropertyTree data = new PropertyTree();
        data.setInstant( "lastChecked", Instant.now().minus( 1, ChronoUnit.DAYS ) );
        when( nodeService.getByPath( any() ) ).thenReturn(
            Node.create().id( NodeId.from( "someId" ) ).parentPath( BoosterContext.SCHEDULED_PARENT_NODE ).data( data ).build() );

        when( nodeService.findByQuery( any( NodeQuery.class ) ) ).thenReturn(
                FindNodesByQueryResult.create().hits( 0 ).totalHits( 0 ).build() )
            .thenReturn( FindNodesByQueryResult.create().hits( 0 ).totalHits( 2 ).build() );

        final List<ProjectName> scheduled = boosterProjectMatchers.findScheduledForInvalidation();

        assertThat( scheduled ).containsExactly( ProjectName.from( "project2" ) );

        verify( nodeService ).getByPath( BoosterContext.SCHEDULED_PARENT_NODE );

        verify( nodeService, times( 2 ) ).findByQuery( any( NodeQuery.class ) );

        final ArgumentCaptor<UpdateNodeParams> captor = captor();
        verify( nodeService ).update( captor.capture() );
        assertEquals( "someId", captor.getValue().getId().toString() );
    }

    @Test
    void findByAppForInvalidation()
    {
        when( projectService.list() ).thenReturn( Projects.from( List.of( Project.create().name( ProjectName.from( "project1" ) ).build(),
                                                                          Project.create()
                                                                              .name( ProjectName.from( "project2" ) )
                                                                              .build() ) ) );

        when( nodeService.findByQuery( any( NodeQuery.class ) ) ).thenReturn(
                FindNodesByQueryResult.create().hits( 0 ).totalHits( 0 ).build() )
            .thenReturn( FindNodesByQueryResult.create().hits( 0 ).totalHits( 2 ).build() );

        final List<ProjectName> scheduled = boosterProjectMatchers.findByAppForInvalidation( List.of( ApplicationKey.from( "someApp" ) ) );

        assertThat( scheduled ).containsExactly( ProjectName.from( "project2" ) );

        verify( nodeService, times( 2 ) ).findByQuery( any( NodeQuery.class ) );
    }
}
