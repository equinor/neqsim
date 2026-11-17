"""Read-only local MCP entry points for the produced-water compatibility study."""

import os
from pathlib import Path
from threading import Lock
from typing import Any

from mcp.server.fastmcp import FastMCP

from study import MINERALS, SCHEMA, run_study

server = FastMCP("NeqSim produced-water compatibility")
simulation_lock = Lock()


@server.tool()
def produced_water_case_schema() -> dict[str, Any]:
    """Return the exact schema and units for the produced-water compatibility case."""
    return SCHEMA


@server.tool()
def screen_produced_water(case: dict[str, Any], blend_ids: list[int] | None = None) -> dict[str, Any]:
    """Return worst mineral SIs per node and CO2 boundary, with optional rows for up to five blend IDs."""
    if blend_ids and (len(blend_ids) > 5 or min(blend_ids) < 1):
        raise ValueError("Request at most five positive blend IDs")
    database = Path(os.environ["PRODUCED_WATER_PITZER_DATABASE"])
    with simulation_lock:
        result = run_study(case, database)
    rows = result.pop("rows")
    result.pop("case")
    if blend_ids and set(blend_ids) - {row["blend_id"] for row in rows}:
        raise ValueError("Requested blend ID is outside this case")
    summary = []
    for node in case["nodes"]:
        for boundary in ("fixed_eos_co2", "closed_dic"):
            subset = [row for row in rows if row["name"] == node["name"] and row["boundary"] == boundary]
            maxima = {}
            for mineral in MINERALS:
                field = f"si_{mineral}"
                worst = max((row for row in subset if row[field] is not None),
                            key=lambda row: row[field], default=None)
                maxima[mineral] = None if worst is None else {
                    "si": worst[field], "blend_id": worst["blend_id"],
                    "water_fractions": {f["name"]: worst[f"solvent_water_fraction_{f['name']}"]
                                        for f in case["formations"]},
                }
            summary.append({**node, "boundary": boundary, "worst_mineral_si": maxima})
    return {**result, "row_count": len(rows), "node_summary": summary,
            "rows": [row for row in rows if row["blend_id"] in (blend_ids or ())]}


if __name__ == "__main__":
    server.run(transport="stdio")
