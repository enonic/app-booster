package com.enonic.app.booster;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.enonic.app.booster.storage.NodeCacheStore;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
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

    @Test
    void preconditionsFail()
        throws Exception
    {
        FilterChain chain = mock();
        final BoosterRequestFilter filter = new BoosterRequestFilter( cacheStore, siteConfigService );
        filter.activate( mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() ) );

        var preconditionsConstruction =
            mockConstruction( Preconditions.class, ( mock, context ) -> when( mock.check( request ) ).thenReturn( false ) );
        try (preconditionsConstruction)
        {
            filter.doHandle( request, response, chain );
        }

        verify( chain ).doFilter( request, response );
        verifyNoMoreInteractions( chain );
    }
}
