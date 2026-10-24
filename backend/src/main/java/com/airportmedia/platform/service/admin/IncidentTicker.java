package com.airportmedia.platform.service.admin;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;

/** Runs the incident monitor on a fixed real-time tick (it evaluates against simulation time). */
@ApplicationScoped
public class IncidentTicker {

    private final IncidentMonitor monitor;

    public IncidentTicker(IncidentMonitor monitor) {
        this.monitor = monitor;
    }

    @Scheduled(every = "{app.admin.incident-tick}", concurrentExecution = ConcurrentExecution.SKIP)
    void tick() {
        monitor.scan();
    }
}
