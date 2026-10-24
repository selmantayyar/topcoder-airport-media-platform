# 14 — Split-screen demo mode

**What to build:** A demo-mode layout showing the Passenger Screen simulator alongside any chosen role dashboard, both driven in sync by the shared simulation control bar.

**Blocked by:** 07 — Passenger Screen simulator + QR claim flow, 08 — Retailer live dashboard + passenger flow, 09 — Retailer campaign builder + creative library + promo codes + history, 10 — Platform Admin console, 04 — Simulation clock & control bar

**Status:** done

- [x] A split-screen layout renders the Passenger Screen on one side and a selectable role dashboard on the other
- [x] Both sides update from the same simulation clock and the same control bar actions, with no visible desync
- [x] Injecting an event (e.g. a flight delay) visibly changes both sides within 2 seconds

"Split-screen demo" in the header (`?demo=1&role=…&screen=…`, bookmarkable): the Passenger Screen (embedded
`PassengerScreenView`, defaulting to SCR-GATE42) on the left, the header-selected role dashboard (`RoleDashboard`) on
the right, both under the app's single SSE connection and the one control bar. In demo mode the control bar's
injections target the shown screen (`screenId` on `/api/sim/inject/*`: the next undelayed departure at its gates, else
its zone), injected events are emitted after commit, and every live view refetches on them (`server/events.ts`).
To keep a delay visible on both sides: a screen's FIDS strip gives its own gates' delayed flights first claim on its 8
places (then own gates, then the zone), the embedded strip wraps, an offline screen renders "No signal", the
retailer flow shows passengers held by delayed departures, and the admin console gains a "Delayed departures" tile.
"Screen online" restores a screen so the demo can be re-run. Airline shows a delay only when it is the selected
airline's flight; Media Manager (Peripheral) refetches but has no flight data to change. Measured with headless Chrome over CDP: delay and
screen-offline injections visibly changed both panes within ~1.6 s (SSE event ~66 ms after the POST).

