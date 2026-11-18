"""Local read-only MCP study over completed NeqSim restart receipts."""

import json
import os
from pathlib import Path

from mcp.server.fastmcp import FastMCP

from decision import assess, challenge, score
from restart_observations import compare_snapshot, load_observations

server = FastMCP("NeqSim production restart decision")


def completed_runs():
    """Load the explicitly configured run manifest, never a path from a client request."""
    manifest = Path(os.environ["NEQSIM_RESTART_MANIFEST"])
    paths = json.loads(manifest.read_text(encoding="utf-8"))
    if not isinstance(paths, list) or not 1 <= len(paths) <= 120:
        raise ValueError("Expected a manifest listing between 1 and 120 run receipts")
    return [json.loads((manifest.parent / p).read_text(encoding="utf-8")) for p in paths]


def restart_quantity_evidence() -> dict:
    """Return decision quantities and separate effects of mesh, timestep and fluid description."""
    return assess(completed_runs())


def restart_challenge(power_limit_kw: float, surge_map_available: bool = True) -> dict:
    """Give an agent the coarse evidence for the synthetic restart challenge."""
    return challenge(completed_runs(), power_limit_kw, surge_map_available=surge_map_available)


def score_restart_choice(power_limit_kw: float, choice: str, surge_map_available: bool = True) -> dict:
    """After the agent commits fixed, ramp or withhold, score against withheld refinements."""
    return score(completed_runs(), power_limit_kw, choice, surge_map_available)


def restart_observation_evidence() -> dict:
    """Compare the configured event measurements with each matching frozen replay."""
    path = Path(os.environ["NEQSIM_RESTART_OBSERVATIONS"])
    document, digest = load_observations(path)
    matching = [r for r in completed_runs() if r["configuration"].get("event_id") == document["event_id"]
                and r["configuration"]["policy"] == document["policy"]]
    if not matching:
        raise ValueError("No matching frozen event and policy in the configured manifest")
    return {"comparisons": [{"configuration": r["configuration"], "comparison": compare_snapshot(r, document, digest)}
                            for r in matching]}


if __name__ == "__main__":
    mode = os.environ.get("NEQSIM_RESTART_MODE", "review")
    if mode == "challenge":
        server.tool()(restart_challenge)
    elif mode == "review":
        server.tool()(restart_quantity_evidence)
        server.tool()(score_restart_choice)
        if os.environ.get("NEQSIM_RESTART_OBSERVATIONS"):
            server.tool()(restart_observation_evidence)
    else:
        raise ValueError("NEQSIM_RESTART_MODE must be challenge or review")
    server.run(transport="stdio")
