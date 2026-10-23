package com.airportmedia.platform.service;

import com.airportmedia.platform.common.RouteDirectory;
import com.airportmedia.platform.common.RouteDirectory.Flavor;
import com.airportmedia.platform.common.RouteDirectory.RouteInfo;
import com.airportmedia.platform.common.enums.AgeBand;
import com.airportmedia.platform.common.enums.FlightDirection;
import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.common.enums.PartyType;
import com.airportmedia.platform.common.enums.SpendTier;
import com.airportmedia.platform.common.enums.TripPurpose;
import com.airportmedia.platform.common.util.Distributions;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.model.output.CrowdProfile;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Derives a plausible {@link CrowdProfile} from a flight, deterministically, so a logged
 * decision can be reproduced from the same flight. Each distribution starts from a base
 * mix and is nudged by a few readable signals, then clamped and normalised to sum to 100:
 *
 * <ul>
 *   <li><b>Time of travel</b> — a weekday peak-commute departure (05–09 / 16–20) on a
 *       domestic or business-hub route skews toward business travellers aged 25–44.</li>
 *   <li><b>Route flavour</b> — holiday routes skew leisure, families and couples; VFR
 *       routes skew visiting-relatives, groups and older bands; business hubs skew
 *       business, solo and premium spend.</li>
 *   <li><b>Carrier &amp; cabin</b> — a low-cost carrier skews low spend and younger/group
 *       travel; a cabin with real business seats lifts premium spend.</li>
 * </ul>
 *
 * Small per-flight jitter (seeded by flight number) varies the exact percentages without
 * breaking determinism.
 */
@ApplicationScoped
public class CrowdProfiler {

    private final RouteDirectory routes;

    public CrowdProfiler(RouteDirectory routes) {
        this.routes = routes;
    }

    public CrowdProfile profile(Flight flight) {
        ZonedDateTime local = flight.scheduledTime.atZone(ZoneOffset.UTC);
        int hour = local.getHour();
        DayOfWeek day = local.getDayOfWeek();
        boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
        boolean peakHour = (hour >= 5 && hour <= 9) || (hour >= 16 && hour <= 20);
        boolean domestic = flight.haul == Haul.DOMESTIC;
        boolean lowCost = flight.airline != null && flight.airline.lowCost;

        RouteInfo route = routes.lookup(profileEndpoint(flight));
        Flavor flavor = route.flavor();
        boolean businessCommute = peakHour && !weekend && (domestic || flavor == Flavor.BUSINESS_HUB);

        int total = Math.max(1, flight.aircraftType.totalSeats());
        double businessRatio = flight.aircraftType.seatsBusiness / (double) total;
        boolean premiumCabin = businessRatio >= 0.10;

        Random jitter = new Random(flight.flightNumber == null ? 0 : flight.flightNumber.hashCode());

        Map<TripPurpose, Integer> tripPurpose = tripPurpose(jitter, businessCommute, weekend, domestic, lowCost, premiumCabin, flavor);
        TripPurpose dominantPurpose = CrowdProfile.dominant(tripPurpose);
        Map<PartyType, Integer> partyType = partyType(jitter, dominantPurpose, flavor, weekend, lowCost, premiumCabin);
        Map<AgeBand, Integer> ageBand = ageBand(jitter, dominantPurpose, flavor, lowCost, domestic);
        Map<SpendTier, Integer> spendTier = spendTier(jitter, dominantPurpose, flavor, lowCost, premiumCabin);

        int femalePct = clamp(50 + jitter.nextInt(9) - 4
                + (dominantPurpose == TripPurpose.BUSINESS ? -4 : 0)
                + (flavor == Flavor.HOLIDAY ? 4 : 0), 35, 65);

        int dwellMinutes = (domestic ? 75 : 120)
                + (flight.status == FlightStatus.DELAYED ? flight.delayMinutes : 0)
                + jitter.nextInt(21) - 10;

        return new CrowdProfile(
                flight.paxCount,
                tripPurpose,
                partyType,
                ageBand,
                femalePct,
                spendTier,
                route.language(),
                route.region(),
                Math.max(20, dwellMinutes),
                flight.status == FlightStatus.DELAYED,
                flight.status == FlightStatus.DELAYED ? flight.delayMinutes : 0);
    }

    /** The non-home end of the leg: where a departure is going, where an arrival came from. */
    private static String profileEndpoint(Flight flight) {
        return flight.direction == FlightDirection.ARRIVAL ? flight.origin : flight.destination;
    }

    private static Map<TripPurpose, Integer> tripPurpose(
            Random jitter, boolean businessCommute, boolean weekend, boolean domestic,
            boolean lowCost, boolean premiumCabin, Flavor flavor) {
        EnumMap<TripPurpose, Double> w = seed(TripPurpose.class, jitter, 25, 50, 25);
        if (businessCommute) {
            add(w, TripPurpose.BUSINESS, 35);
            add(w, TripPurpose.LEISURE, -25);
            add(w, TripPurpose.VFR, -10);
            if (domestic) {
                add(w, TripPurpose.BUSINESS, 10);
            }
        }
        if (weekend) {
            add(w, TripPurpose.LEISURE, 20);
            add(w, TripPurpose.BUSINESS, -18);
        }
        if (lowCost) {
            add(w, TripPurpose.LEISURE, 12);
            add(w, TripPurpose.VFR, 8);
            add(w, TripPurpose.BUSINESS, -18);
        }
        switch (flavor) {
            case BUSINESS_HUB -> add(w, TripPurpose.BUSINESS, 18);
            case HOLIDAY -> {
                add(w, TripPurpose.LEISURE, 22);
                add(w, TripPurpose.BUSINESS, -10);
            }
            case VFR -> {
                add(w, TripPurpose.VFR, 22);
                add(w, TripPurpose.BUSINESS, -8);
            }
            default -> { /* domestic/generic: no regional skew */ }
        }
        if (premiumCabin && !domestic) {
            add(w, TripPurpose.BUSINESS, 10);
        }
        return normalize(w);
    }

    private static Map<PartyType, Integer> partyType(
            Random jitter, TripPurpose dominant, Flavor flavor, boolean weekend, boolean lowCost, boolean premiumCabin) {
        EnumMap<PartyType, Double> w = seed(PartyType.class, jitter, 35, 28, 22, 15);
        switch (dominant) {
            case BUSINESS -> {
                add(w, PartyType.SOLO, 30);
                add(w, PartyType.COUPLE, -8);
                add(w, PartyType.FAMILY, -15);
                add(w, PartyType.GROUP, -7);
            }
            case LEISURE -> {
                add(w, PartyType.FAMILY, 18);
                add(w, PartyType.COUPLE, 12);
                add(w, PartyType.SOLO, -22);
            }
            case VFR -> {
                add(w, PartyType.FAMILY, 15);
                add(w, PartyType.GROUP, 12);
                add(w, PartyType.SOLO, -18);
                add(w, PartyType.COUPLE, -4);
            }
        }
        if (flavor == Flavor.HOLIDAY) {
            add(w, PartyType.FAMILY, 12);
            add(w, PartyType.COUPLE, 10);
            add(w, PartyType.SOLO, -15);
        } else if (flavor == Flavor.VFR) {
            add(w, PartyType.FAMILY, 10);
            add(w, PartyType.GROUP, 8);
        }
        if (weekend) {
            add(w, PartyType.FAMILY, 8);
            add(w, PartyType.COUPLE, 5);
            add(w, PartyType.SOLO, -10);
        }
        if (lowCost) {
            add(w, PartyType.GROUP, 10);
            add(w, PartyType.FAMILY, 4);
        }
        if (premiumCabin && dominant == TripPurpose.BUSINESS) {
            add(w, PartyType.SOLO, 5);
        }
        return normalize(w);
    }

    private static Map<AgeBand, Integer> ageBand(
            Random jitter, TripPurpose dominant, Flavor flavor, boolean lowCost, boolean domestic) {
        EnumMap<AgeBand, Double> w = seed(AgeBand.class, jitter, 15, 40, 30, 15);
        switch (dominant) {
            case BUSINESS -> {
                add(w, AgeBand.A25_44, 18);
                add(w, AgeBand.A45_64, 6);
                add(w, AgeBand.A18_24, -14);
                add(w, AgeBand.A65_PLUS, -10);
            }
            case LEISURE -> add(w, AgeBand.A25_44, 8);
            case VFR -> {
                add(w, AgeBand.A45_64, 10);
                add(w, AgeBand.A65_PLUS, 10);
                add(w, AgeBand.A25_44, -8);
            }
        }
        if (lowCost && domestic) {
            add(w, AgeBand.A18_24, 12);
        }
        if (flavor == Flavor.HOLIDAY) {
            add(w, AgeBand.A45_64, 6);
            add(w, AgeBand.A65_PLUS, 4);
        }
        return normalize(w);
    }

    private static Map<SpendTier, Integer> spendTier(
            Random jitter, TripPurpose dominant, Flavor flavor, boolean lowCost, boolean premiumCabin) {
        EnumMap<SpendTier, Double> w = seed(SpendTier.class, jitter, 60, 28, 12);
        if (premiumCabin) {
            add(w, SpendTier.HIGH, 18);
            add(w, SpendTier.LOW, -15);
            add(w, SpendTier.MEDIUM, -3);
        }
        if (lowCost) {
            add(w, SpendTier.LOW, 25);
            add(w, SpendTier.HIGH, -12);
            add(w, SpendTier.MEDIUM, -10);
        }
        if (dominant == TripPurpose.BUSINESS) {
            add(w, SpendTier.HIGH, 10);
            add(w, SpendTier.MEDIUM, 5);
            add(w, SpendTier.LOW, -15);
        }
        if (flavor == Flavor.HOLIDAY) {
            add(w, SpendTier.MEDIUM, 10);
            add(w, SpendTier.LOW, -8);
        } else if (flavor == Flavor.BUSINESS_HUB) {
            add(w, SpendTier.HIGH, 6);
        }
        return normalize(w);
    }

    // --- distribution helpers --------------------------------------------

    @SafeVarargs
    private static <E extends Enum<E>> EnumMap<E, Double> seed(Class<E> type, Random jitter, int... base) {
        E[] keys = type.getEnumConstants();
        EnumMap<E, Double> w = new EnumMap<>(type);
        for (int i = 0; i < keys.length; i++) {
            w.put(keys[i], (double) (base[i] + (jitter.nextInt(7) - 3)));
        }
        return w;
    }

    private static <E extends Enum<E>> void add(EnumMap<E, Double> w, E key, double delta) {
        w.merge(key, delta, Double::sum);
    }

    private static <E extends Enum<E>> Map<E, Integer> normalize(EnumMap<E, Double> raw) {
        return Distributions.normalize(raw);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
