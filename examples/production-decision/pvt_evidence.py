"""Evaluate measured density rows for the actual feed used by a restart run."""

import hashlib
import json
import math
from pathlib import Path


def validate_manifest(document, fluid_digest):
    """Require matched fluid, separate observations and fixed per-row acceptance criteria."""
    if document.get("fluid_sha256") != fluid_digest:
        raise ValueError("Laboratory observations and restart feed must share a fluid identifier")
    rows = document["observations"]
    ids = {row["id"] for row in rows}
    if len(ids) != len(rows) or {r["role"] for r in rows} != {"calibration", "holdout"}:
        raise ValueError("Require unique observation IDs and separate calibration and held-out rows")
    points = set()
    for r in rows:
        key = (r["temperature_K"], r["pressure_bara"], r["phase"])
        if key in points:
            raise ValueError("Repeated state/phase between observations")
        points.add(key)
        if r["phase"] not in ("gas", "oil", "aqueous") or not str(r["source"]).strip():
            raise ValueError("Each phase density needs a defined phase and a source")
        for field in ("temperature_K", "pressure_bara", "density_kg_m3", "tolerance_kg_m3"):
            if not math.isfinite(r[field]) or r[field] <= 0:
                raise ValueError("Positive finite state, measurement and tolerance required")
    return rows


def check(n, model, path, fluid_digest):
    """Validate two frozen candidates independently from the facility calculation."""
    raw = Path(path).read_bytes()
    rows = validate_manifest(json.loads(raw), fluid_digest)
    observed = []
    for row in rows:
        fluid = model.clone()
        fluid.setTemperature(row["temperature_K"], "K")
        fluid.setPressure(row["pressure_bara"], "bara")
        n.thermodynamicoperations.ThermodynamicOperations(fluid).TPflash()
        fluid.initProperties()
        prediction = None
        if fluid.hasPhaseType(row["phase"]):
            prediction = fluid.getPhase(row["phase"]).getDensity("kg/m3")
        passed = (prediction is not None and math.isfinite(prediction)
                  and abs(prediction - row["density_kg_m3"]) <= row["tolerance_kg_m3"])
        observed.append({**row, "prediction_kg_m3": prediction, "within_tolerance": passed})
    return {"file_sha256": hashlib.sha256(raw).hexdigest(), "fluid_sha256": fluid_digest,
            "all_density_rows_accepted": all(r["within_tolerance"] for r in observed), "rows": observed}
