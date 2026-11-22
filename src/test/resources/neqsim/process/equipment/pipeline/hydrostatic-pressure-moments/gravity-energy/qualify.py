"""Independent retained 80-digit circular-segment energy replay; Python3.11+, modular JDK.

No solver state is modified. Exact-zero controls are exact; every nonzero area moment and energy
must agree within 5e-13 relative, including trace phases. Assertions never own a numerical gate.
"""
import csv
import argparse
from decimal import Decimal as Q, localcontext
import hashlib
import importlib.util
from fractions import Fraction
import json
import math
from pathlib import Path
import statistics
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent
REPO = next(p for p in ROOT.parents if (p / "pom.xml").is_file())
SOURCE = REPO / "src/main/java/neqsim/process/equipment/pipeline/twophasepipe/closure/GeometryCalculator.java"
FIELDS = ["diameter_m", "gas_holdup", "liquid_holdup", "liquid_first_m3", "gas_first_m3", "energy_J_m"]
TIMING_FIELDS = ["round", "queries", "ns_per_query", "checksum"]
BUDGET_FIELDS = ["initial_J", "redistributed_J", "available_potential_drop_J"]
RELATIVE_GATE = Q("5e-13")


def read_numeric_csv(path, fields, count):
    """Require the entire ordered rectangular finite receipt, including every extra field."""
    with path.open(newline="") as source:
        rows = list(csv.reader(source))
    if not rows or rows[0] != fields or len(rows) != count + 1:
        raise ValueError("Incomplete or invalid receipt schema: " + path.name)
    values = []
    for row in rows[1:]:
        if len(row) != len(fields):
            raise ValueError("Malformed receipt row: " + path.name)
        value = [float(field) for field in row]
        if not all(math.isfinite(field) for field in value):
            raise ValueError("Nonfinite receipt: " + path.name)
        values.append(value)
    return values


def relative_error(actual, reference):
    """Preserve exact-zero and frozen relative engineering acceptance under Python -O."""
    error = abs(Q.from_float(actual) - reference)
    if reference == 0:
        if actual != 0:
            raise ValueError("Exact-zero energy reference gate failed")
        return Q(0)
    error /= abs(reference)
    if error > RELATIVE_GATE:
        raise ValueError("Gravity energy reference gate failed")
    return error


def audit_native(path, references):
    """Validate all 162 identities and numerical controls, returning the maximum error."""
    native = read_numeric_csv(path, FIELDS, 162)
    maximum = Q(0)
    for expected, row in zip(references, native):
        if tuple(row[:3]) != tuple(map(float, expected[:3])):
            raise ValueError("Reordered native identity")
        for actual, reference in zip(row[3:], expected[3:]):
            maximum = max(maximum, relative_error(actual, Q(reference)))
    return maximum, native


def audit_timing(path, native):
    """Audit seven fixed-work samples without imposing a machine-dependent speed gate.

    Each loop restarts the cyclic index at zero, while retaining the checksum. Compare to an exact
    rational sum with the standard sequential-summation bound gamma_n * sum(abs(x_i)),
    gamma_n = n*u/(1-n*u), u=2**-53. This bounds accumulation roundoff, not model error.
    """
    rows = read_numeric_csv(path, TIMING_FIELDS, 7)
    energies = [Fraction.from_float(row[5]) for row in native]
    if any(value > 0 for value in energies):
        raise ValueError("Unexpected positive energy in fixed checksum matrix")
    cycle = sum(energies, Fraction(0))
    warm_cycles, warm_tail = divmod(100000, len(energies))
    sample_cycles, sample_tail = divmod(200000, len(energies))
    warm_sum = warm_cycles * cycle + sum(energies[:warm_tail], Fraction(0))
    sample_sum = sample_cycles * cycle + sum(energies[:sample_tail], Fraction(0))
    timings = []
    for index, (round_number, queries, elapsed, checksum) in enumerate(rows):
        if round_number != index or queries != 200000 or elapsed <= 0:
            raise ValueError("Invalid timing identity, work count or elapsed time")
        count = 100000 + (index + 1) * 200000
        expected = warm_sum + (index + 1) * sample_sum
        nu = Fraction(count, 2 ** 53)
        bound = nu / (1 - nu) * abs(expected)
        if abs(Fraction.from_float(checksum) - expected) > bound:
            raise ValueError("Timing checksum violates sequential-summation bound")
        timings.append(elapsed)
    return timings


def budget_reference():
    """Reuse the pinned independent series for the two fixed equal-inventory states."""
    path = ROOT.parent / "coupling/qualify_holdup.py"
    raw = path.read_bytes()
    blob = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
    if blob != "a162547e4ea7cf346c1c02a619f216f155ea6861":
        raise ValueError("Independent series source identity changed")
    spec = importlib.util.spec_from_file_location("independent_holdup_series", path)
    series = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(series)
    with localcontext() as context:
        context.prec = 80
        pi = 16 * series.arctangent(Q(1) / 5) - 4 * series.arctangent(Q(1) / 239)
        energies = []
        for gas, liquid, length in [(0.4, 0.6, 10), (0.7, 0.3, 20)]:
            (jl, jg, al, ag), _ = series.reference(
                Q.from_float(1.0148), Q.from_float(gas), Q.from_float(liquid), pi)
            first = (jl + jg) / (al + ag) * al - jl
            energies.append(length * Q(790) * Q("9.81") * first)
        return [*energies, energies[0] - energies[1]]


def audit_budget(path, expected):
    """Check all three static energies against independently inverted segment integrals."""
    actual = read_numeric_csv(path, BUDGET_FIELDS, 1)[0]
    return max(relative_error(value, reference) for value, reference in zip(actual, expected))


def main(output=None):
    """Construct independent energy references, compile native query and audit the complete fixed matrix."""
    output = ROOT if output is None else Path(output)
    output.mkdir(parents=True, exist_ok=True)
    # A rejected replay cannot leave an earlier successful receipt for the same output directory.
    (output / "summary.json").unlink(missing_ok=True)
    reference_bytes = (ROOT.parent / "coupling/holdup-reference.csv").read_bytes()
    reference_blob = hashlib.sha1(b"blob " + str(len(reference_bytes)).encode() + b"\0" + reference_bytes).hexdigest()
    if reference_blob != "9fdc393a5ef7511c7ee28a2d328f88e7d8efe88c":
        raise ValueError("Independent source matrix identity changed")
    original = list(csv.DictReader(reference_bytes.decode("utf-8").splitlines()))
    if len(original) != 162:
        raise ValueError("Incomplete independent source matrix")
    references = []
    with localcontext() as context:
        context.prec = 80
        for row in original:
            jl, jg, al, ag = [Q(row[key]) for key in ["liquid_m3", "gas_m3", "liquid_m2", "gas_m2"]]
            offset = (jl + jg) / (al + ag)
            liquid_first = offset * al - jl if al and ag else Q(0)
            references.append([row[key] for key in FIELDS[:3]] +
                              [str(liquid_first), str(-liquid_first), str(Q(790) * Q("9.81") * liquid_first)])
    with (output / "reference.csv").open("w", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(FIELDS)
        writer.writerows(references)
    identities = [tuple(map(float, row[:3])) for row in references]
    if len(set(identities)) != 162:
        raise ValueError("Repeated independent identity")
    inputs = "".join(",".join(row[:3]) + "\n" for row in references)
    with tempfile.TemporaryDirectory() as temporary:
        subprocess.run(["java", "--module", "jdk.compiler/com.sun.tools.javac.Main", "--release", "8",
                        "-d", temporary, str(SOURCE), str(ROOT / "GravityEnergyProbe.java")], check=True)
        subprocess.run(["java", "-cp", temporary, "GravityEnergyProbe", str(output / "native.csv"),
                        str(output / "timing.csv"), str(output / "two-cell-budget.csv")], input=inputs, text=True, check=True)
    maximum, native = audit_native(output / "native.csv", references)
    timings = audit_timing(output / "timing.csv", native)
    budget = budget_reference()
    budget_error = audit_budget(output / "two-cell-budget.csv", budget)
    with (output / "budget-reference.csv").open("w", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(BUDGET_FIELDS)
        writer.writerow(budget)
    raw = SOURCE.read_bytes()
    summary = {"source_base": "98a9e7509391dc0b713e6ea985dc3dc236299f6a",
               "source_blob": hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest(),
               "rows": 162, "reference_digits": 80, "relative_gate": "5e-13",
               "maximum_relative_error": str(maximum), "query_ns_min_median_max":
               [min(timings), statistics.median(timings), max(timings)],
               "timing_rows": 7, "queries_per_sample": 200000,
               "budget_maximum_relative_error": str(budget_error),
               "scope": "Explicit cross-sectional energy query only; no dynamic integration or evaporation claim"}
    receipt = json.dumps(summary, indent=2, allow_nan=False) + "\n"
    (output / "summary.json").write_text(receipt)
    print(receipt, end="")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, help="Separate replay output directory; defaults to retained evidence")
    main(parser.parse_args().output_dir)
