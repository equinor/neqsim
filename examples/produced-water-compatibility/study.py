"""NeqSim process and PHREEQC Pitzer produced-water compatibility screen."""

import argparse
import csv
import hashlib
import json
import math
from importlib.metadata import version
from itertools import combinations
from pathlib import Path

from jsonschema import Draft202012Validator

SI_MOLAR_MASS_MG = 60084.3
WATER_MOLAR_MASS_KG = 0.01801528
BAR_PER_ATM = 1.01325
PITZER_SHA256 = "3895bc5caf3f843abbb6deade72c9515c2be1c2efa3dd57052d0bb10cba68996"
MINERALS = ("SiO2(a)", "Quartz", "Chalcedony", "Sepiolite", "Chrysotile",
            "Calcite", "Barite", "Celestite", "Gypsum", "Anhydrite")
IONS = ("Na", "K", "Ca", "Mg", "Ba", "Sr", "S(6)", "Cl")
COMPONENTS = ("methane", "ethane", "propane", "CO2", "n-heptane", "n-decane", "water")
SCHEMA = json.loads(Path(__file__).with_name("case.schema.json").read_text())


def validate_case(case):
    json.dumps(case, allow_nan=False)
    Draft202012Validator(SCHEMA).validate(case)
    formations = case["formations"]
    for records in (formations, case["nodes"]):
        if len({entry["name"] for entry in records}) != len(records):
            raise ValueError("Formation and node names must be unique within their lists")
    nodes = case["nodes"]
    if any(a["pressure_bara"] < b["pressure_bara"] for a, b in zip(nodes, nodes[1:])):
        raise ValueError("Node pressures must stay constant or decrease")
    for blend in case["additional_blends"]:
        if len(blend) != len(formations) or not math.isclose(sum(blend), 1, abs_tol=1e-10):
            raise ValueError("Every blend must give one solvent-water fraction per formation and sum to 1")


def build_blends(case):
    count = len(case["formations"])
    steps = case["blend_steps"]
    blends = {}
    for first, second in combinations(range(count), 2):
        for step in range(steps + 1):
            fractions = [0.0] * count
            fractions[first] = (steps - step) / steps
            fractions[second] = step / steps
            blends[tuple(fractions)] = None
    for fractions in case["additional_blends"]:
        blends[tuple(fractions)] = None
    return list(blends)


def process_blend(case, fractions):
    # Importing the public NeqSim Python package initializes the JVM.
    import neqsim
    from jpype import JPackage

    java = JPackage("neqsim")
    process = java.process.processmodel.ProcessSystem()
    mixer = java.process.equipment.mixer.Mixer("Produced fluid mixer")
    header = case["nodes"][0]
    temperature_k = header["temperature_c"] + 273.15
    mixer.setOutletTemperature(temperature_k)
    feeds = []
    for formation, fraction in zip(case["formations"], fractions):
        if fraction == 0:
            continue
        water_rate = fraction * case["water_kg_s"]
        inventory = formation["gas_mol_per_kg_water"] | formation["oil_mol_per_kg_water"]
        inventory["water"] = 1 / WATER_MOLAR_MASS_KG
        fluid = java.thermo.system.SystemSrkCPAstatoil(temperature_k, header["pressure_bara"])
        for name in COMPONENTS:
            fluid.addComponent(name, water_rate * inventory[name])
        fluid.setMixingRule(10)
        fluid.setMultiPhaseCheck(True)
        stream = java.process.equipment.stream.Stream("Formation " + formation["name"], fluid)
        process.add(stream)
        mixer.addStream(stream)
        feeds.append(stream)

    process.add(mixer)
    stream = mixer.getOutletStream()
    samples = [stream]
    for node in case["nodes"][1:]:
        condition = java.process.equipment.heatexchanger.Heater(node["name"], stream)
        condition.setOutletPressure(node["pressure_bara"])
        condition.setOutletTemperature(node["temperature_c"], "C")
        process.add(condition)
        stream = condition.getOutletStream()
        samples.append(stream)

    separator = java.process.equipment.separator.ThreePhaseSeparator("Final three-phase separator", stream)
    process.add(separator)
    process.run()
    if not process.solved():
        raise RuntimeError("NeqSim ProcessSystem did not solve")
    expected = {name: sum(s.getFluid().getComponent(name).getNumberOfmoles() for s in feeds)
                for name in COMPONENTS}
    discharge = [separator.getGasOutStream(), separator.getOilOutStream(), separator.getWaterOutStream()]
    closure = max(abs(sum(s.getFluid().getComponent(name).getNumberOfmoles() for s in discharge)
                      - quantity) / quantity for name, quantity in expected.items())
    if closure > 1e-7:
        raise RuntimeError(f"NeqSim component closure error {closure:.3g}")

    results = []
    for node, sample in zip(case["nodes"], samples):
        fluid = sample.getFluid()
        if (not math.isclose(fluid.getTemperature("C"), node["temperature_c"], abs_tol=1e-5)
                or not math.isclose(fluid.getPressure(), node["pressure_bara"], abs_tol=1e-5)):
            raise RuntimeError(f"NeqSim did not reach the temperature and pressure at {node['name']}")
        if fluid.getNumberOfPhases() != 3 or not all(fluid.hasPhaseType(p) for p in ("gas", "oil", "aqueous")):
            raise RuntimeError(f"NeqSim did not resolve gas, oil and aqueous phases at {node['name']}")
        rates = {name + "_kg_s": float(fluid.getPhase(name).getFlowRate("kg/sec"))
                 for name in ("gas", "oil", "aqueous")}
        aqueous_water_kg_s = float(fluid.getPhase("aqueous").getComponent("water")
                                    .getNumberOfMolesInPhase()) * WATER_MOLAR_MASS_KG
        water_retention = aqueous_water_kg_s / case["water_kg_s"]
        if not 0 < water_retention <= 1 + 1e-7:
            raise RuntimeError(f"Invalid aqueous solvent-water fraction at {node['name']}")
        closure_at_node = max(abs(sum(fluid.getPhase(i).getComponent(name).getNumberOfMolesInPhase()
                                      for i in range(fluid.getNumberOfPhases())) - quantity) / quantity
                              for name, quantity in expected.items())
        if closure_at_node > 1e-7:
            raise RuntimeError(f"NeqSim component closure error {closure_at_node:.3g} at {node['name']}")
        fugacity = float(fluid.getPhase("gas").getFugacity("CO2"))
        if not math.isfinite(fugacity) or fugacity <= 0:
            raise RuntimeError(f"Invalid NeqSim gas CO2 fugacity at {node['name']}")
        results.append({**rates, "co2_fugacity_bar": fugacity,
                        "aqueous_solvent_water_kg_s": aqueous_water_kg_s,
                        "aqueous_solvent_water_fraction": min(1.0, water_retention),
                        "neqsim_component_closure_rel": max(closure, closure_at_node)})
    return results


def solution_definition(formation, index):
    lines = [f"SOLUTION {index}", "units mol/kgw", f"temp {formation['reference_c']:.12g}",
             f"pressure {formation['reference_pressure_bara'] / BAR_PER_ATM:.12g}",
             f"pH {formation['ph']:.12g}", f"Alkalinity {formation['alkalinity_mmol_kg_water'] / 1000:.12g}",
             f"Si {formation['silica_mg_sio2_kg_water'] / SI_MOLAR_MASS_MG:.12g}"]
    lines.extend(f"{ion} {formation['ions_mol_kg_water'][ion]:.12g}" for ion in IONS)
    return "\n".join(lines)


def chemistry_at_node(formations, fractions, node, log_partial_pressure, water_retention, engine):
    pressure_atm = node["pressure_bara"] / BAR_PER_ATM
    gas_phase = "" if log_partial_pressure is None else f"CO2(g) {log_partial_pressure:.15g} 10"
    input_text = [
        "DELETE", "-all", "END",
        "SELECTED_OUTPUT 1", "-reset false", "-high_precision true", "-pH true",
        "-ionic_strength true", "-water true", "-charge_balance true", "-percent_error true",
        "-totals Si C(4) Na K Ca Mg Ba Sr S(6) Cl",
        "-saturation_indices " + " ".join(MINERALS + ("CO2(g)",)),
        "-equilibrium_phases SiO2(a) CO2(g)",
        "-activities H4SiO4 H2O",
    ]
    input_text.extend(solution_definition(f, i) for i, f in enumerate(formations, 1))
    input_text.extend(["END", "MIX 1"])
    input_text.extend(f"{i} {fraction:.16g}" for i, fraction in enumerate(fractions, 1) if fraction)
    input_text.extend(["REACTION_TEMPERATURE 1", f"{node['temperature_c']:.12g}",
                       "REACTION_PRESSURE 1", f"{pressure_atm:.12g}"])
    if water_retention < 1.0:
        water_removed_mol = (1.0 - water_retention) / WATER_MOLAR_MASS_KG
        input_text.extend(["REACTION 1", "H2O -1", f"{water_removed_mol:.15g} moles"])
    if gas_phase:
        input_text.extend(["EQUILIBRIUM_PHASES 1", gas_phase, "-force_equality true"])
    input_text.extend(["SAVE solution 100", "END", "USE solution 100", "EQUILIBRIUM_PHASES 2"])
    if gas_phase:
        input_text.extend([gas_phase, "-force_equality true"])
    input_text.extend(["SiO2(a) 0 0", "END"])
    engine.run_string("\n".join(input_text))
    headers, *rows = engine.get_selected_output_array()
    if len(rows) != len(formations) + 2:
        raise RuntimeError(f"Unexpected PHREEQC output row count {len(rows)}")
    feeds = [dict(zip(headers, row)) for row in rows[:len(formations)]]
    pre, post = [dict(zip(headers, row)) for row in rows[-2:]]
    return feeds, pre, post


def chemistry_summary(formations, fractions, node, fugacity, water_retention, database):
    from phreeqpy.iphreeqc.phreeqc_dll import IPhreeqc

    target = math.log10(fugacity / BAR_PER_ATM) if fugacity is not None else None
    imposed = target
    engine = IPhreeqc()
    try:
        engine.load_database(str(database))
        if engine.phc_error_count:
            raise ValueError(engine.get_error_string())
        for _ in range(12):
            feeds, pre, post = chemistry_at_node(formations, fractions, node, imposed, water_retention, engine)
            if target is None:
                break
            # EQUILIBRIUM_PHASES takes log(p/atm), whereas selected SI is log(f/atm).
            residual = target - pre["si_CO2(g)"]
            if abs(residual) < 1e-8:
                break
            imposed += residual
        else:
            raise RuntimeError("PHREEQC CO2 partial-pressure iteration did not match the EOS fugacity")
    finally:
        engine.destroy_iphreeqc()
    if any(abs(row["pct_err"]) > 1 for row in (*feeds, pre, post)):
        raise ValueError("PHREEQC electroneutrality error exceeds 1%")
    masses = {element: sum(weight * feed[f"{element}(mol/kgw)"] * feed["mass_H2O"]
                           for weight, feed in zip(fractions, feeds)) for element in ("Si", "C(4)", *IONS)}
    solid = post["SiO2(a)"]
    pre_si_mol = pre["Si(mol/kgw)"] * pre["mass_H2O"]
    post_si_mol = post["Si(mol/kgw)"] * post["mass_H2O"]
    closure = abs(masses["Si"] - post_si_mol - solid)
    ion_closure = max(abs(masses[ion] - stage[f"{ion}(mol/kgw)"] * stage["mass_H2O"])
                      for ion in IONS for stage in (pre, post))
    carbon_closure = None if fugacity else max(
        abs(masses["C(4)"] - row["C(4)(mol/kgw)"] * row["mass_H2O"]) for row in (pre, post)
    )
    residual = max(closure, abs(masses["Si"] - pre_si_mol), ion_closure, carbon_closure or 0)
    if residual > 1e-8 or solid < -1e-10:
        raise RuntimeError(f"PHREEQC inventory residual {residual:.3g} mol/kg feed water")
    if (solid > 1e-9 and abs(post["si_SiO2(a)"]) > 1e-7) or post["si_SiO2(a)"] > 1e-7:
        raise RuntimeError("PHREEQC silica precipitation failed its saturation check")
    if fugacity and any(abs(row["si_CO2(g)"] - math.log10(fugacity / BAR_PER_ATM)) > 1e-7
                        for row in (pre, post)):
        raise RuntimeError("PHREEQC did not reach the requested CO2 fugacity")
    if any(not math.isfinite(row["pH"]) or not 0 < row["pH"] < 14 for row in (pre, post)):
        raise RuntimeError("PHREEQC calculated an invalid pH")
    return {
        "ph": pre["pH"], "log10_co2_fugacity_atm": pre["si_CO2(g)"], "ph_after_silica_equilibrium": post["pH"],
        "ionic_strength_mol_kg_water": pre["mu"],
        "max_charge_error_pct": max(abs(row["pct_err"]) for row in (*feeds, pre, post)),
        "feed_silica_mg_sio2_kg_water": masses["Si"] * SI_MOLAR_MASS_MG,
        "remaining_silica_mg_sio2_kg_feed_water": post_si_mol * SI_MOLAR_MASS_MG,
        "equilibrium_solid_mg_sio2_kg_feed_water": max(0.0, solid) * SI_MOLAR_MASS_MG,
        "log10_activity_h4sio4": pre["la_H4SiO4"] if pre_si_mol > 0 else None,
        "log10_activity_water": pre["la_H2O"],
        "silicon_closure_mol_kg_water": closure,
        "major_ion_closure_mol_kg_water": ion_closure,
        "closed_dic_closure_mol_kg_water": carbon_closure,
        "aqueous_dic_gain_mol_kg_feed_water": pre["C(4)(mol/kgw)"] * pre["mass_H2O"] - masses["C(4)"],
        **{f"si_{mineral}": pre[f"si_{mineral}"] if pre[f"si_{mineral}"] > -900 else None
           for mineral in MINERALS},
        "si_amorphous_after_equilibrium": post["si_SiO2(a)"] if pre_si_mol > 0 else None,
    }


def run_study(case, database):
    validate_case(case)
    database = Path(database).resolve(strict=True)
    checksum = hashlib.sha256(database.read_bytes()).hexdigest()
    if checksum != PITZER_SHA256:
        raise ValueError("Use the qualified PHREEQC v3.7.3 pitzer.dat. See README.md for the source.")
    results = []
    for index, blend in enumerate(build_blends(case), 1):
        process = process_blend(case, blend)
        fractions = {f"solvent_water_fraction_{f['name']}": fraction
                     for f, fraction in zip(case["formations"], blend)}
        for node, state in zip(case["nodes"], process):
            for boundary, fugacity in (("fixed_eos_co2", state["co2_fugacity_bar"]), ("closed_dic", None)):
                chemistry = chemistry_summary(case["formations"], blend, node, fugacity,
                                              state["aqueous_solvent_water_fraction"], database)
                results.append({"blend_id": index, **fractions, **node, **state, "boundary": boundary,
                                **chemistry, "equilibrium_solid_kg_h": chemistry[
                                    "equilibrium_solid_mg_sio2_kg_feed_water"
                                ] * case["water_kg_s"] * 0.0036})
    return {
        "status": "equilibrium_screening",
        "iphreeqc_version": "3.7.3 (phreeqpy default bundled library)",
        "neqsim_version": version("neqsim"),
        "phreeqpy_version": version("phreeqpy"),
        "pitzer_database_sha256": checksum,
        "case": case,
        "rows": results,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("case", type=Path)
    parser.add_argument("--database", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = run_study(json.loads(args.case.read_text()), args.database)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "result.json").write_text(json.dumps(result, allow_nan=False, indent=2) + "\n")
    with (args.output / "blend_results.csv").open("w", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=result["rows"][0].keys())
        writer.writeheader()
        writer.writerows(result["rows"])


if __name__ == "__main__":
    main()
