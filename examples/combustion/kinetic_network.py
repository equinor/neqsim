"""Finite-rate burner PSRs, enthalpy-conserving mixing and a reacting cooling zone.

Cantera 3.2 owns chemical rates and NASA ideal-gas thermochemistry. This reduced
network resolves reactor volumes, not spatial flames. The effective grey-body
exchange parameters are caller inputs. No chemical mechanism is redistributed.
"""

import math

import cantera as ct

from combustion_diagnostics import (
    combustion_branch, conservation_diagnostics, projection_diagnostics,
)


STEFAN_BOLTZMANN = 5.670374419e-8


class ZoneHeatTransfer:
    """Distinct tube heat absorption and steady refractory conduction to ambient."""

    def __init__(self, request, tube_area, refractory_area):
        self.tube_area = tube_area
        self.refractory_area = refractory_area
        self.tube_h = request["tubeConvectionWPerM2K"]
        self.tube_emissivity = request["tubeEffectiveEmissivity"]
        self.oil_temperature = request["heatSinkTemperatureK"]
        self.refractory_h = request["refractoryConvectionWPerM2K"]
        self.refractory_emissivity = request["refractoryEffectiveEmissivity"]
        self.refractory_conductance = (
            request["refractoryConductivityWPerMK"] / request["refractoryThicknessM"]
        )
        self.ambient_temperature = request["ambientTemperatureK"]

    def rates(self, temperature):
        """Return tube absorption and shell loss [W], with signed heat exchange."""
        tube = self.tube_area * (
            self.tube_h * (temperature - self.oil_temperature)
            + self.tube_emissivity * STEFAN_BOLTZMANN
            * (temperature**4 - self.oil_temperature**4)
        )
        low = min(temperature, self.ambient_temperature)
        high = max(temperature, self.ambient_temperature)
        # Monotonic algebraic inner-wall balance; no refractory thermal storage.
        for iteration in range(32):
            wall = (low + high) / 2.0
            gas_to_wall = (
                self.refractory_h * (temperature - wall)
                + self.refractory_emissivity * STEFAN_BOLTZMANN
                * (temperature**4 - wall**4)
            )
            wall_to_ambient = self.refractory_conductance * (
                wall - self.ambient_temperature
            )
            if gas_to_wall > wall_to_ambient:
                low = wall
            else:
                high = wall
        shell = self.refractory_area * self.refractory_conductance * (
            (low + high) / 2.0 - self.ambient_temperature
        )
        return tube, shell


class HeatIntegratingParcel(ct.ExtensibleIdealGasConstPressureReactor):
    """Reacting parcel with independent wall-heat and swept-volume integration."""

    def __init__(self, *args, mass_flow, heat_transfer, zone_volume, **kwargs):
        super().__init__(*args, **kwargs)
        self.mass_flow = mass_flow
        self.heat_transfer = heat_transfer
        self.zone_volume = zone_volume
        self.swept_volume = 0.0
        self.oil_heat_j_per_kg = 0.0
        self.shell_heat_j_per_kg = 0.0

    def after_initialize(self, initial_time):
        """Append independent integral state variables to Cantera's equations."""
        self.swept_index = self.n_vars
        self.oil_index = self.n_vars + 1
        self.shell_index = self.n_vars + 2
        self.n_vars += 3

    def after_get_state(self, state):
        """Export the integral state to CVODES."""
        state[self.swept_index] = self.swept_volume
        state[self.oil_index] = self.oil_heat_j_per_kg
        state[self.shell_index] = self.shell_heat_j_per_kg

    def after_update_state(self, state):
        """Restore the integral state for the current evaluation."""
        self.swept_volume = state[self.swept_index]
        self.oil_heat_j_per_kg = state[self.oil_index]
        self.shell_heat_j_per_kg = state[self.shell_index]

    def after_eval(self, time, left_hand_side, right_hand_side):
        """Add physical cooling and independently integrate its transferred heat."""
        oil, shell = self.heat_transfer.rates(self.T)
        volume_fraction = self.volume / self.zone_volume
        oil *= volume_fraction
        shell *= volume_fraction
        right_hand_side[1] -= oil + shell
        left_hand_side[self.swept_index] = 1.0
        right_hand_side[self.swept_index] = self.mass_flow / self.phase.density
        left_hand_side[self.oil_index] = 1.0
        right_hand_side[self.oil_index] = oil / self.mass
        left_hand_side[self.shell_index] = 1.0
        right_hand_side[self.shell_index] = shell / self.mass


def _gas(backend):
    """Create an independent mechanism state, with no shared mutable reactor phase."""
    return ct.Solution(backend.mechanism, transport_model=None)


def _flows(component_flows, backend):
    """Map positive inlet flows and reject unsupported species without normalization."""
    result = {}
    for component, value in component_flows.items():
        value = float(value)
        if not math.isfinite(value) or value < 0.0:
            raise ValueError("Supply flows must be finite and nonnegative")
        if value > 0.0:
            if component not in backend.mapping:
                raise ValueError(f"Unsupported mechanism inlet component {component}")
            result[backend.mapping[component]] = value
    if not result:
        raise ValueError("Fuel and air supplies must each have positive flow")
    return result


def _mass_flow(gas, species_flows):
    """Convert mol/s to kg/s with the selected mechanism's molecular masses."""
    return sum(
        flow * gas.molecular_weights[gas.species_index(species)] / 1000.0
        for species, flow in species_flows.items()
    )


def _species_flows(gas, mass_flow):
    """Read every species with the existing 1e-12 negative-mass-fraction bound.

    Bounded negative solver roundoff is zeroed, as at the existing final outlet.
    This is never a renormalization or an omission of positive intermediates.
    """
    if any(not math.isfinite(value) or value < -1.0e-12 for value in gas.Y):
        raise ValueError("Zone solver returned invalid species mass fractions")
    return {
        species: float(mass_flow * max(0.0, gas.Y[index]) * 1000.0 / gas.molecular_weights[index])
        for index, species in enumerate(gas.species_names)
    }


def _zone_conservation(inlet_flows, gas, mass_flow, masses, atoms, label):
    """Check the outlet convention while retaining signed raw solver evidence."""
    result = conservation_diagnostics(
        inlet_flows, _species_flows(gas, mass_flow), masses, atoms, label,
    )
    result["negativeSpeciesMassFractionClipped"] = float(sum(-y for y in gas.Y if y < 0.0))
    result["rawElementBalanceResidualsMolAtomsPerSecond"] = {
        element: float(sum(
            mass_flow * gas.Y[index] * 1000.0 / gas.molecular_weights[index]
            * atoms[species].get(element, 0.0)
            for index, species in enumerate(gas.species_names)
        ) - balance["inletMolAtomsPerSecond"])
        for element, balance in result["elementBalances"].items()
    }
    return result


def _add_flows(destination, source, fraction=1.0):
    """Accumulate supplied species mol/s, preserving trace species and atoms."""
    for species, flow in source.items():
        destination[species] = destination.get(species, 0.0) + fraction * flow


def _quantity(gas, mass, backend):
    """Keep Quantity's mutable phase separate from supply and reactor states."""
    separate = _gas(backend)
    separate.TPY = gas.T, gas.P, gas.Y
    return ct.Quantity(separate, mass=mass, constant="HP")


def _chemical_power(gas, mass_flow, backend):
    """Chemical power relative to complete gaseous products at 298.15 K [W]."""
    reference = _gas(backend)
    reference.TPY = 298.15, gas.P, gas.Y
    reference_enthalpy = reference.enthalpy_mass
    atom_moles_per_kg = {
        element: sum(
            reference.Y[index] / reference.molecular_weights[index]
            * reference.n_atoms(index, element)
            for index in range(reference.n_species)
        )
        for element in reference.element_names
    }
    unsupported = {
        element for element, amount in atom_moles_per_kg.items()
        if amount > 1.0e-20 and element not in {"C", "H", "O", "N", "Ar"}
    }
    if unsupported:
        raise ValueError(f"Complete-product energy accounting does not support elements {unsupported}")
    carbon = atom_moles_per_kg.get("C", 0.0)
    hydrogen = atom_moles_per_kg.get("H", 0.0)
    oxygen = atom_moles_per_kg.get("O", 0.0)
    complete = {
        "CO2": carbon,
        "H2O": hydrogen / 2.0,
        "O2": (oxygen - 2.0 * carbon - hydrogen / 2.0) / 2.0,
        "N2": atom_moles_per_kg.get("N", 0.0) / 2.0,
    }
    if "AR" in reference.species_names:
        complete["AR"] = atom_moles_per_kg.get("Ar", 0.0)
    standard_enthalpies = reference.standard_enthalpies_RT * ct.gas_constant * reference.T
    complete_enthalpy = sum(
        amount * standard_enthalpies[reference.species_index(species)]
        for species, amount in complete.items()
    )
    return mass_flow * (reference_enthalpy - complete_enthalpy), reference_enthalpy


def solve_fired_heater(request, backend):
    """Solve independently switched fixed-volume burner PSRs and a common cooling PFR."""
    pressure = float(request["pressurePa"])
    fuel_flows = _flows(request["fuelMolarFlows"], backend)
    air_flows = _flows(request["airMolarFlows"], backend)
    fuel = _gas(backend)
    fuel.TPX = request["fuelTemperatureK"], pressure, fuel_flows
    masses = {
        species: float(fuel.molecular_weights[index] / 1000.0)
        for index, species in enumerate(fuel.species_names)
    }
    atoms = {
        species: {
            element: float(fuel.n_atoms(species, element))
            for element in fuel.element_names if fuel.n_atoms(species, element) > 0.0
        }
        for species in fuel.species_names
    }
    air = _gas(backend)
    air.TPX = request["airTemperatureK"], pressure, air_flows
    fuel_mass = _mass_flow(fuel, fuel_flows)
    air_mass = _mass_flow(air, air_flows)
    total_mass = fuel_mass + air_mass
    inlet_enthalpy_rate = fuel_mass * fuel.enthalpy_mass + air_mass * air.enthalpy_mass
    active = [burner for burner in request["burners"] if burner["enabled"]]
    if not active:
        raise ValueError("At least one burner must be active")
    total_weight = sum(burner["fuelWeight"] for burner in active)
    captured_fraction = sum(burner["airCaptureFraction"] for burner in active)
    if not 0.0 < captured_fraction <= 1.0 + 1.0e-12:
        raise ValueError("Total active captured-air fraction must be in (0, 1]")
    oil_heat = 0.0
    shell_heat = 0.0
    primary_residual = 0.0
    burner_diagnostics = []
    zone_diagnostics = []
    mixer_inlet_flows = {}
    hot_products = None
    cache = {}
    for burner in active:
        fuel_fraction = burner["fuelWeight"] / total_weight
        local_air_mass = air_mass * burner["airCaptureFraction"]
        local_fuel_mass = fuel_mass * fuel_fraction
        local_mass = local_fuel_mass + local_air_mass
        mixture = (
            _quantity(fuel, local_fuel_mass, backend)
            + _quantity(air, local_air_mass, backend)
        )
        local_inlet_flows = {}
        _add_flows(local_inlet_flows, fuel_flows, fuel_fraction)
        _add_flows(local_inlet_flows, air_flows, burner["airCaptureFraction"])
        zone_diagnostics.append(_zone_conservation(
            local_inlet_flows, mixture, local_mass, masses, atoms,
            f"burner {burner['id']} inlet mixing",
        ))
        key = (fuel_fraction, burner["airCaptureFraction"], burner["volumeM3"])
        heat = ZoneHeatTransfer(
            request,
            request["tubeAreaM2"] * request["primaryHeatingAreaFraction"] * fuel_fraction,
            request["refractoryAreaM2"] * request["primaryHeatingAreaFraction"] * fuel_fraction,
        )
        if key in cache:
            temperature, composition, oil, shell, residual, residence, mass_residual, exit_mass = cache[key]
            product = _gas(backend)
            product.TPY = temperature, pressure, composition
        else:
            inlet = _gas(backend)
            inlet.TPY = mixture.T, pressure, mixture.Y
            upstream = ct.Reservoir(inlet, clone=True)
            initial = _gas(backend)
            initial.TPY = request["ignitionTemperatureK"], pressure, mixture.Y
            reactor = ct.IdealGasReactor(initial, clone=True, volume=burner["volumeM3"])
            downstream = ct.Reservoir(initial, clone=True)
            flow = ct.MassFlowController(upstream, reactor, mdot=local_mass)
            exit_flow = ct.PressureController(
                reactor, downstream, primary=flow, K=local_mass / pressure * 100.0
            )
            sink = ct.Reservoir(initial, clone=True)
            ct.Wall(
                reactor, sink, A=1.0,
                Q=lambda time: sum(heat.rates(reactor.T)),
            )
            network = ct.ReactorNet([reactor])
            network.rtol = backend.relative_tolerance
            network.atol = backend.absolute_tolerance
            network.solve_steady()
            product = _gas(backend)
            product.TPY = reactor.T, reactor.phase.P, reactor.phase.Y
            oil, shell = heat.rates(reactor.T)
            residual = local_mass * inlet.enthalpy_mass - exit_flow.mass_flow_rate * (
                product.enthalpy_mass
            ) - oil - shell
            mass_residual = exit_flow.mass_flow_rate / local_mass - 1.0
            exit_mass = exit_flow.mass_flow_rate
            if abs(mass_residual) > 1.0e-7:
                raise ValueError("Burner PSR does not conserve steady mass flow")
            if abs(product.P / pressure - 1.0) > 1.0e-5:
                raise ValueError("Burner PSR does not preserve prescribed pressure")
            energy_scale = max(
                abs(local_mass * inlet.enthalpy_mass),
                abs(local_mass * product.enthalpy_mass),
                abs(oil) + abs(shell), local_mass * 1.0e6,
            )
            if abs(residual) > 1.0e-6 * energy_scale:
                raise ValueError("Burner PSR energy balance exceeds tolerance")
            residence = reactor.mass / local_mass
            cache[key] = (
                product.T, product.Y.copy(), oil, shell, residual, residence, mass_residual, exit_mass
            )
        zone_diagnostics.append(_zone_conservation(
            local_inlet_flows, product, exit_mass, masses, atoms,
            f"burner {burner['id']} PSR",
        ))
        _add_flows(mixer_inlet_flows, _species_flows(product, local_mass))
        oil_heat += oil
        shell_heat += shell
        primary_residual += residual
        parcel = _quantity(product, local_mass, backend)
        hot_products = parcel if hot_products is None else hot_products + parcel
        burner_diagnostics.append({
            "id": burner["id"], "temperatureK": float(product.T),
            "residenceTimeSeconds": float(residence), "fuelMassFlowKgPerSecond": local_fuel_mass,
            "capturedAirMassFlowKgPerSecond": local_air_mass,
            "energyResidualW": float(residual),
            "massBalanceRelativeResidual": float(mass_residual),
        })
    bypass = air_mass * max(0.0, 1.0 - captured_fraction)
    if bypass > 0.0:
        hot_products += _quantity(air, bypass, backend)
        _add_flows(mixer_inlet_flows, air_flows, bypass / air_mass)
    mixed = _gas(backend)
    mixed.TPY = hot_products.T, pressure, hot_products.Y
    mixed_flows = _species_flows(mixed, total_mass)
    zone_diagnostics.append(_zone_conservation(
        mixer_inlet_flows, mixed, total_mass, masses, atoms, "common mixer including bypass air",
    ))
    mixed_enthalpy = mixed.enthalpy_mass
    common_volume = request["commonReactiveVolumeM3"]
    common_heat = ZoneHeatTransfer(
        request,
        request["tubeAreaM2"] * (1.0 - request["primaryHeatingAreaFraction"]),
        request["refractoryAreaM2"] * (1.0 - request["primaryHeatingAreaFraction"]),
    )
    post = HeatIntegratingParcel(
        mixed, clone=True, volume=1.0 / mixed.density, mass_flow=total_mass,
        heat_transfer=common_heat, zone_volume=common_volume,
    )
    network = ct.ReactorNet([post])
    network.rtol = backend.relative_tolerance
    network.atol = backend.absolute_tolerance
    while post.swept_volume < common_volume * (1.0 - 1.0e-8):
        remaining = common_volume - post.swept_volume
        step = min(0.005, 0.9 * remaining * post.phase.density / total_mass)
        network.advance(network.time + step)
    volume_residual = post.swept_volume / common_volume - 1.0
    if abs(volume_residual) > 1.0e-6:
        raise ValueError("Post-flame integration exceeded the specified reactive volume")
    outlet = post.phase
    zone_diagnostics.append(_zone_conservation(
        mixed_flows, outlet, total_mass, masses, atoms, "post-flame PFR",
    ))
    oil_heat += total_mass * post.oil_heat_j_per_kg
    shell_heat += total_mass * post.shell_heat_j_per_kg
    post_residual = total_mass * (
        outlet.enthalpy_mass - mixed_enthalpy
        + post.oil_heat_j_per_kg + post.shell_heat_j_per_kg
    )
    energy_residual = total_mass * outlet.enthalpy_mass - inlet_enthalpy_rate + oil_heat + shell_heat
    chemical_input, fuel_reference_h = _chemical_power(fuel, fuel_mass, backend)
    air_chemical, air_reference_h = _chemical_power(air, air_mass, backend)
    residual_chemical, outlet_reference_h = _chemical_power(outlet, total_mass, backend)
    inlet_sensible = inlet_enthalpy_rate - fuel_mass * fuel_reference_h - air_mass * air_reference_h
    stack_sensible = total_mass * (outlet.enthalpy_mass - outlet_reference_h)
    full_residual = chemical_input + air_chemical + inlet_sensible - (
        oil_heat + shell_heat + stack_sensible + residual_chemical
    )
    scale = max(abs(chemical_input), abs(inlet_enthalpy_rate), 1.0)
    if min(outlet.Y) < -1.0e-12:
        raise ValueError("Detailed solver returned a significantly negative species mass fraction")
    clipped_fraction = float(sum(-value for value in outlet.Y if value < 0.0))
    species_flows = {
        species: float(total_mass * max(0.0, outlet.Y[index]) * 1000.0 / outlet.molecular_weights[index])
        for index, species in enumerate(outlet.species_names)
    }
    carbon_in = sum(
        flow * atoms[species].get("C", 0.0) for species, flow in fuel_flows.items()
    )
    hydrocarbon_carbon = sum(
        flow * atoms[species].get("C", 0.0)
        for species, flow in species_flows.items()
        if atoms[species].get("H", 0.0) > 0.0 and atoms[species].get("O", 0.0) == 0.0
    )
    dry_moles = sum(species_flows.values()) - species_flows.get("H2O", 0.0)
    co_dry_ppm = species_flows.get("CO", 0.0) / dry_moles * 1.0e6
    oxygen_percent = species_flows.get("O2", 0.0) / dry_moles * 100.0
    normal_factor = masses["CO"] * 101325.0 / (ct.gas_constant / 1000.0 * 273.15)
    reference_valid = oxygen_percent < 20.9 - 1.0e-6
    co_normal = co_dry_ppm * normal_factor
    organic_carbon = sum(
        flow * atoms[species].get("C", 0.0)
        for species, flow in species_flows.items()
        if atoms[species].get("C", 0.0) > 0.0 and atoms[species].get("H", 0.0) > 0.0
    )
    methane_carbon = species_flows.get("CH4", 0.0)
    normal_molar_density = 101325.0 / (ct.gas_constant / 1000.0 * 273.15)
    carbon_molecular_mass = outlet.atomic_weights[outlet.element_index("C")] / 1000.0
    organic_factor = normal_molar_density * carbon_molecular_mass * 1.0e6 / dry_moles
    result = {
        "schemaVersion": 1, "converged": True,
        "reactorModel": "MULTI_BURNER_FIRED_HEATER",
        "temperatureK": float(outlet.T), "pressurePa": float(outlet.P),
        "speciesMolarFlows": species_flows, "speciesMolecularMassesKgPerMol": masses,
        "speciesAtomCounts": atoms, "componentToSpecies": backend.mapping,
        "heatTransferToSurroundingsW": float(oil_heat + shell_heat),
        "mechanismInletEnthalpyJPerKg": inlet_enthalpy_rate / total_mass,
        "mechanismOutletEnthalpyJPerKg": float(outlet.enthalpy_mass),
        "energyReference": "Cantera NASA formation enthalpy; complete gaseous products at 298.15 K",
        "energyBalanceRelativeResidual": float(energy_residual / scale),
        "fullEnergyBalanceRelativeResidual": float(full_residual / scale),
        "primaryEnergyResidualW": float(primary_residual), "postFlameEnergyResidualW": float(post_residual),
        "usefulHeatToOilW": float(oil_heat), "shellHeatLossW": float(shell_heat),
        "stackSensibleHeatW": float(stack_sensible), "residualChemicalPowerW": float(residual_chemical),
        "fuelChemicalPowerW": float(chemical_input), "inletSensibleHeatW": float(inlet_sensible),
        "airChemicalPowerW": float(air_chemical),
        "stackSensibleReferenceTemperatureK": 298.15,
        "postFlameResidenceTimeSeconds": float(network.time), "postFlameVolumeM3": float(post.swept_volume),
        "specifiedPostFlameVolumeM3": common_volume,
        "postFlameVolumeRelativeResidual": float(volume_residual),
        "burnerDiagnostics": burner_diagnostics,
        "zoneConservationDiagnostics": zone_diagnostics,
        "coMassRateKgPerHour": species_flows.get("CO", 0.0) * masses["CO"] * 3600.0,
        "coPpmvDry": co_dry_ppm, "coMgPerNormalM3DryAt273_15K101325Pa": co_normal,
        "oxygenDryVolPercent": oxygen_percent,
        "referenceOxygenVolPercent": 3.0,
        "referenceOxygenCorrectionValid": reference_valid,
        "coMgPerNormalM3DryAtReferenceOxygen": co_normal * (20.9 - 3.0) / (20.9 - oxygen_percent)
        if reference_valid else None,
        "hydrocarbonCarbonFraction": hydrocarbon_carbon / carbon_in if carbon_in else 0.0,
        "carbonToCO2Fraction": species_flows.get("CO2", 0.0) / carbon_in if carbon_in else 0.0,
        "branchDiagnostic": combustion_branch(
            oil_heat, chemical_input + air_chemical, residual_chemical,
            carbon_in, organic_carbon, species_flows.get("CO2", 0.0)
        ),
        "organicCarbonFraction": organic_carbon / carbon_in if carbon_in else 0.0,
        "negativeSpeciesMassFractionClipped": clipped_fraction,
        "methaneMgPerNormalM3Dry": methane_carbon * masses.get("CH4", 0.0)
        * normal_molar_density * 1.0e6 / dry_moles,
        "organicCarbonMgCPerNormalM3Dry": organic_carbon * organic_factor,
        "nonMethaneOrganicCarbonMgCPerNormalM3Dry": max(0.0, organic_carbon - methane_carbon)
        * organic_factor,
        "organicCarbonDiagnosticBasis": "Exact gas-phase C/H-containing mechanism species, including oxygenates; carbon-mass surrogate, not calibrated FID response",
        "pollutantAvailability": {
            species: "IN_MECHANISM" if species in outlet.species_names else "NOT_COMPUTED"
            for species in ("CO", "CH4", "NO", "NO2", "N2O", "SO2", "SO3", "HCl")
        },
        "provenance": {
            "solver": "Cantera", "solverVersion": ct.__version__, "mechanism": backend.mechanism,
            "resolvedMechanismSha256": backend.mechanism_fingerprint,
            "speciesCount": outlet.n_species, "reactionCount": outlet.n_reactions,
            "mechanismQualification": "Caller-selected; reduced-zone model, no industrial emissions guarantee",
        },
    }

    combined_flows = dict(fuel_flows)
    for species, flow in air_flows.items():
        combined_flows[species] = combined_flows.get(species, 0.0) + flow
    result.update(projection_diagnostics(
        combined_flows, species_flows, masses, atoms, backend.mapping
    ))
    return result
