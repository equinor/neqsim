"""Dimensioned physical plant layout sheets from the canonical layout dataset.

The drawings reuse the process/SURF report A3 sheet furniture.  Equipment is
drawn as its conceptual installed envelope, not as a process diagram symbol.
Every plotted coordinate comes from the same placement used by the checks.
"""

from __future__ import annotations

import json
import math
from pathlib import Path

from fa_draw import Sheet, patterned
from layout_model import equipment_bounds
from psr_diagrams import kit as K

PALETTE = {
    "separator": "#c8dcd0", "compressor": "#c8d6e8", "pump": "#d3deea",
    "cooler": "#e9dec8", "heat_exchanger": "#e9dec8", "scrubber": "#c8dcd0",
    "utilities": "#ded5e7", "tank": "#e2e5e8", "default": "#dbe4e9",
}
SCALES = (20, 25, 50, 75, 100, 125, 150, 200, 250, 300, 400, 500, 750, 1000, 2000, 5000)


def _project_tb(study):
    """Build shared title-block fields without assigning engineering approval."""
    p = study["project"]
    return {
        "company": p.get("company", "NeqSim synthetic example"),
        "project": p["id"], "title": "Plant layout study", "document_number": p["id"],
        "revision": str(p.get("revision", "A")), "issue_status": "SCREENING",
        "date": p.get("date", "not assigned"), "drawn_by": "NeqSim layout tooling",
        "checked_by": "not assigned", "approved_by": "not assigned",
        "revision_history": [{"rev": str(p.get("revision", "A")),
                              "date": p.get("date", "not assigned"),
                              "description": "Concept layout for engineering review"}],
    }


def _scale(width, depth, panel_width=240.0, panel_height=160.0):
    """Return the smallest preferred denominator that fits the allotted panel."""
    minimum = max(width * 1000.0 / panel_width, depth * 1000.0 / panel_height)
    return next((n for n in SCALES if n >= minimum), math.ceil(minimum / 1000.0) * 1000)


def _dimension(sheet, p1, p2, text, offset, vertical=False):
    """Draw one overall linear dimension in paper millimetres."""
    x1, y1 = p1
    x2, y2 = p2
    if vertical:
        xx = x1 + offset
        sheet.add(K.line(x1, y1, xx - 2, y1, sw=0.2))
        sheet.add(K.line(x2, y2, xx - 2, y2, sw=0.2))
        sheet.add(K.line(xx, y1, xx, y2, sw=0.25))
        for yy in (y1, y2):
            sheet.add(K.line(xx - 1, yy + 1, xx + 1, yy - 1, sw=0.3))
        sheet.text(xx - 2, (y1 + y2) / 2, text, size=2.8, anchor="middle", rotate=-90)
    else:
        yy = y1 + offset
        sheet.add(K.line(x1, y1, x1, yy + 2, sw=0.2))
        sheet.add(K.line(x2, y2, x2, yy + 2, sw=0.2))
        sheet.add(K.line(x1, yy, x2, yy, sw=0.25))
        for xx in (x1, x2):
            sheet.add(K.line(xx - 1, yy + 1, xx + 1, yy - 1, sw=0.3))
        sheet.text((x1 + x2) / 2, yy + 4, text, size=2.8, anchor="middle")


def _north(sheet, study, x=274.0, y=37.0):
    """Draw project north relative to the local grid axes."""
    angle = math.radians(float(study.get("coordinate_system", {}).get("north_deg", 0)))
    dx, dy = math.sin(angle) * 11.0, -math.cos(angle) * 11.0
    sheet.add(K.line(x, y, x + dx, y + dy, sw=0.6))
    sheet.add(K.arrowhead(x + dx, y + dy, dx, dy))
    sheet.text(x + dx, y + dy - 2.5, "N", size=3.0, anchor="middle", bold=True)
    sheet.text(x, y + 7, "GRID +Y UP", size=2.5, anchor="middle")


def _rect_world(sheet, bounds, transform, fill, stroke, sw=0.4, dashed=False):
    """Draw the plan projection of a world-coordinate bounding box."""
    x1, y1 = transform(bounds[0], bounds[1])
    x2, y2 = transform(bounds[3], bounds[4])
    points = [(x1, y1), (x2, y1), (x2, y2), (x1, y2), (x1, y1)]
    if dashed:
        sheet.add(patterned(points, (2.0, 1.1), stroke, sw))
    else:
        sheet.add(K.rect(min(x1, x2), min(y1, y2), abs(x2 - x1), abs(y2 - y1),
                         fill=fill, stroke=stroke, sw=sw))
    return min(x1, x2), min(y1, y2), abs(x2 - x1), abs(y2 - y1)


def _plan_sheet(study, alternative, deck, number, total):
    """Create one plot/deck plan with a coordinate schedule and explicit scale."""
    denominator = _scale(deck["width_m"], deck["depth_m"])
    factor = 1000.0 / denominator
    code = "%s-%s" % (alternative["id"], deck["id"])
    title = "%s  %s  plan at EL %.2f m" % (alternative["id"], deck["id"], deck["elevation_m"])
    sheet = Sheet(code, number, total, title, _project_tb(study), "1:%s" % denominator,
                  "SCREENING - engineering review and missing evidence remain open", min_text=2.5)
    ox = 34.0
    oy = 201.0

    def transform(x, y):
        return ox + x * factor, oy - y * factor

    w, d = deck["width_m"], deck["depth_m"]
    _rect_world(sheet, [0, 0, 0, w, d, 0], transform, "#fafbfc", "#243648", 0.65)
    # Reserved corridor extents are project inputs, not derived safe distances.
    for corridor in study.get("corridors", []):
        if corridor["deck"] != deck["id"]:
            continue
        b = corridor["bounds_m"]
        color = "#d7efdf" if corridor.get("kind") == "escape" else "#e5e7ed"
        _rect_world(sheet, [b[0], b[1], 0, b[2], b[3], 0], transform, color, "#7c9a88", 0.25)
    for obstacle in study.get("obstacles", []):
        if obstacle["deck"] == deck["id"]:
            _rect_world(sheet, obstacle["bounds_m"], transform, "#e4e0de", "#665d59", 0.5)

    index = {e["tag"]: e for e in study["equipment"]}
    placements = sorted((p for p in alternative["placements"] if p["deck"] == deck["id"]),
                        key=lambda p: p["tag"])
    for placement in placements:
        eq = index[placement["tag"]]
        for envelope in eq.get("envelopes", []):
            bounds = equipment_bounds(eq, placement, deck, envelope)
            _rect_world(sheet, bounds, transform, "none", "#b77a27", 0.45, dashed=True)
    for i, placement in enumerate(placements, 1):
        eq = index[placement["tag"]]
        bounds = equipment_bounds(eq, placement, deck)
        color = PALETTE.get(eq.get("kind", ""), PALETTE["default"])
        xx, yy, ww, hh = _rect_world(sheet, bounds, transform, color, "#243648", 0.45)
        label = eq["tag"]
        if K.text_w(label, 2.7, True) > ww - 1.2 or hh < 5:
            label = "%02d" % i
        sheet.text(xx + ww / 2, yy + hh / 2 + 0.8, label, size=2.7, anchor="middle", bold=True)
        sheet.items.append({"kind": "equipment", "tag": eq["tag"], "deck": deck["id"],
                            "bounds_m": bounds, "paper_bounds_mm": [xx, yy, xx + ww, yy + hh],
                            "placement": placement})
    for exit_ in study.get("exits", []):
        if exit_["deck"] != deck["id"]:
            continue
        xx, yy = transform(exit_["x_m"], exit_["y_m"])
        sheet.add(K.circle(xx, yy, 1.9, fill="#fff", stroke="#197344", sw=0.6))
        sheet.text(xx, yy + 0.8, "E", size=2.5, anchor="middle", bold=True, fill="#197344")
    _dimension(sheet, transform(0, 0), transform(w, 0), "%.2f m" % w, 8)
    _dimension(sheet, transform(0, 0), transform(0, d), "%.2f m" % d, -9, vertical=True)
    sheet.text(ox, oy + 20, "Local origin  E 0.00   N 0.00 m", size=2.5)
    _north(sheet, study)
    # Table on the right keeps full tags available when their shapes are small.
    tx, ty = 296.0, 29.0
    sheet.text(tx, ty, "EQUIPMENT COORDINATES", size=3.2, bold=True)
    sheet.text(tx, ty + 7, "No  Tag", size=2.6, bold=True)
    sheet.text(370, ty + 7, "E / N / ROT", size=2.6, bold=True)
    sheet.add(K.line(tx, ty + 9, 403, ty + 9, sw=0.3))
    for i, p in enumerate(placements, 1):
        yy = ty + 15 + (i - 1) * 11
        eq = index[p["tag"]]
        sheet.text(tx, yy, "%02d  %s" % (i, p["tag"]), size=2.7, bold=True)
        sheet.text(403, yy, "%.1f / %.1f / %d" % (p["x_m"], p["y_m"], p.get("rotation_deg", 0)),
                   size=2.5, anchor="end")
        sheet.text(tx + 5, yy + 4, "%.1f x %.1f x %.1f m  %s" % tuple(eq["size_m"] + [eq.get("kind", "equipment")]), size=2.5)
    sy = max(ty + 20 + len(placements) * 11, 158.0)
    counts = {k: sum(c["status"] == k for c in alternative.get("checks", []))
              for k in ("PASS", "FAIL", "NOT_EVALUATED")}
    for j, text in enumerate([
        "ALTERNATIVE STATUS  " + alternative.get("status", "NOT_EVALUATED"),
        "Checks  %d pass / %d fail / %d open" % (counts["PASS"], counts["FAIL"], counts["NOT_EVALUATED"]),
        "E / N coordinates in project metres",
        "ROT is degrees about local vertical",
        "Detailed piping routing not included",
    ]):
        sheet.text(tx, sy + j * 5, text, size=2.5, bold=(j == 0))
    sheet.legend = [("line", "#243648", "solid", "Physical equipment envelope"),
                    ("line", "#b77a27", "dash", "Reserved maintenance or operation envelope"),
                    ("line", "#197344", "solid", "Green area: reserved escape corridor"),
                    ("text", "E: project exit location"),
                    ("text", "Number labels refer to coordinate schedule")]
    sheet.notes = [
        "Dimensions and coordinates are metres. The plotted A3 scale is valid at 100 percent print size.",
        "Geometry is conceptual package envelopes. Vendor dimensions, nozzles, auxiliaries and lifting cases require review.",
        "Corridors are reserved project inputs. Hazard exposure, route independence and evacuation adequacy remain separate checks.",
        "Project frame: +X east, +Y north/grid, +Z up. Datum: %s." % study.get("coordinate_system", {}).get("elevation_datum", "project"),
        "Dataset alternative: %s. Equipment placement is independent of PFD/P&ID sheet coordinates." % alternative["id"],
    ]
    return sheet


def _elevation_sheet(study, alternative, number, total):
    """Create a scaled south elevation that makes deck allocation visible."""
    decks = study["decks"]
    width = max(d["width_m"] for d in decks)
    placements = {p["tag"]: p for p in alternative["placements"]}
    equipment = {e["tag"]: e for e in study["equipment"]}
    by_deck = {d["id"]: d for d in decks}
    heights = [d["elevation_m"] + float(d.get("height_limit_m", 5)) for d in decks]
    heights += [equipment_bounds(equipment[t], p, by_deck[p["deck"]])[5] for t, p in placements.items()]
    zmin, zmax = min(d["elevation_m"] for d in decks) - 1, max(heights) + 1
    denominator = _scale(width, zmax - zmin, panel_width=330, panel_height=150)
    factor = 1000.0 / denominator
    sheet = Sheet(alternative["id"] + "-EL", number, total,
                  alternative["id"] + "  south elevation", _project_tb(study), "1:%s" % denominator,
                  "SCREENING - conceptual envelopes and supplied deck levels", min_text=2.5)
    x0, bottom = 49.0, 207.0

    def xy(x, z):
        return x0 + x * factor, bottom - (z - zmin) * factor

    for deck in decks:
        xx, yy = xy(0, deck["elevation_m"])
        sheet.add(K.line(xx, yy, xx + deck["width_m"] * factor, yy, sw=1.1))
        sheet.text(xx - 4, yy - 1, "%s EL %.1f" % (deck["id"], deck["elevation_m"]), size=2.7, anchor="end")
    for tag, p in sorted(placements.items()):
        eq = equipment[tag]
        b = equipment_bounds(eq, p, by_deck[p["deck"]])
        xx, yy = xy(b[0], b[5])
        ww, hh = (b[3] - b[0]) * factor, (b[5] - b[2]) * factor
        sheet.add(K.rect(xx, yy, ww, hh, fill=PALETTE.get(eq.get("kind"), PALETTE["default"]), stroke="#243648", sw=0.4))
        # Projecting all northings creates genuine superposition in elevation.
        # Tags therefore live in a separate schedule rather than overlapping.
    sheet.text(50, 29, "SOUTH ELEVATION   looking toward +N", size=3.5, bold=True)
    sheet.text(50, 37, "Northing is projected out; overlapping boxes in this view are not a clash assessment.", size=2.7)
    sheet.legend = [("line", "#243648", "solid", "Conceptual equipment projection"),
                    ("line", "#243648", "solid", "Supplied deck elevation")]
    sheet.notes = ["Use the deck plans and canonical coordinates for equipment identities and plan locations.",
                   "Structural members, supports, penetrations and vessel/hull geometry are not designed by this view.",
                   "Project units: metres. Heights are preliminary package dimensions.",
                   "Reference alternative: %s." % alternative["id"]]
    return sheet


def build_drawings(study, alternatives, out_dir):
    """Write vector A3 plans, elevation sheets, drawing specification and previews.

    The returned manifest records automatic annotation QA separately from the
    engineering findings in the alternatives.  It never assigns approval.
    """
    root = Path(out_dir) / "drawings"
    (root / "svg").mkdir(parents=True, exist_ok=True)
    (root / "png").mkdir(exist_ok=True)
    total = len(alternatives) * (len(study["decks"]) + 1)
    plans = []
    for alternative in alternatives:
        for deck in study["decks"]:
            plans.append((alternative, deck))
        plans.append((alternative, None))
    specs, sheets, svg_paths, png_paths, files = [], [], [], [], []
    for number, (alternative, deck) in enumerate(plans, 1):
        sh = (_plan_sheet(study, alternative, deck, number, total) if deck else
              _elevation_sheet(study, alternative, number, total))
        sh.tb["scale"] = sh.scale_text
        qa = sh.qa()
        svg_path = root / "svg" / (K.safe_name(sh.code) + ".svg")
        png_path = root / "png" / (K.safe_name(sh.code) + ".png")
        svg_path.write_text(sh.render(), encoding="utf-8")
        specs.append(sh.spec(qa))
        record = {"number": number, "alternative": alternative["id"],
                  "deck": deck["id"] if deck else None, "title": sh.title,
                  "svg": str(svg_path), "png": str(png_path), "qa": qa["status"]}
        sheets.append(record)
        svg_paths.append(str(svg_path))
        png_paths.append(str(png_path))
        files.append({"path": str(svg_path), "format": "svg", "alternative": alternative["id"]})
    warnings = []
    pdf_path = root / "plant_layout_drawings.pdf"
    try:
        K.to_pdf(svg_paths, str(pdf_path), png_paths, warnings, dpi=120)
    except (ImportError, OSError, RuntimeError) as exc:
        warnings.append("Drawing PDF/PNG adapter failed: %s" % exc)
        pdf_path = None
    if pdf_path:
        files.append({"path": str(pdf_path), "format": "pdf"})
    for png in png_paths:
        if Path(png).is_file():
            files.append({"path": png, "format": "png"})
    spec_path = root / "drawing_spec.json"
    spec_path.write_text(json.dumps({"schema_version": "plant_layout_drawings.v1",
                                     "units": "paper mm and project m", "sheets": specs}, indent=2), encoding="utf-8")
    files.append({"path": str(spec_path), "format": "json"})
    return {"sheets": sheets, "pdf": str(pdf_path) if pdf_path else None,
            "spec": str(spec_path), "files": files, "warnings": warnings,
            "qa_status": "PASS" if all(s["qa"] == "PASS" for s in sheets) else "FAIL"}
