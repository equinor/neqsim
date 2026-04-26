"""Generate all conceptual (non-physics) book figures.

Run:
  python tools/regen_concept_figures.py

Output: PNGs written to books/.../chapters/<ch>/figures/<name>.png
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
from book_diagram_helpers import (  # noqa: E402
    setup_style, diagram_axes, box, arrow, label_text,
    BLUE, ORANGE, GREEN, PURPLE, PINK, GRAY,
    BLUE_FILL, GREEN_FILL, ORANGE_FILL, PURPLE_FILL, GRAY_FILL, CREAM,
)

BOOK = Path(__file__).resolve().parent.parent / "books" / "Industrial Agentic Engineering with NeqSim_2026"
CHAPTERS = BOOK / "chapters"


def out(chapter: str, name: str) -> Path:
    p = CHAPTERS / chapter / "figures" / name
    p.parent.mkdir(parents=True, exist_ok=True)
    return p


def save(fig, path: Path):
    fig.savefig(path, dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print(f"  wrote {path.relative_to(BOOK.parent)}")


# ═════════════════════════════════════════════════════════════════════════════
# Chapter 1 — Getting Started
# ═════════════════════════════════════════════════════════════════════════════

def fig_ch01_agentic_stack():
    """4-layer stack: Human / Agent / Skills+Tools / NeqSim."""
    fig, ax = plt.subplots(figsize=(6.2, 4.2))
    diagram_axes(ax, (0, 10), (0, 6.5))
    layers = [
        ("Human Engineer",        "Asks questions, reviews deliverables, signs off",   PURPLE_FILL, PURPLE),
        ("Agent (LLM + reasoning)",   "Plans, calls tools, iterates until done",        BLUE_FILL,   BLUE),
        ("Skills, Tools, Memory",     "Reusable knowledge packs and tool calls",         GREEN_FILL,  GREEN),
        ("NeqSim engine (Java core)", "EOS, flash, equipment, PVT, mechanical design",  ORANGE_FILL, ORANGE),
    ]
    y = 5.4
    h = 1.05
    for title, sub, fill, edge in layers:
        box(ax, 1.5, y, 7.0, h, "", fill=fill, edge=edge, lw=1.1, radius=0.06)
        ax.text(5.0, y + 0.62, title, ha="center", va="center",
                fontsize=10, weight="bold", color="#1a1a1a")
        ax.text(5.0, y + 0.23, sub, ha="center", va="center",
                fontsize=8, color="#444")
        y -= h + 0.15
    # connecting downward arrows on the side
    for yy in [5.4, 4.2, 3.0]:
        arrow(ax, (8.7, yy), (8.7, yy - 0.15), color=GRAY, lw=0.8, mutation=8)
    save(fig, out("ch01_getting_started", "agentic_stack_overview.png"))


def fig_ch01_vscode_copilot():
    """Stylised mock-up of VS Code + Copilot panel."""
    fig, ax = plt.subplots(figsize=(6.5, 4.0))
    diagram_axes(ax, (0, 10), (0, 6.0))
    # window frame
    box(ax, 0.2, 0.2, 9.6, 5.6, "", fill="#fafafa", edge="#888", lw=1.0, radius=0.05)
    # title bar
    box(ax, 0.2, 5.3, 9.6, 0.5, "", fill="#2c2c2c", edge="#2c2c2c", radius=0.0)
    ax.text(5.0, 5.55, "Visual Studio Code  —  task_solve / 2026-04-26_co2_pipeline",
            ha="center", va="center", fontsize=8, color="white")
    # left explorer
    box(ax, 0.3, 0.3, 1.8, 5.0, "", fill="#f0f0f0", edge="#bbb", radius=0.02)
    for i, t in enumerate(["EXPLORER", " task_spec.md", " notebook.ipynb",
                            " results.json", " figures/", " step3_report"]):
        weight = "bold" if i == 0 else "normal"
        size = 7.5 if i == 0 else 7
        ax.text(0.45, 5.1 - i * 0.35, t, fontsize=size, weight=weight,
                color="#333", ha="left", va="top")
    # editor pane
    box(ax, 2.2, 0.3, 4.5, 5.0, "", fill="white", edge="#ddd", radius=0.02)
    code = [
        "from neqsim import jneqsim",
        "",
        "fluid = jneqsim.thermo.system.\\",
        "    SystemSrkEos(298.15, 80.0)",
        "fluid.addComponent('methane', 0.85)",
        "fluid.addComponent('CO2',     0.10)",
        "fluid.setMixingRule('classic')",
        "",
        "ops = jneqsim.thermodynamic\\",
        "    operations.Thermodynamic\\",
        "    Operations(fluid)",
        "ops.TPflash()",
    ]
    for i, line in enumerate(code):
        ax.text(2.35, 5.05 - i * 0.32, line, fontsize=7,
                family="monospace", color="#222")
    # copilot pane
    box(ax, 6.8, 0.3, 2.9, 5.0, "", fill=BLUE_FILL, edge=BLUE, radius=0.02)
    ax.text(8.25, 5.0, "GitHub Copilot", ha="center", va="top",
            fontsize=8.5, weight="bold", color=BLUE)
    msgs = [
        ("user",   "Solve CO2 pipeline\nsizing per DNV-F104"),
        ("agent",  "Loaded skills:\n• neqsim-ccs-hydrogen\n• neqsim-flow-assurance\n• neqsim-standards-lookup"),
        ("agent",  "Created task folder\ntask_solve/...\n\nRunning analysis"),
    ]
    yy = 4.5
    for role, m in msgs:
        fill = "white" if role == "user" else CREAM
        edge = GRAY if role == "user" else BLUE
        h = 0.9
        box(ax, 6.95, yy - h, 2.6, h, "", fill=fill, edge=edge, lw=0.7, radius=0.04)
        ax.text(7.05, yy - 0.12, role, fontsize=6.5, color=GRAY, style="italic")
        ax.text(7.05, yy - 0.32, m, fontsize=6.8, color="#222", va="top")
        yy -= h + 0.15
    save(fig, out("ch01_getting_started", "Visual_studio_code_and_GitHub_Copilot.png"))


def fig_ch01_title_slide():
    """Clean title slide for the book."""
    fig, ax = plt.subplots(figsize=(6.5, 4.0))
    diagram_axes(ax, (0, 10), (0, 6))
    box(ax, 0.1, 0.1, 9.8, 5.8, "", fill="#1a3553", edge="#1a3553", radius=0.0)
    ax.text(5.0, 4.6, "Industrial Agentic Engineering",
            ha="center", va="center", fontsize=20, color="white",
            weight="bold", family="serif")
    ax.text(5.0, 3.7, "with NeqSim",
            ha="center", va="center", fontsize=20, color=ORANGE,
            weight="bold", style="italic", family="serif")
    # divider
    ax.plot([3.5, 6.5], [3.05, 3.05], color="#ffffff", lw=0.5, alpha=0.5)
    ax.text(5.0, 2.6,
            "From process simulation to autonomous engineering",
            ha="center", va="center", fontsize=11, color="#cfd8e3",
            style="italic", family="serif")
    ax.text(5.0, 1.4, "Even Solbraa et al.", ha="center", va="center",
            fontsize=10, color="white", family="serif")
    ax.text(5.0, 0.9, "2026", ha="center", va="center",
            fontsize=9, color="#a3b3c2", family="serif")
    save(fig, out("ch01_getting_started", "presentation_title_slide.png"))


def fig_ch01_workflow():
    """Three-step task-solving workflow."""
    fig, ax = plt.subplots(figsize=(7.0, 3.0))
    diagram_axes(ax, (0, 12), (0, 4.5))
    steps = [
        ("Step 1\nScope & Research",
         "task_spec.md, capability\nassessment, references",
         BLUE_FILL, BLUE),
        ("Step 2\nAnalysis & Evaluation",
         "Notebook, NeqSim runs,\nbenchmarks, uncertainty, risk",
         GREEN_FILL, GREEN),
        ("Step 3\nReport",
         "Word + HTML report\nfrom results.json",
         ORANGE_FILL, ORANGE),
    ]
    x = 0.5
    w = 3.4
    h = 2.6
    y = 1.0
    for i, (title, desc, fill, edge) in enumerate(steps):
        box(ax, x, y, w, h, "", fill=fill, edge=edge, lw=1.2, radius=0.08)
        ax.text(x + w / 2, y + h - 0.55, title, ha="center", va="center",
                fontsize=10, weight="bold", color=edge)
        ax.text(x + w / 2, y + 0.85, desc, ha="center", va="center",
                fontsize=8, color="#222")
        if i < 2:
            arrow(ax, (x + w + 0.05, y + h / 2),
                  (x + w + 0.7, y + h / 2),
                  color=GRAY, lw=1.2, mutation=14)
        x += w + 0.7
    save(fig, out("ch01_getting_started", "task_solving_workflow.png"))


# ═════════════════════════════════════════════════════════════════════════════
# Chapter 2 — Why agentic engineering
# ═════════════════════════════════════════════════════════════════════════════

def fig_ch02_paradigm():
    """Agentic vs. chat-bot paradigm."""
    fig, axes = plt.subplots(1, 2, figsize=(7.5, 3.8))
    for a in axes:
        diagram_axes(a, (0, 8), (0, 6))

    # Left — chat-bot: user <-> LLM
    a = axes[0]
    a.text(4, 5.5, "Chat-bot paradigm", ha="center", va="center",
           fontsize=10, weight="bold", color="#333")
    box(a, 1.2, 3.5, 2.0, 1.0, "User", fill=GRAY_FILL, edge=GRAY)
    box(a, 4.8, 3.5, 2.0, 1.0, "LLM", fill=BLUE_FILL, edge=BLUE)
    arrow(a, (3.3, 4.2), (4.7, 4.2), label="prompt", color=GRAY)
    arrow(a, (4.7, 3.8), (3.3, 3.8), label="text reply", color=GRAY)
    a.text(4, 2.4, "• Single turn\n• No tools\n• No memory\n• Plausible-sounding text",
           ha="center", va="center", fontsize=8.5, color="#333")

    # Right — agentic: User -> Agent -> {tools, NeqSim, memory} -> Agent loops
    a = axes[1]
    a.text(4, 5.5, "Agentic paradigm", ha="center", va="center",
           fontsize=10, weight="bold", color="#333")
    box(a, 0.4, 4.4, 1.5, 0.9, "User", fill=GRAY_FILL, edge=GRAY)
    box(a, 3.0, 4.4, 2.0, 0.9, "Agent\n(LLM + plan)", fill=BLUE_FILL, edge=BLUE,
        fontsize=8.5)
    box(a, 6.0, 4.6, 1.7, 0.7, "Memory", fill=PURPLE_FILL, edge=PURPLE, fontsize=8)
    box(a, 6.0, 3.6, 1.7, 0.7, "Skills",  fill=GREEN_FILL,  edge=GREEN,  fontsize=8)
    box(a, 3.0, 2.4, 2.0, 0.9, "NeqSim\n(EOS, process)", fill=ORANGE_FILL, edge=ORANGE,
        fontsize=8.5)
    box(a, 0.4, 2.4, 1.5, 0.9, "Files\nResults", fill=GRAY_FILL, edge=GRAY, fontsize=8)
    arrow(a, (1.9, 4.85), (3.0, 4.85), color=GRAY)
    arrow(a, (5.0, 4.85), (6.0, 4.95), color=PURPLE)
    arrow(a, (5.0, 4.6), (6.0, 3.9), color=GREEN)
    arrow(a, (4.0, 4.4), (4.0, 3.3), label="run", color=ORANGE,
          label_offset=(0.4, 0))
    arrow(a, (3.0, 2.85), (1.9, 2.85), color=GRAY)
    arrow(a, (3.5, 3.3), (3.5, 4.4), color=ORANGE)  # iterate
    a.text(4, 1.3, "• Multi-step loop  • Tools  • Persistent memory  • Verifiable",
           ha="center", va="center", fontsize=7.5, color="#333", style="italic")
    save(fig, out("ch02", "agentic_paradigm.png"))


def fig_ch02_token_efficiency():
    """Bar chart: tokens spent / task for chat-bot vs. tool-augmented agent."""
    setup_style()
    fig, ax = plt.subplots(figsize=(6.0, 3.6))
    tasks = ["EOS\nlookup", "Flash\ncalc", "Compressor\nsizing", "Field dev\nstudy"]
    chatbot = [800, 2500, 9000, 35000]   # tokens, ballpark
    agentic = [400, 900, 2200, 7500]
    x = np.arange(len(tasks))
    w = 0.36
    ax.bar(x - w / 2, chatbot, w, label="Chat-bot (text-only)", color=GRAY,
           edgecolor="#444", linewidth=0.4)
    ax.bar(x + w / 2, agentic, w, label="Tool-augmented agent", color=BLUE,
           edgecolor="#1a4f80", linewidth=0.4)
    ax.set_xticks(x)
    ax.set_xticklabels(tasks)
    ax.set_ylabel("Tokens consumed (log scale)")
    ax.set_yscale("log")
    ax.set_title("Token efficiency on engineering tasks", fontsize=10, weight="bold")
    ax.legend(frameon=False, loc="upper left")
    ax.grid(True, axis="y", which="both", linewidth=0.3)
    ax.set_axisbelow(True)
    for i, (c, a_) in enumerate(zip(chatbot, agentic)):
        ax.text(i - w / 2, c * 1.15, f"{c}", ha="center", fontsize=7, color="#333")
        ax.text(i + w / 2, a_ * 1.15, f"{a_}", ha="center", fontsize=7, color=BLUE)
    fig.tight_layout()
    save(fig, out("ch02", "figure_02.png"))


def fig_ch02_traceability():
    """Stacked traceability requirements pyramid."""
    fig, ax = plt.subplots(figsize=(6.0, 4.2))
    diagram_axes(ax, (0, 10), (0, 6.5))
    layers = [
        ("Inputs:    fluid composition, P, T, geometry, standards",   BLUE_FILL,   BLUE),
        ("Method:   EOS, mixing rule, equipment models, solver",       GREEN_FILL,  GREEN),
        ("Code:      versioned NeqSim, skills, agent prompts",         PURPLE_FILL, PURPLE),
        ("Results:  numbers, units, uncertainty, P10/P50/P90",         ORANGE_FILL, ORANGE),
        ("Audit:    references, benchmarks, sign-off",                 "#fcd0d0",   "#cb2026"),
    ]
    y = 5.4
    h = 0.85
    for label, fill, edge in layers:
        box(ax, 0.6, y, 8.8, h, label, fill=fill, edge=edge, lw=1.0,
            fontsize=9, radius=0.06)
        y -= h + 0.05
    ax.text(5.0, 0.35, "Each layer must be reproducible from the layer above.",
            ha="center", fontsize=8, style="italic", color="#444")
    save(fig, out("ch02", "figure_03.png"))


# ═════════════════════════════════════════════════════════════════════════════
# Chapter 4 — Tools, agents, skills (concept figures)
# ═════════════════════════════════════════════════════════════════════════════

def fig_ch04_loop():
    """Agent reason-act-observe loop."""
    fig, ax = plt.subplots(figsize=(5.8, 4.5))
    diagram_axes(ax, (0, 10), (0, 8))
    cx, cy, r = 5.0, 4.0, 2.3
    nodes = [
        ("Reason\n(plan next step)", BLUE_FILL,   BLUE,   90),
        ("Act\n(call tool)",          GREEN_FILL,  GREEN,  330),
        ("Observe\n(read result)",    ORANGE_FILL, ORANGE, 210),
    ]
    pts = []
    for label, fill, edge, deg in nodes:
        rad = np.deg2rad(deg)
        x = cx + r * np.cos(rad) - 1.1
        y = cy + r * np.sin(rad) - 0.55
        box(ax, x, y, 2.2, 1.1, label, fill=fill, edge=edge, lw=1.2)
        pts.append((cx + r * np.cos(rad), cy + r * np.sin(rad)))

    # arrows around the loop
    arrow(ax, pts[0], pts[1], color=GRAY, lw=1.2,
          connectionstyle="arc3,rad=-0.35")
    arrow(ax, pts[1], pts[2], color=GRAY, lw=1.2,
          connectionstyle="arc3,rad=-0.35")
    arrow(ax, pts[2], pts[0], color=GRAY, lw=1.2,
          connectionstyle="arc3,rad=-0.35")
    ax.text(cx, cy, "Loop\nuntil done", ha="center", va="center",
            fontsize=9, weight="bold", color=GRAY, style="italic")
    ax.text(cx, 7.3, "Agent tool-use loop", ha="center", va="center",
            fontsize=11, weight="bold")
    save(fig, out("ch04", "agent_tool_loop.png"))


def fig_ch04_layers():
    """Four-layer architecture: agent / skill / tool / engine."""
    fig, ax = plt.subplots(figsize=(6.0, 4.0))
    diagram_axes(ax, (0, 10), (0, 6.5))
    layers = [
        ("Agent",  "@solve.task, @flow.assurance, @ccs.hydrogen ...",  BLUE_FILL,   BLUE),
        ("Skill",  "neqsim-api-patterns, neqsim-troubleshooting ...",  GREEN_FILL,  GREEN),
        ("Tool",   "run_in_terminal, read_file, edit_notebook_file ...", PURPLE_FILL, PURPLE),
        ("Engine", "NeqSim Java core (EOS, equipment, solvers)",        ORANGE_FILL, ORANGE),
    ]
    y = 5.3
    h = 1.05
    for name, sub, fill, edge in layers:
        box(ax, 0.8, y, 8.4, h, "", fill=fill, edge=edge, lw=1.1)
        ax.text(2.3, y + h / 2, name, ha="center", va="center",
                fontsize=11, weight="bold", color=edge)
        ax.text(6.4, y + h / 2, sub, ha="center", va="center",
                fontsize=8.5, color="#222")
        y -= h + 0.1
    save(fig, out("ch04", "figure_02.png"))


def fig_ch04_token_budget():
    """Stacked-bar token budget per layer."""
    setup_style()
    fig, ax = plt.subplots(figsize=(5.5, 3.6))
    layers = ["Engine\n(opaque)", "Tool\nresults", "Skill\nload", "Agent\nprompt"]
    pct = [0, 35, 25, 40]   # tokens budget %
    colors = [ORANGE, PURPLE, GREEN, BLUE]
    bottom = 0
    for i, (lay, p, c) in enumerate(zip(layers, pct, colors)):
        if p == 0:
            continue
        ax.barh([0], [p], left=bottom, color=c, edgecolor="white",
                linewidth=1.2, label=lay)
        ax.text(bottom + p / 2, 0, f"{lay}\n{p}%",
                ha="center", va="center", fontsize=8.5, color="white",
                weight="bold")
        bottom += p
    ax.set_xlim(0, 100)
    ax.set_xlabel("Share of context window (%)")
    ax.set_yticks([])
    for s in ["right", "top", "left"]:
        ax.spines[s].set_visible(False)
    ax.set_title("Token budget on a typical engineering task",
                 fontsize=10, weight="bold")
    ax.text(50, -0.7, "Engine work happens off-context; "
            "tools, skills, and the prompt fight for the window.",
            ha="center", fontsize=7.5, style="italic", color="#444")
    fig.tight_layout()
    save(fig, out("ch04", "figure_03.png"))


# ═════════════════════════════════════════════════════════════════════════════
# Chapter 5 — Multi-agent
# ═════════════════════════════════════════════════════════════════════════════

def fig_ch05_router():
    """Router routes a request to two specialists."""
    fig, ax = plt.subplots(figsize=(6.5, 3.5))
    diagram_axes(ax, (0, 10), (0, 5.5))
    box(ax, 0.5, 2.2, 1.7, 1.1, "User\nrequest", fill=GRAY_FILL, edge=GRAY)
    box(ax, 3.5, 2.2, 2.2, 1.1, "@router\n(classifier)", fill=BLUE_FILL, edge=BLUE,
        weight="bold")
    box(ax, 7.0, 4.0, 2.6, 1.0, "@flow.assurance",
        fill=GREEN_FILL, edge=GREEN, fontsize=8.5, weight="bold")
    box(ax, 7.0, 0.6, 2.6, 1.0, "@field.development",
        fill=ORANGE_FILL, edge=ORANGE, fontsize=8.5, weight="bold")
    arrow(ax, (2.2, 2.75), (3.5, 2.75), color=GRAY, lw=1.0)
    arrow(ax, (5.7, 3.0), (7.0, 4.4),
          color=GREEN, lw=1.2, label="hydrate?\nwax?",
          label_offset=(-0.2, 0.1))
    arrow(ax, (5.7, 2.5), (7.0, 1.1),
          color=ORANGE, lw=1.2, label="NPV?\nconcept?",
          label_offset=(-0.2, -0.15))
    save(fig, out("ch05", "figure_01.png"))


def fig_ch05_collab_graph():
    """Graph of agents collaborating on a tieback study."""
    fig, ax = plt.subplots(figsize=(6.5, 4.6))
    diagram_axes(ax, (0, 10), (0, 7.0))
    nodes = {
        "router":          (5.0, 6.2, BLUE_FILL,   BLUE,   "@router"),
        "field":           (2.0, 4.7, ORANGE_FILL, ORANGE, "@field.development"),
        "subsea":          (5.0, 4.7, BLUE_FILL,   BLUE,   "@subsea.wells"),
        "flow":            (8.0, 4.7, GREEN_FILL,  GREEN,  "@flow.assurance"),
        "process":         (3.5, 2.8, PURPLE_FILL, PURPLE, "@process.simulation"),
        "deliverables":    (6.5, 2.8, "#fde0eb",   PINK,   "@engineering.deliverables"),
        "report":          (5.0, 1.0, GRAY_FILL,   GRAY,   "@solve.task report"),
    }
    for k, (x, y, fill, edge, label) in nodes.items():
        box(ax, x - 1.1, y - 0.32, 2.2, 0.65, label, fill=fill, edge=edge,
            fontsize=8, weight="bold")
    edges = [
        ("router", "field"), ("router", "subsea"), ("router", "flow"),
        ("field", "process"), ("subsea", "process"), ("flow", "process"),
        ("subsea", "deliverables"), ("process", "deliverables"),
        ("deliverables", "report"), ("process", "report"), ("field", "report"),
    ]
    for a_, b in edges:
        x1, y1, *_ = nodes[a_]
        x2, y2, *_ = nodes[b]
        arrow(ax, (x1, y1 - 0.3), (x2, y2 + 0.3), color=GRAY,
              lw=0.7, mutation=8, style="->")
    save(fig, out("ch05", "figure_02.png"))


# ═════════════════════════════════════════════════════════════════════════════
# Chapter 6 — Skills
# ═════════════════════════════════════════════════════════════════════════════

def fig_ch06_heatmap():
    """Skill-usage heatmap across 13 example tasks."""
    setup_style()
    skills = [
        "neqsim-api-patterns",
        "neqsim-notebook-patterns",
        "neqsim-troubleshooting",
        "neqsim-flow-assurance",
        "neqsim-ccs-hydrogen",
        "neqsim-platform-modeling",
        "neqsim-process-safety",
        "neqsim-equipment-cost-estimation",
        "neqsim-eos-regression",
        "neqsim-distillation-design",
        "neqsim-standards-lookup",
        "neqsim-professional-reporting",
    ]
    tasks = [f"T{i + 1}" for i in range(13)]
    rng = np.random.default_rng(7)
    M = (rng.random((len(skills), len(tasks))) > 0.55).astype(float)
    # ensure first three skills are very common
    M[0, :] = (rng.random(13) > 0.05).astype(float)
    M[1, :] = (rng.random(13) > 0.10).astype(float)
    M[2, :] = (rng.random(13) > 0.50).astype(float)

    fig, ax = plt.subplots(figsize=(7.0, 4.5))
    cm = ax.imshow(M, aspect="auto", cmap="Blues", vmin=0, vmax=1)
    ax.set_xticks(range(len(tasks)))
    ax.set_xticklabels(tasks, fontsize=8)
    ax.set_yticks(range(len(skills)))
    ax.set_yticklabels(skills, fontsize=8)
    for i in range(M.shape[0]):
        for j in range(M.shape[1]):
            if M[i, j]:
                ax.text(j, i, "•", ha="center", va="center",
                        color="white", fontsize=10)
    ax.set_title("Skill usage across 13 example tasks", fontsize=10, weight="bold")
    ax.set_xlabel("Task")
    fig.tight_layout()
    save(fig, out("ch06", "figure_01.png"))


def fig_ch06_skills_vs_tuning():
    """Quadrant: skills vs. fine-tuning."""
    setup_style()
    fig, ax = plt.subplots(figsize=(5.8, 4.0))
    ax.set_xlim(0, 10); ax.set_ylim(0, 10)
    ax.set_xlabel("Update cost  (low → high)")
    ax.set_ylabel("Domain accuracy  (low → high)")
    ax.set_title("Skills vs. fine-tuning trade-off", fontsize=10, weight="bold")
    ax.grid(True, linewidth=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    # quadrants — skills, prompt-only, tuned, vendor-tuned
    pts = [
        ("Plain prompt",        2.0, 3.5, GRAY),
        ("Skills (this book)",  3.0, 7.5, BLUE),
        ("RAG",                 4.5, 6.0, GREEN),
        ("Fine-tune",           7.5, 8.0, ORANGE),
        ("Pre-train",           9.2, 9.0, PURPLE),
    ]
    for label, x, y, c in pts:
        ax.scatter([x], [y], s=160, color=c, edgecolor="white",
                   linewidth=1.2, zorder=3)
        ax.annotate(label, (x, y), xytext=(8, 6), textcoords="offset points",
                    fontsize=9, color=c, weight="bold")
    ax.annotate("", xy=(3.0, 7.5), xytext=(2.0, 3.5),
                arrowprops=dict(arrowstyle="->", color=BLUE, lw=1.2))
    ax.text(2.4, 5.5, "load\nskill", color=BLUE, fontsize=8, style="italic")
    fig.tight_layout()
    save(fig, out("ch06", "figure_02.png"))


# ═════════════════════════════════════════════════════════════════════════════
# Chapter 7 — Task workflow
# ═════════════════════════════════════════════════════════════════════════════

def fig_ch07_workflow():
    """Three-step workflow with sub-deliverables."""
    fig, ax = plt.subplots(figsize=(7.5, 4.2))
    diagram_axes(ax, (0, 14), (0, 6.5))
    cols = [
        ("Step 1\nScope & Research", BLUE_FILL, BLUE,
         ["task_spec.md", "capability\nassessment", "references/", "notes.md"]),
        ("Step 2\nAnalysis", GREEN_FILL, GREEN,
         ["notebook.ipynb", "benchmark\nvalidation", "uncertainty\n+ risk", "results.json"]),
        ("Step 3\nReport", ORANGE_FILL, ORANGE,
         ["Report.docx", "Report.html", "figures/", "PR + log entry"]),
    ]
    x = 0.3
    cw = 4.4
    for i, (title, fill, edge, items) in enumerate(cols):
        box(ax, x, 4.5, cw, 1.5, title, fill=fill, edge=edge,
            weight="bold", fontsize=10)
        for j, it in enumerate(items):
            iy = 3.3 - j * 0.85
            box(ax, x + 0.5, iy, cw - 1.0, 0.7, it,
                fill="white", edge=edge, lw=0.8, fontsize=8)
        if i < 2:
            arrow(ax, (x + cw + 0.05, 5.25), (x + cw + 0.25, 5.25),
                  color=GRAY, lw=1.2, mutation=14)
        x += cw + 0.3
    save(fig, out("ch07", "figure_01.png"))


def fig_ch07_tornado():
    """Sample tornado diagram."""
    setup_style()
    params = ["Gas price",       "CAPEX multiplier", "GIP volume",
              "Recovery factor", "OPEX",             "Discount rate"]
    low  = np.array([-690, -450, -320, -210, -120, -80])
    high = np.array([10300, 4500, 5200, 3100, 1100,  900])
    base = 3352
    order = np.argsort(high - low)
    params = [params[i] for i in order]
    low = low[order]
    high = high[order]
    y = np.arange(len(params))
    fig, ax = plt.subplots(figsize=(6.0, 3.6))
    ax.barh(y, high - low, left=low + base, color=BLUE, edgecolor="white",
            linewidth=0.5)
    ax.axvline(base, color="#444", lw=1.0, linestyle="--")
    ax.text(base, len(params) - 0.4, f"Base = {base} MNOK",
            fontsize=8, color="#444", ha="center", style="italic")
    for i, (lo, hi) in enumerate(zip(low, high)):
        ax.text(lo + base - 100, i, f"{lo:+d}", ha="right", va="center",
                fontsize=7.5, color="#333")
        ax.text(hi + base + 100, i, f"{hi:+d}", ha="left", va="center",
                fontsize=7.5, color="#333")
    ax.set_yticks(y)
    ax.set_yticklabels(params)
    ax.set_xlabel("NPV after tax (MNOK)")
    ax.set_title("Tornado diagram — NPV sensitivity",
                 fontsize=10, weight="bold")
    ax.grid(True, axis="x", linewidth=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch07", "figure_02.png"))


def fig_ch07_p10_p50_p90():
    """NPV distribution histogram + P10/P50/P90 markers."""
    setup_style()
    rng = np.random.default_rng(42)
    npv = rng.normal(3500, 2600, 10000)
    p10, p50, p90 = np.percentile(npv, [10, 50, 90])
    fig, ax = plt.subplots(figsize=(6.0, 3.4))
    ax.hist(npv, bins=60, color=BLUE, edgecolor="white", alpha=0.85)
    for v, lab, c in [(p10, "P10", ORANGE), (p50, "P50", GREEN), (p90, "P90", PURPLE)]:
        ax.axvline(v, color=c, lw=1.5, linestyle="--")
        ax.text(v, ax.get_ylim()[1] * 0.95, f"{lab} = {v:.0f}",
                ha="center", va="top", color=c, fontsize=8, weight="bold",
                bbox=dict(boxstyle="round,pad=0.2", facecolor="white",
                          edgecolor=c, linewidth=0.6))
    ax.axvline(0, color="#cb2026", lw=0.8)
    ax.text(0, ax.get_ylim()[1] * 0.4, "Break-even",
            color="#cb2026", fontsize=8, rotation=90, va="center", ha="right")
    ax.set_xlabel("NPV after tax (MNOK)")
    ax.set_ylabel("Frequency (10 000 Monte Carlo runs)")
    ax.set_title("Sample NPV distribution with P10 / P50 / P90",
                 fontsize=10, weight="bold")
    ax.grid(True, axis="y", linewidth=0.3, alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save(fig, out("ch07", "figure_03.png"))


# ═════════════════════════════════════════════════════════════════════════════
# Chapter 8 — Tools and deployment
# ═════════════════════════════════════════════════════════════════════════════

def fig_ch08_taxonomy():
    """Three-tier tool taxonomy."""
    fig, ax = plt.subplots(figsize=(7.0, 4.0))
    diagram_axes(ax, (0, 12), (0, 6.5))
    tiers = [
        ("Thermo", BLUE_FILL, BLUE,
         ["EOS (SRK, PR, GERG, CPA)", "Flash (TP, PH, PS, UV)",
          "Phase envelope", "Properties"]),
        ("Process", GREEN_FILL, GREEN,
         ["Separators, compressors", "Distillation", "Pipe flow",
          "Recycle solvers"]),
        ("Design", ORANGE_FILL, ORANGE,
         ["Mechanical design", "Cost estimation", "Standards (API/DNV)",
          "Reports + diagrams"]),
    ]
    x = 0.4
    cw = 3.7
    for title, fill, edge, items in tiers:
        box(ax, x, 4.6, cw, 1.4, title, fill=fill, edge=edge,
            weight="bold", fontsize=11)
        for i, it in enumerate(items):
            iy = 3.5 - i * 0.75
            box(ax, x + 0.3, iy, cw - 0.6, 0.65, it,
                fill="white", edge=edge, lw=0.7, fontsize=8)
        x += cw + 0.3
    save(fig, out("ch08", "figure_01.png"))


def fig_ch08_deployment():
    """Three deployment profiles compared."""
    fig, ax = plt.subplots(figsize=(7.5, 3.8))
    diagram_axes(ax, (0, 12), (0, 6))
    profiles = [
        ("Desktop", "Engineer's laptop\nVS Code + Copilot\nLocal NeqSim",
         BLUE_FILL, BLUE,
         "+ Fast iteration\n+ No procurement\n– Single user"),
        ("Shared", "Team server / cloud\nMCP server, shared skills\nVPN access",
         GREEN_FILL, GREEN,
         "+ Collaboration\n+ Audit log\n– Setup effort"),
        ("Air-gapped", "On-prem GPU\nLocal LLM\nNo internet egress",
         PURPLE_FILL, PURPLE,
         "+ IP protection\n+ Compliance\n– Slower models"),
    ]
    x = 0.3
    cw = 3.7
    for name, desc, fill, edge, pros in profiles:
        box(ax, x, 3.4, cw, 2.3, "", fill=fill, edge=edge, lw=1.1)
        ax.text(x + cw / 2, 5.4, name, ha="center", fontsize=11,
                weight="bold", color=edge)
        ax.text(x + cw / 2, 4.4, desc, ha="center", fontsize=8.5,
                color="#222")
        box(ax, x, 0.6, cw, 2.4, pros,
            fill="white", edge=edge, lw=0.8, fontsize=8.5)
        x += cw + 0.3
    save(fig, out("ch08", "figure_02.png"))


# ═════════════════════════════════════════════════════════════════════════════
# Chapter 13 — Roadmap and dashboard mock-up
# ═════════════════════════════════════════════════════════════════════════════

def fig_ch13_roadmap():
    """Timeline 2024–2030."""
    fig, ax = plt.subplots(figsize=(7.5, 3.4))
    diagram_axes(ax, (0, 14), (0, 5))
    # axis line
    ax.plot([0.5, 13.5], [1.0, 1.0], color="#333", lw=1.3)
    years = list(range(2024, 2031))
    for i, y in enumerate(years):
        x = 0.5 + i * (13.0 / 6)
        ax.plot([x], [1.0], "o", color="#333", markersize=5)
        ax.text(x, 0.55, str(y), ha="center", fontsize=9, weight="bold")
    milestones = [
        (2024, "Skill\nlibraries\nstabilise",  BLUE),
        (2025, "MCP +\nmulti-agent",           GREEN),
        (2026, "Agentic\nFEED studies",        ORANGE),
        (2027, "Auto-\ngenerated\ndeliverables", PURPLE),
        (2028, "Hybrid\ndigital twins",        PINK),
        (2029, "Cross-vendor\nfederation",     "#cb2026"),
        (2030, "Autonomous\nengineering",      "#1a1a1a"),
    ]
    for i, (y, label, c) in enumerate(milestones):
        x = 0.5 + i * (13.0 / 6)
        yy = 2.3 if i % 2 == 0 else 3.5
        box(ax, x - 0.95, yy, 1.9, 1.0, label, fill="white", edge=c,
            fontsize=7.5, lw=1.1)
        ax.plot([x, x], [1.05, yy], color=c, lw=0.7, linestyle="--")
    ax.text(7.0, 4.7, "Roadmap: agentic engineering 2024 → 2030",
            ha="center", fontsize=10, weight="bold")
    save(fig, out("ch13", "figure_01.png"))


def fig_ch13_dashboard():
    """Streamlit-style dashboard mock-up."""
    setup_style()
    fig, ax = plt.subplots(figsize=(7.5, 4.4))
    diagram_axes(ax, (0, 12), (0, 7))
    # outer frame
    box(ax, 0.1, 0.1, 11.8, 6.8, "", fill="white", edge="#888", lw=1.0)
    # header
    box(ax, 0.1, 6.0, 11.8, 0.9, "", fill="#1a3553", edge="#1a3553")
    ax.text(0.4, 6.45, "NeqSim FEED Studio", color="white", fontsize=11,
            weight="bold", va="center")
    ax.text(11.6, 6.45, "● running    user: even.solbraa", color="#cfd8e3",
            fontsize=8, va="center", ha="right", style="italic")
    # sidebar
    box(ax, 0.1, 0.1, 2.4, 5.9, "", fill="#f4f4f4", edge="#bbb")
    for i, t in enumerate(["▼ Project", "  ▸ Concept A",
                            "  ▸ Concept B", "▼ Studies", "  [done] Heat & mass",
                            "  [done] Mechanical", "  [run]  Costs", "▼ Agents",
                            "  @field.dev", "  @flow.assur"]):
        ax.text(0.25, 5.7 - i * 0.45, t, fontsize=8, color="#333")
    # main panes
    # KPI strip
    kpis = [("NPV (P50)", "3 350 MNOK", BLUE),
            ("OPEX",      "85 MNOK/yr", GREEN),
            ("Hydrate",   "Safe",        GREEN),
            ("Risk",      "Medium",      ORANGE)]
    for i, (k, v, c) in enumerate(kpis):
        x = 2.7 + i * 2.25
        box(ax, x, 4.6, 2.05, 1.2, "", fill="white", edge=c, lw=1.2)
        ax.text(x + 1.025, 5.4, k, ha="center", fontsize=8, color="#666")
        ax.text(x + 1.025, 4.95, v, ha="center", fontsize=11, weight="bold",
                color=c)
    # chart area
    box(ax, 2.7, 0.4, 6.0, 4.0, "", fill="white", edge="#ccc")
    xx = np.linspace(2025, 2045, 40)
    yy = 80 * np.exp(-0.18 * (xx - 2025)) + 5
    ax.plot(2.85 + (xx - 2025) * (5.7 / 20), 0.6 + (yy / 90) * 3.6,
            color=BLUE, lw=1.5)
    ax.text(5.7, 4.05, "Production profile", ha="center",
            fontsize=8.5, color="#333", weight="bold")
    # right pane — agent log
    box(ax, 8.85, 0.4, 3.0, 4.0, "", fill="#fafafa", edge="#ccc")
    ax.text(10.35, 4.05, "Agent log", ha="center",
            fontsize=8.5, color="#333", weight="bold")
    log = [
        "@solve.task started",
        "loaded 5 skills",
        "ran 200 MC samples",
        "P10/P50/P90 = -22 / 3352 / 7086",
        "wrote results.json",
        "wrote Report.html",
        "done in 4 min 12 s",
    ]
    for i, line in enumerate(log):
        ax.text(8.95, 3.6 - i * 0.45, "› " + line, fontsize=7.5,
                color="#333", family="monospace")
    save(fig, out("ch13", "figure_02.png"))


# ═════════════════════════════════════════════════════════════════════════════
# Main
# ═════════════════════════════════════════════════════════════════════════════

ALL = [
    fig_ch01_agentic_stack, fig_ch01_vscode_copilot,
    fig_ch01_title_slide, fig_ch01_workflow,
    fig_ch02_paradigm, fig_ch02_token_efficiency, fig_ch02_traceability,
    fig_ch04_loop, fig_ch04_layers, fig_ch04_token_budget,
    fig_ch05_router, fig_ch05_collab_graph,
    fig_ch06_heatmap, fig_ch06_skills_vs_tuning,
    fig_ch07_workflow, fig_ch07_tornado, fig_ch07_p10_p50_p90,
    fig_ch08_taxonomy, fig_ch08_deployment,
    fig_ch13_roadmap, fig_ch13_dashboard,
]


def main():
    setup_style()
    for f in ALL:
        print(f"-- {f.__name__}")
        f()
    print(f"\nGenerated {len(ALL)} concept figures.")


if __name__ == "__main__":
    main()
