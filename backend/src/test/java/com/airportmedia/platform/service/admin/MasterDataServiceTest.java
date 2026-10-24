package com.airportmedia.platform.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.airportmedia.platform.common.exception.FieldValidationException;
import com.airportmedia.platform.domain.entity.Screen;
import com.airportmedia.platform.service.SimulationClock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
class MasterDataServiceTest {

    @Inject
    MasterDataService masterData;

    @Inject
    SimulationClock clock;

    @Inject
    EntityManager em;

    @Test
    void everyMasterDataTypeHasASchemaAndRows() {
        assertThat(masterData.schemas()).extracting(s -> s.type())
                .containsExactly("vendors", "outlets", "airlines", "locations", "screens");
        for (var schema : masterData.schemas()) {
            assertThat(masterData.list(schema.type())).as(schema.type()).isNotEmpty();
        }
    }

    @Test
    void createsAndEditsARowThroughTheGenericApi() {
        Map<String, Object> created = masterData.create("airlines",
                new HashMap<>(Map.of("iataCode", "qx", "icaoCode", "qxa", "name", "Quixotic Air", "lowCost", true)));

        assertThat(created).containsEntry("iataCode", "QX").containsEntry("lowCost", true);

        Map<String, Object> edit = new HashMap<>(created);
        edit.put("name", "Quixotic Airways");
        Map<String, Object> updated = masterData.update("airlines", (Long) created.get("id"), edit);
        assertThat(updated).containsEntry("name", "Quixotic Airways");
    }

    @Test
    void rejectsADuplicateUniqueKeyAsAFieldError() {
        FieldValidationException e = catchThrowableOfType(FieldValidationException.class, () -> masterData.create(
                "airlines", new HashMap<>(Map.of("iataCode", "XY", "icaoCode", "XYX", "name", "Clash"))));

        assertThat(e.fieldErrors()).singleElement().satisfies(f -> assertThat(f.field()).isEqualTo("iataCode"));
    }

    @Test
    void changingAScreensStatusStampsWhenItChanged() {
        Instant now = Instant.parse("2026-10-23T15:00:00Z");
        clock.jumpTo(now);
        Screen screen = em.createQuery("from Screen where code = 'SCR-GATE42'", Screen.class).getSingleResult();
        Map<String, Object> row = new HashMap<>(masterData.list("screens").stream()
                .filter(r -> r.get("id").equals(screen.id)).findFirst().orElseThrow());
        row.put("status", "DEGRADED");

        masterData.update("screens", screen.id, row);

        assertThat(screen.statusChangedAt).isEqualTo(now);
    }
}
