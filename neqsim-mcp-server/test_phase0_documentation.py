#!/usr/bin/env python3
"""Check that current Phase 0 documentation matches canonical source accounting."""

import re
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
SOURCE_PATH = REPOSITORY_ROOT / "src/main/java/neqsim/mcp/runners/McpEvidenceInventory.java"
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
require(source, 'inventory.addProperty("inventoryVersion", "1.41")', SOURCE_PATH)
require(
    source,
    "All 71 tools have coverage records; 41 are CONTRACT_TESTED and 10 remain "
    "CONFIRMED_GAP.",
    SOURCE_PATH,
)
require(source, 'case "runSIL":', SOURCE_PATH)
require(source, 'case "compareProcesses":', SOURCE_PATH)
require(source, 'case "runBarrierRegister":', SOURCE_PATH)
require(source, 'case "runOperationalStudy":', SOURCE_PATH)

surface = SURFACE_PATH.read_text(encoding="utf-8")
require(
    surface,
    "scientifically validated: 10 records remain\n"
    "`CONFIRMED_GAP`, forty-one are `CONTRACT_TESTED`",
    SURFACE_PATH,
)

foundation = FOUNDATION_PATH.read_text(encoding="utf-8")
contract_line = next(
    (
        line
        for line in foundation.splitlines()
        if line.startswith("- Forty-one bounded software contracts")
    ),
    None,
)
if contract_line is None:
    raise AssertionError(f"{FOUNDATION_PATH}: missing current 41-contract summary")
contract_tools = re.findall(r"`([A-Za-z][A-Za-z0-9]+)`", contract_line)
if len(contract_tools) != 41 or "runSIL" not in contract_tools or "runBarrierRegister" not in contract_tools or "runOperationalStudy" not in contract_tools or "compareProcesses" not in contract_tools:
    raise AssertionError(
        f"{FOUNDATION_PATH}: expected 41 named contracts including runSIL, runBarrierRegister, runOperationalStudy, and compareProcesses, "
        f"found {len(contract_tools)}"
    )
require(foundation, "- 10 tools remain `CONFIRMED_GAP`", FOUNDATION_PATH)

api_reference = API_PATH.read_text(encoding="utf-8")
require(
    api_reference,
    "all 71 tools\nhave coverage records, but only 20 have tool-specific trust pages; "
    "41 generic-fallback tools have\nbounded `CONTRACT_TESTED` evidence and "
    "10 remain `CONFIRMED_GAP`",
    API_PATH,
)

plugin_contract = PLUGIN_PATH.read_text(encoding="utf-8")
require(
    plugin_contract,
    "Current inventory `1.41 / 20 explicit + 41 contract-tested + 10\n"
    "confirmed gaps`",
    PLUGIN_PATH,
)

print("Phase 0 documentation accounting is consistent: 1.41 / 20 + 41 + 10")
