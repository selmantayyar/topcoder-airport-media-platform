package com.airportmedia.platform.domain.model.output;

import java.time.Instant;

/** A recommended one-hour slot in a zone, with what the pipeline predicts the campaign would get there. */
public record SlotRecommendation(
        int rank,
        long zoneId,
        String zoneName,
        Instant start,
        Instant end,
        long predictedImpressions,
        int winningScreens,
        int peakHeadcount,
        double averageScore) {}
