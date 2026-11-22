"""Bind a NeqSim compressor chart to the declared suction conditions of this host."""

import hashlib
import json
import math
from pathlib import Path


def load_chart(path):
    raw = Path(path).read_bytes()
    chart = json.loads(raw)
    if (chart.get("headUnit") != "kJ/kg" or chart.get("flowBasis") != "actual_suction_m3_h"
            or chart.get("minimumFlowBasis") != "surge" or chart.get("origin") not in ("vendor", "synthetic")
            or not isinstance(chart.get("source"), str) or not chart["source"].strip()):
        raise ValueError("Chart requires source, origin, kJ/kg, actual suction flow and explicit surge endpoints")
    for key in ("pressure_bara", "temperature_K", "molar_mass_kg_mol"):
        low, high = chart["suction_envelope"][key]
        if not (0 < low <= high and math.isfinite(high)):
            raise ValueError("Invalid chart suction envelope")
    curves = sorted(chart["speedCurves"], key=lambda c: c["speed_rpm"])
    if len(curves) < 3 or len({c["speed_rpm"] for c in curves}) != len(curves):
        raise ValueError("Three distinct speed lines are required for this map")
    for c in curves:
        flows, heads, efficiencies = (c[k] for k in ("flow_m3h", "head_kJkg", "polytropicEfficiency_pct"))
        if not (math.isfinite(c["speed_rpm"]) and c["speed_rpm"] > 0 and len(flows) >= 3
                and len(flows) == len(heads) == len(efficiencies)
                and all(0 < v and math.isfinite(v) for v in flows + heads + efficiencies)
                and all(a < b for a, b in zip(flows, flows[1:])) and max(efficiencies) <= 100):
            raise ValueError("Invalid speed line, flow points, head or efficiency")
    chart["speedCurves"] = curves
    return chart, hashlib.sha256(raw).hexdigest()


def assess_point(machine, chart, suction, flow):
    """Bound the native speed solution and both adjacent digitized speed lines."""
    conditions = {"pressure_bara": suction.getPressure("bara"), "temperature_K": suction.getTemperature("K"),
                  "molar_mass_kg_mol": suction.getFluid().getMolarMass()}
    for key, value in conditions.items():
        low, high = chart["suction_envelope"][key]
        if not low <= value <= high:
            raise ValueError(f"Compressor suction {key} outside chart envelope: {value}")
    speed = machine.getSpeed()
    curves = chart["speedCurves"]
    if not curves[0]["speed_rpm"] <= speed <= curves[-1]["speed_rpm"]:
        raise ValueError("Compressor speed outside chart envelope")
    low = next(c for c in reversed(curves) if c["speed_rpm"] <= speed)
    high = next(c for c in curves if c["speed_rpm"] >= speed)
    if any(not c["flow_m3h"][0] <= flow <= c["flow_m3h"][-1] for c in (low, high)):
        raise ValueError("Suction flow outside the bracketing speed lines")
    # By input contract each first point is a supplied surge endpoint. Interpolate
    # these points at the solved speed, rather than choosing the nearest speed.
    weight = ((speed - low["speed_rpm"]) / (high["speed_rpm"] - low["speed_rpm"])) if low != high else 0.
    surge = low["flow_m3h"][0] + weight * (high["flow_m3h"][0] - low["flow_m3h"][0])
    discharge = machine.getOutletStream().getPressure("bara")
    head = machine.getPolytropicHead("kJ/kg")
    if not (abs(discharge - 100.) <= .01 and math.isfinite(head) and head > 0):
        raise ValueError("Compressor map failed the host discharge duty")
    return {"speed_rpm": speed, "head_kJ_kg": head, "surge_flow_actual_m3_h": surge,
            "screening_surge_margin": flow / surge - 1, **conditions}
