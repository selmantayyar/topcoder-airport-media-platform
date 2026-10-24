package com.airportmedia.platform.service.decision;

import com.airportmedia.platform.service.SimulationClock;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Drives the continuous pipeline: while the simulation clock is running, every tick re-decides the
 * online screens that are due (≥15 simulation-seconds since their last decision). Ticks are skipped
 * if one is still running, so a slow batch never overlaps itself.
 */
@ApplicationScoped
public class DecisionLoop {

    private final SimulationClock clock;
    private final DecisionService decisions;

    public DecisionLoop(SimulationClock clock, DecisionService decisions) {
        this.clock = clock;
        this.decisions = decisions;
    }

    @Scheduled(every = "{app.decision.tick}", concurrentExecution = ConcurrentExecution.SKIP)
    void tick() {
        if (clock.snapshot().running()) {
            decisions.runDueScreens();
        }
    }
}
