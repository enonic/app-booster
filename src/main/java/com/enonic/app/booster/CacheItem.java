package com.enonic.app.booster;

import java.time.Instant;
import java.util.Map;

import com.enonic.app.booster.io.ByteSupply;

public record CacheItem(String url, String contentType, Map<String, String[]> headers, Instant cachedTime, int contentLength, String etag,
                        ByteSupply gzipData)
{
}
