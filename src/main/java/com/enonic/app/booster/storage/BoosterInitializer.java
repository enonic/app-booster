package com.enonic.app.booster.storage;

import com.google.common.base.Preconditions;

import com.enonic.xp.init.ExternalInitializer;
import com.enonic.xp.repository.CreateRepositoryParams;
import com.enonic.xp.repository.RepositoryService;


public class BoosterInitializer
    extends ExternalInitializer
{
    private final RepositoryService repositoryService;


    public BoosterInitializer( final Builder builder )
    {
        super( builder );
        this.repositoryService = builder.repositoryService;
    }

    @Override
    protected boolean isInitialized()
    {
        return BoosterContext.callInContext( () -> repositoryService.isInitialized( BoosterContext.REPOSITORY_ID ) );
    }

    @Override
    protected void doInitialize()
    {
        BoosterContext.runInContext( () -> repositoryService.createRepository(
            CreateRepositoryParams.create().repositoryId( BoosterContext.REPOSITORY_ID ).build() ) );
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

        public Builder setRepositoryService( final RepositoryService repositoryService )
        {
            this.repositoryService = repositoryService;
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
