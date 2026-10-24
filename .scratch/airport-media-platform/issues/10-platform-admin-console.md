# 10 — Platform Admin console

**What to build:** The operator's control-room view: a live terminal map of every screen colored by status with a passenger-presence heat overlay, a KPI strip, an auto-raised incident tracker, and master-data management for vendors, outlets, airlines, locations, and screens.

**Blocked by:** 09 — Retailer campaign builder + creative library + promo codes + history, 06 — Decision pipeline + Gate 42 worked example, 05 — ZonePresence + Crowd Profile computation, 03 — Mock data generator + AODB/AIDX/ACRIS adapters

**Status:** done

- [x] A stylised terminal floor plan shows every screen as a dot colored online/degraded/offline
- [x] A heat overlay on the map reflects live ZonePresence data
- [x] KPI strip shows screens-online %, ad plays today, impressions, revenue today (configurable currency), active campaigns, and average screen latency, all live
- [x] Incidents are auto-raised for screen offline > 5 min, feed lag, and failed creative, each with severity/owner/status
- [x] Master data (vendors, outlets/F&B retailers, airlines, locations, screens) can be listed, created, and edited through one reusable generic list+form component

`GET /api/admin/overview` (polled every 2s, refetched on `incident`/`screen-offline` events) carries the KPIs,
the terminal → zone → screen map with a ZonePresence heat value per zone, and incidents. "Ad plays" counts
ads played today; "impressions" is the audience reached (Σ crowd headcount of executed decisions). Revenue =
plays × impression price × `app.admin.currency-rate`, shown in `app.admin.currency`. Incidents are raised by
`IncidentRules` (screen offline > 5 min HIGH / > 30 min CRITICAL; feed silent > `app.admin.feed-lag-minutes`
MEDIUM / > 3× HIGH — AIDX and ACRIS; a running campaign's creative failing `CreativeQa` MEDIUM) and reconciled
every `app.admin.incident-tick`, auto-resolving when the condition clears. Master data is one schema-driven API
(`/api/admin/master/{type}`) and one generic `MasterDataTable` component for vendors, outlets, airlines,
locations and screens.
