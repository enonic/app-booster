package com.enonic.app.booster;

import org.junit.jupiter.api.Test;

import com.enonic.lib.license.LicenseDetails;
import com.enonic.lib.license.LicenseManager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoosterLicenseServiceTest
{
    @Test
    void isValidLicense()
    {
        final LicenseManager licenseManager = mock( LicenseManager.class );
        final LicenseDetails licenseDetails = mock( LicenseDetails.class );
        when( licenseDetails.isExpired() ).thenReturn( false );
        when( licenseManager.validateLicense( "enonic.platform.subscription" ) ).thenReturn( licenseDetails );
        final BoosterLicenseService boosterLicenseService = new BoosterLicenseService( licenseManager );
        assertTrue(boosterLicenseService.isValidLicense());
        assertTrue(boosterLicenseService.isValidLicense());

        verify( licenseManager, times( 1 ) ).validateLicense( "enonic.platform.subscription" );
    }

    @Test
    void isValidLicense_missing()
    {
        final LicenseManager licenseManager = mock( LicenseManager.class );
        when( licenseManager.validateLicense( "enonic.platform.subscription" ) ).thenReturn( null );
        final BoosterLicenseService boosterLicenseService = new BoosterLicenseService( licenseManager );
        assertFalse( boosterLicenseService.isValidLicense());
        assertFalse(boosterLicenseService.isValidLicense());

        verify( licenseManager, times( 2) ).validateLicense( "enonic.platform.subscription" );
    }

    @Test
    void isValidLicense_expired()
    {
        final LicenseManager licenseManager = mock( LicenseManager.class );
        final LicenseDetails licenseDetails = mock( LicenseDetails.class );
        when( licenseDetails.isExpired() ).thenReturn( true );

        when( licenseManager.validateLicense( "enonic.platform.subscription" ) ).thenReturn( licenseDetails );
        final BoosterLicenseService boosterLicenseService = new BoosterLicenseService( licenseManager );
        assertFalse(boosterLicenseService.isValidLicense());
        assertFalse(boosterLicenseService.isValidLicense());

        verify( licenseManager, times( 2) ).validateLicense( "enonic.platform.subscription" );
    }
}
