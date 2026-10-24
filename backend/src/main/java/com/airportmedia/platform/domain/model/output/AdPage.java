package com.airportmedia.platform.domain.model.output;

/** A generated ad page saved to the creative library, in the template a screen renders. */
public record AdPage(long creativeId, String language, String templateKey, String headline, String offerText, String body) {}
