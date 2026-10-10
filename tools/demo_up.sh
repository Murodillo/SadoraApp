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
SADORA_DB_PORT=$(grep -E '^SADORA_DB_PORT=' sadora-backend/server/.env.dev | cut -d= -f2)
export SADORA_DB_PORT=${SADORA_DB_PORT:-5432}
docker compose -f sadora-backend/docker-compose.yml up -d >/dev/null

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
#
# The tunnel puts this server on the internet, so it does not run as DEV: DEV signs
# tokens with the key committed in .env.dev, and anyone holding the URL could mint an
# Owner token. STAGE refuses that key, so the demo gets its own, kept between runs so
# the phones stay signed in. The fixed code 123456 stays — the demo accounts need it —
# and so does the instant payment page; 2FA is not forced on the owner, whose password
# is made private below instead.
JWT_FILE=$RUN/jwt_secret
[[ -s $JWT_FILE ]] || openssl rand -hex 32 > "$JWT_FILE"
SADORA_ENV=STAGE JWT_SECRET=$(cat "$JWT_FILE") BILLING_DEV_PAY=true STORE_ALLOW_TEST_PURCHASES=true \
  ADMIN_REQUIRE_TOTP=false CORS_EXTRA="$ADMIN,$API" \
  ./tools/server_run.sh dev > "$RUN/server.log" 2>&1 &

for i in {1..90}; do
  curl -sf localhost:8080/health/ready >/dev/null && break
  sleep 2
done
curl -sf localhost:8080/health/ready >/dev/null || { echo "backend did not start; see $RUN/server.log"; exit 1; }

echo "==> admin password"
# The owner's password on a laptop is changeme123, which sits in this repository. Once
# the tunnel is up that is a public Owner login, so it is replaced by a random one,
# saved where seed_demo.py and api_audit.py read it.
PASS_FILE=$RUN/admin_password
OWNER=owner@sadora.uz
TOKEN=$(curl -s localhost:8080/v1/admin/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$OWNER\",\"password\":\"changeme123\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
if [[ -n $TOKEN ]]; then
  NEW="Demo$(openssl rand -hex 8)"
  curl -sf localhost:8080/v1/admin/me/password -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
    -d "{\"currentPassword\":\"changeme123\",\"newPassword\":\"$NEW\"}" >/dev/null \
    || { echo "could not replace the published owner password; stopping"; pkill -f "cloudflared tunnel --url"; exit 1; }
  echo "$NEW" > "$PASS_FILE"
  chmod 600 "$PASS_FILE"
fi

echo "==> admin panel"
OLD=$(lsof -ti:4173 -sTCP:LISTEN 2>/dev/null || true)
[[ -n "$OLD" ]] && kill "$OLD" 2>/dev/null && sleep 1
npm --prefix sadora-backend/admin run build >/dev/null
(cd sadora-backend/admin && npx vite preview --port 4173 > "$RUN/admin.log" 2>&1 &)

echo "==> APK against $API"
(cd sadora-client && ./gradlew :androidApp:assembleDebug -Psadora.devHost="$API" --console=plain) > "$RUN/apk.log" 2>&1
cp sadora-client/androidApp/build/outputs/apk/debug/androidApp-debug.apk "$RUN/sadora-online.apk"

# The Mac must not sleep, or both tunnels drop and the client sees a dead link.
pgrep -x caffeinate >/dev/null || (caffeinate -dimsu >/dev/null 2>&1 &)

{
  echo "Admin panel: $ADMIN"
  echo "Admin login: owner@sadora.uz / $(cat "$PASS_FILE" 2>/dev/null || echo '(your own password)')"
  echo "API:         $API"
  echo "APK:         $RUN/sadora-online.apk"
} | tee "$RUN/urls.txt"
