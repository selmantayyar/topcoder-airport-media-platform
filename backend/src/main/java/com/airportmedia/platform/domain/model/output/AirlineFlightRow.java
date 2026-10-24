package com.airportmedia.platform.domain.model.output;

import java.time.Instant;

/** One of the airline's departures today: gate, board status, delay and load factor (pax ÷ seats). */
public record AirlineFlightRow(
        long flightId,
        String flightNumber,
        String destination,
        String gate,
        Instant scheduledTime,
        Instant departureTime,
        String status,
        int delayMinutes,
        int paxCount,
        int seats,
        double loadFactorPct,
        long minutesToDeparture) {}
