package com.airportmedia.platform.domain.model.output;

import java.time.Instant;
import java.util.List;

/** Pick-lists for the campaign builder: zones to target, known destinations, and flights to link codes to. */
public record BuilderOptions(List<ZoneOption> zones, List<String> destinations, List<FlightOption> upcomingFlights) {

    public record ZoneOption(long id, String name, String kind, String terminalCode) {}

    public record FlightOption(long id, String flightNumber, String destination, String gate, Instant departure) {}
}
