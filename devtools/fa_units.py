"""Units, rate bases and inhibitor mass/mole conversions for the flow-assurance study report.

Pure Python (no JVM). Every public function raises :class:`FlowAssuranceError` subclasses with an explanatory
message instead of returning a guessed value. Pressure units distinguish absolute (``bara``) from gauge (``barg``).
"""
import math

ATM_BAR = 1.01325
MW = {"water": 18.01528, "MEG": 62.06784, "methanol": 32.04186, "TEG": 150.1736, "DEG": 106.1204}
STD_DEFAULT = {"t_c": 15.0, "p_bara": ATM_BAR}


class FlowAssuranceError(Exception):
    """Base class of the errors raised by the flow-assurance toolkit (bad input, not a software fault)."""


class UnitError(FlowAssuranceError):
    """Unknown or incompatible unit."""


class GeometryError(FlowAssuranceError):
    """Route geometry that is not usable (gaps, duplicates, impossible lengths)."""


class TopologyError(FlowAssuranceError):
    """Unsupported or inconsistent topology variant."""


_FAMILIES = {
    "pressure": {"Pa": 1.0, "kPa": 1.0e3, "MPa": 1.0e6, "bar": 1.0e5, "bara": 1.0e5, "barg": 1.0e5, "psia": 6894.757293168,
                 "psig": 6894.757293168},
    "temperature": {"K": 1.0, "C": 1.0, "F": 1.0},
    "length": {"m": 1.0, "km": 1.0e3, "mm": 1.0e-3, "in": 0.0254, "ft": 0.3048, "nmi": 1852.0},
    "mass_flow": {"kg/s": 1.0, "kg/h": 1.0 / 3600.0, "kg/hr": 1.0 / 3600.0, "kg/d": 1.0 / 86400.0, "t/h": 1000.0 / 3600.0,
                  "t/d": 1000.0 / 86400.0},
    "molar_flow": {"mol/s": 1.0, "kmol/h": 1000.0 / 3600.0, "kmol/d": 1000.0 / 86400.0},
    "std_volume_flow": {"Sm3/s": 1.0, "Sm3/h": 1.0 / 3600.0, "Sm3/d": 1.0 / 86400.0, "MSm3/d": 1.0e6 / 86400.0,
                        "kSm3/d": 1.0e3 / 86400.0},
    "actual_volume_flow": {"m3/s": 1.0, "m3/h": 1.0 / 3600.0, "m3/d": 1.0 / 86400.0},
    "time": {"s": 1.0, "min": 60.0, "h": 3600.0, "d": 86400.0},
    "temperature_difference": {"dK": 1.0},
    "velocity": {"m/s": 1.0},
    "dimensionless": {"-": 1.0, "fraction": 1.0, "wt%": 0.01, "mol%": 0.01, "%": 0.01},
}
_UNIT_FAMILY = {}
for _fam, _units in _FAMILIES.items():
    for _u in _units:
        _UNIT_FAMILY.setdefault(_u, _fam)
_SUFFIX_UNITS = (("_bara", "bara"), ("_barg", "barg"), ("_bar", "bar"), ("_c", "C"), ("_k", "dK"), ("_kg_s", "kg/s"),
                 ("_kg_h", "kg/h"), ("_m3_s", "m3/s"), ("_sm3_d", "Sm3/d"), ("_m_s", "m/s"), ("_km", "km"), ("_m", "m"),
                 ("_s", "s"), ("_h", "h"), ("_pct", "%"), ("_wt_pct", "wt%"))


def family(unit):
    """Return the unit family name (``pressure``, ``length`` ...) of ``unit``."""
    try:
        return _UNIT_FAMILY[unit]
    except KeyError:
        raise UnitError("unknown unit '%s'" % unit)


def suffix_unit(key):
    """Unit implied by a result key suffix, e.g. ``p_bara`` -> ``bara``; None when the key has no unit suffix."""
    for suffix, unit in _SUFFIX_UNITS:
        if str(key).endswith(suffix):
            return unit
    return None


def convert(value, from_unit, to_unit):
    """Convert a scalar between compatible units; raises :class:`UnitError` for unknown or incompatible units.

    A temperature difference (``dK``) never converts to an absolute temperature.
    """
    if value is None:
        return None
    ff, ft = family(from_unit), family(to_unit)
    if from_unit == to_unit:
        return float(value)
    if ff != ft:
        raise UnitError("cannot convert %s (%s) to %s (%s)" % (from_unit, ff, to_unit, ft))
    v = float(value)
    if ff == "temperature":
        kelvin = {"K": v, "C": v + 273.15, "F": (v - 32.0) / 1.8 + 273.15}[from_unit]
        return {"K": kelvin, "C": kelvin - 273.15, "F": (kelvin - 273.15) * 1.8 + 32.0}[to_unit]
    if ff == "pressure":
        pa = v * _FAMILIES[ff][from_unit]
        if from_unit in ("barg", "psig"):
            pa += ATM_BAR * 1.0e5
        if to_unit in ("barg", "psig"):
            pa -= ATM_BAR * 1.0e5
        return pa / _FAMILIES[ff][to_unit]
    return v * _FAMILIES[ff][from_unit] / _FAMILIES[ff][to_unit]


def finite_positive(value, name):
    """Return ``value`` as float when it is finite and > 0, else raise :class:`FlowAssuranceError`."""
    try:
        v = float(value)
    except (TypeError, ValueError):
        raise FlowAssuranceError("%s is not a number: %r" % (name, value))
    if not math.isfinite(v) or v <= 0.0:
        raise FlowAssuranceError("%s must be finite and positive, got %r" % (name, value))
    return v


RATE_BASES = ("mass", "molar", "std_volume", "actual_volume")


def check_rate(rate, name="rate"):
    """Validate a rate mapping ``{value, unit, basis[, p_bara, t_c]}`` and return it normalised.

    The basis (mass, molar, std_volume, actual_volume) must match the unit family; actual volumes need the pressure and
    temperature they refer to. Standard volumes refer to the standard conditions stated in the study conventions.
    """
    if not isinstance(rate, dict):
        raise FlowAssuranceError("%s must be a mapping with value, unit and basis" % name)
    for key in ("value", "unit", "basis"):
        if rate.get(key) in (None, ""):
            raise FlowAssuranceError("%s.%s is missing" % (name, key))
    basis = rate["basis"]
    if basis not in RATE_BASES:
        raise FlowAssuranceError("%s.basis must be one of %s, got '%s'" % (name, RATE_BASES, basis))
    want = {"mass": "mass_flow", "molar": "molar_flow", "std_volume": "std_volume_flow", "actual_volume": "actual_volume_flow"}[basis]
    if family(rate["unit"]) != want:
        raise UnitError("%s: unit '%s' does not belong to basis '%s'" % (name, rate["unit"], basis))
    if basis == "actual_volume" and not (rate.get("p_bara") and rate.get("t_c") is not None):
        raise FlowAssuranceError("%s: an actual-volume rate needs p_bara and t_c" % name)
    out = dict(rate)
    out["value"] = finite_positive(rate["value"], name + ".value")
    return out


def mass_rate_kg_s(rate, std_density_kg_sm3=None, mean_mw=None):
    """Convert a validated rate to kg/s. Standard-volume rates need ``std_density_kg_sm3``, molar rates ``mean_mw`` (g/mol)."""
    r = check_rate(rate)
    b = r["basis"]
    if b == "mass":
        return convert(r["value"], r["unit"], "kg/s")
    if b == "molar":
        if not mean_mw:
            raise FlowAssuranceError("molar rate needs the mean molar mass")
        return convert(r["value"], r["unit"], "mol/s") * mean_mw / 1000.0
    if b == "std_volume":
        if not std_density_kg_sm3:
            raise FlowAssuranceError("standard-volume rate needs the standard density of the fluid")
        return convert(r["value"], r["unit"], "Sm3/s") * std_density_kg_sm3
    raise FlowAssuranceError("actual-volume rates are not converted without a density from the model")


# --- inhibitor mass/mole bookkeeping -------------------------------------------------------------
def aqueous_inhibitor_moles(water_mol, wt_frac, inhibitor="MEG"):
    """Moles of inhibitor that give a MASS fraction ``wt_frac`` of inhibitor in the water + inhibitor mixture.

    Args:
        water_mol: moles of water in the mixture.
        wt_frac: inhibitor / (inhibitor + water) by mass, 0 <= wt_frac < 1.
        inhibitor: key of :data:`MW`.

    A mole fraction must never be passed here or used as a weight percentage: 5 mol of MEG per 15 mol of water is
    53.5 wt% MEG in the aqueous mixture, not 25 wt%.
    """
    if not 0.0 <= wt_frac < 1.0:
        raise FlowAssuranceError("inhibitor mass fraction must be in [0, 1), got %r" % wt_frac)
    water_mass = water_mol * MW["water"]
    return water_mass * wt_frac / (1.0 - wt_frac) / MW[inhibitor]


def aqueous_wt_fraction(water_mass, inhibitor_mass):
    """Mass fraction of inhibitor in the aqueous mixture (both masses in the same unit)."""
    total = water_mass + inhibitor_mass
    return inhibitor_mass / total if total > 0.0 else 0.0


def lean_injection_rate(water_rate, target_wt, purity_wt):
    """Lean inhibitor stream rate that gives ``target_wt`` inhibitor mass fraction in the aqueous phase.

    Mass balance: the lean stream is ``purity_wt`` inhibitor and ``1 - purity_wt`` water, the produced water rate is
    ``water_rate``; target = purity*m / (water_rate + m)  ->  m = target*water_rate / (purity - target).
    Inhibitor losses to the hydrocarbon and gas phases are not included (state this wherever the result is used).

    Args:
        water_rate: produced/condensed water rate (any mass-rate unit; the result has the same unit).
        target_wt: required inhibitor mass fraction in the aqueous phase (0-1).
        purity_wt: inhibitor mass fraction of the lean stream (0-1), must exceed ``target_wt``.
    """
    if not 0.0 < purity_wt <= 1.0:
        raise FlowAssuranceError("lean inhibitor purity must be in (0, 1]")
    if not 0.0 <= target_wt < purity_wt:
        raise FlowAssuranceError("target mass fraction %.3f must be below the lean purity %.3f" % (target_wt, purity_wt))
    return target_wt * water_rate / (purity_wt - target_wt)


def aqueous_wt_after_injection(water_rate, lean_rate, purity_wt):
    """Inhibitor mass fraction of the aqueous phase after injecting ``lean_rate`` of a ``purity_wt`` lean stream."""
    inhibitor = purity_wt * lean_rate
    water = water_rate + (1.0 - purity_wt) * lean_rate
    return inhibitor / (inhibitor + water) if (inhibitor + water) > 0 else 0.0


def hammerschmidt_depression_k(wt_frac, inhibitor="MEG"):
    """Hammerschmidt screening estimate of the hydrate-temperature depression (K); an empirical rule, never a validation.

    dT = K * W / (M * (100 - W)) with W in wt% and K = 2335 (SI, K in kelvin). It overpredicts at high concentration.
    """
    w = 100.0 * wt_frac
    if w >= 100.0:
        raise FlowAssuranceError("wt% must be below 100")
    return 2335.0 * w / (MW[inhibitor] * (100.0 - w))
