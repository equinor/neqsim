"""Necessary mass-balance checks on primary differential-liberation laboratory data."""

from copy import deepcopy
from decimal import Decimal

from pvt_data import PVTStudyError, identity
from pvt_measurement_bounds import GAS_CONSTANT, _divide, _interval, _product, _required_text, _serialize, _subtract


UNITS = {
    "pressure": "bara",
    "oil_volume_ratio": "m3/m3 residual oil",
    "oil_density": "kg/m3",
    "solution_gor": "Sm3/m3 residual oil",
    "released_gas_molar_mass": "kg/mol",
    "standard_gas_z": "1",
    "cell_gas_z": "1",
    "gas_volume_factor": "m3 cell/Sm3 gas",
}
PA_PER_BAR = (Decimal("100000"), Decimal("100000"))


def _transition(previous, current, standard_volume, cell_temperature):
    """Evaluate one observed differential withdrawal."""
    result = {"status": "unresolved"}
    volume = None
    routes = {}
    if standard_volume is not None and current["standard_gas_z"] is not None:
        routes["standard_state"] = _product(standard_volume, current["standard_gas_z"])
    if current["cell_gas_z"] is not None and current["gas_volume_factor"] is not None:
        ideal_volume = _product((GAS_CONSTANT, GAS_CONSTANT), cell_temperature)
        gas_volume = _product(current["cell_gas_z"], ideal_volume)
        cell_pressure = _product(current["pressure"], PA_PER_BAR)
        routes["cell_gas_volume_factor"] = _divide(gas_volume, _product(cell_pressure, current["gas_volume_factor"]))
    if "cell_gas_volume_factor" in routes:
        result["standard_molar_volume_routes_m3_per_mol"] = {name: _serialize(bounds) for name, bounds in routes.items()}
    if routes:
        volume = max(bounds[0] for bounds in routes.values()), min(bounds[1] for bounds in routes.values())
        if volume[0] <= volume[1] and "cell_gas_volume_factor" in routes:
            result["resolved_standard_molar_volume_m3_per_mol"] = _serialize(volume)

    missing = [side + "." + key for side, state, keys in (
        ("previous", previous, ("oil_volume_ratio", "oil_density", "solution_gor")),
        ("current", current, ("oil_volume_ratio", "oil_density", "solution_gor", "released_gas_molar_mass")),
    ) for key in keys if state[key] is None]
    if missing:
        if volume is not None and volume[0] > volume[1]:
            result.update(status="incompatible", reason="standard_molar_volume_routes_disagree", missing=missing)
        else:
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

    if volume is not None and volume[0] > volume[1]:
        result.update(status="incompatible", reason="standard_molar_volume_routes_disagree")
        return result
    if volume is None:
        if current["cell_gas_z"] is not None or current["gas_volume_factor"] is not None:
            result["reason"] = "incomplete_cell_gas_volume_basis"
        else:
            result["reason"] = "missing_standard_state_or_stage_z"
        return result
    if gas_release[0] < 0:
        # At least zero release remains possible. Use the physical intersection for the mass check.
        gas_release = (Decimal(0), gas_release[1])
    gas_mass = _divide(_product(gas_release, current["released_gas_molar_mass"]), volume)
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
    stage. A stated cell gas Z and formation volume factor on the same gas
    and standard-volume basis provide another route to standard molar volume.
    Return a source-identified JSON-ready report with signed residuals.
    """
    if not isinstance(study, dict) or study.get("protocol") != "DLE":
        raise PVTStudyError("DLE mass audit requires a DLE protocol mapping")
    _required_text(study, ("sample_reference", "experiment_reference", "source_reference",
                           "data_use_reference", "residual_oil_reference", "bounds_reference"))
    cell_temperature = _interval(study.get("experiment_temperature"), "K")
    if cell_temperature is None:
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
        if data["cell_gas_z"] is not None or data["gas_volume_factor"] is not None:
            _required_text(state, ("cell_gas_basis_reference",))
            if previous is None:
                raise PVTStudyError("Cell gas data require a differential withdrawal stage")
        if previous is not None:
            result = _transition(previous, data, standard_volume, cell_temperature)
            result.update(previous_stage=previous_id, current_stage=stage_id)
            transitions.append(result)
        previous, previous_id = data, stage_id
    return {"schema": "pvt_dle_mass_audit.v1", "input_sha256": identity(study),
            "raw_input": deepcopy(study), "transitions": transitions}
