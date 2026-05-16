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

EOS_NAME = "srk"
USE_VOL_CORR = True
USE_MULTIPHASE = True
MIXING_RULE = "classic"


# ---------------------------------------------------------------------------
# Inputs
# ---------------------------------------------------------------------------
COMPS = ["methane", "ethane", "propane", "i-butane", "n-butane",
         "i-pentane", "n-pentane"]

SOLVER_NAME = "NAPHTALI_SANDHOLM"
WARM_START_T = False  # pin every tray T to REF['T_C'] (overridden by --warm-start)

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
    col.setMaxNumberOfIterations(500)
    return col, main_ns, top_ns


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--no-show", action="store_true")
    ap.add_argument("--out", default=str(Path(__file__).with_suffix(".png")))
    ap.add_argument("--solver", default="NAPHTALI_SANDHOLM",
                    choices=["NAPHTALI_SANDHOLM", "DAMPED", "SEQUENTIAL",
                            "INSIDE_OUT"],
                    help="Distillation solver type")
    ap.add_argument("--warm-start", action="store_true",
                    help="Seed every tray T with REF['T_C'] (guess only)")
    ap.add_argument("--eos", default="srk", choices=list(EOS_CLASSES.keys()))
    ap.add_argument("--no-vol-corr", action="store_true",
                    help="Disable Peneloux volume correction")
    ap.add_argument("--no-multiphase", action="store_true",
                    help="Disable multi-phase check")
    ap.add_argument("--mixing-rule", default="classic",
                    help="Mixing rule name passed to setMixingRule()")
    ap.add_argument("--tag", default="", help="Tag appended to output PNG")
    args = ap.parse_args()
    global WARM_START_T, EOS_NAME, USE_VOL_CORR, USE_MULTIPHASE, MIXING_RULE
    WARM_START_T = bool(args.warm_start)
    EOS_NAME = args.eos
    USE_VOL_CORR = not args.no_vol_corr
    USE_MULTIPHASE = not args.no_multiphase
    MIXING_RULE = args.mixing_rule
    if args.tag:
        out_path = Path(args.out)
        args.out = str(out_path.with_name(out_path.stem + "_" + args.tag + out_path.suffix))

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
            L = float(tray.getLiquidOutStream().getFluid().getFlowRate("kg/hr"))
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
    y_neq = list(top.getFluid().getMolarComposition())
    x_neq = list(bot.getFluid().getMolarComposition())
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

    # --- Plots ------------------------------------------------------------
    trays_td = list(range(1, n_trays + 1))
    fig, axes = plt.subplots(2, 2, figsize=(12, 9))

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

    fig.suptitle("col4 — 7-component C1..nC5 column (NeqSim vs reference)",
                 fontweight="bold")
    fig.tight_layout()
    fig.savefig(args.out, dpi=120, bbox_inches="tight")
    print(f"\nPlot saved to: {args.out}")
    if not args.no_show:
        plt.show()


if __name__ == "__main__":
    main()
