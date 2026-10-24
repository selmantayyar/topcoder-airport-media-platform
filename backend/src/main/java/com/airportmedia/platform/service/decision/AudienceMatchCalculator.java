package com.airportmedia.platform.service.decision;

import com.airportmedia.platform.domain.model.output.AudienceMatch;
import com.airportmedia.platform.domain.model.output.AudienceMatch.ScreenOutcome;
import java.util.List;

/**
 * Turns per-screen simulated outcomes for a draft campaign into the builder's audience-match preview:
 *
 * <pre>
 *   matchPercent   = 100 × Σ(crowdFit × headcount over eligible screens) / Σ(headcount over targeted screens)
 *   impressions/hr = winningScreens × 3600 / intervalSimSeconds   (one play per decision slot)
 *   redemptions/hr = impressions/hr × conversionRate
 * </pre>
 *
 * Targeted screens the draft is filtered off count as zero fit, so the match reflects how much of the
 * audience its placement reaches <em>and</em> how well that audience fits.
 */
public final class AudienceMatchCalculator {

    private AudienceMatchCalculator() {}

    public static AudienceMatch compute(List<ScreenOutcome> outcomes, int intervalSimSeconds, double conversionRate) {
        int targeted = 0;
        int eligible = 0;
        int winning = 0;
        double fitWeighted = 0;
        long targetedHeadcount = 0;

        for (ScreenOutcome o : outcomes) {
            if (!o.targeted()) {
                continue;
            }
            targeted++;
            targetedHeadcount += o.headcount();
            if (o.eligible()) {
                eligible++;
                fitWeighted += o.crowdFit() * o.headcount();
            }
            if (o.won()) {
                winning++;
            }
        }

        double match = targetedHeadcount > 0 ? 100.0 * fitWeighted / targetedHeadcount : 0.0;
        double impressionsPerHour = winning * 3600.0 / intervalSimSeconds;
        return new AudienceMatch(
                targeted, eligible, winning,
                round1(match),
                round1(impressionsPerHour),
                round1(impressionsPerHour * conversionRate));
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
