"""Check dated reservoir source evidence before constructing compositional tank steps."""

from copy import deepcopy
from datetime import date
from decimal import Decimal
from fractions import Fraction

from pvt_data import PVTStudyError, identity
from pvt_measurement_bounds import (
    DOWN, UP, _decimal, _interval, _product, _required_text, _serialize, _subtract,
)


PRESSURE_KINDS = {
    "field_average", "local_shut_in", "flowing_bottomhole", "wellhead", "simulated", "unclassified",
}
PRESSURE_UNITS = {"bara", "barg", "psia", "psig"}


def _stream_definitions(study):
    """Declare each named cumulative stream and its fixed volume or amount basis."""
    streams = study.get("streams")
    if not isinstance(streams, dict) or not streams:
        raise PVTStudyError("History intake requires named streams")
    for name, stream in streams.items():
        if not isinstance(name, str) or not name.strip() or not isinstance(stream, dict):
            raise PVTStudyError("Invalid history stream declaration")
        _required_text(stream, ("unit", "basis_reference"))
        if stream.get("direction") not in ("production", "injection"):
            raise PVTStudyError("Stream direction must be production or injection")
    return streams


def _date(text):
    """Parse the exact ISO calendar date without repairing a source date."""
    try:
        parsed = date.fromisoformat(text)
    except (ValueError, TypeError):
        return None
    return parsed if parsed.isoformat() == text else None


def _pressure(item, reference_datum):
    """Check eligibility of a source pressure for the current absolute-bar tank interface."""
    if item is None:
        return {"status": "unresolved", "reasons": ["missing_pressure"]}
    if not isinstance(item, dict):
        raise PVTStudyError("History pressure must be a mapping")
    _required_text(item, ("source_reference",))
    kind = item.get("kind")
    if kind not in PRESSURE_KINDS:
        raise PVTStudyError("Unknown history pressure kind")
    observed = item.get("value")
    if not isinstance(observed, dict) or observed.get("unit") not in PRESSURE_UNITS:
        raise PVTStudyError("History pressure requires a recognized absolute or gauge unit")
    unit = observed["unit"]
    if unit in ("bara", "psia"):
        _interval(observed, unit)
    elif _decimal(observed.get("lower")) > _decimal(observed.get("upper")):
        raise PVTStudyError("Invalid gauge pressure interval order")
    sigma = _interval(item.get("sigma"), "bar")
    role = item.get("role")
    if role not in ("calibration", "holdout", None):
        raise PVTStudyError("Pressure role must be calibration or holdout")

    reasons = []
    if kind != "field_average":
        reasons.append("non_field_average_pressure")
    if not reference_datum or item.get("datum_reference") != reference_datum:
        reasons.append("unverified_common_datum")
    if unit != "bara":
        reasons.append("requires_absolute_bar")
    if sigma is None:
        reasons.append("missing_pressure_sigma")
    if role is None:
        reasons.append("missing_pressure_role")
    return {"status": "unresolved" if reasons else "eligible", "reasons": reasons}


def _increment(start, end, unit):
    """Evaluate the full signed range before allowing a nonnegative increment."""
    if start is None or end is None:
        return {"status": "unresolved", "reason": "missing_cumulative_amount"}
    bounds = _subtract(end, start)
    if bounds[1] < 0:
        status = "incompatible"
        reason = "cumulative_amount_decreases"
    elif bounds[0] < 0:
        status = "unresolved"
        reason = "sign_ambiguous_at_reported_precision"
    else:
        status = "eligible"
        reason = "nonnegative_increment"
    normalized = tuple(value if value else Decimal(0) for value in bounds)
    return {"status": status, "reason": reason, "bounds": _serialize(normalized), "unit": unit}


def audit_reservoir_history(study):
    """Return row and adjacent-window evidence without changing any original observation.

    Dates use ISO YYYY-MM-DD and represent boundaries at the same declared
    daily reporting cutoff. Streams are cumulative nonnegative amounts on
    fixed, named bases. Results are a source screen before EOS conversion
    and tank matching. No time alignment, basis conversion or gap filling
    is applied.
    """
    if not isinstance(study, dict) or study.get("protocol") != "CUMULATIVE_RESERVOIR_HISTORY":
        raise PVTStudyError("History intake requires the CUMULATIVE_RESERVOIR_HISTORY protocol")
    _required_text(study, ("reservoir_reference", "source_reference", "data_use_reference",
                           "bounds_reference", "date_basis_reference"))
    streams = _stream_definitions(study)
    datum = study.get("pressure_datum_reference")
    if datum is not None:
        _required_text(study, ("pressure_datum_reference",))
    rows = study.get("rows")
    if not isinstance(rows, list) or len(rows) < 2:
        raise PVTStudyError("History intake requires at least two cumulative rows")

    reports = []
    windows = []
    seen = set()
    latest = None
    previous_day = None
    previous_amounts = None
    for row in rows:
        if not isinstance(row, dict):
            raise PVTStudyError("History row must be a mapping")
        _required_text(row, ("id", "source_reference"))
        if row["id"] in seen:
            raise PVTStudyError("Duplicate history row identity " + row["id"])
        seen.add(row["id"])

        cumulative = row.get("cumulative", {})
        if not isinstance(cumulative, dict) or cumulative.keys() - streams.keys():
            raise PVTStudyError("Cumulative values must reference declared streams")
        amounts = {name: _interval(cumulative.get(name), spec["unit"], allow_zero=True)
                   for name, spec in streams.items()}
        day = _date(row.get("date"))
        date_issue = None
        if day is None:
            date_issue = "missing_or_invalid_calendar_date"
        elif latest is not None and day <= latest:
            date_issue = "date_not_strictly_increasing"
        else:
            latest = day

        pressure = _pressure(row.get("pressure"), datum)
        if date_issue:
            pressure["reasons"].append(date_issue)
            pressure["status"] = "unresolved"
            day = None
        if reports:
            duration = (day - previous_day).days if day and previous_day else None
            increments = {name: _increment(previous_amounts[name], amounts[name], spec["unit"])
                          for name, spec in streams.items()}
            windows.append({"start_row": reports[-1]["id"], "end_row": row["id"],
                            "duration_days": duration, "increments": increments,
                            "eligible_for_flow_handoff": duration is not None and all(
                                item["status"] == "eligible" for item in increments.values())})
        reports.append({"id": row["id"], "date_issue": date_issue, "pressure": pressure})
        previous_day, previous_amounts = day, amounts
    return {"schema": "reservoir_history_intake.v1", "input_sha256": identity(study),
            "raw_input": deepcopy(study), "rows": reports, "windows": windows}


def _composition_bounds(composition):
    """Enclose each mole fraction subject to the complete mixture summing to one."""
    _required_text(composition, ("source_reference", "basis_reference"))
    if composition.get("averaging") not in ("mole_weighted", "constant"):
        raise PVTStudyError("Composition averaging must be mole_weighted or constant over the interval")
    values = composition.get("components")
    if not isinstance(values, dict) or not values:
        raise PVTStudyError("Interval composition requires the complete named component set")
    bounds = {}
    for name, item in values.items():
        if not isinstance(name, str) or not name.strip() or name != name.strip():
            raise PVTStudyError("Component names must be nonblank without surrounding whitespace")
        interval = _interval(item, "mol/mol", allow_zero=True)
        if interval is None or interval[1] > 1:
            raise PVTStudyError("Mole fractions must have explicit bounds between zero and one")
        bounds[name] = interval
    # Exact sums decide feasibility. Directed contexts enclose subsequent marginal calculations.
    lower = sum((Fraction(value[0]) for value in bounds.values()), Fraction(0))
    upper = sum((Fraction(value[1]) for value in bounds.values()), Fraction(0))
    if lower > 1 or upper < 1:
        return None
    tightened = {}
    for name, (lo, hi) in bounds.items():
        other_upper = Decimal(0)
        other_lower = Decimal(0)
        for other, value in bounds.items():
            if other != name:
                other_upper = UP.add(other_upper, value[1])
                other_lower = DOWN.add(other_lower, value[0])
        tightened[name] = (
            max(lo, DOWN.subtract(Decimal(1), other_upper)),
            min(hi, UP.subtract(Decimal(1), other_lower)),
        )
    return tightened


def audit_component_moles(study):
    """Project eligible total-mole windows to outward-rounded named component bounds.

    Each end row may provide interval_compositions by stream. A composition
    describes the complete mole-weighted interval mixture or a composition
    constant throughout that interval, on that stream's declared
    total-mole basis. Fractions are constrained jointly by sum(x)=1. Bounds
    retain covariance limitations and are not independent tank trial values.
    No volume conversion, renormalization, interpolation or EOS fitting occurs.
    """
    report = audit_reservoir_history(study)
    report["schema"] = "reservoir_component_intake.v1"
    streams = study["streams"]
    for row, window in zip(study["rows"][1:], report["windows"]):
        compositions = row.get("interval_compositions", {})
        if not isinstance(compositions, dict) or compositions.keys() - streams.keys():
            raise PVTStudyError("Interval compositions must reference declared streams")
        projected = {}
        for name, spec in streams.items():
            composition = compositions.get(name)
            fractions = None
            reasons = []
            if composition is None:
                reasons.append("missing_interval_composition")
            else:
                if not isinstance(composition, dict):
                    raise PVTStudyError("Interval composition must be a mapping")
                if composition.get("basis_reference") != spec["basis_reference"]:
                    raise PVTStudyError("Composition and stream mole bases must match")
                fractions = _composition_bounds(composition)
                if fractions is None:
                    reasons.append("incompatible_composition_sum")
            if spec["unit"] != "mol":
                reasons.append("requires_total_moles")
            if not window["eligible_for_flow_handoff"]:
                reasons.append("ineligible_flow_window")
            item = {"direction": spec["direction"], "status": "unresolved" if reasons else "eligible",
                    "reasons": reasons, "component_moles": {}}
            if "incompatible_composition_sum" in reasons:
                item["status"] = "incompatible"
            if not reasons:
                increment = tuple(_decimal(value) for value in window["increments"][name]["bounds"])
                item["component_moles"] = {
                    component: {"unit": "mol", "bounds": _serialize(_product(increment, fraction))}
                    for component, fraction in fractions.items()
                }
            projected[name] = item
        window["component_streams"] = projected
        window["eligible_for_component_handoff"] = all(
            value["status"] == "eligible" for value in projected.values())
    return report
