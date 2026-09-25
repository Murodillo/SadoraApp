"""Fails when the three Gradle builds stop agreeing on the versions they must share.

sadora-client and sadora-doctor include sadora-backend's contract build, so the Kotlin
Gradle plugin and the Android Gradle plugin of all three end up in one Gradle run. Gradle
refuses two versions of the Android plugin there, and a Kotlin mismatch fails later and
less legibly — so each catalog is read and the shared versions compared here, in seconds.

    python3 tools/ci/check_versions.py
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
CATALOGS = {
    "sadora-backend": ROOT / "sadora-backend/gradle/libs.versions.toml",
    "sadora-client": ROOT / "sadora-client/gradle/libs.versions.toml",
    "sadora-doctor": ROOT / "sadora-doctor/gradle/libs.versions.toml",
}
# Must be equal wherever a catalog declares them. The first two are the hard rule; the
# rest keep the contract's Android target and the two apps' libraries in step.
SHARED = ["kotlin", "agp", "android-compileSdk", "android-minSdk", "composeMultiplatform", "ktor"]


def versions(path: pathlib.Path) -> dict[str, str]:
    text = path.read_text()
    block = text.split("[versions]", 1)[1].split("\n[", 1)[0]
    return dict(re.findall(r'^([\w.-]+)\s*=\s*"([^"]+)"', block, re.M))


def check(catalogs: dict[str, pathlib.Path]) -> list[str]:
    found = {name: versions(path) for name, path in catalogs.items()}
    problems = []
    for key in SHARED:
        seen = {name: v[key] for name, v in found.items() if key in v}
        if len(set(seen.values())) > 1:
            problems.append(f"{key}: " + ", ".join(f"{n} {v}" for n, v in seen.items()))
    for key in ("kotlin", "agp"):
        missing = [name for name, v in found.items() if key not in v]
        if missing:
            problems.append(f"{key}: not declared in {', '.join(missing)}")
    return problems


def main() -> int:
    problems = check(CATALOGS)
    for problem in problems:
        print(f"::error title=Version drift::{problem}")
    if not problems:
        print("kotlin, agp and the shared versions agree across " + ", ".join(CATALOGS))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
