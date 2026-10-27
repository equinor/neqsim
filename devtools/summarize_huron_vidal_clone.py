"""Summarize the isolated HV clone matrix without treating timing noise as a speedup."""

import argparse
import csv
from collections import defaultdict
import gzip
import json
from pathlib import Path
from statistics import median


def summarize(directory):
    """Preserve per-JVM medians, allocation counts and independent result differences."""
    rounds_path = directory / "rounds.csv"
    source = rounds_path.open() if rounds_path.exists() else gzip.open(directory / "rounds.csv.gz", "rt")
    with source:
        rounds = list(csv.DictReader(source))
    cells = defaultdict(list)
    keys = ["eos", "components", "operation", "init", "rule"]
    for row in rounds:
        cells[tuple(row[key] for key in keys)].append(row)
    if len(cells) != 64 or len(rounds) != 2048:
        raise ValueError("Expected the complete default 64-cell, 256-JVM, 2048-round matrix")
    summaries = []
    for cell, rows in sorted(cells.items()):
        samples = defaultdict(list)
        for row in rows:
            samples[row["sample"]].append(float(row["ns_per_op"]))
        if len(samples) != 4 or any(len(values) != 8 for values in samples.values()):
            raise ValueError("Each default cell needs four JVMs with eight measured rounds")
        if len({(row["sample"], row["round"]) for row in rows}) != 32:
            raise ValueError("Duplicate cell/JVM/round identity")
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
        if len(rows) != 4:
            raise ValueError("Final phase/component topology differs between the four JVMs")
        for field in maximum:
            reference = float(rows[0][field])
            for row in rows[1:]:
                actual = float(row[field])
                maximum[field] = max(maximum[field], abs(actual - reference))
                if abs(actual - reference) > max(1e-13, abs(reference) * 1e-8):
                    raise ValueError("Baseline/candidate thermodynamic result changed: " + field)
    evidence = {
        "cells": len(cells), "jvms": 256, "raw_rounds": len(rounds), "state_rows": len(states),
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
    summarize(parser.parse_args().directory)
