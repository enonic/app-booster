package com.enonic.app.booster.query;

import java.util.Collection;

public sealed interface Value
    permits Value.Single, Value.Multiple, Value.PathPrefix
{
    final class Multiple
        implements Value
    {
        Collection<String> values;

        Multiple( final Collection<String> values )
        {
            this.values = values;
        }

        public static Multiple of( final Collection<String> values )
        {
            return new Multiple( values );
        }
    }

    final class Single
        implements Value
    {
        String value;

        Single( final String value )
        {
            this.value = value;
        }

        public static Single of( final String value )
        {
            return new Single( value );
        }
    }

    final class PathPrefix
        implements Value
    {
        PathPrefix( final String value )
        {
            this.value = value;
        }

        String value;

        public static PathPrefix of( final String value )
        {
            return new PathPrefix( value );
        }
    }
}
