"""
Acid solubility in high-pressure CO2 using acid-specific models.

H2SO4 is also fitted with a gamma-phi model:
    liquid source fugacity = x_H2SO4 * gamma_H2SO4(Van Laar) * P0_H2SO4
    CO2-rich phase fugacity = y_H2SO4 * phi_H2SO4(SRK) * P
    kij(CO2-H2SO4) = continuous linear temperature-dependent function

HNO3 is fitted with a gamma-phi model:
    liquid source fugacity = x_HNO3 * gamma_HNO3(Van Laar) * P0_HNO3
    CO2-rich phase fugacity = y_HNO3 * phi_HNO3(SRK) * P

The fitted HNO3 SRK-side parameters are:
    Tc = 578.43 K, Pc = 107.44 bar, omega = 0.849
    kij(CO2-HNO3) = continuous temperature-dependent function

The CO2-HNO3 kij function uses a smooth Gaussian-shaped dip around the dense-CO2
transition region. A single linear A + B/T expression could not match both the
40 C point and the lower 48-53 C near-100 bar data.
"""
import importlib.util
import math
import sys
from pathlib import Path

import matplotlib.patches as mpatches
import matplotlib.pyplot as plt
import numpy as np
import jpype

PROJECT_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT_ROOT / "devtools"))
SETUP_PATH = PROJECT_ROOT / "devtools" / "neqsim_dev_setup.py"
SETUP_SPEC = importlib.util.spec_from_file_location(
    "neqsim_dev_setup", SETUP_PATH)
if SETUP_SPEC is None or SETUP_SPEC.loader is None:
    raise ImportError(f"Could not load {SETUP_PATH}")
neqsim_dev_setup = importlib.util.module_from_spec(SETUP_SPEC)
SETUP_SPEC.loader.exec_module(neqsim_dev_setup)
neqsim_init = neqsim_dev_setup.neqsim_init

ns = neqsim_init(project_root=PROJECT_ROOT, recompile=False, verbose=False)
SystemSrkEos = ns.JClass("neqsim.thermo.system.SystemSrkEos")
ThermodynamicOperations = ns.JClass(
    "neqsim.thermodynamicoperations.ThermodynamicOperations")
NitricSulfuricAcidVaporPressure = ns.JClass(
    "neqsim.thermo.util.empiric.NitricSulfuricAcidVaporPressure"
)

# Experimental data: T_C, P_bar, ppm mol, source
HNO3_EXP = [
    (0.0, 100.0, 1828.0, "Rotvoll"),
    (40.0, 100.0, 2443.0, "Rotvoll"),
    (24.0, 98.6, 2150.0, "IFE"),
    (53.0, 98.6, 830.0, "IFE"),
    (53.0, 101.3, 520.0, "IFE"),
    (48.0, 99.3, 600.0, "IFE"),
    (48.0, 119.0, 1250.0, "IFE"),
    (48.0, 169.1, 2230.0, "IFE"),
]
H2SO4_EXP = [
    (25.0, 94.6, 2.26, "IFE"),
    (46.5, 77.9, 0.06, "IFE"),
    (47.2, 98.4, 1.18, "IFE"),
    (47.9, 118.6, 2.40, "IFE"),
    (48.4, 168.7, 7.70, "IFE"),
]

# Liquid-source compositions (mole fractions)
_n_hno3 = 65.0 / 63.01284
_n_h2o_n = 35.0 / 18.015
XL_HNO3 = _n_hno3 / (_n_hno3 + _n_h2o_n)
XL_H2ON = _n_h2o_n / (_n_hno3 + _n_h2o_n)

_n_h2so4 = 98.0 / 98.07848
_n_h2o_s = 2.0 / 18.015
XL_H2SO4 = _n_h2so4 / (_n_h2so4 + _n_h2o_s)
XL_H2OS = _n_h2o_s / (_n_h2so4 + _n_h2o_s)

CO2_TC = 304.13
CO2_PC = 73.77
KIJ_CO2_H2SO4_REFERENCE_T_C = 47.5
KIJ_CO2_H2SO4_REFERENCE = 0.13751412
KIJ_CO2_H2SO4_SLOPE = -0.00181899

# Fitted HNO3 SRK parameters for the Van Laar liquid + SRK vapour model.
HNO3_TC_K = 578.433819
HNO3_PC_BAR = 107.435001
HNO3_OMEGA = 0.849356
KIJ_HNO3_WATER = 0.0
KIJ_CO2_HNO3_BASE = 0.155053849
KIJ_CO2_HNO3_SLOPE = 0.00317409737
KIJ_CO2_HNO3_DIP_AMPLITUDE = 0.218833090
KIJ_CO2_HNO3_DIP_CENTER_C = 36.6198015
KIJ_CO2_HNO3_DIP_WIDTH_C = 9.54990577


def kij_co2_water(t_c):
    return 0.46851 - 98.73906 / (t_c + 273.15)


def kij_co2_hno3(t_c):
    return float(
        KIJ_CO2_HNO3_BASE
        + KIJ_CO2_HNO3_SLOPE * float(t_c)
        - KIJ_CO2_HNO3_DIP_AMPLITUDE
        * math.exp(-0.5 * ((float(t_c) - KIJ_CO2_HNO3_DIP_CENTER_C)
                           / KIJ_CO2_HNO3_DIP_WIDTH_C) ** 2.0)
    )


def kij_co2_h2so4(t_c):
    return float(
        KIJ_CO2_H2SO4_REFERENCE
        + KIJ_CO2_H2SO4_SLOPE * (float(t_c) - KIJ_CO2_H2SO4_REFERENCE_T_C)
    )


def set_hno3_srk_parameters(fluid):
    for phase_index in range(fluid.getNumberOfPhases()):
        component = fluid.getPhase(phase_index).getComponent("nitric acid")
        component.setTC(HNO3_TC_K)
        component.setPC(HNO3_PC_BAR)
        component.setAcentricFactor(HNO3_OMEGA)


def hno3_liquid_fugacity_bar(t_c):
    t_k = t_c + 273.15
    gamma = float(
        NitricSulfuricAcidVaporPressure.activityCoefficientNitricAcid(
            XL_H2ON, XL_HNO3, 0.0, t_k
        )
    )
    p0_bar = float(
        NitricSulfuricAcidVaporPressure.pureVaporPressureNitricAcid(t_k)) / 1.0e5
    return XL_HNO3 * gamma * p0_bar


def hno3_srk_fugacity_coefficient(t_c, p_bar):
    t_k = t_c + 273.15
    kij_co2_acid = kij_co2_hno3(t_c)

    fluid = SystemSrkEos(float(t_k), float(p_bar))
    fluid.addComponent("CO2", 0.99999899)
    fluid.addComponent("nitric acid", 1.0e-8)
    fluid.addComponent("water", 1.0e-6)
    fluid.createDatabase(True)
    set_hno3_srk_parameters(fluid)
    fluid.setMixingRule("classic")
    fluid.setBinaryInteractionParameter(
        "CO2", "nitric acid", float(kij_co2_acid))
    fluid.setBinaryInteractionParameter(
        "CO2", "water", float(kij_co2_water(t_c)))
    fluid.setBinaryInteractionParameter("nitric acid", "water", KIJ_HNO3_WATER)

    try:
        ThermodynamicOperations(fluid).TPflash()
    except jpype.JException:
        return float("nan")

    best_xco2 = -1.0
    fugacity_coefficient = float("nan")
    for phase_index in range(fluid.getNumberOfPhases()):
        phase = fluid.getPhase(phase_index)
        xco2 = float(phase.getComponent("CO2").getx())
        if xco2 > best_xco2:
            best_xco2 = xco2
            component = phase.getComponent("nitric acid")
            component.fugcoef(phase)
            fugacity_coefficient = float(component.getFugacityCoefficient())
    return fugacity_coefficient


def h2so4_liquid_fugacity_bar(t_c):
    t_k = t_c + 273.15
    gamma = float(
        NitricSulfuricAcidVaporPressure.activityCoefficientSulfuricAcid(
            XL_H2OS, 0.0, XL_H2SO4, t_k
        )
    )
    p0_bar = float(
        NitricSulfuricAcidVaporPressure.pureVaporPressureSulfuricAcid(t_k)) / 1.0e5
    return XL_H2SO4 * gamma * p0_bar


def h2so4_srk_fugacity_coefficient(t_c, p_bar):
    t_k = t_c + 273.15
    kij_co2_acid = kij_co2_h2so4(t_c)

    fluid = SystemSrkEos(float(t_k), float(p_bar))
    fluid.addComponent("CO2", 0.99999899)
    fluid.addComponent("sulfuric acid", 1.0e-8)
    fluid.addComponent("water", 1.0e-6)
    fluid.createDatabase(True)
    fluid.setMixingRule("classic")
    fluid.setBinaryInteractionParameter(
        "CO2", "sulfuric acid", float(kij_co2_acid))
    fluid.setBinaryInteractionParameter(
        "CO2", "water", float(kij_co2_water(t_c)))
    fluid.setBinaryInteractionParameter("sulfuric acid", "water", 0.0)

    try:
        ThermodynamicOperations(fluid).TPflash()
    except jpype.JException:
        return float("nan")

    best_xco2 = -1.0
    fugacity_coefficient = float("nan")
    for phase_index in range(fluid.getNumberOfPhases()):
        phase = fluid.getPhase(phase_index)
        xco2 = float(phase.getComponent("CO2").getx())
        if xco2 > best_xco2:
            best_xco2 = xco2
            component = phase.getComponent("sulfuric acid")
            component.fugcoef(phase)
            fugacity_coefficient = float(component.getFugacityCoefficient())
    return fugacity_coefficient


def acid_ppm_flash(t_c, p_bar, acid_name, x_acid, x_water, kij_co2_acid,
                   kij_acid_water=0.0, tune_hno3=False):
    t_k = t_c + 273.15
    n_tot = 10.0 + x_acid + x_water

    fluid = SystemSrkEos(float(t_k), float(p_bar))
    fluid.addComponent("CO2", 10.0 / n_tot)
    fluid.addComponent(acid_name, x_acid / n_tot)
    fluid.addComponent("water", x_water / n_tot)
    fluid.createDatabase(True)
    if tune_hno3:
        set_hno3_srk_parameters(fluid)
    fluid.setMixingRule("classic")
    fluid.setBinaryInteractionParameter("CO2", acid_name, float(kij_co2_acid))
    fluid.setBinaryInteractionParameter(
        "CO2", "water", float(kij_co2_water(t_c)))
    fluid.setBinaryInteractionParameter(
        acid_name, "water", float(kij_acid_water))

    try:
        ThermodynamicOperations(fluid).TPflash()
    except jpype.JException:
        return float("nan")

    if fluid.getNumberOfPhases() < 2:
        return float("nan")

    best_xco2 = -1.0
    y_acid = float("nan")
    other_xco2 = 1.0
    for phase_index in range(fluid.getNumberOfPhases()):
        phase = fluid.getPhase(phase_index)
        xco2 = float(phase.getComponent("CO2").getx())
        if xco2 > best_xco2:
            best_xco2 = xco2
            y_acid = float(phase.getComponent(acid_name).getx())
        else:
            other_xco2 = min(other_xco2, xco2)

    if best_xco2 < 0.5 or other_xco2 > 0.20:
        return float("nan")
    ppm = y_acid * 1.0e6
    return ppm if not math.isnan(ppm) and 0.0 < ppm < 1.0e6 else float("nan")


def hno3_ppm(t_c, p_bar):
    liquid_fugacity = hno3_liquid_fugacity_bar(t_c)
    vapour_phi = hno3_srk_fugacity_coefficient(t_c, p_bar)
    if not vapour_phi > 0.0 or math.isnan(vapour_phi):
        return float("nan")
    y_hno3 = liquid_fugacity / (vapour_phi * p_bar)
    ppm = y_hno3 * 1.0e6
    return ppm if 0.0 < ppm < 1.0e6 else float("nan")


def h2so4_ppm(t_c, p_bar):
    liquid_fugacity = h2so4_liquid_fugacity_bar(t_c)
    vapour_phi = h2so4_srk_fugacity_coefficient(t_c, p_bar)
    if not vapour_phi > 0.0 or math.isnan(vapour_phi):
        return float("nan")
    y_h2so4 = liquid_fugacity / (vapour_phi * p_bar)
    ppm = y_h2so4 * 1.0e6
    return ppm if 0.0 < ppm < 1.0e6 else float("nan")


def remove_plot_discontinuities(values):
    cleaned = []
    previous = None
    for value in values:
        if math.isnan(value):
            cleaned.append(None)
            previous = None
        elif previous is not None and value < 0.45 * previous:
            cleaned.append(None)
            previous = None
        else:
            cleaned.append(value)
            previous = value
    return cleaned


def print_validation():
    print("=" * 65)
    print("HNO3 Van Laar liquid + SRK vapour fit")
    print(
        f"  Tc={HNO3_TC_K:.1f} K, Pc={HNO3_PC_BAR:.1f} bar, omega={HNO3_OMEGA:.3f}")
    print(f"  kij(HNO3-H2O)={KIJ_HNO3_WATER:.4f}")
    print("  kij(CO2-HNO3)(T) = base + slope*T - amplitude*exp(-0.5*((T-center)/width)^2)")
    print(f"    base={KIJ_CO2_HNO3_BASE:.8f}")
    print(f"    slope={KIJ_CO2_HNO3_SLOPE:.8f} 1/C")
    print(f"    amplitude={KIJ_CO2_HNO3_DIP_AMPLITUDE:.8f}")
    print(f"    center={KIJ_CO2_HNO3_DIP_CENTER_C:.4f} C")
    print(f"    width={KIJ_CO2_HNO3_DIP_WIDTH_C:.4f} C")
    print("  kij(CO2-HNO3) samples:")
    for t_c in [0.0, 24.0, 40.0, 48.0, 53.0]:
        print(f"    {t_c:5.1f} C : {kij_co2_hno3(t_c):.5f}")
    print()

    print("HNO3 validation")
    print(f"  {'T C':>6} {'P bar':>7} {'exp ppm':>9} {'model ppm':>10} {'err %':>8}")
    print("  " + "-" * 50)
    hno3_errors = []
    for t_c, p_bar, ppm_exp, _src in HNO3_EXP:
        ppm_calc = hno3_ppm(t_c, p_bar)
        err = (ppm_calc / ppm_exp - 1.0) * \
            100.0 if not math.isnan(ppm_calc) else float("nan")
        if not math.isnan(err):
            hno3_errors.append(abs(err))
        print(
            f"  {t_c:6.1f} {p_bar:7.1f} {ppm_exp:9.0f} {ppm_calc:10.1f} {err:+8.1f}%")
    print(f"\n  HNO3 MAPE = {np.nanmean(hno3_errors):.1f} %")
    print()

    print("H2SO4 Van Laar liquid + SRK vapour fit")
    print("  kij(CO2-H2SO4)(T) = reference + slope*(T - Tref)")
    print(f"    reference={KIJ_CO2_H2SO4_REFERENCE:.8f}")
    print(f"    Tref={KIJ_CO2_H2SO4_REFERENCE_T_C:.2f} C")
    print(f"    slope={KIJ_CO2_H2SO4_SLOPE:.8f} 1/C")
    print("  kij(CO2-H2SO4) samples:")
    for t_c in [25.0, 46.5, 47.2, 47.9, 48.4]:
        print(f"    {t_c:5.1f} C : {kij_co2_h2so4(t_c):.5f}")
    print()

    print("H2SO4 validation")
    print(f"  {'T C':>6} {'P bar':>7} {'exp ppm':>9} {'model ppm':>10} {'err %':>8}")
    print("  " + "-" * 50)
    h2so4_errors = []
    for t_c, p_bar, ppm_exp, _src in H2SO4_EXP:
        ppm_calc = h2so4_ppm(t_c, p_bar)
        err = (ppm_calc / ppm_exp - 1.0) * \
            100.0 if not math.isnan(ppm_calc) else float("nan")
        if not math.isnan(err):
            h2so4_errors.append(abs(err))
        print(
            f"  {t_c:6.1f} {p_bar:7.1f} {ppm_exp:9.3f} {ppm_calc:10.4f} {err:+8.1f}%")
    print(f"\n  H2SO4 MAPE = {np.nanmean(h2so4_errors):.1f} %")
    print()

    print("Physical check - HNO3 at 48 C increases with pressure:")
    for p_bar in [80, 100, 120, 150, 170, 190]:
        ppm = hno3_ppm(48.0, p_bar)
        print(f"  P={p_bar:3d} bar -> {ppm:7.0f} ppm")
    print()

    print("Physical check - HNO3 at 100 bar shows non-monotonic T behavior:")
    for t_c in [0, 24, 40, 48, 53]:
        ppm = hno3_ppm(float(t_c), 100.0)
        print(f"  T={t_c:3d} C -> {ppm:7.0f} ppm")
    print()


def make_figure():
    print("=" * 65)
    print("Generating figure")
    p_fine = np.linspace(63.0, 200.0, 90)
    t_list_hno3 = [0.0, 24.0, 40.0, 48.0, 53.0]
    t_colors = {
        0.0: "#3a7abf",
        24.0: "#2ca02c",
        40.0: "#d62728",
        48.0: "#ff7f0e",
        53.0: "#9467bd",
    }

    fig, (ax_n, ax_s) = plt.subplots(1, 2, figsize=(15, 7))
    fig.subplots_adjust(wspace=0.30)

    for t_c in t_list_hno3:
        state = "liq-CO2" if t_c + 273.15 < CO2_TC else "dense/scCO2"
        values = [hno3_ppm(t_c, p_bar) for p_bar in p_fine]
        ax_n.plot(
            p_fine,
            remove_plot_discontinuities(values),
            "-",
            color=t_colors[t_c],
            lw=2.2,
            label=f"{t_c:.0f} C ({state}), kij={kij_co2_hno3(t_c):.3f}",
        )

    for t_c, p_bar, ppm_exp, source in HNO3_EXP:
        ax_n.scatter(
            p_bar,
            ppm_exp,
            marker="s" if source == "Rotvoll" else "o",
            s=90,
            color=t_colors[t_c],
            edgecolors="k",
            linewidths=0.9,
            zorder=6,
        )

    ax_n.axvspan(63, CO2_PC + 2, alpha=0.07, color="gray")
    ax_n.axvline(CO2_PC, color="0.55", ls="--", lw=1.1)
    ax_n.text(CO2_PC - 1.5, 3900, f"CO2 Pc\n{CO2_PC:.1f} bar", color="0.40",
              fontsize=8, ha="right", va="top")
    ax_n.text(68, 250, "gas-like\nCO2", color="0.5", fontsize=9,
              ha="center", style="italic")
    ax_n.text(150, 250, "dense / liquid-like CO2\npressure increases solubility",
              color="#ff7f0e", fontsize=9, ha="center", style="italic")
    ax_n.set_xlabel("Pressure [bar]", fontsize=12)
    ax_n.set_ylabel("HNO3 in CO2-rich phase [ppm mol]", fontsize=12)
    ax_n.set_title(
        "HNO3 (65 wt% source) in CO2 - Van Laar liquid + SRK vapour\n"
        f"Tc={HNO3_TC_K:.0f} K, Pc={HNO3_PC_BAR:.0f} bar, omega={HNO3_OMEGA:.2f}; "
        "continuous kij(CO2-HNO3, T)",
        fontsize=10.5,
    )
    ax_n.set_xlim(62, 202)
    ax_n.set_ylim(0, 4200)
    ax_n.grid(True, alpha=0.3)

    model_legend = ax_n.legend(fontsize=8.5, loc="upper left", title="Gamma-phi model",
                               framealpha=0.92)
    ax_n.add_artist(model_legend)
    ax_n.legend(
        handles=[
            mpatches.Patch(fc="none", ec="k", label="Rotvoll (square)"),
            mpatches.Patch(fc="none", ec="k", label="IFE (circle)"),
        ],
        fontsize=8.5,
        loc="lower right",
        title="Experiment",
    )

    for t_line, color in [(25.0, "#3a7abf"), (47.5, "#d62728")]:
        values = [h2so4_ppm(t_line, p_bar) for p_bar in p_fine]
        ax_s.plot(
            p_fine,
            remove_plot_discontinuities(values),
            "-",
            color=color,
            lw=2.2,
            label=f"{t_line:.1f} C, kij={kij_co2_h2so4(t_line):.3f}",
        )

    for t_c, p_bar, ppm_exp, source in H2SO4_EXP:
        color = "#3a7abf" if t_c < 40 else "#d62728"
        ax_s.scatter(
            p_bar,
            ppm_exp,
            marker="o",
            s=90,
            color=color,
            edgecolors="k",
            linewidths=0.9,
            zorder=6,
            label=f"{t_c:.1f} C exp ({source})",
        )

    ax_s.axvspan(63, CO2_PC + 2, alpha=0.07, color="gray")
    ax_s.axvline(CO2_PC, color="0.55", ls="--", lw=1.1)
    ax_s.text(CO2_PC - 1.5, 9.3, "CO2 Pc",
              color="0.40", fontsize=8, ha="right")
    ax_s.text(68, 0.15, "gas-like\nCO2", color="0.5", fontsize=9,
              ha="center", style="italic")

    ax_s.set_xlabel("Pressure [bar]", fontsize=12)
    ax_s.set_ylabel("H2SO4 in CO2-rich phase [ppm mol]", fontsize=12)
    ax_s.set_title(
        "H2SO4 (98 wt% source) in CO2 - Van Laar liquid + SRK vapour\n"
        "continuous kij(CO2-H2SO4, T)",
        fontsize=10.5,
    )
    ax_s.set_xlim(62, 202)
    ax_s.set_ylim(-0.2, 10.5)
    ax_s.grid(True, alpha=0.3)
    ax_s.legend(fontsize=8.5, loc="upper left",
                ncol=1, title="Model / Experiment")

    fig.suptitle(
        "Acid solubility in high-pressure CO2\n"
        "Both acids use Van Laar liquid + SRK vapour gamma-phi with fitted CO2-acid kij(T)",
        fontsize=11,
        y=1.015,
    )

    out = PROJECT_ROOT / "examples/notebooks/acid_solubility_in_co2.png"
    fig.savefig(out, dpi=150, bbox_inches="tight")
    print(f"Saved {out.relative_to(PROJECT_ROOT)}")
    plt.show()


if __name__ == "__main__":
    print_validation()
    make_figure()
