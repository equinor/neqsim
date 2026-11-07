#!/usr/bin/env python3
"""Inventory code health and reject new findings against a reviewed baseline.

Source rules are deliberately narrow. Size, branch-token counts and duplication
are triage signals, not proof of poor design or numerical qualification. PMD and
SpotBugs reports are required when selected; missing/invalid reports fail closed.
"""
from __future__ import annotations

import argparse
import ast
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
BASELINE = Path(".config/code-health-baseline.json")
TOOLS = {"source", "pmd", "spotbugs"}
JAVA_NOISE = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'', re.S)
IMPORT = re.compile(r"^\s*import\s+(?:static\s+)?([\w.*]+)\s*;", re.M)
CORE = ("src/main/java/neqsim/thermo/", "src/main/java/neqsim/thermodynamicoperations/")
FORBIDDEN = ("neqsim.process.", "neqsim.mcp.", "neqsim.util.agentic.")


def java_code(text: str) -> str:
    """Remove comments/literals while preserving line coordinates."""
    return JAVA_NOISE.sub(lambda m: " " + "\n" * m.group().count("\n"), text)


def finding(tool: str, rule: str, path: str, evidence: str, line: int = 0) -> dict:
    """Build a line-independent identity with a display-only source location."""
    return {"tool": tool, "rule": rule, "path": path,
            "evidence": " ".join(evidence.split()), "line": line}


def identity(item: dict) -> tuple:
    """Keep repeated identical findings as a counted multiset."""
    return tuple(item[key] for key in ("tool", "rule", "path", "evidence"))


def inventory(root: Path) -> tuple[list, list, dict]:
    """Measure current sources; classify only explicit import/logging violations."""
    metrics, findings = [], []
    fingerprints = defaultdict(list)
    paths = sorted((root / "src/main/java").rglob("*.java"))
    paths += sorted(p for p in (root / "devtools").glob("*.py") if not p.name.startswith("test_"))
    if not paths:
        raise ValueError("No Java or devtools sources found; use a source checkout")
    for path in paths:
        if path.is_symlink():
            raise ValueError("Source symlink is not supported: " + str(path))
        relative = path.relative_to(root).as_posix()
        text = path.read_text(encoding="utf-8")
        if path.suffix == ".java":
            code = java_code(text)
            imports = list(IMPORT.finditer(code))
            branches = len(re.findall(r"\b(?:if|for|while|case|catch)\b|&&|\|\||\?", code))
            for match in imports:
                target = match.group(1)
                line = code.count("\n", 0, match.start()) + 1
                if relative.startswith(CORE) and target.startswith(FORBIDDEN):
                    findings.append(finding("source", "CORE_LAYER_DEPENDENCY", relative, target, line))
                if target.startswith("Jama."):
                    findings.append(finding("source", "JAMA_IMPORT", relative, target, line))
            for match in re.finditer(r"\bSystem\s*\.\s*(?:out|err)\s*\.", code):
                line = code.count("\n", 0, match.start()) + 1
                value = code[match.start():].split(";", 1)[0]
                findings.append(finding("source", "JAVA_CONSOLE_OUTPUT", relative, value, line))
            dependencies = len(imports)
            # Exact normalized windows are advisory candidates, not clone detection.
            significant = [(i, " ".join(value.split())) for i, value in enumerate(code.splitlines(), 1)
                           if len(value.strip()) > 8 and not value.strip().startswith(("import ", "package "))]
            for index in range(max(0, len(significant) - 11)):
                window = significant[index:index + 12]
                digest = hashlib.sha256("\n".join(v for _, v in window).encode()).hexdigest()
                fingerprints[digest].append((relative, window[0][0]))
        else:
            tree = ast.parse(text, filename=relative)
            branches = sum(isinstance(n, (ast.If, ast.For, ast.AsyncFor, ast.While,
                                         ast.ExceptHandler, ast.IfExp, ast.BoolOp)) for n in ast.walk(tree))
            dependencies = sum(isinstance(n, (ast.Import, ast.ImportFrom)) for n in ast.walk(tree))
        metrics.append({"path": relative, "lines": len(text.splitlines()),
                        "branch_tokens": branches, "imports": dependencies})
    duplicates = []
    for digest, occurrences in fingerprints.items():
        by_file = dict(occurrences)
        if len(by_file) > 1:
            duplicates.append({"fingerprint": digest, "files": len(by_file),
                               "examples": [{"path": p, "line": n} for p, n in sorted(by_file.items())[:3]]})
    duplicates.sort(key=lambda x: (-x["files"], x["fingerprint"]))
    return metrics, findings, {"method": "exact normalized 12-line Java windows; overlapping candidates",
                               "candidate_windows": len(duplicates), "examples": duplicates[:10]}


def report_path(value: str, root: Path) -> str:
    """Normalize analyzer source paths without machine-specific absolute paths."""
    path = Path(value.replace("\\", "/"))
    if path.is_absolute():
        return path.resolve().relative_to(root.resolve()).as_posix()
    normalized = path.as_posix()
    if ".." in path.parts:
        raise ValueError("Analyzer path escapes source root: " + value)
    return normalized if normalized.startswith("src/") else "src/main/java/" + normalized


def read_analyzer(path: Path, tool: str, root: Path) -> list:
    """Read high-priority PMD/SpotBugs findings and reject failed analysis."""
    tree = ET.parse(path).getroot()
    tag = lambda node: node.tag.rsplit("}", 1)[-1]
    expected = "pmd" if tool == "pmd" else "BugCollection"
    if tag(tree) != expected:
        raise ValueError(f"Expected {expected} report: {path}")
    if tool == "spotbugs":
        summary = next((n for n in tree if tag(n) == "FindBugsSummary"), None)
        if summary is None or int(summary.get("total_classes", "0")) <= 0:
            raise ValueError("SpotBugs report lacks a completed class inventory")
        reported = int(summary.get("total_bugs", "-1"))
        if reported != sum(tag(n) == "BugInstance" for n in tree):
            raise ValueError("SpotBugs finding count does not match its summary")
    for node in tree.iter():
        if tag(node).lower() in {"error", "processingerror", "configerror", "missingclass"}:
            raise ValueError(f"{tool} analysis incomplete: {ET.tostring(node, encoding='unicode')[:250]}")
        if tag(node) == "Errors" and (int(node.get("errors", "0")) or int(node.get("missingClasses", "0"))):
            raise ValueError("SpotBugs reported analysis errors")
    findings = []
    if tool == "pmd":
        for file in tree:
            if tag(file) != "file":
                continue
            source = report_path(file.attrib["name"], root)
            for node in file:
                if tag(node) == "violation" and int(node.attrib["priority"]) <= 2:
                    findings.append(finding(tool, node.attrib["rule"], source,
                                            "P" + node.attrib["priority"] + ":" + " ".join(node.itertext()),
                                            int(node.get("beginline", "0"))))
    else:
        for bug in tree:
            if tag(bug) != "BugInstance" or int(bug.attrib["priority"]) > 2:
                continue
            sources = [n for n in bug.iter() if tag(n) == "SourceLine" and n.get("sourcepath")]
            if not sources:
                raise ValueError("SpotBugs finding has no source path")
            source = next((n for n in sources if n.get("primary") == "true"), sources[0])
            annotations = []
            for node in bug:
                if tag(node) in {"Class", "Method", "Field", "Type"}:
                    annotations.append(tag(node) + ":" + ":".join(node.get(k, "") for k in
                                                                         ("classname", "name", "signature", "descriptor")))
            findings.append(finding(tool, bug.attrib["type"], report_path(source.attrib["sourcepath"], root),
                                    "P" + bug.attrib["priority"] + ":" + "|".join(annotations),
                                    int(source.get("start", "0"))))
    return findings


def baseline_counts(data: dict, tools: set) -> Counter:
    """Validate a baseline instead of accepting malformed/unknown policy."""
    if data.get("schema_version") != 1 or not isinstance(data.get("findings"), list):
        raise ValueError("Unsupported or malformed baseline")
    if not tools <= set(data.get("tools", [])):
        raise ValueError("Baseline does not cover selected tools")
    counts = Counter()
    for item in data["findings"]:
        count = item.get("count")
        if type(count) is not int or count <= 0 or item.get("tool") not in TOOLS:
            raise ValueError("Invalid baseline finding")
        if item["tool"] in tools:
            key = identity(item)
            if key in counts:
                raise ValueError("Duplicate baseline entry")
            counts[key] = count
    return counts


def make_baseline(findings: list, tools: set, revision: str) -> dict:
    """Create a reviewable baseline; never update it during normal checks."""
    counts = Counter(identity(item) for item in findings)
    return {"schema_version": 1, "source_revision": revision, "tools": sorted(tools),
            "findings": [dict(zip(("tool", "rule", "path", "evidence"), key), count=count)
                         for key, count in sorted(counts.items())]}


def check_baseline(findings: list, data: dict, tools: set) -> tuple[list, list]:
    """Report introduced and resolved occurrences separately."""
    old = baseline_counts(data, tools)
    current = Counter(identity(item) for item in findings)
    pack = lambda counts: [dict(zip(("tool", "rule", "path", "evidence"), key), count=count)
                           for key, count in sorted(counts.items())]
    return pack(current - old), pack(old - current)


def churn(root: Path, paths: set, limit: int) -> dict:
    """Report bounded history availability explicitly; never invent missing churn."""
    command = ["git", "log", f"-{limit}", "--format=", "--name-only", "--", "src/main/java", "devtools"]
    result = subprocess.run(command, cwd=root, text=True, capture_output=True, check=False)
    if result.returncode:
        return {"status": "unavailable", "reason": "Git history unavailable"}
    counts = Counter(p for p in result.stdout.splitlines() if p in paths)
    return {"status": "available", "commit_limit": limit,
            "changes": dict(sorted(counts.items()))}


def main(argv=None) -> int:
    """Emit deterministic evidence and a nonzero status for new/incomplete checks."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--baseline", type=Path, default=BASELINE)
    parser.add_argument("--tools", default="source", help="source,pmd,spotbugs")
    parser.add_argument("--pmd", type=Path)
    parser.add_argument("--spotbugs", type=Path)
    parser.add_argument("--jacoco", type=Path)
    parser.add_argument("--history", type=int, default=0, help="Optional bounded Git history")
    parser.add_argument("--write-baseline", action="store_true")
    parser.add_argument("--revision", default="unspecified")
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    report = {"schema_version": 1, "status": "failed", "errors": [], "new_findings": []}
    try:
        tools = set(args.tools.split(","))
        if not tools or not tools <= TOOLS or args.history < 0:
            raise ValueError("Invalid tools or history limit")
        metrics, source, duplicates = inventory(args.root)
        findings = source if "source" in tools else []
        for tool in sorted(tools - {"source"}):
            path = getattr(args, tool)
            if path is None:
                raise ValueError("Required analyzer report missing: " + tool)
            findings.extend(read_analyzer(path, tool, args.root))
        analyzers = {tool: ET.parse(getattr(args, tool)).getroot().get("version", "unspecified")
                     for tool in sorted(tools - {"source"})}
        analysis_runtime = None
        if "spotbugs" in tools:
            summary = next(n for n in ET.parse(args.spotbugs).getroot()
                           if n.tag.rsplit("}", 1)[-1] == "FindBugsSummary")
            analysis_runtime = {key: summary.get(key) for key in ("java_version", "vm_version")}
            if not all(analysis_runtime.values()):
                raise ValueError("SpotBugs report lacks analysis-runtime metadata")
        baseline = args.baseline if args.baseline.is_absolute() else args.root / args.baseline
        report.update(tools=sorted(tools), analyzers=analyzers, source_files=len(metrics), findings=len(findings),
                      findings_by_tool=dict(sorted(Counter(f["tool"] for f in findings).items())),
                      hotspots=sorted(metrics, key=lambda m: (-m["branch_tokens"], m["path"]))[:10],
                      duplication=duplicates, metrics=metrics,
                      coverage={"status": "not_measured"}, churn={"status": "not_requested"})
        if analysis_runtime:
            report["analysis_runtime"] = analysis_runtime
        if args.jacoco:
            coverage = ET.parse(args.jacoco).getroot()
            if coverage.tag != "report":
                raise ValueError("Invalid JaCoCo report")
            report["coverage"] = {"status": "measured", "counters": [dict(n.attrib) for n in coverage.findall("counter")]}
        if args.history:
            report["churn"] = churn(args.root, {m["path"] for m in metrics}, args.history)
        if args.write_baseline:
            if args.revision == "unspecified":
                raise ValueError("Record --revision when creating a baseline")
            baseline.parent.mkdir(parents=True, exist_ok=True)
            data = make_baseline(findings, tools, args.revision)
            data["analyzers"] = analyzers
            if analysis_runtime:
                data["analysis_runtime"] = analysis_runtime
            baseline.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
            report.update(status="baseline_written")
        else:
            data = json.loads(baseline.read_text(encoding="utf-8"))
            for tool, version in analyzers.items():
                if data.get("analyzers", {}).get(tool, version) != version:
                    raise ValueError("Analyzer version differs from reviewed baseline: " + tool)
            if analysis_runtime:
                expected_runtime = data.get("analysis_runtime")
                if (not isinstance(expected_runtime, dict)
                        or set(expected_runtime) != {"java_version", "vm_version"}
                        or not all(isinstance(v, str) and v.strip() for v in expected_runtime.values())):
                    raise ValueError("Invalid SpotBugs analysis-runtime baseline metadata")
                if analysis_runtime != expected_runtime:
                    raise ValueError("SpotBugs analysis runtime differs from reviewed baseline: expected "
                                     + json.dumps(expected_runtime, sort_keys=True)
                                     + "; use the pinned CI JDK before comparing findings")
            introduced, resolved = check_baseline(findings, data, tools)
            report.update(new_findings=introduced, resolved_findings=resolved,
                          status="passed" if not introduced else "failed")
    except (OSError, ValueError, KeyError, TypeError, ET.ParseError, SyntaxError) as error:
        report["errors"].append(str(error))
    payload = json.dumps(report, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(payload, encoding="utf-8")
    if args.json:
        print(payload, end="")
    else:
        print(f"Code health: {report['status']}; {len(report['new_findings'])} new finding groups")
        if "findings_by_tool" in report:
            print("Selected findings: " + json.dumps(report["findings_by_tool"], sort_keys=True))
            for metric in report["hotspots"][:5]:
                print(f"Triage: {metric['path']} ({metric['lines']} lines, {metric['branch_tokens']} branch tokens)")
        for error in report["errors"]:
            print(error, file=sys.stderr)
        for item in report["new_findings"][:20]:
            print(f"{item['tool']}:{item['rule']} {item['path']}: {item['evidence']}")
    return 0 if report["status"] in {"passed", "baseline_written"} and not report["errors"] else 1


if __name__ == "__main__":
    sys.exit(main())
