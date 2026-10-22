"""Explicit wiring repairs for models whose source lost stream connections (process-study-report toolkit).

``cases.yaml`` may carry a top-level ``wiring`` list that is applied to every freshly built model before the case
overrides and the run::

    wiring:
      - {unit: "sture::gas resycle", inlet: "sture::compressor gas to gas column.outlet"}
      - {unit: "NGL column::NGL column", feed: "NGL column::ngl valve to column.outlet", tray: 5}
      - {unit: "TEX process A::25HJ101", inlet: "TEX process A::dew point scrubber.gasOut"}

``unit`` is ``Area::name`` (``name`` alone for a single ProcessSystem, or a name that is unique in the model).
``inlet`` / ``feed`` is the stream to attach: ``Area::unit.port`` where ``port`` is ``outlet`` (the stream itself or the
first outlet), ``gasOut``, ``liquidOut``, ``oilOut``, ``waterOut``, ``splitN`` or ``outN`` (N-th outlet stream).
Distillation columns get ``addFeedStream`` (optional ``tray``), every other unit ``setInletStream`` (falling back to
``addStream`` for recycles and mixers). Every applied entry is recorded in the report basis so that a repaired
connection is never mistaken for source data. Use ``connectivity_audit.csv`` (repair candidates) to find the stream.
"""

PORT_GETTERS = {"gasout": "getGasOutStream", "liquidout": "getLiquidOutStream", "oilout": "getOilOutStream",
                "waterout": "getWaterOutStream", "aqueousout": "getAqueousOutStream"}


class WiringError(Exception):
    """A wiring entry cannot be resolved or applied."""


def _systems(model):
    """Ordered (area, ProcessSystem) pairs; a bare ProcessSystem has the area ``None``."""
    if hasattr(model, "getAreaNames"):
        return [(str(n), model.get(str(n))) for n in model.getAreaNames()]
    return [(None, model)]


def find_unit(model, reference):
    """Resolve ``Area::name`` (or a unique ``name``) to ``(area, unit)``."""
    area, _, name = reference.rpartition("::")
    matches = []
    for a, system in _systems(model):
        if area and a != area:
            continue
        unit = system.getUnit(name)
        if unit is not None:
            matches.append((a, unit))
    if not matches:
        areas = [a for a, _ in _systems(model) if a]
        raise WiringError("unit %r not found%s" % (reference, " (areas: %s)" % ", ".join(areas) if areas else ""))
    if len(matches) > 1:
        raise WiringError("unit name %r exists in several areas (%s); prefix it with 'Area::'" % (name, ", ".join(a or "-" for a, _ in matches)))
    return matches[0]


def _is_stream(unit):
    import jpype

    return isinstance(unit, jpype.JClass("neqsim.process.equipment.stream.StreamInterface"))


def resolve_stream(model, reference):
    """Resolve ``Area::unit.port`` to a Java stream object."""
    base, dot, port = reference.rpartition(".")
    if not dot or ("::" in port):
        base, port = reference, "outlet"
    _, unit = find_unit(model, base)
    key = port.strip().lower()
    if key in ("", "outlet") and _is_stream(unit):
        return unit
    if key in PORT_GETTERS:
        getter = getattr(unit, PORT_GETTERS[key], None)
        stream = getter() if getter else None
    elif key.startswith("split") and key[5:].isdigit():
        stream = unit.getSplitStream(int(key[5:]))
    elif key.startswith("out") and key[3:].isdigit():
        outs = list(unit.getOutletStreams())
        stream = outs[int(key[3:])] if int(key[3:]) < len(outs) else None
    else:
        outs = list(unit.getOutletStreams())
        stream = outs[0] if outs else None
    if stream is None:
        raise WiringError("stream %r has no port %r" % (base, port))
    return stream


def _attach(unit, stream, tray):
    if hasattr(unit, "addFeedStream"):
        if tray is None:
            unit.addFeedStream(stream)
        else:
            unit.addFeedStream(stream, int(tray))
        return "addFeedStream"
    if hasattr(unit, "setInletStream"):
        unit.setInletStream(stream)
        return "setInletStream"
    if hasattr(unit, "addStream"):
        unit.addStream(stream)
        return "addStream"
    raise WiringError("%s has no setInletStream, addStream or addFeedStream" % unit.getName())


def validate_entries(wiring):
    """Check the shape of the ``wiring`` list; returns the list (empty for None)."""
    if wiring is None:
        return []
    if not isinstance(wiring, list):
        raise WiringError("'wiring' must be a list of mappings")
    for i, entry in enumerate(wiring):
        if not isinstance(entry, dict) or not entry.get("unit") or not (entry.get("inlet") or entry.get("feed")):
            raise WiringError("wiring[%d] needs 'unit' and 'inlet' (or 'feed')" % i)
    return wiring


def apply_wiring(model, wiring):
    """Apply every entry to a freshly built model; returns one record per entry (for the basis and the report)."""
    applied = []
    for entry in validate_entries(wiring):
        area, unit = find_unit(model, str(entry["unit"]))
        stream = resolve_stream(model, str(entry.get("inlet") or entry.get("feed")))
        method = _attach(unit, stream, entry.get("tray"))
        applied.append({"unit": str(entry["unit"]), "stream": str(entry.get("inlet") or entry.get("feed")), "method": method,
                        "tray": entry.get("tray")})
    for _, system in _systems(model):
        invalidate = getattr(system, "invalidateGraph", None)
        if invalidate:
            invalidate()
    return applied
