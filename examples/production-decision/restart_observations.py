"""Compare a frozen restart trajectory to independently supplied event measurements."""

import argparse
import hashlib
import json
import math
import statistics
from pathlib import Path

from decision import quantities


# Boundary rates are accepted interval averages. Pressure, temperature and
# levels are end-of-step states. Each observation declares the same time basis.
INTERVAL = {"riser_gas_kg_s": "kg/s", "riser_oil_kg_s": "kg/s", "riser_water_kg_s": "kg/s"}
POINT = {"manifold_bara": "bara", "arrival_K": "K", "separator_bara": "bara",
         "level_m": "m", "interface_m": "m"}
UNITS = {**INTERVAL, **POINT}


def compare(run, manifest):
    """Score every requested sensor sample, keeping data coverage and origin explicit."""
    config = run["configuration"]
    if not config.get("event_id") or manifest["event_id"] != config["event_id"] or manifest["policy"] != config["policy"]:
        raise ValueError("Event identifier and choke policy must match the frozen run")
    if manifest["origin"] not in ("field", "laboratory", "synthetic"):
        raise ValueError("Declare field, laboratory or synthetic observation origin")
    if not isinstance(manifest["source"], str) or not manifest["source"].strip():
        raise ValueError("Declare the observation source")
    records = manifest["observations"]
    if not records:
        raise ValueError("No held-out observations")
    result = {"event_id": manifest["event_id"], "policy": config["policy"], "origin": manifest["origin"],
              "source": manifest["source"], "revision": config["revision"], "by_quantity": {}}
    if run.get("status") == "rejected":
        return {**result, "status": "rejected_trajectory", "reason": run["reason"]}
    quantities(run)
    dt = config["dt"]
    rows = run["time_series"]
    seen = set()
    samples = {}
    for r in records:
        key = r["quantity"]
        if key not in UNITS or r["unit"] != UNITS[key] or not isinstance(r["tag"], str) or not r["tag"].strip():
            raise ValueError("Expected a known quantity, engineering unit and instrument tag")
        start, end = r["start_s"], r["end_s"]
        if not all(math.isfinite(v) for v in (start, end, r["value"], r["tolerance"])) or r["tolerance"] <= 0:
            raise ValueError("Expected finite times, measurement and positive tolerance")
        if start < 0 or end <= 0 or (start >= end if key in INTERVAL else start != end):
            raise ValueError("Use a positive-duration interval for rates and equal times for end states")
        identity = (key, r["tag"], start, end)
        if identity in seen:
            raise ValueError("Duplicate sensor/time observation")
        seen.add(identity)
        first, last = round(start / dt), round(end / dt)
        predicted = None
        if (last <= len(rows) and last >= 1 and abs(first * dt - start) < 1e-9
                and abs(last * dt - end) < 1e-9):
            values = ([row.get(key) for row in rows[first:last]] if key in INTERVAL
                      else [rows[last - 1].get(key)])
            if values and all(v is not None and math.isfinite(v) for v in values):
                predicted = statistics.mean(values)
        error = None if predicted is None else predicted - r["value"]
        samples.setdefault(key, []).append({**r, "predicted": predicted, "error": error,
                                           "within_tolerance": error is not None and abs(error) <= r["tolerance"]})
    missing, outside = False, False
    for key, items in samples.items():
        errors = [r["error"] for r in items if r["error"] is not None]
        missing |= len(errors) != len(items)
        outside |= any(r["error"] is not None and not r["within_tolerance"] for r in items)
        coverage_by_tag = {}
        for sample in items:
            tag = coverage_by_tag.setdefault(sample["tag"], {"requested": 0, "paired": 0})
            tag["requested"] += 1
            tag["paired"] += sample["predicted"] is not None
        result["by_quantity"][key] = {"unit": UNITS[key], "paired": len(errors), "requested": len(items),
                                      "coverage_by_tag": coverage_by_tag,
                                      "bias": statistics.mean(errors) if errors else None,
                                      "mae": statistics.mean(map(abs, errors)) if errors else None, "samples": items}
    result["status"] = ("outside_tolerance" if outside else "insufficient_coverage" if missing
                        else "within_supplied_tolerances")
    result["complete_coverage"] = not missing
    return result


def load_observations(path):
    """Read and identify one immutable input snapshot for a batch of candidates."""
    raw = path.read_bytes()
    return json.loads(raw), hashlib.sha256(raw).hexdigest()


def compare_snapshot(run, document, digest):
    return {**compare(run, document), "observations_sha256": digest}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("receipt", type=Path)
    parser.add_argument("observations", type=Path)
    args = parser.parse_args()
    print(json.dumps(compare_snapshot(json.loads(args.receipt.read_text()), *load_observations(args.observations)), indent=2, allow_nan=False))
