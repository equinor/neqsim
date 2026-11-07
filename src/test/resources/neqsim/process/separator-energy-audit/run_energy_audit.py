"""Recompute the public V101 control/shutdown energy characterization.

Ordered native stepping makes the consumed pre-vessel boundary flux observable.
The shutdown uses native held sensing and ClosedLoopSafetyFunction logic with
the existing event/end-of-step scan order. It is not a new process model.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import platform
import subprocess

import jpype
import v101_model as model

J = model.J
LIMIT = 1.0e-5
VALVES = ["20-ESDV-001", "20-PCV-001", "20-LCV-001"]
LEDGER = {
    "accepted_steps": "getAcceptedStepCount",
    "elapsed_s": "getElapsedTimeSeconds",
    "initial_U_J": "getInitialEnergyJ",
    "target_U_J": "getTargetEnergyJ",
    "accepted_VU_U_J": "getAcceptedVuEnergyJ",
    "published_U_J": "getPublishedEnergyJ",
    "local_VU_defect_J": "getLocalVuDefectJ",
    "relative_local_VU_defect": "getRelativeLocalVuDefect",
    "relative_volume_defect": "getRelativeVolumeResidual",
    "net_integrated_J": "getNetIntegratedEnergyJ",
    "cumulative_debt_J": "getCumulativeDebtJ",
    "max_abs_cumulative_debt_J": "getMaximumAbsoluteCumulativeDebtJ",
    "sum_signed_local_VU_defect_J": "getCumulativeSignedLocalVuDefectJ",
    "sum_abs_local_VU_defect_J": "getCumulativeAbsoluteLocalVuDefectJ",
    "non_VU_energy_difference_J": "getNonVuEnergyDifferenceJ",
    "normalization_scale_J": "getNormalizationScaleJ",
    "max_relative_cumulative_residual": "getMaximumRelativeCumulativeResidual",
}


def fingerprint(value):
    """Hash deterministic JSON; nonfinite numbers are never serialized."""
    raw = json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False)
    return hashlib.sha256(raw.encode()).hexdigest()


def write_json(path, value):
    """Write an actual recomputation result."""
    path.write_text(json.dumps(value, indent=2, allow_nan=False) + "\n")


def native_ledger(separator):
    """Read only native diagnostics, separately from the external oracle."""
    report = separator.getLastTransientEnergyBalanceReport()
    row = {"status": str(separator.getTransientEnergyBalanceTrackingStatus())}
    if report is None:
        return row
    for key, getter in LEDGER.items():
        value = float(getattr(report, getter)())
        row[key] = value if math.isfinite(value) else None
    row.update(complete=bool(report.isComplete()), issue=str(report.getCoverageIssue()),
               verdict=str(separator.assessTransientEnergyBalance(LIMIT)))
    return row


def state(process):
    """Return native extensive vessel state, without a ledger-derived value."""
    fluid = process.getUnit("20-V-101").getThermoSystem()
    return {"mass": float(fluid.getMass("kg")), "U": float(fluid.getInternalEnergy()),
            "n": {name: float(fluid.getComponent(name).getNumberOfmoles())
                  for name in model.COMPOSITION}}


def flux(stream):
    """Return boundary mass, enthalpy and component rates before vessel stepping."""
    fluid = stream.getFluid()
    return {"mass": float(fluid.getFlowRate("kg/sec")),
            "H": model.boundary_enthalpy_W(fluid),
            "n": {name: float(fluid.getComponent(name).getNumberOfmoles())
                  for name in model.COMPOSITION}}


class Audit:
    """Independent current-inlet/previous-outlet explicit boundary integration."""

    def __init__(self, process):
        self.initial = state(process)
        self.mass = 0.0
        self.energy = 0.0
        self.components = {name: 0.0 for name in model.COMPOSITION}

    def consume(self, process, dt):
        """Sample exactly where Separator consumes the native boundary equation."""
        separator = process.getUnit("20-V-101")
        incoming = flux(process.getUnit("20-ESDV-001").getOutletStream())
        outgoing = [flux(separator.getGasOutStream()), flux(separator.getLiquidOutStream())]
        self.mass += (incoming["mass"] - sum(item["mass"] for item in outgoing)) * dt
        self.energy += (incoming["H"] - sum(item["H"] for item in outgoing)) * dt
        for name in model.COMPOSITION:
            rate = incoming["n"][name] - sum(item["n"][name] for item in outgoing)
            self.components[name] += rate * dt

    def observe(self, process):
        """Calculate external residuals from published inventory."""
        current = state(process)
        return {
            "mass_balance_error_kg": current["mass"] - self.initial["mass"] - self.mass,
            "energy_balance_error_J": current["U"] - self.initial["U"] - self.energy,
            "max_component_error_mol": max(
                abs(current["n"][name] - self.initial["n"][name] - self.components[name])
                for name in model.COMPOSITION),
        }


def sample(process, audit):
    """Record physical values before attaching optional diagnostic columns."""
    separator = process.getUnit("20-V-101")
    current = state(process)
    row = {
        "time_s": float(process.getTime()), "pressure_bara": float(separator.getPressure()),
        "level_fraction": float(separator.getLiquidLevel()),
        "temperature_C": float(separator.getThermoSystem().getTemperature("C")),
        "mass_kg": current["mass"], "internal_energy_J": current["U"],
    }
    row.update({"inventory_moles_" + name: value for name, value in current["n"].items()})
    for tag, prefix in [("20-ESDV-001", "inlet"), ("20-PCV-001", "pcv"), ("20-LCV-001", "lcv")]:
        valve = process.getUnit(tag)
        row[prefix + "_opening_pct"] = float(valve.getPercentValveOpening())
        row[prefix + "_flow_kg_s"] = float(valve.getOutletStream().getFlowRate("kg/sec"))
    row["valve_port_mismatch_kg_s"] = max(
        abs(float(process.getUnit(tag).getInletStream().getFlowRate("kg/sec"))
            - float(process.getUnit(tag).getOutletStream().getFlowRate("kg/sec")))
        for tag in VALVES)
    row.update(audit.observe(process))
    return row


def pressure_sif(process):
    """Build the original native pressure channel, delays and inlet trip action."""
    sensor = J("neqsim.process.measurementdevice.PressureTransmitter")(
        "20-PT-002", process.getUnit("20-V-101").getGasOutStream())
    sensor.setUnit("bara")
    sensor.setMinimumValue(0.0)
    sensor.setMaximumValue(60.0)
    sensor.setTransientSamplingEnabled(True)
    process.add(sensor)
    final_element = J("neqsim.process.logic.esd.ESDLogic")("V101 inlet final element")
    final_element.addAction(J("neqsim.process.logic.action.TripValveAction")(
        process.getUnit("20-ESDV-001")), 0.0)
    extractor = jpype.JProxy("neqsim.process.safety.sif.SafetyFunctionChannel$SignalExtractor",
        dict={"extract": lambda working: float(
            working.getMeasurementDevice("20-PT-002").getTransientSample("bara"))})
    channel = J("neqsim.process.safety.sif.SafetyFunctionChannel").highTrip(
        "20-PT-002", "bara", 41.0, extractor).responseDelaySeconds(0.1).build()
    logic = J("neqsim.process.safety.sif.ClosedLoopSafetyFunction").builder(
        "SIF-V101-PSHH", "20-PSHH-001", process,
        J("neqsim.process.logic.voting.VotingPattern").ONE_OUT_OF_ONE, final_element
    ).addChannel(channel).logicSolverDelaySeconds(0.1).build()
    return logic, extractor


def run_case(case, duration, dt, tracking, conservative):
    """Replicate the native explicit scan while observing pre-vessel boundaries."""
    built = model.build_model()
    process = built["process"]
    units = built["units"]
    separator = units["20-V-101"]
    logic, callback = pressure_sif(process) if case == "shutdown" else (None, None)
    issues = [str(value) for value in J("neqsim.process.dynamics.DynamicCapabilityReport")
              .from_(process).getStrictPreflightIssues()]
    if issues:
        raise RuntimeError("Strict configured capability preflight: " + "; ".join(issues))
    separator.setConservativeTransientEnergyIntegrationEnabled(conservative)
    separator.setTransientEnergyBalanceTrackingEnabled(tracking)
    audit = Audit(process)
    physical_rows = []
    rows = []
    active = case == "control"
    triggered = False
    index = 0
    time = 0.0
    event_origin = 0.0
    while time < duration - 1.0e-12:
        if case == "shutdown" and not triggered and time >= 1.0 - 1.0e-12:
            units["20-PCV-001"].getController().setActive(False)
            units["20-PCV-001"].setPercentValveOpening(0.0)
            logic.activate()
            separator.resetTransientEnergyBalanceTracking()
            audit = Audit(process)
            event_origin = float(process.getTime())
            active = triggered = True
        if active and not rows:
            physical = sample(process, audit)
            physical_rows.append(dict(physical))
            rows.append(dict(physical))
        step = min(dt, duration - time)
        if case == "shutdown" and not triggered:
            step = min(step, 1.0 - time)
        step_id = J("neqsim.process.dynamics.TransientStepIdentifier").deterministicPhysicalStep(
            "V101-ENERGY-CHARACTERIZATION", index)
        if case == "control":
            units["20-ESDV-001"].setPercentValveOpening(
                50.0 + 10.0 * min(1.0, max(0.0, (index * dt - 30.0) / 30.0)))
        process.increaseTime(step)
        for sensor in process.getMeasurementDevices():
            sensor.sampleTransient(step, step_id)
        units["20-FEED-001"].runTransient(step, step_id)
        process.getUnit("V101-INLET-PRESSURE-COUPLING").runTransient(step, step_id)
        units["20-ESDV-001"].runTransient(step, step_id)
        if active:
            audit.consume(process, step)
        separator.runTransient(step, step_id)
        units["20-PCV-001"].runTransient(step, step_id)
        units["20-LCV-001"].runTransient(step, step_id)
        for controller in process.getControllerDevices():
            if controller.isActive() and not controller.hasRunTransient(step_id):
                controller.runTransient(controller.getResponse(), step, step_id)
        if triggered:
            logic.execute(step)
        time += step
        index += 1
        if active:
            physical = sample(process, audit)
            physical_rows.append(dict(physical))
            row = dict(physical)
            row.update({"ledger_" + key: value for key, value in native_ledger(separator).items()})
            rows.append(row)
    maximum = max(abs(row["energy_balance_error_J"]) for row in rows)
    relative = maximum / max(abs(audit.initial["U"]), 1.0)
    peak = max(row["pressure_bara"] for row in rows)
    closure = next((row["time_s"] - event_origin for row in rows
                    if row["inlet_opening_pct"] <= 0.1), None)
    if case == "control":
        response = peak <= 40.5 and abs(rows[-1]["pressure_bara"] - 40.0) <= 0.1
        response = response and abs(rows[-1]["level_fraction"] - 0.5) <= 0.01
    else:
        response = closure is not None and closure <= 3.0
        response = response and all(0.0 < row["pressure_bara"] <= 43.0
            and 0.05 <= row["level_fraction"] <= 0.95 for row in rows)
    summary = {
        "case": case, "duration_s": duration, "dt_s": dt, "tracking": tracking,
        "conservativeEnergyIntegration": conservative,
        "parameters": built["parameters"], "composition": model.COMPOSITION,
        "cvs_US": {tag: float(units[tag].getCv("US")) for tag in VALVES},
        "auditWindowStart_s": event_origin, "initial_internal_energy_J": audit.initial["U"],
        "max_energy_error_J": maximum, "max_relative_external_energy_residual": relative,
        "frozenEnergyLimit": LIMIT, "energyVerdict": "PASS" if relative <= LIMIT else "FAIL",
        "responseVerdict": "PASS" if response else "FAIL", "maximum_pressure_bara": peak,
        "closureAfterEvent_s": closure, "final": physical_rows[-1],
        "physicalTrajectorySha256": fingerprint(physical_rows),
        "physicalStepCount": index, "strictPreflightIssues": issues,
        "max_mass_error_kg": max(abs(row["mass_balance_error_kg"]) for row in rows),
        "max_component_error_mol": max(row["max_component_error_mol"] for row in rows),
    }
    if tracking:
        summary["ledger"] = native_ledger(separator)
        summary["manualLedgerMaximumDifferenceJ"] = max(
            abs(row["ledger_cumulative_debt_J"] - row["energy_balance_error_J"])
            for row in rows if "ledger_cumulative_debt_J" in row)
    if logic is not None:
        summary["nativeSafetyFunctionEvidence"] = json.loads(str(
            J("com.google.gson.Gson")().toJson(logic.getDynamicSafetyScenarioEvidence())))
    return summary, rows


def provenance():
    """Record source hashes and hashes of the actual loaded JVM class bytes."""
    result = {"python": platform.python_version(), "jpype": jpype.__version__,
              "java": str(J("java.lang.System").getProperty("java.version")),
              "fixtureSources": {}, "runtimeClasses": {}}
    for path in Path(__file__).parent.glob("*.py"):
        result["fixtureSources"][path.name] = hashlib.sha256(path.read_bytes()).hexdigest()
    for name in ["Separator", "SeparatorTransientEnergyBalanceReport", "SeparatorTransientEnergyBalanceTracker"]:
        full = "neqsim.process.equipment.separator." + name
        cls = J(full).class_
        stream = cls.getResourceAsStream("/" + full.replace(".", "/") + ".class")
        digest = J("java.security.MessageDigest").getInstance("SHA-256")
        buffer = jpype.JArray(jpype.JByte)(8192)
        while True:
            count = stream.read(buffer)
            if count < 0:
                break
            digest.update(buffer, 0, count)
        stream.close()
        result["runtimeClasses"][full] = {
            "sha256": bytes(int(value) & 255 for value in digest.digest()).hex(),
            "codeSource": str(cls.getProtectionDomain().getCodeSource().getLocation()),
        }
        source = model.ROOT / "src/main/java" / (full.replace(".", "/") + ".java")
        if source.is_file():
            result["runtimeClasses"][full]["sourceSha256"] = hashlib.sha256(source.read_bytes()).hexdigest()
    result["baselineReference"] = "3d20d6086654ed71347627f59b164b2aca46e1b7"
    result["sourceControlState"] = "MATERIALIZED_SOURCE_WITH_FILE_HASHES"
    try:
        top = subprocess.check_output(
            ["git", "rev-parse", "--show-toplevel"], cwd=model.ROOT,
            text=True, stderr=subprocess.DEVNULL).strip()
        if Path(top).resolve() == model.ROOT.resolve():
            result["checkoutCommit"] = subprocess.check_output(
                ["git", "rev-parse", "HEAD"], cwd=model.ROOT, text=True).strip()
            result["sourceControlState"] = "VERIFIED_REPOSITORY_ROOT"
            result["workingTreeDirty"] = bool(subprocess.check_output(
                ["git", "status", "--porcelain"], cwd=model.ROOT, text=True).strip())
    except (OSError, subprocess.CalledProcessError):
        pass
    return result


def main():
    """Run off/on observations; an expected energy FAIL is retained as FAIL."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case", choices=["control", "shutdown"], required=True)
    parser.add_argument("--duration", type=float, required=True)
    parser.add_argument("--dt", type=float, required=True)
    parser.add_argument("--tracking", choices=["both", "off", "on"], default="both")
    parser.add_argument("--conservative", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if (not math.isfinite(args.dt) or not math.isfinite(args.duration)
            or args.dt <= 0.0 or args.duration <= 0.0):
        parser.error("Positive finite duration and dt are required")
    if args.case == "shutdown" and args.duration <= 1.0:
        parser.error("Shutdown duration must extend beyond the1s initiating event")
    args.output.mkdir(parents=True, exist_ok=True)
    summaries = []
    for tracking in ([False, True] if args.tracking == "both" else [args.tracking == "on"]):
        summary, rows = run_case(args.case, args.duration, args.dt, tracking, args.conservative)
        stem = "on" if tracking else "off"
        write_json(args.output / (stem + ".json"), summary)
        with (args.output / (stem + ".csv")).open("w", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=sorted(set().union(*(row.keys() for row in rows))))
            writer.writeheader()
            writer.writerows(rows)
        summaries.append(summary)
    pair = {"case": args.case, "duration_s": args.duration, "dt_s": args.dt,
            "conservativeEnergyIntegration": args.conservative,
            "provenance": provenance(), "energyLimit": LIMIT,
            "responseVerdict": summaries[-1]["responseVerdict"],
            "energyVerdict": summaries[-1]["energyVerdict"],
            "qualification": "Separate response/energy criteria; no design or safety approval"}
    if len(summaries) == 2:
        equal = summaries[0]["physicalTrajectorySha256"] == summaries[1]["physicalTrajectorySha256"]
        pair["exactOffOnPhysicalEquality"] = equal
        if not equal:
            raise AssertionError("Energy tracking changed the physical trajectory")
    if summaries[-1]["tracking"]:
        difference = summaries[-1]["manualLedgerMaximumDifferenceJ"]
        pair["manualLedgerMaximumDifferenceJ"] = difference
        if difference > 1.0e-5:
            raise AssertionError("Native ledger and independent consumed-boundary audit differ")
    write_json(args.output / "pair.json", pair)
    print(json.dumps({key: value for key, value in pair.items() if key != "provenance"}), flush=True)


if __name__ == "__main__":
    main()

