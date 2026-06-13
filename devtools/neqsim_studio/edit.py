"""Edit-by-chat helpers for NeqSim Studio (Proposal #4).

Once a flowsheet exists, newcomers want to *tweak* it without learning the Java
object graph.  :class:`FlowsheetEditor` wraps ``ProcessAutomation`` so every
read/write is a string address plus a unit, and :func:`parse_edit_command`
turns plain instructions such as ``"set Stage 1 Compressor outlet pressure to
130 bara"`` into a structured edit that the editor can apply.

The parser is pure Python (no JVM), so it is unit testable on its own.
"""

from __future__ import annotations

import json as _json
import re
from typing import Dict, List, Optional

# Friendly phrase -> canonical property name used in automation addresses.
_PROPERTY_ALIASES = {
    "outlet pressure": "outletPressure",
    "discharge pressure": "outletPressure",
    "pressure": "pressure",
    "outlet temperature": "outletTemperature",
    "discharge temperature": "outletTemperature",
    "temperature": "temperature",
    "flow rate": "flowRate",
    "flowrate": "flowRate",
    "flow": "flowRate",
    "duty": "duty",
    "efficiency": "polytropicEfficiency",
    "polytropic efficiency": "polytropicEfficiency",
}

# Order matters: try longer phrases first so "outlet pressure" beats "pressure".
_PROPERTY_PHRASES = sorted(_PROPERTY_ALIASES, key=len, reverse=True)

_NUMBER_RE = re.compile(r"[-+]?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?")


def parse_edit_command(text: str, known_units: Optional[List[str]] = None) -> Optional[dict]:
    """Parse a natural-language edit instruction into a structured edit.

    Recognises patterns like::

        set <unit> <property> to <value> <unit-of-measure>
        change <unit> <property> to <value>
        <unit> <property> = <value> <uom>

    Parameters
    ----------
    text:
        The instruction text.
    known_units:
        Optional list of unit names to match against (longest match wins).
        When omitted, the unit is taken as the words before the property phrase.

    Returns
    -------
    dict or None
        ``{"unit": str, "property": str, "value": float, "uom": str}`` on a
        successful parse, otherwise ``None``.
    """
    if not text:
        return None
    lowered = text.strip().lower()

    # Find the property phrase.
    prop_phrase = None
    prop_index = -1
    for phrase in _PROPERTY_PHRASES:
        idx = lowered.find(phrase)
        if idx != -1:
            prop_phrase = phrase
            prop_index = idx
            break
    if prop_phrase is None:
        return None

    # Find the numeric value (after the property phrase).
    tail = text[prop_index + len(prop_phrase):]
    number_match = _NUMBER_RE.search(tail)
    if not number_match:
        return None
    value = float(number_match.group(0))

    # Unit of measure is the token right after the number, if any.
    uom = ""
    after_number = tail[number_match.end():].strip()
    if after_number:
        uom_token = after_number.split()[0]
        uom_token = uom_token.strip(".,;:")
        # Filter out filler words.
        if uom_token.lower() not in ("and", "the", "a", "to", "for"):
            uom = uom_token

    # Unit name: text before the property phrase, with leading verbs removed.
    head = text[:prop_index].strip()
    head = re.sub(r"^(set|change|update|make|adjust|put)\s+", "", head,
                  flags=re.IGNORECASE).strip()
    head = re.sub(r"\b(the|of|for|on|'s|its)\b", "", head, flags=re.IGNORECASE)
    head = re.sub(r"\s+", " ", head).strip()

    unit_name = head
    if known_units:
        match = None
        for candidate in sorted(known_units, key=len, reverse=True):
            if candidate.lower() in lowered:
                match = candidate
                break
        if match is not None:
            unit_name = match

    if not unit_name:
        return None

    return {
        "unit": unit_name,
        "property": _PROPERTY_ALIASES[prop_phrase],
        "value": value,
        "uom": uom,
    }


class FlowsheetEditor:
    """String-addressable editor over ``ProcessAutomation``.

    Parameters
    ----------
    context:
        A :class:`~neqsim_studio.core.ProcessBuilderContext`.
    process:
        The Java ``ProcessSystem`` to edit.
    """

    def __init__(self, context, process):
        self.context = context
        self.process = process
        self.auto = process.getAutomation()

    # ── discovery ──
    def units(self) -> List[str]:
        """List unit names.

        Returns
        -------
        list of str
            Unit names from the automation facade.
        """
        return [str(u) for u in self.auto.getUnitList()]

    def describe(self) -> dict:
        """Return the full automation manifest as a dict.

        Returns
        -------
        dict
            Parsed ``ProcessAutomation.describe()`` output.
        """
        return _json.loads(str(self.auto.describe()))

    def variables(self, unit: str) -> List[dict]:
        """List the variables of a unit with type/unit/description.

        Parameters
        ----------
        unit:
            Unit name.

        Returns
        -------
        list of dict
            One dict per variable with keys ``name``, ``type``, ``unit``,
            ``address``, ``description`` when available.
        """
        out: List[dict] = []
        for var in self.auto.getVariableList(unit):
            entry: Dict[str, object] = {}
            for getter, key in (("getName", "name"), ("getType", "type"),
                                ("getUnit", "unit"), ("getAddress", "address"),
                                ("getDescription", "description")):
                try:
                    entry[key] = str(getattr(var, getter)())
                except Exception:
                    pass
            out.append(entry)
        return out

    def topology(self) -> dict:
        """Return the equipment-and-connection topology.

        Returns
        -------
        dict
            Parsed ``ProcessAutomation.getTopology()`` output.
        """
        return _json.loads(str(self.auto.getTopology()))

    def neighbors(self, unit: str) -> dict:
        """Return immediate upstream/downstream neighbours of a unit.

        Parameters
        ----------
        unit:
            Unit name.

        Returns
        -------
        dict
            Parsed ``ProcessAutomation.getNeighbors(unit)`` output.
        """
        return _json.loads(str(self.auto.getNeighbors(unit)))

    def snapshot(self, scope: str = "*") -> dict:
        """Dump variable values for a unit, area, or the whole process.

        Parameters
        ----------
        scope:
            Unit name, area name, or ``"*"`` for everything.

        Returns
        -------
        dict
            Parsed ``ProcessAutomation.snapshot(scope)`` output.
        """
        return _json.loads(str(self.auto.snapshot(scope)))

    # ── read / write ──
    def get(self, address: str, unit: str = "") -> float:
        """Read a variable value.

        Parameters
        ----------
        address:
            Dot-notation variable address.
        unit:
            Unit of measure for the result.

        Returns
        -------
        float
            The variable value.
        """
        return float(self.auto.getVariableValue(address, unit))

    def set(self, address: str, value: float, unit: str = "", run: bool = True):
        """Write an input variable and optionally re-run.

        Parameters
        ----------
        address:
            Dot-notation variable address.
        value:
            New numeric value.
        unit:
            Unit of measure of ``value``.
        run:
            When True, re-run the process after writing.

        Returns
        -------
        FlowsheetEditor
            ``self`` for chaining.
        """
        if run:
            self.auto.setVariableValueAndRun(address, float(value), unit)
        else:
            self.auto.setVariableValue(address, float(value), unit)
        return self

    def set_safe(self, address: str, value: float, unit: str = "") -> dict:
        """Write a value using the self-healing ``setVariableValueSafe`` path.

        Parameters
        ----------
        address:
            Variable address (typos/case are auto-corrected when possible).
        value:
            New numeric value.
        unit:
            Unit of measure of ``value``.

        Returns
        -------
        dict
            Parsed JSON describing success, auto-correction, or diagnostics.
        """
        return _json.loads(str(self.auto.setVariableValueSafe(address, float(value), unit)))

    def evaluate(self, setpoints: Dict[str, float], readbacks: List[str],
                 unit: Optional[str] = None, readback_unit: str = "",
                 max_iterations: int = 30, tolerance: float = 5.0e-3) -> dict:
        """Apply setpoints, run to convergence, and read objectives in one call.

        Parameters
        ----------
        setpoints:
            Mapping of input address to value.
        readbacks:
            Output addresses to read after convergence.
        unit:
            Unit of measure for all setpoints (``None`` uses each default).
        readback_unit:
            Unit of measure for all readbacks.
        max_iterations:
            Maximum convergence iterations.
        tolerance:
            Convergence tolerance.

        Returns
        -------
        dict
            Parsed JSON with ``feasible``, ``readbacks``, ``setpointsRejected``
            and convergence info.  Never raises on a malformed candidate.
        """
        java_map = self.context.cls("java.util.LinkedHashMap")()
        for key, value in setpoints.items():
            java_map.put(key, float(value))
        java_list = self.context.cls("java.util.ArrayList")()
        for addr in readbacks:
            java_list.add(addr)
        result = self.auto.evaluate(java_map, unit, java_list, readback_unit,
                                    int(max_iterations), float(tolerance))
        return _json.loads(str(result))

    # ── chat ──
    def apply_command(self, text: str, run: bool = True) -> dict:
        """Parse and apply a natural-language edit instruction.

        Parameters
        ----------
        text:
            Instruction such as ``"set Stage 1 Compressor outlet pressure to
            130 bara"``.
        run:
            When True, re-run the process after the edit.

        Returns
        -------
        dict
            ``{"ok": bool, "edit": parsed, "address": str, ...}`` describing
            what happened (including a ``reason`` when parsing fails).
        """
        edit = parse_edit_command(text, known_units=self.units())
        if edit is None:
            return {"ok": False, "reason": "could not parse instruction", "text": text}
        address = "%s.%s" % (edit["unit"], edit["property"])
        outcome = self.set_safe(address, edit["value"], edit["uom"])
        if run:
            try:
                self.process.run()
            except Exception as exc:  # pragma: no cover - depends on model
                return {"ok": False, "edit": edit, "address": address,
                        "reason": "run failed: %s" % exc, "automation": outcome}
        status = str(outcome.get("status", "")).lower()
        ok = status in ("ok", "success", "auto_corrected") or "value" in outcome
        return {"ok": ok, "edit": edit, "address": address, "automation": outcome}
