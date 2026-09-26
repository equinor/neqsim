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


def require_groups(text, pattern, expected, path, label):
    """Require a parsed current-state summary to match canonical accounting."""
    match = re.search(pattern, text, re.MULTILINE)
    if match is None:
        raise AssertionError(f"{path}: missing {label}")
    if match.groups() != expected:
        raise AssertionError(
            f"{path}: stale {label}: {match.groups()!r}; expected {expected!r}"
        )


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
require(source, 'inventory.addProperty("inventoryVersion", "1.46")', SOURCE_PATH)
require(
    source,
    "All 71 tools have coverage records; 46 are CONTRACT_TESTED and 5 remain "
    "CONFIRMED_GAP.",
    SOURCE_PATH,
)
require(source, 'case "runSIL":', SOURCE_PATH)
require(source, 'case "compareProcesses":', SOURCE_PATH)
require(source, 'case "runBarrierRegister":', SOURCE_PATH)
require(source, 'case "runRelief":', SOURCE_PATH)
require(source, 'case "runOperationalStudy":', SOURCE_PATH)
require(source, 'case "runProcessLoop":', SOURCE_PATH)
require(source, 'case "designUtilities":', SOURCE_PATH)
require(source, 'case "runChemistry":', SOURCE_PATH)
require(source, 'case "runFlareNetwork":', SOURCE_PATH)

# Every focused protocol harness must freeze the same inventory as the primary
# harness. Otherwise CI stops at the first stale promotion and never qualifies
# the remaining tools.
expected_inventory = {
    "inventoryVersion": "1.46",
    "contractTestedToolCount": 46,
    "confirmedGapToolCount": 5,
}
for focused_path in sorted(PROTOCOL_PATH.parent.glob("test_*_protocol.py")):
    focused_tree = ast.parse(focused_path.read_text(encoding="utf-8"))
    for node in ast.walk(focused_tree):
        if not isinstance(node, ast.Compare) or len(node.ops) != 1:
            continue
        call = node.left
        if (
            not isinstance(node.ops[0], ast.Eq)
            or not isinstance(call, ast.Call)
            or not isinstance(call.func, ast.Attribute)
            or call.func.attr != "get"
            or not call.args
            or not isinstance(call.args[0], ast.Constant)
        ):
            continue
        field = call.args[0].value
        if field not in expected_inventory:
            continue
        actual = ast.literal_eval(node.comparators[0])
        if actual != expected_inventory[field]:
            raise AssertionError(
                f"{focused_path}:{node.lineno}: stale {field} expectation "
                f"{actual!r}; expected {expected_inventory[field]!r}"
            )

surface = SURFACE_PATH.read_text(encoding="utf-8")
require_groups(
    surface,
    r"as the primary harness: version `([^`]+)`, (\d+) contract-tested tools "
    r"and (\d+) confirmed\ngaps\.",
    ("1.46", "46", "5"),
    SURFACE_PATH,
    "focused-harness inventory summary",
)
require_groups(
    surface,
    r"\| Trust coverage records \| 71 = 20 explicit benchmark \+ "
    r"(\d+) bounded contract-tested software contracts \+ (\d+) confirmed gaps \|",
    ("46", "5"),
    SURFACE_PATH,
    "trust-coverage table row",
)
require_groups(
    surface,
    r"No candidate is queued in inventory ([0-9.]+);",
    ("1.46",),
    SURFACE_PATH,
    "promotion-candidate version",
)
require_groups(
    surface,
    r"now reconciles inventory ([0-9.]+) with 20/(\d+)/(\d+) coverage accounting",
    ("1.46", "46", "5"),
    SURFACE_PATH,
    "API-inspection reconciliation",
)
require(
    surface,
    "among its forty-six bounded software contracts and requires 5 confirmed gaps.",
    SURFACE_PATH,
)
require(surface, f"| MCP protocol scenarios | {protocol_scenario_count} |", SURFACE_PATH)
require(surface, f"{protocol_scenario_count} named scenarios", SURFACE_PATH)
require(
    surface,
    "scientifically validated: 5 records remain\n"
    "`CONFIRMED_GAP`, forty-six are `CONTRACT_TESTED`",
    SURFACE_PATH,
)

foundation = FOUNDATION_PATH.read_text(encoding="utf-8")
contract_line = next(
    (
        line
        for line in foundation.splitlines()
        if line.startswith("- Forty-six bounded software contracts")
    ),
    None,
)
if contract_line is None:
    raise AssertionError(f"{FOUNDATION_PATH}: missing current 46-contract summary")
contract_tools = re.findall(r"`([A-Za-z][A-Za-z0-9]+)`", contract_line)
if len(contract_tools) != 46 or "runSIL" not in contract_tools or "runBarrierRegister" not in contract_tools or "runRelief" not in contract_tools or "runOperationalStudy" not in contract_tools or "compareProcesses" not in contract_tools or "runProcessLoop" not in contract_tools or "designUtilities" not in contract_tools or "runChemistry" not in contract_tools or "runFlareNetwork" not in contract_tools:
    raise AssertionError(
        f"{FOUNDATION_PATH}: expected 46 named contracts including runSIL, runBarrierRegister, runRelief, runOperationalStudy, compareProcesses, runProcessLoop, designUtilities, and runChemistry, runFlareNetwork, "
        f"found {len(contract_tools)}"
    )
require(foundation, "- 5 tools remain `CONFIRMED_GAP`", FOUNDATION_PATH)

api_reference = API_PATH.read_text(encoding="utf-8")
require(
    api_reference,
    "all 71 tools\nhave coverage records, but only 20 have tool-specific trust pages; "
    "46 generic-fallback tools have\nbounded `CONTRACT_TESTED` evidence and "
    "5 remain `CONFIRMED_GAP`",
    API_PATH,
)

plugin_contract = PLUGIN_PATH.read_text(encoding="utf-8")
require(
    plugin_contract,
    "Current inventory `1.46 / 20 explicit + 46 contract-tested + 5\n"
    "confirmed gaps`",
    PLUGIN_PATH,
)

print(
    "Phase 0 documentation accounting is consistent: 1.46 / 20 + 46 + 5; "
    f"{protocol_scenario_count} primary protocol scenarios"
)
