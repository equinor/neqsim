"""Engineering plots from solved NeqSim objects. Each function returns a Matplotlib figure."""

from math import isfinite

import matplotlib.pyplot as plt
import numpy as np


def plot_cycle(system):
    """Draw p–h, p–s and T–s using the solved refrigerant and its own property reference."""
    fluid = system.getRefrigerant()
    condenser = system.getCondenserOutlet()
    stages = [stage for stage in system.getStages() if stage.getMassFlow() > 0]
    if not stages:
        raise ValueError("A cycle diagram requires a running compressor stage")
    levels = sorted(system.getLevels(), key=lambda level: -level.getTemperature())
    temperatures = np.linspace(levels[-1].getTemperature(), system.getCondensingTemperature(), 48)
    liquid = [fluid.saturatedLiquid(float(t)) for t in temperatures]
    vapor = [fluid.saturatedVapor(float(t)) for t in temperatures]
    fig, axes = plt.subplots(1, 3, figsize=(13, 4.6), layout="constrained")

    def draw(states, **style):
        h = [state.getEnthalpy() / 1000.0 for state in states]
        s = [state.getEntropy() / 1000.0 for state in states]
        p = [state.getPressure() for state in states]
        t = [state.getTemperature() - 273.15 for state in states]
        for ax, x, y in zip(axes, (h, s, s), (p, p, t)):
            ax.plot(x, y, **style)

    draw(liquid, color="0.55", lw=1, label="Saturation limits")
    draw(vapor, color="0.55", lw=1)
    draw([condenser], marker="s", color="black", label="Condenser outlet")
    for stage in stages:
        suction, discharge = stage.getSuction(), stage.getDischarge()
        ideal = fluid.stateAtPS(discharge.getPressure(), suction.getEntropy())
        draw([suction, ideal], color="0.45", ls=":", lw=1)
        draw([suction, discharge], marker="o", ms=4, label=str(stage.getLevelName()) + " compression")
    # Each valve starts at its own liquid source. Never join parallel side loads into a serial path.
    flash = str(system.getEconomizerType()) == "FLASH_TANK"
    for index, level in enumerate(levels):
        source = condenser
        if flash and index > 0:
            source = fluid.saturatedLiquid(levels[index - 1].getTemperature())
        if level.getFeedFlow() <= 0:
            continue
        outlet = fluid.stateAtPH(level.getPressure(), source.getEnthalpy())
        draw([source, outlet], color="tab:purple", ls="--", marker="v", ms=3,
             label="Expansion" if index == 0 else None)
        if level.getDuty() > 0:
            inlet = fluid.saturatedLiquid(level.getTemperature()) if flash else outlet
            evap_out = fluid.saturatedVapor(level.getTemperature())
            if level.getSuperheat() > 0:
                evap_out = fluid.vaporAt(level.getPressure(), level.getTemperature() + level.getSuperheat())
            draw([inlet, evap_out], color="tab:green", lw=1.3, label="Evaporation" if index == 0 else None)
    # Show desuperheating, isothermal condensation and any liquid subcooling separately.
    draw([stages[0].getDischarge(), vapor[-1], liquid[-1], condenser],
         color="tab:red", lw=1, label="Heat rejection")
    axes[0].set(xlabel="Specific enthalpy (kJ/kg)", ylabel="Absolute pressure (bar)", yscale="log")
    axes[1].set(xlabel="Specific entropy (kJ/(kg K))", ylabel="Absolute pressure (bar)", yscale="log")
    axes[2].set(xlabel="Specific entropy (kJ/(kg K))", ylabel="Temperature (°C)")
    for ax in axes:
        ax.grid(alpha=0.2)
    axes[0].plot([], [], color="0.45", ls=":", label="Isentropic reference")
    fig.legend(*axes[0].get_legend_handles_labels(), loc="outside lower center", ncols=4, frameon=False)
    fig.suptitle(f"{system.getName()} · {fluid.getComponentName()} · {fluid.getModelName()}")
    return fig


def plot_exchanger(exchanger):
    """Plot NeqSim's unshifted hot and cold composites, using each curve's own duty coordinate."""
    fig, ax = plt.subplots(figsize=(7.5, 4.6), layout="constrained")
    for label, curve, color in (("Hot", exchanger.getHotCompositeCurve(), "tab:red"),
                                ("Cold", exchanger.getColdCompositeCurve(), "tab:blue")):
        points = np.asarray(curve, dtype=float)
        if points.ndim != 2 or points.shape[1] != 2 or len(points) < 2 or not np.isfinite(points).all():
            raise ValueError(f"{label} composite requires a solved exchanger with finite zone results")
        ax.plot(points[:, 0] / 1000, points[:, 1], label=label, color=color)
    mita = exchanger.getMITA()
    if not isfinite(mita):
        raise ValueError("No finite minimum internal approach")
    ax.set(xlabel="Cumulative duty from cold end (MW)", ylabel="Temperature (°C)",
           title=f"{exchanger.getName()} · minimum internal approach {mita:.2f} K")
    ax.grid(alpha=0.2)
    ax.legend()
    return fig
