package com.enonic.app.booster;

import java.io.IOException;
import java.util.concurrent.Callable;

import com.enonic.xp.branch.Branch;
import com.enonic.xp.context.Context;
import com.enonic.xp.context.ContextAccessor;
import com.enonic.xp.context.ContextBuilder;
import com.enonic.xp.repository.RepositoryId;
import com.enonic.xp.security.RoleKeys;
import com.enonic.xp.security.auth.AuthenticationInfo;

public interface BoosterContext
{
    RepositoryId REPOSITORY_ID = RepositoryId.from( "com.enonic.app.booster" );

    static void runInContext( final IORunnable runnable )
    {
        callInContext( () -> {
            runnable.run();
            return null;
        } );
    }

    static <T> T callInContext( final Callable<T> callable )
    {
        final Context context = ContextAccessor.current();
        final AuthenticationInfo authenticationInfo =
            AuthenticationInfo.copyOf( context.getAuthInfo() ).principals( RoleKeys.ADMIN ).build();
        return ContextBuilder.from( context )
            .authInfo( authenticationInfo )
            .repositoryId( BoosterContext.REPOSITORY_ID )
            .branch( Branch.from( "master" ) )
            .build()
            .callWith( callable );
    }

    interface IORunnable
    {
        void run()
            throws IOException;
    }
}
