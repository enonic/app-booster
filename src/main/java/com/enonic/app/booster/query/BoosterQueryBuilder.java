package com.enonic.app.booster.query;

import java.time.Instant;
import java.util.Map;

import com.enonic.app.booster.storage.BoosterContext;
import com.enonic.xp.data.ValueFactory;
import com.enonic.xp.node.NodeQuery;
import com.enonic.xp.query.expr.CompareExpr;
import com.enonic.xp.query.expr.FieldExpr;
import com.enonic.xp.query.expr.FieldOrderExpr;
import com.enonic.xp.query.expr.LogicalExpr;
import com.enonic.xp.query.expr.OrderExpr;
import com.enonic.xp.query.expr.QueryExpr;
import com.enonic.xp.query.expr.ValueExpr;
import com.enonic.xp.query.filter.BooleanFilter;
import com.enonic.xp.query.filter.ExistsFilter;
import com.enonic.xp.query.filter.RangeFilter;
import com.enonic.xp.query.filter.ValueFilter;

public class BoosterQueryBuilder
{
    private BoosterQueryBuilder()
    {
    }

    public static NodeQuery queryNodes( final Map<String, Value> fields, final Instant cutOffTime,
                                        final boolean includeInvalidated, int size )
    {
        final NodeQuery.Builder builder = NodeQuery.create();
        builder.parent( BoosterContext.CACHE_PARENT_NODE );

        for ( Map.Entry<String, Value> entry : fields.entrySet() )
        {
            final Value value = entry.getValue();
            if ( value instanceof Value.Multiple multiple )
            {
                builder.addQueryFilter( ValueFilter.create().fieldName( entry.getKey() ).addValues( multiple.values ).build() );
            }
            else if ( value instanceof Value.PathPrefix pathPrefix )
            {
                final QueryExpr queryExpr = QueryExpr.from(
                    LogicalExpr.or( CompareExpr.eq( FieldExpr.from( entry.getKey() ), ValueExpr.string( pathPrefix.value ) ),
                                    CompareExpr.like( FieldExpr.from( entry.getKey() ), ValueExpr.string( pathPrefix.value + "/*" ) ) ) );
                builder.query( queryExpr );
            }
            else if ( value instanceof Value.Single single )
            {
                builder.addQueryFilter(
                    ValueFilter.create().fieldName( entry.getKey() ).addValue( ValueFactory.newString( single.value ) ).build() );
            }
            else
            {
                throw new IllegalArgumentException( "Unknown value type: " + value );
            }
        }

        if ( !includeInvalidated )
        {
            builder.addQueryFilter(
                BooleanFilter.create().mustNot( ExistsFilter.create().fieldName( "invalidatedTime" ).build() ).build() );
        }
        else
        {
            builder.addOrderBy( FieldOrderExpr.create( "invalidatedTime", OrderExpr.Direction.ASC ) );
        }

        if ( cutOffTime != null )
        {
            builder.addQueryFilter( RangeFilter.create().fieldName( "cachedTime" ).lt( ValueFactory.newDateTime( cutOffTime ) ).build() );
        }
        builder.addOrderBy( FieldOrderExpr.create( "cachedTime", OrderExpr.Direction.ASC ) ).size( size );

        return builder.build();
    }
}
