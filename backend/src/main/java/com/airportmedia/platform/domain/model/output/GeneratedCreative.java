package com.airportmedia.platform.domain.model.output;

import java.util.List;

/**
 * The creative assistant's result: one ready ad page per language, which provider wrote the copy that was
 * kept, and notes on anything rejected on the way (e.g. LLM copy that cited a fact not in the brief).
 */
public record GeneratedCreative(String brief, String provider, List<AdPage> pages, List<String> notes) {}
