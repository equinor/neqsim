"""Auditable PVT fluid studies using NeqSim's existing Java implementations.

The CLI and :func:`run_pipeline` share one canonical dataset. Reports never
execute the solver. Independent process replay is mandatory for E300 handover.
"""

import argparse
import copy
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

import yaml

from pvt_data import FIT_PARAMETERS, PVTQualityError, PVTStudyError, STAGES, finite, identity, quantity, validate_study

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
PROPERTY_KEYS = {
    "relative_volume": "relativeVolume",
    "liquid_dropout_percent": "liquidRelativeVolume",
    "gas_z_factor": "gasZ",
    "rs_sm3_sm3": "rsSm3PerSm3",
    "bo_m3_sm3": "boM3PerSm3",
    "oil_density_kg_m3": "oilDensityKgPerM3",
}


def write_json(path, value):
    """Atomically write strict full-precision JSON with no NaN placeholders."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(value, indent=2, ensure_ascii=False, allow_nan=False) + "\n"
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(text, encoding="utf-8")
    temporary.replace(path)
    return str(path)


def load_study(path):
    """Load JSON or safe YAML; return raw input without changing its data."""
    path = Path(path)
    with path.open(encoding="utf-8") as stream:
        value = json.load(stream) if path.suffix.lower() == ".json" else yaml.safe_load(stream)
    if not isinstance(value, dict):
        raise PVTStudyError("study input must be a mapping")
    return value


def code_identity():
    """Identify workflow, actual compiled engine, databases and dependency bytes.

    Hashing actual classpath artifacts prevents cache reuse after an engine,
    database or dependency update even if the adapter's source is unchanged.
    """
    files = list(HERE.glob("pvt_*.py"))
    java = ROOT / "src/main/java/neqsim/pvtsimulation/util/PVTFluidStudy.java"
    if java.exists():
        files.append(java)
    hashes = {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
              for path in sorted(files)}
    for directory in (ROOT / "target/classes", ROOT / "src/main/resources"):
        if directory.exists():
            for path in sorted(directory.rglob("*")):
                if path.is_file():
                    hashes[str(path.relative_to(ROOT))] = hashlib.sha256(path.read_bytes()).hexdigest()
    jars = [path for path in (ROOT / "target").glob("neqsim-*.jar")
            if not any(marker in path.name for marker in ("-sources", "-javadoc", "original-"))]
    classpath_file = ROOT / "target/neqsim-dev-classpath.txt"
    if jars:
        dependencies = [sorted(jars)[-1]]
    elif classpath_file.exists():
        dependencies = [Path(p) for p in classpath_file.read_text(encoding="utf-8").strip().split(os.pathsep) if p]
    elif not (ROOT / "pom.xml").exists():
        # Match the installed-toolkit fallback without starting or importing a JVM.
        configured = os.environ.get("NEQSIM_JAR")
        location = Path(configured) if configured else None
        dependencies = ([location] if location and location.is_file() else
                        sorted(location.glob("*.jar")) if location and location.is_dir() else [])
        if not dependencies:
            specification = importlib.util.find_spec("neqsim")
            if specification and specification.submodule_search_locations:
                for package_root in specification.submodule_search_locations:
                    dependencies.extend(sorted((Path(package_root) / "lib").glob("*.jar")))
    else:
        dependencies = []
    for index, path in enumerate(dependencies):
        if not path.is_file():
            raise PVTStudyError("runtime dependency is unavailable: %s" % path.name)
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(block)
        hashes["runtime-dependency-%04d-%s" % (index, path.name)] = digest.hexdigest()
    if (ROOT / ".git").exists():
        revision = subprocess.run(["git", "-C", str(ROOT), "rev-parse", "HEAD"], capture_output=True, text=True, check=True)
        hashes["sourceRevision"] = revision.stdout.strip()
    return identity(hashes)


def _runtime():
    """Load the selected workspace classes instead of a separately installed engine."""
    from neqsim_dev_setup import neqsim_init
    ns = neqsim_init(project_root=ROOT, verbose=False, convert_strings=True)
    adapter = ns.JClass("neqsim.pvtsimulation.util.PVTFluidStudy")
    source = Path(str(adapter.class_.getProtectionDomain().getCodeSource().getLocation().toURI().getPath())).resolve()
    if (ROOT / "pom.xml").exists() and source != (ROOT / "target/classes").resolve():
        raise PVTStudyError("running JVM loaded a different NeqSim build; restart Python with this workspace")
    started = ns.JClass("java.lang.management.ManagementFactory").getRuntimeMXBean().getStartTime() / 1000.0
    artifacts = source.rglob("*.class") if source.is_dir() else [source]
    if any(path.stat().st_mtime > started + 0.001 for path in artifacts):
        raise PVTStudyError("compiled engine changed after this JVM started; restart Python to avoid stale classes")
    return ns, adapter


def _array(values):
    """Convert a validated Python sequence to the explicit Java primitive boundary."""
    import jpype
    return jpype.JArray(jpype.JDouble)(values)


def _capture(adapter, fluid):
    """Read the full cubic parameter inventory without mutating the supplied fluid."""
    return json.loads(str(adapter.capture(fluid)))


def _snapshot(adapter, fluid, stage, data, out):
    """Freeze a distinct model checkpoint, identity and human-readable inventory."""
    capture = _capture(adapter, fluid)
    model_id = "%s-%s" % (stage, identity(capture)[:16])
    native = out / "models" / (stage + ".ser")
    native.parent.mkdir(parents=True, exist_ok=True)
    adapter.writeNativeSnapshot(fluid, str(native))
    parameters = write_json(out / "models" / (stage + ".parameters.json"), capture)
    record = {"id": model_id, "stage": stage, "sample_id": data["fluid"]["sample_id"],
              "capture": capture, "snapshot_path": str(native), "parameters_path": parameters,
              "sha256": hashlib.sha256(native.read_bytes()).hexdigest(),
              "parameter_identity": identity(capture)}
    data["models"].append(record)
    return record


def _build_fluid(ns, data):
    """Construct the current reusable SystemInterface using explicit prepared data."""
    config = data["fluid"]
    names = {"SRK": "SystemSrkEos", "PR": "SystemPrEos", "PR78": "SystemPrEos1978"}
    cls = ns.JClass("neqsim.thermo.system." + names[config["eos"]])
    fluid = cls(config["temperature_k"], config["pressure_bara"])
    for component in data["prepared_analysis"]["components"]:
        name, fraction = component["name"], component["mole_fraction"]
        kind = component.get("kind", "pure")
        if kind == "pure":
            fluid.addComponent(name, fraction)
        else:
            method = fluid.addPlusFraction if kind == "plus" else fluid.addTBPfraction
            method(name, fraction, component["molar_mass_kg_mol"], component["density_g_cm3"])
    fluid.setMixingRule(2)
    fluid.init(0)
    viscosity = config.get("viscosity_model", "PFCT")
    if viscosity not in ("PFCT", "LBC"):
        raise PVTStudyError("declared viscosity_model must be PFCT or LBC")
    ns.JClass("neqsim.pvtsimulation.util.PVTFluidStudy").configureViscosity(fluid, viscosity)
    return fluid


def _heavy_moments(capture):
    """Conserved overall mole/mass and additive normal-liquid-volume moments."""
    components = capture["components"]
    return {"mole_fraction_sum": sum(c["moleFraction"] for c in components),
            "mixture_molar_mass_kg_mol": sum(c["moleFraction"] * c["molarMassKgPerMol"] for c in components),
            "additive_liquid_volume_m3_mol": sum(c["moleFraction"] * c["molarMassKgPerMol"] /
                                                 (1000 * c["normalLiquidDensityGPerCm3"])
                                                 for c in components if c["normalLiquidDensityGPerCm3"] > 0)}


def _characterize(fluid, adapter, data):
    """Delegate plus splitting/lumping and audit conserved heavy-end moments."""
    before = _capture(adapter, fluid)
    settings = data["fluid"].get("characterization", {})
    plus = [c for c in data["prepared_analysis"]["components"] if c.get("kind") == "plus"]
    if plus:
        if len(plus) != 1:
            raise PVTStudyError("one plus fraction per final model is supported")
        characterizer = fluid.getCharacterization()
        model = settings.get("plus_fraction_model", "Pedersen")
        if model != "Pedersen":
            raise PVTStudyError("this workflow currently qualifies the Pedersen plus-fraction route only")
        characterizer.setPlusFractionModel("Pedersen")
        lumps = settings.get("lumps", 3)
        if isinstance(lumps, bool) or not isinstance(lumps, int) or not 1 <= lumps <= 30:
            raise PVTStudyError("characterization lumps must be an integer between 1 and 30")
        characterizer.configureLumping().model("PVTlumpingModel").plusFractionGroups(lumps).build()
        characterizer.characterisePlusFraction()
        fluid.setMixingRule(2)
        fluid.init(0)
    for pair in data["fluid"].get("binary_interactions", []):
        value = finite(pair.get("kij"), "binary interaction parameter")
        aliases = {raw["name"]: actual["name"] for raw, actual in
                   zip(data["prepared_analysis"]["components"], before["components"])}
        fluid.setBinaryInteractionParameter(aliases[pair["component_i"]], aliases[pair["component_j"]], value)
    # Characterization may select a heavy-oil transport default. Retain the
    # user's explicit model on the resulting final component representation.
    adapter.configureViscosity(fluid, data["fluid"].get("viscosity_model", "PFCT"))
    after = _capture(adapter, fluid)
    original, prepared = _heavy_moments(before), _heavy_moments(after)
    differences = {key: prepared[key] - original[key] for key in original}
    tol = finite(settings.get("mass_relative_tolerance", 1e-6), "mass conservation tolerance", True)
    mass_error = abs(differences["mixture_molar_mass_kg_mol"])
    if abs(differences["mole_fraction_sum"]) > 1e-8 or mass_error > max(1e-10, original["mixture_molar_mass_kg_mol"] * tol):
        raise PVTStudyError("characterization/lumping violated declared mole or mass conservation")
    data["characterization"] = {"settings": settings, "source_components": before["components"],
                                "final_components": after["components"], "before_moments": original,
                                "after_moments": prepared, "differences": differences,
                                "mole_mass_status": "PASS", "density_moment_status": "REPORTED",
                                "mapping": {"source_cut": plus[0]["name"] if plus else None,
                                            "final_pseudo_components": [c["name"] for c in after["components"] if c["isTBPFraction"]]},
                                "qualification": "Correlation-derived model properties; not experimental validation"}


def _simulate(adapter, fluid, experiments, model_id):
    """Run each actual experiment once on independent Java fluid copies."""
    output = []
    for config in experiments:
        experiment = copy.deepcopy(config)
        start = time.perf_counter()
        if config["kind"] == "SEPARATOR":
            stages = config.get("stages", [])
            if len(stages) < 2:
                raise PVTStudyError("separator protocol needs explicit stages including stock tank")
            pressures = [quantity(stage.get("pressure"), "bara") for stage in stages]
            temperatures = [quantity(stage.get("temperature"), "K") for stage in stages]
            result = adapter.simulateSeparator(fluid, config["temperature_k"], config["pressures_bara"][0],
                                               _array(pressures), _array(temperatures))
        else:
            result = adapter.simulate(fluid, config["kind"], config["temperature_k"], _array(config["pressures_bara"]))
        experiment["predictions"] = json.loads(str(result))
        experiment["model_id"] = model_id
        experiment["solve_seconds"] = time.perf_counter() - start
        experiment["status"] = "COMPLETED"
        output.append(experiment)
    return output


def _prediction_values(experiment, observation):
    """Resolve a physically named observable, preserving absent phases as unavailable."""
    values = experiment["predictions"].get("values", {})
    prop = observation["property"]
    key = PROPERTY_KEYS.get(prop)
    if prop == "viscosity_pa_s":
        phase = observation.get("phase")
        if phase not in ("gas", "oil", "aqueous"):
            raise PVTStudyError("viscosity observation requires gas, oil or aqueous phase identity")
        key = phase + "ViscosityPaS"
        if "phaseAvailability" in experiment["predictions"]:
            masks = experiment["predictions"]["phaseAvailability"]
            if not isinstance(masks, dict):
                raise PVTStudyError("phaseAvailability must be a dictionary of Boolean arrays")
            if phase not in masks:
                raise PVTStudyError("phaseAvailability.%s is required for viscosity observations" % phase)
            availability = masks[phase]
            if (not isinstance(availability, list) or any(type(value) is not bool for value in availability)
                    or len(availability) != len(observation["values"])):
                raise PVTStudyError("phaseAvailability.%s must be a Boolean array matching the observations" % phase)
            if not all(availability):
                raise PVTStudyError("observation cannot be compared against an absent-phase viscosity prediction")
    if key not in values:
        raise PVTStudyError("observable %s unavailable for experiment %s" % (prop, experiment["id"]))
    result = values[key]
    if len(result) != len(observation["values"]) or any(v is None for v in result):
        raise PVTStudyError("observation cannot be compared against a missing/failed predicted phase or point")
    if prop in ("viscosity_pa_s", "oil_density_kg_m3", "gas_z_factor", "relative_volume", "bo_m3_sm3") and any(v <= 0 for v in result):
        raise PVTStudyError("absent-phase zero is not a physical density or viscosity prediction")
    for value in result:
        finite(value, "prediction")
    return result


def _residuals(data):
    """Populate canonical baseline/final residual arrays and per-observable role metrics."""
    final = {e["id"]: e for e in data["experiments"]}
    baseline = {e["id"]: e for e in data.get("baseline_experiments", [])}
    metrics = []
    for observation in data["observations"]:
        predicted = _prediction_values(final[observation["experiment_id"]], observation)
        residual = [p - m for p, m in zip(predicted, observation["values"])]
        observation["predicted_values"] = predicted
        observation["residuals"] = residual
        if observation["experiment_id"] in baseline:
            observation["baseline_values"] = _prediction_values(baseline[observation["experiment_id"]], observation)
        n = len(residual)
        row = {"observation_id": observation["id"], "role": observation["role"],
               "property": observation["property"], "unit": observation["unit"], "count": n,
               "signed_bias": sum(residual) / n, "rmse": math.sqrt(sum(r * r for r in residual) / n),
               "max_absolute_error": max(abs(r) for r in residual),
               "mean_absolute_error": sum(abs(r) for r in residual) / n,
               "source_kind": observation["source_kind"]}
        if observation.get("uncertainty"):
            row["uncertainty_normalized_rmse"] = math.sqrt(sum((r / u) ** 2 for r, u in
                                                               zip(residual, observation["uncertainty"])) / n)
        limit = observation.get("acceptance_max_absolute_error")
        if limit is not None:
            finite(limit, "acceptance max absolute error", True)
            row["acceptance_max_absolute_error"] = limit
            row["status"] = "PASS" if row["max_absolute_error"] <= limit else "FAIL"
        else:
            row["status"] = "NOT ASSESSED"
        metrics.append(row)
    data["metrics"] = metrics


def _calibrate(ns, adapter, fluid, data):
    """Fit only explicitly registered CCE targets with bounded existing PVTRegression."""
    contract = data.get("calibration") or {}
    parameters = contract.get("parameters", [])
    observations = [o for o in data["observations"] if o["role"] == "calibration"]
    if not parameters:
        if observations:
            raise PVTStudyError("calibration observations require an explicit bounded parameter contract")
        data["calibration"] = {"status": "NOT REQUESTED", "parameters": [],
                               "reason": "No fitted model or optimizer convergence is claimed"}
        return fluid
    if not observations:
        raise PVTStudyError("regression requires actual calibration observations")
    experiments = {e["id"]: e for e in data["experiments"]}
    for observation in observations:
        experiment = experiments[observation["experiment_id"]]
        if experiment["kind"] != "CCE" or observation["property"] != "relative_volume":
            raise PVTStudyError("qualified fitting currently supports CCE relative_volume only; CVD/DLE pointwise regression does not preserve the depletion protocol")
    regression_cls = ns.JClass("neqsim.pvtsimulation.regression.PVTRegression")
    parameter_cls = ns.JClass("neqsim.pvtsimulation.regression.RegressionParameter")
    regression = regression_cls(fluid.clone())
    regression.setVerbose(False)
    regression.setMaxIterations(int(contract.get("max_iterations", 30)))
    for observation in observations:
        experiment = experiments[observation["experiment_id"]]
        regression.addCCEData(_array(experiment["pressures_bara"]), _array(observation["values"]),
                              experiment["temperature_k"])
    for parameter in parameters:
        if parameter.get("name") not in FIT_PARAMETERS or not parameter.get("source"):
            raise PVTStudyError("fit parameter needs a supported constant BIP name and sourced bound rationale")
        lower = finite(parameter.get("lower"), "fit lower bound")
        upper = finite(parameter.get("upper"), "fit upper bound")
        initial = finite(parameter.get("initial"), "fit initial guess")
        if not lower <= initial <= upper or lower >= upper:
            raise PVTStudyError("fit requires lower < upper and bounded initial guess")
        regression.addRegressionParameter(parameter_cls.valueOf(parameter["name"]), lower, upper, initial)
    start = time.perf_counter()
    result = regression.runRegression()
    fitted = [finite(result.getOptimizedValue(parameter_cls.valueOf(p["name"])), "fitted parameter")
              for p in parameters]
    # RegressionResult has no public stopping-reason accessor. A finite objective is
    # recorded as a candidate; an explicit independent residual gate decides acceptance.
    rows = []
    for parameter, value in zip(parameters, fitted):
        if not parameter["lower"] <= value <= parameter["upper"]:
            raise PVTStudyError("optimizer returned a parameter outside declared bounds")
        rows.append(dict(parameter, final=value, bound_hit=min(abs(value - parameter["lower"]),
                                                             abs(value - parameter["upper"])) < 1e-8))
    data["calibration"] = {"status": "CANDIDATE", "parameters": rows,
                           "engine": "PVTRegression / LevenbergMarquardt",
                           "engine_objective": finite(result.getTotalObjective(), "fit objective"),
                           "engine_chi_square": finite(result.getFinalChiSquare(), "fit chi-square"),
                           "stopping_reason": "Unavailable from current RegressionResult; not asserted converged",
                           "weighting": "Existing CCE engine uses fixed 0.01 residual scale; supplied measurement uncertainty is reported in post-fit metrics, not applied by this engine",
                           "uncertainty_status": "NOT QUALIFIED; no confidence interval is asserted",
                           "solve_seconds": time.perf_counter() - start}
    return result.getTunedFluid()


def _validate_models(adapter, fluids, data):
    """Assert individual thermodynamic checks at declared single-state test points."""
    rows = []
    grid = data.get("validation", {}).get("states") or [
        {"temperature": data["fluid"]["temperature"], "pressure": data["fluid"]["pressure"]}]
    for stage, fluid in fluids.items():
        model_id = next(m["id"] for m in data["models"] if m["stage"] == stage)
        for state in grid:
            temp, pressure = quantity(state.get("temperature"), "K"), quantity(state.get("pressure"), "bara")
            trial = fluid.clone()
            trial.setTemperature(temp)
            trial.setPressure(pressure)
            checks = json.loads(str(adapter.checkThermodynamics(trial)))
            rows.append({"model_id": model_id, "stage": stage, "temperature_k": temp,
                         "pressure_bara": pressure, "checks": checks,
                         "status": "PASS" if checks.get("passed") else "FAIL"})
    data["consistency"] = rows
    _residuals(data)
    data["evidence_level"] = "NUMERICALLY VERIFIED - synthetic workflow/replay only" if data["study"]["synthetic"] else "MODEL CHECKED - physical validation requires accepted independent measurements"
    measured_holdouts = [o for o in data["observations"] if o["role"] == "validation" and o["source_kind"] == "measured"]
    if not measured_holdouts:
        data["findings"].append({"code": "NO-INDEPENDENT-MEASUREMENTS", "status": "WARN",
                                 "message": "No independent measured hold-out data; experimental accuracy is not validated"})


def _fresh_replay(native, e300, temperature_k, pressures, output):
    """Audit in a new OS process/JVM; never restore missing exported parameters."""
    command = [sys.executable, str(Path(__file__).resolve()), "--audit-worker", str(native), str(e300),
               repr(temperature_k), ",".join(repr(p) for p in pressures), "--worker-out", str(output)]
    result = subprocess.run(command, capture_output=True, text=True, timeout=240)
    if not output.exists():
        raise PVTQualityError("fresh-runtime E300 audit produced no evidence: %s" % result.stderr[-2000:])
    audit = json.loads(output.read_text(encoding="utf-8"))
    audit["fresh_process"] = True
    audit["process_exit_code"] = result.returncode
    if result.returncode != 0 or not audit.get("passed"):
        raise PVTQualityError("fresh-runtime E300 audit failed; inspect %s" % output)
    write_json(output, audit)
    return audit


def _export(adapter, fluid, model, data, out):
    """Export final E300/portable JSON and verify the actual delivered E300 bytes."""
    path = out / "models" / "final_fluid.e300"
    metadata = json.loads(str(adapter.exportE300(fluid, str(path), data["fluid"]["temperature_k"])))
    snapshot = _capture(adapter, fluid)
    portable = snapshot.get("portableFluidJson")
    if portable is None:
        raise PVTStudyError("supported portable NeqSim JSON definition unavailable")
    json_path = write_json(out / "models" / "final_fluid.json", portable)
    pressures = data.get("validation", {}).get("export_pressures_bara") or [data["fluid"]["pressure_bara"], 50.0]
    pressures = [finite(p, "export pressure", True) for p in pressures]
    audit_path = out / "validation" / "e300_fresh_replay.json"
    audit_path.parent.mkdir(parents=True, exist_ok=True)
    audit = _fresh_replay(Path(model["snapshot_path"]), path, data["fluid"]["temperature_k"], pressures, audit_path)
    data["exports"] = [{"id": "E300-" + hashlib.sha256(path.read_bytes()).hexdigest()[:16],
                        "model_id": model["id"], "e300": str(path), "native": model["snapshot_path"],
                        "json": json_path, "metadata": metadata, "audit": audit,
                        "status": "PASS", "target_simulator_compatibility": "UNVERIFIED"}]


def _accept_calibration(data):
    """Finalize scientific fit acceptance before any report is rendered."""
    if data.get("calibration", {}).get("status") == "CANDIDATE":
        metrics = [m for m in data.get("metrics", []) if m["role"] == "calibration"]
        accepted = bool(metrics) and all(m["status"] == "PASS" for m in metrics)
        data["calibration"]["status"] = "ACCEPTED BY RESIDUAL GATE" if accepted else "NOT ACCEPTED"
        if not accepted:
            data["findings"].append({"code": "FIT-NOT-ACCEPTED", "status": "FAIL",
                                     "message": "Calibration lacks passing predeclared residual acceptance limits"})


def _summary(data, out):
    """Keep scientific acceptance separate from workflow completion."""
    failures = [f for f in data["findings"] if f.get("status") == "FAIL"]
    failures.extend(f for f in data.get("consistency", []) if f.get("status") == "FAIL")
    failures.extend(f for f in data.get("metrics", []) if f.get("status") == "FAIL")
    return {"study_id": data["study"]["id"], "status": "FAIL" if failures else "PASS",
            "evidence_level": data.get("evidence_level", "PREPARATION ONLY"),
            "completed_stages": data["completed_stages"], "dataset": str(out / "dataset/pvt_study.json"),
            "model_count": len(data.get("models", [])), "experiment_count": len(data.get("experiments", [])),
            "report": data.get("report"), "exports": [{k: e[k] for k in ("e300", "json", "native", "status")}
                                                      for e in data.get("exports", [])],
            "experimental_validation": "NOT CLAIMED", "failure_count": len(failures)}


def _manifest(out, data, summary):
    """Hash every delivered file so resume cannot reuse a stale or edited artifact."""
    files = {}
    for path in sorted(out.rglob("*")):
        if (path.is_file() and "history" not in path.relative_to(out).parts
                and path.name not in ("manifest.json",) and not path.name.endswith(".tmp")):
            files[str(path.relative_to(out))] = hashlib.sha256(path.read_bytes()).hexdigest()
    write_json(out / "manifest.json", {"input_identity": data["input_identity"], "code_identity": data["code_identity"],
                                        "completed_stages": summary["completed_stages"], "files": files})


def _archive_previous(out):
    """Retain superseded managed evidence; never silently overwrite earlier raw inputs."""
    dataset = out / "dataset/pvt_study.json"
    if not dataset.exists():
        if any((out / name).exists() for name in ("models", "report", "validation")):
            raise PVTStudyError("output directory contains unrecognized results; choose a separate output directory")
        return
    previous = json.loads(dataset.read_text(encoding="utf-8"))
    if previous.get("schema") != "pvt_study_dataset.v1":
        raise PVTStudyError("refusing to replace an unrecognized dataset")
    archive = out / "history" / (str(time.time_ns()) + "-" + previous.get("input_identity", "unknown")[:12])
    archive.mkdir(parents=True)
    for name in ("dataset", "models", "report", "validation", "manifest.json"):
        source = out / name
        if source.exists():
            shutil.move(str(source), str(archive / name))


def run_pipeline(study, out_dir, strict=False, resume=False, stop_after="report"):
    """Run the complete chain, or stop after a named stage, retaining frozen evidence.

    Each non-report run rebuilds from immutable input. Exact completed runs may be
    reused with ``resume=True``; any changed source, input or delivered file forces
    invalidation. No untrusted native snapshot is accepted as a CLI source.
    """
    if stop_after not in STAGES:
        raise PVTStudyError("unknown stage %s" % stop_after)
    out = Path(out_dir).resolve()
    out.mkdir(parents=True, exist_ok=True)
    prepared = validate_study(study)
    mode = prepared["study"].get("mode", "characterization")
    if mode not in ("characterization", "simulation_only"):
        raise PVTStudyError("study.mode must be characterization or explicit simulation_only")
    if mode == "characterization" and STAGES.index(stop_after) >= STAGES.index("calibrate"):
        roles = {o["role"] for o in prepared["observations"]}
        if not {"calibration", "validation"}.issubset(roles):
            raise PVTStudyError("characterization handover starts with calibration and held-out experimental input tables; use explicit study.mode=simulation_only for an incomplete simulation study")
        for observation in prepared["observations"]:
            if observation["role"] in ("calibration", "validation"):
                finite(observation.get("acceptance_max_absolute_error"), "predeclared calibration/hold-out acceptance limit", True)
    input_id, code_id = identity(study), code_identity()
    manifest_path = out / "manifest.json"
    if resume and manifest_path.exists():
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        matches = manifest.get("input_identity") == input_id and manifest.get("code_identity") == code_id
        intact = all((out / name).is_file() and hashlib.sha256((out / name).read_bytes()).hexdigest() == digest
                     for name, digest in manifest.get("files", {}).items())
        if matches and intact and stop_after in manifest.get("completed_stages", []):
            summary = json.loads((out / "dataset/summary.json").read_text(encoding="utf-8"))
            if strict and summary["status"] == "FAIL":
                raise PVTQualityError("resumed study failed acceptance gates")
            return summary
    _archive_previous(out)
    raw_path = out / "inputs" / (input_id + ".raw.json")
    if raw_path.exists():
        if json.loads(raw_path.read_text(encoding="utf-8")) != study:
            raise PVTStudyError("immutable raw input identity conflict")
    else:
        write_json(raw_path, study)
    data = dict(prepared, input_identity=input_id, code_identity=code_id, schema="pvt_study_dataset.v1",
                models=[], parameters={}, findings=[], consistency=[], exports=[], timings={}, completed_stages=[],
                qc=[{"analysis_id": a["id"], "status": "PASS", "closure_error": a["closure_error"],
                     "normalization_factor": a["normalization_factor"]} for a in prepared["analyses"]])
    data["raw_data"] = {"path": str(raw_path), "sha256": hashlib.sha256(raw_path.read_bytes()).hexdigest(),
                         "input_identity": input_id}
    data["findings"].append({"code": "DEPLETION-CALIBRATION-LIMIT", "status": "WARN",
                             "message": "CVD/DLE simulation preserves the full protocol; pointwise calibration is blocked pending history-aware objectives",
                             "issue": "https://github.com/equinor/neqsim/issues/4596"})
    if mode == "simulation_only":
        data["findings"].append({"code": "SIMULATION-ONLY-INCOMPLETE", "status": "FAIL",
                                 "message": "Explicit simulation-only mode: no calibrated, held-out-verified characterization handover is claimed"})
    start = time.perf_counter()
    ns = adapter = fluid = None
    fluids = {}
    try:
        for stage in STAGES[:STAGES.index(stop_after) + 1]:
            clock = time.perf_counter()
            if stage == "characterize":
                ns, adapter = _runtime()
                data["software"] = {"python": sys.version.split()[0],
                                     "java": str(ns.JClass("java.lang.System").getProperty("java.version")),
                                     "engine_and_workflow_identity": code_id,
                                     "runtime_source": str(adapter.class_.getProtectionDomain().getCodeSource().getLocation())}
                fluid = _build_fluid(ns, data)
                _snapshot(adapter, fluid, "base", data, out)
                fluids["base"] = fluid.clone()
                _characterize(fluid, adapter, data)
                _snapshot(adapter, fluid, "characterized", data, out)
                fluids["characterized"] = fluid.clone()
            elif stage == "simulate":
                if not data["experiments"]:
                    raise PVTStudyError("a completed study requires an explicit experiment protocol")
                data["baseline_experiments"] = _simulate(adapter, fluid, data["experiments"], data["models"][-1]["id"])
                data["experiments"] = copy.deepcopy(data["baseline_experiments"])
            elif stage == "calibrate":
                fluid = _calibrate(ns, adapter, fluid, data)
                tuned = _snapshot(adapter, fluid, "tuned", data, out)
                fluids["tuned"] = fluid.clone()
                data["experiments"] = _simulate(adapter, fluid, prepared["experiments"], tuned["id"]) if data["calibration"]["status"] == "CANDIDATE" else copy.deepcopy(data["baseline_experiments"])
                final = _snapshot(adapter, fluid, "final", data, out)
                for experiment in data["experiments"]:
                    experiment["model_id"] = final["id"]
                data["parameters"] = final["capture"]
            elif stage == "validate":
                _validate_models(adapter, fluids, data)
                _accept_calibration(data)
            elif stage == "export":
                _export(adapter, fluid, data["models"][-1], data, out)
            elif stage == "report":
                from pvt_report import build_report
                if importlib.util.find_spec("reportlab") is None:
                    data["findings"].append({"code": "REPORT-PDF-UNAVAILABLE", "status": "FAIL",
                                             "message": "Mandatory PDF renderer unavailable; install the toolkit pdf extra"})
                data["status"] = _summary(data, out)["status"]
                write_json(out / "dataset/report_input.json", data)
                data["report"] = build_report(data, str(out / "report"))
                if not data["report"].get("pdf"):
                    data["findings"].append({"code": "REPORT-PDF-UNAVAILABLE", "status": "FAIL",
                                             "message": "Mandatory PDF was not generated; install the toolkit pdf extra"})
            data["timings"][stage] = time.perf_counter() - clock
            data["completed_stages"].append(stage)
            write_json(out / "dataset/pvt_study.json", data)
    except Exception as exc:
        data["findings"].append({"code": "WORKFLOW-FAILED", "status": "FAIL", "message": str(exc),
                                 "stage": stage if "stage" in locals() else "setup"})
        data["timings"]["total_seconds"] = time.perf_counter() - start
        write_json(out / "dataset/pvt_study.json", data)
        write_json(out / "validation/validation.json", {"status": "FAIL", "findings": data["findings"]})
        if isinstance(exc, PVTStudyError):
            raise
        raise PVTStudyError("%s stage failed: %s" % (stage, exc)) from exc
    data["timings"]["total_seconds"] = time.perf_counter() - start
    summary = _summary(data, out)
    write_json(out / "validation/validation.json", {"status": summary["status"], "consistency": data["consistency"],
                                                    "metrics": data.get("metrics", []), "findings": data["findings"]})
    write_json(out / "dataset/pvt_study.json", data)
    write_json(out / "dataset/summary.json", summary)
    _manifest(out, data, summary)
    if strict and summary["status"] == "FAIL":
        raise PVTQualityError("study completed with failed acceptance gates; inspect validation/validation.json")
    return summary


def build_parser():
    """Construct the CLI parser without starting a JVM or performing file writes."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("study", nargs="?", help="study YAML or JSON with explicit sample/analysis basis")
    parser.add_argument("--out", help="output directory")
    parser.add_argument("--strict", action="store_true", help="return 1 on a failed acceptance gate")
    parser.add_argument("--resume", action="store_true", help="reuse only an identical intact completed run")
    parser.add_argument("--stage", choices=STAGES, default="report", help="execute through this stage")
    parser.add_argument("--init", metavar="DIR", help="write a draft study input and stop")
    parser.add_argument("--demo", choices=("oil", "condensate"), help="run an explicitly synthetic example")
    parser.add_argument("--audit-worker", nargs=4, metavar=("NATIVE", "E300", "K", "PRESSURES"), help=argparse.SUPPRESS)
    parser.add_argument("--worker-out", help=argparse.SUPPRESS)
    return parser


def main(argv=None):
    """Execute the CLI; 0 success, 1 failed quality gate, 2 invalid input/setup."""
    args = build_parser().parse_args(argv)
    try:
        if args.audit_worker:
            native, e300, temperature, pressures = args.audit_worker
            _, adapter = _runtime()
            fluid = adapter.readNativeSnapshot(str(Path(native).resolve()))
            audit = json.loads(str(adapter.auditE300(fluid, str(Path(e300).resolve()), float(temperature),
                                                     _array([float(p) for p in pressures.split(",")]))))
            write_json(args.worker_out, audit)
            return 0 if audit.get("passed") else 1
        if args.init:
            destination = Path(args.init)
            destination.mkdir(parents=True, exist_ok=True)
            path = destination / "study.yaml"
            if path.exists():
                raise PVTStudyError("init will not overwrite an existing study.yaml")
            template = {"schema": "pvt_study.v1", "study": {"id": "TO-BE-CONFIRMED", "title": "PVT fluid study",
                         "synthetic": False, "intended_use": "TO-BE-CONFIRMED"},
                        "samples": [], "analyses": [], "fluid": {}, "experiments": [], "observations": [],
                        "standards": [], "data_gaps": ["Complete sample register, analyses, model basis and experiment protocols before execution"]}
            path.write_text(yaml.safe_dump(template, sort_keys=False), encoding="utf-8")
            print("Draft input written: %s; no model, result or report was created" % path)
            return 0
        source = HERE / "pvt_examples" / (args.demo + ".yaml") if args.demo else args.study
        if not source or not args.out:
            raise PVTStudyError("provide study.yaml (or --demo oil|condensate) and --out DIR")
        summary = run_pipeline(load_study(source), args.out, strict=args.strict, resume=args.resume, stop_after=args.stage)
        print(json.dumps(summary, indent=2, allow_nan=False))
        return 0
    except PVTQualityError as exc:
        sys.stderr.write("PVT study quality gate: %s\n" % exc)
        return 1
    except PVTStudyError as exc:
        sys.stderr.write("PVT study: %s\n" % exc)
        return 2


if __name__ == "__main__":
    sys.exit(main())
