# 16 — Docs + final deployed-playground verification

**What to build:** The written deliverables (README, architecture diagram, adapter guide, OpenAPI/schema spec) and a final check that the deployed droplet URL matches what's demoed locally.

**Blocked by:** 02 — Deploy skeleton to the DigitalOcean droplet, 13 — GenAI creative assistant + Smart recommendation, 14 — Split-screen demo mode, 15 — i18n + white-label theming + responsive/mobile polish

**Status:** done

- [x] README covers setup, architecture, and justifies the Java/Quarkus stack choice
- [x] An architecture diagram is included
- [x] An adapter guide explains how to swap a mock adapter for a real "Live" one via the single config value
- [x] An OpenAPI (or equivalent schema) spec covers the REST API
- [x] The droplet-deployed playground URL is re-verified to match the final local build exactly, is pre-seeded, and is reachable for review

`README.md` (setup, dev loop, demo script, roles, architecture, "Why Java and Quarkus", adapters, config, theming,
deployment); `docs/architecture.md` (Mermaid system view, decision pipeline, QR-claim sequence);
`docs/adapter-guide.md` (Source/Translator, one value per feed: `APP_FLIGHT_SOURCE|APP_AIDX_SOURCE|APP_ACRIS_SOURCE`
= mock|live, passed through compose); OpenAPI from `quarkus-smallrye-openapi` at `/api/openapi` + Swagger UI at
`/api/docs` (48 paths incl. `/api/stream`), snapshot in `docs/openapi.yaml`, pinned by `OpenApiResourceTest`.
`docs/` is now committed, ADRs included.

Re-verification of <http://142.93.232.242> at commit 7079149: the droplet's worktree is clean at the same SHA as
local HEAD; the served `index-B5PFvQCZ.js` / `index-CRu1jRlU.css` have the same SHA-1 as a local `npm run build`;
fresh seed (181 screens incl. SCR-GATE42); at 14:10 the Gate 42 worked example plays the duty-free creative with
promo AMP-PERFUME15 (EN and ES copy); split-screen demo, admin console and the phone claim page render with no
horizontal overflow (headless Chrome over CDP); `/api/openapi` and `/api/docs` answer publicly; port 8080 is closed
to the internet. Clock left paused at 14:00.
