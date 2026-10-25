# Architecture

One Docker Compose stack: Postgres, a Quarkus backend, and an nginx container that serves the React bundle and
proxies `/api` (REST + Server-Sent Events) to the backend, so the browser only ever talks to one origin. The same
stack runs on a laptop and on the DigitalOcean droplet (ADR 0002).

## System view

```mermaid
flowchart LR
  subgraph Clients
    roles["Role dashboards<br/>Admin · Retailer · Media Manager · Airline<br/>(React, EN/ES)"]
    kiosk["Passenger screens<br/>/screen/:id"]
    phone["Passenger phone<br/>/claim/:code"]
  end

  subgraph Compose["docker compose"]
    nginx["frontend<br/>nginx: static bundle + /api proxy<br/>(SSE unbuffered)"]
    subgraph Backend["backend (Quarkus, Java 21)"]
      resource["resource/<br/>REST + SSE /api/stream"]
      service["service/<br/>simulation clock · ZonePresence & crowd profile<br/>decision pipeline · claims · dashboards"]
      adapters["adapter/<br/>AODB · AIDX · ACRIS · GenAI<br/>Source (mock | live) + Translator"]
      repo["repository/ (Panache)"]
    end
    db[("Postgres<br/>Flyway V1–V7")]
  end

  feeds["Vendor feeds (live mode)<br/>AODB CSV · AIDX XML · ACRIS NGSI-LD"]
  llm["OpenAI-compatible LLM<br/>(optional, provider=llm)"]

  roles & kiosk & phone -->|HTTP| nginx
  nginx -->|/api| resource
  resource --> service
  service --> adapters
  service --> repo --> db
  adapters -. live .-> feeds
  adapters -. llm .-> llm
  resource -. "SSE: clock ticks + events" .-> nginx
```

- **Canonical model.** Services and resources only see canonical entities (`domain/entity`: Flight, Zone,
  Campaign, Creative…). Each adapter turns its own wire format into those entities; see the
  [adapter guide](adapter-guide.md).
- **One clock, one stream.** The simulation clock is server-authoritative. `GET /api/stream` merges clock ticks
  and domain events (delays, gate changes, screen status, scans, redemptions, incidents, goodwill vouchers), so
  every view, including the split-screen demo, moves in lock-step at 1×, 10× or 60×.
- **No authentication.** Roles are UI personas the header switches between. There are no sessions or tokens.

## Decision pipeline

Per screen, per slot. It is a pure function of a logged `DecisionInputs` record, so any score can be reproduced
from the decision log (ADR 0004 has the formula and the Gate 42 worked example).

```mermaid
flowchart LR
  inputs["Inputs<br/>ZonePresence + crowd profile<br/>nearby flights · campaigns · ledger"]
  filter["Hard filters<br/>approval · schedule · placement<br/>haul · crowd · budget"]
  score["Score 0–100<br/>0.35 crowdFit + 0.25 boarding<br/>+ 0.15 dwell + 0.15 spend<br/>+ 0.10 engagement"]
  rules["Business rules<br/>frequency cap · airline priority<br/>competitor separation · pacing"]
  fresh{"Inputs fresh<br/>(≤ 10 sim-min)?"}
  winner["Winner + DecisionLog<br/>(evidence trail)"]
  fallback["Rule-based default<br/>flagged fallback"]
  impression["Impression → engagement prior"]

  inputs --> fresh
  fresh -- yes --> filter --> score --> rules --> winner --> impression
  fresh -- no --> fallback --> winner
```

The passenger screen renders the winner's creative in the viewer's language (a stored translation or the
original, never machine-translated), its promo QR, and a "Why this ad?" overlay read straight from the log.

## Request flow: a QR claim

```mermaid
sequenceDiagram
  participant S as Passenger screen
  participant P as Phone (claim page)
  participant N as nginx
  participant B as Backend
  participant D as Retailer dashboard
  S->>B: GET /api/screens/{id}/playback?lang=es
  B-->>S: creative (ES copy) + promo code + claimPath
  P->>N: open /claim/{code}?lang=es (QR)
  P->>B: POST /api/promo/{code}/scan
  B--)D: SSE "scan"
  P->>B: POST /api/promo/{code}/claim?lang=es
  B-->>P: CLAIMED (single-use, expires at boarding close)
  B--)D: SSE "redemption" → funnel refetch (< 2 s)
```

## Backend layout

```
backend/src/main/java/com/airportmedia/platform/
  common/      enums, exceptions + ExceptionMappers (one ErrorResponse shape), reference data
  config/
  domain/      entity/ (canonical model), model/{input,mapper,output,validation}
  adapter/     aodb/ aidx/ acris/ genai/ — Source (mock|live) + Translator per feed
  resource/    REST + SSE endpoints (all under /api)
  repository/  Panache repositories
  service/     simulation, presence, decision/, admin/, media/, airline/, assistant/
```

Decisions behind this shape are in [`docs/adr/`](adr/): depth-first scope (0001), stack and deployment (0002),
injected events through the adapters (0003), decision scoring (0004).
