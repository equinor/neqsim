# NeqSim Copilot Skills

Skills are reusable knowledge packages that agents load automatically when relevant.
Each skill folder contains a `SKILL.md` file with verified patterns, rules, and domain knowledge.

## How Skills Work

1. An agent receives a user request
2. It identifies which skills are relevant based on the task description
3. It loads the `SKILL.md` file(s) using `read_file` before generating any response
4. The skill provides domain-specific patterns, rules, and code templates

**Skills are read-only** — agents consume them but don't modify them.

---

## Skill Index

| Skill | Description | Primary Agents |
|-------|-------------|----------------|
| `neqsim-agent-handoff` | Structured schemas for passing results between agents in multi-agent pipelines | router, solve.task |
| `neqsim-api-patterns` | Core NeqSim API patterns — EOS selection, fluid creation, flash, property access, equipment setup | All agents |
| `neqsim-capability-map` | Structured inventory of NeqSim capabilities by engineering discipline, gap identification | capability.scout, solve.task |
| `neqsim-ccs-hydrogen` | CO2 capture/transport/storage and hydrogen systems — phase behavior, impurity management, injection wells | ccs.hydrogen |
| `neqsim-distillation-design` | Distillation column design — solver selection, feed tray rules, convergence, internals sizing | process.model, solve.task |
| `neqsim-dynamic-simulation` | Dynamic/transient simulation — runTransient, PID controllers, transmitters, depressurization | control.system, safety.depressuring |
| `neqsim-electrolyte-systems` | Electrolyte/brine chemistry — CPA electrolyte EOS, ions, scale risk, MEG/DEG injection | thermo.fluid, flow.assurance |
| `neqsim-eos-regression` | EOS parameter regression — kij tuning, PVT matching (CME, CVD), C7+ characterization | pvt.simulation, thermo.fluid |
| `neqsim-field-development` | Field development workflows — concept selection, tieback analysis, production forecasting, lifecycle management | field.development |
| `neqsim-field-economics` | Oil & gas economics — NPV, IRR, cash flow, tax regimes (Norwegian NCS, UK), cost estimation, Monte Carlo | field.development, solve.task |
| `neqsim-flow-assurance` | Flow assurance — hydrate, wax, asphaltene, corrosion, pipeline hydraulics, inhibitor dosing | flow.assurance |
| `neqsim-input-validation` | Pre-simulation input validation — T, P, composition checks, component name verification | All simulation agents |
| `neqsim-java8-rules` | Java 8 compatibility rules — forbidden Java 9+ features, replacement patterns, JavaDoc requirements | neqsim.test, All Java-writing agents |
| `neqsim-notebook-patterns` | Jupyter notebook patterns — dual-boot setup, class imports, structure, visualization, results.json | notebook.example, solve.task, solve.process |
| `neqsim-physics-explanations` | Plain-language explanations of engineering phenomena for educational context | All agents (educational mode) |
| `neqsim-plant-data` | Plant historian integration — tagreader API (PI/IP.21), tag mapping, digital twin loops, data quality | plant.data |
| `neqsim-power-generation` | Power generation — gas turbines, steam turbines, HRSG, combined cycle, heat integration | process.model, solve.task |
| `neqsim-process-extraction` | Extract process data from text/tables/PFDs into NeqSim JSON builder format | extract.process |
| `neqsim-production-optimization` | Production optimization — decline curves, bottleneck analysis, gas lift, network optimization | field.development, solve.task |
| `neqsim-reaction-engineering` | Chemical reactor patterns — GibbsReactor, PFR, CSTR, kinetics, AnaerobicDigester, bioprocessing | reaction.engineering |
| `neqsim-regression-baselines` | Regression baseline management — creating fixtures, regression tests, detecting accuracy drift | neqsim.test |
| `neqsim-standards-lookup` | Industry standards lookup — equipment-to-standards mapping, CSV database queries, compliance tracking | mechanical.design, solve.task, gas.quality |
| `neqsim-stid-retriever` | Engineering document retrieval — local dirs, manual upload, pluggable backends, relevance filtering | technical.reader, solve.task |
| `neqsim-subsea-and-wells` | Subsea systems and well design — SURF cost estimation, casing design, tieback analysis | field.development, mechanical.design |
| `neqsim-technical-document-reading` | Technical document reading — PDF/Word/Excel extraction, P&ID topology, vendor datasheets, image analysis | technical.reader |
| `neqsim-troubleshooting` | Troubleshooting playbook — flash non-convergence, recycle divergence, zero values, phase ID issues | All simulation agents |
| `neqsim-unisim-reader` | UniSim/HYSYS conversion — COM reader, component/EOS mapping, topology reconstruction, sub-flowsheets | unisim.reader |

---

## Creating a New Skill

1. Create a folder: `.github/skills/neqsim-<skill-name>/`
2. Add a `SKILL.md` file with:
   - Clear title and description
   - "USE WHEN" trigger conditions
   - Domain-specific patterns, rules, and code templates
   - Common pitfalls and their fixes
3. Register the skill in `copilot-instructions.md` under the `<skills>` section
4. Update this README with the new entry
5. Reference the skill from relevant agent files

### Skill File Template

```markdown
# Skill Name

> **USE WHEN:** [describe trigger conditions]

## Quick Reference

[1-paragraph summary of what this skill provides]

## Patterns

### Pattern 1: [Name]
[Code template or rule]

### Pattern 2: [Name]
[Code template or rule]

## Common Pitfalls

| Issue | Cause | Fix |
|-------|-------|-----|
| ... | ... | ... |
```

---

## Cross-Reference Verification

Run the CI cross-reference script to verify all skill references in agent files
resolve to actual skill folders:

```bash
python devtools/verify_agent_skill_refs.py
```

This checks that:
- Every skill name referenced in agent files has a matching folder here
- Every skill folder here is referenced by at least one agent
- Every skill folder contains a `SKILL.md` file
