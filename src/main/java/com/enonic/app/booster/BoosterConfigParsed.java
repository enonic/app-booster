package com.enonic.app.booster;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.enonic.app.booster.utils.SimpleCsvParser;

public record BoosterConfigParsed(long cacheTtlSeconds, Set<String> excludeQueryParams, boolean disableXBoosterCacheHeader,
                                  boolean disableAgeHeader, int cacheSize, Set<String> appList, Map<String, String> overrideHeaders,
                                  Set<String> cacheMimeTypes)
{

    public static BoosterConfigParsed parse( BoosterConfig config )
    {
        var cacheTtlSeconds =
            ( config.cacheTtl() == null || config.cacheTtl().isBlank() ) ? Long.MAX_VALUE : Duration.parse( config.cacheTtl() ).toSeconds();
        var excludeQueryParams = Arrays.stream( config.excludeQueryParams().split( " ", -1 ) )
            .map( String::trim )
            .filter( Predicate.not( String::isEmpty ) )
            .collect( Collectors.toUnmodifiableSet() );
        var disableXBoosterCacheHeader = config.disableXBoosterCacheHeader();
        var cacheSize = config.cacheSize();
        var appList = Arrays.stream( config.appsInvalidateCacheOnStart().split( " ", -1 ) )
            .map( String::trim )
            .filter( Predicate.not( String::isEmpty ) )
            .collect( Collectors.toUnmodifiableSet() );
        var overrideHeaders = config.overrideHeaders() == null
            ? Map.<String, String>of()
            : SimpleCsvParser.parseLine( config.overrideHeaders() )
                .stream()
                .map( s -> s.split( ":", 2 ) )
                .filter( a -> a.length == 2 )
                .collect( Collectors.toUnmodifiableMap( a -> a[0].trim(), a -> a[1].trim() ) );
        var disableAgeHeader = config.disableAgeHeader();
        var cacheMimeTypes = Arrays.stream( config.cacheMimeTypes().split( " ", -1 ) )
            .map( String::trim )
            .map( s -> s.toLowerCase( Locale.ROOT ) )
            .filter( Predicate.not( String::isEmpty ) )
            .collect( Collectors.toUnmodifiableSet() );
        return new BoosterConfigParsed( cacheTtlSeconds, excludeQueryParams, disableXBoosterCacheHeader, disableAgeHeader, cacheSize,
                                        appList, overrideHeaders, cacheMimeTypes );
    }
}
