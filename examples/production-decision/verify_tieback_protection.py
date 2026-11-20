"""Verify completed tie-back receipts and compare safety margins across numerical refinements."""

import argparse
import json
import math
import subprocess
import sys
from pathlib import Path

from tieback_protection import CASES, PROTECTION_CFL


def check(condition, message):
    if not condition:
        raise ValueError(message)


SHORT_CASES = ("host_gas_blocked", "host_psv_only", "choke_failed_open")
PRESSURE_TOLERANCE_BAR = .5
TIMING_TOLERANCE_S = .5


def simulate(directory):
    """Run each JVM independently and preserve existing studies."""
    directory.mkdir(parents=True, exist_ok=False)
    jobs = [(case, case, []) for case in CASES]
    jobs += [(case, case + "-.25-2", ["--mesh", "2"])
             for case in ("normal", "psd_failed", "all_isolation_failed")]
    jobs += [("normal", "normal-.125-1", ["--dt", ".125"]),
             ("psd_failed", "psd_failed-.125-1", ["--dt", ".125"]),
             ("psd_failed", "psd_failed-cfl.125-1", ["--cfl", ".125"]),
             ("all_isolation_failed", "all_isolation_failed-rated", ["--fully-rated"])]
    for case, name, options in jobs:
        output = directory / (name + ".json")
        subprocess.run([sys.executable, str(Path(__file__).with_name("tieback_protection.py")),
                        "--case", case, "--duration", "45" if case in SHORT_CASES else "90",
                        "--output", str(output), *options], check=True)


def load(path, case, dt=.25, cells=60, cfl=PROTECTION_CFL):
    data = json.loads(path.read_text(encoding="utf-8"))
    rows = data["trace"]
    check(data["case"] == case and data["dt_s"] == dt and data["mesh_cells"] == cells
          and data.get("pipe_cfl") == cfl, f"Case or discretization mismatch in {path}")
    duration = 45. if case in SHORT_CASES else 90.
    check(data["duration_s"] == duration and len(rows) == round(duration / dt) + 1
          and all(row["s"] == i * dt for i, row in enumerate(rows)), f"Incomplete trace in {path}")
    zones = data["basis"]["zones"]
    check(zones and len(zones) == len(data["zones"])
          and all(all(z[k] == summary[k] for k in z) for z, summary in zip(zones, data["zones"])),
          f"Pressure zones differ from the declared basis in {path}")
    check(all(len(row["zone_peaks_bara"]) == len(row["zone_envelope_bara"]) == len(zones)
              and all(math.isfinite(row[key]) and row[key] > 0 for key in ("subsea_bara", "riser_bara"))
              for row in rows), f"Incomplete pressure history in {path}")
    for j, zone in enumerate(data["zones"]):
        scans = [row["zone_peaks_bara"][j] for row in rows]
        envelope = [row["zone_envelope_bara"][j] for row in rows]
        # The rated peak includes every accepted internal pipe substep between scans.
        check(all(math.isfinite(x) and x > 0 for x in scans)
              and all(e >= x for e, x in zip(envelope, scans))
              and max(scans) == zone["scan_peak_bara"] and max(envelope) == zone["peak_bara"],
              f"Pressure evidence mismatch in {path}")
    check(all(0. <= data[key] <= 1e-6 for key in ("phase_residual_kg", "component_residual_kg")),
          f"Inventory did not close in {path}")
    return data


def tube_difference(reference, refined, key, window=TIMING_TOLERANCE_S):
    """Largest pressure error after allowing the declared timing tolerance.

    Each reference sample is matched with the closest refined value within the time window.
    A front whose arrival moves by one scan is then judged by the timing tolerance, which is
    checked separately against trip and seat feedback, and not twice as a pressure error.
    """
    error, time = 0., None
    rows = refined["trace"]
    for row in reference["trace"]:
        nearest = min(abs(row[key] - other[key]) for other in rows
                      if abs(other["s"] - row["s"]) <= window + 1e-9)
        if time is None or nearest > error:
            error, time = nearest, row["s"]
    return {"max_abs_bar": error, "at_s": time, "time_window_s": window}


def compare_protection(reference, refined):
    """Compare physical endpoints and valve seating at common sample times."""
    check(refined["case"] == reference["case"] and refined["basis"] == reference["basis"],
          "Mixed pressure protection cases or designs")
    rows = {row["s"]: row for row in refined["trace"]}
    differences, tubes = {}, {}
    for key in ("subsea_bara", "riser_bara"):
        error, time = max(((abs(row[key] - rows[row["s"]][key]), row["s"]) for row in reference["trace"]),
                          key=lambda pair: pair[0])
        differences[key] = {"max_abs_bar": error, "at_s": time}
        tubes[key] = tube_difference(reference, refined, key)
    timings = {}
    for event in ("hipps_trip_s", "hipv_a_seated_s", "hipv_b_seated_s"):
        check(event in reference["events"] and event in refined["events"], f"Missing trip or seat feedback: {event}")
        timings[event] = abs(refined["events"][event] - reference["events"][event])
    peaks = [abs(z["peak_bara"] - r["peak_bara"]) for z, r in zip(refined["zones"], reference["zones"])]
    within = (max(peaks) <= PRESSURE_TOLERANCE_BAR and max(timings.values()) <= TIMING_TOLERANCE_S
              and all(d["max_abs_bar"] <= PRESSURE_TOLERANCE_BAR for d in tubes.values()))
    return {"dt_s": refined["dt_s"], "cells": refined["mesh_cells"], "pipe_cfl": refined.get("pipe_cfl"),
            "hipps_trip_difference_s": timings["hipps_trip_s"], "event_differences_s": timings,
            "route_peak_differences_bar": peaks, "endpoint_history_differences": differences,
            "endpoint_tube_differences": tubes, "within_tolerances": within}


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
    quiet_fine = load(directory / "normal-.125-1.json", "normal", dt=.125)
    for normal in (quiet, quiet_fine):
        check(normal["basis"] == cases["normal"]["basis"] and not normal["events"],
              "Normal refinement changed protection basis or activated a trip")
    unprotected = load(directory / "all_isolation_failed-.25-2.json", "all_isolation_failed", cells=120)
    check(unprotected["zones"][1]["margin_bar"] < 0., "LC1 exceedance lost on finer mesh")
    rated = load(directory / "all_isolation_failed-rated.json", "all_isolation_failed")
    check(all(z["rated_for_source_at_common_datum"] for z in rated["zones"])
          and rated["trace"] == failed["trace"], "Rating comparison changed physical response")
    reference = cases["psd_failed"]
    comparisons = []
    for suffix, dt, cells, cfl in (("-.125-1", .125, 60, PROTECTION_CFL), ("-.25-2", .25, 120, PROTECTION_CFL),
                                   ("-cfl.125-1", .25, 60, PROTECTION_CFL / 2)):
        d = load(directory / ("psd_failed" + suffix + ".json"), "psd_failed", dt, cells, cfl)
        comparisons.append(compare_protection(reference, d))
    within = all(c["within_tolerances"] for c in comparisons)
    return {"functional_checks": "PASS", "numerical_screening": "PASS" if within else "HOLD",
            "timing_tolerance_s": TIMING_TOLERANCE_S, "zone_peak_tolerance_bar": PRESSURE_TOLERANCE_BAR,
            "endpoint_criterion": "pressure within tolerance of the refined history inside the timing window",
            "pipe_cfl": PROTECTION_CFL, "comparisons": comparisons,
            "cases": {k: {x: v for x, v in d.items() if x != "trace"} for k, d in cases.items()}}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--run", action="store_true", help="Generate all cases in a new directory")
    args = parser.parse_args()
    if args.run:
        simulate(args.directory)
    print(json.dumps(assess(args.directory), indent=2, allow_nan=False))
