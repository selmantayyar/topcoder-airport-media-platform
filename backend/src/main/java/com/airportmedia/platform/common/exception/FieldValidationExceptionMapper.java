package com.airportmedia.platform.common.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Schema/uniqueness validation failures → 400 with field-level messages, same shape as Bean Validation. */
@Provider
public class FieldValidationExceptionMapper implements ExceptionMapper<FieldValidationException> {

    @Override
    public Response toResponse(FieldValidationException exception) {
        ErrorResponse body = new ErrorResponse(400, "Bad Request", exception.getMessage(), exception.fieldErrors());
        return Response.status(400).type(MediaType.APPLICATION_JSON).entity(body).build();
    }
}
