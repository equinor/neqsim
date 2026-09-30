"""Analytical conservation and fuel-conversion checks without Cantera."""

import unittest

from combustion_diagnostics import combustion_branch, projection_diagnostics


class CombustionDiagnosticsTest(unittest.TestCase):
    """Prevent mass-only projection checks from hiding hydrogen omission."""

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
