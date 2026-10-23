# 13 — GenAI creative assistant + Smart recommendation (Required WOW)

**What to build:** Inside the creative library, a brief-in/ad-page-out assistant (mocked by default, pluggable to a real LLM provider via config, no API key required for the demo) that produces a ready ad page in English and Spanish, plus a "best 3 slots for this campaign tomorrow" recommendation.

**Blocked by:** 09 — Retailer campaign builder + creative library + promo codes + history, 06 — Decision pipeline + Gate 42 worked example

**Status:** ready-for-agent

- [ ] Typing a brief (e.g. "Festive sweets, 20% off, families flying domestic") produces a ready ad page in the existing creative template, in English and Spanish
- [ ] The default mock provider requires no API key and works fully offline
- [ ] A real provider can be swapped in via a single config value (using the `quarkus-langchain4j` pluggable-provider pattern) without code changes
- [ ] "Best 3 slots for this campaign tomorrow" surfaces three concrete slot recommendations with predicted impressions, computed from ticket 06's scoring logic
