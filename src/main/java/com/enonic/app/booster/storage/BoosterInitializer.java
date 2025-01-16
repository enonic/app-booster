package com.enonic.app.booster.storage;

import com.google.common.base.Preconditions;

import com.enonic.xp.index.ChildOrder;
import com.enonic.xp.init.ExternalInitializer;
import com.enonic.xp.node.CreateNodeParams;
import com.enonic.xp.node.NodePath;
import com.enonic.xp.node.NodeService;
import com.enonic.xp.query.expr.FieldOrderExpr;
import com.enonic.xp.query.expr.OrderExpr;
import com.enonic.xp.repository.CreateRepositoryParams;
import com.enonic.xp.repository.RepositoryService;


public class BoosterInitializer
    extends ExternalInitializer
{
    private final RepositoryService repositoryService;

    private final NodeService nodeService;

    public BoosterInitializer( final Builder builder )
    {
        super( builder );
        this.repositoryService = builder.repositoryService;
        this.nodeService = builder.nodeService;
    }

    @Override
    protected boolean isInitialized()
    {
        return BoosterContext.callInContext( () -> repositoryService.isInitialized( BoosterContext.REPOSITORY_ID ) &&
            nodeService.nodeExists( BoosterContext.CACHE_PARENT_NODE ) && nodeService.nodeExists( BoosterContext.SCHEDULED_PARENT_NODE ) );
    }

    @Override
    protected void doInitialize()
    {
        BoosterContext.runInContext( () -> {
            repositoryService.createRepository( CreateRepositoryParams.create().repositoryId( BoosterContext.REPOSITORY_ID ).build() );
            nodeService.create(
                CreateNodeParams.create().parent( NodePath.ROOT ).name( BoosterContext.CACHE_PARENT_NODE.getName() ).build() );
            nodeService.create( CreateNodeParams.create()
                                    .parent( NodePath.ROOT )
                                    .name( BoosterContext.SCHEDULED_PARENT_NODE.getName() )
                                    .childOrder(
                                        ChildOrder.create().add( FieldOrderExpr.create( "time", OrderExpr.Direction.DESC ) ).build() )
                                    .build() );
        } );
    }

    @Override
    protected String getInitializationSubject()
    {
        return "Booster repository";
    }

    public static Builder create()
    {
        return new Builder();
    }

    public static class Builder
        extends ExternalInitializer.Builder<Builder>
    {
        private RepositoryService repositoryService;

        private NodeService nodeService;

        public Builder setRepositoryService( final RepositoryService repositoryService )
        {
            this.repositoryService = repositoryService;
            return this;
        }

        public Builder setNodeService( final NodeService nodeService )
        {
            this.nodeService = nodeService;
            return this;
        }

        @Override
        protected void validate()
        {
            super.validate();
            Preconditions.checkNotNull( repositoryService );
        }

        public BoosterInitializer build()
        {
            validate();
            return new BoosterInitializer( this );
        }
    }
}
