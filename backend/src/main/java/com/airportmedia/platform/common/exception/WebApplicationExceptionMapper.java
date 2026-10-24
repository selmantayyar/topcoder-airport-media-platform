package com.airportmedia.platform.common.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Not-found, bad-request, conflict and malformed-JSON errors keep their status, in the shared error shape. */
@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    @Override
    public Response toResponse(WebApplicationException exception) {
        Response.StatusType status = exception.getResponse().getStatusInfo();
        ErrorResponse body = ErrorResponse.of(status.getStatusCode(), status.getReasonPhrase(), exception.getMessage());
        return Response.status(status.getStatusCode()).type(MediaType.APPLICATION_JSON).entity(body).build();
    }
}
