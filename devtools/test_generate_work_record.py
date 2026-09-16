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
