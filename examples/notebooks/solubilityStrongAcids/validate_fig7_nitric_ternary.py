"""Validation of Figure 7 of Taleb, Ponche & Mirabel (1996).

Reproduces the predicted nitric acid partial pressure P_HNO3 (torr) over
H2O / HNO3 / H2SO4 mixtures at 273 K, plotted against wt % H2SO4, for nine fixed
HNO3 weight percentages (total-mixture basis). The headline result is the maximum
in P_HNO3 as sulfuric acid is added (H2SO4 first concentrates the nitric acid by
displacing water, then dilutes it), which the model reproduces. Each panel's curve
terminates at wt % H2SO4 = 100 - wt % HNO3, where the water content reaches zero.
"""
import numpy as np
import matplotlib.pyplot as plt

from _neqsim_model import p_nitric_torr, ternary_panel_compositions

TEMPERATURE = 273.15  # K

PANELS = [
    ("a", 4.88),
    ("b", 10.00),
    ("c", 21.90),
    ("d", 30.08),
    ("e", 39.50),
    ("f", 49.95),
    ("g", 59.92),
    ("h", 70.06),
    ("i", 79.84),
]

# Per-row y-axis maxima reproduced from the paper.
Y_MAX = [6.0, 6.0, 6.0, 10.0, 10.0, 10.0, 12.0, 12.0, 12.0]

# Approximate peak P_HNO3 (torr) read off the paper's panels, for reporting only.
PAPER_PEAK = {
    "a": 1.1, "b": 2.3, "c": 4.6, "d": 6.0, "e": 7.7,
    "f": 9.0, "g": 11.0, "h": 11.5, "i": 12.0,
}


def panel_curve(hno3):
    """Return (sulfuric_wt, P_HNO3_torr) arrays for one panel (total basis)."""
    smax = 100.0 - hno3
    sulfuric = np.linspace(0.0, smax, 250)
    pressures = np.array([
        p_nitric_torr(*ternary_panel_compositions(hno3, s), TEMPERATURE)
        for s in sulfuric
    ])
    return sulfuric, pressures


def main():
    fig, axes = plt.subplots(3, 3, figsize=(11, 9), sharex=True)
    axes = axes.ravel()

    for ax, (label, hno3), ymax in zip(axes, PANELS, Y_MAX):
        sulfuric, pressures = panel_curve(hno3)
        ax.plot(sulfuric, pressures, "k-", lw=2, label="NeqSim model")
        # Mark the predicted maximum (the headline feature of the paper).
        imax = int(np.argmax(pressures))
        if 0 < imax < len(sulfuric) - 1:
            ax.plot(sulfuric[imax], pressures[imax], "ro", ms=5)
        ax.set_xlim(0, 90)
        ax.set_ylim(0, ymax)
        ax.text(0.05, 0.90, f"{label} : {hno3:.2f} %", transform=ax.transAxes,
                fontsize=11, fontweight="bold")
        ax.grid(True, alpha=0.3)

    for ax in axes[6:]:
        ax.set_xlabel("wt % H$_2$SO$_4$")
    for row in range(3):
        axes[row * 3].set_ylabel("P HNO$_3$ (torr)")

    fig.suptitle("Figure 7 reproduction: P$_{HNO_3}$ over H$_2$O/HNO$_3$/H$_2$SO$_4$ "
                 "at 273 K (NeqSim Taleb et al. 1996 model)", fontsize=12)
    fig.tight_layout(rect=[0, 0, 1, 0.97])
    out = "validate_fig7_nitric_ternary.png"
    fig.savefig(out, dpi=150)
    print(f"saved {out}")

    # Report the maximum location for each panel against the paper.
    print(f"{'panel':5} {'HNO3%':>7} {'peakP':>8} {'at wt%':>7} {'paper':>7}")
    for label, hno3 in PANELS:
        sulfuric, pressures = panel_curve(hno3)
        imax = int(np.argmax(pressures))
        print(f"{label:5} {hno3:7.2f} {pressures[imax]:8.2f} "
              f"{sulfuric[imax]:7.1f} {PAPER_PEAK[label]:7.1f}")


if __name__ == "__main__":
    main()
