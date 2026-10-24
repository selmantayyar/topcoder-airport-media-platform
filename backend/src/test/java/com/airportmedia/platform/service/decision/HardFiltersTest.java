package com.airportmedia.platform.service.decision;

import static com.airportmedia.platform.service.decision.DecisionFixtures.camp;
import static com.airportmedia.platform.service.decision.DecisionFixtures.flight;
import static com.airportmedia.platform.service.decision.DecisionFixtures.inputs;
import static com.airportmedia.platform.service.decision.DecisionFixtures.profile;
import static com.airportmedia.platform.service.decision.DecisionFixtures.targeting;
import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.CampaignStatus;
import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.common.enums.SpendTier;
import com.airportmedia.platform.domain.model.input.CampaignView;
import com.airportmedia.platform.domain.model.input.DecisionInputs;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HardFiltersTest {

    private final HardFilters filters = new HardFilters();

    @Test
    void eligibleCampaignPassesAllFilters() {
        CampaignView c = camp()
                .targeting(targeting(Set.of("42"), Haul.INTERNATIONAL, Set.of(), 100, 15, null, SpendTier.MEDIUM, 1, 1, 1))
                .build();

        assertThat(filters.firstFailure(c, inputs().build())).isNull();
    }

    @Test
    void dropsCampaignThatIsNotRunning() {
        CampaignView c = camp().status(CampaignStatus.PENDING_APPROVAL).build();

        assertThat(filters.firstFailure(c, inputs().build())).containsIgnoringCase("running");
    }

    @Test
    void dropsCampaignOutsideItsScheduleWindow() {
        CampaignView c = camp().schedule(
                DecisionFixtures.DAY_START.minusSeconds(86400 * 10),
                DecisionFixtures.DAY_START.minusSeconds(86400)).build();

        assertThat(filters.firstFailure(c, inputs().build())).containsIgnoringCase("schedule");
    }

    @Test
    void dropsCampaignWhosePlacementDoesNotCoverTheScreen() {
        CampaignView c = camp()
                .targeting(targeting(Set.of("99"), null, Set.of(), 0, 0, null, SpendTier.MEDIUM, 1, 1, 1))
                .build();

        assertThat(filters.firstFailure(c, inputs().gates(Set.of("42")).build()))
                .containsIgnoringCase("placement");
    }

    @Test
    void dropsInternationalCampaignWhenOnlyDomesticFlightsAreNearby() {
        CampaignView c = camp()
                .targeting(targeting(Set.of("42"), Haul.INTERNATIONAL, Set.of(), 0, 0, null, SpendTier.MEDIUM, 1, 1, 1))
                .build();
        DecisionInputs in = inputs()
                .nearby(List.of(flight("BL12", "Bluebird", "NGT", Haul.DOMESTIC, 20)))
                .build();

        assertThat(filters.firstFailure(c, in)).containsIgnoringCase("international");
    }

    @Test
    void dropsCampaignWhoseTargetDestinationIsNotNearby() {
        CampaignView c = camp()
                .targeting(targeting(Set.of("42"), null, Set.of("JFK"), 0, 0, null, SpendTier.MEDIUM, 1, 1, 1))
                .build();

        assertThat(filters.firstFailure(c, inputs().build())).containsIgnoringCase("destination");
    }

    @Test
    void dropsCampaignBelowTheMinimumCrowd() {
        CampaignView c = camp()
                .targeting(targeting(Set.of("42"), null, Set.of(), 500, 0, null, SpendTier.MEDIUM, 1, 1, 1))
                .build();

        assertThat(filters.firstFailure(c, inputs().headcount(80).build())).containsIgnoringCase("crowd");
    }

    @Test
    void dropsCampaignWithExhaustedBudget() {
        CampaignView c = camp().budget("1000").spent("1000").build();

        assertThat(filters.firstFailure(c, inputs().build())).containsIgnoringCase("budget");
    }

    @Test
    void dropsAlcoholCampaignWhenCrowdIsFamilyHeavy() {
        CampaignView c = camp()
                .targeting(targeting(Set.of("42"), null, Set.of(), 0, 0, "alcohol", SpendTier.MEDIUM, 1, 1, 1))
                .build();
        DecisionInputs in = inputs().profile(profile(400, 10, 60, 30, 55, 60, 30, 10, 120)).build();

        assertThat(filters.firstFailure(c, in)).containsIgnoringCase("brand");
    }

    @Test
    void staticFailureIgnoresCrowdAndFlightDependentFilters() {
        // Fails only the crowd-derived min-crowd filter: static eligibility must still pass.
        CampaignView c = camp()
                .targeting(targeting(Set.of("42"), Haul.INTERNATIONAL, Set.of("JFK"), 9999, 999, null, SpendTier.MEDIUM, 1, 1, 1))
                .build();

        assertThat(filters.firstStaticFailure(c, inputs().headcount(10).build())).isNull();
    }

    @Test
    void staticFailureStillCatchesBudgetAndPlacement() {
        CampaignView c = camp().budget("1000").spent("2000").build();

        assertThat(filters.firstStaticFailure(c, inputs().build())).containsIgnoringCase("budget");
    }
}
