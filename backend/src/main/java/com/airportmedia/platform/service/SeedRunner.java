package com.airportmedia.platform.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** Seeds the fictional airport-day on startup when the database is empty. */
@ApplicationScoped
public class SeedRunner {

    private static final Logger LOG = Logger.getLogger(SeedRunner.class);

    private final SeedService seedService;
    private final boolean onStart;

    public SeedRunner(
            SeedService seedService,
            @ConfigProperty(name = "app.seed.on-start", defaultValue = "true") boolean onStart) {
        this.seedService = seedService;
        this.onStart = onStart;
    }

    void onStart(@Observes StartupEvent event) {
        if (!onStart) {
            return;
        }
        if (seedService.alreadySeeded()) {
            LOG.info("Database already seeded; skipping.");
            return;
        }
        LOG.info("Seeding fictional airport-day...");
        seedService.seedAll();
    }
}
