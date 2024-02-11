package com.enonic.app.booster.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;

import com.google.common.net.MediaType;

public class BodyCachingResponse
    implements CachingResponse
{
    public ByteArrayOutputStream body = new ByteArrayOutputStream();

    private String contentType;

    private String charset;

    private int status;

    final Map<String, List<String>> headers = new LinkedHashMap<>();

    @Override
    public ByteArrayOutputStream getCachedGzipBody()
    {
        return body;
    }

    @Override
    public Map<String, List<String>> getCachedHeaders()
    {
        return headers;
    }

    @Override
    public String getEtag()
    {
        return "";
    }

    @Override
    public int getSize()
    {
        return 0;
    }

    @Override
    public void addCookie( final Cookie cookie )
    {
        //ignore
    }

    @Override
    public boolean containsHeader( final String name )
    {
        return headers.containsKey( name.toLowerCase( Locale.ROOT ) );
    }

    @Override
    public String encodeURL( final String url )
    {
        return url;
    }

    @Override
    public String encodeRedirectURL( final String url )
    {
        return url;
    }

    @Override
    public String encodeUrl( final String url )
    {
        return url;
    }

    @Override
    public String encodeRedirectUrl( final String url )
    {
        return url;
    }

    @Override
    public void sendError( final int sc, final String msg )
        throws IOException
    {
        status = sc;
    }

    @Override
    public void sendError( final int sc )
        throws IOException
    {
        status = sc;
    }

    @Override
    public void sendRedirect( final String location )
        throws IOException
    {
        status = 302;
    }

    @Override
    public void setDateHeader( final String name, final long date )
    {
        setHeader( name, DateTimeFormatter.RFC_1123_DATE_TIME.format(
            ZonedDateTime.ofInstant( Instant.ofEpochMilli( date ), ZoneId.of( "GMT" ) ) ) );
    }

    @Override
    public void addDateHeader( final String name, final long date )
    {
        addHeader( name, DateTimeFormatter.RFC_1123_DATE_TIME.format(
            ZonedDateTime.ofInstant( Instant.ofEpochMilli( date ), ZoneId.of( "GMT" ) ) ) );
    }

    @Override
    public void setHeader( final String name, final String value )
    {
        final String nameLowerCase = name.toLowerCase( Locale.ROOT );
        if ( value == null )
        {
            headers.remove( nameLowerCase );
        }
        else
        {
            headers.put( nameLowerCase, new ArrayList<>( List.of( value ) ) );
        }
    }

    @Override
    public void addHeader( final String name, final String value )
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
    public void setIntHeader( final String name, final int value )
    {
        setHeader( name, Integer.toString( value ) );
    }

    @Override
    public void addIntHeader( final String name, final int value )
    {
        addHeader( name, Integer.toString( value ) );
    }

    @Override
    public void setStatus( final int sc )
    {
        status = sc;
    }

    @Override
    public void setStatus( final int sc, final String sm )
    {
        status = sc;
    }

    @Override
    public int getStatus()
    {
        return status;
    }

    @Override
    public String getHeader( final String name )
    {
        return getHeaders( name ).stream().findFirst().orElse( null );
    }

    @Override
    public Collection<String> getHeaders( final String name )
    {
        final String nameLowerCase = name.toLowerCase( Locale.ROOT );
        return List.copyOf( Objects.requireNonNullElse( headers.get( nameLowerCase ), List.of() ) );
    }

    @Override
    public Collection<String> getHeaderNames()
    {
        return List.copyOf( headers.keySet() );
    }

    @Override
    public String getCharacterEncoding()
    {
        return charset;
    }

    @Override
    public String getContentType()
    {
        return contentType;
    }

    @Override
    public ServletOutputStream getOutputStream()
        throws IOException
    {
        return new ServletOutputStream()
        {
            @Override
            public boolean isReady()
            {
                return false;
            }

            @Override
            public void setWriteListener( final WriteListener writeListener )
            {
                //ignore
            }

            @Override
            public void write( final int b )
                throws IOException
            {
                body.write( b );
            }

            @Override
            public void write( final byte[] b, final int off, final int len )
                throws IOException
            {
                body.write( b, off, len );
            }
        };

    }

    @Override
    public PrintWriter getWriter()
        throws IOException
    {
        throw new IllegalStateException( "Only getOutputStream is allowed" );
    }

    @Override
    public void setCharacterEncoding( final String charset )
    {
        this.charset = charset;
    }

    @Override
    public void setContentLength( final int len )
    {
        //ignore
    }

    @Override
    public void setContentLengthLong( final long len )
    {
        //ignore
    }

    @Override
    public void setContentType( final String type )
    {
        final Charset charset = MediaType.parse( type ).charset().toJavaUtil().orElse( null );

        if ( charset != null )
        {
            this.charset = charset.name();
        }
        contentType = type;
    }

    @Override
    public void setBufferSize( final int size )
    {
        //ignore
    }

    @Override
    public int getBufferSize()
    {
        return 0;
    }

    @Override
    public void flushBuffer()
        throws IOException
    {
        //ignore
    }

    @Override
    public void resetBuffer()
    {
        body.reset();
    }

    @Override
    public boolean isCommitted()
    {
        return false;
    }

    @Override
    public void reset()
    {
        headers.clear();
        body.reset();
    }

    @Override
    public void setLocale( final Locale loc )
    {
        //ignore
    }

    @Override
    public Locale getLocale()
    {
        return Locale.getDefault();
    }
}
