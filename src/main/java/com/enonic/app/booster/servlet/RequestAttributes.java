package com.enonic.app.booster.servlet;

import javax.servlet.http.HttpServletRequest;

import com.enonic.xp.portal.PortalRequest;

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
