"""Independently audit every retained endpoint and the frozen coupled matrix."""
import json
import math
from pathlib import Path
import sys

MESHES = [8, 16, 32, 64]
METRICS = ["maxPressureErrorBara", "maxWaterErrorM3", "componentBalanceErrorMol",
           "storagePressureErrorBara", "finalPoreClosureM3"]


def _finite_tree(value):
    """Reject non-finite or nonnumeric payloads before numerical comparisons."""
    if isinstance(value, dict):
        return all(_finite_tree(item) for item in value.values())
    if isinstance(value, list):
        return all(_finite_tree(item) for item in value)
    return type(value) in (int, float) and math.isfinite(value)


def audit(path, reference_path=None):
    """Recompute endpoint evidence; retain explicit failures rather than trusting summaries."""
    records = json.loads(path.read_text())
    reference_path = reference_path or Path(__file__).with_name("reference.json")
    reference = json.loads(reference_path.read_text())
    failures = []
    cases = []
    endpoints = 0
    total_seconds = 0.0

    def fail(where, quantity, actual=None):
        failures.append({"where": where, "quantity": quantity, "actual": actual})

    def result():
        return {"replays": len(records) if isinstance(records, list) else 0,
                "endpoints": endpoints, "failures": failures,
                "totalReplaySeconds": total_seconds, "cases": cases}

    if not isinstance(records, list) or len(records) != 64:
        fail("matrix", "requires 64 replay records")
        return result()
    if not _finite_tree(records):
        fail("matrix", "requires finite numeric payloads")
        return result()
    if len(reference["cases"]) != 16 or not _finite_tree(reference):
        fail("reference", "requires 16 finite reference cases")
        return result()
    n0 = reference["n0"]
    attraction, covolume = reference["a"], reference["b"]
    for index, record in enumerate(records):
        where = "replay " + str(index)
        expected_case = reference["cases"][index // 4]
        count = MESHES[index % 4]
        required = ["capacityRatio", "productivityRatio", "fraction", "intervals", "seconds", "rows"] + METRICS
        if not isinstance(record, dict) or any(key not in record for key in required):
            fail(where, "missing replay fields")
            continue
        identity = [record[key] for key in ["capacityRatio", "productivityRatio", "fraction"]]
        expected = [expected_case[key] for key in ["capacityRatio", "productivityRatio", "producedFraction"]]
        if identity != expected or record["intervals"] != count:
            fail(where, "case identity or interval mesh mismatch")
            continue
        rows = record["rows"]
        if not isinstance(rows, list) or len(rows) != count or any(not isinstance(row, list) or len(row) != 5 for row in rows):
            fail(where, "incomplete endpoint array")
            continue
        if any(record[key] < 0 for key in METRICS + ["seconds"]):
            fail(where, "negative absolute diagnostic")
        total_seconds += record["seconds"]
        pressure_error = 0.0
        water_error = 0.0
        capacity, productivity, fraction = identity
        for row_index, row in enumerate(rows):
            time, pressure, water, aquifer_pressure, amount = row
            endpoints += 1
            point = where + " endpoint " + str(row_index)
            if abs(time - 10000 * (row_index + 1) / count) > 1e-8:
                fail(point, "elapsed time")
            if amount <= 0 or pressure <= 0 or aquifer_pressure <= 0 or not 0 <= water < 1000:
                fail(point, "physical state bounds")
                continue
            if abs(amount - n0 * (1 - fraction * (row_index + 1) / count)) > 1e-6:
                fail(point, "independent named methane balance")
            molar_volume = (1000 - water) / amount
            if molar_volume <= covolume:
                fail(point, "SRK covolume domain")
                continue
            eos_pressure = (8.3144621 * 400 / (molar_volume - covolume)
                            - attraction / (molar_volume * (molar_volume + covolume))) / 1e5
            if abs(pressure - eos_pressure) > 1e-6:
                fail(point, "independent SRK pressure closure")
            expected_aquifer = 200 - water / (capacity * 5) if capacity > 0 else 200
            if capacity == 0 and productivity > 0:
                expected_aquifer = pressure
            if abs(aquifer_pressure - expected_aquifer) > 1e-9:
                fail(point, "aquifer storage pressure closure")
            if (capacity == 0 or productivity == 0) and abs(water) > 1e-8:
                fail(point, "zero-support water limit")
            target = expected_case["rows"][(row_index + 1) * 64 // count - 1]
            pressure_error = max(pressure_error, abs(pressure - target[0]))
            water_error = max(water_error, abs(water - target[1]))
        if abs(record["maxPressureErrorBara"] - pressure_error) > 1e-10:
            fail(where, "reported pressure maximum differs from endpoints")
        if abs(record["maxWaterErrorM3"] - water_error) > 1e-10:
            fail(where, "reported water maximum differs from endpoints")

    # Apply the original acceptance bounds only after independently checking all endpoints.
    if failures:
        return result()
    for start in range(0, len(records), 4):
        fine, coarse = records[start + 3], records[start + 2]
        for key, tolerance in [("maxPressureErrorBara", .01), ("maxWaterErrorM3", .05),
                               ("componentBalanceErrorMol", 1e-6), ("storagePressureErrorBara", 1e-9),
                               ("finalPoreClosureM3", 1e-4)]:
            if fine[key] > tolerance:
                fail(start // 4, key, fine[key])
        ratios = {}
        if fine["capacityRatio"] != 0 and fine["productivityRatio"] != 0:
            for key in ["maxPressureErrorBara", "maxWaterErrorM3"]:
                ratio = fine[key] / coarse[key] if coarse[key] else None
                ratios[key] = ratio
                if fine[key] >= 1e-7 and (ratio is None or ratio > .4):
                    fail(start // 4, key + " refinement", ratio)
        cases.append({key: value for key, value in fine.items() if key not in ["rows", "seconds"]})
        cases[-1]["ratios64to32"] = ratios
    return result()


if __name__ == "__main__":
    result = audit(Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).with_name("native.json"))
    print(json.dumps(result, indent=2, allow_nan=False))
    sys.exit(bool(result["failures"]))
