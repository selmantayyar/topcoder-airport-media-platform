package com.airportmedia.platform.service.airline;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;

/** Periodically checks for delayed departures that have earned a goodwill voucher. */
@ApplicationScoped
public class GoodwillTicker {

    private final DelayGoodwillService goodwill;

    public GoodwillTicker(DelayGoodwillService goodwill) {
        this.goodwill = goodwill;
    }

    @Scheduled(every = "{app.airline.goodwill-tick}", concurrentExecution = ConcurrentExecution.SKIP)
    void tick() {
        goodwill.scan();
    }
}
