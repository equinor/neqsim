"""Connectivity audit of the extracted process topology (process-study-report toolkit).

A drawing and a stream table are only as good as the wiring of the solved model. A model that was rebuilt from a
JSON export, an old serialized file or a half-finished script can contain units that never received an inlet
(recycles without a tear stream, columns without a feed, heaters without a stream), streams that nothing produces
or consumes, and mixers with a single inlet. The extractor draws exactly what the model contains, so these
defects show up as arrows that end in blank space. This module finds them from the extracted data (no JVM needed),
ranks repair candidates by name and area, and writes a machine-readable audit that the validator, the report and
the drawings use.

Findings are never silently repaired. To repair, add a ``wiring`` list to ``cases.yaml`` (see :mod:`psr_wiring`)
or correct the source model; the audit then shows the effect on the next run.
"""

import csv
import json
import os
import re

SEVERITY_ERROR = "error"
SEVERITY_WARNING = "warning"

# Categories whose units always move a process stream; logic and numerical helpers are not audited.
FLOW_CATEGORIES = ("valve", "mixer", "splitter", "separator", "cooler", "heater", "exchanger", "compressor", "pump",
                   "expander", "ejector", "column", "pipe", "reactor", "recycle")

_STOP = {"recycle", "recycl", "resycle", "stream", "out", "outlet", "inlet", "the", "to", "from", "process", "guess",
         "split", "mixed", "of", "and", "a"}
_PHASE = {"liq": "liq", "liquid": "liq", "gas": "gas", "oil": "oil", "water": "water", "aq": "water"}

CODE_TEXT = {
    "RECYCLE-NO-INLET": ("A recycle has no tear stream, so the loop is open: the stream that should return to it "
                         "is drawn as a product and the recycle outlet as an unexplained feed."),
    "COLUMN-NO-FEED": "A distillation column has no feed stream attached.",
    "UNIT-NO-INLET": "A unit has no inlet stream.",
    "UNIT-NO-OUTLET": "A unit has no outlet stream.",
    "UNIT-UNCONNECTED": "A unit has neither inlet nor outlet streams and is not connected to the flowsheet.",
    "MIXER-SINGLE-INLET": "A mixer has fewer than two inlet streams; a second inlet is probably missing.",
    "STREAM-ISOLATED": "A stream is neither produced nor consumed by any unit.",
}

REMEDY = {
    "RECYCLE-NO-INLET": ("re-export the model with a NeqSim version whose JSON export writes column outlets, multiple column feeds and "
                         "recycle inlets (fixed in JsonProcessExporter), run the live model, or add a wiring entry "
                         "{unit: 'Area::recycle', inlet: 'Area::unit.outlet'} to cases.yaml"),
    "COLUMN-NO-FEED": "wire the feed (wiring entry with feed: and optional tray:) or fix the source model",
    "UNIT-NO-INLET": "wire the inlet (wiring entry with inlet:) or fix the source model",
    "UNIT-NO-OUTLET": "check that the unit is a deliberate sink; otherwise fix the source model",
    "UNIT-UNCONNECTED": "wire the unit (wiring entry with inlet:) or remove it from the model",
    "MIXER-SINGLE-INLET": "add the missing inlet to the mixer in the source model",
    "STREAM-ISOLATED": "connect the stream to its consumer or producer in the source model",
}


def _tokens(text):
    out = set()
    for tok in re.split(r"[^a-z0-9]+", str(text).lower()):
        if tok and tok not in _STOP and not tok.isdigit():
            out.add(_PHASE.get(tok, tok))
    return out


def _phase_tokens(tokens):
    return {t for t in tokens if t in ("liq", "gas", "oil", "water")}


def _score(unit_tokens, stream_tokens):
    shared = (unit_tokens & stream_tokens) - {"liq", "gas", "oil", "water"}
    phase_match = bool(_phase_tokens(unit_tokens) & _phase_tokens(stream_tokens))
    return 2 * len(shared) + (1 if phase_match else 0)


def _candidates(unit, streams, want_kind, recycle_tags=(), limit=3):
    """Streams in the unit's area that could complete its wiring, ranked by shared name tokens.

    Outlets of the unit itself and the tear (guess) streams produced by recycles are never candidates.
    """
    utokens = _tokens(unit["tag"])
    for sid in unit["outlet_streams"]:
        utokens |= _tokens(streams[sid]["label"])
    ranked = []
    for s in streams.values():
        if s["kind"] != want_kind or s.get("area") != unit["area"]:
            continue
        if unit["tag"] in s["producers"] or any(p in recycle_tags for p in s["producers"]):
            continue
        score = _score(utokens, _tokens(s["label"]) | _tokens(s["name"]))
        ranked.append((score, s["id"], s["label"]))
    ranked.sort(key=lambda r: (-r[0], r[1]))
    return [{"stream": sid, "label": label, "score": score} for score, sid, label in ranked[:limit] if score > 0]


def _mass(stream, case):
    res = (stream.get("results") or {}).get(case) or {}
    return res.get("mass_kg_h")


def audit_connectivity(data):
    """Audit the topology of an extracted data set.

    Args:
        data: merged data set from :mod:`psr_extract` (``equipment``, ``streams``, ``cases``).

    Returns:
        dict with ``findings`` (list), ``summary`` (counts per code and severity), ``flagged_units`` (tag -> codes),
        ``flagged_streams`` and ``status`` (``PASS`` / ``WARN`` / ``FAIL``).
    """
    streams = {s["id"]: s for s in data["streams"]}
    cases = [c["id"] for c in data.get("cases", [])]
    case = cases[0] if cases else None
    recycle_tags = {u["tag"] for u in data["equipment"] if u["category"] == "recycle"}
    findings = []

    def add(code, severity, kind, name, area, detail, candidates=None):
        findings.append({"code": code, "severity": severity, "kind": kind, "name": name, "area": area,
                         "message": CODE_TEXT[code], "detail": detail, "remedy": REMEDY[code],
                         "candidates": candidates or []})

    for u in data["equipment"]:
        cat = u["category"]
        if cat not in FLOW_CATEGORIES and cat != "other":
            continue
        n_in, n_out = len(u["inlet_streams"]), len(u["outlet_streams"])
        severity = SEVERITY_ERROR if cat in FLOW_CATEGORIES else SEVERITY_WARNING
        detail = "%s, %d inlet(s), %d outlet(s)" % (u["java_class"], n_in, n_out)
        if n_in == 0 and n_out == 0:
            add("UNIT-UNCONNECTED", severity, "unit", u["tag"], u["area"], detail)
            continue
        if n_in == 0:
            code = "RECYCLE-NO-INLET" if cat == "recycle" else ("COLUMN-NO-FEED" if cat == "column" else "UNIT-NO-INLET")
            add(code, severity, "unit", u["tag"], u["area"], detail, _candidates(u, streams, "product", recycle_tags))
            if cat == "recycle":
                findings[-1]["affects"] = sorted({c for sid in u["outlet_streams"] for c in streams[sid]["consumers"]})
        if n_out == 0:
            add("UNIT-NO-OUTLET", severity, "unit", u["tag"], u["area"], detail)
        if cat == "mixer" and 0 < n_in < 2:
            add("MIXER-SINGLE-INLET", SEVERITY_WARNING, "unit", u["tag"], u["area"], detail,
                _candidates(u, streams, "feed", recycle_tags) + _candidates(u, streams, "isolated", recycle_tags))

    for s in streams.values():
        if s["kind"] == "isolated":
            mass = _mass(s, case)
            flow = "mass flow %s kg/h" % ("n/a" if mass is None else "%.4g" % mass)
            severity = SEVERITY_ERROR if (mass or 0.0) > 0.0 else SEVERITY_WARNING
            add("STREAM-ISOLATED", severity, "stream", s["id"], s.get("area", ""), "%s (%s); %s" % (s["label"], s["name"], flow))

    codes, flagged_units, flagged_streams = {}, {}, {}
    for f in findings:
        codes[f["code"]] = codes.get(f["code"], 0) + 1
        (flagged_units if f["kind"] == "unit" else flagged_streams).setdefault(f["name"], []).append(f["code"])
    n_err = sum(1 for f in findings if f["severity"] == SEVERITY_ERROR)
    n_warn = len(findings) - n_err
    areas = {}
    for f in findings:
        areas[f["area"]] = areas.get(f["area"], 0) + 1
    return {"status": "FAIL" if n_err else ("WARN" if n_warn else "PASS"),
            "summary": {"n_units": len(data["equipment"]), "n_streams": len(streams), "n_findings": len(findings),
                        "n_error": n_err, "n_warning": n_warn, "by_code": codes, "by_area": areas},
            "findings": findings, "flagged_units": flagged_units, "flagged_streams": flagged_streams}


def diagram_notes(audit, limit=2):
    """Short per-unit lines for the drawings, keyed by unit tag."""
    short = {"RECYCLE-NO-INLET": "NOT CONNECTED: recycle has no inlet", "COLUMN-NO-FEED": "NOT CONNECTED: no feed",
             "UNIT-NO-INLET": "NOT CONNECTED: no inlet", "UNIT-NO-OUTLET": "no outlet modelled",
             "UNIT-UNCONNECTED": "NOT CONNECTED: no streams", "MIXER-SINGLE-INLET": "check: single inlet"}
    out = {}
    for tag, codes in audit["flagged_units"].items():
        lines = [short[c] for c in codes if c in short]
        if lines:
            out[tag] = lines[:limit]
    for f in audit["findings"]:
        for tag in f.get("affects", []):
            if "inlet from open recycle" not in out.get(tag, []):
                out.setdefault(tag, []).append("inlet from open recycle")
    return out


def write_audit(audit, out_dir):
    """Write ``connectivity_audit.json`` and ``connectivity_audit.csv``; returns both paths."""
    os.makedirs(out_dir, exist_ok=True)
    jp = os.path.join(out_dir, "connectivity_audit.json")
    with open(jp, "w", encoding="utf-8") as fh:
        json.dump(audit, fh, indent=1)
    cp = os.path.join(out_dir, "connectivity_audit.csv")
    with open(cp, "w", encoding="utf-8", newline="") as fh:
        writer = csv.writer(fh)
        writer.writerow(["severity", "code", "kind", "name", "area", "detail", "repair_candidates", "remedy"])
        for f in audit["findings"]:
            cand = "; ".join("%s %s" % (c["stream"], c["label"]) for c in f["candidates"])
            writer.writerow([f["severity"], f["code"], f["kind"], f["name"], f["area"], f["detail"], cand, f["remedy"]])
    return jp, cp
