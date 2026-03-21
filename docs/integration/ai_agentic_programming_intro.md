---
title: "Introduction to Agentic Engineering with NeqSim"
description: "A comprehensive introduction to using AI agents for engineering calculations and process simulation with NeqSim. Explains what agentic programming is, why it matters for engineers, the architecture of NeqSim's agent system, and how to get started from scratch."
---

# Introduction to Agentic Engineering with NeqSim

This guide explains how NeqSim combines AI agents with a rigorous physics engine
to solve engineering problems. It is written for engineers and scientists who are
new to AI-assisted workflows and want to understand both the concepts and the
practical tools.

**What you will learn:**

- What agentic programming means and why it matters for engineering
- How NeqSim's multi-agent architecture works
- The role of agents, skills, and the physics engine
- How to start using agents to solve real problems
- Worked examples from simple to complex

**Who this is for:** Process engineers, reservoir engineers, thermodynamics
specialists, and anyone who wants to use AI to accelerate engineering work
without sacrificing physical accuracy.

---

## 1. The Problem: AI Alone Cannot Do Engineering

Large Language Models (LLMs) like GPT-4, Claude, and Copilot are remarkable
at understanding natural language, writing code, and reasoning about structure.
But they have a fundamental limitation for engineering work:

**LLMs hallucinate physics.**

Ask an LLM "what is the density of methane at 200 bara and 25 degrees C?" and
you may get a plausible-sounding but wrong answer. It cannot solve the cubic
equation of state. It cannot iterate to find the correct compressibility factor.
It cannot evaluate fugacity coefficients or check phase stability.

NeqSim, on the other hand, can do all of these things with thermodynamic rigor,
but it has no understanding of natural language, cannot interpret engineering
intent, and produces raw numbers without context.

**The solution: combine them.**

```
┌─────────────────────────┐     ┌─────────────────────────────┐
│      AI Agent           │     │      NeqSim Physics         │
│                         │     │                             │
│  Understands intent     │────▶│  SRK / PR / CPA / GERG EOS │
│  Selects methods        │     │  Flash calculations         │
│  Writes simulation code │◀────│  Phase equilibrium          │
│  Interprets results     │     │  Transport properties       │
│  Generates reports      │     │  Process equipment models   │
│  Manages workflows      │     │  Standards (ISO, API, DNV)  │
│                         │     │  Mechanical design          │
└─────────────────────────┘     └─────────────────────────────┘
```

The AI agent writes the code. NeqSim runs the physics. You get validated results.

---

## 2. What is Agentic Programming?

**Agentic programming** is a software design pattern where an AI model does
not just answer questions — it takes actions, uses tools, and completes
multi-step tasks autonomously.

In traditional AI usage, you ask a question and get an answer:

```
You:    "What EOS should I use for natural gas with water?"
AI:     "Use SRK-CPA (SystemSrkCPAstatoil) with mixing rule 10."
```

In agentic programming, you describe a goal and the agent executes it:

```
You:    "Calculate the hydrate formation temperature for this wet gas
         at 100 bara, and generate a report."

Agent:  1. Creates a CPA fluid with the gas composition
        2. Adds water at saturation
        3. Runs hydrate equilibrium calculation via NeqSim
        4. Sweeps pressure from 20 to 200 bara
        5. Creates a plot of the hydrate curve
        6. Validates against literature data
        7. Generates a Word + HTML report
```

The agent has access to **tools** — it can read files, search code, run
simulations, create notebooks, and generate reports. Each tool is a
capability the agent can invoke when needed.

### Key Concepts

| Concept | Definition | NeqSim Example |
|---------|-----------|----------------|
| **Agent** | An AI with a specific purpose, instructions, and tool access | `@solve.task` solves engineering problems end-to-end |
| **Skill** | A knowledge package loaded on-demand by agents | `neqsim-api-patterns` — code recipes for creating fluids |
| **Tool** | A capability the agent can invoke (read file, run code, search) | Terminal execution, file creation, notebook cell runner |
| **Physics Engine** | The computational backend that does the math | NeqSim Java library (EOS, flash, equipment models) |
| **Workflow** | A structured multi-step process the agent follows | 3-step: Scope & Research → Analysis → Report |

### Why Agents Instead of Scripts?

Traditional automation (scripts, macros) is brittle — it breaks when inputs
change. Agents are adaptive:

| Aspect | Script | Agent |
|--------|--------|-------|
| Input format | Must be exact | Natural language |
| Error handling | Crashes or silent failure | Diagnoses and retries |
| Missing data | Stops | Makes documented assumptions |
| New requirement | Write new script | Describe what you want |
| Learning | None | Searches past solutions |

---

## 3. NeqSim's Agent Architecture

NeqSim provides a multi-agent system where specialist agents handle different
engineering disciplines. A router agent directs requests to the right
specialist, and skills provide domain knowledge that agents load on demand.

### Architecture Overview

```
                    ┌───────────────────────┐
                    │     Your Request      │
                    │  "size a pipeline     │
                    │   and check hydrates" │
                    └──────────┬────────────┘
                               │
                    ┌──────────▼────────────┐
                    │    @neqsim.help       │
                    │    (Router Agent)     │
                    └──┬────────────────┬───┘
                       │                │
            ┌──────────▼───┐    ┌───────▼──────────┐
            │ @process.model│    │ @flow.assurance  │
            │ (Pipeline P,T)│    │ (Hydrate curve)  │
            └──────┬───────┘    └───────┬──────────┘
                   │                    │
            ┌──────▼────────────────────▼──────┐
            │         NeqSim Java Engine        │
            │  (SRK-CPA, PipeBeggsAndBrills,    │
            │   HydrateEquilibriumTemperature)   │
            └───────────────────────────────────┘
```

### The Three Layers

**Layer 1: Agents** — Specialist AI assistants, each trained for a specific
engineering discipline. You invoke them with `@agent-name` in VS Code Copilot
Chat.

**Layer 2: Skills** — Reusable knowledge packages that agents load when needed.
A skill contains patterns, rules, and reference data — not code, but the
knowledge needed to write correct code.

**Layer 3: NeqSim Physics Engine** — The Java library that performs all
thermodynamic, transport, and process calculations. Agents generate code that
calls this engine.

### How They Work Together

1. You describe an engineering problem in natural language
2. The router agent analyzes your request and identifies the domain(s)
3. The appropriate specialist agent(s) are invoked
4. Each agent loads relevant skills (EOS selection rules, validation checks)
5. The agent writes Python/Java code that calls the NeqSim engine
6. NeqSim computes the physics (flash calculations, equipment models, etc.)
7. The agent interprets results and presents them with context

---

## 4. The Agent Catalog

NeqSim provides 14 specialist agents organized by function.

### Routing and Discovery

These agents help you find the right tool for the job.

| Agent | Command | What It Does |
|-------|---------|-------------|
| **Router** | `@neqsim.help` | Analyzes your request and routes to the correct specialist. Use when unsure which agent to pick. |
| **Capability Scout** | `@capability.scout` | Checks what NeqSim can do for a task. Identifies gaps and plans implementations. Use before complex multi-discipline work. |

**Example:**
```
@neqsim.help I need to design a gas export pipeline with hydrate
management and cost estimation
```
The router detects three disciplines (process, flow assurance, mechanical
design) and composes a multi-agent pipeline.

### Engineering Specialists

| Agent | Command | Engineering Discipline |
|-------|---------|----------------------|
| **Thermodynamic Fluid** | `@thermo.fluid` | Create fluids, select EOS, run flash calculations, get properties |
| **Process Simulation** | `@process.model` | Build flowsheets with separators, compressors, heat exchangers, valves |
| **PVT Simulation** | `@pvt.simulation` | Run lab experiments (CME, CVD, swelling test, saturation pressure) |
| **Flow Assurance** | `@flow.assurance` | Hydrate prediction, wax, corrosion, pipeline pressure drop |
| **Gas Quality** | `@gas.quality` | ISO 6976, EN 16726, Wobbe index, custody transfer |
| **Mechanical Design** | `@mechanical.design` | Wall thickness, material selection, cost estimation per ASME/DNV/API |
| **Safety** | `@safety.depressuring` | Blowdown, PSV sizing, fire case, source term generation |

### Workflow and Productivity

| Agent | Command | Purpose |
|-------|---------|---------|
| **Solve Engineering Task** | `@solve.task` | End-to-end task solving with 3-step workflow (scope, simulation, report) |
| **Quick Process Sim** | `@solve.process` | Fast path to a working simulation notebook |
| **Jupyter Notebook** | `@notebook.example` | Creates example notebooks with visualization |
| **Unit Tests** | `@neqsim.test` | Writes JUnit 5 tests for NeqSim code |
| **Documentation** | `@documentation` | Creates and updates markdown guides and tutorials |

---

## 5. The Skills Library

Skills are knowledge packages that agents load automatically when relevant.
They contain rules, patterns, and reference data — not executable code, but
the knowledge needed to produce correct code.

### Why Skills Matter

Without skills, an agent might:
- Use the wrong equation of state for a polar system
- Forget to call `initProperties()` after a flash calculation
- Use Java 9+ features that won't compile
- Miss a critical validation step

Skills encode the lessons learned from hundreds of simulations.

### Available Skills

| Skill | What It Contains | Loaded When |
|-------|-----------------|-------------|
| **neqsim-api-patterns** | Code recipes for creating fluids, running flashes, using equipment | Writing any NeqSim code |
| **neqsim-capability-map** | Inventory of all NeqSim capabilities by discipline | Checking "can NeqSim do X?" |
| **neqsim-input-validation** | Rules for valid temperature, pressure, composition, component names | Setting up any simulation |
| **neqsim-troubleshooting** | Recovery strategies for convergence failures, zero values, phase issues | When a simulation fails |
| **neqsim-physics-explanations** | Plain-language explanations of JT effect, retrograde condensation, hydrate formation | Explaining results to users |
| **neqsim-regression-baselines** | How to capture baseline values and write regression tests | Modifying EOS or solver code |
| **neqsim-agent-handoff** | Structured schemas for passing data between agents | Multi-agent pipelines |
| **neqsim-notebook-patterns** | Jupyter notebook structure, import patterns, visualization rules | Creating notebooks |
| **neqsim-java8-rules** | Forbidden Java 9+ features and replacement patterns | Writing any Java code |

### How Skills Are Used

Skills are loaded **automatically** based on context. When you ask
`@process.model` to build a compressor train, it automatically loads:

1. `neqsim-api-patterns` — to use the correct Java API calls
2. `neqsim-input-validation` — to check your pressure and temperature values
3. `neqsim-java8-rules` — if it needs to write any Java code

You don't need to request skills explicitly — the agent knows which ones
to load based on what you asked for.

---

## 6. The Task-Solving Workflow

NeqSim's most powerful pattern is the **3-step task-solving workflow** that
the `@solve.task` agent executes automatically.

### The Three Steps

```
 STEP 1                    STEP 2                    STEP 3
 ─────────────────         ─────────────────         ─────────────────
 Scope & Research          Analysis & Evaluation     Report

 • Define standards        • Build simulation        • Generate Word doc
 • Select methods          • Run NeqSim calculations • Generate HTML page
 • Set acceptance criteria • Validate results        • Include figures
 • Research background     • Create visualizations   • Save results.json
 • Identify NeqSim gaps    • Benchmark against data
```

### Every Task Gets a Folder

When you invoke `@solve.task`, it immediately creates a structured folder:

```
task_solve/2026-03-21_hydrate_analysis/
├── README.md                              ← Progress checklist
├── results.json                           ← Machine-readable results
├── figures/                               ← Saved PNG plots
├── step1_scope_and_research/
│   ├── task_spec.md                       ← Standards, methods, criteria
│   ├── notes.md                           ← Research notes
│   └── references/                        ← PDFs, standards
├── step2_analysis/
│   ├── 01_hydrate_analysis.ipynb          ← Main simulation notebook
│   ├── 02_benchmark_validation.ipynb      ← Comparison against reference data
│   └── 03_uncertainty_risk.ipynb          ← Monte Carlo and risk register
└── step3_report/
    └── generate_report.py                 ← Produces Word + HTML
```

### Adaptive Depth

The same workflow handles everything from a quick property lookup to a
Class A field development study. The depth adapts to what you ask for:

| You Ask For | What Happens |
|-------------|-------------|
| "density of CO2 at 200 bar" | Quick: one flash calculation, brief answer |
| "TEG dehydration for 50 MMSCFD per NORSOK" | Standard: full task folder, notebook, Word + HTML report |
| "field development concept selection per DNV and NORSOK" | Comprehensive: multiple notebooks, Monte Carlo, risk analysis, formal report |

### The Development Flywheel

What makes this workflow unique is that solving tasks and developing NeqSim
happen simultaneously. When an agent discovers a missing capability:

```
 Task → uses NeqSim API → discovers gap → implements improvement → PR back
                                                                      ↓
                                                     next task has better API
```

Every solved task makes NeqSim more capable for the next task.

---

## 7. Getting Started: Your First Agent Interaction

### Prerequisites

| Requirement | Purpose |
|-------------|---------|
| [VS Code](https://code.visualstudio.com/) | Editor |
| [GitHub Copilot extension](https://marketplace.visualstudio.com/items?itemName=GitHub.copilot) | AI agent host |
| [Python 3.8+](https://python.org) | Runs notebooks |
| [Java JDK 8+](https://adoptium.net) | NeqSim physics engine |

### Setup (One Time)

```bash
# Clone the repository
git clone https://github.com/equinor/neqsim.git
cd neqsim

# Build NeqSim
./mvnw install            # Linux/Mac
mvnw.cmd install           # Windows

# Install Python helpers
pip install neqsim                     # Released package
pip install matplotlib python-docx     # For reports and plots
```

### Example 1: Quick Property Lookup

Open VS Code Copilot Chat and type:

```
@thermo.fluid What is the density of methane at 100 bara and 25 degrees C?
```

The agent will:
1. Create a `SystemSrkEos` fluid with methane
2. Run a TP flash at 298.15 K and 100 bara
3. Call `initProperties()` to initialize transport properties
4. Report the density in kg/m3

### Example 2: Process Simulation

```
@process.model Build a 3-stage compression train from 5 to 150 bara
with intercooling to 35 degrees C after each stage
```

The agent will:
1. Create the feed fluid
2. Add three compressor + cooler pairs
3. Set intermediate pressures using equal compression ratio
4. Build a `ProcessSystem` and run it
5. Report power consumption and outlet temperatures for each stage

### Example 3: Full Engineering Task

```
@solve.task Calculate the hydrate formation temperature for a wet natural
gas at pressures from 20 to 200 bara. The gas composition is 85% methane,
8% ethane, 4% propane, 2% CO2, 1% N2 with water-saturated conditions.
Compare against CSMHyd data.
```

The agent executes the complete 3-step workflow — scope, simulation with
validation, and report generation — producing a Word document with figures,
tables, and references.

### Example 4: Capability Assessment

Before starting a complex task, check what NeqSim can do:

```
@capability.scout Can NeqSim handle acid gas injection design with
H2S/CO2 phase behavior, well casing design, corrosion assessment,
and NPV economics?
```

The scout returns a structured capability matrix showing what is available,
what has limitations, and what is missing — with recommended agents and
implementation plans for any gaps.

---

## 8. Multi-Agent Composition

For complex tasks spanning multiple disciplines, agents can work together
in pipelines. The router agent or `@solve.task` automatically composes
these pipelines when it detects cross-discipline needs.

### Common Composition Patterns

**Pattern 1: Process + Mechanical**

Task: "Design a 20-inch export pipeline for 150 bara"

```
@process.model  →  Get operating conditions (P, T, flow, composition)
       ↓
@mechanical.design  →  Calculate wall thickness per DNV-OS-F101
```

**Pattern 2: Fluid + Flow Assurance**

Task: "Check for hydrates in a wet gas pipeline"

```
@thermo.fluid  →  Create CPA fluid with water and MEG
       ↓
@flow.assurance  →  Run hydrate curve + pipeline P,T profile
```

**Pattern 3: Capability Scout + Specialists**

Task: "Full field development study with subsea tieback"

```
@capability.scout  →  Assess all needed capabilities, identify gaps
       ↓
@process.model  →  Steady-state process design
       ↓
@flow.assurance  →  Pipeline sizing and hydrate management
       ↓
@mechanical.design  →  Equipment sizing and SURF cost estimation
       ↓
@solve.task  →  Economics (NPV), uncertainty, risk analysis, report
```

---

## 9. How the Physics Engine Works

Every agent interaction ultimately calls the NeqSim Java library.
Understanding the basics helps you provide better prompts and interpret
results correctly.

### Thermodynamic Models

NeqSim solves equations of state (EOS) to compute fluid properties:

| EOS | Best For | Java Class |
|-----|----------|-----------|
| **SRK** | Dry gas, lean gas, simple hydrocarbons | `SystemSrkEos` |
| **PR** | General hydrocarbons, oil systems | `SystemPrEos` |
| **SRK-CPA** | Water, MEG, methanol, polar molecules | `SystemSrkCPAstatoil` |
| **GERG-2008** | Custody transfer, fiscal metering | `SystemGERG2008Eos` |

### The Flash Calculation

The core of all thermodynamic calculations is the **flash** — given conditions
(temperature, pressure, composition), find how many phases exist and what
their properties are.

```
Input:  T = 298.15 K, P = 100 bara, z = [0.85 CH4, 0.10 C2H6, 0.05 C3H8]
  ↓
Flash:  Solve cubic EOS → find phase fractions, compositions, properties
  ↓
Output: 1 phase (gas), density = 78.5 kg/m3, Z = 0.82, Cp = 2450 J/(kg·K)
```

### Process Equipment

NeqSim models 40+ types of process equipment. Each equipment takes inlet
streams, performs a calculation (isenthalpic, isentropic, equilibrium, etc.),
and produces outlet streams.

```
Feed Stream → [Separator] → Gas Out Stream
                           → Liquid Out Stream
```

Equipment is assembled into a `ProcessSystem` (flowsheet) and solved
sequentially with recycle convergence as needed.

---

## 10. Best Practices

### For Beginners

1. **Start with `@neqsim.help`** — describe your problem in plain language and
   let the router pick the right agent
2. **Be specific about conditions** — include temperature, pressure, and
   composition in your request
3. **Mention standards if applicable** — "per ISO 6976" or "per DNV-OS-F101"
   triggers deeper analysis
4. **Use `@capability.scout` for complex tasks** — check what is available
   before starting

### For Experienced Users

1. **Go directly to specialist agents** — skip the router if you know which
   discipline applies
2. **Use `@solve.task` for formal deliverables** — it produces Word + HTML
   reports with validation
3. **Check `TASK_LOG.md` for past solutions** — avoid solving the same problem
   twice
4. **Contribute improvements back** — when you discover a gap, the agent can
   create a NeqSim Improvement Proposal (NIP)

### Common Pitfalls

| Pitfall | Why It Happens | How to Avoid |
|---------|---------------|-------------|
| Zero density or viscosity | Missing `initProperties()` after flash | Agents handle this automatically via skills |
| Wrong EOS for polar systems | Using SRK instead of CPA for water/MEG | Specify "with water" in your request — the agent selects CPA |
| Convergence failure | Conditions outside EOS validity | The troubleshooting skill provides recovery strategies |
| Java 9+ compile errors | Using `var`, `List.of()` in Java code | The java8-rules skill prevents this |

---

## 11. Comparison with Traditional Approaches

| Aspect | Manual Coding | Commercial Simulator | NeqSim + AI Agents |
|--------|--------------|---------------------|-------------------|
| Learning curve | Steep (learn API) | Moderate (learn GUI) | Low (natural language) |
| Scripting support | Full control | Limited macro language | Full Python + Java |
| Standards compliance | Manual lookup | Some built-in | Agent loads applicable standards |
| Reproducibility | Good (version-controlled code) | Poor (GUI state not tracked) | Excellent (notebook + task folder) |
| Cost | Free (open source) | Expensive licenses | Free (open source + Copilot) |
| Physics rigor | Full (you control everything) | Full (vendor-validated) | Full (same NeqSim engine) |
| Report generation | Manual | Manual export | Automated Word + HTML |
| Extensibility | Add Java classes | Vendor-dependent | Add Java + agent understands it |

---

## 12. Where to Go Next

| Goal | Resource |
|------|----------|
| Solve your first task | [Solve an Engineering Task](../tutorials/solve-engineering-task) tutorial |
| See all agents and skills | [Agents and Skills Reference](ai_agents_reference) |
| See real workflow examples | [Agentic Workflow Examples](ai_workflow_examples) |
| Learn NeqSim Java API | [API Patterns](../../.github/skills/neqsim-api-patterns/SKILL.md) |
| Check NeqSim capabilities | [Capability Map](../../.github/skills/neqsim-capability-map/SKILL.md) |
| Browse example notebooks | [Examples Index](../examples/index) |
| Start from Python | [Python Quickstart](../quickstart/python-quickstart) |

---

## Glossary

| Term | Definition |
|------|-----------|
| **Agent** | An AI assistant with specific instructions, domain knowledge, and tool access. Each agent handles one engineering discipline. |
| **Skill** | A knowledge package (markdown file) loaded by agents to provide domain-specific rules and patterns. |
| **Router** | The `@neqsim.help` agent that analyzes requests and delegates to specialists. |
| **Capability Scout** | The `@capability.scout` agent that assesses what NeqSim can do for a given task. |
| **Flash Calculation** | The core thermodynamic calculation that determines phase composition and properties at given conditions. |
| **EOS** | Equation of State — the mathematical model relating pressure, volume, temperature, and composition. |
| **CPA** | Cubic-Plus-Association — an EOS extension for hydrogen-bonding molecules (water, alcohols). |
| **NIP** | NeqSim Improvement Proposal — a structured plan for adding a missing capability. |
| **ProcessSystem** | NeqSim's flowsheet container that holds equipment, streams, and convergence logic. |
| **Task Folder** | The `task_solve/` subfolder where all artifacts for an engineering task are stored. |
| **jneqsim** | The Python gateway to NeqSim's Java classes, provided by the neqsim-python package. |
| **initProperties()** | Critical method that must be called after a flash calculation to initialize transport properties. |
