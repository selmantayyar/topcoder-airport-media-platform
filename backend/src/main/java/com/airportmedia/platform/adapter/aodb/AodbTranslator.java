package com.airportmedia.platform.adapter.aodb;

import com.airportmedia.platform.common.enums.FlightDirection;
import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.domain.entity.AircraftType;
import com.airportmedia.platform.domain.entity.Airline;
import com.airportmedia.platform.domain.entity.Flight;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;

/**
 * Translates one AODB wire record into the canonical {@link Flight}. The airline and
 * aircraft type are resolved by the caller (they are shared reference data), so this
 * stays a pure, unit-testable mapping of the AODB-owned fields.
 */
@ApplicationScoped
public class AodbTranslator {

    public Flight toFlight(AodbRecord row, Airline airline, AircraftType aircraftType) {
        Flight flight = new Flight();
        flight.flightNumber = row.flightNumber();
        flight.airline = airline;
        flight.aircraftType = aircraftType;
        flight.direction = "ARR".equals(row.direction()) ? FlightDirection.ARRIVAL : FlightDirection.DEPARTURE;
        flight.origin = row.originIata();
        flight.destination = row.destinationIata();
        flight.via = blankToNull(row.viaIata());
        flight.haul = "DOM".equals(row.haul()) ? Haul.DOMESTIC : Haul.INTERNATIONAL;
        flight.paxCount = parseInt(row.paxCount(), 0);
        flight.status = FlightStatus.valueOf(row.status());
        flight.delayMinutes = parseInt(row.delayMinutes(), 0);
        flight.delayCode = blankToNull(row.delayCode1());
        flight.scheduledTime = parseInstant(row.std());
        flight.estimatedTime = parseInstant(row.etd());
        flight.actualTime = parseInstant(row.atd());
        flight.tobt = parseInstant(row.tobt());
        flight.tsat = parseInstant(row.tsat());
        flight.aibt = parseInstant(row.aibt());
        flight.aobt = parseInstant(row.aobt());
        flight.checkInCounters = blankToNull(row.checkInCounters());
        flight.belt = blankToNull(row.baggageBelt());
        return flight;
    }

    public String gate(AodbRecord row) {
        return row.gate();
    }

    public String stand(AodbRecord row) {
        return blankToNull(row.stand());
    }

    private static Instant parseInstant(String value) {
        return (value == null || value.isBlank()) ? null : Instant.parse(value);
    }

    private static int parseInt(String value, int fallback) {
        return (value == null || value.isBlank()) ? fallback : Integer.parseInt(value);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
