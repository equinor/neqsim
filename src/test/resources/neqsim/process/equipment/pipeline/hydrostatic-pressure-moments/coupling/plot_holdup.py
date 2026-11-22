"""Plot retained adapter errors; optional Matplotlib dependency, no solver execution."""
import csv
from decimal import Decimal as Q
from pathlib import Path
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parent


def main():
    """Compare the original and repaired liquid-area adapter at D = 1.0148 m."""
    with (ROOT / "holdup-adapter-before.csv").open() as stream:
        old = [row for row in csv.DictReader(stream) if float(row["liquid_holdup"]) < 0.5]
    with (ROOT / "holdup-native.csv").open() as stream:
        native = {(float(row["diameter_m"]), float(row["liquid_holdup"])): row for row in csv.DictReader(stream)
                  if float(row["gas_holdup"]) >= float(row["liquid_holdup"])}
    with (ROOT / "holdup-reference.csv").open() as stream:
        reference = {(float(row["diameter_m"]), float(row["liquid_holdup"])): row for row in csv.DictReader(stream)
                     if float(row["gas_holdup"]) >= float(row["liquid_holdup"])}
    fractions = [float(row["liquid_holdup"]) for row in old]
    errors = []
    for fraction in fractions:
        key = (1.0148, fraction)
        actual = Q.from_float(float(native[key]["liquid_m2"]))
        expected = Q(reference[key]["liquid_m2"])
        errors.append(float(abs(actual - expected) / expected))
    fig, ax = plt.subplots(figsize=(7.6, 4.6), layout="constrained")
    ax.loglog(fractions, [abs(float(row["relative_area_error"])) for row in old], "o-",
              color="#ad3b21", label="Original depth adapter")
    ax.loglog(fractions, errors, "s-", color="#166899", label="Paired minority-area inversion")
    ax.axhline(5e-13, color="#555555", linestyle="--", linewidth=1, label="Frozen relative gate (5e-13)")
    ax.set(xlabel="Liquid volume fraction [-]", ylabel="Absolute relative liquid-area error [-]",
           title="Pressure-moment area adapter: positive liquid traces\nD = 1.0148 m; repaired values vs 80-digit series reference")
    ax.set_ylim(1e-17, 1e2)
    ax.grid(True, which="major", alpha=0.25)
    ax.legend(loc="center left", fontsize=9)
    fig.savefig(ROOT / "holdup-area-error.png", dpi=170)
    plt.close(fig)


if __name__ == "__main__":
    main()
