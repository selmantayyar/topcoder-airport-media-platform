package com.airportmedia.platform.adapter.genai;

import com.airportmedia.platform.domain.model.input.BriefFacts;
import com.airportmedia.platform.domain.model.output.AdCopySet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;

/**
 * Creative assistant backed by a real LLM through {@link AdCopywriter}. The AI service is resolved lazily so
 * the default mock mode never builds a model client.
 */
@ApplicationScoped
public class LlmCreativeAssistant implements CreativeAssistant {

    private final Instance<AdCopywriter> copywriter;

    public LlmCreativeAssistant(Instance<AdCopywriter> copywriter) {
        this.copywriter = copywriter;
    }

    @Override
    public String name() {
        return "llm";
    }

    @Override
    public AdCopySet write(String brief, BriefFacts facts) {
        return copywriter.get().write(brief);
    }
}
