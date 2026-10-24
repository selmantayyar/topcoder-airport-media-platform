# 07 — Passenger Screen simulator + QR claim flow

**What to build:** A full-screen view of a chosen physical screen that renders whatever the decision pipeline is currently playing, with a FIDS-style context strip, a "Why this ad?" overlay, and a promo QR whose scan on a real phone opens a LAN-served claim page that records a redemption.

**Blocked by:** 06 — Decision pipeline + Gate 42 worked example

**Status:** done

- [x] A screen can be chosen by gate or zone and renders full-screen in 9:16 or 16:9
- [x] The playlist visibly changes as simulation time moves and flights board, delay, or depart
- [x] A context strip shows nearby flights (FIDS-style) next to the ad
- [x] A "Why this ad?" overlay shows the crowd profile bars, top-3 candidate scores, and the winning rule
- [x] The promo QR shows a live countdown and encodes a configurable LAN IP
- [x] Scanning the QR with a real phone on the same LAN opens a mobile claim page and recording a redemption succeeds
- [x] The redemption is persisted immediately and queryable (visible to ticket 08's dashboard once it exists)

Backend: `/api/screens`, `/api/screens/{id}/playback`, `/api/promo/{code}` (+`/claim`). A deterministic
`SCR-GATE42` screen is seeded so the Gate 42 example is choosable by gate. Frontend routes: `/screen/:id`
(kiosk) and `/claim/:code` (mobile). A successful claim broadcasts on the live stream for ticket 08.
