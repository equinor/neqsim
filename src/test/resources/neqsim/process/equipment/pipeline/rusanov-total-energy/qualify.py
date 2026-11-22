"""Independent ideal-gas Euler-variable controls for the existing optional Rusanov flux.

Standard library, Python3.11+, modular JDK. Every retained row and finite field is checked;
the frozen normalized flux gate is3e-14, including positive trace pairs. No assertion owns a gate.
"""
import argparse
import csv
from decimal import Decimal as Q, localcontext
import hashlib
import itertools
import json
import math
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent
REPO = next(path for path in ROOT.parents if (path / "pom.xml").is_file())
SOURCE = REPO / "src/main/java/neqsim/process/equipment/pipeline/twophasepipe/numerics/AUSMPlusFluxCalculator.java"
FIELDS = ["id", "mass_kg_s", "momentum_N", "energy_W", "holdup_flux_m_s", "interface_holdup", "interface_pressure_Pa"]
GATE = Q("3e-14")


def state(rho, u, p, alpha):
    """Specify a gamma1.4 ideal gas with total specific enthalpy."""
    return [rho, u, p, math.sqrt(1.4 * p / rho), 3.5 * p / rho + 0.5 * u * u, alpha]


def matrix():
    """Fixed density, pressure, direction, area and trace controls; no tuned physical targets."""
    rows = []
    for area, rl, rr, pl, pr, ul, ur, alpha in itertools.product(
            [0.01, 1.0148], [1.0, 800.0], [1.0, 800.0], [1e5, 15e6], [1e5, 15e6],
            [-5.0, 3.0], [-2.0, 7.0], [1e-30, 0.25, 1.0]):
        rows.append(state(rl, ul, pl, alpha) + state(rr, ur, pr, 1 - 0.5 * alpha) + [area])
    for alpha, area, ul, ur in itertools.product([1e-30, 1e-16, 1e-8], [0.01, 1.0148], [-5.0, 3.0], [-2.0, 7.0]):
        rows.append(state(1.0, ul, 1e5, alpha) + state(800.0, ur, 15e6, alpha) + [area])
    if len(rows) != 408:
        raise ValueError("Fixed input matrix changed")
    return rows


def reference(row):
    """Construct Euler U and F directly from gamma, pressure and velocity at80digits."""
    with localcontext() as context:
        context.prec = 80
        x = [Q.from_float(v) for v in row]
        rho_l, u_l, p_l, c_l, _, a_l, rho_r, u_r, p_r, c_r, _, a_r, area = x
        speed = max(abs(u_l) + c_l, abs(u_r) + c_r)
        left = [a_l * rho_l, a_l * rho_l * u_l, a_l * (p_l / Q("0.4") + rho_l * u_l * u_l / 2)]
        right = [a_r * rho_r, a_r * rho_r * u_r, a_r * (p_r / Q("0.4") + rho_r * u_r * u_r / 2)]
        fl = [left[1], left[1] * u_l + a_l * p_l, u_l * (left[2] + a_l * p_l)]
        fr = [right[1], right[1] * u_r + a_r * p_r, u_r * (right[2] + a_r * p_r)]
        expected = [area * ((fl[i] + fr[i]) / 2 - speed * (right[i] - left[i]) / 2) for i in range(3)]
        scale = [area * (abs(fl[i]) + abs(fr[i]) + speed * (abs(left[i]) + abs(right[i]))) for i in range(3)]
        holdup = (a_l + a_r) / 2
        expected += [(a_l * u_l + a_r * u_r - speed * (a_r - a_l)) / 2,
                     holdup, (a_l * p_l + a_r * p_r) / (a_l + a_r)]
        scale += [abs(a_l * u_l) + abs(a_r * u_r) + speed * (a_l + a_r), holdup, abs(expected[-1])]
        return expected, scale


def write_csv(path, fields, rows):
    """Retain complete ordered evidence."""
    with path.open("w", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(fields)
        writer.writerows(rows)


def main(source, output):
    """Compile one explicit source and reject any incomplete, nonfinite or inaccurate receipt."""
    output.mkdir(parents=True, exist_ok=True)
    (output / "summary.json").unlink(missing_ok=True)
    rows = matrix()
    inputs = "".join(str(i) + "," + ",".join(map(repr, row)) + "\n" for i, row in enumerate(rows))
    (output / "inputs.csv").write_text(inputs)
    with tempfile.TemporaryDirectory() as temporary:
        subprocess.run(["java", "--module", "jdk.compiler/com.sun.tools.javac.Main", "--release", "8",
                        "-d", temporary, str(source), str(ROOT / "RusanovProbe.java")], check=True)
        subprocess.run(["java", "-cp", temporary, "RusanovProbe", str(output / "native.csv"),
                        str(output / "ausm-control.csv")], input=inputs, text=True, check=True)
    with (output / "native.csv").open(newline="") as stream:
        native = list(csv.reader(stream))
    if len(native) != 409 or native[0] != FIELDS:
        raise ValueError("Incomplete or invalid native schema")
    maximum = Q(0)
    references = []
    for index, (row, actual) in enumerate(zip(rows, native[1:])):
        if len(actual) != len(FIELDS) or actual[0] != str(index):
            raise ValueError("Malformed or reordered native identity")
        values = [float(v) for v in actual[1:]]
        if not all(math.isfinite(v) for v in values):
            raise ValueError("Nonfinite native flux")
        expected, scale = reference(row)
        references.append([index, *expected])
        for value, target, normalization in zip(values, expected, scale):
            error = abs(Q.from_float(value) - target)
            if (normalization == 0 and error != 0) or (normalization != 0 and error / normalization > GATE):
                raise ValueError("Conservative flux gate failed: row " + str(index))
            if normalization:
                maximum = max(maximum, error / normalization)
    write_csv(output / "reference.csv", FIELDS, references)
    raw = source.read_bytes()
    summary = {"base": "5d0edbdf6f9e75a89f8f7337b4769d8cb24b2694", "source_blob":
               hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest(),
               "rows": 408, "decimal_digits": 80, "normalized_gate": str(GATE),
               "maximum_normalized_error": str(maximum), "scope":
               "Optional local Rusanov flux only; no solver selection, component routing or pool qualification"}
    receipt = json.dumps(summary, indent=2, allow_nan=False) + "\n"
    (output / "summary.json").write_text(receipt)
    print(receipt, end="")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=SOURCE)
    parser.add_argument("--output-dir", type=Path, default=ROOT)
    args = parser.parse_args()
    main(args.source, args.output_dir)
