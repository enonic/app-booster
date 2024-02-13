package com.enonic.app.booster;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

import com.enonic.app.booster.io.ByteSupply;

public record CacheItem(String url, String contentType, Map<String, ? extends Collection<String>> headers, Instant cachedTime, Instant invalidatedTime, int contentLength, String etag,
                        ByteSupply gzipData, ByteSupply brotliData)
{
}
