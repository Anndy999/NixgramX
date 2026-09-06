"""Parse workflow YAML and exercise only the offline embedded Python detector."""
import ast
import json
from pathlib import Path
import re
import subprocess
import sys
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[3]


def workflow(name):
    # BaseLoader preserves GitHub's YAML 1.2 'on' key (SafeLoader uses YAML 1.1).
    return yaml.load((ROOT / ".github/workflows" / name).read_text(encoding="utf-8"), Loader=yaml.BaseLoader)


class WorkflowTests(unittest.TestCase):
    def test_all_workflows_parse(self):
        for path in (ROOT / ".github/workflows").glob("*.yml"):
            with self.subTest(workflow=path.name):
                parsed = workflow(path.name)
                self.assertIsInstance(parsed["jobs"], dict)
                self.assertIn("on", parsed)

    def detector(self):
        step = workflow("upstream-watch.yml")["jobs"]["watch"]["steps"][0]
        match = re.search(r'python3 -c "(.*?)"\) \|\|', step["run"], re.DOTALL)
        self.assertIsNotNone(match)
        script = match.group(1)
        ast.parse(script)
        return script

    def test_detector_selects_first_update_commit(self):
        result = subprocess.run(
            [sys.executable, "-B", "-c", self.detector()],
            input=json.dumps([
                {"sha": "ignored", "commit": {"message": "ordinary fix"}},
                {"sha": "selected", "commit": {"message": "Update to 12.10.2\nextra text"}},
                {"sha": "older", "commit": {"message": "update to 12.10.1"}},
            ]), capture_output=True, text=True, timeout=10,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), "selected")
        self.assertEqual(result.stderr.strip(), "Update to 12.10.2")

    def test_no_match_returns_code_handled_by_soft_fail_branch(self):
        result = subprocess.run(
            [sys.executable, "-B", "-c", self.detector()],
            input="[]", capture_output=True, text=True, timeout=10,
        )
        self.assertEqual(result.returncode, 2)
        self.assertEqual(result.stdout, "")
        script = workflow("upstream-watch.yml")["jobs"]["watch"]["steps"][0]["run"]
        self.assertIn('No update-to commit found (or parse failed)', script)
        self.assertIn("exit 0", script)

    def test_watch_remains_main_only_and_report_only(self):
        watch = workflow("upstream-watch.yml")
        self.assertEqual(set(watch["on"]), {"schedule", "workflow_dispatch"})
        self.assertEqual(watch["jobs"]["watch"]["if"], "github.ref == 'refs/heads/main'")
        for step in watch["jobs"]["watch"]["steps"]:
            self.assertNotIn("gradlew", step.get("run", ""))

    def test_publication_jobs_are_serial_and_have_distinct_metadata_variables(self):
        for filename, variable in (("release.yml", "UPDATE_RELEASE_MESSAGE_ID"),
                                   ("staging.yml", "UPDATE_BETA_MESSAGE_ID")):
            with self.subTest(workflow=filename):
                job = workflow(filename)["jobs"]["upload"]
                self.assertEqual(job["concurrency"], {
                    "group": "nixgramx-telegram-publish", "cancel-in-progress": "false",
                })
                step = next(step for step in job["steps"] if step["name"] == "Send to Telegram")
                self.assertEqual(step["env"]["UPDATE_METADATA_MESSAGE_ID"], "${{ vars." + variable + " }}")
                self.assertNotIn("HELPER_BOT_CANARY_TARGET", step["run"])
                self.assertIn("Tools/scripts/upload.py", step["run"])


if __name__ == "__main__":
    unittest.main()
