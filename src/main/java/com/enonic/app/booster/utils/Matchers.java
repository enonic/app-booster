package com.enonic.app.booster.utils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import com.enonic.app.booster.servlet.RequestUtils;

public class Matchers
{
    private static final ConcurrentMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private Matchers()
    {
    }

    public static boolean matchesPattern( final String pattern, final boolean invert, final List<String> values )
    {
        for ( String value : values )
        {
            final boolean matches = PATTERN_CACHE.computeIfAbsent( pattern, Pattern::compile ).matcher( value ).matches();
            if ( matches ^ invert )
            {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesUrlPattern( final String pattern, final boolean invert, final String relativePath,
                                             final Set<String> excludeQueryParams, final Map<String, String[]> params )
    {
        final boolean patternHasQueryParameters = pattern.contains( "\\?" );
        final boolean patternMatches = PATTERN_CACHE.computeIfAbsent( pattern, Pattern::compile )
            .matcher( patternHasQueryParameters
                          ? relativePath + "?" + RequestUtils.normalizedQueryParams( params, excludeQueryParams )
                          : relativePath )
            .matches();
        return invert != patternMatches;
    }
}
