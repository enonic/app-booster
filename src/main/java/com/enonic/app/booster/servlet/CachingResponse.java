package com.enonic.app.booster.servlet;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

public interface CachingResponse extends HttpServletResponse
{
    ByteArrayOutputStream getCachedBody();


    Map<String, List<String>> getCachedHeaders();
}
