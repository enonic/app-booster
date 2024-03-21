package com.enonic.app.booster.utils;

public final class Numbers
{
    private Numbers()
    {
    }

    public static Integer safeParseInteger( final String str )
    {
        if ( str == null || str.isEmpty() )
        {
            return null;
        }
        try
        {
            return Integer.valueOf( str );
        }
        catch ( NumberFormatException e )
        {
            return null;
        }
    }

    public static Integer safeLongToInteger( final Long value, final Integer defaultValue )
    {
        if ( value == null || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE)
        {
            return defaultValue;
        }
        return value.intValue();
    }

    public static Long longValue( final Integer value )
    {
        return value == null ? null : value.longValue();
    }
}
