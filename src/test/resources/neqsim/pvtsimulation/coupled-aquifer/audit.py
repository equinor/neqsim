"""Audit the frozen coupled SRK/aquifer matrix without adjusting inputs or tolerances."""
import json
from pathlib import Path
import sys


def audit(path):
    """Retain complete failures and per-case refinement summaries."""
    records = json.loads(path.read_text())
    assert len(records) == 64
    failures = []
    cases = []
    for start in range(0, len(records), 4):
        rows = records[start:start + 4]
        assert [r["intervals"] for r in rows] == [8, 16, 32, 64]
        fine, coarse = rows[-1], rows[-2]
        for key, tolerance in [("maxPressureErrorBara", .01), ("maxWaterErrorM3", .05),
                               ("componentBalanceErrorMol", 1e-6), ("storagePressureErrorBara", 1e-9), ("finalPoreClosureM3", 1e-4)]:
            if fine[key] > tolerance:
                failures.append({"case": start // 4, "quantity": key, "actual": fine[key], "limit": tolerance})
        ratios = {}
        if fine["capacityRatio"] != 0 and fine["productivityRatio"] != 0:
            for key in ["maxPressureErrorBara", "maxWaterErrorM3"]:
                ratio = fine[key] / coarse[key]
                ratios[key] = ratio
                if fine[key] >= 1e-7 and ratio > .4:
                    failures.append({"case": start // 4, "quantity": key, "ratio": ratio, "limit": .4})
        cases.append({key: value for key, value in fine.items() if key not in ["rows", "seconds"]})
        cases[-1]["ratios64to32"] = ratios
    return {"replays": 64, "endpoints": sum(len(r["rows"]) for r in records), "failures": failures,
            "totalReplaySeconds": sum(r["seconds"] for r in records), "cases": cases}


if __name__ == "__main__":
    result = audit(Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).with_name("native.json"))
    print(json.dumps(result, indent=2))
    sys.exit(bool(result["failures"]))
