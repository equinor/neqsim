"""Audit complete selected-pair JVM receipts and retain a deterministic raw ZIP.

Accepts the fresh runner directory or its archive. Forks are independent units;
rounds are retained subsamples. No timing or empirical accuracy threshold applies.
"""

import argparse
from collections import Counter
import csv
import hashlib
import io
import json
from pathlib import Path
import statistics
import zipfile


def profile_counts(data):
    """Count inclusive methods once per exported JFR stack, separately for each sampling event type."""
    events = data["recording"]["events"]
    if not events:
        raise ValueError("Empty CPU profile")
    groups = {}
    for event in events:
        kind = event["type"]
        if kind not in ("jdk.ExecutionSample", "jdk.NativeMethodSample"):
            raise ValueError("Unexpected CPU event type")
        group = groups.setdefault(kind, {"events": 0, "main_events": 0, "missing_stacks": 0,
                                         "truncated_stacks": 0, "inclusive": Counter(), "self": Counter()})
        group["events"] += 1
        values = event["values"]
        if values.get("sampledThread", {}).get("javaName") == "main":
            group["main_events"] += 1
        stack = values.get("stackTrace") or {}
        group["truncated_stacks"] += bool(stack.get("truncated"))
        frames = stack.get("frames") or []
        methods = [frame["method"]["type"]["name"].replace("/", ".") + "." + frame["method"]["name"]
                   for frame in frames]
        if not methods:
            group["missing_stacks"] += 1
        else:
            group["inclusive"].update(set(methods))
            group["self"][methods[0]] += 1
    for group in groups.values():
        for field in ("inclusive", "self"):
            group[field] = [list(pair) for pair in sorted(group[field].items(), key=lambda pair: (-pair[1], pair[0]))[:25]]
    return groups

from run_huron_vidal_selected_pair_benchmark import selections, validate_evidence


def stem(run):
    """Return the runner's unique file identity for one JVM."""
    return "fork%d_profile%d_%s_n%d_rule%d" % (
        run["fork"], run["profile"], run["scenario"], run["components"], run["rule"])


def audit(manifest, read):
    """Verify declared matrix, SHA256 pins and independent endpoint arithmetic before summarizing."""
    matrix = manifest["matrix"]
    expected = list(selections(matrix["forks"], matrix["profiles"], matrix["scenarios"], matrix["sizes"]))
    actual = [(run["fork"], run["profile"], run["scenario"], run["components"], run["rule"])
              for run in manifest["runs"]]
    if not expected or actual != expected or len(expected) != manifest["planned_jvms"] or "finish_utc" not in manifest:
        raise ValueError("Incomplete or reordered JVM matrix")
    centers = {}
    first_endpoints = {}
    max_repeated_difference = 0.0
    maxima = {key: 0.0 for key in ("balance", "normalization", "log_fugacity")}
    topology = set()
    operations = 0
    state_rows = 0
    log_bytes = 0
    for run in manifest["runs"]:
        if run["exit"] != 0 or "failure" in run:
            raise ValueError("Failed process retained in matrix")
        raw, raw_states = read(stem(run) + ".csv"), read(stem(run) + ".csv.states.csv")
        log_bytes += len(read(stem(run) + ".log"))
        if (hashlib.sha256(raw).hexdigest() != run["csv_sha256"]
                or hashlib.sha256(raw_states).hexdigest() != run["state_sha256"]):
            raise ValueError("Changed raw evidence hash")
        rows = list(csv.DictReader(io.StringIO(raw.decode("utf-8"))))
        states = list(csv.DictReader(io.StringIO(raw_states.decode("utf-8"))))
        validate_evidence(rows, states, run["profile"], run["rule"], run["scenario"], run["components"])
        key = (run["fork"], run["profile"], run["scenario"], run["components"], run["rule"])
        endpoint_key = key[1:]
        canonical = {(int(state["round"]), int(state["phase"]), state["type"], state["component"]):
                     tuple(float(state[field]) for field in ("beta", "z", "x", "logphi")) for state in states}
        if endpoint_key in first_endpoints:
            reference = first_endpoints[endpoint_key]
            if set(canonical) != set(reference):
                raise ValueError("Changed same-case endpoint topology")
            difference = max(abs(a - b) for name in canonical for a, b in zip(canonical[name], reference[name]))
            if difference > 1e-10:
                raise ValueError("Changed same-case endpoint across independent JVMs")
            max_repeated_difference = max(max_repeated_difference, difference)
        else:
            first_endpoints[endpoint_key] = canonical
        centers[key] = {
            "ns": statistics.median(int(row["elapsed_ns"]) / int(row["operations"]) for row in rows),
            "topologies": sorted({row["topology"] for row in rows}),
            "beta0": statistics.median(float(row["beta0"]) for row in rows),
            "rich_water": statistics.median(float(row["rich_water"]) for row in rows),
            "aqueous_CO2": statistics.median(float(row["aqueous_CO2"]) for row in rows),
            "bytes": statistics.median(int(row["allocated_bytes"]) / int(row["operations"]) for row in rows)
            if all(int(row["allocated_bytes"]) >= 0 for row in rows) else None}
        topology.update(row["topology"] for row in rows)
        operations += sum(int(row["operations"]) for row in rows)
        state_rows += len(states)
        for field in maxima:
            maxima[field] = max(maxima[field], max(float(row[field]) for row in rows))
    cells = []
    for profile in matrix["profiles"]:
        for size in matrix["sizes"]:
            for scenario in matrix["scenarios"]:
                classical = [centers[fork, profile, scenario, size, 2] for fork in range(matrix["forks"])]
                hv = [centers[fork, profile, scenario, size, 4] for fork in range(matrix["forks"])]
                ratios = [h["ns"] / c["ns"] for h, c in zip(hv, classical)]
                cells.append({"profile": profile, "components": size, "scenario": scenario,
                              "HV_over_CLASSICAL_per_fork": ratios,
                              "ratio_median": statistics.median(ratios), "ratio_range": [min(ratios), max(ratios)],
                              "CLASSICAL_ns_per_fork": [item["ns"] for item in classical],
                              "HV_ns_per_fork": [item["ns"] for item in hv],
                              "CLASSICAL_same_binary_later_earlier": [classical[i + 1]["ns"] / classical[i]["ns"] for i in range(0, len(classical) - 1, 2)],
                              "HV_same_binary_later_earlier": [hv[i + 1]["ns"] / hv[i]["ns"] for i in range(0, len(hv) - 1, 2)],
                              "CLASSICAL_bytes_per_fork": [item["bytes"] for item in classical],
                              "HV_bytes_per_fork": [item["bytes"] for item in hv],
                              "CLASSICAL_endpoints_per_fork": [{k: v for k, v in item.items() if k not in ("ns", "bytes")} for item in classical],
                              "HV_endpoints_per_fork": [{k: v for k, v in item.items() if k not in ("ns", "bytes")} for item in hv]})
    return {"declared_source_head": manifest["declared_source_head"], "jvms": len(expected),
            "rounds": len(expected) * 10, "measured_operations": operations,
            "state_rows": state_rows, "max_residuals": maxima, "topologies": sorted(topology),
            "retained_process_log_bytes": log_bytes,
            "max_same_case_beta_z_x_logphi_difference": max_repeated_difference,
            "interpretation": "Model cost characterization; different thermodynamics/solver work; no speedup acceptance",
            "cells": cells}


def main():
    """Validate a directory or ZIP and optionally create an immutable deterministic archive."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--archive", type=Path, help="New archive path; only with directory input")
    parser.add_argument("--summary", type=Path, required=True)
    args = parser.parse_args()
    if args.input.is_dir():
        read = lambda name: (args.input / name).read_bytes()
        manifest = json.loads(read("execution.json"))
        result = audit(manifest, read)
        if args.archive:
            with zipfile.ZipFile(args.archive, "x", compression=zipfile.ZIP_DEFLATED) as archive:
                names = ["execution.json"]
                for run in manifest["runs"]:
                    names.extend([stem(run) + ".csv", stem(run) + ".csv.states.csv", stem(run) + ".log"])
                for name in sorted(names):
                    info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    archive.writestr(info, read(name))
    else:
        if args.archive:
            parser.error("Archive creation requires directory input")
        with zipfile.ZipFile(args.input) as archive:
            result = audit(json.loads(archive.read("execution.json")), archive.read)
    args.summary.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in result.items() if key != "cells"}, indent=2))


if __name__ == "__main__":
    main()
