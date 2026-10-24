package com.airportmedia.platform.domain.model.output;

import com.airportmedia.platform.common.enums.FieldType;
import java.util.List;

/**
 * Describes one master-data type (vendors, outlets, airlines, locations, screens) so a single generic
 * list + form component can render, edit and validate any of them. ENUM and REF fields carry their options.
 */
public record MasterDataSchema(String type, String label, List<Field> fields) {

    public record Field(
            String name,
            String label,
            FieldType type,
            boolean required,
            Integer maxLength,
            Double min,
            Double max,
            List<Option> options) {

        public static Field text(String name, String label, boolean required, int maxLength) {
            return new Field(name, label, FieldType.TEXT, required, maxLength, null, null, List.of());
        }

        public static Field number(String name, String label, boolean required, Double min, Double max) {
            return new Field(name, label, FieldType.NUMBER, required, null, min, max, List.of());
        }

        public static Field bool(String name, String label) {
            return new Field(name, label, FieldType.BOOLEAN, false, null, null, null, List.of());
        }

        public static Field choice(String name, String label, List<Option> options) {
            return new Field(name, label, FieldType.ENUM, true, null, null, null, options);
        }

        public static Field ref(String name, String label, List<Option> options) {
            return new Field(name, label, FieldType.REF, true, null, null, null, options);
        }
    }

    /** A selectable value: the stored {@code value} (enum name or row id) and what the user sees. */
    public record Option(String value, String label) {}
}
