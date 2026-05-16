"""Simplified binary column test — 2-tray variant.

Identical to column2.py except:
- 2 trays + reboiler (no internal condenser)
- BOTH feeds enter tray 1 (top)
- Reboiler temperature: 90.61 C

UniSim reference targets:
- Top exit: 43.21 C, y_C2 = 0.738, total = 58800.3652890788 kg/hr
- Tray 2:   80.27 C

Set-up
------
- Binary system: ethane + n-pentane (SRK, classic mixing rule)
- 2 trays + reboiler, no internal condenser
- Top feed  (tray 1):  1000        kmol/hr of pure n-pentane  at 32.14 C / 4.71325 bara
- Main feed (tray 1):  1059.404310 kmol/hr of pure ethane     at 77.00 C / 5.21325 bara
- Pressure profile:  top 5.01325 bara, bottom 5.06325 bara
- Reboiler temperature: 90.61 C, Murphree efficiency 1.0

Run:
    python examples/notebooks/column3.py
    python examples/notebooks/column3.py --no-show
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np


# ---------------------------------------------------------------------------
# Repo bootstrap — use the LOCAL neqsim build, not the pip-installed package
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
Stream = ns.Stream
DistillationColumn = ns.DistillationColumn
SolverType = jpype.JClass(
    "neqsim.process.equipment.distillation.DistillationColumn$SolverType")


# ---------------------------------------------------------------------------
# Inputs
# ---------------------------------------------------------------------------
MAIN_FEED = {
    "name": "main feed (ethane)",
    "component": "ethane",
    "flow_kmol_hr": 1059.40430981003,
    "temperature_C": 77.0000001251743,
    "pressure_bara": 4.2 + 1.01325,        # 5.21325 bara
}

TOP_FEED = {
    "name": "top feed (n-pentane)",
    "component": "n-pentane",
    "flow_kmol_hr": 1000.0,
    "temperature_C": 32.14,
    "pressure_bara": 3.7 + 1.01325,        # 4.71325 bara
}

COLUMN = {
    "n_trays": 2,
    "main_feed_tray_topdown": 1,
    "top_feed_tray_topdown": 1,
    "top_pressure_bara": 4.00,
    "bottom_pressure_bara": 4.05,
    "reboiler_temperature_C": 90.61,
    "tray_efficiency_default": 1.0,
    "reboiler_efficiency": 1.0,
}

# UniSim reference for the 2-tray column (top-down: tray 1 = top, tray 2 = bot)
UNISIM = {
    "T_C": [43.21, 80.27],
    "overhead": {
        "T_C": 43.21,
        "y_ethane": 0.738,
        "mass_flow_kg_hr": 58800.3652890788,
    },
}


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def unisim_tray_to_ns_stage(k_topdown: int, n_trays: int) -> int:
    """UniSim top-down tray -> NeqSim bottom-up stage. NS 0 = reboiler."""
    return n_trays + 1 - int(k_topdown)


def build_base_fluid():
    """Binary ethane / n-pentane SRK fluid."""
    fluid = SystemSrkEos(298.15, 1.0)
    fluid.addComponent("ethane",    1.0e-10)
    fluid.addComponent("n-pentane", 1.0e-10)
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)
    fluid.useVolumeCorrection(True)
    fluid.init(0)
    return fluid


def make_stream(base, feed):
    """Create a single-component stream at the given T, P, molar flow."""
    f = base.clone()
    f.setTemperature(feed["temperature_C"], "C")
    f.setPressure(feed["pressure_bara"], "bara")
    n_comp = f.getNumberOfComponents()
    z = [0.0] * n_comp
    for k in range(n_comp):
        if f.getComponent(k).getName() == feed["component"]:
            z[k] = 1.0
    f.setMolarComposition(z)
    f.init(0)
    s = Stream(feed["name"], f)
    s.setTemperature(feed["temperature_C"], "C")
    s.setPressure(feed["pressure_bara"], "bara")
    s.setFlowRate(float(feed["flow_kmol_hr"]), "kmole/hr")
    s.run()
    return s


def build_column(main_feed, top_feed):
    n_trays = int(COLUMN["n_trays"])
    col = DistillationColumn("col3", n_trays, True, False)

    main_ns = unisim_tray_to_ns_stage(
        COLUMN["main_feed_tray_topdown"], n_trays)
    top_ns = unisim_tray_to_ns_stage(COLUMN["top_feed_tray_topdown"],  n_trays)
    col.addFeedStream(main_feed, main_ns)
    col.addFeedStream(top_feed,  top_ns)

    P_top = float(COLUMN["top_pressure_bara"])
    P_bot = float(COLUMN["bottom_pressure_bara"])
    col.setTopPressure(P_top)
    # Same ΔP compensation used in column2.py / column_study.py
    if n_trays >= 2:
        P_bot_eff = P_top + (P_bot - P_top) * (n_trays / (n_trays - 1.0))
    else:
        P_bot_eff = P_bot
    col.setBottomPressure(P_bot_eff)

    col.getReboiler().setOutTemperature(
        273.15 + COLUMN["reboiler_temperature_C"])

    col.setMurphreeEfficiency(float(COLUMN["tray_efficiency_default"]))
    col.setMurphreeEfficiency(0, float(COLUMN["reboiler_efficiency"]))

    col.setSolverType(SolverType.valueOf("NAPHTALI_SANDHOLM"))
    col.setMaxNumberOfIterations(300)
    return col, main_ns, top_ns


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--no-show", action="store_true")
    ap.add_argument("--out", default=str(Path(__file__).with_suffix(".png")))
    args = ap.parse_args()

    base = build_base_fluid()
    main_feed = make_stream(base, MAIN_FEED)
    top_feed = make_stream(base, TOP_FEED)

    print("\nFeed streams")
    print(f"  Main feed  : {MAIN_FEED['flow_kmol_hr']:>12.4f} kmol/hr  "
          f"{MAIN_FEED['component']}  @ {MAIN_FEED['temperature_C']:.2f} C, "
          f"{MAIN_FEED['pressure_bara']:.4f} bara")
    print(f"  Top feed   : {TOP_FEED['flow_kmol_hr']:>12.4f} kmol/hr  "
          f"{TOP_FEED['component']}  @ {TOP_FEED['temperature_C']:.2f} C, "
          f"{TOP_FEED['pressure_bara']:.4f} bara")
    total_in = MAIN_FEED["flow_kmol_hr"] + TOP_FEED["flow_kmol_hr"]
    print(f"  Total feed : {total_in:>12.4f} kmol/hr")

    col, main_ns, top_ns = build_column(main_feed, top_feed)
    print(f"\nColumn:  {COLUMN['n_trays']} trays + reboiler, "
          f"main feed -> NS stage {main_ns}, top feed -> NS stage {top_ns}")
    print(f"  P_top={COLUMN['top_pressure_bara']:.5f} bara, "
          f"P_bot={COLUMN['bottom_pressure_bara']:.5f} bara")
    print(f"  Reboiler T = {COLUMN['reboiler_temperature_C']:.2f} C, "
          f"eta_reb = {COLUMN['reboiler_efficiency']:.2f}, "
          f"eta_tray = {COLUMN['tray_efficiency_default']:.2f}")

    col.run()

    converged = bool(col.solverHasConverged()) if hasattr(col, "solverHasConverged") \
        else None
    try:
        iters = int(col.getLastIterationCount())
    except Exception:
        iters = None
    print("\nConvergence diagnostics")
    print(f"  Iterations  : {iters}")
    print(f"  Converged   : {converged}")

    # Products
    top = col.getGasOutStream()
    bot = col.getLiquidOutStream()
    top.run()
    bot.run()

    def stream_info(s, label):
        f = s.getFluid()
        T = f.getTemperature("C")
        P = f.getPressure("bara")
        m = f.getFlowRate("kg/hr")
        n = f.getFlowRate("kmole/hr")
        x_eth = f.getPhase(0).getComponent("ethane").getz()
        x_npe = f.getPhase(0).getComponent("n-pentane").getz()
        return T, P, m, n, x_eth, x_npe, label

    print("\nProduct streams")
    print(f"  {'':10s}   T (C)    P (bara)    m (kg/hr)     n (kmol/hr)   "
          f"y_C2     y_nC5")
    for s, lbl in ((top, "overhead"), (bot, "bottoms")):
        T, P, m, n, xc2, xc5, _ = stream_info(s, lbl)
        print(f"  {lbl:10s} {T:8.3f} {P:8.4f}   {m:12.2f}  {n:12.4f}   "
              f"{xc2:6.4f}   {xc5:6.4f}")

    # UniSim overhead deviation
    u_oh = UNISIM["overhead"]
    T_top, _, m_top, _, y_c2_top, _, _ = stream_info(top, "overhead")
    print("\nOverhead vs UniSim")
    print(f"  T_top      : NeqSim {T_top:8.3f}  UniSim {u_oh['T_C']:8.3f}   "
          f"dT = {T_top - u_oh['T_C']:+.3f} C")
    print(f"  y_ethane   : NeqSim {y_c2_top:8.4f}  UniSim {u_oh['y_ethane']:8.4f}   "
          f"dy = {y_c2_top - u_oh['y_ethane']:+.4f}")
    print(f"  mass flow  : NeqSim {m_top:10.2f}  UniSim {u_oh['mass_flow_kg_hr']:10.2f}   "
          f"dm = {m_top - u_oh['mass_flow_kg_hr']:+.2f} kg/hr "
          f"({100.0 * (m_top - u_oh['mass_flow_kg_hr']) / u_oh['mass_flow_kg_hr']:+.2f} %)")

    # Mass / mole balance
    n_in = total_in
    n_out_top = top.getFluid().getFlowRate("kmole/hr")
    n_out_bot = bot.getFluid().getFlowRate("kmole/hr")
    print(f"\nMole balance: in = {n_in:.4f}, out = "
          f"{n_out_top + n_out_bot:.4f}  (err {n_in - (n_out_top + n_out_bot):+.4f} kmol/hr)")

    # Per-tray profile
    n_trays = int(COLUMN["n_trays"])
    print("\nPer-tray profile (top-down) — NeqSim vs UniSim")
    print(f"  {'tray':>4} {'NS':>3}  "
          f"{'T_ns':>7} {'T_us':>7} {'dT':>6}  "
          f"{'V_ns':>10}  {'L_ns':>10}")
    Ts, Vs, Ls = [], [], []
    for k_topdown in range(1, n_trays + 1):
        ns_stage = unisim_tray_to_ns_stage(k_topdown, n_trays)
        tray = col.getTray(ns_stage)
        f = tray.getFluid()
        T = f.getTemperature("C")
        try:
            V = tray.getGasOutStream().getFluid().getFlowRate("kg/hr")
        except Exception:
            V = float("nan")
        try:
            L = tray.getLiquidOutStream().getFluid().getFlowRate("kg/hr")
        except Exception:
            L = float("nan")
        T_us = UNISIM["T_C"][k_topdown - 1]
        dT = T - T_us
        print(f"  {k_topdown:>4} {ns_stage:>3}  "
              f"{T:>7.2f} {T_us:>7.2f} {dT:>+6.2f}  "
              f"{V:>10.1f}  {L:>10.1f}")
        Ts.append(T)
        Vs.append(V)
        Ls.append(L)

    # Plot — NeqSim vs UniSim overlay
    trays_topdown = list(range(1, n_trays + 1))
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(11, 5))
    ax1.plot(Ts, trays_topdown, "o-", label="NeqSim")
    ax1.plot(UNISIM["T_C"], trays_topdown, "s--",
             color="crimson", label="UniSim")
    ax1.invert_yaxis()
    ax1.set_xlabel("Tray temperature (C)")
    ax1.set_ylabel("Tray (top-down)")
    ax1.set_title("Temperature profile")
    ax1.legend()
    ax1.grid(True, alpha=0.3)

    ax2.plot(Vs, trays_topdown, "o-", color="tab:blue", label="V NeqSim")
    ax2.plot(Ls, trays_topdown, "o-", color="tab:green", label="L NeqSim")
    ax2.invert_yaxis()
    ax2.set_xlabel("Internal flow (kg/hr)")
    ax2.set_ylabel("Tray (top-down)")
    ax2.set_title("Vapour & liquid traffic")
    ax2.legend(fontsize=8)
    ax2.grid(True, alpha=0.3)
    fig.suptitle("col3 — 2-tray binary ethane / n-pentane (NeqSim vs UniSim)")
    fig.tight_layout()
    fig.savefig(args.out, dpi=120, bbox_inches="tight")
    print(f"\nProfile plot saved to: {args.out}")
    if not args.no_show:
        plt.show()


if __name__ == "__main__":
    main()
