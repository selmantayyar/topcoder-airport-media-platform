# 12 — Airline Representative (Peripheral Role)

**What to build:** An airline rep's basic but functionally real view of their own flights, the ability to schedule gate-area messaging for their boarding windows, and automatic delay-goodwill offers.

**Blocked by:** 06 — Decision pipeline + Gate 42 worked example, 04 — Simulation clock & control bar, 03 — Mock data generator + AODB/AIDX/ACRIS adapters

**Status:** done

- [x] "My flights today" lists/timelines the selected airline's departures with gate, status, delay, and load factor
- [x] The rep can schedule sponsored content (upgrade offers, lounge passes, destination partners) for their own gates/boarding windows, and it's picked up by ticket 06's pipeline with airline-priority business rules applied
- [x] When a flight delay exceeds a configured threshold, a meal/lounge voucher campaign co-funded with a retailer is automatically created and pushed to that gate's screens without manual action
- [x] Switching between sample airline companies changes the data shown

Sponsored content targets the chosen flight's gate for its boarding window (the shared `FlightBoard` boarding window: departure − 40 min → departure) and is
owned by the airline's advertiser record, so ticket 06's airline-priority rule (×1.5) applies for exactly that window. It
goes live without the Media Manager queue — an airline's own gate messaging in its own boarding window is its
contracted inventory, and worthless if it waits. Delay goodwill: a departure delayed ≥ `app.airline.delay-voucher-minutes`
(default 60; ≥ 2× earns a lounge pass) gets a live voucher campaign on its gate, co-funded by a retail partner
(`GoodwillPolicy`), with a promo code expiring at boarding close — triggered on delay injection and by a scan of
delayed departures within `app.airline.goodwill-horizon-hours`. The 60-minute default keeps the 45-minute Gate 42
worked example voucher-free; the control bar's delay button now offers 45/90/150 minutes.
Limits: a voucher keeps the gate and tier it was created with (a later gate change or a delay growing past the lounge
threshold doesn't update it), and screens switch to it on the decision loop's next tick, i.e. while the clock runs.
Alongside: the control bar's default delay/gate-change target is now the next upcoming undelayed departure (it could
previously pick a flight that had already left), and the flight board reads DEPARTED for a flight that left late.
