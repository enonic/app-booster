package com.enonic.app.booster;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class CollapserTest
{
    @Test
    void collapse_same_key()
        throws Exception
    {
        AtomicInteger counter = new AtomicInteger( 0 );
        final Collapser<Object> collapser = new Collapser<>();
        final CompletableFuture<Object> o1 = CompletableFuture.supplyAsync(
            () -> collapse( collapser, "key1", CollapserTest::expensiveCreateObject, ( v ) -> counter.incrementAndGet() ) );
        final CompletableFuture<Object> o2 = CompletableFuture.supplyAsync(
            () -> collapse( collapser, "key1", CollapserTest::expensiveCreateObject, ( v ) -> counter.incrementAndGet() ) );
        assertSame( o1.get(), o2.get() );
        assertEquals( 1, counter.get() );
    }

    @Test
    void collapse_different_keys()
        throws Exception
    {
        AtomicInteger counter = new AtomicInteger( 0 );
        final Collapser<Object> collapser = new Collapser<>();
        final CompletableFuture<Object> o1 = CompletableFuture.supplyAsync(
            () -> collapse( collapser, "key1", CollapserTest::expensiveCreateObject, ( v ) -> counter.incrementAndGet() ) );
        final CompletableFuture<Object> o2 = CompletableFuture.supplyAsync(
            () -> collapse( collapser, "key2", CollapserTest::expensiveCreateObject, ( v ) -> counter.incrementAndGet() ) );
        assertNotSame( o1.get(), o2.get() );
        assertEquals( 0, counter.get() );
    }

    private static Object expensiveCreateObject()
    {
        Object o = new Object();
        try
        {
            Thread.sleep( 1000 );
        }
        catch ( InterruptedException e )
        {
            Thread.currentThread().interrupt();
            throw new RuntimeException( e );
        }
        return o;
    }

    /*
     * Typical usage of the Collapser:
     * latch,
     * check, if already collapsed, return the value,
     * otherwise, execute the supplier,
     * unlock the latch.
     */
    public static <T> T collapse( final Collapser<T> collapser, final String key, final Supplier<T> supplier, Consumer<T> onCollapsed )
    {
        final Collapser.Latch<T> latch = collapser.latch( key );
        final T collapsed = latch.get();
        if ( collapsed != null )
        {
            onCollapsed.accept( collapsed );
            return collapsed;
        }
        T o = null;
        try
        {
            o = supplier.get();
        }
        finally
        {
            latch.unlock( o );
        }
        return o;
    }
}
