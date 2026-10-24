package com.airportmedia.platform.service;

import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.domain.entity.Flight;
import java.time.Duration;
import java.time.Instant;

/** How a departure reads on a flight board at a given moment — shared by the passenger FIDS and the airline view. */
public final class FlightBoard {

    /**
     * Boarding runs from this many minutes before departure. The single definition of the boarding window: the
     * board status, the decision pipeline's airline-priority rule, and airline sponsored content all use it.
     */
    public static final long BOARDING_WINDOW_MINUTES = 40;

    private FlightBoard() {}

    public static long minutesToDeparture(Flight flight, Instant now) {
        return Duration.between(now, flight.departureTime()).toMinutes();
    }

    /** DEPARTED once its (estimated) time has passed — even if it left late — else DELAYED, BOARDING or ON TIME. */
    public static String status(Flight flight, Instant now) {
        long minutes = minutesToDeparture(flight, now);
        if (minutes < 0) {
            return "DEPARTED";
        }
        if (flight.status == FlightStatus.DELAYED) {
            return "DELAYED";
        }
        return minutes <= BOARDING_WINDOW_MINUTES ? "BOARDING" : "ON TIME";
    }
}
