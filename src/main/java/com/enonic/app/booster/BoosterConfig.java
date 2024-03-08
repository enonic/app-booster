package com.enonic.app.booster;

public @interface BoosterConfig
{
    String cacheTtl() default "PT1H";

    int cacheSize() default 10_000;

    String appsInvalidateCacheOnStart() default "";

    String excludeQueryParams() default "fbclid, twclid, dclid, gclid, gclsrc, wbraid, gbraid, msclkid, yclid, _ga, _gl, utm_source, utm_medium, utm_campaign, utm_term, utm_source_platform, utm_creative_format, utm_marketing_tactic, _hsenc, __hssc, __hstc, __hsfp, hsCtaTracking";

    boolean disableXBoosterCacheHeader() default false;

    boolean disableAgeHeader() default false;

    String overrideHeaders() default "";

    String cacheMimeTypes() default "text/html, text/xhtml";
}
