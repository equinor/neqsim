"""Verify completed tie-back receipts and compare safety margins across numerical refinements."""

import argparse
import json
import math
import subprocess
import sys
from bisect import bisect_left, bisect_right
from pathlib import Path

from tieback_protection import CASES, PRESSURE_PROBES_M, PROTECTION_CFL


def check(condition, message):
    if not condition:
        raise ValueError(message)


SHORT_CASES = ("host_gas_blocked", "host_psv_only", "choke_failed_open")
PRESSURE_TOLERANCE_BAR = .5
TIMING_TOLERANCE_S = .5
FRONT_RISE_BAR = .5
RESPONSE_EVENTS = ("hipps_trip_s", "hipv_a_seated_s", "hipv_b_seated_s")
PROTECTION_REFINEMENTS = (("-.125-1", .125, 60, PROTECTION_CFL),
                          ("-.25-2", .25, 120, PROTECTION_CFL),
                          ("-cfl.125-1", .25, 60, PROTECTION_CFL / 2),
                          ("-.25-4", .25, 240, PROTECTION_CFL))


def simulate(directory):
    """Run each JVM independently and preserve existing studies."""
    directory.mkdir(parents=True, exist_ok=False)
    jobs = [(case, case, []) for case in CASES]
    jobs += [(case, case + "-.25-2", ["--mesh", "2"])
             for case in ("normal", "all_isolation_failed")]
    jobs += [("normal", "normal-.125-1", ["--dt", ".125"]),
             ("normal", "normal-.25-4", ["--mesh", "4"]),
             ("all_isolation_failed", "all_isolation_failed-rated", ["--fully-rated"])]
    jobs += [("psd_failed", "psd_failed" + suffix,
              ["--dt", str(dt), "--mesh", str(cells // 60), "--cfl", str(cfl)])
             for suffix, dt, cells, cfl in PROTECTION_REFINEMENTS]
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
    validate_probes(data, path)
    check(all(len(row["zone_peaks_bara"]) == len(row["zone_envelope_bara"]) == len(zones)
              for row in rows), f"Incomplete zone pressure history in {path}")
    for j, zone in enumerate(data["zones"]):
        scans = [row["zone_peaks_bara"][j] for row in rows]
        envelope = [row["zone_envelope_bara"][j] for row in rows]
        # The rated peak includes every accepted internal pipe substep between scans.
        check(all(math.isfinite(x) and x > 0 for x in scans)
              and all(math.isfinite(e) and e >= x for e, x in zip(envelope, scans))
              and max(scans) == zone["scan_peak_bara"] and max(envelope) == zone["peak_bara"],
              f"Pressure evidence mismatch in {path}")
    check(all(0. <= data[key] <= 1e-6 for key in ("phase_residual_kg", "component_residual_kg")),
          f"Inventory did not close in {path}")
    return data


def validate_probes(data, source):
    check(data.get("pressure_probes_m") == PRESSURE_PROBES_M,
          f"Mismatched pressure probe locations in {source}")
    check(all(key in row and math.isfinite(row[key]) and row[key] > 0
              for row in data["trace"] for key in PRESSURE_PROBES_M),
          f"Incomplete pressure history in {source}")


def pressure_at(rows, times, time, key):
    right = bisect_left(times, time)
    if times[right] == time:
        return rows[right][key]
    left = right - 1
    fraction = (time - times[left]) / (times[right] - times[left])
    return rows[left][key] + fraction * (rows[right][key] - rows[left][key])


def directed_difference(reference, compared, key, window):
    """Match each scan to the continuous pressure range in the other history's time window."""
    times = [row["s"] for row in compared]
    errors = []
    for row in reference:
        time = row["s"]
        start, end = max(times[0], time - window), min(times[-1], time + window)
        values = [pressure_at(compared, times, start, key), pressure_at(compared, times, end, key)]
        values.extend(other[key] for other in compared[bisect_right(times, start):bisect_left(times, end)])
        errors.append((max(0., min(values) - row[key], row[key] - max(values)), time))
    return max(errors, key=lambda pair: pair[0])


def history_difference(reference, compared, key, window):
    forward = directed_difference(reference, compared, key, window)
    reverse = directed_difference(compared, reference, key, window)
    if reverse[0] > forward[0]:
        error, time = reverse
        sampled_from = "compared"
    else:
        error, time = forward
        sampled_from = "reference"
    return {"max_abs_bar": error, "at_s": time, "largest_error_sampled_from": sampled_from}


def first_pressure_rise(data, key):
    """Return the elapsed time to a 0.5 bar rise above the pre-blockage pressure."""
    blocked_at = data["events"]["outlet_blocked_s"]
    rows = data["trace"]
    times = [row["s"] for row in rows]
    baseline = pressure_at(rows, times, blocked_at, key)
    threshold = baseline + FRONT_RISE_BAR
    previous_time, previous_pressure = blocked_at, baseline
    for row in rows[bisect_right(times, blocked_at):]:
        if row[key] >= threshold:
            fraction = (threshold - previous_pressure) / (row[key] - previous_pressure)
            return previous_time + fraction * (row["s"] - previous_time) - blocked_at
        previous_time, previous_pressure = row["s"], row[key]
    return None


def compare_response(reference, refined):
    """Compare synchronized pressure histories, wave arrivals and final-element feedback."""
    check(all(refined[key] == reference[key] for key in ("case", "basis", "pressure_probes_m")),
          "Mixed pressure protection cases, designs or probe locations")
    check(reference["events"]["outlet_blocked_s"] == refined["events"]["outlet_blocked_s"],
          "Different blockage time origins")
    reference_rows, refined_rows = reference["trace"], refined["trace"]
    differences, tubes, arrivals = {}, {}, {}
    for key in PRESSURE_PROBES_M:
        differences[key] = history_difference(reference_rows, refined_rows, key, 0.)
        tubes[key] = history_difference(reference_rows, refined_rows, key, TIMING_TOLERANCE_S)
        tubes[key]["time_window_s"] = TIMING_TOLERANCE_S
        reference_arrival = first_pressure_rise(reference, key)
        refined_arrival = first_pressure_rise(refined, key)
        difference = (abs(reference_arrival - refined_arrival)
                      if reference_arrival is not None and refined_arrival is not None else None)
        arrivals[key] = {"reference_elapsed_s": reference_arrival, "compared_elapsed_s": refined_arrival,
                         "difference_s": difference}
    timings = {}
    for event in RESPONSE_EVENTS:
        check(event in reference["events"] and event in refined["events"], f"Missing trip or seat feedback: {event}")
        timings[event] = abs(refined["events"][event] - reference["events"][event])
    within = (max(timings.values()) <= TIMING_TOLERANCE_S
              and all(d["max_abs_bar"] <= PRESSURE_TOLERANCE_BAR for d in tubes.values())
              and all(a["difference_s"] is not None and a["difference_s"] <= TIMING_TOLERANCE_S
                      for a in arrivals.values()))
    return {"event_differences_s": timings, "endpoint_history_differences": differences,
            "endpoint_tube_differences": tubes, "pressure_fronts": arrivals, "within_tolerances": within}


def compare_protection(reference, refined):
    """Check physical probe histories and route-wide accepted-substep zone maxima."""
    response = compare_response(reference, refined)
    peaks = [abs(z["peak_bara"] - r["peak_bara"]) for z, r in zip(refined["zones"], reference["zones"])]
    return {"reference_dt_s": reference["dt_s"], "reference_cells": reference["mesh_cells"],
            "reference_pipe_cfl": reference.get("pipe_cfl"),
            "dt_s": refined["dt_s"], "cells": refined["mesh_cells"], "pipe_cfl": refined.get("pipe_cfl"),
            **response, "hipps_trip_difference_s": response["event_differences_s"]["hipps_trip_s"],
            "route_peak_differences_bar": peaks,
            "within_tolerances": response["within_tolerances"] and max(peaks) <= PRESSURE_TOLERANCE_BAR}


def assess_protection_refinement(directory, reference):
    """Check independent time refinements and both adjacent mesh refinements."""
    comparisons = []
    spatial_reference = reference
    for suffix, dt, cells, cfl in PROTECTION_REFINEMENTS:
        refined = load(directory / ("psd_failed" + suffix + ".json"), "psd_failed", dt, cells, cfl)
        spatial = cells > reference["mesh_cells"]
        comparisons.append(compare_protection(spatial_reference if spatial else reference, refined))
        if spatial:
            spatial_reference = refined
    return comparisons


def assess_independent_response(native, path):
    """Screen the finest trajectory against a separately generated, synchronized reference."""
    if path is None:
        return {"status": "HOLD", "reason": "Matched independent shut-in reference missing"}
    reference = json.loads(path.read_text(encoding="utf-8"))
    provenance = reference.get("provenance", {})
    check(provenance.get("method") in ("experiment", "independent_solver")
          and all(isinstance(provenance.get(key), str) and provenance[key].strip()
                  for key in ("source", "case_id", "data_rights")),
          f"Missing independent-reference provenance in {path}")
    times = [row["s"] for row in reference["trace"]]
    check(len(times) > 1 and times[0] == 0. and times[-1] == native["duration_s"]
          and all(0. < right - left <= .25 + 1e-9 for left, right in zip(times, times[1:])),
          f"Independent reference must cover the full interval at 0.25 s or finer in {path}")
    validate_probes(reference, path)
    events = reference.get("events", {})
    check(all(key in events and math.isfinite(events[key]) and 0. <= events[key] <= times[-1]
              for key in ("outlet_blocked_s", *RESPONSE_EVENTS))
          and events["outlet_blocked_s"] < events["hipps_trip_s"]
          and all(events["hipps_trip_s"] < events[key] for key in RESPONSE_EVENTS[1:]),
          f"Missing or inconsistent independent trip or seat feedback in {path}")
    response = compare_response(reference, native)
    return {"status": "PASS" if response["within_tolerances"] else "HOLD",
            "native_mesh_cells": native["mesh_cells"], "provenance": provenance, **response}


def assess(directory, benchmark=None):
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
    quiet_finest = load(directory / "normal-.25-4.json", "normal", cells=240)
    for normal in (quiet, quiet_fine, quiet_finest):
        check(normal["basis"] == cases["normal"]["basis"] and not normal["events"],
              "Normal refinement changed protection basis or activated a trip")
    unprotected = load(directory / "all_isolation_failed-.25-2.json", "all_isolation_failed", cells=120)
    check(unprotected["zones"][1]["margin_bar"] < 0., "LC1 exceedance lost on finer mesh")
    rated = load(directory / "all_isolation_failed-rated.json", "all_isolation_failed")
    check(all(z["rated_for_source_at_common_datum"] for z in rated["zones"])
          and rated["trace"] == failed["trace"], "Rating comparison changed physical response")
    comparisons = assess_protection_refinement(directory, cases["psd_failed"])
    within = all(c["within_tolerances"] for c in comparisons)
    finest = load(directory / "psd_failed-.25-4.json", "psd_failed", cells=240) if benchmark else None
    return {"functional_checks": "PASS", "numerical_screening": "PASS" if within else "HOLD",
            "independent_shut_in_screening": assess_independent_response(finest, benchmark),
            "pressure_probes_m": PRESSURE_PROBES_M, "front_rise_bar": FRONT_RISE_BAR,
            "timing_tolerance_s": TIMING_TOLERANCE_S, "zone_peak_tolerance_bar": PRESSURE_TOLERANCE_BAR,
            "endpoint_criterion": "each history within pressure tolerance of the other inside the timing window",
            "pipe_cfl": PROTECTION_CFL, "comparisons": comparisons,
            "cases": {k: {x: v for x, v in d.items() if x != "trace"} for k, d in cases.items()}}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--run", action="store_true", help="Generate all cases in a new directory")
    parser.add_argument("--benchmark", type=Path, help="Matched independent shut-in reference JSON")
    args = parser.parse_args()
    if args.run:
        simulate(args.directory)
    print(json.dumps(assess(args.directory, args.benchmark), indent=2, allow_nan=False))
