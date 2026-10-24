package com.airportmedia.platform.common.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/** Anything unmapped → 500 without leaking internals; the cause is logged server-side. */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GenericExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        LOG.error("Unhandled error", exception);
        ErrorResponse body = ErrorResponse.of(500, "Internal Server Error", "An unexpected error occurred");
        return Response.status(500).type(MediaType.APPLICATION_JSON).entity(body).build();
    }
}
