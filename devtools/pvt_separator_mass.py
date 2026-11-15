"""Audit primary serial separator measurements on the final stock-tank oil basis."""

from copy import deepcopy
from decimal import Decimal

from pvt_data import PVTStudyError, identity
from pvt_measurement_bounds import DOWN, GAS_CONSTANT, UP, _divide, _interval, _product, _required_text, _serialize, _subtract


UNITS = {
    "temperature": "K",
    "pressure": "bara",
    "oil_volume_ratio": "m3/m3 stock tank oil",
    "oil_density": "kg/m3",
    "solution_gor": "Sm3/m3 stock tank oil",
    "released_gas": "Sm3/m3 stock tank oil",
    "standard_gas_density": "kg/Sm3",
    "released_gas_molar_mass": "kg/mol",
    "standard_gas_z": "1",
}
ZERO = Decimal(0)
ONE = Decimal(1)
ZERO_INTERVAL = (ZERO, ZERO)


def _add(left, right):
    """Enclose an interval sum, including signed residuals."""
    return DOWN.add(left[0], right[0]), UP.add(left[1], right[1])


def _includes(interval, value):
    """Determine whether the interval contains a fixed value."""
    return interval[0] <= value <= interval[1]


def _balance(incoming, outgoing, removed):
    """Return the residual and status for an observed balance."""
    if incoming is None or outgoing is None or removed is None:
        return {"status": "unresolved", "reason": "missing_balance_input"}
    residual = _subtract(_subtract(incoming, outgoing), removed)
    status = "compatible" if _includes(residual, ZERO) else "incompatible"
    return {"status": status, "residual": _serialize(residual)}


def _read_stage(raw, ids, previous):
    """Validate the ordered stage identity, conditions, provenance and observation units."""
    if not isinstance(raw, dict):
        raise PVTStudyError("Separator audit stage must be a mapping")
    _required_text(raw, ("id", "source_reference"))
    if raw["id"] in ids:
        raise PVTStudyError("Duplicate separator audit stage " + raw["id"])
    ids.add(raw["id"])
    state = {key: _interval(raw.get(key), unit, key in ("solution_gor", "released_gas"))
             for key, unit in UNITS.items()}
    if state["pressure"] is None or state["temperature"] is None:
        raise PVTStudyError("Separator audit stage requires absolute pressure and temperature")
    if previous is not None and state["pressure"][1] >= previous["pressure"][0]:
        raise PVTStudyError("Separator audit pressure intervals must decrease without overlap")
    if state["oil_density"] is not None:
        _required_text(raw, ("oil_density_reference",))
    return state


def _standard_gas_mass(raw, state, ideal_volume):
    """Convert the independently reported gas volume using source-linked standard-state properties."""
    direct = state["standard_gas_density"]
    mw = state["released_gas_molar_mass"]
    z = state["standard_gas_z"]
    if any(value is not None for value in (direct, mw, z)):
        _required_text(raw, ("standard_gas_property_reference",))
    routes = {}
    if direct is not None:
        routes["reported_density"] = direct
    if mw is not None and z is not None:
        routes["molar_mass_and_z"] = _divide(mw, _product(ideal_volume, z))

    result = {"density_routes_kg_per_sm3": {key: _serialize(value) for key, value in routes.items()}}
    if routes:
        density = max(value[0] for value in routes.values()), min(value[1] for value in routes.values())
        if density[0] > density[1]:
            result.update(status="incompatible", reason="standard_gas_density_routes_disagree")
            return None, result
    else:
        density = None

    volume = state["released_gas"]
    if volume == ZERO_INTERVAL:
        return ZERO_INTERVAL, dict(result, status="available", reason="reported_zero_release",
                                   mass_kg_per_m3_stock_tank_oil=_serialize(ZERO_INTERVAL))
    if volume is None or density is None:
        missing = []
        if volume is None:
            missing.append("released_gas")
        if density is None:
            missing.append("standard_gas_density_or_molar_mass_and_z")
        return None, dict(result, status="unresolved", missing=missing)
    gas_mass = _product(volume, density)
    return gas_mass, dict(result, status="available", resolved_density_kg_per_sm3=_serialize(density),
                          mass_kg_per_m3_stock_tank_oil=_serialize(gas_mass))


def _oil_mass(state):
    """Convert the normalized oil volume and stage density to normalized mass."""
    volume = state["oil_volume_ratio"]
    density = state["oil_density"]
    return _product(volume, density) if volume is not None and density is not None else None


def _transition(previous, current, gas_mass, gas):
    """Screen one step and keep its diagnostic implied density apart from reported gas data."""
    volume = current["released_gas"]
    inlet, outlet = _oil_mass(previous), _oil_mass(current)
    result = {
        "gas": gas,
        "gor_balance_sm3_per_m3_stock_tank_oil": _balance(previous["solution_gor"], current["solution_gor"], volume),
        "mass_balance_kg_per_m3_stock_tank_oil": _balance(inlet, outlet, gas_mass),
    }
    if inlet is not None and outlet is not None:
        loss = _subtract(inlet, outlet)
        result["oil_mass_loss_kg_per_m3_stock_tank_oil"] = _serialize(loss)
        if loss[1] < ZERO:
            result["mass_balance_kg_per_m3_stock_tank_oil"].update(status="incompatible",
                                                                  reason="oil_mass_increases")
        elif loss[0] > ZERO and volume is not None and volume[0] > ZERO:
            result["implied_gas_density_kg_per_sm3"] = _serialize(_divide(loss, volume))
    return result


def _endpoint(state):
    """Check the reported terminal row against the declared physical normalization."""
    tests = {}
    for key, exact in (("oil_volume_ratio", ONE), ("solution_gor", ZERO)):
        bounds = state[key]
        if bounds is None:
            tests[key] = {"status": "unresolved", "reason": "missing_stock_tank_observation"}
        elif _includes(bounds, exact):
            state[key] = (exact, exact)
            tests[key] = {"status": "compatible", "required": str(exact)}
        else:
            # An inconsistent reported endpoint cannot feed a normalized closing balance.
            state[key] = None
            tests[key] = {"status": "incompatible", "required": str(exact), "reported": _serialize(bounds)}
    return tests


def _total(amounts):
    """Sum recorded releases while retaining unknowns in the train total."""
    if any(amount is None for amount in amounts):
        return None
    total = ZERO_INTERVAL
    for amount in amounts:
        total = _add(total, amount)
    return total


def audit_separator_mass(study):
    """Audit a serial oil separator train with gas removal and no material addition.

    Each step uses its own absolute pressure and temperature. Supply the
    incoming oil as the first row and the final reference oil as the last
    row. Pressure decreases without interval overlap. Results use closed,
    outward-rounded bounds and keep the original source map and identity.
    The oil-volume ratio and remaining solution GOR at the terminal row are
    screened separately against the definitions of the reported final basis.
    """
    if not isinstance(study, dict) or study.get("protocol") != "SEPARATOR":
        raise PVTStudyError("Separator mass audit requires a SEPARATOR protocol mapping")
    _required_text(study, ("sample_reference", "experiment_reference", "source_reference",
                           "data_use_reference", "stock_tank_oil_reference", "bounds_reference"))
    standard = study.get("standard_gas")
    if not isinstance(standard, dict):
        raise PVTStudyError("Separator audit requires the standard gas state")
    _required_text(standard, ("source_reference",))
    temperature = _interval(standard.get("temperature"), "K")
    pressure = _interval(standard.get("pressure"), "Pa")
    if temperature is None or pressure is None:
        raise PVTStudyError("Separator standard gas state requires temperature and absolute pressure")
    ideal_volume = _divide(_product((GAS_CONSTANT, GAS_CONSTANT), temperature), pressure)

    stages = study.get("stages")
    if not isinstance(stages, list) or len(stages) < 2:
        raise PVTStudyError("Separator audit requires the feed and at least one withdrawal stage")
    ids = set()
    first = _read_stage(stages[0], ids, None)
    if any(first[key] is not None for key in ("released_gas", "standard_gas_density",
                                              "released_gas_molar_mass", "standard_gas_z")):
        raise PVTStudyError("Separator gas withdrawal data must follow the feed stage")

    transitions = []
    masses = []
    volumes = []
    previous = first
    for index, raw in enumerate(stages[1:], start=1):
        current = _read_stage(raw, ids, previous)
        if index == len(stages) - 1:
            endpoint = _endpoint(current)
        gas_mass, gas = _standard_gas_mass(raw, current, ideal_volume)
        volume = current["released_gas"]
        masses.append(gas_mass)
        volumes.append(volume)
        transition = _transition(previous, current, gas_mass, gas)
        transition.update(previous_stage=stages[index - 1]["id"], current_stage=raw["id"])
        transitions.append(transition)
        previous = current

    return {
        "schema": "pvt_separator_mass_audit.v1", "input_sha256": identity(study), "raw_input": deepcopy(study),
        "transitions": transitions, "stock_tank_endpoint": endpoint,
        "whole_train": {
            "gor_balance_sm3_per_m3_stock_tank_oil": _balance(first["solution_gor"], previous["solution_gor"],
                                                            _total(volumes)),
            "mass_balance_kg_per_m3_stock_tank_oil": _balance(_oil_mass(first), _oil_mass(previous), _total(masses)),
        },
    }
