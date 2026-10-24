package com.airportmedia.platform.service.decision;

import com.airportmedia.platform.common.enums.AdvertiserKind;
import com.airportmedia.platform.common.util.Numbers;
import com.airportmedia.platform.domain.model.input.CampaignView;
import com.airportmedia.platform.domain.model.input.DecisionInputs;
import com.airportmedia.platform.domain.model.output.CandidateScore;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies the spec's business rules to a base-scored candidate, as logged adjustments:
 * <ul>
 *   <li><b>Frequency cap</b> — a campaign that has already hit its per-screen cap is dropped.</li>
 *   <li><b>Airline priority</b> — an airline's own campaign is boosted {@value #AIRLINE_PRIORITY}×
 *       while one of its flights is boarding near the screen.</li>
 *   <li><b>Competitor separation</b> — a campaign of the same advertiser category shown on this
 *       screen in the separation window is penalised {@value #COMPETITOR_PENALTY}×.</li>
 *   <li><b>Budget pacing</b> — a campaign spending ahead of the elapsed-day pace is throttled, up
 *       to {@value #MAX_PACING_PENALTY} of its score.</li>
 * </ul>
 * {@code finalScore = baseScore × ruleMultiplier}; a dropped candidate scores 0.
 */
@ApplicationScoped
public class BusinessRules {

    static final double AIRLINE_PRIORITY = 1.5;
    static final double COMPETITOR_PENALTY = 0.8;
    static final double PACING_SLACK = 0.10;
    static final double MAX_PACING_PENALTY = 0.30;

    public CandidateScore apply(CandidateScore base, CampaignView campaign, DecisionInputs inputs) {
        if (campaign.frequencyCapPerScreen() > 0
                && campaign.impressionsOnScreenInWindow() >= campaign.frequencyCapPerScreen()) {
            return drop(base, "frequency cap reached ("
                    + campaign.impressionsOnScreenInWindow() + "/" + campaign.frequencyCapPerScreen() + ")");
        }

        List<String> notes = new ArrayList<>();
        double multiplier = 1.0;

        if (campaign.advertiserKind() == AdvertiserKind.AIRLINE && ownsBoardingFlight(campaign, inputs)) {
            multiplier *= AIRLINE_PRIORITY;
            notes.add("airline priority (+50%) in own boarding window");
        }

        if (inputs.lastWinnerKind() == campaign.advertiserKind()
                && inputs.lastWinnerAdvertiser() != null
                && !inputs.lastWinnerAdvertiser().equalsIgnoreCase(campaign.advertiserName())) {
            multiplier *= COMPETITOR_PENALTY;
            notes.add("competitor separation (−20%): " + campaign.advertiserKind() + " shown recently");
        }

        double elapsed = Numbers.clamp(dayFraction(inputs), 0, 1);
        double spentFraction = campaign.budget() != null && campaign.budget().signum() > 0
                ? campaign.spentToDate().doubleValue() / campaign.budget().doubleValue()
                : 0.0;
        if (spentFraction > elapsed + PACING_SLACK) {
            double overage = Math.min(MAX_PACING_PENALTY, spentFraction - elapsed);
            multiplier *= (1.0 - overage);
            notes.add(String.format("budget pacing (−%.0f%%): ahead of pace", overage * 100));
        }

        double finalScore = Numbers.round2(base.baseScore() * multiplier);
        return new CandidateScore(
                base.campaignId(), base.campaignName(), base.advertiserName(), base.advertiserKind(),
                true, null,
                base.crowdFit(), base.boardingProximity(), base.dwellFit(), base.spendMatch(), base.engagementPrior(),
                base.baseScore(), List.copyOf(notes), Numbers.round4(multiplier), finalScore);
    }

    private static boolean ownsBoardingFlight(CampaignView campaign, DecisionInputs inputs) {
        return inputs.nearbyFlights().stream()
                .anyMatch(f -> f.boarding() && f.airlineName() != null
                        && f.airlineName().equalsIgnoreCase(campaign.advertiserName()));
    }

    private static CandidateScore drop(CandidateScore base, String reason) {
        return new CandidateScore(
                base.campaignId(), base.campaignName(), base.advertiserName(), base.advertiserKind(),
                false, reason,
                base.crowdFit(), base.boardingProximity(), base.dwellFit(), base.spendMatch(), base.engagementPrior(),
                base.baseScore(), List.of(reason), 0.0, 0.0);
    }

    private static double dayFraction(DecisionInputs inputs) {
        double whole = Duration.between(inputs.dayStart(), inputs.dayEnd()).toSeconds();
        double elapsed = Duration.between(inputs.dayStart(), inputs.now()).toSeconds();
        return whole <= 0 ? 0 : elapsed / whole;
    }
}
