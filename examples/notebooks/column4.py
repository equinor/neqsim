"""column4.py — 7-component multicomponent column test.

Same column geometry / pressure profile as column2.py, but the top feed is
now a 7-component equimolar mix (methane .. n-pentane, 1/7 each) instead of
pure n-pentane. Main feed is still pure ethane. Reboiler T = 88.05 C.

All pressures in the COLUMN / FEED dictionaries are stored in barG and are
converted to barA internally with +1.01325 before being passed to NeqSim.

Run:
    python examples/notebooks/column4.py
    python examples/notebooks/column4.py --no-show
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np


# ---------------------------------------------------------------------------
# Repo bootstrap
# ---------------------------------------------------------------------------
def find_repo_root() -> Path:
    env = os.environ.get("NEQSIM_PROJECT_ROOT")
    if env and (Path(env) / "pom.xml").exists():
        return Path(env)
    here = Path(__file__).resolve()
    for candidate in [here.parent] + list(here.parents):
        if (candidate / "pom.xml").exists() and (candidate / "devtools" / "neqsim_dev_setup.py").exists():
            return candidate
    for guess in [Path("/workspaces/neqsim")]:
        if (guess / "pom.xml").exists():
            return guess
    raise FileNotFoundError(
        "Could not locate NeqSim repo root; set NEQSIM_PROJECT_ROOT.")


REPO_ROOT = find_repo_root()
sys.path.insert(0, str(REPO_ROOT / "devtools"))

from neqsim_dev_setup import neqsim_init, neqsim_classes  # noqa: E402
import jpype  # noqa: E402

ns = neqsim_init(project_root=REPO_ROOT)
ns = neqsim_classes(ns)

SystemSrkEos = ns.SystemSrkEos
SystemPrEos = jpype.JClass("neqsim.thermo.system.SystemPrEos")
SystemPrEos1978 = jpype.JClass("neqsim.thermo.system.SystemPrEos1978")
SystemSrkEosvolcor = jpype.JClass("neqsim.thermo.system.SystemSrkEosvolcor")
SystemPrEosvolcor = jpype.JClass("neqsim.thermo.system.SystemPrEosvolcor")
Stream = ns.Stream
DistillationColumn = ns.DistillationColumn
SolverType = jpype.JClass(
    "neqsim.process.equipment.distillation.DistillationColumn$SolverType")

EOS_CLASSES = {
    "srk":            SystemSrkEos,
    "srk-volcor":     SystemSrkEosvolcor,
    "srk-peneloux":   jpype.JClass("neqsim.thermo.system.SystemSrkPenelouxEos"),
    "srk-twu":        jpype.JClass("neqsim.thermo.system.SystemSrkTwuCoonEos"),
    "srk-twu-stat":   jpype.JClass("neqsim.thermo.system.SystemSrkTwuCoonStatoilEos"),
    "srk-schwartz":   jpype.JClass("neqsim.thermo.system.SystemSrkSchwartzentruberEos"),
    "pr":             SystemPrEos,
    "pr-volcor":      SystemPrEosvolcor,
    "pr78":           SystemPrEos1978,
}

# Defaults below give the closest match to the UniSim reference for this
# 7-component column. Override on the command line if needed.
# srk-twu gives the best overall T+composition match (max dT 0.56 C, bottom
# split error ~7.3% — limited by the residual SRK K-value difference vs
# UniSim's SRK for the iC5/nC5 pair, NOT by solver convergence).
EOS_NAME = "srk-twu"
USE_VOL_CORR = True
USE_MULTIPHASE = True
MIXING_RULE = "classic"
ZERO_KIJ = True


# ---------------------------------------------------------------------------
# Inputs
# ---------------------------------------------------------------------------
COMPS = ["methane", "ethane", "propane", "i-butane", "n-butane",
         "i-pentane", "n-pentane"]

SOLVER_NAME = "NEWTON"
# Seed every tray T with REF['T_C'] (much better convergence basin).
# Disable with --no-warm-start.
WARM_START_T = True

MAIN_FEED = {
    "name": "main feed (ethane)",
    "z": {"ethane": 1.0},
    "flow_kmol_hr": 1059.40430981003,
    "temperature_C": 77.0000001251743,
    "pressure_barg": 4.2,
}

TOP_FEED = {
    "name": "top feed (equimolar C1..nC5)",
    "z": {c: 1.0 / 7.0 for c in COMPS},
    "flow_kmol_hr": 1000.0,
    "temperature_C": 32.14,
    "pressure_barg": 3.7,
}

COLUMN = {
    "n_trays": 10,
    "main_feed_tray_topdown": 5,
    "top_feed_tray_topdown": 1,
    "top_pressure_barg": 4.00,
    "bottom_pressure_barg": 4.05,
    "reboiler_temperature_C": 88.05,
    "tray_efficiency_default": 1.0,
    "reboiler_efficiency": 1.0,
    # Optional partial condenser (set has_condenser=True to enable).
    "has_condenser": False,
    "condenser_temperature_C": 21.27,
}

ATM = 1.01325  # barG -> barA conversion offset

# UniSim / reference (top-down trays 1..10)
REF = {
    # Pressures are in barG (column runs 4.00..4.05 barG -> 5.01..5.06 barA).
    "P_barg": [
        4.00000000000000, 4.00555555555556, 4.01111111111111,
        4.01666666666667, 4.02222222222222, 4.02777777777778,
        4.03333333333333, 4.03888888888889, 4.04444444444444,
        4.05000000000000,
    ],
    "T_C": [
        21.2749178331680, 12.3945907771609, 12.9425994011545,
        14.9947113151709, 16.7454538622382, 50.1335812771740,
        75.7025218492682, 83.9059142657383, 86.1753536599182,
        87.2214626696752,
    ],
    "V_kg_hr": [
        74008.5523624094, 45834.0219054384, 44404.2088989004,
        44033.4369036980, 43922.2964748318, 1898.60245896008,
        3752.85327417567, 5901.51410401078, 6787.45954466968,
        7032.51690566189,
    ],
    "L_kg_hr": [
        21934.0129390251, 20504.1999324871, 20133.4279372846,
        20022.2875084185, 9854.77569125498, 11709.0265064706,
        13857.6873363057, 14743.6327769646, 14988.6901379568,
        15082.5895378021,
    ],
    # Mole fractions in component order: C1..nC5
    "y_top": [
        0.073309694767772, 0.616960714136994, 7.33096220661988e-002,
        7.31827732063146e-002, 7.22445086719427e-002, 4.93741528530644e-002,
        4.16185342977135e-002,
    ],
    "x_bot": [
        1.08569588192724e-018, 2.15899905392992e-005, 1.27950427844865e-006,
        2.23374369740334e-003, 1.87466392691616e-002, 0.421251245903268,
        0.557745501635349,
    ],
    # NOTE: V_kg_hr[0] is the top-vapour PRODUCT leaving the column.
    # L_kg_hr[-1] is the internal liquid entering the reboiler (NOT the
    # bottom product). The actual bottom product is reported separately:
    #   reboiler L_in  = 15082.59 kg/hr (= L_kg_hr[-1])
    #   reboiler V_out =  7126.42 kg/hr (boil-up going back up)
    #   bottom product =  7956.17 kg/hr (= L_in - V_out, leaves column)
    "L_bot_product_kg_hr": 7956.1732322949,
}


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def unisim_tray_to_ns_stage(k_topdown: int, n_trays: int) -> int:
    return n_trays + 1 - int(k_topdown)


def build_base_fluid():
    cls = EOS_CLASSES[EOS_NAME]
    fluid = cls(298.15, 1.0)
    for c in COMPS:
        fluid.addComponent(c, 1.0e-10)
    fluid.setMixingRule(MIXING_RULE)
    fluid.setMultiPhaseCheck(bool(USE_MULTIPHASE))
    fluid.useVolumeCorrection(bool(USE_VOL_CORR))
    fluid.init(0)
    if ZERO_KIJ:
        # Force ALL HC-HC kij to zero (UniSim default for hydrocarbon SRK).
        # Recipe: TPflash first to materialise the mixing rule on every phase,
        # then setBinaryInteractionParameter(ij) AND ji on getPhases()[0..n-1],
        # then TPflash again to propagate the new BIPs.
        ThermodynamicOperations = jpype.JClass(
            "neqsim.thermodynamicoperations.ThermodynamicOperations")
        # Need a non-trivial composition for the flash; use equimolar dummy.
        n = len(COMPS)
        fluid.setMolarComposition([1.0 / n] * n)
        ops = ThermodynamicOperations(fluid)
        try:
            ops.TPflash()
        except Exception:
            pass
        # Always iterate phases 0 and 1 (don't trust getNumberOfPhases — the
        # second phase may not have materialised yet for a single-phase flash).
        # Use ONLY the symmetric setter — the "ij"/"ji" directional setters
        # on Schwartzentruber/TwuCoon mixing rules overwrite alpha-function
        # parameters (L, M, N or temperature-dependent kij coefficients),
        # which destroys the EOS for srk-twu and biases srk-schwartz.
        for p in (0, 1):
            try:
                mr = fluid.getPhases()[p].getMixingRule()
            except Exception:
                continue
            for i in range(n):
                for j in range(n):
                    if i == j:
                        continue
                    try:
                        mr.setBinaryInteractionParameter(i, j, 0.0)
                    except Exception:
                        pass
        try:
            ops.TPflash()
        except Exception:
            pass
        # Verify
        try:
            mr = fluid.getPhases()[0].getMixingRule()
            print(f"[kij0] applied. Sample kij[0,5]={mr.getBinaryInteractionParameter(0,5):.6f}"
                  f"  kij[5,6]={mr.getBinaryInteractionParameter(5,6):.6f}")
            # Also check if directional getter exists
            for getter in ("getBinaryInteractionParameterij",
                           "getBinaryInteractionParameterji"):
                try:
                    v = getattr(mr, getter)(0, 5)
                    print(f"[kij0] {getter}(0,5)={v:.6f}")
                except Exception:
                    pass
        except Exception as e:
            print(f"[kij0] verify failed: {e}")
    return fluid


def make_stream(base, feed):
    P_bara = float(feed["pressure_barg"]) + ATM
    f = base.clone()
    f.setTemperature(feed["temperature_C"], "C")
    f.setPressure(P_bara, "bara")
    z = []
    for c in COMPS:
        z.append(max(float(feed["z"].get(c, 0.0)), 1e-30))
    s_sum = sum(z)
    z = [v / s_sum for v in z]
    f.setMolarComposition(z)
    f.init(0)
    s = Stream(feed["name"], f)
    s.setTemperature(feed["temperature_C"], "C")
    s.setPressure(P_bara, "bara")
    s.setFlowRate(float(feed["flow_kmol_hr"]), "kmole/hr")
    s.run()
    return s


def build_column(main_feed, top_feed):
    n_trays = int(COLUMN["n_trays"])
    has_cond = bool(COLUMN.get("has_condenser", False))
    col = DistillationColumn("col4", n_trays, True, has_cond)
    main_ns = unisim_tray_to_ns_stage(
        COLUMN["main_feed_tray_topdown"], n_trays)
    top_ns = unisim_tray_to_ns_stage(COLUMN["top_feed_tray_topdown"], n_trays)
    col.addFeedStream(main_feed, main_ns)
    col.addFeedStream(top_feed,  top_ns)

    P_top = float(COLUMN["top_pressure_barg"]) + ATM
    P_bot = float(COLUMN["bottom_pressure_barg"]) + ATM
    col.setTopPressure(P_top)
    if n_trays >= 2:
        P_bot_eff = P_top + (P_bot - P_top) * (n_trays / (n_trays - 1.0))
    else:
        P_bot_eff = P_bot
    col.setBottomPressure(P_bot_eff)

    col.getReboiler().setOutTemperature(
        273.15 + COLUMN["reboiler_temperature_C"])
    if has_cond:
        col.getCondenser().setOutTemperature(
            273.15 + float(COLUMN["condenser_temperature_C"]))

    col.setMurphreeEfficiency(float(COLUMN["tray_efficiency_default"]))
    col.setMurphreeEfficiency(0, float(COLUMN["reboiler_efficiency"]))

    # Optional warm start: seed every tray T with the reference profile so the
    # NS solver's Newton initializer starts near the correct basin. Unlike
    # setOutTemperature (which pins T as a MESH constraint), setSeedTemperature
    # only provides an initial guess — T remains a free Newton variable, so
    # the energy balance and flows are still self-consistent.
    if WARM_START_T:
        n = n_trays
        for k in range(1, n + 1):              # k=1 top .. k=n bottom (UniSim)
            ns_stage = n + 1 - k               # NS index for trays 1..n
            t_ref_K = 273.15 + float(REF["T_C"][k - 1])
            col.setSeedTemperature(ns_stage, t_ref_K)

    col.setSolverType(SolverType.valueOf(SOLVER_NAME))
    col.setMaxNumberOfIterations(2000)
    # Tighten convergence tolerances by ~3 orders of magnitude from defaults
    # (default 4e-3 K / 1.6e-2 mass / 1.6e-2 enthalpy). These drive the per-
    # component mass-balance residual at the outlet streams down to ~1e-6.
    col.setTemperatureTolerance(1.0e-6)
    col.setMassBalanceTolerance(1.0e-6)
    col.setEnthalpyBalanceTolerance(1.0e-6)
    try:
        col.setInnerLoopSteps(40)
    except Exception:
        pass
    return col, main_ns, top_ns


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--no-show", action="store_true")
    ap.add_argument("--out", default=str(Path(__file__).with_suffix(".png")))
    ap.add_argument("--solver", default="NAPHTALI_SANDHOLM",
                    choices=["NAPHTALI_SANDHOLM", "INSIDE_OUT", "LEGACY_INSIDE_OUT",
                             "BOSTON_SULLIVAN_IO",
                             "NEWTON", "SUM_RATES", "WEGSTEIN",
                             "DAMPED_SUBSTITUTION", "DIRECT_SUBSTITUTION"],
                    help="Distillation solver type")
    ap.add_argument("--warm-start", dest="warm_start",
                    action=argparse.BooleanOptionalAction, default=True,
                    help="Seed every tray T with REF['T_C'] (default: on; "
                         "use --no-warm-start to disable)")
    ap.add_argument("--eos", default="srk-twu",
                    choices=list(EOS_CLASSES.keys()))
    ap.add_argument("--no-vol-corr", action="store_true",
                    help="Disable Peneloux volume correction")
    ap.add_argument("--no-multiphase", action="store_true",
                    help="Disable multi-phase check")
    ap.add_argument("--mixing-rule", default="classic",
                    help="Mixing rule name passed to setMixingRule()")
    ap.add_argument("--zero-kij", action="store_true",
                    help="Force all HC-HC kij to zero (UniSim default)")
    ap.add_argument("--tag", default="", help="Tag appended to output PNG")
    args = ap.parse_args()
    global WARM_START_T, EOS_NAME, USE_VOL_CORR, USE_MULTIPHASE, MIXING_RULE, ZERO_KIJ
    WARM_START_T = bool(args.warm_start)
    EOS_NAME = args.eos
    USE_VOL_CORR = not args.no_vol_corr
    USE_MULTIPHASE = not args.no_multiphase
    MIXING_RULE = args.mixing_rule
    ZERO_KIJ = bool(args.zero_kij)
    if args.tag:
        out_path = Path(args.out)
        args.out = str(out_path.with_name(
            out_path.stem + "_" + args.tag + out_path.suffix))

    base = build_base_fluid()
    main_feed = make_stream(base, MAIN_FEED)
    top_feed = make_stream(base, TOP_FEED)

    print(f"\n=== EOS={EOS_NAME}  volcor={USE_VOL_CORR}  multiphase={USE_MULTIPHASE}  "
          f"mixing={MIXING_RULE}  warm_start={WARM_START_T}  solver={args.solver} ===")
    print("\nFeed streams")
    print(f"  Main feed  : {MAIN_FEED['flow_kmol_hr']:>12.4f} kmol/hr  ethane "
          f"@ {MAIN_FEED['temperature_C']:.2f} C, "
          f"{MAIN_FEED['pressure_barg']:.4f} barG")
    print(f"  Top feed   : {TOP_FEED['flow_kmol_hr']:>12.4f} kmol/hr  "
          f"equimolar C1..nC5 @ {TOP_FEED['temperature_C']:.2f} C, "
          f"{TOP_FEED['pressure_barg']:.4f} barG")

    global SOLVER_NAME
    SOLVER_NAME = args.solver
    col, main_ns, top_ns = build_column(main_feed, top_feed)
    print(f"\nColumn: {COLUMN['n_trays']} trays + reboiler, "
          f"main feed -> NS stage {main_ns}, top feed -> NS stage {top_ns}")
    print(f"  P_top={COLUMN['top_pressure_barg']:.4f} barG, "
          f"P_bot={COLUMN['bottom_pressure_barg']:.4f} barG")
    print(f"  Reboiler T = {COLUMN['reboiler_temperature_C']:.2f} C")
    print(f"  Solver = {SOLVER_NAME}")

    col.run()

    try:
        iters = int(col.getLastIterationCount())
        converged = bool(col.solved())
    except Exception:
        iters, converged = None, None
    print(f"\nConvergence : iters={iters}, converged={converged}")

    top = col.getGasOutStream()
    bot = col.getLiquidOutStream()
    top.run()
    bot.run()

    # --- Per-tray profile -------------------------------------------------
    n_trays = int(COLUMN["n_trays"])
    Ts, Vs, Ls, Ps = [], [], [], []
    print("\nPer-tray profile (top-down)  NeqSim vs reference")
    print(f"  {'k':>3} {'NS':>3}  "
          f"{'T_ns':>7} {'T_ref':>7} {'dT':>6}  "
          f"{'P_ns':>8} {'P_ref':>8}  "
          f"{'V_ns':>10} {'V_ref':>10} {'dV%':>7}  "
          f"{'L_ns':>10} {'L_ref':>10} {'dL%':>7}")
    for k in range(1, n_trays + 1):
        nsst = unisim_tray_to_ns_stage(k, n_trays)
        tray = col.getTray(nsst)
        f = tray.getFluid()
        T = float(f.getTemperature("C"))
        P_barg = float(f.getPressure("bara")) - ATM
        try:
            V = float(tray.getGasOutStream().getFluid().getFlowRate("kg/hr"))
        except Exception:
            V = float("nan")
        try:
            L = float(tray.getLiquidOutStream(
            ).getFluid().getFlowRate("kg/hr"))
        except Exception:
            L = float("nan")
        T_r = REF["T_C"][k - 1]
        V_r = REF["V_kg_hr"][k - 1]
        L_r = REF["L_kg_hr"][k - 1]
        P_r = REF["P_barg"][k - 1]
        dT = T - T_r
        dV = 100.0 * (V - V_r) / V_r if V_r else float("nan")
        dL = 100.0 * (L - L_r) / L_r if L_r else float("nan")
        print(f"  {k:>3} {nsst:>3}  "
              f"{T:>7.2f} {T_r:>7.2f} {dT:>+6.2f}  "
              f"{P_barg:>8.4f} {P_r:>8.4f}  "
              f"{V:>10.1f} {V_r:>10.1f} {dV:>+7.1f}  "
              f"{L:>10.1f} {L_r:>10.1f} {dL:>+7.1f}")
        Ts.append(T)
        Vs.append(V)
        Ls.append(L)
        Ps.append(P_barg)

    # T/V/L deviations
    def rms(a):
        a = np.asarray(a, dtype=float)
        return float(np.sqrt(np.mean(a * a)))

    dT_arr = [Ts[i] - REF["T_C"][i] for i in range(n_trays)]
    dV_arr = [Vs[i] - REF["V_kg_hr"][i] for i in range(n_trays)]
    dL_arr = [Ls[i] - REF["L_kg_hr"][i] for i in range(n_trays)]
    print(f"\n  T: max |dT| = {max(abs(d) for d in dT_arr):.2f} C, "
          f"RMS = {rms(dT_arr):.2f} C")
    print(f"  V: max |dV| = {max(abs(d) for d in dV_arr):.1f} kg/hr, "
          f"RMS = {rms(dV_arr):.1f} kg/hr")
    print(f"  L: max |dL| = {max(abs(d) for d in dL_arr):.1f} kg/hr, "
          f"RMS = {rms(dL_arr):.1f} kg/hr")

    # --- Composition comparison ------------------------------------------
    # Use per-phase mole fractions (getx) rather than the overall composition
    # (getz / getMolarComposition).  If an outlet stream is 2-phase (e.g. a
    # tiny vapour fraction in the reboiler liquid product), z != x_liquid.
    def _phase_x(fluid, want):
        """Return mole fractions of the GAS or OIL/LIQUID phase if present,
        falling back to the overall composition for a single-phase stream."""
        n = fluid.getNumberOfPhases()
        for i in range(n):
            t = fluid.getPhase(i).getType().toString()
            if (want == "GAS" and t == "GAS") or (
                want == "LIQUID" and t in ("OIL", "LIQUID", "AQUEOUS")):
                ph = fluid.getPhase(i)
                return [float(ph.getComponent(j).getx())
                        for j in range(fluid.getNumberOfComponents())]
        return [float(v) for v in fluid.getMolarComposition()]

    y_neq = _phase_x(top.getFluid(), "GAS")
    x_neq = _phase_x(bot.getFluid(), "LIQUID")
    print("\nProduct composition (mole fraction)")
    print(f"  {'comp':<10} {'y_ref':>13} {'y_neq':>13} {'dy':>12}   "
          f"{'x_ref':>13} {'x_neq':>13} {'dx':>12}")
    print("  " + "-" * 92)
    max_dy = max_dx = 0.0
    sse_y = sse_x = 0.0
    for i, c in enumerate(COMPS):
        yr = REF["y_top"][i]
        xr = REF["x_bot"][i]
        yn = float(y_neq[i])
        xn = float(x_neq[i])
        dy = yn - yr
        dx = xn - xr
        max_dy = max(max_dy, abs(dy))
        max_dx = max(max_dx, abs(dx))
        sse_y += dy * dy
        sse_x += dx * dx
        print(f"  {c:<10} {yr:>13.5e} {yn:>13.5e} {dy:>+12.3e}   "
              f"{xr:>13.5e} {xn:>13.5e} {dx:>+12.3e}")
    rms_y = (sse_y / len(COMPS)) ** 0.5
    rms_x = (sse_x / len(COMPS)) ** 0.5
    print("  " + "-" * 92)
    print(f"  vapour: max |dy| = {max_dy:.3e}, RMS = {rms_y:.3e}")
    print(f"  liquid: max |dx| = {max_dx:.3e}, RMS = {rms_x:.3e}")

    # --- Component molar flow rates (kmol/hr) ----------------------------
    # V_top product = V_kg_hr[0] (vapour leaving top of column).
    # L_bot product = L_bot_product_kg_hr (liquid leaving reboiler, NOT the
    # internal L_kg_hr[-1] which is the liquid entering the reboiler).
    # Use NeqSim's INTERNAL component MWs (not the hardcoded dict) to avoid
    # tiny apparent mass-balance imbalances caused by rounded MW values.
    base_f = top.getFluid()
    MW = {}
    for c in COMPS:
        try:
            MW[c] = float(base_f.getComponent(c).getMolarMass()) * 1000.0
        except Exception:
            MW[c] = {"methane": 16.043, "ethane": 30.070, "propane": 44.097,
                     "i-butane": 58.124, "n-butane": 58.124,
                     "i-pentane": 72.151, "n-pentane": 72.151}[c]
    MW_top_ref = sum(REF["y_top"][i] * MW[c] for i, c in enumerate(COMPS))
    MW_bot_ref = sum(REF["x_bot"][i] * MW[c] for i, c in enumerate(COMPS))
    V_top_ref_kg = REF["V_kg_hr"][0]
    L_bot_ref_kg = REF["L_bot_product_kg_hr"]
    n_top_ref = V_top_ref_kg / MW_top_ref           # kmol/hr
    n_bot_ref = L_bot_ref_kg / MW_bot_ref           # kmol/hr

    # NeqSim outlet PRODUCT rates — get molar flow rates DIRECTLY from the
    # outlet streams (no kg/MW conversion → no precision loss).
    MW_top_neq = sum(y_neq[i] * MW[c] for i, c in enumerate(COMPS))
    MW_bot_neq = sum(x_neq[i] * MW[c] for i, c in enumerate(COMPS))
    try:
        n_top_neq = float(top.getFlowRate("kmole/hr"))
    except Exception:
        n_top_neq = Vs[0] / MW_top_neq
    try:
        L_bot_neq_kg = float(bot.getFlowRate("kg/hr"))
        n_bot_neq = float(bot.getFlowRate("kmole/hr"))
    except Exception:
        L_bot_neq_kg = float("nan")
        n_bot_neq = float("nan")
    print(f"\n  Reboiler internal: L_in_ref = {REF['L_kg_hr'][-1]:.1f} kg/hr,"
          f"  V_boilup_ref = {REF['L_kg_hr'][-1] - L_bot_ref_kg:.1f} kg/hr")
    print(f"  Bottom PRODUCT mass: ref = {L_bot_ref_kg:.2f} kg/hr,"
          f"  neq = {L_bot_neq_kg:.2f} kg/hr,"
          f"  diff = {L_bot_neq_kg - L_bot_ref_kg:+.2f} kg/hr"
          f" ({100*(L_bot_neq_kg - L_bot_ref_kg)/L_bot_ref_kg:+.2f} %)")

    print("\nComponent molar flow rates (kmol/hr)")
    print(f"  Top product   total: ref={n_top_ref:8.2f}  "
          f"neq={n_top_neq:8.2f}  d={n_top_neq-n_top_ref:+7.2f} "
          f"({100*(n_top_neq-n_top_ref)/n_top_ref:+5.2f}%)")
    print(f"  Bot product   total: ref={n_bot_ref:8.2f}  "
          f"neq={n_bot_neq:8.2f}  d={n_bot_neq-n_bot_ref:+7.2f} "
          f"({100*(n_bot_neq-n_bot_ref)/n_bot_ref:+5.2f}%)")
    print(f"  {'comp':<10} "
          f"{'n_top_ref':>11} {'n_top_neq':>11} {'dn_top':>9} "
          f"  {'n_bot_ref':>11} {'n_bot_neq':>11} {'dn_bot':>9}")
    print("  " + "-" * 80)
    for i, c in enumerate(COMPS):
        nt_r = n_top_ref * REF["y_top"][i]
        nt_n = n_top_neq * y_neq[i]
        nb_r = n_bot_ref * REF["x_bot"][i]
        nb_n = n_bot_neq * x_neq[i]
        print(f"  {c:<10} "
              f"{nt_r:>11.3f} {nt_n:>11.3f} {nt_n-nt_r:>+9.3f} "
              f"  {nb_r:>11.3f} {nb_n:>11.3f} {nb_n-nb_r:>+9.3f}")

    # --- Per-component mass balance: feed_in vs out_V + out_L ----------
    # Feeds (from MAIN_FEED + TOP_FEED definitions)
    F_main_kmol = float(MAIN_FEED["flow_kmol_hr"])
    F_top_kmol = float(TOP_FEED["flow_kmol_hr"])
    feed_in_kmol = []
    for c in COMPS:
        m = F_main_kmol * float(MAIN_FEED["z"].get(c, 0.0))
        t = F_top_kmol * float(TOP_FEED["z"].get(c, 0.0))
        feed_in_kmol.append(m + t)

    def _balance_table(label, n_top, n_bot, y, x):
        print(f"\nMass balance ({label}) — kmol/hr")
        print(f"  {'comp':<10} {'feed_in':>10} {'out_V':>10} {'out_L':>10} "
              f"{'out_sum':>10} {'imbal':>10} {'%':>8}")
        print("  " + "-" * 70)
        tot = [0.0] * 4
        for i, c in enumerate(COMPS):
            fi = feed_in_kmol[i]
            ov = n_top * y[i]
            ol = n_bot * x[i]
            osum = ov + ol
            imb = osum - fi
            pct = (100 * imb / fi) if fi > 1e-12 else float("nan")
            tot[0] += fi; tot[1] += ov; tot[2] += ol; tot[3] += imb
            print(f"  {c:<10} {fi:>10.3f} {ov:>10.3f} {ol:>10.3f} "
                  f"{osum:>10.3f} {imb:>+10.3f} {pct:>+7.2f}")
        print("  " + "-" * 70)
        print(f"  {'TOTAL':<10} {tot[0]:>10.3f} {tot[1]:>10.3f} {tot[2]:>10.3f} "
              f"{tot[1]+tot[2]:>10.3f} {tot[3]:>+10.3f} "
              f"{100*tot[3]/tot[0]:>+7.2f}")

    _balance_table("REFERENCE", n_top_ref, n_bot_ref, REF["y_top"], REF["x_bot"])
    _balance_table("NEQSIM   ", n_top_neq, n_bot_neq, y_neq, x_neq)

    # Side-by-side mass balance in kg/hr for i-pentane specifically
    iC5 = COMPS.index("i-pentane")
    fi_kg = feed_in_kmol[iC5] * MW["i-pentane"]
    print(f"\ni-pentane mass balance (kg/hr):")
    print(f"  feed_in   = {fi_kg:8.2f}")
    print(f"  REF   out_V={n_top_ref*REF['y_top'][iC5]*MW['i-pentane']:8.2f}  "
          f"out_L={n_bot_ref*REF['x_bot'][iC5]*MW['i-pentane']:8.2f}  "
          f"sum={(n_top_ref*REF['y_top'][iC5]+n_bot_ref*REF['x_bot'][iC5])*MW['i-pentane']:8.2f}  "
          f"imbal={((n_top_ref*REF['y_top'][iC5]+n_bot_ref*REF['x_bot'][iC5])*MW['i-pentane'] - fi_kg):+8.2f}")
    print(f"  NEQ   out_V={n_top_neq*y_neq[iC5]*MW['i-pentane']:8.2f}  "
          f"out_L={n_bot_neq*x_neq[iC5]*MW['i-pentane']:8.2f}  "
          f"sum={(n_top_neq*y_neq[iC5]+n_bot_neq*x_neq[iC5])*MW['i-pentane']:8.2f}  "
          f"imbal={((n_top_neq*y_neq[iC5]+n_bot_neq*x_neq[iC5])*MW['i-pentane'] - fi_kg):+8.2f}")

    # --- Per-component imbalance (kmol/hr): out_V + out_L - feed_in -----
    imb_ref = []
    imb_neq = []
    pct_ref = []
    pct_neq = []
    for i in range(len(COMPS)):
        fi = feed_in_kmol[i]
        ov_r = n_top_ref * REF["y_top"][i]
        ol_r = n_bot_ref * REF["x_bot"][i]
        ov_n = n_top_neq * y_neq[i]
        ol_n = n_bot_neq * x_neq[i]
        imb_ref.append(ov_r + ol_r - fi)
        imb_neq.append(ov_n + ol_n - fi)
        pct_ref.append(100.0 * (ov_r + ol_r - fi) / fi if fi > 1e-12 else 0.0)
        pct_neq.append(100.0 * (ov_n + ol_n - fi) / fi if fi > 1e-12 else 0.0)

    # --- Reboiler T diagnostic ------------------------------------------
    T_reb_spec = float(COLUMN["reboiler_temperature_C"])
    try:
        reb_fluid = col.getReboiler().getFluid()
        T_reb_ns = float(reb_fluid.getTemperature("C"))
    except Exception:
        T_reb_ns = float("nan")
    T_reb_ref_bot = float(REF["T_C"][-1])
    print("\nReboiler T (C):"
          f"  spec = {T_reb_spec:.3f},"
          f"  NeqSim = {T_reb_ns:.3f},"
          f"  REF bot tray = {T_reb_ref_bot:.3f},"
          f"  d(NS-spec) = {T_reb_ns - T_reb_spec:+.3f}")

    # --- Plots ------------------------------------------------------------
    trays_td = list(range(1, n_trays + 1))
    fig, axes = plt.subplots(3, 2, figsize=(12, 13))

    ax = axes[0, 0]
    ax.plot(Ts, trays_td, "o-", label="NeqSim", color="tab:red")
    ax.plot(REF["T_C"], trays_td, "s--", label="Reference",
            color="tab:blue", alpha=0.7)
    ax.invert_yaxis()
    ax.set_xlabel("Temperature (C)")
    ax.set_ylabel("Tray (top-down)")
    ax.set_title("Temperature profile")
    ax.legend()
    ax.grid(True, alpha=0.3)

    ax = axes[0, 1]
    ax.plot(Vs, trays_td, "o-", color="tab:blue", label="V NeqSim")
    ax.plot(REF["V_kg_hr"], trays_td, "s--", color="tab:blue",
            alpha=0.5, label="V ref")
    ax.plot(Ls, trays_td, "o-", color="tab:green", label="L NeqSim")
    ax.plot(REF["L_kg_hr"], trays_td, "s--", color="tab:green",
            alpha=0.5, label="L ref")
    ax.invert_yaxis()
    ax.set_xlabel("Flow (kg/hr)")
    ax.set_ylabel("Tray (top-down)")
    ax.set_title("Internal V/L profile")
    ax.legend(fontsize=8)
    ax.grid(True, alpha=0.3)

    x_idx = np.arange(len(COMPS))
    width = 0.38
    ax = axes[1, 0]
    ax.bar(x_idx - width / 2, REF["y_top"], width, label="Ref",
           color="tab:blue", edgecolor="black", lw=0.4)
    ax.bar(x_idx + width / 2, [float(v) for v in y_neq], width,
           label="NeqSim", color="tab:red", edgecolor="black", lw=0.4)
    ax.set_xticks(x_idx)
    ax.set_xticklabels(COMPS, rotation=45, ha="right")
    ax.set_ylabel("Mole fraction")
    ax.set_title("Overhead vapour y")
    ax.legend()
    ax.grid(True, axis="y", alpha=0.3)

    ax = axes[1, 1]
    ax.bar(x_idx - width / 2, REF["x_bot"], width, label="Ref",
           color="tab:blue", edgecolor="black", lw=0.4)
    ax.bar(x_idx + width / 2, [float(v) for v in x_neq], width,
           label="NeqSim", color="tab:red", edgecolor="black", lw=0.4)
    ax.set_xticks(x_idx)
    ax.set_xticklabels(COMPS, rotation=45, ha="right")
    ax.set_ylabel("Mole fraction")
    ax.set_title("Reboiler bottoms x")
    ax.legend()
    ax.grid(True, axis="y", alpha=0.3)

    # Row 3, panel [2,0]: per-component mass balance (imbalance %)
    ax = axes[2, 0]
    ax.bar(x_idx - width / 2, pct_ref, width, label="Ref",
           color="tab:blue", edgecolor="black", lw=0.4)
    ax.bar(x_idx + width / 2, pct_neq, width, label="NeqSim",
           color="tab:red", edgecolor="black", lw=0.4)
    ax.axhline(0.0, color="black", lw=0.6)
    ax.set_xticks(x_idx)
    ax.set_xticklabels(COMPS, rotation=45, ha="right")
    ax.set_ylabel("Imbalance (%) = (out_V + out_L − feed_in) / feed_in")
    ax.set_title("Component mass balance — relative imbalance")
    ax.legend()
    ax.grid(True, axis="y", alpha=0.3)

    # Row 3, panel [2,1]: reboiler T (specified vs NeqSim vs ref bottom tray)
    ax = axes[2, 1]
    labels = ["Spec", "NeqSim", "Ref bot"]
    vals = [T_reb_spec, T_reb_ns, T_reb_ref_bot]
    colors = ["tab:gray", "tab:red", "tab:blue"]
    bars = ax.bar(labels, vals, color=colors, edgecolor="black", lw=0.4)
    for b, v in zip(bars, vals):
        ax.text(b.get_x() + b.get_width() / 2.0, v,
                f"{v:.2f} C", ha="center", va="bottom", fontsize=9)
    ax.set_ylabel("Temperature (C)")
    dT_ns = T_reb_ns - T_reb_spec
    ax.set_title(f"Reboiler T   (NS − spec = {dT_ns:+.3f} C)")
    ymin = min(vals) - 1.0
    ymax = max(vals) + 1.5
    ax.set_ylim(ymin, ymax)
    ax.grid(True, axis="y", alpha=0.3)

    fig.suptitle("col4 — 7-component C1..nC5 column (NeqSim vs reference)",
                 fontweight="bold")
    fig.tight_layout()
    fig.savefig(args.out, dpi=120, bbox_inches="tight")
    print(f"\nPlot saved to: {args.out}")
    if not args.no_show:
        plt.show()


if __name__ == "__main__":
    main()
