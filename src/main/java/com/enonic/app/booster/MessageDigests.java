package com.enonic.app.booster;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class MessageDigests
{
    private MessageDigests()
    {
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
