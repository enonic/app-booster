package com.enonic.app.booster.servlet;

import java.util.Enumeration;

final class AcceptEncodingParser
{
    private AcceptEncodingParser()
    {
    }

    static RequestUtils.AcceptEncoding parse( final Enumeration<String> headers )
    {
        if ( headers == null )
        {
            return RequestUtils.AcceptEncoding.UNSPECIFIED;
        }

        boolean acceptBrotli = false;
        boolean gzipListed = false;
        boolean gzipAccepted = false;
        boolean wildcardAccepted = false;

        while ( headers.hasMoreElements() )
        {
            final String header = headers.nextElement();
            if ( header == null )
            {
                continue;
            }
            int i = 0;
            final int len = header.length();
            while ( i < len )
            {
                final int idx = header.indexOf( ',', i );
                final int tokenEnd = idx < 0 ? len : idx;
                switch ( parseToken( header, i, tokenEnd ) )
                {
                    case BROTLI:
                        acceptBrotli = true;
                        break;
                    case GZIP_ACCEPT:
                        gzipListed = true;
                        gzipAccepted = true;
                        break;
                    case GZIP_REJECT:
                        gzipListed = true;
                        break;
                    case WILDCARD_ACCEPT:
                        wildcardAccepted = true;
                        break;
                    case NONE:
                        break;
                }
                i = tokenEnd + 1;
            }
        }

        if ( acceptBrotli )
        {
            return RequestUtils.AcceptEncoding.BROTLI;
        }
        // RFC 9110 §12.5.3: `*` matches only codings not explicitly listed.
        // Policy: wildcard never elevates brotli — only gzip.
        final boolean acceptGzip = gzipAccepted || ( wildcardAccepted && !gzipListed );
        return acceptGzip ? RequestUtils.AcceptEncoding.GZIP : RequestUtils.AcceptEncoding.UNSPECIFIED;
    }

    private enum Outcome
    {
        BROTLI, GZIP_ACCEPT, GZIP_REJECT, WILDCARD_ACCEPT, NONE
    }

    private static Outcome parseToken( final String s, final int start, final int end )
    {
        int b = start;
        int e = end;
        while ( b < e && isOws( s.charAt( b ) ) )
        {
            b++;
        }
        while ( e > b && isOws( s.charAt( e - 1 ) ) )
        {
            e--;
        }
        if ( b == e )
        {
            return Outcome.NONE;
        }

        int nameEnd = b;
        while ( nameEnd < e && s.charAt( nameEnd ) != ';' )
        {
            nameEnd++;
        }
        int nameTrimEnd = nameEnd;
        while ( nameTrimEnd > b && isOws( s.charAt( nameTrimEnd - 1 ) ) )
        {
            nameTrimEnd--;
        }

        final boolean isBrotli = equalsAsciiIgnoreCase( s, b, nameTrimEnd, "br" );
        final boolean isGzip = !isBrotli && equalsAsciiIgnoreCase( s, b, nameTrimEnd, "gzip" );
        // Wildcard `*` (RFC 9110 §12.5.3): treat as gzip when q>0 — never elevate brotli on a generic wildcard.
        final boolean isWildcard = !isBrotli && !isGzip && nameTrimEnd - b == 1 && s.charAt( b ) == '*';
        if ( !isBrotli && !isGzip && !isWildcard )
        {
            return Outcome.NONE;
        }

        boolean qPositive = true;
        boolean qSet = false;
        int p = nameEnd;
        while ( p < e )
        {
            // p points at ';'; advance past it and any OWS.
            do
            {
                p++;
            }
            while ( p < e && isOws( s.charAt( p ) ) );
            final int paramNameStart = p;
            while ( p < e && s.charAt( p ) != '=' && s.charAt( p ) != ';' )
            {
                p++;
            }
            int paramNameEnd = p;
            while ( paramNameEnd > paramNameStart && isOws( s.charAt( paramNameEnd - 1 ) ) )
            {
                paramNameEnd--;
            }
            final boolean isQ = !qSet && equalsAsciiIgnoreCase( s, paramNameStart, paramNameEnd, "q" );

            String value = null;
            if ( p < e && s.charAt( p ) == '=' )
            {
                p++;
                final int valueStart = p;
                while ( p < e && s.charAt( p ) != ';' )
                {
                    p++;
                }
                if ( isQ )
                {
                    value = s.substring( valueStart, p );
                }
            }
            if ( isQ )
            {
                qPositive = isPositiveQValue( value );
                qSet = true;
            }
        }

        if ( isBrotli )
        {
            return qPositive ? Outcome.BROTLI : Outcome.NONE;
        }
        if ( isGzip )
        {
            return qPositive ? Outcome.GZIP_ACCEPT : Outcome.GZIP_REJECT;
        }
        // isWildcard
        return qPositive ? Outcome.WILDCARD_ACCEPT : Outcome.NONE;
    }

    private static boolean isPositiveQValue( final String v )
    {
        if ( v == null || v.isEmpty() || v.length() > 5 )
        {
            return false;
        }
        final char c0 = v.charAt( 0 );
        if ( c0 != '0' && c0 != '1' )
        {
            return false;
        }
        for ( int i = 1; i < v.length(); i++ )
        {
            final char c = v.charAt( i );
            if ( c != '.' && ( c < '0' || c > '9' ) )
            {
                return false;
            }
        }
        try
        {
            final double q = Double.parseDouble( v );
            return q > 0.0 && q <= 1.0;
        }
        catch ( NumberFormatException e )
        {
            return false;
        }
    }

    private static boolean isOws( final char c )
    {
        return c == ' ' || c == '\t';
    }

    private static boolean equalsAsciiIgnoreCase( final String s, final int start, final int end, final String target )
    {
        return end - start == target.length()
            && s.regionMatches( true, start, target, 0, target.length() );
    }
}
