"""Audit preserved CO2/water observations, uncertainties and diagnostic protocols.

This offline audit checks transcription lineage and numerical closure. Experimental
errors are reported without an accuracy acceptance threshold. It never fits data,
repairs the malformed original pressure, or pools distinct timing experiments.
"""

import argparse
import csv
from collections import Counter, defaultdict
from decimal import Decimal
import hashlib
import json
import math
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "src/test/resources/neqsim/thermo/mixingrule/hv-co2-source"
PRIMARY_HASHES = {
    "gas-primary.csv": "bf167a41c6801daa0f0acfdb1e8bc20e92d87af8a0a192f729ed3ea780714472",
    "reverse-primary.csv": "f25ce91f0a25d5552cd98882b47a64e8e151e501bf86ebca99eda88e70808916",
}


def require(condition, message):
    """Reject inconsistent evidence even when Python assertions are disabled."""
    if not condition:
        raise ValueError(message)


def read_csv(path):
    """Read all rows, retaining original decimal and missing-value strings."""
    with path.open(newline="", encoding="utf-8") as stream:
        return list(csv.DictReader(stream))


def audit_observations():
    """Match each executable input to an immutable primary-table transcription."""
    for name, expected in PRIMARY_HASHES.items():
        data = (FIXTURE / name).read_bytes()
        require(b"\r" not in data, "Repository CSV uses canonical LF")
        receipt_bytes = data.replace(b"\n", b"\r\n")
        require(hashlib.sha256(receipt_bytes).hexdigest() == expected,
                "Primary transcription changed beyond CRLF-to-LF normalization: " + name)
    gas = read_csv(FIXTURE / "gas-primary.csv")
    reverse = read_csv(FIXTURE / "reverse-primary.csv")
    inputs = read_csv(FIXTURE / "input.csv")
    require((len(gas), len(reverse), len(inputs)) == (58, 62, 120), "Observation counts")
    require(Counter(row["source"] for row in inputs) == {
        "MeyerHarvey2015": 58, "Lipinski2021": 33, "Ahmadi2018": 29}, "Source counts")
    gas_by_row = {row["row"]: row for row in gas}
    reverse_by_state = {(row["source"], Decimal(row["T_K"]), row["P_bara"]): row
                        for row in reverse}
    require(len(gas_by_row) == 58 and len(reverse_by_state) == 62, "Duplicate primary state")
    blocked = []
    consumed_reverse = set()
    for row in inputs:
        if row["source"] == "MeyerHarvey2015":
            source = gas_by_row[row["row"]]
            require(row["direction"] == "water_in_CO2", "Gas direction")
            require(Decimal(row["observed"]) == Decimal(source["yw_times_1000_printed"]) / 1000,
                    "Gas mole-fraction conversion")
            require(Decimal(row["T_K"]) == Decimal(source["T_K"]), "Gas temperature")
            require(Decimal(row["P_bara"]) == Decimal(source["P_MPa"]) * 10,
                    "MPa to absolute bar conversion")
            require(row["author_Ux_k2"] == "", "Enhancement-factor uncertainty is not U(y)")
            require(Decimal(source["author_Ur_f_percent_k2"]) > 0,
                    "Preserve enhancement-factor uncertainty")
        else:
            state = row["source"], Decimal(row["T_K"]), row["P_bara"]
            require(state not in consumed_reverse, "Repeated reverse observation")
            consumed_reverse.add(state)
            source = reverse_by_state[state]
            require(row["direction"] == "CO2_in_water", "Reverse direction")
            require(Decimal(row["observed"]) == Decimal(source["xCO2"]), "Reverse observation")
            require(Decimal(row["author_Ux_k2"]) == Decimal(source["U_xCO2_k2"]),
                    "Reverse absolute expanded uncertainty")
            if row["P_bara"]:
                require(Decimal(row["P_bara"]) == Decimal(source["P_MPa_raw"]) * 10,
                        "Reverse pressure conversion")
            else:
                require(source["P_MPa_raw"] == "1.0.0180" and source["table"] == "A1"
                        and source["row"] == "6", "Only the documented pressure is blocked")
                blocked.append({"source": row["source"], "row": row["row"],
                                "raw_pressure_MPa": source["P_MPa_raw"]})
    require(len(consumed_reverse) == 62, "Every reverse observation retained")
    require(len(blocked) == 1, "Blocked original observation count")
    return inputs, blocked


def audit_assessment(path, inputs):
    """Check all attempted rows and report errors separately by profile and experiment."""
    rows = read_csv(path)
    expected = {(str(profile), row["source"], row["row"]): row
                for profile in range(3) for row in inputs}
    require(len(rows) == len(expected) == 360, "Attempted observation/profile count")
    seen = set()
    groups = defaultdict(list)
    maximum = {"max_balance_error": 0.0, "max_normalization_error": 0.0,
               "max_log_fugacity_error": 0.0}
    limits = {"max_balance_error": 1e-10, "max_normalization_error": 1e-12,
              "max_log_fugacity_error": 1e-8}
    blocked = 0
    for row in rows:
        key = row["profile"], row["source"], row["row"]
        require(key not in seen and key in expected, "Duplicate or unknown diagnostic row")
        seen.add(key)
        require(all(row[name] == value for name, value in expected[key].items()),
                "Diagnostic input differs from preserved observation")
        if not row["P_bara"]:
            require(row["status"] == "blocked_original_pressure" and not row["predicted"],
                    "Do not invent a pressure or prediction")
            blocked += 1
            continue
        require(row["status"] == "solved", "Unsolved usable observation")
        error = float(row["predicted"]) / float(row["observed"]) - 1.0
        require(math.isfinite(error) and abs(error - float(row["relative_error"])) <= 1e-14,
                "Incorrect reported physical error")
        groups[key[:2]].append(row)
        for name, limit in limits.items():
            value = float(row[name])
            require(math.isfinite(value) and 0 <= value <= limit, "Numerical residual: " + name)
            maximum[name] = max(maximum[name], value)
    require(blocked == 3 and sum(map(len, groups.values())) == 357, "Solved/blocked counts")
    physical = []
    for (profile, source), points in sorted(groups.items()):
        within = sum(abs(float(row["predicted"]) - float(row["observed"]))
                     <= float(row["author_Ux_k2"]) for row in points if row["author_Ux_k2"])
        physical.append({"profile": int(profile), "source": source, "count": len(points),
                         "AARD_percent": 100 * sum(abs(float(row["relative_error"]))
                                                   for row in points) / len(points),
                         "inside_author_Ux_k2": within if source != "MeyerHarvey2015" else None})
    return {"attempted": 360, "solved": 357, "blocked": blocked,
            "maximum_numerical_residuals": maximum, "physical_errors": physical}


def audit_performance():
    """Verify each frozen experiment independently, including every adverse round."""
    protocols = [("performance-model-comparison.csv", 72, 720, 4824000),
                 ("performance-held-candidate.csv", 72, 720, 5418000),
                 ("performance-pr2016-repeat.csv", 12, 120, 606000)]
    results = []
    for name, jvms, rounds, operations in protocols:
        rows = read_csv(FIXTURE / name)
        require(len(rows) == rounds, "Timing round count: " + name)
        groups = defaultdict(list)
        for row in rows:
            groups[(row["profile"], row["rule"], row["scenario"], row["fork"],
                    row.get("variant", "model"))].append(int(row["round"]))
            require(int(row["elapsed_ns"]) > 0 and int(row["operations"]) > 0, "Timing values")
            for field, limit in [("balance", 1e-10), ("normalization", 1e-12),
                                 ("log_fugacity", 1e-8)]:
                value = float(row[field])
                require(math.isfinite(value) and 0 <= value <= limit, "Timing endpoint: " + field)
            require(row["topology"] and 0 <= float(row["beta0"]) <= 1, "Timing topology")
        require(len(groups) == jvms and all(sorted(v) == list(range(10)) for v in groups.values()),
                "Independent JVM/round identity: " + name)
        require(sum(int(row["operations"]) for row in rows) == operations, "Operation count")
        results.append({"file": name, "jvms": jvms, "rounds": rounds, "operations": operations})
    return results


def main():
    """Run the audit on the frozen report or a newly generated native report."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assessment", type=Path, default=FIXTURE / "primary-assessment.csv")
    args = parser.parse_args()
    inputs, blocked = audit_observations()
    result = {"primary_receipt_CRLF_sha256": PRIMARY_HASHES,
              "repository_LF_sha256": {name: hashlib.sha256((FIXTURE / name).read_bytes()).hexdigest()
                                      for name in PRIMARY_HASHES}, "observations": len(inputs),
              "blocked_original": blocked, "assessment": audit_assessment(args.assessment, inputs),
              "performance_protocols_kept_separate": audit_performance(),
              "accuracy_acceptance": "Not established; errors are not test tolerances"}
    print(json.dumps(result, indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
