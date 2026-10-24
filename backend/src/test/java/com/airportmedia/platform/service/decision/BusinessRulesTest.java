package com.airportmedia.platform.service.decision;

import static com.airportmedia.platform.service.decision.DecisionFixtures.camp;
import static com.airportmedia.platform.service.decision.DecisionFixtures.inputs;
import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.AdvertiserKind;
import com.airportmedia.platform.domain.model.input.CampaignView;
import com.airportmedia.platform.domain.model.input.DecisionInputs;
import com.airportmedia.platform.domain.model.output.CandidateScore;
import org.junit.jupiter.api.Test;

class BusinessRulesTest {

    private final DecisionScorer scorer = new DecisionScorer();
    private final BusinessRules rules = new BusinessRules();

    private CandidateScore applied(CampaignView c, DecisionInputs in) {
        return rules.apply(scorer.score(c, in), c, in);
    }

    @Test
    void noRulesLeaveTheScoreUnchanged() {
        CampaignView c = camp().build();
        CandidateScore base = scorer.score(c, inputs().build());

        CandidateScore out = rules.apply(base, c, inputs().build());

        assertThat(out.ruleMultiplier()).isEqualTo(1.0);
        assertThat(out.finalScore()).isEqualTo(base.baseScore());
        assertThat(out.eligible()).isTrue();
    }

    @Test
    void frequencyCapDropsACampaignThatHasHitItsCapOnThisScreen() {
        CampaignView c = camp().freqCap(3).impWindow(3).build();

        CandidateScore out = applied(c, inputs().build());

        assertThat(out.eligible()).isFalse();
        assertThat(out.dropReason()).containsIgnoringCase("frequency");
        assertThat(out.finalScore()).isZero();
    }

    @Test
    void airlineCampaignGetsPriorityInItsOwnBoardingWindow() {
        CampaignView c = camp().kind(AdvertiserKind.AIRLINE).advertiser("Zephyr Airways").build();
        CandidateScore base = scorer.score(c, inputs().build());

        CandidateScore out = applied(c, inputs().build());

        assertThat(out.ruleMultiplier()).isGreaterThan(1.0);
        assertThat(out.finalScore()).isGreaterThan(base.baseScore());
        assertThat(out.ruleNotes()).anyMatch(note -> note.toLowerCase().contains("airline"));
    }

    @Test
    void competitorSeparationPenalisesACategoryShownRecently() {
        CampaignView c = camp().kind(AdvertiserKind.FNB).advertiser("Bean & Brew").build();
        DecisionInputs in = inputs().lastWinner(AdvertiserKind.FNB, "Harvest Deli").build();

        CandidateScore out = applied(c, in);

        assertThat(out.ruleMultiplier()).isLessThan(1.0);
        assertThat(out.ruleNotes()).anyMatch(note -> note.toLowerCase().contains("competitor"));
    }

    @Test
    void budgetPacingPenalisesACampaignRunningAheadOfPace() {
        // 14:30 ≈ 60% through the day, but 90% of budget already spent.
        CampaignView c = camp().budget("1000").spent("900").build();

        CandidateScore out = applied(c, inputs().build());

        assertThat(out.ruleMultiplier()).isLessThan(1.0);
        assertThat(out.ruleNotes()).anyMatch(note -> note.toLowerCase().contains("pacing"));
    }
}
