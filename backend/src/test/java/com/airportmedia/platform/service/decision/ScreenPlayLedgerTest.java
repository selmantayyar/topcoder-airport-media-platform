package com.airportmedia.platform.service.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ScreenPlayLedgerTest {

    private static final Instant T0 = Instant.parse("2026-10-24T14:00:00Z");

    @Test
    void grantsPlaysUpToTheFrequencyCapInsideTheWindow() {
        ScreenPlayLedger ledger = new ScreenPlayLedger(60);

        assertThat(ledger.play(T0, 60, 6)).isEqualTo(6);
        assertThat(ledger.inWindow(T0.plusSeconds(15 * 60))).isEqualTo(6);
        assertThat(ledger.play(T0.plusSeconds(15 * 60), 60, 6)).isZero();
    }

    @Test
    void playsAgeOutOfTheWindowAndFreeTheCapAgain() {
        ScreenPlayLedger ledger = new ScreenPlayLedger(60);
        ledger.play(T0, 60, 6);

        assertThat(ledger.inWindow(T0.plusSeconds(60 * 60))).isZero();
        assertThat(ledger.play(T0.plusSeconds(60 * 60), 60, 6)).isEqualTo(6);
    }

    @Test
    void noCapGrantsEveryWantedPlay() {
        assertThat(new ScreenPlayLedger(60).play(T0, 60, 0)).isEqualTo(60);
    }
}
