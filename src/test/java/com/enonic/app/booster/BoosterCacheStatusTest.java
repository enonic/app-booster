package com.enonic.app.booster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoosterCacheStatusTest
{
    @Test
    void miss()
    {
        assertEquals( "Booster; fwd=miss",BoosterCacheStatus.miss().toString() );
    }

    @Test
    void stale()
    {
        assertEquals( "Booster; fwd=stale",BoosterCacheStatus.stale().toString());
    }

    @Test
    void bypass()
    {
        assertEquals( "Booster; fwd=bypass",BoosterCacheStatus.bypass(null).toString());
        assertEquals( "Booster; fwd=bypass; detail=REASON",BoosterCacheStatus.bypass("REASON").toString());
    }

    @Test
    void collapsed()
    {
        assertEquals( "Booster; fwd=miss; collapsed",BoosterCacheStatus.collapsed().toString());

    }

    @Test
    void hit()
    {
        assertEquals( "Booster; hit",BoosterCacheStatus.hit().toString());
    }
}
