#!/bin/zsh
#
# Deploys the production stack to its own server (SADORA_PROD_HOST); staging is another one.
#
#   ./tools/deploy_prod.sh
#   SADORA_DIRECT=1 ./tools/deploy_prod.sh   # from the server's own LAN, without the jump host
#
# Production is a compose project of its own (`sadora-prod`, in /opt/sadora-prod): its own
# Postgres volume, its own secrets, its own containers, on the ports the gateway forwards
# the production hostnames to (docker-compose.prod.yml):
#
#   doctor.sadora.app 8090 · sadora.app 8091 · admin.sadora.app 8092 · api.sadora.app 8093
#
# It runs the API image staging runs. CI delivers to staging and checks it there; what
# reaches production is a person running this script once staging looks right.
#
# The environment file is generated once on the server and kept — its database password
# is what the volume was initialised with.
#
# Until the release it runs as SADORA_ENV=STAGE with the fixed sign-in code 123456: no
# SMS is sent, and payments stay in test mode. At the release a person sets
# SADORA_ENV=PROD, removes OTP_FIXED_CODE and fills in the Eskiz account on the server —
# production refuses to boot without a way to send codes, so this script then waits for
# that account before it starts the API.

set -e
cd "$(dirname "$0")/.."

HOSTS=sadora-backend/deploy/stage/hosts.env
[[ -f $HOSTS ]] && source $HOSTS
KEY=${SADORA_DEPLOY_KEY:-$HOME/.ssh/id_ed25519_sadora_deploy}
JUMP=${SADORA_JUMP:?set SADORA_JUMP in $HOSTS — see hosts.env.example}
HOST=${SADORA_PROD_HOST:?set SADORA_PROD_HOST in $HOSTS — see hosts.env.example}
STAGE_HOST=${SADORA_HOST:?set SADORA_HOST in $HOSTS — see hosts.env.example}
DIR=${SADORA_PROD_DIR:-/opt/sadora-prod}
STAGE_DIR=${SADORA_DIR:-/opt/sadora}
OUT=build/prod
mkdir -p "$OUT"

SSH_OPTS=(
  -o BatchMode=yes
  -o IdentitiesOnly=yes
  -o ConnectTimeout=20
  -o ServerAliveInterval=30
  -i "$KEY"
)
[[ -z $SADORA_DIRECT ]] && SSH_OPTS+=(-o "ProxyCommand=ssh -o BatchMode=yes -o IdentitiesOnly=yes -i $KEY -W %h:%p $JUMP")
remote() { ssh "${SSH_OPTS[@]}" "$HOST" "$@"; }
stage() { ssh "${SSH_OPTS[@]}" "$STAGE_HOST" "$@"; }

COMPOSE="SADORA_ENV_FILE=server/.env.prod docker compose -p sadora-prod -f docker-compose.prod.yml --env-file server/.env.prod --env-file .release.env"

API_URL=https://api.sadora.app
ADMIN_URL=https://admin.sadora.app
DOCTOR_URL=https://doctor.sadora.app
LANDING_URL=https://sadora.app

echo "==> access"
remote true || { echo "No key access to $HOST (key: $KEY)."; exit 1; }

echo "==> API image (the one staging runs)"
RELEASE=$(stage "cat $STAGE_DIR/.release.env")
IMAGE=$(sed -n 's/^SADORA_API_IMAGE=//p' <<<"$RELEASE")
SHA=$(sed -n 's/^SADORA_RELEASE=//p' <<<"$RELEASE")
[[ -n $IMAGE && -n $SHA ]] || { echo "Staging has no CI release to promote."; exit 1; }
# Staging is another machine, so production pulls the same digest from GHCR.
remote "docker image inspect '$IMAGE' >/dev/null 2>&1 || docker pull -q '$IMAGE'" >/dev/null \
  || { echo "Could not pull $IMAGE on $HOST."; exit 1; }
echo "    ${SHA:0:7} $IMAGE"

echo "==> panels"
# Each panel is a hostname of its own beside the API's, so it is built to call that one.
VITE_API_BASE=$API_URL npm --prefix sadora-backend/admin run build >/dev/null
VITE_API_BASE=$API_URL npm --prefix sadora-doctor-admin run build >/dev/null

echo "==> source"
remote "mkdir -p $DIR"
# The same selection as staging: sadora-backend becomes $DIR, the landing page sits
# beside it. No apps, no build output, no environment files.
COPYFILE_DISABLE=1 tar -czf - \
  --exclude='./.gradle' --exclude='./.kotlin' --exclude='./.idea' \
  --exclude='./build' --exclude='*/build' --exclude='*/node_modules' \
  --exclude='./server/.env.prod' --exclude='./server/.env.stage' --exclude='./server/.env.dev' \
  --exclude='./deploy/stage/hosts.env' --exclude='local.properties' --exclude='.DS_Store' \
  --exclude='./downloads' --exclude='./releases' --exclude='./backups' --exclude='./.release.env' \
  --exclude='*/coverage' \
  -C sadora-backend . -C .. landing | remote "tar -xzf - -C $DIR"
COPYFILE_DISABLE=1 tar -czf - -C sadora-doctor-admin dist \
  | remote "rm -rf $DIR/doctor-admin/dist && mkdir -p $DIR/doctor-admin && tar -xzf - -C $DIR/doctor-admin"
remote "mkdir -p $DIR/downloads $DIR/backups $DIR/server/secrets && chmod 700 $DIR/backups"
printf 'SADORA_API_IMAGE=%s\nSADORA_RELEASE=%s\n' "$IMAGE" "$SHA" | remote "cat > $DIR/.release.env"

echo "==> environment"
if remote "test -f $DIR/server/.env.prod"; then
  echo "    kept the existing server/.env.prod"
else
  # The example, minus what compose supplies (the database and Redis addresses) and
  # what is generated here. Secrets are made on the server and never leave it.
  remote "cd $DIR && umask 077 && {
    grep -vE '^(SADORA_ENV|DB_URL|DB_USER|DB_PASSWORD|REDIS_URL|JWT_SECRET|CORS_ALLOWED_ORIGINS|PUBLIC_BASE_URL|ADMIN_BOOTSTRAP_EMAIL|ADMIN_BOOTSTRAP_PASSWORD|GEMINI_API_KEY)=' server/.env.prod.example
    echo
    echo '# ---- production, generated once on this server ----'
    echo '# Until the release: no SMS, every sign-in code is 123456. At the release set'
    echo '# SADORA_ENV=PROD, delete OTP_FIXED_CODE and fill in ESKIZ_EMAIL / ESKIZ_PASSWORD.'
    echo SADORA_ENV=STAGE
    echo OTP_FIXED_CODE=123456
    echo CORS_ALLOWED_ORIGINS=$ADMIN_URL,$DOCTOR_URL,$LANDING_URL
    echo PUBLIC_BASE_URL=$API_URL
    echo JWT_SECRET=\$(head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n')
    echo POSTGRES_PASSWORD=\$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')
    echo '# The first owner account, made only while no admin exists. Sign in, then change it.'
    echo ADMIN_BOOTSTRAP_EMAIL=owner@sadora.app
    echo ADMIN_BOOTSTRAP_PASSWORD=\$(head -c 12 /dev/urandom | od -An -tx1 | tr -d ' \n')
  } > server/.env.prod"
  # The model key goes over stdin, so it never appears in a remote command line.
  GEMINI=$(grep '^GEMINI_API_KEY=' .env 2>/dev/null | tail -1 | cut -d= -f2-)
  [[ -n $GEMINI ]] && printf 'GEMINI_API_KEY=%s\n' "$GEMINI" | remote "cat >> $DIR/server/.env.prod"
  echo "    generated server/.env.prod"
fi

echo "==> backup"
if remote "docker ps --format '{{.Names}}' | grep -qx sadora-prod-postgres-1"; then
  # Flyway only moves forward; this dump is what undoes a migration.
  remote "cd $DIR && docker exec sadora-prod-postgres-1 sh -c 'pg_dump -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\"' | gzip > backups/\$(date -u +%Y%m%dT%H%M%SZ)-${SHA:0:7}.sql.gz \
    && ls -1t backups/*.sql.gz | tail -n +31 | xargs -r rm -f"
  echo "    done"
else
  echo "    no database yet"
fi

echo "==> stack"
if ! remote "grep -qx 'SADORA_ENV=PROD' $DIR/server/.env.prod" \
  || remote "grep -qE '^ESKIZ_EMAIL=.+' $DIR/server/.env.prod && grep -qE '^ESKIZ_PASSWORD=.+' $DIR/server/.env.prod"; then
  remote "cd $DIR && $COMPOSE up -d --no-build --remove-orphans"
  echo "==> waiting for the API"
  health() { remote 'curl -sf http://127.0.0.1:8093/health/ready'; }
  for i in {1..60}; do health >/dev/null 2>&1 && break; sleep 5; done
  health || { echo "API did not become ready:"; remote "cd $DIR && $COMPOSE logs --tail=40 api"; exit 1; }
  echo
else
  remote "cd $DIR && $COMPOSE up -d --no-build --remove-orphans postgres redis landing admin doctor-admin"
  echo
  echo "    The API is not started: SADORA_ENV=PROD, but ESKIZ_EMAIL and ESKIZ_PASSWORD are blank in"
  echo "    $DIR/server/.env.prod, and production refuses to boot without a way to send"
  echo "    sign-in codes. Fill them in on the server (and OTP_SMS_TEXT, if Eskiz approved"
  echo "    a different wording), then run this script again."
fi

{
  echo "API:             $API_URL"
  echo "Admin panel:     $ADMIN_URL"
  echo "Doctors' panel:  $DOCTOR_URL"
  echo "Landing:         $LANDING_URL"
} | tee "$OUT/urls.txt"
