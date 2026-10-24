package com.airportmedia.platform.service.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.FlightDirection;
import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.GateAssignment;
import com.airportmedia.platform.domain.entity.Zone;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TomorrowProjectionTest {

    private static final Instant TODAY = Instant.parse("2026-10-23T00:00:00Z");

    @Test
    void repeatsTodaysScheduleOneDayLaterWithoutTodaysDisruption() {
        Flight delayed = departure("XY501", Instant.parse("2026-10-23T14:55:00Z"));
        delayed.status = FlightStatus.DELAYED;
        delayed.delayMinutes = 45;
        delayed.estimatedTime = Instant.parse("2026-10-23T15:40:00Z");
        GateAssignment today = gate(delayed, "42");

        List<GateAssignment> tomorrow = TomorrowProjection.project(List.of(today), TODAY);

        assertThat(tomorrow).hasSize(1);
        GateAssignment projected = tomorrow.get(0);
        assertThat(projected).isNotSameAs(today);
        assertThat(projected.gate).isEqualTo("42");
        assertThat(projected.zone).isSameAs(today.zone);
        assertThat(projected.flight.flightNumber).isEqualTo("XY501");
        assertThat(projected.flight.departureTime()).isEqualTo(Instant.parse("2026-10-24T14:55:00Z"));
        assertThat(projected.flight.status).isEqualTo(FlightStatus.SCHEDULED);
        assertThat(projected.flight.delayMinutes).isZero();
        assertThat(projected.flight.paxCount).isEqualTo(delayed.paxCount);
        assertThat(delayed.estimatedTime).as("today's flight is untouched").isNotNull();
    }

    @Test
    void cancelledFlightsStillRunTomorrowButOtherDaysAndArrivalsAreLeftOut() {
        Flight cancelled = departure("ZQ88", Instant.parse("2026-10-23T22:00:00Z"));
        cancelled.status = FlightStatus.CANCELLED;
        Flight yesterday = departure("OLD1", Instant.parse("2026-10-22T10:00:00Z"));
        Flight arrival = departure("ARR9", Instant.parse("2026-10-23T09:00:00Z"));
        arrival.direction = FlightDirection.ARRIVAL;

        List<GateAssignment> tomorrow = TomorrowProjection.project(
                List.of(gate(cancelled, "7"), gate(yesterday, "8"), gate(arrival, "9")), TODAY);

        assertThat(tomorrow).extracting(ga -> ga.flight.flightNumber).containsExactly("ZQ88");
        assertThat(tomorrow.get(0).flight.status).isEqualTo(FlightStatus.SCHEDULED);
    }

    private static Flight departure(String number, Instant scheduled) {
        Flight f = new Flight();
        f.id = (long) number.hashCode();
        f.flightNumber = number;
        f.direction = FlightDirection.DEPARTURE;
        f.origin = "XXX";
        f.destination = "DXB";
        f.haul = Haul.INTERNATIONAL;
        f.paxCount = 240;
        f.status = FlightStatus.SCHEDULED;
        f.scheduledTime = scheduled;
        return f;
    }

    private static GateAssignment gate(Flight flight, String gate) {
        Zone zone = new Zone();
        zone.id = 1L;
        GateAssignment ga = new GateAssignment();
        ga.flight = flight;
        ga.gate = gate;
        ga.zone = zone;
        return ga;
    }
}
