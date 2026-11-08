"""Replay the retained independent SRK equations without importing NeqSim.

Run with Python 3 from any directory. No fitting, reverse-data relabeling or
source-input repair is performed. Report paths are explicit; inputs are read-only.
"""
import argparse
import csv
from decimal import Decimal, InvalidOperation
import json
import math
from pathlib import Path

import binary_solver as solver

ROOT = Path(__file__).resolve().parent
HEADER = ("T_K", "P_bara", "observed_x_CH4", "compiler_U95_x_CH4",
          "independent_x_CH4_MC", "independent_y_MEG_MC",
          "independent_x_CH4_Soave", "independent_y_MEG_Soave")
# Retained r48 observation coordinates/compiler bands, not new measurements or author uncertainty.
OBSERVATIONS = (("273.25", "99.8", ".0116", ".00116"),
                ("283.29", "50", ".00595", ".000595"),
                ("298.25", "100.2", ".01149", ".001149"),
                ("298.33", "50.2", ".00595", ".000595"),
                ("323.35", "50.2", ".00606", ".000606"),
                ("323.45", "100.8", ".01146", ".001146"))


def read_references(path):
    """Reject ambiguous schema, nonfinite numbers or changes to retained observations."""
    with path.open(newline="", encoding="utf-8") as stream:
        rows = list(csv.reader(stream))
    if not rows or tuple(rows[0]) != HEADER:
        raise ValueError("Reference header must preserve units, directions and alpha controls")
    if len(rows) != len(OBSERVATIONS) + 1:
        raise ValueError("Expected all six retained observations")
    result = []
    for number, (row, observation) in enumerate(zip(rows[1:], OBSERVATIONS), 1):
        if len(row) != len(HEADER):
            raise ValueError("Reference row %d must have eight fields" % number)
        try:
            values = [Decimal(value) for value in row]
        except InvalidOperation as error:
            raise ValueError("Reference row %d contains an invalid decimal" % number) from error
        if not all(value.is_finite() for value in values):
            raise ValueError("Reference row %d contains a nonfinite number" % number)
        if values[:4] != [Decimal(value) for value in observation]:
            raise ValueError("Retained observation coordinates, value or compiler uncertainty changed")
        if not all(Decimal(0) < value < Decimal(1) for value in values[2:]):
            raise ValueError("Reference mole fractions and uncertainty must lie strictly between zero and one")
        result.append([float(value) for value in values])
    return result


def main():
    """Require both independent endpoint parity and finite-difference refinement."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if ROOT.resolve() in args.output.resolve().parents:
        raise ValueError("Write reports outside the read-only fixture")
    rows = read_references(ROOT / "declared-input-reference.csv")
    solver.EOS = "SRK"
    solver.PURE = [("720", "82", ".5347"), ("190.56", "45.99", ".0115")]
    solver.TUPLE = ["181", "0", "2274", "0", ".07"]
    results = []
    for values in rows:
        for mc, offset in ((True, 4), (False, 6)):
            solver.MC = [("1.1121", ".9679", "-1.42") if mc else None, None]
            coarse, _, _ = solver.solve(values[0], values[1], Decimal("1e-10"))
            fine, residual, iterations = solver.solve(values[0], values[1], Decimal("5e-11"))
            if not math.isfinite(residual) or residual < 0 or residual >= 1e-10:
                raise ValueError("Independent log-fugacity residual is not qualified")
            for predicted, expected, previous in ((fine[1], values[offset], coarse[1]),
                                                   (fine[0], values[offset + 1], coarse[0])):
                if not all(math.isfinite(value) and 0 < value < 1 for value in (predicted, expected, previous)):
                    raise ValueError("Nonfinite or out-of-range independent endpoint")
                if abs(predicted / expected - 1) > 1e-10 or abs(previous / predicted - 1) > 1e-10:
                    raise ValueError("Independent prediction or step-refinement mismatch")
            results.append(dict(MC=mc, T_K=values[0], P_bara=values[1],
                                x_methane=fine[1], predicted_y_MEG_unmeasured=fine[0],
                                log_fugacity_residual=residual, iterations=iterations))
    args.output.write_text(json.dumps(results, indent=2, allow_nan=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
