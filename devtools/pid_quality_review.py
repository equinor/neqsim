#!/usr/bin/env python3
"""Review native professional P&ID packages without granting engineering approval.

Recheck file integrity, retained native assessment, identities and drawing geometry.
Integrate separately supplied visual/standards receipts bound to the exact package.
Those receipts remain reviewer evidence, not machine verification of ISO compliance.
"""

import argparse
from datetime import datetime
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import re
import sys
import xml.etree.ElementTree as ET

import pfd_layout_check


VISUAL_CHECKS = (
    "symbol_meaning", "physical_connections", "sensing_locations",
    "utility_segregation", "continuations", "readability",
)
STANDARD_FAMILIES = (
    "ISO 10628-1", "ISO 10628-2", "ISO 14617", "ISO 5457", "ISO 7200",
    "IEC 62424", "ANSI/ISA-5.1",
)
REQUIRED_FILES = (
    "professional-pid-readiness.json", "pid-design-model.json",
    "pid-simulation-bindings.json", "pid-document-set.json",
    "pid-engineering-registers.json",
    "pid-drawing-content-report.json", "pid-physical-projection.json",
    "plant.dexpi.xml.pid-projection.json", "dexpi-2.0.1-conformance.json",
    "plant.dexpi.xml", "drawings/pid-drawing-set.pdf",
)


def sha256(path):
    """Hash actual artifact bytes, rather than a generator's visual fingerprint."""
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def package_path(root, relative):
    """Resolve an ordinary package file and reject traversal or escaping symlinks."""
    name = PurePosixPath(relative)
    if not relative or "\\" in relative or name.is_absolute() or ".." in name.parts:
        raise ValueError("Unsafe package path: " + relative)
    path = (root / relative).resolve()
    if root not in path.parents or not path.is_file():
        raise ValueError("Missing or escaping package file: " + relative)
    return path


def load_json(path):
    """Load a JSON object; other top-level values cannot be evidence records."""
    value = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("Expected JSON object: " + str(path))
    return value


def finding(report, dimension, code, status, message, subject=""):
    """Append a traceable finding with an explicit bounded review dimension."""
    report["findings"].append({
        "dimension": dimension, "code": code, "status": status,
        "message": message, "subject": subject,
    })


def require(report, condition, dimension, code, message, subject="", missing="FAIL"):
    """Record a check without silently upgrading missing evidence."""
    finding(report, dimension, code, "PASS" if condition else missing, message, subject)


def hull_distance(point, vertices):
    """Measure distance to the actual SVG polygon, including its closing segment."""
    closest = float("inf")
    for a, b in zip(vertices, vertices[1:] + vertices[:1]):
        dx, dy = b[0] - a[0], b[1] - a[1]
        length2 = dx * dx + dy * dy
        factor = 0 if length2 == 0 else max(0, min(1, ((point[0] - a[0]) * dx + (point[1] - a[1]) * dy) / length2))
        closest = min(closest, math.hypot(point[0] - a[0] - factor * dx, point[1] - a[1] - factor * dy))
    return closest


def check_material_line_identity(report, xml, mapping, attachments, layouts, registers):
    """Reconcile actual native auxiliary SegmentNumber, diagram stream IDs and registers."""
    parents = {child: parent for parent in xml.iter() for child in parent}
    objects = {node.get("id"): node for node in xml.iter() if node.get("id")}
    native_tags = {}
    adjacency = {}
    for item in objects.values():
        for child in item:
            if child.get("property") in ("TagName", "PipingComponentNumber", "PositionNumber"):
                native_tags["".join(child.itertext()).strip()] = item
        if item.get("type") == "Plant/Piping.Pipe":
            endpoints = {child.get("property"): child.get("objects", "").lstrip("#") for child in item}
            a, b = endpoints.get("SourceItem"), endpoints.get("TargetItem")
            if a and b:
                adjacency.setdefault(a, set()).add(b)
                adjacency.setdefault(b, set()).add(a)
    registered = {row.get("lineTag"): row for row in registers.get("lines", [])}
    edges = [edge for layout in layouts for edge in layout.get("edges", [])]
    for attachment in attachments:
        if attachment.get("kind") != "AUXILIARY_ROUTE":
            continue
        tag = attachment["pidTag"]
        node = objects.get(mapping.get(tag))
        while node is not None and not node.get("type", "").endswith(".PipingNetworkSegment"):
            node = parents.get(node)
        line = ""
        if node is not None:
            for child in node:
                if child.get("property") == "SegmentNumber":
                    line = "".join(child.itertext()).strip()
            for side in ("Source", "Target"):
                owner_tag = attachment.get("physical" + side + "EquipmentTag")
                if not owner_tag or attachment.get("physical" + side + "NozzleTag"):
                    continue
                owner = native_tags.get(owner_tag)
                if owner is None:
                    require(report, False, "automatic", "AUXILIARY_OWNER_MISSING", "Owned tie-in equipment is present", tag)
                    continue
                owned = {owner.get("id")} if owner.get("type", "").startswith("Plant/Piping.") else {
                    item.get("id") for item in owner.iter() if item.get("type") == "Plant/ProcessEquipment.ProcessNozzle"}
                tees = []
                for pipe in node.iter():
                    if pipe.get("type") != "Plant/Piping.Pipe":
                        continue
                    for child in pipe:
                        if child.get("property") == side + "Item":
                            endpoint = child.get("objects", "").lstrip("#")
                            if objects.get(endpoint, ET.Element("missing")).get("type") == "Plant/Piping.PipeTee":
                                tees.append(endpoint)
                for tee in tees:
                    pending, seen, reached = [tee], set(), False
                    while pending:
                        current = pending.pop()
                        if current in owned:
                            reached = True
                            break
                        if current in seen or objects.get(current, ET.Element("missing")).get("type") != "Plant/Piping.PipeTee":
                            continue
                        seen.add(current)
                        pending.extend(adjacency.get(current, set()) - seen)
                    require(report, reached, "automatic", "AUXILIARY_OWNED_PORT_ANCHOR",
                            "Equipment-side tie-in has no isolation/check device interposed between tee and owned port",
                            tag + ":" + side)
        incident = [edge for edge in edges if edge.get("fluid") in ("process", "utility")
                    and tag in (edge.get("from"), edge.get("to"))]
        require(report, bool(line) and bool(incident) and all(edge.get("stream") == line for edge in incident),
                "automatic", "AUXILIARY_LINE_IDENTITY", "Native material line and actual diagram routes carry the same tag", tag)
        require(report, bool(line) and line in registered, "automatic", "AUXILIARY_LINE_REGISTER",
                "Every materialized auxiliary line has a coordinated register row", line or tag)
        declared = attachment.get("materialLineTag")
        if declared:
            require(report, declared == line, "automatic", "SHARED_MATERIAL_LINE_IDENTITY",
                    "Shared physical projection uses the actual native line tag", tag)


def review_receipt(report, receipt, kind):
    """Check receipt identity and time; this does not authenticate its reviewer."""
    require(report, receipt.get("schemaVersion") == "neqsim_pid_" + kind + "_review.v1",
            kind, "REVIEW_SCHEMA", "Review receipt uses the supported schema")
    require(report, receipt.get("packageManifestSha256") == report["packageManifestSha256"],
            kind, "REVIEW_CONTENT_BINDING", "Receipt belongs to these exact package bytes")
    reviewer = receipt.get("reviewer", "")
    valid_reviewer = isinstance(reviewer, str) and bool(reviewer.strip())
    try:
        stamp = datetime.fromisoformat(str(receipt.get("reviewedAt", "")).replace("Z", "+00:00"))
        valid_time = stamp.tzinfo is not None
    except ValueError:
        valid_time = False
    require(report, valid_reviewer and valid_time, kind, "REVIEW_PROVENANCE",
            "Identify reviewer and review time with timezone", missing="HOLD")


def visual_review(report, receipt, required, hashes):
    """Require artifact-specific observations for the PDF and every final PID SVG."""
    if receipt is None:
        finding(report, "visual", "VISUAL_REVIEW_REQUIRED", "HOLD",
                "Inspect every final P&ID sheet and supply a hash-bound visual receipt")
        return
    review_receipt(report, receipt, "visual")
    rows = receipt.get("artifacts", [])
    if not isinstance(rows, list):
        raise ValueError("Visual artifacts must be an array")
    by_path = {row.get("path"): row for row in rows if isinstance(row, dict)}
    require(report, len(by_path) == len(rows), "visual", "VISUAL_UNIQUE_ARTIFACTS",
            "Each reviewed artifact appears once")
    for path in required:
        row = by_path.get(path, {})
        if not row:
            finding(report, "visual", "VISUAL_SHEET_MISSING", "HOLD", "Sheet was not reviewed", path)
            continue
        require(report, row.get("sha256") == hashes.get(path), "visual", "VISUAL_ARTIFACT_HASH",
                "Visual receipt matches the actual drawing file", path)
        checks = row.get("checks", [])
        if not isinstance(checks, list):
            raise ValueError("Visual checks must be an array")
        by_name = {item.get("name"): item for item in checks if isinstance(item, dict)}
        require(report, len(by_name) == len(checks), "visual", "VISUAL_UNIQUE_CHECKS",
                "Duplicate or malformed observations cannot overwrite a failed visual check", path)
        for name in VISUAL_CHECKS:
            item = by_name.get(name, {})
            status = item.get("status", "HOLD")
            if status not in ("PASS", "HOLD", "FAIL") or not str(item.get("evidence", "")).strip():
                status = "HOLD"
            finding(report, "visual", "VISUAL_" + name.upper(), status,
                    str(item.get("evidence", "Observed review evidence is required")), path)
        verdict = row.get("status", "HOLD")
        finding(report, "visual", "VISUAL_SHEET_VERDICT",
                verdict if verdict in ("PASS", "HOLD", "FAIL") else "HOLD",
                "Reviewer disposition for this drawing", path)


def standards_review(report, receipt, basis):
    """Integrate controlled-reference receipts, preserving missing editions as HOLD."""
    for family in STANDARD_FAMILIES:
        matches = [item for item in basis if item.startswith(family)]
        require(report, bool(matches), "standards", "STANDARD_BASIS_PRESENT",
                "Applicable standard family must be declared or governed as excluded", family, "HOLD")
        for item in matches:
            require(report, bool(re.search(r":\d{4}", item)), "standards", "STANDARD_EDITION",
                    "Record a controlled edition, amendments and applicability", item, "HOLD")
    if receipt is None:
        finding(report, "standards", "CONTROLLED_STANDARDS_REVIEW_REQUIRED", "HOLD",
                "An emitted standard name or an AI assertion is not controlled standards verification")
        return
    review_receipt(report, receipt, "standards")
    require(report, set(receipt.get("scope", [])) >= {"content", "symbols", "pce", "drafting"},
            "standards", "STANDARDS_REVIEW_SCOPE", "Review covers content, symbols, PCE and drafting", missing="HOLD")
    rows = receipt.get("standards", [])
    if not isinstance(rows, list):
        raise ValueError("Standards evidence must be an array")
    by_standard = {row.get("standard"): row for row in rows if isinstance(row, dict)}
    require(report, len(by_standard) == len(rows), "standards", "UNIQUE_STANDARD_REVIEWS",
            "Each controlled standard review appears once")
    for standard in basis:
        row = by_standard.get(standard, {})
        status = row.get("status", "HOLD")
        if status not in ("PASS", "HOLD", "FAIL") or not all(
                isinstance(row.get(key), str) and row[key].strip() for key in ("reference", "evidence")):
            status = "HOLD"
        finding(report, "standards", "CONTROLLED_STANDARD_EVIDENCE", status,
                str(row.get("reference", "Controlled reference and observed evidence required")), standard)


def inspect_package(root, report, visual, standards):
    """Run deterministic package checks; engineering meaning still requires review."""
    manifest_path = package_path(root, "package-manifest.json")
    report["packageManifestSha256"] = sha256(manifest_path)
    manifest = load_json(manifest_path)
    rows = manifest.get("files", [])
    if not isinstance(rows, list) or not rows:
        raise ValueError("Manifest files must be a nonempty array")
    require(report, manifest.get("hashAlgorithm") == "SHA-256" and manifest.get("fileCount") == len(rows),
            "automatic", "MANIFEST_SCHEMA", "Manifest count and hash algorithm are consistent")
    hashes = {}
    for row in rows:
        path = row["path"]
        if path in hashes:
            raise ValueError("Duplicate manifest path: " + path)
        artifact = package_path(root, path)
        hashes[path] = sha256(artifact)
        require(report, row.get("sha256") == hashes[path] and row.get("sizeBytes") == artifact.stat().st_size,
                "automatic", "ARTIFACT_INTEGRITY", "Artifact matches retained manifest bytes", path)
    for name in REQUIRED_FILES:
        if name not in hashes:
            raise ValueError("Required artifact absent from manifest: " + name)
    actual = {p.relative_to(root).as_posix() for p in root.rglob("*") if p.is_file()}
    require(report, actual == set(hashes) | {"package-manifest.json"}, "automatic", "PACKAGE_FILE_COVERAGE",
            "Every package file is retained in the manifest")
    docs = {name: load_json(package_path(root, name)) for name in REQUIRED_FILES if name.endswith(".json")}
    ready = docs["professional-pid-readiness.json"]
    content = docs["pid-drawing-content-report.json"]
    model = docs["pid-design-model.json"]
    native = docs["plant.dexpi.xml.pid-projection.json"]
    physical = docs["pid-physical-projection.json"]
    assessment = docs["dexpi-2.0.1-conformance.json"]
    report["projectId"] = ready.get("projectId")
    report["revision"] = ready.get("revision")
    require(report, bool(report["projectId"]) and all(
        item.get("projectId") == report["projectId"] for item in (model, content, docs["pid-simulation-bindings.json"])),
        "automatic", "PROJECT_IDENTITY", "Project identity agrees across source/model/bindings/drawing")
    require(report, content.get("revision") == report["revision"] == docs["pid-simulation-bindings.json"].get("revision"),
            "automatic", "REVISION_IDENTITY", "Drawing and binding revisions agree")
    fingerprint = content.get("sourceGraphFingerprint")
    require(report, bool(fingerprint) and docs["pid-document-set.json"].get("sourceGraphFingerprint") == fingerprint
            and all(row.get("sourceGraphFingerprint") == fingerprint for row in content.get("drawingIndex", [])),
            "automatic", "SOURCE_GRAPH_IDENTITY", "Source-area and rendered-sheet graph identities agree")
    require(report, ready.get("generationStatus") == "GENERATED_FOR_REVIEW"
            and ready.get("documentModelValid") is True and ready.get("drawingHasNoErrors") is True
            and ready.get("drawingHasUnresolvedWarnings") is False,
            "automatic", "NATIVE_DRAWING_GENERATION", "Native generation reports no technical drawing error")
    require(report, ready.get("drawingProfile") == "ISO_10628_BASIS_NATIVE", "design", "SUPPORTED_DRAWING_PROFILE",
            "This gate supports the native professional profile; other layouts require a scoped review", missing="HOLD")
    require(report, assessment.get("dexpiSpecificationVersion") == ready.get("dexpiVersion") == "2.0.1"
            and assessment.get("fileSha256") == hashes["plant.dexpi.xml"]
            and assessment.get("status") == "CONFORMANT" and not assessment.get("errors")
            and all(assessment.get(key) is True for key in (
                "officialSchemaVerified", "schemaValid", "officialModelImportsValid", "neqsimSemanticProfileValid")),
            "automatic", "EXACT_NATIVE_ASSESSMENT", "Retained 2.0.1 assessment applies to these final XML bytes")
    raw_xml = package_path(root, "plant.dexpi.xml").read_bytes()
    if b"<!DOCTYPE" in raw_xml.upper() or b"<!ENTITY" in raw_xml.upper():
        raise ValueError("External XML entity declarations are prohibited")
    xml = ET.fromstring(raw_xml)
    xml_ids = {value for node in xml.iter() for key, value in node.attrib.items()
               if key in ("id", "ID", "{http://www.w3.org/XML/1998/namespace}id")}
    elements = model.get("elements", [])
    tags = {item["tag"] for item in elements}
    attachments = physical.get("attachments", [])
    mapping = native.get("nativeIdsByPidTag", {})
    require(report, bool(tags) and len(tags) == len(elements) == len(attachments) and tags == set(mapping)
            == set(content.get("representedProposalTags", [])) == {a.get("pidTag") for a in attachments}
            and content.get("proposalCount") == ready.get("pidElementCount") == len(tags),
            "automatic", "PROPOSAL_COVERAGE", "Every unique proposal has native, physical and drawing evidence")
    require(report, all(value in xml_ids for value in mapping.values())
            and native.get("completeSupportedProjection") is True and native.get("nativeSchemaAndProfileConformant") is True
            and not native.get("unresolvedTags") and not ready.get("nativeUnmappedProposalTags"),
            "automatic", "NATIVE_OBJECT_COVERAGE", "Native proposal IDs exist in the assessed XML")
    destinations = {a.get("destinationTag") for a in attachments if a.get("kind") == "AUXILIARY_ROUTE"}
    for attachment in attachments:
        if attachment.get("kind") == "UNRESOLVED":
            external = attachment.get("elementType") == "OFF_PAGE_CONNECTOR" and attachment.get("pidTag") in destinations
            require(report, external, "design", "PHYSICAL_ATTACHMENT_RESOLUTION",
                    "Off-page destinations may be resolved by explicit route reference; other unresolved attachments remain open",
                    attachment.get("pidTag", ""), "HOLD")
    links = {(x.get("sourceTag"), x.get("targetTag")) for x in physical.get("functionalLinks", [])}
    native_links = native.get("signalLinks", [])
    require(report, len(links) == len(physical.get("functionalLinks", [])) == len(native_links)
            and links == {(x.get("sourceTag"), x.get("targetTag")) for x in native_links}
            and all(a in tags and b in tags for a, b in links)
            and all(x.get("nativeId") in xml_ids for x in native_links),
            "automatic", "FUNCTIONAL_ENDPOINTS", "Declared functional endpoints agree and their native objects exist")
    holds = content.get("holds", [])
    report["nativeHolds"] = holds
    for item in holds:
        finding(report, "design", str(item.get("code", "NATIVE_HOLD")), "HOLD",
                str(item.get("message", "Unresolved native design input")), str(item.get("subject", "")))
    require(report, ready.get("pidStructurallyComplete") is True and ready.get("engineeringGapCount") == 0,
            "design", "ENGINEERING_COMPLETENESS", "Engineering registers retain their separate completeness gate", missing="HOLD")
    require(report, assessment.get("namedCaeRoundTripStatus") in ("QUALIFIED", "PASS"),
            "qualification", "RECEIVING_CAE_QUALIFICATION", "Named receiving-tool round trip is a separate qualification", missing="HOLD")
    sheets = ready.get("sheetFiles", {})
    if not isinstance(sheets, dict) or not sheets:
        raise ValueError("No rendered P&ID sheets")
    drawings = ["drawings/pid-drawing-set.pdf"] + list(sheets.values())
    require(report, set(sheets) == {row.get("sheetId") for row in content.get("drawingIndex", [])}
            and ready.get("sheetCount") == len(sheets), "automatic", "SHEET_INDEX", "Rendered sheet identities reconcile")
    report["drawingArtifacts"] = [{"path": name, "sha256": hashes.get(name)} for name in drawings]
    report["layouts"] = []
    layout_specs = []
    rendered_tags = set()
    for sheet_id, name in sheets.items():
        if name not in hashes:
            raise ValueError("Drawing absent from manifest: " + name)
        raw_svg = package_path(root, name).read_bytes()
        if b"<!DOCTYPE" in raw_svg.upper() or b"<!ENTITY" in raw_svg.upper():
            raise ValueError("External SVG entity declarations are prohibited")
        svg = ET.fromstring(raw_svg)
        hulls = {}
        require(report, svg.get("data-sheet-id") == sheet_id, "automatic", "SVG_SHEET_IDENTITY",
                "Actual vector file carries the indexed sheet identity", name)
        for node in svg.iter():
            if node.text:
                rendered_tags.add(node.text.strip())
            for key, value in node.attrib.items():
                if key.startswith("data-"):
                    rendered_tags.update(value.split(":"))
            if node.tag.rsplit("}", 1)[-1] == "polygon" and node.get("data-semantic-id"):
                coordinates = [float(value) for value in re.findall(r"[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?", node.get("points", ""))]
                hulls[node.get("data-semantic-id")] = list(zip(coordinates[::2], coordinates[1::2]))
        layout_name = str(PurePosixPath(name).with_suffix(".layout.json"))
        if layout_name not in hashes:
            finding(report, "automatic", "LAYOUT_SOURCE_MISSING", "HOLD", "Retain checked source geometry", name)
            continue
        specification = load_json(package_path(root, layout_name))
        layout_specs.append(specification)
        vessel_ids = {item.get("id") for item in specification.get("nodes", []) if item.get("kind") == "vessel"}
        for edge in specification.get("edges", []):
            if edge.get("role") == "sensing" and edge.get("from") in vessel_ids:
                hull = hulls.get(edge["from"], [])
                require(report, bool(hull), "automatic", "VESSEL_HULL_EVIDENCE",
                        "Actual vessel vector hull is available for sensing attachment review", edge.get("id", ""), "HOLD")
                if hull:
                    require(report, hull_distance(edge["points"][0], hull) <= .3,
                            "automatic", "VESSEL_SENSING_TOUCHES_HULL",
                            "Sensing stroke touches the drawn vessel, not just its bounding rectangle", edge.get("id", ""))
        result = pfd_layout_check.review(specification)
        data = result.to_dict()
        report["layouts"].append({"path": layout_name, "review": data})
        for waiver in data.get("waived", []):
            require(report, waiver.get("code") in ("WHITESPACE_POCKET", "WHITESPACE_UNBALANCED")
                    and bool(str(waiver.get("reason", "")).strip()), "automatic", "GEOMETRY_WAIVER_SCOPE",
                    "Only explained clear schematic space is automatically accepted; other waivers require scoped review",
                    name, "HOLD")
        require(report, data["status"] == "PASS", "automatic", "LAYOUT_RECHECK",
                "Fresh deterministic geometry check; source waivers remain visible", name,
                "FAIL" if data["status"] == "FAIL" else "HOLD")
    require(report, tags <= rendered_tags, "automatic", "ACTUAL_VECTOR_TAG_COVERAGE",
            "Proposal identities appear in actual vector metadata or full labels; this is coverage, not connectivity proof",
            ", ".join(sorted(tags - rendered_tags)))
    check_material_line_identity(report, xml, mapping, attachments, layout_specs, docs["pid-engineering-registers.json"])
    visual_review(report, visual, drawings, hashes)
    report["standardsBasis"] = content.get("standardsBasis", [])
    standards_review(report, standards, report["standardsBasis"])
    require(report, all(sha256(package_path(root, path)) == digest for path, digest in hashes.items())
            and sha256(manifest_path) == report["packageManifestSha256"],
            "automatic", "PACKAGE_UNCHANGED_DURING_REVIEW", "Reviewed bytes remained unchanged during the check")


def review_package(package, visual=None, standards=None):
    """Return a bounded review report; never interpret an exception as a pass."""
    root = Path(package).resolve()
    report = {
        "schemaVersion": "neqsim_pid_quality_review.v1", "package": str(root), "findings": [],
        "fitnessForConstruction": False, "engineeringApproval": "NOT_ASSIGNED",
        "standardsConformance": "NOT_ESTABLISHED_BY_AUTOMATION",
        "scope": "Integrity, retained assessment, identity/coverage, layout and supplied review receipts; not independent full-graph or ISO certification",
    }
    try:
        inspect_package(root, report, visual, standards)
    except (ValueError, OSError, KeyError, TypeError, AttributeError, ET.ParseError) as error:
        finding(report, "automatic", "PACKAGE_INPUT_INVALID", "FAIL", str(error))
    dimensions = {}
    for item in report["findings"]:
        old = dimensions.get(item["dimension"], "PASS")
        dimensions[item["dimension"]] = max((old, item["status"]), key={"PASS": 0, "HOLD": 1, "FAIL": 2}.get)
    report["dimensions"] = dimensions
    worst = max(dimensions.values(), key={"PASS": 0, "HOLD": 1, "FAIL": 2}.get, default="FAIL")
    report["status"] = "PASS_FOR_ENGINEERING_REVIEW" if worst == "PASS" else worst
    return report


def write_report(report, output):
    """Write new review artifacts outside the unchanged package."""
    output = Path(output).resolve()
    package = Path(report["package"])
    if output == package or package in output.parents:
        raise ValueError("Review output must be outside the reviewed package")
    output.mkdir(parents=True, exist_ok=True)
    (output / "pid-quality-review.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    lines = ["# P&ID quality review", "", "Status: **" + report["status"] + "**", "",
             "Construction fitness: false. Engineering approval: not assigned.", "", report["scope"], "",
             "| Dimension | Result |", "| --- | --- |"]
    lines += ["| " + key + " | " + value + " |" for key, value in report["dimensions"].items()]
    lines += ["", "## Findings", ""]
    for item in report["findings"]:
        if item["status"] != "PASS":
            lines += ["- **" + item["status"] + " " + item["code"] + "** — " + item["subject"] + ": " + item["message"]]
    (output / "pid-quality-review.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    visual = {
        "schemaVersion": "neqsim_pid_visual_review.v1",
        "packageManifestSha256": report.get("packageManifestSha256", ""),
        "reviewer": "", "reviewedAt": "",
        "artifacts": [dict(item, status="HOLD", checks=[
            {"name": name, "status": "HOLD", "evidence": ""} for name in VISUAL_CHECKS
        ]) for item in report.get("drawingArtifacts", [])],
    }
    standards = {
        "schemaVersion": "neqsim_pid_standards_review.v1",
        "packageManifestSha256": report.get("packageManifestSha256", ""),
        "reviewer": "", "reviewedAt": "", "scope": ["content", "symbols", "pce", "drafting"],
        "standards": [{"standard": name, "status": "HOLD", "reference": "", "evidence": ""}
                      for name in report.get("standardsBasis", [])],
    }
    for name, value in (("visual-review.template.json", visual), ("standards-review.template.json", standards)):
        (output / name).write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def main(argv=None):
    """Run the review; exit 0=review pass, 1=HOLD, 2=FAIL."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--visual-review", type=Path, help="neqsim_pid_visual_review.v1 receipt")
    parser.add_argument("--standards-review", type=Path, help="neqsim_pid_standards_review.v1 receipt")
    args = parser.parse_args(argv)
    try:
        report = review_package(args.package, load_json(args.visual_review) if args.visual_review else None,
                                load_json(args.standards_review) if args.standards_review else None)
        write_report(report, args.output)
    except (ValueError, OSError) as error:
        parser.error(str(error))
    print(json.dumps({"status": report["status"], "dimensions": report["dimensions"], "output": str(args.output)}))
    return {"PASS_FOR_ENGINEERING_REVIEW": 0, "HOLD": 1, "FAIL": 2}[report["status"]]


if __name__ == "__main__":
    sys.exit(main())
