package com.enonic.app.booster.concurrent;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public final class ThreadFactoryImpl
    implements ThreadFactory
{
    private final AtomicLong count = new AtomicLong( 1 );

    private final String namePattern;

    public ThreadFactoryImpl( final String namePattern )
    {
        this.namePattern = namePattern;
    }

    @Override
    public Thread newThread( final Runnable r )
    {
        final Thread thread = Executors.defaultThreadFactory().newThread( r );

        thread.setName( String.format( namePattern, count.getAndIncrement() ) );

        return thread;
    }
}
