package com.enonic.app.booster.servlet;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import javax.servlet.http.HttpServletRequest;

import com.enonic.xp.app.ApplicationKey;
import com.enonic.xp.portal.PortalRequest;
import com.enonic.xp.site.Site;
import com.enonic.xp.site.SiteConfig;
import com.enonic.xp.site.SiteConfigs;

public final class RequestAttributes
{
    private RequestAttributes()
    {
    }

    public static PortalRequest getPortalRequest( final HttpServletRequest request )
    {
        return (PortalRequest) request.getAttribute( PortalRequest.class.getName() );
    }
}
