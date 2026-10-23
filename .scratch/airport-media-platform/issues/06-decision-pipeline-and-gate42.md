# 06 — Decision pipeline + Gate 42 worked example

**What to build:** The full per-screen, per-slot decision pipeline (profile → filter → score → business rules → choose & log → learn) running continuously, reproducing the spec's Gate 42 worked example exactly, with every decision logged with its full evidence trail.

**Blocked by:** 05 — ZonePresence + Crowd Profile computation

**Status:** ready-for-agent

- [ ] Every screen re-runs the full pipeline roughly every 15 seconds of simulation time
- [ ] Hard filters (schedule, placement, approval, brand safety, domestic/international, destination, minimum crowd size, budget) correctly drop ineligible campaigns
- [ ] Scoring produces reproducible scores from logged inputs (same inputs → same score)
- [ ] Business rules apply (airline priority in its own boarding window, competitor separation, frequency cap, budget pacing)
- [ ] Replaying the spec's Gate 42 scenario (flight XY501 delayed 45 min + ZQ88, three candidate campaigns) produces the documented scores and the duty-free campaign winning
- [ ] Every decision is persisted with its full evidence trail (profile, candidates, scores, winning reason) and is queryable
- [ ] A decision whose cited inputs fail freshness/trust checks falls back to the rule-based default rather than executing
