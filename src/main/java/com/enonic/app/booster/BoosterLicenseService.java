package com.enonic.app.booster;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.enonic.lib.license.LicenseDetails;
import com.enonic.lib.license.LicenseManager;

@Component(immediate = true, service = BoosterLicenseService.class)
public class BoosterLicenseService
{
    private volatile boolean validLicense;

    private final LicenseManager licenseManager;

    @Activate
    public BoosterLicenseService( @Reference final LicenseManager licenseManager )
    {
        this.licenseManager = licenseManager;
    }

    public boolean isValidLicense()
    {
        if ( validLicense )
        {
            return true;
        }
        final LicenseDetails licenseDetails = licenseManager.validateLicense( "com.enonic.app.booster" );
        validLicense = licenseDetails != null && !licenseDetails.isExpired();
        return validLicense;
    }
}
