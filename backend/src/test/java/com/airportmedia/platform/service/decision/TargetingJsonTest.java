package com.airportmedia.platform.service.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.common.enums.SpendTier;
import com.airportmedia.platform.domain.model.input.CampaignDraft;
import com.airportmedia.platform.domain.model.input.CampaignTargeting;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** What the builder saves must be exactly what the pipeline reads back. */
class TargetingJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final TargetingJson writer = new TargetingJson(mapper);
    private final CampaignTargetingParser parser = new CampaignTargetingParser(mapper);

    @Test
    void writtenColumnsParseBackToTheSameTargeting() {
        CampaignDraft draft = new CampaignDraft(
                1L, "Spring sale", null, "20% off", "20% off", null, "en", null,
                Set.of("42", "43"), Set.of(5L), Set.of(9L), Set.of("DXB"), Haul.INTERNATIONAL,
                20, 30, 150, 0.2, 0.7, 0.4, SpendTier.HIGH,
                Instant.parse("2026-10-23T00:00:00Z"), Instant.parse("2026-10-30T00:00:00Z"),
                new BigDecimal("10000"), new BigDecimal("1000"), 4);

        TargetingJson.Columns cols = writer.write(draft);
        CampaignTargeting t = parser.parse(cols.placementJson(), cols.hardFiltersJson(), cols.segmentWeightsJson());

        assertThat(t).isEqualTo(new CampaignTargeting(
                Set.of("42", "43"), Set.of(5L), Set.of(9L), Haul.INTERNATIONAL, Set.of("DXB"),
                150, 20, 30, null, SpendTier.HIGH, 0.2, 0.7, 0.4));
    }

    @Test
    void emptyDraftTargetingIsUnrestricted() {
        CampaignDraft draft = new CampaignDraft(
                1L, "Anywhere", null, "Hi", null, null, null, null,
                null, null, null, null, null, 0, 0, 0, 0, 0, 0, null,
                Instant.parse("2026-10-23T00:00:00Z"), Instant.parse("2026-10-24T00:00:00Z"),
                BigDecimal.TEN, null, 3);

        TargetingJson.Columns cols = writer.write(draft);
        CampaignTargeting t = parser.parse(cols.placementJson(), cols.hardFiltersJson(), cols.segmentWeightsJson());

        assertThat(t.placementUnrestricted()).isTrue();
        assertThat(t.haul()).isNull();
        assertThat(t.destinations()).isEmpty();
    }
}
