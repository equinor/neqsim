"""Natural-language flowsheet builder for NeqSim Studio (Proposal #1).

Two paths are supported:

* **LLM path** — pass a callable ``llm(prompt) -> json_text``.  The prompt is
  grounded with the JSON schema and a couple of gallery examples so a capable
  model returns a valid flowsheet definition.
* **Rule-based path (default)** — a deterministic parser that recognises a
  vocabulary of equipment phrases ("cool to", "compress to", "separator",
  "valve to", "heat to") and chains them in the order mentioned.  This works
  fully offline and is unit testable without a JVM.

The rule-based parser purposely targets the *teaching* sweet spot: linear
"feed → unit → unit → ..." descriptions, which is exactly what newcomers write.
"""

from __future__ import annotations

import json as _json
import re
from typing import Callable, Dict, List, Optional

from .jsonspec import FlowsheetSpec

# ── feed parsing ──
_FLOW_RE = re.compile(
    r"(\d+(?:\.\d+)?)\s*(msm3/day|mmscfd|sm3/day|kg/hr|kg/h|kg/s|kmol/hr|mol/s)",
    re.IGNORECASE,
)
_TEMP_RE = re.compile(r"(-?\d+(?:\.\d+)?)\s*(?:deg\s*)?(?:c|celsius|°c)\b", re.IGNORECASE)
_PRES_RE = re.compile(r"(\d+(?:\.\d+)?)\s*(bara|barg|bar)\b", re.IGNORECASE)

_FLOW_UNIT_CANON = {
    "kg/h": "kg/hr",
}

# Component keyword -> NeqSim component name.
_COMPONENT_HINTS = {
    "natural gas": "natural_gas",
    "rich gas": "rich_gas",
    "lean gas": "lean_gas",
    "wet gas": "wet_gas",
    "co2": "co2_stream",
    "acid gas": "rich_gas",
}


def _canon_flow_unit(unit: str) -> str:
    """Normalise a flow unit token to a NeqSim-recognised string.

    Parameters
    ----------
    unit:
        Raw unit token from the text.

    Returns
    -------
    str
        Canonical unit string.
    """
    low = unit.lower()
    return _FLOW_UNIT_CANON.get(low, low.replace("mmscfd", "MSm3/day"))


def _detect_feed(text: str) -> Dict[str, object]:
    """Extract feed flow, temperature, pressure, and fluid preset from text.

    Parameters
    ----------
    text:
        The process description.

    Returns
    -------
    dict
        ``{"flow", "flow_unit", "temperature_c", "pressure_bara", "preset"}``
        with sensible defaults for anything not stated.
    """
    flow = 1000.0
    flow_unit = "kg/hr"
    fmatch = _FLOW_RE.search(text)
    if fmatch:
        flow = float(fmatch.group(1))
        flow_unit = _canon_flow_unit(fmatch.group(2))

    temperature_c = 40.0
    tmatch = _TEMP_RE.search(text)
    if tmatch:
        temperature_c = float(tmatch.group(1))

    pressure_bara = 60.0
    pmatch = _PRES_RE.search(text)
    if pmatch:
        pressure_bara = float(pmatch.group(1))

    preset = "natural_gas"
    low = text.lower()
    for hint, name in _COMPONENT_HINTS.items():
        if hint in low:
            preset = name
            break

    return {
        "flow": flow,
        "flow_unit": flow_unit,
        "temperature_c": temperature_c,
        "pressure_bara": pressure_bara,
        "preset": preset,
    }


# ── operation parsing ──
# Each rule: (regex, handler) where handler(spec, match, counters) mutates spec.
def _make_rules():
    """Build the ordered list of (regex, handler) operation rules.

    Returns
    -------
    list
        Tuples of compiled regex and handler callables.
    """
    def cool(spec, m, c):
        c["cool"] += 1
        spec.cooler("Cooler %d" % c["cool"], outlet_temperature_c=float(m.group(1)))

    def heat(spec, m, c):
        c["heat"] += 1
        spec.heater("Heater %d" % c["heat"], outlet_temperature_c=float(m.group(1)))

    def compress(spec, m, c):
        c["comp"] += 1
        spec.compressor("Compressor %d" % c["comp"], outlet_pressure_bara=float(m.group(1)))

    def pump(spec, m, c):
        c["pump"] += 1
        spec.pump("Pump %d" % c["pump"], outlet_pressure_bara=float(m.group(1)))

    def expand(spec, m, c):
        c["valve"] += 1
        spec.valve("Valve %d" % c["valve"], outlet_pressure_bara=float(m.group(1)))

    def separate(spec, m, c):
        c["sep"] += 1
        spec.separator("Separator %d" % c["sep"])

    return [
        (re.compile(r"\b(?:cool|chill)(?:s|ed|ing)?\b[^.;]*?\bto\s+(-?\d+(?:\.\d+)?)\s*(?:deg\s*)?c\b",
                    re.IGNORECASE), cool),
        (re.compile(r"\bheat(?:s|ed|ing)?\b[^.;]*?\bto\s+(-?\d+(?:\.\d+)?)\s*(?:deg\s*)?c\b",
                    re.IGNORECASE), heat),
        (re.compile(r"\bcompress(?:es|ed|ing)?\b[^.;]*?\bto\s+(\d+(?:\.\d+)?)\s*bar",
                    re.IGNORECASE), compress),
        (re.compile(r"\bpump(?:s|ed|ing)?\b[^.;]*?\bto\s+(\d+(?:\.\d+)?)\s*bar",
                    re.IGNORECASE), pump),
        (re.compile(r"\b(?:expand|throttle|let\s*down|reduce\s+pressure)(?:s|ed|ing)?\b[^.;]*?\bto\s+(\d+(?:\.\d+)?)\s*bar",
                    re.IGNORECASE), expand),
        (re.compile(r"\b(?:separat\w*|knock\s*out|scrub\w*|flash\s+drum|separator)\b",
                    re.IGNORECASE), separate),
    ]


_RULES = _make_rules()


def text_to_spec(text: str) -> FlowsheetSpec:
    """Convert a natural-language description into a :class:`FlowsheetSpec`.

    The operations are applied in the order they appear in the text.

    Parameters
    ----------
    text:
        The process description.

    Returns
    -------
    FlowsheetSpec
        The assembled spec (fluid set from the detected preset).

    Raises
    ------
    ValueError
        If no recognisable operations are found.
    """
    from . import fluids as _fluids

    feed = _detect_feed(text)
    spec = FlowsheetSpec("from_text")
    preset_components = _fluids.PRESET_FLUIDS[feed["preset"]]
    spec.fluid(preset_components, temperature_c=feed["temperature_c"],
               pressure_bara=feed["pressure_bara"])
    spec.stream("feed", flow=feed["flow"], flow_unit=feed["flow_unit"],
                temperature_c=feed["temperature_c"], pressure_bara=feed["pressure_bara"])

    # Collect (position, handler, match) for every rule hit, then apply in order.
    hits = []
    for regex, handler in _RULES:
        for match in regex.finditer(text):
            hits.append((match.start(), handler, match))
    hits.sort(key=lambda item: item[0])

    if not hits:
        raise ValueError(
            "No recognisable process operations found in the description. "
            "Try phrases like 'cool to 30 C', 'compress to 120 bara', "
            "'separator', 'expand to 20 bara'."
        )

    counters = {"cool": 0, "heat": 0, "comp": 0, "pump": 0, "valve": 0, "sep": 0}
    for _pos, handler, match in hits:
        handler(spec, match, counters)

    return spec


def text_to_json(text: str, include_fluid: bool = True) -> dict:
    """Convert a description directly to a JSON-ready dict.

    Parameters
    ----------
    text:
        The process description.
    include_fluid:
        Whether to embed the fluid section (set False when a pre-built fluid
        will be supplied at build time).

    Returns
    -------
    dict
        The flowsheet definition.
    """
    return text_to_spec(text).to_dict(auto_run=True, include_fluid=include_fluid)


# ── LLM grounding ──
def build_prompt(text: str) -> str:
    """Build a grounding prompt for an LLM that emits flowsheet JSON.

    Parameters
    ----------
    text:
        The user's process description.

    Returns
    -------
    str
        A prompt containing the schema, examples, and the request.
    """
    from . import gallery

    examples = []
    for key in list(gallery.RECIPES)[:2]:
        recipe = gallery.RECIPES[key]
        examples.append("# %s\n%s" % (recipe["title"], _json.dumps(recipe["json"], indent=2)))
    examples_text = "\n\n".join(examples)
    return (
        "You convert process descriptions into NeqSim JSON flowsheet definitions.\n"
        "Return ONLY a JSON object with keys: fluid, process, autoRun.\n"
        "Each process entry has: type, name, optional inlet (stream reference),\n"
        "and optional properties where values are [number, \"unit\"] pairs.\n"
        "Valid types: Stream, Cooler, Heater, Compressor, Pump, ThrottlingValve,\n"
        "Separator, ThreePhaseSeparator, Mixer, Splitter, Recycle.\n"
        "Stream references: 'unitName', 'unitName.gasOut', 'unitName.liquidOut',\n"
        "'unitName.oilOut', 'unitName.waterOut'.\n\n"
        "Examples:\n" + examples_text + "\n\n"
        "Now convert this description:\n" + text + "\n"
    )


def build_from_text(studio, text: str, llm: Optional[Callable[[str], str]] = None,
                    fluid=None):
    """Build and run a flowsheet from a natural-language description.

    Parameters
    ----------
    studio:
        The :class:`~neqsim_studio.core.Studio` instance.
    text:
        The process description.
    llm:
        Optional callable ``str -> json_text``.  When omitted the deterministic
        rule-based parser is used.
    fluid:
        Optional pre-built feed fluid (overrides any fluid section).

    Returns
    -------
    FlowsheetResult
        The built flowsheet.
    """
    from .build import build_from_json

    if llm is not None:
        prompt = build_prompt(text)
        json_text = llm(prompt)
        definition = _json.loads(json_text) if isinstance(json_text, str) else json_text
        if fluid is not None and isinstance(definition, dict):
            definition.pop("fluid", None)
        return build_from_json(studio.context, definition, fluid=fluid, source="text:llm")

    # Rule-based fallback.
    definition = text_to_json(text, include_fluid=fluid is None)
    return build_from_json(studio.context, definition, fluid=fluid, source="text:rules")
