package com.enonic.app.booster;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Collapser<T>
{

    // Size does not grow not more that number of Servlet threads.
    private final ConcurrentMap<String, LockWithResult> locks = new ConcurrentHashMap<>();

    public interface Latch<T>
    {
        T get();

        void unlock( final T value );
    }

    private class LockWithResult
        implements Latch<T>
    {
        final String key;

        final Lock lock = new ReentrantLock();

        final AtomicInteger counter = new AtomicInteger( 1 );

        final AtomicReference<Optional<T>> valueRef = new AtomicReference<>();

        public LockWithResult( final String key )
        {
            this.key = key;
        }

        @Override
        public T get()
        {
            return Objects.requireNonNullElse( valueRef.get(), Optional.<T>empty() ).orElse( null );
        }

        @Override
        public void unlock( final T value )
        {
            valueRef.compareAndSet( null, Optional.ofNullable( value ) );
            if ( counter.decrementAndGet() == 0 )
            {
                locks.remove( key, this );
            }
            lock.unlock();
        }
    }

    public Latch<T> latch( final String key )
    {
        final LockWithResult countingLock = locks.compute( key, ( k, v ) -> {
            if ( v != null )
            {
                v.counter.incrementAndGet();
                return v;
            }
            else
            {
                return new LockWithResult( k );
            }
        } );
        countingLock.lock.lock();
        return countingLock;
    }
}
