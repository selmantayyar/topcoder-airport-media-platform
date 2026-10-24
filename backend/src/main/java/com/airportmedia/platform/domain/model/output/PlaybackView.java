package com.airportmedia.platform.domain.model.output;

import java.time.Instant;
import java.util.List;

/**
 * Everything a passenger screen renders at one instant: the screen's shape, the FIDS-style strip of
 * nearby flights, the decision that chose the current ad (profile, candidates, winning reason), the
 * winning creative, and its claimable promo. {@code creative}/{@code promo} are null when nothing is
 * eligible to play.
 */
public record PlaybackView(
        ScreenSummary screen,
        Instant at,
        List<FidsEntry> nearbyFlights,
        DecisionResult decision,
        CreativeView creative,
        PromoPlayback promo) {

    /** One FIDS row next to the ad. */
    public record FidsEntry(
            String flightNumber,
            String airlineName,
            String destination,
            String gate,
            Instant scheduledTime,
            Instant estimatedTime,
            String status,
            long minutesToDeparture) {}

    /** The winning ad's content. */
    public record CreativeView(
            String advertiserName,
            String headline,
            String body,
            String offerText,
            String language,
            String templateKey) {}

    /** The claimable promo shown as a QR with a live countdown. */
    public record PromoPlayback(
            String code,
            Instant validUntil,
            long secondsRemaining,
            String claimPath) {}
}
