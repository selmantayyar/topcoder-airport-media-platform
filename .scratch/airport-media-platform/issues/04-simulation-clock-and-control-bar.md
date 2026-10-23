# 04 — Simulation clock & control bar

**What to build:** A server-authoritative simulation clock, visible and controllable from every role, that can play, pause, change speed (1x/10x/60x), jump to a time, and inject synthetic events (flight delay, gate change, screen offline) that flow through the ticket-03 adapters.

**Blocked by:** 03 — Mock data generator + AODB/AIDX/ACRIS adapters

**Status:** done

- [x] The simulation clock's current time and speed live on the backend and broadcast over the same SSE channel used elsewhere
- [x] A control bar in the app shell (visible to all roles) can play, pause, and set speed to 1x/10x/60x
- [x] Jump-to-time moves the simulation instantly and all connected views reflect the new time within 2 seconds
- [x] "Inject event" buttons exist for: delay a flight, change a gate, take a screen offline — each one visibly changes the relevant mock flight/screen state
- [x] Running at 60x does not cause visible UI lag or stale state
