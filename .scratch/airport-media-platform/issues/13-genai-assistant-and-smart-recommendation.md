# 13 — GenAI creative assistant + Smart recommendation (Required WOW)

**What to build:** Inside the creative library, a brief-in/ad-page-out assistant (mocked by default, pluggable to a real LLM provider via config, no API key required for the demo) that produces a ready ad page in English and Spanish, plus a "best 3 slots for this campaign tomorrow" recommendation.

**Blocked by:** 09 — Retailer campaign builder + creative library + promo codes + history, 06 — Decision pipeline + Gate 42 worked example

**Status:** done

- [x] Typing a brief (e.g. "Festive sweets, 20% off, families flying domestic") produces a ready ad page in the existing creative template, in English and Spanish
- [x] The default mock provider requires no API key and works fully offline
- [x] A real provider can be swapped in via a single config value (using the `quarkus-langchain4j` pluggable-provider pattern) without code changes
- [x] "Best 3 slots for this campaign tomorrow" surfaces three concrete slot recommendations with predicted impressions, computed from ticket 06's scoring logic

Creative assistant (History & library tab): `POST /api/retailers/{id}/creatives/assistant` parses the brief's facts
(`BriefParser`), has the active `adapter/genai` `CreativeAssistant` write EN + ES copy, and saves both pages to the
library in the `promo-basic` template. `app.creative-assistant.provider=mock|llm` (env
`APP_CREATIVE_ASSISTANT_PROVIDER`) switches between the offline mock and a `@RegisterAiService` backed by
quarkus-langchain4j's OpenAI-compatible client (`OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_MODEL` — a local Ollama
works). `GroundingCheck` rejects copy citing any number the brief doesn't state, making an offer claim it doesn't make
(free / half price / today only, EN or ES), dropping the brief's discount, or failing `CreativeQa`; rejected or failed LLM output falls back to the mock's copy with a note. Verified end to end
against Ollama (qwen3:1.7b): one result accepted, one rejected for an 81-char Spanish offer line.

Smart recommendation: `GET /api/campaigns/{id}/recommended-slots` projects tomorrow's departures from today's
schedule (`TomorrowProjection`, no delays carried over), replays the day through the real pipeline every 15 min on
every online screen (`DecisionService.simulateDay`, campaign evaluated as if approved), grants each win
min(15 min of slots, frequency cap − plays in window) plays (`ScreenPlayLedger`), and ranks zone-hours by predicted
impressions = Σ plays (`SlotRecommender`). Competitor separation is off in the replay (no recent winners a day ahead).

