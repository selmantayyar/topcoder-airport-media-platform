package com.airportmedia.platform.adapter.aodb;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.FlightDirection;
import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.domain.entity.AircraftType;
import com.airportmedia.platform.domain.entity.Airline;
import com.airportmedia.platform.domain.entity.Flight;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AodbTranslatorTest {

    private final AodbTranslator translator = new AodbTranslator();

    // The spec's worked example: XY501 to DXB, 240 pax, delayed 45 min.
    private AodbRecord xy501() {
        return new AodbRecord(
                "XY501", "XY", "XYZ", "DEP", "AMP", "DXB", "", "B77W", "A6-ABC", "240",
                "2026-10-23T14:10:00Z", "2026-10-23T14:55:00Z", "", "", "", "",
                "2026-10-23T14:45:00Z", "2026-10-23T14:50:00Z", "", "",
                "42", "S42", "T1", "A10-A18", "", "DELAYED", "45", "93", "",
                "260", "28", "40", "INT");
    }

    private final Airline airline = new Airline();
    private final AircraftType aircraft = new AircraftType();

    @Test
    void mapsScalarFieldsFromTheRow() {
        Flight flight = translator.toFlight(xy501(), airline, aircraft);

        assertThat(flight.flightNumber).isEqualTo("XY501");
        assertThat(flight.direction).isEqualTo(FlightDirection.DEPARTURE);
        assertThat(flight.destination).isEqualTo("DXB");
        assertThat(flight.via).isNull();
        assertThat(flight.haul).isEqualTo(Haul.INTERNATIONAL);
        assertThat(flight.paxCount).isEqualTo(240);
        assertThat(flight.status).isEqualTo(FlightStatus.DELAYED);
        assertThat(flight.delayMinutes).isEqualTo(45);
        assertThat(flight.delayCode).isEqualTo("93");
        assertThat(flight.airline).isSameAs(airline);
        assertThat(flight.aircraftType).isSameAs(aircraft);
    }

    @Test
    void parsesAcdmMilestoneTimestamps() {
        Flight flight = translator.toFlight(xy501(), airline, aircraft);

        assertThat(flight.scheduledTime).isEqualTo(Instant.parse("2026-10-23T14:10:00Z"));
        assertThat(flight.estimatedTime).isEqualTo(Instant.parse("2026-10-23T14:55:00Z"));
        assertThat(flight.actualTime).isNull();
        assertThat(flight.tobt).isEqualTo(Instant.parse("2026-10-23T14:45:00Z"));
        assertThat(flight.tsat).isEqualTo(Instant.parse("2026-10-23T14:50:00Z"));
    }

    @Test
    void readsGateAndStandFromTheRow() {
        AodbRecord row = xy501();

        assertThat(translator.gate(row)).isEqualTo("42");
        assertThat(translator.stand(row)).isEqualTo("S42");
    }
}
