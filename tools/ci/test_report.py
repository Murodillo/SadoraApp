"""Tests for tools/ci/report.py.  Run: python3 -m unittest discover -s tools/ci"""
import io
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

import report

JUNIT = """<?xml version="1.0"?>
<testsuite name="uz.sadora.server.TotpTest" tests="3" skipped="1" failures="1" errors="0" time="1.5">
  <testcase classname="uz.sadora.server.TotpTest" name="accepts the current code"/>
  <testcase classname="uz.sadora.server.TotpTest" name="rejects an old code"><failure message="boom"/></testcase>
  <testcase classname="uz.sadora.server.TotpTest" name="skipped"><skipped/></testcase>
</testsuite>"""

KOVER = """<?xml version="1.0"?>
<report name="Kover">
  <package name="uz/sadora/server"><counter type="LINE" missed="1" covered="1"/></package>
  <counter type="INSTRUCTION" missed="10" covered="30"/>
  <counter type="BRANCH" missed="3" covered="1"/>
  <counter type="LINE" missed="40" covered="60"/>
</report>"""


class ReportTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())

    def write(self, name, text):
        path = self.dir / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        return path

    def run_main(self, *argv):
        out = io.StringIO()
        with redirect_stdout(out):
            self.assertEqual(report.main(list(argv)), 0)
        return out.getvalue()

    def test_counts_failures_and_names_them(self):
        self.write("server/build/test-results/test/TEST-a.xml", JUNIT)
        text = self.run_main("--junit", str(self.dir / "**/TEST-*.xml"))
        self.assertIn("| ❌ | server | 1 | 1 | 1 | 2s |", text)
        self.assertIn("`uz.sadora.server.TotpTest.rejects an old code`", text)

    def test_reads_report_level_kover_totals_not_package_ones(self):
        path = self.write("kover.xml", KOVER)
        self.assertEqual(report.read_kover(path)["line"], 60.0)
        self.assertEqual(report.read_kover(path)["branch"], 25.0)

    def test_renders_vitest_summary(self):
        path = self.write("summary.json", json.dumps({"total": {"lines": {"pct": 97.5}, "branches": {"pct": 91}}}))
        text = self.run_main("--vitest", f"admin={path}")
        self.assertIn("| admin | 97.5% | 91.0% |", text)

    def test_reads_gate_output(self):
        path = self.write("gate.txt", "ok   a\nFAIL b — exit 1\n     | x\nok   c\n\n2 passed, 1 failed\n")
        suite = report.read_gate(path)
        self.assertEqual((suite.tests, suite.failures, suite.failed_names), (3, 1, ["b"]))

    def test_missing_inputs_are_listed_not_fatal(self):
        text = self.run_main("--kover", f"server={self.dir / 'nope.xml'}", "--gate", str(self.dir / "nope.txt"))
        self.assertIn("_Not reported: server coverage, deploy gate tests._", text)

    def test_writes_json_for_later_steps(self):
        self.write("server/build/test-results/test/TEST-a.xml", JUNIT)
        out = self.dir / "out.json"
        self.run_main("--junit", str(self.dir / "**/*.xml"), "--json", str(out))
        self.assertEqual(json.loads(out.read_text())["tests"]["server"]["failures"], 1)


    def test_module_from_artifact_layout(self):
        self.assertEqual(report.module_of(Path("reports/shared/test-results/testAndroidHostTest/TEST-a.xml")), "shared")
        self.assertEqual(report.module_of(Path("server/build/test-results/test/TEST-a.xml")), "server")

    def needs(self, stage_result, outputs=None, deliver="true"):
        return json.dumps({
            "plan": {"result": "success", "outputs": {"deliver": deliver}},
            "backend": {"result": "success", "outputs": {}},
            "ios": {"result": "failure", "outputs": {}},
            "stage": {"result": stage_result, "outputs": outputs or {}},
        })

    def test_delivery_details_when_staging_succeeded(self):
        outputs = {"ready": "true", "release": "a" * 40, "build": "57", "apk_size": "17299054",
                   "apk_sha256": "f" * 64, "image": "ghcr.io/x/y@sha256:" + "1" * 64, "apk_cert": "ab:cd"}
        text = self.run_main("--needs-json", self.needs("success", outputs))
        self.assertIn("| ❌ | iOS shared | failure |", text)
        self.assertIn("Deployed `aaaaaaa`", text)
        self.assertIn("build **#57**", text)
        self.assertIn("16.5 MB", text)

    def test_explains_why_nothing_was_delivered(self):
        self.assertIn("a check it depends on did not pass", self.run_main("--needs-json", self.needs("skipped")))
        self.assertIn("checked only", self.run_main("--needs-json", self.needs("skipped", deliver="false")))
        self.assertIn("no deploy secrets yet", self.run_main("--needs-json", self.needs("success", {"ready": "false"})))
        self.assertIn("rolls itself back", self.run_main("--needs-json", self.needs("failure")))


if __name__ == "__main__":
    unittest.main()
