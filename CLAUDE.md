# Airport Retail Media Platform

## Mission

A locally runnable POC: ads and promotions on airport screens, timed to live flight and passenger-flow data, scored against a fixed Topcoder spec. Full domain language (Demo Spine, Peripheral Role, Should Item, canonical entities) lives in `CONTEXT.md`. Architectural decisions and their rationale live in `docs/adr/`. The scored ticket breakdown lives in `.scratch/airport-media-platform/issues/`. Read these before assuming a term or a decision isn't already settled.

## Stack

- Java 21, Quarkus, Postgres (Quarkus Dev Services locally, Testcontainers in tests — no manual DB setup either way)
- React + Vite frontend, SSE for live push (dashboard updates and the simulation clock both ride the same channel)
- No authentication anywhere. The role switcher is a pure UI persona swap, not a session. There is no user to log in as — don't build login, tokens, or session state for it.

## Backend architecture

Layered, same shape regardless of feature:

```
java/com/airportmedia/platform/
  common/        date, enums, exception, util
  config/
  domain/
    entity/
    model/{input,mapper,output,validation}
  adapter/       one package per source: aodb/, aidx/, acris/
  resource/      REST + SSE endpoints
  repository/
  service/
```

**The `adapter/` package is load-bearing, not boilerplate.** Each source (AODB, AIDX, ACRIS) gets an interface like `FlightSource.stream(): AsyncIterable<FlightEvent>`, a mock implementation, and a stub Live implementation, switched by one config value (`FLIGHT_SOURCE=mock-aidx|vendor-api`). Business logic and resources depend only on the canonical model (`domain/entity`) — never on an adapter's wire format. If you catch yourself branching on "is this mock or live" outside the adapter package, that's the tell the boundary leaked.

Validation and errors: a global `ExceptionMapper` per exception type (validation, JSON parse, not found, generic), returning one `ErrorResponse` shape with field-level messages for Bean Validation failures.

## TDD — where it's load-bearing and where it isn't

The spec itself demands auditability on the decision pipeline: scores must be reproducible from logged inputs, and a hallucinated fact must never reach a screen. That's exactly where a red test earns its keep.

**Test-first, red before green, no exceptions:** the decision pipeline (profile → filter → score → rules → log), ZonePresence / crowd-profile computation, every adapter's translation into the canonical model, and promo-code/business-rule logic (expiry, frequency caps, budget pacing, the Gate 42 worked example's exact numbers). Each of these is pure enough to pin with a unit test before the implementation exists.

**Write the test alongside or just after, not strictly first:** React components, SSE wiring, the control bar, screen layout. The dominant risk there is visual and interaction correctness, which a running browser catches faster than a render-tree assertion — forcing red-first ceremony on presentational code burns clock without buying confidence, and the clock here is 30-36 hours.

If you're unsure which bucket a piece of code falls in, ask: "does this compute a number or a decision that gets logged and must be reproducible?" If yes, it's the first bucket.

## Java style

- Records for value objects and DTOs; Lombok to cut getter/setter boilerplate where a record doesn't fit. Fields `final` unless the object is genuinely mutated after construction.
- Constructor injection everywhere — Quarkus/CDI supports it natively, and it's what makes the test-first logic above testable without a container.
- Method references over lambdas where the lambda is just forwarding (`Campaign::isActive`, not `c -> c.isActive()`).
- Minimal comments. When you touch decision-pipeline scoring, put the formula and its inputs in the Javadoc — that's the one place a future reader (or reviewer auditing a decision) needs the "why," same as the spec's own evidence-trail requirement demands.

## How to respond

Summarize what you're about to implement before writing code. Work one ticket from `.scratch/airport-media-platform/issues/` at a time, in dependency order.
