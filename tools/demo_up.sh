#!/bin/zsh
#
# Brings the whole demo up on this Mac and publishes it to the internet.
#
#   ./tools/demo_up.sh
#
# Starts Postgres/Redis, the backend, the built admin panel, and two Cloudflare quick
# tunnels — one for the API the phone talks to, one for the admin panel the client opens
# in a browser. Prints both URLs at the end and writes them to build/demo/urls.txt.
#
# Quick tunnels get a NEW random hostname every time they start. The APK carries its
# backend URL inside it, so whenever this script prints a new API URL the APK has to be
# rebuilt against it — the script does that itself, into build/demo/sadora-online.apk.
#
# Stop everything with ./tools/demo_down.sh.

set -e
cd "$(dirname "$0")/.."
ROOT=$(pwd)
RUN=$ROOT/build/demo
mkdir -p "$RUN"

echo "==> Postgres and Redis"
# docker-compose reads SADORA_DB_PORT for the published port, and it lives in the
# environment file rather than the shell. Without it compose falls back to 5432, which
# on this machine belongs to another project — and the container is recreated bound to
# a port it cannot have, leaving the demo with no database.
SADORA_DB_PORT=$(grep -E '^SADORA_DB_PORT=' server/.env.dev | cut -d= -f2)
export SADORA_DB_PORT=${SADORA_DB_PORT:-5432}
docker compose up -d >/dev/null

echo "==> tunnels"
pkill -f "cloudflared tunnel --url" 2>/dev/null || true
sleep 1
cloudflared tunnel --url http://localhost:8080 --no-autoupdate > "$RUN/tunnel-api.log" 2>&1 &
cloudflared tunnel --url http://localhost:4173 --no-autoupdate > "$RUN/tunnel-admin.log" 2>&1 &

# The URL is only printed once the tunnel is registered, a few seconds in.
for i in {1..60}; do
  API=$(grep -o "https://[a-z0-9-]*\.trycloudflare\.com" "$RUN/tunnel-api.log" 2>/dev/null | head -1)
  ADMIN=$(grep -o "https://[a-z0-9-]*\.trycloudflare\.com" "$RUN/tunnel-admin.log" 2>/dev/null | head -1)
  [[ -n "$API" && -n "$ADMIN" ]] && break
  sleep 2
done
[[ -n "$API" && -n "$ADMIN" ]] || { echo "tunnels did not come up; see $RUN/tunnel-*.log"; exit 1; }

echo "==> backend"
# The old one holds port 8080 and would not pick up the new CORS origins.
OLD=$(lsof -ti:8080 -sTCP:LISTEN 2>/dev/null || true)
[[ -n "$OLD" ]] && kill "$OLD" 2>/dev/null && sleep 3
# Everything but the tunnel origins comes from the dev environment file; the tunnel
# hostnames are only known a moment ago, so they are appended rather than written there.
CORS_EXTRA="$ADMIN,$API" ./tools/server_run.sh dev > "$RUN/server.log" 2>&1 &

for i in {1..90}; do
  curl -sf localhost:8080/health/ready >/dev/null && break
  sleep 2
done
curl -sf localhost:8080/health/ready >/dev/null || { echo "backend did not start; see $RUN/server.log"; exit 1; }

echo "==> admin panel"
OLD=$(lsof -ti:4173 -sTCP:LISTEN 2>/dev/null || true)
[[ -n "$OLD" ]] && kill "$OLD" 2>/dev/null && sleep 1
npm --prefix admin run build >/dev/null
(cd admin && npx vite preview --port 4173 > "$RUN/admin.log" 2>&1 &)

echo "==> APK against $API"
./gradlew :androidApp:assembleDebug -Psadora.devHost="$API" --console=plain > "$RUN/apk.log" 2>&1
cp androidApp/build/outputs/apk/debug/androidApp-debug.apk "$RUN/sadora-online.apk"

# The Mac must not sleep, or both tunnels drop and the client sees a dead link.
pgrep -x caffeinate >/dev/null || (caffeinate -dimsu >/dev/null 2>&1 &)

{
  echo "Admin panel: $ADMIN"
  echo "API:         $API"
  echo "APK:         $RUN/sadora-online.apk"
} | tee "$RUN/urls.txt"
