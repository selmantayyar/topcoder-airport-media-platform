package com.airportmedia.platform.domain.model.output;

import java.util.List;

/**
 * Forward-looking footfall near a company's stores: how many departing passengers pass through its
 * terminals in the next 60 and 120 minutes, which destinations and nationalities they skew to, and how many
 * of them a delay is holding airside longer.
 */
public record PassengerFlow(FlowWindow next60, FlowWindow next120) {

    /**
     * @param delayedDepartures delayed flights departing inside the window
     * @param delayedPassengers their passengers — a captive crowd with extra dwell time near the stores
     */
    public record FlowWindow(
            int minutes,
            int footfall,
            List<LabelCount> topDestinations,
            List<LabelCount> topNationalities,
            int delayedDepartures,
            int delayedPassengers) {}

    /** A ranked label with its passenger count (destination city or nationality/language). */
    public record LabelCount(String label, int value) {}
}
