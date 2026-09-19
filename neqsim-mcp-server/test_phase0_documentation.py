#!/usr/bin/env python3
"""Check that current Phase 0 documentation matches canonical source accounting."""

import ast
import re
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
SOURCE_PATH = REPOSITORY_ROOT / "src/main/java/neqsim/mcp/runners/McpEvidenceInventory.java"
PROTOCOL_PATH = REPOSITORY_ROOT / "neqsim-mcp-server/test_mcp_server.py"
SURFACE_PATH = REPOSITORY_ROOT / "neqsim-mcp-server/docs/SURFACE_INVENTORY.md"
FOUNDATION_PATH = REPOSITORY_ROOT / "neqsim-mcp-server/docs/FOUNDATION_TRACEABILITY.md"
API_PATH = REPOSITORY_ROOT / "neqsim-mcp-server/docs/API_REFERENCE.md"
PLUGIN_PATH = (
    REPOSITORY_ROOT / "neqsim-mcp-server/docs/evidence/PLUGIN_EXECUTION_CONTRACT.md"
)


def require(text, expected, path):
    """Require one current-state phrase in a source or documentation file."""
    if expected not in text:
        raise AssertionError(f"{path}: missing expected current-state text: {expected!r}")


source = SOURCE_PATH.read_text(encoding="utf-8")
# Recount the harness before packaging so newly added scenarios cannot leave
# the published evidence inventory and its documentation silently out of date.
protocol_tree = ast.parse(PROTOCOL_PATH.read_text(encoding="utf-8"))
protocol_scenario_count = sum(
    isinstance(node, ast.FunctionDef) and node.name.startswith("test_")
    for node in protocol_tree.body
)
require(
    source,
    f"int PROTOCOL_SCENARIO_COUNT = {protocol_scenario_count};",
    SOURCE_PATH,
)
require(source, 'inventory.addProperty("inventoryVersion", "1.42")', SOURCE_PATH)
require(
    source,
    "All 71 tools have coverage records; 42 are CONTRACT_TESTED and 9 remain "
    "CONFIRMED_GAP.",
    SOURCE_PATH,
)
require(source, 'case "runSIL":', SOURCE_PATH)
require(source, 'case "compareProcesses":', SOURCE_PATH)
require(source, 'case "runBarrierRegister":', SOURCE_PATH)
require(source, 'case "runRelief":', SOURCE_PATH)
require(source, 'case "runOperationalStudy":', SOURCE_PATH)

surface = SURFACE_PATH.read_text(encoding="utf-8")
require(surface, f"| MCP protocol scenarios | {protocol_scenario_count} |", SURFACE_PATH)
require(surface, f"{protocol_scenario_count} named scenarios", SURFACE_PATH)
require(
    surface,
    "scientifically validated: 9 records remain\n"
    "`CONFIRMED_GAP`, forty-two are `CONTRACT_TESTED`",
    SURFACE_PATH,
)

foundation = FOUNDATION_PATH.read_text(encoding="utf-8")
contract_line = next(
    (
        line
        for line in foundation.splitlines()
        if line.startswith("- Forty-two bounded software contracts")
    ),
    None,
)
if contract_line is None:
    raise AssertionError(f"{FOUNDATION_PATH}: missing current 42-contract summary")
contract_tools = re.findall(r"`([A-Za-z][A-Za-z0-9]+)`", contract_line)
if len(contract_tools) != 42 or "runSIL" not in contract_tools or "runBarrierRegister" not in contract_tools or "runRelief" not in contract_tools or "runOperationalStudy" not in contract_tools or "compareProcesses" not in contract_tools:
    raise AssertionError(
        f"{FOUNDATION_PATH}: expected 42 named contracts including runSIL, runBarrierRegister, runRelief, runOperationalStudy, and compareProcesses, "
        f"found {len(contract_tools)}"
    )
require(foundation, "- 9 tools remain `CONFIRMED_GAP`", FOUNDATION_PATH)

api_reference = API_PATH.read_text(encoding="utf-8")
require(
    api_reference,
    "all 71 tools\nhave coverage records, but only 20 have tool-specific trust pages; "
    "42 generic-fallback tools have\nbounded `CONTRACT_TESTED` evidence and "
    "9 remain `CONFIRMED_GAP`",
    API_PATH,
)

plugin_contract = PLUGIN_PATH.read_text(encoding="utf-8")
require(
    plugin_contract,
    "Current inventory `1.42 / 20 explicit + 42 contract-tested + 9\n"
    "confirmed gaps`",
    PLUGIN_PATH,
)

print(
    "Phase 0 documentation accounting is consistent: 1.42 / 20 + 42 + 9; "
    f"{protocol_scenario_count} primary protocol scenarios"
)
