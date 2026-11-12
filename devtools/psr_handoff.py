"""Generic machine-readable hand-off of a process-study report run.

Downstream document builders (design-basis, package or register builders) must not scrape the Word report.
``neqsim process-report`` therefore writes ``handoff/process_basis.json`` (schema ``process_basis.v1``) and
``handoff/stream_table.csv`` next to the report. The schema is generic: it carries only what the run computed or
what the basis file stated, with no consumer-specific names.

Python API: :func:`build_process_basis` and :func:`write_handoff`.
"""
import csv
import datetime
import json
import os

SCHEMA = "process_basis.v1"
STREAM_KEYS = ("t_c", "p_bara", "mass_kg_h", "mole_kmol_h", "mw_g_mol", "vol_actual_m3_h", "density_kg_m3", "std_vol_ideal_sm3_h",
               "h_kj_kg", "H_kw", "state")
EQUIPMENT_KEYS = ("duty_kw", "power_kw", "dp_bar")


def _rel(path, base):
    if not path:
        return None
    try:
        return os.path.relpath(path, base).replace("\\", "/")
    except ValueError:
        return str(path).replace("\\", "/")


def _pick(record, keys):
    return {k: record.get(k) for k in keys if record.get(k) is not None}


def _streams(data):
    out = []
    for s in data.get("streams", []):
        by_case = {}
        for case in data.get("cases", []):
            r = (s.get("results") or {}).get(case["id"]) or {}
            if not r.get("available", True):
                continue
            row = _pick(r, STREAM_KEYS)
            if r.get("z"):
                row["mole_fractions"] = dict(r["z"])
            by_case[case["id"]] = row
        out.append({"id": s["id"], "label": s.get("label"), "area": s.get("area"), "kind": s.get("kind"),
                    "source": s.get("source") or "boundary", "destination": s.get("destination") or "boundary", "by_case": by_case})
    return out


def _equipment(data):
    out = []
    for e in data.get("equipment", []):
        by_case = {}
        for case in data.get("cases", []):
            r = (e.get("results") or {}).get(case["id"]) or {}
            row = _pick(r, EQUIPMENT_KEYS)
            if r.get("extras"):
                row["extras"] = dict(r["extras"])
            by_case[case["id"]] = row
        out.append({"tag": e["tag"], "area": e.get("area"), "type": e.get("java_class"), "category": e.get("category"),
                    "inlet_streams": list(e.get("inlet_streams", [])), "outlet_streams": list(e.get("outlet_streams", [])),
                    "design": dict(e.get("design") or {}), "by_case": by_case})
    return out


def _design_cases(data, basis):
    described = {str(c.get("id")): c for c in basis.get("operating_cases") or []}
    out = []
    for case in data.get("cases", []):
        info = described.get(str(case["id"]), {})
        out.append({"id": case["id"], "label": case.get("label"), "role": info.get("role"), "description": info.get("description"),
                    "data_source": info.get("data_source")})
    return out


def _diagrams(diagrams, out_dir):
    if not diagrams:
        return {"bfd": None, "pfd_sheets": [], "layout_review": None}
    bfd = diagrams.get("bfd") or {}
    pfd = diagrams.get("pfd") or {}
    sheets = [{"number": s.get("number"), "title": s.get("title"), "drawing_number": s.get("drawing_number"), "svg": _rel(s.get("svg"), out_dir)}
              for s in pfd.get("sheets", [])]
    review = diagrams.get("layout_review") or {}
    return {"bfd": {"svg": _rel(bfd.get("svg"), out_dir), "pdf": _rel(bfd.get("pdf"), out_dir)}, "pfd_pdf": _rel(pfd.get("pdf"), out_dir),
            "pfd_sheets": sheets, "layout_review": {k: review.get(k) for k in ("status", "sheets", "counts") if k in review}}


def build_process_basis(data, basis, standards, balances, diagrams, validation, out_dir, docx_path=None, pdf_path=None):
    """Assemble the ``process_basis.v1`` document as a JSON-serialisable dict.

    Args:
        data: merged data set from ``psr_extract``.
        basis: engineering basis dict.
        standards: standards register (list of dicts).
        balances: result of ``psr_balances.compute_balances``.
        diagrams: result of ``process_study_report.build_diagrams`` (may be None).
        validation: final validation result (dict with ``status`` and ``checks``).
        out_dir: report output folder; all paths in the document are relative to it.
        docx_path: path of the Word report, when built.
        pdf_path: path of the PDF report, when built.

    Returns:
        dict following schema ``process_basis.v1``.
    """
    doc = basis.get("document") or {}
    meta = data.get("meta") or {}
    checks = (validation or {}).get("checks") or []
    return {
        "schema": SCHEMA,
        "generated": datetime.date.today().isoformat(),
        "generator": "neqsim process-report",
        "document": {k: doc.get(k) for k in ("title", "document_number", "revision", "issue_status", "readiness", "readiness_note", "date")},
        "report": {"docx": _rel(docx_path, out_dir), "pdf": _rel(pdf_path, out_dir),
                   "validation_status": (validation or {}).get("status"),
                   "validation_checks": {c["id"]: c["status"] for c in checks}},
        "model": {"name": meta.get("model_name"), "neqsim_version": meta.get("neqsim_version"), "eos": meta.get("eos"),
                  "mixing_rules": meta.get("mixing_rules"),
                  "standard_conditions": meta.get("standard_conditions") or (basis.get("conventions") or {}).get("standard_conditions")},
        "units": (basis.get("conventions") or {}).get("units"),
        "boundaries": basis.get("boundaries") or {},
        "design_cases": _design_cases(data, basis),
        "components": [c["name"] for c in data.get("components", [])],
        "streams": _streams(data),
        "equipment": _equipment(data),
        "diagrams": _diagrams(diagrams, out_dir),
        "balances_summary": (balances or {}).get("summary"),
        "acceptance_criteria": basis.get("acceptance_criteria") or [],
        "assumptions": basis.get("assumptions") or [],
        "data_gaps": basis.get("data_gaps") or [],
        "exclusions": basis.get("exclusions") or [],
        "standards": [{k: s.get(k) for k in ("id", "title", "status", "applies") if k in s} for s in standards or []],
    }


def write_handoff(out_dir, process_basis, data):
    """Write ``handoff/process_basis.json`` and ``handoff/stream_table.csv`` under ``out_dir``.

    Args:
        out_dir: report output folder.
        process_basis: dict from :func:`build_process_basis`.
        data: merged data set (used for the flat stream table).

    Returns:
        dict with the written ``json`` and ``stream_table`` paths.
    """
    folder = os.path.join(out_dir, "handoff")
    os.makedirs(folder, exist_ok=True)
    json_path = os.path.join(folder, "process_basis.json")
    with open(json_path, "w", encoding="utf-8") as fh:
        json.dump(process_basis, fh, indent=1, default=str)
    csv_path = os.path.join(folder, "stream_table.csv")
    columns = ["case", "stream_id", "stream_name", "area", "source", "destination"] + list(STREAM_KEYS)
    with open(csv_path, "w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=columns)
        writer.writeheader()
        for s in process_basis["streams"]:
            for case_id, row in s["by_case"].items():
                record = {"case": case_id, "stream_id": s["id"], "stream_name": s["label"], "area": s["area"], "source": s["source"],
                          "destination": s["destination"]}
                record.update({k: row.get(k, "") for k in STREAM_KEYS})
                writer.writerow(record)
    return {"json": json_path, "stream_table": csv_path}
