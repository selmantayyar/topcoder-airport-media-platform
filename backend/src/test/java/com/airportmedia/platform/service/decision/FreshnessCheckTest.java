package com.airportmedia.platform.service.decision;

import static com.airportmedia.platform.service.decision.DecisionFixtures.inputs;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FreshnessCheckTest {

    private final FreshnessCheck freshness = new FreshnessCheck();

    @Test
    void trustsInputsWhoseDataIsCurrent() {
        assertThat(freshness.untrustReason(inputs().dataAsOf(DecisionFixtures.NOW).build())).isNull();
    }

    @Test
    void rejectsInputsWithNoAsOfTimestamp() {
        assertThat(freshness.untrustReason(inputs().dataAsOf(null).build())).isNotBlank();
    }

    @Test
    void rejectsInputsOlderThanTheStalenessWindow() {
        var stale = inputs().now(DecisionFixtures.NOW)
                .dataAsOf(DecisionFixtures.NOW.minusSeconds(11 * 60))
                .build();

        assertThat(freshness.untrustReason(stale)).containsIgnoringCase("stale");
    }

    @Test
    void rejectsInputsWithNoCrowdEvidence() {
        // A fresh timestamp but no cited crowd/flight profile is "missing data" — must not score on it.
        assertThat(freshness.untrustReason(inputs().profile(null).build()))
                .containsIgnoringCase("no");
    }
}
