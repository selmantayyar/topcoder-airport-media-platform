package com.airportmedia.platform.domain.model.output;

import java.time.Instant;
import java.util.List;

/**
 * The outcome of one decision: the audience profile it faced, every candidate's scoring, and the
 * winner with the reason it was chosen. When {@code fallback} is true the dynamic score was not
 * trusted (stale/absent inputs) and the winner came from the rule-based default instead.
 */
public record DecisionResult(
        long screenId,
        Long zoneId,
        Instant decidedAt,
        int headcount,
        CrowdProfile profile,
        List<CandidateScore> candidates,
        Long winnerCampaignId,
        String winnerName,
        String reason,
        boolean fallback) {

    public boolean hasWinner() {
        return winnerCampaignId != null;
    }
}
