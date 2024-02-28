package com.enonic.app.booster.utils;

import java.util.Set;

public final class MimeTypes
{
    private MimeTypes()
    {
    }

    public static boolean isContentTypeSupported( final Set<String> mimeTypes, final String contentType )
    {
        if ( !contentType.contains( "/" ) )
        {
            return false;
        }

        int semicolonIndex = contentType.indexOf( ';' );
        int endIdx = semicolonIndex != -1 ? semicolonIndex : contentType.length();
        String baseContentType;
        baseContentType = contentType.substring( 0, endIdx ).trim();

        for ( String mimeType : mimeTypes )
        {
            // Direct match (case-insensitive)
            if ( mimeType.equalsIgnoreCase( baseContentType ) )
            {
                return true;
            }

            // Wildcard type/subtype match
            if ( mimeType.endsWith( "/*" ) )
            {
                String baseType = mimeType.substring( 0, mimeType.length() - 1 );
                if ( baseContentType.regionMatches( true, 0, baseType, 0, baseType.length() ) )
                {
                    return true;
                }
            }
            if ( mimeType.startsWith( "*/" ) )
            {
                String subType = mimeType.substring( 2 );
                if ( baseContentType.regionMatches( true, baseContentType.indexOf( '/' ) + 1, subType, 0, subType.length() ) )
                {
                    return true;
                }
            }

        }
        return false;
    }
}
