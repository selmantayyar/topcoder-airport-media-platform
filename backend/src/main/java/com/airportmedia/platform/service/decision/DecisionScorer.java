package com.airportmedia.platform.service.decision;

import com.airportmedia.platform.common.enums.PartyType;
import com.airportmedia.platform.common.enums.SpendTier;
import com.airportmedia.platform.common.enums.TripPurpose;
import com.airportmedia.platform.common.util.Numbers;
import com.airportmedia.platform.domain.model.input.CampaignTargeting;
import com.airportmedia.platform.domain.model.input.CampaignView;
import com.airportmedia.platform.domain.model.input.DecisionInputs;
import com.airportmedia.platform.domain.model.output.CandidateScore;
import com.airportmedia.platform.service.ZonePresenceService;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * Scores one eligible campaign against the audience a screen currently faces. The base score is a
 * fixed weighted sum of five components, each normalised to 0..1, so the same inputs always yield
 * the same score (reproducible from the logged {@link DecisionInputs}):
 *
 * <pre>
 *   base = 100 × ( 0.35·crowdFit + 0.25·boardingProximity + 0.15·dwellFit
 *                + 0.15·spendMatch + 0.10·engagementPrior )
 * </pre>
 *
 * <ul>
 *   <li><b>crowdFit</b> — weighted average of the audience's business / leisure / family shares
 *       using the campaign's segment weights (how well the crowd matches who it wants).</li>
 *   <li><b>boardingProximity</b> — the strongest nearby-flight boarding signal, reusing the
 *       {@link ZonePresenceService#presenceFraction presence ramp}; peaks while a flight boards.</li>
 *   <li><b>dwellFit</b> — expected crowd dwell over a {@value #DWELL_REFERENCE_MINUTES}-minute
 *       reference (a delayed, captive crowd scores higher).</li>
 *   <li><b>spendMatch</b> — how much of the crowd sits in (or adjacent to) the offer's spend tier.</li>
 *   <li><b>engagementPrior</b> — Laplace-smoothed historical conversion, a cold campaign scoring
 *       a neutral 0.5.</li>
 * </ul>
 */
@ApplicationScoped
public class DecisionScorer {

    static final double W_CROWD_FIT = 0.35;
    static final double W_BOARDING = 0.25;
    static final double W_DWELL = 0.15;
    static final double W_SPEND = 0.15;
    static final double W_ENGAGEMENT = 0.10;

    static final double DWELL_REFERENCE_MINUTES = 120.0;
    private static final double CONVERSION_REFERENCE = 0.10;
    private static final double SMOOTHING = 20.0;

    public CandidateScore score(CampaignView campaign, DecisionInputs inputs) {
        double crowdFit = crowdFit(campaign.targeting(), inputs);
        double boarding = boardingProximity(inputs);
        double dwell = dwellFit(inputs);
        double spend = spendMatch(campaign.targeting(), inputs);
        double engagement = engagementPrior(campaign);

        double base = 100.0 * (W_CROWD_FIT * crowdFit
                + W_BOARDING * boarding
                + W_DWELL * dwell
                + W_SPEND * spend
                + W_ENGAGEMENT * engagement);
        double baseScore = Numbers.round2(base);

        return new CandidateScore(
                campaign.campaignId(), campaign.campaignName(), campaign.advertiserName(), campaign.advertiserKind(),
                true, null,
                Numbers.round4(crowdFit), Numbers.round4(boarding), Numbers.round4(dwell),
                Numbers.round4(spend), Numbers.round4(engagement),
                baseScore, List.of(), 1.0, baseScore);
    }

    private static double crowdFit(CampaignTargeting t, DecisionInputs inputs) {
        if (inputs.profile() == null) {
            return 0.0;
        }
        double business = share(inputs.profile().tripPurpose().getOrDefault(TripPurpose.BUSINESS, 0));
        double holiday = share(inputs.profile().tripPurpose().getOrDefault(TripPurpose.LEISURE, 0));
        double family = share(inputs.profile().partyType().getOrDefault(PartyType.FAMILY, 0));
        double weightTotal = t.weightTotal();
        if (weightTotal <= 0) {
            return (business + holiday + family) / 3.0;
        }
        return (t.weightBusiness() * business + t.weightHoliday() * holiday + t.weightFamily() * family) / weightTotal;
    }

    private static double boardingProximity(DecisionInputs inputs) {
        return inputs.nearbyFlights().stream()
                .mapToDouble(f -> ZonePresenceService.presenceFraction(f.minutesToDeparture()))
                .max()
                .orElse(0.0);
    }

    private static double dwellFit(DecisionInputs inputs) {
        if (inputs.profile() == null) {
            return 0.0;
        }
        return Numbers.clamp(inputs.profile().dwellMinutes() / DWELL_REFERENCE_MINUTES, 0, 1);
    }

    private static double spendMatch(CampaignTargeting t, DecisionInputs inputs) {
        if (inputs.profile() == null) {
            return 0.0;
        }
        double low = share(inputs.profile().spendTier().getOrDefault(SpendTier.LOW, 0));
        double medium = share(inputs.profile().spendTier().getOrDefault(SpendTier.MEDIUM, 0));
        double high = share(inputs.profile().spendTier().getOrDefault(SpendTier.HIGH, 0));
        return switch (t.spendTargetOrDefault()) {
            case HIGH -> high + 0.5 * medium;
            case MEDIUM -> medium + 0.5 * (low + high);
            case LOW -> low + 0.5 * medium;
        };
    }

    private static double engagementPrior(CampaignView campaign) {
        double rate = (campaign.redemptionsTotal() + 1.0) / (campaign.impressionsTotal() + SMOOTHING);
        return Numbers.clamp(rate / CONVERSION_REFERENCE, 0, 1);
    }

    private static double share(int percent) {
        return percent / 100.0;
    }
}
