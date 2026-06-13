"""Template recipes for NeqSim Studio (Proposal #2).

Each recipe turns a handful of friendly keyword arguments into a validated
flowsheet.  Recipes build a :class:`~neqsim_studio.jsonspec.FlowsheetSpec`
(pure Python, testable) and then hand it to the studio's JSON builder.

A recipe never requires the caller to know Java class names — only the
engineering parameters that matter (pressures, temperatures, stage counts).

The pure-python spec functions (``*_spec``) are separated from the studio-bound
wrappers so the generated JSON can be unit tested without a JVM.
"""

from __future__ import annotations

from typing import Callable, Dict, List, Optional

from . import fluids as _fluids
from .jsonspec import FlowsheetSpec, quantity

# ── registry ──
_TEMPLATES: Dict[str, str] = {
    "gas_compression": "Multi-stage gas compression train with intercooling",
    "three_stage_separation": "Three-stage oil/gas separation train",
    "dehydration": "Gas dehydration knockout + cooling (TEG-style pre-treat)",
    "co2_capture": "CO2 removal pre-treatment (cool, knock out, compress)",
}


def list_templates() -> Dict[str, str]:
    """List the available template recipes.

    Returns
    -------
    dict
        Mapping of recipe name to one-line description.
    """
    return dict(_TEMPLATES)


# ── pure-python spec builders ──
def gas_compression_spec(stages: int = 2, suction_pressure_bara: float = 20.0,
                         discharge_pressure_bara: float = 120.0,
                         interstage_temperature_c: float = 30.0,
                         feed_temperature_c: float = 40.0,
                         flow: float = 5.0, flow_unit: str = "MSm3/day",
                         polytropic_efficiency: float = 0.78) -> FlowsheetSpec:
    """Build the JSON spec for a multi-stage compression train.

    Parameters
    ----------
    stages:
        Number of compression stages (>= 1).
    suction_pressure_bara:
        First-stage suction pressure in bara.
    discharge_pressure_bara:
        Final discharge pressure in bara.
    interstage_temperature_c:
        Intercooler outlet temperature in Celsius.
    feed_temperature_c:
        Feed temperature in Celsius.
    flow:
        Feed flow magnitude.
    flow_unit:
        Feed flow unit.
    polytropic_efficiency:
        Polytropic efficiency fraction for each stage.

    Returns
    -------
    FlowsheetSpec
        The assembled (fluid-less) flowsheet spec.

    Raises
    ------
    ValueError
        If ``stages`` < 1 or pressures are inconsistent.
    """
    if stages < 1:
        raise ValueError("stages must be >= 1")
    if discharge_pressure_bara <= suction_pressure_bara:
        raise ValueError("discharge pressure must exceed suction pressure")

    ratio = (discharge_pressure_bara / suction_pressure_bara) ** (1.0 / stages)
    spec = FlowsheetSpec("gas_compression")
    spec.stream("feed", flow=flow, flow_unit=flow_unit,
                temperature_c=feed_temperature_c, pressure_bara=suction_pressure_bara)
    pressure = suction_pressure_bara
    for stage in range(1, stages + 1):
        pressure = (pressure * ratio if stage < stages else discharge_pressure_bara)
        spec.compressor("Stage %d Compressor" % stage, outlet_pressure_bara=pressure,
                        polytropic_efficiency=polytropic_efficiency)
        if stage < stages:
            spec.cooler("Stage %d Intercooler" % stage,
                        outlet_temperature_c=interstage_temperature_c)
    return spec


def three_stage_separation_spec(hp_pressure_bara: float = 70.0,
                                mp_pressure_bara: float = 20.0,
                                lp_pressure_bara: float = 3.0,
                                feed_temperature_c: float = 60.0,
                                flow: float = 1000.0,
                                flow_unit: str = "kg/hr") -> FlowsheetSpec:
    """Build the JSON spec for a three-stage separation train.

    Parameters
    ----------
    hp_pressure_bara:
        High-pressure separator pressure in bara.
    mp_pressure_bara:
        Medium-pressure separator pressure in bara.
    lp_pressure_bara:
        Low-pressure separator pressure in bara.
    feed_temperature_c:
        Feed temperature in Celsius.
    flow:
        Feed flow magnitude.
    flow_unit:
        Feed flow unit.

    Returns
    -------
    FlowsheetSpec
        The assembled (fluid-less) flowsheet spec.

    Raises
    ------
    ValueError
        If the stage pressures are not strictly decreasing.
    """
    if not (hp_pressure_bara > mp_pressure_bara > lp_pressure_bara):
        raise ValueError("require hp > mp > lp pressures")

    spec = FlowsheetSpec("three_stage_separation")
    spec.stream("feed", flow=flow, flow_unit=flow_unit,
                temperature_c=feed_temperature_c, pressure_bara=hp_pressure_bara)
    spec.separator("HP Separator", inlet="feed", kind="ThreePhaseSeparator")
    spec.valve("HP to MP Valve", outlet_pressure_bara=mp_pressure_bara,
               inlet="HP Separator.oilOut")
    spec.separator("MP Separator", inlet="HP to MP Valve", kind="ThreePhaseSeparator")
    spec.valve("MP to LP Valve", outlet_pressure_bara=lp_pressure_bara,
               inlet="MP Separator.oilOut")
    spec.separator("LP Separator", inlet="MP to LP Valve", kind="ThreePhaseSeparator")
    return spec


def dehydration_spec(feed_temperature_c: float = 40.0,
                     contactor_temperature_c: float = 25.0,
                     feed_pressure_bara: float = 70.0,
                     flow: float = 1000.0,
                     flow_unit: str = "kg/hr") -> FlowsheetSpec:
    """Build the JSON spec for a gas dehydration pre-treatment.

    This is a simplified knockout + cooling representation that drops free
    water before a glycol contactor — useful for teaching the topology without
    the full absorber column convergence.

    Parameters
    ----------
    feed_temperature_c:
        Wet-gas feed temperature in Celsius.
    contactor_temperature_c:
        Cooled gas temperature into the knockout in Celsius.
    feed_pressure_bara:
        Feed pressure in bara.
    flow:
        Feed flow magnitude.
    flow_unit:
        Feed flow unit.

    Returns
    -------
    FlowsheetSpec
        The assembled (fluid-less) flowsheet spec.
    """
    spec = FlowsheetSpec("dehydration")
    spec.stream("wet gas", flow=flow, flow_unit=flow_unit,
                temperature_c=feed_temperature_c, pressure_bara=feed_pressure_bara)
    spec.cooler("Inlet Cooler", outlet_temperature_c=contactor_temperature_c)
    spec.separator("Water Knockout", kind="ThreePhaseSeparator")
    return spec


def co2_capture_spec(feed_temperature_c: float = 50.0,
                     cooled_temperature_c: float = 30.0,
                     feed_pressure_bara: float = 40.0,
                     export_pressure_bara: float = 120.0,
                     flow: float = 1000.0,
                     flow_unit: str = "kg/hr") -> FlowsheetSpec:
    """Build the JSON spec for a CO2 removal pre-treatment train.

    A simplified cool → knock out → compress arrangement suitable for showing
    the CO2-rich gas conditioning topology.

    Parameters
    ----------
    feed_temperature_c:
        Feed temperature in Celsius.
    cooled_temperature_c:
        Cooled temperature into the knockout in Celsius.
    feed_pressure_bara:
        Feed pressure in bara.
    export_pressure_bara:
        Compressor discharge pressure in bara.
    flow:
        Feed flow magnitude.
    flow_unit:
        Feed flow unit.

    Returns
    -------
    FlowsheetSpec
        The assembled (fluid-less) flowsheet spec.
    """
    spec = FlowsheetSpec("co2_capture")
    spec.stream("acid gas", flow=flow, flow_unit=flow_unit,
                temperature_c=feed_temperature_c, pressure_bara=feed_pressure_bara)
    spec.cooler("Pre-Cooler", outlet_temperature_c=cooled_temperature_c)
    spec.separator("Knockout Drum")
    spec.compressor("Export Compressor", outlet_pressure_bara=export_pressure_bara,
                    inlet="Knockout Drum.gasOut")
    return spec


_SPEC_BUILDERS: Dict[str, Callable[..., FlowsheetSpec]] = {
    "gas_compression": gas_compression_spec,
    "three_stage_separation": three_stage_separation_spec,
    "dehydration": dehydration_spec,
    "co2_capture": co2_capture_spec,
}

# Default preset fluid for each recipe when the caller does not pass one.
_DEFAULT_FLUID: Dict[str, str] = {
    "gas_compression": "natural_gas",
    "three_stage_separation": "rich_gas",
    "dehydration": "wet_gas",
    "co2_capture": "rich_gas",
}


# ── studio-bound wrappers ──
def _build(studio, recipe: str, fluid, params):
    """Build a recipe's spec and hand it to the studio JSON builder.

    Parameters
    ----------
    studio:
        The :class:`~neqsim_studio.core.Studio` instance.
    recipe:
        Recipe name.
    fluid:
        Optional pre-built fluid; a preset is used when ``None``.
    params:
        Recipe keyword arguments.

    Returns
    -------
    FlowsheetResult
        The built flowsheet.
    """
    spec = _SPEC_BUILDERS[recipe](**params)
    if fluid is None:
        fluid = _fluids.make_preset(studio.context.cls, _DEFAULT_FLUID[recipe])
    definition = spec.to_dict(auto_run=True, include_fluid=False)
    from .build import build_from_json

    return build_from_json(studio.context, definition, fluid=fluid,
                           source="template:%s" % recipe)


def gas_compression(studio, fluid=None, **params):
    """Build a gas compression train (see :func:`gas_compression_spec`).

    Parameters
    ----------
    studio:
        The studio instance.
    fluid:
        Optional feed fluid.
    **params:
        Recipe parameters.

    Returns
    -------
    FlowsheetResult
        The built flowsheet.
    """
    return _build(studio, "gas_compression", fluid, params)


def three_stage_separation(studio, fluid=None, **params):
    """Build a three-stage separation train (see :func:`three_stage_separation_spec`).

    Parameters
    ----------
    studio:
        The studio instance.
    fluid:
        Optional feed fluid.
    **params:
        Recipe parameters.

    Returns
    -------
    FlowsheetResult
        The built flowsheet.
    """
    return _build(studio, "three_stage_separation", fluid, params)


def dehydration(studio, fluid=None, **params):
    """Build a dehydration pre-treatment (see :func:`dehydration_spec`).

    Parameters
    ----------
    studio:
        The studio instance.
    fluid:
        Optional feed fluid.
    **params:
        Recipe parameters.

    Returns
    -------
    FlowsheetResult
        The built flowsheet.
    """
    return _build(studio, "dehydration", fluid, params)


def co2_capture(studio, fluid=None, **params):
    """Build a CO2 removal pre-treatment (see :func:`co2_capture_spec`).

    Parameters
    ----------
    studio:
        The studio instance.
    fluid:
        Optional feed fluid.
    **params:
        Recipe parameters.

    Returns
    -------
    FlowsheetResult
        The built flowsheet.
    """
    return _build(studio, "co2_capture", fluid, params)


def from_template(studio, name: str, fluid=None, **params):
    """Build any registered template recipe by name.

    Parameters
    ----------
    studio:
        The studio instance.
    name:
        Recipe name (see :func:`list_templates`).
    fluid:
        Optional feed fluid.
    **params:
        Recipe parameters.

    Returns
    -------
    FlowsheetResult
        The built flowsheet.

    Raises
    ------
    ValueError
        If ``name`` is not a registered recipe.
    """
    key = name.strip().lower()
    if key not in _SPEC_BUILDERS:
        raise ValueError(
            "Unknown template '%s'. Choose from: %s"
            % (name, ", ".join(sorted(_SPEC_BUILDERS)))
        )
    return _build(studio, key, fluid, params)
