package com.airportmedia.platform.adapter.aodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AodbCsvCodecTest {

    @Test
    void headerHasThe33AodbColumns() {
        assertThat(AodbCsvCodec.HEADER).hasSize(33);
    }

    @Test
    void roundTripsARowThroughCsv() {
        AodbRecord row = new AodbRecord(
                "XY501", "XY", "XYZ", "DEP", "AMP", "DXB", "", "B77W", "A6-ABC", "240",
                "2026-10-23T14:10:00Z", "2026-10-23T14:55:00Z", "", "", "", "",
                "2026-10-23T14:45:00Z", "2026-10-23T14:50:00Z", "", "",
                "42", "S42", "T1", "A10-A18", "", "DELAYED", "45", "93", "",
                "260", "28", "40", "INT");

        List<AodbRecord> decoded = AodbCsvCodec.decode(AodbCsvCodec.encode(List.of(row)));

        assertThat(decoded).containsExactly(row);
    }
}
