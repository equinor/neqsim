"""Generate synthetic prescribed-pressure ODE references independently of NeqSim.

Units: reservoir m3, bara, seconds, ct in 1/bar and J in m3/(s bar).
The Decimal solution uses an affine particular solution plus an exponential.
Interior water extrema use derivative-sign bisection, not the native crossing formula.
No measured-data, radial-diffusion, coupled-EOS or field-accuracy claim is made.
"""

import argparse
import csv
from decimal import Decimal, localcontext
from itertools import product
from pathlib import Path


def generate(output, precision):
    """Write frozen inputs and 36-significant-digit reference values."""
    pairs = [(300, 300), (300, 150), (200, 450), (400, 150)]
    meshes = {
        "grid": [1e-10, 1e-6, 1e-4, .01, 1, 10, 100],
        "boundaries": [.0000999999, .0001, .0001000001, .000999999, .001, .001000001],
    }
    output.mkdir(parents=True, exist_ok=True)
    for name, exposures in meshes.items():
        rows = []
        for wi, ct, j, x, (p0, p1) in product(
            [1e3, 1e6, 1e9], [1e-6, 1e-4, 1e-3], [1e-5, .02, 2], exposures, pairs
        ):
            rows.append([str(wi), str(ct), str(j), str(wi * ct * x / j), str(p0), str(p1)])
        for wi, ct, j, (p0, p1) in product([1e6], [0, 1e-4], [0, .02], pairs):
            rows.append([str(wi), str(ct), str(j), "10000", str(p0), str(p1)])
        for p0, p1 in pairs:
            rows.append(["Infinity", ".0001", ".02", "10000", str(p0), str(p1)])
        with localcontext() as context, (output / (name + ".csv")).open("w", newline="") as handle:
            context.prec = precision
            writer = csv.writer(handle, lineterminator="\n")
            writer.writerow(["id", "Wi_m3", "ct_per_bar", "J_m3_per_s_bar", "dt_s", "pr0_bara",
                             "pr1_bara", "pa_bara", "We_m3", "minimum_We_m3", "maximum_We_m3"])
            for index, row in enumerate(rows):
                wi, ct, j, dt, p0, p1 = map(Decimal, row)
                capacity = wi * ct if ct else Decimal(0)
                initial = Decimal(300)

                def state(fraction):
                    if not j:
                        return initial, Decimal(0)
                    if not capacity:
                        return p0 + (p1 - p0) * fraction, Decimal(0)
                    if capacity.is_infinite():
                        return initial, j * dt * ((initial - p0) * fraction
                                                  - (p1 - p0) * fraction ** 2 / 2)
                    rate = j / capacity
                    slope = (p1 - p0) / dt
                    pressure = (p0 + slope * dt * fraction - slope / rate
                                + (initial - p0 + slope / rate) * (-rate * dt * fraction).exp())
                    return pressure, capacity * (initial - pressure)

                def drive(fraction):
                    return state(fraction)[0] - (p0 + (p1 - p0) * fraction)

                pressure, water = state(Decimal(1))
                extrema = [Decimal(0), water]
                if j and capacity and drive(Decimal(0)) * drive(Decimal(1)) < 0:
                    low, high = Decimal(0), Decimal(1)
                    sign = drive(low) > 0
                    for _ in range(100):
                        middle = (low + high) / 2
                        if (drive(middle) > 0) == sign:
                            low = middle
                        else:
                            high = middle
                    extrema.append(state((low + high) / 2)[1])
                reference = [pressure, water, min(extrema), max(extrema)]
                writer.writerow([index] + row + [format(value, ".35E") if value else "0" for value in reference])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent)
    parser.add_argument("--precision", type=int, default=60)
    arguments = parser.parse_args()
    generate(arguments.output, arguments.precision)
