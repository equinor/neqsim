import json
import math
import os
import unittest
from pathlib import Path

from jsonschema import ValidationError
from phreeqpy.iphreeqc.phreeqc_dll import IPhreeqc
from study import build_blends, run_study, validate_case

DATABASE = Path(os.environ["PRODUCED_WATER_PITZER_DATABASE"]).resolve()


class CompatibilityStudyTest(unittest.TestCase):
    def setUp(self):
        self.case = json.loads(Path(__file__).with_name("case.json").read_text())

    def test_pairwise_end_members_and_ternary_mix(self):
        blends = build_blends(self.case)
        self.assertEqual(len(blends), 34)
        self.assertIn((1, 0, 0), blends)
        self.assertIn((0, 1, 0), blends)
        self.assertIn((0, 0, 1), blends)
        self.assertIn((1 / 3, 1 / 3, 1 / 3), blends)

    def test_invalid_basis_and_unknown_assay_species(self):
        self.case["additional_blends"] = [[0.1, 0.2, 0.3]]
        with self.assertRaises(ValueError):
            validate_case(self.case)
        self.case["additional_blends"] = []
        self.case["formations"][0]["ions_mol_kg_water"]["iron"] = 1e-4
        with self.assertRaises(ValidationError):
            validate_case(self.case)

    def test_amorphous_silica_solubility_against_marshall_1980(self):
        # Marshall, GCA (1980), doi:10.1016/0016-7037(80)90281-1:
        # measured 0.00218 mol/kg water at 25 C without added salt.
        engine = IPhreeqc()
        try:
            engine.load_database(str(DATABASE))
            self.assertEqual(engine.phc_error_count, 0)
            engine.run_string("""SELECTED_OUTPUT
-reset false
-high_precision true
-totals Si
-saturation_indices SiO2(a)
SOLUTION 1
temp 25
pH 7
EQUILIBRIUM_PHASES 1
SiO2(a) 0 1
END""")
            result = engine.get_selected_output_array()[-1]
            self.assertAlmostEqual(result[0], 0.00218, delta=0.00011)
            self.assertAlmostEqual(result[1], 0, delta=1e-8)
        finally:
            engine.destroy_iphreeqc()

    def test_full_case_process_and_chemistry(self):
        rows = run_study(self.case, DATABASE)["rows"]
        self.assertEqual(len(rows), 204)
        for row in rows:
            self.assertLess(row["neqsim_component_closure_rel"], 1e-7)
            self.assertLess(row["silicon_closure_mol_kg_water"], 1e-8)
            self.assertLess(row["major_ion_closure_mol_kg_water"], 1e-8)
            self.assertTrue(all(row[phase + "_kg_s"] > 0 for phase in ("gas", "oil", "aqueous")))
            if row["boundary"] == "fixed_eos_co2":
                self.assertAlmostEqual(row["log10_co2_fugacity_atm"],
                                       math.log10(row["co2_fugacity_bar"] / 1.01325), delta=1e-7)
            else:
                self.assertLess(row["closed_dic_closure_mol_kg_water"], 1e-8)

        fixed = [row for row in rows if row["boundary"] == "fixed_eos_co2"]
        blend = [row for row in fixed if math.isclose(row["solvent_water_fraction_A"], 1 / 3)]
        self.assertEqual(len(blend), 3)
        self.assertLess(blend[0]["si_SiO2(a)"], 0)
        self.assertGreater(blend[-1]["si_SiO2(a)"], 0)
        self.assertAlmostEqual(blend[-1]["si_amorphous_after_equilibrium"], 0, delta=1e-7)
        highest_barite = max(fixed, key=lambda row: row["si_Barite"])
        self.assertAlmostEqual(highest_barite["solvent_water_fraction_A"], 0.4)
        self.assertAlmostEqual(highest_barite["solvent_water_fraction_B"], 0.6)


if __name__ == "__main__":
    unittest.main()
