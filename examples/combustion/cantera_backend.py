"""Optional detailed-chemistry adapter for FiniteRateCombustionReactor.

Requires Cantera >= 3.2 and JPype for attachment. There is no mechanism download,
rate fit, CO emission factor, equilibrium initialization or automatic ignition.
The selected mechanism owns ideal-gas NASA thermochemistry. NeqSim owns only
the separately checked stable-species EOS projection and downstream equipment.
"""

import hashlib
import json
import math

import cantera as ct


DEFAULT_COMPONENT_TO_SPECIES = {
    "methane": "CH4",
    "ethane": "C2H6",
    "propane": "C3H8",
    "n-butane": "NC4H10",
    "i-butane": "IC4H10",
    "ethylene": "C2H4",
    "hydrogen": "H2",
    "oxygen": "O2",
    "nitrogen": "N2",
    "water": "H2O",
    "CO": "CO",
    "CO2": "CO2",
    "argon": "AR",
    "NO": "NO",
    "NO2": "NO2",
    "N2O": "N2O",
}


class HeatTrackingParcel(ct.ExtensibleIdealGasConstPressureReactor):
    """Independently integrate the prescribed wall heat for a finite-age parcel."""

    def __init__(self, *args, heat_coefficient, surroundings_temperature, **kwargs):
        super().__init__(*args, **kwargs)
        self.heat_coefficient = heat_coefficient
        self.surroundings_temperature = surroundings_temperature
        self.wall_heat_j_per_kg = 0.0

    def after_initialize(self, initial_time):
        """Add an integral state to the reactor equations."""
        self.heat_index = self.n_vars
        self.n_vars += 1

    def after_get_state(self, state):
        """Export accumulated imposed wall heat."""
        state[self.heat_index] = self.wall_heat_j_per_kg

    def after_update_state(self, state):
        """Restore accumulated imposed wall heat."""
        self.wall_heat_j_per_kg = state[self.heat_index]

    def after_eval(self, time, left_hand_side, right_hand_side):
        """Integrate physical wall heat separately from the enthalpy equation."""
        left_hand_side[self.heat_index] = 1.0
        right_hand_side[self.heat_index] = self.heat_coefficient * (
            self.T - self.surroundings_temperature
        )


class CanteraBackend:
    """Solve a caller-selected ideal-gas mechanism with an explicit reactor model.

    ``PERFECTLY_STIRRED`` solves a steady PSR with cold reactant inflow and a
    separately initialized tank temperature. Hot initialization selects a
    candidate burning branch; cold or extinguished steady states are retained.
    ``CONSTANT_PRESSURE_PFR`` evolves a homogeneous parcel for the specified age;
    an elevated inlet temperature therefore represents physical inlet heating.

    The mass-specific wall law is Q = coefficient * mass * (T - surroundings).
    A PSR uses constant pressure and equal inlet/outlet mass flows, so mass is
    constant while volume changes during the numerical approach to steady state.
    Its steady equivalent volume is inferred from flow, residence and density.
    This is a steady-state solver, not a physical vessel startup simulation.
    """

    def __init__(
        self,
        mechanism,
        component_to_species=None,
        relative_tolerance=1.0e-9,
        absolute_tolerance=1.0e-18,
        steady_threshold=1.0e-7,
        maximum_steps=20000,
    ):
        self.mechanism = str(mechanism)
        self.mapping = dict(component_to_species or DEFAULT_COMPONENT_TO_SPECIES)
        if len(set(self.mapping.values())) != len(self.mapping):
            raise ValueError("Component-to-species mapping must be one-to-one")
        self.relative_tolerance = _positive(relative_tolerance, "relative tolerance")
        self.absolute_tolerance = _positive(absolute_tolerance, "absolute tolerance")
        self.steady_threshold = _positive(steady_threshold, "steady threshold")
        if self.steady_threshold <= self.relative_tolerance:
            raise ValueError("Steady threshold must exceed solver relative tolerance")
        self.maximum_steps = int(maximum_steps)
        if self.maximum_steps < 1:
            raise ValueError("Maximum steps must be positive")
        probe = ct.Solution(self.mechanism)
        if probe.thermo_model != "ideal-gas":
            raise ValueError("This adapter requires ideal-gas mechanism thermochemistry")
        if component_to_species is None and "C4H10" in probe.species_names:
            self.mapping["n-butane"] = "C4H10"
        self.mapping = {
            component: species
            for component, species in self.mapping.items()
            if species in probe.species_names
        }
        phase_data = dict(probe.input_data)
        phase_data.pop("state", None)
        resolved = {
            "phase": phase_data,
            "species": [dict(species.input_data) for species in probe.species()],
            "reactions": [dict(reaction.input_data) for reaction in probe.reactions()],
        }
        canonical = json.dumps(resolved, sort_keys=True, separators=(",", ":"))
        self.mechanism_fingerprint = hashlib.sha256(canonical.encode("utf-8")).hexdigest()

    def as_java_backend(self):
        """Return a JPype interface proxy; the JVM must already be running."""
        import jpype

        return jpype.JProxy(
            "neqsim.process.util.combustion.CombustionKineticsBackend",
            inst=self,
        )

    def solve(self, request_json):
        """Solve the Java version-1 SI-unit contract and return exact-state JSON."""
        request = json.loads(str(request_json))
        if request["schemaVersion"] != 1:
            raise ValueError("Unsupported combustion request schema")
        if request["reactorModel"] == "MULTI_BURNER_FIRED_HEATER":
            from kinetic_network import solve_fired_heater

            return json.dumps(solve_fired_heater(request, self), allow_nan=False)
        temperature = _positive(request["temperatureK"], "inlet temperature")
        pressure = _positive(request["pressurePa"], "absolute pressure")
        residence = _positive(request["residenceTimeSeconds"], "residence time")
        surroundings = _positive(request["surroundingsTemperatureK"], "surroundings")
        heat_coefficient = float(request["heatLossCoefficientWPerKgK"])
        if not math.isfinite(heat_coefficient) or heat_coefficient < 0.0:
            raise ValueError("Heat-loss coefficient must be finite and nonnegative")
        inlet_flows = {}
        for component, value in request["componentMolarFlows"].items():
            flow = float(value)
            if not math.isfinite(flow) or flow < 0.0:
                raise ValueError("Inlet molar flows must be finite and nonnegative")
            if flow == 0.0:
                continue
            if component not in self.mapping:
                raise ValueError(f"No mechanism mapping for inlet component {component}")
            inlet_flows[self.mapping[component]] = flow
        if not inlet_flows:
            raise ValueError("Positive inlet molar flow is required")
        gas = ct.Solution(self.mechanism)
        gas.TPX = temperature, pressure, inlet_flows
        inlet_enthalpy = float(gas.enthalpy_mass)
        inlet_mass_flow = sum(
            flow * gas.molecular_weights[gas.species_index(species)] / 1000.0
            for species, flow in inlet_flows.items()
        )
        model = request["reactorModel"]
        if model == "PERFECTLY_STIRRED":
            upstream = ct.Reservoir(gas, clone=True)
            downstream = ct.Reservoir(gas, clone=True)
            ignition = _positive(
                request.get("ignitionTemperatureK", temperature),
                "initial PSR temperature",
            )
            gas.TPX = ignition, pressure, inlet_flows
        elif model != "CONSTANT_PRESSURE_PFR":
            raise ValueError(f"Unsupported reactor model {model}")
        if model == "PERFECTLY_STIRRED":
            reactor = ct.IdealGasConstPressureReactor(gas, clone=True)
        else:
            reactor = HeatTrackingParcel(
                gas,
                clone=True,
                heat_coefficient=heat_coefficient,
                surroundings_temperature=surroundings,
            )
        reactor.volume = 1.0
        if model == "PERFECTLY_STIRRED":
            controlled_mass_flow = reactor.mass / residence
            inlet_controller = ct.MassFlowController(
                upstream, reactor, mdot=controlled_mass_flow
            )
            outlet_controller = ct.MassFlowController(
                reactor, downstream, mdot=controlled_mass_flow
            )
        ambient_gas = ct.Solution(self.mechanism)
        ambient_gas.TPX = surroundings, pressure, inlet_flows
        ambient = ct.Reservoir(ambient_gas, clone=True)
        wall = ct.Wall(
            reactor,
            ambient,
            A=1.0,
            Q=lambda time: heat_coefficient * reactor.mass * (reactor.T - surroundings),
        )
        network = ct.ReactorNet([reactor])
        network.rtol = self.relative_tolerance
        network.atol = self.absolute_tolerance
        steady_residual = None
        if model == "PERFECTLY_STIRRED":
            residuals = network.advance_to_steady_state(
                max_steps=self.maximum_steps,
                residual_threshold=self.steady_threshold,
                return_residuals=True,
            )
            steady_residual = float(residuals[-1])
        else:
            network.advance(residence)
        phase = reactor.phase
        if min(phase.Y) < -1.0e-12:
            raise ValueError("Detailed solver returned a significantly negative species mass fraction")
        clipped_fraction = float(sum(-value for value in phase.Y if value < 0.0))
        species_flows = {
            species: float(inlet_mass_flow * max(0.0, phase.Y[index]) * 1000.0 / phase.molecular_weights[index])
            for index, species in enumerate(phase.species_names)
        }
        molecular_masses = {
            species: float(phase.molecular_weights[index] / 1000.0)
            for index, species in enumerate(phase.species_names)
        }
        atom_counts = {
            species: {
                element: float(phase.n_atoms(species, element))
                for element in phase.element_names
                if phase.n_atoms(species, element) > 0.0
            }
            for species in phase.species_names
        }
        carbon_inlet = sum(
            flow * atom_counts[species].get("C", 0.0)
            for species, flow in inlet_flows.items()
        )
        hydrocarbon_carbon = sum(
            flow * atom_counts[species].get("C", 0.0)
            for species, flow in species_flows.items()
            if atom_counts[species].get("C", 0.0) > 0.0
            and atom_counts[species].get("H", 0.0) > 0.0
            and atom_counts[species].get("O", 0.0) == 0.0
        )
        outlet_enthalpy = float(phase.enthalpy_mass)
        if model == "PERFECTLY_STIRRED":
            heat_transfer = (
                heat_coefficient * residence * (phase.T - surroundings) * inlet_mass_flow
            )
            energy_residual = inlet_mass_flow * (outlet_enthalpy - inlet_enthalpy) + heat_transfer
        else:
            heat_transfer = inlet_mass_flow * reactor.wall_heat_j_per_kg
            energy_residual = inlet_mass_flow * (outlet_enthalpy - inlet_enthalpy) + heat_transfer
        energy_scale = max(
            abs(inlet_mass_flow * inlet_enthalpy),
            abs(inlet_mass_flow * outlet_enthalpy),
            abs(heat_transfer),
            inlet_mass_flow * 1.0e6,
        )
        dry_moles = sum(species_flows.values()) - species_flows.get("H2O", 0.0)
        co_flow = species_flows.get("CO", 0.0)
        co_ppmv = co_flow / dry_moles * 1.0e6
        oxygen_dry_percent = species_flows.get("O2", 0.0) / dry_moles * 100.0
        hydrocarbons_fraction = hydrocarbon_carbon / carbon_inlet if carbon_inlet else 0.0
        result = {
            "schemaVersion": 1,
            "converged": True,
            "reactorModel": model,
            "temperatureK": float(phase.T),
            "pressurePa": float(phase.P),
            "residenceTimeSeconds": residence,
            "steadyResidual": steady_residual,
            "integrationTimeSeconds": float(network.time),
            "equivalentVolumeM3": inlet_mass_flow * residence / phase.density,
            "speciesMolarFlows": species_flows,
            "speciesMolecularMassesKgPerMol": molecular_masses,
            "speciesAtomCounts": atom_counts,
            "componentToSpecies": self.mapping,
            "heatTransferToSurroundingsW": float(heat_transfer),
            "energyBalanceRelativeResidual": float(energy_residual / energy_scale),
            "mechanismInletEnthalpyJPerKg": inlet_enthalpy,
            "mechanismOutletEnthalpyJPerKg": outlet_enthalpy,
            "energyReference": "Cantera mechanism formation enthalpy and NASA ideal-gas thermochemistry",
            "coMassRateKgPerHour": co_flow * molecular_masses.get("CO", 0.0) * 3600.0,
            "coPpmvDry": co_ppmv,
            "coMgPerNormalM3DryAt273_15K101325Pa": co_ppmv
            * molecular_masses.get("CO", 0.0)
            * 101325.0
            / (ct.gas_constant / 1000.0 * 273.15),
            "oxygenDryVolPercent": oxygen_dry_percent,
            "hydrocarbonCarbonFraction": hydrocarbons_fraction,
            "carbonToCO2Fraction": species_flows.get("CO2", 0.0) / carbon_inlet
            if carbon_inlet else 0.0,
            "branchDiagnostic": "FUEL_SLIP" if hydrocarbons_fraction > 0.01 else "MOSTLY_CONVERTED",
            "negativeSpeciesMassFractionClipped": clipped_fraction,
            "provenance": {
                "solver": "Cantera",
                "solverVersion": ct.__version__,
                "mechanism": self.mechanism,
                "resolvedMechanismSha256": self.mechanism_fingerprint,
                "speciesCount": phase.n_species,
                "reactionCount": phase.n_reactions,
                "mechanismQualification": "Caller-selected; no plant or propane-validation claim",
            },
        }
        return json.dumps(result, allow_nan=False)


def _positive(value, label):
    """Validate a positive finite scalar in the versioned contract."""
    result = float(value)
    if not math.isfinite(result) or result <= 0.0:
        raise ValueError(f"{label} must be finite and positive")
    return result
