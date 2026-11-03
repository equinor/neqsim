"""Independent static circular-pipe reference; no NeqSim imports or time integration.

Requires NumPy and SciPy. Adaptive depth quadrature and Brent inversion differ
from the production angular Gauss rule and bisection. Outputs are deterministic
within the frozen tolerances, rather than guaranteed byte-identical across SciPy.
"""

import csv
import math
from pathlib import Path

import numpy as np
from scipy.integrate import quad
from scipy.optimize import brentq


ROOT = Path(__file__).resolve().parent
LENGTH = 5000.0
DIAMETER = 1.0148
RADIUS = DIAMETER / 2.0
FULL_AREA = math.pi * RADIUS**2
# Exported 600 s liquid inventory: a counterfactual static redistribution only.
VOLUME = 92.5481689873561


def terrain(position):
    """Original twelve-cell physical terrain with its documented final shelf."""
    fraction = np.asarray(position) / LENGTH
    elevation = (
        -300.0
        - 55.0 * np.sin(math.pi * fraction)**2
        - 12.0 * np.sin(3.0 * math.pi * fraction)**2
    )
    return np.where(fraction <= 0.75, elevation, -333.5)


def metric(depth, kind):
    """Circular segment area, chord width or wetted cylindrical perimeter."""
    height = min(DIAMETER, max(0.0, depth))
    angle = math.acos((RADIUS - height) / RADIUS)
    chord_half = math.sqrt(max(0.0, height * (DIAMETER - height)))
    if kind == 0:
        return RADIUS**2 * angle - (RADIUS - height) * chord_half
    if kind == 1:
        return 2.0 * chord_half
    return DIAMETER * angle


def average(start_depth, end_depth, kind):
    """Integrate over normal depth, splitting dry/partial/full regions."""
    if start_depth == end_depth:
        return metric(start_depth, kind)
    low, high = sorted((start_depth, end_depth))
    cuts = sorted({low, high} | {v for v in (0.0, DIAMETER) if low < v < high})
    integral = sum(
        quad(lambda h: metric(h, kind), a, b, epsabs=1e-12, epsrel=1e-12)[0]
        for a, b in zip(cuts[:-1], cuts[1:])
    )
    return integral / (high - low)


def reconstruct(cells):
    """Determine one connected horizontal plane for the declared volume."""
    cell_length = LENGTH / cells
    elevations = terrain(np.linspace(0.0, LENGTH, cells + 1))
    angles = np.arcsin(np.diff(elevations) / cell_length)
    cosines = np.cos(angles)

    def depths(surface):
        start = RADIUS + (surface - elevations[:-1]) / cosines
        end = RADIUS + (surface - elevations[1:]) / cosines
        return start, end

    def residual(surface):
        start, end = depths(surface)
        return sum(average(a, b, 0) for a, b in zip(start, end)) * cell_length - VOLUME

    surface = brentq(residual, -367.0 - RADIUS, -347.0, xtol=1e-12)
    start, end = depths(surface)
    rows = []
    for index in range(cells):
        a, b = start[index], end[index]
        depth_start = min(DIAMETER, max(0.0, a))
        depth_end = min(DIAMETER, max(0.0, b))
        rows.append({
            "cells": cells,
            "index": index,
            "holdup": average(a, b, 0) / FULL_AREA,
            "diameter": DIAMETER,
            "length": cell_length,
            "angle": float(angles[index]),
            "start_depth": depth_start,
            "end_depth": depth_end,
            "interface_area": average(a, b, 1) * cell_length / cosines[index],
            "wall_area": average(a, b, 2) * cell_length,
            "minimum_gas_area": metric(DIAMETER - max(depth_start, depth_end), 0),
        })
    return rows


def main():
    """Write all 3060 independent cell references on eight prescribed meshes."""
    rows = []
    for cells in (12, 24, 48, 96, 192, 384, 768, 1536):
        rows.extend(reconstruct(cells))
    with (ROOT / "reference.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]), lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {len(rows)} independent static cell references")


if __name__ == "__main__":
    main()
