"""Local read-only MCP study over completed NeqSim restart receipts."""

import json
import os
from pathlib import Path

from mcp.server.fastmcp import FastMCP

from decision import assess, challenge, score

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


if __name__ == "__main__":
    mode = os.environ.get("NEQSIM_RESTART_MODE", "review")
    if mode == "challenge":
        server.tool()(restart_challenge)
    elif mode == "review":
        server.tool()(restart_quantity_evidence)
        server.tool()(score_restart_choice)
    else:
        raise ValueError("NEQSIM_RESTART_MODE must be challenge or review")
    server.run(transport="stdio")
