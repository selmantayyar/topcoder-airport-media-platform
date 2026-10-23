package com.airportmedia.platform.service;

import com.airportmedia.platform.domain.model.output.ClockTick;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;

/**
 * Emits the live heartbeat consumed by every dashboard. For ticket 01 the source
 * is wall-clock time; ticket 04 swaps in the authoritative simulation clock behind
 * the same {@link ClockTick} stream.
 */
@ApplicationScoped
public class ClockService {

    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(1);

    public Multi<ClockTick> stream() {
        return ticks(DEFAULT_INTERVAL);
    }

    Multi<ClockTick> ticks(Duration interval) {
        return Multi.createFrom()
                .ticks()
                .every(interval)
                .map(n -> new ClockTick(n, Instant.now()));
    }
}
