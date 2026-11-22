"""Compare the staged water/n-butane PR-HV tuple with published coexistence endpoints."""

import argparse
import csv
from decimal import Decimal
import sys

from audit_huron_vidal_propane import (
    ATTRACTION_STEP,
    FIXTURES,
    PSIA_TO_BARA,
    match_water_pressure,
    model,
    water_saturation_pressure,
)

DATA = FIXTURES / "hv-water-nbutane"


def configure():
    """Apply declared NeqSim pure inputs and the recovered Table 1 water-first tuple."""
    model.EOS = "PR"
    model.PURE = [("647.30", "220.89", ".344"), ("425.12", "37.96", ".2002")]
    model.TUPLE = ("4968", "-19.6", "1567", "5.7", ".06")
    model.MC = [None, None]


def pure_pressure(temperature, reference, index):
    """Solve equality of stable pure-root fugacities near the reference pressure in bara."""
    def difference(pressure):
        composition = (1, 0) if index == 0 else (0, 1)
        gas, zg, _ = model.phi(composition, temperature, pressure, True, ATTRACTION_STEP)
        liquid, zl, _ = model.phi(composition, temperature, pressure, False, ATTRACTION_STEP)
        return gas[index] - liquid[index] if zg - zl > 1e-6 else None

    low = None
    for offset in range(-40, 41):
        high = reference * (1 + offset / 100)
        residual = difference(high)
        if residual is None:
            low = None
        elif residual <= 0:
            low = high
        elif low is not None:
            break
    else:
        raise ValueError("Pure saturation pressure is outside the declared bracket")
    for _ in range(48):
        middle = (low + high) / 2
        residual = difference(middle)
        if residual is None:
            raise ValueError("Pure saturation requires two distinct stable roots")
        if residual < 0:
            low = middle
        else:
            high = middle
    if abs(residual) >= 1e-10:
        raise ValueError("Pure saturation fugacities did not close")
    return middle


def assess(writer, match_pressure):
    """Compare independent pressure roots and both composition endpoints where reported."""
    with (DATA / "observations.csv").open() as source:
        reader = csv.DictReader(source)
        rows = list(reader)
    coordinates = {(row["temperature_degF"], row["total_pressure_psia"]) for row in rows}
    if len(rows) != 14 or len(coordinates) != len(rows):
        raise ValueError("Expected 14 distinct observed states")
    writer.writerow((*rows[0], "T_K", "P_bara", "in_source_emphasis", "rich_water_x_pr", "aqueous_nbutane_x_pr",
                     "rich_Z", "aqueous_Z", "rich_water_error_pct", "aqueous_nbutane_error_pct",
                     "max_logf", "max_pressure_error_bara"))
    for row in rows:
        t = (float(row["temperature_degF"]) - 32) * 5 / 9 + 273.15
        p = float(row["total_pressure_psia"]) * PSIA_TO_BARA
        rich_gas = row["rich_phase"] == "vapor"
        if match_pressure:
            match_water_pressure(t)
        result, logf, _ = model.solve(t, p, ATTRACTION_STEP, rich_gas=rich_gas)
        refined, refined_logf, _ = model.solve(t, p, ATTRACTION_STEP / 2, rich_gas=rich_gas)
        if max(logf, refined_logf) >= 1e-10 or max(abs(a - b) for a, b in zip(result[:4], refined[:4])) >= 1e-9:
            raise ValueError("Independent endpoint solution did not pass step refinement")
        y, x, zr, za, pressure_error = result
        if zr <= za:
            raise ValueError("Expected distinct rich and aqueous roots")
        aqueous_observed = float(Decimal(row["aqueous_nbutane_ppm"]) * Decimal("1e-6"))
        rich_value = row["rich_water_x_times_1000"]
        rich_error = 100 * (y / float(Decimal(rich_value) * Decimal(".001")) - 1) if rich_value else ""
        writer.writerow((*row.values(), t, p, 323.15 <= t <= 423.15 and 25 <= p <= 700,
                         y, x, zr, za, rich_error, 100 * (x / aqueous_observed - 1), logf, pressure_error))


def main():
    """Write the default, local water-alpha or pure-pressure comparison as CSV."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("staged", "match-water-pressure", "pure-pressure"), default="staged")
    args = parser.parse_args()
    configure()
    writer = csv.writer(sys.stdout, lineterminator="\n")
    if args.mode == "pure-pressure":
        writer.writerow(("temperature_degF", "temperature_K", "component", "reference_bara",
                         "pr_bara", "relative_error_pct"))
        for fahrenheit in (160, 220, 280):
            temperature = (fahrenheit - 32) * 5 / 9 + 273.15
            nbutane_pressure = 10 ** (4.35576 - 1175.581 / (temperature - 2.071))
            for index, (name, reference) in enumerate(
                    (("water", water_saturation_pressure(temperature)), ("n-butane", nbutane_pressure))):
                predicted = pure_pressure(temperature, reference, index)
                writer.writerow((fahrenheit, temperature, name, reference, predicted, 100 * (predicted / reference - 1)))
    else:
        assess(writer, args.mode == "match-water-pressure")


if __name__ == "__main__":
    main()
