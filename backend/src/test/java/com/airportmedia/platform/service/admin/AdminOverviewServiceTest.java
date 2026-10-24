package com.airportmedia.platform.service.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.domain.model.output.AdminOverview;
import com.airportmedia.platform.domain.model.output.AdminOverview.ZoneMap;
import com.airportmedia.platform.service.SimulationClock;
import com.airportmedia.platform.service.SimulationService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
class AdminOverviewServiceTest {

    @Inject
    AdminOverviewService overview;

    @Inject
    IncidentMonitor monitor;

    @Inject
    SimulationClock clock;

    @Inject
    SimulationService simulation;

    @Test
    void kpisMapAndIncidentsDescribeTheLiveAirport() {
        clock.jumpTo(Instant.parse("2026-10-23T14:30:00Z"));
        monitor.scan();

        AdminOverview o = overview.overview();

        assertThat(o.kpis().screensTotal()).isEqualTo(21);
        assertThat(o.kpis().screensOnlinePct()).isBetween(0.0, 100.0);
        assertThat(o.kpis().activeCampaigns()).isPositive();
        assertThat(o.kpis().currency()).isEqualTo("USD");
        assertThat(o.kpis().averageLatencyMs()).isPositive();
        assertThat(o.kpis().openIncidents()).isPositive();

        assertThat(o.terminals()).hasSize(2);
        var zones = o.terminals().stream().flatMap(t -> t.zones().stream()).toList();
        assertThat(zones.stream().mapToInt(z -> z.screens().size()).sum()).isEqualTo(21);
        assertThat(zones).allSatisfy(z -> assertThat(z.heat()).isBetween(0.0, 1.0));
        assertThat(zones).extracting(ZoneMap::heat).contains(1.0);

        assertThat(o.incidents()).anyMatch(i -> i.type().equals("FEED_LAG"));
    }

    @Test
    void anInjectedDelayShowsUpAsADelayedDeparture() {
        clock.jumpTo(Instant.parse("2026-10-23T14:30:00Z"));
        long before = overview.overview().kpis().delayedDepartures();

        simulation.injectDelay(null, 30, null);

        assertThat(overview.overview().kpis().delayedDepartures()).isEqualTo(before + 1);
    }
}
