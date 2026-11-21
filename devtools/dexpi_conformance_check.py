#!/usr/bin/env python3
"""Check which DEXPI version and profile a file is and whether it conforms.

Layers, each reported separately so one green layer never stands in for another:

1. ``identity``: pure-Python classification (DEXPI 2.0.x Plant/Process, Proteus 1.x, other)
   from the Core import URI, never from the file name.
2. ``neqsimAssessment``: the pinned official schema and model definitions bundled with NeqSim
   (``Dexpi20ConformanceAssessment``), run through the NeqSim JVM.
3. ``pyDexpi``: independent check with pyDEXPI (https://github.com/process-intelligence-research/pyDEXPI),
   the community reference implementation of DEXPI 2.0.1: full load plus its rule engine
   (connectivity, composition tree, ports, mandatory attributes).

None of these is a DEXPI certificate or a named CAE round trip.

pyDEXPI needs Python 3.12+ and is never installed here. Provide it with
``uv tool install "pydexpi>=2.1"`` (or ``--pydexpi`` / ``PYDEXPI`` pointing at its executable).
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

CREATE_NO_WINDOW = 0x08000000 if sys.platform == "win32" else 0
CORE_VERSION = re.compile(r"/models/(\d+\.\d+\.\d+)/Core\.xml$")
INSTALL_HINT = 'uv tool install "pydexpi>=2.1"'


def identify(path: Path) -> dict:
    """Classify a file by its root element and model imports."""
    root = ET.parse(path).getroot()
    tag = root.tag.rsplit("}", 1)[-1]
    if tag == "PlantModel":
        return {
            "kind": "PROTEUS_1X",
            "specificationVersion": None,
            "profile": None,
            "note": "Proteus XML (DEXPI 1.x) is not a DEXPI 2.0.x document",
        }
    if tag != "Model":
        return {"kind": "UNKNOWN", "specificationVersion": None, "profile": None,
                "note": f"Unexpected root element {root.tag}"}
    imports = {
        item.attrib.get("prefix"): item.attrib.get("source", "")
        for item in root.findall("Import")
    }
    match = CORE_VERSION.search(imports.get("Core", ""))
    version = match.group(1) if match else None
    profile = "Plant" if "Plant" in imports else "Process" if "Process" in imports else None
    if version is None or profile is None:
        return {"kind": "UNKNOWN", "specificationVersion": version, "profile": profile,
                "imports": imports, "note": "No official Core and Plant/Process imports found"}
    return {"kind": "DEXPI_2X", "specificationVersion": version, "profile": profile,
            "imports": imports}


def run_neqsim_assessment(path: Path, identity: dict, project_root: Path | None) -> dict:
    """Run the Java conformance assessment bound to the file bytes."""
    if identity["kind"] != "DEXPI_2X" or identity["specificationVersion"] not in ("2.0.0", "2.0.1"):
        return {"status": "NOT_APPLICABLE",
                "reason": "NeqSim bundles only the 2.0.0 and 2.0.1 definitions"}
    import neqsim_dev_setup

    ns = neqsim_dev_setup.neqsim_init(project_root=str(project_root) if project_root else None,
                                      verbose=False)
    assessment = ns.JClass("neqsim.process.processmodel.dexpi.Dexpi20ConformanceAssessment")
    profile_enum = ns.JClass("neqsim.process.processmodel.dexpi.Dexpi20ConformanceAssessment$Profile")
    paths = ns.JClass("java.nio.file.Paths")
    profile = profile_enum.PLANT_P_ID if identity["profile"] == "Plant" else profile_enum.PROCESS_PFD_BFD
    report = assessment.assess(paths.get(str(path.resolve()), []), profile,
                               identity["specificationVersion"] == "2.0.1")
    return json.loads(str(report.toJson()))


def find_pydexpi(explicit: Path | None) -> str | None:
    """Locate the pyDEXPI executable without installing anything."""
    for candidate in (explicit, os.environ.get("PYDEXPI")):
        if candidate and Path(candidate).is_file():
            return str(candidate)
    return shutil.which("pydexpi")


def _pydexpi_version(executable: str) -> str | None:
    """Best-effort version from the interpreter that sits next to the executable."""
    folder = Path(executable).resolve().parent
    for name in ("python.exe", "python"):
        python = folder / name
        if python.is_file():
            try:
                done = subprocess.run(
                    [str(python), "-c", "import importlib.metadata as m;print(m.version('pydexpi'))"],
                    capture_output=True, text=True, timeout=60, check=False,
                    creationflags=CREATE_NO_WINDOW)
            except (OSError, subprocess.TimeoutExpired):
                return None
            return done.stdout.strip() or None
    return None


def run_pydexpi(path: Path, executable: str | None, fail_on: str = "mandatory",
                timeout: int = 600) -> dict:
    """Run ``pydexpi validate --json`` (load plus all rules) and summarise the findings."""
    if not executable:
        return {"status": "NOT_RUN", "reason": f"pydexpi not found; install with: {INSTALL_HINT}"}
    command = [executable, "validate", str(path.resolve()), "--json", "--fail-on", fail_on]
    try:
        completed = subprocess.run(command, capture_output=True, text=True, encoding="utf-8",
                                   errors="replace", timeout=timeout, check=False,
                                   creationflags=CREATE_NO_WINDOW)
        report = json.loads(completed.stdout)
    except (OSError, subprocess.TimeoutExpired, json.JSONDecodeError) as exc:
        return {"status": "NOT_RUN", "reason": f"pydexpi did not produce a report: {exc}"}
    loader = report.get("loader", [])
    issues = report.get("issues", [])
    loader_errors = [item for item in loader if item.get("level") in ("error", "critical")]
    by_rule: dict = {}
    for issue in issues:
        key = (issue.get("level"), issue.get("rule"))
        by_rule[key] = by_rule.get(key, 0) + 1
    clean = report.get("loaded", False) and completed.returncode == 0 and not loader_errors
    return {
        "status": "PASSED" if clean else "ISSUES_FOUND",
        "version": _pydexpi_version(executable),
        "loaded": report.get("loaded", False),
        "failOn": fail_on,
        "issueCount": len(issues),
        "loaderErrorCount": len(loader_errors),
        "loaderWarningCount": sum(1 for item in loader if item.get("level") == "warning"),
        "issuesByRule": [{"level": level, "rule": rule, "count": count}
                         for (level, rule), count in sorted(by_rule.items(), key=str)],
        "issues": issues[:50],
        "loaderErrors": loader_errors[:20],
        "tool": "https://github.com/process-intelligence-research/pyDEXPI",
    }


def verdict(report: dict, expect_version: str | None) -> tuple[str, list[str]]:
    """Combine the layers into one conservative verdict with reasons."""
    reasons = []
    identity = report["identity"]
    if identity["kind"] != "DEXPI_2X":
        reasons.append(f"File is {identity['kind']}: {identity.get('note', '')}".strip())
    elif expect_version and identity["specificationVersion"] != expect_version:
        reasons.append(
            f"Expected DEXPI {expect_version} but the Core import is {identity['specificationVersion']}")
    neqsim = report["neqsimAssessment"]
    if neqsim.get("status") == "NONCONFORMANT":
        reasons.append("NeqSim schema/profile assessment is NONCONFORMANT")
    if report["pyDexpi"].get("status") == "ISSUES_FOUND":
        reasons.append("pyDEXPI reported load errors or rule violations")
    if reasons:
        return "FAIL", reasons
    if neqsim.get("status") != "CONFORMANT":
        return "UNVERIFIED", ["The NeqSim schema/profile assessment did not run or does not apply"]
    if report["pyDexpi"].get("status") == "NOT_RUN":
        return "PASS_NEQSIM_ONLY", ["Independent validator pyDEXPI was not run"]
    return "PASS", []


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("file", type=Path)
    parser.add_argument("--expect-version", help="require this Core version, for example 2.0.1")
    parser.add_argument("--pydexpi", type=Path, help="pyDEXPI executable (default: PYDEXPI or PATH)")
    parser.add_argument("--fail-on", default="mandatory",
                        choices=("mandatory", "recommended", "optional"),
                        help="lowest pyDEXPI rule level that fails the run")
    parser.add_argument("--project-root", type=Path, help="NeqSim checkout for the JVM classpath")
    parser.add_argument("--no-java", action="store_true", help="skip the NeqSim JVM assessment")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    try:
        identity = identify(args.file)
    except (OSError, ET.ParseError) as exc:
        print(f"DEXPI conformance check failed: {exc}", file=sys.stderr)
        return 2
    report = {
        "profile": "neqsim_dexpi_conformance_check.v2",
        "file": str(args.file),
        "identity": identity,
        "neqsimAssessment": {"status": "NOT_RUN", "reason": "--no-java"} if args.no_java
        else run_neqsim_assessment(args.file, identity, args.project_root),
        "pyDexpi": run_pydexpi(args.file, find_pydexpi(args.pydexpi), args.fail_on)
        if identity["kind"] == "DEXPI_2X"
        else {"status": "NOT_RUN", "reason": "not a DEXPI 2.0.x document"},
        "notCertified": "Not a DEXPI EV certificate and not a named CAE round trip",
    }
    overall, reasons = verdict(report, args.expect_version)
    report["verdict"] = overall
    report["reasons"] = reasons
    rendered = json.dumps(report, indent=2) + "\n"
    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")
    return 0 if overall.startswith("PASS") else 1


if __name__ == "__main__":
    raise SystemExit(main())
