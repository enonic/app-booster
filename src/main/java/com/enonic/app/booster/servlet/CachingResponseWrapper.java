package com.enonic.app.booster.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.DigestOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import com.enonic.app.booster.MessageDigests;

public final class CachingResponseWrapper
    extends HttpServletResponseWrapper
    implements CachingResponse
{
    String etag;

    final ByteArrayOutputStream gzipData = new ByteArrayOutputStream();

    final DigestOutputStream digestOutputStream;

    final GZIPOutputStream gzipOutputStream;

    int size;

    final Map<String, List<String>> headers = new LinkedHashMap<>();

    public CachingResponseWrapper( final HttpServletResponse response )
    {
        super( response );
        try
        {
            gzipOutputStream = new GZIPOutputStream( gzipData );
        }
        catch ( IOException e )
        {
            throw new UncheckedIOException( e );
        }
        digestOutputStream = new DigestOutputStream( gzipOutputStream, MessageDigests.sha256() );
    }

    @Override
    public ByteArrayOutputStream getCachedGzipBody()
    {
        try
        {
            digestOutputStream.close();
        }
        catch ( IOException e )
        {
            throw new UncheckedIOException( e );
        }
        return gzipData;
    }

    @Override
    public String getEtag()
    {
        try
        {
            digestOutputStream.close();
        }
        catch ( IOException e )
        {
            throw new UncheckedIOException( e );
        }

        if ( etag == null )
        {
            etag = HexFormat.of().formatHex( Arrays.copyOf( digestOutputStream.getMessageDigest().digest(), 16 ) );
        }

        return etag;
    }

    @Override
    public int getSize()
    {
        return size;
    }

    @Override
    public Map<String, List<String>> getCachedHeaders()
    {
        return headers;
    }

    @Override
    public void setHeader( final String name, final String value )
    {
        super.setHeader( name, value );
        setCachedHeader( name, value );
    }

    @Override
    public void addHeader( final String name, final String value )
    {
        super.addHeader( name, value );
        addCachedHeader( name, value );
    }

    @Override
    public void setDateHeader( final String name, final long date )
    {
        super.setDateHeader( name, date );
        setCachedHeader( name, formatDateTime( date ) );
    }

    @Override
    public void addDateHeader( final String name, final long date )
    {
        super.addDateHeader( name, date );
        addCachedHeader( name, formatDateTime( date ) );
    }

    private static String formatDateTime( final long date )
    {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format( ZonedDateTime.ofInstant( Instant.ofEpochMilli( date ), ZoneId.of( "GMT" ) ) );
    }

    @Override
    public void setIntHeader( final String name, final int value )
    {
        setHeader( name, Integer.toString( value ) );
    }

    @Override
    public void addIntHeader( final String name, final int value )
    {
        addHeader( name, Integer.toString( value ) );
    }

    private void setCachedHeader( String name, String value )
    {
        final String lowerCaseName = name.toLowerCase( Locale.ROOT );
        if ( value == null )
        {
            headers.remove( lowerCaseName );
        }
        else
        {
            headers.put( lowerCaseName, List.of( value ) );
        }
    }

    private void addCachedHeader( final String name, final String value )
    {
        final String nameLowerCase = name.toLowerCase( Locale.ROOT );
        if ( value != null )
        {
            if ( headers.containsKey( nameLowerCase ) )
            {
                headers.get( nameLowerCase ).add( value );
            }
            else
            {
                headers.put( nameLowerCase, new ArrayList<>( List.of( value ) ) );
            }
        }
    }

    @Override
    public ServletOutputStream getOutputStream()
        throws IOException
    {
        final ServletOutputStream delegate = super.getOutputStream();
        return new ServletOutputStream()
        {
            @Override
            public void setWriteListener( final WriteListener writeListener )
            {
                delegate.setWriteListener( writeListener );
            }

            @Override
            public boolean isReady()
            {
                return delegate.isReady();
            }

            @Override
            public void write( final int b )
                throws IOException
            {
                CachingResponseWrapper.this.size++;
                delegate.write( b );
                gzipOutputStream.write( b );
            }

            @Override
            public void write( final byte[] b, final int off, final int len )
                throws IOException
            {
                CachingResponseWrapper.this.size += len;
                delegate.write( b, off, len );
                gzipOutputStream.write( b );
            }
        };
    }
}
