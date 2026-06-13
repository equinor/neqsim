"""Guided wizard for NeqSim Studio (Proposal #3).

The wizard asks a short, fixed set of questions and routes the answers to the
right template recipe.  It runs in two modes:

* **Programmatic** — pass an ``answers`` dict; nothing is printed and no input
  is requested.  This is what the tests and notebooks use.
* **Interactive** — set ``interactive=True`` to prompt via ``input()``.

The routing logic (:func:`plan_from_answers`) is pure Python and testable.
"""

from __future__ import annotations

from typing import Dict, List, Optional

# Objective keyword -> template recipe + parameter mapping.
_OBJECTIVES = {
    "compress": "gas_compression",
    "compression": "gas_compression",
    "boost": "gas_compression",
    "separate": "three_stage_separation",
    "separation": "three_stage_separation",
    "stabilise": "three_stage_separation",
    "stabilize": "three_stage_separation",
    "dehydrate": "dehydration",
    "dehydration": "dehydration",
    "dry": "dehydration",
    "co2": "co2_capture",
    "capture": "co2_capture",
    "sweeten": "co2_capture",
}

# Questions presented in interactive mode (key, prompt, default).
QUESTIONS = [
    ("objective", "What do you want to do? (compress / separate / dehydrate / co2)", "compress"),
    ("fluid", "Feed fluid preset (natural_gas / rich_gas / lean_gas / wet_gas / co2_stream)",
     "natural_gas"),
    ("feed_temperature_c", "Feed temperature [C]", "40"),
    ("feed_pressure_bara", "Feed pressure [bara]", "60"),
    ("target_pressure_bara", "Target/discharge pressure [bara] (compression only)", "120"),
]


def plan_from_answers(answers: Dict[str, object]) -> Dict[str, object]:
    """Map wizard answers to a recipe name and parameter dict.

    Parameters
    ----------
    answers:
        Mapping with at least an ``objective`` key; other keys are optional and
        map to recipe parameters.

    Returns
    -------
    dict
        ``{"recipe": str, "fluid_preset": str, "params": dict}``.

    Raises
    ------
    ValueError
        If the objective cannot be matched to a recipe.
    """
    objective = str(answers.get("objective", "")).strip().lower()
    recipe = None
    for keyword, name in _OBJECTIVES.items():
        if keyword in objective:
            recipe = name
            break
    if recipe is None:
        raise ValueError(
            "Could not map objective '%s' to a recipe. Choose one of: "
            "compress, separate, dehydrate, co2." % objective
        )

    fluid_preset = str(answers.get("fluid", "")).strip().lower() or None

    params: Dict[str, object] = {}
    if "feed_temperature_c" in answers and answers["feed_temperature_c"] not in (None, ""):
        params["feed_temperature_c"] = float(answers["feed_temperature_c"])
    if "feed_pressure_bara" in answers and answers["feed_pressure_bara"] not in (None, ""):
        fp = float(answers["feed_pressure_bara"])
        if recipe == "gas_compression":
            params["suction_pressure_bara"] = fp
        elif recipe == "three_stage_separation":
            params["hp_pressure_bara"] = fp
        else:
            params["feed_pressure_bara"] = fp
    if "target_pressure_bara" in answers and answers["target_pressure_bara"] not in (None, ""):
        tp = float(answers["target_pressure_bara"])
        if recipe == "gas_compression":
            params["discharge_pressure_bara"] = tp
        elif recipe == "co2_capture":
            params["export_pressure_bara"] = tp

    return {"recipe": recipe, "fluid_preset": fluid_preset, "params": params}


def _collect_interactive() -> Dict[str, object]:
    """Prompt the user for answers via ``input()``.

    Returns
    -------
    dict
        Collected answers keyed by question name.
    """
    print("NeqSim Studio — guided flowsheet builder")
    print("(press Enter to accept the [default])\n")
    answers: Dict[str, object] = {}
    for key, prompt, default in QUESTIONS:
        raw = input("%s [%s]: " % (prompt, default)).strip()
        answers[key] = raw if raw else default
    return answers


def run_wizard(studio, answers: Optional[Dict[str, object]] = None,
               interactive: bool = False):
    """Run the guided builder and return the resulting flowsheet.

    Parameters
    ----------
    studio:
        The :class:`~neqsim_studio.core.Studio` instance.
    answers:
        Pre-filled answers for non-interactive use.
    interactive:
        When True (and ``answers`` is None), prompt via ``input()``.

    Returns
    -------
    FlowsheetResult
        The built flowsheet.

    Raises
    ------
    ValueError
        If no answers are provided in non-interactive mode.
    """
    if answers is None:
        if interactive:
            answers = _collect_interactive()
        else:
            raise ValueError(
                "Provide answers={...} or set interactive=True to be prompted."
            )

    plan = plan_from_answers(answers)
    fluid = None
    if plan["fluid_preset"]:
        fluid = studio.preset_fluid(
            plan["fluid_preset"],
            temperature_c=float(answers.get("feed_temperature_c", 15.0) or 15.0),
            pressure_bara=float(answers.get("feed_pressure_bara", 1.01325) or 1.01325),
        )
    return studio.from_template(plan["recipe"], fluid=fluid, **plan["params"])
