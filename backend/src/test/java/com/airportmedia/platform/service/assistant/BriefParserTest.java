package com.airportmedia.platform.service.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.domain.model.input.BriefFacts;
import org.junit.jupiter.api.Test;

class BriefParserTest {

    @Test
    void readsProductDiscountAndAudienceFromTheTicketExample() {
        BriefFacts facts = BriefParser.parse("Festive sweets, 20% off, families flying domestic");

        assertThat(facts.product()).isEqualTo("Festive sweets");
        assertThat(facts.discountPercent()).isEqualTo(20);
        assertThat(facts.families()).isTrue();
        assertThat(facts.business()).isFalse();
        assertThat(facts.haul()).isEqualTo(Haul.DOMESTIC);
        assertThat(facts.numbers()).containsExactly("20");
    }

    @Test
    void understandsPercentInWordsAndInternationalBusinessAudiences() {
        BriefFacts facts = BriefParser.parse("Premium watches; 15 percent off for business travellers going long-haul");

        assertThat(facts.product()).isEqualTo("Premium watches");
        assertThat(facts.discountPercent()).isEqualTo(15);
        assertThat(facts.business()).isTrue();
        assertThat(facts.families()).isFalse();
        assertThat(facts.haul()).isEqualTo(Haul.INTERNATIONAL);
    }

    @Test
    void aBriefWithoutAnOfferHasNoDiscountAndNoAudience() {
        BriefFacts facts = BriefParser.parse("  Fresh coffee  ");

        assertThat(facts.product()).isEqualTo("Fresh coffee");
        assertThat(facts.discountPercent()).isNull();
        assertThat(facts.haul()).isNull();
        assertThat(facts.numbers()).isEmpty();
    }

    @Test
    void everyNumberInTheBriefIsAGroundedFact() {
        BriefFacts facts = BriefParser.parse("Buy 2 get 1 free on chocolate, 10% off for kids");

        assertThat(facts.numbers()).containsExactlyInAnyOrder("2", "1", "10");
        assertThat(facts.families()).isTrue();
    }

    @Test
    void aBriefWithoutCommasStillFindsTheProductAmongTheOfferAndAudience() {
        BriefFacts facts = BriefParser.parse("20% off festive sweets for families flying domestic");

        assertThat(facts.product()).isEqualTo("festive sweets");
        assertThat(facts.discountPercent()).isEqualTo(20);
        assertThat(facts.families()).isTrue();
    }
}
