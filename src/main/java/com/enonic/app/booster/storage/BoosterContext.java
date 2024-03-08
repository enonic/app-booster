package com.enonic.app.booster.storage;

import java.util.concurrent.Callable;

import com.enonic.app.booster.io.RunnableWithException;
import com.enonic.xp.branch.Branch;
import com.enonic.xp.context.Context;
import com.enonic.xp.context.ContextAccessor;
import com.enonic.xp.context.ContextBuilder;
import com.enonic.xp.node.NodePath;
import com.enonic.xp.repository.RepositoryId;
import com.enonic.xp.security.RoleKeys;
import com.enonic.xp.security.auth.AuthenticationInfo;

public interface BoosterContext
{
    RepositoryId REPOSITORY_ID = RepositoryId.from( "com.enonic.app.booster" );
    NodePath CACHE_PARENT_NODE = NodePath.create().addElement( "cache" ).build();
    NodePath SCHEDULED_PARENT_NODE = NodePath.create().addElement( "scheduled" ).build();

    static void runInContext( final RunnableWithException runnable )
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

}
