package com.enonic.app.booster.servlet;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Optional;

import javax.servlet.http.HttpServletResponse;

import com.enonic.app.booster.utils.Numbers;

import static java.util.Objects.requireNonNullElseGet;

public record ResponseFreshness(Integer maxAge, Integer sMaxAge, boolean noStore, boolean privateCache, boolean noCache, Instant time, Integer age)
{
    public Instant expiresTime()
    {
        Integer effectiveMaxAge = null;
        if ( sMaxAge != null )
        {
            effectiveMaxAge = sMaxAge;
        }
        else if ( maxAge != null )
        {
            effectiveMaxAge = maxAge;
        }
        if ( effectiveMaxAge != null )
        {
            return time.plusSeconds( effectiveMaxAge - ( age == null ? 0 : age ) );
        }
        else
        {
            return null;
        }
    }

    public boolean notCacheable()
    {
        return noStore || noCache || privateCache;
    }

    public static ResponseFreshness build( final HttpServletResponse response )
    {
        final Instant time = Optional.ofNullable( response.getHeader( "Date" ) )
            .map( s -> DateTimeFormatter.RFC_1123_DATE_TIME.parse( s, Instant::from ) )
            .orElseGet( Instant::now );

        final Collection<String> cacheControls = response.getHeaders( "Cache-Control" );
        Integer maxAge = null;
        Integer sMaxAge = null;
        Integer age = null;
        boolean noStore = false;
        boolean noCache = false;
        boolean privateCache = false;


        for ( String cacheControl : cacheControls )
        {
            final String[] directives = cacheControl.split( "," );

            for ( String directive : directives )
            {
                directive = directive.trim();

                if ( directive.contains( "=" ) )
                {
                    String[] parts = directive.split( "=", 2 );
                    if ( parts.length != 2 )
                    {
                        continue;
                    }
                    final String name = parts[0].trim();
                    final String value = parts[1].trim();

                    switch ( name )
                    {
                        case "max-age" -> maxAge =  maxAge != null ? maxAge : Numbers.safeParseInteger( value );
                        case "s-maxage" -> sMaxAge = sMaxAge != null ? sMaxAge : Numbers.safeParseInteger( value );
                    }
                }
                else
                {
                    switch ( directive )
                    {
                        case "no-cache" -> noCache = true;
                        case "no-store" -> noStore = true;
                        case "private" -> privateCache = true;
                    }
                }
            }
        }

            final Integer safe = Numbers.safeParseInteger( response.getHeader( "Age" ) );
            if ( safe != null && safe > 0 )
            {
                age = safe;
            }

        return new ResponseFreshness( maxAge, sMaxAge, noStore, privateCache, noCache, time, age );
    }
}
