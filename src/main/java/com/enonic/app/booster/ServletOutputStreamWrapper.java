package com.enonic.app.booster;

import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

public class ServletOutputStreamWrapper extends ServletOutputStream
{
    private final ServletOutputStream delegate;

    public ServletOutputStreamWrapper( ServletOutputStream delegate )
    {
        this.delegate = delegate;
    }

    @Override
    public boolean isReady()
    {
        return delegate.isReady();
    }

    @Override
    public void setWriteListener( final WriteListener writeListener )
    {
        delegate.setWriteListener( writeListener );
    }

    @Override
    public void write( final int b )
        throws IOException
    {
        delegate.write( b );
    }

    @Override
    public void write( final byte[] b, final int off, final int len )
        throws IOException
    {
        delegate.write( b, off, len );
    }
}
