package com.airportmedia.platform.service.admin;

import com.airportmedia.platform.domain.model.validation.ScreenCopyLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pre-flight checks that a creative will render on an airport screen. Anything listed here means the ad
 * would play broken, so a running campaign with a failing creative raises a CREATIVE_FAILED incident.
 */
public final class CreativeQa {

    static final int MAX_HEADLINE = ScreenCopyLimits.MAX_HEADLINE;
    static final int MAX_OFFER = ScreenCopyLimits.MAX_OFFER;
    static final int MAX_BODY = ScreenCopyLimits.MAX_BODY;
    static final Set<String> RENDERABLE_LANGUAGES = Set.of("en", "ar", "es");

    private CreativeQa() {}

    /** @return human-readable problems; empty when the creative is good to play. */
    public static List<String> problems(String headline, String offerText, String body, String language) {
        List<String> problems = new ArrayList<>();
        if (headline == null || headline.isBlank()) {
            problems.add("missing headline");
        } else if (headline.length() > MAX_HEADLINE) {
            problems.add("headline too long for screen (" + headline.length() + " > " + MAX_HEADLINE + " chars)");
        }
        if (offerText != null && offerText.length() > MAX_OFFER) {
            problems.add("offer text too long for screen (" + offerText.length() + " > " + MAX_OFFER + " chars)");
        }
        if (body != null && body.length() > MAX_BODY) {
            problems.add("body too long for screen (" + body.length() + " > " + MAX_BODY + " chars)");
        }
        if (language == null || !RENDERABLE_LANGUAGES.contains(language)) {
            problems.add("unsupported language '" + language + "' (screens render " + RENDERABLE_LANGUAGES + ")");
        }
        return problems;
    }
}
