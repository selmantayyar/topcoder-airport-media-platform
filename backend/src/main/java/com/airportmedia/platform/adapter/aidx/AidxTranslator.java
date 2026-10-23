package com.airportmedia.platform.adapter.aidx;

import com.airportmedia.platform.common.enums.FlightEventType;
import com.airportmedia.platform.common.enums.FlightStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;

/**
 * Translates an AIDX {@code IATA_AIDX_FlightLegNotifRQ} XML message into a canonical
 * {@link AidxFlightLegUpdate}. The event type is inferred from which parts of the leg
 * data are populated: a delay wins over a gate change, which wins over a bare status.
 */
@ApplicationScoped
public class AidxTranslator {

    private final XmlMapper xmlMapper = new XmlMapper();

    public AidxFlightLegUpdate parse(String xml) {
        JsonNode root = readTree(xml);
        JsonNode leg = root.path("FlightLeg");
        JsonNode id = leg.path("LegIdentifier");
        JsonNode data = leg.path("LegData");

        Integer delayMinutes = delayMinutes(data);
        String newGate = gate(data);
        FlightStatus newStatus = mapStatus(text(data.path("OperationalStatus")));

        return new AidxFlightLegUpdate(
                text(id.path("Airline")),
                text(id.path("FlightNumber")),
                text(id.path("OriginDate")),
                classify(delayMinutes, newGate, newStatus),
                Instant.parse(root.path("TimeStamp").asText()),
                nullIfEmpty(root.path("TransactionIdentifier").asText(null)),
                delayMinutes,
                newGate,
                newStatus);
    }

    private FlightEventType classify(Integer delayMinutes, String newGate, FlightStatus newStatus) {
        if (delayMinutes != null) {
            return FlightEventType.DELAY;
        }
        if (newGate != null) {
            return FlightEventType.GATE_CHANGE;
        }
        if (newStatus == FlightStatus.BOARDING) {
            return FlightEventType.BOARDING_STARTED;
        }
        return FlightEventType.STATUS_CHANGE;
    }

    private Integer delayMinutes(JsonNode data) {
        JsonNode delay = data.path("IrregularityDelay");
        if (delay.isMissingNode() || delay.isNull()) {
            return null;
        }
        String duration = text(delay.path("Duration"));
        return duration == null ? null : (int) Duration.parse(duration).toMinutes();
    }

    private String gate(JsonNode data) {
        JsonNode gate = data.path("AirportResources").path("Resource").path("PassengerGate");
        return nullIfEmpty(text(gate));
    }

    private static FlightStatus mapStatus(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "DL" -> FlightStatus.DELAYED;
            case "BD" -> FlightStatus.BOARDING;
            case "DP" -> FlightStatus.DEPARTED;
            case "LD" -> FlightStatus.LANDED;
            case "CX" -> FlightStatus.CANCELLED;
            default -> null;
        };
    }

    private JsonNode readTree(String xml) {
        try {
            return xmlMapper.readTree(xml.getBytes());
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed AIDX message", e);
        }
    }

    /** XmlMapper stores an element's text content under the empty-string key when it also has attributes. */
    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        JsonNode textChild = node.get("");
        return textChild == null ? null : textChild.asText();
    }

    private static String nullIfEmpty(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
