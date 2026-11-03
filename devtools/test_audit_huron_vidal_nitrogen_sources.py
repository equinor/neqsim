"""Guard nitrogen/water source versions without calibrating an EOS."""

import copy
import csv
from decimal import Decimal
import hashlib
import io
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

from audit_huron_vidal_nitrogen_sources import ROOT, audit, reconcile, validate_metadata


class NitrogenSourceTest(unittest.TestCase):
    """Check independent scale arithmetic, explicit joins and rejection boundaries."""

    def setUp(self):
        """Load the original representations for semantic mutation tests."""
        self.thesis = (ROOT / "thesis-2004/nitrogen-in-water.csv").read_bytes()
        self.compiler = (ROOT / "nitrogen-in-water.csv").read_bytes()
        self.metadata = json.loads((ROOT / "thesis-2004/provenance.json").read_text())

    def mutate(self, raw, change):
        """Change numeric semantics independently of the immutable-byte gate."""
        reader = csv.DictReader(io.StringIO(raw.decode()))
        records = list(reader)
        change(records)
        stream = io.StringIO(newline="")
        writer = csv.DictWriter(stream, fieldnames=reader.fieldnames)
        writer.writeheader()
        writer.writerows(records)
        return stream.getvalue().encode()

    def test_bidirectional_counts_and_exact_decimal_scaling(self):
        """Known independent arithmetic preserves the small liquid discrepancy."""
        result = audit()
        gas = result["water-in-nitrogen"]
        liquid = result["nitrogen-in-water"]
        self.assertEqual((35, 35, 35), tuple(gas[k] for k in ("rows", "exact_TP", "exact_TPx")))
        self.assertEqual((52, 50, 9, 41), tuple(liquid[k] for k in (
            "rows", "exact_TP", "exact_TPx", "changed_x_at_common_TP")))
        self.assertEqual(Decimal("0.002040"), Decimal(gas["comparisons"][0]["thesis_mole_fraction"]))
        row = liquid["comparisons"][2]
        self.assertEqual((3, 1), (row["thesis_row_1_based"], row["compiler_row_1_based"]))
        self.assertEqual(Decimal("-0.0000002"), Decimal(row["compiler_minus_thesis_mole_fraction"]))
        self.assertEqual("1.1", row["AD_exp_percent_as_printed"])
        self.assertEqual("0.000016", row["compiler_U95_absolute"])
        self.assertEqual(Decimal("0.0000005"), Decimal(liquid["max_absolute_fraction_difference"]))

    def test_two_recorded_temperature_versions_are_not_exact_joins(self):
        """Both temperatures survive; no rounding makes the observations equal."""
        result = audit()["nitrogen-in-water"]
        self.assertEqual([25, 52], result["temperature_discrepancy_thesis_rows"])
        pairs = [(row["thesis_T_K"], row["compiler_T_K"], row["compiler_P_kPa"])
                 for row in result["comparisons"] if not row["exact_TP"]]
        self.assertEqual([("293.1", "293.12", "984"), ("363.00", "363.02", "6842")], pairs)
        self.assertTrue(all(not row["exact_TPx"] for row in result["comparisons"]
                            if not row["exact_TP"]))

    def test_no_nearest_state_or_reused_state_fallback(self):
        """Plausibly close unrecorded states and duplicate keys are rejected."""
        for change in (lambda r: r[0].update(T_K="274.19001"),
                       lambda r: r[24].update(T_K="293.11"),
                       lambda r: r[0].update(P_MPa="0.979001"),
                       lambda r: r.__setitem__(1, dict(r[0]))):
            with self.subTest(change=change), self.assertRaises(ValueError):
                reconcile(self.mutate(self.thesis, change), self.compiler, "nitrogen-in-water")

    def test_missing_duplicate_and_nonfinite_observations_fail(self):
        """Reject impossible values even when byte fingerprints are not involved."""
        for change in (lambda r: r.pop(), lambda r: r.append(dict(r[0])),
                       lambda r: r[0].update(x_exp_times_1e3="NaN"),
                       lambda r: r[0].update(x_exp_times_1e3="1001"),
                       lambda r: r[0].update(AD_exp_percent_as_printed="-1")):
            with self.subTest(change=change), self.assertRaises(ValueError):
                reconcile(self.mutate(self.thesis, change), self.compiler, "nitrogen-in-water")
        for change in (lambda r: r[0].update(compiler_U95_absolute="0"),
                       lambda r: r[0].update(mole_fraction="Infinity"),
                       lambda r: r.__setitem__(1, dict(r[0]))):
            with self.subTest(change=change), self.assertRaises(ValueError):
                reconcile(self.thesis, self.mutate(self.compiler, change), "nitrogen-in-water")

    def test_wrong_direction_scale_and_units_fail(self):
        """Never reinterpret scaled mole fractions or MPa by a renamed header."""
        for before, after in ((b"P_MPa", b"P_kPa"),
                              (b"x_exp_times_1e3", b"x_exp_times_1e4"),
                              (b"x_exp_times_1e3", b"mass_fraction")):
            with self.subTest(after=after), self.assertRaises(ValueError):
                reconcile(self.thesis.replace(before, after), self.compiler, "nitrogen-in-water")
        with self.assertRaises(ValueError):
            reconcile(self.thesis, self.compiler, "water-in-nitrogen")

    def test_source_pins_reject_reorder_and_self_rehashed_receipts(self):
        """Neither a changed table nor its rewritten metadata can replace a source."""
        for filename in ("nitrogen-in-water.csv", "thesis-2004/nitrogen-in-water.csv"):
            with self.subTest(filename=filename), tempfile.TemporaryDirectory() as temp:
                directory = Path(temp) / "data"
                shutil.copytree(ROOT, directory)
                path = directory / filename
                path.write_bytes(self.mutate(path.read_bytes(), lambda r: r.reverse()))
                metadata = copy.deepcopy(self.metadata)
                metadata["tables"]["nitrogen-in-water"]["sha256"] = hashlib.sha256(
                    path.read_bytes()).hexdigest()
                (directory / "thesis-2004/provenance.json").write_text(json.dumps(metadata))
                with self.assertRaises(ValueError):
                    audit(directory)

    def test_full_comparison_cannot_hide_or_repair_discrepancies(self):
        """An attractive summary is insufficient if any row-level receipt changed."""
        for field, value in (("compiler_T_K", "293.1"),
                             ("exact_TP", True), ("AD_exp_percent_as_printed", "2.5")):
            with self.subTest(field=field), tempfile.TemporaryDirectory() as temp:
                directory = Path(temp) / "data"
                shutil.copytree(ROOT, directory)
                path = directory / "thesis-2004/comparison.json"
                result = json.loads(path.read_text())
                result["nitrogen-in-water"]["comparisons"][24][field] = value
                path.write_text(json.dumps(result))
                with self.assertRaises(ValueError):
                    audit(directory)

    def test_author_calibration_AD_and_compiler_U95_are_distinct(self):
        """No automatic confidence conversion or uncertainty substitution is accepted."""
        changes = [lambda m: m["uncertainties"]["thesis_liquid_calibration"].update(
                       quantity="mole fraction", confidence_level="95"),
                   lambda m: m["uncertainties"].update(thesis_liquid_AD_exp="point U95"),
                   lambda m: m["uncertainties"].update(compiler="author standard uncertainty")]
        for change in changes:
            metadata = copy.deepcopy(self.metadata)
            change(metadata)
            with self.assertRaises(ValueError):
                validate_metadata(metadata)

    def test_provenance_cannot_promote_or_reverse_source_direction(self):
        """Qualified status, substitute pages and changed source identities fail."""
        changes = [lambda m: m.update(parameter_activation=True),
                   lambda m: m.update(status="qualified"), lambda m: m.update(missing=[]),
                   lambda m: m["primary"].update(pdf_sha256="0" * 64),
                   lambda m: m["tables"]["nitrogen-in-water"].update(pdf_page_1_based=192),
                   lambda m: m["tables"]["nitrogen-in-water"].update(measured_component="water"),
                   lambda m: m.update(lineage="independent holdout")]
        for change in changes:
            metadata = copy.deepcopy(self.metadata)
            change(metadata)
            with self.assertRaises(ValueError):
                validate_metadata(metadata)

    def test_documented_cli_is_read_only_normal_and_optimized(self):
        """Execute the documented command and compare its complete output."""
        paths = list(ROOT.rglob("*"))
        before = {str(p): p.read_bytes() for p in paths if p.is_file()}
        script = Path(__file__).with_name("audit_huron_vidal_nitrogen_sources.py")
        for flags in ([], ["-O"]):
            result = subprocess.run([sys.executable] + flags + [str(script)], check=True,
                                    capture_output=True, text=True)
            self.assertEqual(audit(), json.loads(result.stdout))
        result = subprocess.run([sys.executable, str(script), "--summary"], check=True,
                                capture_output=True, text=True)
        self.assertEqual(41, json.loads(result.stdout)["nitrogen-in-water"]["changed_x_at_common_TP"])
        self.assertEqual(before, {str(p): p.read_bytes() for p in paths if p.is_file()})


if __name__ == "__main__":
    unittest.main()
