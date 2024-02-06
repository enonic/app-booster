package com.enonic.app.booster;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

public final class MessageDigests
{
    private MessageDigests()
    {
    }

    public static String sha256_128( byte[] value )
    {
        final byte[] digest = sha256().digest( value );
        final byte[] truncated = Arrays.copyOf( digest, 16 );
        return HexFormat.of().formatHex( truncated );
    }

    public static MessageDigest sha256()
    {
        try
        {
            return MessageDigest.getInstance( "SHA-256" );
        }
        catch ( NoSuchAlgorithmException e )
        {
            throw new AssertionError( e );
        }
    }
}
