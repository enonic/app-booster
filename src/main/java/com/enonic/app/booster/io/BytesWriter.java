package com.enonic.app.booster.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;

import com.google.common.io.ByteSource;

public abstract class BytesWriter
{
    public abstract int size();

    public abstract void writeTo( final OutputStream out )
        throws IOException;

    public abstract InputStream openStream();

    public static BytesWriter of( final ByteArrayOutputStream baos )
    {
        return new BaosBytes( baos );
    }

    public static BytesWriter of( final ByteSource byteSource )
    {
        return new ByteSourceBytes( byteSource );
    }

    private static final class BaosBytes
        extends BytesWriter
    {
        final ByteArrayOutputStream baos;

        public BaosBytes( final ByteArrayOutputStream baos )
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

    private static final class ByteSourceBytes
        extends BytesWriter
    {
        final ByteSource byteSource;

        public ByteSourceBytes( final ByteSource byteSource )
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
        public InputStream openStream() {
            try
            {
                return byteSource.openStream();
            }
            catch ( IOException e )
            {
                throw new UncheckedIOException( e );
            }
        }
    }
}
