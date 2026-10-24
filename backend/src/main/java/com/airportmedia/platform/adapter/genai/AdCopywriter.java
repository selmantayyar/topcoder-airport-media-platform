package com.airportmedia.platform.adapter.genai;

import com.airportmedia.platform.domain.model.output.AdCopySet;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * The quarkus-langchain4j AI service behind {@link LlmCreativeAssistant}. Which model answers is pure
 * configuration ({@code quarkus.langchain4j.openai.*}: any OpenAI-compatible endpoint, e.g. OpenAI itself or
 * a local Ollama), so swapping providers needs no code change.
 */
@RegisterAiService
public interface AdCopywriter {

    @SystemMessage("""
            You write copy for digital advertising screens in an airport.
            Write one ad in English (en) and the same ad in Spanish (es).
            Use only facts stated in the brief: never invent prices, discounts, percentages, times, dates,
            quantities or brand names. If the brief states a discount, put it in the headline or offer text
            exactly as given (e.g. "20%").
            headline: at most 60 characters. offerText: at most 80 characters. body: at most 280 characters,
            ending with an invitation to scan the QR code to claim the offer in store.
            """)
    @UserMessage("Brief: {brief}")
    AdCopySet write(String brief);
}
