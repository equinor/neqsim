"""Render retained synthetic Safety Shutdown evidence; no simulation or calibration."""
import argparse
import json
import gzip
from pathlib import Path
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np


def read(root, name):
    """Read immutable JSON evidence from this validation directory."""
    path = root / name
    if path.exists():
        return json.loads(path.read_text())
    with gzip.open(path.with_suffix(path.suffix + ".gz"), "rt") as stream:
        return json.load(stream)


def render(root, png):
    """Show accepted interval data and the explicitly unresolved mesh dependence."""
    output = read(root, "public-output.json")
    series = output["timeSeries"]
    rows = np.asarray(output["acceptedLiftIntervals"]["rows"])
    contact = series["liftCheckValve"][-1]["lastContactTime_s"]
    refinement = read(root, "coupled-characteristic-refinement.json")
    meshes = read(root, "mesh-refinement.json") + read(root, "finer-mesh-diagnostic.json")
    plt.rcParams.update({"font.size": 10, "svg.hashsalt": "SafetyShutdown4707"})
    figure, axes = plt.subplots(2, 2, figsize=(11.5, 7.5), constrained_layout=True)
    pressure = axes[0, 0]
    pressure.plot(series["time_s"], series["outletPressure_bara"], color="#086788", label="Outlet pressure")
    pressure.set(xlabel="Time (s)", ylabel="Absolute pressure (bar)", xlim=(0, 0.16),
                 title="51 faces: pressure and translating lift")
    lift_axis = pressure.twinx()
    lift_axis.plot(rows[:, 0], rows[:, 1] * 1000, color="#b95612", label="Lift")
    lift_axis.set(ylabel="Lift (mm)", ylim=(-0.1, 5.3))
    pressure.axvline(contact, color="#666666", linestyle=":", label="Numerical seat contact")
    handles, labels = pressure.get_legend_handles_labels()
    extra, extra_labels = lift_axis.get_legend_handles_labels()
    pressure.legend(handles + extra, labels + extra_labels, fontsize=8, loc="lower left")
    flow = axes[0, 1]
    flow.plot(rows[:, 0], rows[:, 8] * 1000, label="Signed jet flow", color="#086788")
    flow.plot(rows[:, 0], rows[:, 9] * 1000, label="Swept flow", color="#b95612")
    flow.axvline(contact, color="#666666", linestyle=":")
    flow.axhline(0, color="#777777", linewidth=0.7)
    flow.set(xlabel="Time (s)", ylabel="Accepted interval flow (L/s)", xlim=(0.09, 0.12),
             title="Reverse flow is retained before seating")
    flow.legend(fontsize=8)
    errors = axes[1, 0]
    steps = np.asarray([row["dt_s"] for row in refinement])
    values = np.asarray([row["normalizedCombinedError"] for row in refinement])
    errors.loglog(steps * 1e6, values, "o-", color="#086788", label="Java vs independent continuous oracle")
    errors.loglog(steps * 1e6, values[0] * steps / steps[0], "--", color="#777777", label="First-order slope")
    errors.set(xlabel="Step duration (µs)", ylabel="Combined dimensionless error",
               title="Constant-characteristic coupling verification")
    from matplotlib.ticker import FixedLocator, FuncFormatter, NullFormatter
    errors.xaxis.set_major_locator(FixedLocator(steps * 1e6))
    errors.xaxis.set_major_formatter(FuncFormatter(lambda value, position: f"{value:g}"))
    errors.xaxis.set_minor_formatter(NullFormatter())
    errors.legend(fontsize=8)
    impact = axes[1, 1]
    nodes = [row["nodes"] for row in meshes]
    energy = [row["firstImpactLoss_J"] for row in meshes]
    impact.semilogx(nodes, energy, "o-", color="#ad343e")
    impact.set(xlabel="Physical faces", ylabel="First inelastic impact loss (J)",
               title="Sharp-front impact is mesh dependent")
    impact.set_xticks(nodes, labels=[str(node) for node in nodes])
    impact.annotate("801 faces: two contacts by 0.5 s", xy=(801, energy[-1]),
                    xytext=(75, 0.115), fontsize=9, color="#ad343e",
                    arrowprops={"arrowstyle": "->", "color": "#ad343e"})
    for axis in axes.flat:
        axis.grid(True, alpha=0.25)
    figure.suptitle("Safety Shutdown — synthetic ideal translating check valve\n"
                   "Analytical verification; sharp-front slam accuracy NOT QUALIFIED", fontsize=13)
    figure.savefig(root / "validation.svg", metadata={"Date": None})
    svg = root / "validation.svg"
    svg.write_text("\n".join(line.rstrip() for line in svg.read_text().splitlines()) + "\n")
    if png is not None:
        figure.savefig(png, dpi=160)
    plt.close(figure)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--png", type=Path)
    arguments = parser.parse_args()
    render(Path(__file__).resolve().parent, arguments.png)
