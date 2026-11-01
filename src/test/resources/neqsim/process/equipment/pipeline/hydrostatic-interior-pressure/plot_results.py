"""Render the saved analytical force/work controls and RHS-only timing observations.

Run beside the CSV files with Python 3, NumPy and Matplotlib. The Java audit writes
the force/work files; HydrostaticPressureRhsBenchmark writes the timing files.
"""
from pathlib import Path
import csv
import statistics

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

ROOT = Path(__file__).resolve().parent
plt.rcParams.update({"font.size": 11, "svg.fonttype": "none", "svg.hashsalt": "neqsim-hydrostatic-20261031"})


def read_csv(name):
    """Read retained numerical evidence without changing any acceptance limits."""
    with (ROOT / name).open(newline="", encoding="utf-8") as stream:
        return list(csv.DictReader(stream))


def render_controls():
    """Plot instantaneous buoyancy, fixed-error gates and signed pressure work."""
    forces = read_csv("phase-forces.csv")
    work = read_csv("phase-work.csv")
    fig, axes = plt.subplots(1, 3, figsize=(15, 4.8), constrained_layout=True)
    sample = [row for row in forces if row["terrain"] == "rise"
              and row["density"] == "constant" and row["cells"] == "12"
              and float(row["water_cut"]) == 0.0]
    fraction = np.linspace(0.001, 0.999, 250)
    mixture_density = 10.0 * fraction + 800.0 * (1.0 - fraction)
    for phase, density, color in [("gas", 10.0, "#176b9a"), ("oil", 800.0, "#c05a24")]:
        expected = (mixture_density / density - 1.0) * 9.81 * 55.0 / 5000.0
        axes[0].plot(fraction, expected, color=color, label=f"{phase}: analytical")
        present = [row for row in sample if (float(row["gas_holdup"]) > 0.0
                   if phase == "gas" else float(row["gas_holdup"]) < 1.0)]
        axes[0].scatter([float(row["gas_holdup"]) for row in present],
                        [float(row[f"first_{phase}_acceleration_m_s2"]) for row in present],
                        color=color, facecolors="none", s=45, label=f"{phase}: RHS")
    axes[0].axhline(0.0, color="0.6", linewidth=0.8)
    axes[0].set(xlabel="Gas volume fraction", ylabel="Initial acceleration [m/s²]",
                title="Opposing phase forces\n55 m rise over 5 km; initially at rest")
    axes[0].legend(fontsize=9)

    mesh = [12, 24, 48, 96, 192]
    error = [max(float(row["max_acceleration_error_m_s2"]) for row in forces
                 if int(row["cells"]) == count) for count in mesh]
    axes[1].loglog(mesh, error, "o-", color="#176b9a", label="Largest error in matrix")
    axes[1].axhline(1.0e-10, color="#b83838", linestyle="--", label="Fixed acceptance gate")
    axes[1].set(xlabel="Cells", ylabel="Acceleration error [m/s²]",
                title="Discrete analytical identity\n720 controls; roundoff is retained")
    axes[1].set_xticks(mesh, labels=[str(count) for count in mesh])
    axes[1].legend(fontsize=9)

    expected_work = np.array([float(row["expected_work_W"]) for row in work]) / 1000.0
    observed_work = np.array([float(row["observed_work_W"]) for row in work]) / 1000.0
    limit = max(abs(expected_work)) * 1.1
    axes[2].plot([-limit, limit], [-limit, limit], "--", color="0.5", label="Q × pressure correction")
    axes[2].scatter(expected_work, observed_work, color="#176b9a", s=24, alpha=0.7, label="48 RHS controls")
    axes[2].set(xlabel="Analytical pressure work [kW]", ylabel="Observed added energy flux [kW]",
                title="Signed gas/oil/water work\nCo-current and countercurrent donors")
    axes[2].legend(fontsize=9)
    for axis in axes:
        axis.grid(alpha=0.2)
    fig.suptitle("Common-pressure qualification — instantaneous operators, not retained pools", fontsize=14)
    fig.savefig(ROOT / "phase-controls.svg", metadata={"Date": None})
    fig.savefig(ROOT / "phase-controls.png", dpi=150)
    plt.close(fig)


def render_timing():
    """Keep every timing batch and plot medians without asserting process throughput."""
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.5), constrained_layout=True)
    for axis, terrain in zip(axes, ["flat", "original"]):
        for label, style in [("master", "--"), ("head", "-")]:
            rows = read_csv(f"performance-{label}.csv")
            for fraction, color in [(1.0, "#176b9a"), (0.35, "#c05a24")]:
                mesh = [12, 24, 96, 192]
                samples = [[float(row["ns_per_call"]) / 1000.0 for row in rows
                            if row["terrain"] == terrain
                            and float(row["gas_holdup"]) == fraction
                            and int(row["cells"]) == count] for count in mesh]
                medians = np.array([statistics.median(values) for values in samples])
                lower = medians - np.array([np.percentile(values, 25) for values in samples])
                upper = np.array([np.percentile(values, 75) for values in samples]) - medians
                phase = "gas" if fraction == 1.0 else "gas/oil"
                axis.errorbar(mesh, medians, yerr=[lower, upper], linestyle=style, marker="o", color=color,
                              capsize=3, label=f"{label}: {phase}")
        axis.set(xlabel="Cells", ylabel="Median RHS cost [µs/call]", title=f"{terrain.capitalize()} terrain")
        axis.grid(alpha=0.2)
        axis.legend(fontsize=9)
    fig.suptitle("RHS timing: median and interquartile range; no flash or time integration", fontsize=12)
    fig.savefig(ROOT / "rhs-timing.svg", metadata={"Date": None})
    fig.savefig(ROOT / "rhs-timing.png", dpi=150)
    plt.close(fig)


def aggregate_timing():
    """Combine both complete long-warmup forks, retaining every batch and its fork."""
    for label in ["head", "master"]:
        combined = []
        for fork in [1, 2]:
            rows = read_csv(f"performance-{label}-long-{fork}.csv")
            if len(rows) != 112:
                raise ValueError("Incomplete timing fork")
            combined.extend(dict(row, fork=fork) for row in rows)
        with (ROOT / f"performance-{label}.csv").open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=list(combined[0]), lineterminator="\n")
            writer.writeheader()
            writer.writerows(combined)


if __name__ == "__main__":
    aggregate_timing()
    render_controls()
    render_timing()
