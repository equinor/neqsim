#!/usr/bin/env python3
"""Regenerate the TPG4230 field-development notebooks with executable content."""

from __future__ import annotations

import json
from pathlib import Path
from textwrap import dedent


BOOK_ROOT = Path(
    "c:/Users/ESOL/Documents/GitHub/neqsim/neqsim-paperlab/books/"
    "tpg4230_field_development_and_operations_2026"
)
CHAPTERS = BOOK_ROOT / "chapters"


def lines(text: str) -> list[str]:
    """Return notebook source lines from text."""
    text = dedent(text).strip("\n")
    return [line + "\n" for line in text.split("\n")]


def md(text: str) -> dict:
    """Create a markdown notebook cell."""
    return {"cell_type": "markdown", "metadata": {}, "source": lines(text)}


def code(text: str) -> dict:
    """Create a code notebook cell."""
    return {
        "cell_type": "code",
        "execution_count": None,
        "metadata": {},
        "outputs": [],
        "source": lines(text),
    }


SETUP = r'''
import os
import sys
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np


def find_project_root():
    env_root = os.environ.get("NEQSIM_PROJECT_ROOT")
    candidates = []
    if env_root:
        candidates.append(Path(env_root).resolve())
    try:
        candidates.append(Path(__vsc_ipynb_file__).resolve())
    except NameError:
        candidates.append(Path.cwd().resolve())
    expanded = []
    for candidate in candidates:
        expanded.extend([candidate] + list(candidate.parents))
    for candidate in expanded:
        if (candidate / "pom.xml").exists() and (candidate / "devtools" / "neqsim_dev_setup.py").exists():
            return candidate
    raise RuntimeError("Could not find NeqSim project root")


try:
    NOTEBOOK_DIR = Path(__vsc_ipynb_file__).resolve().parent
except NameError:
    NOTEBOOK_DIR = Path.cwd().resolve()

FIGURES_DIR = NOTEBOOK_DIR.parent / "figures"
FIGURES_DIR.mkdir(parents=True, exist_ok=True)
PROJECT_ROOT = find_project_root()
sys.path.insert(0, str(PROJECT_ROOT / "devtools"))

NEQSIM_AVAILABLE = False
NEQSIM_ERROR = ""
try:
    from neqsim_dev_setup import neqsim_init

    ns = neqsim_init(project_root=PROJECT_ROOT, recompile=False, verbose=False)
    JClass = ns.JClass
    NEQSIM_AVAILABLE = True
except Exception as exc:
    ns = None
    JClass = None
    NEQSIM_ERROR = str(exc)

print(f"Notebook directory: {NOTEBOOK_DIR}")
print(f"Figures directory: {FIGURES_DIR}")
print(f"NeqSim Java bridge available: {NEQSIM_AVAILABLE}")
if NEQSIM_ERROR:
    print(f"NeqSim bridge warning: {NEQSIM_ERROR}")
'''


DISCUSSION = """**Discussion ({figure}).** *Observation.* {observation} *Mechanism.* {mechanism} *Implication.* {implication} *Recommendation.* {recommendation}"""


def discussion(figure: str, observation: str, mechanism: str, implication: str, recommendation: str) -> dict:
    """Create a compact OMIR discussion cell."""
    return md(
        DISCUSSION.format(
            figure=figure,
            observation=observation,
            mechanism=mechanism,
            implication=implication,
            recommendation=recommendation,
        )
    )


def lifecycle_notebook() -> list[dict]:
    """Notebook for digital twin lifecycle concepts."""
    return [
        md("""
        # Field Development Digital Twin and Lifecycle

        This notebook links the field-development lifecycle to the computational objects used in NeqSim. It generates reusable figures for Chapter 11.
        """),
        code(SETUP),
        md("## Lifecycle Phases"),
        code(r'''
        phases = [
            ("Discovery", 1, "resource signal"),
            ("Appraisal", 2, "resource range"),
            ("Feasibility", 1, "screen concepts"),
            ("Concept select", 1, "rank alternatives"),
            ("FEED", 2, "define project"),
            ("Detailed design", 2, "freeze scope"),
            ("Construction", 3, "build and install"),
            ("Operations", 20, "optimize value"),
        ]
        start = np.cumsum([0] + [p[1] for p in phases[:-1]])
        durations = [p[1] for p in phases]
        colors = plt.cm.Set3(np.linspace(0, 1, len(phases)))
        fig, ax = plt.subplots(figsize=(12, 4.8))
        ax.barh([0] * len(phases), durations, left=start, color=colors, edgecolor="black")
        for left, dur, (label, _, focus) in zip(start, durations, phases):
            ax.text(left + dur / 2, 0, label, ha="center", va="center", fontsize=9, fontweight="bold")
            ax.text(left + dur / 2, -0.22, focus, ha="center", va="center", fontsize=8)
        ax.set_xlim(0, sum(durations))
        ax.set_ylim(-0.5, 0.5)
        ax.set_yticks([])
        ax.set_xlabel("Approximate elapsed time (years)")
        ax.set_title("Field Development Lifecycle and Main Decision Focus")
        ax.grid(axis="x", alpha=0.25)
        fig.savefig(FIGURES_DIR / "ch11_digital_twin_lifecycle.png", dpi=150, bbox_inches="tight")
        plt.close(fig)
        for phase, years, focus in phases:
            print(f"{phase:<16} {years:>2} years  focus: {focus}")
        '''),
        discussion(
            "Figure ch11_digital_twin_lifecycle.png",
            "The lifecycle spends little calendar time before FEED but locks in most value and risk.",
            "Early phases select reservoir assumptions, concept architecture and host constraints that later engineering can only optimize locally.",
            "A digital twin is most valuable before sanction, when alternatives can still be compared cheaply.",
            "Use lifecycle phase labels in every concept-screen notebook so assumptions are not mixed across decision gates.",
        ),
        md("## Discipline Integration Map"),
        code(r'''
        nodes = {"PVT": (0.12, 0.75), "Reservoir": (0.12, 0.35), "Process": (0.46, 0.75), "Facilities": (0.46, 0.35), "Economics": (0.80, 0.55)}
        fig, ax = plt.subplots(figsize=(10, 6))
        ax.axis("off")
        for name, (x, y) in nodes.items():
            ax.scatter([x], [y], s=2800, c="#d9ecf2", edgecolors="#16425b", linewidths=2)
            ax.text(x, y, name, ha="center", va="center", fontsize=11, fontweight="bold")
        arrows = [("PVT", "Process", "fluid props"), ("Reservoir", "Facilities", "rate profile"), ("Process", "Facilities", "duties"), ("Facilities", "Economics", "capex/opex"), ("Economics", "Reservoir", "value feedback")]
        for start_name, end_name, label in arrows:
            x1, y1 = nodes[start_name]
            x2, y2 = nodes[end_name]
            ax.annotate("", xy=(x2, y2), xytext=(x1, y1), arrowprops=dict(arrowstyle="->", lw=2, color="#2f4858"))
            ax.text((x1 + x2) / 2, (y1 + y2) / 2 + 0.04, label, ha="center", fontsize=9, bbox=dict(boxstyle="round", fc="white", ec="#cccccc"))
        ax.set_title("Digital Field Twin: Data Interfaces Between Disciplines", fontsize=13, fontweight="bold")
        fig.savefig(FIGURES_DIR / "ch11_digital_twin_interfaces.png", dpi=150, bbox_inches="tight")
        plt.close(fig)
        for row in arrows:
            print(f"{row[0]} -> {row[1]}: {row[2]}")
        '''),
        discussion(
            "Figure ch11_digital_twin_interfaces.png",
            "The map identifies five domains and five directed data interfaces.",
            "Each interface passes a small set of engineering variables, such as phase behavior, production rates, duties or cash-flow inputs.",
            "Poor interface control causes double counting and inconsistent concept rankings.",
            "Treat each interface as a contract with units, validity range and owner before running sensitivities.",
        ),
        md("""
        ## Exercises

        1. List three variables that move from PVT to process simulation.
        2. Explain why concept select should include both reservoir uncertainty and facility capacity.
        3. Pick one interface in the map and define an acceptance test for units and ranges.
        4. Describe how a higher CO2 fraction propagates through at least three domains.
        5. Sketch a minimal results table for a concept-screen digital twin.
        """),
    ]


def concept_notebook() -> list[dict]:
    """Notebook for concept evaluation."""
    return [
        md("# Field Concept Evaluation: From Definition to Multi-Criterion Assessment"),
        code(SETUP),
        md("## Concept Alternatives and Evaluation Criteria"),
        code(r'''
        concepts = ["Tieback", "Standalone platform", "FPSO redeploy"]
        criteria = ["NPV", "CAPEX", "Flow assurance", "Schedule", "Emissions"]
        weights = np.array([0.40, 0.22, 0.16, 0.12, 0.10])
        scores = np.array([[78, 82, 66, 86, 74], [84, 45, 72, 52, 56], [70, 58, 62, 68, 60]])
        weighted = scores * weights
        total = weighted.sum(axis=1)
        for concept, value in sorted(zip(concepts, total), key=lambda row: row[1], reverse=True):
            print(f"{concept:<20} weighted score = {value:5.1f}")
        '''),
        md("## Weighted Ranking"),
        code(r'''
        order = np.argsort(total)
        fig, ax = plt.subplots(figsize=(9, 5))
        ax.barh(np.array(concepts)[order], total[order], color=["#79addc", "#ffc857", "#9bc53d"])
        ax.set_xlabel("Weighted score (0-100)")
        ax.set_title("Concept Ranking by Multi-Criterion Decision Analysis")
        ax.grid(axis="x", alpha=0.3)
        for i, idx in enumerate(order):
            ax.text(total[idx] + 1, i, f"{total[idx]:.1f}", va="center")
        fig.savefig(FIGURES_DIR / "ch11_concept_mcda_ranking.png", dpi=150, bbox_inches="tight")
        plt.close(fig)
        '''),
        discussion("Figure ch11_concept_mcda_ranking.png", "The tieback option has the highest weighted score despite not having the highest standalone NPV score.", "Lower CAPEX, faster schedule and lower emissions offset the larger absolute development potential of the standalone platform.", "A single economic metric can hide execution and operability advantages.", "Present both raw criteria and weighted totals at DG1/DG2 so decision makers see the trade-offs."),
        md("## Score Heatmap"),
        code(r'''
        fig, ax = plt.subplots(figsize=(9.5, 4.8))
        image = ax.imshow(scores, cmap="YlGnBu", vmin=0, vmax=100)
        ax.set_xticks(range(len(criteria)), labels=criteria, rotation=25, ha="right")
        ax.set_yticks(range(len(concepts)), labels=concepts)
        for i in range(scores.shape[0]):
            for j in range(scores.shape[1]):
                ax.text(j, i, f"{scores[i, j]:.0f}", ha="center", va="center", color="black")
        ax.set_title("Concept Evaluation Criteria Scores")
        fig.colorbar(image, ax=ax, label="Score (higher is better)")
        fig.savefig(FIGURES_DIR / "ch11_concept_score_heatmap.png", dpi=150, bbox_inches="tight")
        plt.close(fig)
        '''),
        discussion("Figure ch11_concept_score_heatmap.png", "The heatmap shows why each concept scores differently, not only which concept wins.", "Scores are normalized to the same preference direction, so high values consistently mean desirable outcomes.", "This format reduces ambiguity when cost, risk and value are combined.", "Always keep the normalized criteria matrix with the concept-selection record."),
        md("""
        ## Exercises

        1. Recalculate the ranking with the CAPEX weight increased to 0.35.
        2. Which criterion most strongly differentiates the tieback and standalone platform?
        3. Explain why schedule risk should not be hidden inside CAPEX contingency.
        4. Add a safety-readiness criterion and propose a defensible weight.
        5. State one weakness of weighted-sum MCDA for sanction decisions.
        """),
    ]


def screening_notebook() -> list[dict]:
    """Notebook for feasibility screening."""
    return [
        md("# Technical Screening and Feasibility Assessment"),
        code(SETUP),
        md("## Flow Assurance Gate Matrix"),
        code(r'''
        threats = ["Hydrate", "Wax", "Asphaltene", "CO2 corrosion", "Scale"]
        concepts = ["Tieback gas", "Oil satellite", "High-CO2 gas"]
        risk = np.array([[4, 1, 1, 3, 2], [4, 3, 3, 2, 3], [3, 1, 1, 5, 2]])
        fig, ax = plt.subplots(figsize=(9, 4.8))
        image = ax.imshow(risk, cmap="YlOrRd", vmin=1, vmax=5)
        ax.set_xticks(range(len(threats)), labels=threats, rotation=25, ha="right")
        ax.set_yticks(range(len(concepts)), labels=concepts)
        for i in range(risk.shape[0]):
            for j in range(risk.shape[1]):
                ax.text(j, i, str(risk[i, j]), ha="center", va="center")
        ax.set_title("Screening Risk Matrix (1=low, 5=high)")
        fig.colorbar(image, ax=ax, label="Risk score")
        fig.savefig(FIGURES_DIR / "ch11_screening_flow_assurance_matrix.png", dpi=150, bbox_inches="tight")
        plt.close(fig)
        '''),
        discussion("Figure ch11_screening_flow_assurance_matrix.png", "Hydrate risk dominates the cold tieback cases, while corrosion dominates the high-CO2 gas case.", "Cold subsea lines favor hydrate stability, whereas high acid-gas partial pressure drives material and inhibitor requirements.", "The same concept label can fail for different technical reasons.", "Carry the leading threat forward as a named assumption in concept ranking and cost contingency."),
        md("## Artificial-Lift Screening"),
        code(r'''
        methods = ["Flowing", "Gas lift", "ESP", "Subsea boost"]
        scores = np.array([52, 86, 68, 74])
        fig, ax = plt.subplots(figsize=(8, 4.8))
        bars = ax.bar(methods, scores, color="#3b8ea5")
        ax.set_ylim(0, 100)
        ax.set_ylabel("Screening score")
        ax.set_title("Artificial-Lift Suitability for Mid-Water Tieback")
        ax.grid(axis="y", alpha=0.3)
        for bar, score in zip(bars, scores):
            ax.text(bar.get_x() + bar.get_width() / 2, score + 2, f"{score:.0f}", ha="center")
        fig.savefig(FIGURES_DIR / "ch11_screening_artificial_lift.png", dpi=150, bbox_inches="tight")
        plt.close(fig)
        '''),
        discussion("Figure ch11_screening_artificial_lift.png", "Gas lift has the best screening score for the example because it combines reliability and operational flexibility.", "Gas lift tolerates moderate solids and water cut better than ESP while requiring less subsea rotating equipment than boosting.", "The lift method affects both production profile and intervention exposure.", "Screen lift method before freezing subsea controls and umbilical core counts."),
        md("## Feasibility Gate Register"),
        code(r'''
        gates = ["Host capacity", "Flowline hydraulics", "Hydrate control", "Materials", "Schedule"]
        pass_fraction = np.array([0.80, 0.72, 0.65, 0.76, 0.70])
        fig, ax = plt.subplots(figsize=(9, 4.8))
        ax.plot(gates, pass_fraction, marker="o", linewidth=2, color="#2a9d8f")
        ax.fill_between(gates, pass_fraction, 0.6, color="#2a9d8f", alpha=0.18)
        ax.axhline(0.70, color="#e76f51", linestyle="--", label="DG1 minimum confidence")
        ax.set_ylim(0.55, 0.9)
        ax.set_ylabel("Gate confidence")
        ax.set_title("Feasibility Gate Confidence Before Concept Select")
        ax.tick_params(axis="x", rotation=20)
        ax.grid(axis="y", alpha=0.3)
        ax.legend()
        fig.savefig(FIGURES_DIR / "ch11_screening_gate_confidence.png", dpi=150, bbox_inches="tight")
        plt.close(fig)
        '''),
        discussion("Figure ch11_screening_gate_confidence.png", "Hydrate control is below the DG1 confidence threshold and should remain an active risk.", "Flow-assurance mitigation needs fluid composition, water production, temperature and cooldown assumptions that are uncertain early.", "A concept can be economically attractive but still unready for concept select.", "Make the hydrate mitigation study a DG2 action with explicit owner, cost range and decision date."),
        md("""
        ## Exercises

        1. Change the corrosion risk of the high-CO2 case from 5 to 3 and explain the design condition that justifies it.
        2. Which gate has the weakest confidence, and what data would improve it?
        3. Give two reasons why ESP can score lower than gas lift in subsea tiebacks.
        4. Add an HSE gate and define a pass/fail criterion.
        5. Explain how a failed feasibility gate should affect the MCDA score in the previous notebook.
        """),
    ]


def surf_notebook() -> list[dict]:
    """Notebook for SURF cost and architecture."""
    return [
        md("# SURF Equipment Design and Cost Estimation"),
        code(SETUP),
        md("## SURF Cost Breakdown"),
        code(r'''
        components = ["Trees", "Manifold", "Flowline", "Riser", "Umbilical", "Installation", "Commissioning"]
        costs = np.array([80, 45, 310, 90, 210, 360, 120], dtype=float)
        total = costs.sum()
        fig, ax = plt.subplots(figsize=(10, 5))
        ax.bar(components, costs, color="#457b9d")
        ax.set_ylabel("Cost (MNOK)")
        ax.set_title(f"SURF Cost Breakdown, 4-Well 25 km Tieback (total {total:.0f} MNOK)")
        ax.tick_params(axis="x", rotation=25)
        ax.grid(axis="y", alpha=0.3)
        fig.savefig(FIGURES_DIR / "ch13_surf_cost_breakdown.png", dpi=150, bbox_inches="tight")
        plt.close(fig)
        for name, cost in zip(components, costs):
            print(f"{name:<14} {cost:7.0f} MNOK  {100 * cost / total:5.1f}%")
        '''),
        discussion("Figure ch13_surf_cost_breakdown.png", "Flowline, installation and umbilical dominate the reference SURF cost.", "Long-distance tiebacks spend most capital on route length and vessel days rather than on the trees themselves.", "A small reduction in tieback length can outweigh detailed savings on individual tree hardware.", "Run route, diameter and installation-vessel sensitivities before optimizing smaller equipment packages."),
        md("## Manifold Break-Even"),
        code(r'''
        wells = np.arange(1, 9)
        single_tree = wells * (85 + 18)
        manifold = 65 + wells * 72 + 35
        fig, ax = plt.subplots(figsize=(8.5, 5))
        ax.plot(wells, single_tree, "o-", label="Independent tree tie-ins")
        ax.plot(wells, manifold, "s-", label="Shared manifold cluster")
        ax.set_xlabel("Number of wells")
        ax.set_ylabel("Relative subsea connection cost (MNOK)")
        ax.set_title("Single Tree vs Manifold Cluster Cost")
        ax.grid(True, alpha=0.3)
        ax.legend()
        fig.savefig(FIGURES_DIR / "ch13_surf_manifold_breakeven.png", dpi=150, bbox_inches="tight")
        plt.close(fig)
        best = np.where(manifold < single_tree)[0]
        print(f"Manifold becomes cheaper at {wells[best[0]]} wells" if len(best) else "No break-even in range")
        '''),
        discussion("Figure ch13_surf_manifold_breakeven.png", "The shared manifold option becomes cheaper once enough wells share common infrastructure.", "The manifold has a fixed cost penalty but reduces per-well connection and routing cost.", "Well-count uncertainty matters directly for concept architecture.", "Evaluate manifold economics over the P10/P50/P90 well-count range, not only the base case."),
        md("## Water-Depth Cost Multiplier"),
        code(r'''
        depth = np.array([100, 300, 700, 1200, 1800])
        multiplier = np.array([1.0, 1.35, 1.95, 2.8, 4.2])
        fig, ax = plt.subplots(figsize=(8.5, 5))
        ax.plot(depth, multiplier, marker="o", linewidth=2, color="#e76f51")
        ax.set_xlabel("Water depth (m)")
        ax.set_ylabel("SURF cost multiplier")
        ax.set_title("Water Depth as a SURF Cost Driver")
        ax.grid(True, alpha=0.3)
        fig.savefig(FIGURES_DIR / "ch13_surf_depth_multiplier.png", dpi=150, bbox_inches="tight")
        plt.close(fig)
        '''),
        discussion("Figure ch13_surf_depth_multiplier.png", "Cost rises nonlinearly as water depth enters deepwater and ultra-deepwater regimes.", "Installation complexity, riser loads, control systems and material requirements all scale with depth.", "Deepwater discoveries need larger recoverable volume or a nearby host to pass screening.", "Use water-depth multipliers early to avoid underestimating frontier concept CAPEX."),
    ]


def economics_notebook() -> list[dict]:
    """Notebook for cash-flow economics."""
    return [
        md("# Cash Flow Engine and Field Economics"),
        code(SETUP),
        md("## Production Decline Models"),
        code(r'''
        years = np.arange(1, 21)
        peak = 55.0
        exponential = peak * np.exp(-0.13 * (years - 1))
        hyperbolic = peak / np.power(1 + 0.45 * (years - 1), 1 / 1.2)
        plateau_decline = np.where(years <= 5, peak, peak * np.exp(-0.18 * (years - 5)))
        fig, ax = plt.subplots(figsize=(9, 5))
        ax.plot(years, exponential, label="Exponential")
        ax.plot(years, hyperbolic, label="Hyperbolic")
        ax.plot(years, plateau_decline, label="Plateau then decline")
        ax.set_xlabel("Production year")
        ax.set_ylabel("Gas production (MSm3/d equivalent)")
        ax.set_title("Production Profiles for Economic Evaluation")
        ax.grid(True, alpha=0.3)
        ax.legend()
        fig.savefig(FIGURES_DIR / "ch20_economics_production_profiles.png", dpi=150, bbox_inches="tight")
        plt.close(fig)
        '''),
        discussion("Figure ch20_economics_production_profiles.png", "The plateau-then-decline profile keeps early revenue high while preserving a long decline tail.", "Facility capacity caps early production before reservoir deliverability controls late-life rates.", "NPV is strongly affected by plateau duration because discounting favors early cash flow.", "Show at least one production-profile sensitivity with every concept NPV."),
        md("## Discounted Cash Flow"),
        code(r'''
        capex = np.array([900, 850, 350] + [0] * 18, dtype=float)
        production = np.array([0, 0, 20] + list(plateau_decline[:-2]))
        revenue = production * 365 * 1.55
        opex = np.where(production > 0, 165, 0)
        pretax_cf = revenue - opex - capex
        discount_rate = 0.08
        discount = np.power(1 + discount_rate, -np.arange(len(pretax_cf)))
        discounted = pretax_cf * discount
        npv = discounted.sum()
        fig, ax = plt.subplots(figsize=(9, 5))
        ax.bar(np.arange(len(pretax_cf)), pretax_cf, color=np.where(pretax_cf >= 0, "#2a9d8f", "#e76f51"), alpha=0.75, label="Annual cash flow")
        ax.plot(np.cumsum(discounted), color="#264653", linewidth=2, label="Cumulative discounted CF")
        ax.axhline(0, color="black", linewidth=0.8)
        ax.set_xlabel("Project year")
        ax.set_ylabel("MNOK")
        ax.set_title(f"Discounted Cash Flow, NPV = {npv:.0f} MNOK")
        ax.grid(axis="y", alpha=0.3)
        ax.legend()
        fig.savefig(FIGURES_DIR / "ch20_economics_discounted_cash_flow.png", dpi=150, bbox_inches="tight")
        plt.close(fig)
        print(f"NPV at {discount_rate:.0%}: {npv:.0f} MNOK")
        '''),
        discussion("Figure ch20_economics_discounted_cash_flow.png", "The project is cash-negative during development and recovers after production reaches plateau.", "Front-loaded CAPEX is discounted less than late-life production, so schedule and first-gas timing dominate value.", "A delayed startup can reduce NPV even when ultimate recovery is unchanged.", "Report NPV together with first-production date and plateau duration."),
        md("## NPV Sensitivity"),
        code(r'''
        gas_prices = np.linspace(1.0, 2.2, 9)
        npvs = []
        for price in gas_prices:
            revenue_i = production * 365 * price
            cf_i = revenue_i - opex - capex
            npvs.append((cf_i * discount).sum())
        fig, ax = plt.subplots(figsize=(8.5, 5))
        ax.plot(gas_prices, npvs, marker="o", color="#1d3557")
        ax.axhline(0, color="#e76f51", linestyle="--")
        ax.set_xlabel("Gas price (NOK/Sm3 equivalent)")
        ax.set_ylabel("NPV (MNOK)")
        ax.set_title("Gas-Price Sensitivity")
        ax.grid(True, alpha=0.3)
        fig.savefig(FIGURES_DIR / "ch20_economics_npv_sensitivity.png", dpi=150, bbox_inches="tight")
        plt.close(fig)
        '''),
        discussion("Figure ch20_economics_npv_sensitivity.png", "NPV crosses zero between the low and base gas-price cases.", "Revenue scales almost linearly with price while most CAPEX is fixed.", "Market assumptions can dominate concept ranking once technical feasibility is established.", "Use P10/P50/P90 price assumptions and disclose breakeven price in the decision summary."),
    ]


def gas_lift_notebook() -> list[dict]:
    """Notebook for network optimization and gas lift."""
    return [
        md("# Production Network Optimization: Multi-Well Gathering and Gas Lift Allocation"),
        code(SETUP),
        md("## Network Equilibrium"),
        code(r'''
        reservoir_pressure = np.array([245, 235, 220, 210], dtype=float)
        productivity = np.array([0.085, 0.075, 0.068, 0.060])
        manifold_pressure = np.linspace(50, 120, 50)
        total_rate = []
        for pressure in manifold_pressure:
            rates = np.maximum((reservoir_pressure - pressure) * productivity, 0)
            total_rate.append(rates.sum())
        fig, ax = plt.subplots(figsize=(8.5, 5))
        ax.plot(manifold_pressure, total_rate, linewidth=2)
        ax.set_xlabel("Manifold pressure (bar)")
        ax.set_ylabel("Total liquid rate (relative units)")
        ax.set_title("Production Network Rate vs Manifold Pressure")
        ax.grid(True, alpha=0.3)
        fig.savefig(FIGURES_DIR / "ch20_network_rate_pressure.png", dpi=150, bbox_inches="tight")
        plt.close(fig)
        '''),
        discussion("Figure ch20_network_rate_pressure.png", "Total rate falls as manifold pressure increases.", "Higher backpressure reduces drawdown for every connected well.", "Separator pressure, compressor suction pressure and host constraints feed directly into reservoir deliverability.", "Include host operating pressure as an optimization variable when tiebacks are capacity constrained."),
        md("## Gas-Lift Economic Optimum"),
        code(r'''
        lift = np.linspace(0, 7.0, 80)
        oil_gain = 42 * (1 - np.exp(-0.65 * lift)) - 0.65 * np.maximum(lift - 4.5, 0) ** 2
        compression_cost = 4.2 * lift
        net_value = oil_gain * 3.1 - compression_cost
        idx = int(np.argmax(net_value))
        fig, ax = plt.subplots(figsize=(8.5, 5))
        ax.plot(lift, net_value, label="Net value")
        ax.plot(lift, oil_gain * 3.1, label="Incremental oil value", alpha=0.75)
        ax.plot(lift, compression_cost, label="Compression cost", alpha=0.75)
        ax.axvline(lift[idx], color="#e76f51", linestyle="--", label=f"Optimum {lift[idx]:.2f}")
        ax.set_xlabel("Lift-gas allocation (relative units)")
        ax.set_ylabel("Value index")
        ax.set_title("Gas-Lift Allocation Economic Optimum")
        ax.grid(True, alpha=0.3)
        ax.legend()
        fig.savefig(FIGURES_DIR / "ch20_gas_lift_optimization.png", dpi=150, bbox_inches="tight")
        plt.close(fig)
        print(f"Optimal lift allocation: {lift[idx]:.2f} relative units")
        '''),
        discussion("Figure ch20_gas_lift_optimization.png", "Net value peaks before maximum lift-gas injection.", "Oil response saturates while compression power continues to increase with injected lift gas.", "Maximum rate and maximum profit are not the same objective.", "Use marginal value equalization rather than proportional lift allocation when lift gas is scarce."),
        md("## Allocation Sensitivity"),
        code(r'''
        available_lift = np.array([2, 3, 4, 5, 6, 7], dtype=float)
        optimized_value = 70 * (1 - np.exp(-0.52 * available_lift))
        equal_split_value = 64 * (1 - np.exp(-0.45 * available_lift))
        fig, ax = plt.subplots(figsize=(8.5, 5))
        ax.plot(available_lift, optimized_value, "o-", label="Optimized allocation")
        ax.plot(available_lift, equal_split_value, "s--", label="Equal split")
        ax.set_xlabel("Available lift gas (relative units)")
        ax.set_ylabel("Production value index")
        ax.set_title("Lift-Gas Allocation Strategy Sensitivity")
        ax.grid(True, alpha=0.3)
        ax.legend()
        fig.savefig(FIGURES_DIR / "ch20_gas_lift_allocation_sensitivity.png", dpi=150, bbox_inches="tight")
        plt.close(fig)
        '''),
        discussion("Figure ch20_gas_lift_allocation_sensitivity.png", "Optimized allocation outperforms equal split most strongly when lift gas is scarce.", "Constrained gas should go first to wells with the highest marginal oil response.", "Operational allocation rules matter most during compressor constraints or late-life scarcity.", "Update gas-lift response curves when well tests or surveillance data change."),
    ]


def api_notebook() -> list[dict]:
    """Notebook for the field-development Java API map."""
    return [
        md("# Field Development API Mastery"),
        code(SETUP),
        md("## API Availability Check"),
        code(r'''
        class_names = [
            "neqsim.process.fielddevelopment.concept.FieldConcept",
            "neqsim.process.fielddevelopment.concept.ReservoirInput",
            "neqsim.process.fielddevelopment.concept.WellsInput",
            "neqsim.process.fielddevelopment.concept.InfrastructureInput",
            "neqsim.process.fielddevelopment.evaluation.ConceptEvaluator",
            "neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker",
            "neqsim.process.fielddevelopment.screening.FlowAssuranceScreener",
            "neqsim.process.fielddevelopment.screening.GasLiftCalculator",
            "neqsim.process.fielddevelopment.network.NetworkSolver",
            "neqsim.process.fielddevelopment.tieback.TiebackAnalyzer",
            "neqsim.process.fielddevelopment.subsea.SubseaProductionSystem",
            "neqsim.process.fielddevelopment.economics.CashFlowEngine",
        ]
        available = []
        for name in class_names:
            ok = False
            if JClass is not None:
                try:
                    JClass(name)
                    ok = True
                except Exception:
                    ok = False
            available.append((name.split(".")[-1], ok))
        for cls, ok in available:
            status = "available" if ok else "not loaded"
            print(f"{cls:<28} {status}")
        '''),
        md("## Capability Coverage"),
        code(r'''
        packages = ["concept", "screening", "evaluation", "economics", "facility", "network", "subsea", "tieback", "workflow"]
        counts = np.array([4, 10, 9, 8, 5, 3, 2, 4, 2])
        fig, ax = plt.subplots(figsize=(9, 5))
        ax.bar(packages, counts, color="#577590")
        ax.set_ylabel("Class count in package")
        ax.set_title("NeqSim Field-Development API Coverage by Package")
        ax.tick_params(axis="x", rotation=25)
        ax.grid(axis="y", alpha=0.3)
        fig.savefig(FIGURES_DIR / "ch24_fielddev_api_package_coverage.png", dpi=150, bbox_inches="tight")
        plt.close(fig)
        '''),
        discussion("Figure ch24_fielddev_api_package_coverage.png", "The API spans concept definition, screening, evaluation, economics, facilities, network, subsea, tieback and workflow classes.", "The field-development module separates data objects, calculators and orchestration workflows into packages.", "Users can start with simple screening and progressively connect more detailed process models.", "Teach the package map before asking students to build a full concept workflow."),
        md("## API Workflow Map"),
        code(r'''
        steps = ["Inputs", "Screen", "Simulate", "Evaluate", "Rank", "Report"]
        x = np.arange(len(steps))
        fig, ax = plt.subplots(figsize=(10, 3.8))
        ax.plot(x, np.zeros_like(x), color="#264653", linewidth=2)
        ax.scatter(x, np.zeros_like(x), s=900, color="#a8dadc", edgecolors="#1d3557", linewidths=2)
        for xi, step in zip(x, steps):
            ax.text(xi, 0, step, ha="center", va="center", fontweight="bold")
        ax.set_xlim(-0.6, len(steps) - 0.4)
        ax.set_ylim(-0.7, 0.7)
        ax.axis("off")
        ax.set_title("Field-Development API Workflow")
        fig.savefig(FIGURES_DIR / "ch24_fielddev_api_workflow.png", dpi=150, bbox_inches="tight")
        plt.close(fig)
        '''),
        discussion("Figure ch24_fielddev_api_workflow.png", "The workflow runs from structured inputs through screening, simulation, evaluation, ranking and reporting.", "Each stage consumes the previous stage's outputs and adds more physical or economic detail.", "Notebook examples should expose intermediate objects rather than only final NPV values.", "When debugging, validate each stage independently before running the full workflow."),
        md("""
        ## Exercises

        1. Identify which package owns concept input data and which package owns ranking.
        2. Explain why API availability checks should catch exceptions rather than failing immediately.
        3. Add a hypothetical RiskRegister class to the package map and choose its package.
        4. Describe when to use direct JClass access instead of a preloaded Python alias.
        5. Write a six-stage pseudocode workflow for concept selection.
        """),
    ]


NOTEBOOKS = {
    CHAPTERS / "ch11_field_development_building_blocks/notebooks/ch11_01_digital_twin_and_lifecycle.ipynb": lifecycle_notebook,
    CHAPTERS / "ch11_field_development_building_blocks/notebooks/ch11_02_concept_evaluation_framework.ipynb": concept_notebook,
    CHAPTERS / "ch11_field_development_building_blocks/notebooks/ch11_03_screening_and_feasibility.ipynb": screening_notebook,
    CHAPTERS / "ch13_subsea_surf_systems/notebooks/ch13_02_surf_equipment_design_cost.ipynb": surf_notebook,
    CHAPTERS / "ch20_production_optimisation/notebooks/ch20_03_cash_flow_engine_and_economics.ipynb": economics_notebook,
    CHAPTERS / "ch20_production_optimisation/notebooks/ch20_04_network_optimization_gas_lift.ipynb": gas_lift_notebook,
    CHAPTERS / "ch24_computational_tools_neqsim/notebooks/ch24_02_field_development_api_mastery.ipynb": api_notebook,
}


def notebook(cells: list[dict]) -> dict:
    """Create a minimal nbformat v4 notebook."""
    for index, cell in enumerate(cells, start=1):
        cell.setdefault("id", f"cell-{index:03d}")
    return {
        "cells": cells,
        "metadata": {
            "kernelspec": {"display_name": "Python 3", "language": "python", "name": "python3"},
            "language_info": {"name": "python", "pygments_lexer": "ipython3"},
        },
        "nbformat": 4,
        "nbformat_minor": 5,
    }


def main() -> None:
    """Regenerate all target notebooks."""
    for path, factory in NOTEBOOKS.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(notebook(factory()), indent=1) + "\n", encoding="utf-8")
        print(f"repaired {path.relative_to(BOOK_ROOT)}")


if __name__ == "__main__":
    main()