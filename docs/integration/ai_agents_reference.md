---
title: "NeqSim AI Agents and Skills Reference"
description: "Complete reference catalog of all 14 AI agents and 9 skills in the NeqSim ecosystem. Includes agent commands, descriptions, example prompts, skill summaries, and cross-reference tables showing which skills each agent uses."
---

# NeqSim AI Agents and Skills Reference

This document is the complete catalog of NeqSim's AI agents and skills.
Use it to find the right agent for your task and understand what skills
power each agent.

> **New to agents?** Read [Introduction to Agentic Engineering with NeqSim](ai_agentic_programming_intro)
> first for concepts and architecture.

---

## How to Use This Reference

**Finding an agent:** Browse the [Agent Catalog](#agent-catalog) below.
Each entry has the VS Code command, description, example prompts, and
related skills.

**Unsure which agent?** Use `@neqsim.help` — the router agent analyzes
your request and picks the right specialist.

**Checking capabilities:** Use `@capability.scout` before complex tasks
to verify NeqSim covers all needed disciplines.

---

## Agent Catalog

### Routing and Discovery Agents

#### `@neqsim.help` — Router

| Field | Value |
|-------|-------|
| **File** | `.github/agents/router.agent.md` |
| **Purpose** | Routes requests to the best specialist agent or composes multi-agent pipelines |
| **Skills** | `neqsim-input-validation` |

**When to use:** When you are not sure which agent applies, or when your
problem spans multiple disciplines.

**Example prompts:**

```
@neqsim.help I need to size a pipeline and check for hydrates
@neqsim.help Calculate density of CO2 at 200 bar
@neqsim.help Full field development study with economics
```

**Multi-agent composition patterns:**

The router recognizes six composition patterns:

1. **Process + Mechanical** — operating conditions then equipment sizing
2. **Fluid + Flow Assurance** — phase behavior then pipeline analysis
3. **PVT + Process** — lab data fitting then process design
4. **Scout + Specialists** — capability check then execution
5. **Safety + Process** — blowdown or PSV sizing with process conditions
6. **Full Stack** — fluid + process + flow assurance + mechanical + economics

---

#### `@capability.scout` — Capability Assessment

| Field | Value |
|-------|-------|
| **File** | `.github/agents/capability.scout.agent.md` |
| **Purpose** | Analyzes tasks for required capabilities, checks NeqSim coverage, identifies gaps, generates NIPs |
| **Skills** | `neqsim-capability-map`, `neqsim-api-patterns`, `neqsim-input-validation`, `neqsim-troubleshooting`, `neqsim-physics-explanations`, `neqsim-regression-baselines`, `neqsim-notebook-patterns`, `neqsim-agent-handoff`, `neqsim-java8-rules` |

**When to use:** Before any complex or unfamiliar task. The scout tells
you what NeqSim can handle, what needs workarounds, and what is missing.

**Example prompts:**

```
@capability.scout Calculate JT cooling for 100 bara lean gas
@capability.scout Design a 50 km subsea tieback with hydrate management and cost estimation
@capability.scout Acid gas injection well design with H2S/CO2 phase behavior, corrosion, and economics
```

**Output:** A structured capability matrix with coverage levels (Full,
Partial, Gap) and, for gaps, a NeqSim Improvement Proposal (NIP) with
proposed Java classes and method signatures.

---

### Engineering Specialist Agents

#### `@thermo.fluid` — Thermodynamic Fluid

| Field | Value |
|-------|-------|
| **File** | `.github/agents/thermo.fluid.agent.md` |
| **Purpose** | Create fluids, select EOS, run flashes, get properties |
| **Skills** | `neqsim-java8-rules`, `neqsim-api-patterns`, `neqsim-input-validation`, `neqsim-troubleshooting`, `neqsim-physics-explanations` |

**Example prompts:**

```
@thermo.fluid Natural gas with 85% methane, 10% ethane, 5% propane at 60 bara
@thermo.fluid Oil with C7+ characterization from assay data
@thermo.fluid CO2-rich stream with water for CCS
```

---

#### `@process.model` — Process Simulation

| Field | Value |
|-------|-------|
| **File** | `.github/agents/process.model.agent.md` |
| **Purpose** | Build flowsheets with equipment, run simulations, validate results |
| **Skills** | `neqsim-java8-rules`, `neqsim-api-patterns`, `neqsim-input-validation`, `neqsim-troubleshooting`, `neqsim-regression-baselines` |

**Example prompts:**

```
@process.model 3-stage gas compression with intercooling from 5 to 150 bara
@process.model TEG dehydration unit for 50 MMSCFD wet gas
@process.model HP/LP separation train with export pipeline
```

---

#### `@pvt.simulation` — PVT Lab Simulation

| Field | Value |
|-------|-------|
| **File** | `.github/agents/pvt.simulation.agent.md` |
| **Purpose** | Run CME, CVD, separator tests, swelling tests, saturation pressure, slim tube |
| **Skills** | `neqsim-java8-rules`, `neqsim-api-patterns` |

**Example prompts:**

```
@pvt.simulation CME test at 100°C with pressures from 400 to 50 bara
@pvt.simulation CVD for reservoir fluid with C7+ characterization
@pvt.simulation Fit EOS to match experimental saturation pressure of 250 bara
```

---

#### `@flow.assurance` — Flow Assurance

| Field | Value |
|-------|-------|
| **File** | `.github/agents/flow.assurance.agent.md` |
| **Purpose** | Hydrate prediction, wax temperature, corrosion, pipeline pressure drop, thermal-hydraulic analysis |
| **Skills** | `neqsim-java8-rules`, `neqsim-api-patterns` |

**Example prompts:**

```
@flow.assurance Hydrate formation temperature for wet gas at 100 bara
@flow.assurance Pipeline pressure drop and temperature profile for 50 km subsea line
@flow.assurance Asphaltene stability screening for reservoir fluid under gas injection
```

---

#### `@gas.quality` — Gas Quality Standards

| Field | Value |
|-------|-------|
| **File** | `.github/agents/gas.quality.agent.md` |
| **Purpose** | ISO 6976, ISO 6578, AGA 3/7, GPA 2145, EN 16723 / 16726 calculations |
| **Skills** | `neqsim-java8-rules`, `neqsim-api-patterns` |

**Example prompts:**

```
@gas.quality Calculate heating value and Wobbe index for a natural gas per ISO 6976
@gas.quality Check if gas meets EN 16726 H-gas specification
@gas.quality Calculate AGA flow measurement for custody transfer
```

---

#### `@mechanical.design` — Mechanical Design

| Field | Value |
|-------|-------|
| **File** | `.github/agents/mechanical.design.agent.md` |
| **Purpose** | Wall thickness, material selection, weight, cost per ASME/API/DNV/ISO/NORSOK |
| **Skills** | `neqsim-java8-rules`, `neqsim-api-patterns` |

**Example prompts:**

```
@mechanical.design Design a 20-inch export pipeline for 150 bara per DNV-OS-F101
@mechanical.design Size an HP separator vessel per ASME VIII Div.1
@mechanical.design Mechanical design for a subsea manifold with Equinor TR requirements
```

---

#### `@safety.depressuring` — Safety and Depressuring

| Field | Value |
|-------|-------|
| **File** | `.github/agents/safety.depressuring.agent.md` |
| **Purpose** | Blowdown, PSV sizing (API 520/521), fire case, source terms, safety envelopes |
| **Skills** | `neqsim-java8-rules`, `neqsim-api-patterns` |

**Example prompts:**

```
@safety.depressuring Depressurize an HP separator from 85 bara under fire case
@safety.depressuring Size a PSV for blocked outlet on a gas cooler
@safety.depressuring Generate source terms for a 2-inch leak from a gas pipeline at 120 bara
```

---

### Workflow and Productivity Agents

#### `@solve.task` — Full Engineering Task Solver

| Field | Value |
|-------|-------|
| **File** | `.github/agents/solve.task.agent.md` |
| **Purpose** | End-to-end task solving: 3-step workflow (Scope → Analysis → Report) with notebooks, validation, Word + HTML reports |
| **Skills** | `neqsim-capability-map`, `neqsim-notebook-patterns`, `neqsim-api-patterns` |

**When to use:** When you need a formal deliverable with validation,
figures, and a generated report.

**Example prompts:**

```
@solve.task JT cooling for rich gas at 100 bara
@solve.task TEG dehydration sizing for 50 MMSCFD wet gas
@solve.task Field development concept selection for deepwater gas per NORSOK
```

---

#### `@solve.process` — Quick Process Notebook

| Field | Value |
|-------|-------|
| **File** | `.github/agents/solve.process.agent.md` |
| **Purpose** | Fast path to a working process simulation Jupyter notebook |
| **Skills** | `neqsim-notebook-patterns`, `neqsim-api-patterns` |

**When to use:** When you want a simulation notebook quickly without the
full 3-step workflow.

**Example prompts:**

```
@solve.process 3-stage compression with intercooling from 5 to 150 bara for 50 MMSCFD gas
@solve.process Simple separator train with HP/LP separation
@solve.process Gas export pipeline pressure drop calculation
```

---

#### `@notebook.example` — Jupyter Notebook Creator

| Field | Value |
|-------|-------|
| **File** | `.github/agents/notebook.example.agent.md` |
| **Purpose** | Creates example notebooks with proper structure and visualization |
| **Skills** | `neqsim-notebook-patterns`, `neqsim-api-patterns` |

**Example prompts:**

```
@notebook.example TEG dehydration process with results visualization
@notebook.example Phase envelope calculation for a rich gas
@notebook.example CO2 injection swelling test with matplotlib plots
```

---

#### `@neqsim.test` — Unit Test Creator

| Field | Value |
|-------|-------|
| **File** | `.github/agents/neqsim.test.agent.md` |
| **Purpose** | Creates JUnit 5 tests following NeqSim conventions |
| **Skills** | `neqsim-java8-rules`, `neqsim-api-patterns` |

**Example prompts:**

```
@neqsim.test Test the new compressor anti-surge logic
@neqsim.test Write regression tests for SRK EOS with CO2-methane binary
@neqsim.test Test the separator with three-phase flow and C7+ oil
```

---

#### `@documentation` — Documentation Writer

| Field | Value |
|-------|-------|
| **File** | `.github/agents/documentation.agent.md` |
| **Purpose** | Creates and updates markdown guides, API references, cookbook recipes, tutorials |
| **Skills** | `neqsim-notebook-patterns`, `neqsim-java8-rules`, `neqsim-api-patterns` |

**Example prompts:**

```
@documentation Document the new pipe flow network solver
@documentation Add a cookbook recipe for TEG dehydration
@documentation Create a tutorial for phase envelope calculation
```

---

## Skills Reference

### neqsim-api-patterns

| Field | Value |
|-------|-------|
| **Path** | `.github/skills/neqsim-api-patterns/SKILL.md` |
| **Scope** | Code recipes for all common NeqSim operations |
| **Used by** | All 13 specialist agents |

**Contains:**
- EOS selection guide (SRK, PR, CPA, GERG — when to use each)
- Fluid creation sequence (constructor, addComponent, setMixingRule)
- Oil characterization (TBP, plus fractions)
- Flash calculations and property retrieval (initProperties mandatory)
- Stream and equipment patterns
- Unit conventions (Kelvin, bara, etc.)

---

### neqsim-java8-rules

| Field | Value |
|-------|-------|
| **Path** | `.github/skills/neqsim-java8-rules/SKILL.md` |
| **Scope** | Forbidden Java 9+ features and their Java 8 replacements |
| **Used by** | All agents producing Java code (12 of 14) |

**Key rules:**
- No `var` — use explicit types
- No `List.of()`, `Set.of()`, `Map.of()` — use `Arrays.asList()`, `new HashSet<>()`, `HashMap`
- No `String.repeat()` — use `StringUtils.repeat()`
- No text blocks, records, pattern matching `instanceof`
- No `Optional.isEmpty()` — use `!optional.isPresent()`

---

### neqsim-notebook-patterns

| Field | Value |
|-------|-------|
| **Path** | `.github/skills/neqsim-notebook-patterns/SKILL.md` |
| **Scope** | Standard patterns for Jupyter notebooks |
| **Used by** | `@notebook.example`, `@solve.process`, `@solve.task`, `@documentation`, `@capability.scout` |

**Contains:**
- Dual-boot setup cell (devtools vs pip)
- Import pattern with `jneqsim` gateway
- 8-section notebook structure
- Visualization requirements (2-3 matplotlib figures minimum)
- `results.json` schema for report generation

---

### neqsim-capability-map

| Field | Value |
|-------|-------|
| **Path** | `.github/skills/neqsim-capability-map/SKILL.md` |
| **Scope** | Structured inventory of all NeqSim capabilities by discipline |
| **Used by** | `@capability.scout`, `@solve.task` |

**Covers:**
- 30+ thermodynamic model classes
- 40+ process equipment types
- PVT simulation methods
- Gas quality standards
- Mechanical design codes
- Flow assurance tools
- Safety and depressuring

---

### neqsim-input-validation

| Field | Value |
|-------|-------|
| **Path** | `.github/skills/neqsim-input-validation/SKILL.md` |
| **Scope** | Pre-simulation validation rules |
| **Used by** | `@thermo.fluid`, `@process.model`, `@neqsim.help`, `@capability.scout` |

**Checks:**
- Temperature must be in Kelvin and > 0
- Pressure must be > 0 bara
- Mole fractions must sum to approximately 1.0
- Component names must match entries in COMP.csv
- Equipment parameters within physically valid ranges

---

### neqsim-troubleshooting

| Field | Value |
|-------|-------|
| **Path** | `.github/skills/neqsim-troubleshooting/SKILL.md` |
| **Scope** | Ranked recovery strategies for common failures |
| **Used by** | `@thermo.fluid`, `@process.model`, `@capability.scout` |

**Failure categories covered:**
- Flash non-convergence
- Recycle divergence
- Adjuster failures
- Zero or NaN property values
- Phase identification errors
- Missing component parameters

Each category has ordered recovery steps: try the first strategy, if it
fails try the next, and so on.

---

### neqsim-physics-explanations

| Field | Value |
|-------|-------|
| **Path** | `.github/skills/neqsim-physics-explanations/SKILL.md` |
| **Scope** | Plain-language explanations of engineering phenomena |
| **Used by** | `@thermo.fluid`, `@capability.scout` |

**Topics:**
- Joule-Thomson cooling
- Retrograde condensation
- Hydrate formation
- Phase envelopes and critical points
- Water dew point
- Vapor-liquid equilibrium

---

### neqsim-regression-baselines

| Field | Value |
|-------|-------|
| **Path** | `.github/skills/neqsim-regression-baselines/SKILL.md` |
| **Scope** | Baseline management for preventing accuracy drift |
| **Used by** | `@process.model`, `@capability.scout` |

**Workflow:**
1. Capture baseline values before making changes
2. Save baselines as JSON fixtures in `src/test/resources`
3. Write regression tests that compare against baselines with explicit tolerances
4. Run regression tests after changes to detect drift

---

### neqsim-agent-handoff

| Field | Value |
|-------|-------|
| **Path** | `.github/skills/neqsim-agent-handoff/SKILL.md` |
| **Scope** | Structured JSON schemas for multi-agent data passing |
| **Used by** | `@capability.scout` (for composing multi-agent workflows) |

**Schemas defined:**
- Fluid definition handoff (EOS, components, mixing rule, conditions)
- Process simulation results (stream conditions, equipment performance)
- Design output (dimensions, materials, costs, standards compliance)

---

## Cross-Reference Tables

### Agent-to-Skills Matrix

| Agent | api-patterns | java8 | notebook | input-val | troubleshoot | cap-map | physics | regression | handoff |
|-------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `@neqsim.help` | | | | X | | | | | |
| `@capability.scout` | X | X | X | X | X | X | X | X | X |
| `@thermo.fluid` | X | X | | X | X | | X | | |
| `@process.model` | X | X | | X | X | | | X | |
| `@pvt.simulation` | X | X | | | | | | | |
| `@flow.assurance` | X | X | | | | | | | |
| `@gas.quality` | X | X | | | | | | | |
| `@mechanical.design` | X | X | | | | | | | |
| `@safety.depressuring` | X | X | | | | | | | |
| `@solve.task` | X | | X | | | X | | | |
| `@solve.process` | X | | X | | | | | | |
| `@notebook.example` | X | | X | | | | | | |
| `@neqsim.test` | X | X | | | | | | | |
| `@documentation` | X | X | X | | | | | | |

### Task Type to Agent Mapping

| Task Type | Primary Agent | Often Combined With |
|-----------|--------------|-------------------|
| Quick property lookup | `@thermo.fluid` | — |
| Flash calculation | `@thermo.fluid` | — |
| Process flowsheet | `@process.model` | `@thermo.fluid` |
| PVT lab experiment | `@pvt.simulation` | `@thermo.fluid` |
| Hydrate prediction | `@flow.assurance` | `@thermo.fluid` |
| Pipeline sizing | `@flow.assurance` | `@mechanical.design` |
| Gas quality check | `@gas.quality` | — |
| Equipment sizing | `@mechanical.design` | `@process.model` |
| PSV / blowdown | `@safety.depressuring` | `@process.model` |
| Full engineering study | `@solve.task` | All specialists |
| Quick notebook | `@solve.process` | — |
| Capability check | `@capability.scout` | — |
| Write documentation | `@documentation` | — |
| Write unit tests | `@neqsim.test` | — |

---

## Related Documentation

- [Introduction to Agentic Engineering](ai_agentic_programming_intro) — concepts and architecture
- [Agentic Workflow Examples](ai_workflow_examples) — step-by-step walkthroughs
- [Solve an Engineering Task](../tutorials/solve-engineering-task) — hands-on tutorial
- [Task Solving Guide](../development/TASK_SOLVING_GUIDE) — developer workflow reference
- [Code Patterns](../development/CODE_PATTERNS) — copy-paste code starters
