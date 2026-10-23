package com.airportmedia.platform.adapter.acris;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.adapter.SampleAirport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MockAcrisSourceTest {

    private final MockAcrisSource source = new MockAcrisSource(new SampleAirport(), new ObjectMapper());

    private Map<String, Long> countsByType() {
        return source.entities().stream()
                .collect(Collectors.groupingBy(n -> n.get("type").asText(), Collectors.counting()));
    }

    @Test
    void emitsAllFiveFiwareAcrisEntityTypes() {
        Map<String, Long> counts = countsByType();

        assertThat(counts).containsKeys(
                "TerminalFacility", "ZoneAreaLocation", "CheckpointFacility",
                "PassengerQueue", "QueueMeasurement");
        assertThat(counts.get("TerminalFacility")).isEqualTo(2);
    }

    @Test
    void measurementsReferenceAPassengerQueueThatReferencesACheckpoint() {
        Map<String, JsonNode> byId = source.entities().stream()
                .collect(Collectors.toMap(n -> n.get("id").asText(), Function.identity()));
        List<JsonNode> measurements = source.entities().stream()
                .filter(n -> n.get("type").asText().equals("QueueMeasurement"))
                .toList();

        assertThat(measurements).isNotEmpty();
        assertThat(measurements).allSatisfy(m -> {
            String queueId = m.get("refPassengerQueue").get("object").asText();
            JsonNode queue = byId.get(queueId);
            assertThat(queue.get("type").asText()).isEqualTo("PassengerQueue");
            String checkpointId = queue.get("refCheckpointFacility").get("object").asText();
            assertThat(byId.get(checkpointId).get("type").asText()).isEqualTo("CheckpointFacility");
        });
    }
}
