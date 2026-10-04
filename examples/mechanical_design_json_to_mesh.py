"""Convert NeqSim design-data JSON to a preliminary shell or plot-space mesh.

Run the process and mechanical design first, then save design.toDesignDataJson().
This example uses metres throughout. An STL has no unit metadata, so a JSON sidecar
retains the units, source snapshot, representation and independent volume check.
No heads, nozzles, internals, welds or supports are inferred.

Example:
    python examples/mechanical_design_json_to_mesh.py design.json shell.stl
    python examples/mechanical_design_json_to_mesh.py compressor.json skid.glb --mode envelope
"""

import argparse
import json
import math
from pathlib import Path

import trimesh


def quantity(data, section, name, unit):
    """Read an available positive finite quantity in the declared unit."""
    entry = data[section][name]
    value = entry.get("value")
    if entry.get("status") != "available" or entry.get("unit") != unit:
        raise ValueError(f"{section}.{name} is unavailable or is not in {unit}")
    if isinstance(value, bool) or not isinstance(value, (float, int)):
        raise ValueError(f"{section}.{name} must be numeric")
    if not math.isfinite(value) or value <= 0:
        raise ValueError(f"{section}.{name} must be finite and positive")
    return value


def build_mesh(document, mode="shell", sections=256):
    """Build a mesh and independently check its dimensions and enclosed solid volume."""
    data = document.get("designData", document)
    if data.get("schemaVersion") != "1.0":
        raise ValueError("Expected NeqSim design-data schema 1.0")
    if sections < 32:
        raise ValueError("Use at least 32 circumferential sections")
    if mode == "shell":
        kind = data.get("geometryKind")
        if kind not in ("cylindrical_shell", "straight_pipe_section"):
            raise ValueError("This equipment has no qualified cylindrical shell; use envelope mode")
        if data.get("geometryConsistency") != "consistent":
            raise ValueError("Shell diameters and thickness are incomplete or inconsistent")
        inner = quantity(data, "geometry", "innerDiameter", "m")
        outer = quantity(data, "geometry", "outerDiameter", "m")
        thickness = quantity(data, "geometry", "wallThickness", "m")
        length_key = "length" if kind == "straight_pipe_section" else "tangentLength"
        length = quantity(data, "geometry", length_key, "m")
        if outer <= inner or not math.isclose(outer - inner, 2 * thickness, rel_tol=1e-7):
            raise ValueError("Outer diameter must equal inner diameter plus twice the wall thickness")
        mesh = trimesh.creation.annulus(
            r_min=inner / 2,
            r_max=outer / 2,
            height=length,
            sections=sections,
        )
        orientation = data.get("orientation")
        if kind == "cylindrical_shell" and orientation not in ("horizontal", "vertical"):
            raise ValueError("Specify a horizontal or vertical vessel orientation")
        if orientation == "horizontal":
            mesh.apply_transform(trimesh.transformations.rotation_matrix(math.pi / 2, [0, 1, 0]))
        analytic_volume = math.pi * (outer**2 - inner**2) * length / 4
        representation = "open-ended shell wall only; annular ends close the wall solid, not the vessel bore"
    elif mode == "envelope":
        length = quantity(data, "geometry", "moduleLength", "m")
        width = quantity(data, "geometry", "moduleWidth", "m")
        height = quantity(data, "geometry", "moduleHeight", "m")
        mesh = trimesh.creation.box(extents=[length, width, height])
        analytic_volume = length * width * height
        representation = "plot-space box including access allowances; not equipment metal or pressure boundary"
    else:
        raise ValueError("Mode must be shell or envelope")
    relative_error = abs(mesh.volume - analytic_volume) / analytic_volume
    if not mesh.is_watertight or mesh.volume <= 0 or relative_error > 0.007:
        raise ValueError("Mesh failed topology or independent volume verification")
    report = {
        "lengthUnit": "m",
        "representation": representation,
        "analyticVolume_m3": analytic_volume,
        "meshVolume_m3": float(mesh.volume),
        "relativeVolumeError": float(relative_error),
        "bounds_m": mesh.bounds.tolist(),
        "watertightSolid": bool(mesh.is_watertight),
        "fabricationReady": False,
        "sourceDesignData": data,
    }
    return mesh, report


def main():
    """Write the mesh together with its unit and provenance sidecar."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--mode", choices=("shell", "envelope"), default="shell")
    args = parser.parse_args()
    if args.output.suffix.lower() not in (".stl", ".glb"):
        parser.error("Output must end in .stl or .glb")
    document = json.loads(args.input.read_text(encoding="utf-8"))
    mesh, report = build_mesh(document, mode=args.mode)
    mesh.export(args.output)
    sidecar = args.output.with_suffix(args.output.suffix + ".json")
    sidecar.write_text(json.dumps(report, indent=2, allow_nan=False), encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "sourceDesignData"}, indent=2))


if __name__ == "__main__":
    main()
