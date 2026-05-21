# Booster: Cache Rendered Pages

Booster caches pages rendered with the Enonic framework/site engine. It is useful for sites that have a lot of traffic, or simply for getting faster pages.

## Deploy locally

```shell
./gradlew deploy
```

## Documentation

https://developer.enonic.com/docs/booster/stable

## Default excluded query parameters

Booster ships with a built-in preset of query parameters that are excluded from the cache key. The list is sourced from [mpchadwick/tracking-query-params-registry](https://github.com/mpchadwick/tracking-query-params-registry) — see that repository for the full, up-to-date list.

See `docs/configuration.adoc` for how to extend or shrink this default.
