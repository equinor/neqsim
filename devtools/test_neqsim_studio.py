"""Pure-Python tests for the NeqSim Studio package.

These tests cover the JVM-free building blocks — the JSON spec builder, the
template spec functions, the natural-language parser, the edit-command parser,
the wizard router, and the gallery catalog.  They run without starting Java, in
the same spirit as ``test_unisim_outputs.py``.

Run with::

    python -m pytest devtools/test_neqsim_studio.py
    # or, without pytest:
    python devtools/test_neqsim_studio.py
"""

from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from neqsim_studio.edit import parse_edit_command  # noqa: E402
from neqsim_studio.gallery import (  # noqa: E402
    RECIPES,
    find_recipes,
    get_recipe,
    list_recipes,
)
from neqsim_studio.jsonspec import FlowsheetSpec, quantity  # noqa: E402
from neqsim_studio.templates import (  # noqa: E402
    co2_capture_spec,
    gas_compression_spec,
    list_templates,
    three_stage_separation_spec,
)
from neqsim_studio.text import text_to_json, text_to_spec  # noqa: E402
from neqsim_studio.wizard import plan_from_answers  # noqa: E402


# ── jsonspec ──
def test_quantity_pair():
    assert quantity(30.0, "C") == [30.0, "C"]


def test_flowsheet_spec_to_dict_structure():
    spec = (FlowsheetSpec("demo")
            .fluid({"methane": 0.9, "ethane": 0.1}, temperature_c=20.0, pressure_bara=50.0)
            .stream("feed", flow=100.0, flow_unit="kg/hr", temperature_c=40.0, pressure_bara=60.0)
            .cooler("Cooler 1", outlet_temperature_c=25.0)
            .compressor("Comp 1", outlet_pressure_bara=120.0))
    definition = spec.to_dict()
    assert definition["autoRun"] is True
    assert definition["fluid"]["model"] == "SRK"
    # Kelvin conversion.
    assert abs(definition["fluid"]["temperature"] - (20.0 + 273.15)) < 1e-9
    types = [u["type"] for u in definition["process"]]
    assert types == ["Stream", "Cooler", "Compressor"]
    # default inlet chaining
    assert definition["process"][1]["inlet"] == "feed"
    assert definition["process"][2]["inlet"] == "Cooler 1"
    # property pair form
    assert definition["process"][1]["properties"]["outletTemperature"] == [25.0, "C"]


def test_flowsheet_spec_requires_units():
    spec = FlowsheetSpec("empty")
    try:
        spec.to_dict()
    except ValueError:
        return
    raise AssertionError("expected ValueError for empty flowsheet")


def test_flowsheet_spec_include_fluid_false_skips_fluid():
    spec = FlowsheetSpec("x").stream("feed", flow=1.0)
    definition = spec.to_dict(include_fluid=False)
    assert "fluid" not in definition


def test_mixer_uses_inlets_list():
    spec = (FlowsheetSpec("m")
            .stream("a", flow=1.0)
            .stream("b", flow=1.0)
            .mixer("Mix", inlets=["a", "b"]))
    mixer = spec.to_dict(include_fluid=False)["process"][-1]
    assert mixer["inlets"] == ["a", "b"]


# ── templates ──
def test_list_templates_has_core_recipes():
    names = list_templates()
    for expected in ("gas_compression", "three_stage_separation", "dehydration", "co2_capture"):
        assert expected in names


def test_gas_compression_spec_stage_count_and_pressures():
    spec = gas_compression_spec(stages=3, suction_pressure_bara=10.0,
                                discharge_pressure_bara=80.0)
    definition = spec.to_dict(include_fluid=False)
    types = [u["type"] for u in definition["process"]]
    # feed + 3 compressors + 2 intercoolers
    assert types.count("Compressor") == 3
    assert types.count("Cooler") == 2
    last_comp = [u for u in definition["process"] if u["type"] == "Compressor"][-1]
    assert last_comp["properties"]["outletPressure"] == [80.0, "bara"]


def test_gas_compression_spec_validates_pressure():
    try:
        gas_compression_spec(suction_pressure_bara=100.0, discharge_pressure_bara=50.0)
    except ValueError:
        return
    raise AssertionError("expected ValueError for discharge < suction")


def test_three_stage_separation_uses_oil_refs():
    spec = three_stage_separation_spec()
    definition = spec.to_dict(include_fluid=False)
    valves = [u for u in definition["process"] if u["type"] == "ThrottlingValve"]
    assert valves[0]["inlet"] == "HP Separator.oilOut"
    assert valves[1]["inlet"] == "MP Separator.oilOut"


def test_three_stage_separation_validates_pressure_order():
    try:
        three_stage_separation_spec(hp_pressure_bara=10.0, mp_pressure_bara=20.0,
                                    lp_pressure_bara=3.0)
    except ValueError:
        return
    raise AssertionError("expected ValueError for non-decreasing pressures")


def test_co2_capture_spec_compressor_on_gas():
    definition = co2_capture_spec().to_dict(include_fluid=False)
    comp = [u for u in definition["process"] if u["type"] == "Compressor"][0]
    assert comp["inlet"] == "Knockout Drum.gasOut"


# ── text parser ──
def test_text_to_spec_linear_chain():
    spec = text_to_spec("Take natural gas, cool it to 30 C, then compress to 120 bara")
    definition = spec.to_dict()
    types = [u["type"] for u in definition["process"]]
    assert "Cooler" in types and "Compressor" in types
    # order preserved: cooler before compressor
    assert types.index("Cooler") < types.index("Compressor")


def test_text_to_json_detects_feed_conditions():
    definition = text_to_json("Compress 5 MSm3/day of lean gas at 25 C and 40 bara to 150 bara")
    feed = definition["process"][0]
    assert feed["type"] == "Stream"
    assert feed["properties"]["pressure"] == [40.0, "bara"]
    assert feed["properties"]["temperature"] == [25.0, "C"]


def test_text_to_spec_separator_and_valve():
    spec = text_to_spec("Feed gas to a separator then expand to 20 bara")
    types = [u["type"] for u in spec.to_dict().get("process", [])]
    assert "Separator" in types
    assert "ThrottlingValve" in types


def test_text_to_spec_raises_when_no_operations():
    try:
        text_to_spec("hello there, nothing to do here")
    except ValueError:
        return
    raise AssertionError("expected ValueError when no operations found")


# ── edit command parser ──
def test_parse_edit_command_basic():
    edit = parse_edit_command("set Stage 1 Compressor outlet pressure to 130 bara")
    assert edit["unit"] == "Stage 1 Compressor"
    assert edit["property"] == "outletPressure"
    assert edit["value"] == 130.0
    assert edit["uom"] == "bara"


def test_parse_edit_command_temperature_alias():
    edit = parse_edit_command("change Cooler 1 outlet temperature to 22 C")
    assert edit["property"] == "outletTemperature"
    assert edit["value"] == 22.0
    assert edit["uom"] == "C"


def test_parse_edit_command_known_units_match():
    edit = parse_edit_command("make the export compressor discharge pressure 200 bara",
                              known_units=["Export Compressor", "Cooler 1"])
    assert edit["unit"] == "Export Compressor"
    assert edit["property"] == "outletPressure"
    assert edit["value"] == 200.0


def test_parse_edit_command_returns_none_without_number():
    assert parse_edit_command("set Cooler 1 outlet temperature to cold") is None


def test_parse_edit_command_returns_none_without_property():
    assert parse_edit_command("please do something useful") is None


# ── wizard router ──
def test_plan_from_answers_compression():
    plan = plan_from_answers({
        "objective": "compress the gas",
        "fluid": "natural_gas",
        "feed_pressure_bara": "20",
        "target_pressure_bara": "120",
    })
    assert plan["recipe"] == "gas_compression"
    assert plan["fluid_preset"] == "natural_gas"
    assert plan["params"]["suction_pressure_bara"] == 20.0
    assert plan["params"]["discharge_pressure_bara"] == 120.0


def test_plan_from_answers_separation():
    plan = plan_from_answers({"objective": "separate oil and gas",
                              "feed_pressure_bara": "70"})
    assert plan["recipe"] == "three_stage_separation"
    assert plan["params"]["hp_pressure_bara"] == 70.0


def test_plan_from_answers_unknown_objective():
    try:
        plan_from_answers({"objective": "teleport the gas"})
    except ValueError:
        return
    raise AssertionError("expected ValueError for unknown objective")


# ── gallery ──
def test_gallery_recipes_are_self_contained():
    assert RECIPES
    for key, recipe in RECIPES.items():
        assert "json" in recipe
        definition = recipe["json"]
        assert "fluid" in definition
        assert definition["process"]
        # every non-stream unit references an inlet
        for unit in definition["process"]:
            if unit["type"] != "Stream":
                assert "inlet" in unit or "inlets" in unit, (key, unit["name"])


def test_list_recipes_and_get_recipe():
    titles = list_recipes()
    assert titles
    first = next(iter(titles))
    recipe = get_recipe(first)
    assert recipe["title"] == titles[first]


def test_get_recipe_unknown_raises():
    try:
        get_recipe("does-not-exist")
    except ValueError:
        return
    raise AssertionError("expected ValueError for unknown recipe")


def test_find_recipes_by_tag():
    keys = find_recipes("compression")
    assert keys
    for key in keys:
        assert "compression" in [t.lower() for t in RECIPES[key]["tags"]]


# ── simple runner when pytest is unavailable ──
def _run_all():
    """Execute every ``test_*`` function and report pass/fail.

    Returns
    -------
    int
        Process exit code (0 on success, 1 on any failure).
    """
    funcs = [(name, obj) for name, obj in sorted(globals().items())
             if name.startswith("test_") and callable(obj)]
    failures = []
    for name, func in funcs:
        try:
            func()
            print("PASS %s" % name)
        except Exception as exc:  # noqa: BLE001
            failures.append((name, exc))
            print("FAIL %s -> %s" % (name, exc))
    print("\n%d passed, %d failed" % (len(funcs) - len(failures), len(failures)))
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(_run_all())
