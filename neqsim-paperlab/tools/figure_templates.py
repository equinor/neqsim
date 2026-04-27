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
    (r"(cost|influence)[-\s]?(curve|influence).*|influence.*cost|"
     r"decision[-\s]?gate.*curve",
     _INFLUENCE_CURVE),
    (r"phase[-\s]?envelope|cricondentherm|cricondenbar|two[-\s]?phase[-\s]?region",
     _NEQSIM_PHASE_ENVELOPE),
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
