"""Validation of Figure 2 of Taleb, Ponche & Mirabel (1996).

Reproduces the predicted water and nitric acid partial pressures over the binary
water - nitric acid system as a function of 1/T, for several fixed nitric acid mole
fractions. Pressures are plotted on a logarithmic axis in torr.
"""
import numpy as np
import matplotlib.pyplot as plt

from _neqsim_model import get_model, PA_TO_TORR

# Nitric acid mole fractions of the binary mixture (paper Figure 2 labels).
X_NITRIC = [0.100, 0.155, 0.200, 0.250, 0.300, 0.400, 0.468]

# Temperature range for the binary system (K), matching the paper's 1/T axes.
T_MIN, T_MAX = 210.0, 285.0


def main():
    model = get_model()
    temperatures = np.linspace(T_MIN, T_MAX, 200)
    inv_t = 1.0 / temperatures

    fig, (ax_w, ax_n) = plt.subplots(1, 2, figsize=(13, 6))

    for x2 in X_NITRIC:
        x1 = 1.0 - x2
        p_water = np.array([
            model.partialPressureWater(x1, x2, 0.0, float(t)) * PA_TO_TORR
            for t in temperatures
        ])
        p_nitric = np.array([
            model.partialPressureNitricAcid(x1, x2, 0.0, float(t)) * PA_TO_TORR
            for t in temperatures
        ])
        ax_w.semilogy(inv_t, p_water, lw=2, label=f"x$_{{HNO_3}}$={x2:.3f}")
        ax_n.semilogy(inv_t, p_nitric, lw=2, label=f"x$_{{HNO_3}}$={x2:.3f}")

    for ax, title, ylab in [
        (ax_w, "P$_{H_2O}$ over H$_2$O/HNO$_3$", "P H$_2$O (torr)"),
        (ax_n, "P$_{HNO_3}$ over H$_2$O/HNO$_3$", "P HNO$_3$ (torr)"),
    ]:
        ax.set_xlabel("1/T (1/K)")
        ax.set_ylabel(ylab)
        ax.set_title(title)
        ax.grid(True, which="both", alpha=0.3)
        ax.legend(fontsize=8)

    fig.suptitle("Figure 2 reproduction: binary H$_2$O/HNO$_3$ vapour pressures "
                 "(NeqSim Taleb et al. 1996 model)", fontsize=12)
    fig.tight_layout(rect=[0, 0, 1, 0.96])
    out = "validate_fig2_binary_nitric.png"
    fig.savefig(out, dpi=150)
    print(f"saved {out}")


if __name__ == "__main__":
    main()
