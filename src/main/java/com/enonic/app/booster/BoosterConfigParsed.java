package com.enonic.app.booster;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record BoosterConfigParsed(long cacheTtlSeconds, Set<String> excludeQueryParams, boolean disableXBoosterCacheHeader, int cacheSize,
                                  Set<String> appList, boolean preventDownstreamCaching)
{

    public static BoosterConfigParsed parse( BoosterConfig config )
    {
        var cacheTtlSeconds =
            ( config.cacheTtl() == null || config.cacheTtl().isBlank() ) ? Long.MAX_VALUE : Duration.parse( config.cacheTtl() ).toSeconds();
        var excludeQueryParams = Arrays.stream( config.excludeQueryParams().split( ",", -1 ) )
            .map( String::trim )
            .filter( Predicate.not( String::isEmpty ) )
            .collect( Collectors.toUnmodifiableSet() );
        var disableXBoosterCacheHeader = config.disableXBoosterCacheHeader();
        var cacheSize = config.cacheSize();
        var appList = Arrays.stream( config.appsInvalidateCacheOnStart().split( ",", -1 ) )
            .map( String::trim )
            .filter( Predicate.not( String::isEmpty ) )
            .collect( Collectors.toUnmodifiableSet() );
        var preventDownstreamCaching = config.preventDownstreamCaching();

        return new BoosterConfigParsed( cacheTtlSeconds, excludeQueryParams, disableXBoosterCacheHeader, cacheSize, appList,
                                        preventDownstreamCaching );
    }
}
