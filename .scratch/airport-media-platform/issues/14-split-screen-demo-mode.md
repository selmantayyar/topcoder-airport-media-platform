# 14 — Split-screen demo mode

**What to build:** A demo-mode layout showing the Passenger Screen simulator alongside any chosen role dashboard, both driven in sync by the shared simulation control bar.

**Blocked by:** 07 — Passenger Screen simulator + QR claim flow, 08 — Retailer live dashboard + passenger flow, 09 — Retailer campaign builder + creative library + promo codes + history, 10 — Platform Admin console, 04 — Simulation clock & control bar

**Status:** ready-for-agent

- [ ] A split-screen layout renders the Passenger Screen on one side and a selectable role dashboard on the other
- [ ] Both sides update from the same simulation clock and the same control bar actions, with no visible desync
- [ ] Injecting an event (e.g. a flight delay) visibly changes both sides within 2 seconds
