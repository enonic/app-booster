package com.enonic.app.booster.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import com.enonic.xp.index.IndexService;
import com.enonic.xp.node.NodeService;
import com.enonic.xp.repository.CreateRepositoryParams;
import com.enonic.xp.repository.RepositoryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class BoosterStorageActivatorTest
{
    @Mock
    RepositoryService repositoryService;

    @Mock
    IndexService indexService;

    @Mock
    NodeService nodeService;

    @Test
    void testActivate()
    {
        when( indexService.isMaster() ).thenReturn( true );
        when( indexService.waitForYellowStatus() ).thenReturn( true );
        new BoosterStorageActivator( indexService, repositoryService, nodeService );
        ArgumentCaptor<CreateRepositoryParams> paramsCaptor = ArgumentCaptor.forClass( CreateRepositoryParams.class );
        verify( repositoryService ).createRepository( paramsCaptor.capture() );

        assertThat( paramsCaptor.getValue() ).extracting( CreateRepositoryParams::getRepositoryId, CreateRepositoryParams::isTransient )
            .containsExactly( BoosterContext.REPOSITORY_ID, true );
    }
}
