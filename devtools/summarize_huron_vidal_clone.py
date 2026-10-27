"""Summarize the isolated HV clone matrix without treating timing noise as a speedup."""

import argparse
import csv
from collections import defaultdict
import gzip
import json
import math
from pathlib import Path
from statistics import median


def summarize(directory, expected_cells=64, expected_rounds=8):
    """Preserve per-JVM medians and require the explicitly selected complete matrix."""
    if expected_cells < 1 or expected_rounds < 2:
        raise ValueError("Require positive cell count and at least two measured rounds")
    rounds_path = directory / "rounds.csv"
    source = rounds_path.open() if rounds_path.exists() else gzip.open(directory / "rounds.csv.gz", "rt")
    with source:
        rounds = list(csv.DictReader(source))
    cells = defaultdict(list)
    keys = ["eos", "components", "operation", "init", "rule"]
    for row in rounds:
        cells[tuple(row[key] for key in keys)].append(row)
    if len(cells) != expected_cells or len(rounds) != expected_cells * 4 * expected_rounds:
        raise ValueError("Incomplete matrix: expected " + str(expected_cells)
                         + " cells with four JVMs and " + str(expected_rounds) + " rounds each")
    summaries = []
    expected_samples = {}
    for cell, rows in sorted(cells.items()):
        samples = defaultdict(list)
        for row in rows:
            samples[row["sample"]].append(float(row["ns_per_op"]))
        if len(samples) != 4 or any(len(values) != expected_rounds for values in samples.values()):
            raise ValueError("Each cell needs four JVMs with the selected measured round count")
        if len({(row["sample"], row["round"]) for row in rows}) != 4 * expected_rounds:
            raise ValueError("Duplicate cell/JVM/round identity")
        expected_samples[cell] = set(samples)
        medians = {label: median(values) for label, values in samples.items()}
        result = dict(zip(keys, cell))
        for label in ("baseline", "candidate"):
            values = [value for name, value in medians.items() if name.startswith(label)]
            if len(values) != 2:
                raise ValueError("Each variant needs two independent JVMs")
            result[label + "_ns_per_op"] = median(values)
            result[label + "_jvm_min_ns"] = min(values)
            result[label + "_jvm_max_ns"] = max(values)
            result[label + "_bytes_per_op"] = median([
                float(row["allocated_bytes_per_op"]) for row in rows if row["sample"].startswith(label)
            ])
        result["candidate_over_baseline"] = result["candidate_ns_per_op"] / result["baseline_ns_per_op"]
        summaries.append(result)
    with (directory / "summary.csv").open("w", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=list(summaries[0]), lineterminator="\n")
        writer.writeheader()
        writer.writerows(summaries)
    states_path = directory / "rounds.csv.states.csv"
    source = states_path.open() if states_path.exists() else gzip.open(directory / "states.csv.gz", "rt")
    with source:
        states = list(csv.DictReader(source))
    values = defaultdict(list)
    for row in states:
        values[tuple(row[key] for key in keys + ["phase", "component"])].append(row)
    maximum = {key: 0.0 for key in ["beta", "x", "logphi"]}
    for rows in values.values():
        cell = tuple(rows[0][key] for key in keys)
        if len(rows) != 4 or {row["sample"] for row in rows} != expected_samples.get(cell):
            raise ValueError("Final phase/component topology differs between the four JVMs")
        for field in maximum:
            reference = float(rows[0][field])
            for row in rows[1:]:
                actual = float(row[field])
                if not math.isfinite(reference) or not math.isfinite(actual):
                    raise ValueError("Non-finite thermodynamic result: " + field)
                maximum[field] = max(maximum[field], abs(actual - reference))
                if abs(actual - reference) > max(1e-13, abs(reference) * 1e-8):
                    raise ValueError("Baseline/candidate thermodynamic result changed: " + field)
    evidence = {
        "cells": len(cells), "jvms": 4 * len(cells), "raw_rounds": len(rounds), "state_rows": len(states),
        "max_balance": max(float(row["max_balance"]) for row in rounds),
        "max_logfugacity": max(float(row["max_logfugacity"]) for row in rounds),
        "max_baseline_candidate_state_difference": maximum,
        "phase_types": sorted({row["phase"] for row in states}),
        "interpretation": "Median of two independent per-JVM medians; min/max retain between-JVM variation. "
        "No statistical speedup, absolute optimality or experimental-accuracy assertion.",
    }
    (directory / "numerical-summary.json").write_text(json.dumps(evidence, indent=2) + "\n")
    print(json.dumps(evidence, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--cells", type=int, default=64)
    parser.add_argument("--rounds", type=int, default=8)
    args = parser.parse_args()
    summarize(args.directory, args.cells, args.rounds)
