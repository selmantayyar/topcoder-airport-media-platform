package com.airportmedia.platform.adapter.aidx;

import java.time.Instant;

/** An irregularity to encode as an AIDX FlightLegNotifRQ message. */
public record AidxLegChange(
        String airlineIata,
        String flightNumber,
        String originDate,
        Integer delayMinutes,
        String newGate,
        String statusCode,
        Instant occurredAt,
        String messageRef) {}
