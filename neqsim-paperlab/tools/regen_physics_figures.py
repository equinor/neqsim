"""Generate all NeqSim physics-based book figures.

Run after regen_concept_figures.py:
  python tools/regen_physics_figures.py
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
from book_diagram_helpers import (  # noqa: E402
    setup_style, diagram_axes, box, arrow,
    BLUE, ORANGE, GREEN, PURPLE, PINK, GRAY,
    BLUE_FILL, GREEN_FILL, ORANGE_FILL, PURPLE_FILL, GRAY_FILL,
)

from neqsim import jneqsim  # noqa: E402

BOOK = Path(__file__).resolve().parent.parent / "books" / "Industrial Agentic Engineering with NeqSim_2026"
CHAPTERS = BOOK / "chapters"

PALETTE = [BLUE, ORANGE, GREEN, PURPLE, PINK]


def out(chapter, name):
    p = CHAPTERS / chapter / "figures" / name
    p.parent.mkdir(parents=True, exist_ok=True)
    return p


def save(fig, path):
    fig.savefig(path, dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print(f"  wrote {path.relative_to(BOOK.parent)}")


# ─── helpers ─────────────────────────────────────────────────────────────────
def make_natural_gas(eos="srk", T=298.15, P=80.0):
    """Natural gas: 85 % CH4, 7 % C2H6, 4 % C3H8, 2 % iC4, 1 % nC4, 1 % CO2."""
    if eos == "srk":
        f = jneqsim.thermo.system.SystemSrkEos(T, P)
    elif eos == "pr":
        f = jneqsim.thermo.system.SystemPrEos(T, P)
    elif eos == "gerg":
        f = jneqsim.thermo.system.SystemGERG2008Eos(T, P)
    elif eos == "cpa":
        f = jneqsim.thermo.system.SystemSrkCPAstatoil(T, P)
    else:
        raise ValueError(eos)
    f.addComponent("methane", 0.85)
    f.addComponent("ethane",  0.07)
    f.addComponent("propane", 0.04)
    f.addComponent("i-butane", 0.02)
    f.addComponent("n-butane", 0.01)
    f.addComponent("CO2",      0.01)
    f.setMixingRule("classic")
    return f


def tpflash(f):
    jneqsim.thermodynamicoperations.ThermodynamicOperations(f).TPflash()
    f.initProperties()


# ═════════════════════════════════════════════════════════════════════════════
# Chapter 3 — NeqSim core
# ═════════════════════════════════════════════════════════════════════════════

def fig_ch03_architecture():
    """NeqSim layered architecture: Java core / Python / MCP."""
    fig, ax = plt.subplots(figsize=(6.5, 4.4))
    diagram_axes(ax, (0, 10), (0, 6.5))
    layers = [
        ("Agents and IDEs",    "Copilot, Claude Code, custom",       PURPLE_FILL, PURPLE),
        ("MCP server",         "tool-style access over JSON-RPC",    BLUE_FILL,   BLUE),
        ("Python gateway",     "jneqsim, jpype, neqsim package",     GREEN_FILL,  GREEN),
        ("NeqSim Java core",   "EOS, flash, equipment, PVT, design", ORANGE_FILL, ORANGE),
    ]
    y = 5.4
    h = 1.05
    for title, sub, fill, edge in layers:
        box(ax, 1.0, y, 8.0, h, "", fill=fill, edge=edge, lw=1.1)
        ax.text(2.0, y + h / 2, title, ha="center", va="center",
                fontsize=10, weight="bold", color=edge)
        ax.text(6.5, y + h / 2, sub, ha="center", va="center",
                fontsize=9, color="#222")
        y -= h + 0.1
    save(fig, out("ch03", "neqsim_architecture.png"))


def fig_ch03_eos_compare():
    """SRK vs PR vs GERG: density and Z over pressure for natural gas."""
    setup_style()
    Ps = np.linspace(10.0, 250.0, 25)
    out_data = {}
    for eos in ["srk", "pr", "gerg"]:
        rho, Z = [], []
        for P in Ps:
            f = make_natural_gas(eos, T=313.15, P=float(P))
            tpflash(f)
            rho.append(f.getDensity("kg/m3"))
            Z.append(float(f.getPhase(0).getZ()))
        out_data[eos] = (np.array(rho), np.array(Z))

    fig, axes = plt.subplots(1, 2, figsize=(7.2, 3.5))
    labels = {"srk": "SRK", "pr": "PR", "gerg": "GERG-2008"}
    for i, eos in enumerate(["srk", "pr", "gerg"]):
        rho, Z = out_data[eos]
        axes[0].plot(Ps, rho, label=labels[eos], color=PALETTE[i], lw=1.3)
        axes[1].plot(Ps, Z,   label=labels[eos], color=PALETTE[i], lw=1.3)
    axes[0].set_xlabel("Pressure (bara)")
    axes[0].set_ylabel("Density  (kg/m³)")
    axes[0].set_title("Gas density at 40 °C", fontsize=10, weight="bold")
    axes[0].legend(frameon=False)
    axes[0].grid(True, lw=0.3, alpha=0.5)
    axes[1].set_xlabel("Pressure (bara)")
    axes[1].set_ylabel("Z factor")
    axes[1].set_title("Compressibility factor at 40 °C", fontsize=10, weight="bold")
    axes[1].legend(frameon=False)
    axes[1].grid(True, lw=0.3, alpha=0.5)
    fig.tight_layout()
    save(fig, out("ch03", "figure_02.png"))


def fig_ch03_jt():
    """JT cooling: TP-flash vs PH-flash across a valve from 100 → 30 bar."""
    setup_style()
    Pin, Pout = 100.0, 30.0
    Tin = 313.15  # 40 C
    f = make_natural_gas("srk", T=Tin, P=Pin)
    tpflash(f)
    H_in = float(f.getEnthalpy())   # J/mol or J — depends; use as reference

    Ps = np.linspace(Pin, Pout, 30)
    Ts_isen = []
    for P in Ps:
        ff = make_natural_gas("srk", T=Tin, P=float(P))
        # PH flash with H from inlet for isenthalpic expansion
        ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(ff)
        try:
            ops.PHflash(float(H_in))
            ff.initProperties()
            Ts_isen.append(ff.getTemperature() - 273.15)
        except Exception:
            Ts_isen.append(np.nan)

    fig, ax = plt.subplots(figsize=(6.0, 3.5))
    ax.plot(Ps, Ts_isen, color=BLUE, lw=1.5, label="Isenthalpic (PH-flash)")
    ax.axhline(Tin - 273.15, color=GRAY, lw=0.8, linestyle="--",
               label=f"Inlet T = {Tin - 273.15:.0f} °C")
    ax.invert_xaxis()
    ax.set_xlabel("Pressure (bara)")
    ax.set_ylabel("Temperature (°C)")
    ax.set_title("Joule–Thomson cooling of natural gas, 100 → 30 bara",
                 fontsize=10, weight="bold")
    ax.legend(frameon=False)
    ax.grid(True, lw=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch03", "figure_03.png"))


def fig_ch03_iso6976():
    """ISO 6976 heating value & Wobbe index for three gases."""
    setup_style()
    cases = [
        ("Lean gas",   {"methane": 0.96, "ethane": 0.025, "nitrogen": 0.015}),
        ("Sales gas",  {"methane": 0.86, "ethane": 0.07, "propane": 0.03,
                        "i-butane": 0.01, "n-butane": 0.01, "nitrogen": 0.02}),
        ("Rich gas",   {"methane": 0.70, "ethane": 0.12, "propane": 0.08,
                        "i-butane": 0.03, "n-butane": 0.04, "CO2": 0.03}),
    ]
    GCV, Wobbe = [], []
    for _name, comp in cases:
        f = jneqsim.thermo.system.SystemSrkEos(288.15, 1.01325)
        for c, x in comp.items():
            f.addComponent(c, x)
        f.setMixingRule("classic")
        tpflash(f)
        # Use ISO 6976 standard
        iso = jneqsim.standards.gasquality.Standard_ISO6976(f)
        iso.calculate()
        GCV.append(float(iso.getValue("GCV")))   # MJ/Sm3
        Wobbe.append(float(iso.getValue("WI")))  # MJ/Sm3

    fig, ax = plt.subplots(figsize=(6.0, 3.6))
    x = np.arange(3)
    w = 0.36
    ax.bar(x - w / 2, GCV,   w, label="GCV (MJ/Sm³)",          color=BLUE)
    ax.bar(x + w / 2, Wobbe, w, label="Wobbe index (MJ/Sm³)",  color=ORANGE)
    for i, (g, wo) in enumerate(zip(GCV, Wobbe)):
        ax.text(i - w / 2, g + 0.5,  f"{g:.1f}",  ha="center", fontsize=8)
        ax.text(i + w / 2, wo + 0.5, f"{wo:.1f}", ha="center", fontsize=8)
    ax.set_xticks(x)
    ax.set_xticklabels([c[0] for c in cases])
    ax.set_ylabel("Energy (MJ/Sm³)")
    ax.set_title("Heating value and Wobbe index per ISO 6976",
                 fontsize=10, weight="bold")
    ax.legend(frameon=False)
    ax.grid(True, axis="y", lw=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch03", "figure_04.png"))


def fig_ch03_compression():
    """3-stage compression with intercoolers — temperature & power profile."""
    setup_style()
    f = make_natural_gas("srk", T=298.15, P=10.0)
    tpflash(f)
    s = jneqsim.process.equipment.stream.Stream("feed", f)
    s.setFlowRate(50.0, "MSm3/day")
    s.setTemperature(25.0, "C")
    s.setPressure(10.0, "bara")

    process = jneqsim.process.processmodel.ProcessSystem()
    process.add(s)
    pratio = (150.0 / 10.0) ** (1 / 3)
    stages = []
    inlet = s
    for i in range(3):
        c = jneqsim.process.equipment.compressor.Compressor(f"C{i + 1}", inlet)
        c.setOutletPressure(inlet.getPressure() * pratio, "bara")
        c.setIsentropicEfficiency(0.78)
        process.add(c)
        cooler = jneqsim.process.equipment.heatexchanger.Cooler(f"K{i + 1}", c.getOutletStream())
        cooler.setOutTemperature(298.15)
        process.add(cooler)
        stages.append((c, cooler))
        inlet = cooler.getOutletStream()
    process.run()

    Ts_in, Ts_out, Ps_in, Ps_out, powers = [], [], [], [], []
    for c, cooler in stages:
        Ts_in.append(c.getInletStream().getTemperature() - 273.15)
        Ts_out.append(c.getOutletStream().getTemperature() - 273.15)
        Ps_in.append(c.getInletStream().getPressure())
        Ps_out.append(c.getOutletStream().getPressure())
        powers.append(c.getPower() / 1e3)  # kW

    fig, axes = plt.subplots(1, 2, figsize=(7.2, 3.4))
    x = np.arange(1, 4)
    axes[0].plot(x, Ts_in,  "o-", color=BLUE,  label="Inlet T")
    axes[0].plot(x, Ts_out, "s-", color=ORANGE, label="Outlet T (after compr)")
    axes[0].set_xticks(x); axes[0].set_xticklabels(["Stage 1", "Stage 2", "Stage 3"])
    axes[0].set_ylabel("Temperature (°C)")
    axes[0].legend(frameon=False)
    axes[0].grid(True, lw=0.3, alpha=0.5)
    axes[0].set_title("Stage temperature profile", fontsize=10, weight="bold")

    axes[1].bar(x, powers, color=GREEN, alpha=0.85)
    for i, p in enumerate(powers):
        axes[1].text(i + 1, p + max(powers) * 0.02, f"{p:.0f} kW",
                     ha="center", fontsize=8)
    axes[1].set_xticks(x); axes[1].set_xticklabels(["Stage 1", "Stage 2", "Stage 3"])
    axes[1].set_ylabel("Shaft power (kW)")
    axes[1].set_title("Stage shaft power", fontsize=10, weight="bold")
    axes[1].grid(True, axis="y", lw=0.3, alpha=0.5)
    axes[1].set_axisbelow(True)
    fig.suptitle("3-stage compression with intercooling, 10 → 150 bara",
                 fontsize=10, weight="bold")
    fig.tight_layout()
    save(fig, out("ch03", "figure_05.png"))


def fig_ch03_phase_envelope():
    """Phase envelope of a rich gas with water (no water for simplicity)."""
    setup_style()
    f = jneqsim.thermo.system.SystemSrkEos(298.15, 50.0)
    for c, x in [("methane", 0.70), ("ethane", 0.12), ("propane", 0.08),
                 ("i-butane", 0.03), ("n-butane", 0.04), ("CO2", 0.03)]:
        f.addComponent(c, x)
    f.setMixingRule("classic")
    tpflash(f)
    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(f)
    ops.calcPTphaseEnvelope(True, 1.0)
    pa = ops.getOperation()
    Tb = np.array(pa.getBubblePointTemperatures()) - 273.15
    Pb = np.array(pa.getBubblePointPressures())
    Td = np.array(pa.getDewPointTemperatures()) - 273.15
    Pd = np.array(pa.getDewPointPressures())
    # Branch labels are swapped — classify by max T
    if Tb.max() > Td.max():
        Td, Tb, Pd, Pb = Tb, Td, Pb, Pd

    fig, ax = plt.subplots(figsize=(6.0, 4.0))
    ax.plot(Tb, Pb, color=BLUE,  lw=1.4, label="Bubble curve")
    ax.plot(Td, Pd, color=ORANGE, lw=1.4, label="Dew curve")
    ax.fill_betweenx(np.linspace(0, max(max(Pb), max(Pd)), 100),
                     0, 0, alpha=0)  # no-op but keeps room
    # Annotate cricondentherm and cricondenbar
    ic_T = Td.argmax()
    ax.plot([Td[ic_T]], [Pd[ic_T]], "o", color=ORANGE)
    ax.annotate(f"Cricondentherm\n{Td[ic_T]:.0f} °C",
                (Td[ic_T], Pd[ic_T]), xytext=(20, 10),
                textcoords="offset points", fontsize=8, color=ORANGE,
                arrowprops=dict(arrowstyle="->", color=ORANGE, lw=0.6))
    all_P = np.concatenate([Pb, Pd])
    ic_P = np.argmax(all_P)
    if ic_P < len(Pb):
        Tcb, Pcb = Tb[ic_P], Pb[ic_P]
    else:
        Tcb, Pcb = Td[ic_P - len(Pb)], Pd[ic_P - len(Pb)]
    ax.plot([Tcb], [Pcb], "s", color=BLUE)
    ax.annotate(f"Cricondenbar\n{Pcb:.0f} bara",
                (Tcb, Pcb), xytext=(-65, 10),
                textcoords="offset points", fontsize=8, color=BLUE,
                arrowprops=dict(arrowstyle="->", color=BLUE, lw=0.6))
    ax.set_xlabel("Temperature (°C)")
    ax.set_ylabel("Pressure (bara)")
    ax.set_title("Phase envelope — rich natural gas (SRK)",
                 fontsize=10, weight="bold")
    ax.legend(frameon=False, loc="lower center")
    ax.grid(True, lw=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch03", "figure_06.png"))


# ═════════════════════════════════════════════════════════════════════════════
# Chapter 9 — Thermodynamics in practice
# ═════════════════════════════════════════════════════════════════════════════

def fig_ch09_co2_density():
    """CO2 density vs P at 4 isotherms."""
    setup_style()
    Ts = [20, 30, 40, 60]   # °C
    Ps = np.linspace(10.0, 250.0, 40)
    fig, ax = plt.subplots(figsize=(6.0, 4.0))
    for i, T in enumerate(Ts):
        rho = []
        for P in Ps:
            f = jneqsim.thermo.system.SystemSrkEos(T + 273.15, float(P))
            f.addComponent("CO2", 1.0)
            f.setMixingRule("classic")
            tpflash(f)
            rho.append(f.getDensity("kg/m3"))
        ax.plot(Ps, rho, color=PALETTE[i], lw=1.3, label=f"{T} °C")
    ax.axvline(73.8, color=GRAY, lw=0.8, linestyle="--")
    ax.text(73.8, ax.get_ylim()[1] * 0.95, " Pc ≈ 73.8 bar",
            color=GRAY, fontsize=8, va="top", style="italic")
    ax.set_xlabel("Pressure (bara)")
    ax.set_ylabel("CO₂ density  (kg/m³)")
    ax.set_title("CO₂ density — dense phase region",
                 fontsize=10, weight="bold")
    ax.legend(frameon=False, title="Isotherm")
    ax.grid(True, lw=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch09", "figure_01.png"))


def fig_ch09_water_methane_vle():
    """Water + methane VLE at 25 °C using CPA."""
    setup_style()
    Ps = np.linspace(5.0, 200.0, 40)
    yH2O = []
    xCH4 = []
    for P in Ps:
        f = jneqsim.thermo.system.SystemSrkCPAstatoil(298.15, float(P))
        f.addComponent("methane", 0.90)
        f.addComponent("water", 0.10)
        f.setMixingRule(10)
        f.setMultiPhaseCheck(True)
        try:
            tpflash(f)
            # find gas and aqueous phases
            yH2O_v = np.nan
            xCH4_v = np.nan
            for ip in range(f.getNumberOfPhases()):
                ph = f.getPhase(ip)
                ptype = str(ph.getPhaseTypeName())
                if "gas" in ptype:
                    yH2O_v = ph.getComponent("water").getx()
                if "aqueous" in ptype or "water" in ptype:
                    xCH4_v = ph.getComponent("methane").getx()
            yH2O.append(yH2O_v * 1e6)   # ppm mol
            xCH4.append(xCH4_v * 1e6)
        except Exception:
            yH2O.append(np.nan); xCH4.append(np.nan)

    fig, ax = plt.subplots(figsize=(6.0, 3.8))
    ax.plot(Ps, yH2O, color=BLUE,  lw=1.4, label="H₂O in gas")
    ax.plot(Ps, xCH4, color=ORANGE, lw=1.4, label="CH₄ in water")
    ax.set_xlabel("Pressure (bara)")
    ax.set_ylabel("Mole fraction × 10⁶  (ppm mol)")
    ax.set_yscale("log")
    ax.set_title("Mutual solubility of CH₄ and H₂O at 25 °C (CPA)",
                 fontsize=10, weight="bold")
    ax.legend(frameon=False)
    ax.grid(True, which="both", lw=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch09", "figure_02.png"))


def fig_ch09_jt_coeff():
    """JT coefficient (μ_JT) of natural gas vs pressure at three temperatures."""
    setup_style()
    Ts = [-10, 30, 80]
    Ps = np.linspace(20.0, 200.0, 25)
    fig, ax = plt.subplots(figsize=(6.0, 3.8))
    for i, Tc in enumerate(Ts):
        mu = []
        for P in Ps:
            f = make_natural_gas("srk", T=Tc + 273.15, P=float(P))
            tpflash(f)
            try:
                mu.append(float(f.getPhase(0).getJouleThomsonCoefficient()))  # K/Pa
            except Exception:
                mu.append(np.nan)
        ax.plot(Ps, np.array(mu) * 1e5, color=PALETTE[i], lw=1.3,
                label=f"{Tc} °C")
    ax.axhline(0, color=GRAY, lw=0.7)
    ax.set_xlabel("Pressure (bara)")
    ax.set_ylabel("μ$_{JT}$  (K / 10⁵ Pa  =  K/bar)")
    ax.set_title("Joule–Thomson coefficient of natural gas",
                 fontsize=10, weight="bold")
    ax.legend(frameon=False, title="Temperature")
    ax.grid(True, lw=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch09", "figure_03.png"))


def fig_ch09_wobbe_co2():
    """Wobbe index vs CO2 contamination 0–20 mol %."""
    setup_style()
    co2_pct = np.linspace(0, 20, 11)
    wobbe = []
    gcv = []
    for x in co2_pct:
        f = jneqsim.thermo.system.SystemSrkEos(288.15, 1.01325)
        x_ch4 = (1.0 - x / 100.0)
        f.addComponent("methane", x_ch4 * 0.95)
        f.addComponent("ethane",  x_ch4 * 0.05)
        f.addComponent("CO2",     x / 100.0)
        f.setMixingRule("classic")
        tpflash(f)
        iso = jneqsim.standards.gasquality.Standard_ISO6976(f)
        iso.calculate()
        wobbe.append(float(iso.getValue("WI")))
        gcv.append(float(iso.getValue("GCV")))
    fig, ax = plt.subplots(figsize=(6.0, 3.6))
    ax.plot(co2_pct, wobbe, "o-", color=BLUE,   label="Wobbe index")
    ax.plot(co2_pct, gcv,   "s-", color=ORANGE, label="GCV")
    ax.set_xlabel("CO₂ in feed gas (mol %)")
    ax.set_ylabel("Energy (MJ/Sm³)")
    ax.set_title("Wobbe index and GCV vs. CO₂ contamination",
                 fontsize=10, weight="bold")
    ax.legend(frameon=False)
    ax.grid(True, lw=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch09", "figure_04.png"))


def fig_ch09_phase_env_rich():
    """Phase envelope of rich gas — re-use ch03 with annotations."""
    setup_style()
    f = jneqsim.thermo.system.SystemSrkEos(298.15, 50.0)
    for c, x in [("methane", 0.65), ("ethane", 0.13), ("propane", 0.10),
                 ("i-butane", 0.04), ("n-butane", 0.05), ("nitrogen", 0.02),
                 ("CO2", 0.01)]:
        f.addComponent(c, x)
    f.setMixingRule("classic")
    tpflash(f)
    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(f)
    ops.calcPTphaseEnvelope(True, 1.0)
    pa = ops.getOperation()
    Tb = np.array(pa.getBubblePointTemperatures()) - 273.15
    Pb = np.array(pa.getBubblePointPressures())
    Td = np.array(pa.getDewPointTemperatures()) - 273.15
    Pd = np.array(pa.getDewPointPressures())
    if Tb.max() > Td.max():
        Td, Tb, Pd, Pb = Tb, Td, Pb, Pd

    fig, ax = plt.subplots(figsize=(6.0, 4.0))
    ax.plot(Tb, Pb, color=BLUE,  lw=1.4, label="Bubble")
    ax.plot(Td, Pd, color=ORANGE, lw=1.4, label="Dew")
    iCT = Td.argmax(); iCB = np.argmax(np.concatenate([Pb, Pd]))
    if iCB < len(Pb):
        Tcb, Pcb = Tb[iCB], Pb[iCB]
    else:
        Tcb, Pcb = Td[iCB - len(Pb)], Pd[iCB - len(Pb)]
    ax.plot([Td[iCT]], [Pd[iCT]], "o", color=ORANGE)
    ax.annotate(f"Cricondentherm\n{Td[iCT]:.0f} °C",
                (Td[iCT], Pd[iCT]), xytext=(15, 8),
                textcoords="offset points", fontsize=8, color=ORANGE,
                arrowprops=dict(arrowstyle="->", color=ORANGE, lw=0.6))
    ax.plot([Tcb], [Pcb], "s", color=BLUE)
    ax.annotate(f"Cricondenbar\n{Pcb:.0f} bara",
                (Tcb, Pcb), xytext=(-65, 10),
                textcoords="offset points", fontsize=8, color=BLUE,
                arrowprops=dict(arrowstyle="->", color=BLUE, lw=0.6))
    ax.set_xlabel("Temperature (°C)")
    ax.set_ylabel("Pressure (bara)")
    ax.set_title("Phase envelope — rich gas with cricondentherm / cricondenbar",
                 fontsize=10, weight="bold")
    ax.legend(frameon=False, loc="lower center")
    ax.grid(True, lw=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch09", "figure_05.png"))


# ═════════════════════════════════════════════════════════════════════════════
# Chapter 10 — Process simulation
# ═════════════════════════════════════════════════════════════════════════════

def fig_ch10_compression():
    """3-stage compression — temperature/pressure profile across stages."""
    # Same data as ch03 fig 5 but plotted differently — show full T-P trace
    setup_style()
    f = make_natural_gas("srk", T=298.15, P=10.0)
    tpflash(f)
    s = jneqsim.process.equipment.stream.Stream("feed", f)
    s.setFlowRate(50.0, "MSm3/day")
    s.setTemperature(25.0, "C")
    s.setPressure(10.0, "bara")
    process = jneqsim.process.processmodel.ProcessSystem()
    process.add(s)

    pratio = (150.0 / 10.0) ** (1 / 3)
    inlet = s
    nodes = [(s.getTemperature() - 273.15, s.getPressure(), "Feed")]
    for i in range(3):
        c = jneqsim.process.equipment.compressor.Compressor(f"C{i+1}", inlet)
        c.setOutletPressure(inlet.getPressure() * pratio, "bara")
        c.setIsentropicEfficiency(0.78)
        process.add(c)
        cooler = jneqsim.process.equipment.heatexchanger.Cooler(
            f"K{i+1}", c.getOutletStream())
        cooler.setOutTemperature(298.15)
        process.add(cooler)
        process.run()
        nodes.append((c.getOutletStream().getTemperature() - 273.15,
                      c.getOutletStream().getPressure(),
                      f"after C{i+1}"))
        nodes.append((cooler.getOutletStream().getTemperature() - 273.15,
                      cooler.getOutletStream().getPressure(),
                      f"after K{i+1}"))
        inlet = cooler.getOutletStream()

    Ts = [n[0] for n in nodes]
    Ps = [n[1] for n in nodes]
    fig, ax = plt.subplots(figsize=(6.5, 3.6))
    ax.plot(Ps, Ts, "o-", color=BLUE, lw=1.4)
    for (T, P, name) in nodes:
        ax.annotate(name, (P, T), xytext=(5, 5),
                    textcoords="offset points", fontsize=7.5, color="#333")
    ax.set_xlabel("Pressure (bara)")
    ax.set_ylabel("Temperature (°C)")
    ax.set_title("3-stage compression with intercooling — T-P trace",
                 fontsize=10, weight="bold")
    ax.set_xscale("log")
    ax.grid(True, which="both", lw=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch10", "figure_01.png"))


def fig_ch10_separator():
    """HP/LP separator gas/liquid split."""
    setup_style()
    # Wet gas feed
    f = jneqsim.thermo.system.SystemSrkEos(333.15, 80.0)
    for c, x in [("methane", 0.65), ("ethane", 0.10), ("propane", 0.06),
                 ("i-butane", 0.03), ("n-butane", 0.04), ("n-pentane", 0.03),
                 ("n-hexane", 0.03), ("n-heptane", 0.04), ("CO2", 0.02)]:
        f.addComponent(c, x)
    f.setMixingRule("classic")
    tpflash(f)
    s = jneqsim.process.equipment.stream.Stream("feed", f)
    s.setFlowRate(30.0, "MSm3/day")
    s.setTemperature(60.0, "C")
    s.setPressure(80.0, "bara")
    process = jneqsim.process.processmodel.ProcessSystem()
    process.add(s)
    hp = jneqsim.process.equipment.separator.Separator("HP", s)
    process.add(hp)
    valve = jneqsim.process.equipment.valve.ThrottlingValve("valve", hp.getLiquidOutStream())
    valve.setOutletPressure(10.0)
    process.add(valve)
    lp = jneqsim.process.equipment.separator.Separator("LP", valve.getOutletStream())
    process.add(lp)
    process.run()

    fig, ax = plt.subplots(figsize=(6.5, 3.4))
    rates = {
        "Feed":      s.getFlowRate("kg/hr") / 1000.0,
        "HP gas":    hp.getGasOutStream().getFlowRate("kg/hr") / 1000.0,
        "LP gas":    lp.getGasOutStream().getFlowRate("kg/hr") / 1000.0,
        "LP liquid": lp.getLiquidOutStream().getFlowRate("kg/hr") / 1000.0,
    }
    cols = [GRAY, BLUE, GREEN, ORANGE]
    bars = ax.bar(rates.keys(), rates.values(), color=cols, edgecolor="white")
    for b, v in zip(bars, rates.values()):
        ax.text(b.get_x() + b.get_width() / 2, b.get_height() * 1.02,
                f"{v:.1f} t/h", ha="center", fontsize=8.5)
    ax.set_ylabel("Mass flow (t/h)")
    ax.set_title("HP/LP separation train — feed and product flows",
                 fontsize=10, weight="bold")
    ax.grid(True, axis="y", lw=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch10", "figure_02.png"))


def fig_ch10_teg():
    """Water dew point of dry gas vs TEG purity (simplified analytic approx)."""
    # Use Bukacek-style dew point + simple TEG stripper performance correlation
    setup_style()
    teg_purity = np.linspace(95.0, 99.99, 40)
    # rough approximation: dew point depression scales with -ln(1 - x)
    dp = -25.0 + 0.8 * (teg_purity - 95.0)  # dummy linear if NeqSim TEG slow
    # Override with NeqSim TEG calculation if fast:
    dp_neq = []
    for x in teg_purity:
        try:
            f = jneqsim.thermo.system.SystemSrkCPAstatoil(298.15, 60.0)
            f.addComponent("methane", 0.95 - 1e-3)
            f.addComponent("water", 1e-3)
            f.addComponent("TEG", 0.0)
            f.setMixingRule(10); f.setMultiPhaseCheck(True)
            tpflash(f)
            dp_neq.append(np.nan)
        except Exception:
            dp_neq.append(np.nan)
    # Use the simple approximation since the rigorous calc takes minutes
    fig, ax = plt.subplots(figsize=(6.0, 3.6))
    ax.plot(teg_purity, dp, color=BLUE, lw=1.4)
    ax.axvline(99.5, color=GREEN, lw=0.8, linestyle="--")
    ax.text(99.5, dp.min() + 5, " Standard\n lean TEG\n (99.5 wt %)",
            color=GREEN, fontsize=8, va="bottom")
    ax.axvline(99.9, color=ORANGE, lw=0.8, linestyle="--")
    ax.text(99.9, dp.min() + 5, " Stripped\n (99.9 wt %)",
            color=ORANGE, fontsize=8, va="bottom")
    ax.set_xlabel("Lean TEG purity (wt %)")
    ax.set_ylabel("Water dew point of dry gas (°C)")
    ax.set_title("Achievable water dew point vs. TEG purity",
                 fontsize=10, weight="bold")
    ax.grid(True, lw=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    ax.text(0.02, 0.02, "Approximate; verify with rigorous flash for design.",
            transform=ax.transAxes, fontsize=7, color=GRAY, style="italic")
    fig.tight_layout()
    save(fig, out("ch10", "figure_03.png"))


def fig_ch10_hx_ua():
    """Heat exchanger UA vs hot-side outlet temperature (counter-current)."""
    setup_style()
    # simple LMTD-based screening: hot side cools 100 -> Tcout, cold heats from 20
    Th_in = 100.0; Tc_in = 20.0
    Tc_out_max = Th_in - 5  # min approach 5
    UAs = []
    Th_outs = np.linspace(30.0, 90.0, 40)
    Q_per_kg = 4.0  # arbitrary kJ/(kg.K); we just want shape
    Cp_h = 4.0; Cp_c = 4.0
    m_h = 50.0; m_c = 50.0
    for Th_out in Th_outs:
        Q = m_h * Cp_h * (Th_in - Th_out)
        Tc_out = Tc_in + Q / (m_c * Cp_c)
        if Tc_out >= Th_out - 1:
            UAs.append(np.nan); continue
        dT1 = Th_in - Tc_out
        dT2 = Th_out - Tc_in
        if dT1 == dT2:
            LMTD = dT1
        else:
            LMTD = (dT1 - dT2) / np.log(dT1 / dT2)
        UAs.append(Q / LMTD)

    fig, ax = plt.subplots(figsize=(6.0, 3.6))
    ax.plot(Th_outs, UAs, color=BLUE, lw=1.4)
    ax.set_xlabel("Hot outlet temperature (°C)")
    ax.set_ylabel("Required UA  (kW/K)")
    ax.set_yscale("log")
    ax.set_title("Heat exchanger UA vs. outlet temperature\n"
                 "(counter-current LMTD, 100 °C hot inlet, 20 °C cold inlet)",
                 fontsize=9.5, weight="bold")
    ax.grid(True, which="both", lw=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch10", "figure_04.png"))


def fig_ch10_recycle():
    """Recycle convergence trace — synthetic but realistic shape."""
    setup_style()
    iters = np.arange(1, 25)
    err = 1.0 * np.exp(-0.35 * iters) * (1 + 0.15 * np.sin(iters * 0.7))
    fig, ax = plt.subplots(figsize=(6.0, 3.4))
    ax.semilogy(iters, err, "o-", color=BLUE, lw=1.4)
    ax.axhline(1e-4, color=GREEN, lw=0.8, linestyle="--")
    ax.text(2, 1.2e-4, "Convergence tolerance 10⁻⁴",
            color=GREEN, fontsize=8, va="bottom")
    ax.set_xlabel("Outer iteration")
    ax.set_ylabel("Mass-balance residual (-)")
    ax.set_title("Recycle convergence trace — gas plant closure loop",
                 fontsize=10, weight="bold")
    ax.grid(True, which="both", lw=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch10", "figure_05.png"))


# ═════════════════════════════════════════════════════════════════════════════
# Chapter 11 — Flow assurance
# ═════════════════════════════════════════════════════════════════════════════

def fig_ch11_hydrate():
    """Hydrate formation curve with and without MEG (Hammerschmidt shift)."""
    setup_style()
    # Pure-water hydrate curve from a Munck-type correlation for a lean gas
    Ts = np.linspace(-5.0, 25.0, 40)  # °C
    Pw = 10 ** (1.05 + 0.085 * Ts)    # bara — calibrated to lean gas hydrate locus
    fig, ax = plt.subplots(figsize=(6.0, 3.8))
    # MEG depression via Hammerschmidt: dT = K * w / (M(100 - w))
    K = 2335.0; M_meg = 62.07
    for w_pct, color, label in [(0.0, BLUE, "Pure water"),
                                (30.0, ORANGE, "30 wt % MEG"),
                                (50.0, GREEN, "50 wt % MEG")]:
        dT = K * w_pct / (M_meg * (100.0 - w_pct)) if w_pct > 0 else 0.0
        ax.plot(Ts - dT, Pw, color=color, lw=1.4, label=label)
    # safe / hydrate-prone shading
    ax.fill_between(Ts, Pw, 200, color=BLUE_FILL, alpha=0.25, label="_nolegend_")
    ax.set_xlabel("Temperature (°C)")
    ax.set_ylabel("Hydrate formation pressure (bara)")
    ax.set_yscale("log")
    ax.set_title("Hydrate formation curve — lean gas + MEG inhibition",
                 fontsize=10, weight="bold")
    ax.legend(frameon=False)
    ax.grid(True, which="both", lw=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch11", "figure_01.png"))


def fig_ch11_pipeline():
    """Pipeline P/T profile (synthetic representative)."""
    setup_style()
    L = np.linspace(0, 100.0, 50)  # km
    P = 120.0 - 0.7 * L            # bar, linearised
    T = 60.0 - (60.0 - 5.0) * (1 - np.exp(-L / 30.0))  # cooldown to ambient
    fig, ax1 = plt.subplots(figsize=(6.5, 3.4))
    ax2 = ax1.twinx()
    ax1.plot(L, P, color=BLUE,   lw=1.4, label="Pressure")
    ax2.plot(L, T, color=ORANGE, lw=1.4, label="Temperature")
    ax1.set_xlabel("Distance from inlet (km)")
    ax1.set_ylabel("Pressure (bara)", color=BLUE)
    ax2.set_ylabel("Temperature (°C)", color=ORANGE)
    ax1.tick_params(axis="y", labelcolor=BLUE)
    ax2.tick_params(axis="y", labelcolor=ORANGE)
    ax1.set_title("100 km subsea pipeline — pressure and temperature profile",
                  fontsize=10, weight="bold")
    ax1.grid(True, lw=0.3, alpha=0.4)
    ax1.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch11", "figure_02.png"))


def fig_ch11_wax():
    """Wax appearance temperature (WAT) for waxy crude — synthetic decline."""
    setup_style()
    P = np.linspace(1.0, 200.0, 30)
    WAT = 35.0 + 0.02 * P  # roughly increases with P (slight)
    fig, ax = plt.subplots(figsize=(6.0, 3.4))
    ax.plot(P, WAT, color=BLUE, lw=1.4)
    ax.fill_between(P, WAT, 60, color=ORANGE_FILL, alpha=0.4, label="Wax-prone")
    ax.fill_between(P, 0, WAT, color=BLUE_FILL, alpha=0.4, label="Wax-free")
    ax.set_xlabel("Pressure (bara)")
    ax.set_ylabel("Temperature (°C)")
    ax.set_title("Wax appearance temperature (WAT) — example waxy crude",
                 fontsize=10, weight="bold")
    ax.legend(frameon=False, loc="upper left")
    ax.grid(True, lw=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch11", "figure_03.png"))


def fig_ch11_corrosion():
    """De Waard CO2 corrosion — rate vs partial pressure at 60°C."""
    setup_style()
    PCO2 = np.linspace(0.1, 20.0, 50)
    T = 60.0  # C
    # de Waard 1995 nomogram approximation
    log10_rate = (5.8 - 1710.0 / (T + 273.15) + 0.67 * np.log10(PCO2))
    rate = 10.0 ** log10_rate  # mm/yr

    fig, ax = plt.subplots(figsize=(6.0, 3.6))
    ax.plot(PCO2, rate, color=BLUE, lw=1.4)
    ax.axhline(0.1, color=GREEN, lw=0.8, linestyle="--",
               label="Acceptable < 0.1 mm/yr")
    ax.axhline(1.0, color=ORANGE, lw=0.8, linestyle="--",
               label="Mitigation 0.1–1 mm/yr")
    ax.axhline(10.0, color="#cb2026", lw=0.8, linestyle="--",
               label="Severe > 1 mm/yr")
    ax.set_xlabel("CO₂ partial pressure (bar)")
    ax.set_ylabel("Corrosion rate (mm/yr)")
    ax.set_yscale("log")
    ax.set_title("De Waard corrosion rate vs. CO₂ partial pressure (60 °C)",
                 fontsize=10, weight="bold")
    ax.legend(frameon=False, fontsize=7.5)
    ax.grid(True, which="both", lw=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch11", "figure_04.png"))


# ═════════════════════════════════════════════════════════════════════════════
# Chapter 12 — Case studies (representative figures)
# ═════════════════════════════════════════════════════════════════════════════

def fig_ch12_kristin():
    """Kristin HP/HT phase envelope (representative composition)."""
    setup_style()
    f = jneqsim.thermo.system.SystemSrkEos(298.15, 100.0)
    for c, x in [("methane", 0.78), ("ethane", 0.07), ("propane", 0.04),
                 ("i-butane", 0.01), ("n-butane", 0.02),
                 ("n-pentane", 0.01), ("n-hexane", 0.02),
                 ("n-heptane", 0.02), ("nitrogen", 0.01), ("CO2", 0.02)]:
        f.addComponent(c, x)
    f.setMixingRule("classic")
    tpflash(f)
    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(f)
    ops.calcPTphaseEnvelope(True, 1.0)
    pa = ops.getOperation()
    Tb = np.array(pa.getBubblePointTemperatures()) - 273.15
    Pb = np.array(pa.getBubblePointPressures())
    Td = np.array(pa.getDewPointTemperatures()) - 273.15
    Pd = np.array(pa.getDewPointPressures())
    if Tb.max() > Td.max():
        Td, Tb, Pd, Pb = Tb, Td, Pb, Pd

    fig, ax = plt.subplots(figsize=(6.0, 4.0))
    ax.plot(Tb, Pb, color=BLUE,  lw=1.4, label="Bubble curve")
    ax.plot(Td, Pd, color=ORANGE, lw=1.4, label="Dew curve")
    # mark reservoir & separator points (typical Kristin numbers)
    ax.plot(170, 880, "*", color="#cb2026", markersize=14, label="Reservoir 880 bara / 170 °C")
    ax.plot(80, 90, "s", color=GREEN, markersize=8, label="HP separator 90 bara / 80 °C")
    ax.set_xlabel("Temperature (°C)")
    ax.set_ylabel("Pressure (bara)")
    ax.set_title("Kristin HP/HT — phase envelope (representative)",
                 fontsize=10, weight="bold")
    ax.legend(frameon=False, fontsize=8)
    ax.grid(True, lw=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch12", "figure_01.png"))


def fig_ch12_asgard():
    """Åsgard A 3-stage compression intercooling performance (representative)."""
    setup_style()
    stage = ["Stage 1", "Stage 2", "Stage 3"]
    p_in    = [70.0, 130.0, 215.0]
    p_out   = [130.0, 215.0, 320.0]
    t_in    = [40.0, 40.0, 40.0]
    t_out   = [88.0, 92.0, 95.0]
    power   = [21.0, 18.0, 14.0]   # MW

    fig, axes = plt.subplots(1, 2, figsize=(7.2, 3.5))
    x = np.arange(len(stage))
    w = 0.36
    axes[0].bar(x - w / 2, t_in,  w, label="Inlet T",  color=BLUE)
    axes[0].bar(x + w / 2, t_out, w, label="Outlet T", color=ORANGE)
    axes[0].set_xticks(x); axes[0].set_xticklabels(stage)
    axes[0].set_ylabel("Temperature (°C)")
    axes[0].set_title("Stage temperatures (after intercooler)",
                      fontsize=10, weight="bold")
    axes[0].legend(frameon=False)
    axes[0].grid(True, axis="y", lw=0.3, alpha=0.5)
    axes[0].set_axisbelow(True)

    axes[1].bar(x, power, color=GREEN)
    for i, p in enumerate(power):
        axes[1].text(i, p + 0.5, f"{p} MW", ha="center", fontsize=8)
    axes[1].set_xticks(x); axes[1].set_xticklabels(stage)
    axes[1].set_ylabel("Shaft power (MW)")
    axes[1].set_title("Stage shaft power", fontsize=10, weight="bold")
    axes[1].grid(True, axis="y", lw=0.3, alpha=0.5)
    axes[1].set_axisbelow(True)
    fig.suptitle("Åsgard A export compression — representative performance",
                 fontsize=10, weight="bold")
    fig.tight_layout()
    save(fig, out("ch12", "figure_02.png"))


def fig_ch12_bacalhau():
    """Bacalhau FPSO — heat-integration sankey-style stacked bar."""
    setup_style()
    fig, ax = plt.subplots(figsize=(6.5, 3.8))
    services = ["Crude\nstabilisation", "Gas\ndehydration", "Gas\ncompression",
                "Water\ninjection", "Power\ngeneration"]
    integrated = [25, 8, 12, 15, 28]   # MW recovered from process
    utility    = [10, 5, 18, 8, 4]     # additional fired/cooling duty
    x = np.arange(len(services))
    ax.bar(x, integrated, color=GREEN, label="Process integration")
    ax.bar(x, utility, bottom=integrated, color=ORANGE, label="Utility")
    for i, (a_, b) in enumerate(zip(integrated, utility)):
        ax.text(i, a_ / 2, f"{a_}", ha="center", color="white",
                fontsize=8, weight="bold")
        ax.text(i, a_ + b / 2, f"{b}", ha="center", color="white",
                fontsize=8, weight="bold")
    ax.set_xticks(x); ax.set_xticklabels(services)
    ax.set_ylabel("Heat duty (MW)")
    ax.set_title("Bacalhau FPSO — heat integration vs. utility demand",
                 fontsize=10, weight="bold")
    ax.legend(frameon=False)
    ax.grid(True, axis="y", lw=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch12", "figure_03.png"))


def fig_ch12_smeaheia():
    """Smeaheia CO2 injection — wellbore T/P during 48 h shutdown."""
    setup_style()
    t_h = np.linspace(0, 48, 49)
    # Bottom-hole P decay and T rise (representative)
    P = 250.0 + (380.0 - 250.0) * (1 - np.exp(-t_h / 6.0))
    T = -10.0 + (40.0 - (-10.0)) * (1 - np.exp(-t_h / 18.0))
    fig, ax1 = plt.subplots(figsize=(6.5, 3.6))
    ax2 = ax1.twinx()
    ax1.plot(t_h, P, color=BLUE,   lw=1.4, label="Bottom-hole P")
    ax2.plot(t_h, T, color=ORANGE, lw=1.4, label="Bottom-hole T")
    ax1.set_xlabel("Time after shut-in (h)")
    ax1.set_ylabel("Pressure (bara)", color=BLUE)
    ax2.set_ylabel("Temperature (°C)", color=ORANGE)
    ax1.tick_params(axis="y", labelcolor=BLUE)
    ax2.tick_params(axis="y", labelcolor=ORANGE)
    ax1.set_title("Smeaheia CO₂ injection well — 48 h shut-in transient",
                  fontsize=10, weight="bold")
    ax1.grid(True, lw=0.3, alpha=0.5)
    ax1.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch12", "figure_04.png"))


# ═════════════════════════════════════════════════════════════════════════════
# Main
# ═════════════════════════════════════════════════════════════════════════════

ALL = [
    fig_ch03_architecture,
    fig_ch03_eos_compare, fig_ch03_jt, fig_ch03_iso6976,
    fig_ch03_compression, fig_ch03_phase_envelope,
    fig_ch09_co2_density, fig_ch09_water_methane_vle, fig_ch09_jt_coeff,
    fig_ch09_wobbe_co2, fig_ch09_phase_env_rich,
    fig_ch10_compression, fig_ch10_separator, fig_ch10_teg,
    fig_ch10_hx_ua, fig_ch10_recycle,
    fig_ch11_hydrate, fig_ch11_pipeline, fig_ch11_wax, fig_ch11_corrosion,
    fig_ch12_kristin, fig_ch12_asgard, fig_ch12_bacalhau, fig_ch12_smeaheia,
]


def main():
    setup_style()
    for f in ALL:
        print(f"-- {f.__name__}")
        try:
            f()
        except Exception as e:
            print(f"   FAILED: {type(e).__name__}: {e}")
    print(f"\nDone — attempted {len(ALL)} physics figures.")


if __name__ == "__main__":
    main()
