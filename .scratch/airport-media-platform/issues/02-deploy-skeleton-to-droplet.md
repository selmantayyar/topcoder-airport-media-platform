# 02 — Deploy skeleton to the DigitalOcean droplet

**What to build:** The exact Compose stack from ticket 01 running on the existing DigitalOcean droplet, reachable at a stable URL, so deployment risk is found and fixed on day one rather than at the deadline.

**Blocked by:** 01 — App skeleton + one-command start

**Status:** ready-for-agent

- [ ] The same docker-compose definition used locally runs unmodified on the droplet
- [ ] The app is reachable at a stable public URL
- [ ] The live SSE tick from ticket 01 visibly updates when viewed at that URL
- [ ] Redeploying (e.g. after a `git pull` + `docker compose up -d --build`) is a single documented command
