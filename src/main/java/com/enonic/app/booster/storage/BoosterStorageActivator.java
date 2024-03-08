package com.enonic.app.booster.storage;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.enonic.xp.index.IndexService;
import com.enonic.xp.node.NodeService;
import com.enonic.xp.repository.RepositoryService;

@Component(immediate = true)
public class BoosterStorageActivator
{
    @Activate
    public BoosterStorageActivator( @Reference final IndexService indexService, @Reference final RepositoryService repositoryService,
                                    @Reference final NodeService nodeService )
    {
        BoosterInitializer.create()
            .setIndexService( indexService )
            .setRepositoryService( repositoryService )
            .setNodeService( nodeService )
            .build()
            .initialize();
    }
}
