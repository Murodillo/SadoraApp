#!/bin/zsh
#
# Takes the public demo off the internet.
#
#   ./tools/demo_down.sh
#
# Closes both tunnels first — that is the part that matters, since it is what makes the
# backend reachable from outside — then stops the local backend, the admin panel and the
# sleep inhibitor. Postgres is left running; it holds the demo data.

cd "$(dirname "$0")/.."

echo "==> tunnels"
pkill -f "cloudflared tunnel --url" 2>/dev/null && echo "closed" || echo "none running"

echo "==> admin panel"
PID=$(lsof -ti:4173 -sTCP:LISTEN 2>/dev/null)
[[ -n "$PID" ]] && kill "$PID" && echo "stopped" || echo "not running"

echo "==> backend"
PID=$(lsof -ti:8080 -sTCP:LISTEN 2>/dev/null)
[[ -n "$PID" ]] && kill "$PID" && echo "stopped" || echo "not running"

echo "==> sleep inhibitor"
pkill -x caffeinate 2>/dev/null && echo "released" || echo "none running"

echo
echo "Postgres is still up (docker compose -f sadora-backend/docker-compose.yml down stops it, and keeps the data)."
