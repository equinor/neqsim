"""Complete SRK receipt and rejection controls; also run with optimized Python."""
import copy
import csv
from pathlib import Path
import tempfile
import unittest
import qualify_srk

ROOT = Path(__file__).resolve().parent


class SrkReceipts(unittest.TestCase):
    """Check the retained324-row evidence and fail-closed rejection independently of Java/JUnit."""

    def test_complete_reference(self):
        """All native and Java-reference fluxes pass the80-digit numerical audit."""
        with tempfile.TemporaryDirectory() as temporary:
            result = qualify_srk.evaluate(ROOT / "srk-native.csv", Path(temporary))
            self.assertEqual(324, result["rows"])
            self.assertEqual(54, result["transition_rows"])

    def test_corrupt_receipts_reject(self):
        """No missing/invalid/wrong-volume evidence may retain a successful summary."""
        with (ROOT / "srk-native.csv").open(newline="") as stream:
            base = list(csv.reader(stream))
        changes = [("energy_native_W", "1e30"), ("energy_reference_W", "1e30"),
                   ("xL_methane", "NaN"), ("xL_methane", "0"), ("rhoL_kg_m3", "-1"),
                   ("rhoEOSL_kg_m3", base[1][base[0].index("rhoL_kg_m3")]),
                   ("eMechanicalL_J_kg", base[1][base[0].index("eL_J_kg")]),
                   ("T_K", "300"), ("phase", "aqueous")]
        mutants = []
        for key, value in changes:
            rows = copy.deepcopy(base)
            rows[1][rows[0].index(key)] = value
            mutants.append(rows)
        mutants += [base[:-1], base + [base[-1]]]
        rows = copy.deepcopy(base)
        rows[1].append("1")
        mutants.append(rows)
        self.assertEqual(12, len(mutants))
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for index, rows in enumerate(mutants):
                with self.subTest(mutant=index):
                    native = directory / "corrupt.csv"
                    with native.open("w", newline="") as stream:
                        csv.writer(stream).writerows(rows)
                    # A stale successful receipt must be removed even if validation fails immediately.
                    (directory / "srk-summary.json").write_text("stale success")
                    with self.assertRaises(ValueError):
                        qualify_srk.evaluate(native, directory)
                    self.assertFalse((directory / "srk-summary.json").exists())


if __name__ == "__main__":
    unittest.main()
