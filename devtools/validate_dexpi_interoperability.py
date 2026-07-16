#!/usr/bin/env python3
"""Validate a generated NeqSim DEXPI package with optional external importers."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def validate_package(package: Path, commercial_result: Path | None) -> dict:
    native = package / "plant.dexpi.xml"
    proteus = package / "plant-pydexpi.xml"
    report = {
        "profile": "neqsim_dexpi_interoperability.v1",
        "nativeDexpi": {"file": native.name, "status": "FAILED"},
        "pyDexpi": {"file": proteus.name, "status": "NOT_AVAILABLE"},
        "commercialCae": {"status": "QUALIFICATION_REQUIRED"},
    }
    root = ET.parse(native).getroot()
    if root.tag != "Model":
        raise ValueError(f"Unexpected native DEXPI root: {root.tag}")
    imports = {item.attrib.get("prefix"): item.attrib.get("source") for item in root.findall("Import")}
    if not imports.get("Core") or not imports.get("Plant"):
        raise ValueError("Native DEXPI document does not import Core and Plant models")
    report["nativeDexpi"] = {
        "file": native.name,
        "status": "STRUCTURE_PASSED",
        "objectCount": len(root.findall(".//Object")),
    }

    try:
        from pydexpi.loaders import ProteusSerializer
    except ImportError:
        pass
    else:
        ProteusSerializer().load(str(package), proteus.name)
        report["pyDexpi"] = {
            "file": proteus.name,
            "status": "IMPORT_PASSED",
            "importer": "pyDEXPI ProteusSerializer",
        }

    if commercial_result is not None:
        evidence = json.loads(commercial_result.read_text(encoding="utf-8"))
        required = {"tool", "version", "importStatus", "roundTripStatus", "evidenceReference"}
        missing = sorted(required.difference(evidence))
        if missing:
            raise ValueError(f"Commercial CAE evidence is missing: {', '.join(missing)}")
        if evidence["importStatus"] != "PASSED" or evidence["roundTripStatus"] != "PASSED":
            raise ValueError("Commercial CAE import and round-trip statuses must both be PASSED")
        report["commercialCae"] = evidence
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("package", type=Path)
    parser.add_argument("--commercial-cae-result", type=Path)
    parser.add_argument("--require-pydexpi", action="store_true")
    parser.add_argument("--require-commercial-cae", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        report = validate_package(args.package, args.commercial_cae_result)
        if args.require_pydexpi and report["pyDexpi"]["status"] != "IMPORT_PASSED":
            raise ValueError("pyDEXPI is required but is not installed")
        if args.require_commercial_cae and report["commercialCae"].get("status") != "PASSED":
            if report["commercialCae"].get("roundTripStatus") != "PASSED":
                raise ValueError("Passing commercial CAE evidence is required")
        rendered = json.dumps(report, indent=2) + "\n"
        if args.output:
            args.output.write_text(rendered, encoding="utf-8")
        else:
            print(rendered, end="")
        return 0
    except (OSError, ET.ParseError, ValueError, json.JSONDecodeError) as exc:
        print(f"DEXPI interoperability validation failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
