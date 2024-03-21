package com.enonic.app.booster.servlet;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletResponse;

import com.enonic.app.booster.StoreConditions;
import com.enonic.app.booster.io.ByteSupply;

public interface CachingResponse
    extends HttpServletResponse, AutoCloseable
{
    ByteSupply getCachedGzipBody();

    Optional<ByteSupply> getCachedBrBody();

    String getEtag();

    int getSize();

    Map<String, List<String>> getCachedHeaders();

    boolean isStore();

    ResponseFreshness getFreshness();
}
