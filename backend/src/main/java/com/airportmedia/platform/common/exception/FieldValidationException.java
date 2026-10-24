package com.airportmedia.platform.common.exception;

import com.airportmedia.platform.common.exception.ErrorResponse.FieldError;
import java.util.List;

/** Validation failures found outside Bean Validation (e.g. schema-driven master data, uniqueness). */
public class FieldValidationException extends RuntimeException {

    private final List<FieldError> fieldErrors;

    public FieldValidationException(List<FieldError> fieldErrors) {
        super("Validation failed");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public static FieldValidationException of(String field, String message) {
        return new FieldValidationException(List.of(new FieldError(field, message)));
    }

    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }
}
