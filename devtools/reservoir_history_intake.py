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


def _selected_value(value, lower, upper, label):
    """Require a supplied decimal to lie within exact rational source bounds."""
    number = Fraction(_decimal(value))
    if not lower <= number <= upper:
        raise PVTStudyError("Selected " + label + " is outside the source bounds")
    return number


def _exact_decimal(value):
    """Serialize finite-decimal arithmetic without rounding component conservation."""
    denominator = value.denominator
    twos = fives = 0
    while denominator % 2 == 0:
        denominator //= 2
        twos += 1
    while denominator % 5 == 0:
        denominator //= 5
        fives += 1
    if denominator != 1:
        raise PVTStudyError("Selected amount has no exact finite-decimal representation")
    places = max(twos, fives)
    coefficient = abs(value.numerator) * 2 ** (places - twos) * 5 ** (places - fives)
    digits = tuple(int(digit) for digit in str(coefficient))
    return str(Decimal((int(value < 0), digits, -places)))


def select_component_history(study, scenario):
    """Validate an explicit joint scenario and return ordered molar tank-step inputs.

    Selections specify every admitted interval, stream total, complete composition,
    and end pressure/sigma. Exact rational checks use raw cumulative endpoints,
    rather than the outward-rounded marginal report. The caller chooses the
    scenario and its prior weight separately. Output decimal arrays preserve
    conservation before the caller converts them to the Java double boundary.
    """
    report = audit_component_moles(study)
    if not isinstance(scenario, dict):
        raise PVTStudyError("Component scenario must be a mapping")
    _required_text(scenario, ("reference", "source_sha256"))
    if scenario["source_sha256"] != report["input_sha256"]:
        raise PVTStudyError("Component scenario source identity does not match the history")
    order = scenario.get("component_order")
    if not isinstance(order, list) or not order or any(
            not isinstance(name, str) or not name.strip() or name != name.strip() for name in order):
        raise PVTStudyError("Scenario requires an explicit named component order")
    if len(order) != len(set(order)):
        raise PVTStudyError("Scenario component order contains duplicates")
    selected_windows = scenario.get("windows")
    if not isinstance(selected_windows, list) or len(selected_windows) != len(report["windows"]):
        raise PVTStudyError("Scenario must select every source interval")
    if not all(window["eligible_for_component_handoff"] for window in report["windows"]):
        raise PVTStudyError("Scenario requires an eligible component flow window")
    used_components = set()
    accumulated = {name: Fraction(0) for name in study["streams"]}
    origins = {name: (Fraction(_decimal(study["rows"][0]["cumulative"][name]["lower"])),
                      Fraction(_decimal(study["rows"][0]["cumulative"][name]["upper"])))
               for name in study["streams"]}
    steps = []
    for index, (window, selected) in enumerate(zip(report["windows"], selected_windows)):
        if not isinstance(selected, dict) or selected.get("end_row") != window["end_row"]:
            raise PVTStudyError("Scenario intervals must match the dated source order")
        row = study["rows"][index + 1]
        previous = study["rows"][index]
        pressure = report["rows"][index + 1]["pressure"]
        if pressure["status"] != "eligible":
            raise PVTStudyError("Scenario requires an eligible field-average pressure and sigma")
        stream_values = selected.get("streams")
        if not isinstance(stream_values, dict) or stream_values.keys() != study["streams"].keys():
            raise PVTStudyError("Scenario must select every named source stream")
        totals = {direction: {name: Fraction(0) for name in order}
                  for direction in ("production", "injection")}
        named_streams = {}
        for name, spec in study["streams"].items():
            chosen = stream_values[name]
            if not isinstance(chosen, dict):
                raise PVTStudyError("Selected stream must be a mapping")
            earlier = previous["cumulative"][name]
            later = row["cumulative"][name]
            lower = Fraction(_decimal(later["lower"])) - Fraction(_decimal(earlier["upper"]))
            upper = Fraction(_decimal(later["upper"])) - Fraction(_decimal(earlier["lower"]))
            total = _selected_value(chosen.get("total_moles"), lower, upper, "stream total")
            accumulated[name] += total
            origin_lower = max(origins[name][0], Fraction(_decimal(later["lower"])) - accumulated[name])
            origin_upper = min(origins[name][1], Fraction(_decimal(later["upper"])) - accumulated[name])
            if origin_lower > origin_upper:
                raise PVTStudyError("Selected intervals are incompatible with shared cumulative endpoints")
            origins[name] = origin_lower, origin_upper
            source_fractions = row["interval_compositions"][name]["components"]
            fractions = chosen.get("mole_fractions")
            if not isinstance(fractions, dict) or fractions.keys() != source_fractions.keys():
                raise PVTStudyError("Selected composition must include every source component")
            selected_fractions = {}
            for component, source in source_fractions.items():
                if component not in totals[spec["direction"]]:
                    raise PVTStudyError("Scenario component order omits a source component")
                used_components.add(component)
                selected_fractions[component] = _selected_value(
                    fractions[component], Fraction(_decimal(source["lower"])),
                    Fraction(_decimal(source["upper"])), "mole fraction")
            if sum(selected_fractions.values(), Fraction(0)) != 1:
                raise PVTStudyError("Selected mole fractions must sum exactly to one")
            amounts = {component: total * fraction for component, fraction in selected_fractions.items()}
            for component, amount in amounts.items():
                totals[spec["direction"]][component] += amount
            named_streams[name] = {
                "direction": spec["direction"], "total_moles": _exact_decimal(total),
                "component_moles": {key: _exact_decimal(value) for key, value in amounts.items()},
            }
        measured = row["pressure"]
        chosen_pressure = _selected_value(
            selected.get("pressure_bara"), Fraction(_decimal(measured["value"]["lower"])),
            Fraction(_decimal(measured["value"]["upper"])), "pressure")
        chosen_sigma = _selected_value(
            selected.get("sigma_bara"), Fraction(_decimal(measured["sigma"]["lower"])),
            Fraction(_decimal(measured["sigma"]["upper"])), "pressure sigma")
        steps.append({
            "end_row": window["end_row"], "duration_seconds": window["duration_days"] * 86400,
            "streams": named_streams,
            "produced_moles": [_exact_decimal(totals["production"][name]) for name in order],
            "injected_moles": [_exact_decimal(totals["injection"][name]) for name in order],
            "pressure_bara": _exact_decimal(chosen_pressure), "sigma_bara": _exact_decimal(chosen_sigma),
            "holdout": measured["role"] == "holdout",
        })
    if used_components != set(order):
        raise PVTStudyError("Scenario component order must contain exactly the source component set")
    return {"schema": "reservoir_component_selection.v1", "source_sha256": report["input_sha256"],
            "scenario_sha256": identity(scenario), "raw_input": deepcopy(study),
            "raw_scenario": deepcopy(scenario), "component_order": list(order),
            "cumulative_origin_bounds": {name: [_exact_decimal(lo), _exact_decimal(hi)]
                                         for name, (lo, hi) in origins.items()}, "steps": steps}


def build_component_history(study, scenario, model, temperature_k, pressure_bara,
                            phase_volume_fractions, initial_reference, java):
    """Construct the existing Java history from a revalidated conserved selection.

    The caller supplies an accepted CalibratedPvtModel and an already initialized
    namespace from neqsim_dev_setup. Initial gas/oil/aqueous fractions are
    reservoir initialization inputs. Java validates the complete EOS component
    set and initialization values; replay subsequently checks phase feasibility.
    The returned history is reusable for replay and bounded volume matching.
    Exact source selections accompany the conversion to Java double arrays.
    """
    if not isinstance(initial_reference, str) or not initial_reference.strip():
        raise PVTStudyError("Reservoir initialization requires a provenance reference")
    selected = select_component_history(study, scenario)
    if not callable(getattr(java, "JClass", None)):
        raise PVTStudyError("An initialized NeqSim Java namespace is required")
    history_type = java.JClass("neqsim.pvtsimulation.util.CompositionalReservoirHistory")
    step_type = java.JClass("neqsim.pvtsimulation.util.CompositionalReservoirHistory$Step")
    steps = [
        step_type(row["duration_seconds"], [float(value) for value in row["produced_moles"]],
                  [float(value) for value in row["injected_moles"]], float(row["pressure_bara"]),
                  float(row["sigma_bara"]), row["holdout"])
        for row in selected["steps"]
    ]
    fractions = list(phase_volume_fractions)
    reference = (scenario["reference"] + "; source_sha256=" + selected["source_sha256"]
                 + "; scenario_sha256=" + selected["scenario_sha256"]
                 + "; initialization=" + initial_reference)
    history = history_type(model, reference, selected["component_order"], temperature_k,
                           pressure_bara, fractions, steps)
    return {"history": history, "selection": selected,
            "calibration_provenance": str(model.getProvenance()),
            "initialization": {"reference": initial_reference, "temperature_k": temperature_k,
                               "pressure_bara": pressure_bara, "phase_volume_fractions": fractions}}
