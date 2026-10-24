package com.airportmedia.platform.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.airportmedia.platform.common.exception.ErrorResponse.FieldError;
import com.airportmedia.platform.common.exception.FieldValidationException;
import com.airportmedia.platform.domain.model.output.MasterDataSchema;
import com.airportmedia.platform.domain.model.output.MasterDataSchema.Field;
import com.airportmedia.platform.domain.model.output.MasterDataSchema.Option;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MasterDataValidatorTest {

    private static final MasterDataSchema SCREEN = new MasterDataSchema("screens", "Screens", List.of(
            Field.text("code", "Code", true, 12),
            Field.number("latencyMs", "Latency (ms)", true, 0.0, 5000.0),
            Field.bool("featured", "Featured"),
            Field.choice("status", "Status", List.of(new Option("ONLINE", "Online"), new Option("OFFLINE", "Offline"))),
            Field.ref("zone", "Zone", List.of(new Option("7", "Pier D"))),
            Field.text("notes", "Notes", false, 20)));

    private static Map<String, Object> valid() {
        Map<String, Object> p = new HashMap<>();
        p.put("code", "  SCR-900 ");
        p.put("latencyMs", "120");
        p.put("featured", true);
        p.put("status", "ONLINE");
        p.put("zone", 7);
        return p;
    }

    private static List<String> failingFields(Map<String, Object> payload) {
        FieldValidationException e = catchThrowableOfType(FieldValidationException.class,
                () -> MasterDataValidator.validate(SCREEN, payload));
        return e.fieldErrors().stream().map(FieldError::field).toList();
    }

    @Test
    void coercesAValidPayloadIntoTypedValues() {
        Map<String, Object> clean = MasterDataValidator.validate(SCREEN, valid());

        assertThat(clean)
                .containsEntry("code", "SCR-900")
                .containsEntry("latencyMs", 120.0)
                .containsEntry("featured", true)
                .containsEntry("status", "ONLINE")
                .containsEntry("zone", 7L)
                .containsEntry("notes", null);
    }

    @Test
    void reportsEveryInvalidFieldAtOnce() {
        Map<String, Object> p = valid();
        p.put("code", "");                 // required
        p.put("latencyMs", "fast");       // not a number
        p.put("status", "EXPLODED");      // not an option
        p.put("zone", 99);                // unknown reference
        p.put("notes", "x".repeat(21));   // too long

        assertThat(failingFields(p)).containsExactlyInAnyOrder("code", "latencyMs", "status", "zone", "notes");
    }

    @Test
    void enforcesNumericBoundsAndMaxLength() {
        Map<String, Object> p = valid();
        p.put("latencyMs", -1);
        p.put("code", "SCR-0000000000");

        assertThat(failingFields(p)).containsExactlyInAnyOrder("latencyMs", "code");
    }

    @Test
    void missingRequiredFieldsAreReported() {
        assertThat(failingFields(new HashMap<>())).containsExactlyInAnyOrder("code", "latencyMs", "status", "zone");
    }
}
