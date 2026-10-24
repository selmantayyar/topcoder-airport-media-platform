package com.airportmedia.platform.common.exception;

import com.airportmedia.platform.common.exception.ErrorResponse.FieldError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Comparator;
import java.util.List;

/** Bean Validation failures → 400 with one field-level message per violated constraint. */
@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<FieldError> fields = exception.getConstraintViolations().stream()
                .map(v -> new FieldError(leafName(v), v.getMessage()))
                .sorted(Comparator.comparing(FieldError::field))
                .toList();
        ErrorResponse body = new ErrorResponse(400, "Bad Request", "Validation failed", fields);
        return Response.status(400).type(MediaType.APPLICATION_JSON).entity(body).build();
    }

    private static String leafName(ConstraintViolation<?> violation) {
        String leaf = null;
        for (Path.Node node : violation.getPropertyPath()) {
            leaf = node.getName();
        }
        return leaf != null ? leaf : "";
    }
}
