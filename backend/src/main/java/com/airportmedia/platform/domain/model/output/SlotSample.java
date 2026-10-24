package com.airportmedia.platform.domain.model.output;

import java.time.Instant;

/**
 * One simulated decision for a campaign on one screen at one instant of a projected day.
 *
 * @param plays      plays the campaign is granted for this sample (0 when it loses or is capped)
 * @param finalScore the campaign's final pipeline score there (0 when filtered off)
 */
public record SlotSample(
        long zoneId,
        String zoneName,
        long screenId,
        Instant at,
        int headcount,
        boolean eligible,
        long plays,
        double finalScore) {}
