package com.airportmedia.platform.domain.model.output;

import java.time.Instant;

/** A campaign the airline runs at its gates: content it sponsored, or a delay voucher created for it. */
public record AirlineContentRow(
        long campaignId,
        String name,
        String purpose,
        String status,
        String flightNumber,
        String gate,
        Instant scheduleStart,
        Instant scheduleEnd,
        String coFunderName,
        String headline,
        String offerText) {}
