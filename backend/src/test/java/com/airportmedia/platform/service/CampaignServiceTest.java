package com.airportmedia.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.common.enums.SpendTier;
import com.airportmedia.platform.domain.entity.Campaign;
import com.airportmedia.platform.domain.entity.Creative;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.Screen;
import com.airportmedia.platform.domain.entity.Zone;
import com.airportmedia.platform.domain.model.input.CampaignDraft;
import com.airportmedia.platform.domain.model.input.PromoRequest;
import com.airportmedia.platform.service.decision.DecisionService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.WebApplicationException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
class CampaignServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T14:30:00Z");

    @Inject
    CampaignService campaigns;

    @Inject
    DecisionService decisions;

    @Inject
    SimulationClock clock;

    @Inject
    EntityManager em;

    private Campaign dutyFree() {
        return em.createQuery("from Campaign where name like 'Azure Duty-Free%'", Campaign.class)
                .setMaxResults(1).getResultList().get(0);
    }

    private Screen gate42Screen() {
        return em.createQuery("from Screen where code = 'SCR-GATE42'", Screen.class).getSingleResult();
    }

    private CampaignDraft draft(long advertiserId, Long creativeId, Set<Long> zones, Set<Long> screens) {
        return new CampaignDraft(
                advertiserId, "Holiday perfume push", creativeId,
                creativeId == null ? "Fly fragrant" : null, creativeId == null ? "30% off travel sizes" : null,
                null, "en",
                Set.of(), zones, screens, Set.of(), Haul.INTERNATIONAL,
                0, 0, 0, 0.1, 0.9, 0.6, SpendTier.MEDIUM,
                NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofDays(3)),
                new BigDecimal("20000"), new BigDecimal("2000"), 6);
    }

    @Test
    void aSavedCampaignIsPickedUpByTheDecisionPipelineOnItsNextRun() {
        clock.jumpTo(NOW);
        Screen screen = gate42Screen();

        var saved = campaigns.create(draft(dutyFree().advertiser.id, null, Set.of(), Set.of(screen.id)));

        assertThat(saved.status()).isEqualTo("RUNNING");
        var decision = decisions.preview(screen.id);
        assertThat(decision.candidates())
                .anyMatch(c -> c.campaignId() == saved.campaignId() && c.eligible());
    }

    @Test
    void previewEstimatesAudienceMatchAndExpectedVolumes() {
        clock.jumpTo(NOW);
        Zone pierD = em.createQuery("from Zone where name = 'Pier D'", Zone.class).getSingleResult();

        var match = campaigns.preview(draft(dutyFree().advertiser.id, null, Set.of(pierD.id), Set.of()));

        assertThat(match.targetedScreens()).isPositive();
        assertThat(match.eligibleScreens()).isPositive();
        assertThat(match.matchPercent()).isBetween(0.1, 100.0);
        assertThat(match.expectedImpressionsPerHour()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void libraryCreativesAreReusedAsACopyOnTheNewCampaign() {
        clock.jumpTo(NOW);
        Campaign original = dutyFree();
        var library = campaigns.library(original.advertiser.id);
        assertThat(library).isNotEmpty();

        var saved = campaigns.create(draft(original.advertiser.id, library.get(0).id(), Set.of(), Set.of()));

        Creative copy = em.createQuery("from Creative where campaign.id = :id", Creative.class)
                .setParameter("id", saved.campaignId()).getSingleResult();
        assertThat(copy.id).isNotEqualTo(library.get(0).id());
        assertThat(copy.headline).isEqualTo(library.get(0).headline());
    }

    @Test
    void runAgainLaunchesACopyNowForTheOriginalsDuration() {
        clock.jumpTo(NOW);
        Campaign original = dutyFree();

        var rerun = campaigns.runAgain(original.id);

        assertThat(rerun.status()).isEqualTo("RUNNING");
        assertThat(rerun.scheduleStart()).isEqualTo(NOW);
        assertThat(Duration.between(rerun.scheduleStart(), rerun.scheduleEnd()))
                .isEqualTo(Duration.between(original.scheduleStart, original.scheduleEnd));
        assertThat(campaigns.history(original.advertiser.id))
                .extracting(r -> r.campaignId())
                .contains(rerun.campaignId());
    }

    @Test
    void duplicateOpensAPrefilledDraftThatSavesAsANewRunningCampaign() {
        clock.jumpTo(NOW);
        Campaign original = dutyFree();

        CampaignDraft prefill = campaigns.draftFrom(original.id);

        assertThat(prefill.name()).isEqualTo(original.name + " (copy)");
        assertThat(prefill.creativeId()).isNotNull();
        assertThat(prefill.haul()).isEqualTo(Haul.INTERNATIONAL);
        assertThat(prefill.gates()).contains("42");
        assertThat(prefill.scheduleStart()).isEqualTo(NOW);
        var saved = campaigns.create(prefill);
        assertThat(saved.status()).isEqualTo("RUNNING");
        assertThat(saved.campaignId()).isNotEqualTo(original.id);
    }

    @Test
    void historyReportsSpendRevenueAndRoi() {
        clock.jumpTo(NOW);
        var rows = campaigns.history(dutyFree().advertiser.id);

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.spend()).isEqualByComparingTo(BigDecimal.valueOf(r.impressions() * 0.5));
            assertThat(r.revenue()).isEqualByComparingTo(BigDecimal.valueOf(r.redemptions() * 45.0));
        });
    }

    @Test
    void aFlightLinkedPromoExpiresWhenItsBoardingCloses() {
        clock.jumpTo(NOW);
        Flight xy501 = em.createQuery("from Flight where flightNumber = 'XY501'", Flight.class).getSingleResult();

        var issued = campaigns.issuePromo(dutyFree().id, new PromoRequest(xy501.id, 600));

        assertThat(issued.linkedFlightNumber()).isEqualTo("XY501");
        assertThat(issued.validUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(600)));
        // XY501 now departs 14:55 → boarding closes 14:40, well before the 10-hour time box.
        assertThat(issued.expiresAt()).isEqualTo(Instant.parse("2026-10-23T14:40:00Z"));
    }

    @Test
    void aCodeCannotBeIssuedForAFlightWhoseBoardingHasClosed() {
        // XY501 boards until 14:40; at 14:45 a code linked to it would be dead on arrival.
        clock.jumpTo(Instant.parse("2026-10-23T14:45:00Z"));
        Flight xy501 = em.createQuery("from Flight where flightNumber = 'XY501'", Flight.class).getSingleResult();

        assertThatThrownBy(() -> campaigns.issuePromo(dutyFree().id, new PromoRequest(xy501.id, 120)))
                .isInstanceOf(WebApplicationException.class);
    }
}
