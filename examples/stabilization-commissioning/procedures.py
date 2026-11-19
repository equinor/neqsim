"""Execute operating procedures on the closed stabilization reference train."""
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "devtools"))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from neqsim_dev_setup import neqsim_init

ns = neqsim_init(project_root=REPO, verbose=False)
J = ns.JClass

from steady import build

ROOT = REPO / "target" / "stabilization-commissioning"
ROOT.mkdir(parents=True, exist_ok=True)


def _event_time(record, event_type, step_id=None):
    """Return the first matching event time from a procedure record."""
    for event in record["events"]:
        if event["type"] == event_type and (step_id is None or event["stepId"] == step_id):
            return float(event["timeSeconds"])
    return None


def _configure_reference(process, initial_opening=100.0, stiction=0.0):
    """Reset the closed reference train and configure the physical inlet choke."""
    process.reset()
    valve = process.getUnit("CHV-100")
    valve.setPercentValveOpening(initial_opening)
    valve.setTargetPercentValveOpening(initial_opening)
    valve.setTravelModel(J("neqsim.process.equipment.valve.ValveTravelModel").LINEAR_RATE_LIMIT)
    valve.setTravelTime(4.0)
    valve.setValveStiction(stiction)
    valve.setPhysicalStepIdempotencyEnabled(True)
    valve.setCalculateSteadyState(False)
    streams = {"feed": process.getUnit("F-001")}
    sinks = {
        "oil_export": process.getUnit("E-102").getOutletStream(),
        "gas_export": process.getUnit("SPL-203").getSplitStream(0),
    }
    for number in (101, 102, 103):
        sinks["water" + str(number)] = process.getUnit("V-" + str(number)).getWaterOutStream()
    for number in (201, 202, 203, 211, 212, 213):
        sinks["water" + str(number)] = process.getUnit("V-" + str(number)).getWaterOutStream()
    return valve, streams, sinks


def _procedure(valve, procedure_id, target, timeout, operator_owned=False, recovery_target=None):
    """Create one measured-position procedure step for CHV-100."""
    procedure_class = J("neqsim.process.logic.procedure.OperatingProcedure")
    action_class = J("neqsim.process.logic.action.SetValveTargetAction")
    condition_class = J("neqsim.process.logic.condition.ValvePositionCondition")
    procedure = procedure_class(procedure_id, "A", "CHV-100 operating procedure")
    action = action_class(valve, target, 0.5)
    completion = condition_class(valve, "==", target, 0.5)
    recovery = None
    if recovery_target is not None:
        recovery = action_class(valve, recovery_target, 0.5)
    if operator_owned:
        procedure.addOperatorStep("POSITION-CHV-100", None, action, completion, timeout, recovery)
    else:
        procedure.addAutomaticStep("POSITION-CHV-100", None, action, completion, timeout, recovery)
    return procedure


def _run_case(process, case_id, target, initial_opening, time_step, operator_mode="automatic",
              stiction=0.0, timeout=8.0, recovery_target=None):
    """Execute one case with deterministic physical-step identities."""
    valve, streams, sinks = _configure_reference(process, initial_opening, stiction)
    operator_owned = operator_mode != "automatic"
    procedure = _procedure(valve, case_id, target, timeout, operator_owned, recovery_target)
    procedure.activate()
    procedure.execute(0.0)
    if operator_mode == "authorized":
        procedure.authorizeOperatorStep("POSITION-CHV-100")
        procedure.execute(0.0)

    trace = []
    index = 0
    elapsed = 0.0
    identifier = J("neqsim.process.dynamics.TransientStepIdentifier")
    while procedure.isActive() and elapsed < 12.0:
        step_id = identifier.deterministicPhysicalStep(case_id + "-dt-" + str(time_step), index)
        process.runTransient(time_step, step_id)
        elapsed += time_step
        procedure.execute(time_step)
        admitted_flow = float(streams["feed"].getFlowRate("kg/hr"))
        exported_flow = sum(float(stream.getFlowRate("kg/hr")) for stream in sinks.values())
        trace.append({
            "time_s": elapsed,
            "chv_100_target_pct": float(valve.getTargetPercentValveOpening()),
            "chv_100_actual_pct": float(valve.getPercentValveOpening()),
            "admitted_feed_kg_h": admitted_flow,
            "oil_export_kg_h": float(sinks["oil_export"].getFlowRate("kg/hr")),
            "gas_export_kg_h": float(sinks["gas_export"].getFlowRate("kg/hr")),
            "external_mass_residual_kg_h": admitted_flow - exported_flow,
        })
        index += 1

    if procedure.isActive():
        procedure.abort("Procedure exceeded 12 s execution horizon")
    record = json.loads(str(procedure.snapshot().toJson()))
    residuals = [sample["external_mass_residual_kg_h"] for sample in trace]
    position_errors = [abs(initial_opening - target)]
    position_errors.extend(abs(sample["chv_100_actual_pct"] - target) for sample in trace)
    position_iae = sum(
        (position_errors[index - 1] + position_errors[index]) * time_step / 2.0
        for index in range(1, len(position_errors))
    )
    unclosed_mass = sum(residuals) * time_step / 3600.0
    record.update({
        "caseId": case_id,
        "timeStepSeconds": time_step,
        "initialOpeningPercent": initial_opening,
        "targetOpeningPercent": target,
        "finalOpeningPercent": float(valve.getPercentValveOpening()),
        "finalTargetPercent": float(valve.getTargetPercentValveOpening()),
        "completionTimeSeconds": _event_time(record, "STEP_COMPLETED", "POSITION-CHV-100"),
        "commandTimeSeconds": _event_time(record, "COMMAND_ISSUED", "POSITION-CHV-100"),
        "positionIaePercentagePointSeconds": position_iae,
        "positionOvershootPercentagePoints": max(
            [0.0]
            + [
                max(0.0, target - sample["chv_100_actual_pct"])
                if target < initial_opening
                else max(0.0, sample["chv_100_actual_pct"] - target)
                for sample in trace
            ]
        ),
        "maxAbsExternalMassResidualKgPerHour": max([0.0] + [abs(value) for value in residuals]),
        "maxExternalMassResidualPercentOfDesignFeed": (
            max([0.0] + [abs(value) for value in residuals]) / 100000.0 * 100.0
        ),
        "integratedUnclosedDynamicMassKg": unclosed_mass,
        "trace": trace,
    })
    return record


def execute():
    """Run successful, faulted, operator and refinement cases."""
    process, units, streams, sinks, work = build()
    valve = units["CHV-100"]
    valve.setTravelModel(J("neqsim.process.equipment.valve.ValveTravelModel").LINEAR_RATE_LIMIT)
    valve.setTravelTime(4.0)
    valve.setPhysicalStepIdempotencyEnabled(True)
    valve.setCalculateSteadyState(False)
    process.storeInitialState()
    cases = {
        "turndown_dt_0_50": _run_case(process, "OPS-TURNDOWN-001", 55.0, 100.0, 0.5),
        "turndown_dt_0_25": _run_case(process, "OPS-TURNDOWN-001", 55.0, 100.0, 0.25),
        "controlled_shutdown": _run_case(process, "OPS-SHUTDOWN-001", 0.0, 100.0, 0.5),
        "authorized_restart": _run_case(
            process, "OPS-RESTART-001", 100.0, 0.0, 0.5, operator_mode="authorized"
        ),
        "inhibited_restart": _run_case(
            process, "OPS-RESTART-INHIBITED-001", 100.0, 0.0, 0.5,
            operator_mode="inhibited", timeout=2.0, recovery_target=0.0,
        ),
        "stuck_shutdown": _run_case(
            process, "OPS-SHUTDOWN-STUCK-001", 0.0, 100.0, 0.5,
            stiction=150.0, timeout=2.0, recovery_target=100.0,
        ),
        "deterministic_replay": _run_case(process, "OPS-SHUTDOWN-001", 0.0, 100.0, 0.5),
    }
    cases["deterministicReplayMatches"] = (
        cases["controlled_shutdown"] == cases["deterministic_replay"]
    )
    completion_delta = abs(
        cases["turndown_dt_0_50"]["completionTimeSeconds"]
        - cases["turndown_dt_0_25"]["completionTimeSeconds"]
    )
    cases["turndownCompletionSensitivitySeconds"] = completion_delta
    cases["turndownPositionIaeSensitivityPercentagePointSeconds"] = abs(
        cases["turndown_dt_0_50"]["positionIaePercentagePointSeconds"]
        - cases["turndown_dt_0_25"]["positionIaePercentagePointSeconds"]
    )
    cases["modelRevision"] = "stabilization-commissioning/DYN-C047-A"
    cases["designFeedKgPerHour"] = 100000.0
    cases["limitations"] = [
        "Most downstream equipment remains algebraic during transient scans.",
        "The external residual is a closure diagnostic, not physical vessel inventory.",
        "Synthetic procedure evidence is not site acceptance or operating approval.",
    ]
    return cases


def validate(result):
    """Fail execution when the declared procedure acceptance checks are not met."""
    healthy = (
        "turndown_dt_0_50",
        "turndown_dt_0_25",
        "controlled_shutdown",
        "authorized_restart",
    )
    assert all(result[name]["state"] == "COMPLETED" for name in healthy)
    assert result["controlled_shutdown"]["completionTimeSeconds"] == 4.0
    assert result["authorized_restart"]["completionTimeSeconds"] == 4.0
    assert result["turndownCompletionSensitivitySeconds"] <= 0.25
    assert result["turndownPositionIaeSensitivityPercentagePointSeconds"] < 1.0
    assert result["deterministicReplayMatches"]
    assert result["inhibited_restart"]["failureReason"] == (
        "Timeout waiting for operator authorization"
    )
    assert result["inhibited_restart"]["finalOpeningPercent"] == 0.0
    assert result["stuck_shutdown"]["failureReason"] == "Step completion timeout"
    assert result["stuck_shutdown"]["finalOpeningPercent"] == 100.0
    for case in result.values():
        if isinstance(case, dict):
            assert case["positionOvershootPercentagePoints"] == 0.0
            assert case["maxExternalMassResidualPercentOfDesignFeed"] < 3.0
            assert abs(case["integratedUnclosedDynamicMassKg"]) < 0.7


if __name__ == "__main__":
    result = execute()
    validate(result)
    output = ROOT / "procedures.json"
    output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
