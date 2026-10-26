"""Optional, source-traceable CAD exports for conceptual plant layouts.

The authoritative geometry is the layout dataset in metres.  Equipment is
represented by its physical bounding prism, not by fabricated vendor geometry.
CadQuery and ezdxf are imported only when an export is requested.  STEP uses
millimetres, DXF metres, and GLB uses metres with glTF's right-handed Y-up axes.
Every successful adapter is checked by reopening its exported representation.
"""
from __future__ import annotations

import importlib
import hashlib
import json
import math
import re
import struct
from pathlib import Path

from layout_model import equipment_bounds


def _safe_name(value):
    """Return an identifier safe for a file or CAD component name."""
    original = str(value)
    cleaned = re.sub(r"[^A-Za-z0-9_.-]+", "_", original).strip(".") or "unnamed"
    if cleaned != original:
        cleaned += "_" + hashlib.sha256(original.encode("utf-8")).hexdigest()[:10]
    return cleaned


def _write_json(path, value):
    """Write stable, finite JSON."""
    Path(path).write_text(
        json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + "\n",
        encoding="utf-8",
    )


def _check(check_id, status, message, **evidence):
    """Create one CAD exchange check with explicit scope."""
    return dict(id=check_id, status=status, message=message, evidence=evidence)


def _bounds_values(box):
    """Return a CadQuery bounding box as six numeric values."""
    return [box.xmin, box.ymin, box.zmin, box.xmax, box.ymax, box.zmax]


def _close_bounds(actual, expected, tolerance=1e-5):
    """Compare six-coordinate bounds in metres."""
    return all(abs(float(a) - float(b)) <= tolerance for a, b in zip(actual, expected))


def _scene_items(study, alternative):
    """Build tagged physical geometry, retaining incomplete placement findings."""
    decks = {d["id"]: d for d in study["decks"]}
    equipment = {e["tag"]: e for e in study["equipment"]}
    items = []
    warnings = []
    seen = set()
    for index, placement in enumerate(alternative.get("placements", []), 1):
        tag = placement.get("tag")
        if tag not in equipment or placement.get("deck") not in decks or tag in seen:
            warnings.append("Skipped invalid or duplicate placement for %s" % tag)
            continue
        seen.add(tag)
        item = equipment[tag]
        bounds = equipment_bounds(item, placement, decks[placement["deck"]])
        if not all(math.isfinite(float(v)) for v in bounds):
            warnings.append("Skipped non-finite physical bounds for %s" % tag)
            continue
        if any(bounds[i + 3] <= bounds[i] for i in range(3)):
            warnings.append("Skipped non-positive physical bounds for %s" % tag)
            continue
        items.append({
            "name": "EQ_%03d_%s" % (index, _safe_name(tag)),
            "tag": tag,
            "kind": "equipment",
            "equipment_kind": item.get("kind", "equipment"),
            "deck": placement["deck"],
            "bounds_m": list(bounds),
            "dimension_source": item.get("dimension_source", {}),
        })
    missing = sorted(set(equipment) - seen)
    if missing:
        warnings.append("Equipment without exported physical placement: %s" % ", ".join(missing))
    for index, deck in enumerate(study["decks"], 1):
        z = float(deck["elevation_m"])
        items.append({
            "name": "DECK_%03d_%s" % (index, _safe_name(deck["id"])),
            "tag": deck["id"],
            "kind": "display_deck",
            "deck": deck["id"],
            "bounds_m": [0.0, 0.0, z - 0.1, float(deck["width_m"]), float(deck["depth_m"]), z],
        })
    for index, obstacle in enumerate(study.get("obstacles", []), 1):
        bounds = list(map(float, obstacle["bounds_m"]))
        deck = decks.get(obstacle.get("deck"))
        if deck is None:
            warnings.append("Skipped obstacle with unknown deck: %s" % obstacle.get("id"))
            continue
        # Obstacle bounds are deck-local, just as equipment base elevations are.
        bounds[2] += float(deck["elevation_m"])
        bounds[5] += float(deck["elevation_m"])
        items.append({
            "name": "OBS_%03d_%s" % (index, _safe_name(obstacle["id"])),
            "tag": obstacle["id"],
            "kind": "obstacle",
            "deck": obstacle["deck"],
            "bounds_m": bounds,
        })
    return items, warnings, missing


def _read_glb(path):
    """Read the JSON document and remaining chunks of a binary glTF file."""
    blob = Path(path).read_bytes()
    magic, version, length = struct.unpack_from("<III", blob, 0)
    if magic != 0x46546C67 or version != 2 or length != len(blob):
        raise ValueError("Invalid GLB 2.0 header")
    offset = 12
    document = None
    rest = []
    while offset < len(blob):
        size, kind = struct.unpack_from("<II", blob, offset)
        offset += 8
        payload = blob[offset:offset + size]
        if len(payload) != size:
            raise ValueError("Truncated GLB chunk")
        offset += size
        if kind == 0x4E4F534A:
            document = json.loads(payload)
        else:
            rest.append((kind, payload))
    if document is None:
        raise ValueError("GLB has no JSON chunk")
    return document, rest


def _write_glb(path, document, chunks):
    """Write a GLB after editing only its metadata/scene transforms."""
    encoded = json.dumps(document, separators=(",", ":"), allow_nan=False).encode("utf-8")
    encoded += b" " * ((-len(encoded)) % 4)
    body = struct.pack("<II", len(encoded), 0x4E4F534A) + encoded
    for kind, data in chunks:
        padded = data + b"\0" * ((-len(data)) % 4)
        body += struct.pack("<II", len(padded), kind) + padded
    Path(path).write_bytes(struct.pack("<III", 0x46546C67, 2, len(body) + 12) + body)


def _metre_glb(path, items):
    """Convert CadQuery millimetre coordinates to glTF metres explicitly."""
    document, chunks = _read_glb(path)
    nodes = document.setdefault("nodes", [])
    names = {item["name"]: item for item in items}
    for node in nodes:
        if node.get("name") in names:
            item = names[node["name"]]
            node.setdefault("extras", {}).update({
                "neqsim_tag": item["tag"],
                "neqsim_kind": item["kind"],
                "bounds_project_m": item["bounds_m"],
            })
    for scene in document.get("scenes", []):
        root = len(nodes)
        nodes.append({
            "name": "NEQSIM millimetres to metres",
            "scale": [0.001, 0.001, 0.001],
            "children": scene.get("nodes", []),
        })
        scene["nodes"] = [root]
    document.setdefault("asset", {}).setdefault("extras", {}).update({
        "length_unit": "m",
        "axes": "glTF Y-up; project (x,y,z) maps to (x,z,-y)",
        "representation": "conceptual physical bounding prisms; no vendor detail",
    })
    _write_glb(path, document, chunks)


def _identity():
    """Return a row-major identity matrix."""
    return [[float(i == j) for j in range(4)] for i in range(4)]


def _multiply(a, b):
    """Multiply two row-major 4 by 4 matrices."""
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def _node_matrix(node):
    """Convert a glTF column-major matrix or TRS to row-major form."""
    if "matrix" in node:
        value = node["matrix"]
        return [[float(value[j * 4 + i]) for j in range(4)] for i in range(4)]
    x, y, z, w = node.get("rotation", [0.0, 0.0, 0.0, 1.0])
    rotation = [
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), 0.0],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), 0.0],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y), 0.0],
        [0.0, 0.0, 0.0, 1.0],
    ]
    scale = node.get("scale", [1.0, 1.0, 1.0])
    translation = node.get("translation", [0.0, 0.0, 0.0])
    for i in range(3):
        for j in range(3):
            rotation[i][j] *= float(scale[j])
        rotation[i][3] = float(translation[i])
    return rotation


def _transform_bounds(bounds, matrix):
    """Transform all bounding-box corners, then take their enclosing bounds."""
    points = []
    for x in (bounds[0], bounds[3]):
        for y in (bounds[1], bounds[4]):
            for z in (bounds[2], bounds[5]):
                vector = [x, y, z, 1.0]
                points.append([sum(matrix[i][j] * vector[j] for j in range(4)) for i in range(3)])
    return [min(p[i] for p in points) for i in range(3)] + [max(p[i] for p in points) for i in range(3)]


def _glb_checks(path, items, alternative_id):
    """Independently inspect exported mesh bounds, identities and world transforms."""
    document, _ = _read_glb(path)
    actual = {}
    nodes = document.get("nodes", [])

    def visit(index, parent):
        node = nodes[index]
        world = _multiply(parent, _node_matrix(node))
        if "mesh" in node:
            bounds = []
            for primitive in document["meshes"][node["mesh"]].get("primitives", []):
                accessor = document["accessors"][primitive["attributes"]["POSITION"]]
                bounds.append(_transform_bounds(accessor["min"] + accessor["max"], world))
            if bounds:
                merged = [min(b[i] for b in bounds) for i in range(3)]
                merged += [max(b[i] for b in bounds) for i in range(3, 6)]
                actual[node.get("name")] = (merged, node.get("extras", {}).get("neqsim_tag"))
        for child in node.get("children", []):
            visit(child, world)

    for index in document["scenes"][document.get("scene", 0)].get("nodes", []):
        visit(index, _identity())
    checks = []
    for item in items:
        b = item["bounds_m"]
        expected = [b[0], b[2], -b[4], b[3], b[5], -b[1]]
        observed, tag = actual.get(item["name"], (None, None))
        passed = observed is not None and tag == item["tag"] and _close_bounds(observed, expected)
        checks.append(_check(
            "%s:GLB:%s" % (alternative_id, item["name"]),
            "PASS" if passed else "FAIL",
            "GLB mesh bounds in metres and tag identity after Y-up transform",
            expected_bounds_m=expected, observed_bounds_m=observed, tag=tag,
            tolerance_m=1e-5,
        ))
    return checks


def _cadquery_export(cq, items, directory, alternative_id):
    """Export tagged solids and independently reopen STEP and GLB."""
    assembly = cq.Assembly(name="NEQSIM_%s" % _safe_name(alternative_id))
    colors = {
        "equipment": cq.Color(0.16, 0.37, 0.58),
        "display_deck": cq.Color(0.65, 0.70, 0.74, 0.55),
        "obstacle": cq.Color(0.62, 0.25, 0.20),
    }
    for item in items:
        b = [v * 1000.0 for v in item["bounds_m"]]
        solid = cq.Workplane("XY").box(
            b[3] - b[0], b[4] - b[1], b[5] - b[2], centered=(False, False, False)
        ).translate(tuple(b[:3]))
        assembly.add(solid, name=item["name"], color=colors[item["kind"]])
    step_path = directory / "physical_layout.step"
    glb_path = directory / "physical_layout.glb"
    assembly.export(str(step_path), exportType="STEP")
    assembly.export(str(glb_path), exportType="GLTF")
    _metre_glb(glb_path, items)
    reopened = cq.Assembly.importStep(str(step_path))
    imported = {node.name: node for _, node in reopened.traverse() if node.obj is not None}
    checks = []
    for item in items:
        node = imported.get(item["name"])
        observed = None if node is None else [v / 1000.0 for v in _bounds_values(node.toCompound().BoundingBox())]
        passed = observed is not None and _close_bounds(observed, item["bounds_m"])
        checks.append(_check(
            "%s:STEP:%s" % (alternative_id, item["name"]),
            "PASS" if passed else "FAIL",
            "Reopened STEP component bounds and identity; millimetres converted to metres",
            tag=item["tag"], expected_bounds_m=item["bounds_m"],
            observed_bounds_m=observed, tolerance_m=1e-5,
        ))
    checks.extend(_glb_checks(glb_path, items, alternative_id))
    return [
        dict(path=str(step_path), format="STEP", length_unit="mm", alternative=alternative_id),
        dict(path=str(glb_path), format="GLB", length_unit="m", axes="Y-up", alternative=alternative_id),
    ], checks


def _dxf_export(ezdxf, study, alternative, items, directory):
    """Write one metre-based, layered DXF plan per deck and reopen its geometry."""
    files = []
    checks = []
    alternative_id = alternative["id"]
    for deck in study["decks"]:
        doc = ezdxf.new("R2010")
        doc.units = 6  # INSUNITS = metres.
        doc.appids.add("NEQSIM_LAYOUT")
        for name, color in (("DECK", 7), ("EQUIPMENT", 5), ("OBSTACLES", 1),
                            ("CORRIDORS", 3), ("TAGS", 7)):
            doc.layers.add(name, color=color)
        model = doc.modelspace()
        width, depth = float(deck["width_m"]), float(deck["depth_m"])
        model.add_lwpolyline([(0, 0), (width, 0), (width, depth), (0, depth)], close=True,
                             dxfattribs={"layer": "DECK"})
        expected = {}
        for item in items:
            if item["deck"] != deck["id"] or item["kind"] == "display_deck":
                continue
            b = item["bounds_m"]
            polyline = model.add_lwpolyline(
                [(b[0], b[1]), (b[3], b[1]), (b[3], b[4]), (b[0], b[4])],
                close=True, dxfattribs={"layer": "EQUIPMENT" if item["kind"] == "equipment" else "OBSTACLES"},
            )
            polyline.set_xdata("NEQSIM_LAYOUT", [(1000, item["name"]), (1000, str(item["tag"]))])
            expected[item["name"]] = [b[0], b[1], b[3], b[4]]
            model.add_text(str(item["tag"]), dxfattribs={
                "height": max(0.2, min(width, depth) / 100.0),
                "insert": (b[0] + 0.1, b[1] + 0.1), "layer": "TAGS",
            })
        for corridor in study.get("corridors", []):
            if corridor["deck"] != deck["id"]:
                continue
            b = corridor["bounds_m"]
            model.add_lwpolyline([(b[0], b[1]), (b[2], b[1]), (b[2], b[3]), (b[0], b[3])],
                                 close=True, dxfattribs={"layer": "CORRIDORS"})
        model.add_text("%s | %s | %s | SCREENING | metres" % (
            study["project"]["id"], alternative_id, deck["id"]), dxfattribs={
                "height": max(0.3, min(width, depth) / 70.0), "insert": (0, depth + 1.0), "layer": "TAGS",
            })
        path = directory / ("deck_%s.dxf" % _safe_name(deck["id"]))
        doc.saveas(path)
        reopened = ezdxf.readfile(path)
        observed = {}
        for entity in reopened.modelspace().query("LWPOLYLINE"):
            if not entity.has_xdata("NEQSIM_LAYOUT"):
                continue
            name = entity.get_xdata("NEQSIM_LAYOUT")[0].value
            points = list(entity.get_points("xy"))
            observed[name] = [min(p[0] for p in points), min(p[1] for p in points),
                              max(p[0] for p in points), max(p[1] for p in points)]
        passed = reopened.units == 6 and set(observed) == set(expected)
        passed = passed and all(
            all(abs(a - b) <= 1e-8 for a, b in zip(observed[name], bounds))
            for name, bounds in expected.items()
        )
        checks.append(_check(
            "%s:DXF:%s" % (alternative_id, deck["id"]),
            "PASS" if passed else "FAIL", "Reopened DXF units, tagged identities and plan bounds",
            length_unit="m", components=len(expected), tolerance_m=1e-8,
        ))
        files.append(dict(path=str(path), format="DXF", length_unit="m",
                          alternative=alternative_id, deck=deck["id"]))
    return files, checks


def export_cad(study, alternatives, out_dir):
    """Export optional CAD representations and a finite exchange-loss manifest.

    An unavailable dependency or failed adapter is recorded explicitly.  Each
    alternative also receives a canonical data sidecar and an editable rebuild
    script.  Adapter success says nothing about plant design approval.
    """
    directory = Path(out_dir) / "cad"
    directory.mkdir(parents=True, exist_ok=True)
    manifest = {
        "files": [], "adapters": {}, "warnings": [], "checks": [],
        "representation": "Conceptual equipment bounding prisms and display-only deck slabs",
        "losses": [
            "No vendor shape, nozzle, internals, pipework, structural or marine design is inferred.",
            "Display deck slabs are 0.1 m thick graphical references, not structural thicknesses.",
            "STEP/GLB contain physical geometry; operational/maintenance/hazard envelopes remain in the model and drawings.",
            "Original tag and source provenance are retained in the JSON sidecar; STEP names use a unique safe component mapping.",
            "IFC federation is not implemented by this adapter.",
        ],
    }
    modules = {}
    for name in ("cadquery", "ezdxf"):
        try:
            module = importlib.import_module(name)
            modules[name] = module
            manifest["adapters"][name] = {"status": "AVAILABLE", "version": getattr(module, "__version__", "unknown")}
        except Exception as exc:
            manifest["adapters"][name] = {"status": "UNAVAILABLE", "reason": str(exc)}
            manifest["warnings"].append("Optional %s adapter unavailable; its formats were not generated." % name)
    if not alternatives:
        manifest["warnings"].append("No alternatives supplied; no CAD geometry was generated.")
    for alternative in alternatives:
        alternative_id = alternative["id"]
        target = directory / _safe_name(alternative_id)
        target.mkdir(exist_ok=True)
        items, warnings, missing = _scene_items(study, alternative)
        manifest["warnings"].extend("%s: %s" % (alternative_id, message) for message in warnings)
        manifest["checks"].append(_check(
            "%s:CAD:IDENTITY_COVERAGE" % alternative_id,
            "PASS" if not missing and not warnings else "FAIL",
            "Every equipment tag has one exported physical placement", missing_tags=missing,
        ))
        source = target / "model.json"
        _write_json(source, dict(study=study, alternative=alternative, components=items))
        rebuild = target / "rebuild_cad.py"
        rebuild.write_text(
            '"""Rebuild this conceptual CAD package with the installed NeqSim layout toolkit."""\n'
            'import json\nfrom pathlib import Path\nfrom layout_cad import export_cad\n\n'
            'source = Path(__file__).with_name("model.json")\n'
            'data = json.loads(source.read_text(encoding="utf-8"))\n'
            'export_cad(data["study"], [data["alternative"]], source.parent.parent.parent)\n',
            encoding="utf-8",
        )
        manifest["files"].extend([
            dict(path=str(source), format="JSON", alternative=alternative_id),
            dict(path=str(rebuild), format="PYTHON", alternative=alternative_id),
        ])
        for name, action in (
            ("cadquery", lambda module: _cadquery_export(module, items, target, alternative_id)),
            ("ezdxf", lambda module: _dxf_export(module, study, alternative, items, target)),
        ):
            if name not in modules:
                continue
            try:
                files, checks = action(modules[name])
                manifest["files"].extend(files)
                manifest["checks"].extend(checks)
            except Exception as exc:
                manifest["checks"].append(_check(
                    "%s:%s:EXPORT" % (alternative_id, name), "FAIL", str(exc),
                ))
                manifest["warnings"].append("%s: %s export failed: %s" % (alternative_id, name, exc))
    manifest["status"] = "FAIL" if any(c["status"] == "FAIL" for c in manifest["checks"]) else (
        "INCOMPLETE" if manifest["warnings"] else "PASS"
    )
    path = directory / "exchange_manifest.json"
    _write_json(path, manifest)
    manifest["manifest"] = str(path)
    return manifest
