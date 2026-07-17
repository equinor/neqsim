"""Optimise the Antoine parameters for pure HNO3 to reduce the ~7 % systematic
under-prediction seen in the Vandoni (1944) ternary salting-out peak data.

Strategy
--------
The Van Laar partial pressure of HNO3 at any composition (x1, x2, x3) is

    P_HNO3 = gamma_HNO3(x, T) * x2 * P0_HNO3(T)

The ~7 % under-prediction is systematic across ALL nine Vandoni panels, which
points to P0_HNO3 being slightly too low at 273 K rather than to an error in
the activity-coefficient model (which would show composition-dependent residuals).

Approach
--------
We optimise the two free Antoine parameters A and B (keeping C = 43 K fixed,
since it is determined by the low-temperature range of the Pennington 1951 data)
subject to two constraints:

  1.  NBP constraint : P0(T_NBP) = P_NBP_TORR  (experimental normal boiling point)
  2.  Low-T anchor   : P0(T_LOW)  = P_LOW_TORR  (low-temperature calibration point
                       matched to the average of the nine Vandoni peaks, i.e. +7 % vs
                       current value)

We also vary Tc, Pc, and acentric factor omega for the SRK EOS component
properties and evaluate the SRK saturation pressure against the same targets, so
the user can decide which lever makes the most sense for their application.

Run from the solubilityStrongAcids folder after './mvnw compile':

    python3 optimize_hno3_antoine.py

"""

import sys
from pathlib import Path
import numpy as np
import matplotlib.pyplot as plt
import matplotlib.gridspec as gridspec
from scipy.optimize import minimize, brentq

# --------------------------------------------------------------------------- #
# JVM setup
# --------------------------------------------------------------------------- #
PROJECT_ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(PROJECT_ROOT / "devtools"))
from neqsim_dev_setup import neqsim_init   # noqa: E402

ns = neqsim_init(project_root=PROJECT_ROOT, recompile=False, verbose=False)
AcidVP = ns.JClass(
    "neqsim.thermo.util.empiric.NitricSulfuricAcidVaporPressure")
PA_TO_TORR = 1.0 / 133.322368421

# --------------------------------------------------------------------------- #
# Known experimental anchors
# --------------------------------------------------------------------------- #
T_NBP = 356.15          # K  – normal boiling point of HNO3 at 1 atm
P_NBP_TORR = 760.0      # torr (1 atm)

# Vandoni (1944) peak P_HNO3 at 273 K (digitised from Taleb et al. 1996, Fig 7)
PANELS = [("a", 4.88), ("b", 10.00), ("c", 21.90), ("d", 30.08), ("e", 39.50),
          ("f", 49.95), ("g", 59.92), ("h", 70.06), ("i", 79.84)]
EXP_PEAK = {"a": 1.0, "b": 2.4, "c": 5.2, "d": 6.7, "e": 8.7,
            "f": 9.0, "g": 10.5, "h": 11.5, "i": 12.0}

# Literature Tc, Pc, omega for HNO3 (Perry's / DIPPR)
TC_LIT = 520.0    # K
PC_LIT = 68.9     # bar
OMEGA_LIT = 0.7144

# --------------------------------------------------------------------------- #
# Antoine equation helper  (T in K, returns P in torr)
# --------------------------------------------------------------------------- #
C_FIXED = 43.0   # K  – kept at Pennington (1951) value


def antoine_torr(T, A, B, C=C_FIXED):
    """log10(P/torr) = A - B/(T - C)."""
    return 10.0 ** (A - B / (T - C))


# Current (Pennington / Taleb) parameters
A0, B0 = 7.61628, 1486.238

# --------------------------------------------------------------------------- #
# Section 1 – Current model vs experimental anchors
# --------------------------------------------------------------------------- #
print("=" * 65)
print("Section 1: Current Antoine parameters")
print(f"  A = {A0}, B = {B0}, C = {C_FIXED}")
print()
T_low = 273.15
p0_low_curr = antoine_torr(T_low, A0, B0)
p0_nbp_curr = antoine_torr(T_NBP, A0, B0)
print(f"  P0_HNO3 at {T_low:.2f} K  = {p0_low_curr:.3f} torr")
print(
    f"  P0_HNO3 at {T_NBP:.2f} K  = {p0_nbp_curr:.3f} torr   (target {P_NBP_TORR:.0f} torr)")
print(
    f"  NBP error = {p0_nbp_curr - P_NBP_TORR:+.1f} torr  ({(p0_nbp_curr/P_NBP_TORR-1)*100:+.2f} %)")
print()

# Compute predicted Vandoni peaks with current parameters


def model_peaks(A, B, T=273.15):
    """Return dict of predicted peak P_HNO3 (torr) for each Vandoni panel."""
    scale = antoine_torr(T, A, B) / antoine_torr(T, A0, B0)
    # Peaks scale proportionally with P0 (activity coefficients unchanged)
    peaks = {}
    from _neqsim_model import get_model
    model = get_model()
    for label, hno3_wt in PANELS:
        # sweep wt% H2SO4 over [0, 100-hno3_wt]
        smax = 100.0 - hno3_wt
        x_s = np.linspace(0.0, smax, 40)
        pressures = []
        for s in x_s:
            w_w = 100.0 - hno3_wt - s
            xx = model.moleFractionsFromMassFractions(
                float(w_w), float(hno3_wt), float(s))
            p = float(model.partialPressureNitricAcid(
                xx[0], xx[1], xx[2], float(T)))
            pressures.append(p * PA_TO_TORR)
        peaks[label] = max(pressures) * scale
    return peaks


print("  Computing Vandoni peaks with current model …", flush=True)
current_peaks = model_peaks(A0, B0)
mape_curr = np.mean([abs(current_peaks[l] - EXP_PEAK[l]) / EXP_PEAK[l]
                     for l, _ in PANELS]) * 100.0
print(f"  MAPE vs Vandoni = {mape_curr:.1f} %")
print()

# --------------------------------------------------------------------------- #
# Section 2 – Analytical 2-constraint optimisation (A, B with C fixed)
# --------------------------------------------------------------------------- #
# Constraint 1: P0(T_NBP) = P_NBP_TORR
#   A - B/(T_NBP - C) = log10(P_NBP_TORR)          ...(i)
#
# Constraint 2: P0(T_LOW) = P_LOW_TORR  (current + mape_corr%)
#   A - B/(T_LOW  - C) = log10(P_LOW_TORR)          ...(ii)
#
# Solving the 2x2 linear system for A and B.


def solve_AB(T_low, p_low_torr, T_nbp=T_NBP, p_nbp=P_NBP_TORR, C=C_FIXED):
    """Return (A, B) satisfying both Antoine constraints exactly."""
    # log10(P) = A - B / (T-C)
    # let x = 1/(T-C), then log10(P) = A - B*x  =>  [1, -x1][A]   [y1]
    #                                                 [1, -x2][B] = [y2]
    x1 = 1.0 / (T_low - C)
    x2 = 1.0 / (T_nbp - C)
    y1 = np.log10(p_low_torr)
    y2 = np.log10(p_nbp)
    # A - B*x1 = y1
    # A - B*x2 = y2
    # B*(x2 - x1) = y2 - y1
    B = (y2 - y1) / (x2 - x1)
    A = y1 + B * x1
    return A, B


# Target: raise P0 at 273.15 K by exactly MAPE %  (to cancel systematic bias)
mape_frac = mape_curr / 100.0
T_low_anchor = 273.15
p_low_target = p0_low_curr * (1.0 + mape_frac)
A_opt, B_opt = solve_AB(T_low_anchor, p_low_target)

print("=" * 65)
print("Section 2: Optimised Antoine parameters (raise P0 at 273 K by MAPE)")
print(f"  Target P0_HNO3 at {T_low_anchor:.2f} K = {p_low_target:.3f} torr"
      f"  (+{mape_frac*100:.1f} % vs current {p0_low_curr:.3f} torr)")
print(f"  A_opt = {A_opt:.6f}   (was {A0})")
print(f"  B_opt = {B_opt:.6f}   (was {B0})")
print(f"  C (fixed) = {C_FIXED}")

# Verify
p0_low_opt = antoine_torr(T_low_anchor, A_opt, B_opt)
p0_nbp_opt = antoine_torr(T_NBP, A_opt, B_opt)
print()
print(f"  Check P0 at 273.15 K = {p0_low_opt:.4f} torr"
      f"  (error {(p0_low_opt/p_low_target-1)*100:+.3f} %)")
print(f"  Check P0 at NBP      = {p0_nbp_opt:.4f} torr"
      f"  (error {(p0_nbp_opt/P_NBP_TORR-1)*100:+.3f} %)")
print()

# Predicted Vandoni peaks with optimised parameters
print("  Computing Vandoni peaks with optimised model …", flush=True)
opt_peaks = model_peaks(A_opt, B_opt)
mape_opt = np.mean([abs(opt_peaks[l] - EXP_PEAK[l]) / EXP_PEAK[l]
                    for l, _ in PANELS]) * 100.0
print(f"  MAPE vs Vandoni (optimised) = {mape_opt:.1f} %  "
      f"(was {mape_curr:.1f} %)")
print()

# Check across the full calibration range 200-400 K
T_range = np.linspace(200.0, 400.0, 100)
ratio = np.array([antoine_torr(t, A_opt, B_opt) /
                 antoine_torr(t, A0, B0) for t in T_range])
print(f"  P0_opt / P0_orig range over 200-400 K: "
      f"{ratio.min():.3f} … {ratio.max():.3f}")
print()

# --------------------------------------------------------------------------- #
# Section 3 – Sensitivity study: varying A only (keeping NBP)
# --------------------------------------------------------------------------- #
print("=" * 65)
print("Section 3: Sensitivity – varying A (B adjusted to preserve NBP)")
print()
print(f"  {'delta_A':>8s}  {'A':>10s}  {'B':>12s}  {'P0@273K':>10s}  "
      f"{'MAPE%':>7s}  {'NBP_err':>9s}")
print("  " + "-" * 64)

sensitivity_rows = []
for dA in [-0.04, -0.02, 0.0, 0.02, 0.03, 0.04, 0.05, 0.06, 0.08]:
    A_try = A0 + dA
    # Keep NBP: A_try - B_try/(T_NBP - C) = log10(P_NBP) => B_try = (A_try - log10(P_NBP))*(T_NBP-C)
    B_try = (A_try - np.log10(P_NBP_TORR)) * (T_NBP - C_FIXED)
    p0_273 = antoine_torr(273.15, A_try, B_try)
    scale = p0_273 / p0_low_curr
    mape = mape_curr / scale   # approximate: MAPE scales inversely with P0
    nbp_err = antoine_torr(T_NBP, A_try, B_try) - P_NBP_TORR
    marker = " <-- optimal" if abs(dA - (A_opt - A0)) < 0.005 else ""
    print(f"  {dA:+8.3f}  {A_try:10.5f}  {B_try:12.4f}  {p0_273:10.3f}  "
          f"{mape:7.1f} %  {nbp_err:+9.2f} torr{marker}")
    sensitivity_rows.append((dA, A_try, B_try, p0_273, mape, nbp_err))

print()

# --------------------------------------------------------------------------- #
# Section 4 – SRK saturation pressure via Tc, Pc, omega (Lee-Kesler)
# --------------------------------------------------------------------------- #


def lk_log10_pr(Tr, omega):
    """Lee-Kesler log10(P_r_sat) = [f0 + omega*f1] / ln(10)."""
    f0 = 5.92714 - 6.09648 / Tr - 1.28862 * np.log(Tr) + 0.169347 * Tr ** 6
    f1 = 15.2518 - 15.6875 / Tr - 13.4721 * np.log(Tr) + 0.43577 * Tr ** 6
    return (f0 + omega * f1) / np.log(10.0)


def srk_psat_torr(T, Tc, Pc_bar, omega):
    """Approximate SRK saturation pressure (torr) using Lee-Kesler correlation."""
    Tr = T / Tc
    log10_pr = lk_log10_pr(Tr, omega)
    P_bar = Pc_bar * 10.0 ** log10_pr
    return P_bar * 1e5 / 133.322368421   # bar -> torr


print("=" * 65)
print("Section 4: SRK saturation pressure via Tc / Pc / omega")
print()
print("  Literature values:  Tc = 520 K,  Pc = 68.9 bar,  omega = 0.7144")
print()

# Baseline SRK
for T in [233.15, 253.15, 273.15, 293.15, 313.15, 356.15]:
    p_srk = srk_psat_torr(T, TC_LIT, PC_LIT, OMEGA_LIT)
    p_ant = antoine_torr(T, A0, B0)
    print(f"  T = {T:6.2f} K :  SRK(lit) = {p_srk:8.3f} torr   "
          f"Antoine = {p_ant:8.3f} torr   ratio = {p_srk/p_ant:.3f}")
print()

# Find omega that minimises SRK vs Antoine deviation at 273.15 K + NBP


def srk_nbp_err(omega, Tc=TC_LIT, Pc=PC_LIT):
    """Error in NBP (torr): SRK should give 760 torr at 356.15 K."""
    return srk_psat_torr(T_NBP, Tc, Pc, omega) - P_NBP_TORR


try:
    omega_nbp = brentq(srk_nbp_err, 0.5, 1.5)
except Exception:
    omega_nbp = OMEGA_LIT

print(f"  omega_NBP (SRK gives 760 torr at 83°C): {omega_nbp:.4f}  "
      f"(literature {OMEGA_LIT})")
p_srk_273_omega_nbp = srk_psat_torr(273.15, TC_LIT, PC_LIT, omega_nbp)
print(f"  SRK P0(273 K) with omega_NBP = {p_srk_273_omega_nbp:.3f} torr  "
      f"(Antoine = {p0_low_curr:.3f} torr)")
print()

print("  Sensitivity to omega (SRK, Tc and Pc fixed at literature values)")
print(f"  {'omega':>8s}  {'P0@273K':>10s}  {'P0@356K':>10s}  {'NBP_err':>9s}")
print("  " + "-" * 45)
for om in np.arange(0.60, 0.95, 0.05):
    p273 = srk_psat_torr(273.15, TC_LIT, PC_LIT, om)
    p356 = srk_psat_torr(T_NBP,  TC_LIT, PC_LIT, om)
    print(f"  {om:8.3f}  {p273:10.3f}  {p356:10.3f}  {p356-760:+9.1f}")
print()

print("  Sensitivity to Tc (omega and Pc fixed at literature values)")
print(f"  {'Tc [K]':>8s}  {'P0@273K':>10s}  {'P0@356K':>10s}  {'NBP_err':>9s}")
print("  " + "-" * 45)
for tc in [490.0, 500.0, 510.0, 520.0, 530.0, 540.0, 550.0]:
    p273 = srk_psat_torr(273.15, tc, PC_LIT, OMEGA_LIT)
    p356 = srk_psat_torr(T_NBP,  tc, PC_LIT, OMEGA_LIT)
    print(f"  {tc:8.1f}  {p273:10.3f}  {p356:10.3f}  {p356-760:+9.1f}")
print()

# --------------------------------------------------------------------------- #
# Section 5 – Recommended values
# --------------------------------------------------------------------------- #
print("=" * 65)
print("Section 5: RECOMMENDATIONS")
print()
print("  Antoine equation  (NitricSulfuricAcidVaporPressure.java)")
print(f"  CURRENT : A = {A0:.5f},  B = {B0:.3f},  C = {C_FIXED:.1f}")
print(f"  PROPOSED: A = {A_opt:.5f},  B = {B_opt:.3f},  C = {C_FIXED:.1f}")
print()
print("  Physical validity:")
print(f"    P0(273.15 K) changes  {p0_low_curr:.3f} -> {p0_low_opt:.3f} torr  "
      f"({(p0_low_opt/p0_low_curr-1)*100:+.1f} %)")
print(f"    P0(356.15 K) changes  {p0_nbp_curr:.3f} -> {p0_nbp_opt:.3f} torr  "
      f"(NBP error {(p0_nbp_opt/P_NBP_TORR-1)*100:+.3f} %)")
print(f"    MAPE vs Vandoni improves  {mape_curr:.1f} % -> {mape_opt:.1f} %")
print()
print("  SRK critical properties  (COMP.csv):")
print(
    f"    Tc   = {TC_LIT:.1f} K   -> KEEP  (well-established DIPPR/NIST value)")
print(
    f"    Pc   = {PC_LIT:.1f} bar -> KEEP  (well-established DIPPR/NIST value)")
print(f"    omega= {OMEGA_LIT:.4f}  -> {omega_nbp:.4f}  (adjusts SRK NBP from "
      f"{srk_psat_torr(T_NBP, TC_LIT, PC_LIT, OMEGA_LIT):.0f} to {P_NBP_TORR:.0f} torr)")
print(f"    Note: omega change has NO effect on the Van Laar model (which uses")
print(f"    the explicit Antoine equation, not SRK, for pure-component P0).")
print(f"    The omega change only improves SRK-based flash calculations with HNO3.")
print()

# --------------------------------------------------------------------------- #
# Section 6 – Figures
# --------------------------------------------------------------------------- #
fig = plt.figure(figsize=(14, 12))
gs = gridspec.GridSpec(2, 2, figure=fig, hspace=0.35, wspace=0.30)

# --- Fig A: P0_HNO3(T) original vs proposed ---
ax_a = fig.add_subplot(gs[0, 0])
T_vec = np.linspace(200.0, 390.0, 200)
p0_curr_vec = [antoine_torr(t, A0,    B0) for t in T_vec]
p0_opt_vec = [antoine_torr(t, A_opt, B_opt) for t in T_vec]
ax_a.semilogy(T_vec, p0_curr_vec, 'b-',  lw=2,
              label=f"Current  A={A0:.5f}, B={B0:.3f}")
ax_a.semilogy(T_vec, p0_opt_vec,  'r--', lw=2,
              label=f"Proposed A={A_opt:.5f}, B={B_opt:.3f}")
ax_a.axhline(760, color="k", ls=":", lw=1, label="760 torr (1 atm)")
ax_a.axvline(T_NBP,    color="k", ls="--", lw=0.8)
ax_a.axvline(273.15,   color="gray", ls="--", lw=0.8)
ax_a.scatter([273.15, T_NBP], [p0_low_curr, p0_nbp_curr],
             color='b', zorder=5, s=50)
ax_a.scatter([273.15, T_NBP], [p0_low_opt,  p0_nbp_opt],
             color='r', zorder=5, s=50, marker='D')
ax_a.set_xlabel("Temperature (K)")
ax_a.set_ylabel("P$_0$ HNO$_3$ (torr)")
ax_a.set_title("Pure HNO$_3$ vapour pressure:\ncurrent vs proposed Antoine")
ax_a.legend(fontsize=8)
ax_a.grid(True, which="both", alpha=0.3)

# --- Fig B: ratio opt/curr over T range ---
ax_b = fig.add_subplot(gs[0, 1])
ax_b.plot(T_vec, ratio[:len(T_vec)] if len(ratio) == len(T_vec) else
          [antoine_torr(t, A_opt, B_opt)/antoine_torr(t, A0, B0)
           for t in T_vec],
          'r-', lw=2)
ax_b.axhline(1.0, color='k', ls='--', lw=0.8)
ax_b.axvline(273.15, color='gray', ls='--', lw=0.8, label='273.15 K')
ax_b.axvline(T_NBP,  color='gray', ls=':',
             lw=0.8, label=f'{T_NBP:.1f} K (NBP)')
ax_b.set_xlabel("Temperature (K)")
ax_b.set_ylabel("P$_0$(proposed) / P$_0$(current)")
ax_b.set_title("Ratio of proposed to current\nHNO$_3$ vapour pressure")
ax_b.legend(fontsize=9)
ax_b.grid(True, alpha=0.3)

# --- Fig C: Vandoni peak parity before/after ---
ax_c = fig.add_subplot(gs[1, 0])
exp_vals = [EXP_PEAK[l] for l, _ in PANELS]
curr_vals = [current_peaks[l] for l, _ in PANELS]
opt_vals = [opt_peaks[l] for l, _ in PANELS]
lim = max(exp_vals) + 1.5
ax_c.plot([0, lim], [0, lim], 'k--', lw=1, label="parity")
ax_c.plot([0, lim], [0, lim*1.10], 'k:', lw=0.8, alpha=0.5, label="+10 %")
ax_c.plot([0, lim], [0, lim*0.90], 'k:', lw=0.8, alpha=0.5)
ax_c.scatter(exp_vals, curr_vals, s=80, color='b', edgecolor='k', lw=0.7,
             label=f"Current MAPE = {mape_curr:.1f} %", zorder=4)
ax_c.scatter(exp_vals, opt_vals,  s=80, color='r', edgecolor='k', lw=0.7,
             marker='D', label=f"Proposed MAPE = {mape_opt:.1f} %", zorder=5)
for i, (l, _) in enumerate(PANELS):
    ax_c.annotate(l, (exp_vals[i], curr_vals[i]), fontsize=8, color='b',
                  xytext=(2, -8), textcoords='offset points')
ax_c.set_xlim(0, lim)
ax_c.set_ylim(0, lim)
ax_c.set_xlabel("Vandoni 1944 exp. peak P$_{HNO_3}$ (torr)")
ax_c.set_ylabel("NeqSim model peak P$_{HNO_3}$ (torr)")
ax_c.set_title("Vandoni peak parity at 273 K\ncurrent vs proposed Antoine")
ax_c.legend(fontsize=8, loc='lower right')
ax_c.grid(True, alpha=0.3)

# --- Fig D: SRK P0 vs Antoine across T, omega sensitivity ---
ax_d = fig.add_subplot(gs[1, 1])
T_srk = np.linspace(220.0, 370.0, 150)
p_ant_current = np.array([antoine_torr(t, A0, B0) for t in T_srk])
ax_d.semilogy(T_srk, p_ant_current, 'b-',  lw=2, label=f"Antoine (current)")
ax_d.semilogy(T_srk, [antoine_torr(t, A_opt, B_opt) for t in T_srk],
              'r--', lw=2, label=f"Antoine (proposed)")
for om, style, col in [(0.70, ':', 'gray'), (OMEGA_LIT, '--', 'green'),
                       (omega_nbp, '-', 'orange')]:
    p_srk_vec = np.array([srk_psat_torr(t, TC_LIT, PC_LIT, om) for t in T_srk])
    ax_d.semilogy(T_srk, p_srk_vec, style, color=col, lw=1.5,
                  label=f"SRK omega={om:.4f}")
ax_d.axhline(760, color='k', ls=':', lw=0.8)
ax_d.set_xlabel("Temperature (K)")
ax_d.set_ylabel("P$_0$ HNO$_3$ (torr)")
ax_d.set_title("SRK (Lee-Kesler) vs Antoine\nomega sensitivity")
ax_d.legend(fontsize=8, loc='lower right')
ax_d.grid(True, which="both", alpha=0.3)

fig.suptitle("HNO$_3$ property optimisation for the Van Laar / SRK acid model\n"
             "(Taleb, Ponche & Mirabel 1996)", fontsize=13)
out = "optimize_hno3_antoine.png"
fig.savefig(out, dpi=150, bbox_inches='tight')
plt.show()
print(f"Saved {out}")

# --------------------------------------------------------------------------- #
# Summary table for updating the Java source
# --------------------------------------------------------------------------- #
print()
print("=" * 65)
print("Java source snippet to replace in NitricSulfuricAcidVaporPressure.java:")
print()
print("  /** Antoine coefficient A for pure HNO3 vapour pressure.")
print("   *  Optimised to match Vandoni (1944) ternary peak data at 273 K")
print("   *  while preserving the experimental normal boiling point (83 degC). */")
print(f"  private static final double HNO3_ANTOINE_A = {A_opt:.6f};")
print()
print("  /** Antoine coefficient B for pure HNO3 vapour pressure.")
print("   *  Paired with HNO3_ANTOINE_A to satisfy P0(356.15 K) = 760 torr. */")
print(f"  private static final double HNO3_ANTOINE_B = {B_opt:.6f};")
print()
print("  /** Antoine coefficient C for pure HNO3 vapour pressure [K].")
print("   *  Retained from Pennington (1951) / Taleb et al. (1996). */")
print(f"  private static final double HNO3_ANTOINE_C = {C_FIXED:.1f};")
print()
print("COMP.csv update for nitric acid:")
print(f"  Tc   = {TC_LIT:.2f} °C (= {TC_LIT:.1f} K)  -> UNCHANGED")
print(f"  Pc   = {PC_LIT:.3f} bar                 -> UNCHANGED")
print(
    f"  omega= {omega_nbp:.4f}                    -> update from {OMEGA_LIT:.4f}")
print("  (omega improves SRK-based flash with HNO3; no effect on Van Laar model)")
