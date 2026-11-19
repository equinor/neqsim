"""Join one drawing revision to SAS I/O, procedures and recorded scenario evidence."""
import copy
import hashlib
import html
import json
from xml.etree import ElementTree

from steady import ROOT


REVISION = "stabilization-commissioning/DYN-C049-A"
DEMAND_CASES = {
    "20-XV-101A": ["healthy", "healthy_refined", "deterministic_replay", "failed_final",
                    "pst_interrupt", "reset_restart"],
    "20-XV-101B": ["healthy", "healthy_refined", "deterministic_replay", "failed_final",
                    "pst_interrupt", "reset_restart"],
    "10-ESDV-100": ["healthy", "healthy_refined", "deterministic_replay", "fg_demand"],
}


def canonical_hash(value):
    """Return deterministic SHA-256 for a JSON-compatible value."""
    encoded = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def _read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def _sheet_tags(package, sheet_files):
    """Resolve exact data-tag identities from the generated vector sheets."""
    result = {}
    for sheet_id, relative in sheet_files.items():
        root = ElementTree.parse(package / relative).getroot()
        for node in root.iter():
            tag = node.attrib.get("data-tag")
            if tag:
                result.setdefault(tag, []).append({
                    "sheetId": sheet_id,
                    "file": relative,
                    "selector": "[data-tag='" + tag + "']",
                    "placementStatus": "PHYSICAL_SYMBOL",
                })
            text = (node.text or "").strip()
            semantic_id = node.attrib.get("data-semantic-id")
            if text and semantic_id and text not in result:
                result.setdefault(text, []).append({
                    "sheetId": sheet_id,
                    "file": relative,
                    "selector": "[data-semantic-id='" + semantic_id + "']",
                    "placementStatus": "HOLD_REGISTER",
                })
    for targets in result.values():
        targets.sort(key=lambda item: item["placementStatus"] != "PHYSICAL_SYMBOL")
    return result


def _scenario_dependencies(sas):
    """Declare tag dependencies for every retained virtual-FAT result."""
    common = [point["tag"] for point in sas["ioMap"]["points"]]
    dependencies = {}
    for name, case in sas.items():
        if isinstance(case, dict) and "case" in case:
            tags = list(common)
            if name != "fg_demand":
                tags.remove("00-FG-001")
            dependencies[name] = tags
    return dependencies


def execute():
    """Create deterministic requirement-to-tag-to-result traceability evidence."""
    intake = _read(ROOT / "drawing-intake.json")
    package = ROOT / intake["package_directory"]
    model = _read(package / "pid-design-model.json")
    bindings = _read(package / "pid-simulation-bindings.json")
    projection = _read(package / "plant.dexpi.xml.pid-projection.json")
    readiness = _read(package / "professional-pid-readiness.json")
    manifest = _read(package / "package-manifest.json")
    sas = _read(ROOT / "sas.json")
    procedures = _read(ROOT / "procedures.json")
    by_tag = {row["tag"]: row for row in model["elements"]}
    binding_by_tag = {row["pidTag"]: row for row in bindings["bindings"]}
    sheets = _sheet_tags(package, readiness["sheetFiles"])
    dependencies = _scenario_dependencies(sas)
    native_ids = projection["nativeIdsByPidTag"]

    rows = []
    for point in sas["ioMap"]["points"]:
        tag = point["tag"]
        model_row = by_tag.get(tag)
        evidence_cases = sorted(name for name, tags in dependencies.items() if tag in tags)
        rows.append({
            "tag": tag,
            "direction": point["direction"],
            "unit": point["unit"],
            "logicEndpoint": point["endpoint"],
            "pidElementId": None if model_row is None else model_row["id"],
            "pidElementType": None if model_row is None else model_row["type"],
            "dexpiNativeId": native_ids.get(tag),
            "simulationBinding": binding_by_tag.get(tag),
            "drawingTargets": sheets.get(tag, []),
            "scenarioResults": ["sas.json#/" + name for name in evidence_cases],
            "procedureResults": (
                ["sas.json#/reset_restart/procedure"]
                if tag.startswith("20-XV-101") else []
            ),
            "diagnostics": [],
        })
    for row in rows:
        for field, diagnostic in (
            ("pidElementId", "MISSING_CANONICAL_PID_IDENTITY"),
            ("dexpiNativeId", "MISSING_DEXPI_NATIVE_IDENTITY"),
        ):
            if not row[field]:
                row["diagnostics"].append(diagnostic)
        if not row["drawingTargets"]:
            row["diagnostics"].append("MISSING_VECTOR_DRAWING_TARGET")
        if not row["scenarioResults"]:
            row["diagnostics"].append("MISSING_EXECUTED_SCENARIO_RESULT")

    source = {
        "packageManifestSha256": hashlib.sha256(
            (package / "package-manifest.json").read_bytes()
        ).hexdigest(),
        "packageSemanticFingerprint": intake["semantic_fingerprint"],
        "pidFingerprint": bindings["pidFingerprint"],
        "sourceFingerprint": bindings["sourceFingerprint"],
        "sasIoMap": sas["ioMap"],
        "sasModelRevision": sas["modelRevision"],
        "procedureModelRevision": procedures["modelRevision"],
    }
    revision_fingerprint = canonical_hash(source)

    revised_model = copy.deepcopy(model)
    revised_valve = next(row for row in revised_model["elements"] if row["tag"] == "20-XV-101A")
    original_stroke = revised_valve["attributes"]["strokeSeconds"]
    revised_valve["attributes"]["strokeSeconds"] = 2.5
    revised_pid_fingerprint = canonical_hash(revised_model)
    invalidated = sorted(DEMAND_CASES["20-XV-101A"])
    all_cases = sorted(dependencies)
    revision_test = {
        "modifiedTag": "20-XV-101A",
        "changedField": "attributes.strokeSeconds",
        "originalValueSeconds": original_stroke,
        "revisedValueSeconds": 2.5,
        "originalPidArtifactFingerprint": canonical_hash(model),
        "revisedPidArtifactFingerprint": revised_pid_fingerprint,
        "revisionChanged": canonical_hash(model) != revised_pid_fingerprint,
        "invalidatedScenarios": invalidated,
        "unaffectedScenarios": [name for name in all_cases if name not in invalidated],
        "policy": "Invalidate retained evidence whose declared tag dependencies include the changed valve",
    }
    return {
        "schemaVersion": "neqsim_commissioning_traceability.v1",
        "modelRevision": REVISION,
        "revisionFingerprint": revision_fingerprint,
        "sourceRevision": source,
        "packageDirectory": intake["package_directory"],
        "tagLinks": rows,
        "scenarioDependencies": dependencies,
        "singleValveRevisionTest": revision_test,
        "packageManifestFileCount": manifest["fileCount"],
        "acceptance": {
            "ioPointCount": len(rows),
            "completeCanonicalIdentityCount": sum(not row["diagnostics"] for row in rows),
            "missingIdentityCount": sum(bool(row["diagnostics"]) for row in rows),
            "physicalBindingCount": sum(row["simulationBinding"] is not None for row in rows),
            "drawingTargetCount": sum(bool(row["drawingTargets"]) for row in rows),
            "physicallyPlacedIoCount": sum(
                any(target["placementStatus"] == "PHYSICAL_SYMBOL"
                    for target in row["drawingTargets"]) for row in rows
            ),
            "holdRegisterIoCount": sum(
                all(target["placementStatus"] == "HOLD_REGISTER"
                    for target in row["drawingTargets"]) for row in rows
            ),
            "executedScenarioLinkCount": sum(len(row["scenarioResults"]) for row in rows),
        },
        "limitations": [
            "Logical command and feedback I/O points have canonical and DEXPI identities but are not separate simulated equipment.",
            "The vector tag index is engineering navigation evidence, not an approved drawing or construction deliverable.",
            "Scenario invalidation follows explicit declared dependencies and does not replace multidisciplinary change control.",
        ],
    }


def validate(result):
    """Fail closed on missing identities, links or revision invalidation evidence."""
    assert result["acceptance"]["ioPointCount"] == 10
    assert result["acceptance"]["completeCanonicalIdentityCount"] == 10
    assert result["acceptance"]["missingIdentityCount"] == 0
    assert result["acceptance"]["physicalBindingCount"] == 3
    assert result["acceptance"]["drawingTargetCount"] == 10
    assert result["acceptance"]["physicallyPlacedIoCount"] == 0
    assert result["acceptance"]["holdRegisterIoCount"] == 10
    assert result["acceptance"]["executedScenarioLinkCount"] > 50
    assert result["singleValveRevisionTest"]["revisionChanged"]
    assert "failed_final" in result["singleValveRevisionTest"]["invalidatedScenarios"]
    assert "fg_demand" in result["singleValveRevisionTest"]["unaffectedScenarios"]
    assert all(not row["diagnostics"] for row in result["tagLinks"])


def write_html(result):
    """Write a compact selectable index beside the retained JSON evidence."""
    lines = [
        "<!doctype html><meta charset='utf-8'><title>OSR tag traceability</title>",
        "<style>body{font:14px sans-serif;margin:2rem}table{border-collapse:collapse}",
        "th,td{border:1px solid #bbb;padding:.4rem;text-align:left}code{font-size:12px}</style>",
        "<h1>OSR DYN-C049 tag traceability</h1>",
        "<p>Revision fingerprint: <code>" + result["revisionFingerprint"] + "</code></p>",
        "<table><tr><th>Tag</th><th>Direction</th><th>DEXPI identity</th><th>Drawing</th>",
        "<th>Logic endpoint</th><th>Executed scenarios</th></tr>",
    ]
    for row in result["tagLinks"]:
        target = row["drawingTargets"][0]
        href = result["packageDirectory"] + "/" + target["file"]
        lines.append(
            "<tr><td><a href='" + html.escape(href) + "'>" + html.escape(row["tag"]) + "</a></td>"
            "<td>" + html.escape(row["direction"]) + "</td><td><code>" +
            html.escape(row["dexpiNativeId"]) + "</code></td><td>" +
            html.escape(target["sheetId"]) + "</td><td>" + html.escape(row["logicEndpoint"]) +
            "</td><td>" + str(len(row["scenarioResults"])) + "</td></tr>"
        )
    lines.append("</table>")
    (ROOT / "traceability-index.html").write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    result = execute()
    validate(result)
    (ROOT / "traceability.json").write_text(
        json.dumps(result, indent=2, allow_nan=False) + "\n", encoding="utf-8"
    )
    write_html(result)
    print(json.dumps(result["acceptance"], indent=2))
