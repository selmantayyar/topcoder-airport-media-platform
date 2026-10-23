# 12 — Airline Representative (Peripheral Role)

**What to build:** An airline rep's basic but functionally real view of their own flights, the ability to schedule gate-area messaging for their boarding windows, and automatic delay-goodwill offers.

**Blocked by:** 06 — Decision pipeline + Gate 42 worked example, 04 — Simulation clock & control bar, 03 — Mock data generator + AODB/AIDX/ACRIS adapters

**Status:** ready-for-agent

- [ ] "My flights today" lists/timelines the selected airline's departures with gate, status, delay, and load factor
- [ ] The rep can schedule sponsored content (upgrade offers, lounge passes, destination partners) for their own gates/boarding windows, and it's picked up by ticket 06's pipeline with airline-priority business rules applied
- [ ] When a flight delay exceeds a configured threshold, a meal/lounge voucher campaign co-funded with a retailer is automatically created and pushed to that gate's screens without manual action
- [ ] Switching between sample airline companies changes the data shown
