package com.airportmedia.platform.domain.model.output;

import com.airportmedia.platform.common.enums.AdvertiserKind;
import java.util.List;

/**
 * One campaign's full scoring record for a decision: whether it survived the hard filters, its
 * five weighted score components, the base score, every business-rule adjustment that fired,
 * and the final score the winner is chosen on. This is the per-candidate evidence trail.
 *
 * <p>All component values are 0..1; {@code baseScore} and {@code finalScore} are 0..100.
 * {@code finalScore = baseScore × ruleMultiplier} for eligible candidates, and 0 otherwise.
 */
public record CandidateScore(
        long campaignId,
        String campaignName,
        String advertiserName,
        AdvertiserKind advertiserKind,
        boolean eligible,
        String dropReason,
        double crowdFit,
        double boardingProximity,
        double dwellFit,
        double spendMatch,
        double engagementPrior,
        double baseScore,
        List<String> ruleNotes,
        double ruleMultiplier,
        double finalScore) {}
