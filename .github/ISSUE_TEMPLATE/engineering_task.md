---
name: Engineering task (AI-assisted simulation)
about: Create an engineering task for Copilot to solve with NeqSim simulations
title: ''
labels: copilot-engineering
assignees: ''
---

<!--
  ENGINEERING TASK — Copilot will:
    ✅ Build NeqSim from source and set up Python
    ✅ Create task folder (task_solve/) with scope and notes
    ✅ Write Jupyter notebooks with NeqSim simulations
    ✅ Run simulations, generate figures and results
    ✅ Write Java tests for any new NeqSim classes
    ✅ Copy reusable outputs (notebooks, tests, docs) to tracked paths
    ✅ Open a PR with the deliverables

  For simple code changes (no simulation needed), use the
  "Copilot task (AI-assisted)" template with label 'copilot-solve' instead.
-->

## Engineering Problem

<!-- Describe the engineering problem to solve. Be specific about what you need calculated. -->
<!-- Examples: "Calculate hydrate formation temperature for wet gas at 100 bara",
     "Size a TEG dehydration unit for 50 MMSCFD", "Pipeline pressure drop for 50 km subsea line" -->



## Fluid / System

<!-- Describe the fluid composition or system. Include mole fractions if known. -->

- **Fluid type:** <!-- e.g., lean gas, rich gas, oil, condensate, CO2 stream -->
- **Composition:** <!-- e.g., methane 0.85, ethane 0.10, propane 0.05 -->
- **Conditions:** <!-- e.g., 60 bara, 25°C, 100 kg/hr -->

## Requirements

<!-- What outputs are needed? -->

- [ ]
- [ ]
- [ ] Jupyter notebook with working simulation
- [ ] Results validated against acceptance criteria
- [ ] Figures with axis labels and units

## Standards / Constraints

<!-- Which design codes, standards, or operating constraints apply? -->
<!-- e.g., NORSOK, DNV, API, ASME, temperature limits, pressure limits -->



## Acceptance Criteria

<!-- How do you know the result is correct? Reference values, expected ranges, etc. -->

- Expected range: <!-- e.g., hydrate temperature between 15-25°C -->
- Validation method: <!-- e.g., compare with HYSYS results, published data -->

## Additional Context

<!-- Links to references, similar analyses, related issues, or prior work. -->

