package com.airportmedia.platform.service.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.common.enums.SpendTier;
import com.airportmedia.platform.domain.model.input.CampaignTargeting;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CampaignTargetingParserTest {

    private final CampaignTargetingParser parser = new CampaignTargetingParser(new ObjectMapper());

    @Test
    void parsesAllThreeColumns() {
        CampaignTargeting t = parser.parse(
                "{\"gates\":[\"40\",\"42\"]}",
                "{\"haul\":\"INTERNATIONAL\",\"minCrowd\":100,\"minDwellMinutes\":15,\"restricted\":\"alcohol\"}",
                "{\"business\":0.2,\"holiday\":0.7,\"family\":0.4,\"spendTarget\":\"HIGH\"}");

        assertThat(t.gates()).containsExactlyInAnyOrder("40", "42");
        assertThat(t.haul()).isEqualTo(Haul.INTERNATIONAL);
        assertThat(t.minCrowd()).isEqualTo(100);
        assertThat(t.minDwellMinutes()).isEqualTo(15);
        assertThat(t.restricted()).isEqualTo("alcohol");
        assertThat(t.spendTarget()).isEqualTo(SpendTier.HIGH);
        assertThat(t.weightHoliday()).isEqualTo(0.7);
    }

    @Test
    void toleratesNullAndBlankJson() {
        CampaignTargeting t = parser.parse(null, "", "   ");

        assertThat(t.gates()).isEmpty();
        assertThat(t.haul()).isNull();
        assertThat(t.minCrowd()).isZero();
        assertThat(t.spendTarget()).isNull();
        assertThat(t.spendTargetOrDefault()).isEqualTo(SpendTier.MEDIUM);
    }

    @Test
    void parsesZoneScreenAndDelayTargeting() {
        CampaignTargeting t = parser.parse(
                "{\"gates\":[],\"zoneIds\":[3,4],\"screenIds\":[17]}",
                "{\"minDelayMinutes\":30}",
                null);

        assertThat(t.zoneIds()).containsExactlyInAnyOrder(3L, 4L);
        assertThat(t.screenIds()).containsExactly(17L);
        assertThat(t.minDelayMinutes()).isEqualTo(30);
    }
}
