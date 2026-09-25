#!/usr/bin/env python3
"""
Prints the build number the next TestFlight upload should carry: one above the highest
App Store Connect has for the app, whoever uploaded it — CI or a person at a Mac.

    ASC_KEY_ID=… ASC_ISSUER_ID=… ASC_KEY_PATH=AuthKey_….p8 python3 tools/ci/asc_next_build.py 6811085950

Needs `cryptography` (the JWT is ES256, signed by hand) and `curl`.
"""
import base64
import json
import os
import subprocess
import sys
import time

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, utils


def token(key_id: str, issuer: str, key_path: str) -> str:
    key = serialization.load_pem_private_key(open(key_path, "rb").read(), None)
    enc = lambda d: base64.urlsafe_b64encode(d).rstrip(b"=")
    now = int(time.time())
    head = enc(json.dumps({"alg": "ES256", "kid": key_id, "typ": "JWT"}).encode())
    body = enc(json.dumps({"iss": issuer, "iat": now, "exp": now + 600, "aud": "appstoreconnect-v1"}).encode())
    r, s = utils.decode_dss_signature(key.sign(head + b"." + body, ec.ECDSA(hashes.SHA256())))
    return (head + b"." + body + b"." + enc(r.to_bytes(32, "big") + s.to_bytes(32, "big"))).decode()


def main() -> None:
    app_id = sys.argv[1]
    jwt = token(os.environ["ASC_KEY_ID"], os.environ["ASC_ISSUER_ID"], os.environ["ASC_KEY_PATH"])
    url = f"https://api.appstoreconnect.apple.com/v1/builds?filter[app]={app_id}&sort=-uploadedDate&limit=50&fields[builds]=version"
    out = subprocess.run(
        ["curl", "-sfg", url, "-H", f"Authorization: Bearer {jwt}"], capture_output=True, text=True, check=True
    ).stdout
    versions = [int(b["attributes"]["version"]) for b in json.loads(out)["data"] if b["attributes"]["version"].isdigit()]
    print(max(versions, default=0) + 1)


if __name__ == "__main__":
    main()
