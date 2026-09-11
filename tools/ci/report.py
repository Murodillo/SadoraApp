#!/usr/bin/env python3
"""
Turns what the test jobs leave behind into the markdown a pull request shows.

    python3 tools/ci/report.py \
        --junit 'reports/**/TEST-*.xml' \
        --kover server=reports/server/kover.xml \
        --vitest admin=reports/admin/coverage-summary.json \
        --gate reports/gate/results.txt

JUnit XML from Gradle, Kover's XML (JaCoCo's format), vitest's json-summary, and the
gate's TAP-like output. Standard library only, so it runs on any runner without a setup
step. Missing inputs are reported as missing rather than failing the report: the comment
should still appear when one job did not get as far as producing its file.
"""
from __future__ import annotations

import argparse
import glob
import json
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class Suite:
    tests: int = 0
    failures: int = 0
    skipped: int = 0
    seconds: float = 0.0
    failed_names: list[str] = field(default_factory=list)

    def add(self, other: "Suite") -> None:
        self.tests += other.tests
        self.failures += other.failures
        self.skipped += other.skipped
        self.seconds += other.seconds
        self.failed_names += other.failed_names


def read_junit(path: Path) -> Suite:
    root = ET.parse(path).getroot()
    suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
    total = Suite()
    for suite in suites:
        total.tests += int(suite.get("tests", 0))
        total.failures += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
        total.skipped += int(suite.get("skipped", 0))
        total.seconds += float(suite.get("time", 0) or 0)
        for case in suite.findall("testcase"):
            if case.find("failure") is not None or case.find("error") is not None:
                total.failed_names.append(f"{case.get('classname', '')}.{case.get('name', '')}")
    return total


def module_of(path: Path) -> str:
    """server/build/test-results/… locally, reports/server/test-results/… from CI artifacts → server."""
    parts = path.parts
    for marker in ("build", "test-results"):
        if marker in parts and parts.index(marker) > 0:
            return parts[parts.index(marker) - 1]
    return "tests"


def read_kover(path: Path) -> dict[str, float | None]:
    root = ET.parse(path).getroot()
    out: dict[str, float | None] = {}
    for counter in root.findall("counter"):  # report-level totals only
        missed, covered = int(counter.get("missed", 0)), int(counter.get("covered", 0))
        out[counter.get("type", "").lower()] = 100.0 * covered / (missed + covered) if missed + covered else None
    return out


def read_vitest(path: Path) -> dict[str, float | None]:
    total = json.loads(path.read_text())["total"]
    return {kind: total[kind]["pct"] for kind in ("lines", "branches", "functions") if kind in total}


def read_gate(path: Path) -> Suite:
    text = path.read_text()
    passed = len(re.findall(r"^ok\s", text, re.M))
    failed = re.findall(r"^FAIL (\S+)", text, re.M)
    return Suite(tests=passed + len(failed), failures=len(failed), failed_names=failed)


JOBS = {
    "backend": "Backend",
    "android": "Android and shared",
    "ios": "iOS shared",
    "admin": "Admin panel",
    "infra": "Deploy tooling",
    "stage": "Staging",
}
ICONS = {"success": "✅", "failure": "❌", "cancelled": "⏹️", "skipped": "⏭️"}


def render_needs(needs: dict) -> str:
    """The `needs` context of the report job: what each job did, and what staging got."""
    lines = ["### Jobs", "", "| | Job | Result |", "|---|---|---|"]
    for key, label in JOBS.items():
        if key in needs:
            result = needs[key].get("result", "unknown")
            lines.append(f"| {ICONS.get(result, '❔')} | {label} | {result} |")

    stage = needs.get("stage")
    if stage is not None:
        result, out = stage.get("result"), stage.get("outputs") or {}
        planned = (needs.get("plan", {}).get("outputs") or {}).get("deliver") == "true"
        lines += ["", "### Staging", ""]
        if result == "success" and out.get("ready") == "false":
            lines.append("Not delivered: the `staging` environment has no deploy secrets yet — see “CI/CD” in the README.")
        elif result == "success":
            size = f"{int(out['apk_size']) / 1048576:.1f} MB" if out.get("apk_size", "").isdigit() else "—"
            lines += [
                f"Deployed `{out.get('release', '')[:7]}`, smoke-tested, and published APK build "
                f"**#{out.get('build', '?')}** on the staging landing page (`/download.html`).",
                "",
                "| | |",
                "|---|---|",
                f"| API image | `{out.get('image') or '—'}` |",
                f"| APK | {size} · SHA-256 `{out.get('apk_sha256') or '—'}` |",
                f"| Signed by | `{out.get('apk_cert') or '—'}` |",
            ]
        elif result == "skipped" and planned:
            lines.append("Not delivered: a check it depends on did not pass.")
        elif result == "skipped":
            lines.append("Not delivered: this event is checked only.")
        else:
            lines.append(f"Delivery **{result}**. A deploy that does not become healthy rolls itself back; the Staging jobs say where it stopped.")
    return "\n".join(lines) + "\n"


def pct(value: float | None) -> str:
    return "—" if value is None else f"{value:.1f}%"


def render(suites: dict[str, Suite], coverage: dict[str, dict[str, float | None]], missing: list[str]) -> str:
    lines = ["### Tests", "", "| | Module | Passed | Failed | Skipped | Time |", "|---|---|---:|---:|---:|---:|"]
    for name, s in sorted(suites.items()):
        icon = "❌" if s.failures else "✅"
        lines.append(f"| {icon} | {name} | {s.tests - s.failures - s.skipped} | {s.failures} | {s.skipped} | {s.seconds:.0f}s |")
    failed = [n for s in suites.values() for n in s.failed_names]
    if failed:
        lines += ["", "<details><summary>Failed tests</summary>", ""]
        lines += [f"- `{n}`" for n in failed[:50]]
        lines += ["", "</details>"]
    if coverage:
        lines += ["", "### Coverage", "", "| Component | Lines | Branches |", "|---|---:|---:|"]
        for name, c in sorted(coverage.items()):
            lines.append(f"| {name} | {pct(c.get('line', c.get('lines')))} | {pct(c.get('branch', c.get('branches')))} |")
    if missing:
        lines += ["", "_Not reported: " + ", ".join(missing) + "._"]
    return "\n".join(lines) + "\n"


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--junit", action="append", default=[], help="glob of JUnit XML files")
    parser.add_argument("--kover", action="append", default=[], help="name=path to a Kover XML report")
    parser.add_argument("--vitest", action="append", default=[], help="name=path to coverage-summary.json")
    parser.add_argument("--gate", help="output of deploy/stage/test/sadora-ci.test.sh")
    parser.add_argument("--needs-json", help="toJSON(needs) of the reporting job")
    parser.add_argument("--json", help="also write the numbers here, for later steps")
    args = parser.parse_args(argv)

    suites: dict[str, Suite] = {}
    coverage: dict[str, dict[str, float | None]] = {}
    missing: list[str] = []

    for pattern in args.junit:
        files = [Path(p) for p in glob.glob(pattern, recursive=True)]
        if not files:
            missing.append(f"JUnit `{pattern}`")
        for f in files:
            suites.setdefault(module_of(f), Suite()).add(read_junit(f))
    for spec, reader in [(s, read_kover) for s in args.kover] + [(s, read_vitest) for s in args.vitest]:
        name, _, path = spec.partition("=")
        if Path(path).is_file():
            coverage[name] = reader(Path(path))
        else:
            missing.append(f"{name} coverage")
    if args.gate:
        if Path(args.gate).is_file():
            suites["deploy gate"] = read_gate(Path(args.gate))
        else:
            missing.append("deploy gate tests")

    if args.needs_json:
        sys.stdout.write(render_needs(json.loads(args.needs_json)) + "\n")
    sys.stdout.write(render(suites, coverage, missing))
    if args.json:
        Path(args.json).write_text(json.dumps({
            "tests": {k: vars(v) for k, v in suites.items()},
            "coverage": coverage,
            "missing": missing,
        }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
