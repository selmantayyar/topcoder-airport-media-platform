package com.airportmedia.platform.service;

import com.airportmedia.platform.domain.model.output.ClockSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The single, server-authoritative simulation clock. Sim time is derived from real
 * elapsed time since the last state change, scaled by the speed multiplier, so every
 * client that reads a {@link #snapshot()} sees the same instant. The real-time source
 * is injected to keep the advancement math unit-testable.
 */
@ApplicationScoped
public class SimulationClock {

    private final Supplier<Instant> realNow;

    private Instant simAnchor;
    private Instant realAnchor;
    private int speed = 1;
    private boolean running = false;

    @Inject
    public SimulationClock(@ConfigProperty(name = "app.sim.start") Instant start) {
        this(start, Instant::now);
    }

    SimulationClock(Instant start, Supplier<Instant> realNow) {
        this.realNow = realNow;
        this.simAnchor = start;
        this.realAnchor = realNow.get();
    }

    public synchronized ClockSnapshot snapshot() {
        return new ClockSnapshot(currentSimTime(), speed, running);
    }

    public synchronized void play() {
        if (!running) {
            reanchor();
            running = true;
        }
    }

    public synchronized void pause() {
        if (running) {
            reanchor();
            running = false;
        }
    }

    public synchronized void setSpeed(int newSpeed) {
        reanchor();
        this.speed = newSpeed;
    }

    public synchronized void jumpTo(Instant target) {
        this.simAnchor = target;
        this.realAnchor = realNow.get();
    }

    private Instant currentSimTime() {
        if (!running) {
            return simAnchor;
        }
        Duration realElapsed = Duration.between(realAnchor, realNow.get());
        return simAnchor.plus(realElapsed.multipliedBy(speed));
    }

    /** Capture the current sim time as the new anchor so a state change doesn't jump time. */
    private void reanchor() {
        this.simAnchor = currentSimTime();
        this.realAnchor = realNow.get();
    }
}
