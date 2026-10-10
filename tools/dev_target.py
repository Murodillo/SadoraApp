"""
Where the dev tools (seed_demo, api_audit) may point, and the admin they sign in as.

Both write rows — demo accounts, blocked users, Premium grants — so production is
refused unless the caller passes --i-mean-it. Production is api.sadora.app and whatever
SADORA_PROD_HOST names in deploy/stage/hosts.env; staging (dev-api, staging-api) and a
laptop are fine.

The admin password is no longer the repository's changeme123 everywhere: demo_up.sh
replaces it with a random one in build/demo/admin_password before a tunnel can reach
it. SADORA_ADMIN_PASSWORD overrides both.
"""
import os
import sys
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parent.parent
ADMIN_EMAIL = os.environ.get("SADORA_ADMIN_EMAIL", "owner@sadora.uz")


def _prod_hosts() -> set[str]:
    hosts = {"api.sadora.app", "sadora.app", "admin.sadora.app", "doctor.sadora.app"}
    env = ROOT / "sadora-backend" / "deploy" / "stage" / "hosts.env"
    if env.exists():
        for line in env.read_text().splitlines():
            if line.startswith("SADORA_PROD_HOST="):
                value = line.split("=", 1)[1].strip().strip('"').strip("'")
                hosts.add(value.split("@")[-1])
    return hosts


def base_url(default: str = "http://localhost:8080") -> str:
    """The first non-flag argument, refused when it is production."""
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    base = (args[0] if args else default).rstrip("/")
    host = urlparse(base).hostname or ""
    if host in _prod_hosts() and "--i-mean-it" not in sys.argv:
        raise SystemExit(
            f"{host} is production. These tools create accounts and change data; "
            "pass --i-mean-it if that is really what you want."
        )
    return base


def admin_password() -> str:
    if os.environ.get("SADORA_ADMIN_PASSWORD"):
        return os.environ["SADORA_ADMIN_PASSWORD"]
    saved = ROOT / "build" / "demo" / "admin_password"
    if saved.exists():
        return saved.read_text().strip()
    return "changeme123"
