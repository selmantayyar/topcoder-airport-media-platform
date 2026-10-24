package com.airportmedia.platform.service;

import com.airportmedia.platform.common.enums.AgeBand;
import com.airportmedia.platform.common.enums.FlightDirection;
import com.airportmedia.platform.common.enums.PartyType;
import com.airportmedia.platform.common.enums.SpendTier;
import com.airportmedia.platform.common.enums.TripPurpose;
import com.airportmedia.platform.common.util.Distributions;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.GateAssignment;
import com.airportmedia.platform.domain.entity.QueueMeasurement;
import com.airportmedia.platform.domain.entity.Zone;
import com.airportmedia.platform.domain.model.output.CrowdProfile;
import com.airportmedia.platform.domain.model.output.ZonePresence;
import com.airportmedia.platform.domain.model.output.ZonePresence.FlightContribution;
import com.airportmedia.platform.repository.GateAssignmentRepository;
import com.airportmedia.platform.repository.QueueMeasurementRepository;
import com.airportmedia.platform.repository.ZoneRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Estimates, per zone and per 5-minute window, how many passengers are near it and who they
 * are. Each departure whose gate sits in the zone contributes {@code paxCount ×} a
 * {@link #presenceFraction time-to-boarding weight} — passengers gather as boarding nears and
 * thin out once the flight has left. Checkpoint zones add their latest ACRIS queue occupancy.
 * The zone's {@link CrowdProfile} is the head-count-weighted blend of its contributing flights.
 */
@ApplicationScoped
public class ZonePresenceService {

    static final int WINDOW_MINUTES = 5;

    private final ZoneRepository zones;
    private final GateAssignmentRepository gateAssignments;
    private final QueueMeasurementRepository queueMeasurements;
    private final CrowdProfiler profiler;
    private final SimulationClock clock;

    public ZonePresenceService(
            ZoneRepository zones,
            GateAssignmentRepository gateAssignments,
            QueueMeasurementRepository queueMeasurements,
            CrowdProfiler profiler,
            SimulationClock clock) {
        this.zones = zones;
        this.gateAssignments = gateAssignments;
        this.queueMeasurements = queueMeasurements;
        this.profiler = profiler;
        this.clock = clock;
    }

    public List<ZonePresence> snapshot() {
        return snapshotAt(clock.snapshot().simTime());
    }

    @Transactional
    public List<ZonePresence> snapshotAt(Instant now) {
        Map<Long, List<GateAssignment>> byZone = new HashMap<>();
        for (GateAssignment ga : gateAssignments.listAll()) {
            if (ga.zone != null) {
                byZone.computeIfAbsent(ga.zone.id, k -> new ArrayList<>()).add(ga);
            }
        }
        Map<Long, Integer> queueByZone = queueOccupancyByZone(now);

        return zones.listAll().stream()
                .map(zone -> compute(
                        zone,
                        byZone.getOrDefault(zone.id, List.of()),
                        queueByZone.getOrDefault(zone.id, 0),
                        now,
                        WINDOW_MINUTES))
                .sorted(Comparator.comparingInt(ZonePresence::estimatedHeadcount).reversed())
                .toList();
    }

    public ZonePresence compute(Zone zone, List<GateAssignment> assignments, int queueOccupancy, Instant now, int windowMinutes) {
        Instant windowStart = floorToWindow(now, windowMinutes);
        List<FlightContribution> contributions = new ArrayList<>();
        List<CrowdProfile> profiles = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int flightHeadcount = 0;

        for (GateAssignment ga : assignments) {
            Flight flight = ga.flight;
            if (flight == null || flight.direction != FlightDirection.DEPARTURE) {
                continue;
            }
            Instant departure = flight.departureTime();
            long minutesToDeparture = Duration.between(now, departure).toMinutes();
            int pax = (int) Math.round(flight.paxCount * presenceFraction(minutesToDeparture));
            if (pax <= 0) {
                continue;
            }
            contributions.add(new FlightContribution(
                    flight.id, flight.flightNumber, ga.gate, flight.destination, minutesToDeparture, pax));
            profiles.add(profiler.profile(flight));
            weights.add(pax);
            flightHeadcount += pax;
        }

        contributions.sort(Comparator.comparingLong(FlightContribution::minutesToDeparture));
        int headcount = flightHeadcount + Math.max(0, queueOccupancy);
        CrowdProfile blended = profiles.isEmpty() ? null : blend(profiles, weights, headcount);

        return new ZonePresence(
                zone.id, zone.name, zone.kind, windowStart, windowMinutes, headcount, contributions, blended);
    }

    /**
     * Fraction of a flight's passengers estimated to be near its gate: zero until ~2h out,
     * ramping to full by 40 minutes before departure, full through boarding, then fading to
     * zero by 15 minutes after departure.
     */
    public static double presenceFraction(long minutesToDeparture) {
        long m = minutesToDeparture;
        if (m >= 120 || m < -15) {
            return 0.0;
        }
        if (m >= 40) {
            return (120 - m) / 80.0;
        }
        if (m >= 0) {
            return 1.0;
        }
        return (15.0 + m) / 15.0;
    }

    // --- blending ---------------------------------------------------------

    private CrowdProfile blend(List<CrowdProfile> profiles, List<Integer> weights, int crowdSize) {
        boolean delayed = profiles.stream().anyMatch(CrowdProfile::delayed);
        int delayMinutes = profiles.stream().mapToInt(CrowdProfile::delayMinutes).max().orElse(0);
        return new CrowdProfile(
                crowdSize,
                blendDistribution(profiles, weights, CrowdProfile::tripPurpose, TripPurpose.class),
                blendDistribution(profiles, weights, CrowdProfile::partyType, PartyType.class),
                blendDistribution(profiles, weights, CrowdProfile::ageBand, AgeBand.class),
                (int) Math.round(weightedAverage(profiles, weights, CrowdProfile::femalePct)),
                blendDistribution(profiles, weights, CrowdProfile::spendTier, SpendTier.class),
                dominantValue(profiles, weights, CrowdProfile::language),
                dominantValue(profiles, weights, CrowdProfile::region),
                (int) Math.round(weightedAverage(profiles, weights, CrowdProfile::dwellMinutes)),
                delayed,
                delayMinutes);
    }

    private static <E extends Enum<E>> Map<E, Integer> blendDistribution(
            List<CrowdProfile> profiles, List<Integer> weights,
            Function<CrowdProfile, Map<E, Integer>> selector, Class<E> type) {
        EnumMap<E, Double> acc = new EnumMap<>(type);
        for (E key : type.getEnumConstants()) {
            acc.put(key, 0.0);
        }
        for (int i = 0; i < profiles.size(); i++) {
            double weight = weights.get(i);
            selector.apply(profiles.get(i)).forEach((key, pct) -> acc.merge(key, pct * weight, Double::sum));
        }
        return Distributions.normalize(acc);
    }

    private static double weightedAverage(
            List<CrowdProfile> profiles, List<Integer> weights, Function<CrowdProfile, Integer> selector) {
        double totalWeight = 0;
        double sum = 0;
        for (int i = 0; i < profiles.size(); i++) {
            sum += selector.apply(profiles.get(i)) * (double) weights.get(i);
            totalWeight += weights.get(i);
        }
        return totalWeight == 0 ? 0 : sum / totalWeight;
    }

    private static String dominantValue(
            List<CrowdProfile> profiles, List<Integer> weights, Function<CrowdProfile, String> selector) {
        Map<String, Integer> byWeight = new HashMap<>();
        for (int i = 0; i < profiles.size(); i++) {
            byWeight.merge(selector.apply(profiles.get(i)), weights.get(i), Integer::sum);
        }
        return byWeight.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    // --- queue occupancy --------------------------------------------------

    /** Latest reading per checkpoint at or before {@code now}, summed into its zone. */
    private Map<Long, Integer> queueOccupancyByZone(Instant now) {
        Map<Long, QueueMeasurement> latestByCheckpoint = new HashMap<>();
        for (QueueMeasurement m : queueMeasurements.listAll()) {
            if (m.checkpoint == null || m.measuredAt.isAfter(now)) {
                continue;
            }
            latestByCheckpoint.merge(m.checkpoint.id, m,
                    (a, b) -> a.measuredAt.isAfter(b.measuredAt) ? a : b);
        }
        Map<Long, Integer> byZone = new HashMap<>();
        for (QueueMeasurement m : latestByCheckpoint.values()) {
            if (m.checkpoint.zone != null) {
                byZone.merge(m.checkpoint.zone.id, m.occupancy, Integer::sum);
            }
        }
        return byZone;
    }

    private static Instant floorToWindow(Instant now, int windowMinutes) {
        long windowSeconds = windowMinutes * 60L;
        long epoch = now.getEpochSecond();
        return Instant.ofEpochSecond(epoch - Math.floorMod(epoch, windowSeconds));
    }
}
