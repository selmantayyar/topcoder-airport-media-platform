#!/usr/bin/env bash
# Redeploy on the droplet: pull the latest commit, rebuild what changed, restart, report.
#   ssh root@142.93.232.242 /root/topcoder-airport-media-platform/scripts/redeploy.sh
# The database volume survives, so the seeded airport-day and anything added since stay.
set -euo pipefail
cd "$(dirname "$0")/.."

git pull --ff-only
docker compose up -d --build --remove-orphans
docker image prune -f >/dev/null

port=$(grep -E '^HTTP_PORT=' .env 2>/dev/null | cut -d= -f2)
port=${port:-8088}
for _ in $(seq 1 90); do
  if curl -sf "http://localhost:${port}/api/screens" >/dev/null; then
    echo "Deployed $(git rev-parse --short HEAD) — http://localhost:${port} is up."
    exit 0
  fi
  sleep 2
done
echo "Backend did not come up within 3 minutes; see: docker compose logs backend" >&2
exit 1
