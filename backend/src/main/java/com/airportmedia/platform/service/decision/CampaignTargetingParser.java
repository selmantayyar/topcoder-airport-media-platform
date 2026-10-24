package com.airportmedia.platform.service.decision;

import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.common.enums.SpendTier;
import com.airportmedia.platform.domain.model.input.CampaignTargeting;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Parses a campaign's three JSON columns into the single {@link CampaignTargeting} the pipeline
 * reasons over. Tolerant of missing/blank fields so partially-specified campaigns still decide.
 */
@ApplicationScoped
public class CampaignTargetingParser {

    private final ObjectMapper mapper;

    public CampaignTargetingParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public CampaignTargeting parse(String placementJson, String hardFiltersJson, String segmentWeightsJson) {
        JsonNode placement = read(placementJson);
        JsonNode filters = read(hardFiltersJson);
        JsonNode weights = read(segmentWeightsJson);

        Set<String> gates = strings(placement.get("gates"));
        Set<String> destinations = strings(placement.get("destinations"));
        destinations.addAll(strings(filters.get("destinations")));

        Haul haul = text(filters.get("haul")) == null ? null : Haul.valueOf(text(filters.get("haul")));
        int minCrowd = filters.path("minCrowd").asInt(0);
        int minDwell = filters.path("minDwellMinutes").asInt(0);
        int minDelay = filters.path("minDelayMinutes").asInt(0);
        String restricted = text(filters.get("restricted"));

        SpendTier spendTarget = text(weights.get("spendTarget")) == null
                ? null : SpendTier.valueOf(text(weights.get("spendTarget")));

        return new CampaignTargeting(
                gates,
                longs(placement.get("zoneIds")),
                longs(placement.get("screenIds")),
                haul,
                destinations,
                minCrowd,
                minDwell,
                minDelay,
                restricted,
                spendTarget,
                weights.path("business").asDouble(0),
                weights.path("holiday").asDouble(0),
                weights.path("family").asDouble(0));
    }

    private JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private static Set<String> strings(JsonNode array) {
        Set<String> out = new LinkedHashSet<>();
        if (array != null && array.isArray()) {
            array.forEach(node -> out.add(node.asText()));
        }
        return out;
    }

    private static Set<Long> longs(JsonNode array) {
        Set<Long> out = new LinkedHashSet<>();
        if (array != null && array.isArray()) {
            array.forEach(node -> out.add(node.asLong()));
        }
        return out;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value.isBlank() ? null : value;
    }
}
