package com.enonic.app.booster;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Preconditions
{
    private static final Logger LOG = LoggerFactory.getLogger( Preconditions.class );

    public boolean check( final HttpServletRequest request )
    {
        // Anything else except http and https (like ws, wss) we bypass.
        final String scheme = request.getScheme();
        if ( !scheme.equals( "http" ) && !scheme.equals( "https" ) )
        {
            LOG.debug( "Bypassing request with scheme {}", scheme );
            return false;
        }

        // Browsers use only GET then they visit pages. We also allow HEAD requests but only for the case that we can serve response from cache.
        final String method = request.getMethod();
        if ( !"GET".equalsIgnoreCase( method ) && !"HEAD".equalsIgnoreCase( method ) )
        {
            LOG.debug( "Bypassing request with method {}", method );
            return false;
        }

        // Authorization header indicates that request is personalized.
        // Don't cache even if later response has cache-control 'public' to information leak do to misconfiguration.
        final boolean hasAuthorization = request.getHeader( "Authorization" ) != null;
        if ( hasAuthorization )
        {
            LOG.debug( "Bypassing request with Authorization" );
            return false;
        }

        // Session means that request is personalized.
        final boolean validSession = request.isRequestedSessionIdValid();
        if ( validSession )
        {
            LOG.debug( "Bypassing request with valid session" );
            return false;
        }

        // If path contains /_/ it is a controller request. We don't cache them here at least for now.
        final String requestURI = request.getRequestURI();
        if ( requestURI.contains( "/_/" ) )
        {
            LOG.debug( "Bypassing request with uri {}", requestURI );
            return false;
        }
        return true;
    }
}
