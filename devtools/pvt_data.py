"""Validated, provenance-preserving input contracts for PVT fluid studies.

This module performs no thermodynamic calculations and has no JVM dependency.
Fractions are converted only after their original closure has been checked.
"""

import copy
import hashlib
import json
import math


class PVTStudyError(ValueError):
    """A study is incomplete, inconsistent, unsupported or failed a quality gate."""


class PVTQualityError(PVTStudyError):
    """A calculation or export failed a required acceptance gate."""


STAGES = ("import", "characterize", "simulate", "calibrate", "validate", "export", "report")
KINDS = ("TP", "CCE", "CVD", "DLE", "SEPARATOR", "VISCOSITY")
ROLES = ("simulation", "calibration", "validation")
OBSERVABLES = {
    "relative_volume": "1",
    "liquid_dropout_percent": "% saturation volume",
    "gas_z_factor": "1",
    "rs_sm3_sm3": "Sm3/Sm3",
    "bo_m3_sm3": "m3/Sm3",
    "oil_density_kg_m3": "kg/m3",
    "viscosity_pa_s": "Pa s",
}
FIT_PARAMETERS = ("BIP_METHANE_C7PLUS", "BIP_C2C6_C7PLUS", "BIP_CO2_HC", "BIP_N2_HC")


def finite(value, name, positive=False):
    """Return a finite number, rejecting booleans and invalid physical signs."""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise PVTStudyError("%s must be a finite number" % name)
    value = float(value)
    if not math.isfinite(value) or (positive and value <= 0.0):
        raise PVTStudyError("%s must be %sfinite" % (name, "positive and " if positive else ""))
    return value


def quantity(item, target):
    """Convert explicit absolute pressure or absolute temperature to bara or K."""
    if not isinstance(item, dict) or "value" not in item or "unit" not in item:
        raise PVTStudyError("quantity requires value and explicit unit (%s)" % target)
    value = finite(item["value"], target)
    unit = item["unit"]
    if target == "bara":
        scales = {"bara": 1.0, "Pa": 1e-5, "kPa": 0.01, "MPa": 10.0}
        if unit not in scales:
            raise PVTStudyError("absolute pressure required; unsupported unit %s" % unit)
        value *= scales[unit]
    elif target == "K":
        if unit in ("C", "degC"):
            value += 273.15
        elif unit != "K":
            raise PVTStudyError("unsupported temperature unit %s" % unit)
    else:
        raise PVTStudyError("unsupported target unit %s" % target)
    if value <= 0:
        raise PVTStudyError("%s must be positive on an absolute scale" % target)
    return value


def identity(value):
    """Hash full-precision canonical JSON with nonfinite numbers disallowed."""
    encoded = json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False)
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def _indexed(rows, name):
    """Validate stable nonempty unique identifiers for a record collection."""
    if not isinstance(rows, list):
        raise PVTStudyError("%s must be a list" % name)
    out = {}
    for row in rows:
        if not isinstance(row, dict) or not isinstance(row.get("id"), str) or not row["id"].strip():
            raise PVTStudyError("every %s record needs an id" % name)
        if row["id"] in out:
            raise PVTStudyError("duplicate %s id %s" % (name, row["id"]))
        out[row["id"]] = row
    return out


def normalize_analysis(analysis):
    """Keep raw composition, check closure, and prepare explicit mole fractions."""
    if not isinstance(analysis, dict):
        raise PVTStudyError("analysis must be a mapping")
    result = copy.deepcopy(analysis)
    basis = result.get("basis")
    if basis not in ("mole_fraction", "mole_percent", "mass_fraction", "mass_percent"):
        raise PVTStudyError("analysis %s needs an explicit mole/mass composition basis" % result.get("id"))
    components = result.get("components")
    if not isinstance(components, list) or not components:
        raise PVTStudyError("analysis needs nonempty components")
    names = set()
    values = []
    for component in components:
        if not isinstance(component, dict) or set(component) - {
                "name", "value", "kind", "molar_mass_kg_mol", "density_g_cm3", "source",
                "lab_name", "alias", "metadata", "uncertainty", "raw_value", "mole_fraction"}:
            raise PVTStudyError("unsupported component overrides cannot be silently ignored; use the documented effective parameter contract")
        name = component.get("name")
        if not isinstance(name, str) or not name or name in names:
            raise PVTStudyError("component names must be nonempty and unique within each analysis")
        names.add(name)
        if component.get("kind", "pure") not in ("pure", "tbp", "plus"):
            raise PVTStudyError("component kind must be pure, tbp or plus")
        number = finite(component.get("value"), "%s composition" % name)
        if number < 0:
            raise PVTStudyError("negative composition for %s" % name)
        values.append(number)
        if component.get("kind", "pure") != "pure" or basis.startswith("mass"):
            finite(component.get("molar_mass_kg_mol"), "%s molar mass kg/mol" % name, True)
        if component.get("kind", "pure") != "pure":
            finite(component.get("density_g_cm3"), "%s density g/cm3" % name, True)
    total = sum(values)
    target = 100.0 if basis.endswith("percent") else 1.0
    tolerance = finite(result.get("closure_tolerance", 1e-6), "closure tolerance", True)
    if total <= 0 or abs(total - target) > tolerance:
        raise PVTStudyError("analysis %s composition closure %s differs from %s beyond %s; no silent normalization" %
                            (result.get("id"), total, target, tolerance))
    fractions = [v / total for v in values]
    if basis.startswith("mass"):
        molar_amounts = [v / c["molar_mass_kg_mol"] for c, v in zip(components, fractions)]
        fractions = [v / sum(molar_amounts) for v in molar_amounts]
    for component, z in zip(components, fractions):
        component["mole_fraction"] = z
        component["raw_value"] = component["value"]
    result["pre_normalization_sum"] = total
    result["closure_error"] = total - target
    result["normalization_factor"] = 1.0 / total
    result["prepared_basis"] = "mole_fraction"
    return result


def recombine(analyses, contract):
    """Mix declared phase mole amounts with an exact component/mole balance."""
    if contract.get("basis") != "molar_amount":
        raise PVTStudyError("recombination requires basis molar_amount; measured GOR needs a sourced conversion first")
    phases = contract.get("phases", [])
    if not isinstance(phases, list) or len(phases) < 2 or any(not isinstance(p, dict) for p in phases):
        raise PVTStudyError("recombination needs at least two contributing phase analyses")
    definitions = {}
    amounts = {}
    total = 0.0
    for phase in phases:
        if phase.get("analysis_id") not in analyses:
            raise PVTStudyError("unknown recombination analysis %s" % phase.get("analysis_id"))
        amount = finite(phase.get("moles"), "phase moles", True)
        total += amount
        for component in analyses[phase["analysis_id"]]["components"]:
            name = component["name"]
            definition = {k: component.get(k) for k in ("kind", "molar_mass_kg_mol", "density_g_cm3")}
            if name in definitions and definitions[name]["definition"] != definition:
                raise PVTStudyError("recombination component %s has inconsistent pseudo properties" % name)
            definitions[name] = {"definition": definition, "component": component}
            amounts[name] = amounts.get(name, 0.0) + amount * component["mole_fraction"]
    components = []
    for name, amount in amounts.items():
        component = copy.deepcopy(definitions[name]["component"])
        component["value"] = amount / total
        components.append(component)
    prepared = normalize_analysis({"id": contract["output_analysis_id"], "sample_id": contract["output_sample_id"],
                                   "basis": "mole_fraction", "source": contract.get("source"), "components": components})
    audit = {"id": contract["id"], "phases": copy.deepcopy(phases), "total_input_moles": total,
             "total_output_moles": sum(amounts.values()), "component_moles": amounts,
             "mole_balance_error": sum(amounts.values()) - total,
             "status": "PASS", "equation": "n_i = sum(n_phase * z_i_phase)"}
    if abs(audit["mole_balance_error"]) > max(1e-12, total * 1e-12):
        raise PVTStudyError("recombination mole balance failed")
    return prepared, audit


def validate_study(study):
    """Validate and normalize the single-final-fluid study, retaining all raw inputs."""
    result = copy.deepcopy(study)
    if result.get("schema") != "pvt_study.v1":
        raise PVTStudyError("expected schema pvt_study.v1")
    metadata = result.get("study", {})
    if not isinstance(metadata, dict):
        raise PVTStudyError("study metadata must be a mapping")
    if not metadata.get("id") or not metadata.get("title") or not metadata.get("intended_use"):
        raise PVTStudyError("study needs id, title and intended_use")
    if not isinstance(metadata.get("synthetic"), bool):
        raise PVTStudyError("study.synthetic must explicitly identify synthetic versus real evidence")
    samples = _indexed(result.get("samples", []), "sample")
    if not samples:
        raise PVTStudyError("at least one sample and its basis are required")
    for sample in samples.values():
        if not sample.get("type") or not sample.get("source") or "accepted" not in sample:
            raise PVTStudyError("sample %s needs type, source and explicit acceptance" % sample["id"])
        if sample["accepted"] is not True:
            raise PVTStudyError("sample %s is not accepted for modeling" % sample["id"])
        if metadata["synthetic"] and sample.get("synthetic") is not True:
            raise PVTStudyError("synthetic sample metadata must be explicitly labeled synthetic")
    analyses = {}
    for key, analysis in _indexed(result.get("analyses", []), "analysis").items():
        if analysis.get("sample_id") not in samples or not analysis.get("source"):
            raise PVTStudyError("analysis %s needs known sample_id and source provenance" % key)
        analyses[key] = normalize_analysis(analysis)
    result["recombination_audit"] = []
    recombinations = result.get("recombination", [])
    if not isinstance(recombinations, list):
        raise PVTStudyError("recombination must be a list of explicit contracts")
    for contract in recombinations:
        if not isinstance(contract, dict) or any(not contract.get(k) for k in
                                                ("id", "output_analysis_id", "output_sample_id", "phases")):
            raise PVTStudyError("recombination needs id, output_analysis_id, output_sample_id and phases")
        if contract.get("output_sample_id") not in samples or not contract.get("source"):
            raise PVTStudyError("recombination needs a known output sample and source")
        if contract.get("output_analysis_id") in analyses:
            raise PVTStudyError("recombination must not overwrite raw analysis")
        prepared, audit = recombine(analyses, contract)
        analyses[prepared["id"]] = prepared
        result["recombination_audit"].append(audit)
    fluid = result.get("fluid", {})
    if not isinstance(fluid, dict):
        raise PVTStudyError("fluid definition must be a mapping")
    extra_fluid_keys = set(fluid) - {"sample_id", "analysis_id", "eos", "mixing_rule", "temperature", "pressure",
                                     "viscosity_model", "characterization", "binary_interactions",
                                     "temperature_k", "pressure_bara"}
    if extra_fluid_keys:
        raise PVTStudyError("unsupported fluid settings must not be silently ignored: %s" % sorted(extra_fluid_keys))
    analysis = analyses.get(fluid.get("analysis_id"))
    if not analysis or analysis["sample_id"] != fluid.get("sample_id"):
        raise PVTStudyError("fluid must link to an analysis of its explicit sample_id")
    if fluid.get("eos") not in ("SRK", "PR", "PR78") or fluid.get("mixing_rule") != 2:
        raise PVTStudyError("this workflow supports explicit SRK/PR/PR78 with classic mixing_rule 2 only")
    fluid["temperature_k"] = quantity(fluid.get("temperature"), "K")
    fluid["pressure_bara"] = quantity(fluid.get("pressure"), "bara")
    component_names = {c["name"] for c in analysis["components"]}
    plus_names = {c["name"] for c in analysis["components"] if c.get("kind") == "plus"}
    seen_pairs = set()
    for pair in fluid.get("binary_interactions", []):
        if not isinstance(pair, dict) or set(pair) - {"component_i", "component_j", "kij"}:
            raise PVTStudyError("binary interaction supports constant kij only; unsupported settings cannot be ignored")
        first, second = pair.get("component_i"), pair.get("component_j")
        if first not in component_names or second not in component_names or first == second:
            raise PVTStudyError("binary interaction requires two distinct known input components")
        if first in plus_names or second in plus_names:
            raise PVTStudyError("input plus-fraction BIPs require an explicit post-characterization mapping; use bounded grouped regression instead")
        names = tuple(sorted((first, second)))
        if names in seen_pairs:
            raise PVTStudyError("duplicate or reversed binary interaction pair")
        seen_pairs.add(names)
        finite(pair.get("kij"), "binary interaction parameter")
    characterization = fluid.get("characterization", {})
    if not isinstance(characterization, dict) or set(characterization) - {"plus_fraction_model", "lumps", "mass_relative_tolerance"}:
        raise PVTStudyError("unsupported characterization settings cannot be silently ignored")
    calibration = result.get("calibration", {})
    if not isinstance(calibration, dict):
        raise PVTStudyError("calibration must be a mapping")
    if set(calibration) - {"parameters", "max_iterations"}:
        raise PVTStudyError("unsupported calibration settings cannot be silently ignored")
    parameters = calibration.get("parameters", [])
    if not isinstance(parameters, list):
        raise PVTStudyError("calibration parameters must be a list")
    iterations = calibration.get("max_iterations", 30)
    if isinstance(iterations, bool) or not isinstance(iterations, int) or iterations <= 0:
        raise PVTStudyError("calibration max_iterations must be a positive integer")
    fitted_names = set()
    for parameter in parameters:
        if not isinstance(parameter, dict) or set(parameter) - {"name", "lower", "upper", "initial", "source"}:
            raise PVTStudyError("unsupported calibration parameter settings cannot be silently ignored")
        name = parameter.get("name")
        if name not in FIT_PARAMETERS or not parameter.get("source"):
            raise PVTStudyError("fit parameter needs a supported constant BIP name and sourced bound rationale")
        if name in fitted_names:
            raise PVTStudyError("duplicate fit parameter %s" % name)
        fitted_names.add(name)
        lower = finite(parameter.get("lower"), "fit lower bound")
        upper = finite(parameter.get("upper"), "fit upper bound")
        initial = finite(parameter.get("initial"), "fit initial guess")
        if lower >= upper or not lower <= initial <= upper:
            raise PVTStudyError("fit requires lower < upper and bounded initial guess")
    experiments = _indexed(result.get("experiments", []), "experiment")
    for experiment in experiments.values():
        if experiment.get("sample_id") != fluid["sample_id"]:
            raise PVTStudyError("experiment must identify this final fluid sample")
        if experiment.get("kind") not in KINDS or experiment.get("role") not in ROLES:
            raise PVTStudyError("unsupported experiment kind/role")
        experiment["temperature_k"] = quantity(experiment.get("temperature"), "K")
        pressures = experiment.get("pressures")
        if not isinstance(pressures, dict) or not isinstance(pressures.get("values"), list) or not pressures["values"]:
            raise PVTStudyError("experiment pressures need nonempty values and unit")
        experiment["pressures_bara"] = [quantity({"value": v, "unit": pressures.get("unit")}, "bara")
                                          for v in pressures["values"]]
        if experiment["kind"] in ("CCE", "CVD", "DLE"):
            if any(a <= b for a, b in zip(experiment["pressures_bara"], experiment["pressures_bara"][1:])):
                raise PVTStudyError("depletion/expansion pressures must retain a strictly decreasing protocol")
    observations = _indexed(result.get("observations", []), "observation")
    source_roles = {}
    for observation in observations.values():
        experiment = experiments.get(observation.get("experiment_id"))
        if not experiment or not observation.get("source"):
            raise PVTStudyError("observation needs a known experiment_id and source")
        prop = observation.get("property")
        if prop not in OBSERVABLES or observation.get("unit") != OBSERVABLES[prop]:
            raise PVTStudyError("unsupported observable or explicit unit for %s" % prop)
        values = observation.get("values")
        if not isinstance(values, list) or len(values) != len(experiment["pressures_bara"]):
            raise PVTStudyError("observation array length must match the full experiment protocol")
        for value in values:
            finite(value, "observed %s" % prop)
        uncertainty = observation.get("uncertainty")
        if uncertainty is not None:
            if not isinstance(uncertainty, list) or len(uncertainty) != len(values):
                raise PVTStudyError("observation uncertainty array length must match values")
            for value in uncertainty:
                finite(value, "observation uncertainty", True)
        if observation.get("source_kind") not in ("measured", "synthetic_reference"):
            raise PVTStudyError("observations must distinguish measured from synthetic_reference")
        if metadata["synthetic"] and observation["source_kind"] == "measured":
            raise PVTStudyError("synthetic fixture values cannot be labeled measured")
        observation["role"] = experiment["role"]
        source_key = json.dumps(observation["source"], sort_keys=True)
        source_roles.setdefault(source_key, set()).add(observation["role"])
    if any("calibration" in roles and "validation" in roles for roles in source_roles.values()):
        raise PVTStudyError("same measurement source cannot supply independent calibration and validation")
    for experiment in experiments.values():
        if experiment["role"] != "simulation" and not any(o["experiment_id"] == experiment["id"] for o in observations.values()):
            raise PVTStudyError("calibration/validation experiment needs actual observations")
    result["samples"] = list(samples.values())
    result["analyses"] = list(analyses.values())
    result["experiments"] = list(experiments.values())
    result["observations"] = list(observations.values())
    result["prepared_analysis"] = analysis
    return result
