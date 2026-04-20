package com.enonic.app.booster;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoosterConfigParsedTest
{
    @Test
    void defaults()
    {
        BoosterConfig config = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        final BoosterConfigParsed parse = BoosterConfigParsed.parse( config );
        assertEquals( 3600, parse.cacheTtlSeconds() );
        assertEquals( 164, parse.excludeQueryParams().size() );
        assertTrue( parse.excludeQueryParams().containsAll(
            Set.of( "fbclid", "srsltid", "hsCtaTracking", "_hsenc", "__hssc", "__hstc", "__hsfp", "utm_source", "ttclid", "ScCid" ) ) );
        assertEquals( Set.of(), parse.appsForceInvalidateOnInstall() );
        assertEquals( 10000, parse.cacheSize() );
        assertFalse( parse.disableCacheStatusHeader() );
        assertEquals( Map.of(), parse.overrideHeaders() );
        assertEquals( Set.of( "text/html", "text/xhtml" ), parse.cacheMimeTypes() );
    }

    @Test
    void parse()
    {
        BoosterConfig config = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        when( config.excludeQueryParamsPreset() ).thenReturn( "b, a" );
        when( config.appsForceInvalidateOnInstall() ).thenReturn( "app2, app1" );
        when( config.cacheTtl() ).thenReturn( 86400L );
        when( config.cacheMimeTypes() ).thenReturn( "text/html, text/xhtml, application/json" );
        when( config.overrideHeaders() ).thenReturn( "\"Cache-Control: private, no-store\", \"X-Instance: \"\"jupiter\"\"\"" );
        final BoosterConfigParsed parse = BoosterConfigParsed.parse( config );
        assertEquals( Set.of( "a", "b" ), parse.excludeQueryParams() );
        assertEquals( Set.of( "app1", "app2" ), parse.appsForceInvalidateOnInstall() );
        assertEquals( 86400L, parse.cacheTtlSeconds() );
        assertEquals( Map.of( "Cache-Control", "private, no-store", "X-Instance", "\"jupiter\"" ), parse.overrideHeaders() );
        assertEquals( Set.of( "text/html", "text/xhtml", "application/json" ), parse.cacheMimeTypes() );
    }

    @Test
    void excludeQueryParamsPreset()
    {
        BoosterConfig config = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        when( config.excludeQueryParams() ).thenReturn( "c, d" );
        final BoosterConfigParsed parse = BoosterConfigParsed.parse( config );
        assertEquals( 166, parse.excludeQueryParams().size() );
        assertTrue( parse.excludeQueryParams().containsAll( Set.of( "c", "d", "fbclid", "srsltid", "hsCtaTracking" ) ) );
    }

    @Test
    void excludeQueryParamsSubtractFromPreset()
    {
        BoosterConfig config = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        when( config.excludeQueryParams() ).thenReturn( "-fbclid, my_custom" );
        final BoosterConfigParsed parse = BoosterConfigParsed.parse( config );
        assertFalse( parse.excludeQueryParams().contains( "fbclid" ) );
        assertTrue( parse.excludeQueryParams().contains( "my_custom" ) );
        assertTrue( parse.excludeQueryParams().contains( "srsltid" ) );
    }

    @Test
    void excludeQueryParamsSubtractUnknownIsNoOp()
    {
        BoosterConfig config = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        when( config.excludeQueryParams() ).thenReturn( "-nonexistent" );
        final BoosterConfigParsed parse = BoosterConfigParsed.parse( config );
        assertEquals( 164, parse.excludeQueryParams().size() );
        assertTrue( parse.excludeQueryParams().contains( "fbclid" ) );
    }

    @Test
    void excludeQueryParamsMixedAdditionsAndRemovals()
    {
        BoosterConfig config = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        when( config.excludeQueryParams() ).thenReturn( "custom, -fbclid, -_ga, other" );
        final BoosterConfigParsed parse = BoosterConfigParsed.parse( config );
        assertTrue( parse.excludeQueryParams().contains( "custom" ) );
        assertTrue( parse.excludeQueryParams().contains( "other" ) );
        assertFalse( parse.excludeQueryParams().contains( "fbclid" ) );
        assertFalse( parse.excludeQueryParams().contains( "_ga" ) );
    }

    @Test
    void excludeQueryParamsSubtractOnlyInExcludeNotPreset()
    {
        BoosterConfig config = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        when( config.excludeQueryParamsPreset() ).thenReturn( "a, -b, c" );
        final BoosterConfigParsed parse = BoosterConfigParsed.parse( config );
        assertEquals( Set.of( "a", "-b", "c" ), parse.excludeQueryParams() );
    }

}
