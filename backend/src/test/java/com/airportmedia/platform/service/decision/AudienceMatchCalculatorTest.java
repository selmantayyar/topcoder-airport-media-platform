package com.airportmedia.platform.service.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.airportmedia.platform.domain.model.output.AudienceMatch;
import com.airportmedia.platform.domain.model.output.AudienceMatch.ScreenOutcome;
import java.util.List;
import org.junit.jupiter.api.Test;

class AudienceMatchCalculatorTest {

    @Test
    void aggregatesScreenOutcomesIntoMatchAndExpectedVolumes() {
        List<ScreenOutcome> outcomes = List.of(
                new ScreenOutcome(true, true, true, 0.6, 300),   // eligible + wins
                new ScreenOutcome(true, true, false, 0.4, 100),  // eligible, loses
                new ScreenOutcome(true, false, false, 0.0, 100), // targeted, filtered out
                new ScreenOutcome(false, false, false, 0.0, 500)); // not targeted

        AudienceMatch m = AudienceMatchCalculator.compute(outcomes, 15, 0.05);

        assertThat(m.targetedScreens()).isEqualTo(3);
        assertThat(m.eligibleScreens()).isEqualTo(2);
        assertThat(m.winningScreens()).isEqualTo(1);
        // (0.6×300 + 0.4×100) / (300+100+100) = 220/500 = 44%
        assertThat(m.matchPercent()).isEqualTo(44.0, within(0.01));
        // 1 winning screen × 3600/15 slots per hour
        assertThat(m.expectedImpressionsPerHour()).isEqualTo(240.0, within(0.01));
        assertThat(m.expectedRedemptionsPerHour()).isEqualTo(12.0, within(0.01));
    }

    @Test
    void nothingTargetedMeansNoMatch() {
        AudienceMatch m = AudienceMatchCalculator.compute(
                List.of(new ScreenOutcome(false, false, false, 0, 200)), 15, 0.05);

        assertThat(m.targetedScreens()).isZero();
        assertThat(m.matchPercent()).isZero();
        assertThat(m.expectedImpressionsPerHour()).isZero();
    }
}
