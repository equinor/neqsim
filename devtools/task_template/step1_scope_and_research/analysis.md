# Deep Analysis & Solution Design

This document provides the engineering analysis that informs the simulation work
in Step 2. It must be completed BEFORE writing any notebook code.

## 1. Physics & Theory Deep-Dive

### Governing Equations

<!-- List the key equations with derivations or references. Use LaTeX notation. -->

$$
[equation]
$$

Where:
- $variable$ = description [unit]

### Physical Mechanisms

<!-- Explain what causes the phenomenon, why it matters -->

### Thermodynamic Basis

<!-- Which EOS is appropriate and why. Phase behavior considerations. -->

### Transport Phenomena

<!-- Heat/mass transfer, fluid mechanics if relevant -->

### Key Assumptions

<!-- What simplifications are being made and their impact -->

| Assumption | Justification | Impact if Wrong |
|------------|---------------|-----------------|
| | | |

### Order-of-Magnitude Estimates

<!-- Quick hand calculations to set expectations BEFORE running simulations -->

| Estimate | Calculation | Expected Result |
|----------|-------------|-----------------|
| | | |

## 2. Alternative Solution Approaches

| Approach | Description | Pros | Cons | Recommended? |
|----------|-------------|------|------|--------------|
| A | | | | |
| B | | | | |
| C | | | | |

### Justification for Chosen Approach

<!-- Why is the recommended approach the best fit for this task? -->

## 3. NeqSim Capability Assessment

| Capability Needed | NeqSim Class/Method | Status | Gap Description |
|-------------------|---------------------|--------|-----------------|
| | | ✅ / ⚠️ / ❌ | |

**Legend:** ✅ Available | ⚠️ Partial (works with limitations) | ❌ Missing

**For every ❌ or ⚠️, write a NIP in `neqsim_improvements.md`.**

## 4. Solution Architecture

### Flowsheet / Calculation Sequence

<!-- Text-based diagram or table showing equipment/calculation order -->

```
[Input] → [Step 1] → [Step 2] → ... → [Output]
```

### Data Flow

<!-- What feeds into what, what outputs are needed at each step -->

### Iteration Strategy

<!-- Recycles, adjusters, convergence approach if applicable -->

### Parametric Studies Planned

| Parameter | Range | Why This Sweep | Expected Trend |
|-----------|-------|----------------|----------------|
| | | | |

### Expected Results (Pre-Simulation)

| Output | Expected Range | Basis for Estimate |
|--------|---------------|--------------------|
| | | From hand calc / literature / experience |

## 5. Risk & Failure Mode Analysis

| Risk | Type | Likelihood | Mitigation / Fallback |
|------|------|------------|----------------------|
| | Numerical / Physical / Data | High/Med/Low | |

## 6. Engineering Insight Questions

These questions must be explicitly answered in the report conclusions.
They drive the analysis beyond "what is the number" to "what does it mean."

1. ?
2. ?
3. ?
4. ?
5. ?

<!-- Add more questions as needed. Aim for 5-10 questions. -->

## 7. Overpressure / relief tasks only: existing wells, blocked outlet, scenario register and valve Cv

<!-- Delete this section unless the task sizes or verifies pressure protection for a new source on a separator/header that already has producers. -->

- [ ] Compare the new source with existing/previous wells and inputs to the receiving separator/header: riser/inlet pressure, SIWHP, rates, choke dP, what the PSV/relief basis was sized for, ratio new versus historical, effect on the failed-open / choke-collapse relief load.
- [ ] Check blocked outlet as a function of current inflow (not design inflow); state explicitly whether it governs (lower production reduces blocked outlet but not IVO+CC).
- [ ] Record both in results.json key existing_wells_comparison and in two report sections (see the neqsim-hp-lp-interface-overpressure skill, section 2b).
- [ ] Open the relief chapter with a scenario register: one row per scenario (failed-open / IVO with and without choke collapse, choke collapse alone, blocked outlet per separator/header, gas blow-by, PSV failure, fire, hydrate plug, cold JT) with study status (calculated / simple estimate / qualitative), valve states (open, closed, failed), limiting mechanism, relief rate and governing yes/no (results.json key scenario_register).
- [ ] Add an installed-Cv input table: every flow-limiting valve (subsea and topside chokes intact and collapsed, riser EV, PV and its maximum opening, crossover valves, PSV orifice and set pressure, stroke times, volumes) with value, unit, source (document, revision, page, or ASSUMED) and quality grade (results.json key valve_cv_inputs). Fetch real Cv read-only from datasheets and sizing calculations; list exact blockers.
- [ ] Run a Cv sensitivity for failed-open AND blocked outlet (script, executed notebook, figure, table; results.json key cv_sensitivity) and state what the relief rate depends on (installed choke Cv, actual dP, stored inventory) and what it does not. Add a short "what the report can and cannot be used for" statement (section 2c of the skill).
