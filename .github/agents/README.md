# NeqSim Copilot Agents

This directory contains specialized GitHub Copilot Chat agents for NeqSim development.

## Quick Start

In VS Code Copilot Chat, type `@<agent-name>` followed by your request:

```
@solve.task 3-stage compression with intercooling from 5 to 150 bara
```

---

## Available Agents

### Routing & Help

| Agent | Command | Purpose |
|-------|---------|----------|
| **neqsim.help** | `@neqsim.help <description>` | **Routes requests** to the right specialist agent. Use when unsure which agent to pick. |
| **capability.scout** | `@capability.scout <description>` | **Assesses capabilities** needed for a task, checks NeqSim coverage, identifies gaps, plans implementations, recommends skills. |
| **literature.scout** | `@literature.scout <topic>` | **Pulls papers, standards, and internal docs** into `step1_scope_and_research/references/`, writes a manifest, summarises into `notes.md`. |
| **review** | `@review <task folder>` | **Pre-PR quality gate** — runs schema validator, consistency checker, capability/figure-traceability/repo-memory audits. Read-only. |

**Examples:**
```
@neqsim.help I need to size a pipeline and check for hydrates
@capability.scout Can NeqSim handle acid gas injection with H2S corrosion and well design?
@literature.scout wax inhibitor injection for subsea tieback
@review task_solve/2026-04-26_co2_pipeline_sizing/
```

---

### Task Solving & Workflows

| Agent | Command | Purpose |
|-------|---------|---------|
| **solve.task** | `@solve.task <description>` | **End-to-end task solving** with report generation (3-step workflow) |
| **solve.process** | `@solve.process <description>` | **Quick process simulation** → working notebook (skips formal reporting) |

**Examples:**
```
@solve.task JT cooling for rich gas at 100 bara
@solve.task field development NPV per NORSOK Z-013
@solve.process TEG dehydration for 50 MMSCFD wet gas
```

---

### Field Development

| Agent | Command | Purpose |
|-------|---------|---------|
| **field.development** | `@field.development <description>` | Full field development studies — concept screening, tieback analysis, production forecasting, NPV/IRR economics, subsea design, well planning, risk assessment |

**Examples:**
```
@field.development Evaluate subsea tieback vs FPSO for 100 MMboe gas condensate at 350m water depth
@field.development Production forecast and NPV for 4-well subsea development on Norwegian NCS
@field.development Screen tieback options for satellite gas field 25 km from host platform
@field.development Concept selection for deepwater oil development in 800m water depth
@field.development Late-life IOR screening for mature oil field with 60% water cut
```

---

### Thermodynamics & Fluids

| Agent | Command | Purpose |
|-------|---------|---------|
| **thermo.fluid** | `@thermo.fluid <description>` | Create thermodynamic fluid systems (EOS, components, flash, properties) |

**Examples:**
```
@thermo.fluid Create a CO2-rich fluid at 200 bara and 25°C
@thermo.fluid Phase envelope for natural gas with 15% CO2
@thermo.fluid Density and viscosity of MEG-water at -10°C
```

---

### Process Simulation

| Agent | Command | Purpose |
|-------|---------|---------|
| **process.model** | `@process.model <description>` | Build process simulations (separators, compressors, heat exchangers, flowsheets) |

**Examples:**
```
@process.model 3-stage compression train with intercooling
@process.model HP/LP separator train with export compressor
@process.model Complete GOSP (gas-oil separation plant)
```

---

### PVT Analysis

| Agent | Command | Purpose |
|-------|---------|---------|
| **pvt.simulation** | `@pvt.simulation <description>` | Run PVT lab tests (CME, CVD, DL, separator tests, swelling) |

**Examples:**
```
@pvt.simulation CME test at 100°C from 400 to 50 bara
@pvt.simulation CVD for black oil with C7+ characterization
@pvt.simulation Separator test at 3 stage: 50/15/2 bara
```

---

### Flow Assurance

| Agent | Command | Purpose |
|-------|---------|---------|
| **flow.assurance** | `@flow.assurance <description>` | Hydrate prediction, wax, asphaltene, corrosion, pipeline pressure drop |

**Examples:**
```
@flow.assurance Hydrate formation temperature for wet gas at 100 bara
@flow.assurance Wax appearance temperature for crude oil
@flow.assurance Pipeline pressure drop for 50 km subsea line
@flow.assurance CO2 corrosion rate per NORSOK M-506
```

---

### Standards & Gas Quality

| Agent | Command | Purpose |
|-------|---------|---------|
| **gas.quality** | `@gas.quality <description>` | ISO 6976 calorific values, gas quality specs, sales contracts |

**Examples:**
```
@gas.quality Calculate Wobbe index per ISO 6976
@gas.quality Check if gas meets EN 16726 H-gas spec
@gas.quality Hydrocarbon dew point at 30 bara per ISO 23874
```

---

### Mechanical Design & Sizing

| Agent | Command | Purpose |
|-------|---------|---------|
| **mechanical.design** | `@mechanical.design <description>` | ASME/API/DNV mechanical design (wall thickness, material selection, cost) |

**Examples:**
```
@mechanical.design 20-inch export pipeline per DNV-OS-F101
@mechanical.design HP separator vessel per ASME VIII Div.1
@mechanical.design Subsea well casing design with NORSOK D-010
```

---

### Safety & Depressurization

| Agent | Command | Purpose |
|-------|---------|---------|
| **safety.depressuring** | `@safety.depressuring <description>` | Blowdown, relief valve sizing, fire case, source terms |

**Examples:**
```
@safety.depressuring Fire-case blowdown for HP separator at 85 bara
@safety.depressuring Size PSV for blocked outlet on gas cooler
@safety.depressuring Generate source term for 2-inch gas leak at 120 bara
```

---

### Plant Data & Digital Twins

| Agent | Command | Purpose |
|-------|---------|--------|
| **plant.data** | `@plant.data <description>` | Connect NeqSim models to plant historian data (PI/IP.21) via tagreader |

**Examples:**
```
@plant.data Connect compressor model to PI historian tags
@plant.data Compare separator simulation to plant data
@plant.data Build a digital twin loop for a gas compression train
@plant.data Read Troll A compressor data from Aspen IP.21
```

---

### Testing & Examples

| Agent | Command | Purpose |
|-------|---------|---------|
| **neqsim.test** | `@neqsim.test <description>` | Create JUnit 5 unit tests for NeqSim code |
| **notebook.example** | `@notebook.example <description>` | Create Jupyter notebook examples |

**Examples:**
```
@neqsim.test Write tests for the new compressor anti-surge logic
@notebook.example TEG dehydration with results visualization
```

---

### CCS & Hydrogen

| Agent | Command | Purpose |
|-------|---------|---------|
| **ccs.hydrogen** | `@ccs.hydrogen <description>` | CO2 capture/transport/storage and hydrogen systems — phase behavior with impurities, dense phase pipeline design, injection well analysis, H2 blending |

**Examples:**
```
@ccs.hydrogen CO2 pipeline design for 5 Mt/yr with 2% N2 impurity
@ccs.hydrogen Injection well safety analysis for CO2 with H2 impurity
@ccs.hydrogen Hydrogen blending impact on gas network Wobbe index
@ccs.hydrogen Full CCS chain from capture to injection
```

---

### Reaction Engineering

| Agent | Command | Purpose |
|-------|---------|---------|
| **reaction.engineering** | `@reaction.engineering <description>` | Chemical reactor design — equilibrium (Gibbs), kinetic PFR/CSTR, catalyst beds, conversion analysis, reactor sizing |

**Examples:**
```
@reaction.engineering Steam methane reforming at 850°C and 30 bar
@reaction.engineering Ammonia synthesis reactor with Fe catalyst
@reaction.engineering Claus reactor for sulfur recovery
@reaction.engineering Water-gas shift reactor downstream of gasifier
```

---

### Emissions & Environmental

| Agent | Command | Purpose |
|-------|---------|---------|
| **emissions.environmental** | `@emissions.environmental <description>` | GHG emissions, flaring/venting, carbon intensity, regulatory reporting (EU ETS, Norwegian CO2 tax), ESG metrics |

**Examples:**
```
@emissions.environmental CO2 emissions from gas turbine compressor driver
@emissions.environmental Flare gas inventory for HP/LP separation
@emissions.environmental Carbon intensity of LNG production
@emissions.environmental Methane slip from gas engine power generation
```

---

### Control Systems

| Agent | Command | Purpose |
|-------|---------|---------|
| **control.system** | `@control.system <description>` | PID controller design and tuning, control loop architecture, measurement device selection, alarm/trip configuration, control narratives |

**Examples:**
```
@control.system Design level control for HP separator with 2m diameter
@control.system Tune pressure controller for gas export compressor
@control.system Cascade temperature control for heat exchanger
@control.system Generate control narrative for 3-stage separation
```

---

### Engineering Deliverables

| Agent | Command | Purpose |
|-------|---------|---------|
| **engineering.deliverables** | `@engineering.deliverables <description>` | PFDs, thermal utility summaries, instrument schedules, fire/noise assessments, spare parts inventories |

**Examples:**
```
@engineering.deliverables Generate Class A deliverables for HP/LP separation train
@engineering.deliverables Produce Class B deliverables for subsea tieback concept
@engineering.deliverables Full FEED deliverable package for gas compression facility
```

---

### Process Extraction

| Agent | Command | Purpose |
|-------|---------|---------|
| **extract.process** | `@extract.process <description>` | Extract process info from text, PFDs, or data sheets and convert to NeqSim JSON / ProcessModule |

**Examples:**
```
@extract.process "Feed gas at 80 bara, 40°C → cooler to 15°C → separator → compress to 120 bara"
@extract.process Build a NeqSim model from this heat and mass balance table
@extract.process Convert this PFD description into a running simulation
```

---

### UniSim / HYSYS Conversion

| Agent | Command | Purpose |
|-------|---------|---------|
| **unisim.reader** | `@unisim.reader <description>` | Read UniSim Design / Aspen HYSYS .usc files via COM and convert to NeqSim models |

**Examples:**
```
@unisim.reader Read C:\Models\GasPlant.usc and build a NeqSim model
@unisim.reader Convert all UniSim cases in C:\Cases\ to NeqSim
@unisim.reader Compare UniSim and NeqSim results for a platform model
```

---

### Technical Document Reading

| Agent | Command | Purpose |
|-------|---------|---------|
| **technical.reader** | `@technical.reader <description>` | Extract structured engineering data from PDFs, Word docs, Excel files (data sheets, design basis, TRs, stream tables, inspection reports) |

**Examples:**
```
@technical.reader Read this design basis PDF and extract feed gas composition
@technical.reader Parse the equipment data sheet for separator V-100
@technical.reader Extract stream table from the heat & mass balance Excel
@technical.reader Pull requirements from this technical requirement document
@technical.reader Extract wall thickness data from the inspection report
```

---

### Documentation

| Agent | Command | Purpose |
|-------|---------|---------|
| **documentation** | `@documentation <description>` | Write/update markdown docs, API references, cookbook recipes |

**Examples:**
```
@documentation Create a guide for pipeline sizing calculations
@documentation Update the distillation column reference with examples
@documentation Add a cookbook recipe for MEG regeneration
```

---

## Agent Architecture

Each agent is a specialized prompt that:
1. **Reads context** from CONTEXT.md, AGENTS.md, and relevant docs
2. **Validates inputs** against NeqSim capabilities
3. **Generates code** following established patterns (CODE_PATTERNS.md)
4. **Verifies outputs** (compilation, tests, validation)
5. **Documents results** (JavaDoc, markdown, notebook comments)

### Agent Capabilities by Domain

| Domain | Can Do | Cannot Do |
|--------|--------|-----------|
| **Thermodynamics** | Create fluids, run flash, read properties, characterize oil | Modify EOS internals (requires Java changes) |
| **Process** | Build complete flowsheets, run simulations, size equipment | Real-time process control (use NeqSim-Live) |
| **PVT** | All standard lab tests, parameter fitting | Non-standard experiments (add to NeqSim first) |
| **Standards** | Calculations per ISO/API/NORSOK | Legal interpretation of standards |
| **Document Reading** | Extract data from PDF, Word, Excel (data sheets, TRs, stream tables, inspection reports) | OCR of scanned diagrams, reading proprietary CAD formats |
| **Mechanical** | Design per codes, material selection, cost | Detailed FEA or stress analysis |
| **CCS & Hydrogen** | CO2 phase envelopes, dense phase transport, injection wells, H2 blending | Geological storage simulation, reservoir composition tracking |
| **Reactions** | Equilibrium (Gibbs), kinetic PFR/CSTR, catalyst beds | Detailed reaction mechanism fitting, CFD reactor modeling |
| **Emissions** | GHG inventories, flaring calculations, carbon intensity | Atmospheric dispersion modeling, regulatory submissions |
| **Control Systems** | PID tuning, control loop design, alarm configuration | DCS/SIS programming, SIL verification calculations |
| **UniSim Conversion** | Read .usc via COM, convert to NeqSim ProcessSystem, compare results | Modify UniSim files, convert from Aspen Plus |

---

## Skills Reference

Skills are reusable knowledge packages that agents load automatically when relevant.
They contain verified patterns, rules, and domain knowledge.

| Skill | When Loaded | Purpose |
|-------|------------|----------|
| `neqsim-api-patterns` | Writing Java/Python code using NeqSim | EOS selection, fluid creation, flash, property access, equipment patterns |
| `neqsim-java8-rules` | Writing or reviewing any Java code | Forbidden Java 9+ features, replacement patterns, JavaDoc requirements |
| `neqsim-notebook-patterns` | Creating Jupyter notebooks | Dual-boot setup, class imports, notebook structure, visualization, performance estimation |
| `neqsim-troubleshooting` | Simulation fails or produces unexpected results | Flash non-convergence, recycle divergence, zero values, phase ID issues |
| `neqsim-input-validation` | Setting up simulations | Pre-simulation checks for T, P, composition, component names, EOS selection |
| `neqsim-regression-baselines` | Modifying solver logic or correlations | Creating baseline fixtures, regression tests, detecting accuracy drift |
| `neqsim-agent-handoff` | Multi-agent pipelines | Structured schemas for passing results between agents |
| `neqsim-physics-explanations` | Explaining results or adding educational context | Plain-language explanations of thermodynamic and process phenomena |
| `neqsim-capability-map` | Checking what NeqSim can do, planning implementations | Structured inventory of all NeqSim capabilities by discipline |
| `neqsim-technical-document-reading` | Reading technical documents (PDF, Word, Excel) | Extraction patterns, unit normalization, component mapping, quality scoring |
| `neqsim-trapped-liquid-fire-rupture` | Blocked-in liquid fire rupture studies | Evidence retrieval, trapped inventory, fire exposure, material/flange derating, PFP demand, source-term handoff |
| `neqsim-ccs-hydrogen` | CCS or hydrogen system tasks | CO2 phase behavior, impurity management, injection wells, H2 blending |
| `neqsim-distillation-design` | Distillation column setup or troubleshooting | Solver selection, feed tray rules, convergence, internals sizing |
| `neqsim-dynamic-simulation` | Transient simulations, controller tuning | runTransient, PID controllers, transmitters, depressurization |
| `neqsim-electrolyte-systems` | Brine, MEG, ions, or scale prediction | SystemElectrolyteCPAstatoil, ion components, scale risk |
| `neqsim-eos-regression` | Fitting EOS to experimental data | kij tuning, PVT matching (CME, CVD), C7+ characterization |
| `neqsim-field-development` | Field development studies | Concept selection, tieback analysis, production forecasting, lifecycle |
| `neqsim-field-economics` | NPV, IRR, cash flow, tax regimes | Norwegian NCS, UK fiscal, cost estimation, Monte Carlo |
| `neqsim-flow-assurance` | Hydrate, wax, corrosion, pipeline hydraulics | All flow assurance threats with NeqSim code patterns |
| `neqsim-plant-data` | Connecting to plant historian data | Tagreader API, tag mapping, digital twin loops, data quality |
| `neqsim-power-generation` | Gas/steam turbines, HRSG, combined cycle | Equipment patterns, efficiency calculations, heat integration |
| `neqsim-process-extraction` | Extracting process data from text or tables | Equipment mapping, stream wiring, unit conversion, JSON builder |
| `neqsim-production-optimization` | Production optimization, bottleneck analysis | Decline curves, gas lift, network optimization |
| `neqsim-reaction-engineering` | Chemical reactor modeling | GibbsReactor, PFR, CSTR, kinetics, AnaerobicDigester |
| `neqsim-standards-lookup` | Standards compliance or lookup | Equipment-to-standards mapping, CSV database queries |
| `neqsim-stid-retriever` | Retrieving vendor/engineering documents | Local dirs, manual upload, retrieval backends, relevance filtering |
| `neqsim-subsea-and-wells` | Subsea systems, well design, SURF cost | Casing design, tieback analysis, cost estimation |
| `neqsim-unisim-reader` | UniSim/HYSYS conversion tasks | COM reader, component/EOS mapping, topology reconstruction |
| `neqsim-controllability-operability` | Operability, turndown, control valve sizing | ISA-75 valve sizing, startup/shutdown, recycle stability |
| `neqsim-equipment-cost-estimation` | Equipment-level CAPEX (AACE Class 3/4) | Turton/Peters/Ulrich correlations, CEPCI escalation, material/pressure factors |
| `neqsim-heat-integration` | Pinch analysis, heat-recovery, MER targeting | Composite curves, ΔTmin selection, HEN synthesis, retrofit |
| `neqsim-model-calibration-and-data-reconciliation` | Tuning models against plant data | Bounded optimization, residual diagnostics, train/validation reports |
| `neqsim-platform-modeling` | Topside platform process modeling | Multi-stage separation, recompression, anti-surge, scrubber recycles |
| `neqsim-process-safety` | HAZOP, LOPA, SIL, bow-tie, risk matrix | IEC 61508/61511, NORSOK Z-013, quantitative risk evaluation |
| `neqsim-relief-flare-network` | PSV sizing, flare loads, radiation | API 520/521/537, fire case, header back-pressure & Mach |
| `neqsim-utilities-specification` | Steam, cooling water, instrument air, fuel gas | NORSOK U-001, ISA-7.0.01, utility duty consolidation |

### API Changelog

See `CHANGELOG_AGENT_NOTES.md` in the repo root for recent API changes,
new classes, deprecated methods, and migration guidance.

---

## Best Practices

### 1. Be Specific
❌ `@solve.task compressor`
✅ `@solve.task 3-stage gas compression from 5 to 150 bara with intercooling to 40°C`

### 2. Mention Standards When Applicable
❌ `@mechanical.design pipeline`
✅ `@mechanical.design 20-inch pipeline at 150 bara per DNV-OS-F101`

### 3. Provide Context
❌ `@flow.assurance hydrate`
✅ `@flow.assurance hydrate formation temperature for wet gas (5% water) at 100 bara`

### 4. Combine Agents for Complex Tasks
```
# Step 1: Create fluid
@thermo.fluid Reservoir gas with 85% CH4, 10% C2, 5% C3 at 250 bara

# Step 2: Build process
@process.model 3-stage separation train using the fluid above

# Step 3: Flow assurance check
@flow.assurance Check for hydrate risk in the separator train

# Step 4: Generate full report
@solve.task Complete the above work as a formal study with report
```

---

## Customizing Agents

Agents are markdown files with YAML frontmatter. To modify or create an agent:

1. **Copy an existing agent** as a template
2. **Edit the prompt** sections
3. **Test with sample requests**
4. **Submit PR** if useful for others

See [agent-customization](https://code.visualstudio.com/docs/copilot/copilot-customization) skill for help.

---

## Troubleshooting

### Agent Not Responding
- Check VS Code Copilot is enabled (bottom right status bar)
- Verify agent name spelling: `@solve.task` not `@task.solve`
- Try shorter request first, then elaborate

### Wrong Output
- Be more specific in request (mention standards, units, conditions)
- Provide example input/output if available
- Ask agent to read specific docs: "Read docs/process/separators.md first"

### Agent Suggests Wrong API
- Report as issue (agent may have outdated patterns)
- Verify in actual code: `file_search("**/ClassName.java")`
- Correct the agent and submit PR

---

## See Also

- **[AGENTS.md](../../AGENTS.md)** - Agent instructions for external AI tools (OpenAI Codex, Claude)
- **[copilot-instructions.md](../copilot-instructions.md)** - Global Copilot settings for this repo
- **[TASK_SOLVING_GUIDE.md](../../docs/development/TASK_SOLVING_GUIDE.md)** - Full workflow documentation
- **[CODE_PATTERNS.md](../../docs/development/CODE_PATTERNS.md)** - Copy-paste code starters

---

**Questions or issues?** Open a [GitHub Discussion](https://github.com/equinor/neqsim/discussions) or submit an issue.
