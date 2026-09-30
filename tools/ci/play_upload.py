#!/usr/bin/env python3
"""
Google Play for CI: the next versionCode, and an upload to the internal testing track.

    PLAY_SERVICE_ACCOUNT=play.json python3 tools/ci/play_upload.py next uz.sadora.doctor
    PLAY_SERVICE_ACCOUNT=play.json python3 tools/ci/play_upload.py upload uz.sadora.doctor app.aab "Release name"

`next` prints one above the highest versionCode Play has for the package, whoever
uploaded it — CI, or a person in the Play Console. `upload` puts the bundle on the
internal track as a completed release, so the internal testers get it once Play has
processed it; nothing reaches closed, open or production testing from here.

The service account needs "Release apps to testing tracks" on the app in the Play Console
(Users and permissions). Needs `cryptography` (the token is an RS256 JWT, signed by hand,
like asc_next_build.py) and `curl`. The account's key is never printed.
"""
import base64
import json
import os
import subprocess
import sys
import time

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications"


def access_token(account: dict) -> str:
    enc = lambda d: base64.urlsafe_b64encode(d).rstrip(b"=")
    now = int(time.time())
    head = enc(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    body = enc(json.dumps({
        "iss": account["client_email"],
        "scope": "https://www.googleapis.com/auth/androidpublisher",
        "aud": account["token_uri"],
        "iat": now,
        "exp": now + 600,
    }).encode())
    key = serialization.load_pem_private_key(account["private_key"].encode(), None)
    signature = enc(key.sign(head + b"." + body, padding.PKCS1v15(), hashes.SHA256()))
    assertion = (head + b"." + body + b"." + signature).decode()
    out = curl(["-X", "POST", account["token_uri"],
                "-d", "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer",
                "-d", f"assertion={assertion}"])
    return out["access_token"]


def curl(args: list, token: str | None = None) -> dict:
    auth = ["-H", f"Authorization: Bearer {token}"] if token else []
    result = subprocess.run(["curl", "-sS", "-w", "\n%{http_code}", *auth, *args], capture_output=True, text=True)
    text, _, status = result.stdout.rpartition("\n")
    if result.returncode != 0 or not status.startswith("2"):
        # Google's error body names the problem (permission, versionCode, package) and
        # carries no secret; the request itself, with the token, is not printed.
        sys.exit(f"Play API answered {status or 'nothing'}: {text[:800]}")
    return json.loads(text) if text.strip() else {}


def open_edit(package: str, token: str) -> str:
    return curl(["-X", "POST", f"{API}/{package}/edits", "-H", "Content-Type: application/json", "-d", "{}"], token)["id"]


def next_version(package: str, token: str) -> int:
    edit = open_edit(package, token)
    bundles = curl([f"{API}/{package}/edits/{edit}/bundles"], token).get("bundles", [])
    apks = curl([f"{API}/{package}/edits/{edit}/apks"], token).get("apks", [])
    curl(["-X", "DELETE", f"{API}/{package}/edits/{edit}"], token)
    codes = [int(b["versionCode"]) for b in bundles] + [int(a["versionCode"]) for a in apks]
    return max(codes, default=0) + 1


def upload(package: str, bundle: str, name: str, token: str) -> int:
    edit = open_edit(package, token)
    uploaded = curl([
        "-X", "POST", f"{UPLOAD}/{package}/edits/{edit}/bundles?uploadType=media",
        "-H", "Content-Type: application/octet-stream", "--data-binary", f"@{bundle}",
    ], token)
    code = int(uploaded["versionCode"])
    track = {"track": "internal", "releases": [{"name": name, "versionCodes": [str(code)], "status": "completed"}]}
    curl(["-X", "PUT", f"{API}/{package}/edits/{edit}/tracks/internal",
          "-H", "Content-Type: application/json", "-d", json.dumps(track)], token)
    curl(["-X", "POST", f"{API}/{package}/edits/{edit}:commit"], token)
    return code


def main() -> None:
    account = json.load(open(os.environ["PLAY_SERVICE_ACCOUNT"]))
    token = access_token(account)
    command, package = sys.argv[1], sys.argv[2]
    if command == "next":
        print(next_version(package, token))
    elif command == "upload":
        print(upload(package, sys.argv[3], sys.argv[4], token))
    else:
        sys.exit(f"unknown command {command}")


if __name__ == "__main__":
    main()
