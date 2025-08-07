package com.enonic.app.booster;

public @interface BoosterConfig
{
    long cacheTtl() default 3_600;

    int cacheSize() default 10_000;

    String appsForceInvalidateOnInstall() default "";

    String excludeQueryParamsPreset() default "fbclid, twclid, dclid, gclid, gclsrc, wbraid, gbraid, msclkid, yclid, _ga, _gl, utm_source, utm_medium, utm_campaign, utm_term, utm_source_platform, utm_creative_format, utm_marketing_tactic, _hsenc, __hssc, __hstc, __hsfp, hsCtaTracking";

    String excludeQueryParams() default "";

    boolean disableCacheStatusHeader() default false;

    String overrideHeaders() default "";

    String cacheMimeTypes() default "text/html, text/xhtml";

    String excludeUserAgents() default "facebookexternalhit, LinkedInBot, Twitterbot, WhatsApp, TelegramBot, Slackbot, Discordbot, Applebot, Googlebot, Bingbot, YandexBot, DuckDuckBot, Baiduspider, Sogou, 360Spider, AhrefsBot, SemrushBot, MJ12bot, DotBot, BLEXBot, Screaming Frog SEO Spider, rogerbot, exabot, ia_archiver, archive.org_bot, Wget, curl, PostmanRuntime, Apache-HttpClient, Java, Python, Go-http-client, node-fetch, axios, okhttp";
}
