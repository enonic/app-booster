package com.enonic.app.booster;

public @interface BoosterConfig
{
    String cacheTtl() default "PT1H";

    int cacheSize() default 10_000;

    String appsInvalidateCacheOnStart() default "";

    String excludeQueryParams() default ""; //fbclid,_ga,utm_source,utm_medium,utm_campaign,utm_content,utm_term
}