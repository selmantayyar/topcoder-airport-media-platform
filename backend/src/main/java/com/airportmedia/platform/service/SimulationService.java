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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.time.ZoneOffset;
import java.util.Map;
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
            EventStream stream) {
        this.flights = flights;
        this.gateAssignments = gateAssignments;
        this.screens = screens;
        this.flightEvents = flightEvents;
        this.eventSource = eventSource;
        this.aidxTranslator = aidxTranslator;
        this.flightEventMapper = flightEventMapper;
        this.clock = clock;
        this.stream = stream;
    }

    @Transactional
    public ServerEvent injectDelay(Long flightId, Integer minutes) {
        Flight flight = resolveFlight(flightId, flights.firstUndelayedDeparture().orElse(null));
        int delay = minutes != null ? minutes : DEFAULT_DELAY_MINUTES;

        flight.status = FlightStatus.DELAYED;
        flight.delayMinutes = delay;
        flight.estimatedTime = flight.scheduledTime.plusSeconds(delay * 60L);

        persistAidxEvent(flight, delay, null, "DL");

        ServerEvent event = ServerEvent.of("flight-delayed", Map.of(
                "flightId", flight.id,
                "flightNumber", flight.flightNumber,
                "delayMinutes", delay));
        stream.emit(event);
        return event;
    }

    @Transactional
    public ServerEvent injectGateChange(Long flightId, String newGate) {
        Flight flight = resolveFlight(flightId, flights.firstUndelayedDeparture().orElse(null));
        GateAssignment assignment = gateAssignments.findByFlightId(flight.id)
                .orElseThrow(() -> new NotFoundException("No gate assignment for flight " + flight.id));
        String target = newGate != null ? newGate : alternativeGate(assignment.gate);

        assignment.gate = target;
        persistAidxEvent(flight, null, target, "SH");

        ServerEvent event = ServerEvent.of("gate-changed", Map.of(
                "flightId", flight.id,
                "flightNumber", flight.flightNumber,
                "newGate", target));
        stream.emit(event);
        return event;
    }

    @Transactional
    public ServerEvent injectScreenOffline(Long screenId) {
        Screen screen = screenId != null
                ? screens.findByIdOptional(screenId).orElseThrow(() -> new NotFoundException("No screen " + screenId))
                : screens.firstOnline().orElseThrow(() -> new NotFoundException("No online screen to take offline"));

        screen.status = ScreenStatus.OFFLINE;

        ServerEvent event = ServerEvent.of("screen-offline", Map.of(
                "screenId", screen.id,
                "code", screen.code));
        stream.emit(event);
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
