package com.airportmedia.platform.domain.model.input;

import com.airportmedia.platform.common.enums.AdvertiserKind;
import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.domain.model.output.CrowdProfile;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * The complete, immutable input to one decision — the audit record the pipeline reasons over.
 * Logging this makes a decision reproducible: the same inputs always yield the same scores.
 *
 * @param dataAsOf        when the cited flight/crowd data was last known fresh (freshness check)
 * @param nearbyFlights   flights whose gates sit in the screen's zone, soonest departure first
 * @param lastWinnerKind  advertiser kind chosen on this screen in the separation window (null = none)
 * @param lastWinnerAdvertiser advertiser chosen on this screen in the separation window (null = none)
 * @param dayStart        start of the simulated day, used for budget pacing
 * @param dayEnd          end of the simulated day, used for budget pacing
 */
public record DecisionInputs(
        long screenId,
        Long zoneId,
        Set<String> screenGates,
        Instant now,
        Instant dataAsOf,
        int headcount,
        CrowdProfile profile,
        List<NearbyFlight> nearbyFlights,
        List<CampaignView> campaigns,
        AdvertiserKind lastWinnerKind,
        String lastWinnerAdvertiser,
        Instant dayStart,
        Instant dayEnd) {

    /** One flight near the screen, as cited by the decision. */
    public record NearbyFlight(
            String flightNumber,
            String airlineName,
            String destination,
            Haul haul,
            long minutesToDeparture,
            boolean boarding) {}
}
