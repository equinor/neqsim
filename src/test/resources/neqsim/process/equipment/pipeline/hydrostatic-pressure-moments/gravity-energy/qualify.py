"""Independent retained 80-digit circular-segment energy replay; Python3.11+, modular JDK.

No solver state is modified. Exact-zero controls are exact; every nonzero area moment and energy
must agree within 5e-13 relative, including trace phases. Assertions never own a numerical gate.
"""
import csv
from decimal import Decimal as Q, localcontext
import hashlib
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


def main():
    """Construct independent energy references, compile native query and audit the complete fixed matrix."""
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
    with (ROOT / "reference.csv").open("w", newline="") as output:
        writer = csv.writer(output, lineterminator="\n")
        writer.writerow(FIELDS)
        writer.writerows(references)
    identities = [tuple(map(float, row[:3])) for row in references]
    if len(set(identities)) != 162:
        raise ValueError("Repeated independent identity")
    inputs = "".join(",".join(row[:3]) + "\n" for row in references)
    with tempfile.TemporaryDirectory() as temporary:
        subprocess.run(["java", "--module", "jdk.compiler/com.sun.tools.javac.Main", "--release", "8",
                        "-d", temporary, str(SOURCE), str(ROOT / "GravityEnergyProbe.java")], check=True)
        subprocess.run(["java", "-cp", temporary, "GravityEnergyProbe", str(ROOT / "native.csv"),
                        str(ROOT / "timing.csv"), str(ROOT / "two-cell-budget.csv")], input=inputs, text=True, check=True)
    with (ROOT / "native.csv").open() as source:
        reader = csv.DictReader(source)
        if reader.fieldnames != FIELDS:
            raise ValueError("Native schema mismatch")
        native = list(reader)
    if len(native) != 162:
        raise ValueError("Incomplete native matrix")
    maximum = Q(0)
    for identity, expected, row in zip(identities, references, native):
        if set(row) != set(FIELDS) or any(row[k] is None for k in FIELDS):
            raise ValueError("Malformed native row")
        values = [float(row[k]) for k in FIELDS]
        if tuple(values[:3]) != identity or not all(math.isfinite(v) for v in values):
            raise ValueError("Nonfinite or reordered output")
        for actual, reference in zip(values[3:], expected[3:]):
            ref = Q(reference)
            error = abs(Q.from_float(actual) - ref)
            if (ref == 0 and actual != 0) or (ref != 0 and error / abs(ref) > Q("5e-13")):
                raise ValueError("Gravity energy reference gate failed: " + str(identity))
            if ref:
                maximum = max(maximum, error / abs(ref))
    with (ROOT / "timing.csv").open() as source:
        timings = [float(row["ns_per_query"]) for row in csv.DictReader(source)]
    raw = SOURCE.read_bytes()
    summary = {"source_base": "98a9e7509391dc0b713e6ea985dc3dc236299f6a",
               "source_blob": hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest(),
               "rows": 162, "reference_digits": 80, "relative_gate": "5e-13",
               "maximum_relative_error": str(maximum), "query_ns_min_median_max":
               [min(timings), statistics.median(timings), max(timings)],
               "scope": "Explicit cross-sectional energy query only; no dynamic integration or evaporation claim"}
    (ROOT / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
