package com.enonic.app.booster;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public record BoosterConfigParsed(long cacheTtlSeconds, Set<String> excludeQueryParams, boolean disableXBoosterCacheHeader, int cacheSize,
                                  Set<String> appList, boolean preventDownstreamCaching)
{

    public static BoosterConfigParsed parse( BoosterConfig config )
    {
        var cacheTtlSeconds =
            ( config.cacheTtl() == null || config.cacheTtl().isBlank() ) ? Long.MAX_VALUE : Duration.parse( config.cacheTtl() ).toSeconds();
        var excludeQueryParams =
            Arrays.stream( config.excludeQueryParams().split( "," ) ).map( String::trim ).collect( Collectors.toUnmodifiableSet() );
        var disableXBoosterCacheHeader = config.disableXBoosterCacheHeader();
        var cacheSize = config.cacheSize();
        var appList =
            Arrays.stream( config.appsInvalidateCacheOnStart().split( "," ) ).map( String::trim ).collect( Collectors.toUnmodifiableSet() );
        var preventDownstreamCaching = config.preventDownstreamCaching();

        return new BoosterConfigParsed( cacheTtlSeconds, excludeQueryParams, disableXBoosterCacheHeader, cacheSize, appList,
                                        preventDownstreamCaching );
    }
}
