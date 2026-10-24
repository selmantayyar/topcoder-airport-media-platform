package com.airportmedia.platform.adapter.genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.domain.model.input.BriefFacts;
import com.airportmedia.platform.domain.model.output.AdCopySet;
import com.airportmedia.platform.service.assistant.BriefParser;
import com.airportmedia.platform.service.assistant.GroundingCheck;
import org.junit.jupiter.api.Test;

class MockCreativeAssistantTest {

    private final MockCreativeAssistant assistant = new MockCreativeAssistant();

    @Test
    void writesAGroundedEnglishAndSpanishPageFromTheTicketBrief() {
        String brief = "Festive sweets, 20% off, families flying domestic";
        BriefFacts facts = BriefParser.parse(brief);

        AdCopySet copy = assistant.write(brief, facts);

        assertThat(copy.en().headline()).contains("Festive Sweets").contains("20%");
        assertThat(copy.en().offerText()).isEqualTo("20% off festive sweets");
        assertThat(copy.en().body()).contains("family").contains("domestic");
        assertThat(copy.es().headline()).contains("Dulces festivos").contains("20%");
        assertThat(copy.es().offerText()).isEqualTo("20% de descuento en dulces festivos");
        assertThat(copy.es().body()).contains("familia").contains("nacional");
        assertThat(GroundingCheck.problems(copy.en(), "en", facts)).isEmpty();
        assertThat(GroundingCheck.problems(copy.es(), "es", facts)).isEmpty();
    }

    @Test
    void isDeterministicSoTheDemoNeverChanges() {
        String brief = "Fresh coffee";
        BriefFacts facts = BriefParser.parse(brief);

        assertThat(assistant.write(brief, facts)).isEqualTo(assistant.write(brief, facts));
        assertThat(assistant.write(brief, facts).en().offerText()).doesNotContainPattern("\\d");
    }

    @Test
    void anOverlongProductStillFitsTheScreen() {
        String brief = "Handmade artisanal chocolate truffles from the old town confectionery shop, 10% off";
        BriefFacts facts = BriefParser.parse(brief);

        AdCopySet copy = assistant.write(brief, facts);

        assertThat(GroundingCheck.problems(copy.en(), "en", facts)).isEmpty();
        assertThat(GroundingCheck.problems(copy.es(), "es", facts)).isEmpty();
    }
}
