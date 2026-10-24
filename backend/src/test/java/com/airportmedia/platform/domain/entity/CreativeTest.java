package com.airportmedia.platform.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.domain.model.output.LocalizedCopy;
import org.junit.jupiter.api.Test;

/** A creative renders in the requested language when it has that copy, and never invents a translation. */
class CreativeTest {

    private static Creative perfume() {
        Creative c = new Creative();
        c.language = "en";
        c.headline = "15% off perfume";
        c.offerText = "15% off perfume";
        c.body = "Limited-time offer for travellers.";
        return c;
    }

    @Test
    void thePrimaryLanguageRendersThePrimaryCopy() {
        assertThat(perfume().copyIn("en"))
                .isEqualTo(new LocalizedCopy("en", "15% off perfume", "15% off perfume",
                        "Limited-time offer for travellers."));
    }

    @Test
    void aTranslatedLanguageRendersItsTranslation() {
        Creative c = perfume();
        c.translate("es", "15% de descuento en perfumes", "15% de descuento en perfumes",
                "Oferta por tiempo limitado para viajeros.");

        assertThat(c.copyIn("es"))
                .isEqualTo(new LocalizedCopy("es", "15% de descuento en perfumes", "15% de descuento en perfumes",
                        "Oferta por tiempo limitado para viajeros."));
    }

    @Test
    void anUntranslatedLanguageFallsBackToThePrimaryCopyAndSaysSo() {
        LocalizedCopy copy = perfume().copyIn("es");

        assertThat(copy.language()).isEqualTo("en");
        assertThat(copy.headline()).isEqualTo("15% off perfume");
    }

    @Test
    void noRequestedLanguageRendersThePrimaryCopy() {
        assertThat(perfume().copyIn(null).language()).isEqualTo("en");
        assertThat(perfume().copyIn(" ").language()).isEqualTo("en");
    }

    @Test
    void languagesAreMatchedCaseInsensitively() {
        Creative c = perfume();
        c.translate("es", "Hola", null, null);

        assertThat(c.copyIn("ES").headline()).isEqualTo("Hola");
        assertThat(c.languages()).containsExactly("en", "es");
    }
}
