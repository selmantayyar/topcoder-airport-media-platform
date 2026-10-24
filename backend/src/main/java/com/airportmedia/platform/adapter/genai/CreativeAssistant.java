package com.airportmedia.platform.adapter.genai;

import com.airportmedia.platform.domain.model.input.BriefFacts;
import com.airportmedia.platform.domain.model.output.AdCopySet;

/**
 * Writes ad copy for the creative template from a retailer's brief, in English and Spanish. The mock
 * implementation is offline and deterministic; the LLM one calls whatever chat model quarkus-langchain4j is
 * configured with. Selected by {@code app.creative-assistant.provider} ({@link CreativeAssistantProvider}).
 */
public interface CreativeAssistant {

    /** Short provider label shown with the result, e.g. "mock" or "llm". */
    String name();

    AdCopySet write(String brief, BriefFacts facts);
}
