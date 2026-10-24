package com.airportmedia.platform.service.admin.masterdata;

import com.airportmedia.platform.common.exception.FieldValidationException;
import com.airportmedia.platform.domain.model.output.MasterDataSchema.Option;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Small helpers shared by the master-data types for building rows and options. */
final class Rows {

    private Rows() {}

    /** A row from alternating key/value pairs; keeps insertion order and allows nulls. */
    static Map<String, Object> of(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }

    static <E extends Enum<E>> List<Option> options(Class<E> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(e -> new Option(e.name(), pretty(e.name())))
                .toList();
    }

    static String ref(Long id) {
        return id != null ? String.valueOf(id) : null;
    }

    static String text(Map<String, Object> values, String key) {
        return (String) values.get(key);
    }

    static double number(Map<String, Object> values, String key) {
        Object v = values.get(key);
        return v != null ? (Double) v : 0.0;
    }

    static long id(Map<String, Object> values, String key) {
        return (Long) values.get(key);
    }

    /** Fails the field if {@code existing} is a different row than the one being saved. */
    static <T> void requireUnique(Optional<T> existing, Long savingId, Function<T, Long> idOf, String field, String message) {
        existing.filter(other -> !idOf.apply(other).equals(savingId)).ifPresent(other -> {
            throw FieldValidationException.of(field, message);
        });
    }

    private static String pretty(String enumName) {
        if (enumName.equals("FNB")) {
            return "F&B";
        }
        String spaced = enumName.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
