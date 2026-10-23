package com.airportmedia.platform.adapter.aidx;

import com.airportmedia.platform.common.enums.FlightEventType;
import com.airportmedia.platform.common.enums.FlightStatus;
import java.time.Instant;

/**
 * The canonical content of one AIDX {@code IATA_AIDX_FlightLegNotifRQ} message, after
 * translation but before the flight is resolved. The caller matches {@link #airlineCode}
 * + {@link #flightNumber} + {@link #originDate} to a {@code Flight} and builds the event.
 */
public record AidxFlightLegUpdate(
        String airlineCode,
        String flightNumber,
        String originDate,
        FlightEventType type,
        Instant occurredAt,
        String messageRef,
        Integer delayMinutes,
        String newGate,
        FlightStatus newStatus) {

    public String fullFlightNumber() {
        return airlineCode + flightNumber;
    }
}
