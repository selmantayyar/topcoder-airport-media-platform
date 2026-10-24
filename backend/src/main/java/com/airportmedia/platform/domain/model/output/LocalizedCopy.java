package com.airportmedia.platform.domain.model.output;

/** A creative's copy in one language, with the language it is actually written in (which a fallback can differ from the one asked for). */
public record LocalizedCopy(String language, String headline, String offerText, String body) {}
