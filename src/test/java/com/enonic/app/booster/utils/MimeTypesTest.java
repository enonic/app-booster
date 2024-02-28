package com.enonic.app.booster.utils;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MimeTypesTest
{

    @Test
    void isContentTypeSupported()
    {
        assertTrue(MimeTypes.isContentTypeSupported( Set.of("text/*"), "text/html" ));
        assertTrue(MimeTypes.isContentTypeSupported( Set.of("text/html"), "text/html" ));
        assertTrue(MimeTypes.isContentTypeSupported( Set.of("text/xhtml"), "text/xhtml; charset=utf8" ));
        assertTrue(MimeTypes.isContentTypeSupported( Set.of("*/xhtml"), "text/xhtml; charset=utf8" ));
        assertTrue(MimeTypes.isContentTypeSupported( Set.of("*/html", "*/json"), "text/html; charset=utf8" ));

        assertFalse(MimeTypes.isContentTypeSupported( Set.of("text/*"), "application/json" ));
    }
}
