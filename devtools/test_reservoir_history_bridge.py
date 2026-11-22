"""Hermetic routing tests for the selected-source to existing Java history boundary."""

import json
from pathlib import Path
from types import SimpleNamespace

import pytest

from pvt_data import PVTStudyError
from reservoir_history_intake import build_component_history

ROOT = Path(__file__).resolve().parents[1]


def inputs():
    folder = ROOT / "devtools/pvt_examples"
    return (json.loads((folder / "reservoir_component_intake.json").read_text()),
            json.loads((folder / "reservoir_component_selection.json").read_text()))


class Model:
    """Represent the caller-owned immutable bundle for routing tests."""

    def getProvenance(self):
        return '{"original":"v1","tuned":"v2","calibrationExperiments":["lab-only"]}'


class Java:
    """Capture exact constructor arguments independently of the thermodynamic solver."""

    def __init__(self):
        self.calls = []

    def JClass(self, name):
        self.calls.append(name)
        return lambda *args: args


def test_complete_source_routing_and_provenance():
    study, scenario = inputs()
    model = Model()
    java = Java()
    fractions = [1, 0, 0]
    result = build_component_history(study, scenario, model, 400, 200, fractions, "gas reservoir v1", java)
    args = result["history"]
    assert args[0] is model
    assert args[2] == ["methane", "ethane", "water"]
    assert args[3:6] == (400, 200, [1, 0, 0])
    assert args[6] == [(86400, [8.5, 1.5, 0.0], [0.0, 0.0, 2.0], 199.0, 0.2, False)]
    assert result["selection"]["source_sha256"] in args[1]
    assert result["selection"]["scenario_sha256"] in args[1]
    assert "initialization=gas reservoir v1" in args[1]
    assert result["calibration_provenance"] == model.getProvenance()
    fractions[0] = 0
    assert args[5] == [1, 0, 0]


def test_reject_source_before_resolving_java_classes():
    study, scenario = inputs()
    scenario["source_sha256"] = "stale"
    java = Java()
    with pytest.raises(PVTStudyError, match="identity"):
        build_component_history(study, scenario, Model(), 400, 200, [1, 0, 0], "initial", java)
    assert not java.calls


@pytest.mark.parametrize("reference", [None, "", "  "])
def test_initialization_requires_separate_provenance(reference):
    study, scenario = inputs()
    with pytest.raises(PVTStudyError, match="initialization"):
        build_component_history(study, scenario, Model(), 400, 200, [1, 0, 0], reference, Java())


def test_namespace_is_explicit():
    study, scenario = inputs()
    with pytest.raises(PVTStudyError, match="namespace"):
        build_component_history(study, scenario, Model(), 400, 200, [1, 0, 0], "initial", SimpleNamespace())


def test_documented_constructor_helper(monkeypatch):
    path = ROOT / "docs/pvtsimulation/reservoir_history_intake.md"
    fence = chr(96)*3
    code = path.read_text().split(fence+"python\n")[4].split(fence)[0]
    monkeypatch.chdir(ROOT)
    scope = {}
    exec(compile(code, str(path), "exec"), scope)
    built = scope["history_from_source"](Model(), Java())
    assert built["history"][6][0] == (86400, [8.5, 1.5, 0.0], [0.0, 0.0, 2.0], 199.0, 0.2, False)
