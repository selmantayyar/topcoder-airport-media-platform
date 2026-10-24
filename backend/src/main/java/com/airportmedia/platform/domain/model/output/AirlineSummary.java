package com.airportmedia.platform.domain.model.output;

/** A selectable airline in the Airline Representative view. */
public record AirlineSummary(long id, String iataCode, String name, boolean lowCost) {}
