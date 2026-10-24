package com.airportmedia.platform.service.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.domain.model.input.BriefFacts;
import com.airportmedia.platform.domain.model.output.AdCopy;
import org.junit.jupiter.api.Test;

class GroundingCheckTest {

    private final BriefFacts facts = BriefParser.parse("Festive sweets, 20% off, families flying domestic");

    @Test
    void copyThatOnlyRestatesTheBriefIsGrounded() {
        AdCopy copy = new AdCopy("Festive Sweets — 20% Off", "20% off festive sweets",
                "Treat the whole family before your flight.");

        assertThat(GroundingCheck.problems(copy, "en", facts)).isEmpty();
    }

    @Test
    void aNumberTheBriefNeverMentionedIsAHallucination() {
        AdCopy copy = new AdCopy("Festive Sweets — 30% Off", "30% off festive sweets", "Valid until 18:00.");

        assertThat(GroundingCheck.problems(copy, "en", facts))
                .anyMatch(p -> p.contains("30"))
                .anyMatch(p -> p.contains("18"));
    }

    @Test
    void droppingTheBriefsDiscountIsAProblemToo() {
        AdCopy copy = new AdCopy("Festive Sweets", "Sweet treats for the trip", "Treat the whole family.");

        assertThat(GroundingCheck.problems(copy, "en", facts)).anyMatch(p -> p.contains("20%"));
    }

    @Test
    void copyMustStillRenderOnAScreen() {
        AdCopy copy = new AdCopy("x".repeat(61), "20% off", null);

        assertThat(GroundingCheck.problems(copy, "en", facts)).anyMatch(p -> p.contains("headline too long"));
        assertThat(GroundingCheck.problems(new AdCopy("20% off", "20% off", null), "de", facts))
                .anyMatch(p -> p.contains("unsupported language"));
    }

    @Test
    void anOfferClaimTheBriefNeverMadeIsAHallucinationInEitherLanguage() {
        AdCopy en = new AdCopy("Festive Sweets — 20% Off", "20% off + a free gift", "Today only, while stocks last.");
        AdCopy es = new AdCopy("Dulces — 20% de descuento", "20% y regalo gratis", "Solo hoy, a mitad de precio.");

        assertThat(GroundingCheck.problems(en, "en", facts))
                .anyMatch(p -> p.contains("free"))
                .anyMatch(p -> p.contains("today only"));
        assertThat(GroundingCheck.problems(es, "es", facts))
                .anyMatch(p -> p.contains("free"))
                .anyMatch(p -> p.contains("today only"))
                .anyMatch(p -> p.contains("half price"));
    }

    @Test
    void anOfferClaimTheBriefDidMakeIsGrounded() {
        BriefFacts freebie = BriefParser.parse("Fresh coffee, free pastry with every cup");
        AdCopy copy = new AdCopy("Fresh Coffee", "Free pastry with every cup", "Pastelería gratis con cada café.");

        assertThat(GroundingCheck.problems(copy, "en", freebie)).isEmpty();
    }
}
