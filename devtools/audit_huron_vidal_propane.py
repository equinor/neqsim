"""Reproduce the staged PR-HV propane–water assessments and pure-pressure controls."""

import argparse
import csv
from decimal import Decimal
import math
from pathlib import Path
from statistics import mean
import sys

FIXTURES = Path(__file__).resolve().parents[1] / "src/test/resources/neqsim/thermo/mixingrule"
sys.path.insert(0, str(FIXTURES / "hv-meg-methane"))
import binary_solver as model

ATTRACTION_STEP = Decimal("1e-10")
PSIA_TO_BARA = .06894757293168
DATASETS = {
    "kobayashi-katz": ("observations.csv", 18),
    "azarnoosh-mcketta": ("azarnoosh-mcketta-observations.csv", 9),
}
TEMPERATURE_ERROR_K = {"kobayashi-katz": (-.3, .3), "azarnoosh-mcketta": (0.,)}
RELATIVE_COMPOSITION_ERROR = {"kobayashi-katz": .03, "azarnoosh-mcketta": .02}


def configure():
    """Apply the staged water-first tuple and declared NeqSim pure inputs."""
    model.EOS = "PR"
    model.PURE = [("647.30", "220.89", ".344"), ("369.83", "42.48", ".1523")]
    model.TUPLE = ("-1584", "-.44", "4750", "-3.3", ".07")
    model.MC = [None, None]


def water_saturation_pressure(temperature):
    """Return IAPWS water saturation pressure in bara."""
    tr = temperature / 647.096
    tau = 1 - tr
    coefficients = (-7.85951783, 1.84408259, -11.7866497, 22.6807411, -15.9618719, 1.80122502)
    powers = (1, 1.5, 3, 3.5, 4, 7.5)
    return 220.64 * math.exp(sum(a * tau**b for a, b in zip(coefficients, powers)) / tr)


def match_water_pressure(temperature):
    """Set a temperature-local water alpha parameter at IAPWS saturation pressure."""
    pressure = water_saturation_pressure(temperature)

    def equal_fugacity(m):
        model.MC = [(str(m), "0", "0"), None]
        vapor, zg, _ = model.phi([1, 0], temperature, pressure, True, ATTRACTION_STEP)
        liquid, zl, _ = model.phi([1, 0], temperature, pressure, False, ATTRACTION_STEP)
        if zg <= zl:
            raise ValueError("Pure water requires distinct stable roots")
        return vapor[0] - liquid[0]

    low, high = Decimal(".4"), Decimal("1.2")
    if not equal_fugacity(low) < 0 < equal_fugacity(high):
        raise ValueError("Pure-water alpha control failed to bracket saturation")
    for _ in range(46):
        middle = (low + high) / 2
        if equal_fugacity(middle) < 0:
            low = middle
        else:
            high = middle
    if abs(equal_fugacity((low + high) / 2)) >= 1e-10:
        raise ValueError("Pure-water saturation fugacities do not close")


def rich_phase_assessment(match_pressure, thesis_propane):
    """Compare the water content of the reported propane-rich phase in mole units."""
    if thesis_propane:
        model.PURE[1] = ("369.80", "42.46", ".1524")
    filename = FIXTURES / "hv-water-propane/kobayashi-katz-rich-observations.csv"
    with filename.open() as observations:
        rows = list(csv.DictReader(observations))
    if len(rows) != 22:
        raise ValueError("Expected all 22 reported rich-phase observations at 230–300 °F")

    writer = csv.writer(sys.stdout, lineterminator="\n")
    writer.writerow(("F", "psia", "T_K", "P_bar", "y_obs", "y_pr", "x_pr",
                     "Z_rich", "Z_aq", "error_pct", "logf", "pressure_error"))
    in_window_errors = []
    for row in rows:
        observed = Decimal(row["rich_water_mole_fraction"])
        if observed + Decimal(row["rich_propane_ppm"]) * Decimal("1e-6") != 1:
            raise ValueError("Transcribed rich-phase fractions do not sum to unity")
        fahrenheit = float(row["temperature_degF"])
        psia = float(row["total_pressure_psia"])
        temperature = (fahrenheit - 32) * 5 / 9 + 273.15
        pressure = psia * PSIA_TO_BARA
        if match_pressure:
            match_water_pressure(temperature)
        state, residual, _ = model.solve(temperature, pressure, ATTRACTION_STEP)
        refined, refined_residual, _ = model.solve(temperature, pressure, ATTRACTION_STEP / 2)
        if max(residual, refined_residual) >= 1e-10 or max(abs(a - b) for a, b in zip(state[:4], refined[:4])) >= 1e-9:
            raise ValueError("Independent rich-phase solution did not pass refinement")
        water, propane, rich_z, aqueous_z, pressure_error = state
        if rich_z <= aqueous_z or temperature <= max(369.83, 369.80):
            raise ValueError("Expected distinct rich/aqueous roots above pure propane's critical temperature")
        error = 100 * (water / float(observed) - 1)
        writer.writerow((fahrenheit, psia, temperature, pressure, observed, water, propane,
                         rich_z, aqueous_z, error, residual, pressure_error))
        if 323.15 <= temperature <= 423.15 and 25 <= pressure <= 700:
            in_window_errors.append(error)
    print("inside", len(in_window_errors), mean(map(abs, in_window_errors)),
          min(in_window_errors), max(in_window_errors), file=sys.stderr)


def main():
    """Compare both calculated phase endpoints, retaining measured aqueous observations."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", choices=(*DATASETS, "kobayashi-katz-rich"), default="kobayashi-katz",
                        help="Select an aqueous or propane-rich experiment")
    parser.add_argument("--thesis-propane", action="store_true",
                        help="Use the Yushchenko thesis propane properties for the rich-phase comparison")
    parser.add_argument("--uncertainty-envelope", action="store_true",
                        help="Scan reported input error bounds on in-window points")
    parser.add_argument("--match-water-pressure", action="store_true",
                        help="Run the local pure-pressure sensitivity at the in-window points")
    args = parser.parse_args()
    if args.match_water_pressure and args.uncertainty_envelope:
        parser.error("Choose one sensitivity per run")
    if args.thesis_propane and args.dataset != "kobayashi-katz-rich":
        parser.error("The thesis propane control applies to the rich-phase dataset")
    configure()
    if args.dataset == "kobayashi-katz-rich":
        if args.uncertainty_envelope:
            parser.error("No phase-specific experimental uncertainty is available for these rich-phase data")
        rich_phase_assessment(args.match_water_pressure, args.thesis_propane)
        return
    filename, expected_count = DATASETS[args.dataset]
    with (FIXTURES / "hv-water-propane" / filename).open() as observations:
        fields = ("temperature_degF", "total_pressure_psia", "aqueous_propane_x_times_1e5")
        rows = [tuple(float(row[field]) for field in fields) for row in csv.DictReader(observations)]
    if len(rows) != expected_count or len({(row[0], row[1]) for row in rows}) != expected_count:
        raise ValueError(f"Expected {expected_count} distinct measured aqueous coordinates")

    writer = csv.writer(sys.stdout, lineterminator="\n")
    if not args.uncertainty_envelope:
        writer.writerow(("F", "psia", "T_K", "P_bar", "x_obs", "x_pr", "y_pr",
                         "Z_gas", "Z_aq", "error_pct", "logf", "pressure_error"))
    errors = {True: [], False: []}
    for fahrenheit, psia, scaled_x in rows:
        temperature = (fahrenheit - 32) * 5 / 9 + 273.15
        pressure = psia * PSIA_TO_BARA
        inside = 323.15 <= temperature <= 423.15 and 25 <= pressure <= 700
        if args.uncertainty_envelope:
            if inside:
                neighbors = [
                    model.solve(temperature + offset, pressure * scale, ATTRACTION_STEP)[0][1]
                    for offset in TEMPERATURE_ERROR_K[args.dataset] for scale in (.99, 1.01)
                ]
                lower_estimate = scaled_x * 1e-5 * (1 - RELATIVE_COMPOSITION_ERROR[args.dataset])
                if max(neighbors) >= lower_estimate:
                    raise ValueError("Propane prediction overlaps the compiler error screen")
                print(temperature, pressure, min(neighbors), max(neighbors), lower_estimate, file=sys.stderr)
            continue
        if args.match_water_pressure:
            if not inside:
                continue
            match_water_pressure(temperature)
        state, residual, _ = model.solve(temperature, pressure, ATTRACTION_STEP)
        refined, refined_residual, _ = model.solve(temperature, pressure, ATTRACTION_STEP / 2)
        y_water, x_propane, zg, zl, pressure_error = state
        if not (zg > .5 and zl < .05):
            raise ValueError(f"Unexpected cubic roots at {temperature} K, {pressure} bar")
        if max(abs(a - b) for a, b in zip(state[:4], refined[:4])) >= 1e-9:
            raise ValueError("Composition differences did not converge")
        if max(residual, refined_residual) >= 1e-10:
            raise ValueError("Independent endpoint fugacities do not close")
        measured_x = scaled_x * 1e-5
        signed_error = 100 * (x_propane / measured_x - 1)
        writer.writerow((fahrenheit, psia, temperature, pressure, measured_x, x_propane,
                         y_water, zg, zl, signed_error, residual, pressure_error))
        errors[inside].append(signed_error)
    for inside, values in errors.items():
        if values:
            label = "inside" if inside else "outside"
            print(label, len(values), mean(map(abs, values)), min(values), max(values), file=sys.stderr)


if __name__ == "__main__":
    main()
