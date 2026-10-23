package com.airportmedia.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.domain.model.output.ZonePresence;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * End-to-end over the seeded airport: presence is computed from real flights and queues and
 * visibly moves as the simulation clock advances — the acceptance bullet for the read view.
 */
@QuarkusTest
class ZonePresenceLiveTest {

    /** Around the Gate 42 worked example (XY501 departs ~14:55 after its 45-min delay). */
    private static final Instant BUSY = Instant.parse("2026-10-23T14:00:00Z");
    /** Pre-dawn: every flight is still more than two hours out, so gates are empty. */
    private static final Instant QUIET = Instant.parse("2026-10-23T02:00:00Z");

    @Inject
    ZonePresenceService presence;

    @Test
    void presenceReflectsFlightsNearBoardingAndChangesWithTheClock() {
        List<ZonePresence> busy = presence.snapshotAt(BUSY);
        List<ZonePresence> quiet = presence.snapshotAt(QUIET);

        assertThat(totalHeadcount(busy)).isGreaterThan(totalHeadcount(quiet));

        ZonePresence busiest = busy.get(0);
        assertThat(busiest.estimatedHeadcount()).isPositive();
        assertThat(busiest.profile()).isNotNull();
        assertThat(busiest.profile().tripPurpose().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(100);
    }

    @Test
    void everyZoneReportsAFiveMinuteWindowAnchoredAtOrBeforeNow() {
        for (ZonePresence zone : presence.snapshotAt(BUSY)) {
            assertThat(zone.windowMinutes()).isEqualTo(5);
            assertThat(zone.windowStart()).isBeforeOrEqualTo(BUSY);
        }
    }

    private static int totalHeadcount(List<ZonePresence> zones) {
        return zones.stream().mapToInt(ZonePresence::estimatedHeadcount).sum();
    }
}
