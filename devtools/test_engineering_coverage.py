"""Coverage must expose gaps and reject stale or fabricated bindings."""
import json

import pytest

import build_engineering_coverage as coverage


@pytest.fixture
def source_tree(tmp_path):
    files = {
        "src/main/java/neqsim/thermo/Example.java": "package neqsim.thermo; public class Example { public static double calculate(double value) { return value; } }",
        "src/main/java/neqsim/mcp/runners/McpImplementationInventory.java":
            'bind(implementations, "runFlash", "FlashRunner");',
        ".github/skills/example/SKILL.md": "Example and neqsim.thermo.Example",
        ".github/agents/specialist.agent.md": "---\nrequired_skills:\n- example\n---\n",
        "src/test/java/ExampleTest.java": "test source",
    }
    for name, content in files.items():
        path = tmp_path / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
    registry = {"capabilities": [{"id": "example", "title": "Example", "domain": "thermo",
        "tool": "runFlash", "apis": ["neqsim.thermo.Example"], "skills": ["example"],
        "agents": ["specialist"], "testSources": ["src/test/java/ExampleTest.java"],
        "limitations": "An example only"}]}
    path = tmp_path / coverage.REGISTRY
    path.parent.mkdir(parents=True)
    path.write_text(json.dumps(registry))
    return tmp_path


def test_source_presence_is_not_execution_or_qualification(source_tree):
    data = coverage.build(source_tree)
    assert data["complete"] is False
    assert data["summary"]["publicTypes"] == 1
    row = data["capabilities"][0]
    assert row["evidence"]["execution"] == "not_recorded"
    assert row["evidence"]["engineeringQualification"] == "not_assessed"
    assert data["apis"][0]["agents"] == ["specialist"]


@pytest.mark.parametrize("field,value", [
    ("tool", "nonexistent"), ("apis", ["neqsim.Missing"]),
    ("skills", ["missing"]), ("agents", ["missing"]),
    ("testSources", ["src/test/java/Missing.java"]), ("limitations", "")])
def test_broken_registration_is_rejected(source_tree, field, value):
    path = source_tree / coverage.REGISTRY
    data = json.loads(path.read_text())
    data["capabilities"][0][field] = value
    path.write_text(json.dumps(data))
    with pytest.raises(ValueError):
        coverage.build(source_tree)


def test_unregistered_api_is_visible_and_changes_digest(source_tree):
    before = coverage.build(source_tree)
    path = source_tree / "src/main/java/neqsim/thermo/NewModel.java"
    path.write_text("package neqsim.thermo; public class NewModel {}")
    after = coverage.build(source_tree)
    assert after["summary"]["reviewRequired"] == 1
    assert after["catalogDigest"] != before["catalogDigest"]
    assert after["apis"][1]["disposition"] == "review_required"


def test_existing_api_change_invalidates_digest(source_tree):
    before = coverage.build(source_tree)
    path = source_tree / "src/main/java/neqsim/thermo/Example.java"
    path.write_text("package neqsim.thermo; public class Example { public double added() {return 1;} }")
    assert coverage.build(source_tree)["catalogDigest"] != before["catalogDigest"]


def test_comments_literals_and_nested_types_do_not_inflate_inventory(source_tree):
    path = source_tree / "src/main/java/neqsim/thermo/Fake.java"
    path.write_text('package neqsim.thermo; /* public class Fake {} */ class Holder { '
                    'String text="public class Fake {}"; public class Fake {} }')
    assert len(coverage.java_types(source_tree)) == 1


def example_operation():
    return {"id": "example-calculation", "classification": "supported",
            "api": "neqsim.thermo.Example", "method": "calculate",
            "signature": "double neqsim.thermo.Example.calculate(double)",
            "units": {"inputs": {"value": "dimensionless"}, "outputs": {"value": "dimensionless"}},
            "applicability": "Fixture operation only", "route": "runCapability action=invoke",
            "example": {"arguments": [2.0], "parameterTypes": ["double"],
                        "expected": 2.0, "absoluteTolerance": 0.0},
            "evidenceSources": ["src/test/java/ExampleTest.java"]}


def test_supported_operation_contract_is_counted(source_tree):
    path = source_tree / coverage.REGISTRY
    data = json.loads(path.read_text())
    data["capabilities"][0]["operations"] = [example_operation()]
    path.write_text(json.dumps(data))
    built = coverage.build(source_tree)
    assert built["summary"]["classifiedOperations"] == 1
    assert built["summary"]["supportedOperations"] == 1
    assert built["capabilities"][0]["operations"][0]["signature"].endswith("calculate(double)")


@pytest.mark.parametrize("field,value", [
    ("classification", "guessed"), ("api", "neqsim.thermo.Missing"),
    ("method", "missing"), ("evidenceSources", ["src/test/java/Missing.java"])])
def test_invalid_operation_contract_is_rejected(source_tree, field, value):
    path = source_tree / coverage.REGISTRY
    data = json.loads(path.read_text())
    operation = example_operation()
    operation[field] = value
    data["capabilities"][0]["operations"] = [operation]
    path.write_text(json.dumps(data))
    with pytest.raises(ValueError):
        coverage.build(source_tree)


def test_inventory_is_deterministic(source_tree):
    assert coverage.build(source_tree) == coverage.build(source_tree)


def test_duplicate_ids_are_rejected(source_tree):
    path = source_tree / coverage.REGISTRY
    data = json.loads(path.read_text())
    data["capabilities"] *= 2
    path.write_text(json.dumps(data))
    with pytest.raises(ValueError, match="duplicate"):
        coverage.build(source_tree)
