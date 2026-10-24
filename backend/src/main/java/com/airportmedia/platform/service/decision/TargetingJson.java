package com.airportmedia.platform.service.decision;

import com.airportmedia.platform.domain.model.input.CampaignDraft;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Set;

/**
 * Writes a {@link CampaignDraft}'s targeting into the campaign's three JSON columns — the exact inverse
 * of {@link CampaignTargetingParser}, so what the builder previews and saves is what the pipeline reads.
 */
@ApplicationScoped
public class TargetingJson {

    private final ObjectMapper mapper;

    public TargetingJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record Columns(String placementJson, String hardFiltersJson, String segmentWeightsJson) {}

    public Columns write(CampaignDraft draft) {
        ObjectNode placement = mapper.createObjectNode();
        placement.set("gates", mapper.valueToTree(sorted(draft.gates())));
        placement.set("zoneIds", mapper.valueToTree(sorted(draft.zoneIds())));
        placement.set("screenIds", mapper.valueToTree(sorted(draft.screenIds())));

        ObjectNode filters = mapper.createObjectNode();
        if (draft.haul() != null) {
            filters.put("haul", draft.haul().name());
        }
        filters.set("destinations", mapper.valueToTree(sorted(draft.destinations())));
        filters.put("minCrowd", draft.minCrowd());
        filters.put("minDwellMinutes", draft.minDwellMinutes());
        filters.put("minDelayMinutes", draft.minDelayMinutes());

        ObjectNode weights = mapper.createObjectNode();
        weights.put("business", draft.weightBusiness());
        weights.put("holiday", draft.weightHoliday());
        weights.put("family", draft.weightFamily());
        if (draft.spendTarget() != null) {
            weights.put("spendTarget", draft.spendTarget().name());
        }

        return new Columns(placement.toString(), filters.toString(), weights.toString());
    }

    /** Null-safe, deterministic ordering so identical drafts serialise identically. */
    private static <T extends Comparable<T>> List<T> sorted(Set<T> values) {
        return values == null ? List.of() : values.stream().sorted().toList();
    }
}
