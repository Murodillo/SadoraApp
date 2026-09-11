#!/usr/bin/env bash
#
# Checks staging from the outside — through the tunnels, the way a browser and a phone do.
#
#   tools/ci/smoke.sh <app url> <landing url> <release sha> [apk build] [apk sha256]
#
# Run by stage.yml after every deploy, and by hand against whatever staging is serving:
#   tools/ci/smoke.sh https://….trycloudflare.com https://….trycloudflare.com <sha>
#
# Every check runs even after one fails, so a broken deploy is described completely in one
# go. The URLs are never printed — CI logs of a public repository are public.
set -uo pipefail

APP=${1:?app URL} LANDING=${2:?landing URL} RELEASE=${3:?release sha} BUILD=${4:-} APK_SHA256=${5:-}
FAILED=0
CODE='' BODY=''
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

summary() { [[ -n ${GITHUB_STEP_SUMMARY:-} ]] && printf '%s\n' "$1" >> "$GITHUB_STEP_SUMMARY"; return 0; }
pass() { printf 'ok    %s\n' "$1"; summary "| ✅ | $1 | |"; }
fail() {
  FAILED=$((FAILED + 1))
  printf 'FAIL  %s — %s\n' "$1" "$2"
  summary "| ❌ | $1 | $2 |"
  [[ -n ${GITHUB_ACTIONS:-} ]] && echo "::error title=Smoke test::$1 — $2"
  return 0
}

# check <name> <detail when failed> <test…> — records a pass or a failure, never exits.
check() {
  local name=$1 detail=$2; shift 2
  if "$@"; then pass "$name"; else fail "$name" "$detail"; fi
}

# fetch <method> <url> [curl args…] → CODE, BODY (first 64 KiB)
fetch() {
  local method=$1 url=$2; shift 2
  CODE=$(curl -sS -m 60 -X "$method" -o "$TMP/body" -w '%{http_code}' "$@" "$url" 2>/dev/null) || CODE=000
  BODY=$(head -c 65536 "$TMP/body" 2>/dev/null || true)
}
has() { [[ $CODE == "$1" && $BODY == *"$2"* ]]; }

sha256() { if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1; }

summary "### Smoke test"
summary ""
summary "| | Check | Detail |"
summary "|---|---|---|"

# 1. The API answers, through Caddy and the tunnel, and it is the release just deployed.
#    A fresh tunnel route can take a moment to settle, so this one check waits.
for _ in $(seq 1 24); do
  fetch GET "$APP/health/ready"
  [[ $CODE == 200 && $BODY == *'"status":"ready"'* && $BODY == *"\"release\":\"$RELEASE\""* ]] && break
  sleep 5
done
if [[ $CODE == 200 && $BODY == *"\"release\":\"$RELEASE\""* ]]; then pass "API is ready and runs ${RELEASE:0:7}"
else fail "API is ready and runs ${RELEASE:0:7}" "HTTP $CODE, release $(grep -o '"release":"[^"]*"' <<<"$BODY" | cut -d'"' -f4 | cut -c1-7)"; fi

# 2. The admin panel, and a deep link into it (a route in the browser, not a file).
fetch GET "$APP/"
check "admin panel loads" "HTTP $CODE" has 200 'id="root"'
fetch GET "$APP/rewards"
check "admin deep link falls back to the app" "HTTP $CODE" has 200 'id="root"'

# 3. The browser's sign-in request — Origin and all. A 403 here is the API refusing the
#    panel's own origin (CORS), which is exactly how the panel broke once: curl without an
#    Origin header got 200 while every browser got 403. Wrong credentials must say 401.
fetch POST "$APP/v1/admin/auth/login" -H "Origin: $APP" -H 'Content-Type: application/json' \
  --data '{"email":"ci-smoke@sadora.uz","password":"smoke-test-not-a-password"}'
case $CODE in
  401) pass "panel sign-in is allowed from its own origin" ;;
  429) pass "panel sign-in is allowed from its own origin (rate limited, not refused)" ;;
  403) fail "panel sign-in is allowed from its own origin" "403 — the API's CORS list lacks the tunnel URL" ;;
  *) fail "panel sign-in is allowed from its own origin" "HTTP $CODE, expected 401" ;;
esac

# 4. The API still refuses what needs a token.
fetch GET "$APP/v1/shop"
check "API requires a token" "HTTP $CODE, expected 401" has 401 ""

# 5. The landing page, its invite route, and the download page.
fetch GET "$LANDING/"
check "landing page loads" "HTTP $CODE" has 200 SADORA
fetch GET "$LANDING/r/K7M2QP"
check "invite link opens the invite page" "HTTP $CODE" has 404 /download.html
fetch GET "$LANDING/download.html"
check "download page loads" "HTTP $CODE" has 200 latest.json

# 6. The published APK: the manifest names this release and build, and the bytes a phone
#    downloads are the bytes CI built.
if [[ -n $BUILD ]]; then
  fetch GET "$LANDING/download/latest.json" -H 'Cache-Control: no-cache'
  manifest=$BODY
  if [[ $CODE != 200 ]]; then
    fail "APK manifest is published" "HTTP $CODE"
  elif [[ $(jq -r .release <<<"$manifest") != "$RELEASE" || $(jq -r .build <<<"$manifest") != "$BUILD" ]]; then
    fail "APK manifest is published" "names build $(jq -r .build <<<"$manifest") of $(jq -r .release <<<"$manifest" | cut -c1-7)"
  else
    pass "APK manifest names build #$BUILD of ${RELEASE:0:7}"
    path=$(jq -r .path <<<"$manifest")
    meta=$(curl -sS -m 300 -o "$TMP/app.apk" -w '%{http_code}|%{content_type}' "$LANDING$path" 2>/dev/null) || meta="000|"
    if [[ ${meta%%|*} != 200 || ${meta#*|} != application/vnd.android.package-archive* ]]; then
      fail "APK downloads" "HTTP ${meta%%|*}, ${meta#*|}"
    elif [[ -n $APK_SHA256 && $(sha256 "$TMP/app.apk") != "$APK_SHA256" ]]; then
      fail "APK downloads" "the downloaded file's SHA-256 is not the one CI built"
    else
      pass "APK downloads as an APK, byte for byte what CI built"
    fi
  fi
fi

echo
if ((FAILED)); then echo "$FAILED smoke check(s) failed"; exit 1; fi
echo "all smoke checks passed"
