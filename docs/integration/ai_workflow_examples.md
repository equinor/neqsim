---
title: "Agentic Workflow Examples for NeqSim"
description: "Step-by-step walkthroughs showing how AI agents solve real engineering problems using NeqSim. Includes examples from simple property lookups to complex multi-discipline field development studies with multi-agent composition."
---

# Agentic Workflow Examples

This document shows how NeqSim's AI agents handle real engineering problems,
from simple single-agent interactions to complex multi-agent pipelines.
Each example shows the prompt, what happens behind the scenes, and the
output you receive.

> **Prerequisites:** Read [Introduction to Agentic Engineering](ai_agentic_programming_intro)
> for the underlying concepts, and [Agents Reference](ai_agents_reference)
> for the full agent catalog.

---

## Example 1: Simple Property Lookup

**Difficulty:** Beginner | **Agent:** `@thermo.fluid` | **Time:** seconds

### The Prompt

```
@thermo.fluid What is the density and viscosity of methane at 200 bara and 40°C?
```

### What the Agent Does

1. **Selects EOS** — SRK for a simple hydrocarbon
2. **Creates fluid** — `SystemSrkEos(273.15 + 40.0, 200.0)` with methane
3. **Sets mixing rule** — `"classic"`
4. **Runs flash** — `TPflash()` to determine the phase state
5. **Initializes properties** — `initProperties()` (critical step for transport props)
6. **Reads values** — density via `getDensity("kg/m3")`, viscosity via phase methods

### Behind the Scenes

The agent loads two skills automatically:
- `neqsim-api-patterns` — knows the correct method calls
- `neqsim-input-validation` — confirms 200 bara and 313.15 K are valid

### The Output

```
At 200 bara and 40°C, methane is a supercritical fluid:
- Density: 162.3 kg/m³
- Dynamic viscosity: 2.45 × 10⁻⁵ Pa·s
- Compressibility factor Z: 0.785
```

---

## Example 2: Process Simulation Notebook

**Difficulty:** Intermediate | **Agent:** `@solve.process` | **Time:** minutes

### The Prompt

```
@solve.process Build a 3-stage compression train from 5 to 150 bara with
intercooling to 35°C for a natural gas at 100,000 Sm3/day
```

### What the Agent Does

1. **Plans the process:**
   - Compression ratio per stage: $(150/5)^{1/3} \approx 3.11$
   - Stage 1: 5 → 15.6 bara
   - Stage 2: 15.6 → 48.4 bara
   - Stage 3: 48.4 → 150 bara

2. **Creates fluid:**
   ```python
   fluid = SystemSrkEos(273.15 + 25.0, 5.0)
   fluid.addComponent("methane", 0.85)
   fluid.addComponent("ethane", 0.08)
   fluid.addComponent("propane", 0.04)
   fluid.addComponent("n-butane", 0.02)
   fluid.addComponent("nitrogen", 0.01)
   fluid.setMixingRule("classic")
   ```

3. **Builds flowsheet** with 3 compressor + cooler pairs

4. **Runs simulation** via `process.run()`

5. **Creates visualization** — discharge temperature, power per stage, P-T diagram

### Skills Loaded

- `neqsim-api-patterns` — correct `Compressor` and `Cooler` constructors
- `neqsim-notebook-patterns` — proper notebook structure with 8 sections

### Output

A complete Jupyter notebook with:
- Process flow diagram (text description)
- Results table (pressures, temperatures, power per stage)
- Matplotlib figures (P-T profile, power distribution)
- Total power consumption summary

---

## Example 3: Flow Assurance Study

**Difficulty:** Intermediate | **Agent:** `@flow.assurance` | **Time:** minutes

### The Prompt

```
@flow.assurance Check if a wet gas pipeline from platform to shore has
hydrate risk. The gas is 90% methane, 5% ethane, 3% propane, 2% CO2
at 120 bara. Pipeline is 80 km subsea at 4°C seabed temperature.
What concentration of MEG is needed for inhibition?
```

### What the Agent Does

1. **Creates CPA fluid** — Uses `SystemSrkCPAstatoil` because water + MEG
   are polar molecules requiring the CPA equation of state

2. **Calculates hydrate curve** — Sweeps pressure from 10 to 200 bara,
   finding the hydrate formation temperature at each pressure

3. **Compares with pipeline conditions** — The pipeline operates at 120 bara
   with arrival temperature near 4°C. If the hydrate temperature at
   120 bara is above 4°C, there is hydrate risk.

4. **Calculates MEG requirement** — Adds MEG at increasing concentrations
   (10%, 20%, 30% weight in aqueous phase) until the hydrate curve
   shifts below the minimum pipeline temperature

5. **Generates plots:**
   - Hydrate curve (P vs T) with pipeline operating point
   - MEG dosage sensitivity
   - Pipeline temperature profile

### Skills Loaded

- `neqsim-api-patterns` — CPA fluid creation, hydrate operation methods
- `neqsim-physics-explanations` — explains why hydrates form and how
  MEG inhibition works

### Output

```
Hydrate Analysis Results:
- Hydrate formation temperature at 120 bara: 18.5°C
- Pipeline minimum temperature: 4.0°C
- Subcooling: 14.5°C — SEVERE HYDRATE RISK

MEG Inhibition:
- 30 wt% MEG: hydrate T drops to 8.2°C — insufficient
- 40 wt% MEG: hydrate T drops to 2.1°C — SAFE (1.9°C margin)
- Recommended: 40 wt% MEG in water phase

Notebook saved with hydrate curve and MEG sensitivity plots.
```

---

## Example 4: Full Engineering Task with Report

**Difficulty:** Advanced | **Agent:** `@solve.task` | **Time:** 30-60 minutes

### The Prompt

```
@solve.task Design a TEG dehydration unit for a 50 MMSCFD wet natural gas
at 70 bara and 35°C. Target water dew point is -18°C per NORSOK P-001.
Include equipment sizing, TEG circulation rate, and reboiler duty.
Compare results against GPSA Engineering Data Book correlations.
```

### What the Agent Does

**Step 1 — Scope & Research**

1. Creates task folder: `task_solve/2026-XX-XX_teg_dehydration_design/`
2. Fills `task_spec.md`:
   - Design standard: NORSOK P-001
   - Reference: GPSA Engineering Data Book, 14th Ed.
   - Acceptance criteria: water content < 30 ppm, pressure drop < 0.5 bar
3. Researches TEG dehydration theory in `notes.md`

**Step 2 — Analysis & Evaluation**

4. Creates main simulation notebook:
   - CPA fluid with natural gas + water
   - TEG absorption column (SimpleTEGAbsorber or DistillationColumn)
   - TEG regeneration (reboiler + stripping gas option)
   - Runs simulation and extracts results

5. Creates benchmark notebook:
   - Compares water removal efficiency against GPSA Fig. 20-70
   - Validates TEG circulation rate against GPSA correlation
   - Creates parity plot (NeqSim vs GPSA)

6. Creates uncertainty notebook:
   - Varies inlet water content, temperature, TEG lean purity
   - Monte Carlo with N=200 iterations
   - Tornado diagram showing sensitivity ranking
   - Risk register (equipment, operational, commercial risks)

7. Saves `results.json` with all key numbers

**Step 3 — Report**

8. Runs `generate_report.py` to produce:
   - Word document (.docx) with professional formatting
   - HTML document with interactive navigation
   - All figures embedded and numbered
   - References cited
   - Benchmark validation table (PASS/FAIL)
   - Uncertainty P10/P50/P90 table
   - Risk register with color-coded severity

### Output Structure

```
task_solve/2026-XX-XX_teg_dehydration_design/
├── results.json
├── figures/
│   ├── teg_performance_vs_circulation_rate.png
│   ├── water_dew_point_sensitivity.png
│   ├── benchmark_parity_plot.png
│   ├── tornado_diagram.png
│   └── risk_matrix.png
├── step1_scope_and_research/
│   ├── task_spec.md
│   └── notes.md
├── step2_analysis/
│   ├── 01_teg_dehydration_design.ipynb
│   ├── 02_benchmark_validation.ipynb
│   └── 03_uncertainty_risk.ipynb
└── step3_report/
    ├── generate_report.py
    ├── TEG_Dehydration_Design_Report.docx
    └── TEG_Dehydration_Design_Report.html
```

---

## Example 5: Multi-Agent Composition

**Difficulty:** Advanced | **Agent:** `@neqsim.help` (routes to multiple) | **Time:** varies

### The Prompt

```
@neqsim.help I need to design a 20-inch gas export pipeline from an
offshore platform to shore. I need:
1. Steady-state hydraulics (pressure drop over 120 km)
2. Hydrate check and inhibitor requirement
3. Wall thickness per DNV-OS-F101
4. Cost estimation for pipeline and installation
```

### How the Router Composes the Pipeline

The router detects four sub-tasks spanning three disciplines:

```
Request Analysis:
├── Sub-task 1: Pipeline hydraulics     → @process.model
├── Sub-task 2: Hydrate check           → @flow.assurance
├── Sub-task 3: Wall thickness          → @mechanical.design
└── Sub-task 4: Cost estimation         → @mechanical.design
```

**Dependencies:**
- Sub-task 1 runs first (establishes P, T profile)
- Sub-tasks 2 and 3 depend on sub-task 1 results
- Sub-task 4 depends on sub-task 3 (wall thickness needed for cost)

The router sequences them and passes results via the `neqsim-agent-handoff`
skill schema.

### Execution Sequence

**Phase 1:** `@process.model` creates a `PipeBeggsAndBrills` or
`AdiabaticPipe` model for the 120 km pipeline. Output: pressure and
temperature at every point along the pipeline.

**Phase 2a:** `@flow.assurance` takes the P,T profile and overlays
the hydrate equilibrium curve. Determines if any point along the pipeline
enters the hydrate region. Calculates MEG dosage if needed.

**Phase 2b:** `@mechanical.design` takes the maximum operating pressure
and applies DNV-OS-F101 wall thickness calculation. Selects material grade
(API 5L X65), applies design factors, corrosion allowance.

**Phase 3:** `@mechanical.design` uses wall thickness, pipeline length,
material grade, and installation method to estimate costs using
NeqSim's SURF cost estimation classes.

### Combined Output

A unified summary with results from all four sub-tasks, cross-referenced
so that dependencies are explicit (e.g., "wall thickness of 22.3 mm based
on maximum pressure of 142 bara at inlet from hydraulic simulation").

---

## Example 6: Capability Gap Discovery

**Difficulty:** Advanced | **Agent:** `@capability.scout` | **Time:** minutes

### The Prompt

```
@capability.scout Can NeqSim handle HPHT (high pressure high temperature)
well design for a 280°C, 1200 bara reservoir with H2S and CO2?
```

### What the Scout Returns

```
Capability Assessment for HPHT Well Design:

✅ FULL COVERAGE:
  - H2S/CO2 phase behavior (CPA EOS, multiple mixing rules)
  - Well casing design (API 5C3, NORSOK D-010)
  - Corrosion assessment (DeWaardMilliams, NACE MR0175)
  - Cost estimation (WellCostEstimator, SURFCostEstimator)

⚠️  PARTIAL COVERAGE:
  - HPHT EOS accuracy: SRK/PR may have reduced accuracy above 200°C
    Recommendation: Use PR-Peneloux with validated BIPs for H2S/CO2
  - Thermal stress: Basic tubing stress via API 5C3, but no thermal
    cycling fatigue model

🔴 GAPS:
  - No dedicated HPHT material selection model (CRA vs carbon steel at 280°C)
    NIP: Create HPHTMaterialSelector class in process.mechanicaldesign

Recommended Agent Pipeline:
  1. @thermo.fluid — Create HPHT fluid with H2S, CO2 at extreme conditions
  2. @flow.assurance — Corrosion assessment at reservoir conditions
  3. @mechanical.design — Casing design with HPHT design factors
  4. @solve.task — Full study with uncertainty and reporting
```

---

## Example 7: Gas Quality Compliance

**Difficulty:** Beginner | **Agent:** `@gas.quality` | **Time:** seconds

### The Prompt

```
@gas.quality Check if this gas meets EN 16726 H-gas specification:
methane 89%, ethane 6%, propane 2.5%, CO2 1.5%, N2 1%
```

### What the Agent Does

1. Creates fluid with the given composition
2. Runs ISO 6976 calculations (superior heating value, Wobbe index,
   relative density)
3. Compares results against EN 16726 limits for H-gas

### Output

```
EN 16726 H-Gas Compliance Check:

| Property | Calculated | Limit | Status |
|----------|-----------|-------|--------|
| Wobbe Index | 51.2 MJ/m³ | 46.1 – 52.8 | PASS |
| Superior HV | 39.8 MJ/m³ | > 34.95 | PASS |
| Relative Density | 0.604 | < 0.700 | PASS |
| CO₂ | 1.5 mol% | < 2.5 | PASS |
| H₂S | 0 mg/m³ | < 5 | PASS |

Result: GAS MEETS EN 16726 H-GAS SPECIFICATION ✅
```

---

## Patterns for Effective Prompts

### Be Specific About Conditions

```
# Less effective:
@process.model Simulate a separator

# More effective:
@process.model Simulate a 3-phase separator at 70 bara and 80°C for a
gas-condensate with 95% methane, 3% propane, 1% n-hexane, 1% water
at 200,000 Sm3/day
```

### Mention Standards When Applicable

```
# Without standard:
@mechanical.design Calculate wall thickness for a 12-inch pipeline at 100 bara

# With standard (triggers deeper analysis):
@mechanical.design Calculate wall thickness for a 12-inch subsea pipeline
at 100 bara per DNV-OS-F101 with Equinor TR requirements
```

### Request Specific Deliverables

```
# Vague:
@solve.task Study TEG dehydration

# Specific:
@solve.task Design a TEG dehydration unit for 50 MMSCFD at 70 bara.
Target -18°C water dew point per NORSOK P-001. Deliver a notebook with
validation against GPSA and a Word report.
```

### Use the Scout for Unknowns

```
# Before starting a complex task:
@capability.scout Can NeqSim handle mercury removal from LNG feed gas
with activated carbon adsorption and mercury mass balance?
```

---

## Related Documentation

- [Introduction to Agentic Engineering](ai_agentic_programming_intro) — concepts and architecture
- [Agents and Skills Reference](ai_agents_reference) — complete catalog
- [Solve an Engineering Task Tutorial](../tutorials/solve-engineering-task) — hands-on guide
- [Task Solving Guide](../development/TASK_SOLVING_GUIDE) — developer workflow reference
- [Code Patterns](../development/CODE_PATTERNS) — copy-paste NeqSim code starters
- [Example Notebooks](../examples/index) — browse completed examples
