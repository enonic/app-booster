package com.enonic.app.booster.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;

import com.google.common.io.ByteSource;

public abstract class ByteSupply
{
    public abstract int size();

    public abstract void writeTo( final OutputStream out )
        throws IOException;

    public abstract InputStream openStream()
        throws IOException;

    public static ByteSupply of( final ByteArrayOutputStream baos )
    {
        return new BaosByteSupply( baos );
    }

    public static ByteSupply of( final ByteSource byteSource )
    {
        return new ByteSourceByteSupply( byteSource );
    }

    private static final class BaosByteSupply
        extends ByteSupply
    {
        final ByteArrayOutputStream baos;

        public BaosByteSupply( final ByteArrayOutputStream baos )
        {
            this.baos = baos;
        }

        @Override
        public int size()
        {
            return baos.size();
        }

        @Override
        public void writeTo( final OutputStream out )
            throws IOException
        {
            baos.writeTo( out );
        }

        @Override
        public InputStream openStream()
        {
            return new ByteArrayInputStream( baos.toByteArray() );
        }
    }

    private static final class ByteSourceByteSupply
        extends ByteSupply
    {
        final ByteSource byteSource;

        public ByteSourceByteSupply( final ByteSource byteSource )
        {
            this.byteSource = byteSource;
        }

        @Override
        public int size()
        {
            try
            {
                return (int) byteSource.size();
            }
            catch ( IOException e )
            {
                throw new UncheckedIOException( e );
            }
        }

        @Override
        public void writeTo( final OutputStream out )
            throws IOException
        {
            byteSource.copyTo( out );
        }

        @Override
        public InputStream openStream()
            throws IOException
        {
                return byteSource.openStream();
        }
    }
}
