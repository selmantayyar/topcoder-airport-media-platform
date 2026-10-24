package com.airportmedia.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.common.enums.OutletType;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.Campaign;
import com.airportmedia.platform.domain.entity.Impression;
import com.airportmedia.platform.domain.entity.Outlet;
import com.airportmedia.platform.domain.entity.Screen;
import com.airportmedia.platform.domain.entity.Zone;
import com.airportmedia.platform.domain.model.output.DashboardMetrics;
import com.airportmedia.platform.domain.model.output.PassengerFlow;
import com.airportmedia.platform.domain.model.output.RetailerSummary;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The retailer dashboard aggregates the funnel and footfall from seeded + claimed data. */
@QuarkusTest
@TestTransaction
class RetailerDashboardServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T14:30:00Z");

    @Inject
    RetailerDashboardService dashboard;

    @Inject
    ClaimService claims;

    @Inject
    SimulationClock clock;

    @Inject
    EntityManager em;

    private Campaign dutyFreeCampaign() {
        return em.createQuery("from Campaign where name like 'Azure Duty-Free%'", Campaign.class)
                .setMaxResults(1).getResultList().get(0);
    }

    @Test
    void listsRetailCompaniesWithOutletCounts() {
        var retailers = dashboard.listRetailers();

        assertThat(retailers).isNotEmpty();
        assertThat(retailers).allMatch(r -> r.kind().equals("RETAILER") || r.kind().equals("FNB"));
        assertThat(retailers).anyMatch(r -> r.name().equals("Azure Duty-Free"));
        assertThat(retailers).anyMatch((RetailerSummary r) -> r.outletCount() > 0);
    }

    @Test
    void metricsAggregateTheFunnelAndAttributedRevenue() {
        clock.jumpTo(NOW);
        Campaign dutyFree = dutyFreeCampaign();
        Screen screen = em.createQuery("from Screen", Screen.class).setMaxResults(1).getResultList().get(0);
        for (int i = 0; i < 10; i++) {
            Impression imp = new Impression();
            imp.screen = screen;
            imp.campaign = dutyFree;
            imp.shownAt = NOW;
            em.persist(imp);
        }
        em.flush();
        claims.scan("AMP-PERFUME15");
        claims.claim("AMP-PERFUME15");

        DashboardMetrics metrics = dashboard.metrics(dutyFree.advertiser.id);

        assertThat(metrics.impressions()).isGreaterThanOrEqualTo(10);
        assertThat(metrics.scans()).isGreaterThanOrEqualTo(1);
        assertThat(metrics.redemptions()).isGreaterThanOrEqualTo(1);
        assertThat(metrics.conversionRate()).isBetween(0.0, 1.0);
        assertThat(metrics.attributedRevenue())
                .isEqualByComparingTo(BigDecimal.valueOf(metrics.redemptions() * 45.0));
        assertThat(metrics.campaigns())
                .anyMatch(c -> c.name().startsWith("Azure Duty-Free") && c.redemptions() >= 1);
    }

    @Test
    void passengerFlowShowsUpcomingFootfallWithDestinationsAndNationalities() {
        clock.jumpTo(NOW);
        Campaign dutyFree = dutyFreeCampaign();

        // Give the company an outlet in Pier D (T2), where XY501 and ZQ88 board within the window.
        Zone pierD = em.createQuery("from Zone where name = 'Pier D'", Zone.class).getSingleResult();
        Outlet outlet = new Outlet();
        outlet.advertiser = dutyFree.advertiser;
        outlet.name = "Azure Duty-Free — Pier D";
        outlet.type = OutletType.DUTY_FREE;
        outlet.zone = pierD;
        em.persist(outlet);
        em.flush();

        PassengerFlow flow = dashboard.passengerFlow(dutyFree.advertiser.id, null);

        assertThat(flow.next60().minutes()).isEqualTo(60);
        assertThat(flow.next120().minutes()).isEqualTo(120);
        assertThat(flow.next120().footfall()).isGreaterThanOrEqualTo(flow.next60().footfall());
        assertThat(flow.next120().footfall()).isPositive();
        assertThat(flow.next120().topDestinations()).isNotEmpty();
        assertThat(flow.next120().topNationalities()).isNotEmpty();
        assertThat(flow.next120().topDestinations().size()).isLessThanOrEqualTo(5);
    }

    private Outlet pierDOutlet(Campaign dutyFree) {
        Zone pierD = em.createQuery("from Zone where name = 'Pier D'", Zone.class).getSingleResult();
        Outlet outlet = new Outlet();
        outlet.advertiser = dutyFree.advertiser;
        outlet.name = "Azure Duty-Free — Gate 42";
        outlet.type = OutletType.DUTY_FREE;
        outlet.zone = pierD;
        em.persist(outlet);
        em.flush();
        return outlet;
    }

    @Test
    void flowCanBeScopedToOneOutletAndListsTheCompanysOutlets() {
        clock.jumpTo(NOW);
        Campaign dutyFree = dutyFreeCampaign();
        Outlet outlet = pierDOutlet(dutyFree);

        assertThat(dashboard.listOutlets(dutyFree.advertiser.id)).anyMatch(o -> o.id() == outlet.id);
        PassengerFlow scoped = dashboard.passengerFlow(dutyFree.advertiser.id, outlet.id);

        // Pier D sits in T2, where XY501 (DXB) boards inside the next two hours.
        assertThat(scoped.next120().topDestinations()).anyMatch(d -> d.label().equals("DXB"));
    }

    @Test
    void cancelledDeparturesDoNotCountTowardsFootfall() {
        clock.jumpTo(NOW);
        Campaign dutyFree = dutyFreeCampaign();
        Outlet outlet = pierDOutlet(dutyFree);
        int before = dashboard.passengerFlow(dutyFree.advertiser.id, outlet.id).next120().footfall();

        Flight xy501 = em.createQuery("from Flight where flightNumber = 'XY501'", Flight.class).getSingleResult();
        xy501.status = FlightStatus.CANCELLED;
        em.flush();

        int after = dashboard.passengerFlow(dutyFree.advertiser.id, outlet.id).next120().footfall();
        assertThat(after).isEqualTo(before - xy501.paxCount);
    }

    @Test
    void aDelayedDepartureShowsUpAsPassengersHeldNearTheStores() {
        clock.jumpTo(NOW);
        Campaign dutyFree = dutyFreeCampaign();
        Outlet outlet = pierDOutlet(dutyFree);
        PassengerFlow.FlowWindow before = dashboard.passengerFlow(dutyFree.advertiser.id, outlet.id).next120();

        Flight zq88 = em.createQuery("from Flight where flightNumber = 'ZQ88'", Flight.class).getSingleResult();
        zq88.status = FlightStatus.DELAYED;
        zq88.delayMinutes = 30;
        zq88.estimatedTime = zq88.scheduledTime.plusSeconds(30 * 60L);
        em.flush();

        PassengerFlow.FlowWindow after = dashboard.passengerFlow(dutyFree.advertiser.id, outlet.id).next120();
        assertThat(after.delayedDepartures()).isEqualTo(before.delayedDepartures() + 1);
        assertThat(after.delayedPassengers()).isEqualTo(before.delayedPassengers() + zq88.paxCount);
        assertThat(after.footfall()).isEqualTo(before.footfall());
    }
}
