package com.enonic.app.booster;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Map;

public class CacheItem
{
    final String url;

    final String contentType;

    final ByteArrayOutputStream gzipData;

    final Map<String, String[]> headers;

    final String etag;

    final Instant cachedTime;

    final int contentLength;

    public CacheItem( final String url, final String contentType, final Map<String, String[]> headers, final Instant cachedTime,
                      final int contentLength, final String etag, final ByteArrayOutputStream gzipData )
    {
        this.url = url;
        this.contentType = contentType;
        this.headers = headers;
        this.cachedTime = cachedTime;
        this.contentLength = contentLength;
        this.gzipData = gzipData;
        this.etag = etag;
    }
}
