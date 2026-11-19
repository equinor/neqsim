"""Independently check the ten 20-second V-101 receipts and the fixed-boundary control."""

import argparse
import json
import math
import subprocess
import sys
from pathlib import Path


CASES = ("psv", "bdv", "simultaneous", "delayed", "wet")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def verify(path, case, dt, fixed, revision, energy_policy="conservative"):
    d = json.loads(path.read_text(encoding="utf-8"))
    require(d["case"] == case and d["dt_s"] == dt and d["fixed_boundary"] == fixed
            and d["source_revision"] == revision and d["vessel_energy_policy"] == energy_policy, f"Wrong case or build in {path}")
    rows = d["rows"]
    require(len(rows) == round(20 / dt), f"Incomplete run in {path}")
    previous = d["tip_static_bara"]
    for i, row in enumerate(rows):
        start = i * dt
        require(abs(row["end_s"] - start - dt) < 1e-10, f"Broken clock in {path}")
        flange = d["tip_static_bara"] if fixed else previous
        for key in ("valve_flange_bara", "bdv_flange_bara"):
            if key == "bdv_flange_bara" and case == "psv":
                continue
            require(abs(row[key] - flange) <= 1e-8, f"Broken {key} connection at {start} s")
        require(0 <= row["lift_fraction"] <= 1 and row["condensate_kg"] >= 0, "Invalid equipment state")
        if case == "psv" or start < (5 if case == "delayed" else 1):
            require(row["bdv_kg_s"] == 0, "Flow through a closed BDV")
        if case == "bdv":
            require(row["psv_kg_s"] == 0, "Flow through an immobilized PSV")
        # The independent steady dry-gas terminal boundary at the observed tip load.
        steady_tip = d["tip_static_bara"] + d["tip_rated_rise_bar"] * (row["tip_kg_s"] / d["tip_rated_kg_s"])**2
        require(abs(max(row["header_bara"], d["tip_static_bara"]) - steady_tip) <= 1.00001e-6, "Terminal rating mismatch")
        previous = row["header_bara"]
    for key, peak in d["peaks"].items():
        observed = max(rows, key=lambda row: row[key])
        require(observed[key] == peak["value"] and (peak["end_s"] is None if observed[key] == 0
                else observed["end_s"] == peak["end_s"]), f"Incorrect {key} peak")
    for total, flows in (("admitted_kg", ("psv_kg_s", "bdv_kg_s")), ("drained_kg", ("drain_kg_s",)),
                          ("discharged_kg", ("tip_kg_s",))):
        calculated = math.fsum(row[key] * dt for row in rows for key in flows)
        require(abs(calculated - d[total]) <= 1e-8, f"Incorrect integrated {total}")
    require(max(abs(row["mass_residual_kg"]) for row in rows) <= 1e-8
            and max(row["max_component_residual_mol"] for row in rows) <= 1e-8
            and max(abs(row["volume_residual_m3"]) for row in rows) <= 1e-5 * d["header_volume_m3"]
            and max(row["relative_energy_residual"] for row in rows) <= 1e-5, "Header inventory residual")
    require((d["numerical_status"] == "PASS") == all(d["numerical_checks"].values())
            and d["numerical_status"] in {"HOLD", "PASS"}, "Inconsistent numerical status")
    for tag, ledger in d["vessel_energy_ledger"].items():
        expected = ledger["initial_J"] + ledger["net_boundary_energy_J"]
        require(math.isclose(ledger["expected_J"], expected, rel_tol=1e-12, abs_tol=1e-6)
                and math.isclose(ledger["published_J"] - expected, ledger["final_debt_J"], abs_tol=1e-5)
                and math.isclose(ledger["max_abs_debt_J"] / max(abs(ledger["initial_J"]), 1.),
                                 d["vessel_relative_energy_residuals"][tag], rel_tol=1e-12),
                f"Inconsistent source vessel energy ledger for {tag}")
    if max(d["vessel_relative_energy_residuals"].values()) > 1e-5:
        require(not d["numerical_checks"]["source_vessel_energy"], "Unreported vessel energy exceedance")
    require(all(d["numerical_checks"][k] for k in d["numerical_checks"] if k != "source_vessel_energy"),
            f"Disposal balance, physical flange, or discharge check failed in {path}")
    if energy_policy == "conservative":
        require(all(ledger["complete"] for ledger in d["vessel_energy_ledger"].values())
                and max(d["vessel_relative_energy_residuals"].values()) <= 1e-5,
                f"Conservative source-energy integration exceeded the reference limit in {path}")
    if case == "wet":
        require(max(r["relief_liquid_kg_s"] for r in rows) > 0 and d["drained_kg"] > 0,
                "Missing wet release or physical drainage")
    require(d["engineering_qualification"] == "HOLD", "Unsupported qualification")
    return d


def assess(folder, revision):
    control = verify(folder / "fixed-.1.json", "psv", .1, True, revision, "reference")
    verify(folder / "fixed-conservative-.1.json", "psv", .1, True, revision)
    for key, value, time in (("psv_kg_s", 5.495517794, 3.2), ("vessel_bara", 42.36368588, 2.4)):
        peak = control["peaks"][key]
        require(abs(peak["value"] - value) < 1e-5 and abs(peak["end_s"] - time) < 1e-8,
                f"Native fixed-boundary regression for {key}")
    summary = {}
    for case in CASES:
        coarse, fine = [verify(folder / f"{case}-{suffix}.json", case, dt, False, revision)
                        for suffix, dt in ((".1", .1), (".05", .05))]
        summary[case] = {"minimum_fluid_K": {key: [min(row[key] for row in d["rows"]) for d in (coarse, fine)]
                                             for key in ("10-V-100_K", "20-V-101_K", "header_K")},
                         "numerical_status": [coarse["numerical_status"], fine["numerical_status"]],
                         "maximum_source_energy_residual": [max(d["vessel_relative_energy_residuals"].values())
                                                           for d in (coarse, fine)],
                         "peak_change": {key: fine["peaks"][key]["value"] - peak["value"]
                                         for key, peak in coarse["peaks"].items()},
                         "peak_time_change_s": {key: fine["peaks"][key]["end_s"] - peak["end_s"]
                                                for key, peak in coarse["peaks"].items() if peak["end_s"] is not None}}
    return summary


def simulate(folder, revision):
    """Create replay receipts in a fresh directory. Stop on the first failed process."""
    folder.mkdir(parents=True, exist_ok=False)
    driver = str(Path(__file__).with_name("v101_disposal.py"))
    runs = [(case, suffix, case + "-" + suffix, []) for case in CASES for suffix in (".1", ".05")]
    runs.append(("psv", ".1", "fixed-.1", ["--fixed-boundary", "--vessel-energy", "reference"]))
    runs.append(("psv", ".1", "fixed-conservative-.1", ["--fixed-boundary"]))
    for case, step, name, extra in runs:
        subprocess.run([sys.executable, driver, "--case", case, "--dt", step, "--revision", revision,
                        "--output", str(folder / (name + ".json")), *extra], check=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--run", action="store_true", help="Run the full matrix in a fresh directory")
    args = parser.parse_args()
    if args.run:
        simulate(args.directory, args.revision)
    print(json.dumps(assess(args.directory, args.revision), indent=2, allow_nan=False))
