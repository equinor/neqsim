"""Validation of Figure 6 of Taleb, Ponche & Mirabel (1996).

Reproduces the predicted water partial pressure P_H2O (torr) over
H2O / HNO3 / H2SO4 mixtures at 273 K, plotted against wt % H2SO4, for nine fixed
HNO3 weight percentages (total-mixture basis). The solid lines in the paper are the
model curves; this script regenerates them from the NeqSim implementation. Each
panel's curve terminates at wt % H2SO4 = 100 - wt % HNO3 (water content reaches zero).
"""
import numpy as np
import matplotlib.pyplot as plt

from _neqsim_model import p_water_torr, ternary_panel_compositions

TEMPERATURE = 273.15  # K

# Panel label -> HNO3 weight percent of the total ternary mixture.
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

# Per-row y-axis maxima reproduced from the paper (rows of three panels).
Y_MAX = [5.0, 5.0, 5.0, 4.0, 4.0, 4.0, 1.0, 1.0, 1.0]


def panel_curve(hno3):
    """Return (sulfuric_wt, P_H2O_torr) arrays for one panel (total basis)."""
    smax = 100.0 - hno3
    sulfuric = np.linspace(0.0, smax, 250)
    pressures = np.array([
        p_water_torr(*ternary_panel_compositions(hno3, s), TEMPERATURE)
        for s in sulfuric
    ])
    return sulfuric, pressures


def main():
    fig, axes = plt.subplots(3, 3, figsize=(11, 9), sharex=True)
    axes = axes.ravel()

    for ax, (label, hno3), ymax in zip(axes, PANELS, Y_MAX):
        sulfuric, pressures = panel_curve(hno3)
        ax.plot(sulfuric, pressures, "k-", lw=2, label="NeqSim model")
        ax.set_xlim(0, 90)
        ax.set_ylim(0, ymax)
        ax.text(0.05, 0.90, f"{label} : {hno3:.2f} %", transform=ax.transAxes,
                fontsize=11, fontweight="bold")
        ax.grid(True, alpha=0.3)

    for ax in axes[6:]:
        ax.set_xlabel("wt % H$_2$SO$_4$")
    for row in range(3):
        axes[row * 3].set_ylabel("P H$_2$O (torr)")

    fig.suptitle("Figure 6 reproduction: P$_{H_2O}$ over H$_2$O/HNO$_3$/H$_2$SO$_4$ "
                 "at 273 K (NeqSim Taleb et al. 1996 model)", fontsize=12)
    fig.tight_layout(rect=[0, 0, 1, 0.97])
    out = "validate_fig6_water_ternary.png"
    fig.savefig(out, dpi=150)
    print(f"saved {out}")

    # Print a couple of reference points for sanity (binary H2O-HNO3 at s=0).
    for label, hno3 in [("a", 4.88), ("c", 21.90), ("g", 59.92)]:
        w1, w2, w3 = ternary_panel_compositions(hno3, 0.0)
        print(f"panel {label}: P_H2O at 0 wt% H2SO4 = "
              f"{p_water_torr(w1, w2, w3, TEMPERATURE):.3f} torr")


if __name__ == "__main__":
    main()
