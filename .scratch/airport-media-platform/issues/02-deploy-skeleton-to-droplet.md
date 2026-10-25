# 02 — Deploy skeleton to the DigitalOcean droplet

**What to build:** The exact Compose stack from ticket 01 running on the existing DigitalOcean droplet, reachable at a stable URL, so deployment risk is found and fixed on day one rather than at the deadline.

**Blocked by:** 01 — App skeleton + one-command start

**Status:** done

- [x] The same docker-compose definition used locally runs unmodified on the droplet
- [x] The app is reachable at a stable public URL
- [x] The live SSE tick from ticket 01 visibly updates when viewed at that URL
- [x] Redeploying (e.g. after a `git pull` + `docker compose up -d --build`) is a single documented command

Live at <http://142.93.232.242> (Ubuntu 24.04, 1 vCPU / 2 GB + 2 GB swap so the Maven image build fits). The droplet
runs the repo's `docker-compose.yml` as is; only the gitignored `.env` differs (`HTTP_PORT=80`). Services restart
unless stopped (survives reboot); the backend is published on loopback only, so the API is reachable solely through
nginx's `/api`. `/api/stream` ticks every ~0.5 s through the public URL (nginx buffering off). Redeploy:
`ssh root@142.93.232.242 /root/topcoder-airport-media-platform/scripts/redeploy.sh` (pull, build, restart, wait
for `/api` through nginx); documented in the README. Cold first build: 5 m 39 s.
