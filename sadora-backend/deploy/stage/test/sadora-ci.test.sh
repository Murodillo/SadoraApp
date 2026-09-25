#!/usr/bin/env bash
#
# Tests for deploy/stage/sadora-ci — the script that decides what the CI key may do.
#
#   bash deploy/stage/test/sadora-ci.test.sh
#
# Needs GNU userland (it is what the server has), so on a Mac run it in a container:
#   docker run --rm -v "$PWD":/src -w /src ubuntu:24.04 bash deploy/stage/test/sadora-ci.test.sh
#
# Docker and curl are replaced by fakes that keep their state in files, so every path —
# including a deploy that never becomes healthy and has to roll back — runs in seconds.

set -uo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
GATE=$HERE/../sadora-ci
PASS=0 FAIL=0
SHA1=1111111111111111111111111111111111111111
SHA2=2222222222222222222222222222222222222222
SHA3=3333333333333333333333333333333333333333
IMG1=ghcr.io/murodillo/sadora-api@sha256:$(printf 'a%.0s' {1..64})
IMG2=ghcr.io/murodillo/sadora-api@sha256:$(printf 'b%.0s' {1..64})
IMG3=ghcr.io/murodillo/sadora-api@sha256:$(printf 'c%.0s' {1..64})

setup() {
  T=$(mktemp -d)
  export SADORA_ROOT=$T/root SADORA_LOCK=$T/lock SADORA_GATE_PATH=$T/installed-gate
  export SADORA_HEALTH_TIMEOUT=2 SADORA_KEEP=2 FAKE=$T/fake HOME=$T/home
  mkdir -p "$SADORA_ROOT/server" "$SADORA_ROOT/deploy/stage" "$FAKE" "$HOME" "$T/bin"
  printf 'JWT_SECRET=x\nCORS_ALLOWED_ORIGINS=http://localhost:5173\n' > "$SADORA_ROOT/server/.env.stage"
  echo compose > "$SADORA_ROOT/docker-compose.prod.yml"
  echo compose > "$SADORA_ROOT/docker-compose.stage.yml"
  echo caddy > "$SADORA_ROOT/deploy/stage/Caddyfile"
  cp "$GATE" "$SADORA_GATE_PATH"
  : > "$FAKE/images"; : > "$FAKE/unhealthy"; : > "$FAKE/calls"

  cat > "$T/bin/docker" <<'FAKEDOCKER'
#!/usr/bin/env bash
echo "docker $*" >> "$FAKE/calls"
case "$1 ${2:-}" in
  "logs sadora-tunnel-app-1") echo "INF |  https://app-one.trycloudflare.com  |" ;;
  "logs sadora-tunnel-landing-1") echo "INF |  https://landing-one.trycloudflare.com  |" ;;
  "image inspect") grep -qxF "$3" "$FAKE/images" ;;
  "image rm") echo "$3" >> "$FAKE/removed" ;;
  "login ghcr.io") cat > "$FAKE/login-token"; echo "$DOCKER_CONFIG" > "$FAKE/login-config"; [[ ! -e $FAKE/login-fails ]] ;;
  "pull -q") echo "$3" >> "$FAKE/images" ;;
  "ps --format") echo sadora-postgres-1 ;;
  "exec sadora-postgres-1") echo "-- dump" ;;
  "restart sadora-web-1") : ;;
  compose*) if [[ " $* " == *" up "* ]]; then sed -n 's/^SADORA_RELEASE=//p' "$SADORA_ROOT/.release.env" > "$FAKE/running"; fi ;;
esac
FAKEDOCKER
  cat > "$T/bin/curl" <<'FAKECURL'
#!/usr/bin/env bash
running=$(cat "$FAKE/running" 2>/dev/null)
grep -qxF "$running" "$FAKE/unhealthy" && exit 22
printf '{"status":"ready","environment":"dev","version":"0.1.0","release":"%s"}' "$running"
FAKECURL
  printf '#!/bin/sh\n:\n' > "$T/bin/sleep"
  chmod +x "$T/bin/"*
  export PATH=$T/bin:$PATH
}
teardown() { rm -rf "$T"; }

# A release bundle as CI builds it; extra arguments are `name=content` files to add.
bundle() {
  local dir=$T/bundle-src
  rm -rf "$dir"; mkdir -p "$dir/admin/dist" "$dir/landing" "$dir/infra"
  echo "admin $1" > "$dir/admin/dist/index.html"
  echo "landing $1" > "$dir/landing/index.html"
  echo caddy > "$dir/infra/Caddyfile"
  echo "$1" > "$dir/RELEASE"
  shift
  local kv; for kv in "$@"; do mkdir -p "$dir/$(dirname "${kv%%=*}")"; echo "${kv#*=}" > "$dir/${kv%%=*}"; done
  tar -czf "$T/bundle.tgz" -C "$dir" .
}

# Runs the gate the way sshd does: arguments only through SSH_ORIGINAL_COMMAND.
gate() { OUT=$(SSH_ORIGINAL_COMMAND="$1" bash "$SADORA_GATE_PATH" 2>&1 < "${2:-/dev/null}"); STATUS=$?; }
deploy() { # <sha> <image> [token]
  { echo "${3:-tok-123}"; cat "$T/bundle.tgz"; } > "$T/stdin"
  gate "deploy $1 $2" "$T/stdin"
}

ok()   { PASS=$((PASS + 1)); printf 'ok   %s\n' "$CURRENT"; }
fail() { FAIL=$((FAIL + 1)); printf 'FAIL %s — %s\n     | %s\n' "$CURRENT" "$1" "${OUT//$'\n'/$'\n     | '}"; }
expect_status() { [[ $STATUS == "$1" ]] || { fail "exit $STATUS, expected $1"; return 1; }; }
expect_out()    { [[ $OUT == *"$1"* ]] || { fail "output lacks '$1'"; return 1; }; }
expect_file()   { grep -qF -- "$2" "$1" 2>/dev/null || { fail "$1 lacks '$2'"; return 1; }; }
test_case() { CURRENT=$1; setup; "$2" && ok; teardown; }

# ---------------------------------------------------------------------------------------

t_refuses_a_shell() {
  gate "bash -i"; expect_status 1 && expect_out usage || return 1
  gate "url;id"; expect_status 1 && expect_out usage || return 1
  gate ""; expect_status 1
}

t_url_is_json() {
  gate url; expect_status 0 && expect_out '{"app":"https://app-one.trycloudflare.com","landing":"https://landing-one.trycloudflare.com"}'
}

t_deploy_validates_arguments() {
  bundle "$SHA1"
  deploy abc123 "$IMG1"; expect_status 1 && expect_out "40-character" || return 1
  deploy "$SHA1" ghcr.io/murodillo/sadora-api:latest; expect_status 1 && expect_out "@sha256" || return 1
  deploy "$SHA1" "docker.io/evil/api@sha256:$(printf 'a%.0s' {1..64})"; expect_status 1
}

t_deploy_rejects_path_traversal() {
  bundle "$SHA1"
  mkdir -p "$T/evil"; echo x > "$T/evil/passwd"
  tar -czPf "$T/bundle.tgz" -C "$T/bundle-src" . --transform 's#^\./landing/index.html#../../etc/cron.d/x#'
  deploy "$SHA1" "$IMG1"; expect_status 1 && expect_out "unsafe path" || return 1
  [[ ! -e $SADORA_ROOT/.release.env ]] || { fail "activated anyway"; return 1; }
}

t_deploy_rejects_links() {
  bundle "$SHA1"; ln -s /etc "$T/bundle-src/landing/etc"
  tar -czf "$T/bundle.tgz" -C "$T/bundle-src" .
  deploy "$SHA1" "$IMG1"; expect_status 1 && expect_out "links"
}

t_deploy_rejects_unexpected_paths() {
  bundle "$SHA1" "server/.env.stage=JWT_SECRET=stolen"
  deploy "$SHA1" "$IMG1"; expect_status 1 && expect_out "unexpected path" || return 1
  expect_file "$SADORA_ROOT/server/.env.stage" "JWT_SECRET=x"
}

t_deploy_happy_path() {
  bundle "$SHA1"
  deploy "$SHA1" "$IMG1" tok-secret-9
  expect_status 0 && expect_out "is live" || return 1
  expect_file "$SADORA_ROOT/.release.env" "SADORA_API_IMAGE=$IMG1" || return 1
  expect_file "$SADORA_ROOT/.release.env" "SADORA_RELEASE=$SHA1" || return 1
  expect_file "$SADORA_ROOT/admin/dist/index.html" "admin $SHA1" || return 1
  expect_file "$SADORA_ROOT/landing/index.html" "landing $SHA1" || return 1
  expect_file "$SADORA_ROOT/releases/HISTORY" "$SHA1 $IMG1 ok" || return 1
  expect_file "$SADORA_ROOT/server/.env.stage" "CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:4173,https://app-one.trycloudflare.com" || return 1
  expect_file "$FAKE/calls" "up -d --no-build --force-recreate api web" || return 1
  ls "$SADORA_ROOT"/backups/*-1111111.sql.gz >/dev/null 2>&1 || { fail "no database backup"; return 1; }
  # The token reached docker login, and nothing kept it.
  expect_file "$FAKE/login-token" "tok-secret-9" || return 1
  [[ ! -e $(cat "$FAKE/login-config") ]] || { fail "the temporary docker config survived"; return 1; }
  if grep -rqF tok-secret-9 "$SADORA_ROOT" "$HOME"; then fail "the token was written to disk"; return 1; fi
}

t_deploy_skips_pull_and_cors_when_unchanged() {
  bundle "$SHA1"; deploy "$SHA1" "$IMG1"; expect_status 0 || return 1
  echo "$IMG2" >> "$FAKE/images"; : > "$FAKE/calls"
  bundle "$SHA2"; deploy "$SHA2" "$IMG2"; expect_status 0 || return 1
  if grep -q "login\|pull" "$FAKE/calls"; then fail "pulled an image that was already there"; return 1; fi
  if grep -q "force-recreate" "$FAKE/calls"; then fail "recreated the API for an unchanged CORS line"; return 1; fi
}

t_deploy_reports_infrastructure_drift() {
  bundle "$SHA1"; echo "caddy v2" > "$T/bundle-src/infra/Caddyfile"
  tar -czf "$T/bundle.tgz" -C "$T/bundle-src" .
  deploy "$SHA1" "$IMG1"; expect_status 0 && expect_out "::warning::deploy/stage/Caddyfile differs" || return 1
  expect_file "$SADORA_ROOT/deploy/stage/Caddyfile" "caddy"
}

t_unhealthy_deploy_rolls_back() {
  bundle "$SHA1"; deploy "$SHA1" "$IMG1"; expect_status 0 || return 1
  echo "$SHA2" > "$FAKE/unhealthy"
  bundle "$SHA2"; deploy "$SHA2" "$IMG2"
  expect_status 1 && expect_out "rolling back to 1111111" || return 1
  expect_file "$SADORA_ROOT/.release.env" "SADORA_RELEASE=$SHA1" || return 1
  expect_file "$SADORA_ROOT/landing/index.html" "landing $SHA1" || return 1
  expect_file "$SADORA_ROOT/releases/HISTORY" "$SHA2 $IMG2 failed"
}

t_first_unhealthy_deploy_fails_loudly() {
  echo "$SHA1" > "$FAKE/unhealthy"
  bundle "$SHA1"; deploy "$SHA1" "$IMG1"
  expect_status 1 && expect_out "no earlier CI release"
}

t_refused_token_stops_before_activation() {
  bundle "$SHA1"
  touch "$FAKE/login-fails"
  deploy "$SHA1" "$IMG1"
  expect_status 1 && expect_out "refused the token" || return 1
  [[ ! -e $SADORA_ROOT/.release.env ]] || { fail "activated without an image"; return 1; }
}

t_rollback_goes_to_the_previous_healthy_release() {
  bundle "$SHA1"; deploy "$SHA1" "$IMG1"
  bundle "$SHA2"; deploy "$SHA2" "$IMG2"; expect_status 0 || return 1
  echo > "$T/no-token"; gate rollback "$T/no-token"
  expect_status 0 && expect_out "2222222 → 1111111" || return 1
  expect_file "$SADORA_ROOT/.release.env" "SADORA_RELEASE=$SHA1"
}

t_old_releases_are_pruned() {
  bundle "$SHA1"; deploy "$SHA1" "$IMG1"; touch -d '-3 minutes' "$SADORA_ROOT/releases/$SHA1"
  bundle "$SHA2"; deploy "$SHA2" "$IMG2"; touch -d '-2 minutes' "$SADORA_ROOT/releases/$SHA2"
  bundle "$SHA3"; deploy "$SHA3" "$IMG3"; expect_status 0 || return 1
  [[ ! -d $SADORA_ROOT/releases/$SHA1 ]] || { fail "kept more than SADORA_KEEP releases"; return 1; }
  [[ -d $SADORA_ROOT/releases/$SHA2 ]] || { fail "pruned the release a rollback would need"; return 1; }
  expect_file "$FAKE/removed" "$IMG1"
}

t_publish_apk() {
  { printf 'PK\003\004'; head -c 4096 /dev/urandom; } > "$T/app.apk"
  gate "publish-apk $SHA1 41" "$T/app.apk"; expect_status 0 || { fail "publish failed"; return 1; }
  local dir=$SADORA_ROOT/downloads name=sadora-stage-41-1111111.apk
  cmp -s "$T/app.apk" "$dir/$name" || { fail "versioned APK differs"; return 1; }
  cmp -s "$T/app.apk" "$dir/sadora-stage.apk" || { fail "stable alias differs"; return 1; }
  expect_file "$dir/latest.json" "\"sha256\":\"$(sha256sum "$T/app.apk" | cut -d' ' -f1)\"" || return 1
  expect_file "$dir/latest.json" '"path":"/download/sadora-stage-41-1111111.apk"' || return 1
  expect_file "$dir/latest.json" '"api":"https://app-one.trycloudflare.com"'
}

t_publish_apk_rejects_non_apks() {
  head -c 4096 /dev/urandom | tr -d 'P' > "$T/not.apk"
  gate "publish-apk $SHA1 41" "$T/not.apk"; expect_status 1 && expect_out "not an APK" || return 1
  gate "publish-apk $SHA1 4x1" "$T/not.apk"; expect_status 1 && expect_out "numeric build" || return 1
  [[ ! -e $SADORA_ROOT/downloads/latest.json ]] || { fail "published anyway"; return 1; }
}

t_publish_apk_keeps_only_recent_builds() {
  { printf 'PK\003\004'; head -c 2048 /dev/urandom; } > "$T/app.apk"
  local b; for b in 1 2 3; do
    gate "publish-apk $SHA1 $b" "$T/app.apk"
    touch -d "-$((10 - b)) minutes" "$SADORA_ROOT/downloads/sadora-stage-$b-1111111.apk"
  done
  gate "publish-apk $SHA1 4" "$T/app.apk"
  [[ $(find "$SADORA_ROOT/downloads" -name 'sadora-stage-*-*.apk' | wc -l) == 2 ]] || { fail "kept $(find "$SADORA_ROOT/downloads" -name 'sadora-stage-*-*.apk' | wc -l) builds"; return 1; }
}

for t in $(declare -F | awk '{print $3}' | grep '^t_'); do test_case "${t#t_}" "$t"; done
printf '\n%d passed, %d failed\n' "$PASS" "$FAIL"
((FAIL == 0))
