package com.airportmedia.platform.service.decision;

import static com.airportmedia.platform.service.decision.DecisionFixtures.camp;
import static com.airportmedia.platform.service.decision.DecisionFixtures.flight;
import static com.airportmedia.platform.service.decision.DecisionFixtures.inputs;
import static com.airportmedia.platform.service.decision.DecisionFixtures.profile;
import static com.airportmedia.platform.service.decision.DecisionFixtures.targeting;
import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.common.enums.SpendTier;
import com.airportmedia.platform.domain.model.input.CampaignView;
import com.airportmedia.platform.domain.model.input.DecisionInputs;
import com.airportmedia.platform.domain.model.output.CandidateScore;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DecisionScorerTest {

    private final DecisionScorer scorer = new DecisionScorer();

    private static CampaignView weighted(double business, double holiday, double family, SpendTier spend) {
        return camp().targeting(
                targeting(Set.of("42"), null, Set.of(), 0, 0, null, spend, business, holiday, family)).build();
    }

    @Test
    void componentsAndScoreStayInRange() {
        CandidateScore s = scorer.score(weighted(1, 1, 1, SpendTier.MEDIUM), inputs().build());

        assertThat(s.crowdFit()).isBetween(0.0, 1.0);
        assertThat(s.boardingProximity()).isBetween(0.0, 1.0);
        assertThat(s.dwellFit()).isBetween(0.0, 1.0);
        assertThat(s.spendMatch()).isBetween(0.0, 1.0);
        assertThat(s.engagementPrior()).isBetween(0.0, 1.0);
        assertThat(s.baseScore()).isBetween(0.0, 100.0);
        assertThat(s.eligible()).isTrue();
    }

    @Test
    void crowdFitRewardsSegmentMatchWithTheAudience() {
        // Audience is leisure-heavy; a holiday-weighted campaign should fit better than business-weighted.
        DecisionInputs in = inputs().profile(profile(400, 20, 60, 20, 30, 40, 35, 25, 130)).build();

        double holidayFit = scorer.score(weighted(0.1, 0.8, 0.1, SpendTier.MEDIUM), in).crowdFit();
        double businessFit = scorer.score(weighted(0.8, 0.1, 0.1, SpendTier.MEDIUM), in).crowdFit();

        assertThat(holidayFit).isGreaterThan(businessFit);
    }

    @Test
    void boardingProximityPeaksWhenAFlightIsBoardingAndFallsOffWhenDistant() {
        double boarding = scorer.score(weighted(1, 1, 1, SpendTier.MEDIUM),
                inputs().nearby(List.of(flight("XY1", "A", "DXB", Haul.INTERNATIONAL, 10))).build()).boardingProximity();
        double distant = scorer.score(weighted(1, 1, 1, SpendTier.MEDIUM),
                inputs().nearby(List.of(flight("XY2", "A", "DXB", Haul.INTERNATIONAL, 200))).build()).boardingProximity();

        assertThat(boarding).isGreaterThan(0.9);
        assertThat(distant).isZero();
    }

    @Test
    void spendMatchTracksTheTargetedTier() {
        DecisionInputs richCrowd = inputs().profile(profile(400, 30, 40, 30, 20, 20, 30, 50, 130)).build();

        double highTarget = scorer.score(weighted(1, 1, 1, SpendTier.HIGH), richCrowd).spendMatch();
        double lowTarget = scorer.score(weighted(1, 1, 1, SpendTier.LOW), richCrowd).spendMatch();

        assertThat(highTarget).isGreaterThan(lowTarget);
    }

    @Test
    void engagementPriorRisesWithHistoricalConversions() {
        double converting = scorer.score(camp().history(100, 20).build(), inputs().build()).engagementPrior();
        double cold = scorer.score(camp().history(0, 0).build(), inputs().build()).engagementPrior();

        assertThat(converting).isGreaterThan(cold);
    }

    @Test
    void scoringIsDeterministic() {
        CampaignView c = weighted(0.3, 0.6, 0.4, SpendTier.MEDIUM);

        assertThat(scorer.score(c, inputs().build())).isEqualTo(scorer.score(c, inputs().build()));
    }
}
