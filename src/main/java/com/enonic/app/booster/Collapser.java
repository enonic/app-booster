package com.enonic.app.booster;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Collapser<T>
{
    private static final Logger LOG = LoggerFactory.getLogger( Collapser.class );

    private static class LockWithResult<T>
    {
        final Lock lock = new ReentrantLock();

        final AtomicInteger counter = new AtomicInteger( 1 );

        volatile AtomicReference<T> valueRef;

    }

    // Size does not grow not more that number of Servlet threads.
    private final ConcurrentMap<String, LockWithResult<T>> locks = new ConcurrentHashMap<>();

    public T await( final String key )
    {
        final LockWithResult<T> countingLock = locks.compute( key, ( k, v ) -> {
            if ( v != null )
            {
                final int counter = v.counter.incrementAndGet();
                LOG.debug( "returning existing lock for key {} counter {}", key, counter );
                return v;
            }
            else
            {
                return new LockWithResult<>();
            }
        } );
        LOG.debug( "locking {}", key );
        countingLock.lock.lock();
        LOG.debug( "after lock {}, {}", key, countingLock.valueRef );
        return countingLock.valueRef != null ? countingLock.valueRef.get() : null;
    }

    public void signalAll( final String key, final T value )
    {
        final LockWithResult<T> lock = locks.get( key );
        if ( lock == null )
        {
            LOG.error( "No lock for key {}", key );
            return;
        }
        if ( lock.valueRef == null )
        {
            LOG.debug( "Setting value for key {}", key );
            lock.valueRef = new AtomicReference<>( value );
        }
        else
        {
            LOG.debug( "Value already set for key {}", key );
        }
        if (lock.counter.decrementAndGet() == 0) {
            LOG.debug( "deleting lock for key {}", key );
            locks.remove( key, lock );
        }
        lock.lock.unlock();
    }
}
