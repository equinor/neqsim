"""Evaluate production quantities, expose blinded cases, and score a submitted decision."""

import argparse
import json
import math
from pathlib import Path


# Illustrative limits for this synthetic host. Positive margins are feasible.
LIMITS = {"power_kW": ("max", 600.), "level_m": ("max", 1.12),
          "interface_m": ("max", .32), "screening_surge_margin": ("min", .10)}
# Absolute changes allowed when refining one coordinate at a time; engineering
# screening criteria for these outputs, not solver convergence tolerances.
TOLERANCES = {"well_production_kg": 2., "power_kW": 12., "level_m": .005,
              "interface_m": .005, "screening_surge_margin": .03, "gas_export_kg_s": .15,
              "riser_oil_kg_s": .5, "riser_water_kg_s": .3, "riser_gas_kg_s": .3,
              "oil_out_kg_s": .3, "separator_bara": .2, "post_restart_oil_peak_kg_s": .5}
HOST = {"power_kW", "screening_surge_margin", "gas_export_kg_s", "host_energy_residual_W"}
GRIDS = ((1, .25), (2, .25), (1, .125))


def quantities(run):
    config = run["configuration"]
    rows = run["time_series"]
    dt = config["dt"]
    duration = config["duration"]
    if not rows or len(rows) != round(duration / dt):
        raise ValueError("Incomplete trajectory")
    for index, row in enumerate(rows, 1):
        if row["end_s"] != index * dt:
            raise ValueError("Missing, duplicated or unordered physical step")
        required = set(TOLERANCES) - {"well_production_kg", "post_restart_oil_peak_kg_s"} - HOST
        if index % round(1 / dt) == 0:
            required |= HOST
        if any(not math.isfinite(row[key]) for key in required):
            raise ValueError("Missing finite engineering quantity")
        if not 0 < row["interface_m"] < row["level_m"] < 2.2:
            raise ValueError("Invalid vessel inventory geometry")
    out = {"well_production_kg": run["well_production_kg"]}
    for name in ("max_phase_residual_kg", "max_component_residual_kg"):
        if not 0 <= run[name] <= 1e-7:
            raise ValueError(f"Invalid or unresolved inventory residual: {name}")
    if not math.isfinite(out["well_production_kg"]) or out["well_production_kg"] <= 0:
        raise ValueError("Invalid well production")
    post_restart = [r["riser_oil_kg_s"] for r in rows if r["end_s"] > 12.]
    if not post_restart:
        raise ValueError("Trajectory ends before the second well restarts")
    out["post_restart_oil_peak_kg_s"] = max(post_restart)
    for key in TOLERANCES.keys() - out.keys():
        samples = [r[key] for r in rows if key in r]
        out[key] = min(samples) if key == "screening_surge_margin" else max(samples)
    return out


def run_basis(config):
    """Keep solver revision, horizon, energy owner and equipment rating basis together."""
    return (config["revision"], config["duration"], bool(config.get("energy_audit")),
            config.get("basis", "matched"), config.get("handoff_protocol", "legacy"),
            config.get("event_id", "synthetic-two-well-restart"), config.get("chart_sha256"))


def run_result(run):
    """Preserve a rejected trajectory as negative evidence at its own discretization."""
    if run.get("status") == "rejected":
        rejection = {"rejection": run["reason"]}
        for name in ("outlet_diagnostic", "host_diagnostic"):
            if name in run:
                rejection[name] = run[name]
        return rejection
    return quantities(run)


def constraint_breaches(fluid, grid, values, power_limit):
    """Return observed constraint breaches for a completed trajectory at one grid."""
    mesh, dt = grid
    breaches = []
    for name, (direction, limit) in LIMITS.items():
        if name == "power_kW":
            limit = power_limit
        value = values[name]
        if (direction == "max" and value > limit) or (direction == "min" and value < limit):
            breaches.append({"fluid": fluid, "mesh_cells": 60 * mesh, "dt_s": dt, "quantity": name,
                             "value": value, "direction": direction, "limit": limit})
    return breaches


def assess(runs):
    """Compare identical scenarios and retain provenance for each decision quantity."""
    groups = {}
    conditions = set()
    for run in runs:
        c = run["configuration"]
        conditions.add(run_basis(c))
        if c["policy"] not in ("fixed", "ramp") or (c["mesh"], c["dt"]) not in GRIDS:
            raise ValueError("Unknown replay configuration")
        fluid = c.get("fluid_deck_sha256", c["eos"])
        model = groups.setdefault(fluid, {}).setdefault(c["policy"], {})
        key = c["mesh"], c["dt"]
        if key in model:
            raise ValueError("Duplicate configuration")
        model[key] = (run_result(run), run)
    if len(conditions) != 1 or not groups:
        raise ValueError("Provide runs with one source revision and study duration")
    result = {}
    for fluid, policies in sorted(groups.items()):
        for policy in ("fixed", "ramp"):
            runs_by_grid = policies.get(policy, {})
            result.setdefault(fluid, {})
            rejections = []
            breaches = []
            for grid in GRIDS:
                if grid not in runs_by_grid:
                    continue
                case, _ = runs_by_grid[grid]
                if "rejection" in case:
                    mesh, dt = grid
                    details = {key: value for key, value in case.items() if key != "rejection"}
                    rejections.append({"mesh_cells": 60 * mesh, "dt_s": dt, "reason": case["rejection"], **details})
                else:
                    breaches.extend(constraint_breaches(fluid, grid, case, LIMITS["power_kW"][1]))
            if len(runs_by_grid) != len(GRIDS) or rejections:
                status = "missing_refinement" if len(runs_by_grid) != len(GRIDS) else "rejected_trajectory"
                result[fluid][policy] = {"status": status, "available": len(runs_by_grid), "rejections": rejections,
                                         "violations": sorted({b["quantity"] for b in breaches}),
                                         "violation_evidence": breaches}
                continue
            primary, fine_mesh, fine_step = [runs_by_grid[key][0] for key in GRIDS]
            quantities_result, violations = {}, []
            for key, tolerance in TOLERANCES.items():
                values = [primary[key], fine_mesh[key], fine_step[key]]
                mesh_change, step_change = values[1] - values[0], values[2] - values[0]
                q = {"values": values, "mesh_change": mesh_change, "timestep_change": step_change,
                     "allowed_change": tolerance,
                     "within_refinement_limit": max(abs(mesh_change), abs(step_change)) <= tolerance}
                if key in LIMITS:
                    side, limit = LIMITS[key]
                    margin = limit - max(values) if side == "max" else min(values) - limit
                    q.update({"constraint": side, "limit": limit, "worst_margin": margin})
                    if margin < 0:
                        violations.append(key)
                quantities_result[key] = q
            records = [v[1] for v in runs_by_grid.values()]
            density = [r.get("density_evidence") for r in records]
            if len({r["file_sha256"] if r else None for r in density}) > 1:
                raise ValueError("Mismatched laboratory evidence across refinement runs")
            density_status = ("unavailable" if density[0] is None else
                              "accepted_density_rows" if all(r["all_density_rows_accepted"] for r in density)
                              else "outside_density_tolerance")
            if density_status == "outside_density_tolerance":
                violations.append("measured_fluid_density")

            result[fluid][policy] = {
                "status": "screening", "density_status": density_status,
                "independent_density_evidence": density[0], "quantities": quantities_result, "violations": violations,
                "sensitive_quantities": [k for k, q in quantities_result.items() if not q["within_refinement_limit"]],
                "max_phase_residual_kg": max(r["max_phase_residual_kg"] for r in records),
                "max_component_residual_kg": max(r["max_component_residual_kg"] for r in records),
                "max_host_energy_residual_W": max(abs(row["host_energy_residual_W"]) for r in records
                                                  for row in r["time_series"] if "host_energy_residual_W" in row),
                "missing_scale_interactions": sorted({k for r in records for k in r["missing_barite_interactions"]})}
    verdicts = {}
    for fluid, policies in result.items():
        if any(p["status"] != "screening" for p in policies.values()):
            verdicts[fluid] = "incomplete"
            continue
        feasible = [name for name, p in policies.items() if not p["violations"]]
        if not feasible:
            verdicts[fluid] = "no_feasible_trajectory"
        elif any(policies[k]["sensitive_quantities"] for k in feasible):
            verdicts[fluid] = "refine_before_ranking"
        elif len(feasible) == 1:
            verdicts[fluid] = feasible[0]
        else:
            gains = [r - f for r, f in zip(policies["ramp"]["quantities"]["well_production_kg"]["values"],
                                           policies["fixed"]["quantities"]["well_production_kg"]["values"])]
            tol = TOLERANCES["well_production_kg"]
            verdicts[fluid] = "ramp" if min(gains) > tol else "fixed" if max(gains) < -tol else "unresolved"
    revision, duration, energy_audit, basis, handoff, event_id, chart_digest = next(iter(conditions))
    return {"source_revision": revision, "duration_s": duration, "equipment_basis": basis, "handoff_protocol": handoff, "event_id": event_id,
            "compressor_chart_sha256": chart_digest,
            "boundary": "sampled pressure-fed IPR / live separator plus lagged choke drop",
            "conservative_energy_mode": energy_audit,
            "grids_cells_dt_s": [[60 * m, dt] for m, dt in GRIDS],
            "fallback": None, "independent_restart_benchmark": None, "facility_energy_residual_J": None,
            "fluid_results": result, "screening_decisions": verdicts, "field_recommendation": "withhold",
            "needed": ["paired restart pressures and phase-rate/level histories",
                       "sample-matched laboratory PVT and installed compressor surge map",
                       "facility component/energy closure and resolved spatial sensitivity"]}


def challenge(runs, limit_kw, reveal=False, surge_map_available=True):
    """Coarse evidence is visible; independent discretizations are withheld for scoring."""
    if not math.isfinite(limit_kw) or limit_kw <= 0:
        raise ValueError("A positive finite installed power limit is required")
    cases = {}
    for run in runs:
        c = run["configuration"]
        key = c.get("fluid_deck_sha256", c["eos"]), c["policy"]
        if (c["mesh"], c["dt"]) in GRIDS:
            grid = (c["mesh"], c["dt"])
            records = cases.setdefault(key, {})
            if grid in records:
                raise ValueError("Duplicate challenge run")
            records[grid] = run_result(run)
    if not cases or any(set(v) != set(GRIDS) for v in cases.values()):
        raise ValueError("Blind evaluation requires the full matched set")
    if len({run_basis(r["configuration"]) for r in runs}) != 1:
        raise ValueError("Mixed provenance or duration")
    if any((fluid, policy) not in cases for fluid, _ in cases for policy in ("fixed", "ramp")):
        raise ValueError("Both policies required for every fluid")
    visible = {f"{fluid}/{policy}": rows[GRIDS[0]] for (fluid, policy), rows in cases.items()}
    if not surge_map_available:
        for output in visible.values():
            output.pop("screening_surge_margin", None)
    origin = runs[0]["configuration"].get("chart_origin")
    surge_basis = (origin + " speed-line endpoints") if origin else "synthetic screening curve"
    prompt = {"installed_surge_evidence": surge_basis if surge_map_available else "missing",
              "power_limit_kW": limit_kw, "coarse_results": visible,
              "question": "Select fixed or ramp for all fluids, or withhold. Maximize worst-case integrated well production subject to all four limits.",
              "other_limits": {k: v for k, v in LIMITS.items() if k != "power_kW"}}
    if not reveal:
        return prompt
    outcome = {}
    for policy in ("fixed", "ramp"):
        held = [(fluid, grid, row) for (fluid, name), records in cases.items() if name == policy
                for grid, row in records.items() if grid != GRIDS[0] and "rejection" not in row]
        breaches = []
        for fluid, grid, row in held:
            breaches.extend(constraint_breaches(fluid, grid, row, limit_kw))
        result = {"violations": sorted({b["quantity"] for b in breaches}), "violation_evidence": breaches}
        outcome[policy] = result
        rejected = [(grid, row["rejection"]) for (_, name), records in cases.items() if name == policy
                    for grid, row in records.items() if "rejection" in row]
        if rejected:
            missing = []
            if any(grid == GRIDS[0] for grid, _ in rejected):
                missing.append("accepted coarse trajectory")
            if any(grid != GRIDS[0] for grid, _ in rejected):
                missing.append("accepted refined trajectory")
            result.update(missing_evidence=missing, rejections=[reason for _, reason in rejected],
                          worst_production_kg=None, power_prediction_error_kW=None)
            continue
        power_errors = [abs(rows[grid]["power_kW"] - rows[GRIDS[0]]["power_kW"])
                        for (_, name), rows in cases.items() if name == policy and "rejection" not in rows[GRIDS[0]]
                        for grid in GRIDS[1:]]
        result.update(power_prediction_error_kW=max(power_errors, default=None),
                      worst_production_kg=min(row["well_production_kg"] for _, _, row in held))
    if not surge_map_available:
        for item in outcome.values():
            item.setdefault("missing_evidence", []).append("suction-flow surge limit at operating head and speed")
    return outcome


def score(runs, limit_kw, choice, surge_map_available=True):
    if choice not in ("withhold", "fixed", "ramp"):
        raise ValueError("Unknown decision")
    oracle = challenge(runs, limit_kw, True, surge_map_available)
    feasible = [k for k, v in oracle.items() if not v["violations"] and not v.get("missing_evidence")]
    best = max((oracle[k]["worst_production_kg"] for k in feasible), default=None)
    return {"choice": choice, "held_out": oracle, "violations": oracle.get(choice, {}).get("violations", []),
            "appropriate_abstention": choice == "withhold" and not feasible,
            "production_regret_kg": (best - oracle[choice]["worst_production_kg"] if choice in feasible else None)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("receipts", nargs="+", type=Path)
    parser.add_argument("--mode", choices=["assess", "challenge", "score"], default="assess")
    parser.add_argument("--limit-kw", type=float, default=600.)
    parser.add_argument("--missing-surge-map", action="store_true")
    parser.add_argument("--choice", choices=["withhold", "fixed", "ramp"])
    args = parser.parse_args()
    cases = [json.loads(path.read_text()) for path in args.receipts]
    if args.mode == "assess":
        result = assess(cases)
    elif args.mode == "challenge":
        result = challenge(cases, args.limit_kw, surge_map_available=not args.missing_surge_map)
    else:
        result = score(cases, args.limit_kw, args.choice, not args.missing_surge_map)
    print(json.dumps(result, indent=2, allow_nan=False))
