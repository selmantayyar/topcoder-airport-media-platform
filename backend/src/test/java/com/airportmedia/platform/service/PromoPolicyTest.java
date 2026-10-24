package com.airportmedia.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PromoPolicyTest {

    private static final Instant VALID_UNTIL = Instant.parse("2026-10-23T18:00:00Z");

    @Test
    void boardingClosesFifteenMinutesBeforeDeparture() {
        assertThat(PromoPolicy.boardingCloses(Instant.parse("2026-10-23T14:55:00Z")))
                .isEqualTo(Instant.parse("2026-10-23T14:40:00Z"));
    }

    @Test
    void unlinkedCodeExpiresAtItsTimeBox() {
        assertThat(PromoPolicy.effectiveExpiry(VALID_UNTIL, null)).isEqualTo(VALID_UNTIL);
    }

    @Test
    void linkedCodeExpiresWhenBoardingClosesIfThatComesFirst() {
        assertThat(PromoPolicy.effectiveExpiry(VALID_UNTIL, Instant.parse("2026-10-23T14:55:00Z")))
                .isEqualTo(Instant.parse("2026-10-23T14:40:00Z"));
    }

    @Test
    void linkedCodeKeepsItsTimeBoxIfTheFlightLeavesLater() {
        assertThat(PromoPolicy.effectiveExpiry(VALID_UNTIL, Instant.parse("2026-10-23T22:00:00Z")))
                .isEqualTo(VALID_UNTIL);
    }
}
