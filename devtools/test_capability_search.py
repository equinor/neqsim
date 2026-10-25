"""Source discovery must stay complete, bounded and honest about evidence."""
import json
from pathlib import Path

import pytest

import capability_search as discovery


@pytest.fixture
def checkout(tmp_path):
    files = {
        ".github/agents/example.agent.md": "---\nrequired_skills:\n- example\n---\n",
        ".github/skills/example/SKILL.md": "---\nname: example\ndescription: Example\n---\n",
        "docs/example.md": "Example guide",
        "src/main/java/neqsim/process/Widget.java":
            "package neqsim.process; public class Widget { public void calculateFrobnication() {} }",
        "src/main/java/neqsim/process/special/Widget.java":
            "package neqsim.process.special; public class Widget {}",
        "src/main/java/neqsim/process/SpecialWidget.java":
            'package neqsim.process; /* public void fakeMethod() {} */ public class SpecialWidget { '
            'String s = "public void falseMethod() {}"; }',
        "src/test/java/neqsim/process/WidgetTest.java":
            "package neqsim.process; class WidgetTest { Widget value; }",
        "src/test/java/neqsim/other/ImportedTest.java":
            "package neqsim.other; import neqsim.process.Widget; class ImportedTest { Widget value; }",
        "src/test/java/neqsim/other/WildcardTest.java":
            "package neqsim.other; import neqsim.process.*; class WildcardTest { Widget value; }",
        "src/test/java/neqsim/other/FalseReferenceTest.java":
            'package neqsim.other; /* neqsim.process.Widget */ class FalseReferenceTest { '
            'String s = "neqsim.process.Widget"; }',
        "src/test/java/neqsim/other/CollisionTest.java":
            "package neqsim.other; import neqsim.process.special.Widget; class CollisionTest { Widget value; }",
        "devtools/engineering_capabilities.json": json.dumps({"capabilities": [{
            "id": "bounded-widget", "tool": "runProcess", "apis": ["neqsim.process.Widget"],
            "limitations": "Fixture registration, not every Widget method"}]}),
    }
    for name, content in files.items():
        path = tmp_path / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    route = {"package": "neqsim.process", "title": "Widget calculation",
             "keywords": "special calculation frobnication", "agent": "example",
             "skill": "example", "docs": ["docs/example.md"]}
    (tmp_path / discovery.ROUTES).write_text(json.dumps({"routes": [route]}))
    return tmp_path


def test_exact_fqn_beats_ambiguous_class_name_and_does_not_claim_execution(checkout):
    result = discovery.search("neqsim.process.Widget", checkout)
    first = result["apis"][0]
    assert first["id"] == "neqsim.process.Widget"
    assert first["execution"] == "not_recorded"
    assert first["engineeringQualification"] == "not_assessed"
    assert first["route"]["agent"] == "example"
    assert first["declaredRegistrations"][0]["tool"] == "runProcess"
    second = next(api for api in result["apis"] if api["id"] == "neqsim.process.special.Widget")
    assert second["declaredRegistrations"] == []


def test_test_references_observe_imports_packages_and_ignore_comments(checkout):
    refs = discovery._test_references(checkout, "neqsim.process.Widget")
    assert "src/test/java/neqsim/process/WidgetTest.java" == refs[0]
    assert {Path(ref).name for ref in refs} == {"WidgetTest.java", "ImportedTest.java", "WildcardTest.java"}
    assert len(discovery._test_references(checkout, "neqsim.process.Widget", limit=1)) == 1


def test_batched_references_read_tests_once_and_prefer_each_mirrored_package(checkout, monkeypatch):
    path = checkout / "src/test/java/neqsim/process/special/OwnTest.java"
    path.parent.mkdir()
    path.write_text("package neqsim.process.special; class OwnTest { Widget value; }")
    read = discovery.read_source
    visits = []

    def count_reads(path):
        visits.append(path)
        return read(path)

    monkeypatch.setattr(discovery, "read_source", count_reads)
    refs = discovery._test_reference_index(checkout, [
        "neqsim.process.Widget", "neqsim.process.special.Widget"], limit=1)
    assert len(visits) == len(set(visits))
    assert refs["neqsim.process.special.Widget"] == [path.relative_to(checkout).as_posix()]


def test_new_source_and_public_member_are_discoverable_without_snapshot(checkout):
    path = checkout / "src/main/java/neqsim/process/NewFluid.java"
    path.write_text("package neqsim.process; public class NewFluid { public double thermalConductivity() { return 1; } }")
    result = discovery.search("thermal conductivity", checkout, top=1)
    assert result["apis"][0]["id"] == "neqsim.process.NewFluid"
    assert discovery.search("calculateFrobnication", checkout, top=1)["apis"][0]["id"] == "neqsim.process.Widget"
    assert discovery.search("falseMethod", checkout)["apis"] == []
    assert discovery.search("fakeMethod", checkout)["apis"] == []


def test_no_match_does_not_return_arbitrary_zero_score_apis(checkout):
    assert discovery.search("zzzznoSuchCapability", checkout)["apis"] == []


def test_longest_package_boundary_route_wins(checkout):
    routes = discovery.load_routes(checkout)
    specialized = dict(routes[0], package="neqsim.process.special", title="Specific")
    assert discovery.route_for("neqsim.process.special.Widget", routes + [specialized]) == specialized
    assert discovery.route_for("neqsim.procession.Widget", routes) is None


@pytest.mark.parametrize("field,value", [
    ("agent", "absent"), ("skill", "absent"), ("docs", ["docs/absent.md"]),
    ("title", ""), ("keywords", ""),
])
def test_broken_routes_fail_closed(checkout, field, value):
    path = checkout / discovery.ROUTES
    data = json.loads(path.read_text())
    data["routes"][0][field] = value
    path.write_text(json.dumps(data))
    with pytest.raises(ValueError):
        discovery.load_routes(checkout)


def test_missing_required_skill_and_duplicate_route_are_rejected(checkout):
    agent = checkout / ".github/agents/example.agent.md"
    original = agent.read_text()
    agent.write_text("---\nrequired_skills:\n- other\n---\n")
    with pytest.raises(ValueError, match="does not load"):
        discovery.load_routes(checkout)
    agent.write_text(original)
    path = checkout / discovery.ROUTES
    data = json.loads(path.read_text())
    data["routes"] *= 2
    path.write_text(json.dumps(data))
    with pytest.raises(ValueError, match="Duplicate"):
        discovery.load_routes(checkout)


def test_audit_exposes_unmapped_new_domain_and_empty_route(checkout):
    new = checkout / "src/main/java/neqsim/newdomain/Feature.java"
    new.parent.mkdir()
    new.write_text("package neqsim.newdomain; public class Feature {}")
    result = discovery.audit(checkout)
    assert result["unroutedTypes"] == ["neqsim.newdomain.Feature"]
    assert result["routedTypes"] == result["publicTypes"] - 1
    path = checkout / discovery.ROUTES
    data = json.loads(path.read_text())
    data["routes"].append(dict(data["routes"][0], package="neqsim.empty"))
    path.write_text(json.dumps(data))
    assert discovery.audit(checkout)["emptyRoutes"] == ["neqsim.empty"]


@pytest.mark.parametrize("query,top", [(" ", 5), ("Widget", 0), ("Widget", 21)])
def test_invalid_query_or_unbounded_output_is_rejected(checkout, query, top):
    with pytest.raises(ValueError):
        discovery.search(query, checkout, top)


def test_cli_audit_exit_and_json_contract(checkout, monkeypatch, capsys):
    monkeypatch.setattr(discovery, "ROOT", checkout)
    assert discovery.main(["--audit", "--json"]) == 0
    result = json.loads(capsys.readouterr().out)
    assert result["routedTypes"] == 3
    assert result["engineeringQualification"] == "not_assessed"
    path = checkout / "src/main/java/neqsim/process/Widget.java"
    path.write_text("package neqsim.unmapped; public class Widget {}")
    assert discovery.main(["--audit"]) == 1


def test_all_current_public_types_have_valid_workflow_entry_points():
    result = discovery.audit(discovery.ROOT)
    assert result["publicTypes"] > 0
    assert result["unroutedTypes"] == []
    assert result["emptyRoutes"] == []
    assert result["routedTypes"] == result["publicTypes"]
    assert result["engineeringQualification"] == "not_assessed"
