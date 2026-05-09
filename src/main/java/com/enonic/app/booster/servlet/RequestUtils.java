package com.enonic.app.booster.servlet;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.commonjava.mimeparse.MIMEParse;

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
        final String path = requireNonNullElseGet( (String) request.getAttribute( RequestDispatcher.FORWARD_REQUEST_URI ),
                                                   request::getRequestURI ).toLowerCase( Locale.ROOT );

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

        if ( acceptEncodingHeaders == null )
        {
            return AcceptEncoding.UNSPECIFIED;
        }

        // Accept-Encoding is case-insensitive per RFC 9110; combine all header values
        final String combinedHeader = Collections.list( acceptEncodingHeaders )
            .stream()
            .collect( Collectors.joining( ", " ) )
            .toLowerCase( Locale.ROOT );

        if ( combinedHeader.isBlank() )
        {
            return AcceptEncoding.UNSPECIFIED;
        }

        // MIMEParse expects type/subtype format; prefix encoding tokens with a synthetic type
        final String mimeHeader = toMimeTypeHeader( combinedHeader );

        // bestMatch selects the highest-quality encoding the client accepts; brotli is listed last
        // so it wins as a tiebreaker when the client assigns equal quality to both
        final String best = MIMEParse.bestMatch( List.of( "encoding/gzip", "encoding/br" ), mimeHeader );
        if ( "encoding/br".equals( best ) )
        {
            return AcceptEncoding.BROTLI;
        }
        if ( "encoding/gzip".equals( best ) )
        {
            return AcceptEncoding.GZIP;
        }
        return AcceptEncoding.UNSPECIFIED;
    }

    private static String toMimeTypeHeader( final String encodingHeader )
    {
        return Arrays.stream( encodingHeader.split( "," ) )
            .map( String::trim )
            .filter( token -> !token.isEmpty() )
            .map( token -> {
                int semi = token.indexOf( ';' );
                String name = semi >= 0 ? token.substring( 0, semi ).trim() : token;
                String params = semi >= 0 ? token.substring( semi ) : "";
                return "encoding/" + name + params;
            } )
            .collect( Collectors.joining( ", " ) );
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
