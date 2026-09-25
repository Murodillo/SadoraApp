#!/bin/zsh
#
# Runs the backend in one of the two environments.
#
#   ./tools/server_run.sh dev      # gradle, reloads on restart, talks to docker-compose
#   ./tools/server_run.sh prod     # the built distribution, against the real database
#
# The settings come from `server/.env.dev` or `server/.env.prod` — this script's only job
# is to load one of them, so there is exactly one place per environment where a value
# lives and no environment can be started with another's settings by mistake.

set -e
cd "$(dirname "$0")/.."

ENV_NAME=${1:-dev}
FILE="server/.env.$ENV_NAME"

if [[ ! -f $FILE ]]; then
  echo "No $FILE."
  [[ $ENV_NAME == prod ]] && echo "Copy server/.env.prod.example to it and fill in the secrets."
  exit 1
fi

# `set -a` exports everything the file assigns, which is what AppConfig reads. Comments
# and blank lines are ordinary shell, and ${SADORA_DB_PORT} expands as written.
set -a
source "$FILE"
set +a

# The Gemini key is deliberately absent from the committed dev file; it lives in the
# untracked .env at the repository root. Production sets it in its own env file.
if [[ -z $GEMINI_API_KEY && -f .env ]]; then
  export GEMINI_API_KEY=$(grep '^GEMINI_API_KEY=' .env | tail -1 | cut -d= -f2-)
fi

# Push the same way: the service account key sits in server/secrets/ (gitignored) and the
# untracked .env names it, so a clone without the key still boots and only logs. The
# WHOOP and Oura secrets and the token key live there too; without them a provider shows as not set up.
for name in FCM_PROJECT_ID FCM_SERVICE_ACCOUNT_FILE GOOGLE_PLAY_SERVICE_ACCOUNT_FILE \
    WHOOP_CLIENT_SECRET OURA_CLIENT_SECRET WEARABLE_TOKEN_KEY; do
  if [[ -z ${(P)name} && -f .env ]]; then
    export $name="$(grep "^$name=" .env | tail -1 | cut -d= -f2-)"
  fi
done

# A caller that publishes the panel under a hostname the env file cannot know — a
# tunnel, a preview deployment — adds its origin here instead of editing the file.
if [[ -n $CORS_EXTRA ]]; then
  export CORS_ALLOWED_ORIGINS="$CORS_ALLOWED_ORIGINS,$CORS_EXTRA"
fi

echo "SADORA_ENV=$SADORA_ENV  db=${DB_URL##*/}  port=$PORT"

if [[ $ENV_NAME == prod ]]; then
  # A production start must not depend on a Gradle daemon or a source tree being
  # present, so it runs the distribution the build produced.
  [[ -x server/build/install/server/bin/server ]] || ./gradlew :server:installDist
  exec server/build/install/server/bin/server
else
  exec ./gradlew :server:run --console=plain
fi
