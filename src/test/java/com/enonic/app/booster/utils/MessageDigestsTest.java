package com.enonic.app.booster.utils;

import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageDigestsTest
{

    @Test
    void sha256()
    {
        assertEquals( "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                      HexFormat.of().formatHex( MessageDigests.sha256().digest() ) );
    }
}
