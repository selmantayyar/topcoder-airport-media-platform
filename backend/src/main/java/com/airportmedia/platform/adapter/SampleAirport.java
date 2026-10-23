package com.airportmedia.platform.adapter;

import com.airportmedia.platform.common.enums.Haul;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;

/**
 * The fictional airport's reference catalog: the single source of truth that every
 * mock feed reads from, so AODB flights, AIDX events and ACRIS topology all agree
 * (a flight's gate really maps to a zone, its airline really exists, etc.). All
 * names are invented — no real airport, airline or brand marks.
 */
@ApplicationScoped
public class SampleAirport {

    public static final String IATA = "AMP";
    public static final String ICAO = "OAMP";
    public static final String NAME = "Amberport International";

    public record TerminalSpec(String acrisId, String code, String name) {}

    public record ZoneSpec(String acrisId, String terminalCode, String name, double lat, double lon) {}

    public record GateSpec(String label, String zoneAcrisId) {}

    public record AirlineSpec(String iata, String icao, String name, boolean lowCost) {}

    public record AircraftSpec(String icao, String name, int economy, int premium, int business) {
        public int seats() {
            return economy + premium + business;
        }
    }

    public record RouteSpec(String destIata, String city, String region, Haul haul, int minPax, int maxPax) {}

    private final List<TerminalSpec> terminals = new ArrayList<>();
    private final List<ZoneSpec> zones = new ArrayList<>();
    private final List<GateSpec> gates = new ArrayList<>();
    private final List<AirlineSpec> airlines = List.of(
            new AirlineSpec("XY", "XYZ", "Zephyr Airways", false),
            new AirlineSpec("ZQ", "ZQA", "Quokka Air", true),
            new AirlineSpec("VN", "VNT", "Vantage Airlines", false),
            new AirlineSpec("BL", "BLU", "Bluebird Connect", true),
            new AirlineSpec("SA", "SAF", "Saffron Air", false),
            new AirlineSpec("NP", "NPT", "Northpoint Airways", false),
            new AirlineSpec("CW", "CWG", "Crosswind Jet", true),
            new AirlineSpec("MR", "MRD", "Meridian Air", false),
            new AirlineSpec("HT", "HTL", "Halcyon Travel", true),
            new AirlineSpec("OA", "OAS", "Orient Star", false));
    private final List<AircraftSpec> aircraft = List.of(
            new AircraftSpec("B77W", "Widebody 777-size", 290, 28, 42),
            new AircraftSpec("A359", "Widebody A350-size", 260, 36, 40),
            new AircraftSpec("B789", "Widebody 787-size", 240, 21, 30),
            new AircraftSpec("A321", "Narrowbody A321-size", 180, 0, 20),
            new AircraftSpec("B738", "Narrowbody 737-size", 162, 0, 16),
            new AircraftSpec("A320", "Narrowbody A320-size", 150, 0, 12));
    private final List<RouteSpec> routes = List.of(
            new RouteSpec("DXB", "Dubai", "Gulf", Haul.INTERNATIONAL, 180, 320),
            new RouteSpec("LHR", "London", "Europe", Haul.INTERNATIONAL, 180, 300),
            new RouteSpec("SIN", "Singapore", "Asia", Haul.INTERNATIONAL, 200, 320),
            new RouteSpec("JFK", "New York", "Americas", Haul.INTERNATIONAL, 200, 300),
            new RouteSpec("CDG", "Paris", "Europe", Haul.INTERNATIONAL, 150, 280),
            new RouteSpec("IST", "Istanbul", "Europe", Haul.INTERNATIONAL, 160, 300),
            new RouteSpec("BKK", "Bangkok", "Asia", Haul.INTERNATIONAL, 180, 300),
            new RouteSpec("DEL", "Delhi", "Asia", Haul.INTERNATIONAL, 180, 300),
            new RouteSpec("AUH", "Abu Dhabi", "Gulf", Haul.INTERNATIONAL, 120, 260),
            new RouteSpec("DOH", "Doha", "Gulf", Haul.INTERNATIONAL, 120, 260),
            new RouteSpec("FRA", "Frankfurt", "Europe", Haul.INTERNATIONAL, 150, 280),
            new RouteSpec("AMS", "Amsterdam", "Europe", Haul.INTERNATIONAL, 150, 260),
            new RouteSpec("MLE", "Male", "Asia", Haul.INTERNATIONAL, 120, 240),
            new RouteSpec("ZRH", "Zurich", "Europe", Haul.INTERNATIONAL, 120, 220),
            new RouteSpec("NBO", "Nairobi", "Africa", Haul.INTERNATIONAL, 120, 240),
            new RouteSpec("NGT", "Northgate", "Domestic", Haul.DOMESTIC, 90, 180),
            new RouteSpec("RVT", "Riverton", "Domestic", Haul.DOMESTIC, 80, 170),
            new RouteSpec("CPM", "Capemoor", "Domestic", Haul.DOMESTIC, 90, 180),
            new RouteSpec("LKH", "Lakehaven", "Domestic", Haul.DOMESTIC, 70, 150),
            new RouteSpec("STH", "Stonehill", "Domestic", Haul.DOMESTIC, 80, 160));

    public SampleAirport() {
        buildTopology();
    }

    private void buildTopology() {
        // Two terminals, two gate-area piers each, plus a checkpoint, retail and arrivals zone.
        String[][] terminalDefs = {{"T1", "Terminal 1"}, {"T2", "Terminal 2"}};
        String[][] piersByTerminal = {{"A", "B"}, {"C", "D"}};
        // Pier D starts at gate 40 so the spec's worked-example gate 42 exists.
        int[] pierGateBase = {1, 11, 21, 40};
        double lat = 25.10;
        double lon = 55.20;
        int pierIndex = 0;

        for (int t = 0; t < terminalDefs.length; t++) {
            String tc = terminalDefs[t][0];
            terminals.add(new TerminalSpec("urn:ngsi-ld:TerminalFacility:" + IATA + "-" + tc, tc, terminalDefs[t][1]));

            for (String pier : piersByTerminal[t]) {
                String zid = "urn:ngsi-ld:ZoneAreaLocation:" + IATA + "-" + tc + "-PIER" + pier;
                zones.add(new ZoneSpec(zid, tc, "Pier " + pier, round(lat), round(lon)));
                int base = pierGateBase[pierIndex++];
                for (int g = 0; g < 10; g++) {
                    gates.add(new GateSpec(String.valueOf(base + g), zid));
                }
                lat += 0.004;
                lon += 0.004;
            }
            zones.add(new ZoneSpec("urn:ngsi-ld:ZoneAreaLocation:" + IATA + "-" + tc + "-SEC",
                    tc, tc + " Security Checkpoint", round(lat), round(lon)));
            zones.add(new ZoneSpec("urn:ngsi-ld:ZoneAreaLocation:" + IATA + "-" + tc + "-RETAIL",
                    tc, tc + " Duty-Free Plaza", round(lat + 0.001), round(lon + 0.001)));
            zones.add(new ZoneSpec("urn:ngsi-ld:ZoneAreaLocation:" + IATA + "-" + tc + "-ARR",
                    tc, tc + " Arrivals Hall", round(lat + 0.002), round(lon + 0.002)));
            lat += 0.01;
            lon += 0.01;
        }
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    public List<TerminalSpec> terminals() {
        return terminals;
    }

    public List<ZoneSpec> zones() {
        return zones;
    }

    public List<GateSpec> gates() {
        return gates;
    }

    public List<AirlineSpec> airlines() {
        return airlines;
    }

    public List<AircraftSpec> aircraft() {
        return aircraft;
    }

    public List<RouteSpec> routes() {
        return routes;
    }
}
