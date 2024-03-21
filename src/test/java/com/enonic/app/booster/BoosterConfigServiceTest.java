package com.enonic.app.booster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class BoosterConfigServiceTest
{
    @Test
    void getConfig()
    {
        final BoosterConfigService boosterConfigService = new BoosterConfigService();
        boosterConfigService.activate( mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() ) );
        assertNotNull( boosterConfigService.getConfig() );
    }
}
