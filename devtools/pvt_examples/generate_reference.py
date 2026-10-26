"""Regenerate frozen synthetic oil experiment tables from a separate reference EOS.

Run deliberately from a source build, not during a study. The reference model has
grouped methane/heavy BIP 0.04; the initial candidate in oil.yaml does not. No
physical sample or experimental accuracy is implied by this numerical fixture.
"""

import copy
import json
from pathlib import Path
import subprocess
import sys

import yaml

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

import pvt_study
from pvt_data import identity, quantity, validate_study


def generate_case(case="oil"):
    """Create source-controlled synthetic laboratory-style input tables, without noise."""
    if case not in ("oil", "condensate"):
        raise ValueError("case must be oil or condensate")
    prefix = "OIL" if case == "oil" else "CONDENSATE"
    reference_bip = 0.04 if case == "oil" else 0.025
    path = HERE / (case + ".yaml")
    study = pvt_study.load_study(path)
    study["observations"] = []
    for experiment in study["experiments"]:
        experiment["role"] = "simulation"
    study["calibration"] = {"parameters": []}
    cce = next(e for e in study["experiments"] if e["id"] == prefix + "-CCE")
    if case == "oil":
        cce["pressures"]["values"] = [400.0, 300.0, 200.0, 100.0, 50.0]
    if not any(e["id"] == prefix + "-CCE-HOLDOUT" for e in study["experiments"]):
        holdout = copy.deepcopy(cce)
        holdout["id"] = prefix + "-CCE-HOLDOUT"
        holdout["temperature"]["value"] -= 10.0
        study["experiments"].append(holdout)
    if case == "oil" and not any(e["id"] == "OIL-VISCOSITY" for e in study["experiments"]):
        study["experiments"].append({"id": "OIL-VISCOSITY", "sample_id": "OIL-001", "kind": "VISCOSITY",
                                     "role": "simulation", "temperature": {"value": 100.0, "unit": "C"},
                                     "pressures": {"values": [100.0, 50.0, 10.0], "unit": "bara"},
                                     "reference_basis": "Oil-phase dynamic viscosity at stated absolute conditions"})
    prepared = validate_study(study)
    ns, adapter = pvt_study._runtime()
    truth = pvt_study._build_fluid(ns, prepared)
    pvt_study._characterize(truth, adapter, prepared)
    parameter = ns.JClass("neqsim.pvtsimulation.regression.RegressionParameter")
    parameter.BIP_METHANE_C7PLUS.applyToFluid(truth, reference_bip)
    reference_capture = pvt_study._capture(adapter, truth)
    revision = subprocess.check_output(["git", "-C", str(HERE.parent.parent), "rev-parse", "HEAD"], text=True).strip()
    generated = []
    definitions = {
        prefix + "-CCE": [("relative_volume", "relativeVolume", "1", 0.001, 0.0005)],
        prefix + "-CCE-HOLDOUT": [("relative_volume", "relativeVolume", "1", 0.001, 0.0005)],
        "OIL-DLE": [("rs_sm3_sm3", "rsSm3PerSm3", "Sm3/Sm3", 0.5, 0.05),
                    ("bo_m3_sm3", "boM3PerSm3", "m3/Sm3", 0.005, 0.001),
                    ("oil_density_kg_m3", "oilDensityKgPerM3", "kg/m3", 1.0, 0.1)],
        "OIL-VISCOSITY": [("viscosity_pa_s", "oilViscosityPaS", "Pa s", 0.00001, 0.00001)],
        "CONDENSATE-CVD": [("liquid_dropout_percent", "liquidRelativeVolume", "% saturation volume", 0.05, 0.01),
                           ("gas_z_factor", "gasZ", "1", 0.002, 0.001)],
        "CONDENSATE-VISCOSITY": [("viscosity_pa_s", "gasViscosityPaS", "Pa s", 0.000001, 0.000001)],
    }
    for experiment in study["experiments"]:
        if experiment["id"] not in definitions:
            continue
        experiment["role"] = "calibration" if experiment["id"] == prefix + "-CCE" else "validation"
        experiment["source_kind"] = "synthetic_reference"
        prediction = json.loads(str(adapter.simulate(truth, experiment["kind"], quantity(experiment["temperature"], "K"),
                                                     pvt_study._array(experiment["pressures"]["values"]))))
        for prop, key, unit, uncertainty, limit in definitions[experiment["id"]]:
            values = prediction["values"][key]
            if any(value is None for value in values):
                raise ValueError("synthetic fixture cannot disguise a failed or absent phase")
            observation = {"id": experiment["id"] + "-" + prop, "experiment_id": experiment["id"],
                           "property": prop, "unit": unit, "values": values,
                           "uncertainty": [uncertainty] * len(values),
                           "uncertainty_basis": "Assumed synthetic standard uncertainty for a software fixture; not an instrument measurement",
                           "source_kind": "synthetic_reference",
                           "source": {"fixture": case + "_reference.json", "table": experiment["id"] + "/" + prop,
                                      "reference_parameter_identity": identity(reference_capture)},
                           "acceptance_max_absolute_error": limit}
            if prop == "viscosity_pa_s":
                observation["phase"] = "oil" if case == "oil" else "gas"
            study["observations"].append(observation)
        generated.append({"experiment_id": experiment["id"], "kind": experiment["kind"],
                          "temperature_k": quantity(experiment["temperature"], "K"),
                          "pressures_bara": experiment["pressures"]["values"],
                          "reference_predictions": {key: value for key, value in prediction.items()
                                                     if key not in ("legacyReportMarkdown", "reportMarkdown", "legacyReportLimitations")}})
    provenance = {"kind": "synthetic_reference", "reference_generator": "pvt_examples/generate_reference.py",
                  "engine_source_revision": revision, "engine_and_workflow_identity": pvt_study.code_identity(),
                  "reference_eos": "PR, classic mixing rule 2, declared LBC transport",
                  "reference_grouped_methane_heavy_bip": reference_bip,
                  "initial_candidate": "Same composition and characterization, original database BIPs before fitting",
                  "reference_parameter_identity": identity(reference_capture), "added_noise": "none", "noise_seed": None,
                  "independence": "Temperature and experiment hold-outs test numerical recovery; all share the same synthetic EOS and do not establish independent physical accuracy",
                  "uncertainty": "Explicit arbitrary synthetic standard uncertainties; no confidence interval or laboratory accreditation claim"}
    study["synthetic_reference"] = provenance
    study["calibration"] = {"parameters": [{"name": "BIP_METHANE_C7PLUS",
                                             "lower": 0.0 if case == "oil" else 0.024,
                                             "upper": 0.1 if case == "oil" else 0.026,
                                             "initial": 0.03 if case == "oil" else 0.0248,
                                             "source": ("Predeclared software-fixture bounds for numerical recovery" if case == "oil" else
                                                        "Restricted synthetic numerical fixture domain around the independently fixed reference BIP 0.025. Broader trials and some starting guesses reject nonconservative flash endpoints; this is not global optimizer qualification.")}],
                             "max_iterations": 15}
    study["data_gaps"] = ["No physical sample, laboratory measurement or target simulator ingestion is supplied",
                           "Synthetic data demonstrate recovery and replay only; no experimental-accuracy claim",
                           "Supplied synthetic uncertainties are reported in metrics; current CCE fitting engine uses its fixed residual scale",
                           "No standards conformity or statistical parameter-confidence interval is claimed"]
    if case == "condensate":
        study["data_gaps"].append("Condensate calibration is restricted to the declared numerical fixture bounds and start; broader near-critical optimizer trials can fail conservation guards")
    validate_study(study)
    fixture = {"schema": "synthetic_pvt_reference.v1", "provenance": provenance,
               "reference_parameter_inventory": reference_capture, "experiments": generated,
               "observations": study["observations"]}
    pvt_study.write_json(HERE / (case + "_reference.json"), fixture)
    path.write_text(yaml.safe_dump(study, sort_keys=False, allow_unicode=True), encoding="utf-8")
    return path


if __name__ == "__main__":
    for case in ("oil", "condensate"):
        print(generate_case(case))
