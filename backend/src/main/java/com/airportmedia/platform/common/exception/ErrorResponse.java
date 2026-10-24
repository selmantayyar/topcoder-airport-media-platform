package com.airportmedia.platform.common.exception;

import java.util.List;

/** The single error shape every endpoint returns; {@code fieldErrors} is populated for validation failures. */
public record ErrorResponse(int status, String error, String message, List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {}

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, List.of());
    }
}
