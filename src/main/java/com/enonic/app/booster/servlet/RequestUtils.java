package com.enonic.app.booster.servlet;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import static java.util.Objects.requireNonNullElseGet;

public final class RequestUtils
{
    private RequestUtils()
    {
    }

    public static RequestURL buildRequestURL( final HttpServletRequest request, final Set<String> excludeQueryParams )
    {
        // rebuild the URL from the request
        final String scheme = request.getScheme();
        final String serverName = request.getServerName().toLowerCase( Locale.ROOT );
        final int serverPort = request.getServerPort();
        final String path =
            requireNonNullElseGet( (String) request.getAttribute( RequestDispatcher.FORWARD_REQUEST_URI ), request::getRequestURI )
                .toLowerCase( Locale.ROOT );

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

        return new RequestURL( urlBuilder.toString(), serverName, path );
    }


    public static String normalizedQueryParams( final Map<String, String[]> params, Set<String> exclude )
    {
        if ( params.isEmpty() )
        {
            return "";
        }

        return params.entrySet()
            .stream()
            .filter( entry -> !exclude.contains( entry.getKey() ) )
            .sorted( Map.Entry.comparingByKey() )
            .flatMap( entry -> Arrays.stream( entry.getValue() )
                .map( value -> URLEncoder.encode( entry.getKey(), StandardCharsets.UTF_8 ) + "=" +
                    URLEncoder.encode( value, StandardCharsets.UTF_8 ) ) )
            .collect( Collectors.joining( "&" ) );
    }

    public static AcceptEncoding acceptEncoding( final HttpServletRequest request )
    {
        final var acceptEncodingHeaders = request.getHeaders( "Accept-Encoding" );
        // According to spec, if no header is present, it is equivalent to accepting all encodings
        // But we will follow the most common behavior - no header means no support

        boolean acceptGzip = false;
        if ( acceptEncodingHeaders != null )
        {
            // We want to prefer brotli over gzip, regardless of client preference
            while ( acceptEncodingHeaders.hasMoreElements() )
            {
                String acceptEncoding = acceptEncodingHeaders.nextElement();
                if ( acceptEncoding.contains( "br" ) && !acceptEncoding.contains( "br;q=0" ) )
                {
                    return AcceptEncoding.BROTLI;
                }
                else if ( acceptEncoding.contains( "gzip" ) && !acceptEncoding.contains( "gzip;q=0" ) )
                {
                    acceptGzip = true;
                }
            }
        }
        return acceptGzip ? AcceptEncoding.GZIP : AcceptEncoding.UNSPECIFIED;
    }

    public static boolean isComponentRequest( final HttpServletRequest request )
    {
        final String requestURI = request.getRequestURI();
        final int indexOfUnderscore = requestURI.indexOf( "/_/" );
        return indexOfUnderscore != -1 &&
            requestURI.regionMatches( indexOfUnderscore + "/_/".length(), "component/", 0, "component/".length() );
    }

    public static List<String> listHeaders( final HttpServletRequest request, String name )
    {
        return Collections.list( Objects.requireNonNullElse( request.getHeaders( name ), Collections.emptyEnumeration() ) );
    }

    public static List<String> listCookies( final HttpServletRequest request, String name )
    {
        if ( request.getCookies() == null )
        {
            return Collections.emptyList();
        }
        return Arrays.stream( request.getCookies() ).filter( c -> c.getName().equals( name ) ).map( Cookie::getValue ).toList();
    }

    public enum AcceptEncoding
    {
        GZIP, BROTLI, UNSPECIFIED
    }
}
