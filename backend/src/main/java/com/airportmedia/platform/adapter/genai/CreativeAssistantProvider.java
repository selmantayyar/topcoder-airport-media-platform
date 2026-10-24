package com.airportmedia.platform.adapter.genai;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Selects the active {@link CreativeAssistant} from {@code app.creative-assistant.provider}: {@code mock}
 * (default, offline, no API key) or {@code llm}. The mock is also exposed as the safe fallback.
 */
@ApplicationScoped
public class CreativeAssistantProvider {

    private final MockCreativeAssistant mock;
    private final LlmCreativeAssistant llm;
    private final String mode;

    public CreativeAssistantProvider(
            MockCreativeAssistant mock,
            LlmCreativeAssistant llm,
            @ConfigProperty(name = "app.creative-assistant.provider", defaultValue = "mock") String mode) {
        this.mock = mock;
        this.llm = llm;
        this.mode = mode;
    }

    public CreativeAssistant active() {
        return "llm".equals(mode) ? llm : mock;
    }

    public CreativeAssistant fallback() {
        return mock;
    }
}
