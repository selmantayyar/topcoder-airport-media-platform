package com.airportmedia.platform.service.airline;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.AdvertiserKind;
import com.airportmedia.platform.common.enums.CampaignPurpose;
import com.airportmedia.platform.common.enums.CampaignStatus;
import com.airportmedia.platform.domain.entity.Campaign;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.GateAssignment;
import com.airportmedia.platform.domain.entity.PromoCode;
import com.airportmedia.platform.service.SimulationClock;
import com.airportmedia.platform.service.SimulationService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
class DelayGoodwillServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T14:30:00Z");

    @Inject
    SimulationService simulation;

    @Inject
    DelayGoodwillService goodwill;

    @Inject
    SimulationClock clock;

    @Inject
    EntityManager em;

    /** A departure later today that isn't delayed yet. */
    private Flight upcomingDeparture() {
        return em.createQuery("from Flight where direction = 'DEPARTURE' and status = 'SCHEDULED' "
                        + "and scheduledTime > :t order by scheduledTime", Flight.class)
                .setParameter("t", NOW.plusSeconds(3600)).setMaxResults(1).getSingleResult();
    }

    private List<Campaign> vouchers(Flight f) {
        return em.createQuery("from Campaign where purpose = :p and linkedFlight.id = :f", Campaign.class)
                .setParameter("p", CampaignPurpose.GOODWILL).setParameter("f", f.id).getResultList();
    }

    @Test
    void aLongDelayAutomaticallyCreatesACoFundedMealVoucherOnTheFlightsGate() {
        clock.jumpTo(NOW);
        Flight flight = upcomingDeparture();
        String gate = em.createQuery("from GateAssignment where flight.id = :f", GateAssignment.class)
                .setParameter("f", flight.id).getSingleResult().gate;

        simulation.injectDelay(flight.id, 90, null);

        assertThat(vouchers(flight)).singleElement().satisfies(c -> {
            assertThat(c.status).isEqualTo(CampaignStatus.RUNNING);
            assertThat(c.name).contains("free meal");
            assertThat(c.coFunder).isNotNull();
            assertThat(c.coFunder.kind).isNotEqualTo(AdvertiserKind.AIRLINE);
            assertThat(c.advertiser.kind).isEqualTo(AdvertiserKind.AIRLINE);
            assertThat(c.placementJson).contains("\"" + gate + "\"");
            assertThat(c.scheduleEnd).isEqualTo(flight.departureTime());
        });
        Campaign voucher = vouchers(flight).get(0);
        assertThat(em.createQuery("from PromoCode where campaign.id = :c", PromoCode.class)
                .setParameter("c", voucher.id).getResultList())
                .singleElement().satisfies(p -> assertThat(p.linkedFlight.id).isEqualTo(flight.id));
    }

    @Test
    void aDelayBelowTheThresholdEarnsNothing() {
        clock.jumpTo(NOW);
        Flight flight = upcomingDeparture();

        simulation.injectDelay(flight.id, 45, null);

        assertThat(vouchers(flight)).isEmpty();
    }

    @Test
    void aVeryLongDelayEarnsALoungePassAndIsNeverDuplicated() {
        clock.jumpTo(NOW);
        Flight flight = upcomingDeparture();

        simulation.injectDelay(flight.id, 150, null);
        goodwill.scan();
        goodwill.scan();

        assertThat(vouchers(flight)).singleElement().satisfies(c -> assertThat(c.name).contains("lounge pass"));
    }
}
