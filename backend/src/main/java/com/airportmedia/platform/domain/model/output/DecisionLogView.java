package com.airportmedia.platform.domain.model.output;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * API view of a persisted {@link com.airportmedia.platform.domain.entity.DecisionLog}: the stored
 * profile and candidate JSON are re-parsed into structured nodes so clients get the full evidence
 * trail without traversing the entity graph.
 */
public record DecisionLogView(
        long id,
        long screenId,
        String screenCode,
        Long zoneId,
        Instant decidedAt,
        int headcount,
        Long winnerCampaignId,
        String winnerName,
        String reason,
        boolean fallback,
        JsonNode inputs,
        JsonNode profile,
        JsonNode candidates) {}
