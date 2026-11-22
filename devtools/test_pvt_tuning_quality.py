"""Tests of the pvt_tuning_quality reporting standard: validator checks and report table expansion."""

import importlib.util
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import validate_task_results as v  # noqa: E402

BLOCK = {
    "summary": "tuned",
    "experiments": [{"sample": "810102", "type": "dew point", "metric": "error vs lab", "before": 43.4, "after": 8.2, "unit": "%"}],
    "parameters": [{"name": "S", "initial": 0.5, "tuned": 0.98, "lower": 0.05, "upper": 0.98, "at_bound": True}],
    "exclusions": [{"sample": "TE CME", "reason": "run on separator liquid"}],
    "figures": ["step2_analysis/figures/fig01.png"],
}


def test_valid_block_has_no_errors_or_warnings():
    errors, warnings = v._validate_pvt_tuning_quality(BLOCK)
    assert errors == [] and warnings == []


def test_block_must_be_object():
    errors, _ = v._validate_pvt_tuning_quality([1])
    assert errors


def test_missing_parts_warn():
    _, warnings = v._validate_pvt_tuning_quality({"experiments": [{"sample": "x"}]})
    text = " ".join(warnings)
    assert "before" in text and "parameters" in text and "exclusions" in text and "figures" in text


def test_task_that_tunes_without_block_is_warned():
    results = {"key_results": {"a": 1}, "validation": {}, "approach": "The EOS was tuned to PVT data.", "conclusions": "ok"}
    _, warnings = v.validate(results)
    assert any("pvt_tuning_quality" in w for w in warnings)
    results["pvt_tuning_quality"] = BLOCK
    _, warnings = v.validate(results)
    assert not any("pvt_tuning_quality" in w for w in warnings)


def _load_generator():
    path = HERE / "task_template" / "step3_report" / "generate_report.py"
    spec = importlib.util.spec_from_file_location("gen_report_for_test", path)
    module = importlib.util.module_from_spec(spec)
    old_argv = sys.argv
    sys.argv = [str(path)]
    try:
        spec.loader.exec_module(module)
    finally:
        sys.argv = old_argv
    return module


def test_report_generator_expands_block_into_tables():
    gen = _load_generator()
    data = gen._expand_pvt_tuning_tables({"pvt_tuning_quality": BLOCK})
    titles = [t["title"] for t in data["tables"]]
    assert "PVT tuning quality: error before and after tuning" in titles
    assert "PVT tuning quality: tuned parameters and bounds" in titles
    assert "PVT tuning quality: data excluded from the tuning" in titles
    params = next(t for t in data["tables"] if "parameters" in t["title"])
    assert params["rows"][0][-1] == "yes"
    again = gen._expand_pvt_tuning_tables(data)
    assert len(again["tables"]) == 3
