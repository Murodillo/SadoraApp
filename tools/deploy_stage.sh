#!/bin/zsh
#
# Deploys the staging stack to the internal server, through the jump host.
#
#   ./tools/deploy_stage.sh
#
#   SADORA_DIRECT=1 ./tools/deploy_stage.sh   # from the server's own LAN, without the jump host
#
# Ships the source, builds the API image on the server, and brings up Postgres, Redis,
# the API and the three static sites (admin panel, doctors' panel, landing page), each on
# the port the gateway forwards its dev- hostname to (docker-compose.stage.yml). Prints
# the public URLs at the end and writes them to build/stage/urls.txt.
#
# Access is by key only. The script never asks for a password and never passes one:
# BatchMode makes a missing key fail loudly instead of falling back to a prompt.
#
# Re-running it is safe. The environment file is generated once on the server and kept
# — its database password is what the existing volume was initialised with, so
# regenerating it would lock the API out of its own data.
#
# This is also how infrastructure changes reach staging. CI (stage.yml) deploys the API
# image and the static files through /usr/local/bin/sadora-ci, and that script refuses to
# touch compose files, the nginx configs or itself — a person applies those, here. It installs
# the gate, and when ~/.config/sadora/ci/stage_ci_ed25519.pub exists, binds that CI key to
# it: on the server the key can run sadora-ci and nothing else, and on the jump host it can
# open a connection to the server and nothing else.

set -e
cd "$(dirname "$0")/.."

# Where the server is lives outside the repository, which is public:
# sadora-backend/deploy/stage/hosts.env (gitignored, see hosts.env.example) or the same
# variables in the environment.
HOSTS=sadora-backend/deploy/stage/hosts.env
[[ -f $HOSTS ]] && source $HOSTS
KEY=${SADORA_DEPLOY_KEY:-$HOME/.ssh/id_ed25519_sadora_deploy}
JUMP=${SADORA_JUMP:?set SADORA_JUMP in $HOSTS — see hosts.env.example}
HOST=${SADORA_HOST:?set SADORA_HOST in $HOSTS — see hosts.env.example}
DIR=${SADORA_DIR:-/opt/sadora}
OUT=build/stage
mkdir -p "$OUT"

SSH_OPTS=(
  -o BatchMode=yes
  -o IdentitiesOnly=yes
  -o ConnectTimeout=20
  -o ServerAliveInterval=30
  -i "$KEY"
)
# From inside the server's LAN (the office network or its VPN) the jump host's public
# address does not answer, and is not needed: SADORA_DIRECT=1 connects straight to it.
[[ -z $SADORA_DIRECT ]] && SSH_OPTS+=(-o "ProxyCommand=ssh -o BatchMode=yes -o IdentitiesOnly=yes -i $KEY -W %h:%p $JUMP")
remote() { ssh "${SSH_OPTS[@]}" "$HOST" "$@"; }

CI_PUB=${SADORA_CI_PUB:-$HOME/.config/sadora/ci/stage_ci_ed25519.pub}
# .release.env names the API image CI deployed last (see deploy/stage/sadora-ci).
COMPOSE="SADORA_ENV_FILE=server/.env.stage docker compose -p sadora -f docker-compose.prod.yml -f docker-compose.stage.yml --env-file server/.env.stage --env-file .release.env"

echo "==> access"
remote true || { echo "No key access to $HOST through $JUMP (key: $KEY)."; exit 1; }
remote 'echo "    $(hostname) · $(uname -m) · $(. /etc/os-release 2>/dev/null; echo $PRETTY_NAME)"'

echo "==> docker"
if ! remote 'command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1'; then
  echo "    not installed — installing from get.docker.com"
  remote 'curl -fsSL https://get.docker.com | sh'
fi
remote 'docker --version'

# The public hostnames — fixed, the same ones sadora-ci reports to CI.
API_URL=https://dev-api.sadora.app
ADMIN_URL=https://dev-admin.sadora.app
DOCTOR_URL=https://dev-doctor.sadora.app
LANDING_URL=https://dev.sadora.app

echo "==> admin panels"
# Each panel is a hostname of its own beside the API's, so it is built to call that one.
VITE_API_BASE=$API_URL npm --prefix sadora-backend/admin run build >/dev/null
VITE_API_BASE=$API_URL npm --prefix sadora-doctor-admin run build >/dev/null

echo "==> source"
remote "mkdir -p $DIR"
# Only what the stack needs: sadora-backend becomes the server's $DIR, and the landing
# page sits beside it as $DIR/landing — the layout the deploy gate and the compose files
# expect. No apps, no build output, no secrets; the image's build stage compiles the
# server from scratch.
COPYFILE_DISABLE=1 tar -czf - \
  --exclude='./.gradle' --exclude='./.kotlin' --exclude='./.idea' \
  --exclude='./build' --exclude='*/build' --exclude='*/node_modules' \
  --exclude='./server/.env.prod' --exclude='./server/.env.stage' \
  --exclude='./deploy/stage/hosts.env' --exclude='local.properties' --exclude='.DS_Store' \
  --exclude='./downloads' --exclude='./releases' --exclude='./backups' --exclude='./.release.env' \
  --exclude='*/coverage' \
  -C sadora-backend . -C .. landing | remote "tar -xzf - -C $DIR"
# The doctors' panel is its own project; only its build goes, as $DIR/doctor-admin/dist.
COPYFILE_DISABLE=1 tar -czf - -C sadora-doctor-admin dist \
  | remote "rm -rf $DIR/doctor-admin/dist && mkdir -p $DIR/doctor-admin && tar -xzf - -C $DIR/doctor-admin"

echo "==> environment"
if remote "test -f $DIR/server/.env.stage"; then
  echo "    kept the existing server/.env.stage"
else
  # The dev settings, minus the two values a machine on the internet must not share with
  # a public repository: the JWT secret, and the database password it has never had.
  remote "cd $DIR && umask 077 && {
    grep -vE '^(JWT_SECRET|GEMINI_API_KEY|POSTGRES_PASSWORD)=' server/.env.dev
    echo
    echo '# ---- staging, generated once on this server ----'
    echo JWT_SECRET=\$(head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n')
    echo POSTGRES_PASSWORD=\$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')
  } > server/.env.stage"
  # The model key goes over stdin, so it never appears in a remote command line.
  GEMINI=$(grep '^GEMINI_API_KEY=' .env 2>/dev/null | tail -1 | cut -d= -f2-)
  [[ -n $GEMINI ]] && printf 'GEMINI_API_KEY=%s\n' "$GEMINI" | remote "cat >> $DIR/server/.env.stage"
  echo "    generated server/.env.stage"
fi

echo "==> deploy gate"
remote "install -m 755 $DIR/deploy/stage/sadora-ci /usr/local/bin/sadora-ci && mkdir -p $DIR/downloads $DIR/releases && touch $DIR/.release.env"

if [[ -f $CI_PUB ]]; then
  echo "==> CI key ($(ssh-keygen -lf "$CI_PUB" | awk '{print $2}'))"
  # Idempotent: any earlier line with this key is replaced, every other key is left alone,
  # and the previous file is kept beside it.
  bind_key() { # <ssh target> <options>
    local target=$1 options=$2 body
    body=$(awk '{print $2}' "$CI_PUB")
    printf '%s %s\n' "$options" "$(cat "$CI_PUB")" | $target "umask 077 && mkdir -p ~/.ssh && touch ~/.ssh/authorized_keys \
      && cp ~/.ssh/authorized_keys ~/.ssh/authorized_keys.before-sadora-ci \
      && { grep -vF '$body' ~/.ssh/authorized_keys; cat; } > ~/.ssh/authorized_keys.new \
      && mv ~/.ssh/authorized_keys.new ~/.ssh/authorized_keys"
  }
  jump() { ssh "${SSH_OPTS[@]:0:10}" "$JUMP" "$@"; }
  if [[ -z $SADORA_DIRECT ]]; then
    bind_key jump "restrict,port-forwarding,permitopen=\"${HOST#*@}:22\",command=\"/bin/false\""
  else
    echo "    direct connection: the jump host's binding is left as it is"
  fi
  bind_key remote 'restrict,command="/usr/local/bin/sadora-ci"'
  echo "    bound: jump host → ${HOST#*@}:22 only; server → sadora-ci only"
fi

echo "==> stack (the first build compiles the server and takes a few minutes)"
if remote "grep -q '^SADORA_API_IMAGE=' $DIR/.release.env"; then
  # CI has deployed an image; a manual run applies infrastructure around it, and never
  # replaces it with one built from this laptop's checkout.
  echo "    keeping the API image CI deployed: $(remote "sed -n 's/^SADORA_API_IMAGE=//p' $DIR/.release.env")"
  remote "cd $DIR && $COMPOSE up -d --no-build --remove-orphans"
else
  remote "cd $DIR && $COMPOSE up -d --build --remove-orphans"
fi

echo "==> waiting for the API"
health() { remote 'curl -sf http://127.0.0.1:8083/health/ready 2>/dev/null || wget -qO- http://127.0.0.1:8083/health/ready 2>/dev/null'; }
for i in {1..120}; do
  health >/dev/null && break
  sleep 5
done
health || { echo "API did not become ready:"; remote "cd $DIR && $COMPOSE logs --tail=40 api"; exit 1; }
echo

# The panels are other origins than the API, and a browser sends an Origin header on
# every POST: the API accepts only the origins it has been told about — without them,
# sign-in answers 403 in a browser while curl gets 200. The hostnames are fixed, so this
# is written once and only changes here. The gateway also forwards the staging-
# hostnames to the same ports, so their panels are origins of their own.
echo "==> CORS for the panels' origins"
ALIASES=https://staging-admin.sadora.app,https://staging-doctor.sadora.app,https://staging.sadora.app
ORIGINS="CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:5174,$ADMIN_URL,$DOCTOR_URL,$LANDING_URL,$ALIASES"
if ! remote "grep -qxF '$ORIGINS' $DIR/server/.env.stage"; then
  remote "cd $DIR && sed -i '/^CORS_EXTRA_ORIGINS=/d; /^CORS_ALLOWED_ORIGINS=/d' server/.env.stage && echo '$ORIGINS' >> server/.env.stage"
  remote "cd $DIR && $COMPOSE up -d api"
  for i in {1..60}; do health >/dev/null && break; sleep 5; done
fi
echo "    $ORIGINS"

{
  echo "API:             $API_URL"
  echo "Admin panel:     $ADMIN_URL"
  echo "Doctors' panel:  $DOCTOR_URL"
  echo "Landing:         $LANDING_URL"
} | tee "$OUT/urls.txt"
