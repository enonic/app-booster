package com.enonic.app.booster;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;

public final class UrlUtils
{
    private UrlUtils()
    {
    }

    public static String buildFullURL( final HttpServletRequest request, Set<String> excludeQueryParams )
    {
        // rebuild the URL from the request
        final String scheme = request.getScheme();
        final String serverName = ( request.getServerName().endsWith( "." )
            ? request.getServerName().substring( 0, request.getServerName().length() - 1 )
            : request.getServerName() ).toLowerCase( Locale.ROOT );
        final int serverPort = request.getServerPort();
        final String path = request.getRequestURI().toLowerCase( Locale.ROOT );

        final var params = request.getParameterMap();// we only support GET requests, no POST data can sneak in.

        final String queryString = normalizedQueryParams( params, excludeQueryParams );

        final StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append( scheme ).append( "://" ).append( serverName );
        if ( !( ( "http".equals( scheme ) && serverPort == 80 ) || ( "https".equals( scheme ) && serverPort == 443 ) ) )
        {
            urlBuilder.append( ":" ).append( serverPort );
        }
        urlBuilder.append( path );
        if ( !queryString.isEmpty() )
        {
            urlBuilder.append( "?" ).append( queryString );
        }

        return urlBuilder.toString();
    }


    public static String normalizedQueryParams( final Map<String, String[]> params, Set<String> exclude )
    {
        if ( params.isEmpty() )
        {
            return "";
        }

        final Escaper urlEscaper = UrlEscapers.urlFormParameterEscaper();
        return params.entrySet()
            .stream()
            .filter( entry -> !exclude.contains( entry.getKey() ) )
            .sorted( Map.Entry.comparingByKey() )
            .flatMap( entry -> Arrays.stream( entry.getValue() )
                .map( value -> urlEscaper.escape( entry.getKey() ) + "=" + urlEscaper.escape( value ) ) )
            .collect( Collectors.joining( "&" ) );
    }
}
