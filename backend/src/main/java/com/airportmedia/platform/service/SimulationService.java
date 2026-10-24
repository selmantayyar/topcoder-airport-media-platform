package com.airportmedia.platform.service;

import com.airportmedia.platform.adapter.aidx.AidxLegChange;
import com.airportmedia.platform.adapter.aidx.AidxTranslator;
import com.airportmedia.platform.adapter.aidx.FlightEventSourceProvider;
import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.common.enums.ScreenStatus;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.FlightEvent;
import com.airportmedia.platform.domain.entity.GateAssignment;
import com.airportmedia.platform.domain.entity.Screen;
import com.airportmedia.platform.domain.model.mapper.FlightEventMapper;
import com.airportmedia.platform.domain.model.output.ServerEvent;
import com.airportmedia.platform.repository.FlightEventRepository;
import com.airportmedia.platform.repository.FlightRepository;
import com.airportmedia.platform.repository.GateAssignmentRepository;
import com.airportmedia.platform.repository.ScreenRepository;
import com.airportmedia.platform.service.airline.DelayGoodwillService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Applies injected simulation events. Flight changes are routed through the AIDX
 * adapter — we serialise an AIDX message and translate it back — so an injected delay
 * produces a real {@link FlightEvent} on the same path a live feed would, and every
 * change is broadcast on the live stream for dashboards to react within 2 seconds.
 */
@ApplicationScoped
public class SimulationService {

    private final FlightRepository flights;
    private final GateAssignmentRepository gateAssignments;
    private final ScreenRepository screens;
    private final FlightEventRepository flightEvents;
    private final FlightEventSourceProvider eventSource;
    private final AidxTranslator aidxTranslator;
    private final FlightEventMapper flightEventMapper;
    private final SimulationClock clock;
    private final EventStream stream;
    private final DelayGoodwillService goodwill;
    private final AtomicInteger messageSeq = new AtomicInteger(1000);

    private static final int DEFAULT_DELAY_MINUTES = 45;

    public SimulationService(
            FlightRepository flights,
            GateAssignmentRepository gateAssignments,
            ScreenRepository screens,
            FlightEventRepository flightEvents,
            FlightEventSourceProvider eventSource,
            AidxTranslator aidxTranslator,
            FlightEventMapper flightEventMapper,
            SimulationClock clock,
            EventStream stream,
            DelayGoodwillService goodwill) {
        this.flights = flights;
        this.gateAssignments = gateAssignments;
        this.screens = screens;
        this.flightEvents = flightEvents;
        this.eventSource = eventSource;
        this.aidxTranslator = aidxTranslator;
        this.flightEventMapper = flightEventMapper;
        this.clock = clock;
        this.stream = stream;
        this.goodwill = goodwill;
    }

    /** @param screenId when set (and no flightId), the delay lands on a flight near that screen */
    @Transactional
    public ServerEvent injectDelay(Long flightId, Integer minutes, Long screenId) {
        Flight flight = resolveFlight(flightId, defaultTarget(screenId));
        int delay = minutes != null ? minutes : DEFAULT_DELAY_MINUTES;

        flight.status = FlightStatus.DELAYED;
        flight.delayMinutes = delay;
        flight.estimatedTime = flight.scheduledTime.plusSeconds(delay * 60L);

        persistAidxEvent(flight, delay, null, "DL");
        goodwill.handle(flight);

        ServerEvent event = ServerEvent.of("flight-delayed", Map.of(
                "flightId", flight.id,
                "flightNumber", flight.flightNumber,
                "delayMinutes", delay));
        stream.emitAfterCommit(event);
        return event;
    }

    /** @param screenId when set (and no flightId), the gate change lands on a flight near that screen */
    @Transactional
    public ServerEvent injectGateChange(Long flightId, String newGate, Long screenId) {
        Flight flight = resolveFlight(flightId, defaultTarget(screenId));
        GateAssignment assignment = gateAssignments.findByFlightId(flight.id)
                .orElseThrow(() -> new NotFoundException("No gate assignment for flight " + flight.id));
        String target = newGate != null ? newGate : alternativeGate(assignment.gate);

        assignment.gate = target;
        persistAidxEvent(flight, null, target, "SH");

        ServerEvent event = ServerEvent.of("gate-changed", Map.of(
                "flightId", flight.id,
                "flightNumber", flight.flightNumber,
                "newGate", target));
        stream.emitAfterCommit(event);
        return event;
    }

    @Transactional
    public ServerEvent injectScreenOffline(Long screenId) {
        Screen screen = screenId != null
                ? screens.findByIdOptional(screenId).orElseThrow(() -> new NotFoundException("No screen " + screenId))
                : screens.firstOnline().orElseThrow(() -> new NotFoundException("No online screen to take offline"));

        screen.status = ScreenStatus.OFFLINE;
        screen.statusChangedAt = clock.snapshot().simTime();

        ServerEvent event = ServerEvent.of("screen-offline", Map.of(
                "screenId", screen.id,
                "code", screen.code));
        stream.emitAfterCommit(event);
        return event;
    }

    /** Brings a screen back online (the given one, else the first offline screen) so a demo can be re-run. */
    @Transactional
    public ServerEvent injectScreenOnline(Long screenId) {
        Screen screen = screenId != null
                ? screens.findByIdOptional(screenId).orElseThrow(() -> new NotFoundException("No screen " + screenId))
                : screens.firstOffline().orElseThrow(() -> new NotFoundException("No offline screen to restore"));

        screen.status = ScreenStatus.ONLINE;
        screen.statusChangedAt = clock.snapshot().simTime();

        ServerEvent event = ServerEvent.of("screen-online", Map.of(
                "screenId", screen.id,
                "code", screen.code));
        stream.emitAfterCommit(event);
        return event;
    }

    private void persistAidxEvent(Flight flight, Integer delayMinutes, String newGate, String statusCode) {
        String numeric = flight.flightNumber.replaceAll("^[A-Za-z]+", "");
        String originDate = flight.scheduledTime.atZone(ZoneOffset.UTC).toLocalDate().toString();
        AidxLegChange change = new AidxLegChange(
                flight.airline.iataCode, numeric, originDate, delayMinutes, newGate, statusCode,
                clock.snapshot().simTime(), String.format("AMP-AIDX-INJ-%d", messageSeq.incrementAndGet()));

        String xml = eventSource.active().buildFlightLegNotif(change);
        var update = aidxTranslator.parse(xml);
        flightEvents.persist(flightEventMapper.toFlightEvent(update, flight));
    }

    /**
     * The injection target when no flight is named: the next undelayed departure from the given screen's own
     * gates, else from its zone (so the change shows on that screen's FIDS strip and decision), else — or with
     * no screen — the next undelayed departure anywhere.
     */
    private Flight defaultTarget(Long screenId) {
        Instant now = clock.snapshot().simTime();
        if (screenId != null) {
            Screen screen = screens.findByIdOptional(screenId)
                    .orElseThrow(() -> new NotFoundException("No screen " + screenId));
            if (screen.zone != null) {
                List<GateAssignment> upcoming = gateAssignments.findDeparturesInZone(screen.zone.id).stream()
                        .filter(ga -> ga.flight.status != FlightStatus.DELAYED && ga.flight.scheduledTime.isAfter(now))
                        .toList();
                Comparator<GateAssignment> soonest = Comparator.comparing(ga -> ga.flight.scheduledTime);
                Optional<GateAssignment> near = upcoming.stream()
                        .filter(ga -> screen.gates().contains(ga.gate))
                        .min(soonest)
                        .or(() -> upcoming.stream().min(soonest));
                if (near.isPresent()) {
                    return near.get().flight;
                }
            }
        }
        return flights.nextUndelayedDeparture(now).orElse(null);
    }

    private Flight resolveFlight(Long flightId, Flight fallback) {
        if (flightId != null) {
            return flights.findByIdOptional(flightId)
                    .orElseThrow(() -> new NotFoundException("No flight " + flightId));
        }
        if (fallback == null) {
            throw new NotFoundException("No eligible flight to inject onto");
        }
        return fallback;
    }

    /** A plausible reassignment: the adjacent gate number (e.g. 42 -> 43). */
    private static String alternativeGate(String current) {
        try {
            return String.valueOf(Integer.parseInt(current) + 1);
        } catch (NumberFormatException e) {
            return current + "A";
        }
    }
}
