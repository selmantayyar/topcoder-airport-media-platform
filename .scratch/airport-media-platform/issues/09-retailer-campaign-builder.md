# 09 — Retailer campaign builder + creative library + promo codes + history

**What to build:** A retailer can build a campaign (creative, audience rules, placement, schedule, budget), see a live audience-match preview, reuse creatives, generate time-boxed promo codes with a share-to-family option, and review past campaigns with a one-click "run again."

**Blocked by:** 07 — Passenger Screen simulator + QR claim flow, 06 — Decision pipeline + Gate 42 worked example

**Status:** done

- [x] Campaign builder lets a retailer pick a creative, target zones/screens, set audience rules (destination, domestic/international, dwell time, delay), set a schedule and budget, and preview on a mock screen
- [x] A campaign saved here is picked up by ticket 06's decision pipeline on its next run
- [x] Live audience-match preview shows an estimated match percentage and expected impressions/redemptions while building the campaign
- [x] Creative library supports reuse and one-click duplication of a past campaign
- [x] Promo codes are time-boxed, expire when the linked flight's boarding closes, and support generating a second shareable code for family
- [x] Campaign history lists past campaigns with spend, impressions, redemptions, and ROI, with a working "run again" action

Builder saves campaigns as RUNNING (no approval queue exists in ticket 10), so the pipeline picks them up on
its next tick. Placement targets gates, zones or screens; audience rules add destination, haul, dwell, crowd
and a minimum-delay filter. The audience-match preview runs the draft through the real pipeline on every
online screen (`DecisionService.simulateDraft`); see `AudienceMatchCalculator` for the formulas. Promo codes
are time-boxed and, when linked to a flight, expire at its boarding close (departure − 15 min, evaluated
live — `PromoPolicy`); a claimed code can mint one share-to-family code. Also fixed: `/api/screens/{id}/decision`
had been shadowed (404) since ticket 07 — now on `ScreenResource`, guarded by a route test.
