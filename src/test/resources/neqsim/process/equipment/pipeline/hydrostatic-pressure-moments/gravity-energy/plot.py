"""Render the retained static energy budgets (optional Matplotlib; no simulation)."""
import csv
from pathlib import Path
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parent


def main():
    """Plot one representative diameter and the closed two-cell configuration difference."""
    with (ROOT / "native.csv").open() as source:
        rows = sorted((row for row in csv.DictReader(source) if float(row["diameter_m"]) == 1.0148),
                      key=lambda row: float(row["liquid_holdup"]))
    with (ROOT / "two-cell-budget.csv").open() as source:
        budget = next(csv.DictReader(source))
    fig, axes = plt.subplots(1, 2, figsize=(10, 4.6), layout="constrained")
    axes[0].plot([float(r["liquid_holdup"]) for r in rows], [float(r["energy_J_m"]) for r in rows],
                 "o-", color="#166899", markersize=4)
    axes[0].axhline(0, color="#555555", linestyle="--", linewidth=1)
    axes[0].set(xlabel="Liquid volume fraction [-]", ylabel="Potential energy relative to centre [J/m]",
                title="Cross-section: D = 1.0148 m\nGas 10 / liquid 800 kg/m³; normal g = 9.81 m/s²")
    values = [float(budget["initial_J"]) / 1000, float(budget["redistributed_J"]) / 1000]
    bars = axes[1].bar(["Dry + 60% liquid", "30% + 30% liquid"], values, color=["#ad6730", "#166899"])
    for bar, value in zip(bars, values):
        axes[1].text(bar.get_x() + bar.get_width() / 2, value - 0.4, f"{value:.3f} kJ", ha="center", va="top")
    axes[1].set(ylabel="Total potential energy [kJ]", ylim=(-15, 0),
                title="Two closed horizontal 10 m cells\nSame liquid volume in both configurations")
    axes[1].text(0.5, 0.1, "Potential drop = " + f"{float(budget['available_potential_drop_J']) / 1000:.3f} kJ",
                 transform=axes[1].transAxes, ha="center")
    for ax in axes:
        ax.grid(axis="y", alpha=0.2)
    fig.suptitle("Static gravitational energy budget — no elapsed drainage or evaporation prediction")
    fig.savefig(ROOT / "gravity-energy.png", dpi=170)
    plt.close(fig)


if __name__ == "__main__":
    main()
