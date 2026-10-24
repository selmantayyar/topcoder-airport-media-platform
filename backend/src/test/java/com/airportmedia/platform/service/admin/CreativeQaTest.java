package com.airportmedia.platform.service.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.domain.entity.Creative;
import org.junit.jupiter.api.Test;

class CreativeQaTest {

    @Test
    void aWellFormedCreativePasses() {
        assertThat(CreativeQa.problems("15% off perfume", "15% off perfume", "Limited time.", "en")).isEmpty();
    }

    @Test
    void flagsAMissingHeadline() {
        assertThat(CreativeQa.problems("  ", "offer", null, "en")).anyMatch(p -> p.contains("headline"));
    }

    @Test
    void flagsCopyTooLongToRenderOnAScreen() {
        assertThat(CreativeQa.problems("x".repeat(61), null, null, "en")).anyMatch(p -> p.contains("too long"));
        assertThat(CreativeQa.problems("ok", "y".repeat(81), null, "en")).anyMatch(p -> p.contains("offer"));
        assertThat(CreativeQa.problems("ok", null, "z".repeat(281), "en")).anyMatch(p -> p.contains("body"));
    }

    @Test
    void flagsALanguageTheScreensCannotRender() {
        assertThat(CreativeQa.problems("ok", null, null, "xx")).anyMatch(p -> p.contains("language"));
    }

    @Test
    void checksEveryLanguageTheCreativeRendersIn() {
        Creative c = new Creative();
        c.language = "en";
        c.headline = "15% off perfume";
        c.translate("es", "x".repeat(61), null, null);

        assertThat(CreativeQa.problems(c)).singleElement().satisfies(p -> assertThat(p).startsWith("es: headline too long"));
    }

    @Test
    void aFullyTranslatedWellFormedCreativePasses() {
        Creative c = new Creative();
        c.language = "en";
        c.headline = "15% off perfume";
        c.translate("es", "15% de descuento en perfumes", null, null);

        assertThat(CreativeQa.problems(c)).isEmpty();
    }
}
