package com.airportmedia.platform.domain.model.output;

import com.airportmedia.platform.common.enums.ZoneKind;
import java.time.Instant;
import java.util.List;

/**
 * How many passengers — and of what kind — are estimated to be near one zone during one
 * 5-minute window, built from the departures whose gates sit in the zone (weighted by how
 * close each is to boarding) plus any ACRIS queue occupancy. The blended {@link CrowdProfile}
 * is the head-count-weighted mix of the contributing flights; it is {@code null} when the
 * zone is empty in this window.
 */
public record ZonePresence(
        Long zoneId,
        String zoneName,
        ZoneKind zoneKind,
        Instant windowStart,
        int windowMinutes,
        int estimatedHeadcount,
        List<FlightContribution> flights,
        CrowdProfile profile) {

    /** One flight's contribution to a zone's presence in this window. */
    public record FlightContribution(
            Long flightId,
            String flightNumber,
            String gate,
            String destination,
            long minutesToDeparture,
            int passengers) {}
}
