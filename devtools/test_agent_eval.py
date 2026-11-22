"""CI entry for the agent evaluation harness (see ``agent_eval.py``).

One test per golden case so a regression names the prompt that broke.
"""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))

import agent_eval  # noqa: E402

CASES = agent_eval.load_cases()


@pytest.mark.parametrize("case", CASES["routing"], ids=lambda c: c["id"])
def test_routing(case):
    result = agent_eval.eval_routing(case)
    assert result["agent_hit"], "expected one of {} in top agents {}".format(
        case.get("expect_agent_top3"), result["agents_top"])
    assert not result["missing_skills"], "skills {} missing from top {}".format(
        result["missing_skills"], result["skills_top"])


@pytest.mark.parametrize("case", CASES["routing"], ids=lambda c: c["id"])
def test_core_only_routing(case, monkeypatch):
    """A community installation must not mask a broken public core fallback."""
    import agent_search
    import discovery_registry

    discover = agent_search._discover_roots
    monkeypatch.setattr(agent_search, "_discover_roots", lambda root, extra: [
        row for row in discover(root, extra) if row[1] == "neqsim"])
    monkeypatch.setattr(discovery_registry, "sibling_skill_roots", lambda root: [])
    monkeypatch.setattr(discovery_registry, "installed_skills_roots", lambda: [])
    monkeypatch.setattr(discovery_registry, "plugin_skill_roots", lambda: [])
    result = agent_eval.eval_routing(case)
    assert result["ok"], result


@pytest.mark.parametrize("case", CASES["contracts"], ids=lambda c: c["id"])
def test_contract(case):
    result = agent_eval.eval_contract(case)
    assert result["ok"], "expected {}; validator errors: {}".format(case["expect"], result["errors"])
