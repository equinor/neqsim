"""Plot retained Rusanov energy controls; no interpolated physics or simulated pool history."""
import csv
from pathlib import Path
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import qualify

ROOT = Path(__file__).resolve().parent
inputs = qualify.matrix()
with (ROOT / "native.csv").open() as stream:
    native = list(csv.DictReader(stream))
with (ROOT / "baseline-native.csv").open() as stream:
    baseline = list(csv.DictReader(stream))
if len(native) != 408 or len(baseline) != 408:
    raise ValueError("Incomplete plot matrix")
before = []
after = []
for index, row in enumerate(inputs):
    target, scale = qualify.reference(row)
    expected = float(target[2])
    normalization = float(scale[2])
    before.append(abs(float(baseline[index]["energy_W"]) - expected) / normalization)
    after.append(abs(float(native[index]["energy_W"]) - expected) / normalization)
fig, axes = plt.subplots(1, 2, figsize=(11, 4), layout="constrained")
axes[0].plot(before, label="Original enthalpy-inventory dissipation")
axes[0].plot(after, label="Corrected total-energy dissipation")
axes[0].set(xlabel="Fixed control index [-]", ylabel="Normalized energy-flux error [-]",
            title="408 independent ideal-gas controls")
axes[0].legend(fontsize=8, loc="upper center", bbox_to_anchor=(0.5, -0.2))
axes[1].plot(after, ".", markersize=2)
axes[1].axhline(3e-14, color="red", linestyle="--", label="Frozen acceptance 3e-14")
axes[1].set(xlabel="Fixed control index [-]", ylabel="Normalized energy-flux error [-]",
            title="Corrected flux including trace pairs")
axes[1].legend(fontsize=8)
for axis in axes:
    axis.grid(alpha=0.3)
fig.suptitle("Optional local Rusanov flux; no pipeline solver selection or pool prediction")
fig.savefig(ROOT / "energy-flux.png", dpi=160, bbox_inches="tight")
