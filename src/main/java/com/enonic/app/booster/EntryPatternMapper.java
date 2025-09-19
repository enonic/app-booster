package com.enonic.app.booster;

import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

import com.enonic.xp.data.PropertySet;

public class EntryPatternMapper
{
    private EntryPatternMapper()
    {
    }

    public static List<EntryPattern> mapEntryPatterns( final Iterable<PropertySet> elements )
    {
        if ( elements == null )
        {
            return List.of();
        }
        return StreamSupport.stream( elements.spliterator(), false ).map( p -> {
            final String name = p.getString( "name" );
            final String pattern = Objects.requireNonNullElse( p.getString( "pattern" ), "" );
            final boolean invert = Boolean.TRUE.equals( p.getBoolean( "invert" ) );
            return new EntryPattern( name, pattern, invert );
        } ).toList();
    }
}
