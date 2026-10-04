#!/usr/bin/env python3
"""Build a conservative, reproducible agent/MCP coverage inventory.

Source mentions are discovery evidence, never proof of execution or qualification.
The denominator is public top-level Java source types, not engineering operations.
Run with --check in CI; regenerate after changing sources, skills or registrations.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = Path("devtools/engineering_capabilities.json")
OUTPUT = Path("src/main/resources/neqsim/mcp/engineering-coverage.json")
LEXICAL = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'', re.S)
OPERATION_CLASSIFICATIONS = ("supported", "internal", "experimental", "deprecated")
TEST_SOURCE_PREFIXES = ("src/test/java/", "neqsim-mcp-server/tests/")


def java_types(root):
    """Inventory public primary types; ignore comments, literals and nested types."""
    result = {}
    for path in sorted((root / "src/main/java/neqsim").rglob("*.java")):
        source = path.read_text(encoding="utf-8")
        text = LEXICAL.sub(" ", source)
        package = re.search(r"\bpackage\s+([\w.]+)\s*;", text)
        pattern = r"\bpublic\s+(?:(?:abstract|final|strictfp)\s+)*(class|interface|enum)\s+" + re.escape(path.stem) + r"\b"
        declaration = re.search(pattern, text)
        if not package or not declaration:
            continue
        # A matching nested type is not a primary public API.
        before = text[:declaration.start()]
        if before.count("{") != before.count("}"):
            continue
        name = package.group(1) + "." + path.stem
        result[name] = {"id": name, "kind": declaration.group(1),
                        "source": path.relative_to(root).as_posix(),
                        "sourceDigest": hashlib.sha256(source.encode()).hexdigest(),
                        "domain": name.split(".")[1], "skills": [], "agents": [],
                        "capabilities": [], "disposition": "review_required"}
    return result


def validate_operation(root, row, operation, apis):
    """Validate an explicit operation contract without broadening runtime authority."""
    required = ("id", "classification", "api", "method", "signature", "units",
                "applicability", "route", "example", "evidenceSources")
    if not all(operation.get(field) for field in required):
        raise ValueError("incomplete operation contract: " + str(operation.get("id", "<missing>")))
    if operation["classification"] not in OPERATION_CLASSIFICATIONS:
        raise ValueError("unknown operation classification: " + operation["classification"])
    if operation["api"] not in row["apis"] or operation["api"] not in apis:
        raise ValueError("operation API is not a registered anchor: " + operation["api"])
    if operation["api"] + "." + operation["method"] + "(" not in operation["signature"]:
        raise ValueError("operation signature does not match API and method: " + operation["id"])
    source = (root / apis[operation["api"]]["source"]).read_text(encoding="utf-8")
    method_pattern = r"\bpublic\s+static\s+\S+\s+" + re.escape(operation["method"]) + r"\s*\("
    if not re.search(method_pattern, LEXICAL.sub(" ", source)):
        raise ValueError("operation method is not a public static source method: " + operation["id"])
    if not isinstance(operation["units"], dict) or not operation["units"]:
        raise ValueError("operation units must be a non-empty object: " + operation["id"])
    example = operation["example"]
    if (not isinstance(example, dict) or not isinstance(example.get("arguments"), list)
            or not isinstance(example.get("parameterTypes"), list)
            or len(example["arguments"]) != len(example["parameterTypes"])
            or "expected" not in example or not isinstance(example.get("absoluteTolerance"), (int, float))
            or example["absoluteTolerance"] < 0):
        raise ValueError("invalid operation example: " + operation["id"])
    evidence = operation["evidenceSources"]
    if not isinstance(evidence, list) or not evidence or any(path not in row["testSources"] for path in evidence):
        raise ValueError("operation evidence must be declared test sources: " + operation["id"])


def build(root):
    """Resolve registrations and source mentions without inferring executable APIs."""
    apis = java_types(root)
    names = {}
    for fqn in apis:
        names.setdefault(fqn.rsplit(".", 1)[-1], []).append(fqn)
    skill_paths = {}
    skill_mentions = {}
    for path in sorted((root / ".github/skills").glob("*/SKILL.md")):
        name = path.parent.name
        skill_paths[name] = path.relative_to(root).as_posix()
        text = path.read_text(encoding="utf-8")
        tokens = set(re.findall(r"\b[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*", text))
        mentioned = set()
        for token in tokens:
            if token in apis:
                mentioned.add(token)
            elif token in names and len(names[token]) == 1:
                mentioned.add(names[token][0])
        skill_mentions[name] = mentioned
        for fqn in mentioned:
            apis[fqn]["skills"].append(name)
    agents = {}
    for path in sorted((root / ".github/agents").glob("*.agent.md")):
        text = path.read_text(encoding="utf-8")
        front = text.split("---", 2)[1] if text.startswith("---") else ""
        match = re.search(r"(?m)^required_skills:\s*\n((?:- [^\n]+\n)*)", front)
        required = re.findall(r"(?m)^- (\S+)", match.group(1)) if match else []
        handle = path.name.removesuffix(".agent.md")
        agents[handle] = required
        for skill in required:
            for fqn in skill_mentions.get(skill, []):
                apis[fqn]["agents"].append(handle)

    registry = json.loads((root / REGISTRY).read_text(encoding="utf-8"))
    bindings = (root / "src/main/java/neqsim/mcp/runners/McpImplementationInventory.java").read_text()
    tools = set(re.findall(r'bind\(implementations,\s*"(\w+)"', bindings))
    seen = set()
    operation_ids = set()
    operation_counts = {name: 0 for name in OPERATION_CLASSIFICATIONS}
    for row in registry["capabilities"]:
        identifier = row["id"]
        if identifier in seen:
            raise ValueError("duplicate capability: " + identifier)
        seen.add(identifier)
        if row["tool"] not in tools:
            raise ValueError("unknown MCP tool: " + row["tool"])
        if not row["apis"] or not row["skills"] or not row["agents"] or not row["limitations"]:
            raise ValueError("incomplete registration: " + identifier)
        for fqn in row["apis"]:
            if fqn not in apis:
                raise ValueError("unknown API: " + fqn)
            apis[fqn]["capabilities"].append(identifier)
            apis[fqn]["disposition"] = "registered_anchor"
        for skill in row["skills"]:
            if skill not in skill_paths:
                raise ValueError("unknown skill: " + skill)
        for agent in row["agents"]:
            if agent not in agents:
                raise ValueError("unknown agent: " + agent)
        for path in row["testSources"]:
            candidate = root / path
            if not path.startswith(TEST_SOURCE_PREFIXES) or not candidate.is_file():
                raise ValueError("missing test source: " + path)
        for operation in row.get("operations", []):
            validate_operation(root, row, operation, apis)
            if operation["id"] in operation_ids:
                raise ValueError("duplicate operation: " + operation["id"])
            operation_ids.add(operation["id"])
            operation_counts[operation["classification"]] += 1
        # Presence is deliberately separate from an exact-build execution receipt.
        row["evidence"] = {"route": "declared", "testSources": "present",
                           "execution": "not_recorded", "engineeringQualification": "not_assessed"}
    for api in apis.values():
        api["agents"] = sorted(set(api["agents"]))
        if api["id"].startswith("neqsim.mcp.") and not api["capabilities"]:
            api["disposition"] = "infrastructure"
    rows = sorted(apis.values(), key=lambda r: r["id"])
    domains = {}
    for row in rows:
        counts = domains.setdefault(row["domain"], {"publicTypes": 0, "registeredAnchors": 0,
                                                   "withSkillMention": 0, "reviewRequired": 0})
        counts["publicTypes"] += 1
        counts["registeredAnchors"] += bool(row["capabilities"])
        counts["withSkillMention"] += bool(row["skills"])
        counts["reviewRequired"] += row["disposition"] == "review_required"
    data = {"schemaVersion": "1.0", "complete": False,
            "denominator": "public top-level Java source types; not methods or engineering operations",
            "interpretation": "Mentions and declared routes do not prove execution, full class exposure or engineering qualification. Explicit supported operations are bounded to their recorded signatures, units, applicability and examples.",
            "summary": {"publicTypes": len(rows), "registeredCapabilities": len(seen),
                        "classifiedOperations": len(operation_ids),
                        "supportedOperations": operation_counts["supported"],
                        "internalOperations": operation_counts["internal"],
                        "experimentalOperations": operation_counts["experimental"],
                        "deprecatedOperations": operation_counts["deprecated"],
                        "registeredAnchors": sum(bool(r["capabilities"]) for r in rows),
                        "withSkillMention": sum(bool(r["skills"]) for r in rows),
                        "withAgentMention": sum(bool(r["agents"]) for r in rows),
                        "reviewRequired": sum(r["disposition"] == "review_required" for r in rows),
                        "domains": domains},
            "capabilities": sorted(registry["capabilities"], key=lambda r: r["id"]), "apis": rows}
    encoded = json.dumps(data, sort_keys=True, separators=(",", ":")).encode()
    data["catalogDigest"] = hashlib.sha256(encoded).hexdigest()
    return data


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)
    data = build(ROOT)
    # One API per line keeps regeneration diffs reviewable across domain PRs.
    header = {key: value for key, value in data.items() if key != "apis"}
    rendered = json.dumps(header, ensure_ascii=True, indent=2)[:-1].rstrip() + ',\n  "apis": [\n'
    rendered += ",\n".join("    " + json.dumps(row, ensure_ascii=True, separators=(",", ":"))
                           for row in data["apis"])
    rendered += "\n  ]\n}\n"
    path = ROOT / OUTPUT
    if args.check:
        if not path.exists() or path.read_text() != rendered:
            print("Engineering coverage is stale; run python devtools/build_engineering_coverage.py")
            return 1
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(rendered, encoding="utf-8")
    print(json.dumps(data["summary"], indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
