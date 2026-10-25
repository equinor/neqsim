"""Tests for the production-optimisation stages (gates, constraints, guard, outcome)."""
import copy
import json
import math
import os
import pathlib
import subprocess
import sys
from types import SimpleNamespace

import pytest
import yaml

import neqsim_continuous as nc
from neqsim_continuous import production
from neqsim_continuous.cycle import run_cycle
from neqsim_continuous.ledger import Ledger
from neqsim_continuous.living import make_living, promote

RVP = {"name": "rvp", "kpi": "rvp_bara", "op": "<=", "limit": 0.70, "margin": 0.05, "warn": 0.03, "hard": True}


def test_slack_uses_margin_and_direction():
    assert production.slack(RVP, 0.60) == pytest.approx(0.05)
    assert production.slack(RVP, 0.66) < 0
    floor = {"kpi": "x", "op": ">=", "limit": 10.0, "margin": 1.0}
    assert production.slack(floor, 12.0) == pytest.approx(1.0)
    assert production.slack({"kpi": "x", "limit": None}, 1.0) is None


def test_evaluate_constraints_statuses():
    rows = {r["name"]: r for r in production.evaluate_constraints(
        [RVP, dict(RVP, name="a", limit=None), dict(RVP, name="b", kpi="missing")], {"rvp_bara": 0.63})}
    assert rows["rvp"]["status"] == "low_margin"
    assert rows["a"]["status"] == "unconfirmed" and rows["b"]["status"] == "no_data"
    assert production.evaluate_constraints([RVP], {"rvp_bara": 0.7})[0]["status"] == "breach"


def test_demonstrated_limit_is_larger_of_design_and_quantile():
    values = list(range(1, 101))
    result = nc.demonstrated_limit(values, quantile=0.99, design=95.0)
    assert result["demonstrated"] == pytest.approx(99.01) and result["limit"] == pytest.approx(99.01)
    assert result["basis"] == "demonstrated"
    assert nc.demonstrated_limit(values, quantile=0.5, design=95.0)["limit"] == 95.0
    few = nc.demonstrated_limit([1.0, 2.0], design=5.0)
    assert few["limit"] == 5.0 and "too few" in few["basis"]


STAGE_UPDATE = '''
def run(ctx):
    return {"kpis": {"rvp_bara": 0.60, "oil_Sm3d": 13000.0, "resid_K": ctx.plan["_resid"]}}
'''

STAGE_OPTIMIZE = '''
def run(ctx):
    import math
    import os
    import yaml

    config_path = os.path.join(ctx.task_dir, "study_config.yaml")
    with open(config_path, encoding="utf-8") as config_file:
        config = yaml.safe_load(config_file) or {}
    production_route = float(config.get("production_route_configuration", 0.0))
    injection_route = float(config.get("injection_route_configuration", 0.0))
    reservoir_pressure_bara = float(config.get("reservoir_pressure_bara", 150.0))
    mapped_network_power_kw = float(config.get("mapped_network_power_kw", 120.0))
    common_shaft_power_kw = float(config.get("common_shaft_power_kw", 900.0))
    common_shaft_speed_rpm = float(config.get("common_shaft_speed_rpm", 9000.0))
    driver_power_limit_kw = float(config.get("driver_power_limit_kw", 1100.0))
    shared_plant_power_limit_kw = float(config.get("shared_plant_power_limit_kw", 1200.0))
    shared_plant_power_kw = mapped_network_power_kw + common_shaft_power_kw
    host_power_values = [mapped_network_power_kw, common_shaft_power_kw,
                         common_shaft_speed_rpm, driver_power_limit_kw,
                         shared_plant_power_limit_kw, shared_plant_power_kw]
    host_power_finite = all(math.isfinite(value) for value in host_power_values)
    accepted_values = [0.5, production_route, injection_route]
    optimizer_evidence = {
        "cycle_id": ctx.cycle_id,
        "simulation_converged": True,
        "candidate_feasible": (host_power_finite
                               and production_route in (0.0, 1.0)
                               and injection_route in (0.0, 1.0, 2.0)
                               and reservoir_pressure_bara > 0.0
                               and common_shaft_speed_rpm > 0.0
                               and common_shaft_power_kw <= driver_power_limit_kw
                               and shared_plant_power_kw <= shared_plant_power_limit_kw),
        "candidate_finite": host_power_finite,
        "constraint_evidence_complete": True,
        "state_restore_complete": True,
        "accepted_point_replayed": True,
        "actions_complete": True,
        "evaluation_count": 12,
        "runtime_seconds": 1.25,
        "operating_definition": {
            "production_route_configuration": production_route,
            "injection_route_configuration": injection_route,
            "reservoir_pressure_bara": reservoir_pressure_bara,
            "mapped_network_power_kw": mapped_network_power_kw,
            "common_shaft_power_kw": common_shaft_power_kw,
            "common_shaft_speed_rpm": common_shaft_speed_rpm,
            "driver_power_limit_kw": driver_power_limit_kw,
            "shared_plant_power_kw": shared_plant_power_kw,
            "shared_plant_power_limit_kw": shared_plant_power_limit_kw,
        },
    }
    point = {
        "candidate_values": accepted_values,
        "baseline_restored": True,
        "baseline_simulation_converged": True,
    }
    envelope_evidence = {
        "schema_version": 1,
        "cycle_id": ctx.cycle_id,
        "study_id": "field-envelope",
        "candidate_evaluator_id": "field-actions",
        "optimizer_evidence": optimizer_evidence,
        "accepted_action_ids": ["p3_barg", "configuration.field.selection",
                                "configuration.injector route.selection"],
        "accepted_candidate_values": accepted_values,
        "slice_count": 3,
        "requested_point_count": 3,
        "completed_point_count": 3,
        "envelope_evaluation_count": 3,
        "envelope_runtime_seconds": 0.3,
        "total_evaluation_count": 15,
        "slices": [
            {
                "study_id": "field-envelope",
                "candidate_evaluator_id": "field-actions",
                "axis_action_ids": ["p3_barg", "configuration.field.selection"],
                "anchor_values": accepted_values,
                "requested_point_count": 1,
                "completed_point_count": 1,
                "evaluation_count": 1,
                "runtime_seconds": 0.1,
                "outcome": "COMPLETE",
                "complete": True,
                "points": [point],
            },
            {
                "study_id": "field-envelope",
                "candidate_evaluator_id": "field-actions",
                "axis_action_ids": ["p3_barg"],
                "anchor_values": accepted_values,
                "requested_point_count": 1,
                "completed_point_count": 1,
                "evaluation_count": 1,
                "runtime_seconds": 0.1,
                "outcome": "COMPLETE",
                "complete": True,
                "points": [point],
            },
            {
                "study_id": "field-envelope",
                "candidate_evaluator_id": "field-actions",
                "axis_action_ids": ["configuration.injector route.selection"],
                "anchor_values": accepted_values,
                "requested_point_count": 1,
                "completed_point_count": 1,
                "evaluation_count": 1,
                "runtime_seconds": 0.1,
                "outcome": "COMPLETE",
                "complete": True,
                "points": [point],
            },
        ],
        "advisory": True,
    }
    lifecycle_evidence = {
        "schema_version": 1,
        "cycle_id": ctx.cycle_id,
        "lifecycle_study_id": "field-lifecycle",
        "lifecycle_study_name": "Field lifecycle envelope",
        "lifecycle_study_provenance": "synthetic current operating definition",
        "time_unit": "cycle",
        "period_count": 1,
        "accepted_action_ids": envelope_evidence["accepted_action_ids"],
        "requested_point_count": envelope_evidence["requested_point_count"],
        "completed_point_count": envelope_evidence["completed_point_count"],
        "total_evaluation_count": envelope_evidence["total_evaluation_count"],
        "envelope_runtime_seconds": envelope_evidence["envelope_runtime_seconds"],
        "periods": [{
            "period_id": ctx.cycle_id,
            "period_name": "Current cycle",
            "elapsed_time": 0.0,
            "time_unit": "cycle",
            "configuration_provenance": "synthetic current operating definition",
            "envelope_study_id": envelope_evidence["study_id"],
            "candidate_evaluator_id": envelope_evidence["candidate_evaluator_id"],
            "accepted_bottleneck": envelope_evidence.get("accepted_point_bottleneck"),
            "envelope_evidence": envelope_evidence,
        }],
        "bottleneck_transitions": [],
        "advisory": True,
    }
    base = {"title": "%s", "category": "operational",
            "setpoints": {"p3_barg": 0.5,
                          "configuration.field.selection": production_route,
                          "configuration.injector route.selection": injection_route},
            "expected_gain": {"value": 300.0, "kpi": "oil_Sm3d"},
            "objective_kpi": "oil_Sm3d", "baseline_value": 12700.0,
            "optimizer_evidence": optimizer_evidence,
            "accepted_point_envelope_evidence": envelope_evidence,
            "lifecycle_envelope_evidence": lifecycle_evidence}
    return {"proposals": [
        dict(base, title="safe", predicted={"rvp_bara": 0.63}),
        dict(base, title="off spec", predicted={"rvp_bara": 0.68}),
        dict(base, title="no prediction"),
        dict(base, title="no gain", expected_gain={"value": 0.0}, predicted={"rvp_bara": 0.6}),
    ]}
'''


def _task(tmp_path, resid=1.0, limit=0.70):
    task = tmp_path / "t"
    (task / "continuous" / "stages").mkdir(parents=True)
    (task / "study_config.yaml").write_text(
        "production_route_configuration: 0.0\n"
        "injection_route_configuration: 0.0\n"
        "reservoir_pressure_bara: 150.0\n"
        "mapped_network_power_kw: 120.0\n"
        "common_shaft_power_kw: 900.0\n"
        "common_shaft_speed_rpm: 9000.0\n"
        "driver_power_limit_kw: 1100.0\n"
        "shared_plant_power_limit_kw: 1200.0\n", encoding="utf-8")
    make_living(str(task), template="production")
    cont = task / "continuous"
    (cont / "stages" / "model_update.py").write_text(STAGE_UPDATE.replace('ctx.plan["_resid"]', str(resid)))
    (cont / "stages" / "optimize.py").write_text(STAGE_OPTIMIZE)
    plan = yaml.safe_load((cont / "cycle_plan.yaml").read_text())
    plan["gates"] = [{"name": "resid", "kpi": "resid_K", "abs_max": 3.0}]
    plan["production"]["require_envelope_evidence"] = True
    plan["production"]["require_lifecycle_envelope_evidence"] = True
    plan["evidence"] = {
        "rules": [{
            "name": "field-operating-definition",
            "matches": ["study_config.yaml", "continuous/goal.yaml"],
            "stages": ["guard"],
            "conclusions": ["validated field operating point and envelope"],
        }],
        "dependencies": {
            "guard": ["script:optimize", "gates", "constraints"],
            "script:optimize": ["script:model_update"],
            "gates": ["script:model_update"],
            "constraints": ["script:model_update"],
        },
    }
    plan["stages"] = [s for s in plan["stages"] if s not in ("sense", "refresh", "agent")]
    (cont / "cycle_plan.yaml").write_text(yaml.safe_dump(plan))
    goal = yaml.safe_load((cont / "goal.yaml").read_text())
    goal["objective"] = {"metric": "oil_Sm3d", "direction": "maximize", "target": None}
    goal["constraints"] = [dict(RVP, limit=limit)]
    (cont / "goal.yaml").write_text(yaml.safe_dump(goal))
    return str(task)


def _guard(task, manifest):
    path = os.path.join(task, "continuous", "cycles", manifest["cycle_id"], "guard.json")
    with open(path) as f:
        return json.load(f)


def _guard_context(tmp_path, proposal):
    return SimpleNamespace(
        cycle_id="C-1",
        cycle_dir=str(tmp_path),
        goal={"constraints": [RVP]},
        guard_blocks=[],
        plan={"production": {"min_gain": 0.0}},
        proposals=[proposal],
        sections=[],
    )


def _valid_proposal():
    proposal = {
        "title": "candidate",
        "setpoints": {"p3_barg": 0.5},
        "expected_gain": {"value": 300.0, "kpi": "oil_Sm3d"},
        "predicted": {"rvp_bara": 0.63},
        "objective_kpi": "oil_Sm3d",
        "baseline_value": 12700.0,
        "optimizer_evidence": {
            "cycle_id": "C-1",
            "simulation_converged": True,
            "candidate_feasible": True,
            "candidate_finite": True,
            "constraint_evidence_complete": True,
            "state_restore_complete": True,
            "accepted_point_replayed": True,
            "actions_complete": True,
            "evaluation_count": 12,
            "runtime_seconds": 1.25,
        },
    }
    proposal["accepted_point_envelope_evidence"] = _valid_envelope_evidence(
        proposal["optimizer_evidence"])
    return proposal


def _valid_envelope_evidence(optimizer_evidence):
    point = {
        "axis_values": [0.5],
        "candidate_values": [0.5],
        "feasible": True,
        "leading_constraint": None,
        "baseline_restored": True,
        "baseline_simulation_converged": True,
    }
    return {
        "schema_version": 1,
        "cycle_id": "C-1",
        "study_id": "field-envelope",
        "candidate_evaluator_id": "field-actions",
        "optimizer_evidence": optimizer_evidence,
        "accepted_action_ids": ["p3_barg"],
        "accepted_candidate_values": [0.5],
        "slice_count": 1,
        "requested_point_count": 1,
        "completed_point_count": 1,
        "envelope_evaluation_count": 1,
        "envelope_runtime_seconds": 0.1,
        "total_evaluation_count": 13,
        "slices": [{
            "study_id": "field-envelope",
            "candidate_evaluator_id": "field-actions",
            "axis_action_ids": ["p3_barg"],
            "axis_values": [[0.5]],
            "anchor_values": [0.5],
            "requested_point_count": 1,
            "completed_point_count": 1,
            "evaluation_count": 1,
            "runtime_seconds": 0.1,
            "outcome": "COMPLETE",
            "complete": True,
            "feasible_point_count": 1,
            "points": [point],
            "bottleneck_transitions": [],
        }],
        "advisory": True,
    }


def _valid_lifecycle_evidence(current_envelope):
    current_period_envelope = copy.deepcopy(current_envelope)
    future_optimizer = dict(current_envelope["optimizer_evidence"], cycle_id="future-period")
    future_envelope = copy.deepcopy(current_envelope)
    future_envelope["cycle_id"] = "future-period"
    future_envelope["optimizer_evidence"] = future_optimizer
    future_envelope["accepted_candidate_values"] = [0.4]
    future_envelope["slices"][0]["anchor_values"] = [0.4]
    future_envelope["slices"][0]["points"][0]["candidate_values"] = [0.4]
    periods = [
        {
            "period_id": "C-1",
            "period_name": "Current cycle",
            "elapsed_time": 0.0,
            "time_unit": "year",
            "configuration_provenance": "current reservoir and host limits",
            "envelope_study_id": current_period_envelope["study_id"],
            "candidate_evaluator_id": current_period_envelope["candidate_evaluator_id"],
            "accepted_bottleneck": current_period_envelope.get("accepted_point_bottleneck"),
            "envelope_evidence": current_period_envelope,
        },
        {
            "period_id": "future-period",
            "period_name": "Future period",
            "elapsed_time": 5.0,
            "time_unit": "year",
            "configuration_provenance": "forecast reservoir and host limits",
            "envelope_study_id": future_envelope["study_id"],
            "candidate_evaluator_id": future_envelope["candidate_evaluator_id"],
            "accepted_bottleneck": future_envelope.get("accepted_point_bottleneck"),
            "envelope_evidence": future_envelope,
        },
    ]
    period_change = {
        "from_period_id": "C-1",
        "to_period_id": "future-period",
        "from_elapsed_time": 0.0,
        "to_elapsed_time": 5.0,
        "time_unit": "year",
        "accepted_action_changes": [{
            "action_id": current_envelope["accepted_action_ids"][0],
            "from_value": 0.5,
            "to_value": 0.4,
            "delta": 0.4 - 0.5,
        }],
        "comparable_slice_count": 1,
        "removed_slice_axes": [],
        "added_slice_axes": [],
        "slice_changes": [{
            "axis_action_ids": current_envelope["slices"][0]["axis_action_ids"],
            "grid_identical": True,
            "from_requested_point_count": 1,
            "to_requested_point_count": 1,
            "from_feasible_point_count": 1,
            "to_feasible_point_count": 1,
            "feasible_point_count_delta": 0,
            "comparable_point_count": 1,
            "from_only_point_count": 0,
            "to_only_point_count": 0,
            "became_feasible_count": 0,
            "became_infeasible_count": 0,
            "leading_constraint_change_count": 0,
            "from_bottleneck_transition_count": 0,
            "to_bottleneck_transition_count": 0,
        }],
        "advisory": True,
    }
    return {
        "schema_version": 2,
        "cycle_id": "C-1",
        "lifecycle_study_id": "field-lifecycle",
        "lifecycle_study_name": "Field lifecycle envelope",
        "lifecycle_study_provenance": "approved current and forecast cases",
        "time_unit": "year",
        "period_count": 2,
        "accepted_action_ids": current_envelope["accepted_action_ids"],
        "requested_point_count": 2,
        "completed_point_count": 2,
        "total_evaluation_count": 26,
        "envelope_runtime_seconds": 0.2,
        "periods": periods,
        "bottleneck_transitions": [],
        "period_change_diagnostics": [period_change],
        "advisory": True,
    }


def _add_empty_operating_region_evidence(envelope):
    """Add valid one-point sampled operating-region evidence to a test envelope."""
    for envelope_slice in envelope["slices"]:
        envelope_slice["axis_action_semantics"] = [
            "CONTINUOUS" for _ in envelope_slice["axis_action_ids"]
        ]
        envelope_slice["operating_region_transitions"] = []
        for sequence, point in enumerate(envelope_slice["points"]):
            point["grid_indexes"] = [0 for _ in envelope_slice["axis_action_ids"]]
            point["sequence_index"] = sequence
            point["outcome"] = "FEASIBLE"


def _valid_region_lifecycle_evidence(current_envelope):
    """Extend valid lifecycle evidence with exact sampled operating-region migration fields."""
    evidence = _valid_lifecycle_evidence(current_envelope)
    evidence["schema_version"] = 4
    for period in evidence["periods"]:
        _add_empty_operating_region_evidence(period["envelope_evidence"])
    for change in evidence["period_change_diagnostics"]:
        for slice_change in change["slice_changes"]:
            slice_change.update({
                "from_operating_region_transition_count": 0,
                "to_operating_region_transition_count": 0,
                "retained_operating_region_transition_count": 0,
                "removed_operating_region_transitions": [],
                "added_operating_region_transitions": [],
            })
    return evidence


def _add_empty_boundary_refinement_evidence(envelope):
    """Add a disabled but complete boundary-refinement contract to a test envelope."""
    for envelope_slice in envelope["slices"]:
        envelope_slice["maximum_boundary_refinement_evaluations"] = 0
        envelope_slice["boundary_refinement_relative_tolerance"] = 1.0e-3
        envelope_slice["boundary_refinement_tolerance_provenance"] = (
            "diagnostic continuous-action range fraction")
        envelope_slice["operating_region_boundary_refinements"] = []


def _add_empty_constraint_summary_evidence(envelope):
    """Upgrade one constraint-free fixture to exact accepted-envelope schema v2."""
    envelope["schema_version"] = 2
    envelope["cross_slice_constraint_summary"] = {
        "summary_version": 1,
        "maximum_normalized_margin": 0.1,
        "threshold_provenance": "test normalized remaining-margin screen",
        "observed_constraint_count": 0,
        "ranked_constraint_count": 0,
        "excluded_constraint_count": 0,
        "ranked_constraints": [],
        "advisory": True,
        "interpretation": (
            "Exact sampled/refined evidence; not a shadow price, causal attribution, "
            "global active set, or approval"),
    }


def _valid_boundary_lifecycle_evidence(current_envelope):
    """Extend valid schema-v4 evidence with fail-closed continuous-boundary receipts."""
    evidence = _valid_region_lifecycle_evidence(current_envelope)
    evidence["schema_version"] = 5
    for period in evidence["periods"]:
        _add_empty_boundary_refinement_evidence(period["envelope_evidence"])
    for change in evidence["period_change_diagnostics"]:
        for slice_change in change["slice_changes"]:
            slice_change.update({
                "from_operating_region_boundary_refinement_count": 0,
                "to_operating_region_boundary_refinement_count": 0,
                "retained_operating_region_boundary_refinement_count": 0,
                "removed_operating_region_boundary_refinements": [],
                "added_operating_region_boundary_refinements": [],
            })
    return evidence


def _valid_boundary_headroom_lifecycle_evidence(current_envelope):
    """Extend valid schema-v5 evidence with exact refined-boundary endpoint constraints."""
    evidence = _valid_boundary_lifecycle_evidence(current_envelope)
    evidence["schema_version"] = 6
    return evidence


def _valid_constraint_summary_lifecycle_evidence(current_envelope):
    """Extend valid schema-v6 evidence with exact reconstructed cross-slice summaries."""
    evidence = _valid_boundary_headroom_lifecycle_evidence(current_envelope)
    evidence["schema_version"] = 7
    return evidence


def _valid_constraint_summary_migration_lifecycle_evidence(current_envelope):
    """Extend schema-v7 evidence with exact adjacent-period summary identity changes."""
    evidence = _valid_constraint_summary_lifecycle_evidence(current_envelope)
    evidence["schema_version"] = 8
    for change, before, after in zip(
            evidence["period_change_diagnostics"], evidence["periods"], evidence["periods"][1:]):
        from_identities = production._constraint_summary_identities(before["envelope_evidence"])
        to_identities = production._constraint_summary_identities(after["envelope_evidence"])
        removed = production._exact_list_difference(from_identities, to_identities)
        added = production._exact_list_difference(to_identities, from_identities)
        change.update({
            "from_ranked_constraint_identity_count": len(from_identities),
            "to_ranked_constraint_identity_count": len(to_identities),
            "retained_ranked_constraint_identity_count": len(from_identities) - len(removed),
            "removed_ranked_constraint_identities": removed,
            "added_ranked_constraint_identities": added,
        })
    return evidence


def _valid_state_lifecycle_evidence(current_envelope):
    """Extend valid schema-v4 evidence with exact accepted physical-state metrics."""
    evidence = _valid_region_lifecycle_evidence(current_envelope)
    evidence["accepted_state_metric_ids"] = [
        "reservoir-pressure", "production-rate", "injection-rate", "shared-power"
    ]
    values = [
        [150.0, 0.0178, 0.0142, 3.02],
        [148.25, 0.0173, 0.0147, 2.99],
    ]
    units = ["bara", "kg/s", "kg/s", "kW"]
    names = [
        "Shared reservoir pressure", "Accepted production rate",
        "Accepted injection rate", "Accepted shared power",
    ]
    for period, period_values in zip(evidence["periods"], values):
        period["accepted_state_metrics"] = [{
            "metric_id": metric_id,
            "metric_name": name,
            "value": value,
            "unit": unit,
            "provenance": "canonical accepted shared-reservoir step",
        } for metric_id, name, value, unit in zip(
            evidence["accepted_state_metric_ids"], names, period_values, units)]
    evidence["period_change_diagnostics"][0]["accepted_state_changes"] = [{
        "metric_id": metric_id,
        "unit": unit,
        "from_value": before,
        "to_value": after,
        "delta": after - before,
    } for metric_id, unit, before, after in zip(
        evidence["accepted_state_metric_ids"], units, values[0], values[1])]
    return evidence


def test_cycle_keeps_only_safe_proposals(tmp_path):
    task = _task(tmp_path)
    manifest = run_cycle(task, no_agent=True)
    guard = _guard(task, manifest)
    assert guard["accepted"] == ["safe"]
    reasons = {w["title"]: w["reasons"] for w in guard["withheld"]}
    assert reasons["off spec"] == ["violates:rvp"]
    assert reasons["no prediction"] == ["no_prediction:rvp"]
    assert reasons["no gain"] == ["gain_not_above_0.0"]
    assert "proposals_withheld" in manifest["triggers"]
    items = list(Ledger(os.path.join(task, "continuous", "ledger", "events.jsonl")).current().values())
    titles = [v["title"] for v in items]
    assert "safe" in titles and "off spec" not in titles
    safe = next(item for item in items if item["title"] == "safe")
    assert safe["optimizer_evidence"]["cycle_id"] == manifest["cycle_id"]
    assert safe["optimizer_evidence"]["accepted_point_replayed"] is True
    envelope = safe["accepted_point_envelope_evidence"]
    assert envelope["cycle_id"] == manifest["cycle_id"]
    assert envelope["accepted_action_ids"] == [
        "p3_barg", "configuration.field.selection", "configuration.injector route.selection"]
    assert envelope["accepted_candidate_values"] == [0.5, 0.0, 0.0]
    assert envelope["slice_count"] == 3
    assert envelope["total_evaluation_count"] == 15
    lifecycle = safe["lifecycle_envelope_evidence"]
    assert lifecycle["cycle_id"] == manifest["cycle_id"]
    assert lifecycle["period_count"] == 1
    assert lifecycle["periods"][0]["period_id"] == manifest["cycle_id"]
    assert lifecycle["periods"][0]["envelope_evidence"] == envelope
    assert safe["evidence_current"] is True


def test_selective_field_route_power_and_limit_revisions_supersede_stale_advice(tmp_path):
    from neqsim_continuous import cli

    task = _task(tmp_path)
    first = run_cycle(task, no_agent=True)
    ledger_path = os.path.join(task, "continuous", "ledger", "events.jsonl")
    first_items = Ledger(ledger_path).current()
    first_id, first_item = next(iter(first_items.items()))
    assert first_item["accepted_point_envelope_evidence"]["accepted_candidate_values"] == [0.5, 0.0, 0.0]

    config_path = os.path.join(task, "study_config.yaml")
    with open(config_path, encoding="utf-8") as config_file:
        config = yaml.safe_load(config_file)
    config["production_route_configuration"] = 1.0
    with open(config_path, "w", encoding="utf-8") as config_file:
        yaml.safe_dump(config, config_file, sort_keys=False)

    assert cli.main(["update", task, "--no-agent"]) == 0
    items = Ledger(ledger_path).current()
    current = [(key, item) for key, item in items.items() if item["status"] == "proposed"]
    assert len(current) == 1
    second_id, second_item = current[0]
    assert second_item["revalidation_of"] == first_id
    assert second_item["evidence_current"] is True
    assert second_item["cycle"] != first["cycle_id"]
    assert second_item["accepted_point_envelope_evidence"]["cycle_id"] == second_item["cycle"]
    assert second_item["accepted_point_envelope_evidence"]["accepted_candidate_values"] == [0.5, 1.0, 0.0]
    assert items[first_id]["status"] == "superseded"
    assert items[first_id]["evidence_current"] is False

    with open(config_path, encoding="utf-8") as config_file:
        config = yaml.safe_load(config_file)
    config["injection_route_configuration"] = 1.5
    config["reservoir_pressure_bara"] = 175.0
    with open(config_path, "w", encoding="utf-8") as config_file:
        yaml.safe_dump(config, config_file, sort_keys=False)

    assert cli.main(["update", task, "--no-agent"]) == 0
    items = Ledger(ledger_path).current()
    assert items[second_id]["status"] == "superseded"
    assert items[second_id]["evidence_current"] is False
    assert not [item for item in items.values()
                if item["status"] == "proposed" and item.get("evidence_current")]

    config["injection_route_configuration"] = 2.0
    with open(config_path, "w", encoding="utf-8") as config_file:
        yaml.safe_dump(config, config_file, sort_keys=False)

    assert cli.main(["update", task, "--no-agent"]) == 0
    items = Ledger(ledger_path).current()
    current = [(key, item) for key, item in items.items() if item["status"] == "proposed"]
    assert len(current) == 1
    third_id, third_item = current[0]
    assert third_item["revalidation_of"] == second_id
    assert third_item["accepted_point_envelope_evidence"]["accepted_candidate_values"] == [0.5, 1.0, 2.0]
    operating_definition = third_item["optimizer_evidence"]["operating_definition"]
    assert operating_definition["reservoir_pressure_bara"] == 175.0
    assert operating_definition["mapped_network_power_kw"] == 120.0
    assert operating_definition["common_shaft_power_kw"] == 900.0
    assert operating_definition["shared_plant_power_kw"] == 1020.0

    config["mapped_network_power_kw"] = 400.0
    with open(config_path, "w", encoding="utf-8") as config_file:
        yaml.safe_dump(config, config_file, sort_keys=False)

    assert cli.main(["update", task, "--no-agent"]) == 0
    items = Ledger(ledger_path).current()
    assert items[third_id]["status"] == "superseded"
    assert items[third_id]["evidence_current"] is False
    assert not [item for item in items.values()
                if item["status"] == "proposed" and item.get("evidence_current")]

    config["shared_plant_power_limit_kw"] = 1400.0
    with open(config_path, "w", encoding="utf-8") as config_file:
        yaml.safe_dump(config, config_file, sort_keys=False)

    assert cli.main(["update", task, "--no-agent"]) == 0
    items = Ledger(ledger_path).current()
    current = [(key, item) for key, item in items.items() if item["status"] == "proposed"]
    assert len(current) == 1
    fourth_id, fourth_item = current[0]
    assert fourth_item["revalidation_of"] == third_id
    power_definition = fourth_item["optimizer_evidence"]["operating_definition"]
    assert power_definition["mapped_network_power_kw"] == 400.0
    assert power_definition["shared_plant_power_kw"] == 1300.0
    assert power_definition["shared_plant_power_limit_kw"] == 1400.0

    goal_path = os.path.join(task, "continuous", "goal.yaml")
    with open(goal_path, encoding="utf-8") as goal_file:
        goal = yaml.safe_load(goal_file)
    goal["constraints"][0]["limit"] = 0.62
    with open(goal_path, "w", encoding="utf-8") as goal_file:
        yaml.safe_dump(goal, goal_file, sort_keys=False)

    assert cli.main(["update", task, "--no-agent"]) == 0
    items = Ledger(ledger_path).current()
    assert items[fourth_id]["status"] == "superseded"
    assert items[fourth_id]["evidence_current"] is False
    assert not [item for item in items.values()
                if item["status"] == "proposed" and item.get("evidence_current")]
    with open(os.path.join(task, "continuous", "LIVING_REPORT.md"), encoding="utf-8") as report_file:
        report = report_file.read()
    assert "| Evidence |" in report
    assert "stale since" in report


def test_non_finite_mapped_network_power_invalidates_without_replacement(tmp_path):
    from neqsim_continuous import cli

    task = _task(tmp_path)
    run_cycle(task, no_agent=True)
    ledger_path = os.path.join(task, "continuous", "ledger", "events.jsonl")
    first_id = next(iter(Ledger(ledger_path).current()))
    config_path = os.path.join(task, "study_config.yaml")
    with open(config_path, encoding="utf-8") as config_file:
        config = yaml.safe_load(config_file)
    config["mapped_network_power_kw"] = float("nan")
    with open(config_path, "w", encoding="utf-8") as config_file:
        yaml.safe_dump(config, config_file, sort_keys=False)

    assert cli.main(["update", task, "--no-agent"]) == 0
    items = Ledger(ledger_path).current()
    assert items[first_id]["status"] == "superseded"
    assert items[first_id]["evidence_current"] is False
    assert not [item for item in items.values()
                if item["status"] == "proposed" and item.get("evidence_current")]


def test_malformed_evidence_sensitivity_configuration_invalidates_fail_closed(tmp_path):
    from neqsim_continuous import cli

    task = _task(tmp_path)
    run_cycle(task, no_agent=True)
    ledger_path = os.path.join(task, "continuous", "ledger", "events.jsonl")
    first_id = next(iter(Ledger(ledger_path).current()))

    plan_path = os.path.join(task, "continuous", "cycle_plan.yaml")
    with open(plan_path, encoding="utf-8") as plan_file:
        plan = yaml.safe_load(plan_file)
    plan["production"]["evidence_sensitive_stages"] = "guard"
    with open(plan_path, "w", encoding="utf-8") as plan_file:
        yaml.safe_dump(plan, plan_file, sort_keys=False)

    config_path = os.path.join(task, "study_config.yaml")
    with open(config_path, encoding="utf-8") as config_file:
        config = yaml.safe_load(config_file)
    config["production_route_configuration"] = 1.0
    with open(config_path, "w", encoding="utf-8") as config_file:
        yaml.safe_dump(config, config_file, sort_keys=False)

    assert cli.main(["update", task, "--no-agent"]) == 0
    items = Ledger(ledger_path).current()
    assert items[first_id]["status"] == "superseded"
    assert items[first_id]["evidence_current"] is False


def test_failed_guard_prevents_unchecked_proposal_from_reaching_ledger(tmp_path):
    task = _task(tmp_path)
    optimize_path = os.path.join(task, "continuous", "stages", "optimize.py")
    with open(optimize_path, "w", encoding="utf-8") as optimize_file:
        optimize_file.write("def run(ctx):\n    return {'proposals': [5]}\n")

    manifest = run_cycle(task, no_agent=True)
    statuses = {row["name"]: row["status"] for row in manifest["stages"]}
    assert statuses["guard"] == "fail"
    assert statuses["ledger"] == "skipped"
    ledger_path = os.path.join(task, "continuous", "ledger", "events.jsonl")
    assert Ledger(ledger_path).events() == []


@pytest.mark.parametrize("prior_status", ["proposed", "accepted", "implemented"])
@pytest.mark.parametrize("failure", ["malformed_proposal", "optimizer_exception"])
def test_changed_power_limit_invalidates_existing_advice_before_failed_guard(
        tmp_path, prior_status, failure):
    task = _task(tmp_path)
    run_cycle(task, no_agent=True)
    book = Ledger(os.path.join(task, "continuous", "ledger", "events.jsonl"))
    incumbent_id = next(iter(book.current()))
    if prior_status in ("accepted", "implemented"):
        book.set_status(incumbent_id, "accepted", by="test-reviewer")
    if prior_status == "implemented":
        book.set_status(incumbent_id, "implemented", by="test-reviewer")

    config_path = pathlib.Path(task) / "study_config.yaml"
    config = yaml.safe_load(config_path.read_text(encoding="utf-8"))
    config["shared_plant_power_limit_kw"] = 1.0
    config_path.write_text(yaml.safe_dump(config), encoding="utf-8")
    optimize_path = pathlib.Path(task) / "continuous" / "stages" / "optimize.py"
    optimize_path.write_text(
        "def run(ctx):\n    return {'proposals': [5]}\n" if failure == "malformed_proposal"
        else "def run(ctx):\n    raise RuntimeError('synthetic optimizer failure')\n",
        encoding="utf-8")

    failed_cycle = run_cycle(task, no_agent=True)
    statuses = {row["name"]: row["status"] for row in failed_cycle["stages"]}
    assert failed_cycle["degraded"] is True
    assert statuses["evidence"] == "ok"
    if failure == "malformed_proposal":
        assert statuses["guard"] == "fail"
        assert statuses["ledger"] == "skipped"
    else:
        assert statuses["script:optimize"] == "fail"
    incumbent = book.current()[incumbent_id]
    assert incumbent["evidence_current"] is False
    assert incumbent["evidence_invalidated_cycle"] == failed_cycle["cycle_id"]
    assert incumbent["status"] == ("implemented" if prior_status == "implemented" else "superseded")
    assert len(book.current()) == 1
    report = (pathlib.Path(task) / "continuous" / "LIVING_REPORT.md").read_text(encoding="utf-8")
    row = next(line for line in report.splitlines() if line.startswith("| " + incumbent_id + " |"))
    assert "stale since " + failed_cycle["cycle_id"] in row
    assert "| current |" not in row
    assert "Waiting for a decision: " + incumbent_id not in report

    # Repeating a degraded cycle must not append duplicate invalidation events.
    run_cycle(task, no_agent=True)
    invalidations = [event for event in book.events() if event["event"] == "evidence_invalidated"]
    assert len(invalidations) == 1

    # A later safe definition creates fresh advice and preserves prior lineage.
    config["shared_plant_power_limit_kw"] = 1200.0
    config_path.write_text(yaml.safe_dump(config), encoding="utf-8")
    optimize_path.write_text(STAGE_OPTIMIZE, encoding="utf-8")
    recovered_cycle = run_cycle(task, no_agent=True)
    assert recovered_cycle["degraded"] is False
    replacements = [item for item in book.current().values() if item.get("evidence_current")]
    assert len(replacements) == 1
    assert replacements[0]["revalidation_of"] == incumbent_id
    assert replacements[0]["cycle"] == recovered_cycle["cycle_id"]


def test_dry_run_does_not_invalidate_persisted_evidence(tmp_path):
    task = _task(tmp_path)
    run_cycle(task, no_agent=True)
    book = Ledger(os.path.join(task, "continuous", "ledger", "events.jsonl"))
    before = book.events()
    config_path = pathlib.Path(task) / "study_config.yaml"
    config = yaml.safe_load(config_path.read_text(encoding="utf-8"))
    config["shared_plant_power_limit_kw"] = 1.0
    config_path.write_text(yaml.safe_dump(config), encoding="utf-8")

    preview = run_cycle(task, dry_run=True, no_agent=True)

    assert preview["dry_run"] is True
    assert preview["evidence"]["invalidated_opportunities"] == [next(iter(book.current()))]
    assert book.events() == before


def _interrupt_after_stage(task, stage):
    """Freeze a cycle at a completed stage before later publication side effects."""
    plan = yaml.safe_load((pathlib.Path(task) / "continuous" / "cycle_plan.yaml").read_text())
    selected = plan["stages"][:plan["stages"].index(stage) + 1]
    completed = run_cycle(task, stages=selected, no_agent=True)
    cycle_path = pathlib.Path(task) / "continuous" / "cycles" / completed["cycle_id"] / "cycle.json"
    interrupted = json.loads(cycle_path.read_text(encoding="utf-8"))
    interrupted["status"] = "running"
    interrupted.pop("finished_at", None)
    cycle_path.write_text(json.dumps(interrupted), encoding="utf-8")
    return completed, cycle_path.parent


def test_resume_after_guard_revalidates_filtered_proposals_before_ledger(tmp_path):
    task = _task(tmp_path)
    first, cycle = _interrupt_after_stage(task, "guard")
    initial = json.loads((cycle / "guard.json").read_text())
    assert initial["accepted"] == ["safe"]
    assert {row["title"] for row in initial["withheld"]} == {"off spec", "no prediction", "no gain"}
    # The expensive model/optimizer stages must remain checkpointed.
    (pathlib.Path(task) / "continuous" / "stages" / "optimize.py").write_text(
        "def run(ctx):\n    raise AssertionError('completed optimizer was rerun')\n")

    resumed = run_cycle(task, no_agent=True)

    assert resumed["cycle_id"] == first["cycle_id"]
    assert resumed["degraded"] is False
    assert json.loads((cycle / "guard.json").read_text()) == initial
    items = list(Ledger(pathlib.Path(task) / "continuous" / "ledger" / "events.jsonl").current().values())
    assert [item["title"] for item in items] == ["safe"]
    assert items[0]["guard"] == "passed"
    assert items[0]["requires_approval"] is True
    assert items[0]["evidence_current"] is True


@pytest.mark.parametrize("stage,resid,limit,block", [
    ("gates", 4.0, 0.70, "model_gate:resid"),
    ("constraints", 4.0, 0.70, "model_gate:resid"),
    ("constraints", 1.0, None, "constraint_unconfirmed:rvp"),
    ("guard", 1.0, None, "constraint_unconfirmed:rvp"),
])
def test_resume_retains_upstream_production_blocks(tmp_path, stage, resid, limit, block):
    task = _task(tmp_path, resid=resid, limit=limit)
    first, cycle = _interrupt_after_stage(task, stage)

    resumed = run_cycle(task, no_agent=True)

    assert resumed["cycle_id"] == first["cycle_id"]
    guard = json.loads((cycle / "guard.json").read_text())
    assert guard["accepted"] == []
    assert block in guard["blocks"]
    assert Ledger(pathlib.Path(task) / "continuous" / "ledger" / "events.jsonl").events() == []


def test_resume_restores_frozen_user_restrictions_before_revalidation(tmp_path):
    from neqsim_continuous import user_input

    task = _task(tmp_path)
    entry = user_input.add(task, "Tighter current RVP limit", by="engineer",
                           effects=[{"kind": "constraint", "name": "rvp", "limit": 0.5}])
    first, cycle = _interrupt_after_stage(task, "inputs")
    # A later edit belongs to the next cycle, not the already frozen input stage.
    user_input.resolve(task, entry["id"], by="engineer")

    resumed = run_cycle(task, no_agent=True)

    assert resumed["cycle_id"] == first["cycle_id"]
    assert resumed["degraded"] is False
    guard = json.loads((cycle / "guard.json").read_text())
    assert guard["accepted"] == []
    assert Ledger(pathlib.Path(task) / "continuous" / "ledger" / "events.jsonl").events() == []
    assert entry["id"] in (cycle / "digest.md").read_text()


def test_resume_missing_user_input_checkpoint_blocks_advice(tmp_path):
    task = _task(tmp_path)
    first, cycle = _interrupt_after_stage(task, "inputs")
    (cycle / "user_input.json").unlink()

    resumed = run_cycle(task, no_agent=True)

    assert resumed["cycle_id"] == first["cycle_id"]
    assert resumed["degraded"] is True
    guard = json.loads((cycle / "guard.json").read_text())
    assert guard["accepted"] == []
    assert "user_input_resume_state_invalid" in guard["blocks"]
    assert Ledger(pathlib.Path(task) / "continuous" / "ledger" / "events.jsonl").events() == []


def test_optimizer_evidence_cannot_reach_ledger_without_guard(tmp_path):
    task = _task(tmp_path)
    plan_path = pathlib.Path(task) / "continuous" / "cycle_plan.yaml"
    plan = yaml.safe_load(plan_path.read_text())
    plan["stages"].remove("guard")
    plan_path.write_text(yaml.safe_dump(plan))

    manifest = run_cycle(task, no_agent=True)

    statuses = {row["name"]: row["status"] for row in manifest["stages"]}
    assert statuses["ledger"] == "fail"
    assert manifest["degraded"] is True
    assert Ledger(pathlib.Path(task) / "continuous" / "ledger" / "events.jsonl").events() == []


@pytest.mark.parametrize("change", ["old_cycle", "setpoint", "evidence_boolean", "new_proposal", "removed_evidence"])
def test_ledger_rejects_proposals_added_or_changed_after_guard(tmp_path, change):
    from neqsim_continuous import stages

    ctx = _guard_context(tmp_path, _valid_proposal())
    ctx.state_dir, ctx.dry_run, ctx.evidence_analysis = str(tmp_path), False, None
    ctx.kpis = {}
    ctx.stage_results = {"guard": production.guard(ctx, {})}
    accepted = copy.deepcopy(ctx.proposals[0])
    if change == "old_cycle":
        ctx.cycle_id = "C-2"
        ctx.proposals = []
        ctx.stage_results["guard"] = production.guard(ctx, {})
        ctx.proposals.append(accepted)
    elif change == "setpoint":
        ctx.proposals[0]["setpoints"]["p3_barg"] = 99.0
    elif change == "evidence_boolean":
        # Python equality treats True and 1 as equal, but the evidence contract does not.
        ctx.proposals[0]["optimizer_evidence"]["simulation_converged"] = 1
    elif change == "removed_evidence":
        del ctx.proposals[0]["optimizer_evidence"]
        del ctx.proposals[0]["accepted_point_envelope_evidence"]
    else:
        accepted["title"] = "unchecked extra proposal"
        ctx.proposals.append(accepted)

    result = stages.ledger(ctx, {})

    assert result.status == "fail"
    assert "unguarded_optimizer_proposal" in result.triggers
    assert Ledger(tmp_path / "ledger" / "events.jsonl").events() == []


def test_resume_after_ledger_keeps_current_cycle_revalidation_current(tmp_path):
    task = _task(tmp_path)
    run_cycle(task, no_agent=True)
    book = Ledger(os.path.join(task, "continuous", "ledger", "events.jsonl"))
    incumbent_id = next(iter(book.current()))
    config_path = pathlib.Path(task) / "study_config.yaml"
    config = yaml.safe_load(config_path.read_text(encoding="utf-8"))
    config["production_route_configuration"] = 1.0
    config_path.write_text(yaml.safe_dump(config), encoding="utf-8")
    completed = run_cycle(task, no_agent=True)
    events_before_resume = book.events()
    replacement_id = next(key for key, item in book.current().items() if item.get("evidence_current"))

    # Recreate interruption after the ledger's persisted side effects, before digest.
    cycle_path = pathlib.Path(task) / "continuous" / "cycles" / completed["cycle_id"] / "cycle.json"
    interrupted = json.loads(cycle_path.read_text(encoding="utf-8"))
    interrupted["status"] = "running"
    interrupted.pop("finished_at", None)
    ledger_index = next(index for index, row in enumerate(interrupted["stages"]) if row["name"] == "ledger")
    interrupted["stages"] = interrupted["stages"][:ledger_index + 1]
    cycle_path.write_text(json.dumps(interrupted), encoding="utf-8")

    resumed = run_cycle(task, no_agent=True)

    assert resumed["cycle_id"] == completed["cycle_id"]
    assert resumed["degraded"] is False
    assert book.events() == events_before_resume
    assert book.current()[replacement_id]["evidence_current"] is True
    assert book.current()[replacement_id]["revalidation_of"] == incumbent_id


@pytest.mark.parametrize("field, reason", [
    ("simulation_converged", "simulation_not_converged"),
    ("candidate_feasible", "infeasible_candidate"),
    ("candidate_finite", "non_finite_candidate"),
    ("constraint_evidence_complete", "constraint_evidence_incomplete"),
    ("state_restore_complete", "state_restore_incomplete"),
    ("accepted_point_replayed", "accepted_point_not_replayed"),
    ("actions_complete", "partial_action_application"),
])
def test_guard_rejects_incomplete_optimizer_evidence(tmp_path, field, reason):
    proposal = _valid_proposal()
    proposal["optimizer_evidence"][field] = False
    ctx = _guard_context(tmp_path, proposal)
    production.guard(ctx, {})
    assert ctx.proposals == []
    assert json.loads((tmp_path / "guard.json").read_text())["withheld"][0]["reasons"] == [reason]


def test_guard_rejects_missing_and_stale_optimizer_evidence(tmp_path):
    missing = _valid_proposal()
    missing.pop("optimizer_evidence")
    missing.pop("accepted_point_envelope_evidence")
    stale = _valid_proposal()
    stale["title"] = "stale"
    stale["optimizer_evidence"]["cycle_id"] = "C-0"
    ctx = _guard_context(tmp_path, missing)
    ctx.proposals.append(stale)
    production.guard(ctx, {})
    reasons = {row["title"]: row["reasons"]
               for row in json.loads((tmp_path / "guard.json").read_text())["withheld"]}
    assert reasons == {"candidate": ["missing_optimizer_evidence"], "stale": ["stale_candidate"]}


def test_guard_keeps_optimizer_only_proposal_when_envelope_is_optional(tmp_path):
    proposal = _valid_proposal()
    proposal.pop("accepted_point_envelope_evidence")
    ctx = _guard_context(tmp_path, proposal)
    production.guard(ctx, {})
    assert [row["title"] for row in ctx.proposals] == ["candidate"]


def test_guard_requires_envelope_when_configured(tmp_path):
    proposal = _valid_proposal()
    proposal.pop("accepted_point_envelope_evidence")
    ctx = _guard_context(tmp_path, proposal)
    ctx.plan["production"]["require_envelope_evidence"] = True
    production.guard(ctx, {})
    assert ctx.proposals == []
    assert json.loads((tmp_path / "guard.json").read_text())["withheld"][0]["reasons"] == [
        "missing_accepted_point_envelope_evidence"
    ]


def test_guard_rejects_stale_or_internally_inconsistent_envelope(tmp_path):
    variants = []

    stale = _valid_proposal()
    stale["title"] = "stale envelope"
    stale["accepted_point_envelope_evidence"]["cycle_id"] = "C-0"
    variants.append(stale)

    optimizer_mismatch = _valid_proposal()
    optimizer_mismatch["title"] = "optimizer mismatch"
    optimizer_mismatch["accepted_point_envelope_evidence"]["optimizer_evidence"] = dict(
        optimizer_mismatch["optimizer_evidence"], evaluation_count=11)
    variants.append(optimizer_mismatch)

    setpoint_mismatch = _valid_proposal()
    setpoint_mismatch["title"] = "setpoint mismatch"
    setpoint_mismatch["accepted_point_envelope_evidence"]["accepted_candidate_values"] = [0.6]
    variants.append(setpoint_mismatch)

    partial_slice = _valid_proposal()
    partial_slice["title"] = "partial slice"
    partial_slice["accepted_point_envelope_evidence"]["slices"][0]["complete"] = False
    variants.append(partial_slice)

    partial_recovery = _valid_proposal()
    partial_recovery["title"] = "partial recovery"
    partial_recovery["accepted_point_envelope_evidence"]["slices"][0]["points"][0][
        "baseline_restored"] = False
    variants.append(partial_recovery)

    count_mismatch = _valid_proposal()
    count_mismatch["title"] = "count mismatch"
    count_mismatch["accepted_point_envelope_evidence"]["completed_point_count"] = 2
    variants.append(count_mismatch)

    duplicate_axes = _valid_proposal()
    duplicate_axes["title"] = "duplicate axes"
    duplicate_slice = copy.deepcopy(duplicate_axes["accepted_point_envelope_evidence"]["slices"][0])
    duplicate_axes["accepted_point_envelope_evidence"].update({
        "slice_count": 2,
        "requested_point_count": 2,
        "completed_point_count": 2,
        "envelope_evaluation_count": 2,
        "envelope_runtime_seconds": 0.2,
        "total_evaluation_count": 14,
    })
    duplicate_axes["accepted_point_envelope_evidence"]["slices"].append(duplicate_slice)
    variants.append(duplicate_axes)

    ctx = _guard_context(tmp_path, variants[0])
    ctx.plan["production"]["require_envelope_evidence"] = True
    ctx.proposals.extend(variants[1:])
    production.guard(ctx, {})
    assert ctx.proposals == []
    reasons = {row["title"]: row["reasons"]
               for row in json.loads((tmp_path / "guard.json").read_text())["withheld"]}
    assert "stale_envelope_evidence" in reasons["stale envelope"]
    assert "envelope_optimizer_evidence_mismatch" in reasons["optimizer mismatch"]
    assert "envelope_setpoints_mismatch" in reasons["setpoint mismatch"]
    assert "envelope_slice_0_incomplete" in reasons["partial slice"]
    assert "envelope_slice_0_point_recovery_incomplete" in reasons["partial recovery"]
    assert "envelope_aggregate_count_mismatch" in reasons["count mismatch"]
    assert "duplicate_envelope_axes" in reasons["duplicate axes"]


def test_guard_recomputes_optional_operating_region_transitions(tmp_path):
    valid = _valid_proposal()
    valid["title"] = "valid sampled transitions"
    valid_slice = valid["accepted_point_envelope_evidence"]["slices"][0]
    valid_slice["axis_action_semantics"] = ["CONTINUOUS"]
    valid_slice["operating_region_transitions"] = []
    valid_slice["points"][0].update({
        "sequence_index": 0,
        "grid_indexes": [0],
        "outcome": "FEASIBLE",
    })

    tampered = copy.deepcopy(valid)
    tampered["title"] = "tampered sampled transitions"
    tampered["accepted_point_envelope_evidence"]["slices"][0][
        "operating_region_transitions"].append({"kind": "BECAME_INFEASIBLE"})

    ctx = _guard_context(tmp_path, valid)
    ctx.plan["production"]["require_envelope_evidence"] = True
    ctx.proposals.append(tampered)
    production.guard(ctx, {})

    assert [row["title"] for row in ctx.proposals] == ["valid sampled transitions"]
    withheld = json.loads((tmp_path / "guard.json").read_text())["withheld"]
    assert withheld[0]["reasons"] == [
        "envelope_slice_0_operating_region_transition_mismatch"
    ]


def test_guard_fails_closed_for_malformed_envelope_types(tmp_path):
    malformed = _valid_proposal()
    malformed["title"] = "malformed envelope"
    malformed["accepted_point_envelope_evidence"] = []
    malformed_actions = _valid_proposal()
    malformed_actions["title"] = "malformed actions"
    malformed_actions["accepted_point_envelope_evidence"]["accepted_action_ids"] = 7
    malformed_optimizer = _valid_proposal()
    malformed_optimizer["title"] = "malformed optimizer"
    malformed_optimizer["optimizer_evidence"] = "not a mapping"

    ctx = _guard_context(tmp_path, malformed)
    ctx.plan["production"]["require_envelope_evidence"] = True
    ctx.proposals.extend([malformed_actions, malformed_optimizer])
    production.guard(ctx, {})
    assert ctx.proposals == []
    reasons = {row["title"]: row["reasons"]
               for row in json.loads((tmp_path / "guard.json").read_text())["withheld"]}
    assert reasons["malformed envelope"] == ["invalid_accepted_point_envelope_evidence"]
    assert "invalid_accepted_action_vector" in reasons["malformed actions"]
    assert "missing_optimizer_evidence" in reasons["malformed optimizer"]


def test_guard_persists_complete_lifecycle_envelope(tmp_path):
    proposal = _valid_proposal()
    proposal["lifecycle_envelope_evidence"] = _valid_lifecycle_evidence(
        proposal["accepted_point_envelope_evidence"])
    ctx = _guard_context(tmp_path, proposal)
    ctx.plan["production"]["require_envelope_evidence"] = True
    ctx.plan["production"]["require_lifecycle_envelope_evidence"] = True
    production.guard(ctx, {})
    assert [row["title"] for row in ctx.proposals] == ["candidate"]
    lifecycle = ctx.proposals[0]["lifecycle_envelope_evidence"]
    assert lifecycle["period_count"] == 2
    assert lifecycle["periods"][0]["period_id"] == "C-1"
    assert lifecycle["periods"][1]["period_id"] == "future-period"


def test_guard_persists_schema4_operating_region_migration_evidence(tmp_path):
    proposal = _valid_proposal()
    _add_empty_operating_region_evidence(proposal["accepted_point_envelope_evidence"])
    proposal["lifecycle_envelope_evidence"] = _valid_region_lifecycle_evidence(
        proposal["accepted_point_envelope_evidence"])
    ctx = _guard_context(tmp_path, proposal)
    ctx.plan["production"]["require_lifecycle_envelope_evidence"] = True
    production.guard(ctx, {})
    assert [row["title"] for row in ctx.proposals] == ["candidate"]
    lifecycle = ctx.proposals[0]["lifecycle_envelope_evidence"]
    assert lifecycle["schema_version"] == 4
    slice_change = lifecycle["period_change_diagnostics"][0]["slice_changes"][0]
    assert slice_change["retained_operating_region_transition_count"] == 0
    assert slice_change["removed_operating_region_transitions"] == []
    assert slice_change["added_operating_region_transitions"] == []


def test_guard_persists_schema5_boundary_refinement_migration_evidence(tmp_path):
    proposal = _valid_proposal()
    _add_empty_operating_region_evidence(proposal["accepted_point_envelope_evidence"])
    _add_empty_boundary_refinement_evidence(proposal["accepted_point_envelope_evidence"])
    proposal["lifecycle_envelope_evidence"] = _valid_boundary_lifecycle_evidence(
        proposal["accepted_point_envelope_evidence"])
    ctx = _guard_context(tmp_path, proposal)
    ctx.plan["production"]["require_lifecycle_envelope_evidence"] = True
    production.guard(ctx, {})
    assert [row["title"] for row in ctx.proposals] == ["candidate"]
    lifecycle = ctx.proposals[0]["lifecycle_envelope_evidence"]
    assert lifecycle["schema_version"] == 5
    slice_change = lifecycle["period_change_diagnostics"][0]["slice_changes"][0]
    assert slice_change["retained_operating_region_boundary_refinement_count"] == 0
    assert slice_change["removed_operating_region_boundary_refinements"] == []
    assert slice_change["added_operating_region_boundary_refinements"] == []


def test_guard_persists_schema6_boundary_headroom_evidence(tmp_path):
    proposal = _valid_proposal()
    _add_empty_operating_region_evidence(proposal["accepted_point_envelope_evidence"])
    _add_empty_boundary_refinement_evidence(proposal["accepted_point_envelope_evidence"])
    proposal["lifecycle_envelope_evidence"] = _valid_boundary_headroom_lifecycle_evidence(
        proposal["accepted_point_envelope_evidence"])
    ctx = _guard_context(tmp_path, proposal)
    ctx.plan["production"]["require_lifecycle_envelope_evidence"] = True
    production.guard(ctx, {})
    assert [row["title"] for row in ctx.proposals] == ["candidate"]
    assert ctx.proposals[0]["lifecycle_envelope_evidence"]["schema_version"] == 6


def test_guard_persists_schema7_cross_slice_constraint_summary(tmp_path):
    proposal = _valid_proposal()
    _add_empty_operating_region_evidence(proposal["accepted_point_envelope_evidence"])
    _add_empty_boundary_refinement_evidence(proposal["accepted_point_envelope_evidence"])
    _add_empty_constraint_summary_evidence(proposal["accepted_point_envelope_evidence"])
    proposal["lifecycle_envelope_evidence"] = _valid_constraint_summary_lifecycle_evidence(
        proposal["accepted_point_envelope_evidence"])
    ctx = _guard_context(tmp_path, proposal)
    ctx.plan["production"]["require_lifecycle_envelope_evidence"] = True
    production.guard(ctx, {})
    assert [row["title"] for row in ctx.proposals] == ["candidate"]
    lifecycle = ctx.proposals[0]["lifecycle_envelope_evidence"]
    assert lifecycle["schema_version"] == 7
    assert lifecycle["periods"][0]["envelope_evidence"][
        "cross_slice_constraint_summary"]["ranked_constraints"] == []


def test_guard_persists_schema8_constraint_identity_migration(tmp_path):
    proposal = _valid_proposal()
    envelope = proposal["accepted_point_envelope_evidence"]
    _add_empty_operating_region_evidence(envelope)
    _add_empty_boundary_refinement_evidence(envelope)
    current_constraint = {
        "source": "PLANT_CONSTRAINT",
        "qualified_constraint_name": "host::separator gas capacity",
        "normalized_utilization": 0.96,
        "normalized_margin": 0.04,
        "current_value": 960.0,
        "applicable_limit": 1000.0,
        "physical_margin": 40.0,
        "physical_unit": "kg/hr",
        "feasible": True,
        "diagnostic": "AVAILABLE",
        "applicable_limit_role": "OPERATING_LIMIT",
        "applicable_limit_provenance": "synthetic separator limit",
    }
    envelope["slices"][0]["points"][0]["leading_constraint"] = current_constraint
    _add_empty_constraint_summary_evidence(envelope)
    envelope["cross_slice_constraint_summary"] = production._cross_slice_constraint_summary(
        envelope["slices"], envelope["cross_slice_constraint_summary"])
    lifecycle = _valid_constraint_summary_migration_lifecycle_evidence(envelope)

    future_envelope = lifecycle["periods"][1]["envelope_evidence"]
    future_constraint = dict(
        current_constraint,
        qualified_constraint_name="host::shared power",
        current_value=980.0,
        applicable_limit=1000.0,
        physical_margin=20.0,
        physical_unit="kW",
        applicable_limit_role="SHARED_RESOURCE",
        applicable_limit_provenance="synthetic host power budget",
    )
    future_envelope["slices"][0]["points"][0]["leading_constraint"] = future_constraint
    future_envelope["cross_slice_constraint_summary"] = production._cross_slice_constraint_summary(
        future_envelope["slices"], future_envelope["cross_slice_constraint_summary"])
    lifecycle["period_change_diagnostics"] = production._period_change_diagnostics(
        lifecycle["periods"], lifecycle["accepted_action_ids"], lifecycle["time_unit"],
        require_operating_transitions=True, require_boundary_refinements=True,
        require_constraint_summary_identities=True)
    proposal["lifecycle_envelope_evidence"] = lifecycle

    ctx = _guard_context(tmp_path, proposal)
    ctx.plan["production"]["require_lifecycle_envelope_evidence"] = True
    production.guard(ctx, {})
    assert [row["title"] for row in ctx.proposals] == ["candidate"]
    change = ctx.proposals[0]["lifecycle_envelope_evidence"][
        "period_change_diagnostics"][0]
    assert change["retained_ranked_constraint_identity_count"] == 0
    assert change["removed_ranked_constraint_identities"][0][
        "qualified_constraint_name"] == "host::separator gas capacity"
    assert change["added_ranked_constraint_identities"][0][
        "qualified_constraint_name"] == "host::shared power"

    tampered = copy.deepcopy(proposal)
    tampered["title"] = "tampered lifecycle constraint identity"
    tampered["lifecycle_envelope_evidence"]["period_change_diagnostics"][0][
        "added_ranked_constraint_identities"][0]["physical_unit"] = "MW"
    tampered_ctx = _guard_context(tmp_path, tampered)
    tampered_ctx.plan["production"]["require_lifecycle_envelope_evidence"] = True
    production.guard(tampered_ctx, {})
    assert tampered_ctx.proposals == []
    reasons = json.loads((tmp_path / "guard.json").read_text())["withheld"][0]["reasons"]
    assert "lifecycle_period_change_diagnostics_mismatch" in reasons


def _promotable_schema8_task(tmp_path):
    """Create one completed cycle with a guard-approved schema-v8 ledger receipt."""
    task = tmp_path / "promotable-lifecycle"
    task.mkdir()
    (task / "results.json").write_text(json.dumps({
        "key_results": {"accepted_export_kg_hr": 1234.0},
        "conclusions": "Synthetic ten-well current-best result.",
    }), encoding="utf-8")
    (task / "study_config.yaml").write_text("study:\n  title: Ten-well lifecycle\n", encoding="utf-8")
    make_living(str(task), template="production")
    plan_path = task / "continuous" / "cycle_plan.yaml"
    plan = yaml.safe_load(plan_path.read_text(encoding="utf-8"))
    plan["production"]["require_envelope_evidence"] = True
    plan["production"]["require_lifecycle_envelope_evidence"] = True
    plan_path.write_text(yaml.safe_dump(plan), encoding="utf-8")

    proposal = _valid_proposal()
    envelope = proposal["accepted_point_envelope_evidence"]
    _add_empty_operating_region_evidence(envelope)
    _add_empty_boundary_refinement_evidence(envelope)
    current_constraint = {
        "source": "PLANT_CONSTRAINT",
        "qualified_constraint_name": "host::separator gas capacity",
        "normalized_utilization": 0.96,
        "normalized_margin": 0.04,
        "current_value": 960.0,
        "applicable_limit": 1000.0,
        "physical_margin": 40.0,
        "physical_unit": "kg/hr",
        "feasible": True,
        "diagnostic": "AVAILABLE",
        "applicable_limit_role": "OPERATING_LIMIT",
        "applicable_limit_provenance": "synthetic ten-well separator limit",
    }
    envelope["slices"][0]["points"][0]["leading_constraint"] = current_constraint
    _add_empty_constraint_summary_evidence(envelope)
    envelope["cross_slice_constraint_summary"] = production._cross_slice_constraint_summary(
        envelope["slices"], envelope["cross_slice_constraint_summary"])
    lifecycle = _valid_constraint_summary_migration_lifecycle_evidence(envelope)
    future_envelope = lifecycle["periods"][1]["envelope_evidence"]
    future_constraint = dict(
        current_constraint,
        qualified_constraint_name="host::shared power",
        current_value=1020.0,
        applicable_limit=1000.0,
        normalized_utilization=1.02,
        normalized_margin=-0.02,
        physical_margin=-20.0,
        physical_unit="kW",
        feasible=False,
        applicable_limit_role="SHARED_RESOURCE",
        applicable_limit_provenance="synthetic ten-well shared host power budget",
    )
    future_envelope["slices"][0]["points"][0].update({
        "feasible": False,
        "outcome": "OTHER_MODEL_CONSTRAINT_VIOLATED",
        "leading_constraint": future_constraint,
    })
    future_envelope["cross_slice_constraint_summary"] = production._cross_slice_constraint_summary(
        future_envelope["slices"], future_envelope["cross_slice_constraint_summary"])
    lifecycle["period_change_diagnostics"] = production._period_change_diagnostics(
        lifecycle["periods"], lifecycle["accepted_action_ids"], lifecycle["time_unit"],
        require_operating_transitions=True, require_boundary_refinements=True,
        require_constraint_summary_identities=True)
    proposal = production.proposal_from_lifecycle_json(
        json.dumps(lifecycle), cycle_id="C-1", title=proposal["title"],
        objective_kpi=proposal["objective_kpi"], baseline_value=proposal["baseline_value"],
        expected_gain=proposal["expected_gain"]["value"], predicted=proposal["predicted"])
    assert proposal["setpoints"] == {"p3_barg": 0.5}
    assert proposal["accepted_point_envelope_evidence"] == envelope
    ctx = _guard_context(task / "continuous" / "cycles" / "C-1", proposal)
    ctx.plan["production"]["require_envelope_evidence"] = True
    ctx.plan["production"]["require_lifecycle_envelope_evidence"] = True
    production.guard(ctx, {})
    accepted = ctx.proposals[0]

    cycle = task / "continuous" / "cycles" / "C-1"
    cycle.mkdir(parents=True, exist_ok=True)
    (cycle / "cycle.json").write_text(json.dumps({
        "cycle_id": "C-1",
        "status": "complete",
        "versions": {"neqsim_commit": "synthetic-test"},
        "stages": [
            {"name": "guard", "status": "ok"},
            {"name": "ledger", "status": "ok"},
        ],
    }), encoding="utf-8")
    fields = {key: value for key, value in accepted.items()
              if key not in ("title", "category")}
    fields["evidence_current"] = True
    book = Ledger(task / "continuous" / "ledger" / "events.jsonl")
    event = book.create(accepted["title"], accepted.get("category", "operational"),
                        cycle="C-1", **fields)
    return task, accepted["lifecycle_envelope_evidence"], event["id"]


@pytest.mark.parametrize("failure", ["stale", "duplicate", "envelope-cycle", "non-finite"])
def test_lifecycle_json_proposal_adapter_fails_closed(tmp_path, failure):
    _, evidence, _ = _promotable_schema8_task(tmp_path)
    candidate = copy.deepcopy(evidence)
    if failure == "stale":
        candidate["cycle_id"] = "C-0"
    elif failure == "duplicate":
        candidate["periods"].append(copy.deepcopy(candidate["periods"][0]))
        candidate["period_count"] += 1
    elif failure == "envelope-cycle":
        candidate["periods"][0]["envelope_evidence"]["cycle_id"] = "C-0"
    else:
        candidate["periods"][0]["envelope_evidence"]["envelope_runtime_seconds"] = float("nan")

    with pytest.raises(ValueError, match="Lifecycle|lifecycle"):
        production.proposal_from_lifecycle_json(
            json.dumps(candidate), cycle_id="C-1", title="unsafe adapter candidate",
            objective_kpi="oil_Sm3d", baseline_value=12700.0, expected_gain=300.0,
            predicted={"rvp_bara": 0.63})


def _objective_bound_lifecycle(evidence):
    """Add exact current-source objective metadata to one valid lifecycle fixture."""
    candidate = copy.deepcopy(evidence)
    current = next(period for period in candidate["periods"] if period["period_id"] == "C-1")
    current["envelope_evidence"]["optimizer_evidence"].update({
        "objective_name": "validated gas export",
        "objective_direction": "MAXIMIZE",
        "objective_unit": "kg/hr",
        "initial_objective_value": 12700.0,
        "accepted_objective_value": 13000.0,
    })
    return candidate


def _objective_context(tmp_path):
    """Return the task-local goal and reviewed baseline for objective binding tests."""
    return SimpleNamespace(
        cycle_id="C-1",
        goal={"objective": {
            "metric": "export_kg_hr",
            "source_name": "validated gas export",
            "direction": "maximize",
            "unit": "kg/hr",
        }},
        baseline={"kpis": {"export_kg_hr": 12700.0}},
        cycle_dir=str(tmp_path),
    )


def test_lifecycle_context_binds_source_objective_to_reviewed_baseline(tmp_path):
    _, evidence, _ = _promotable_schema8_task(tmp_path)

    proposal = production.proposal_from_lifecycle_context(
        json.dumps(_objective_bound_lifecycle(evidence)),
        _objective_context(tmp_path),
        "Increase validated field export",
        predicted={"rvp_bara": 0.63},
    )

    assert proposal["objective_kpi"] == "export_kg_hr"
    assert proposal["baseline_value"] == 12700.0
    assert proposal["expected_gain"] == {"value": 300.0, "kpi": "export_kg_hr"}
    assert proposal["predicted"] == {"rvp_bara": 0.63, "export_kg_hr": 13000.0}

    guard_dir = tmp_path / "guard"
    guard_dir.mkdir()
    ctx = _guard_context(guard_dir, proposal)
    ctx.goal = _objective_context(tmp_path).goal
    ctx.baseline = _objective_context(tmp_path).baseline
    ctx.plan["production"]["require_objective_evidence"] = True
    production.guard(ctx, {})
    assert [row["title"] for row in ctx.proposals] == ["Increase validated field export"]


@pytest.mark.parametrize("failure", ["name", "direction", "unit", "baseline", "accepted", "prediction"])
def test_lifecycle_context_rejects_mismatched_objective_metadata(tmp_path, failure):
    _, evidence, _ = _promotable_schema8_task(tmp_path)
    candidate = _objective_bound_lifecycle(evidence)
    ctx = _objective_context(tmp_path)
    predicted = {}
    optimizer = next(period for period in candidate["periods"]
                     if period["period_id"] == "C-1")["envelope_evidence"]["optimizer_evidence"]
    if failure == "name":
        ctx.goal["objective"]["source_name"] = "different objective"
    elif failure == "direction":
        ctx.goal["objective"]["direction"] = "minimize"
    elif failure == "unit":
        ctx.goal["objective"]["unit"] = "Sm3/day"
    elif failure == "baseline":
        ctx.baseline["kpis"]["export_kg_hr"] = 12699.0
    elif failure == "accepted":
        optimizer["accepted_objective_value"] = float("nan")
    else:
        predicted["export_kg_hr"] = 12999.0

    with pytest.raises(ValueError, match="objective|Objective|baseline|finite"):
        production.proposal_from_lifecycle_context(
            json.dumps(candidate), ctx, "unsafe objective binding", predicted=predicted)


def test_promote_copies_only_guarded_schema8_lifecycle_evidence(tmp_path):
    task, evidence, _ = _promotable_schema8_task(tmp_path)

    promoted = promote(str(task), "C-1", "Engineering reviewer", note="accepted")

    current = json.loads((task / "results.json").read_text(encoding="utf-8"))
    snapshot = json.loads((task / "continuous" / "baseline" / "results_snapshot.json")
                          .read_text(encoding="utf-8"))
    assert promoted["source_cycle"] == "C-1"
    assert current["lifecycle_envelope_evidence"] == evidence
    assert snapshot == current
    assert current["key_results"]["accepted_export_kg_hr"] == 1234.0

    generator = pathlib.Path(__file__).parent / "task_template" / "step3_report" / "generate_report.py"
    completed = subprocess.run([
        sys.executable, str(generator), "--task-dir", str(task), "--no-template",
        "--title", "Promoted ten-well lifecycle",
    ], capture_output=True, text=True, timeout=120)
    assert completed.returncode == 0, completed.stdout + completed.stderr
    report = (task / "step3_report" / "Promoted_ten-well_lifecycle.html").read_text(encoding="utf-8")
    assert "Lifecycle Operating Envelope" in report
    assert "host::separator gas capacity" in report and "40 kg/hr" in report
    assert "host::shared power" in report and "-20 kW" in report
    assert "synthetic ten-well shared host power budget" in report


@pytest.mark.parametrize("failure", ["stale", "unguarded", "ambiguous", "tampered"])
def test_promote_rejects_unqualified_lifecycle_handoff(tmp_path, failure):
    task, _, item_id = _promotable_schema8_task(tmp_path)
    ledger_path = task / "continuous" / "ledger" / "events.jsonl"
    events = Ledger(ledger_path).events()
    if failure == "stale":
        Ledger(ledger_path).invalidate_evidence(item_id, [{"path": "study_config.yaml"}], cycle="C-2")
    elif failure == "unguarded":
        events[0]["guard"] = "not-run"
        ledger_path.write_text("\n".join(json.dumps(event) for event in events) + "\n", encoding="utf-8")
    elif failure == "tampered":
        events[0]["lifecycle_envelope_evidence"]["period_change_diagnostics"][0][
            "added_ranked_constraint_identities"] = [{"classification": "REJECTING"}]
        ledger_path.write_text("\n".join(json.dumps(event) for event in events) + "\n", encoding="utf-8")
    else:
        second = copy.deepcopy(events[0])
        second["event_id"] = "second-guarded-evidence"
        second["id"] = "OPP-0002"
        second["title"] = "different guarded lifecycle"
        second["lifecycle_envelope_evidence"]["lifecycle_study_name"] = "Different study"
        ledger_path.write_text("\n".join(json.dumps(event) for event in events + [second]) + "\n",
                               encoding="utf-8")
    before = (task / "results.json").read_text(encoding="utf-8")

    with pytest.raises(ValueError, match="lifecycle evidence|ambiguous"):
        promote(str(task), "C-1", "Engineering reviewer")

    assert (task / "results.json").read_text(encoding="utf-8") == before


def test_guard_reconstructs_schema2_constraint_summary_fail_closed(tmp_path):
    proposal = _valid_proposal()
    envelope = proposal["accepted_point_envelope_evidence"]
    constraint = {
        "source": "PLANT_CONSTRAINT",
        "qualified_constraint_name": "host::shared power",
        "normalized_utilization": 0.96,
        "normalized_margin": 0.04,
        "current_value": 960.0,
        "applicable_limit": 1000.0,
        "physical_margin": 40.0,
        "physical_unit": "kW",
        "feasible": True,
        "diagnostic": "AVAILABLE",
        "applicable_limit_role": "SHARED_RESOURCE",
        "applicable_limit_provenance": "approved host power budget",
    }
    envelope["slices"][0]["points"][0]["leading_constraint"] = constraint
    _add_empty_constraint_summary_evidence(envelope)
    envelope["cross_slice_constraint_summary"] = production._cross_slice_constraint_summary(
        envelope["slices"], envelope["cross_slice_constraint_summary"])
    ctx = _guard_context(tmp_path, proposal)
    production.guard(ctx, {})
    assert [row["title"] for row in ctx.proposals] == ["candidate"]
    entry = ctx.proposals[0]["accepted_point_envelope_evidence"][
        "cross_slice_constraint_summary"]["ranked_constraints"][0]
    assert entry["classification"] == "NEAR_ACTIVE"
    assert entry["qualified_constraint_name"] == "host::shared power"
    assert entry["physical_unit"] == "kW"

    tampered = _valid_proposal()
    tampered["title"] = "tampered summary"
    tampered_envelope = tampered["accepted_point_envelope_evidence"]
    tampered_envelope["slices"][0]["points"][0]["leading_constraint"] = constraint
    _add_empty_constraint_summary_evidence(tampered_envelope)
    tampered_envelope["cross_slice_constraint_summary"] = production._cross_slice_constraint_summary(
        tampered_envelope["slices"], tampered_envelope["cross_slice_constraint_summary"])
    tampered_envelope["cross_slice_constraint_summary"]["ranked_constraints"][0][
        "minimum_normalized_margin"] = 0.03
    tampered_ctx = _guard_context(tmp_path, tampered)
    production.guard(tampered_ctx, {})
    assert tampered_ctx.proposals == []
    reasons = json.loads((tmp_path / "guard.json").read_text())["withheld"][0]["reasons"]
    assert "envelope_constraint_summary_mismatch" in reasons


def test_boundary_refinement_receipt_recomputes_midpoints_fail_closed():
    sampled_feasible_constraint = {
        "qualified_constraint_name": "separator-capacity",
        "physical_margin": 100.0,
        "physical_unit": "kg/hr",
        "feasible": True,
    }
    sampled_infeasible_constraint = {
        "qualified_constraint_name": "driver-power",
        "physical_margin": -50.0,
        "physical_unit": "kW",
        "feasible": False,
    }
    midpoint_feasible_constraint = {
        "qualified_constraint_name": "driver-power",
        "physical_margin": 20.0,
        "physical_unit": "kW",
        "feasible": True,
    }
    midpoint_infeasible_constraint = {
        "qualified_constraint_name": "driver-power",
        "physical_margin": -10.0,
        "physical_unit": "kW",
        "feasible": False,
    }
    transition = {
        "axis_action_id": "choke-a",
        "action_value_semantics": "CONTINUOUS",
        "kind": "BECAME_INFEASIBLE",
        "from_sequence_index": 0,
        "to_sequence_index": 1,
        "from_axis_value": 700.0,
        "to_axis_value": 900.0,
        "from_feasible": True,
        "to_feasible": False,
        "from_outcome": "FEASIBLE",
        "to_outcome": "OTHER_MODEL_CONSTRAINT_VIOLATED",
    }
    steps = [
        {
            "sequence_index": 0,
            "axis_value": 800.0,
            "candidate_values": [800.0, 400.0],
            "outcome": "FEASIBLE",
            "feasible": True,
            "candidate_simulation_converged": True,
            "candidate_evaluator_feasible": True,
            "baseline_restored": True,
            "baseline_simulation_converged": True,
            "leading_constraint": midpoint_feasible_constraint,
        },
        {
            "sequence_index": 1,
            "axis_value": 850.0,
            "candidate_values": [850.0, 400.0],
            "outcome": "OTHER_MODEL_CONSTRAINT_VIOLATED",
            "feasible": False,
            "candidate_simulation_converged": True,
            "candidate_evaluator_feasible": False,
            "baseline_restored": True,
            "baseline_simulation_converged": True,
            "leading_constraint": midpoint_infeasible_constraint,
        },
    ]
    receipt = {
        "axis_action_id": "choke-a",
        "action_value_semantics": "CONTINUOUS",
        "from_sequence_index": 0,
        "to_sequence_index": 1,
        "sampled_from_axis_value": 700.0,
        "sampled_to_axis_value": 900.0,
        "action_lower_bound": 500.0,
        "action_upper_bound": 1000.0,
        "relative_tolerance": 0.01,
        "absolute_tolerance": 5.0,
        "tolerance_provenance": "synthetic choke tolerance",
        "maximum_evaluations": 2,
        "evaluation_count": 2,
        "feasible_axis_value": 800.0,
        "infeasible_axis_value": 850.0,
        "feasible_boundary_constraint": copy.deepcopy(midpoint_feasible_constraint),
        "infeasible_boundary_constraint": copy.deepcopy(midpoint_infeasible_constraint),
        "bracket_width": 50.0,
        "status": "MAXIMUM_EVALUATIONS_REACHED",
        "converged": False,
        "steps": steps,
        "interpolated": False,
        "advisory": True,
    }
    row = {
        "points": [
            {"leading_constraint": sampled_feasible_constraint},
            {"leading_constraint": sampled_infeasible_constraint},
        ],
        "operating_region_transitions": [transition],
        "maximum_boundary_refinement_evaluations": 2,
        "boundary_refinement_relative_tolerance": 0.01,
        "boundary_refinement_tolerance_provenance": "synthetic choke tolerance",
        "operating_region_boundary_refinements": [receipt],
    }
    assert production._valid_operating_region_boundary_refinements(row)

    changed_midpoint = copy.deepcopy(row)
    changed_midpoint["operating_region_boundary_refinements"][0]["steps"][0]["axis_value"] = 801.0
    assert not production._valid_operating_region_boundary_refinements(changed_midpoint)
    partial_recovery = copy.deepcopy(row)
    partial_recovery["operating_region_boundary_refinements"][0]["steps"][1]["baseline_restored"] = False
    assert not production._valid_operating_region_boundary_refinements(partial_recovery)
    changed_headroom = copy.deepcopy(row)
    changed_headroom["operating_region_boundary_refinements"][0][
        "infeasible_boundary_constraint"]["physical_margin"] = -9.0
    assert not production._valid_operating_region_boundary_refinements(changed_headroom)
    missing_headroom = copy.deepcopy(row)
    del missing_headroom["operating_region_boundary_refinements"][0][
        "feasible_boundary_constraint"]
    assert not production._valid_operating_region_boundary_refinements(missing_headroom)
    assert production._has_boundary_constraint_headroom([row])
    assert not production._has_boundary_constraint_headroom([missing_headroom])


def test_guard_persists_schema4_accepted_physical_state_changes(tmp_path):
    proposal = _valid_proposal()
    _add_empty_operating_region_evidence(proposal["accepted_point_envelope_evidence"])
    proposal["lifecycle_envelope_evidence"] = _valid_state_lifecycle_evidence(
        proposal["accepted_point_envelope_evidence"])
    ctx = _guard_context(tmp_path, proposal)
    ctx.plan["production"]["require_lifecycle_envelope_evidence"] = True
    production.guard(ctx, {})
    assert [row["title"] for row in ctx.proposals] == ["candidate"]
    lifecycle = ctx.proposals[0]["lifecycle_envelope_evidence"]
    assert lifecycle["schema_version"] == 4
    assert lifecycle["periods"][1]["accepted_state_metrics"][0]["value"] == 148.25
    changes = lifecycle["period_change_diagnostics"][0]["accepted_state_changes"]
    assert changes[0] == {
        "metric_id": "reservoir-pressure",
        "unit": "bara",
        "from_value": 150.0,
        "to_value": 148.25,
        "delta": -1.75,
    }


def test_guard_accepts_legacy_schema3_state_evidence(tmp_path):
    proposal = _valid_proposal()
    _add_empty_operating_region_evidence(proposal["accepted_point_envelope_evidence"])
    proposal["lifecycle_envelope_evidence"] = _valid_state_lifecycle_evidence(
        proposal["accepted_point_envelope_evidence"])
    proposal["lifecycle_envelope_evidence"]["schema_version"] = 3
    ctx = _guard_context(tmp_path, proposal)
    ctx.plan["production"]["require_lifecycle_envelope_evidence"] = True
    production.guard(ctx, {})
    assert [row["title"] for row in ctx.proposals] == ["candidate"]


def test_guard_rejects_invalid_schema4_accepted_physical_state(tmp_path):
    variants = []
    for title, mutate in [
        ("non-finite accepted state",
         lambda evidence: evidence["periods"][1]["accepted_state_metrics"][0].update(
             value=math.nan)),
        ("changed accepted state unit",
         lambda evidence: evidence["periods"][1]["accepted_state_metrics"][0].update(
             unit="kPa")),
        ("changed accepted state delta",
         lambda evidence: evidence["period_change_diagnostics"][0][
             "accepted_state_changes"][0].update(delta=-1.0)),
    ]:
        proposal = _valid_proposal()
        proposal["title"] = title
        _add_empty_operating_region_evidence(proposal["accepted_point_envelope_evidence"])
        proposal["lifecycle_envelope_evidence"] = _valid_state_lifecycle_evidence(
            proposal["accepted_point_envelope_evidence"])
        mutate(proposal["lifecycle_envelope_evidence"])
        variants.append(proposal)

    ctx = _guard_context(tmp_path, variants[0])
    ctx.plan["production"]["require_lifecycle_envelope_evidence"] = True
    ctx.proposals.extend(variants[1:])
    production.guard(ctx, {})
    assert ctx.proposals == []
    reasons = {row["title"]: row["reasons"]
               for row in json.loads((tmp_path / "guard.json").read_text())["withheld"]}
    assert "lifecycle_period_1_invalid_accepted_state_metrics" in reasons[
        "non-finite accepted state"]
    assert "lifecycle_period_1_accepted_state_metric_unit_mismatch" in reasons[
        "changed accepted state unit"]
    assert "lifecycle_period_change_diagnostics_mismatch" in reasons[
        "changed accepted state delta"]


def test_guard_accepts_legacy_lifecycle_schema_without_period_diagnostics(tmp_path):
    proposal = _valid_proposal()
    proposal["lifecycle_envelope_evidence"] = _valid_lifecycle_evidence(
        proposal["accepted_point_envelope_evidence"])
    proposal["lifecycle_envelope_evidence"]["schema_version"] = 1
    proposal["lifecycle_envelope_evidence"].pop("period_change_diagnostics")
    ctx = _guard_context(tmp_path, proposal)
    ctx.plan["production"]["require_lifecycle_envelope_evidence"] = True
    production.guard(ctx, {})
    assert [row["title"] for row in ctx.proposals] == ["candidate"]


def test_guard_requires_lifecycle_envelope_when_configured(tmp_path):
    proposal = _valid_proposal()
    ctx = _guard_context(tmp_path, proposal)
    ctx.plan["production"]["require_lifecycle_envelope_evidence"] = True
    production.guard(ctx, {})
    assert ctx.proposals == []
    assert json.loads((tmp_path / "guard.json").read_text())["withheld"][0]["reasons"] == [
        "missing_lifecycle_envelope_evidence"
    ]


def test_guard_rejects_stale_or_internally_inconsistent_lifecycle_envelope(tmp_path):
    variants = []

    stale = _valid_proposal()
    stale["title"] = "stale lifecycle"
    stale["lifecycle_envelope_evidence"] = _valid_lifecycle_evidence(
        stale["accepted_point_envelope_evidence"])
    stale["lifecycle_envelope_evidence"]["cycle_id"] = "C-0"
    variants.append(stale)

    no_current = _valid_proposal()
    no_current["title"] = "missing current period"
    no_current["lifecycle_envelope_evidence"] = _valid_lifecycle_evidence(
        no_current["accepted_point_envelope_evidence"])
    no_current["lifecycle_envelope_evidence"]["periods"][0]["period_id"] = "past-period"
    no_current["lifecycle_envelope_evidence"]["periods"][0]["envelope_evidence"][
        "cycle_id"] = "past-period"
    no_current["lifecycle_envelope_evidence"]["periods"][0]["envelope_evidence"][
        "optimizer_evidence"]["cycle_id"] = "past-period"
    variants.append(no_current)

    changed_current = _valid_proposal()
    changed_current["title"] = "changed current period"
    changed_current["lifecycle_envelope_evidence"] = _valid_lifecycle_evidence(
        changed_current["accepted_point_envelope_evidence"])
    changed_current["lifecycle_envelope_evidence"]["periods"][0]["envelope_evidence"][
        "accepted_candidate_values"] = [0.6]
    variants.append(changed_current)

    unordered = _valid_proposal()
    unordered["title"] = "unordered periods"
    unordered["lifecycle_envelope_evidence"] = _valid_lifecycle_evidence(
        unordered["accepted_point_envelope_evidence"])
    unordered["lifecycle_envelope_evidence"]["periods"][1]["elapsed_time"] = 0.0
    variants.append(unordered)

    partial = _valid_proposal()
    partial["title"] = "partial future recovery"
    partial["lifecycle_envelope_evidence"] = _valid_lifecycle_evidence(
        partial["accepted_point_envelope_evidence"])
    partial["lifecycle_envelope_evidence"]["periods"][1]["envelope_evidence"][
        "slices"][0]["points"][0]["baseline_restored"] = False
    variants.append(partial)

    infeasible_future = _valid_proposal()
    infeasible_future["title"] = "infeasible future period"
    infeasible_future["lifecycle_envelope_evidence"] = _valid_lifecycle_evidence(
        infeasible_future["accepted_point_envelope_evidence"])
    infeasible_future["lifecycle_envelope_evidence"]["periods"][1]["envelope_evidence"][
        "optimizer_evidence"]["candidate_feasible"] = False
    variants.append(infeasible_future)

    aggregate = _valid_proposal()
    aggregate["title"] = "aggregate mismatch"
    aggregate["lifecycle_envelope_evidence"] = _valid_lifecycle_evidence(
        aggregate["accepted_point_envelope_evidence"])
    aggregate["lifecycle_envelope_evidence"]["total_evaluation_count"] = 25
    variants.append(aggregate)

    transition = _valid_proposal()
    transition["title"] = "transition mismatch"
    transition["lifecycle_envelope_evidence"] = _valid_lifecycle_evidence(
        transition["accepted_point_envelope_evidence"])
    transition["lifecycle_envelope_evidence"]["bottleneck_transitions"] = [{}]
    variants.append(transition)

    period_change = _valid_proposal()
    period_change["title"] = "period change mismatch"
    period_change["lifecycle_envelope_evidence"] = _valid_lifecycle_evidence(
        period_change["accepted_point_envelope_evidence"])
    period_change["lifecycle_envelope_evidence"]["period_change_diagnostics"][0][
        "slice_changes"][0]["became_infeasible_count"] = 1
    variants.append(period_change)

    operating_region_change = _valid_proposal()
    operating_region_change["title"] = "operating region migration mismatch"
    _add_empty_operating_region_evidence(
        operating_region_change["accepted_point_envelope_evidence"])
    operating_region_change["lifecycle_envelope_evidence"] = _valid_region_lifecycle_evidence(
        operating_region_change["accepted_point_envelope_evidence"])
    operating_region_change["lifecycle_envelope_evidence"]["period_change_diagnostics"][0][
        "slice_changes"][0]["added_operating_region_transitions"].append({
            "kind": "BECAME_INFEASIBLE"
        })
    variants.append(operating_region_change)

    ctx = _guard_context(tmp_path, variants[0])
    ctx.plan["production"]["require_envelope_evidence"] = True
    ctx.plan["production"]["require_lifecycle_envelope_evidence"] = True
    ctx.proposals.extend(variants[1:])
    production.guard(ctx, {})
    assert ctx.proposals == []
    reasons = {row["title"]: row["reasons"]
               for row in json.loads((tmp_path / "guard.json").read_text())["withheld"]}
    assert "stale_lifecycle_envelope_evidence" in reasons["stale lifecycle"]
    assert "missing_current_lifecycle_period" in reasons["missing current period"]
    assert "lifecycle_current_period_envelope_mismatch" in reasons["changed current period"]
    assert "lifecycle_period_1_invalid_elapsed_time" in reasons["unordered periods"]
    assert "lifecycle_period_1_envelope_slice_0_point_recovery_incomplete" in reasons[
        "partial future recovery"]
    assert "lifecycle_period_1_infeasible_candidate" in reasons["infeasible future period"]
    assert "lifecycle_aggregate_count_mismatch" in reasons["aggregate mismatch"]
    assert "lifecycle_bottleneck_transitions_mismatch" in reasons["transition mismatch"]
    assert "lifecycle_period_change_diagnostics_mismatch" in reasons["period change mismatch"]
    assert "lifecycle_period_change_diagnostics_mismatch" in reasons[
        "operating region migration mismatch"]


@pytest.mark.parametrize("field, value, reason", [
    ("evaluation_count", 0, "invalid_evaluation_count"),
    ("evaluation_count", 1.5, "invalid_evaluation_count"),
    ("runtime_seconds", math.nan, "invalid_runtime_seconds"),
    ("runtime_seconds", -0.1, "invalid_runtime_seconds"),
])
def test_guard_rejects_invalid_performance_evidence(tmp_path, field, value, reason):
    proposal = _valid_proposal()
    proposal["optimizer_evidence"][field] = value
    ctx = _guard_context(tmp_path, proposal)
    production.guard(ctx, {})
    assert ctx.proposals == []
    assert reason in json.loads((tmp_path / "guard.json").read_text())["withheld"][0]["reasons"]


@pytest.mark.parametrize("field, value, reason", [
    ("setpoint", math.nan, "non_finite_setpoint:p3_barg"),
    ("setpoint", math.inf, "non_finite_setpoint:p3_barg"),
    ("prediction", math.nan, "non_finite_prediction:rvp_bara"),
    ("gain", math.inf, "gain_not_above_0.0"),
    ("baseline", -math.inf, "non_finite_baseline"),
])
def test_guard_rejects_non_finite_candidate_data(tmp_path, field, value, reason):
    proposal = _valid_proposal()
    if field == "setpoint":
        proposal["setpoints"]["p3_barg"] = value
    elif field == "prediction":
        proposal["predicted"]["rvp_bara"] = value
    elif field == "gain":
        proposal["expected_gain"]["value"] = value
    else:
        proposal["baseline_value"] = value
    ctx = _guard_context(tmp_path, proposal)
    production.guard(ctx, {})
    assert ctx.proposals == []
    assert reason in json.loads((tmp_path / "guard.json").read_text())["withheld"][0]["reasons"]


def test_guard_rejects_invalid_limit_and_minimum_gain(tmp_path):
    proposal = _valid_proposal()
    ctx = _guard_context(tmp_path, proposal)
    ctx.plan["production"]["min_gain"] = math.nan
    ctx.goal["constraints"][0] = dict(RVP, margin=math.inf)
    assert production.effective_limit(ctx.goal["constraints"][0]) is None
    production.guard(ctx, {})
    assert ctx.proposals == []
    assert json.loads((tmp_path / "guard.json").read_text())["withheld"][0]["reasons"] == [
        "invalid_min_gain"
    ]


def test_failed_gate_withholds_everything(tmp_path):
    task = _task(tmp_path, resid=5.0)
    manifest = run_cycle(task, no_agent=True)
    guard = _guard(task, manifest)
    assert guard["accepted"] == [] and "model_gate:resid" in guard["blocks"]
    assert all("model_gate:resid" in w["reasons"] for w in guard["withheld"])
    assert "model_gate:resid" in manifest["triggers"]


def test_unconfirmed_hard_constraint_withholds_advice(tmp_path):
    task = _task(tmp_path, limit=None)
    guard = _guard(task, run_cycle(task, no_agent=True))
    assert guard["accepted"] == [] and "constraint_unconfirmed:rvp" in guard["blocks"]


def test_breach_of_current_operation_is_a_trigger(tmp_path):
    task = _task(tmp_path, limit=0.60)
    manifest = run_cycle(task, no_agent=True)
    assert "constraint_breach:rvp" in manifest["triggers"]


def test_outcome_compares_realised_with_predicted_gain(tmp_path):
    task = _task(tmp_path)
    book = Ledger(os.path.join(task, "continuous", "ledger", "events.jsonl"))
    key = book.create("applied", "operational", expected_gain={"value": 400.0}, objective_kpi="oil_Sm3d", baseline_value=12700.0)["id"]
    book.set_status(key, "accepted", by="eng")
    book.set_status(key, "implemented", by="eng")
    manifest = run_cycle(task, no_agent=True)
    assert "outcome_confirmed:" + key in manifest["triggers"]  # 13000 - 12700 = 300 >= 50 % of 400


def test_user_input_effects_and_triggers(tmp_path):
    from neqsim_continuous import user_input

    task = pathlib.Path(_task(tmp_path, limit=None))
    plan_path = task / "continuous" / "cycle_plan.yaml"
    plan = yaml.safe_load(plan_path.read_text())
    plan["stages"].insert(0, "inputs")
    plan_path.write_text(yaml.safe_dump(plan))
    assert (task / "continuous" / "user_input.yaml").exists()

    # No limit set: every proposal is withheld by the unconfirmed hard constraint.
    first = run_cycle(str(task), now=None, no_agent=True)
    guard = json.loads((task / "continuous" / "cycles" / first["cycle_id"] / "guard.json").read_text())
    assert guard["accepted"] == [] and "constraint_unconfirmed:rvp" in guard["blocks"]

    # The engineer supplies the limit and a lever restriction; the next cycle acts on it.
    user_input.add(str(task), "Spec is 0.70 bara RVP per the lab, keep the 3rd stage as is.", by="eng",
                   effects=[{"kind": "constraint", "name": "rvp", "limit": 0.70, "margin": 0.05},
                            {"kind": "lever_freeze", "lever": "3rd stage"},
                            {"kind": "setting", "key": "production.rvp_bias_bara", "value": 0.02}])
    second = run_cycle(str(task), now=None, no_agent=True)
    cycle = task / "continuous" / "cycles" / second["cycle_id"]
    guard = json.loads((cycle / "guard.json").read_text())
    assert guard["accepted"] == ["safe"] and "user_input:U-001" in second["triggers"]
    applied = json.loads((cycle / "user_input.json").read_text())["applied"]["U-001"]
    assert set(applied) == {"constraint:rvp", "lever_freeze:3rd stage", "setting:production.rvp_bias_bara"}
    assert "User input in force" in (cycle / "digest.md").read_text()

    # Unchanged entry: no repeated trigger. Resolved entry: effects stop applying.
    third = run_cycle(str(task), now=None, no_agent=True)
    assert "user_input:U-001" not in third["triggers"]
    user_input.resolve(str(task), "U-001", by="eng")
    fourth = run_cycle(str(task), now=None, no_agent=True)
    guard = json.loads((task / "continuous" / "cycles" / fourth["cycle_id"] / "guard.json").read_text())
    assert guard["accepted"] == [] and "constraint_unconfirmed:rvp" in guard["blocks"]


def test_user_input_lever_limits_and_expiry(tmp_path):
    from datetime import datetime, timezone

    from neqsim_continuous import user_input

    class Ctx(object):
        user_levers = {}

    ctx = Ctx()
    ctx.user_levers = {"20b-va01": {"lo": 28.0, "hi": 30.0}, "vigdis hp wells": {"frozen": True}}
    assert nc.lever_limits(ctx, "20B-VA01", 27.0, 31.0) == (28.0, 30.0, False)
    assert nc.lever_limits(ctx, "Vigdis HP wells", -5, 10) == (-5, 10, True)
    assert nc.lever_limits(ctx, "other", 1, 2) == (1, 2, False)

    task = tmp_path / "x"
    (task / "continuous").mkdir(parents=True)
    entry = user_input.add(str(task), "temporary", by="eng", expires="2026-01-01")
    assert entry["id"] == "U-001" and user_input.add(str(task), "second")["id"] == "U-002"
    assert user_input._expired(entry, datetime(2026, 2, 1, tzinfo=timezone.utc).date())
    with pytest.raises(ValueError):
        user_input.add(str(task), "bad", effects=[{"kind": "nonsense"}])
