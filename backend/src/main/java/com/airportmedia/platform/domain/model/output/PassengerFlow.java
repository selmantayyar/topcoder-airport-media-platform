package com.airportmedia.platform.domain.model.output;

import java.util.List;

/**
 * Forward-looking footfall near a company's stores: how many departing passengers pass through its
 * terminals in the next 60 and 120 minutes, and which destinations and nationalities they skew to.
 */
public record PassengerFlow(FlowWindow next60, FlowWindow next120) {

    public record FlowWindow(
            int minutes,
            int footfall,
            List<LabelCount> topDestinations,
            List<LabelCount> topNationalities) {}

    /** A ranked label with its passenger count (destination city or nationality/language). */
    public record LabelCount(String label, int value) {}
}
