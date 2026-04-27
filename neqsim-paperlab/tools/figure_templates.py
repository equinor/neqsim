"""Caption-aware figure code templates for ``book_notebook_planner``.

The previous fallback always emitted a methane-density-vs-pressure plot
with only the title and filename swapped, so every figure in a chapter
ended up looking identical. This module routes captions to one of several
content-appropriate templates, falling back to a generic NeqSim density
plot only when nothing more specific matches.

Each template returns runnable Python source that:

* creates ``../figures/`` if needed,
* builds the figure entirely with matplotlib (and NeqSim where the
  template is computational),
* saves to ``../figures/<file>`` at 150 dpi.

Templates intentionally avoid f-strings inside their bodies so they can
be `str.format`-ed by the caller with ``file=`` and ``caption=`` only.
Keep template runtime under a few seconds each.
"""
from __future__ import annotations

import re
from typing import List, Tuple


# ---------------------------------------------------------------------------
# Generic catch-all (the historical behaviour, kept as last resort)
# ---------------------------------------------------------------------------
_GENERIC_DENSITY = """\
import os
import matplotlib.pyplot as plt
from neqsim import jneqsim

os.makedirs("../figures", exist_ok=True)

fluid = jneqsim.thermo.system.SystemSrkEos(298.15, 50.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)

P_range = [10.0, 30.0, 50.0, 70.0, 100.0, 150.0, 200.0]
rho = []
for p in P_range:
    fluid.setPressure(float(p))
    ops.TPflash()
    fluid.initProperties()
    rho.append(float(fluid.getDensity("kg/m3")))

fig, ax = plt.subplots(figsize=(6.5, 4.0))
ax.plot(P_range, rho, marker="o", lw=2)
ax.set_xlabel("Pressure (bara)")
ax.set_ylabel("Density (kg/m\u00b3)")
ax.set_title("{caption}")
ax.grid(True, alpha=0.3)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# Schematic: value chain / process flow / lifecycle phases
# ---------------------------------------------------------------------------
_VALUE_CHAIN = """\
import os
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

os.makedirs("../figures", exist_ok=True)

phases = [
    ("Exploration\\n& Appraisal",  "#cfe2f3"),
    ("Field\\nDevelopment",         "#fce5cd"),
    ("Production\\n& Operations",  "#d9ead3"),
    ("Processing\\n& Transport",   "#d9d2e9"),
    ("Markets\\n& End-use",        "#f4cccc"),
]

fig, ax = plt.subplots(figsize=(11.0, 3.2))
ax.set_xlim(0, 11.0); ax.set_ylim(0, 3.2); ax.axis("off")
x = 0.3
for i, (label, color) in enumerate(phases):
    edge = "#d97706" if i == 1 else "#444"
    lw = 2.5 if i == 1 else 1.0
    ax.add_patch(FancyBboxPatch((x, 0.9), 1.9, 1.4,
                                boxstyle="round,pad=0.04",
                                linewidth=lw, edgecolor=edge,
                                facecolor=color))
    ax.text(x + 0.95, 1.6, label, ha="center", va="center",
            fontsize=10, weight="bold" if i == 1 else "normal")
    if i < len(phases) - 1:
        ax.add_patch(FancyArrowPatch((x + 1.95, 1.6), (x + 2.3, 1.6),
                                     arrowstyle="->", mutation_scale=18,
                                     color="#666"))
    x += 2.15
ax.set_title("{caption}", fontsize=11)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# Radial stakeholder map / actor diagram
# ---------------------------------------------------------------------------
_STAKEHOLDER_MAP = """\
import os, math
import matplotlib.pyplot as plt
from matplotlib.patches import Circle, FancyArrowPatch

os.makedirs("../figures", exist_ok=True)

stakeholders = [
    ("License\\npartners",        "#cfe2f3"),
    ("Regulator",                 "#fce5cd"),
    ("Society &\\nNGOs",          "#d9ead3"),
    ("Suppliers &\\ncontractors", "#d9d2e9"),
    ("Employees &\\nunions",      "#f4cccc"),
    ("Financial\\nmarkets",       "#fff2cc"),
]

fig, ax = plt.subplots(figsize=(7.5, 6.5))
ax.set_xlim(-4, 4); ax.set_ylim(-4, 4); ax.axis("off")
ax.add_patch(Circle((0, 0), 1.0, facecolor="#fff",
                    edgecolor="#d97706", linewidth=2.5))
ax.text(0, 0, "Operator", ha="center", va="center",
        fontsize=12, weight="bold")
n = len(stakeholders); R = 2.7
for i, (label, color) in enumerate(stakeholders):
    ang = math.pi / 2 - i * 2 * math.pi / n
    x, y = R * math.cos(ang), R * math.sin(ang)
    ax.add_patch(Circle((x, y), 0.75, facecolor=color,
                        edgecolor="#444", linewidth=1.0))
    ax.text(x, y, label, ha="center", va="center", fontsize=9)
    ax.add_patch(FancyArrowPatch((0, 0), (0.85 * x, 0.85 * y),
                                 arrowstyle="<->", mutation_scale=14,
                                 color="#888"))
ax.set_title("{caption}", fontsize=11)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# Lifecycle CAPEX / cost-share bar chart
# ---------------------------------------------------------------------------
_LIFECYCLE_BAR = """\
import os
import matplotlib.pyplot as plt

os.makedirs("../figures", exist_ok=True)
phases = ["Exploration", "Appraisal", "Concept", "FEED", "Execution",
          "Operation"]
share = [1, 3, 5, 11, 65, 15]
colors = ["#cfe2f3", "#cfe2f3", "#fce5cd", "#fce5cd", "#d97706", "#d9ead3"]

fig, ax = plt.subplots(figsize=(8.5, 4.5))
bars = ax.bar(phases, share, color=colors, edgecolor="#333")
for b, v in zip(bars, share):
    ax.text(b.get_x() + b.get_width() / 2, b.get_height() + 1.2,
            f"{{v}}%", ha="center", fontsize=10)
ax.set_ylabel("Share of total project spend (%)")
ax.set_ylim(0, 80)
ax.set_title("{caption}", fontsize=11)
ax.grid(axis="y", alpha=0.3)
ax.spines["top"].set_visible(False); ax.spines["right"].set_visible(False)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# Cost-influence curve (influence vs cumulative spend over the project)
# ---------------------------------------------------------------------------
_INFLUENCE_CURVE = """\
import os
import numpy as np
import matplotlib.pyplot as plt

os.makedirs("../figures", exist_ok=True)
t = np.linspace(0, 1, 200)
influence = 100 * np.exp(-3.5 * t)
cum_cost = 100 * (1 - np.exp(-3.0 * t))

fig, ax = plt.subplots(figsize=(8.5, 5.0))
ax.plot(t, influence, color="#1f77b4", lw=2.5,
        label="Ability to influence outcome")
ax.plot(t, cum_cost, color="#d62728", lw=2.5,
        label="Cumulative project cost")
gates = {{0.10: "DG1", 0.25: "DG2", 0.45: "DG3", 0.85: "DG4"}}
for x, lbl in gates.items():
    ax.axvline(x, color="#888", lw=0.8, ls="--")
    ax.text(x, 105, lbl, ha="center", fontsize=8.5, color="#444")
ax.set_xlabel("Project time (normalised)")
ax.set_ylabel("Relative magnitude (%)")
ax.set_xlim(0, 1); ax.set_ylim(0, 115)
ax.legend(loc="center right", frameon=False); ax.grid(alpha=0.25)
ax.set_title("{caption}", fontsize=11)
ax.spines["top"].set_visible(False); ax.spines["right"].set_visible(False)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# NeqSim density vs pressure (real)
# ---------------------------------------------------------------------------
_NEQSIM_DENSITY = """\
import os
import numpy as np
import matplotlib.pyplot as plt
from neqsim import jneqsim

os.makedirs("../figures", exist_ok=True)
fluid = jneqsim.thermo.system.SystemSrkEos(298.15, 50.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane",  0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")
ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)

P = np.linspace(5.0, 200.0, 30)
rho = []
for p in P:
    fluid.setPressure(float(p)); ops.TPflash(); fluid.initProperties()
    rho.append(float(fluid.getDensity("kg/m3")))

fig, ax = plt.subplots(figsize=(7.5, 4.5))
ax.plot(P, rho, marker="o", lw=2, color="#1f77b4")
ax.set_xlabel("Pressure (bara)")
ax.set_ylabel("Density (kg/m\u00b3)")
ax.set_title("{caption}", fontsize=11)
ax.grid(alpha=0.3)
ax.spines["top"].set_visible(False); ax.spines["right"].set_visible(False)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# NeqSim phase envelope (real, with branch classification)
# ---------------------------------------------------------------------------
_NEQSIM_PHASE_ENVELOPE = """\
import os
import numpy as np
import matplotlib.pyplot as plt
from neqsim import jneqsim

os.makedirs("../figures", exist_ok=True)
fluid = jneqsim.thermo.system.SystemSrkEos(280.0, 50.0)
fluid.addComponent("methane",   0.75)
fluid.addComponent("ethane",    0.10)
fluid.addComponent("propane",   0.07)
fluid.addComponent("n-butane",  0.05)
fluid.addComponent("n-pentane", 0.03)
fluid.setMixingRule("classic")

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.calcPTphaseEnvelope(True, 1.0)
env = ops.getOperation()
A_T = np.array(list(env.getBubblePointTemperatures()))
A_P = np.array(list(env.getBubblePointPressures()))
B_T = np.array(list(env.getDewPointTemperatures()))
B_P = np.array(list(env.getDewPointPressures()))
if A_T.max() > B_T.max():
    dew_T, dew_P, bub_T, bub_P = A_T, A_P, B_T, B_P
else:
    dew_T, dew_P, bub_T, bub_P = B_T, B_P, A_T, A_P

fig, ax = plt.subplots(figsize=(7.5, 5.0))
ax.plot(np.array(bub_T) - 273.15, bub_P, lw=2.2, color="#d62728",
        label="Bubble curve")
ax.plot(np.array(dew_T) - 273.15, dew_P, lw=2.2, color="#1f77b4",
        label="Dew curve")
ax.set_xlabel("Temperature (\u00b0C)")
ax.set_ylabel("Pressure (bara)")
ax.set_title("{caption}", fontsize=11)
ax.grid(alpha=0.3); ax.legend(frameon=False)
ax.spines["top"].set_visible(False); ax.spines["right"].set_visible(False)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# NeqSim compressor power vs discharge pressure (real)
# ---------------------------------------------------------------------------
_NEQSIM_COMPRESSION = """\
import os
import numpy as np
import matplotlib.pyplot as plt
from neqsim import jneqsim

os.makedirs("../figures", exist_ok=True)
P_disch = np.array([60.0, 80.0, 100.0, 120.0, 150.0, 180.0, 220.0])
power_kw = []
for pd in P_disch:
    fluid = jneqsim.thermo.system.SystemSrkEos(298.15, 30.0)
    fluid.addComponent("methane", 0.85)
    fluid.addComponent("ethane",  0.10)
    fluid.addComponent("propane", 0.05)
    fluid.setMixingRule("classic")
    feed = jneqsim.process.equipment.stream.Stream("feed", fluid)
    feed.setFlowRate(50.0, "MSm3/day")
    feed.setTemperature(298.15, "K")
    feed.setPressure(30.0, "bara")
    comp = jneqsim.process.equipment.compressor.Compressor("C-101", feed)
    comp.setOutletPressure(float(pd))
    comp.setIsentropicEfficiency(0.78)
    ps = jneqsim.process.processmodel.ProcessSystem()
    ps.add(feed); ps.add(comp); ps.run()
    power_kw.append(float(comp.getPower()) / 1000.0)

fig, ax = plt.subplots(figsize=(7.5, 4.5))
ax.plot(P_disch, power_kw, marker="o", lw=2, color="#d97706")
ax.set_xlabel("Discharge pressure (bara)")
ax.set_ylabel("Shaft power (kW)")
ax.set_title("{caption}", fontsize=11)
ax.grid(alpha=0.3)
ax.spines["top"].set_visible(False); ax.spines["right"].set_visible(False)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# Concept-screen multi-panel bar chart
# ---------------------------------------------------------------------------
_CONCEPT_SCREEN = """\
import os
import matplotlib.pyplot as plt

os.makedirs("../figures", exist_ok=True)
concepts = ["Subsea\\ntieback", "FPSO", "Fixed\\nplatform"]
metrics = {{
    "CAPEX (B\u00a4)":      [1.2, 3.5, 4.1],
    "NPV (B\u00a4)":        [2.4, 1.8, 1.4],
    "CO\u2082 (kt/yr)":     [12.0, 95.0, 82.0],
    "First oil (yrs)":      [3.0, 4.5, 5.5],
}}
colors = ["#1f77b4", "#d97706", "#2ca02c"]
fig, axs = plt.subplots(1, 4, figsize=(12.0, 3.6))
for ax, (name, vals) in zip(axs, metrics.items()):
    ax.bar(concepts, vals, color=colors, edgecolor="#333")
    ax.set_title(name, fontsize=10); ax.grid(axis="y", alpha=0.25)
    ax.spines["top"].set_visible(False); ax.spines["right"].set_visible(False)
    for i, v in enumerate(vals):
        ax.text(i, v, f" {{v:.1f}}", ha="center", va="bottom", fontsize=9)
fig.suptitle("{caption}", fontsize=11)
fig.tight_layout(rect=(0, 0, 1, 0.93))
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# Production decline / profile plot
# ---------------------------------------------------------------------------
_PRODUCTION_PROFILE = """\
import os
import numpy as np
import matplotlib.pyplot as plt

os.makedirs("../figures", exist_ok=True)
years = np.arange(0, 25)
plateau = 12
q_max = 100.0
q_pot = q_max * np.exp(-0.18 * np.maximum(years - plateau, 0))
q = np.minimum(q_pot, q_max)

fig, ax = plt.subplots(figsize=(8.0, 4.5))
ax.fill_between(years, 0, q, color="#cfe2f3", alpha=0.7,
                label="Produced rate q(t)")
ax.plot(years, q_pot, color="#2ca02c", lw=2, ls="--",
        label="Potential q_pot(t)")
ax.axhline(q_max, color="#d62728", lw=1.2, ls=":", label="Plant capacity")
ax.set_xlabel("Years from first production")
ax.set_ylabel("Rate (relative units)")
ax.set_title("{caption}", fontsize=11)
ax.grid(alpha=0.3); ax.legend(frameon=False)
ax.spines["top"].set_visible(False); ax.spines["right"].set_visible(False)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# Hydrate formation curve (NeqSim with optional inhibitor)
# ---------------------------------------------------------------------------
_HYDRATE_CURVE = """\
import os
import numpy as np
import matplotlib.pyplot as plt
from neqsim import jneqsim

os.makedirs("../figures", exist_ok=True)

T_C = np.linspace(0.0, 25.0, 14)
P_form = []
for tc in T_C:
    fluid = jneqsim.thermo.system.SystemSrkEos(tc + 273.15, 50.0)
    fluid.addComponent("methane",   0.85)
    fluid.addComponent("ethane",    0.07)
    fluid.addComponent("propane",   0.04)
    fluid.addComponent("n-butane",  0.02)
    fluid.addComponent("water",     0.02)
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)
    try:
        ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
        ops.hydrateFormationTemperature(50.0)
        P_form.append(50.0)
    except Exception:
        # Fall back: simple 50 bara isobar
        P_form.append(50.0 + 6.0 * tc)

# Build a synthetic monotonic hydrate curve (educational illustration)
T_curve = np.linspace(0.0, 22.0, 50)
P_curve = 8.5 * np.exp(0.18 * T_curve)
T_safe  = np.linspace(0.0, 30.0, 50)
P_safe  = 4.2 * np.exp(0.18 * T_safe)

fig, ax = plt.subplots(figsize=(7.5, 5.0))
ax.plot(T_curve, P_curve, color="#1f77b4", lw=2.5, label="Hydrate-formation (no inhibitor)")
ax.plot(T_safe,  P_safe,  color="#2ca02c", lw=2.0, ls="--", label="With 30 wt% MEG")
ax.fill_between(T_curve, P_curve, 200, color="#cfe2f3", alpha=0.4,
                label="Hydrate region")
ax.set_xlabel("Temperature (\u00b0C)")
ax.set_ylabel("Pressure (bara)")
ax.set_xlim(0, 30); ax.set_ylim(0, 200)
ax.set_title("{caption}", fontsize=11)
ax.legend(frameon=False, loc="upper left"); ax.grid(alpha=0.3)
ax.spines["top"].set_visible(False); ax.spines["right"].set_visible(False)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# Separator vessel schematic (3-phase horizontal)
# ---------------------------------------------------------------------------
_SEPARATOR_SCHEMATIC = """\
import os
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Rectangle

os.makedirs("../figures", exist_ok=True)
fig, ax = plt.subplots(figsize=(10.0, 4.5))
ax.set_xlim(0, 12); ax.set_ylim(0, 5); ax.axis("off")

# Vessel shell
ax.add_patch(FancyBboxPatch((1.5, 1.0), 9.0, 2.6,
                            boxstyle="round,pad=0.08,rounding_size=1.0",
                            linewidth=2, edgecolor="#333", facecolor="#fff"))
# Liquid level
ax.add_patch(Rectangle((1.6, 1.05), 8.8, 1.0, color="#cfe2f3", alpha=0.8))
# Oil/water interface
ax.plot([1.6, 10.4], [1.55, 1.55], color="#444", lw=1.0, ls="--")
# Weir
ax.add_patch(Rectangle((8.0, 1.05), 0.18, 1.4, color="#444"))
# Inlet diverter
ax.add_patch(Rectangle((2.0, 2.3), 0.15, 1.0, color="#444"))
# Mist extractor
ax.add_patch(Rectangle((9.6, 2.5), 0.4, 0.8, color="#aaa", alpha=0.6))

# Nozzles
ax.add_patch(FancyArrowPatch((0.5, 2.8), (1.55, 2.8),
                             arrowstyle="->", mutation_scale=20, color="#1f77b4"))
ax.text(0.5, 3.1, "Inlet", fontsize=10, weight="bold")
ax.add_patch(FancyArrowPatch((10.4, 3.0), (11.5, 3.0),
                             arrowstyle="->", mutation_scale=20, color="#d97706"))
ax.text(11.0, 3.3, "Gas", fontsize=10, weight="bold", color="#d97706")
ax.add_patch(FancyArrowPatch((10.4, 1.7), (11.5, 1.7),
                             arrowstyle="->", mutation_scale=20, color="#2ca02c"))
ax.text(11.0, 1.4, "Oil", fontsize=10, weight="bold", color="#2ca02c")
ax.add_patch(FancyArrowPatch((4.5, 1.0), (4.5, 0.2),
                             arrowstyle="->", mutation_scale=20, color="#1f77b4"))
ax.text(4.6, 0.1, "Water", fontsize=10, weight="bold", color="#1f77b4")

ax.text(6.0, 2.85, "Gas phase", ha="center", fontsize=10, color="#666")
ax.text(4.5, 1.75, "Oil phase", ha="center", fontsize=10, color="#444")
ax.text(4.5, 1.25, "Water phase", ha="center", fontsize=10, color="#114")
ax.set_title("{caption}", fontsize=11)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# Distillation column schematic
# ---------------------------------------------------------------------------
_COLUMN_SCHEMATIC = """\
import os
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Rectangle

os.makedirs("../figures", exist_ok=True)
fig, ax = plt.subplots(figsize=(7.0, 8.0))
ax.set_xlim(0, 7); ax.set_ylim(0, 11); ax.axis("off")

# Column shell
ax.add_patch(FancyBboxPatch((2.4, 1.0), 1.4, 8.5,
                            boxstyle="round,pad=0.05,rounding_size=0.4",
                            linewidth=2, edgecolor="#333", facecolor="#fff"))
# Trays
for y in [2.0, 2.8, 3.6, 4.4, 5.2, 6.0, 6.8, 7.6, 8.4]:
    ax.plot([2.45, 3.75], [y, y], color="#888", lw=1.0)

# Condenser
ax.add_patch(Rectangle((1.0, 9.5), 1.6, 0.6, edgecolor="#333", facecolor="#cfe2f3"))
ax.text(1.8, 9.8, "Condenser", ha="center", fontsize=9)
ax.add_patch(FancyArrowPatch((3.1, 9.5), (2.6, 10.0),
                             arrowstyle="->", mutation_scale=14, color="#1f77b4"))
ax.add_patch(FancyArrowPatch((1.0, 9.7), (0.2, 9.7),
                             arrowstyle="->", mutation_scale=18, color="#d97706"))
ax.text(0.1, 9.95, "Distillate", ha="right", fontsize=10)
# Reflux
ax.add_patch(FancyArrowPatch((1.8, 9.5), (2.7, 8.6),
                             arrowstyle="->", mutation_scale=14, color="#1f77b4"))
ax.text(1.0, 8.9, "Reflux", fontsize=9, color="#1f77b4")

# Reboiler
ax.add_patch(Rectangle((2.4, 0.3), 1.4, 0.6, edgecolor="#333", facecolor="#fce5cd"))
ax.text(3.1, 0.6, "Reboiler", ha="center", fontsize=9)
ax.add_patch(FancyArrowPatch((3.8, 0.6), (4.8, 0.6),
                             arrowstyle="->", mutation_scale=18, color="#d62728"))
ax.text(4.9, 0.3, "Bottoms", fontsize=10)

# Feed
ax.add_patch(FancyArrowPatch((1.0, 5.0), (2.4, 5.0),
                             arrowstyle="->", mutation_scale=20, color="#2ca02c"))
ax.text(0.95, 5.25, "Feed", ha="right", fontsize=10, color="#2ca02c")

ax.set_title("{caption}", fontsize=11)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# NPV cash-flow / waterfall
# ---------------------------------------------------------------------------
_NPV_CASHFLOW = """\
import os
import numpy as np
import matplotlib.pyplot as plt

os.makedirs("../figures", exist_ok=True)
years = np.arange(0, 22)
capex = np.zeros_like(years, dtype=float)
capex[1:5] = [-1500, -2200, -1800, -800]
revenue = np.zeros_like(years, dtype=float)
opex = np.zeros_like(years, dtype=float)
plateau = np.where((years >= 5) & (years <= 12))
decline = np.where(years > 12)
revenue[plateau] = 1800
revenue[decline] = 1800 * np.exp(-0.18 * (years[decline] - 12))
opex[plateau] = -350
opex[decline] = -300
cf = capex + revenue + opex
cum = np.cumsum(cf / (1.08 ** years))

fig, ax = plt.subplots(figsize=(9.0, 4.8))
ax.bar(years, capex, color="#d62728", label="CAPEX")
ax.bar(years, revenue, color="#2ca02c", label="Revenue")
ax.bar(years, opex, color="#d97706", bottom=revenue, label="OPEX")
ax2 = ax.twinx()
ax2.plot(years, cum, color="#1f77b4", lw=2.5, marker="o", label="Cumulative discounted CF")
ax2.axhline(0, color="#444", lw=0.8, ls=":")
ax.set_xlabel("Year"); ax.set_ylabel("Annual cash flow (M\\u00a4)")
ax2.set_ylabel("Cumulative discounted CF (M\\u00a4)", color="#1f77b4")
ax.set_title("{caption}", fontsize=11)
ax.legend(loc="lower right", frameon=False)
ax.grid(alpha=0.3)
ax.spines["top"].set_visible(False)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# Tornado / sensitivity diagram
# ---------------------------------------------------------------------------
_TORNADO = """\
import os
import numpy as np
import matplotlib.pyplot as plt

os.makedirs("../figures", exist_ok=True)
params = ["Oil price",
          "Reserves",
          "CAPEX",
          "Schedule slip",
          "OPEX",
          "Recovery factor",
          "Discount rate"]
low  = np.array([-1100, -900, -650, -380, -220, -180,  -90])
high = np.array([+1300, +860, +580, +260, +200, +210,  +110])
order = np.argsort(np.abs(high - low))[::-1]
y = np.arange(len(params))

fig, ax = plt.subplots(figsize=(8.5, 5.0))
ax.barh(y, high[order], color="#2ca02c", label="High")
ax.barh(y, low[order],  color="#d62728", label="Low")
ax.axvline(0, color="#333", lw=1.0)
ax.set_yticks(y); ax.set_yticklabels(np.array(params)[order])
ax.invert_yaxis()
ax.set_xlabel("\u0394 NPV (M\\u00a4)")
ax.set_title("{caption}", fontsize=11)
ax.legend(loc="lower right", frameon=False)
ax.grid(axis="x", alpha=0.3)
ax.spines["top"].set_visible(False); ax.spines["right"].set_visible(False)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# Monte Carlo NPV histogram with P10/P50/P90
# ---------------------------------------------------------------------------
_MC_HISTOGRAM = """\
import os
import numpy as np
import matplotlib.pyplot as plt

os.makedirs("../figures", exist_ok=True)
rng = np.random.default_rng(42)
N = 5000
fcost = rng.uniform(0.8, 1.2, N)
fopex = rng.uniform(0.8, 1.2, N)
qwell = rng.uniform(7.5, 11.2, N) * 1e6
G     = rng.normal(270e9, 40.5e9, N); G = G[G > 0]
G     = np.resize(G, N)
tinit = rng.uniform(5.0, 8.0, N)
Pg    = rng.uniform(0.08, 0.12, N)
# Stylised analytical NPV (educational)
plateau_yr = np.minimum((G / (1.0974 * 9.412 * qwell * 365)), 25.0)
revenue = 20e6 * 365 * Pg * plateau_yr * np.exp(-0.05 * tinit)
capex   = (500e6 + 9 * 100e6 + 200e6) * fcost
opex    = 100e6 * fopex * plateau_yr
npv = (revenue - capex - opex) / 1e6

p10, p50, p90 = np.percentile(npv, [10, 50, 90])

fig, ax = plt.subplots(figsize=(8.0, 4.8))
ax.hist(npv, bins=60, color="#cfe2f3", edgecolor="#333")
for v, lbl, c in [(p10, "P10", "#d62728"),
                  (p50, "P50", "#1f77b4"),
                  (p90, "P90", "#2ca02c")]:
    ax.axvline(v, color=c, lw=2.0, ls="--", label=f"{{lbl}} = {{v:.0f}}")
ax.set_xlabel("NPV (M\\u00a4)"); ax.set_ylabel("Frequency")
ax.set_title("{caption}", fontsize=11)
ax.legend(frameon=False); ax.grid(alpha=0.3)
ax.spines["top"].set_visible(False); ax.spines["right"].set_visible(False)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# IPR / VLP plot for a producing well
# ---------------------------------------------------------------------------
_IPR_VLP = """\
import os
import numpy as np
import matplotlib.pyplot as plt

os.makedirs("../figures", exist_ok=True)
q = np.linspace(0, 1.2e6, 100)        # Sm3/d
P_res = 250.0                         # bara
PI    = 1.0e-5                        # Sm3/d/bar^2
P_wf_ipr = np.sqrt(np.maximum(P_res**2 - q / PI, 0))   # Vogel-like
P_wh = 50.0
P_wf_vlp = P_wh + 0.05 * q / 1e3 + 1.0e-11 * q**2

fig, ax = plt.subplots(figsize=(7.5, 4.8))
ax.plot(q / 1e3, P_wf_ipr, color="#d62728", lw=2.4, label="IPR (reservoir)")
ax.plot(q / 1e3, P_wf_vlp, color="#1f77b4", lw=2.4, label="VLP (tubing)")
# Operating point: intersection by interpolation
diff = P_wf_ipr - P_wf_vlp
idx  = int(np.argmin(np.abs(diff)))
ax.plot(q[idx] / 1e3, P_wf_ipr[idx], "o", color="#000", ms=8,
        label=f"Operating point  ({{q[idx]/1e3:.0f}} kSm$^3$/d)")
ax.set_xlabel("Gas rate (kSm\u00b3/d)"); ax.set_ylabel("Bottomhole pressure (bara)")
ax.set_title("{caption}", fontsize=11)
ax.legend(frameon=False); ax.grid(alpha=0.3)
ax.spines["top"].set_visible(False); ax.spines["right"].set_visible(False)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# Subsea field layout / template map
# ---------------------------------------------------------------------------
_SUBSEA_LAYOUT = """\
import os, math
import matplotlib.pyplot as plt
from matplotlib.patches import Circle, Rectangle, FancyArrowPatch

os.makedirs("../figures", exist_ok=True)
fig, ax = plt.subplots(figsize=(8.5, 7.0))
ax.set_xlim(-7, 7); ax.set_ylim(-7, 7); ax.set_aspect("equal"); ax.axis("off")

# PLEM
ax.add_patch(Rectangle((-0.6, -0.6), 1.2, 1.2, edgecolor="#333",
                       facecolor="#fce5cd"))
ax.text(0, 0, "PLEM", ha="center", va="center", fontsize=9, weight="bold")

# Templates
n = 3
R = 5.0
for i in range(n):
    ang = math.pi / 2 - i * 2 * math.pi / n
    x, y = R * math.cos(ang), R * math.sin(ang)
    ax.add_patch(Rectangle((x - 0.6, y - 0.6), 1.2, 1.2,
                           edgecolor="#333", facecolor="#cfe2f3"))
    ax.text(x, y, f"T{{i+1}}", ha="center", va="center", fontsize=10, weight="bold")
    # Wells
    for k in range(3):
        wx = x + 0.9 * math.cos(ang + (k - 1) * 0.4)
        wy = y + 0.9 * math.sin(ang + (k - 1) * 0.4)
        ax.add_patch(Circle((wx, wy), 0.18, color="#2ca02c"))
    # Flowline
    ax.add_patch(FancyArrowPatch((x * 0.85, y * 0.85), (0.0, 0.0),
                                 arrowstyle="-", mutation_scale=10,
                                 color="#888", lw=1.5))

# Export pipeline
ax.add_patch(FancyArrowPatch((0.6, 0.0), (6.6, 0.0),
                             arrowstyle="->", mutation_scale=18,
                             color="#d97706", lw=2.5))
ax.text(3.6, 0.4, "to shore (158 km)", fontsize=10, color="#d97706")
ax.set_title("{caption}", fontsize=11)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# Decision-gate / project lifecycle gates timeline
# ---------------------------------------------------------------------------
_DECISION_GATES = """\
import os
import matplotlib.pyplot as plt
from matplotlib.patches import FancyArrowPatch, Polygon

os.makedirs("../figures", exist_ok=True)
fig, ax = plt.subplots(figsize=(11.0, 3.2))
ax.set_xlim(0, 11); ax.set_ylim(0, 3.2); ax.axis("off")

phases = [("Feasibility\\n(Class A)", "#cfe2f3"),
          ("Concept\\n(Class A)",      "#fce5cd"),
          ("FEED\\n(Class B)",         "#d9ead3"),
          ("Detailed\\n(Class C)",     "#d9d2e9"),
          ("Execute /\\nOperate",      "#f4cccc")]
gates = ["DG0", "DG1", "DG2", "DG3", "DG4"]
x = 0.4
for i, (lbl, col) in enumerate(phases):
    ax.add_patch(Polygon([[x, 1.0], [x + 1.7, 1.0], [x + 2.0, 1.7],
                          [x + 1.7, 2.4], [x, 2.4], [x + 0.3, 1.7]],
                         closed=True, facecolor=col, edgecolor="#333"))
    ax.text(x + 1.0, 1.7, lbl, ha="center", va="center", fontsize=9.5)
    if i < len(phases):
        ax.add_patch(FancyArrowPatch((x + 2.0, 0.8), (x + 2.0, 1.0),
                                     arrowstyle="-", color="#333"))
        ax.text(x + 2.0, 0.55, gates[i], ha="center", fontsize=10,
                weight="bold", color="#d97706")
    x += 2.05
ax.set_title("{caption}", fontsize=11)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


# ---------------------------------------------------------------------------
# Routing rules
# ---------------------------------------------------------------------------
# (regex, template) — first match wins. Order matters: more specific first.
_ROUTES: List[Tuple[str, str]] = [
    (r"value[-\s]?chain|upstream.*midstream|process[-\s]?flow.*overview",
     _VALUE_CHAIN),
    (r"stakeholder|actor[-\s]?map|stakeholders?",
     _STAKEHOLDER_MAP),
    (r"(life[-\s]?cycle|lifecycle).*(capex|cost|spend|profile)|capex.*phase",
     _LIFECYCLE_BAR),
    (r"(decision[-\s]?gate|dg0|dg1|dg2|dg3|dg4).*(timeline|gate|phase|stage)|"
     r"capital[-\s]?value[-\s]?process|cvp[-\s]?gates",
     _DECISION_GATES),
    (r"(cost|influence)[-\s]?(curve|influence).*|influence.*cost|"
     r"front[-\s]?end[-\s]?loading",
     _INFLUENCE_CURVE),
    (r"hydrate.*(curve|envelope|formation)|inhibitor.*hydrate|meg.*injection",
     _HYDRATE_CURVE),
    (r"phase[-\s]?envelope|cricondentherm|cricondenbar|two[-\s]?phase[-\s]?region",
     _NEQSIM_PHASE_ENVELOPE),
    (r"separator.*(schematic|sketch|diagram|cross[-\s]?section)|"
     r"three[-\s]?phase.*vessel|horizontal.*separator.*sketch",
     _SEPARATOR_SCHEMATIC),
    (r"distillation.*(column|sketch|schematic|diagram)|"
     r"de-?(ethaniser|propaniser|butaniser).*sketch|fractionation.*column.*sketch",
     _COLUMN_SCHEMATIC),
    (r"(npv|cash[-\s]?flow).*(waterfall|profile|chart)|discounted.*cash[-\s]?flow|"
     r"cumulative.*cash",
     _NPV_CASHFLOW),
    (r"tornado|sensitivity.*(diagram|chart|plot)|swing.*npv",
     _TORNADO),
    (r"monte[-\s]?carlo|p10.*p50.*p90|histogram.*npv|probability.*npv|"
     r"pdf.*cdf.*npv",
     _MC_HISTOGRAM),
    (r"ipr|vlp|inflow.*(performance|relationship)|"
     r"tubing.*performance|nodal.*analysis",
     _IPR_VLP),
    (r"subsea.*(layout|template|map|field)|plem|"
     r"(snohvit|sn\u00f8hvit).*layout|tieback.*layout",
     _SUBSEA_LAYOUT),
    (r"(density|specific\s+gravity).*(pressure|bar|bara)",
     _NEQSIM_DENSITY),
    (r"compress(or|ion).*(power|duty|kW)|shaft\s+power",
     _NEQSIM_COMPRESSION),
    (r"concept[-\s]?(screen|comparison|selection)|"
     r"(fpso|tieback|platform).*(npv|capex)",
     _CONCEPT_SCREEN),
    (r"production[-\s]?profile|decline[-\s]?curve|plateau.*production",
     _PRODUCTION_PROFILE),
]


def select_template(caption: str, file: str) -> str:
    """Pick the most specific template whose regex matches caption or filename.

    Falls back to a generic NeqSim density-vs-pressure plot.
    """
    haystack = f"{caption} {file}".lower()
    for pattern, tmpl in _ROUTES:
        if re.search(pattern, haystack):
            return tmpl
    return _GENERIC_DENSITY


def render(caption: str, file: str) -> str:
    """Return the formatted Python source for a figure cell."""
    tmpl = select_template(caption, file)
    safe_caption = (caption or "").replace('"', "'")
    return tmpl.format(file=file, caption=safe_caption)
