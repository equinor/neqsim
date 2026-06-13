"""Recipe gallery / cookbook for NeqSim Studio (Proposal #5).

A curated set of ready-to-run flowsheet definitions.  Each entry is a complete,
self-contained JSON definition (fluid + process), so a newcomer can:

* browse them with :func:`print_gallery`,
* build one instantly with ``studio.build_recipe(name)``,
* read the JSON as a worked example of the schema, and
* (internally) ground the natural-language builder's LLM prompt.

The catalog is assembled from the same pure-Python spec builders used by the
template recipes, guaranteeing the JSON stays valid as the schema evolves.
"""

from __future__ import annotations

from typing import Dict, List

from . import fluids as _fluids
from . import templates as _templates


def _with_fluid(spec, preset: str, temperature_c: float, pressure_bara: float) -> dict:
    """Attach a preset fluid to a spec and render a self-contained dict.

    Parameters
    ----------
    spec:
        A :class:`~neqsim_studio.jsonspec.FlowsheetSpec`.
    preset:
        Preset fluid key from :data:`neqsim_studio.fluids.PRESET_FLUIDS`.
    temperature_c:
        Fluid temperature in Celsius.
    pressure_bara:
        Fluid pressure in bara.

    Returns
    -------
    dict
        The complete flowsheet definition including the fluid section.
    """
    spec.fluid(_fluids.PRESET_FLUIDS[preset], temperature_c=temperature_c,
               pressure_bara=pressure_bara)
    return spec.to_dict(auto_run=True, include_fluid=True)


def _build_catalog() -> Dict[str, dict]:
    """Assemble the recipe catalog.

    Returns
    -------
    dict
        Mapping of recipe key to ``{title, description, tags, json}``.
    """
    catalog: Dict[str, dict] = {}

    catalog["two_stage_export_compression"] = {
        "title": "Two-stage export gas compression",
        "description": "Boost natural gas from 20 to 120 bara in two stages with "
                       "intercooling to 30 C — the classic export compression train.",
        "tags": ["compression", "export", "gas"],
        "json": _with_fluid(
            _templates.gas_compression_spec(
                stages=2, suction_pressure_bara=20.0, discharge_pressure_bara=120.0,
                interstage_temperature_c=30.0, feed_temperature_c=40.0,
                flow=5.0, flow_unit="MSm3/day"),
            "natural_gas", 40.0, 20.0),
    }

    catalog["three_stage_separation"] = {
        "title": "Three-stage oil/gas separation",
        "description": "Stabilise a rich gas/condensate through HP/MP/LP three-phase "
                       "separators at 70 / 20 / 3 bara.",
        "tags": ["separation", "stabilisation", "oil"],
        "json": _with_fluid(
            _templates.three_stage_separation_spec(
                hp_pressure_bara=70.0, mp_pressure_bara=20.0, lp_pressure_bara=3.0,
                feed_temperature_c=60.0, flow=1000.0, flow_unit="kg/hr"),
            "rich_gas", 60.0, 70.0),
    }

    catalog["water_knockout"] = {
        "title": "Inlet cooling + water knockout",
        "description": "Cool wet gas to 25 C and drop free water in a three-phase "
                       "knockout — a dehydration pre-treatment topology.",
        "tags": ["dehydration", "water", "pretreatment"],
        "json": _with_fluid(
            _templates.dehydration_spec(
                feed_temperature_c=40.0, contactor_temperature_c=25.0,
                feed_pressure_bara=70.0, flow=1000.0, flow_unit="kg/hr"),
            "wet_gas", 40.0, 70.0),
    }

    catalog["co2_conditioning"] = {
        "title": "CO2-rich gas conditioning",
        "description": "Cool a CO2-rich acid gas, knock out condensate, and compress "
                       "to 120 bara for export/injection.",
        "tags": ["co2", "capture", "compression"],
        "json": _with_fluid(
            _templates.co2_capture_spec(
                feed_temperature_c=50.0, cooled_temperature_c=30.0,
                feed_pressure_bara=40.0, export_pressure_bara=120.0,
                flow=1000.0, flow_unit="kg/hr"),
            "rich_gas", 50.0, 40.0),
    }

    return catalog


RECIPES: Dict[str, dict] = _build_catalog()


def list_recipes() -> Dict[str, str]:
    """List the gallery recipes.

    Returns
    -------
    dict
        Mapping of recipe key to title.
    """
    return {key: value["title"] for key, value in RECIPES.items()}


def get_recipe(name: str) -> dict:
    """Return a single gallery recipe entry.

    Parameters
    ----------
    name:
        Recipe key.

    Returns
    -------
    dict
        The recipe entry (``title``, ``description``, ``tags``, ``json``).

    Raises
    ------
    ValueError
        If ``name`` is not a known recipe.
    """
    key = name.strip().lower()
    if key not in RECIPES:
        raise ValueError(
            "Unknown recipe '%s'. Available: %s"
            % (name, ", ".join(sorted(RECIPES)))
        )
    return RECIPES[key]


def find_recipes(tag: str) -> List[str]:
    """Find recipe keys carrying a given tag.

    Parameters
    ----------
    tag:
        Tag to filter on (case-insensitive).

    Returns
    -------
    list of str
        Matching recipe keys.
    """
    low = tag.strip().lower()
    return [key for key, value in RECIPES.items()
            if low in [t.lower() for t in value.get("tags", [])]]


def print_gallery() -> None:
    """Print the gallery as a readable cookbook listing.

    Returns
    -------
    None
    """
    print("NeqSim Studio — recipe gallery")
    print("=" * 64)
    for key, value in RECIPES.items():
        units = [u.get("name") for u in value["json"].get("process", [])]
        print("\n%s" % value["title"])
        print("  key:   %s" % key)
        print("  tags:  %s" % ", ".join(value.get("tags", [])))
        print("  units: %s" % " -> ".join(units))
        print("  %s" % value["description"])
    print("\nBuild one with:  studio.build_recipe(\"<key>\")")
