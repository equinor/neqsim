"""Independent 80-digit series inversion of the fixed 162-section holdup matrix.

Run with Python 3.11+ and a modular JDK. Only the standard library is required.
Relative area and moment acceptance is 5e-13, including every positive trace;
exactly absent phases must have exactly zero area and moment. No assertions own gates.
"""
import csv
from decimal import Decimal as Q, localcontext
import hashlib
import json
import math
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent
REPO = next(path for path in ROOT.parents if (path / "pom.xml").is_file())
SOURCE = REPO / "src/main/java/neqsim/process/equipment/pipeline/twophasepipe/closure/GeometryCalculator.java"
FIELDS = ["diameter_m", "gas_holdup", "liquid_holdup", "liquid_m3", "gas_m3", "liquid_m2", "gas_m2"]
DIAMETERS = [0.001, 0.01, 0.2, 1.0148, 3.0, 10.0]
MINORITIES = [1e-30, 1e-24, 1e-20, 1e-16, 1e-14, 1e-12, 1e-10, 1e-8, 1e-6, 0.01, 0.1, 0.25]
MATRIX = [(d, 1.0, 0.0) for d in DIAMETERS]
MATRIX += [(d, 0.0, 1.0) for d in DIAMETERS]
MATRIX += [(d, 0.5, 0.5) for d in DIAMETERS]
MATRIX += [(d, gas, liquid) for d in DIAMETERS for a in MINORITIES
           for gas, liquid in [(1.0 - a, a), (a, 1.0 - a)]]


def segment(depth, terms):
    """Analytically integrate the binomial expansion of 2 sqrt(y (1-y))."""
    coefficient = Q(1)
    power = Q(1)
    area_sum = Q(0)
    moment_sum = Q(0)
    for n in range(terms):
        exponent = Q(n) + Q("1.5")
        area_sum += coefficient * power / exponent
        moment_sum += coefficient * power / (exponent * (exponent + 1))
        coefficient *= (Q(n) - Q("0.5")) / Q(n + 1)
        power *= depth
    scale = 2 * depth * depth.sqrt()
    return scale * area_sum, scale * depth * moment_sum


def arctangent(value):
    """Independent pi construction using Machin's rapidly converging rational series."""
    return sum(((-1) ** n * value ** (2 * n + 1) / Q(2 * n + 1) for n in range(140)), Q(0))


def reference(diameter, gas, liquid, pi):
    """Invert the smaller unit-circle segment with high-precision analytic derivatives."""
    whole_area = pi / 4
    if min(gas, liquid) == 0:
        unit = (Q(0), -whole_area / 2, Q(0), whole_area) if liquid == 0 else (
            whole_area / 2, Q(0), whole_area, Q(0))
        refinement = Q(0)
    else:
        target = min(gas, liquid) * whole_area
        depth = (3 * pi * min(gas, liquid) / 16) ** (Q(2) / 3)
        for _ in range(40):
            area, moment = segment(depth, 180)
            error = area - target
            if abs(error / target) < Q("1e-65"):
                break
            depth -= error / (2 * (depth * (1 - depth)).sqrt())
        else:
            raise ValueError("Independent series inversion failed")
        coarse_area, coarse_moment = segment(depth, 120)
        refinement = max(abs(area - coarse_area), abs(moment - coarse_moment))
        other_moment = (depth - Q("0.5")) * whole_area - moment
        unit = (moment, other_moment, area, whole_area - area) if liquid <= gas else (
            -other_moment, -moment, whole_area - area, area)
    return [value * diameter ** (3 if i < 2 else 2) for i, value in enumerate(unit)], refinement


def write_csv(path, rows):
    """Save complete, ordered numerical evidence."""
    with path.open("w", newline="") as output:
        writer = csv.writer(output, lineterminator="\n")
        writer.writerow(FIELDS)
        writer.writerows(rows)


def main():
    """Compile the current source, audit every fixed identity, and retain all outputs."""
    if len(MATRIX) != 162 or len(set(MATRIX)) != 162:
        raise ValueError("Fixed input matrix is incomplete")
    inputs = "".join(",".join(repr(value) for value in row) + "\n" for row in MATRIX)
    with tempfile.TemporaryDirectory() as temporary:
        subprocess.run(["java", "--module", "jdk.compiler/com.sun.tools.javac.Main", "--release", "8",
                        "-d", temporary, str(SOURCE), str(ROOT / "HoldupPressureMomentProbe.java")], check=True)
        subprocess.run(["java", "-cp", temporary, "HoldupPressureMomentProbe", str(ROOT / "holdup-native.csv")],
                       input=inputs, text=True, check=True)
    with (ROOT / "holdup-native.csv").open(newline="") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != FIELDS:
            raise ValueError("Native schema mismatch")
        rows = list(reader)
    if len(rows) != len(MATRIX):
        raise ValueError("Native matrix is incomplete or contains extra rows")
    maximum_error = Q(0)
    maximum_refinement = Q(0)
    references = []
    with localcontext() as context:
        context.prec = 80
        pi = 16 * arctangent(Q(1) / 5) - 4 * arctangent(Q(1) / 239)
        for identity, row in zip(MATRIX, rows):
            if set(row) != set(FIELDS) or any(row[key] is None for key in FIELDS):
                raise ValueError("Malformed native row")
            values = [float(row[key]) for key in FIELDS]
            if not all(math.isfinite(value) for value in values) or tuple(values[:3]) != identity:
                raise ValueError("Nonfinite or reordered native identity")
            expected, refinement = reference(*(Q.from_float(value) for value in identity), pi)
            maximum_refinement = max(maximum_refinement, refinement)
            for actual, value in zip(values[3:], expected):
                error = abs(Q.from_float(actual) - value)
                if (value == 0 and actual != 0) or (value != 0 and error / abs(value) > Q("5e-13")):
                    raise ValueError("Relative trace/area/moment acceptance failed: " + str(identity))
                if value:
                    maximum_error = max(maximum_error, error / abs(value))
            references.append([*identity, *expected])
    write_csv(ROOT / "holdup-reference.csv", references)
    raw = SOURCE.read_bytes()
    summary = {"source_parent_sha": "3dbfef974a2e8d50ce59f44030b2c5dad0f7ad4b",
               "geometry_blob": hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest(),
               "matrix_rows": len(rows), "diameters_m": DIAMETERS, "minimum_positive_holdup": min(MINORITIES),
               "decimal_digits": 80, "series_terms": [120, 180], "relative_gate": "5e-13",
               "maximum_relative_error": str(maximum_error),
               "maximum_unit_series_refinement": str(maximum_refinement)}
    (ROOT / "holdup-summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
