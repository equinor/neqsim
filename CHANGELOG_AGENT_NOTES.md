# NeqSim API Changelog — Agent Notes

> **Purpose:** Track API changes that affect agent instructions, code patterns,
> and existing examples. Agents read this file to stay aware of breaking changes,
> deprecated methods, and new capabilities.
>
> Format: most recent changes at the top. Include the date, what changed,
> migration steps, and which agents/skills need updating.

---

## 2026-03-21 — Capability Scout Agent and Capability Map Skill

### New Agent
- **`@capability.scout`** — Analyzes engineering tasks, identifies required capabilities,
  checks NeqSim coverage, identifies gaps, writes NIPs, recommends skills and agent pipelines.
  Use before starting complex multi-discipline tasks.

### New Skill
| Skill | Purpose |
|-------|---------|
| `neqsim-capability-map` | Structured inventory of all NeqSim capabilities by discipline (EOS, equipment, PVT, standards, mechanical design, flow assurance, safety, economics) |

### Updated Files
- `solve.task.agent.md` — Phase 1.5 Section 7b.3 now recommends invoking `@capability.scout` for comprehensive tasks
- `router.agent.md` — Added capability scout to routing table and Pattern 6 (Capability Assessment + Implementation)
- `README.md` — Added capability scout to Routing & Help section and capability-map to Skills table
- `AGENTS.md` — Added capability scout to Key Paths and capability-map to Skills Reference
- `CONTEXT.md` — Updated agent count to 14, skill count to 9
- `copilot-instructions.md` — Added Capability Assessment bullet point

---

## 2026-03-21 — Agent Ecosystem v2: Router, Skills, Validation

### New Agents
- **`@neqsim.help` (router agent)** — Routes requests to specialist agents. Use when unsure which agent to pick.

### New Skills (6 added)
| Skill | Purpose |
|-------|---------|
| `neqsim-troubleshooting` | Recovery strategies for convergence failures, zero values, phase issues |
| `neqsim-input-validation` | Pre-simulation input checks (T, P, composition, component names, order of operations) |
| `neqsim-regression-baselines` | Baseline management for preventing silent accuracy drift |
| `neqsim-agent-handoff` | Structured schemas for agent-to-agent result passing |
| `neqsim-physics-explanations` | Plain-language explanations of thermodynamic and process phenomena |
| `neqsim-performance-guide` | Simulation time estimates and optimization strategies (in notebook-patterns skill) |

### Updated Files
- `solve.task.agent.md` — Added auto-search past solutions (Phase 0, Step 1.5) and cross-discipline consistency gate
- `README.md` — Updated with new router agent, skills table, and cross-references
- `neqsim-notebook-patterns/SKILL.md` — Added performance estimation table and optimization tips

---

## 2026-03-14 — Fix IEC 60534 Gas Valve Sizing

### Changed
- `ControlValveSizing_IEC_60534.java` — Gas valve Cv now uses standard volumetric flow instead of actual
- `ControlValveSizing_IEC_60534_full.java` — Same fix applied to full version

### Migration
- If you have code using `sizeControlValveGas()`, results will now be correct (previously ~98% too low at 50 bara)
- No API change — same methods, corrected internal calculations

---

## 2026-03-10 — Process Architecture Improvements

### New APIs
| API | Class | Description |
|-----|-------|-------------|
| `getInletStreams()` | `ProcessEquipmentInterface` | Returns list of inlet streams for any equipment |
| `getOutletStreams()` | `ProcessEquipmentInterface` | Returns list of outlet streams for any equipment |
| `addController(tag, ctrl)` | `ProcessEquipmentBaseClass` | Attach named controller to equipment |
| `getController(tag)` | `ProcessEquipmentBaseClass` | Retrieve controller by tag name |
| `getControllers()` | `ProcessEquipmentBaseClass` | Get all named controllers as map |
| `connect(src, dst, type, label)` | `ProcessSystem` | Record typed connection metadata |
| `getConnections()` | `ProcessSystem` | Query all recorded connections |
| `getAllElements()` | `ProcessSystem` | Get all equipment, controllers, and measurements |

### New Classes
| Class | Package | Purpose |
|-------|---------|---------|
| `ProcessElementInterface` | `process` | Unified supertype for equipment, controllers, measurements |
| `MultiPortEquipment` | `process.equipment` | Abstract base for multi-inlet/outlet equipment |
| `ProcessConnection` | `process.processmodel` | Typed connection metadata (MATERIAL/ENERGY/SIGNAL) |

### Migration
- **Backward compatible** — all existing code continues to work
- Legacy `setController()`/`getController()` still work alongside named controllers
- `getInletStreams()`/`getOutletStreams()` default to empty lists for classes that don't override

---

## 2026-03-09 — CO2 Corrosion Analyzer

### New Classes
| Class | Package | Purpose |
|-------|---------|---------|
| `CO2CorrosionAnalyzer` | `pvtsimulation.flowassurance` | Couples electrolyte CPA EOS with de Waard-Milliams corrosion model |

### Important Note
Must call `chemicalReactionInit()` before `createDatabase(true)` and `setMixingRule(10)` to enable aqueous chemical equilibrium. Without this, pH returns 7.0 (neutral) because H3O+ is not generated.

---

## Pre-2026 — Stable API Reference

### Key Methods (Unchanged)
These core methods have been stable for years and are safe to use:
- `SystemInterface.addComponent(name, moleFraction)`
- `SystemInterface.setMixingRule(rule)`
- `SystemInterface.initProperties()`
- `ThermodynamicOperations.TPflash()` / `PHflash()` / `PSflash()`
- `Stream.setFlowRate(value, unit)` / `setTemperature(value, unit)` / `setPressure(value, unit)`
- `ProcessSystem.add(equipment)` / `run()`
- `Separator.getGasOutStream()` / `getLiquidOutStream()`
- `Compressor.setOutletPressure(value)` / `getPower(unit)`

### Known Method Name Corrections
| Wrong Name (Don't Use) | Correct Name |
|-------------------------|-------------|
| `getUnitOperation("name")` | `getUnit("name")` |
| `characterise()` | `characterisePlusFraction()` |
| `characterize()` | `characterisePlusFraction()` |
| `Optional.isEmpty()` | `!optional.isPresent()` (Java 8) |
