"""Replay the retained independent SRK equations without importing NeqSim.

Run with Python 3 from any directory. No fitting, reverse-data relabeling or
source-input repair is performed. Report paths are explicit; inputs are read-only.
"""
import argparse
import csv
from decimal import Decimal
import json
from pathlib import Path

import binary_solver as solver

ROOT = Path(__file__).resolve().parent


def main():
    """Require both independent endpoint parity and finite-difference refinement."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    solver.EOS = "SRK"
    solver.PURE = [("720", "82", ".5347"), ("190.56", "45.99", ".0115")]
    solver.TUPLE = ["181", "0", "2274", "0", ".07"]
    results = []
    with (ROOT / "declared-input-reference.csv").open(newline="") as stream:
        rows = list(csv.reader(stream))[1:]
    if len(rows) != 6:
        raise ValueError("Expected all six retained observations")
    for row in rows:
        values = list(map(float, row))
        for mc, offset in ((True, 4), (False, 6)):
            solver.MC = [("1.1121", ".9679", "-1.42") if mc else None, None]
            coarse, _, _ = solver.solve(values[0], values[1], Decimal("1e-10"))
            fine, residual, iterations = solver.solve(values[0], values[1], Decimal("5e-11"))
            for predicted, expected, previous in ((fine[1], values[offset], coarse[1]),
                                                   (fine[0], values[offset + 1], coarse[0])):
                if abs(predicted / expected - 1) > 1e-10 or abs(previous / predicted - 1) > 1e-10:
                    raise ValueError("Independent prediction or step-refinement mismatch")
            results.append(dict(MC=mc, T_K=values[0], P_bara=values[1],
                                x_methane=fine[1], predicted_y_MEG_unmeasured=fine[0],
                                log_fugacity_residual=residual, iterations=iterations))
    args.output.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
