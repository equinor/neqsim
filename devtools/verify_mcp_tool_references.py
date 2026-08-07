#!/usr/bin/env python3
"""Verify that MCP tool names referenced by skills and agents actually exist.

Skills and agents hardcode NeqSim MCP tool names (``runOperationalStudy``,
``runRelief``, ``composeWorkflow``, ...). Renaming or removing a tool silently
breaks those documents, and the breakage only surfaces at runtime when an agent
tries to call a tool that no longer exists.

This linter extracts the authoritative tool surface from
``IndustrialProfile.java`` and checks every referenced name against it. It scans
the neqsim repo plus, when present, the sibling skills and agents repositories
that are normally open alongside it in the same workspace.

Usage::

    python devtools/verify_mcp_tool_references.py
    python devtools/verify_mcp_tool_references.py --root ../neqsim-enterprise-skills
    python devtools/verify_mcp_tool_references.py --list-tools

Exit code 1 when an unknown tool name is referenced.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

INDUSTRIAL_PROFILE = (
    REPO_ROOT / "src" / "main" / "java" / "neqsim" / "mcp" / "runners" / "IndustrialProfile.java"
)

# Sibling repositories that reference MCP tools by name.
SIBLING_REPOS = [
    "neqsim-community-skills",
    "neqsim-community-agents",
    "neqsim-enterprise-skills",
    "neqsim-enterprise-agents",
    "engineering-harness",
]

SCAN_SUFFIXES = {".md", ".json", ".yaml", ".yml", ".py"}

SKIP_DIRS = {
    ".git",
    ".venv",
    "node_modules",
    "target",
    "__pycache__",
    "build",
    "dist",
    ".mypy_cache",
    ".pytest_cache",
    "site-packages",
}

# ``mcp_neqsim_runFlash`` / ``mcp_neqsim2_runFlash`` — the VS Code tool-id form.
MCP_TOOL_ID = re.compile(r"\bmcp_neqsim2?_([A-Za-z][A-Za-z0-9]*)\b")

# Backticked camelCase names such as `runOperationalStudy`. Only considered when the same line
# also mentions MCP, otherwise it is a Java or Python method name in an example rather than a
# tool reference.
BACKTICKED = re.compile(
    r"`((?:run|get|set|list|manage|compose|validate|size|compare|design|solve|bridge|save|"
    r"check|diagnose|stream|search|generate)[A-Z]\w*)`")

MENTIONS_MCP = re.compile(r"\bMCP\b", re.IGNORECASE)

# Names that look like tools but are Java/Python methods, not MCP tools.
KNOWN_NON_TOOLS = {
    "runTransient",
    "runUntilConverged",
    "runUntilConvergedJson",
    "getUtilizationSnapshot",
    "getOperatingPoint",
    "checkScalePotential",
    "getReport",
    "getResults",
    "getValue",
    "setValue",
    "runAll",
    "runCase",
    "runStudy",
    "getStatus",
    "evaluate",
    "run",
}


def extract_tool_surface(source: Path) -> set[str]:
    """Read the authoritative tool names from the governance tier sets."""
    if not source.exists():
        raise SystemExit(f"Cannot find governance source: {source}")
    text = source.read_text(encoding="utf-8")
    tools: set[str] = set()
    for field in ("INDUSTRIAL_CORE", "ENGINEERING_ADVANCED", "EXPERIMENTAL_TOOLS"):
        match = re.search(rf"{field}\s*=\s*Collections[\s\S]*?;", text)
        if not match:
            raise SystemExit(f"Could not locate {field} in {source}")
        tools.update(re.findall(r'"([A-Za-z][A-Za-z0-9]*)"', match.group(0)))
    if not tools:
        raise SystemExit("Extracted an empty tool surface — check IndustrialProfile.java format")
    return tools


def iter_files(root: Path):
    """Yield scannable files under a root, skipping build and vendor directories."""
    for path in root.rglob("*"):
        if path.suffix.lower() not in SCAN_SUFFIXES:
            continue
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        yield path


def find_references(path: Path) -> set[tuple[str, int, str]]:
    """Return (tool, line number, form) references found in one file."""
    try:
        text = path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return set()
    found: set[tuple[str, int, str]] = set()
    for lineno, line in enumerate(text.splitlines(), start=1):
        for match in MCP_TOOL_ID.finditer(line):
            found.add((match.group(1), lineno, "tool-id"))
        if MENTIONS_MCP.search(line):
            for match in BACKTICKED.finditer(line):
                found.add((match.group(1), lineno, "backtick"))
    return found


def main() -> int:
    """Run the linter."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", action="append", default=[],
                        help="Extra directory to scan (repeatable).")
    parser.add_argument("--list-tools", action="store_true",
                        help="Print the authoritative tool surface and exit.")
    parser.add_argument("--strict-backticks", action="store_true",
                        help="Also fail on backticked names that are not known MCP tools.")
    args = parser.parse_args()

    tools = extract_tool_surface(INDUSTRIAL_PROFILE)

    if args.list_tools:
        for tool in sorted(tools):
            print(tool)
        return 0

    roots = [REPO_ROOT / ".github"]
    for sibling in SIBLING_REPOS:
        candidate = REPO_ROOT.parent / sibling
        if candidate.is_dir():
            roots.append(candidate)
    roots.extend(Path(extra).resolve() for extra in args.root)

    errors: list[str] = []
    warnings: list[str] = []
    scanned = 0

    for root in roots:
        if not root.is_dir():
            continue
        for path in iter_files(root):
            scanned += 1
            for tool, lineno, form in sorted(find_references(path)):
                if tool in tools or tool in KNOWN_NON_TOOLS:
                    continue
                location = f"{path}:{lineno}: unknown MCP tool '{tool}'"
                if form == "tool-id":
                    errors.append(location)
                elif args.strict_backticks:
                    errors.append(location)
                else:
                    warnings.append(location)

    print(f"Checked {scanned} files against {len(tools)} published MCP tools "
          f"across {len([r for r in roots if r.is_dir()])} roots.")

    for warning in sorted(set(warnings)):
        print(f"WARNING {warning}")
    for error in sorted(set(errors)):
        print(f"ERROR   {error}")

    if errors:
        print(f"\nFAILED: {len(set(errors))} reference(s) to tools that do not exist.")
        print("Either restore the tool name, add an alias, or update the referencing document.")
        return 1

    print("OK: every referenced MCP tool exists.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
