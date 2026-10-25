# Adapter guide: swapping a mock feed for a live one

Every external feed sits behind an adapter in `backend/src/main/java/com/airportmedia/platform/adapter/`. The rest
of the app (services, the decision pipeline, REST resources) only ever sees canonical entities, so going live
changes one class and one config value. Nothing else is touched.

## How an adapter is built

Each feed splits into two halves:

| Half | Job | Mock | Live |
|---|---|---|---|
| **Source** | Where the wire-format payload comes from | Generates realistic payloads for the fictional airport | Stub that pulls from the vendor feed |
| **Translator** | Wire payload → canonical entities | Shared by both Sources | Same class, already unit-tested |

A **provider** picks the active Source from one config value. Because the Translator is shared, a live payload
goes through exactly the translation the mock already exercises in the tests.

| Feed | Wire format | Source interface | Translator | Config value | Env var |
|---|---|---|---|---|---|
| AODB (flights, gates, aircraft) | 33-column CSV export | `aodb/FlightSource` | `aodb/AodbTranslator` | `app.flight.source` | `APP_FLIGHT_SOURCE` |
| AIDX (real-time leg updates) | IATA `FlightLegNotifRQ` XML | `aidx/FlightEventSource` | `aidx/AidxTranslator` | `app.aidx.source` | `APP_AIDX_SOURCE` |
| ACRIS (terminals, zones, queues) | FIWARE NGSI-LD JSON | `acris/AcrisSource` | `acris/AcrisTranslator` | `app.acris.source` | `APP_ACRIS_SOURCE` |
| GenAI creative assistant | — | `genai/CreativeAssistant` | (grounding check in `service/assistant`) | `app.creative-assistant.provider` | `APP_CREATIVE_ASSISTANT_PROVIDER` |

Values: `mock` (default) or `live` for the three feeds; `mock` (default) or `llm` for the assistant.

## Going live, step by step (AODB as the example)

1. **Implement the Live Source.** `LiveFlightSource` currently throws `UnsupportedOperationException`. Make
   `aodbSnapshotCsv(...)` fetch the vendor's export (HTTP, SFTP, a file drop) and return it in the same 33-column
   shape `AodbCsvCodec` reads. If the vendor's columns differ, map them in the Source; the Translator's contract
   stays the canonical one.
2. **Flip the one value.** In `.env` (Compose passes it through):

   ```
   APP_FLIGHT_SOURCE=live
   ```

   or `app.flight.source=live` in `application.properties`, or `-Dapp.flight.source=live`.
3. **Redeploy.** Locally run `docker compose up -d --build`. On the droplet run `scripts/redeploy.sh`.
4. **Prove it.** Feed one captured vendor payload through `AodbTranslatorTest`-style tests: if the Translator
   accepts it, every downstream feature (presence, scoring, FIDS strips, dashboards) works unchanged.

AIDX and ACRIS follow the same steps with `LiveFlightEventSource` and `LiveAcrisSource`. For AIDX, a live Source
*receives* pushed messages (e.g. a queue consumer) and hands each XML message to `AidxTranslator`.

## The rule that keeps this true

Business code never branches on "is this mock or live". The only exception is deliberate and documented: the
simulation's injected events (delay, gate change) are serialised to AIDX XML and translated back, so the demo
exercises the real feed path end to end (ADR 0003).

## GenAI assistant

`APP_CREATIVE_ASSISTANT_PROVIDER=llm` switches to a real model through `quarkus-langchain4j`, using any
OpenAI-compatible endpoint (`OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_MODEL`; a local Ollama works). Copy that
cites a number or offer the brief never stated is rejected and replaced by the offline mock's copy. A
hallucinated claim never reaches the library or a screen.
