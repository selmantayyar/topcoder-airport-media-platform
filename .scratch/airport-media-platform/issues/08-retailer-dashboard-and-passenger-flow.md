# 08 — Retailer live dashboard + passenger flow

**What to build:** For a selected outlet, a live dashboard of impressions/scans/redemptions/conversion/revenue ticking in real time, plus a forward-looking footfall view for the next two hours derived from upcoming departures.

**Blocked by:** 07 — Passenger Screen simulator + QR claim flow, 05 — ZonePresence + Crowd Profile computation

**Status:** done

- [x] Selecting an outlet (among sample fictional retailer companies) shows live impressions, scans, redemptions, conversion rate, and attributed revenue, updating without a manual refresh
- [x] A redemption recorded via ticket 07's claim page appears on this dashboard within 2 seconds
- [x] "Passenger flow near my store" shows next-60/120-minute footfall with top destinations and nationalities, matching the spec's example format
- [x] Switching between sample retailer companies changes the data shown

Backend: `/api/retailers`, `/{id}/metrics` (impressions → scans → redemptions, conversion = redemptions ÷
impressions, attributed revenue = redemptions × `app.retail.avg-basket`), `/{id}/flow` (next-60/120-min
departing footfall over the company's outlet terminals, top destinations + nationalities by route language).
Scans are recorded when the claim page opens (`POST /api/promo/{code}/scan`). Claim/scan SSE events are
emitted after commit, so the dashboard's event-triggered refetch always sees the new count.
