package com.airportmedia.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.FlightEventType;
import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.common.enums.ScreenStatus;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.FlightEvent;
import com.airportmedia.platform.domain.entity.Screen;
import com.airportmedia.platform.repository.FlightEventRepository;
import com.airportmedia.platform.repository.FlightRepository;
import com.airportmedia.platform.repository.GateAssignmentRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
class SimulationServiceTest {

    @Inject
    SimulationService simulation;

    @Inject
    FlightRepository flights;

    @Inject
    FlightEventRepository flightEvents;

    @Inject
    GateAssignmentRepository gateAssignments;

    @Inject
    SimulationClock clock;

    @Inject
    EntityManager em;

    private Screen gate42Screen() {
        return em.createQuery("from Screen where code = 'SCR-GATE42'", Screen.class).getSingleResult();
    }

    private FlightEvent latestEvent() {
        return flightEvents.findAll(io.quarkus.panache.common.Sort.by("id").descending()).firstResult();
    }

    @Test
    void injectingADelayUpdatesTheFlightAndRecordsAnAidxEvent() {
        long eventsBefore = flightEvents.count();

        simulation.injectDelay(null, 30, null);

        assertThat(flightEvents.count()).isEqualTo(eventsBefore + 1);
        FlightEvent latest = latestEvent();
        assertThat(latest.type).isEqualTo(FlightEventType.DELAY);
        assertThat(latest.delayMinutes).isEqualTo(30);
        assertThat(latest.source).isEqualTo("AIDX");

        Flight delayed = latest.flight;
        assertThat(delayed.status).isEqualTo(FlightStatus.DELAYED);
        assertThat(delayed.delayMinutes).isEqualTo(30);
        assertThat(delayed.estimatedTime).isEqualTo(delayed.scheduledTime.plusSeconds(30 * 60L));
    }

    @Test
    void delayWithoutAnExplicitMinutesUsesTheDefault() {
        simulation.injectDelay(null, null, null);

        assertThat(latestEvent().delayMinutes).isEqualTo(45);
    }

    @Test
    void injectingAGateChangeMovesTheGateAndRecordsAGateChangeEvent() {
        var event = simulation.injectGateChange(null, null, null);

        FlightEvent latest = latestEvent();
        assertThat(latest.type).isEqualTo(FlightEventType.GATE_CHANGE);
        assertThat(latest.newGate).isNotBlank();

        Long flightId = ((java.util.Map<?, ?>) event.data()).get("flightId") instanceof Long id ? id : null;
        assertThat(gateAssignments.findByFlightId(flightId).orElseThrow().gate)
                .isEqualTo(latest.newGate);
    }

    @Test
    void injectingAScreenOfflineChangesScreenStatus() {
        var event = simulation.injectScreenOffline(null);

        assertThat(event.type()).isEqualTo("screen-offline");
    }

    @Test
    void aDelayAimedAtAScreenLandsOnTheNextDepartureFromThatScreensGates() {
        clock.jumpTo(Instant.parse("2026-10-23T14:00:00Z"));
        Screen gate42 = gate42Screen();

        simulation.injectDelay(null, 30, gate42.id);

        // Gate 42's screen covers gates 40-45; XY501 is already delayed, so the next one to board is ZQ88.
        assertThat(latestEvent().flight.flightNumber).isEqualTo("ZQ88");
    }

    @Test
    void aScreenTakenOfflineCanBeBroughtBackForTheNextDemoRun() {
        Screen gate42 = gate42Screen();
        simulation.injectScreenOffline(gate42.id);

        var event = simulation.injectScreenOnline(gate42.id);

        assertThat(event.type()).isEqualTo("screen-online");
        assertThat(gate42Screen().status).isEqualTo(ScreenStatus.ONLINE);
    }

    @Test
    void aGateChangeAimedAtAScreenMovesAFlightNearThatScreen() {
        clock.jumpTo(Instant.parse("2026-10-23T14:00:00Z"));
        Screen gate42 = gate42Screen();

        var event = simulation.injectGateChange(null, null, gate42.id);

        assertThat(latestEvent().type).isEqualTo(FlightEventType.GATE_CHANGE);
        assertThat(gateAssignments.findByFlightId(latestEvent().flight.id).orElseThrow().zone.id)
                .isEqualTo(gate42.zone.id);
    }
}
