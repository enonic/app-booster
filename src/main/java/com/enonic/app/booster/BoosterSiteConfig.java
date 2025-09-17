package com.enonic.app.booster;

import java.util.List;
import java.util.Objects;
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
    public Integer defaultTTL;

    public Integer componentTTL;

    public List<InvertablePattern> patterns;

    public List<EntryPattern> headers;

    public List<EntryPattern> cookies;

    public BoosterSiteConfig( final Integer defaultTTL, final Integer componentTTL, final List<InvertablePattern> patterns,
                              final List<EntryPattern> headers, final List<EntryPattern> cookies )
    {
        this.defaultTTL = defaultTTL;
        this.componentTTL = componentTTL;
        this.patterns = patterns;
        this.cookies = cookies;
        this.headers = headers;
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

        final List<InvertablePattern> patterns =
            StreamSupport.stream( boosterConfig.getSets( "patterns" ).spliterator(), false ).map( p -> {
                String pattern = Objects.requireNonNullElse( p.getString( "pattern" ), "" );
                boolean invert = Boolean.TRUE.equals( p.getBoolean( "invert" ) );
                return new InvertablePattern( pattern, invert );
            } ).toList();

        final List<EntryPattern> headers = EntryPatternMapper.mapEntryPatterns( boosterConfig.getSets( "bypassHeaders" ) );

        final List<EntryPattern> cookies = EntryPatternMapper.mapEntryPatterns( boosterConfig.getSets( "bypassCookies" ) );

        final Integer defaultTTL = Numbers.safeParseInteger( boosterConfig.getString( "defaultTTL" ) );
        final Integer componentTTL = Numbers.safeParseInteger( boosterConfig.getString( "componentTTL" ) );
        return new BoosterSiteConfig( defaultTTL, componentTTL, patterns, headers, cookies );
    }
}
