"""Check dated reservoir source evidence before constructing compositional tank steps."""

from copy import deepcopy
from datetime import date
from decimal import Decimal

from pvt_data import PVTStudyError, identity
from pvt_measurement_bounds import _decimal, _interval, _required_text, _serialize, _subtract


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
