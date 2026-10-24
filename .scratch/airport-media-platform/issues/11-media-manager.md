# 11 — Media Manager (Peripheral Role)

**What to build:** The ad-sales and inventory team's basic but functionally real view: an inventory calendar, a yield/pricing suggestion, an approval queue that actually gates which campaigns the decision pipeline considers, and an advertiser overview.

**Blocked by:** 09 — Retailer campaign builder + creative library + promo codes + history, 06 — Decision pipeline + Gate 42 worked example, 10 — Platform Admin console

**Status:** done

- [x] Inventory calendar shows every screen × hour as booked/available/premium, where premium reflects predicted footfall from ticket 05
- [x] Yield/pricing shows a suggested price per slot plus occupancy % and a 7-day revenue forecast
- [x] Approval queue lets the Media Manager approve or reject creatives/campaigns, and a rejected/unapproved campaign is actually excluded by ticket 06's hard-filter stage
- [x] Advertiser overview lists top advertisers by spend, running campaigns, and flags under-delivering campaigns

Inventory covers all 24 hours of the simulated day. Approval now gates the pipeline end to end: the retailer builder and "run again" submit campaigns as
PENDING_APPROVAL; the Media Manager approves (→ RUNNING) or rejects (→ REJECTED). The pipeline's candidate set
is every campaign scheduled for now, so unapproved/rejected ones are dropped — and logged — by the hard-filter
stage (`not running (…)`). Inventory: a slot is booked when a RUNNING campaign's schedule and placement cover
it; premium = predicted ZonePresence footfall ≥ the 75th percentile of busy slots. Pricing and pacing formulas
are in `YieldPricing` and `DeliveryPacing`; the 7-day forecast assumes each day repeats today's footfall.
