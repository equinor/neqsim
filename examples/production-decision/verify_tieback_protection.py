"""Verify completed tie-back receipts and compare safety margins across numerical refinements."""

import argparse
import json
import math
import subprocess
import sys
from pathlib import Path

from tieback_protection import CASES


def check(condition, message):
    if not condition:
        raise ValueError(message)


SHORT_CASES = ("host_gas_blocked", "host_psv_only", "choke_failed_open")


def simulate(directory):
    """Run each JVM independently and preserve existing studies."""
    directory.mkdir(parents=True, exist_ok=False)
    jobs = [(case, case, []) for case in CASES]
    jobs += [(case, case + "-.25-2", ["--mesh", "2"])
             for case in ("normal", "psd_failed", "all_isolation_failed")]
    jobs += [("psd_failed", "psd_failed-.125-1", ["--dt", ".125"]),
             ("all_isolation_failed", "all_isolation_failed-rated", ["--fully-rated"])]
    for case, name, options in jobs:
        output = directory / (name + ".json")
        subprocess.run([sys.executable, str(Path(__file__).with_name("tieback_protection.py")),
                        "--case", case, "--duration", "45" if case in SHORT_CASES else "90",
                        "--output", str(output), *options], check=True)


def load(path, case, dt=.25, cells=60):
    data = json.loads(path.read_text(encoding="utf-8"))
    rows = data["trace"]
    check(data["case"] == case and data["dt_s"] == dt and data["mesh_cells"] == cells,
          f"Case or discretization mismatch in {path}")
    duration = 45. if case in SHORT_CASES else 90.
    check(data["duration_s"] == duration and len(rows) == round(duration / dt) + 1
          and all(row["s"] == i * dt for i, row in enumerate(rows)), f"Incomplete trace in {path}")
    for j, zone in enumerate(data["zones"]):
        values = [row["zone_peaks_bara"][j] for row in rows]
        check(all(math.isfinite(x) and x > 0 for x in values)
              and max(values) == zone["peak_bara"], f"Pressure evidence mismatch in {path}")
    check(max(data["phase_residual_kg"], data["component_residual_kg"]) <= 1e-6,
          f"Inventory did not close in {path}")
    return data


def assess(directory):
    cases = {case: load(directory / (case + ".json"), case) for case in CASES}
    for c in ("normal", "false_high"):
        check(not cases[c]["events"], f"Unexpected trip in {c}")
    for c in ("blocked", "choke_failed_open", "host_psd"):
        d = cases[c]
        check(d["events"]["subsea_psd_command_s"] < d["events"]["tree_seated_s"]
              and d["trace"][-1]["requested_inlet_kg_s"] == 0., f"Isolation failed in {c}")
    for c in ("psd_failed", "first_hipv_stuck"):
        ev = cases[c]["events"]
        check(ev["hipv_b_seated_s"] > ev["hipps_trip_s"]
              and cases[c]["trace"][-1]["requested_inlet_kg_s"] == 0., f"HIPPS failed in {c}")
    check("hipv_a_seated_s" not in cases["first_hipv_stuck"]["events"], "Stuck valve reported seated")
    failed = cases["all_isolation_failed"]
    check(failed["trace"][-1]["requested_inlet_kg_s"] > 0.
          and failed["zones"][1]["margin_bar"] < 0., "Missing LC1 exceedance with lost isolation")
    hold = cases["host_gas_blocked"]
    relief = cases["host_psv_only"]
    check(hold["events"]["outlet_blocked_s"] > hold["events"]["separator_pshh_s"]
          and hold["dynamic_host"]["psv_cycles"] == 0, "Host shutdown response changed")
    check(relief["dynamic_host"]["psv_cycles"] >= 1
          and relief["dynamic_host"]["relief_to_3_bara_header_kg"] > 0., "Missing host relief")
    quiet = load(directory / "normal-.25-2.json", "normal", cells=120)
    check(not quiet["events"], "Mesh refinement activated a trip in normal operation")
    unprotected = load(directory / "all_isolation_failed-.25-2.json", "all_isolation_failed", cells=120)
    check(unprotected["zones"][1]["margin_bar"] < 0., "LC1 exceedance lost on finer mesh")
    rated = load(directory / "all_isolation_failed-rated.json", "all_isolation_failed")
    check(all(z["rated_for_source_at_common_datum"] for z in rated["zones"])
          and rated["trace"] == failed["trace"], "Rating comparison changed physical response")
    reference = cases["psd_failed"]
    comparisons = []
    for suffix, dt, cells in (("-.125-1", .125, 60), ("-.25-2", .25, 120)):
        d = load(directory / ("psd_failed" + suffix + ".json"), "psd_failed", dt, cells)
        check(d["basis"] == reference["basis"], "Mixed pressure protection designs")
        comparisons.append({"dt_s": dt, "cells": cells,
                            "hipps_trip_difference_s": abs(d["events"]["hipps_trip_s"]
                                                          - reference["events"]["hipps_trip_s"]),
                            "route_peak_differences_bar": [abs(z["peak_bara"] - r["peak_bara"])
                                for z, r in zip(d["zones"], reference["zones"])]})
    # Explicit numerical screening tolerances for this fixture only.
    within = all(c["hipps_trip_difference_s"] <= .5 and max(c["route_peak_differences_bar"]) <= .5
                 for c in comparisons)
    return {"functional_checks": "PASS", "numerical_screening": "PASS" if within else "HOLD",
            "timing_tolerance_s": .5, "zone_peak_tolerance_bar": .5, "comparisons": comparisons,
            "cases": {k: {x: v for x, v in d.items() if x != "trace"} for k, d in cases.items()}}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--run", action="store_true", help="Generate all cases in a new directory")
    args = parser.parse_args()
    if args.run:
        simulate(args.directory)
    print(json.dumps(assess(args.directory), indent=2, allow_nan=False))
