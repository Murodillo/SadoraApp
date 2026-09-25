#!/usr/bin/env bash
#
# Gives GitHub Actions what it needs to deliver to staging. Run once, by a person:
#
#   ./tools/ci_secrets.sh
#
# Stores five secrets in the repository's `staging` environment, read from files on this
# machine — nothing is typed, and nothing is printed:
#
#   STAGE_SSH_KEY           ~/.config/sadora/ci/stage_ci_ed25519   the CI key; on the server it
#                                                                   can run sadora-ci and nothing else
#   STAGE_SSH_KNOWN_HOSTS   ~/.config/sadora/ci/known_hosts         pinned host keys of both machines
#   STAGE_JUMP, STAGE_HOST  sadora-backend/deploy/stage/hosts.env   where the server is
#   STAGE_KEYSTORE_B64      ~/.android/debug.keystore               the key staging APKs are signed
#                                                                   with — this machine's, so a phone
#                                                                   with a locally built APK updates in place
#
# And, when an App Store Connect team key is on this machine, three more for TestFlight:
#
#   ASC_KEY_ID                  from the key's file name, AuthKey_<id>.p8
#   ASC_ISSUER_ID               given in the shell: ASC_ISSUER_ID=… ./tools/ci_secrets.sh
#   ASC_KEY_P8                  ~/.appstoreconnect/private_keys/AuthKey_<id>.p8
#
# Re-running replaces them. Rotating the CI key: generate a new pair at the same path, run
# tools/deploy_stage.sh (it rebinds the key on both hosts), then run this again.
set -euo pipefail
cd "$(dirname "$0")/.."

CI_DIR=${SADORA_CI_DIR:-$HOME/.config/sadora/ci}
KEYSTORE=${SADORA_STAGE_KEYSTORE:-$HOME/.android/debug.keystore}
ENV_NAME=staging

need() { [[ -s $1 ]] || { echo "missing: $1 — $2" >&2; exit 1; }; }
need "$CI_DIR/stage_ci_ed25519" "generate it with: ssh-keygen -t ed25519 -N '' -C sadora-ci@github-actions -f $CI_DIR/stage_ci_ed25519"
need "$CI_DIR/known_hosts" "pin the host keys first (README → CI/CD)"
HOSTS=sadora-backend/deploy/stage/hosts.env
need $HOSTS "copy $HOSTS.example and fill it in"
need "$KEYSTORE" "build the app once with (cd sadora-client && ./gradlew :androidApp:assembleDebug) to create it"
# shellcheck source=/dev/null
source $HOSTS
: "${SADORA_JUMP:?SADORA_JUMP is not set in $HOSTS}" "${SADORA_HOST:?SADORA_HOST is not set in $HOSTS}"

REPO=$(gh repo view --json nameWithOwner --jq .nameWithOwner)
echo "==> $REPO · environment '$ENV_NAME'"
gh api --silent -X PUT "repos/$REPO/environments/$ENV_NAME"

set_secret() { gh secret set "$1" --env "$ENV_NAME" --repo "$REPO" > /dev/null && echo "    $1"; }
set_secret STAGE_SSH_KEY < "$CI_DIR/stage_ci_ed25519"
set_secret STAGE_SSH_KNOWN_HOSTS < "$CI_DIR/known_hosts"
printf '%s' "$SADORA_JUMP" | set_secret STAGE_JUMP
printf '%s' "$SADORA_HOST" | set_secret STAGE_HOST
base64 < "$KEYSTORE" | tr -d '\n' | set_secret STAGE_KEYSTORE_B64
ASC_KEY=$(ls "$HOME"/.appstoreconnect/private_keys/AuthKey_*.p8 2>/dev/null | head -1 || true)
if [[ -s $ASC_KEY && -n ${ASC_ISSUER_ID:-} ]]; then
  ASC_KEY_ID=$(basename "$ASC_KEY" .p8); ASC_KEY_ID=${ASC_KEY_ID#AuthKey_}
  printf '%s' "$ASC_KEY_ID" | set_secret ASC_KEY_ID
  printf '%s' "$ASC_ISSUER_ID" | set_secret ASC_ISSUER_ID
  set_secret ASC_KEY_P8 < "$ASC_KEY"
else
  echo "    (no App Store Connect key, or no ASC_ISSUER_ID in the shell — TestFlight from CI stays off)"
fi

echo "==> done. Re-run the latest CI run of an open pull request into dev to deliver it:"
echo "    gh run rerun \$(gh run list --workflow CI --limit 1 --json databaseId --jq '.[0].databaseId')"
