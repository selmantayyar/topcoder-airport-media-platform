package com.airportmedia.platform.service.decision;

import com.airportmedia.platform.common.enums.FlightDirection;
import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.GateAssignment;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Tomorrow's departures, projected from today's: an airport's schedule repeats daily, so each of today's
 * departures runs again one day later, at its scheduled time and gate. Today's disruption (delays,
 * cancellations) is not carried over — it can't be predicted a day ahead. The copies are detached
 * (never persisted) and today's entities are left untouched.
 */
public final class TomorrowProjection {

    private static final Duration ONE_DAY = Duration.ofDays(1);

    private TomorrowProjection() {}

    /** @param today start of today; only departures scheduled within [today, today + 1 day) are projected */
    public static List<GateAssignment> project(List<GateAssignment> assignments, Instant today) {
        Instant end = today.plus(ONE_DAY);
        return assignments.stream()
                .filter(ga -> ga.flight != null && ga.flight.direction == FlightDirection.DEPARTURE)
                .filter(ga -> !ga.flight.scheduledTime.isBefore(today) && ga.flight.scheduledTime.isBefore(end))
                .map(TomorrowProjection::nextDay)
                .toList();
    }

    private static GateAssignment nextDay(GateAssignment today) {
        Flight f = today.flight;
        Flight next = new Flight();
        next.id = f.id;
        next.flightNumber = f.flightNumber;
        next.airline = f.airline;
        next.direction = f.direction;
        next.origin = f.origin;
        next.destination = f.destination;
        next.via = f.via;
        next.haul = f.haul;
        next.aircraftType = f.aircraftType;
        next.paxCount = f.paxCount;
        next.status = FlightStatus.SCHEDULED;
        next.scheduledTime = f.scheduledTime.plus(ONE_DAY);

        GateAssignment ga = new GateAssignment();
        ga.flight = next;
        ga.gate = today.gate;
        ga.stand = today.stand;
        ga.zone = today.zone;
        return ga;
    }
}
