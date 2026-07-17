"""Validation of Figure 3 of Taleb, Ponche & Mirabel (1996).

Reproduces the predicted water activity a_H2O = gamma_H2O * x_H2O in the binary
water - sulfuric acid system at 298 K, plotted against the sulfuric acid mole
fraction on a logarithmic axis spanning roughly 1e-9 to 1.
"""
import numpy as np
import matplotlib.pyplot as plt

from _neqsim_model import water_activity_from_mole_fraction

TEMPERATURE = 298.0  # K


def main():
    x_sulfuric = np.linspace(0.0, 0.85, 300)
    activity = np.array([
        water_activity_from_mole_fraction(x, TEMPERATURE) for x in x_sulfuric
    ])

    fig, ax = plt.subplots(figsize=(7, 6))
    ax.semilogy(x_sulfuric, activity, "k-", lw=2, label="NeqSim model (298 K)")
    ax.set_xlabel("X$_{H_2SO_4}$ (mole fraction)")
    ax.set_ylabel("Water activity  a$_{H_2O}$")
    ax.set_xlim(0, 0.85)
    ax.set_ylim(1e-9, 1.0)
    ax.set_title(
        "Figure 3 reproduction: water activity in H$_2$O/H$_2$SO$_4$ at 298 K")
    ax.grid(True, which="both", alpha=0.3)
    ax.legend()
    fig.tight_layout()
    out = "validate_fig3_water_activity.png"
    fig.savefig(out, dpi=150)
    print(f"saved {out}")

    for x in [0.1, 0.3, 0.5, 0.7]:
        print(
            f"x_H2SO4={x:.1f}: a_H2O={water_activity_from_mole_fraction(x, TEMPERATURE):.3e}")


if __name__ == "__main__":
    main()
