package com.airportmedia.platform.adapter.aodb;

import com.airportmedia.platform.adapter.SampleAirport;
import com.airportmedia.platform.adapter.SampleAirport.AircraftSpec;
import com.airportmedia.platform.adapter.SampleAirport.AirlineSpec;
import com.airportmedia.platform.adapter.SampleAirport.GateSpec;
import com.airportmedia.platform.adapter.SampleAirport.RouteSpec;
import com.airportmedia.platform.common.enums.Haul;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a realistic one-day AODB snapshot for the fictional airport. Deterministic
 * (fixed seed) so the demo is reproducible. The first row is always the spec's worked
 * example (XY501 to DXB, 240 pax, delayed 45 min, gate 42).
 */
@ApplicationScoped
public class MockFlightSource implements FlightSource {

    /** The simulated day. Flights are spread across it; the Gate 42 example sits at 14:10. */
    public static final LocalDate DEMO_DAY = LocalDate.parse("2026-10-23");

    private static final String[] DELAY_CODES = {"93", "89", "17", "41", "71"};

    private final SampleAirport airport;

    public MockFlightSource(SampleAirport airport) {
        this.airport = airport;
    }

    @Override
    public String mode() {
        return "mock";
    }

    @Override
    public String aodbSnapshotCsv(int flightCount) {
        Random rng = new Random(42);
        List<AodbRecord> rows = new ArrayList<>(flightCount);
        rows.add(workedExample());
        if (flightCount > 1) {
            rows.add(workedExampleSecondary());
        }
        for (int i = rows.size(); i < flightCount; i++) {
            rows.add(randomRow(rng, i));
        }
        return AodbCsvCodec.encode(rows);
    }

    private AodbRecord workedExample() {
        return new AodbRecord(
                "XY501", "XY", "XYZ", "DEP", SampleAirport.IATA, "DXB", "", "B77W", "A6-XY1", "240",
                iso(14, 10), iso(14, 55), "", "", "", "",
                iso(14, 10), iso(14, 15), "", "",
                "42", "S42", "T2", "D40-D50", "", "DELAYED", "45", "93", "",
                "290", "28", "42", "INT");
    }

    /** The second flight in the Gate 42 example: ZQ88, a low-cost holiday run boarding at gate 43. */
    private AodbRecord workedExampleSecondary() {
        return new AodbRecord(
                "ZQ88", "ZQ", "ZQA", "DEP", SampleAirport.IATA, "MLE", "", "A321", "ZQ-A88", "160",
                iso(15, 0), iso(15, 0), "", "", "", "",
                iso(15, 0), iso(15, 5), "", "",
                "43", "S43", "T2", "D40-D50", "", "SCHEDULED", "0", "", "",
                "180", "0", "20", "INT");
    }

    private AodbRecord randomRow(Random rng, int i) {
        RouteSpec route = airport.routes().get(rng.nextInt(airport.routes().size()));
        AirlineSpec airline = airport.airlines().get(rng.nextInt(airport.airlines().size()));
        AircraftSpec ac = pickAircraft(rng, route);
        GateSpec gate = airport.gates().get(rng.nextInt(airport.gates().size()));
        boolean departure = rng.nextInt(100) < 70;

        int pax = Math.min(ac.seats(), route.minPax() + rng.nextInt(Math.max(1, route.maxPax() - route.minPax())));
        int hour = 5 + rng.nextInt(18);
        int minute = rng.nextInt(60);
        boolean delayed = rng.nextInt(100) < 12;
        int delay = delayed ? 20 + rng.nextInt(70) : 0;

        String std = iso(hour, minute);
        String etd = iso(hour, minute); // same unless delayed
        if (delayed) {
            int em = (minute + delay) % 60;
            int eh = hour + (minute + delay) / 60;
            etd = iso(Math.min(eh, 23), em);
        }
        String origin = departure ? SampleAirport.IATA : route.destIata();
        String destination = departure ? route.destIata() : SampleAirport.IATA;

        return new AodbRecord(
                airline.iata() + (100 + rng.nextInt(899)),
                airline.iata(), airline.icao(),
                departure ? "DEP" : "ARR",
                origin, destination, "",
                ac.icao(), airline.icao() + "-" + letters(rng),
                String.valueOf(pax),
                std, etd, "", "", "", "",
                std, iso(hour, Math.min(59, minute + 5)), "", "",
                gate.label(), "S" + gate.label(),
                gate.zoneAcrisId().contains("T1") ? "T1" : "T2",
                departure ? "C" + (1 + rng.nextInt(60)) : "",
                departure ? "" : String.valueOf(1 + rng.nextInt(12)),
                delayed ? "DELAYED" : "SCHEDULED",
                String.valueOf(delay),
                delayed ? DELAY_CODES[rng.nextInt(DELAY_CODES.length)] : "",
                "",
                String.valueOf(ac.economy()), String.valueOf(ac.premium()), String.valueOf(ac.business()),
                route.haul() == Haul.DOMESTIC ? "DOM" : "INT");
    }

    private AircraftSpec pickAircraft(Random rng, RouteSpec route) {
        List<AircraftSpec> fit = airport.aircraft().stream()
                .filter(a -> a.seats() >= route.minPax())
                .toList();
        List<AircraftSpec> pool = fit.isEmpty() ? airport.aircraft() : fit;
        return pool.get(rng.nextInt(pool.size()));
    }

    private static String iso(int hour, int minute) {
        return DEMO_DAY.atTime(hour, minute).toInstant(ZoneOffset.UTC).toString();
    }

    private static String letters(Random rng) {
        char[] c = new char[3];
        for (int i = 0; i < 3; i++) {
            c[i] = (char) ('A' + rng.nextInt(26));
        }
        return new String(c);
    }
}
