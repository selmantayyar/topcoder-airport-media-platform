package com.airportmedia.platform.adapter.acris;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.ZoneKind;
import com.airportmedia.platform.domain.entity.Checkpoint;
import com.airportmedia.platform.domain.entity.Terminal;
import com.airportmedia.platform.domain.entity.Zone;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AcrisTranslatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AcrisTranslator translator = new AcrisTranslator();

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }

    @Test
    void mapsTerminalFacility() throws Exception {
        JsonNode node = json("""
            {
              "id": "urn:ngsi-ld:TerminalFacility:AMP-T1",
              "type": "TerminalFacility",
              "Name": {"type":"Property","value":"Terminal 1"},
              "Identifier": {"type":"Property","value":"T1"}
            }
            """);

        Terminal terminal = translator.toTerminal(node);

        assertThat(terminal.acrisId).isEqualTo("urn:ngsi-ld:TerminalFacility:AMP-T1");
        assertThat(terminal.code).isEqualTo("T1");
        assertThat(terminal.name).isEqualTo("Terminal 1");
    }

    @Test
    void mapsZoneAreaLocationWithCoordinatesAndInferredKind() throws Exception {
        JsonNode node = json("""
            {
              "id": "urn:ngsi-ld:ZoneAreaLocation:AMP-T1-A",
              "type": "ZoneAreaLocation",
              "Name": {"type":"Property","value":"Gate Lounge A"},
              "TerminalAreaLocation": {"type":"Property","value":{
                "Name":"T1",
                "AirportLocation":{"Latitude":25.11,"Longitude":55.22,"Srid":4326}}}
            }
            """);
        Terminal terminal = new Terminal();

        Zone zone = translator.toZone(node, terminal);

        assertThat(zone.acrisId).isEqualTo("urn:ngsi-ld:ZoneAreaLocation:AMP-T1-A");
        assertThat(zone.name).isEqualTo("Gate Lounge A");
        assertThat(zone.kind).isEqualTo(ZoneKind.GATE_AREA);
        assertThat(zone.latitude).isEqualTo(25.11);
        assertThat(zone.longitude).isEqualTo(55.22);
        assertThat(zone.terminal).isSameAs(terminal);
    }

    @Test
    void infersCheckpointKindFromZoneName() throws Exception {
        JsonNode node = json("""
            {
              "id": "urn:ngsi-ld:ZoneAreaLocation:AMP-T1-SEC",
              "type": "ZoneAreaLocation",
              "Name": {"type":"Property","value":"Central Checkpoint"},
              "TerminalAreaLocation": {"type":"Property","value":{
                "AirportLocation":{"Latitude":25.1,"Longitude":55.2}}}
            }
            """);

        Zone zone = translator.toZone(node, new Terminal());

        assertThat(zone.kind).isEqualTo(ZoneKind.CHECKPOINT);
    }

    @Test
    void mapsCheckpointFacility() throws Exception {
        JsonNode node = json("""
            {
              "id": "urn:ngsi-ld:CheckpointFacility:AMP-T1-SEC1",
              "type": "CheckpointFacility",
              "Name": {"type":"Property","value":"Security Checkpoint 1"},
              "CheckpointFacilityType": {"type":"Property","value":{
                "Code":"SEC","Description":"Security Screening"}}
            }
            """);
        Zone zone = new Zone();

        Checkpoint checkpoint = translator.toCheckpoint(node, zone);

        assertThat(checkpoint.acrisId).isEqualTo("urn:ngsi-ld:CheckpointFacility:AMP-T1-SEC1");
        assertThat(checkpoint.name).isEqualTo("Security Checkpoint 1");
        assertThat(checkpoint.type).isEqualTo("Security Screening");
        assertThat(checkpoint.zone).isSameAs(zone);
    }

    @Test
    void mapsQueueMeasurement() throws Exception {
        JsonNode node = json("""
            {
              "id": "urn:ngsi-ld:QueueMeasurement:AMP-T1-SEC1-001",
              "type": "QueueMeasurement",
              "Occupancy": {"type":"Property","value":58},
              "WaitTime": {"type":"Property","value":646},
              "ProjectedWaitTime": {"type":"Property","value":544},
              "Throughput": {"type":"Property","value":384},
              "MeasurementTimePeriod": {"type":"Property","value":{"EndTime":"2026-10-23T18:59:02Z"}}
            }
            """);
        Checkpoint checkpoint = new Checkpoint();

        var measurement = translator.toQueueMeasurement(node, checkpoint);

        assertThat(measurement.occupancy).isEqualTo(58);
        assertThat(measurement.waitTimeSeconds).isEqualTo(646);
        assertThat(measurement.projectedWaitTimeSeconds).isEqualTo(544);
        assertThat(measurement.throughput).isEqualTo(384);
        assertThat(measurement.measuredAt).isEqualTo(Instant.parse("2026-10-23T18:59:02Z"));
        assertThat(measurement.checkpoint).isSameAs(checkpoint);
    }
}
