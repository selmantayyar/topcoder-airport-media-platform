# 10 — Platform Admin console

**What to build:** The operator's control-room view: a live terminal map of every screen colored by status with a passenger-presence heat overlay, a KPI strip, an auto-raised incident tracker, and master-data management for vendors, outlets, airlines, locations, and screens.

**Blocked by:** 09 — Retailer campaign builder + creative library + promo codes + history, 06 — Decision pipeline + Gate 42 worked example, 05 — ZonePresence + Crowd Profile computation, 03 — Mock data generator + AODB/AIDX/ACRIS adapters

**Status:** ready-for-agent

- [ ] A stylised terminal floor plan shows every screen as a dot colored online/degraded/offline
- [ ] A heat overlay on the map reflects live ZonePresence data
- [ ] KPI strip shows screens-online %, ad plays today, impressions, revenue today (configurable currency), active campaigns, and average screen latency, all live
- [ ] Incidents are auto-raised for screen offline > 5 min, feed lag, and failed creative, each with severity/owner/status
- [ ] Master data (vendors, outlets/F&B retailers, airlines, locations, screens) can be listed, created, and edited through one reusable generic list+form component
