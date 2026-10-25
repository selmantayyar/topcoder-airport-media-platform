# Decision-pipeline scoring and the Gate 42 worked example

The spec requires the Gate 42 worked example to "produce the documented scores" with the duty-free
campaign winning, but the challenge's own numbers were not available to this build. We therefore
**define** the scoring model here and treat these numbers as canonical: the formula below, the frozen
Gate 42 figures, and the pinning test (`DecisionPipelineTest.gate42WorkedExample_dutyFreeWins`) are
the single source of truth. Any change to scoring must update all three together.

## Pipeline

Per screen, per slot: **profile → filter → score → rules → choose & log → learn**. The whole thing
is a pure function of a logged `DecisionInputs` record (`DecisionPipeline.decide`), so a score is
reproducible from exactly the inputs that produced it — the spec's auditability requirement.

- **Hard filters** (drop + reason): approval/status, schedule, placement, haul, destination,
  brand-safety, minimum crowd, budget. Filters split into *static* (approval, schedule, placement,
  budget — trustworthy regardless of live data) and *dynamic* (the rest), so the fallback can reuse
  only the static ones.
- **Score** (0..100): a fixed weighted sum of five 0..1 components:

  ```
  base = 100 × ( 0.35·crowdFit + 0.25·boardingProximity + 0.15·dwellFit
               + 0.15·spendMatch + 0.10·engagementPrior )
  ```

  - **crowdFit** — weighted average of the audience's business/leisure/family shares under the
    campaign's segment weights.
  - **boardingProximity** — strongest nearby-flight boarding signal, reusing the ZonePresence
    presence ramp.
  - **dwellFit** — expected dwell over a 120-minute reference (a delayed, captive crowd scores high).
  - **spendMatch** — crowd share in (and adjacent to) the offer's spend tier.
  - **engagementPrior** — Laplace-smoothed historical conversion `(red+1)/(imp+20) / 0.10`, a cold
    campaign scoring a neutral 0.5.
- **Business rules** (logged multipliers): frequency cap (drop), airline priority (×1.5 in its own
  boarding window), competitor separation (×0.8), budget pacing (down to ×0.7). `final = base × mult`.
- **Freshness/trust**: if the cited data is missing or older than 10 simulation-minutes, the dynamic
  score is discarded and the winner comes from the **rule-based default** (static-eligible campaign
  with the most remaining budget), flagged `fallback`.
- **Learn**: executing a decision records an `Impression` for the winner, which feeds the engagement
  prior of later decisions.

## Gate 42 — frozen numbers

Scenario at `2026-10-23T14:30Z`, screen at gate 42 (Pier D): **XY501** (DXB, 240 pax, delayed 45 →
boards 14:55) and **ZQ88** (MLE, low-cost, 160 pax, boards 15:00). The two blend to a 400-pax,
leisure-dominant, Gulf/holiday audience (trip BUSINESS 16 / LEISURE 60 / VFR 24; spend LOW 45 /
MEDIUM 31 / HIGH 24; dwell 139m). Three candidates, all placement/schedule/budget-eligible, no rule
adjustments, so `final = base`:

| Campaign | crowdFit | boarding | dwell | spendMatch | engagement | **score** |
|---|---|---|---|---|---|---|
| **Azure Duty-Free — 15% off perfume** | 0.4831 | 1.00 | 1.00 | 0.655 | 0.50 | **71.73** |
| Bean & Brew Coffee — Free coffee upgrade | 0.4000 | 1.00 | 1.00 | 0.605 | 0.50 | 68.08 |
| Luxe Watches — Premium watch sale | 0.2760 | 1.00 | 1.00 | 0.395 | 0.50 | 60.59 |

Duty-free wins: the leisure/holiday crowd fits its holiday-weighted targeting, and the mixed spend
tier matches its mid-market offer better than the watch brand's high-spend pitch.

## Trade-off: the re-run loop at high speed

Every online screen re-decides every ~15 simulation-seconds via a scheduled loop that reuses one
ZonePresence snapshot per zone per tick. Refreshing all screens every 15 *sim*-seconds is infeasible
at 60× (0.25 real-seconds), so the loop is due-based with a per-tick screen cap: at 1× it hits the
15-second cadence; at high speed it is best-effort and screens catch up over subsequent ticks. We
accept this because the demo's visible screens stay current and decision storage stays bounded.
