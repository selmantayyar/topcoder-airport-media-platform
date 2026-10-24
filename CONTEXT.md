# Airport Retail Media Platform — Domain Context

A locally runnable, demo-ready POC of an airport retail media platform: ads and promotions on airport screens, timed to live flight and passenger-flow data, built for a scored Topcoder submission.

## Language

**Demo Spine**:
The roles and subsystems that get full visual and interaction polish first, because they carry the 15-minute demo narrative: Passenger Screen, Retailer, Platform Admin, the decision pipeline, and the simulation engine. Every Must item in every role is still built and functionally real regardless of spine membership — this term only governs how much polish beyond "correct and working" an area receives first.
_Avoid_: core features, critical path

**Peripheral Role**:
A role (Media Manager, Airline Representative) whose Must items are fully functional and wired to real data, but implemented at the simplest correct depth (generic list views, plain tables) rather than bespoke, polished UI, because it isn't on screen during the live demo. Never means stubbed or non-functional.
_Avoid_: secondary role, stubbed role, low-priority role

**Source** / **Translator**:
The two halves every adapter splits into. A *Source* decides where wire-format bytes come from — the mock Source generates realistic payloads (AODB CSV, AIDX XML, ACRIS NGSI-LD JSON); the Live Source is a stub that would pull from a real vendor feed. A *Translator* turns one wire payload into canonical entities and is shared by both Sources, so going live swaps only the Source and the translation stays tested. Switched per source by one config value (e.g. `app.flight.source=mock|live`).
_Avoid_: parser, mapper (for Translator); feed, provider (for Source)

**Canonical entity**:
A domain entity the app core sees, never a source's wire shape. From AODB: Flight, GateAssignment, AircraftType. From AIDX: FlightEvent. From ACRIS: Terminal, Zone, Checkpoint, QueueMeasurement. Platform-owned: Screen, Advertiser, Outlet, Campaign, Creative, PromoCode, Impression, PromoScan, Redemption, DecisionLog. The retail funnel is Impression (an ad played) → PromoScan (a QR opened the claim page) → Redemption (the offer was claimed). Business logic and resources depend only on these.
_Avoid_: model, record, DTO

**Should Item**:
A feature explicitly marked "Should" or "Should (proposed)" for a given role in the challenge spec — the spec's own optional, bonus-scored tier. Distinct from a "Required WOW feature" (e.g. the GenAI creative assistant, the Smart recommendation), which despite living under the demo-narrative "WOW" heading is mandatory, not optional.
_Avoid_: optional feature, nice-to-have, bonus feature
