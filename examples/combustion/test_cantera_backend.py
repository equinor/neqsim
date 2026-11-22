"""Executed physics/API regression tests for the optional Cantera 3.2 adapter."""

import copy
import json
import unittest

import cantera as ct

from cantera_backend import CanteraBackend
from kinetic_network import _species_flows, _zone_conservation


def heater_request():
    """Public synthetic 30 MW-scale C2/C3 geometry, independent of private measurements."""
    return {
        "schemaVersion": 1,
        "reactorModel": "MULTI_BURNER_FIRED_HEATER",
        "pressurePa": 101325.0,
        "fuelTemperatureK": 300.0,
        "airTemperatureK": 300.0,
        "fuelMolarFlows": {"ethane": 8.68, "propane": 8.68},
        "airMolarFlows": {"oxygen": 82.0, "nitrogen": 82.0 * 3.76},
        "burners": [
            {
                "id": index,
                "enabled": True,
                "fuelWeight": 1.0,
                "airCaptureFraction": 1.0 / 7.0,
                "volumeM3": 1.0 / 7.0,
            }
            for index in range(7)
        ],
        "ignitionTemperatureK": 2000.0,
        "commonReactiveVolumeM3": 10.0,
        "tubeAreaM2": 300.0,
        "tubeConvectionWPerM2K": 200.0,
        "tubeEffectiveEmissivity": 0.05,
        "primaryHeatingAreaFraction": 0.0,
        "heatSinkTemperatureK": 550.0,
        "refractoryAreaM2": 25.0,
        "refractoryThicknessM": 0.2,
        "refractoryConductivityWPerMK": 1.0,
        "refractoryEffectiveEmissivity": 0.8,
        "refractoryConvectionWPerM2K": 15.0,
        "ambientTemperatureK": 300.0,
    }


class CanteraBackendTest(unittest.TestCase):
    """Conservation, nonlinear chemistry, branch and reproducibility checks."""

    @classmethod
    def setUpClass(cls):
        """Use the bundled demonstration mechanism, without a propane qualification claim."""
        cls.backend = CanteraBackend("gri30.yaml")

    def test_supported_stable_species_are_projected(self):
        """Keep actual methanol and ammonia, with their independently supported atoms."""
        self.assertEqual(self.backend.mapping["methanol"], "CH3OH")
        self.assertEqual(self.backend.mapping["ammonia"], "NH3")

    def test_hydrogen_heater_has_no_carbon_division(self):
        """Refined carbon-free solve must preserve even absent-element invariants."""
        request = heater_request()
        request["fuelMolarFlows"] = {"hydrogen": 120.0}
        # Scalar CVODES atol=1e-18 can generate absent-C roundoff above the native
        # 1e-27 mol-atoms/s absolute gate. Tighten integration, never that gate.
        refined = CanteraBackend("gri30.yaml", absolute_tolerance=1.0e-28)
        result = json.loads(refined.solve(json.dumps(request)))
        self.assertGreater(result["usefulHeatToOilW"], 0.0)
        self.assertEqual(result["branchDiagnostic"], "BURNING")
        self.assertLess(abs(result["fullEnergyBalanceRelativeResidual"]), 1.0e-6)
        self.assertLess(abs(result["postFlameVolumeRelativeResidual"]), 1.0e-6)
        for element in ("H", "O", "N"):
            diagnostic = result["elementProjectionDiagnostics"][element]
            self.assertLess(abs(diagnostic["mechanismRelativeResidual"]), 1.0e-7)
        self.check_zones(result, 16)

    def check_zones(self, result, count):
        """Check all elements, including absent elements, at every retained boundary."""
        self.assertEqual(len(result["zoneConservationDiagnostics"]), count)
        for zone in result["zoneConservationDiagnostics"]:
            self.assertLessEqual(abs(zone["massBalanceRelativeResidual"]), 1.0e-7)
            self.assertEqual(set(zone["elementBalances"]), {"C", "H", "O", "N", "Ar"})
            for balance in zone["elementBalances"].values():
                self.assertLessEqual(abs(balance["acceptanceScaledResidual"]), 1.0e-7)

    def test_absent_element_contamination_fails_closed(self):
        """Tiny spurious carbon cannot pass an otherwise mass-balanced N2 state."""
        gas = ct.Solution("gri30.yaml")
        gas.TPX = 600.0, 101325.0, {"N2": 1.0, "CH4": 1.0e-15}
        atoms = {s: {e: gas.n_atoms(s, e) for e in gas.element_names}
                 for s in gas.species_names}
        masses = dict(zip(gas.species_names, gas.molecular_weights / 1000.0))
        with self.assertRaisesRegex(ValueError, "post-flame: element C"):
            _zone_conservation({"N2": 1.0}, gas, 0.028014, masses, atoms, "post-flame")
        fractions = [0.0] * gas.n_species
        fractions[gas.species_index("N2")] = 1.0
        fractions[gas.species_index("CH4")] = -1.0e-5
        gas.set_unnormalized_mass_fractions(fractions)
        with self.assertRaisesRegex(ValueError, "invalid species mass fractions"):
            _species_flows(gas, 0.028014)

    def test_bypass_air_and_primary_heat_preserve_zone_inventories(self):
        """Stage 15% common-air bypass and independently transfer heat in burner zones."""
        request = heater_request()
        request["primaryHeatingAreaFraction"] = 0.02
        for burner in request["burners"]:
            burner["airCaptureFraction"] = 0.85 / 7.0
        result = json.loads(self.backend.solve(json.dumps(request)))
        self.check_zones(result, 16)
        self.assertGreater(result["usefulHeatToOilW"], 20.0e6)
        self.assertLess(abs(result["fullEnergyBalanceRelativeResidual"]), 1.0e-6)

    def test_stable_mechanism_fingerprint(self):
        """Generated timestamps and phase state must not alter mechanism identity."""
        self.assertEqual(
            self.backend.mechanism_fingerprint,
            CanteraBackend("gri30.yaml").mechanism_fingerprint,
        )

    def test_reject_unmapped_inlet_and_duplicate_aliases(self):
        """Never substitute generic properties or silently drop a supply component."""
        with self.assertRaises(ValueError):
            CanteraBackend("gri30.yaml", {"methane": "CH4", "fake": "CH4"})
        request = heater_request()
        request["fuelMolarFlows"]["n-butane"] = 1.0
        with self.assertRaisesRegex(ValueError, "Unsupported"):
            self.backend.solve(json.dumps(request))

    def test_finite_age_wall_heat_is_independently_integrated(self):
        """Nonzero wall heat must close against the separately evolved NASA enthalpy."""
        request = {
            "schemaVersion": 1,
            "reactorModel": "CONSTANT_PRESSURE_PFR",
            "temperatureK": 1600.0,
            "pressurePa": 101325.0,
            "residenceTimeSeconds": 0.02,
            "heatLossCoefficientWPerKgK": 200.0,
            "surroundingsTemperatureK": 550.0,
            "componentMolarFlows": {"ethane": 1.0, "oxygen": 7.0, "nitrogen": 26.32},
        }
        result = json.loads(self.backend.solve(json.dumps(request)))
        self.assertGreater(result["heatTransferToSurroundingsW"], 1000.0)
        self.assertLess(abs(result["energyBalanceRelativeResidual"]), 1.0e-7)
        self.assertGreater(result["speciesMolarFlows"]["CO"], 0.0)

    def test_heater_energy_elements_and_radiation(self):
        """Separate useful oil duty, shell loss, stack energy and chemical fuel slip."""
        request = heater_request()
        result = json.loads(self.backend.solve(json.dumps(request)))
        self.assertGreater(result["usefulHeatToOilW"], 20.0e6)
        self.check_zones(result, 16)
        self.assertGreater(result["shellHeatLossW"], 0.0)
        self.assertLess(result["stackSensibleHeatW"], 7.0e6)
        self.assertLess(abs(result["fullEnergyBalanceRelativeResidual"]), 1.0e-6)
        self.assertLess(result["hydrocarbonCarbonFraction"], 1.0e-4)
        self.assertEqual(result["branchDiagnostic"], "BURNING")
        self.assertLess(abs(result["postFlameVolumeRelativeResidual"]), 1.0e-6)
        for burner in result["burnerDiagnostics"]:
            self.assertLess(abs(burner["massBalanceRelativeResidual"]), 1.0e-7)
        for element in ("C", "H", "O", "N"):
            initial = sum(
                value * result["speciesAtomCounts"][self.backend.mapping[component]].get(element, 0.0)
                for group in ("fuelMolarFlows", "airMolarFlows")
                for component, value in request[group].items()
            )
            final = sum(
                value * result["speciesAtomCounts"][species].get(element, 0.0)
                for species, value in result["speciesMolarFlows"].items()
            )
            self.assertLess(abs(final / initial - 1.0), 1.0e-7)
        no_radiation = copy.deepcopy(request)
        no_radiation["tubeEffectiveEmissivity"] = 0.0
        no_radiation["refractoryEffectiveEmissivity"] = 0.0
        without = json.loads(self.backend.solve(json.dumps(no_radiation)))
        self.assertGreater(result["usefulHeatToOilW"], without["usefulHeatToOilW"])

    def test_switching_preserves_supplied_fuel_and_air(self):
        """Seven versus five lit burners is an explicit redistribution, not fuel deletion."""
        request = heater_request()
        seven = json.loads(self.backend.solve(json.dumps(request)))
        for index, burner in enumerate(request["burners"]):
            burner["enabled"] = index < 5
            burner["airCaptureFraction"] = 1.0 / 5.0
        five = json.loads(self.backend.solve(json.dumps(request)))
        self.check_zones(five, 12)
        self.assertEqual(len(five["burnerDiagnostics"]), 5)
        self.assertAlmostEqual(five["fuelChemicalPowerW"], seven["fuelChemicalPowerW"])
        self.assertGreater(five["burnerDiagnostics"][0]["fuelMassFlowKgPerSecond"],
                           seven["burnerDiagnostics"][0]["fuelMassFlowKgPerSecond"])
        self.assertNotEqual(five["coPpmvDry"], seven["coPpmvDry"])
        self.assertLess(five["hydrocarbonCarbonFraction"], 1.0e-4)


if __name__ == "__main__":
    unittest.main()
