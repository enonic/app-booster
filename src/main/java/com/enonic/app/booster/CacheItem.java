package com.enonic.app.booster;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.enonic.app.booster.io.ByteSupply;

public record CacheItem(int status, String contentType, Map<String, ? extends Collection<String>> headers, Instant cachedTime,
                        Instant expireTime, Integer age, Instant invalidatedTime, int contentLength, String etag,
                        List<EntryPattern> configBypassHeaders, List<EntryPattern> configBypassCookies, ByteSupply gzipData, ByteSupply brotliData)
{
}
