package com.airportmedia.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.domain.model.output.ClockSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SimulationClockTest {

    private static final Instant START = Instant.parse("2026-10-23T14:00:00Z");

    private final AtomicReference<Instant> realNow = new AtomicReference<>(Instant.parse("2020-01-01T00:00:00Z"));
    private final SimulationClock clock = new SimulationClock(START, realNow::get);

    private void advanceRealSeconds(long seconds) {
        realNow.updateAndGet(t -> t.plusSeconds(seconds));
    }

    @Test
    void startsPausedAtTheConfiguredTime() {
        ClockSnapshot snap = clock.snapshot();

        assertThat(snap.simTime()).isEqualTo(START);
        assertThat(snap.speed()).isEqualTo(1);
        assertThat(snap.running()).isFalse();
    }

    @Test
    void whilePausedSimTimeDoesNotAdvance() {
        advanceRealSeconds(120);

        assertThat(clock.snapshot().simTime()).isEqualTo(START);
    }

    @Test
    void playingAdvancesSimTimeWithRealTimeAtOneX() {
        clock.play();
        advanceRealSeconds(30);

        assertThat(clock.snapshot().simTime()).isEqualTo(START.plusSeconds(30));
    }

    @Test
    void speedMultipliesSimTimeAdvance() {
        clock.play();
        clock.setSpeed(60);
        advanceRealSeconds(1);

        assertThat(clock.snapshot().simTime()).isEqualTo(START.plus(Duration.ofMinutes(1)));
        assertThat(clock.snapshot().speed()).isEqualTo(60);
    }

    @Test
    void pauseFreezesTimeThenPlayResumesFromThere() {
        clock.play();
        advanceRealSeconds(10);
        clock.pause();
        Instant frozen = clock.snapshot().simTime();

        advanceRealSeconds(100);
        assertThat(clock.snapshot().simTime()).isEqualTo(frozen);

        clock.play();
        advanceRealSeconds(5);
        assertThat(clock.snapshot().simTime()).isEqualTo(frozen.plusSeconds(5));
    }

    @Test
    void jumpMovesSimTimeImmediately() {
        Instant target = Instant.parse("2026-10-23T14:10:00Z");

        clock.jumpTo(target);

        assertThat(clock.snapshot().simTime()).isEqualTo(target);
    }
}
