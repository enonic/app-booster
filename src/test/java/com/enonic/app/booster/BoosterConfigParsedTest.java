package com.enonic.app.booster;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals( Set.of(), parse.excludeQueryParams() );
        assertEquals( Set.of(), parse.appList() );
        assertEquals( 10000, parse.cacheSize() );
        assertFalse( parse.disableXBoosterCacheHeader() );
        assertEquals( Map.of(), parse.overrideHeaders() );
        assertEquals( Set.of("text/html", "text/xhtml"), parse.cacheMimeTypes() );
    }

    @Test
    void parse()
    {
        BoosterConfig config = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        when( config.excludeQueryParams() ).thenReturn( "b a" );
        when( config.appsInvalidateCacheOnStart() ).thenReturn( "app2 app1" );
        when( config.cacheTtl() ).thenReturn( "PT24H" );
        when( config.cacheMimeTypes() ).thenReturn( "text/html text/xhtml application/json" );
        when( config.overrideHeaders() ).thenReturn( "\"Cache-Control: private, no-store\", \"X-Instance: \"\"jupiter\"\"\"" );
        final BoosterConfigParsed parse = BoosterConfigParsed.parse( config );
        assertEquals( Set.of( "a", "b" ), parse.excludeQueryParams() );
        assertEquals( Set.of( "app1", "app2" ), parse.appList() );
        assertEquals( 86400, parse.cacheTtlSeconds() );
        assertEquals( Map.of( "Cache-Control", "private, no-store", "X-Instance", "\"jupiter\"" ), parse.overrideHeaders() );
        assertEquals( Set.of("text/html", "text/xhtml"), parse.cacheMimeTypes() );
    }
}
