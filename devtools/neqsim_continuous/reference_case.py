"""Public reference case: a synthetic compressor station with injected faults.

It lets anyone (no enterprise access, no Java) run and test the whole living-task loop:
monitor cycles, drift detection, backtesting against known fault dates, the ledger and a
solve loop. The "model" is the ideal-gas polytropic relation, so the case runs with the
Python standard library only.

Injected faults (days after ``start``):

* ``fouling_day``: polytropic efficiency declines linearly by ``fouling_drop`` over 90 days;
* ``meter_bias_day``: flow meter B reads ``meter_bias`` (fraction) high;
* ``pressure_step_day``: suction pressure drops by ``pressure_step`` bar.
"""

import csv
import json
import math
import os
import random
from datetime import datetime, timedelta, timezone

K = 1.28

STATION_MODEL = '''"""Stage script of the reference case: ideal-gas polytropic efficiency and meter mismatch."""
import math

K = 1.28


def run(ctx):
    eta, mismatch = [], []
    for row in ctx.new_rows.get("station", []):
        try:
            pressure_ratio = row["discharge_pressure_bara"] / row["suction_pressure_bara"]
            temperature_ratio = (row["discharge_temperature_C"] + 273.15) / (row["suction_temperature_C"] + 273.15)
            eta.append((K - 1.0) / K * math.log(pressure_ratio) / math.log(temperature_ratio))
            mismatch.append(100.0 * (row["flow_b_kg_s"] - row["flow_a_kg_s"]) / row["flow_a_kg_s"])
        except (KeyError, TypeError, ValueError, ZeroDivisionError):
            continue
    kpis = {}
    if eta:
        kpis["polytropic_efficiency"] = sum(eta) / len(eta)
    if mismatch:
        kpis["flow_meter_mismatch_pct"] = sum(mismatch) / len(mismatch)
    proposals = []
    if eta and kpis["polytropic_efficiency"] < 0.785:
        proposals.append({"title": "Wash or inspect the compressor (efficiency below 0.785)",
                          "category": "maintenance", "kpi": "polytropic_efficiency",
                          "value": {"metric": "polytropic_efficiency", "value": kpis["polytropic_efficiency"]}})
    return {"kpis": kpis, "proposals": proposals}
'''

SOLVER = '''"""Solver stage of the reference case: lower the suction temperature towards the dew-point limit.

Compression power at fixed pressure ratio is proportional to the absolute suction temperature,
so the power reduction from cooling is (T_base - T) / T_base. The dew-point margin sets a
lower bound of 22 C, which bounds the reachable reduction.
"""
import json
import os

T_BASE, T_MIN = 303.15, 295.15


def run(ctx):
    path = os.path.join(ctx.state_dir, "solver_state.json")
    state = json.load(open(path)) if os.path.exists(path) else {"T": T_BASE}
    state["T"] = state["T"] - 0.5 * (state["T"] - T_MIN)
    json.dump(state, open(path, "w"))
    value = 100.0 * (T_BASE - state["T"]) / T_BASE
    bound = 100.0 * (T_BASE - T_MIN) / T_BASE
    remaining = bound - value
    return {"kpis": {"power_reduction_pct": value},
            "solve": {"value": value, "validated": True, "confidence": "high",
                      "action": "lower suction temperature to {:.2f} K".format(state["T"]),
                      "upper_bound": bound,
                      "candidates": [{"action": "lower suction temperature further", "branch": "cooling",
                                      "p_success": 0.9, "predicted_delta": 0.5 * remaining}]}}
'''

BRIEF = """# Task brief: reference compressor station

## 1. Goal
Keep the station efficiency and meters under watch, and reduce compression power by cooling the suction gas.

## 2. Success measure
Power reduction of at least 2.0 % at unchanged pressure ratio, validated by the station model.

## 4. Constraints
Suction temperature not below 22 C (dew-point margin).

## 8. Stop rules
Goal met, or less than 2 % relative gain over three rounds.
"""


def generate_data(path, start, days=365, seed=7, fouling_day=120, fouling_drop=0.03,
                  meter_bias_day=200, meter_bias=0.04, pressure_step_day=280, pressure_step=3.0):
    """Write the hourly synthetic historian export and return the fault onset times."""
    rng = random.Random(seed)
    with open(path, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["timestamp", "suction_pressure_bara", "discharge_pressure_bara",
                         "suction_temperature_C", "discharge_temperature_C", "flow_a_kg_s", "flow_b_kg_s"])
        for hour in range(days * 24):
            day = hour / 24.0
            stamp = start + timedelta(hours=hour)
            eta = 0.80 - fouling_drop * min(max(day - fouling_day, 0.0) / 90.0, 1.0)
            p1 = 30.0 - (pressure_step if day >= pressure_step_day else 0.0)
            p2 = 90.0
            t1 = 303.15
            t2 = t1 * (p2 / p1) ** ((K - 1.0) / (K * eta))
            flow = 100.0 + rng.gauss(0.0, 0.5)
            bias = 1.0 + (meter_bias if day >= meter_bias_day else 0.0)
            writer.writerow([stamp.isoformat(), round(p1 + rng.gauss(0, 0.05), 4), round(p2 + rng.gauss(0, 0.05), 4),
                             round(t1 - 273.15 + rng.gauss(0, 0.1), 4), round(t2 - 273.15 + rng.gauss(0, 0.1), 4),
                             round(flow * (1 + rng.gauss(0, 0.002)), 4),
                             round(flow * bias * (1 + rng.gauss(0, 0.002)), 4)])
    return {"fouling": start + timedelta(days=fouling_day),
            "meter_bias": start + timedelta(days=meter_bias_day),
            "pressure_step": start + timedelta(days=pressure_step_day)}


def create_reference_task(root, name="reference_compressor_station", start=None, days=365, seed=7):
    """Create a complete living reference task under ``root`` and return its folder."""
    import yaml
    start = start or datetime(2025, 10, 1, tzinfo=timezone.utc)
    task = os.path.join(str(root), name)
    for sub in ("data_drop", "continuous/stages", "continuous/baseline", "continuous/ledger",
                "continuous/cycles", "step1_scope_and_research/references/manual"):
        os.makedirs(os.path.join(task, sub), exist_ok=True)
    onsets = generate_data(os.path.join(task, "data_drop", "station_hourly.csv"), start, days, seed)
    brief = os.path.join(task, "step1_scope_and_research", "references", "manual", "brief.md")
    with open(brief, "w", encoding="utf-8") as f:
        f.write(BRIEF)
    with open(os.path.join(task, "continuous", "stages", "station_model.py"), "w", encoding="utf-8") as f:
        f.write(STATION_MODEL)
    with open(os.path.join(task, "continuous", "stages", "solver.py"), "w", encoding="utf-8") as f:
        f.write(SOLVER)
    with open(os.path.join(task, "study_config.yaml"), "w", encoding="utf-8") as f:
        f.write('study:\n  title: "Reference compressor station"\n  task_type: "B"\n'
                'inputs:\n  prompt_file: "step1_scope_and_research/references/manual/brief.md"\n'
                "continuous:\n  enabled: true\n  plan: continuous/cycle_plan.yaml\n")
    with open(os.path.join(task, "results.json"), "w", encoding="utf-8") as f:
        json.dump({"key_results": {"polytropic_efficiency": 0.80, "suction_pressure_bara": 30.0,
                                   "flow_meter_mismatch_pct": 0.0, "power_reduction_pct": 0.0},
                   "recommendations": ["Monitor polytropic efficiency weekly and wash when it falls 1.5 points."]},
                  f, indent=2)
    plan = {
        "schema_version": "1.0",
        "sources": {"station": {"adapter": "file", "initial_lookback_days": 1, "stale_after_hours": 48,
                                "options": {"folder": "data_drop", "time_column": "timestamp",
                                            "pattern": "*.csv"}}},
        "scripts": {"station_model": {"file": "continuous/stages/station_model.py", "function": "run"},
                    "solver": {"file": "continuous/stages/solver.py", "function": "run"}},
        "kpis": {"suction_pressure_bara": {"source": "station", "column": "suction_pressure_bara", "agg": "mean"}},
        "drift": {"signals": ["polytropic_efficiency", "flow_meter_mismatch_pct", "suction_pressure_bara"],
                  "settings": {"lambda": 0.2, "L": 3.5, "k": 0.5, "h": 6.0, "warmup": 30, "confirm": 2,
                               # Engineering floors: smaller shifts are real but not worth a review.
                               "signals": {"polytropic_efficiency": {"min_sigma": 0.002},
                                           "flow_meter_mismatch_pct": {"min_sigma": 0.5},
                                           "suction_pressure_bara": {"min_sigma": 0.2}}}},
        "triggers": {"kpi_step": {"suction_pressure_bara": 2.0},
                     "criteria": {"polytropic_efficiency": "< 0.785"}},
        "stages": ["sense", "refresh", "script:station_model", "kpis", "drift", "goal", "diff",
                   "ledger", "digest", "notify", "agent"],
        "notify": {"channels": ["file"]},
        "agent": {"enabled": False},
        "solve": {"stages": ["sense", "script:solver", "goal", "diff", "ledger", "digest"],
                  "max_rounds": 12, "max_branches": 3},
        "backtest": {"expected": [
            {"trigger": "drift:polytropic_efficiency", "onset": onsets["fouling"].isoformat(), "max_delay_days": 45},
            {"trigger": "drift:flow_meter_mismatch_pct", "onset": onsets["meter_bias"].isoformat(), "max_delay_days": 5},
            {"trigger": "drift:suction_pressure_bara", "onset": onsets["pressure_step"].isoformat(), "max_delay_days": 5}]},
    }
    with open(os.path.join(task, "continuous", "cycle_plan.yaml"), "w", encoding="utf-8") as f:
        yaml.safe_dump(plan, f, sort_keys=False)
    from .plan import file_sha256
    goal = {"schema_version": "1.0", "brief": "step1_scope_and_research/references/manual/brief.md",
            "brief_sha256": file_sha256(brief), "confirmed_by": "reference case",
            "objective": {"metric": "power_reduction_pct", "direction": "maximize", "target": 2.0,
                          "confidence_required": "medium", "source_section": "2. Success measure"},
            "constraints": ["suction temperature >= 22 C"],
            "stop": {"converged": {"window": 3, "relative": 0.02, "absolute": 0.01},
                     "budget": {"iterations": 12, "agent_sessions": 2},
                     "expected_improvement_cost": 0.01},
            "reopen": {"regress_margin": 0.5, "on": ["new_evidence", "brief_changed"]}}
    with open(os.path.join(task, "continuous", "goal.yaml"), "w", encoding="utf-8") as f:
        yaml.safe_dump(goal, f, sort_keys=False)
    from .living import make_living
    make_living(task)
    return task
