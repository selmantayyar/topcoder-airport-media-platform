package com.airportmedia.platform.adapter.acris;

import com.airportmedia.platform.common.enums.ZoneKind;
import com.airportmedia.platform.domain.entity.Checkpoint;
import com.airportmedia.platform.domain.entity.QueueMeasurement;
import com.airportmedia.platform.domain.entity.Terminal;
import com.airportmedia.platform.domain.entity.Zone;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;

/**
 * Translates ACRIS NGSI-LD entities (FIWARE dataModel.ACRIS) into canonical topology.
 * Parent references (terminal, zone, checkpoint) are resolved by the caller, which
 * walks the hierarchy, so each method is a pure mapping of one NGSI-LD entity.
 */
@ApplicationScoped
public class AcrisTranslator {

    public Terminal toTerminal(JsonNode node) {
        Terminal terminal = new Terminal();
        terminal.acrisId = node.get("id").asText();
        terminal.code = value(node, "Identifier").asText();
        terminal.name = value(node, "Name").asText();
        return terminal;
    }

    public Zone toZone(JsonNode node, Terminal terminal) {
        Zone zone = new Zone();
        zone.acrisId = node.get("id").asText();
        zone.name = value(node, "Name").asText();
        zone.kind = inferZoneKind(zone.name);
        zone.terminal = terminal;
        JsonNode location = value(node, "TerminalAreaLocation").get("AirportLocation");
        zone.latitude = location.path("Latitude").asDouble();
        zone.longitude = location.path("Longitude").asDouble();
        return zone;
    }

    public Checkpoint toCheckpoint(JsonNode node, Zone zone) {
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.acrisId = node.get("id").asText();
        checkpoint.name = value(node, "Name").asText();
        checkpoint.type = value(node, "CheckpointFacilityType").get("Description").asText();
        checkpoint.zone = zone;
        return checkpoint;
    }

    public QueueMeasurement toQueueMeasurement(JsonNode node, Checkpoint checkpoint) {
        QueueMeasurement measurement = new QueueMeasurement();
        measurement.checkpoint = checkpoint;
        measurement.occupancy = value(node, "Occupancy").asInt();
        measurement.waitTimeSeconds = value(node, "WaitTime").asInt();
        measurement.projectedWaitTimeSeconds = value(node, "ProjectedWaitTime").asInt();
        measurement.throughput = value(node, "Throughput").asInt();
        measurement.measuredAt = Instant.parse(value(node, "MeasurementTimePeriod").get("EndTime").asText());
        return measurement;
    }

    /** Reads the {@code value} of an NGSI-LD Property attribute. */
    private static JsonNode value(JsonNode node, String attribute) {
        return node.get(attribute).get("value");
    }

    private static ZoneKind inferZoneKind(String name) {
        String n = name.toLowerCase();
        if (n.contains("concourse")) {
            return ZoneKind.CONCOURSE;
        }
        if (n.contains("checkpoint") || n.contains("security")) {
            return ZoneKind.CHECKPOINT;
        }
        if (n.contains("check-in") || n.contains("checkin")) {
            return ZoneKind.CHECKIN;
        }
        if (n.contains("arrival")) {
            return ZoneKind.ARRIVALS;
        }
        if (n.contains("retail") || n.contains("plaza") || n.contains("duty")) {
            return ZoneKind.RETAIL;
        }
        return ZoneKind.GATE_AREA;
    }
}
