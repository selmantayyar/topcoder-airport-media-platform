package com.airportmedia.platform.common;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

/**
 * Canonical reference data about where a route goes: the region, the dominant language of
 * passengers on it, and the kind of travel it attracts. Keyed by IATA code. This is domain
 * knowledge about destinations (not a source's wire format), so business logic may depend on
 * it directly. Unknown codes fall back to a generic international entry.
 */
@ApplicationScoped
public class RouteDirectory {

    /** What a route is mostly used for — nudges the crowd profile toward plausible mixes. */
    public enum Flavor {
        BUSINESS_HUB,
        HOLIDAY,
        VFR,
        DOMESTIC,
        GENERIC
    }

    public record RouteInfo(String region, String language, Flavor flavor) {}

    private static final RouteInfo GENERIC = new RouteInfo("International", "English", Flavor.GENERIC);

    private static final Map<String, RouteInfo> ROUTES = Map.ofEntries(
            Map.entry("DXB", new RouteInfo("Gulf", "Arabic", Flavor.HOLIDAY)),
            Map.entry("AUH", new RouteInfo("Gulf", "Arabic", Flavor.HOLIDAY)),
            Map.entry("DOH", new RouteInfo("Gulf", "Arabic", Flavor.HOLIDAY)),
            Map.entry("MLE", new RouteInfo("Asia", "Dhivehi", Flavor.HOLIDAY)),
            Map.entry("BKK", new RouteInfo("Asia", "Thai", Flavor.HOLIDAY)),
            Map.entry("DEL", new RouteInfo("Asia", "Hindi", Flavor.VFR)),
            Map.entry("IST", new RouteInfo("Europe", "Turkish", Flavor.VFR)),
            Map.entry("NBO", new RouteInfo("Africa", "Swahili", Flavor.VFR)),
            Map.entry("LHR", new RouteInfo("Europe", "English", Flavor.BUSINESS_HUB)),
            Map.entry("CDG", new RouteInfo("Europe", "French", Flavor.BUSINESS_HUB)),
            Map.entry("FRA", new RouteInfo("Europe", "German", Flavor.BUSINESS_HUB)),
            Map.entry("AMS", new RouteInfo("Europe", "Dutch", Flavor.BUSINESS_HUB)),
            Map.entry("ZRH", new RouteInfo("Europe", "German", Flavor.BUSINESS_HUB)),
            Map.entry("SIN", new RouteInfo("Asia", "English", Flavor.BUSINESS_HUB)),
            Map.entry("JFK", new RouteInfo("Americas", "English", Flavor.BUSINESS_HUB)),
            Map.entry("NGT", new RouteInfo("Domestic", "English", Flavor.DOMESTIC)),
            Map.entry("RVT", new RouteInfo("Domestic", "English", Flavor.DOMESTIC)),
            Map.entry("CPM", new RouteInfo("Domestic", "English", Flavor.DOMESTIC)),
            Map.entry("LKH", new RouteInfo("Domestic", "English", Flavor.DOMESTIC)),
            Map.entry("STH", new RouteInfo("Domestic", "English", Flavor.DOMESTIC)));

    public RouteInfo lookup(String iata) {
        return ROUTES.getOrDefault(iata, GENERIC);
    }
}
