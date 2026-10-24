package com.airportmedia.platform.domain.model.output;

/**
 * The live audience-match preview for a campaign being built: how many online screens its placement
 * covers, how many it is eligible on right now, how many it would win, the audience-weighted fit, and
 * the impressions and redemptions it can expect per hour if it ran now.
 */
public record AudienceMatch(
        int targetedScreens,
        int eligibleScreens,
        int winningScreens,
        double matchPercent,
        double expectedImpressionsPerHour,
        double expectedRedemptionsPerHour) {

    /** One online screen's simulated outcome for the draft campaign. */
    public record ScreenOutcome(boolean targeted, boolean eligible, boolean won, double crowdFit, int headcount) {}
}
