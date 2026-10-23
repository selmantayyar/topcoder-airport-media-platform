package com.airportmedia.platform.adapter.aodb;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.adapter.SampleAirport;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockFlightSourceTest {

    private final MockFlightSource source = new MockFlightSource(new SampleAirport());

    @Test
    void producesTheRequestedNumberOfFlights() {
        assertThat(AodbCsvCodec.decode(source.aodbSnapshotCsv(50))).hasSize(50);
    }

    @Test
    void firstRowIsTheReproducibleWorkedExample() {
        AodbRecord first = AodbCsvCodec.decode(source.aodbSnapshotCsv(10)).get(0);

        assertThat(first.flightNumber()).isEqualTo("XY501");
        assertThat(first.destinationIata()).isEqualTo("DXB");
        assertThat(first.paxCount()).isEqualTo("240");
        assertThat(first.delayMinutes()).isEqualTo("45");
        assertThat(first.gate()).isEqualTo("42");
    }

    @Test
    void isDeterministicAcrossRuns() {
        assertThat(source.aodbSnapshotCsv(100)).isEqualTo(source.aodbSnapshotCsv(100));
    }

    @Test
    void everyFlightIsPlausible() {
        List<AodbRecord> rows = AodbCsvCodec.decode(source.aodbSnapshotCsv(200));

        assertThat(rows).allSatisfy(row -> {
            int seats = Integer.parseInt(row.seatsEconomy())
                    + Integer.parseInt(row.seatsPremium())
                    + Integer.parseInt(row.seatsBusiness());
            assertThat(Integer.parseInt(row.paxCount()))
                    .as("pax never exceeds seats for %s", row.flightNumber())
                    .isLessThanOrEqualTo(seats);
            assertThat(row.haul()).isIn("DOM", "INT");
            assertThat(row.direction()).isIn("DEP", "ARR");
        });
    }
}
