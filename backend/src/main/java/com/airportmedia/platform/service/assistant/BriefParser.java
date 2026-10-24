package com.airportmedia.platform.service.assistant;

import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.domain.model.input.BriefFacts;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the facts out of a free-text creative brief. The brief is split into comma/semicolon segments: the
 * first segment without a number is the product (failing that, the first segment minus its discount and its
 * "for …" / "flying …" audience tail); a "N%" / "N percent" is the discount; audience and flight
 * type come from keywords anywhere in it. Every number in the brief is kept, because those are the only
 * numbers generated copy may cite (see {@link GroundingCheck}).
 */
public final class BriefParser {

    private static final Pattern DISCOUNT = Pattern.compile("(\\d{1,2})\\s*(?:%|percent|per cent)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern DISCOUNT_PHRASE = Pattern.compile(
            "\\d{1,2}\\s*(?:%|percent|per cent)(?:\\s+(?:off|discount)(?:\\s+(?:on|of))?)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUDIENCE_TAIL = Pattern.compile(
            "\\s+(?:for|flying|going|to)\\s+.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FAMILIES = Pattern.compile("\\b(famil\\w*|kids?|children|child)\\b");
    private static final Pattern BUSINESS = Pattern.compile("\\bbusiness\\b");
    private static final Pattern DOMESTIC = Pattern.compile("\\bdomestic\\b");
    private static final Pattern INTERNATIONAL = Pattern.compile("\\b(international|long[- ]haul|abroad|overseas)\\b");

    private BriefParser() {}

    public static BriefFacts parse(String brief) {
        String text = brief == null ? "" : brief.strip();
        String lower = text.toLowerCase(Locale.ROOT);

        Matcher discount = DISCOUNT.matcher(text);
        Integer discountPercent = discount.find() ? Integer.valueOf(discount.group(1)) : null;

        List<String> numbers = new ArrayList<>();
        Matcher number = NUMBER.matcher(text);
        while (number.find()) {
            numbers.add(number.group());
        }

        Haul haul = DOMESTIC.matcher(lower).find() ? Haul.DOMESTIC
                : INTERNATIONAL.matcher(lower).find() ? Haul.INTERNATIONAL
                : null;

        return new BriefFacts(product(text), discountPercent,
                FAMILIES.matcher(lower).find(), BUSINESS.matcher(lower).find(), haul, List.copyOf(numbers), text);
    }

    private static String product(String text) {
        List<String> segments = Arrays.stream(text.split("[,;]"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
        return segments.stream()
                .filter(s -> !NUMBER.matcher(s).find())
                .findFirst()
                .orElseGet(() -> segments.isEmpty() ? "" : AUDIENCE_TAIL.matcher(
                        DISCOUNT_PHRASE.matcher(segments.get(0)).replaceAll("").strip()).replaceAll("").strip());
    }
}
