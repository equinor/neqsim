"""Check review aggregation and tamper guards, not ISO or native-schema qualification.

The compact fixture substitutes an upstream native assessment. Real schema/model
validation and layout behavior are covered by Java and pfd_layout_check suites.
"""
import hashlib
import json
from pathlib import Path
from unittest import mock

import pytest

import pid_quality_review as qc


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def manifest(package):
    rows = []
    for path in sorted(package.rglob("*")):
        if path.is_file() and path.name != "package-manifest.json":
            rows.append({"path": path.relative_to(package).as_posix(),
                         "sizeBytes": path.stat().st_size, "sha256": qc.sha256(path)})
    save(package / "package-manifest.json", {"hashAlgorithm": "SHA-256", "fileCount": len(rows), "files": rows})


@pytest.fixture
def package(tmp_path):
    package = tmp_path / "package"
    package.mkdir()
    xml = b'<fixture><object id="n1"/></fixture>'
    (package / "plant.dexpi.xml").write_bytes(xml)
    basis = ["ISO 10628-1:2014", "ISO 10628-2:2012", "ISO 14617-1:2025", "ISO 5457:1999",
             "ISO 7200:2004", "IEC 62424:2016", "ANSI/ISA-5.1:2024"]
    save(package / "professional-pid-readiness.json", {
        "projectId": "QC", "revision": "A", "dexpiVersion": "2.0.1", "generationStatus": "GENERATED_FOR_REVIEW",
        "documentModelValid": True, "drawingHasNoErrors": True, "drawingHasUnresolvedWarnings": False,
        "drawingProfile": "ISO_10628_BASIS_NATIVE", "pidElementCount": 1, "pidStructurallyComplete": True,
        "engineeringGapCount": 0, "sheetCount": 1, "nativeUnmappedProposalTags": [],
        "sheetFiles": {"SHEET-001": "drawings/pid-sheet-001.svg"},
    })
    save(package / "pid-drawing-content-report.json", {
        "projectId": "QC", "revision": "A", "sourceGraphFingerprint": "graph", "proposalCount": 1,
        "representedProposalTags": ["PT-001"], "drawingIndex": [{"sheetId": "SHEET-001", "sourceGraphFingerprint": "graph"}],
        "holds": [], "standardsBasis": basis,
    })
    save(package / "pid-document-set.json", {"sourceGraphFingerprint": "graph"})
    save(package / "pid-engineering-registers.json", {"lines": []})
    save(package / "pid-design-model.json", {"projectId": "QC", "elements": [{"tag": "PT-001"}]})
    save(package / "pid-simulation-bindings.json", {"projectId": "QC", "revision": "A"})
    save(package / "plant.dexpi.xml.pid-projection.json", {
        "nativeIdsByPidTag": {"PT-001": "n1"}, "completeSupportedProjection": True,
        "nativeSchemaAndProfileConformant": True, "unresolvedTags": {}, "signalLinks": [],
    })
    save(package / "pid-physical-projection.json", {"attachments": [{"pidTag": "PT-001", "kind": "SENSING"}], "functionalLinks": []})
    save(package / "dexpi-2.0.1-conformance.json", {
        "dexpiSpecificationVersion": "2.0.1", "fileSha256": hashlib.sha256(xml).hexdigest(),
        "status": "CONFORMANT", "errors": [], "officialSchemaVerified": True, "schemaValid": True,
        "officialModelImportsValid": True, "neqsimSemanticProfileValid": True, "namedCaeRoundTripStatus": "QUALIFIED",
    })
    drawings = package / "drawings"
    drawings.mkdir()
    (drawings / "pid-drawing-set.pdf").write_bytes(b"%PDF-fixture")
    (drawings / "pid-sheet-001.svg").write_text('<svg data-sheet-id="SHEET-001"><g data-semantic-id="PT-001"/></svg>')
    save(drawings / "pid-sheet-001.layout.json", {"fixture": True})
    manifest(package)
    return package


def run(package, **kwargs):
    geometry = mock.Mock()
    geometry.to_dict.return_value = {"status": "PASS", "waived": []}
    with mock.patch.object(qc.pfd_layout_check, "review", return_value=geometry):
        return qc.review_package(package, **kwargs)


def evidence(package):
    digest = qc.sha256(package / "package-manifest.json")
    visual = {"schemaVersion": "neqsim_pid_visual_review.v1", "packageManifestSha256": digest,
              "reviewer": "Synthetic unit-test reviewer", "reviewedAt": "2026-11-03T19:00:00+01:00", "artifacts": []}
    for name in ("drawings/pid-drawing-set.pdf", "drawings/pid-sheet-001.svg"):
        visual["artifacts"].append({"path": name, "sha256": qc.sha256(package / name), "status": "PASS",
                                    "checks": [{"name": check, "status": "PASS", "evidence": "Synthetic observation"}
                                               for check in qc.VISUAL_CHECKS]})
    content = qc.load_json(package / "pid-drawing-content-report.json")
    standards = {"schemaVersion": "neqsim_pid_standards_review.v1", "packageManifestSha256": digest,
                 "reviewer": "Synthetic unit-test reviewer", "reviewedAt": visual["reviewedAt"],
                 "scope": ["content", "symbols", "pce", "drafting"], "standards": [
                     {"standard": name, "status": "PASS", "reference": "Unit-test reference", "evidence": "Unit-test observation"}
                     for name in content["standardsBasis"]]}
    return visual, standards


def test_missing_reviews_are_hold_even_when_automatic_checks_pass(package):
    report = run(package)
    assert report["dimensions"]["automatic"] == "PASS"
    assert report["status"] == "HOLD"
    assert report["fitnessForConstruction"] is False


def test_supplied_receipts_never_assign_engineering_approval(package):
    visual, standards = evidence(package)
    report = run(package, visual=visual, standards=standards)
    assert report["status"] == "PASS_FOR_ENGINEERING_REVIEW"
    assert report["standardsConformance"] == "NOT_ESTABLISHED_BY_AUTOMATION"
    assert report["engineeringApproval"] == "NOT_ASSIGNED"


def test_changed_drawing_fails_even_with_previous_reviews(package):
    visual, standards = evidence(package)
    (package / "drawings/pid-drawing-set.pdf").write_bytes(b"changed")
    assert run(package, visual=visual, standards=standards)["status"] == "FAIL"


def test_rebuilt_manifest_does_not_hide_stale_review(package):
    visual, standards = evidence(package)
    path = package / "pid-design-model.json"
    data = qc.load_json(path)
    data["fixtureRevision"] = 2
    save(path, data)
    manifest(package)
    report = run(package, visual=visual, standards=standards)
    assert any(f["code"] == "REVIEW_CONTENT_BINDING" and f["status"] == "FAIL" for f in report["findings"])


def test_scalar_approval_does_not_replace_actual_review_evidence(package):
    assert run(package, visual={"approved": True}, standards={"approved": True})["status"] != "PASS_FOR_ENGINEERING_REVIEW"


def test_duplicate_visual_checks_cannot_hide_a_failed_observation(package):
    visual, standards = evidence(package)
    visual["artifacts"][0]["checks"].insert(0, {
        "name": "sensing_locations", "status": "FAIL", "evidence": "Detached sensing stroke observed",
    })
    report = run(package, visual=visual, standards=standards)
    assert report["status"] == "FAIL"
    assert any(f["code"] == "VISUAL_UNIQUE_CHECKS" and f["status"] == "FAIL" for f in report["findings"])


def test_retained_conformance_must_match_final_xml(package):
    (package / "plant.dexpi.xml").write_bytes(b'<fixture><object id="other"/></fixture>')
    manifest(package)
    report = run(package)
    assert any(f["code"] == "EXACT_NATIVE_ASSESSMENT" and f["status"] == "FAIL" for f in report["findings"])


def test_manifest_traversal_is_rejected(package):
    data = qc.load_json(package / "package-manifest.json")
    data["files"][0]["path"] = "../outside"
    save(package / "package-manifest.json", data)
    assert run(package)["status"] == "FAIL"


def test_vector_content_is_checked_beyond_generated_tag_counts(package):
    (package / "drawings/pid-sheet-001.svg").write_text('<svg data-sheet-id="SHEET-001"/>')
    manifest(package)
    report = run(package)
    assert any(f["code"] == "ACTUAL_VECTOR_TAG_COVERAGE" and f["status"] == "FAIL" for f in report["findings"])


def test_missing_standard_edition_and_native_holds_remain_hold(package):
    path = package / "pid-drawing-content-report.json"
    data = qc.load_json(path)
    data["standardsBasis"] = ["ISO 10628-1"]
    data["holds"] = [{"code": "FAIL_ACTION", "subject": "SDV", "message": "Controlled failure action needed"}]
    save(path, data)
    manifest(package)
    visual, standards = evidence(package)
    report = run(package, visual=visual, standards=standards)
    assert report["status"] == "HOLD"
    assert any(f["code"] == "STANDARD_EDITION" and f["status"] == "HOLD" for f in report["findings"])


def test_review_output_cannot_modify_the_reviewed_package(package):
    with pytest.raises(ValueError, match="outside"):
        qc.write_report(run(package), package / "review")


def test_templates_retain_hold_until_reviewed(package, tmp_path):
    output = tmp_path / "review"
    qc.write_report(run(package), output)
    visual = qc.load_json(output / "visual-review.template.json")
    standards = qc.load_json(output / "standards-review.template.json")
    assert run(package, visual=visual, standards=standards)["status"] == "HOLD"


def test_critical_geometry_waivers_cannot_get_an_automatic_pass(package):
    geometry = mock.Mock()
    geometry.to_dict.return_value = {"status": "PASS", "waived": [{"code": "BOX_OVERLAP", "reason": "Ignore"}]}
    with mock.patch.object(qc.pfd_layout_check, "review", return_value=geometry):
        report = qc.review_package(package)
    assert report["dimensions"]["automatic"] == "HOLD"


def test_sensing_stroke_must_touch_actual_drawn_hull(package):
    (package / "drawings/pid-sheet-001.svg").write_text(
        '<svg data-sheet-id="SHEET-001"><g data-semantic-id="PT-001"/>'
        '<polygon data-semantic-id="VESSEL" points="0,0 10,0 10,10 0,10"/></svg>')
    save(package / "drawings/pid-sheet-001.layout.json", {
        "nodes": [{"id": "VESSEL", "kind": "vessel"}],
        "edges": [{"id": "sense", "from": "VESSEL", "role": "sensing", "points": [[12, 5], [20, 5]]}],
    })
    manifest(package)
    report = run(package)
    assert any(f["code"] == "VESSEL_SENSING_TOUCHES_HULL" and f["status"] == "FAIL" for f in report["findings"])
