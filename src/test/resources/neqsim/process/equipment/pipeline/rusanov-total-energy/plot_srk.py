"""Plot equilibrium SRK inputs and the explicit volume convention, not an elapsed pool result."""
import csv
from pathlib import Path
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parent
with (ROOT / "srk-native.csv").open(newline="") as stream:
    rows = list(csv.DictReader(stream))
fig, axes = plt.subplots(1, 2, figsize=(11, 5))
colors = ["#0072B2", "#D55E00", "#009E73"]
for temperature, color in zip([260.0, 277.15, 290.0], colors):
    for phase, style in [("gas", "-"), ("oil", "--")]:
        selected = [row for row in rows if float(row["T_K"]) == temperature and row["phase"] == phase
                    and float(row["fraction_scale"]) == 1 and float(row["uL_m_s"]) == 0
                    and float(row["alphaL"]) > 0]
        pressure = [float(row["pL_bara"]) for row in selected]
        correction = [float(row["eMechanicalL_J_kg"]) - float(row["eL_J_kg"]) for row in selected]
        heavy = [float(row["xL_n-heptane"]) for row in selected]
        label = f"{temperature:g} K, {phase}"
        axes[0].plot(pressure, correction, style, marker="o", color=color, label=label)
        axes[1].plot(pressure, heavy, style, marker="o", color=color, label=label)
axes[0].set_ylabel("Mechanical minus EOS internal energy [J/kg]")
axes[0].set_title("Explicit pressure-volume convention")
axes[1].set_ylabel("Phase n-heptane mole fraction [mol/mol]")
axes[1].set_title("Equilibrium phase compositions")
for axis in axes:
    axis.set_xlabel("Pressure [bara]")
    axis.grid(alpha=0.25)
fig.suptitle("Fixed six-component SRK/classic samples: local-flux qualification inputs")
handles, labels = axes[0].get_legend_handles_labels()
fig.legend(handles, labels, loc="lower center", ncol=3, bbox_to_anchor=(0.5, 0.02))
fig.tight_layout(rect=(0, 0.14, 1, 0.93))
fig.savefig(ROOT / "srk-inputs.png", dpi=160, bbox_inches="tight")
