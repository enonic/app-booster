package com.enonic.app.booster;

public @interface BoosterConfig
{
    String cacheTtl() default "PT1H";

    int cacheSize() default 10_000;

    String appsInvalidateCacheOnStart() default "";

    String excludeQueryParams() default "";

    boolean disableXBoosterCacheHeader() default false;

    boolean disableAgeHeader() default false;

    String overrideHeaders() default "";

    String cacheMimeTypes() default "text/html text/xhtml";
}
