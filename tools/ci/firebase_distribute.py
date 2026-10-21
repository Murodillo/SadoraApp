#!/usr/bin/env python3
"""
Firebase App Distribution for CI: a staging APK to the testers' group.

    FIREBASE_SERVICE_ACCOUNT=firebase.json python3 tools/ci/firebase_distribute.py \
        google-services.json uz.sadora.doctor app.apk "Release notes" testers

The project number and the app's id are read from the app's google-services.json, the
same file the build is given, so nothing here names a Firebase project. The APK is
uploaded, its release gets the notes, and it is sent to the group (a group alias; several
are comma-separated). A group that does not exist yet is created, empty — testers are
added to it in the Firebase console, and they get every release after that.

Staging and dev builds go here; Google Play only ever gets production builds
(release.yml). The service account needs "Firebase App Distribution Admin" — the Firebase
Admin SDK account the server sends pushes with has it. Needs `cryptography` (the token is
an RS256 JWT, signed by hand, like play_upload.py) and `curl`. The key is never printed.
"""
import base64
import json
import os
import subprocess
import sys
import time

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

API = "https://firebaseappdistribution.googleapis.com"


def access_token(account: dict) -> str:
    enc = lambda d: base64.urlsafe_b64encode(d).rstrip(b"=")
    now = int(time.time())
    head = enc(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    body = enc(json.dumps({
        "iss": account["client_email"],
        "scope": "https://www.googleapis.com/auth/cloud-platform",
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


def curl(args: list, token: str | None = None, allow: tuple = ()) -> dict:
    auth = ["-H", f"Authorization: Bearer {token}"] if token else []
    result = subprocess.run(["curl", "-sS", "-w", "\n%{http_code}", *auth, *args], capture_output=True, text=True)
    text, _, status = result.stdout.rpartition("\n")
    if status in allow:
        return {"_status": status}
    if result.returncode != 0 or not status.startswith("2"):
        # Google's error body names the problem (permission, app id, group) and carries
        # no secret; the request itself, with the token, is not printed.
        sys.exit(f"App Distribution answered {status or 'nothing'}: {text[:800]}")
    return json.loads(text) if text.strip() else {}


def find_app(services: dict, package: str) -> tuple[str, str]:
    number = services["project_info"]["project_number"]
    for client in services["client"]:
        if client["client_info"]["android_client_info"]["package_name"] == package:
            return number, client["client_info"]["mobilesdk_app_id"]
    sys.exit(f"google-services.json has no app for {package}")


def ensure_group(project: str, alias: str, token: str) -> None:
    found = curl([f"{API}/v1/projects/{project}/groups/{alias}"], token, allow=("404",))
    if found.get("_status") == "404":
        curl(["-X", "POST", f"{API}/v1/projects/{project}/groups?groupId={alias}",
              "-H", "Content-Type: application/json", "-d", json.dumps({"displayName": alias})], token)
        print(f"created the group '{alias}' — add testers to it in the Firebase console", file=sys.stderr)


def distribute(project: str, app: str, apk: str, notes: str, groups: list, token: str) -> str:
    operation = curl([
        "-X", "POST", f"{API}/upload/v1/projects/{project}/apps/{app}/releases:upload",
        "-H", "X-Goog-Upload-Protocol: raw",
        "-H", f"X-Goog-Upload-File-Name: {os.path.basename(apk)}",
        "-H", "Content-Type: application/octet-stream",
        "--data-binary", f"@{apk}",
    ], token)
    # The upload answers with a long-running operation; processing takes seconds.
    for _ in range(60):
        if operation.get("done"):
            break
        time.sleep(5)
        operation = curl([f"{API}/v1/{operation['name']}"], token)
    else:
        sys.exit("App Distribution did not finish processing the APK in five minutes")
    if "error" in operation:
        sys.exit(f"App Distribution rejected the APK: {json.dumps(operation['error'])[:800]}")
    release = operation["response"]["release"]["name"]
    curl(["-X", "PATCH", f"{API}/v1/{release}?updateMask=release_notes.text",
          "-H", "Content-Type: application/json", "-d", json.dumps({"releaseNotes": {"text": notes}})], token)
    for alias in groups:
        ensure_group(project, alias, token)
    curl(["-X", "POST", f"{API}/v1/{release}:distribute",
          "-H", "Content-Type: application/json", "-d", json.dumps({"groupAliases": groups})], token)
    return release


def main() -> None:
    services_file, package, apk, notes, groups = sys.argv[1:6]
    account = json.load(open(os.environ["FIREBASE_SERVICE_ACCOUNT"]))
    token = access_token(account)
    project, app = find_app(json.load(open(services_file)), package)
    aliases = [g.strip() for g in groups.split(",") if g.strip()]
    print(distribute(project, app, apk, notes, aliases, token).rsplit("/", 1)[-1])


if __name__ == "__main__":
    main()
