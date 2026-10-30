"""Numerical and provenance checks for the preliminary structure-H dataset."""

import copy
import csv
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("fit_structure_h_dissociation.py")
SPEC = importlib.util.spec_from_file_location("fit_structure_h_dissociation", SCRIPT)
fitter = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(fitter)


class StructureHDissociationFitTest(unittest.TestCase):
    """Keep observed data, regression, and component-qualification claims separate."""

    def test_recovers_known_coefficients_without_reciprocal_temperature_cancellation(self):
        """Use an independent prescribed curve, including a narrow temperature interval."""
        for temperature in ([270.0, 280.0, 290.0, 300.0], [279.999, 280.0, 280.001]):
            with self.subTest(temperature=temperature):
                pressure = [math.exp(36.0 - 9800.0 / value) for value in temperature]
                intercept, slope = fitter.fit_coefficients(temperature, pressure)
                self.assertAlmostEqual(9800.0, slope, delta=1e-5)
                self.assertAlmostEqual(36.0, intercept + slope / 280.0, delta=1e-7)

    def test_rejects_unpaired_nonphysical_and_rank_deficient_observations(self):
        """Invalid observations must not be clipped into a fit."""
        cases = [
            ([280.0], [3.0]),
            ([280.0, 281.0], [3.0]),
            ([280.0, 280.0], [3.0, 4.0]),
            ([0.0, 281.0], [3.0, 4.0]),
            ([280.0, float("nan")], [3.0, 4.0]),
            ([280.0, 281.0], [3.0, float("inf")]),
            ([280.0, 281.0], [3.0, -4.0]),
        ]
        for temperature, pressure in cases:
            with self.subTest(temperature=temperature, pressure=pressure):
                with self.assertRaises(ValueError):
                    fitter.fit_coefficients(temperature, pressure)

    def test_frozen_data_preserve_all_24_experimental_markers(self):
        """Keep source systems and counts independent of fitted curves or legend markers."""
        rows, provenance = fitter.load_dataset()
        self.assertEqual(24, len(rows))
        self.assertEqual("S3", provenance["source_figure"])
        self.assertEqual(4, provenance["source_page_one_based"])
        counts = {key: sum(row["system_id"] == key for row in rows) for key in provenance["systems"]}
        self.assertEqual({"ICP": 7, "ClCHP": 6, "BrCHP": 5, "t14DClCH": 6}, counts)
        self.assertTrue(all(275 < row["temperature_K"] < 290 for row in rows))
        self.assertTrue(all(1 < row["reported_pressure_MPa"] < 10 for row in rows))

    def test_independent_vector_axis_calibration_matches_frozen_data(self):
        """Check coordinate conversion from the known linear 1/T and log-pressure axes."""
        rows, provenance = fitter.load_dataset()
        axes = provenance["vector_extraction"]["axes_pdf_pt"]
        for row in rows:
            x_fraction = (row["marker_x_pdf_pt"] - axes["left"]) / (axes["right"] - axes["left"])
            y_fraction = (row["marker_y_pdf_pt"] - axes["top"]) / (axes["bottom"] - axes["top"])
            self.assertAlmostEqual(0.00345 + 0.0002 * x_fraction, 1.0 / row["temperature_K"], places=14)
            self.assertAlmostEqual(1 - y_fraction, math.log10(row["reported_pressure_MPa"]), places=12)

    def test_regression_matches_prior_numpy_least_squares_and_reports_internal_validation(self):
        """Compare the standard-library implementation with separately computed NumPy results."""
        rows, provenance = fitter.load_dataset()
        result = fitter.analyze(rows, provenance)
        references = {
            "ICP": (35.240818054839266, 9515.04068752929, 1.447988973911702),
            "ClCHP": (37.12751626127393, 10097.059487392376, 2.756343717010118),
            "BrCHP": (35.71211676939441, 9650.022558876759, 1.7235657040576124),
            "t14DClCH": (34.250618402321905, 9264.657125132057, 1.9820955753134974),
        }
        for key, expected in references.items():
            actual = result["systems"][key]
            self.assertAlmostEqual(expected[0], actual["A_dimensionless"], places=9)
            self.assertAlmostEqual(expected[1], actual["B_K"], places=6)
            self.assertAlmostEqual(expected[2], actual["leave_one_out"]["AARD_percent"], places=8)
            self.assertEqual(2, actual["internal_holdout"]["holdout_count"])
            self.assertEqual(actual["number_of_points"] - 2, actual["internal_holdout"]["training_count"])
            self.assertGreater(actual["B_K"], 0)
        self.assertFalse(result["qualification"]["accepted_for_COMP"])
        self.assertFalse(result["qualification"]["independent_study_validation"])
        self.assertEqual("METHANE_WATER_PROMOTER_SYSTEM_CORRELATION", result["coefficient_type"])
        self.assertIn("BLOCKED", result["qualification"]["physical_component_fit"])

    def test_leave_one_out_actually_refits_after_excluding_the_target(self):
        """An outlying marker must increase held-out error rather than reusing the full fit."""
        rows, provenance = fitter.load_dataset()
        clean = fitter.analyze(rows, provenance)["systems"]["ICP"]
        changed = copy.deepcopy(rows)
        group = sorted((row for row in changed if row["system_id"] == "ICP"), key=lambda row: row["temperature_K"])
        group[0]["reported_pressure_MPa"] *= 1.5
        disturbed = fitter.analyze(changed, provenance)["systems"]["ICP"]
        self.assertGreater(disturbed["leave_one_out"]["maximum_relative_error_percent"], 20)
        self.assertGreater(disturbed["leave_one_out"]["AARD_percent"], clean["leave_one_out"]["AARD_percent"])

    def test_pressure_trend_failure_is_not_a_component_qualification(self):
        """Reject a physically reversed dissociation-pressure trend."""
        rows, provenance = fitter.load_dataset()
        for row in rows:
            row["reported_pressure_MPa"] = math.exp(10000.0 / row["temperature_K"] - 35.0)
        with self.assertRaisesRegex(ValueError, "increase with temperature"):
            fitter.analyze(rows, provenance)

    def test_provenance_rejects_modified_data_and_invalid_rows(self):
        """Detect corrupt bytes, even when a new checksum is supplied for invalid data."""
        with tempfile.TemporaryDirectory() as folder:
            data_path = Path(folder) / "data.csv"
            provenance_path = Path(folder) / "provenance.json"
            original = fitter.DATA.read_bytes()
            metadata = json.loads(fitter.PROVENANCE.read_text(encoding="utf-8"))
            data_path.write_bytes(original + b"\n")
            provenance_path.write_text(json.dumps(metadata), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "checksum"):
                fitter.load_dataset(data_path, provenance_path)
            with fitter.DATA.open(encoding="utf-8", newline="") as stream:
                reader = csv.DictReader(stream)
                fields = reader.fieldnames
                rows = list(reader)
            rows[0]["reported_pressure_MPa"] = "nan"
            with data_path.open("w", encoding="utf-8", newline="") as stream:
                writer = csv.DictWriter(stream, fieldnames=fields)
                writer.writeheader()
                writer.writerows(rows)
            metadata["derived_csv_sha256"] = hashlib.sha256(data_path.read_bytes()).hexdigest()
            provenance_path.write_text(json.dumps(metadata), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Nonfinite"):
                fitter.load_dataset(data_path, provenance_path)

    def test_pdf_audit_rejects_wrong_source_before_importing_optional_dependency(self):
        """The offline test suite needs neither PyMuPDF nor a network download."""
        rows, provenance = fitter.load_dataset()
        with tempfile.TemporaryDirectory() as folder:
            pdf = Path(folder) / "wrong.pdf"
            pdf.write_bytes(b"not the reviewed primary source")
            with self.assertRaisesRegex(ValueError, "Source PDF checksum"):
                fitter.audit_source_pdf(pdf, rows, provenance)

    def test_documented_cli_runs_offline_and_does_not_claim_a_pdf_audit(self):
        """Execute the documented command and verify explicit qualification status."""
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder) / "fit.json"
            completed = subprocess.run([sys.executable, str(SCRIPT), "--output", str(output)],
                                       capture_output=True, text=True, check=False)
            self.assertEqual(0, completed.returncode, completed.stderr)
            result = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual("NOT_RUN", result["source_pdf_audit"])
            self.assertFalse(result["qualification"]["accepted_for_COMP"])
            self.assertEqual(4, len(result["systems"]))


if __name__ == "__main__":
    unittest.main()
