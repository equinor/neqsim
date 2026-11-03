"""Tests for devtools/discipline_report_manifest.py."""
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import discipline_report_manifest as drm  # noqa: E402


def _task(root, results):
    task = Path(root) / "pvt_task"
    (task / "step3_report").mkdir(parents=True)
    (task / "results.json").write_text(json.dumps(results), encoding="utf-8")
    (task / "step3_report" / "report.docx").write_bytes(b"docx")
    return task


class EmitTest(unittest.TestCase):
    def test_emits_manifest_and_records(self):
        with tempfile.TemporaryDirectory() as tmp:
            task = _task(tmp, {
                "key_results": {"saturation_pressure_bara": 312.4,
                                "gor": {"value": 180.0, "unit": "Sm3/Sm3", "label": "GOR"}},
                "assumptions": ["SRK EOS"], "approach": "CME on recombined sample",
                "conclusions": "Fluid frozen.", "data_gaps": ["no viscosity data"]})
            out = Path(tmp) / "case" / "reports" / "pvt-fluid-report"
            code = drm.main([str(task), "--type", "pvt-fluid-report", "--project-id", "TURMALINA",
                             "--person", "A. Engineer", "--out", str(out)])
            self.assertEqual(code, 0)
            manifest = json.loads((out / "report_manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(manifest["schema"], "discipline_report.v1")
            self.assertEqual(manifest["files"], {"results": "engineering_results.jsonl", "report": "report.docx"})
            self.assertEqual(manifest["person"], "A. Engineer")
            self.assertEqual(manifest["validation_status"], "screening")
            self.assertEqual(manifest["gaps"], ["no viscosity data"])
            records = [json.loads(l) for l in (out / "engineering_results.jsonl").read_text(encoding="utf-8").splitlines()]
            by_name = {r["quantity"]: r for r in records}
            self.assertEqual(by_name["saturation_pressure_bara"]["unit"], "bara")
            self.assertEqual(by_name["saturation_pressure_bara"]["value"], 312.4)
            self.assertEqual(by_name["gor"]["unit"], "Sm3/Sm3")
            self.assertEqual(by_name["gor"]["discipline"], "pvt")
            self.assertEqual(by_name["gor"]["responsible_agent"], "neqsim-task-solve")
            self.assertTrue(all(r["schema"] == "engineering_result.v1" for r in records))

    def test_missing_results_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(drm.main([tmp, "--type", "pvt-fluid-report"]), 2)

    def test_empty_key_results_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            task = _task(tmp, {"key_results": {}})
            self.assertEqual(drm.main([str(task), "--type", "pvt-fluid-report"]), 2)
            self.assertFalse((task / "discipline_report").exists())


if __name__ == "__main__":
    unittest.main()
