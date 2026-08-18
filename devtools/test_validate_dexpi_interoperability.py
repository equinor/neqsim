import csv
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import unittest.mock


SCRIPT = Path(__file__).with_name("validate_dexpi_interoperability.py")
SPEC = importlib.util.spec_from_file_location("validate_dexpi_interoperability", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


CSV_HEADER = [
    "Object ID",
    "Line Number",
    "Object Type",
    "Rule ID",
    "Severity",
    "Severity Score",
    "Rule Description",
    "Location (XPath)",
    "Profile Source",
    "Suggested Correction",
]


class ValidateDexpiInteroperabilityTest(unittest.TestCase):
    def test_csv_findings_are_typed_and_deterministically_sorted(self):
        with tempfile.TemporaryDirectory() as temporary:
            report = Path(temporary) / "report.csv"
            with report.open("w", encoding="utf-8", newline="") as stream:
                writer = csv.writer(stream)
                writer.writerow(CSV_HEADER)
                writer.writerow(
                    [
                        "Pump1",
                        "7",
                        "Plant/ProcessEquipment.CentrifugalPump",
                        "VAL-004",
                        "Warning",
                        "2",
                        "Missing representation",
                        "//*[@id='Pump1']",
                        "Base",
                        "Add reviewed representation data",
                    ]
                )
                writer.writerow(
                    [
                        "",
                        "3",
                        "Core/EngineeringModel",
                        "ERR-E07",
                        "Error",
                        "3",
                        "Missing export provenance",
                        "/Model/Object[1]",
                        "Base",
                        "Add controlled provenance",
                    ]
                )

            findings = MODULE.parse_dexpi_viewer_csv(report)

            self.assertEqual("ERR-E07", findings[0]["ruleId"])
            self.assertEqual(3, findings[0]["lineNumber"])
            self.assertEqual("VAL-004", findings[1]["ruleId"])

    def test_run_requires_exact_checkout_and_retains_findings(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "validate-cli.js").write_text("// fake", encoding="utf-8")
            native = root / "plant.dexpi.xml"
            native.write_text("<Model/>", encoding="utf-8")

            def fake_run(command, **kwargs):
                if command[0] == "git":
                    return subprocess.CompletedProcess(
                        command, 0, stdout=MODULE.DEXPI_VIEWER_COMMIT + "\n", stderr=""
                    )
                output_directory = Path(command[command.index("--out") + 1])
                csv_path = output_directory / "plant.dexpi.csv"
                with csv_path.open("w", encoding="utf-8", newline="") as stream:
                    writer = csv.writer(stream)
                    writer.writerow(CSV_HEADER)
                    writer.writerow(
                        [
                            "",
                            "3",
                            "Core/EngineeringModel",
                            "ERR-E07",
                            "Error",
                            "3",
                            "Missing export provenance",
                            "/Model/Object[1]",
                            "Base",
                            "Add controlled provenance",
                        ]
                    )
                return subprocess.CompletedProcess(command, 1, stdout="issues", stderr="")

            with unittest.mock.patch.object(MODULE.subprocess, "run", side_effect=fake_run):
                result = MODULE.run_dexpi_viewer(
                    native, root, MODULE.DEXPI_VIEWER_COMMIT
                )

            self.assertEqual("ISSUES_FOUND", result["status"])
            self.assertEqual({"errors": 1, "warnings": 0, "infos": 0}, result["issueCounts"])
            self.assertEqual(MODULE.DEXPI_VIEWER_COMMIT, result["commit"])
            self.assertEqual(1, len(result["findings"]))

    def test_baseline_comparison_checks_provenance_and_counts(self):
        with tempfile.TemporaryDirectory() as temporary:
            baseline = Path(temporary) / "baseline.json"
            baseline.write_text(
                json.dumps(
                    {
                        "repository": MODULE.DEXPI_VIEWER_REPOSITORY,
                        "commit": MODULE.DEXPI_VIEWER_COMMIT,
                        "inputSha256": "abc",
                        "expectedIssueCounts": {"errors": 8, "warnings": 3},
                    }
                ),
                encoding="utf-8",
            )
            result = {
                "repository": MODULE.DEXPI_VIEWER_REPOSITORY,
                "commit": MODULE.DEXPI_VIEWER_COMMIT,
                "inputSha256": "abc",
                "issueCounts": {"errors": 8, "warnings": 3, "infos": 0},
            }

            matched = MODULE.compare_dexpi_viewer_baseline(result, baseline)
            self.assertEqual("MATCHED", matched["status"])

            result["issueCounts"]["errors"] = 7
            changed = MODULE.compare_dexpi_viewer_baseline(result, baseline)
            self.assertEqual("CHANGED", changed["status"])
            self.assertEqual("issueCounts.errors", changed["differences"][0]["field"])

    def test_native_file_can_be_validated_without_a_pydexpi_package(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            native = root / "fixture.xml"
            native.write_text(
                "<Model><Import prefix='Core' source='core'/>"
                "<Import prefix='Plant' source='plant'/><Object type='Core/EngineeringModel'/>"
                "</Model>",
                encoding="utf-8",
            )

            report = MODULE.validate_package(root, None, native_file=native)

            self.assertEqual("STRUCTURE_PASSED", report["nativeDexpi"]["status"])
            self.assertEqual("NOT_AVAILABLE", report["pyDexpi"]["status"])
            self.assertEqual("NOT_RUN", report["dexpiViewer"]["status"])


if __name__ == "__main__":
    unittest.main()
