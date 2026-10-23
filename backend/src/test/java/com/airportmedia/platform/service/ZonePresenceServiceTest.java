package com.airportmedia.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.airportmedia.platform.common.RouteDirectory;
import com.airportmedia.platform.common.enums.FlightDirection;
import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.common.enums.ZoneKind;
import com.airportmedia.platform.domain.entity.AircraftType;
import com.airportmedia.platform.domain.entity.Airline;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.GateAssignment;
import com.airportmedia.platform.domain.entity.Zone;
import com.airportmedia.platform.domain.model.output.ZonePresence;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure unit tests of the presence math — no database needed. */
class ZonePresenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T14:02:00Z");

    private final ZonePresenceService service =
            new ZonePresenceService(null, null, null, new CrowdProfiler(new RouteDirectory()), null);

    @Test
    void presenceFractionRampsUpTowardBoardingAndFadesAfterDeparture() {
        assertThat(ZonePresenceService.presenceFraction(200)).isZero();
        assertThat(ZonePresenceService.presenceFraction(120)).isZero();
        assertThat(ZonePresenceService.presenceFraction(80)).isEqualTo(0.5, within(0.01));
        assertThat(ZonePresenceService.presenceFraction(20)).isEqualTo(1.0, within(0.01));
        assertThat(ZonePresenceService.presenceFraction(0)).isEqualTo(1.0, within(0.01));
        assertThat(ZonePresenceService.presenceFraction(-20)).isZero();

        assertThat(ZonePresenceService.presenceFraction(15))
                .isGreaterThan(ZonePresenceService.presenceFraction(120));
    }

    @Test
    void countsPassengersFromFlightsCloseToBoardingAndIgnoresDistantOnes() {
        Zone zone = zone(1L, "Pier D", ZoneKind.GATE_AREA);
        GateAssignment boarding = gate(zone, "42", departure("XY501", "DXB", 200, NOW.plusSeconds(20 * 60)));
        GateAssignment distant = gate(zone, "43", departure("ZQ88", "MLE", 300, NOW.plusSeconds(180 * 60)));

        ZonePresence presence = service.compute(zone, List.of(boarding, distant), 0, NOW, 5);

        assertThat(presence.estimatedHeadcount()).isEqualTo(200);
        assertThat(presence.flights()).hasSize(1);
        assertThat(presence.flights().get(0).flightNumber()).isEqualTo("XY501");
        assertThat(presence.flights().get(0).passengers()).isEqualTo(200);
        assertThat(presence.profile()).isNotNull();
        assertThat(presence.profile().crowdSize()).isEqualTo(200);
        assertThat(presence.windowMinutes()).isEqualTo(5);
        assertThat(presence.windowStart()).isBeforeOrEqualTo(NOW);
    }

    @Test
    void checkpointZoneHeadcountComesFromQueueOccupancy() {
        Zone zone = zone(2L, "T2 Security Checkpoint", ZoneKind.CHECKPOINT);

        ZonePresence presence = service.compute(zone, List.of(), 55, NOW, 5);

        assertThat(presence.estimatedHeadcount()).isEqualTo(55);
        assertThat(presence.flights()).isEmpty();
        assertThat(presence.profile()).isNull();
    }

    @Test
    void blendsTheProfilesOfEveryContributingFlight() {
        Zone zone = zone(3L, "Pier A", ZoneKind.GATE_AREA);
        GateAssignment one = gate(zone, "1", departure("XY100", "LHR", 180, NOW.plusSeconds(15 * 60)));
        GateAssignment two = gate(zone, "2", departure("ZQ200", "MLE", 160, NOW.plusSeconds(25 * 60)));

        ZonePresence presence = service.compute(zone, List.of(one, two), 0, NOW, 5);

        assertThat(presence.estimatedHeadcount()).isEqualTo(340);
        assertThat(presence.flights()).hasSize(2);
        assertThat(presence.profile().crowdSize()).isEqualTo(340);
        assertThat(presence.profile().tripPurpose().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(100);
    }

    // --- builders ---------------------------------------------------------

    private static Zone zone(Long id, String name, ZoneKind kind) {
        Zone zone = new Zone();
        zone.id = id;
        zone.name = name;
        zone.kind = kind;
        return zone;
    }

    private static GateAssignment gate(Zone zone, String gate, Flight flight) {
        GateAssignment ga = new GateAssignment();
        ga.zone = zone;
        ga.gate = gate;
        ga.flight = flight;
        return ga;
    }

    private static Flight departure(String number, String destination, int pax, Instant scheduled) {
        Flight flight = new Flight();
        flight.flightNumber = number;
        flight.airline = carrier();
        flight.aircraftType = widebody();
        flight.direction = FlightDirection.DEPARTURE;
        flight.origin = "AMP";
        flight.destination = destination;
        flight.haul = Haul.INTERNATIONAL;
        flight.paxCount = pax;
        flight.status = FlightStatus.SCHEDULED;
        flight.scheduledTime = scheduled;
        flight.estimatedTime = scheduled;
        return flight;
    }

    private static Airline carrier() {
        Airline airline = new Airline();
        airline.iataCode = "XY";
        airline.icaoCode = "XYZ";
        airline.name = "Zephyr Airways";
        airline.lowCost = false;
        return airline;
    }

    private static AircraftType widebody() {
        AircraftType type = new AircraftType();
        type.icaoCode = "B77W";
        type.name = "Widebody";
        type.seatsEconomy = 290;
        type.seatsPremium = 28;
        type.seatsBusiness = 42;
        return type;
    }
}
