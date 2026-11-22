"""Tests for the Bayesian hypothesis tracker used by living root-cause tasks (continuous/hypotheses.yaml)."""
import os

import pytest
import yaml

from neqsim_continuous import hypotheses as hyp


def _spec(**extra):
    spec = {
        "question": "q", "floor": 0.02,
        "hypotheses": [{"id": "a", "name": "Cause A (detail)", "prior": 0.5},
                       {"id": "b", "name": "Cause B", "prior": 0.5}],
        "observations": [], "tests": []}
    spec.update(extra)
    return spec


def _obs(oid, la, lb, **extra):
    data = {"id": oid, "date": "2026-01-0{}".format(len(oid)), "likelihood": {"a": la, "b": lb}}
    data.update(extra)
    return data


def test_single_observation_is_exact_bayes():
    result = hyp.compute(_spec(observations=[_obs("x", 0.8, 0.2)]))
    assert result["posterior"]["a"] == pytest.approx(0.8)
    assert result["weights"][0]["odds_factor"] == pytest.approx(4.0)
    assert result["short"]["a"] == "Cause A"


def test_unreliable_observation_carries_less_and_zero_reliability_none():
    full = hyp.compute(_spec(observations=[_obs("x", 0.8, 0.2)]))["posterior"]["a"]
    half = hyp.compute(_spec(observations=[_obs("x", 0.8, 0.2, reliability=0.5)]))["posterior"]["a"]
    none = hyp.compute(_spec(observations=[_obs("x", 0.8, 0.2, reliability=0.0)]))["posterior"]["a"]
    assert 0.5 < half < full and none == pytest.approx(0.5)


def test_repeats_in_a_family_are_tempered():
    observations = [_obs("x", 0.8, 0.2, family="f"), _obs("xx", 0.8, 0.2, family="f")]
    result = hyp.compute(_spec(observations=observations, family_decay=0.5))
    odds = 4.0 * 4.0 ** 0.5
    assert result["posterior"]["a"] == pytest.approx(odds / (1 + odds))
    independent = hyp.compute(_spec(observations=[_obs("x", 0.8, 0.2), _obs("xx", 0.8, 0.2)]))
    assert independent["posterior"]["a"] == pytest.approx(16.0 / 17.0)


def test_information_gain_of_perfect_and_useless_tests():
    tests = [{"id": "perfect", "cost": 1, "outcomes": {"p": {"a": 1.0, "b": 0.0}, "q": {"a": 0.0, "b": 1.0}}},
             {"id": "useless", "cost": 1, "outcomes": {"p": {"a": 0.5, "b": 0.5}, "q": {"a": 0.5, "b": 0.5}}}]
    result = hyp.compute(_spec(tests=tests))
    gains = {t["id"]: t["info_cause_bits"] for t in result["tests"]}
    assert gains["perfect"] == pytest.approx(1.0, abs=1e-4)
    assert gains["useless"] == pytest.approx(0.0, abs=1e-9)
    assert result["tests"][0]["id"] == "perfect"


def test_cheap_test_ranks_first_per_unit_cost():
    outcomes = {"p": {"a": 0.9, "b": 0.1}, "q": {"a": 0.1, "b": 0.9}}
    tests = [{"id": "dear", "cost": 10, "outcomes": outcomes},
             {"id": "cheap", "cost": 1, "outcomes": {"p": {"a": 0.7, "b": 0.3}, "q": {"a": 0.3, "b": 0.7}}}]
    assert hyp.compute(_spec(tests=tests))["tests"][0]["id"] == "cheap"


def test_auto_ranking_switches_to_cause_when_decision_is_settled():
    spec = _spec(hypotheses=[{"id": "a", "prior": 0.6}, {"id": "a2", "prior": 0.38}, {"id": "b", "prior": 0.02}],
                 decision_groups={"ok": ["a", "a2"], "not_ok": ["b"]})
    assert hyp.compute(spec)["rank_by"] == "info_cause_bits"
    spec["hypotheses"][2]["prior"] = 0.5
    assert hyp.compute(spec)["rank_by"] == "info_decision_bits"


def test_floor_is_a_reporting_threshold_not_a_cap():
    result = hyp.compute(_spec(observations=[_obs("x", 0.999, 0.001)]))
    assert result["posterior"]["a"] > 0.99
    assert result["below_floor"] == ["b"]
    assert hyp.format_probability(result["posterior"]["b"], 0.02) == "< 2%"


def test_validate_catches_common_mistakes():
    spec = _spec(observations=[{"id": "x", "likelihood": {"a": 0.8}}, {"id": "x", "likelihood": {"a": 1.2, "b": 0.1}}],
                 tests=[{"id": "t", "outcomes": {"p": {"a": 0.6, "b": 0.5}, "q": {"a": 0.6, "b": 0.5}}}],
                 decision_groups={"g": ["a"]})
    problems = " | ".join(hyp.validate(spec))
    for text in ("missing b", "duplicate id", "within 0..1", "sum to 1.20", "decision_groups"):
        assert text in problems


def test_template_is_valid():
    assert hyp.validate(yaml.safe_load(hyp.template())) == []


def test_run_writes_state_and_daily_report_shows_section(tmp_path):
    pytest.importorskip("docx")
    task = str(tmp_path / "task")
    cont = os.path.join(task, "continuous")
    os.makedirs(os.path.join(cont, "daily"))
    with open(os.path.join(cont, "cycle_plan.yaml"), "w", encoding="utf-8") as handle:
        handle.write("schema_version: '1.0'\ndaily_report: {enabled: true}\n")
    with open(os.path.join(cont, "hypotheses.yaml"), "w", encoding="utf-8") as handle:
        handle.write(hyp.template())
    with open(os.path.join(cont, "daily", "2026-01-02.json"), "w", encoding="utf-8") as handle:
        handle.write('{"date": "2026-01-02", "headline": "h"}')
    result, problems = hyp.run(task)
    assert not problems and os.path.isfile(os.path.join(cont, hyp.STATE_FILE))
    assert result["robustness"][0]["id"] == "step_without_process_change"

    from docx import Document
    from neqsim_continuous import daily_report
    path = daily_report.build(task)
    text = "\n".join(p.text for p in Document(path).paragraphs)
    assert "Candidate causes, weighted by the evidence" in text
    assert "Next most informative test:" in text
