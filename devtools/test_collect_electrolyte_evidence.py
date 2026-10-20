"""Hermetic regression tests for lossless, exact-head electrolyte evidence."""

import hashlib
import json
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent))
import collect_electrolyte_evidence as evidence


class ElectrolyteEvidenceTest(unittest.TestCase):
    """Check output identity, incomplete runs and explicit publication failures."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.output = self.root / "target/electrolyte-evidence"
        self.sha = "a" * 40
        self.context = {"GITHUB_SHA": "b" * 40, "shard": "6"}
        self.spec = evidence.ASSESSMENTS[0]
        self.producer, self.filename, self.rows, self.fixture = self.spec
        self.selected = {evidence.PACKAGE + self.producer}
        for producer, filename, rows, fixture in evidence.ASSESSMENTS:
            self.write(evidence.SOURCE_DIR + producer + ".java", "// reference producer\n")
            if fixture:
                self.write(evidence.FIXTURE_DIR + fixture, "reference,value\na,1\n")
        self.write(evidence.PROVENANCE, "Existing source and qualification boundaries.\n")

    def write(self, path, text):
        path = self.root / path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8", newline="")
        return path

    def report(self, producer=None, tests=1, failures=0, errors=0, skipped=0):
        producer = producer or self.producer
        class_name = evidence.PACKAGE + producer
        return self.write("target/surefire-reports/TEST-" + class_name + ".xml",
                          '<testsuite name="{}" tests="{}" failures="{}" '
                          'errors="{}" skipped="{}"/>'.format(
                              class_name, tests, failures, errors, skipped))

    def csv(self, filename=None, rows=None):
        count = self.rows if rows is None else rows
        # NaN is deliberately preserved for non-measured control endpoints.
        return self.write("target/" + (filename or self.filename),
                          "recipe,value\r\n" + "control,NaN\r\n" * count)

    def collect(self, selected=None):
        return evidence.collect(self.root, self.output,
                                self.selected if selected is None else selected,
                                self.context, self.sha)

    def test_lossless_bytes_hashes_and_actual_checkout(self):
        self.report()
        source = self.csv()
        original = source.read_bytes()
        manifest, errors = self.collect()
        self.assertEqual(errors, [])
        self.assertEqual((self.output / self.filename).read_bytes(), original)
        self.assertEqual(source.read_bytes(), original)
        item = manifest["assessments"][0]
        self.assertEqual(item["sha256"], hashlib.sha256(original).hexdigest())
        self.assertEqual(item["data_rows"], 28)
        self.assertEqual(manifest["checkout_sha"], self.sha)
        self.assertNotEqual(manifest["checkout_sha"], manifest["context"]["GITHUB_SHA"])
        self.assertEqual(manifest["qualification"], "not_assessed")
        self.assertTrue(manifest["selected_evidence_complete"])
        saved = json.loads((self.output / "manifest.json").read_text())
        self.assertEqual(saved, manifest)

    def test_every_assessment_is_collected_with_its_distinct_row_count(self):
        selected = set()
        for producer, filename, rows, fixture in evidence.ASSESSMENTS:
            selected.add(evidence.PACKAGE + producer)
            self.report(producer=producer)
            self.csv(filename=filename, rows=rows)
        manifest, errors = self.collect(selected)
        self.assertEqual(errors, [])
        self.assertTrue(manifest["selected_evidence_complete"])
        self.assertEqual(manifest["selected_assessment_count"], 8)
        self.assertEqual([item["data_rows"] for item in manifest["assessments"]],
                         [28, 10, 6, 7, 12, 18, 110, 110])
        self.assertEqual(sum(item["data_rows"] for item in manifest["assessments"]), 301)
        self.assertEqual(manifest["assessments"][1]["fixture"]["basis"],
                         "inline reference values")

    def test_full_family_numerical_failure_retains_all_110_rows(self):
        producer, filename, rows, fixture = evidence.ASSESSMENTS[-2]
        self.assertEqual(rows, 110)
        self.report(producer=producer, tests=1, failures=1)
        source = self.csv(filename=filename, rows=rows)
        manifest, errors = self.collect({evidence.PACKAGE + producer})
        self.assertEqual(errors, [])
        self.assertFalse(manifest["selected_evidence_complete"])
        self.assertEqual(manifest["qualification"], "not_assessed")
        item = manifest["assessments"][-2]
        self.assertEqual(item["producer_result"]["status"], "failed")
        self.assertEqual(item["data_rows"], 110)
        self.assertEqual((self.output / filename).read_bytes(), source.read_bytes())
        self.assertEqual(item["fixture"]["sha256"],
                         evidence.digest(self.root / evidence.FIXTURE_DIR / fixture))

    def test_measured_state_failure_retains_every_pair_without_qualification(self):
        producer, filename, rows, fixture = evidence.ASSESSMENTS[-1]
        self.assertEqual(producer, "CO2BrineHydrateMeasuredStateAssessmentTest")
        self.assertEqual(rows, 110)
        self.report(producer=producer, tests=1, failures=1)
        source = self.csv(filename=filename, rows=rows)
        manifest, errors = self.collect({evidence.PACKAGE + producer})
        self.assertEqual(errors, [])
        self.assertFalse(manifest["selected_evidence_complete"])
        self.assertEqual(manifest["qualification"], "not_assessed")
        item = manifest["assessments"][-1]
        self.assertEqual(item["producer_result"]["status"], "failed")
        self.assertEqual(item["data_rows"], 110)
        self.assertEqual((self.output / filename).read_bytes(), source.read_bytes())
        self.assertEqual(item["fixture"]["sha256"],
                         evidence.digest(self.root / evidence.FIXTURE_DIR / fixture))

    def test_unselected_leftovers_and_unrelated_files_are_not_uploaded(self):
        self.csv()
        self.report()
        self.write("target/private.csv", "must,not,collect\n")
        manifest, errors = self.collect(set())
        self.assertEqual(errors, [])
        self.assertEqual(list(self.output.iterdir()), [self.output / "manifest.json"])
        self.assertEqual(manifest["selected_assessment_count"], 0)
        self.assertTrue(all(item["producer_result"]["status"] == "not_selected"
                            for item in manifest["assessments"]))

    def test_missing_selection_is_not_complete(self):
        self.csv()
        manifest, errors = evidence.collect(self.root, self.output, None, {}, self.sha)
        self.assertEqual(errors, [])
        self.assertFalse(manifest["selection_available"])
        self.assertFalse(manifest["selected_evidence_complete"])
        self.assertFalse((self.output / self.filename).exists())

    def test_passed_test_without_csv_is_packaging_error(self):
        self.report()
        manifest, errors = self.collect()
        self.assertIn("did not produce", errors[0])
        self.assertFalse(manifest["selected_evidence_complete"])

    def test_absent_surefire_is_not_success_even_with_csv(self):
        self.csv()
        manifest, errors = self.collect()
        self.assertEqual(errors, [])
        self.assertEqual(manifest["assessments"][0]["producer_result"]["status"],
                         "not_completed")
        self.assertFalse(manifest["selected_evidence_complete"])

    def test_failed_test_retains_output_without_claiming_completion(self):
        self.report(failures=1)
        self.csv()
        manifest, errors = self.collect()
        self.assertEqual(errors, [])
        self.assertTrue((self.output / self.filename).is_file())
        self.assertEqual(manifest["assessments"][0]["producer_result"]["status"], "failed")
        self.assertFalse(manifest["selected_evidence_complete"])

    def test_failed_test_without_output_is_explicit_not_a_packaging_failure(self):
        self.report(errors=1)
        manifest, errors = self.collect()
        self.assertEqual(errors, [])
        self.assertFalse(manifest["assessments"][0]["output_present"])
        self.assertFalse(manifest["selected_evidence_complete"])

    def test_skipped_or_zero_tests_are_not_passed(self):
        for counts in ({"skipped": 1}, {"tests": 0}):
            with self.subTest(counts=counts):
                path = self.report(**counts)
                result = evidence.producer_result(path, evidence.PACKAGE + self.producer)
                self.assertEqual(result["status"], "not_fully_executed")

    def test_wrong_suite_is_rejected(self):
        path = self.report()
        path.write_text(path.read_text().replace(self.producer, "OtherTest"))
        _, errors = self.collect()
        self.assertIn("identity", errors[0])

    def test_invalid_surefire_counts_are_rejected(self):
        path = self.report(tests=-1)
        with self.assertRaisesRegex(ValueError, "non-negative"):
            evidence.producer_result(path, evidence.PACKAGE + self.producer)

    def test_jvm_properties_are_whitelisted(self):
        path = self.report()
        path.write_text(path.read_text().replace('/>', '><properties>'
                        '<property name="java.version" value="21.0.12"/>'
                        '<property name="private.setting" value="not-for-manifest"/>'
                        '</properties></testsuite>'))
        result = evidence.producer_result(path, evidence.PACKAGE + self.producer)
        self.assertEqual(result["runtime"], {"java.version": "21.0.12"})

    def test_corrupt_xml_is_recorded_and_manifest_survives(self):
        self.write("target/surefire-reports/TEST-" + evidence.PACKAGE + self.producer + ".xml",
                   "<not-closed")
        manifest, errors = self.collect()
        self.assertEqual(len(errors), 1)
        self.assertFalse(manifest["selected_evidence_complete"])
        self.assertTrue((self.output / "manifest.json").is_file())

    def test_truncated_csv_is_copied_but_rejected(self):
        self.report()
        self.csv(rows=1)
        _, errors = self.collect()
        self.assertIn("Expected 28 data rows, found 1", errors[0])
        self.assertTrue((self.output / self.filename).is_file())

    def test_invalid_csv_shapes_and_encoding_are_rejected(self):
        for data in (b"", b"a,a\n1,2\n", b"a,b\n1\n", b"a,b\n\xff\n"):
            with self.subTest(data=data):
                path = self.csv()
                path.write_bytes(data)
                with self.assertRaises((ValueError, UnicodeError)):
                    evidence.csv_shape(path)

    def test_symlink_output_is_rejected_without_copying_target(self):
        self.report()
        secret = self.write("not-an-assessment.txt", "private\n")
        (self.root / "target" / self.filename).symlink_to(secret)
        _, errors = self.collect()
        self.assertIn("symlink", errors[0])
        self.assertFalse((self.output / self.filename).exists())

    def test_missing_reference_source_is_not_silently_ignored(self):
        self.report()
        self.csv()
        (self.root / evidence.FIXTURE_DIR / self.fixture).unlink()
        _, errors = self.collect()
        self.assertIn("Not a regular evidence file", errors[0])

    def test_existing_bundle_is_never_reused(self):
        self.output.mkdir(parents=True)
        marker = self.output / "prior-evidence.csv"
        marker.write_text("keep this\n")
        with self.assertRaises(FileExistsError):
            self.collect()
        self.assertEqual(marker.read_text(), "keep this\n")

    def test_full_commit_sha_is_required(self):
        with self.assertRaisesRegex(ValueError, "full Git commit"):
            evidence.collect(self.root, self.output, set(), {}, "main")
        self.assertFalse(self.output.exists())

    def test_manifest_never_claims_experimental_accuracy(self):
        self.report()
        self.csv()
        manifest, _ = self.collect()
        self.assertEqual(manifest["qualification"], "not_assessed")
        self.assertNotIn("tolerance", json.dumps(manifest))

    def test_cli_uses_actual_checkout_not_event_sha(self):
        self.report()
        self.csv()
        script = self.root / "devtools/collect_electrolyte_evidence.py"
        script.parent.mkdir()
        shutil.copyfile(Path(evidence.__file__), script)
        selection = self.write("selected.txt", "\n".join(self.selected) + "\n")
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        subprocess.run(["git", "-C", str(self.root), "-c", "user.name=Evidence Test",
                        "-c", "user.email=evidence@example.invalid", "commit", "-q",
                        "--allow-empty", "-m", "test checkout"], check=True)
        actual = subprocess.check_output(["git", "-C", str(self.root), "rev-parse", "HEAD"],
                                         text=True).strip()
        result = subprocess.run([sys.executable, str(script), "--selected-tests", str(selection),
                                 "--shard", "6"], capture_output=True, text=True,
                                env=dict(os.environ, GITHUB_SHA="b" * 40))
        self.assertEqual(result.returncode, 0, result.stderr)
        manifest = json.loads((self.output / "manifest.json").read_text())
        self.assertEqual(manifest["checkout_sha"], actual)
        self.assertEqual(manifest["context"]["GITHUB_SHA"], "b" * 40)
        self.assertTrue(manifest["selected_evidence_complete"])


class EvidenceWorkflowContractTest(unittest.TestCase):
    """Keep collection additive, failure-visible and covered by hosted tests."""

    @classmethod
    def setUpClass(cls):
        cls.root = Path(__file__).resolve().parent.parent
        cls.workflow = yaml.safe_load((cls.root / ".github/workflows/"
                                      "core-run-build-test-and-javadoc.yml").read_text())

    def test_collector_runs_after_tests_even_on_failure(self):
        steps = self.workflow["jobs"]["test_java_slow"]["steps"]
        by_name = {step.get("name"): step for step in steps}
        run = by_name["Run slow-test shard without JaCoCo"]["run"]
        self.assertLess(run.index("slow-selected-tests.txt"), run.index("mvn -B test"))
        collector = by_name["Collect electrolyte assessment evidence"]
        self.assertEqual(collector["if"], "always()")
        self.assertIn('--selected-tests "$RUNNER_TEMP/slow-selected-tests.txt"', collector["run"])
        upload = by_name["Retain electrolyte assessment evidence"]
        self.assertEqual(upload["if"], "always()")
        self.assertEqual(upload["with"]["path"], "target/electrolyte-evidence/")
        self.assertEqual(upload["with"]["if-no-files-found"], "error")
        self.assertNotIn("continue-on-error", collector)
        self.assertEqual(by_name["Retain slow-test reports"]["with"]["path"],
                         "target/surefire-reports/")

    def test_changes_trigger_full_existing_matrix_and_keep_required_gate(self):
        filters = self.workflow["jobs"]["changes"]["steps"][1]["with"]["filters"]
        self.assertIn("devtools/collect_electrolyte_evidence.py", filters)
        self.assertIn("devtools/test_collect_electrolyte_evidence.py", filters)
        gate = self.workflow["jobs"]["test_java_slow_required"]["steps"][0]
        self.assertEqual(gate["run"], 'test "$SLOW_TEST_RESULT" = success')

    def test_unit_tests_are_in_hosted_devtools_matrix(self):
        workflow = (self.root / ".github/workflows/ai-devtools-python-tests.yml").read_text()
        self.assertIn("devtools/test_collect_electrolyte_evidence.py", workflow)


if __name__ == "__main__":
    unittest.main()
