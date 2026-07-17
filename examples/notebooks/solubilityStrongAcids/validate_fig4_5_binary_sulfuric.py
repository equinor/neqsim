"""Validation of Figures 4 and 5 of Taleb, Ponche & Mirabel (1996).

Figure 4: water partial pressure over the binary water - sulfuric acid system as a
function of 1/T for several fixed H2SO4 weight percentages.

Figure 5: water partial pressure over the binary water - sulfuric acid system as a
function of H2SO4 weight percent at 200 K and 250 K.
"""
import numpy as np
import matplotlib.pyplot as plt

from _neqsim_model import get_model, mole_fractions, PA_TO_TORR


def p_water_torr_binary(w_sulfuric, temperature):
    """Water partial pressure (torr) for a binary H2O/H2SO4 mixture at given wt%."""
    model = get_model()
    x1, x2, x3 = mole_fractions(100.0 - w_sulfuric, 0.0, w_sulfuric)
    return model.partialPressureWater(x1, x2, x3, float(temperature)) * PA_TO_TORR


def main():
    fig, (ax4, ax5) = plt.subplots(1, 2, figsize=(13, 6))

    # Figure 4: P_H2O vs 1/T for several H2SO4 wt%.
    sulfuric_wt_levels = [33.85, 40.0, 50.0, 60.0, 70.0]
    temperatures = np.linspace(200.0, 300.0, 200)
    inv_t = 1.0 / temperatures
    for w in sulfuric_wt_levels:
        p = np.array([p_water_torr_binary(w, t) for t in temperatures])
        ax4.semilogy(inv_t, p, lw=2, label=f"{w:.1f} wt% H$_2$SO$_4$")
    ax4.set_xlabel("1/T (1/K)")
    ax4.set_ylabel("P H$_2$O (torr)")
    ax4.set_title("Figure 4: P$_{H_2O}$ over H$_2$O/H$_2$SO$_4$ vs 1/T")
    ax4.grid(True, which="both", alpha=0.3)
    ax4.legend(fontsize=9)

    # Figure 5: P_H2O vs H2SO4 wt% at 200 K and 250 K.
    sulfuric_wt = np.linspace(0.0, 80.0, 200)
    for temperature, style in [(200.0, "k-"), (250.0, "b-")]:
        p = np.array([p_water_torr_binary(w, temperature)
                     for w in sulfuric_wt])
        ax5.semilogy(sulfuric_wt, p, style, lw=2, label=f"{temperature:.0f} K")
    ax5.set_xlabel("wt % H$_2$SO$_4$")
    ax5.set_ylabel("P H$_2$O (torr)")
    ax5.set_title("Figure 5: P$_{H_2O}$ over H$_2$O/H$_2$SO$_4$ vs wt %")
    ax5.set_xlim(0, 80)
    ax5.grid(True, which="both", alpha=0.3)
    ax5.legend(fontsize=9)

    fig.suptitle("Figures 4 & 5 reproduction: binary H$_2$O/H$_2$SO$_4$ water vapour "
                 "pressure (NeqSim Taleb et al. 1996 model)", fontsize=12)
    fig.tight_layout(rect=[0, 0, 1, 0.96])
    out = "validate_fig4_5_binary_sulfuric.png"
    fig.savefig(out, dpi=150)
    print(f"saved {out}")


if __name__ == "__main__":
    main()
