package com.enonic.app.booster;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import com.enonic.app.booster.utils.Numbers;
import com.enonic.xp.app.ApplicationKey;
import com.enonic.xp.data.PropertyTree;
import com.enonic.xp.portal.PortalRequest;
import com.enonic.xp.site.Site;
import com.enonic.xp.site.SiteConfig;
import com.enonic.xp.site.SiteConfigs;

public final class BoosterSiteConfig
{
    private static final ConcurrentMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    public Integer defaultTTL;

    public List<PathPattern> patterns;

    public BoosterSiteConfig( final Integer defaultTTL, final List<PathPattern> patterns )
    {
        this.defaultTTL = defaultTTL;
        this.patterns = patterns;
    }

    private static final ApplicationKey APPLICATION_KEY = ApplicationKey.from( "com.enonic.app.booster" );

    public static BoosterSiteConfig getSiteConfig( final PortalRequest portalRequest )
    {
        if ( portalRequest == null )
        {
            return null;
        }
        final Site site = portalRequest.getSite();
        final SiteConfigs siteConfigs;
        if ( site != null )
        {
            siteConfigs = site.getSiteConfigs();
        }
        else
        {
            return null;
        }

        final SiteConfig siteConfig = siteConfigs.get( APPLICATION_KEY );
        if ( siteConfig == null )
        {
            return null;
        }

        final PropertyTree boosterConfig = siteConfig.getConfig();

        final Boolean disabled = boosterConfig.getBoolean( "disable" );
        if ( Boolean.TRUE.equals( disabled ) )
        {
            return null;
        }

        final List<PathPattern> patterns = StreamSupport.stream( boosterConfig.getSets( "patterns" ).spliterator(), false ).map( p -> {
            String pattern = p.getString( "pattern" );
            boolean invert = Boolean.TRUE.equals( p.getBoolean( "invert" ) );
            return new PathPattern( PATTERN_CACHE.computeIfAbsent( pattern, Pattern::compile ), invert );
        } ).toList();

        final Integer defaultTTL = Numbers.safeParseInteger( boosterConfig.getString( "defaultTTL" ) );
        return new BoosterSiteConfig( defaultTTL, patterns );
    }

    public static class PathPattern
    {
        public Pattern pattern;

        public boolean invert;

        public PathPattern( final Pattern pattern, final boolean invert )
        {
            this.pattern = pattern;
            this.invert = invert;
        }
    }
}
