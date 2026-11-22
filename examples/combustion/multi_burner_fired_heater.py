"""Executable native NeqSim + Cantera gas-fired hot-oil heater example.

Run after building NeqSim: python examples/combustion/multi_burner_fired_heater.py
    --project-root . --mechanism gri30.yaml
The optional --classpath accepts explicit Java class directories/JARs for an
isolated validation runtime. Geometry and material properties are illustrative.
"""

import argparse
import json
import math
from pathlib import Path
import sys

import cantera as ct

from cantera_backend import CanteraBackend
from kinetic_network import _chemical_power


def initialize_runtime(project_root, classpath=None):
    """Load workspace Java classes before attaching the optional kinetics backend."""
    import jpype

    if classpath:
        jpype.startJVM(classpath=classpath, convertStrings=True)
    else:
        sys.path.insert(0, str(Path(project_root) / "devtools"))
        from neqsim_dev_setup import neqsim_init

        neqsim_init(project_root=project_root)
    return jpype.JClass


def make_stream(java_class, name, composition, temperature, mass_flow, pressure_bar=1.01325):
    """Create a nonreactive SRK stream; all combustion physics stays in the mechanism."""
    fluid = java_class("neqsim.thermo.system.SystemSrkEos")(temperature, pressure_bar)
    for component, amount in composition.items():
        fluid.addComponent(component, amount)
    fluid.createDatabase(True)
    fluid.setMixingRule(2)
    fluid.setTotalFlowRate(mass_flow, "kg/sec")
    return java_class("neqsim.process.equipment.stream.Stream")(name, fluid)


def build_case(java_class, backend, firing_mw=30.0):
    """Build a 10–40 MW-scalable synthetic heater, with explicit physical scaling."""
    fuel_gas = ct.Solution(backend.mechanism)
    fuel_gas.TPX = 300.0, ct.one_atm, {"C2H6": 0.5, "C3H8": 0.5}
    heating_value, reference_enthalpy = _chemical_power(fuel_gas, 1.0, backend)
    fuel_mass = firing_mw * 1.0e6 / heating_value
    fuel_moles = fuel_mass * 1000.0 / fuel_gas.mean_molecular_weight
    oxygen_moles = fuel_moles * 4.25 / 0.9
    air_gas = ct.Solution(backend.mechanism)
    air_gas.TPX = 300.0, ct.one_atm, {"O2": 1.0, "N2": 3.76}
    air_mass = oxygen_moles * 4.76 * air_gas.mean_molecular_weight / 1000.0
    scale = firing_mw / 30.0
    fuel = make_stream(java_class, "fuel", {"ethane": 0.5, "propane": 0.5}, 300.0, fuel_mass)
    air = make_stream(java_class, "common air", {"oxygen": 1.0, "nitrogen": 3.76}, 300.0, air_mass)
    oil = make_stream(java_class, "hot oil", {"n-decane": 1.0}, 500.0, 100.0 * scale, 20.0)
    heater = java_class("neqsim.process.equipment.reactor.MultiBurnerFiredHeater")(
        "gas fired hot oil heater", fuel, air, 7
    )
    heater.setKineticsBackend(backend.as_java_backend())
    for index in range(7):
        heater.configureBurner(index, True, 1.0, 1.0 / 7.0, scale / 7.0)
    chamber_diameter = 2.0 * scale ** (1.0 / 3.0)
    chamber_length = 11.0 / math.pi * scale ** (1.0 / 3.0)
    heater.setChamberGeometry(chamber_diameter, chamber_length)
    heater.setTubeHeatTransfer(300.0 * scale, 200.0, 0.05, 0.0, 550.0)
    heater.setRefractory(0.2, 1.0, 0.8, 15.0, 300.0)
    heater.setIgnitionTemperature(2000.0)
    heater.setHotOilInlet(oil)
    process = java_class("neqsim.process.processmodel.ProcessSystem")()
    for equipment in (fuel, air, oil, heater):
        process.add(equipment)
    return process, heater, fuel, air, oil, fuel_mass


def summarize(heater):
    """Return current detailed diagnostics alongside the accepted native outlet."""
    result = json.loads(str(heater.getKineticsResultJson()))
    fields = (
        "temperatureK", "coPpmvDry", "coMgPerNormalM3DryAt273_15K101325Pa",
        "oxygenDryVolPercent", "coMassRateKgPerHour", "hydrocarbonCarbonFraction",
        "carbonToCO2Fraction", "branchDiagnostic", "fuelChemicalPowerW",
        "usefulHeatToOilW", "shellHeatLossW", "stackSensibleHeatW",
        "residualChemicalPowerW", "fullEnergyBalanceRelativeResidual",
        "organicCarbonFraction", "methaneMgPerNormalM3Dry",
        "organicCarbonMgCPerNormalM3Dry", "nonMethaneOrganicCarbonMgCPerNormalM3Dry",
        "pollutantAvailability",
        "zoneConservationDiagnostics", "elementProjectionDiagnostics",
        "provenance", "referenceOxygenVolPercent", "referenceOxygenCorrectionValid",
        "coMgPerNormalM3DryAtReferenceOxygen",
    )
    summary = {field: result[field] for field in fields}
    summary["unmappedMassFraction"] = heater.getUnmappedMassFraction()
    summary["projectedEosMassResidual"] = heater.getProjectedEosMassResidual()
    summary["hotOilOutletTemperatureK"] = heater.getHotOilOutlet().getTemperature()
    summary["minimumBurnerTemperatureK"] = min(
        burner["temperatureK"] for burner in result["burnerDiagnostics"]
    )
    summary["maximumBurnerTemperatureK"] = max(
        burner["temperatureK"] for burner in result["burnerDiagnostics"]
    )
    mapped_species = set(result["componentToSpecies"].values())
    omitted_elements = {}
    for element in ("C", "H", "O", "N"):
        exact = sum(
            flow * result["speciesAtomCounts"][species].get(element, 0.0)
            for species, flow in result["speciesMolarFlows"].items()
        )
        mapped = sum(
            flow * result["speciesAtomCounts"][species].get(element, 0.0)
            for species, flow in result["speciesMolarFlows"].items()
            if species in mapped_species
        )
        omitted_elements[element] = (exact - mapped) / exact if exact else 0.0
    summary["omittedElementFractions"] = omitted_elements
    return summary


def run_with_diagnostics(process, heater):
    """Keep the exact omitted-atom evidence visible when native projection fails."""
    try:
        process.run()
    except Exception:
        retained = heater.getKineticsResultJson()
        if retained is not None:
            result = json.loads(str(retained))
            print(json.dumps({
                "projectionFailureDiagnostics": result.get("elementProjectionDiagnostics"),
                "unmappedMechanismMassFraction": result.get("unmappedMechanismMassFraction"),
                "temperatureK": result.get("temperatureK"),
            }, indent=2, allow_nan=False))
        raise


def run_demonstration(java_class, backend, include_sweep=False, sweep_projection_tolerance=1.0e-3):
    """Compare installed burner states at constant total fuel and common air."""
    process, heater, fuel, air, oil, design_fuel_mass = build_case(java_class, backend)
    run_with_diagnostics(process, heater)
    seven = summarize(heater)
    for index in range(7):
        heater.configureBurner(index, index < 5, 1.0, 1.0 / 5.0, 1.0 / 7.0)
    run_with_diagnostics(process, heater)
    five = summarize(heater)
    results = {"seven_burners": seven, "five_burners_same_total_fuel_air": five}
    if include_sweep:
        # A declared, example-only projection bound for frozen oxygenates/intermediates.
        # Full mechanism species and emissions stay exact; the native default remains 1e-6.
        heater.setProjectionTolerance(sweep_projection_tolerance)
        sweep = []
        for load in (1.0, 0.6, 0.4, 0.3, 0.2, 0.1):
            fuel.getThermoSystem().setTotalFlowRate(design_fuel_mass * load, "kg/sec")
            # Explicit optional entrainment hypothesis; coefficient 2 is an assumption.
            # It is not inferred from burner count or fitted to plant CO measurements.
            captured_air_fraction = min(1.0, 2.0 * 0.9 * load)
            for index in range(7):
                heater.configureBurner(index, True, 1.0, captured_air_fraction / 7.0, 1.0 / 7.0)
            run_with_diagnostics(process, heater)
            row = summarize(heater)
            row["fuelLoadFraction"] = load
            row["assumedCapturedAirFraction"] = captured_air_fraction
            row["specifiedProjectionTolerance"] = sweep_projection_tolerance
            sweep.append(row)
        results["constant_air_optional_entrainment_hypothesis"] = sweep
    return results


def main():
    """Run an inspectable public demonstration; the selected mechanism must be available."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-root", default=str(Path(__file__).resolve().parents[2]))
    parser.add_argument("--mechanism", default="gri30.yaml")
    parser.add_argument("--classpath", nargs="+")
    parser.add_argument("--sweep", action="store_true")
    parser.add_argument("--sweep-projection-tolerance", type=float, default=1.0e-3)
    parser.add_argument("--scale-study", action="store_true")
    parser.add_argument("--output", type=Path)
    arguments = parser.parse_args()
    java_class = initialize_runtime(arguments.project_root, arguments.classpath)
    backend = CanteraBackend(arguments.mechanism)
    results = run_demonstration(
        java_class, backend, arguments.sweep, arguments.sweep_projection_tolerance
    )
    if arguments.scale_study:
        scaled = []
        for firing_mw in (10.0, 20.0, 30.0, 40.0):
            process, heater, fuel, air, oil, fuel_mass = build_case(java_class, backend, firing_mw)
            run_with_diagnostics(process, heater)
            row = summarize(heater)
            row["nominalFiringMW"] = firing_mw
            scaled.append(row)
        results["explicit_geometry_heat_transfer_scaling"] = scaled
    text = json.dumps(results, indent=2, allow_nan=False)
    print(text)
    if arguments.output:
        arguments.output.write_text(text + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
