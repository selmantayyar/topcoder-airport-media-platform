# Airport Retail Media Platform — Domain Context

A locally runnable, demo-ready POC of an airport retail media platform: ads and promotions on airport screens, timed to live flight and passenger-flow data, built for a scored Topcoder submission.

## Language

**Demo Spine**:
The roles and subsystems that get full visual and interaction polish first, because they carry the 15-minute demo narrative: Passenger Screen, Retailer, Platform Admin, the decision pipeline, and the simulation engine. Every Must item in every role is still built and functionally real regardless of spine membership — this term only governs how much polish beyond "correct and working" an area receives first.
_Avoid_: core features, critical path

**Peripheral Role**:
A role (Media Manager, Airline Representative) whose Must items are fully functional and wired to real data, but implemented at the simplest correct depth (generic list views, plain tables) rather than bespoke, polished UI, because it isn't on screen during the live demo. Never means stubbed or non-functional.
_Avoid_: secondary role, stubbed role, low-priority role

**Should Item**:
A feature explicitly marked "Should" or "Should (proposed)" for a given role in the challenge spec — the spec's own optional, bonus-scored tier. Distinct from a "Required WOW feature" (e.g. the GenAI creative assistant, the Smart recommendation), which despite living under the demo-narrative "WOW" heading is mandatory, not optional.
_Avoid_: optional feature, nice-to-have, bonus feature
