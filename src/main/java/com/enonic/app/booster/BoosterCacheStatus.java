package com.enonic.app.booster;


public record BoosterCacheStatus(String fwd, boolean isCollapsed, String detail)
{

    @Override
    public String toString()
    {
        // RFC 9211
        final StringBuilder sb = new StringBuilder( "Booster" );
        if ( fwd == null )
        {
            sb.append( "; hit" );
        }
        else
        {
            sb.append( "; fwd=" ).append( fwd );
            if ( isCollapsed )
            {
                sb.append( "; collapsed" );
            }
        }

        if ( detail != null && !detail.isBlank() )
        {
            sb.append( "; detail=" ).append( detail );
        }
        return sb.toString();
    }

    public static BoosterCacheStatus miss()
    {
        return new BoosterCacheStatus( "miss", false, null );
    }

    public static BoosterCacheStatus stale()
    {
        return new BoosterCacheStatus( "stale", false, null );
    }

    public static BoosterCacheStatus bypass( String detail )
    {
        return new BoosterCacheStatus( "bypass", false, detail );
    }

    public static BoosterCacheStatus collapsed()
    {
        return new BoosterCacheStatus( null, true, null );
    }

    public static BoosterCacheStatus hit()
    {
        return new BoosterCacheStatus( null, false, null );
    }
}
