"""Analytical conservation and fuel-conversion checks without Cantera."""

import unittest

from combustion_diagnostics import (
    combustion_branch, conservation_diagnostics, projection_diagnostics,
)


class CombustionDiagnosticsTest(unittest.TestCase):
    """Prevent mass-only projection checks from hiding hydrogen omission."""

    def test_zone_gate_rejects_hydrogen_loss_even_when_mass_is_preserved(self):
        """Trace hydrogen loss cannot be absorbed into the dominant nitrogen flow."""
        with self.assertRaisesRegex(ValueError, "burner 4: element H"):
            conservation_diagnostics(
                {"H2": 1.0e-8, "N2": 1.0},
                {"N2": 1.0 + 2.0e-11 / 0.028},
                {"H2": 0.002, "N2": 0.028},
                {"H2": {"H": 2}, "N2": {"N": 2}}, "burner 4",
            )

    def test_zone_gate_rejects_unexpected_element(self):
        """An absent inlet element must not disappear into a null relative residual."""
        with self.assertRaisesRegex(ValueError, "post-flame: element H"):
            conservation_diagnostics(
                {"N2": 1.0}, {"N2": 1.0, "H2": 1.0e-15},
                {"N2": 0.028, "H2": 0.002},
                {"N2": {"N": 2}, "H2": {"H": 2}}, "post-flame",
            )

    def test_zone_gate_keeps_trace_inventory_and_zero_element(self):
        result = conservation_diagnostics(
            {"H2": 1.0e-12, "N2": 1.0}, {"H": 2.0e-12, "N2": 1.0},
            {"H2": 0.002, "H": 0.001, "N2": 0.028, "O2": 0.032},
            {"H2": {"H": 2}, "H": {"H": 1}, "N2": {"N": 2}, "O2": {"O": 2}},
            "mixer",
        )
        self.assertEqual(result["elementBalances"]["H"]["relativeResidual"], 0.0)
        self.assertEqual(result["elementBalances"]["O"]["outletMolAtomsPerSecond"], 0.0)
        self.assertIsNone(result["elementBalances"]["O"]["relativeResidual"])

    def test_zone_gate_rejects_nonfinite_species(self):
        for flow in (float("nan"), float("inf")):
            with self.assertRaisesRegex(ValueError, "must be finite"):
                conservation_diagnostics({"N2": 1.0}, {"N2": flow},
                                         {"N2": 0.028}, {"N2": {"N": 2}}, "mixer")

    def test_small_mass_omission_can_remove_all_hydrogen(self):
        atoms = {"H2": {"H": 2}, "H": {"H": 1}, "N2": {"N": 2}}
        masses = {"H2": 0.002, "H": 0.001, "N2": 0.028}
        result = projection_diagnostics(
            {"H2": 1.0e-8, "N2": 1.0},
            {"H": 2.0e-8, "N2": 1.0}, masses, atoms,
            {"hydrogen": "H2", "nitrogen": "N2"},
        )
        self.assertLess(result["unmappedMechanismMassFraction"], 1.0e-6)
        hydrogen = result["elementProjectionDiagnostics"]["H"]
        self.assertEqual(hydrogen["omittedInletElementFraction"], 1.0)
        self.assertEqual(hydrogen["projectionRelativeResidual"], -1.0)
        self.assertEqual(hydrogen["mechanismRelativeResidual"], 0.0)
        self.assertEqual(hydrogen["omittedSpeciesMolAtomsPerSecond"], {"H": 2.0e-8})

    def test_supported_oxygenate_keeps_its_atoms(self):
        atoms = {"CH3OH": {"C": 1, "H": 4, "O": 1}}
        result = projection_diagnostics(
            {"CH3OH": 3.0}, {"CH3OH": 3.0}, {"CH3OH": 0.032}, atoms,
            {"methanol": "CH3OH"},
        )
        self.assertEqual(result["unmappedMechanismMassFraction"], 0.0)
        for balance in result["elementProjectionDiagnostics"].values():
            self.assertEqual(balance["projectionRelativeResidual"], 0.0)

    def test_unexpected_element_is_not_hidden_by_relative_normalization(self):
        result = projection_diagnostics(
            {"N2": 1.0}, {"N2": 1.0, "H2": 0.1},
            {"N2": 0.028, "H2": 0.002}, {"N2": {"N": 2}, "H2": {"H": 2}},
            {"nitrogen": "N2", "hydrogen": "H2"},
        )
        hydrogen = result["elementProjectionDiagnostics"]["H"]
        self.assertIsNone(hydrogen["mechanismRelativeResidual"])
        self.assertEqual(hydrogen["outletMolAtomsPerSecond"], 0.2)
        self.assertGreater(result["mechanismMassRelativeResidual"], 0.0)

    def test_hydrogen_fuel_requires_energy_conversion(self):
        self.assertEqual(combustion_branch(900.0, 1000.0, 0.0, 0.0, 0.0, 0.0), "BURNING")
        self.assertNotEqual(combustion_branch(900.0, 1000.0, 50.0, 0.0, 0.0, 0.0), "BURNING")

    def test_carbon_conversion_cannot_hide_hydrogen_slip(self):
        self.assertNotEqual(combustion_branch(900.0, 1000.0, 50.0, 1.0, 0.0, 1.0), "BURNING")
        self.assertEqual(combustion_branch(900.0, 1000.0, 0.0, 1.0, 0.0, 1.0), "BURNING")

    def test_no_heat_or_no_fuel_is_not_burning(self):
        self.assertNotEqual(combustion_branch(0.0, 1000.0, 0.0, 1.0, 0.0, 1.0), "BURNING")
        self.assertNotEqual(combustion_branch(100.0, 0.0, 0.0, 0.0, 0.0, 0.0), "BURNING")


if __name__ == "__main__":
    unittest.main()
