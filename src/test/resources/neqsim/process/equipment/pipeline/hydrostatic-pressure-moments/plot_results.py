"""Plot retained Java pressure-moment controls without rerunning or fitting physics."""
import csv
from pathlib import Path
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parent
with (ROOT / "moments.csv").open() as source:
    moments = list(csv.DictReader(source))
with (ROOT / "forces.csv").open() as source:
    forces = list(csv.DictReader(source))
fig, axes = plt.subplots(1, 3, figsize=(13, 3.9), constrained_layout=True)
selected = [row for row in moments if float(row["diameter_m"]) == 1.0148]
x = [float(row["depth_fraction"]) for row in selected]
for name, label in (("liquid_moment_m3", "Liquid Jl"), ("gas_moment_m3", "Gas Jg")):
    axes[0].plot(x, [float(row[name]) / 1.0148**3 for row in selected], "o-", label=label)
axes[0].set(xlabel="Normal interface depth h / D", ylabel="Signed moment / D³", title="Pressure moments about interface")
axes[0].legend()
for diameter in (0.01, 0.2, 1.0148, 3.0):
    rows = [row for row in moments if float(row["diameter_m"]) == diameter]
    axes[1].plot([float(row["depth_fraction"]) for row in rows],
                 [float(row["error_m3"]) / diameter**3 for row in rows], "o-", label=f"D = {diameter:g} m")
axes[1].set(xlabel="Normal interface depth h / D", ylabel="Max moment error / D³", title="Independent angular integral")
axes[1].ticklabel_format(axis="y", style="sci", scilimits=(0, 0))
axes[1].legend(fontsize=8)
intervals = (64, 256, 1024, 4096)
errors = [max(abs(float(row["residual_N"]) / float(row["gravity_N"]))
              for row in forces if int(row["intervals"]) == count) for count in intervals]
axes[2].loglog(intervals, errors, "o-", label="Worst of 48 phase controls")
axes[2].axhline(1e-10, color="black", linestyle="--", label="Final relative gate")
axes[2].set(xlabel="Independent Simpson subintervals", ylabel="|Traction − gravity| / |gravity|", title="Inclined-cell force identity")
axes[2].legend(fontsize=8)
for axis in axes:
    axis.grid(True, alpha=0.25)
fig.savefig(ROOT / "qualification.svg", metadata={"Date": None})
svg = ROOT / "qualification.svg"
svg.write_text("\n".join(line.rstrip() for line in svg.read_text().splitlines()) + "\n")
fig.savefig(ROOT / "qualification.png", dpi=160)
