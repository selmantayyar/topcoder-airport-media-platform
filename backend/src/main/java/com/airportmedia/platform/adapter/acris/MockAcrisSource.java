package com.airportmedia.platform.adapter.acris;

import com.airportmedia.platform.adapter.SampleAirport;
import com.airportmedia.platform.adapter.SampleAirport.TerminalSpec;
import com.airportmedia.platform.adapter.SampleAirport.ZoneSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates the fictional airport's ACRIS topology as NGSI-LD normalized entities,
 * matching the field names of FIWARE dataModel.ACRIS (TerminalFacility,
 * ZoneAreaLocation, CheckpointFacility, QueueMeasurement). Parent links use NGSI-LD
 * Relationship attributes so the seeder can resolve the hierarchy.
 */
@ApplicationScoped
public class MockAcrisSource implements AcrisSource {

    private static final LocalDate DEMO_DAY = LocalDate.parse("2026-10-23");

    private final SampleAirport airport;
    private final ObjectMapper mapper;

    public MockAcrisSource(SampleAirport airport, ObjectMapper mapper) {
        this.airport = airport;
        this.mapper = mapper;
    }

    @Override
    public String mode() {
        return "mock";
    }

    @Override
    public List<JsonNode> entities() {
        Random rng = new Random(7);
        List<JsonNode> out = new ArrayList<>();

        for (TerminalSpec t : airport.terminals()) {
            out.add(terminal(t));
        }
        for (ZoneSpec z : airport.zones()) {
            out.add(zone(z));
            if (z.name().contains("Checkpoint")) {
                String checkpointId = z.acrisId() + "-CP1";
                out.add(checkpoint(checkpointId, z));
                String queueId = checkpointId + "-Q1";
                out.add(passengerQueue(queueId, checkpointId));
                for (int i = 0; i < 3; i++) {
                    out.add(queueMeasurement(queueId, i, rng));
                }
            }
        }
        return out;
    }

    private JsonNode terminal(TerminalSpec t) {
        ObjectNode node = base(t.acrisId(), "TerminalFacility");
        node.set("Name", prop(t.name()));
        node.set("Identifier", prop(t.code()));
        ObjectNode facility = mapper.createObjectNode();
        facility.put("IataCode", SampleAirport.IATA);
        facility.put("IcaoCode", SampleAirport.ICAO);
        facility.put("Name", SampleAirport.NAME);
        node.set("AirportFacility", propNode(facility));
        return node;
    }

    private JsonNode zone(ZoneSpec z) {
        ObjectNode node = base(z.acrisId(), "ZoneAreaLocation");
        node.set("Name", prop(z.name()));
        ObjectNode location = mapper.createObjectNode();
        location.put("Latitude", z.lat());
        location.put("Longitude", z.lon());
        location.put("Srid", 4326);
        ObjectNode terminalArea = mapper.createObjectNode();
        terminalArea.put("Name", z.terminalCode());
        terminalArea.set("AirportLocation", location);
        node.set("TerminalAreaLocation", propNode(terminalArea));
        node.set("refTerminalFacility", rel(terminalAcrisId(z.terminalCode())));
        return node;
    }

    private JsonNode checkpoint(String id, ZoneSpec zone) {
        ObjectNode node = base(id, "CheckpointFacility");
        node.set("Name", prop(zone.name().replace("Security Checkpoint", "Security Checkpoint 1")));
        ObjectNode type = mapper.createObjectNode();
        type.put("Code", "SEC");
        type.put("Description", "Security Screening");
        node.set("CheckpointFacilityType", propNode(type));
        node.set("refZoneAreaLocation", rel(zone.acrisId()));
        return node;
    }

    private JsonNode passengerQueue(String id, String checkpointId) {
        ObjectNode node = base(id, "PassengerQueue");
        node.set("Identifier", prop(id));
        node.set("Name", prop("Economy Screening Queue"));
        ObjectNode type = mapper.createObjectNode();
        type.put("Code", "ECONOMY");
        type.put("Description", "Economy");
        node.set("QueueType", propNode(type));
        ObjectNode status = mapper.createObjectNode();
        status.put("Name", "Open");
        node.set("QueueStatus", propNode(status));
        node.set("refCheckpointFacility", rel(checkpointId));
        return node;
    }

    private JsonNode queueMeasurement(String queueId, int index, Random rng) {
        ObjectNode node = base(queueId + "-M" + index, "QueueMeasurement");
        int occupancy = 20 + rng.nextInt(90);
        int wait = 120 + rng.nextInt(700);
        node.set("Occupancy", prop(occupancy));
        node.set("WaitTime", prop(wait));
        node.set("ProjectedWaitTime", prop(wait - rng.nextInt(120)));
        node.set("Throughput", prop(250 + rng.nextInt(300)));
        ObjectNode period = mapper.createObjectNode();
        period.put("EndTime", DEMO_DAY.atTime(8 + index * 4, 0).toInstant(ZoneOffset.UTC).toString());
        node.set("MeasurementTimePeriod", propNode(period));
        node.set("refPassengerQueue", rel(queueId));
        return node;
    }

    private String terminalAcrisId(String code) {
        return "urn:ngsi-ld:TerminalFacility:" + SampleAirport.IATA + "-" + code;
    }

    private ObjectNode base(String id, String type) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", id);
        node.put("type", type);
        return node;
    }

    private ObjectNode prop(String value) {
        ObjectNode p = mapper.createObjectNode();
        p.put("type", "Property");
        p.put("value", value);
        return p;
    }

    private ObjectNode prop(int value) {
        ObjectNode p = mapper.createObjectNode();
        p.put("type", "Property");
        p.put("value", value);
        return p;
    }

    private ObjectNode propNode(JsonNode value) {
        ObjectNode p = mapper.createObjectNode();
        p.put("type", "Property");
        p.set("value", value);
        return p;
    }

    private ObjectNode rel(String object) {
        ObjectNode r = mapper.createObjectNode();
        r.put("type", "Relationship");
        r.put("object", object);
        return r;
    }
}
