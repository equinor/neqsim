"""Exercise scanner outputs and the actual issue-publication workflow offline."""

import contextlib
import copy
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import yaml

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "neqsim-paperlab" / "tools"))

import daily_scan
import trending_topics


class DailyScanPublicationTest(unittest.TestCase):
    """Keep the scanner-to-workflow report contract executable."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.workspace = Path(self.temp.name)
        self.output = self.workspace / "neqsim-paperlab" / "papers" / "_research_scan"
        self.output.mkdir(parents=True)
        self.github_output = self.workspace / "github-output"
        self.workflow = yaml.safe_load(
            (REPO_ROOT / ".github" / "workflows" / "research-scan.yml").read_text()
        )
        self.pick = {
            "title": "Synthetic CO2 transport study",
            "domain": "CO2 transport",
            "paper_type": "comparative",
            "trend_score": 80,
            "effort": "medium",
            "effort_weeks": 8,
            "suggested_journals": ["fluid_phase_equilibria"],
            "inspiring_paper": {"title": "Synthetic benchmark", "authors": []},
            "research_angle": "Compare transport predictions with a benchmark.",
            "suggestion_date": "2026-09-10",
            "scan_metadata": {"scan_date": "2026-09-10", "domains_searched": 1},
        }
        self.legacy = {
            "opportunities": [
                {"class_name": "SystemSrkEos", "score": 70, "readiness": "ready"}
            ],
            "summary": {"total_opportunities": 1, "ready_count": 1, "top_score": 70},
        }

    def run_scan(self, args=(), fallback=False):
        """Run the real CLI with deterministic scanner inputs and no network."""
        trending = {
            "opportunities": [self.pick],
            "summary": {"total_opportunities": 1},
            "metadata": self.pick["scan_metadata"],
        }
        environment = {
            "NEQSIM_REPO_ROOT": str(self.workspace),
            "SCAN_OUTPUT_DIR": str(self.output),
            "GITHUB_OUTPUT": str(self.github_output),
        }
        with contextlib.ExitStack() as stack:
            stack.enter_context(patch.dict(os.environ, environment))
            stack.enter_context(patch.object(sys, "argv", ["daily_scan.py", *args]))
            stack.enter_context(patch.object(
                trending_topics, "daily_suggestion",
                return_value=None if fallback else copy.deepcopy(self.pick),
            ))
            stack.enter_context(patch.object(trending_topics, "scan_trending", return_value=trending))
            stack.enter_context(patch.object(daily_scan, "scan_opportunities", return_value=self.legacy))
            stack.enter_context(patch.object(
                daily_scan, "generate_markdown_report", return_value="# Legacy scan\n",
            ))
            stack.enter_context(contextlib.redirect_stdout(io.StringIO()))
            self.assertEqual(0, daily_scan.main())
        return json.loads((self.output / ".pr_metadata.json").read_text())

    def publish(self, fail=False):
        """Execute the workflow's JavaScript with an in-memory GitHub API."""
        steps = self.workflow["jobs"]["scan"]["steps"]
        script = next(step["with"]["script"] for step in steps if step.get("name") == "Create Issue")
        metadata = json.loads((self.output / ".pr_metadata.json").read_text())
        # Expand the old workflow expressions too, so the original bug reproduces.
        script = script.replace("${{ github.workspace }}", str(self.workspace))
        script = script.replace("${{ steps.check.outputs.title }}", metadata["title"])
        harness = """
const calls = [];
const context = {repo: {owner: 'example', repo: 'neqsim'}};
const github = {rest: {issues: {create: async (request) => {
  if (process.env.FAIL_PUBLICATION === 'true') throw new Error('publication failed');
  calls.push(request);
  return {data: {number: 123}};
}}}};
async function run() {
""" + script + """
}
run().then(() => console.log(JSON.stringify(calls))).catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
"""
        node = os.environ.get("CODEX_PRIMARY_RUNTIME_NODE") or shutil.which("node")
        self.assertIsNotNone(node, "Node.js is required to execute the workflow regression")
        return subprocess.run(
            [node, "-e", harness], capture_output=True, text=True, check=False,
            env={**os.environ, "SCAN_OUTPUT_DIR": str(self.output),
                 "FAIL_PUBLICATION": str(fail).lower()},
        )

    def test_default_scan_publishes_the_generated_report(self):
        metadata = self.run_scan()
        result = self.publish()
        self.assertEqual(0, result.returncode, result.stderr)
        request, = json.loads(result.stdout)
        self.assertEqual(metadata["title"], request["title"])
        self.assertEqual((self.output / "daily_suggestion.md").read_text(), request["body"])

    def test_all_modes_identify_the_current_report_despite_stale_files(self):
        cases = [
            ((), False, "daily_suggestion.md", "# Daily Paper Suggestion"),
            (("--full-scan",), False, "trending_report.md", "# Trending Paper Opportunities"),
            (("--legacy",), False, "scout_report.md", "# Legacy scan"),
            ((), True, "scout_report.md", "# Legacy scan"),
        ]
        for args, fallback, filename, heading in cases:
            with self.subTest(args=args, fallback=fallback):
                for report in ("daily_suggestion.md", "trending_report.md", "scout_report.md"):
                    (self.output / report).write_text("stale report")
                metadata = self.run_scan(args, fallback)
                self.assertEqual(filename, metadata["report_file"])
                result = self.publish()
                self.assertEqual(0, result.returncode, result.stderr)
                request, = json.loads(result.stdout)
                self.assertTrue(request["body"].startswith(heading))
                self.assertNotIn("stale report", request["body"])

    def test_unchanged_scan_is_skipped_unless_forced(self):
        self.assertTrue(self.run_scan()["changed"])
        self.assertFalse(self.run_scan()["changed"])
        self.assertIn("changed=false", self.github_output.read_text())
        self.assertTrue(self.run_scan(("--force",))["changed"])

    def test_publication_errors_fail_the_step(self):
        self.run_scan()
        result = self.publish(fail=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("publication failed", result.stderr)

    def test_missing_report_fails_before_issue_creation(self):
        self.run_scan()
        (self.output / "daily_suggestion.md").unlink()
        result = self.publish()
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", result.stdout)

    def test_state_cache_is_saved_only_after_successful_publication(self):
        steps = self.workflow["jobs"]["scan"]["steps"]
        publisher = next(i for i, step in enumerate(steps) if step.get("name") == "Create Issue")
        restorers = [step for step in steps if step.get("uses", "").startswith("actions/cache/restore@")]
        savers = [(i, step) for i, step in enumerate(steps)
                  if step.get("uses", "").startswith("actions/cache/save@")]
        self.assertEqual(1, len(restorers))
        self.assertEqual(1, len(savers))
        index, saver = savers[0]
        self.assertGreater(index, publisher)
        self.assertEqual("success()", saver.get("if", "success()"))
        self.assertEqual(restorers[0]["with"]["path"], saver["with"]["path"])
        for filename in (".last_scan_hash", "suggestion_history.json"):
            self.assertIn(filename, saver["with"]["path"])
        self.assertIn("github.run_id", saver["with"]["key"])
        self.assertIn("github.run_attempt", saver["with"]["key"])
        self.assertTrue(restorers[0]["with"]["restore-keys"].strip())


if __name__ == "__main__":
    unittest.main()
