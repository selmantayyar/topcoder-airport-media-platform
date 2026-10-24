package com.airportmedia.platform.adapter.genai;

import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.domain.model.input.BriefFacts;
import com.airportmedia.platform.domain.model.output.AdCopy;
import com.airportmedia.platform.domain.model.output.AdCopySet;
import com.airportmedia.platform.domain.model.validation.ScreenCopyLimits;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The default, offline creative assistant: fills fixed English/Spanish copy patterns from the brief's facts
 * (product, discount, audience, flight type). Spanish product names go through a small glossary — whole
 * phrases first, then word by word, unknown words kept as written. Needs no API key and never cites a fact
 * the brief didn't state, so the demo always produces a page the screens can play.
 */
@ApplicationScoped
public class MockCreativeAssistant implements CreativeAssistant {

    private static final String QR_EN = " Scan the QR code to claim the offer in store.";
    private static final String QR_ES = " Escanea el código QR para canjear la oferta en tienda.";

    private static final Map<String, String> PHRASES_ES = Map.of(
            "festive sweets", "dulces festivos",
            "fresh coffee", "café recién hecho",
            "premium watches", "relojes de lujo",
            "travel essentials", "esenciales de viaje",
            "duty-free perfume", "perfume libre de impuestos",
            "local souvenirs", "recuerdos locales");

    private static final Map<String, String> WORDS_ES = Map.ofEntries(
            Map.entry("sweets", "dulces"), Map.entry("coffee", "café"), Map.entry("tea", "té"),
            Map.entry("chocolate", "chocolate"), Map.entry("chocolates", "bombones"), Map.entry("truffles", "trufas"),
            Map.entry("watches", "relojes"), Map.entry("perfume", "perfume"), Map.entry("perfumes", "perfumes"),
            Map.entry("snacks", "aperitivos"), Map.entry("souvenirs", "recuerdos"), Map.entry("books", "libros"),
            Map.entry("toys", "juguetes"), Map.entry("wine", "vino"), Map.entry("cosmetics", "cosméticos"),
            Map.entry("sunglasses", "gafas de sol"), Map.entry("handmade", "artesanales"),
            Map.entry("fresh", "frescos"), Map.entry("premium", "de lujo"));

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public AdCopySet write(String brief, BriefFacts facts) {
        return new AdCopySet(english(facts), spanish(facts));
    }

    private static AdCopy english(BriefFacts facts) {
        String product = facts.product();
        Integer discount = facts.discountPercent();

        String headline = discount != null
                ? withSuffix(titleCase(product), " — " + discount + "% Off", ScreenCopyLimits.MAX_HEADLINE)
                : fit(titleCase(product), ScreenCopyLimits.MAX_HEADLINE);
        String offer = discount != null
                ? withPrefix(discount + "% off ", lower(product), ScreenCopyLimits.MAX_OFFER)
                : withPrefix("Treat yourself to ", lower(product), ScreenCopyLimits.MAX_OFFER);

        String flight = facts.haul() == Haul.DOMESTIC ? "domestic flight"
                : facts.haul() == Haul.INTERNATIONAL ? "international flight"
                : "flight";
        String pitch = facts.families() ? "Treat the whole family before your " + flight + "."
                : facts.business() ? "A quick treat between meetings, right by your gate."
                : "Grab yours before your " + flight + ".";
        return new AdCopy(headline, offer, pitch + QR_EN);
    }

    private static AdCopy spanish(BriefFacts facts) {
        String product = translate(facts.product());
        Integer discount = facts.discountPercent();

        String headline = discount != null
                ? withSuffix(capitalise(product), " — " + discount + "% de descuento", ScreenCopyLimits.MAX_HEADLINE)
                : fit(capitalise(product), ScreenCopyLimits.MAX_HEADLINE);
        String offer = discount != null
                ? withPrefix(discount + "% de descuento en ", lower(product), ScreenCopyLimits.MAX_OFFER)
                : withPrefix("Date un gusto: ", lower(product), ScreenCopyLimits.MAX_OFFER);

        String flight = facts.haul() == Haul.DOMESTIC ? "vuelo nacional"
                : facts.haul() == Haul.INTERNATIONAL ? "vuelo internacional"
                : "vuelo";
        String pitch = facts.families() ? "Consiente a toda la familia antes de tu " + flight + "."
                : facts.business() ? "Un capricho rápido entre reuniones, junto a tu puerta de embarque."
                : "Consigue el tuyo antes de tu " + flight + ".";
        return new AdCopy(headline, offer, pitch + QR_ES);
    }

    static String translate(String product) {
        String key = lower(product);
        if (PHRASES_ES.containsKey(key)) {
            return PHRASES_ES.get(key);
        }
        return Arrays.stream(key.split("\\s+"))
                .map(word -> WORDS_ES.getOrDefault(word, word))
                .collect(Collectors.joining(" "));
    }

    private static String withSuffix(String text, String suffix, int max) {
        return fit(text, max - suffix.length()) + suffix;
    }

    private static String withPrefix(String prefix, String text, int max) {
        return prefix + fit(text, max - prefix.length());
    }

    /** Shortens at a word boundary with an ellipsis so the copy fits its template slot. */
    private static String fit(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        int cut = text.lastIndexOf(' ', max - 1);
        return text.substring(0, cut > 0 ? cut : max - 1).strip() + "…";
    }

    private static String titleCase(String text) {
        return Arrays.stream(text.split("\\s+"))
                .map(MockCreativeAssistant::capitalise)
                .collect(Collectors.joining(" "));
    }

    private static String capitalise(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    private static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
