package com.enonic.app.booster;

import java.time.Instant;
import java.util.Map;

import com.enonic.app.booster.io.BytesWriter;

public class CacheItem
{
    final String url;

    final String contentType;

    final BytesWriter gzipData;

    final Map<String, String[]> headers;

    final String etag;

    final Instant cachedTime;

    final int contentLength;

    public CacheItem( final String url, final String contentType, final Map<String, String[]> headers, final Instant cachedTime,
                      final int contentLength, final String etag, final BytesWriter gzipData )
    {
        this.url = url;
        this.contentType = contentType;
        this.headers = headers;
        this.cachedTime = cachedTime;
        this.contentLength = contentLength;
        this.etag = etag;
        this.gzipData = gzipData;
    }
}
