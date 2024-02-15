package com.enonic.app.booster.io;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.common.io.ByteSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ByteSupplyTest
{
    @Test
    void ofByteArrayOutputStream()
        throws Exception
    {
        final ByteArrayOutputStream source = new ByteArrayOutputStream();
        source.write( "Hello, World!".getBytes( StandardCharsets.UTF_8 ) );
        ByteSupply byteSupply = ByteSupply.of( source );
        assertEquals( 13, byteSupply.size() );

        final ByteArrayOutputStream receiver = new ByteArrayOutputStream();
        byteSupply.writeTo( receiver );
        assertEquals( "Hello, World!", receiver.toString( StandardCharsets.UTF_8 ) );

        final InputStream inputStream = byteSupply.openStream();
        try (inputStream)
        {
            assertEquals( "Hello, World!", new String( inputStream.readAllBytes(), StandardCharsets.UTF_8 ) );
        }
    }

    @Test
    void ofByteSource()
        throws Exception
    {
        final ByteSupply byteSupply = ByteSupply.of( ByteSource.wrap( "Hello, World!".getBytes( StandardCharsets.UTF_8 ) ) );
        assertEquals( 13, byteSupply.size() );

        final ByteArrayOutputStream receiver = new ByteArrayOutputStream();
        byteSupply.writeTo( receiver );
        assertEquals( "Hello, World!", receiver.toString( StandardCharsets.UTF_8 ) );

        final InputStream inputStream = byteSupply.openStream();
        try (inputStream)
        {
            assertEquals( "Hello, World!", new String( inputStream.readAllBytes(), StandardCharsets.UTF_8 ) );
        }
    }

    @Test
    void asByteSource()
        throws Exception
    {
        final ByteArrayOutputStream source = new ByteArrayOutputStream();
        source.write( "Hello, World!".getBytes( StandardCharsets.UTF_8 ) );
        final ByteSupply byteSupply = ByteSupply.of( source );

        final ByteSource byteSource = ByteSupply.asByteSource( byteSupply );
        assertEquals( 13, byteSource.sizeIfKnown().get() );

        assertEquals( "Hello, World!", new String( byteSource.read(), StandardCharsets.UTF_8 ) );
    }
}
