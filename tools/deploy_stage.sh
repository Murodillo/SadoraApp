#!/bin/zsh
#
# Deploys the staging stack to the internal server, through the jump host.
#
#   ./tools/deploy_stage.sh
#
# Ships the source, builds the API image on the server, and brings up Postgres, Redis,
# the API, the web front (admin panel + landing page) and two Cloudflare tunnels. Prints
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
# touch compose files, the Caddyfile or itself — a person applies those, here. It installs
# the gate, and when ~/.config/sadora/ci/stage_ci_ed25519.pub exists, binds that CI key to
# it: on the server the key can run sadora-ci and nothing else, and on the jump host it can
# open a connection to the server and nothing else.

set -e
cd "$(dirname "$0")/.."

# Where the server is lives outside the repository, which is public: deploy/stage/hosts.env
# (gitignored, see hosts.env.example) or the same variables in the environment.
[[ -f deploy/stage/hosts.env ]] && source deploy/stage/hosts.env
KEY=${SADORA_DEPLOY_KEY:-$HOME/.ssh/id_ed25519_sadora_deploy}
JUMP=${SADORA_JUMP:?set SADORA_JUMP in deploy/stage/hosts.env — see hosts.env.example}
HOST=${SADORA_HOST:?set SADORA_HOST in deploy/stage/hosts.env — see hosts.env.example}
DIR=${SADORA_DIR:-/opt/sadora}
OUT=build/stage
mkdir -p "$OUT"

SSH_OPTS=(
  -o BatchMode=yes
  -o IdentitiesOnly=yes
  -o ConnectTimeout=20
  -o ServerAliveInterval=30
  -i "$KEY"
  -o "ProxyCommand=ssh -o BatchMode=yes -o IdentitiesOnly=yes -i $KEY -W %h:%p $JUMP"
)
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

echo "==> admin panel"
npm --prefix admin run build >/dev/null

echo "==> source"
remote "mkdir -p $DIR"
# Only what the stack needs: no mobile modules, no build output, no secrets. The image's
# build stage compiles the server from scratch, and settings.gradle.kts includes the
# mobile modules only when their directories exist.
COPYFILE_DISABLE=1 tar -czf - \
  --exclude='./.git' --exclude='./.gradle' --exclude='./.kotlin' --exclude='./.idea' \
  --exclude='./build' --exclude='*/build' --exclude='*/node_modules' \
  --exclude='./androidApp' --exclude='./iosApp' --exclude='./shared' --exclude='./design' \
  --exclude='./.env' --exclude='./server/.env.prod' --exclude='./server/.env.stage' \
  --exclude='./deploy/stage/hosts.env' --exclude='./local.properties' --exclude='.DS_Store' \
  --exclude='./downloads' --exclude='./releases' --exclude='./backups' --exclude='./.release.env' \
  --exclude='*/coverage' \
  . | remote "tar -xzf - -C $DIR"

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
  bind_key jump "restrict,port-forwarding,permitopen=\"${HOST#*@}:22\",command=\"/bin/false\""
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
for i in {1..120}; do
  remote 'curl -sf http://127.0.0.1:8081/health/ready >/dev/null 2>&1 || wget -qO- http://127.0.0.1:8081/health/ready >/dev/null 2>&1' && break
  sleep 5
done
remote 'curl -sf http://127.0.0.1:8081/health/ready 2>/dev/null || wget -qO- http://127.0.0.1:8081/health/ready' \
  || { echo "API did not become ready:"; remote "cd $DIR && $COMPOSE logs --tail=40 api"; exit 1; }
echo

echo "==> tunnels"
url_of() {
  remote "docker logs sadora-$1-1 2>&1" | grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' | tail -1
}
for i in {1..40}; do
  APP=$(url_of tunnel-app || true)
  LANDING=$(url_of tunnel-landing || true)
  [[ -n $APP && -n $LANDING ]] && break
  sleep 3
done
[[ -n $APP && -n $LANDING ]] || { echo "Tunnels did not report a URL; see: docker logs sadora-tunnel-app-1"; exit 1; }

echo "==> CORS for the panel's origin"
# The panel and the API share an origin behind Caddy, but a browser still sends an Origin
# header on every POST, and the API accepts only the origins it has been told about —
# without this, login answers 403 in a browser while curl gets 200. The tunnel hostname
# is random, so it is written into the allowlist each time it is read, and only the API
# container is recreated: the tunnels, and therefore the URL, stay exactly as they are.
remote "cd $DIR && sed -i 's#^CORS_ALLOWED_ORIGINS=.*#CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:4173,$APP#' server/.env.stage"
remote "cd $DIR && $COMPOSE up -d api"
for i in {1..60}; do
  remote 'curl -sf http://127.0.0.1:8081/health/ready >/dev/null 2>&1 || wget -qO- http://127.0.0.1:8081/health/ready >/dev/null 2>&1' && break
  sleep 5
done

{
  echo "App API + admin: $APP"
  echo "Landing:         $LANDING"
} | tee "$OUT/urls.txt"
