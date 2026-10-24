package com.airportmedia.platform.service.assistant;

import com.airportmedia.platform.domain.model.input.BriefFacts;
import com.airportmedia.platform.domain.model.output.AdCopy;
import com.airportmedia.platform.service.admin.CreativeQa;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Keeps hallucinated facts off the screens: generated copy may cite only numbers the brief itself states
 * (no invented discounts, prices, times or dates), may make an offer claim (free, half price, today only /
 * limited time) in English or Spanish only when the brief makes it, must carry the brief's discount when it
 * has one, and must pass the same {@link CreativeQa} checks every creative is held to. A heuristic guard, not
 * a proof: it catches the fact types an ad can mislead with.
 */
public final class GroundingCheck {

    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /** An offer claim: allowed in the copy only when the (English) brief states it. */
    private record Claim(String label, Pattern inBrief, Pattern inCopy) {}

    private static final List<Claim> CLAIMS = List.of(
            new Claim("free",
                    Pattern.compile("\\b(free|gratis|complimentary)\\b", FLAGS),
                    Pattern.compile("\\b(free|gratis|gratuit[oa]s?|complimentary)\\b", FLAGS)),
            new Claim("half price",
                    Pattern.compile("\\bhalf\\b|\\b50\\s*%", FLAGS),
                    Pattern.compile("half[- ]price|half off|mitad de precio|a mitad", FLAGS)),
            new Claim("today only / limited time",
                    Pattern.compile("\\b(today|tonight|limited|ends|while stocks)\\b", FLAGS),
                    Pattern.compile("today only|limited[- ]time|while stocks last|s[oó]lo hoy|por tiempo limitado"
                            + "|hasta agotar", FLAGS)));

    private GroundingCheck() {}

    /** @return human-readable problems; empty when the copy is safe to save. */
    public static List<String> problems(AdCopy copy, String language, BriefFacts facts) {
        List<String> problems = new ArrayList<>();
        List<String> texts = Stream.of(copy.headline(), copy.offerText(), copy.body())
                .filter(Objects::nonNull)
                .toList();
        Set<String> cited = new LinkedHashSet<>();
        texts.forEach(s -> {
            Matcher m = NUMBER.matcher(s);
            while (m.find()) {
                cited.add(m.group());
            }
        });
        cited.stream()
                .filter(n -> !facts.numbers().contains(n))
                .forEach(n -> problems.add("cites " + n + ", which the brief never states"));

        CLAIMS.stream()
                .filter(c -> !c.inBrief().matcher(facts.briefText()).find())
                .filter(c -> texts.stream().anyMatch(t -> c.inCopy().matcher(t).find()))
                .forEach(c -> problems.add("claims " + c.label() + ", which the brief never states"));

        if (facts.discountPercent() != null) {
            String discount = facts.discountPercent() + "%";
            boolean stated = Stream.of(copy.headline(), copy.offerText())
                    .anyMatch(s -> s != null && s.contains(discount));
            if (!stated) {
                problems.add("drops the brief's " + discount + " discount");
            }
        }
        problems.addAll(CreativeQa.problems(copy.headline(), copy.offerText(), copy.body(), language));
        return problems;
    }
}
