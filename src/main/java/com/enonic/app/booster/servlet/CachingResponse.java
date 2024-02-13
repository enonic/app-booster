package com.enonic.app.booster.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

public interface CachingResponse extends HttpServletResponse, AutoCloseable
{
    ByteArrayOutputStream getCachedGzipBody()
        throws IOException;

    ByteArrayOutputStream getCachedBrBody()
        throws IOException;

    String getEtag()
        throws IOException;

    int getSize();

    Map<String, List<String>> getCachedHeaders();
}
