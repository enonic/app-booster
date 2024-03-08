package com.enonic.app.booster;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;

@Component(service = BoosterConfigService.class, configurationPid = "com.enonic.app.booster")
public class BoosterConfigService
{

    private volatile BoosterConfigParsed config;

    @Activate
    @Modified
    public void activate( final BoosterConfig config )
    {
        this.config = BoosterConfigParsed.parse( config );
    }


    public BoosterConfigParsed getConfig()
    {
        return config;
    }
}
