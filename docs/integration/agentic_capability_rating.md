---
title: "Agentic Process Simulation — Capability Rating"
description: Dimensional capability rating for NeqSim's agentic process simulation stack after the Agentic Process Engineering v1 release (typed write validators + rollback, SeparationDuty + FlowsheetSynthesisEngine, dynamics infrastructure wired into runTransient).
---

# Agentic Process Simulation — Capability Rating

**Date:** 2026-05-30
**Scope:** `neqsim.process.{automation, dynamics, synthesis, processmodel}` and `neqsim.util.agentic`.
**Baseline:** pre-v1 (before commits `6eed8f50b4`, `893c7ccb70`, `baf2a01f93`, `f10b0df025`).

## Overall: **7.5 / 10**

Production-ready for read/write and steady-state agents; dynamics and synthesis are MVP.

## Dimensional scorecard

| # | Dimension | Score | State |
|---|-----------|-------|-------|
| 1 | Discovery & introspection | 9/10 | `describe()`, `getTopology()`, `getNeighbors()`, `snapshot()`, `getVariableList()`, `getAreaList()` all return JSON with `SCHEMA_VERSION=1.0`. Agents navigate any flowsheet without Java reflection. |
| 2 | Read path (typed + structured) | 9/10 | `getVariableValue`, `getStructured` (composition/k-values), `getValues` batch, `validateAddress`, `getAllowedUnits`, fuzzy auto-correction. |
| 3 | Write path (typed + transactional) | 9/10 | **v1**: `WriteValidatorRegistry` + `DefaultWriteValidators` (range, type, read-only, unit conversion). `setValuesWithRollback` is true transactional — reverts all on first failure. `getWriteHistory()` audit log. |
| 4 | Self-healing & learning | 8/10 | `AutomationDiagnostics`: fuzzy name match (edit-dist ≤2), learned-correction cache, physical-bounds validation, thread-safe history. |
| 5 | Steady-state run loop | 8/10 | `runIfDirty()`, `setVariableValueAndRun()`, dirty flag, cached facade across turns. Recycle/adjuster orchestration solid. |
| 6 | Dynamic simulation | 6/10 | **v1**: pluggable `IntegratorStrategy` (Explicit Euler + BDF-1 with FD Jacobian + fallback flag) wired into `ProcessSystem.runTransient` and `ProcessModel.runTransient`. `EventScheduler` for ESD/setpoint events. 3 new measurement devices. |
| 7 | Flowsheet synthesis | 5/10 | **v1**: `SeparationDuty` + `FlowsheetSynthesisEngine` + `FlowsheetProposal` rank topologies on TAC / recovery / energy. MVP — separation duty only. |
| 8 | Quality gates & validation | 8/10 | `TaskResultValidator` (Java + Python mirror), `SimulationQualityGate`, `AgentBenchmarkSuite`, `AgentFeedbackCollector`, `AgentSession`. CI gate in `task_quality_gate.yml`. |
| 9 | State lifecycle & reproducibility | 8/10 | `ProcessSystemState` / `ProcessModelState` — JSON snapshot, save/load, `validate()`, `compare()` with `ModelDiff`, gzip transfer. Git-diffable. |
| 10 | Skill / agent ecosystem | 9/10 | 45+ skills, 30+ agents, semantic skill search, CI-enforced agent↔skill mapping. |
| 11 | Multi-area orchestration | 7/10 | `ProcessModel` with area-qualified addresses (`Area::Unit.property`), scheduler/integrator propagation, plant-wide `getAutomation()`. |
| 12 | Documentation & onboarding | 8/10 | `AGENTS.md`, `copilot-instructions.md`, `CHANGELOG_AGENT_NOTES.md`, `TASK_LOG.md`, dynamic-simulation skill all current. |

## Before vs. after v1

| Layer | Pre-v1 | Post-v1 |
|-------|--------|---------|
| Read agents | 8 | 9 |
| Write agents (mutating control) | 5 | 9 |
| Dynamic / event-driven agents | 3 | 6 |
| Design / synthesis agents | 2 | 5 |
| Reproducibility / governance | 7 | 8 |
| **Weighted overall** | **5.7** | **7.5** |

## Top remaining gaps (priority order)

1. ~~**Adaptive / higher-order integrator**~~ — **ADDRESSED in v1.1** by `RK4Integrator` (fixed-step classical RK4) and `AdaptiveRK45Integrator` (Cash–Karp 5(4) with adaptive sub-stepping and tolerance control). True higher-order BDF (BDF-2..5) and Radau IIA still open for very stiff CCS / refrigeration trains.
2. ~~**Sensitivity & gradient API**~~ — **ADDRESSED in v1.1** by `neqsim.process.automation.SensitivityAnalyzer` (finite-difference gradients/Jacobians through `ProcessAutomation`, `CENTRAL`/`FORWARD` modes, JSON with `SCHEMA_VERSION="1.0"`). Analytic / AD-based gradients still future work.
3. ~~**Synthesis breadth**~~ — **PARTIALLY ADDRESSED in v1.1** by `CompressionDuty` + `FlowsheetSynthesisEngine.proposeAndBuildCompression` (multi-stage compression with inter/after-coolers). HEN synthesis (pair with `PinchAnalysis`) and reactor-separation-recycle superstructures still open.
4. **Cross-session learned corrections** — persist `AutomationDiagnostics` correction cache to disk so a new agent invocation starts already calibrated to the model.
5. **Convergence-aware retry policy** — `runIfDirty()` should classify convergence failures (`CONVERGENCE_FAILURE` exists in taxonomy but no auto-recovery: damping, reset to last good snapshot, switch solver).
6. **DAE / index-1 algebraic constraints** — `IntegratorStrategy` currently treats the system as pure ODE; controllers with algebraic loops would benefit from a true DAE solver.
7. **Parallel multi-area execution** — `ProcessModel.runTransient` iterates areas sequentially; independent areas could run in parallel threads.
8. **Signed / versioned state snapshots** — `ProcessModelState` needs schema-version migration and optional digital signature for audit trails.

## Verdict

NeqSim is now **clearly the strongest open-source process-simulation substrate for autonomous agents**. The v1 commits closed the two biggest credibility gaps (untyped writes without rollback, standalone dynamics infrastructure not wired into the live transient loop). The **v1.1 follow-up closed the three top "depth" gaps**: more integrator options (`RK4Integrator`, `AdaptiveRK45Integrator`), gradient access (`SensitivityAnalyzer`), and broader synthesis coverage (multi-stage `CompressionDuty`). Remaining gaps are now genuinely deep-domain work (true DAE solver, HEN synthesis, parallel execution).
