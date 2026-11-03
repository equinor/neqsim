"""Guard source direction, loading-feed limits and uncertainty interpretation."""

import copy
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

from audit_huron_vidal_natural_gas import (
    HASHES, ROOT, audit, check_gas, check_states, number, project_observations, read_rows)


class NaturalGasReceiptTest(unittest.TestCase):
    """Keep measured data usable without turning incomplete inputs into a fit."""

    def setUp(self):
        """Load the primary numeric transcriptions independently for mutations."""
        self.observations = read_rows((ROOT / "observations.csv").read_bytes())
        self.states = read_rows((ROOT / "states.csv").read_bytes())
        self.gas = read_rows((ROOT / "gas-feed.csv").read_bytes())

    def test_complete_source_matrix(self):
        """Retain missing cells and every measured quantity in its own direction."""
        result = audit()
        self.assertEqual((11, 220, 218, 18), tuple(result[k] for k in (
            "states", "source_observation_slots", "retained_observations", "loading_gas_components")))
        self.assertEqual({"y": 20, "x": 99, "y/x": 99}, result["quantities"])
        self.assertEqual([6, 9], result["missing_vapor_MEG_experiments"])
        self.assertEqual([[3, "y", "water"], [4, "x", "i-butane"], [5, "y", "water"]],
                         json.loads(json.dumps(result["printed_zero_RSD_cells"])))

    def test_resolvable_numeric_projection_preserves_units(self):
        """Manually resolvable ppm examples cannot acquire a millionfold error."""
        rows = project_observations(self.observations)
        self.assertEqual(number("0.00000069"), rows[0]["value"])
        self.assertEqual(number("0.0000911"), rows[1]["value"])
        self.assertEqual(number("0.001375"), rows[2]["value"])
        self.assertEqual(number("16.1"), rows[119]["value"])
        self.assertEqual("y/x", rows[119]["quantity"])
        self.assertEqual(number("2.7"), rows[119]["relative_standard_deviation_percent"])

    def test_zero_repeatability_and_excluded_average_are_retained(self):
        """Rounded zero RSD is data; footnote b does not delete observations."""
        rows = project_observations(self.observations)
        excluded = [r for r in rows if r["uncertainty_footnote"] == "b"]
        self.assertEqual([10, 11], [r["experiment"] for r in excluded])
        self.assertEqual([number("15.6"), number("5.7")],
                         [r["relative_standard_deviation_percent"] for r in excluded])
        self.assertEqual(3, sum(r["relative_standard_deviation_percent"] == 0 for r in rows))

    def test_missing_observations_cannot_be_filled_with_zero_or_interpolation(self):
        """Absent high-temperature MEG values have no numeric surrogate."""
        for value in ("0", "1.0", "NaN"):
            rows = copy.deepcopy(self.observations)
            rows[55]["value_as_printed"] = value
            with self.subTest(value=value), self.assertRaises(ValueError):
                project_observations(rows)

    def test_phase_direction_units_and_footnotes(self):
        """Reject reciprocal K, mass ppm, negative RSD or lost source exclusions."""
        for index, field, value in ((0, "quantity", "x"), (119, "quantity", "x/y"),
                                    (0, "unit", "ppm_mass_fraction"),
                                    (100, "uncertainty_footnote", "-"),
                                    (0, "relative_standard_deviation_percent", "-1")):
            rows = copy.deepcopy(self.observations)
            rows[index][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                project_observations(rows)

    def test_missing_duplicated_reordered_and_relabelled_data(self):
        """Joining on nearest T/P must not replace the source experiment identity."""
        for mutation in (lambda r: r.pop(), lambda r: r.append(r[0]),
                         lambda r: r.reverse(), lambda r: r[0].update(component="methanol")):
            rows = copy.deepcopy(self.observations)
            mutation(rows)
            with self.assertRaises(ValueError):
                project_observations(rows)

    def test_loading_states_and_purity_inequalities(self):
        """Keep exact loading T/P separately from nominal measurement labels."""
        result = check_states(self.states)
        self.assertEqual(["288.18", "323.17"], result["temperature_range_K"])
        self.assertEqual(["5.99", "12.51"], result["pressure_range_MPa"])
        self.assertEqual(11, result["nominal_temperature_differs_from_loading_state"])
        self.assertEqual(5, result["nominal_pressure_differs_from_loading_state"])
        for field, value in (("wMEG_feed_table5_g_g", "0.998"),
                             ("wMEG_feed_table5_g_g", "1.0"), ("Vgas_table5_mL", "0")):
            rows = copy.deepcopy(self.states)
            rows[9][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                check_states(rows)

    def test_loading_gas_alias_and_lumps_are_not_silently_corrected(self):
        """Name/CAS disambiguate a duplicated alias; totals have no pure CAS."""
        self.assertEqual(number("100.00022"), number(check_gas(self.gas)))
        for index, field, value in ((12, "subscript_as_printed", "23DMC4"),
                                    (12, "CAS", "75-83-2"),
                                    (16, "CAS", "142-82-5")):
            rows = copy.deepcopy(self.gas)
            rows[index][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                check_gas(rows)

    def test_nonfinite_and_negative_source_values(self):
        """Fail before a missing or invalid measurement can reach a model."""
        for field, value in (("value_as_printed", "NaN"), ("value_as_printed", "Infinity"),
                             ("value_as_printed", "-1"),
                             ("relative_standard_deviation_percent", "NaN")):
            rows = copy.deepcopy(self.observations)
            rows[0][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                project_observations(rows)

    def test_byte_identity_and_self_rehashed_transcription(self):
        """A receipt cannot authorize its own edited observations or state."""
        for name in HASHES:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temp:
                directory = Path(temp)
                for file in (*HASHES, "provenance.json"):
                    shutil.copyfile(ROOT / file, directory / file)
                path = directory / name
                path.write_bytes(path.read_bytes() + b"\n")
                metadata = json.loads((directory / "provenance.json").read_text())
                metadata["files"][name] = hashlib.sha256(path.read_bytes()).hexdigest()
                (directory / "provenance.json").write_text(json.dumps(metadata))
                with self.assertRaises(ValueError):
                    audit(directory)

    def test_metadata_cannot_promote_or_conflate_uncertainty(self):
        """No source-model transfer, measured-molar-feed or accuracy promotion."""
        for field, value in (("parameter_activation", True), ("measured_overall_molar_feed", True),
                             ("status", "qualified"), ("source_model", "SRK-HV"), ("missing", []),
                             ("uncertainty", {"composition": "expanded 95 percent uncertainty"})):
            with self.subTest(field=field), tempfile.TemporaryDirectory() as temp:
                directory = Path(temp)
                for name in HASHES:
                    shutil.copyfile(ROOT / name, directory / name)
                metadata = json.loads((ROOT / "provenance.json").read_text())
                metadata[field] = value
                (directory / "provenance.json").write_text(json.dumps(metadata))
                with self.assertRaises(ValueError):
                    audit(directory)

    def test_primary_conflicts_and_apparatus_uncertainty_cannot_be_repaired(self):
        """Do not silently change the author's species, basis or density unit."""
        for parent, field, value in (
                ("source_conflicts", "printed23_PDF24_model_assumption_species_as_printed", "MEG"),
                ("source_conflicts", "table3_MEG_water_content_percent_upper_bound", "0.2"),
                ("apparatus_table2", "density_uncertainty_unit_as_printed", "g mL^-1"),
                ("apparatus_table2", "relative_percent", {})):
            with self.subTest(field=field), tempfile.TemporaryDirectory() as temp:
                directory = Path(temp)
                for name in HASHES:
                    shutil.copyfile(ROOT / name, directory / name)
                metadata = json.loads((ROOT / "provenance.json").read_text())
                metadata[parent][field] = value
                (directory / "provenance.json").write_text(json.dumps(metadata))
                with self.assertRaises(ValueError):
                    audit(directory)

    def test_documented_cli_is_read_only_and_optimized(self):
        """Execute the documented example and preserve input bytes with -O."""
        paths = [ROOT / n for n in (*HASHES, "provenance.json")]
        before = [p.read_bytes() for p in paths]
        script = Path(__file__).with_name("audit_huron_vidal_natural_gas.py")
        process = subprocess.run([sys.executable, "-O", str(script)], check=True,
                                 capture_output=True, text=True)
        self.assertEqual(218, json.loads(process.stdout)["retained_observations"])
        self.assertEqual(before, [p.read_bytes() for p in paths])


if __name__ == "__main__":
    unittest.main()
