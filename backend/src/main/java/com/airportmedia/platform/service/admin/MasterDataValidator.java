package com.airportmedia.platform.service.admin;

import com.airportmedia.platform.common.enums.FieldType;
import com.airportmedia.platform.common.exception.ErrorResponse.FieldError;
import com.airportmedia.platform.common.exception.FieldValidationException;
import com.airportmedia.platform.domain.model.output.MasterDataSchema;
import com.airportmedia.platform.domain.model.output.MasterDataSchema.Field;
import com.airportmedia.platform.domain.model.output.MasterDataSchema.Option;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates and coerces a master-data form payload against its {@link MasterDataSchema}, so one generic
 * form can create and edit any type. Values become: TEXT → trimmed String, NUMBER → Double, BOOLEAN →
 * Boolean, ENUM → the option value, REF → the referenced row's Long id. Every bad field is reported at once.
 */
public final class MasterDataValidator {

    private MasterDataValidator() {}

    public static Map<String, Object> validate(MasterDataSchema schema, Map<String, Object> payload) {
        Map<String, Object> clean = new LinkedHashMap<>();
        List<FieldError> errors = new ArrayList<>();
        for (Field field : schema.fields()) {
            try {
                clean.put(field.name(), coerce(field, payload.get(field.name())));
            } catch (IllegalArgumentException e) {
                errors.add(new FieldError(field.name(), e.getMessage()));
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        return clean;
    }

    private static Object coerce(Field field, Object raw) {
        boolean blank = raw == null || (raw instanceof String s && s.isBlank());
        if (blank) {
            if (field.type() == FieldType.BOOLEAN) {
                return false;
            }
            if (field.required()) {
                throw new IllegalArgumentException(field.label() + " is required");
            }
            return null;
        }
        return switch (field.type()) {
            case TEXT -> text(field, raw.toString().trim());
            case NUMBER -> number(field, raw);
            case BOOLEAN -> raw instanceof Boolean b ? b : Boolean.parseBoolean(raw.toString());
            case ENUM -> option(field, raw.toString(), "must be one of " + labels(field.options()));
            case REF -> Long.valueOf(option(field, raw.toString(), "unknown " + field.label().toLowerCase()));
        };
    }

    private static String text(Field field, String value) {
        if (field.maxLength() != null && value.length() > field.maxLength()) {
            throw new IllegalArgumentException(field.label() + " must be at most " + field.maxLength() + " characters");
        }
        return value;
    }

    private static Double number(Field field, Object raw) {
        double value;
        try {
            value = raw instanceof Number n ? n.doubleValue() : Double.parseDouble(raw.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field.label() + " must be a number");
        }
        if (field.min() != null && value < field.min()) {
            throw new IllegalArgumentException(field.label() + " must be at least " + field.min());
        }
        if (field.max() != null && value > field.max()) {
            throw new IllegalArgumentException(field.label() + " must be at most " + field.max());
        }
        return value;
    }

    private static String option(Field field, String value, String message) {
        // A REF id posted as a JSON number may arrive as a Double ("7.0"); option values are id strings ("7").
        String normalised = value.endsWith(".0") ? value.substring(0, value.length() - 2) : value;
        return field.options().stream()
                .map(Option::value)
                .filter(normalised::equals)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(field.label() + " " + message));
    }

    private static String labels(List<Option> options) {
        return options.stream().map(Option::label).toList().toString();
    }
}
