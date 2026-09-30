"""Conservation diagnostics independent of a chemistry or EOS package."""


def projection_diagnostics(inlet_flows, outlet_flows, masses, atoms, mapping):
    """Identify omitted atoms without replacing species or relaxing acceptance gates.

    Flows are mol/s, masses kg/mol, and elemental flows mol atoms/s. Relative
    omissions use the inlet element inventory, matching the Java projection gate.
    An element absent at the inlet has no defined relative residual (JSON null).
    """
    mapped = set(mapping.values())
    elements = sorted({element for counts in atoms.values() for element in counts})
    inlet_mass = sum(flow * masses[species] for species, flow in inlet_flows.items())
    outlet_mass = sum(flow * masses[species] for species, flow in outlet_flows.items())
    omitted = {
        species: flow for species, flow in outlet_flows.items()
        if species not in mapped and flow > 0.0
    }
    omitted_mass = sum(flow * masses[species] for species, flow in omitted.items())
    balances = {}
    for element in elements:
        initial = sum(
            flow * atoms[species].get(element, 0.0)
            for species, flow in inlet_flows.items()
        )
        final = sum(
            flow * atoms[species].get(element, 0.0)
            for species, flow in outlet_flows.items()
        )
        contributions = {
            species: flow * atoms[species].get(element, 0.0)
            for species, flow in omitted.items()
            if atoms[species].get(element, 0.0) > 0.0
        }
        missing = sum(contributions.values())
        balances[element] = {
            "inletMolAtomsPerSecond": initial,
            "outletMolAtomsPerSecond": final,
            "omittedMolAtomsPerSecond": missing,
            "mechanismRelativeResidual": (final - initial) / initial if initial else None,
            "projectionRelativeResidual": (final - missing - initial) / initial
            if initial else None,
            "omittedInletElementFraction": missing / initial if initial else None,
            "omittedSpeciesMolAtomsPerSecond": dict(sorted(
                contributions.items(), key=lambda item: (-item[1], item[0])
            )),
        }
    return {
        "mechanismMassRelativeResidual": (outlet_mass - inlet_mass) / inlet_mass,
        "unmappedMechanismMassFraction": omitted_mass / outlet_mass,
        "elementProjectionDiagnostics": balances,
    }


def combustion_branch(useful_heat, chemical_input, residual_chemical,
                      carbon_in, organic_carbon, carbon_dioxide):
    """Require heat recovery and chemical conversion, also for carbon-free fuels.

    A low carbon slip alone misses unburned H2 and CO-rich products. Residual
    chemical power uses the same complete-gaseous-product reference as fuel LHV.
    The 99% thresholds are diagnostic conventions, not burner safety limits.
    """
    energy_converted = chemical_input > 0.0 and residual_chemical < 0.01 * chemical_input
    carbon_converted = carbon_in == 0.0 or (
        organic_carbon < 0.01 * carbon_in and carbon_dioxide > 0.99 * carbon_in
    )
    if useful_heat > 0.0 and energy_converted and carbon_converted:
        return "BURNING"
    return "INCOMPLETE_CONVERSION_OR_NO_USEFUL_HEAT"
