"""Qualify source-intake bounds against exact rational arithmetic."""

import json
from fractions import Fraction
from itertools import product
from pathlib import Path

import pytest

from pvt_data import PVTStudyError, identity
from reservoir_history_intake import audit_reservoir_history


ROOT = Path(__file__).resolve().parents[1]
EXAMPLE = ROOT / "devtools/pvt_examples/reservoir_history_intake.json"
OIL = "well-A oil"
WATER = "well-B water"


def example():
    """Read the documented example without sharing mutable state."""
    return json.loads(EXAMPLE.read_text(encoding="utf-8"))


def test_documented_example(monkeypatch, capsys):
    """Execute the source guide snippet from the repository root."""
    doc = ROOT / "docs/pvtsimulation/reservoir_history_intake.md"
    code = doc.read_text(encoding="utf-8").split("```python\n")[1].split("```")[0]
    monkeypatch.chdir(ROOT)
    exec(compile(code, str(doc), "exec"), {})
    assert capsys.readouterr().out.strip() == "[True, True]"


def test_immutable_source_roles_dates_and_exact_no_flow():
    study = example()
    report = audit_reservoir_history(study)
    assert report["input_sha256"] == identity(study)
    assert report["raw_input"] == study
    assert [row["pressure"]["status"] for row in report["rows"]] == ["unresolved", "eligible", "eligible"]
    assert [row["pressure"]["role"] for row in report["raw_input"]["rows"][1:]] == ["calibration", "holdout"]
    assert [row["duration_days"] for row in report["windows"]] == [1, 3]
    assert report["windows"][1]["increments"][WATER]["bounds"] == ["0", "0"]
    study["rows"][0]["date"] = "changed"
    assert report["raw_input"] == example()


@pytest.mark.parametrize("final_bounds,status", [
    (("8.4", "8.6"), "eligible"),
    (("1.005001", "2.005009"), "incompatible"),
    (("6", "6.1"), "unresolved"),
    (("6.1", "6.1"), "eligible"),
    (("6.099999999999999999999999999999999999", "6.10"), "unresolved"),
])
def test_signed_enclosure_and_source_admission(final_bounds, status):
    study = example()
    earlier, later = (r["cumulative"][OIL] for r in study["rows"][1:])
    later.update(zip(("lower", "upper"), final_bounds))
    exact = [Fraction(end) - Fraction(start) for start, end in product(
        (earlier["lower"], earlier["upper"]), final_bounds)]
    window = audit_reservoir_history(study)["windows"][1]
    result = window["increments"][OIL]
    lo, hi = map(Fraction, result["bounds"])
    assert lo <= min(exact) <= max(exact) <= hi
    assert max(min(exact) - lo, hi - max(exact)) <= Fraction("1e-33") * max(1, abs(lo), abs(hi))
    assert result["status"] == status
    assert window["eligible_for_flow_handoff"] == (status == "eligible")


@pytest.mark.parametrize("value,reason", [
    ("2024-02-30", "missing_or_invalid_calendar_date"),
    ("20240229", "missing_or_invalid_calendar_date"),
    ("2024-W09-4", "missing_or_invalid_calendar_date"),
    (None, "missing_or_invalid_calendar_date"),
    ("2024-02-28", "date_not_strictly_increasing"),
])
def test_invalid_date_cannot_bridge_windows(value, reason):
    study = example()
    study["rows"][1]["date"] = value
    result = audit_reservoir_history(study)
    assert result["rows"][1]["date_issue"] == reason
    assert result["rows"][1]["pressure"]["status"] == "unresolved"
    assert all(row["duration_days"] is None and not row["eligible_for_flow_handoff"] for row in result["windows"])


def test_missing_input_differs_from_zero_no_bridge_or_inferred_rate():
    study = example()
    study["rows"][1]["cumulative"].pop(WATER)
    result = audit_reservoir_history(study)
    for row in result["windows"]:
        assert row["increments"][WATER]["reason"] == "missing_cumulative_amount"
        assert row["increments"][OIL]["status"] == "eligible"
        assert not row["eligible_for_flow_handoff"]


@pytest.mark.parametrize("kind", ["flowing_bottomhole", "local_shut_in", "wellhead", "simulated", "unclassified"])
def test_pressure_kind_is_preserved_but_ineligible(kind):
    study = example()
    study["rows"][1]["pressure"]["kind"] = kind
    result = audit_reservoir_history(study)
    assert result["rows"][1]["pressure"]["reasons"] == ["non_field_average_pressure"]
    assert result["raw_input"]["rows"][1]["pressure"]["kind"] == kind


def test_pressure_basis_and_sigma_require_explicit_evidence():
    study = example()
    datum = study["rows"][1]["pressure"]
    datum["datum_reference"] = "Well-gauge timestamp and depth"
    datum["value"].update(unit="psig", lower="-1", upper="-1")
    datum.pop("sigma")
    datum.pop("role")
    result = audit_reservoir_history(study)
    assert result["rows"][1]["pressure"] == {"status": "unresolved", "reasons": [
        "unverified_common_datum", "requires_absolute_bar", "missing_pressure_sigma", "missing_pressure_role"]}
    assert result["raw_input"]["rows"][1]["pressure"] == datum


@pytest.mark.parametrize("change,reason", [
    (lambda s: s["rows"][1].update(id="start"), "Duplicate"),
    (lambda s: s["rows"][1]["cumulative"].update(unknown={}), "declared streams"),
    (lambda s: s["rows"][1]["cumulative"][OIL].update(unit="m3"), "unit Sm3"),
    (lambda s: s["streams"][WATER].update(direction="net"), "direction"),
    (lambda s: s["rows"][1]["pressure"]["sigma"].update(lower="0"), "sign"),
    (lambda s: s["rows"][1]["pressure"].update(kind="unknown"), "pressure kind"),
    (lambda s: s["rows"][1]["pressure"].update(role="fit-all"), "Pressure role"),
    (lambda s: s["streams"][OIL].pop("basis_reference"), "basis_reference"),
])
def test_reject_bad_schema(change, reason):
    study = example()
    change(study)
    with pytest.raises(PVTStudyError, match=reason):
        audit_reservoir_history(study)


def test_seeded_exact_rational_increments():
    """Compare a fixed set of near-zero and reversed inputs with Fraction arithmetic."""
    from random import Random

    rng = Random(4740)
    study = example()
    start = study["rows"][1]["cumulative"][OIL]
    end = study["rows"][2]["cumulative"][OIL]
    counts = {"eligible": 0, "unresolved": 0, "incompatible": 0}
    for _ in range(1024):
        center = rng.randint(10, 10000)
        parameters = [center, center + rng.randint(0, 8), center + rng.randint(-10, 10)]
        parameters.append(parameters[2] + rng.randint(0, 8))
        strings = [str(value // 1000) + "." + str(value % 1000).zfill(3) for value in parameters]
        start.update(lower=strings[0], upper=strings[1])
        end.update(lower=strings[2], upper=strings[3])
        a, b, c, d = (Fraction(value, 1000) for value in parameters)
        expected = (c - b, d - a)
        result = audit_reservoir_history(study)["windows"][1]["increments"][OIL]
        assert tuple(map(Fraction, result["bounds"])) == expected
        status = "incompatible" if expected[1] < 0 else "unresolved" if expected[0] < 0 else "eligible"
        assert result["status"] == status
        counts[status] += 1
    assert min(counts.values()) > 0
