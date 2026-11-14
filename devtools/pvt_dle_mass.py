"""Necessary mass-balance checks on primary differential-liberation laboratory data."""

from copy import deepcopy
from decimal import Context, Decimal, InvalidOperation, ROUND_CEILING, ROUND_FLOOR

from pvt_data import PVTStudyError, identity


UNITS = {
    "pressure": "bara",
    "oil_volume_ratio": "m3/m3 residual oil",
    "oil_density": "kg/m3",
    "solution_gor": "Sm3/m3 residual oil",
    "released_gas_molar_mass": "kg/mol",
    "standard_gas_z": "1",
}
UP = Context(prec=34, rounding=ROUND_CEILING)
DOWN = Context(prec=34, rounding=ROUND_FLOOR)
GAS_CONSTANT = Decimal("8.31446261815324")


def _required_text(record, names):
    """Require nonblank provenance entries."""
    for name in names:
        if not isinstance(record.get(name), str) or not record[name].strip():
            raise PVTStudyError("DLE evidence requires " + name)


def _decimal(value):
    """Read a finite decimal without introducing a binary floating-point conversion."""
    if not isinstance(value, str):
        raise PVTStudyError("DLE interval endpoints must be decimal strings")
    try:
        result = Decimal(value)
    except InvalidOperation as error:
        raise PVTStudyError("Invalid DLE interval endpoint") from error
    if not result.is_finite() or result.adjusted() < -150 or result.adjusted() > 150:
        raise PVTStudyError("DLE interval endpoint must be finite with a supported decimal exponent")
    return result


def _interval(item, unit, allow_zero=False):
    """Read an optional, explicitly unit-bearing closed interval."""
    if item is None:
        return None
    if not isinstance(item, dict) or item.get("unit") != unit:
        raise PVTStudyError("DLE interval requires unit " + unit)
    lower, upper = _decimal(item.get("lower")), _decimal(item.get("upper"))
    if lower > upper or lower < 0 or (lower == 0 and not allow_zero):
        raise PVTStudyError("Invalid DLE interval order or sign for " + unit)
    return lower, upper


def _subtract(left, right):
    """Enclose the signed difference of two intervals."""
    return DOWN.subtract(left[0], right[1]), UP.subtract(left[1], right[0])


def _product(left, right):
    """Enclose the product of nonnegative quantities."""
    return DOWN.multiply(left[0], right[0]), UP.multiply(left[1], right[1])


def _divide(top, bottom):
    """Enclose a nonnegative numerator divided by a positive interval."""
    return DOWN.divide(top[0], bottom[1]), UP.divide(top[1], bottom[0])


def _serialize(interval):
    """Return JSON-safe decimal interval endpoints."""
    return [str(endpoint) for endpoint in interval]


def _transition(previous, current, standard_volume):
    """Evaluate one observed differential withdrawal."""
    result = {"status": "unresolved"}
    missing = [side + "." + key for side, state, keys in (
        ("previous", previous, ("oil_volume_ratio", "oil_density", "solution_gor")),
        ("current", current, ("oil_volume_ratio", "oil_density", "solution_gor", "released_gas_molar_mass")),
    ) for key in keys if state[key] is None]
    if missing:
        result.update(reason="missing_transition_input", missing=missing)
        return result

    oil_loss = _subtract(_product(previous["oil_volume_ratio"], previous["oil_density"]),
                         _product(current["oil_volume_ratio"], current["oil_density"]))
    gas_release = _subtract(previous["solution_gor"], current["solution_gor"])
    result.update(oil_mass_loss_kg_per_m3_reference=_serialize(oil_loss),
                  released_gas_sm3_per_m3_reference=_serialize(gas_release))
    if min(oil_loss[1], gas_release[1]) < 0:
        result.update(status="incompatible", reason="negative_oil_loss_or_gas_release")
        return result
    if oil_loss[0] > 0 and gas_release[0] > 0:
        implied = _divide(_product(gas_release, current["released_gas_molar_mass"]), oil_loss)
        result["implied_standard_molar_volume_m3_per_mol"] = _serialize(implied)

    if standard_volume is None or current["standard_gas_z"] is None:
        result["reason"] = "missing_standard_state_or_stage_z"
        return result
    if gas_release[0] < 0:
        # At least zero release remains possible. Use the physical intersection for the mass check.
        gas_release = (Decimal(0), gas_release[1])
    gas_mass = _divide(_product(gas_release, current["released_gas_molar_mass"]),
                       _product(standard_volume, current["standard_gas_z"]))
    residual = _subtract(oil_loss, gas_mass)
    result["mass_residual_kg_per_m3_reference"] = _serialize(residual)
    if residual[0] > 0 or residual[1] < 0:
        result.update(status="incompatible", reason="mass_residual_excludes_zero")
    elif oil_loss[0] > 0 and gas_release[0] > 0:
        result.update(status="compatible", reason="mass_residual_includes_zero")
    else:
        result["reason"] = "zero_or_indeterminate_withdrawal"
    return result


def audit_dle_mass(study):
    """Audit adjacent isothermal DLE stages without changing observations or running an EOS.

    Amounts are per cubic metre of the declared final residual oil. Molecular
    mass and optional standard-state Z belong to the gas removed at the current
    stage. Return a source-identified JSON-ready report with signed residuals.
    """
    if not isinstance(study, dict) or study.get("protocol") != "DLE":
        raise PVTStudyError("DLE mass audit requires a DLE protocol mapping")
    _required_text(study, ("sample_reference", "experiment_reference", "source_reference",
                           "data_use_reference", "residual_oil_reference", "bounds_reference"))
    _interval(study.get("experiment_temperature"), "K")  # Retained as the shared isothermal basis.
    if study.get("experiment_temperature") is None:
        raise PVTStudyError("DLE experiment temperature is required")

    standard = study.get("standard_gas")
    standard_volume = None
    if standard is not None:
        if not isinstance(standard, dict):
            raise PVTStudyError("DLE standard gas state must be a mapping")
        _required_text(standard, ("source_reference",))
        temperature = _interval(standard.get("temperature"), "K")
        pressure = _interval(standard.get("pressure"), "Pa")
        if temperature is None or pressure is None:
            raise PVTStudyError("DLE standard gas state requires temperature and absolute pressure")
        standard_volume = _divide(_product((GAS_CONSTANT, GAS_CONSTANT), temperature), pressure)

    states = study.get("stages")
    if not isinstance(states, list) or len(states) < 2:
        raise PVTStudyError("DLE audit requires at least two ordered stages")
    transitions = []
    previous = None
    ids = set()
    for state in states:
        if not isinstance(state, dict):
            raise PVTStudyError("DLE stage must be a mapping")
        _required_text(state, ("id", "source_reference"))
        stage_id = state["id"]
        if stage_id in ids:
            raise PVTStudyError("Duplicate DLE stage " + stage_id)
        ids.add(stage_id)
        data = {key: _interval(state.get(key), unit, key == "solution_gor") for key, unit in UNITS.items()}
        if data["pressure"] is None or (previous is not None and data["pressure"][1] >= previous["pressure"][0]):
            raise PVTStudyError("DLE pressure intervals must decrease without overlap")
        if data["standard_gas_z"] is not None and standard_volume is None:
            raise PVTStudyError("Stage standard gas Z requires the standard gas state")
        if previous is not None:
            result = _transition(previous, data, standard_volume)
            result.update(previous_stage=previous_id, current_stage=stage_id)
            transitions.append(result)
        previous, previous_id = data, stage_id
    return {"schema": "pvt_dle_mass_audit.v1", "input_sha256": identity(study),
            "raw_input": deepcopy(study), "transitions": transitions}
