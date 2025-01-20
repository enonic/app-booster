package com.enonic.app.booster.storage;

import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.enonic.app.booster.BoosterConfig;
import com.enonic.xp.node.DeleteNodeParams;
import com.enonic.xp.node.FindNodesByQueryResult;
import com.enonic.xp.node.NodeHit;
import com.enonic.xp.node.NodeId;
import com.enonic.xp.node.NodeQuery;
import com.enonic.xp.node.NodeService;
import com.enonic.xp.node.RefreshMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.captor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoosterScavengerTest
{
    @Mock
    NodeService nodeService;

    @Mock
    ScheduledExecutorService schedulerService;

    @Test
    void scavenge()
    {
        final BoosterConfig configMock = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        when( configMock.cacheSize() ).thenReturn( 1 );

        final BoosterScavenger boosterScavenger = new BoosterScavenger( nodeService, schedulerService );
        boosterScavenger.activate( configMock );


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

        boosterScavenger.scavenge();

        verify( nodeService, times( 2 ) ).findByQuery( any( NodeQuery.class ) );
        verify( nodeService ).refresh( RefreshMode.SEARCH );
        final ArgumentCaptor<DeleteNodeParams> captor = captor();
        verify( nodeService ).delete( captor.capture() );
        assertThat( captor.getValue().getNodeId() ).asString().isEqualTo( "node1" );
        verifyNoMoreInteractions( nodeService );
    }

}
