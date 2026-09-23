"""Tests for the task work-record generator (devtools/generate_work_record.py)."""
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import generate_work_record as gwr  # noqa: E402


CONFIG = """\
study:
  title: "Demo study"
  task_type: "G"
  scale: standard

inputs:
  data_sources:
    - system: sap_maintenance
      scope: "plant 1820"
      access: read_only
      evidence: step2_analysis/pull.json
    - system: historian
      scope: "2 tags"
      evidence: step2_analysis/absent.json

analysis:
  engine: script
  scripts:
    - file: 01_pull.py
      purpose: Read the source system.
      produces: step2_analysis/pull.json

notebooks:
  required: false
  plan: []

report:
  work_record: required
"""


@pytest.fixture()
def task(tmp_path):
    for folder in ("step1_scope_and_research/references", "step2_analysis",
                   "step3_report", "figures"):
        (tmp_path / folder).mkdir(parents=True)
    (tmp_path / "study_config.yaml").write_text(CONFIG, encoding="utf-8")
    (tmp_path / "results.json").write_text(
        '{"key_results": {"rate_per_year": 3.2}, "data_sources": ["sap"]}',
        encoding="utf-8")
    (tmp_path / "step2_analysis" / "01_pull.py").write_text(
        '"""Read the source system."""\n', encoding="utf-8")
    (tmp_path / "step2_analysis" / "pull.json").write_text("{}", encoding="utf-8")
    return tmp_path


def test_config_parser_reads_nested_lists_and_scalars(task):
    config = gwr.load_study_config(task)
    assert config["study"]["title"] == "Demo study"
    assert config["notebooks"]["required"] is False
    sources = config["inputs"]["data_sources"]
    assert [entry["system"] for entry in sources] == ["sap_maintenance", "historian"]
    assert sources[0]["evidence"] == "step2_analysis/pull.json"
    assert config["analysis"]["scripts"][0]["purpose"] == "Read the source system."


def test_generated_record_covers_method_data_and_map(task):
    assert gwr.main([str(task)]) == 0
    text = (task / "step3_report" / "WORK_RECORD.md").read_text(encoding="utf-8")
    assert "# Work Record — Demo study" in text
    assert "01_pull.py" in text
    assert "sap_maintenance" in text
    # Declared evidence that exists is captured; the missing one is flagged.
    assert "captured" in text
    assert "MISSING" in text
    assert "rate_per_year" in text
    for heading in ("## 1.", "## 4. Data used", "## 6. Where to find everything",
                    "## 7. How to reproduce"):
        assert heading in text


def test_glob_outputs_and_optional_notebook_plan_are_not_missing(task):
    config = (task / "study_config.yaml").read_text(encoding="utf-8")
    config = config.replace(
        "      produces: step2_analysis/pull.json",
        "      produces: step2_analysis/case_*.json")
    config = config.replace(
        "  plan: []",
        "  plan:\n    - file: 01_main_analysis.ipynb\n      purpose: Template notebook.")
    config = config.replace("      evidence: step2_analysis/absent.json",
                            "      evidence: step2_analysis/pull.json")
    (task / "study_config.yaml").write_text(config, encoding="utf-8")
    (task / "step2_analysis" / "case_a.json").write_text("{}", encoding="utf-8")
    assert gwr.main([str(task)]) == 0
    text = (task / "step3_report" / "WORK_RECORD.md").read_text(encoding="utf-8")
    assert "case_*.json (missing)" not in text
    assert "01_main_analysis.ipynb" not in text
    assert "MISSING" not in text


def test_check_flags_unfilled_narrative(task):
    gwr.main([str(task)])
    problems = gwr.check_work_record(task)
    assert any("template text" in problem for problem in problems)


def test_narrative_is_preserved_across_regeneration(task):
    gwr.main([str(task)])
    path = task / "step3_report" / "WORK_RECORD.md"
    text = path.read_text(encoding="utf-8")
    text = text.replace(
        "[The method in prose: the approach chosen, why it was chosen over the "
        "alternatives, what each step established, and the decisions taken along "
        "the way.]",
        "Pulled SAP, normalised per running hour, ranked the fleet.")
    path.write_text(text, encoding="utf-8")

    gwr.main([str(task)])
    regenerated = path.read_text(encoding="utf-8")
    assert "Pulled SAP, normalised per running hour" in regenerated
    assert regenerated.count("WORK_RECORD:NARRATIVE id=method") == 1


def test_missing_work_record_is_reported(task):
    problems = gwr.check_work_record(task)
    assert problems and "missing" in problems[0].lower()


def test_rejects_non_task_folder(tmp_path):
    assert gwr.main([str(tmp_path)]) == 2


def test_assumption_entries_render_as_prose_not_json():
    """A schema-correct assumption/gap dict must not be dumped as raw JSON."""
    assumption = gwr._format_assumption({
        "assumption": "Levels are percent of instrument range",
        "basis": "Historian unit string is %",
        "effect": "No volume calculation without the data sheet",
    })
    assert assumption.startswith("**Levels are percent of instrument range**")
    assert "Basis: Historian unit string is %" in assumption
    assert "Effect: No volume calculation" in assumption
    assert "{" not in assumption

    gap = gwr._format_assumption({
        "gap": "No temperature transmitter",
        "source": "stid",
        "status": "confirmed absent",
        "assumed": "No temperature used",
        "effect": "No energy balance",
    })
    assert gap.startswith("**No temperature transmitter**")
    assert "Source: stid" in gap
    assert "Assumed instead: No temperature used" in gap
    assert "{" not in gap


def test_assumption_accepts_plain_string_and_unknown_shape():
    assert gwr._format_assumption("plain text") == "plain text"
    # An unrecognised dict still has to produce something, not raise.
    assert gwr._format_assumption({"odd": "shape"})
