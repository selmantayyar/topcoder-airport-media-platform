# 01 — App skeleton + one-command start

**What to build:** A developer or reviewer runs one command and gets the whole system up on localhost: Postgres, Quarkus backend, React frontend, with an app shell showing a role switcher and a live server-pushed tick proving the real-time channel works end to end.

**Blocked by:** None — can start immediately

**Status:** done

- [x] `docker compose up` (or equivalent single command) brings up Postgres + backend + frontend with no manual steps
- [x] App shell renders with a role switcher in the header (roles can be placeholder stubs)
- [x] Dark theme applies when "Platform Admin" is selected; light theme applies for other roles
- [x] A backend SSE endpoint pushes a server clock/tick value at least once per second
- [x] The frontend subscribes to that SSE stream and renders the live value, visibly updating without a page reload
