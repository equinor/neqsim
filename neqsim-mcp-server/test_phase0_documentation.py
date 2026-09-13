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
require(source, 'inventory.addProperty("inventoryVersion", "1.38")', SOURCE_PATH)
require(
    source,
    "All 71 tools have coverage records; 38 are CONTRACT_TESTED and 13 remain "
    "CONFIRMED_GAP.",
    SOURCE_PATH,
)
require(source, 'case "runSIL":', SOURCE_PATH)

surface = SURFACE_PATH.read_text(encoding="utf-8")
require(
    surface,
    "scientifically validated: 13 records remain\n"
    "`CONFIRMED_GAP`, thirty-eight are `CONTRACT_TESTED`",
    SURFACE_PATH,
)

foundation = FOUNDATION_PATH.read_text(encoding="utf-8")
contract_line = next(
    (
        line
        for line in foundation.splitlines()
        if line.startswith("- Thirty-eight bounded software contracts")
    ),
    None,
)
if contract_line is None:
    raise AssertionError(f"{FOUNDATION_PATH}: missing current 38-contract summary")
contract_tools = re.findall(r"`([A-Za-z][A-Za-z0-9]+)`", contract_line)
if len(contract_tools) != 38 or "runSIL" not in contract_tools:
    raise AssertionError(
        f"{FOUNDATION_PATH}: expected 38 named contracts including runSIL, "
        f"found {len(contract_tools)}"
    )
require(foundation, "- 13 tools remain `CONFIRMED_GAP`", FOUNDATION_PATH)

api_reference = API_PATH.read_text(encoding="utf-8")
require(
    api_reference,
    "all 71 tools\nhave coverage records, but only 20 have tool-specific trust pages; "
    "38 generic-fallback tools have\nbounded `CONTRACT_TESTED` evidence and "
    "13 remain `CONFIRMED_GAP`",
    API_PATH,
)

plugin_contract = PLUGIN_PATH.read_text(encoding="utf-8")
require(
    plugin_contract,
    "Current inventory `1.38 / 20 explicit + 38 contract-tested + 13\n"
    "confirmed gaps`",
    PLUGIN_PATH,
)

print("Phase 0 documentation accounting is consistent: 1.38 / 20 + 38 + 13")
