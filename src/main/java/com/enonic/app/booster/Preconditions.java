package com.enonic.app.booster;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Preconditions
{
    private static final Logger LOG = LoggerFactory.getLogger( Preconditions.class );

    List<Function<HttpServletRequest, Result>> extraConditions;

    @SafeVarargs
    public Preconditions( Function<HttpServletRequest, Result>... extraConditions )
    {
        this.extraConditions = List.of( extraConditions );
    }

    public Result check( final HttpServletRequest request )
    {
        // Anything else except http and https (like ws, wss) we bypass.
        final String scheme = request.getScheme();
        if ( !scheme.equals( "http" ) && !scheme.equals( "https" ) )
        {
            LOG.debug( "Bypassing request with scheme {}", scheme );
            return Result.SILENT_BYPASS;
        }

        // Browsers use only GET then they visit pages. We also bypass HEAD requests but only for the case that we can serve response from cache.
        final String method = request.getMethod();
        if ( !"GET".equalsIgnoreCase( method ) && !"HEAD".equalsIgnoreCase( method ) )
        {
            LOG.debug( "Bypassing request with method {}", method );
            return Result.SILENT_BYPASS;
        }

        // If path contains /_/ it is a controller request. We don't cache them here at least for now.
        final String requestURI = request.getRequestURI();
        if ( requestURI.contains( "/_/" ) )
        {
            LOG.debug( "Bypassing request with uri {}", requestURI );
            return Result.SILENT_BYPASS;
        }

        // Authorization header indicates that request is personalized.
        // Don't cache even if later response has cache-control 'public' to information leak do to misconfiguration.
        final boolean hasAuthorization = request.getHeader( "Authorization" ) != null;
        if ( hasAuthorization )
        {
            LOG.debug( "Bypassing request with Authorization" );
            return Result.AUTHORIZATION_BYPASS;
        }

        // Session means that request is personalized.
        final boolean validSession = request.isRequestedSessionIdValid();
        if ( validSession )
        {
            LOG.debug( "Bypassing request with valid session" );
            return Result.SESSION_BYPASS;
        }

        for ( var extraCondition : extraConditions )
        {
            final Result result = extraCondition.apply( request );
            if ( !result.bypass )
            {
                return result;
            }
        }

        return Result.PROCEED;
    }

    public static class LicensePrecondition
    {
        final BooleanSupplier licenseCheck;

        public LicensePrecondition( final BooleanSupplier licenseCheck )
        {
            this.licenseCheck = licenseCheck;
        }

        public Result check( final HttpServletRequest request )
        {
            final boolean validLicense = licenseCheck.getAsBoolean();
            if ( !validLicense )
            {
                LOG.debug( "Bypassing request with invalid license" );
                return Result.LICENSE_BYPASS;
            }
            return Result.PROCEED;
        }
    }

    public record Result(boolean bypass, String detail)
    {
        public static final Result PROCEED = new Result( false, null );

        public static final Result SILENT_BYPASS = new Result( true, null );

        private static final Result SESSION_BYPASS = new Result( true, "SESSION" );

        private static final Result AUTHORIZATION_BYPASS = new Result( true, "AUTHORIZATION" );

        private static final Result LICENSE_BYPASS = new Preconditions.Result( true, "LICENSE" );
    }
}

