package com.enonic.app.booster;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class BitesWriter
{
    private final ByteArrayOutputStream baos;

    public BitesWriter( final ByteArrayOutputStream baos )
    {
        this.baos = baos;
    }

    public int size()
    {
        return baos.size();
    }

    public void writeTo( final OutputStream out )
        throws IOException
    {
        baos.writeTo( out );
    }
}
