"""Import NeqSim process evidence into a physical plant-layout study.

Process topology and physical geometry have different owners.  This adapter
preserves extracted process tags and stream identities, but never turns a PFD
symbol or vessel diameter into a package footprint.  Explicit layout dimensions
are required unless a complete mechanical-design result is supplied separately.
The module imports no JVM or NeqSim package until a live-process call is made.
"""

from __future__ import annotations

import copy
import hashlib
import json
import math
import os
from pathlib import Path


PROCESS_SCHEMA = "neqsim.process_study_report.data/1"


def _finite_number(value, label, positive=False):
    """Return a finite number, rejecting booleans and invalid dimensions."""
    if isinstance(value, bool):
        raise ValueError("%s must be a finite number" % label)
    try:
        number = float(value)
    except (TypeError, ValueError, OverflowError) as exc:
        raise ValueError("%s must be a finite number" % label) from exc
    if not math.isfinite(number) or (positive and number <= 0):
        raise ValueError("%s must be %sfinite" % (label, "positive and " if positive else ""))
    return number


def _layout_equipment(study):
    """Index explicit layout equipment and validate physical-size evidence."""
    if not isinstance(study, dict):
        raise ValueError("study must be a dictionary")
    records = study.get("equipment")
    if not isinstance(records, list):
        raise ValueError("study.equipment must be a list")
    result = {}
    for record in records:
        if not isinstance(record, dict):
            raise ValueError("study equipment entries must be dictionaries")
        tag = record.get("tag")
        if not isinstance(tag, str) or not tag.strip() or tag in result:
            raise ValueError("layout equipment tags must be nonempty and unique")
        size = record.get("size_m")
        if not isinstance(size, (list, tuple)) or len(size) != 3:
            raise ValueError("%s requires explicit size_m [length, width, height]" % tag)
        for axis, number in zip(("length", "width", "height"), size):
            _finite_number(number, "%s %s" % (tag, axis), positive=True)
        source = record.get("dimension_source")
        if not isinstance(source, dict) or not str(source.get("reference", "")).strip():
            raise ValueError("%s requires dimension_source.reference" % tag)
        result[tag] = record
    return result


def _read_source(source):
    """Read extracted process data and return data plus a traceable reference."""
    if isinstance(source, (str, os.PathLike)):
        path = Path(source)
        with path.open(encoding="utf-8") as handle:
            data = json.load(handle)
        reference = str(path.resolve())
    elif isinstance(source, dict):
        data = copy.deepcopy(source)
        reference = "in-memory extracted process data"
    else:
        raise ValueError("source must be extracted process data or its JSON path")
    if not isinstance(data, dict):
        raise ValueError("extracted process data must be a JSON object")
    if data.get("schema", PROCESS_SCHEMA) != PROCESS_SCHEMA:
        raise ValueError("expected psr_extract schema %s" % PROCESS_SCHEMA)
    if not isinstance(data.get("equipment"), list) or not isinstance(data.get("streams"), list):
        raise ValueError("extracted process data requires equipment and streams lists")
    try:
        encoded = json.dumps(data, sort_keys=True, separators=(",", ":"), allow_nan=False)
    except (TypeError, ValueError) as exc:
        raise ValueError("process data must contain only finite JSON-compatible values") from exc
    return data, reference, hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def _endpoints(stream, plural, singular):
    """Read ordered endpoint lists without dropping repeated attachments."""
    if plural in stream:
        values = stream[plural]
    else:
        value = stream.get(singular)
        values = [] if value is None else [value]
    if not isinstance(values, list) or any(not isinstance(tag, str) or not tag for tag in values):
        raise ValueError("stream %s requires a list of %s tags" % (stream.get("id"), plural))
    return values


def import_process(source, study):
    """Return a deep-copied study enriched by extracted NeqSim process evidence.

    ``source`` is a ``psr_extract.extract_process`` dictionary or JSON file.
    Every source equipment tag must map exactly into ``study['equipment']``;
    layout-only objects such as buildings may also exist.  Internal connections
    replace the input connection list and retain the original stream identity.
    Feed/product boundary streams and all source results remain in the preserved
    JSON snapshot under ``process_import.source_data``.  No geometry is inferred.
    Invalid inputs raise ``ValueError`` before either caller-owned object changes.
    """
    result = copy.deepcopy(study)
    layout = _layout_equipment(result)
    data, reference, fingerprint = _read_source(source)
    source_tags = set()
    for record in data["equipment"]:
        if not isinstance(record, dict):
            raise ValueError("process equipment entries must be dictionaries")
        tag = record.get("tag")
        if not isinstance(tag, str) or not tag or tag in source_tags:
            raise ValueError("process equipment tags must be nonempty and unique")
        if tag not in layout:
            raise ValueError("unmapped process equipment tag: %s" % tag)
        source_tags.add(tag)
        layout[tag]["process_data"] = copy.deepcopy(record)

    connections = []
    stream_ids = set()
    connection_ids = set()
    boundary_ids = []
    for stream in data["streams"]:
        if not isinstance(stream, dict):
            raise ValueError("process streams must be dictionaries")
        sid = stream.get("id")
        if not isinstance(sid, str) or not sid or sid in stream_ids:
            raise ValueError("process stream ids must be nonempty and unique")
        stream_ids.add(sid)
        producers = _endpoints(stream, "producers", "source")
        consumers = _endpoints(stream, "consumers", "destination")
        for tag in producers + consumers:
            if tag not in source_tags:
                raise ValueError("stream %s references unmapped process tag: %s" % (sid, tag))
        if not producers or not consumers:
            boundary_ids.append(sid)
        for pi, producer in enumerate(producers):
            for ci, consumer in enumerate(consumers):
                cid = sid if len(producers) * len(consumers) == 1 else "%s:p%d:c%d" % (sid, pi, ci)
                if cid in connection_ids:
                    raise ValueError("imported connection id collision: %s" % cid)
                connection_ids.add(cid)
                connections.append({
                    "id": cid,
                    "from_tag": producer,
                    "to_tag": consumer,
                    "service": stream.get("service") or "material",
                    "stream_id": sid,
                    "source_reference": reference,
                })
    result["connections"] = connections
    result["process_import"] = {
        "schema": PROCESS_SCHEMA,
        "reference": reference,
        "fingerprint": fingerprint,
        "equipment_tags": sorted(source_tags),
        "boundary_stream_ids": boundary_ids,
        "source_data": data,
        "execution_status": "SOURCE_EXTRACT_ONLY",
        "geometry_policy": "Explicit layout dimensions; no geometry inferred from process symbols.",
    }
    return result


def import_mechanical_design(mechanical_design, study):
    """Apply complete preliminary NeqSim mechanical dimensions to mapped tags.

    The caller must already have calculated ``SystemMechanicalDesign``.  This
    function checks its structured completeness result and reads numeric summary
    getters, never a dimension-description string.  Names must equal layout tags;
    ambiguous multi-area names require an explicit caller-created mapping first.
    These module estimates remain preliminary and do not establish vendor GA,
    centre-of-gravity, operating/test mass or maintenance-envelope completeness.
    """
    result = copy.deepcopy(study)
    if not isinstance(result, dict) or not isinstance(result.get("equipment"), list):
        raise ValueError("study.equipment must be a list")
    layout = {}
    for record in result["equipment"]:
        tag = record.get("tag") if isinstance(record, dict) else None
        if not isinstance(tag, str) or not tag or tag in layout:
            raise ValueError("layout equipment tags must be nonempty and unique")
        layout[tag] = record
    try:
        optional = mechanical_design.getLastCalculationResult()
        if optional is None or not optional.isPresent():
            raise ValueError("mechanical design has no calculated result")
        calculation = optional.get()
        if not calculation.isComplete():
            raise ValueError("mechanical design result is incomplete")
        summaries = list(mechanical_design.getEquipmentList())
    except ValueError:
        raise
    except Exception as exc:
        raise ValueError("expected calculated NeqSim SystemMechanicalDesign APIs") from exc

    imported = []
    for summary in summaries:
        try:
            tag = str(summary.getName())
            raw_dimensions = [summary.getLength(), summary.getWidth(), summary.getHeight()]
            raw_weight = summary.getWeight()
        except Exception as exc:
            raise ValueError("mechanical summaries require numeric dimension and mass getters") from exc
        if tag not in layout:
            raise ValueError("unmapped mechanical equipment name: %s" % tag)
        if tag in imported:
            raise ValueError("ambiguous mechanical equipment name: %s" % tag)
        dimensions = [
            _finite_number(raw_dimensions[0], "%s mechanical length" % tag, positive=True),
            _finite_number(raw_dimensions[1], "%s mechanical width" % tag, positive=True),
            _finite_number(raw_dimensions[2], "%s mechanical height" % tag, positive=True),
        ]
        weight = _finite_number(raw_weight, "%s mechanical mass" % tag)
        if weight < 0:
            raise ValueError("%s mechanical mass must be nonnegative" % tag)
        record = layout[tag]
        record["size_m"] = dimensions
        record["mass_kg"] = weight
        record["dimension_source"] = {
            "kind": "calculated",
            "reference": "NeqSim SystemMechanicalDesign numeric EquipmentDesignSummary: %s" % tag,
            "maturity": "PRELIMINARY_ESTIMATE",
        }
        record["mass_source"] = {
            "kind": "calculated",
            "reference": "NeqSim EquipmentDesignSummary.getWeight; mass case requires confirmation",
        }
        imported.append(tag)
    expected = result.get("process_import", {}).get("equipment_tags", [])
    missing = sorted(set(expected) - set(imported))
    if missing:
        raise ValueError("missing mechanical dimensions for process tags: %s" % ", ".join(missing))
    if not imported:
        raise ValueError("mechanical design contains no equipment summaries")
    result["mechanical_import"] = {
        "equipment_tags": sorted(imported),
        "status": "COMPLETE_PRELIMINARY_ESTIMATE",
        "source": "NeqSim SystemMechanicalDesign numeric equipment summaries",
    }
    result.setdefault("data_gaps", []).append(
        "Preliminary mechanical module estimates require vendor dimensions, mass-case/CoG "
        "confirmation and operation/maintenance/lifting envelopes."
    )
    _layout_equipment(result)
    return result


def import_live_process(process, study, mechanical_design=None, case_id="A"):
    """Import a solved ProcessSystem/ProcessModel using the existing extractor.

    The caller owns JVM initialization and process execution.  Standard-condition
    reflashes are disabled because placement requires no extra thermodynamic
    solve.  When supplied, a previously calculated mechanical design replaces
    explicit footprint estimates through :func:`import_mechanical_design`.
    """
    import psr_extract

    if hasattr(process, "getAreaNames"):
        systems = [(str(area), process.get(area)) for area in process.getAreaNames()]
    else:
        systems = [("process", process)]
    if not systems:
        raise ValueError("live process contains no process areas")
    for area, system in systems:
        try:
            status = system.getRunStatus()
            successful = status.isCompleted() and status.isSuccess() and status.isConverged()
        except Exception as exc:
            raise ValueError("live process %s requires current NeqSim run-status evidence" % area) from exc
        if not successful:
            raise ValueError("live process %s has not completed a successful converged run" % area)
    basis = study
    if mechanical_design is not None:
        basis = import_mechanical_design(mechanical_design, study)
    data = psr_extract.extract_process(process, case_id=case_id, std_flash=False)
    result = import_process(data, basis)
    result["process_import"]["execution_status"] = "SOLVED_SUCCESSFULLY"
    if mechanical_design is not None:
        expected = set(result["process_import"]["equipment_tags"])
        calculated = set(result["mechanical_import"]["equipment_tags"])
        if expected - calculated:
            raise ValueError("mechanical dimensions are missing for process equipment")
    return result
