package com.enonic.app.booster;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public record BoosterConfigParsed(long cacheTtlSeconds, List<String> excludeQueryParams, boolean disableXBoosterCacheHeader, int cacheSize,
                                  List<String> appList)
{

    public static BoosterConfigParsed parse( BoosterConfig config )
    {
        var cacheTtlSeconds =
            ( config.cacheTtl() == null || config.cacheTtl().isBlank() ) ? Long.MAX_VALUE : Duration.parse( config.cacheTtl() ).toSeconds();
        var excludeQueryParams = Arrays.stream( config.excludeQueryParams().split( "," ) ).map( String::trim ).toList();
        var disableXBoosterCacheHeader = config.disableXBoosterCacheHeader();
        var cacheSize = config.cacheSize();
        var appList = Arrays.stream( config.appsInvalidateCacheOnStart().split( "," ) ).map( String::trim ).toList();

        return new BoosterConfigParsed( cacheTtlSeconds, excludeQueryParams, disableXBoosterCacheHeader, cacheSize, appList );
    }
}
