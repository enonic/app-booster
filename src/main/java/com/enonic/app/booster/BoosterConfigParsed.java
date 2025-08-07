package com.enonic.app.booster;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.enonic.app.booster.utils.SimpleCsvParser;

public record BoosterConfigParsed(long cacheTtlSeconds, Set<String> excludeQueryParams, boolean disableCacheStatusHeader, int cacheSize,
                                  Set<String> appsForceInvalidateOnInstall, Map<String, String> overrideHeaders, Set<String> cacheMimeTypes,
                                  Set<String> excludeUserAgents)
{
    public static BoosterConfigParsed parse( BoosterConfig config )
    {
        var cacheTtlSeconds = config.cacheTtl();
        var cacheSize = config.cacheSize();
        var disableCacheStatusHeader = config.disableCacheStatusHeader();

        var excludeQueryParams = Stream.concat( SimpleCsvParser.parseLine( config.excludeQueryParamsPreset() ).stream(),
                                                SimpleCsvParser.parseLine( config.excludeQueryParams() ).stream() )
            .map( String::trim )
            .filter( Predicate.not( String::isEmpty ) )
            .collect( Collectors.toUnmodifiableSet() );

        var appsForceInvalidateOnInstall = SimpleCsvParser.parseLine( config.appsForceInvalidateOnInstall() )
            .stream()
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

        var cacheMimeTypes = SimpleCsvParser.parseLine( config.cacheMimeTypes() )
            .stream()
            .map( String::trim )
            .map( s -> s.toLowerCase( Locale.ROOT ) )
            .filter( Predicate.not( String::isEmpty ) )
            .collect( Collectors.toUnmodifiableSet() );

        var excludeUserAgents = SimpleCsvParser.parseLine( config.excludeUserAgents() )
            .stream()
            .map( String::trim )
            .map( s -> s.toLowerCase( Locale.ROOT ) )
            .filter( Predicate.not( String::isEmpty ) )
            .collect( Collectors.toUnmodifiableSet() );

        return new BoosterConfigParsed( cacheTtlSeconds, excludeQueryParams, disableCacheStatusHeader, cacheSize, appsForceInvalidateOnInstall, overrideHeaders,
                                        cacheMimeTypes, excludeUserAgents );
    }
}
