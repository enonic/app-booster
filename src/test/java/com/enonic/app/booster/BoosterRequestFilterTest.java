package com.enonic.app.booster;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.enonic.app.booster.concurrent.Collapser;
import com.enonic.app.booster.io.ByteSupply;
import com.enonic.app.booster.storage.NodeCacheStore;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoosterRequestFilterTest
{
    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    @Mock
    NodeCacheStore cacheStore;

    @Mock
    SiteConfigService siteConfigService;

    @Mock
    FilterChain filterChain;

    @Test
    void preconditionsFail()
        throws Exception
    {
        final BoosterRequestFilter filter = new BoosterRequestFilter( cacheStore, siteConfigService );
        filter.activate( mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() ) );

        var preconditionsConstruction =
            mockConstruction( Preconditions.class, ( mock, context ) -> when( mock.check( request ) ).thenReturn( false ) );
        try (preconditionsConstruction)
        {
            filter.doHandle( request, response, filterChain );
        }

        verify( filterChain ).doFilter( request, response );
        verifyNoMoreInteractions( filterChain );
    }

    @Test
    void cached()
        throws Exception
    {
        mockRequest();
        final BoosterRequestFilter filter = new BoosterRequestFilter( cacheStore, siteConfigService );
        filter.activate( mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() ) );

        final CacheItem cacheItem =
            new CacheItem( "https://example.com/", 200, "text/html", Map.of( "header1", List.of( "value1" ) ), Instant.now(), null, 1234,
                           "1234567890", ByteSupply.of( new ByteArrayOutputStream() ), ByteSupply.of( new ByteArrayOutputStream() ) );

        var preconditionsConstruction =
            mockConstruction( Preconditions.class, ( mock, context ) -> when( mock.check( request ) ).thenReturn( true ) );

        var cachedResponseWriterConstruction = mockConstruction( CachedResponseWriter.class );

        try (preconditionsConstruction; cachedResponseWriterConstruction)
        {
            when( cacheStore.generateCacheKey( "https://example.com/site/repo/branch/s" ) ).thenCallRealMethod();
            when( cacheStore.get( "1ddd92089d02d31e68f1c6db45db255c" ) ).thenReturn( cacheItem );

            filter.doHandle( request, response, filterChain );

            verify( cacheStore ).get( "1ddd92089d02d31e68f1c6db45db255c" );
            verify( cachedResponseWriterConstruction.constructed().get( 0 ) ).write( same( response ), same( cacheItem ) );
            verifyNoInteractions( filterChain );
        }
    }

    @Test
    void collapsed()
        throws Exception
    {
        mockRequest();

        final CacheItem collapsedItem =
            new CacheItem( "https://example.com/", 200, "text/html", Map.of( "header1", List.of( "value1" ) ), Instant.now(), null, 1234,
                           "1234567890", ByteSupply.of( new ByteArrayOutputStream() ), ByteSupply.of( new ByteArrayOutputStream() ) );

        final var latch = mock( Collapser.Latch.class );
        when( latch.get() ).thenReturn( collapsedItem );
        var collapserConstruction = mockConstruction( Collapser.class, (mock, context) -> when( mock.latch( "1ddd92089d02d31e68f1c6db45db255c" ) ).thenReturn( latch ) );

        final BoosterRequestFilter filter;
        try (collapserConstruction)
        {
            filter = new BoosterRequestFilter( cacheStore, siteConfigService );
        }

        filter.activate( mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() ) );

        final CacheItem cacheItem =
            new CacheItem( "https://example.com/", 200, "text/html", Map.of( "header1", List.of( "value1" ) ), Instant.EPOCH, Instant.EPOCH, 1234,
                           "1234567890", ByteSupply.of( new ByteArrayOutputStream() ), ByteSupply.of( new ByteArrayOutputStream() ) );

        var preconditionsConstruction =
            mockConstruction( Preconditions.class, ( mock, context ) -> when( mock.check( request ) ).thenReturn( true ) );

        var cachedResponseWriterConstruction = mockConstruction( CachedResponseWriter.class );

        try (preconditionsConstruction; cachedResponseWriterConstruction)
        {
            when( cacheStore.generateCacheKey( "https://example.com/site/repo/branch/s" ) ).thenCallRealMethod();
            when( cacheStore.get( "1ddd92089d02d31e68f1c6db45db255c" ) ).thenReturn( cacheItem );

            filter.doHandle( request, response, filterChain );
            verify( latch ).get();
            verify( latch ).unlock( any() );
            verify( cacheStore ).get( "1ddd92089d02d31e68f1c6db45db255c" );
            verify( cachedResponseWriterConstruction.constructed().get( 0 ) ).write( same( response ), same( collapsedItem ) );
            verifyNoInteractions( filterChain );
        }
    }

    void mockRequest()
    {
        when( request.getScheme() ).thenReturn( "https" );
        when( request.getServerName() ).thenReturn( "example.com" );
        when( request.getServerPort() ).thenReturn( 443 );
        when( request.getRequestURI() ).thenReturn( "/site/repo/branch/s" );
    }

}
