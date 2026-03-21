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

**Examples:**
```
@neqsim.help I need to size a pipeline and check for hydrates
@capability.scout Can NeqSim handle acid gas injection with H2S corrosion and well design?
@capability.scout TEG dehydration with BTEX emissions and cost estimation
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

### API Changelog

See `CHANGELOG_AGENT_NOTES.md` in the repo root for recent API changes,
new classes, deprecated methods, and migration guidance.
| **Mechanical** | Design per codes, material selection, cost | Detailed FEA or stress analysis |

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
