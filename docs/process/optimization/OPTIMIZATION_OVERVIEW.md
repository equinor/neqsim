---
title: Process Optimization in NeqSim - Overview
description: This document provides a high-level introduction to the process optimization capabilities in NeqSim, explaining how the different components relate to each other and when to use each one.
---

This document provides a high-level introduction to the process optimization capabilities in NeqSim, explaining how the different components relate to each other and when to use each one.

## Table of Contents

- [Quick Navigation](#quick-navigation)
- [Architecture Overview](#architecture-overview)
- [The Two Main Optimizers](#the-two-main-optimizers)
- [Full ProcessModel Optimization](#full-processmodel-optimization)
- [When to Use Which Optimizer](#when-to-use-which-optimizer)
- [Key Concepts](#key-concepts)
- [Search Algorithms](#search-algorithms)
- [Python usage through neqsim-python](#python-usage-through-neqsim-python)
- [Getting Started](#getting-started)
- [Complete Examples](#complete-examples)
- [Related Documentation](#related-documentation)

---

## Quick Navigation

| I want to... | Use this class | Documentation |
|--------------|----------------|---------------|
| Find maximum throughput for given pressures | `ProcessOptimizationEngine` | [Optimizer Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE.md) |
| Optimize arbitrary objectives with constraints | `ProductionOptimizer` | [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE.md) |
| Do multi-objective Pareto optimization | `ProductionOptimizer.optimizePareto()` | [Multi-Objective Optimization](multi-objective-optimization.md) |
| Run batch parameter studies | `BatchStudy` | [Batch Studies](batch-studies.md) |
| Generate and rank candidate flowsheets from feed/product targets | `ProcessResearcher` | [Process Researcher](process-researcher.md) |
| Calculate flow rates for pressure boundaries | `FlowRateOptimizer` | [Flow Rate Optimization](flow-rate-optimization.md) |
| Generate Eclipse lift curves (VFP tables) | `EclipseVFPExporter` | [Optimizer Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE.md#eclipse-vfp-export) |
| Evaluate equipment constraints | `ProcessConstraintEvaluator` | [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK.md) |
| Integrate with external optimizers (SciPy, NLopt) | `ProcessSimulationEvaluator` | [External Optimizer Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION.md) |
| Optimize full multi-area process models | `ProcessModelSimulationEvaluator` | Use area-qualified `ProcessAutomation` addresses and installed `CapacityConstraint` limits |
| Ramp producers until a full facility reaches a bottleneck | `ProcessModelThroughputOptimizer` | Use producer mappings, installed capacity tables, and exported case traces |
| Solve constrained NLP (equality + inequality) | `SQPoptimizer` | [SQP Optimizer](sqp_optimizer.md) |
| Calibrate model parameters to data | `BatchParameterEstimator` | [Data Reconciliation and Steady-State Detection](data-reconciliation.md) |
| Load optimization config from YAML/JSON | `ProductionOptimizationSpecLoader` | [YAML Spec Format](#yaml-specification-files) |

---


## Getting Started

If you are new to process optimization in NeqSim, begin with:

1. [Getting Started](getting-started.md)
2. [Optimization & Constraints Guide](OPTIMIZATION_AND_CONSTRAINTS.md)
3. [Constraint Framework](constraint-framework.md)

This sequence covers base-run requirements, optimizer selection, and safe variable access through `ProcessAutomation`.

---

## All Documentation Files

| Document | Purpose |
|----------|---------|
| **This Document** | High-level overview and when to use which optimizer |
| **[Optimization & Constraints Guide](OPTIMIZATION_AND_CONSTRAINTS.md)** | **COMPREHENSIVE: Complete guide to algorithms, constraint types, bottleneck analysis, practical examples** |
| **[ProductionOptimizer Tutorial (Jupyter)](../../examples/ProductionOptimizer_Tutorial.md)** | **Interactive notebook: algorithms, single/multi-variable, Pareto, constraints** |
| **[Python Optimization Tutorial (Jupyter)](../../examples/NeqSim_Python_Optimization.md)** | **Using SciPy/Python optimizers with NeqSim: constraints, Pareto, global opt** |
| [Optimizer Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE.md) | Equipment capacity strategies, ProcessOptimizationEngine API, VFP export |
| [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE.md) | Complete examples for ProductionOptimizer with Java/Python |
| [Practical Examples](PRACTICAL_EXAMPLES.md) | Code samples for common optimization tasks |
| [Process Researcher](process-researcher.md) | Candidate flowsheet generation and ranking from feed/product specifications |
| [Multi-Objective Optimization](multi-objective-optimization.md) | Pareto fronts, weighted-sum, epsilon-constraint methods |
| [Batch Studies](batch-studies.md) | Parallel parameter sweeps and sensitivity analysis |
| [Flow Rate Optimization](flow-rate-optimization.md) | FlowRateOptimizer and lift curve tables |
| [External Optimizer Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION.md) | ProcessSimulationEvaluator and ProcessModelSimulationEvaluator for Python/SciPy integration |
| [Getting Started](getting-started.md) | Step-by-step first optimization workflow for process models/systems |
| [Optimizer Guide](../../util/optimizer_guide.md) | Detailed API reference for all optimizer classes |
| [SQP Optimizer](sqp_optimizer.md) | Sequential Quadratic Programming — constrained NLP with BFGS + active-set QP |
| [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK.md) | Equipment constraints and bottleneck detection |

---

## Architecture Overview

NeqSim provides three main levels of optimization capability:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    LEVEL 3: Application-Specific                             │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐             │
│  │ ProductionOpt.   │ │ BatchParameter   │ │ EclipseVFP       │             │
│  │ (max throughput) │ │ (model calibr.)  │ │ (lift curves)    │             │
│  └────────┬─────────┘ └────────┬─────────┘ └────────┬─────────┘             │
└───────────┼──────────────────────────────────────────┼───────────────────────┘
            │                    │                     │
┌───────────┼──────────────────────────────────────────┼───────────────────────┐
│           ▼        LEVEL 2: Unified Engine           ▼                       │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                   ProcessOptimizationEngine                              ││
│  │  • findMaximumThroughput()   • evaluateAllConstraints()                  ││
│  │  • analyzeSensitivity()      • generateLiftCurve()                       ││
│  │  • Search algorithms: Binary, Golden-Section, BFGS                       ││
│  └──────────────────────────────────┬──────────────────────────────────────┘│
└─────────────────────────────────────┼────────────────────────────────────────┘
                                      │
┌─────────────────────────────────────┼────────────────────────────────────────┐
│                                     ▼                                        │
│                   LEVEL 1: Equipment Constraint Layer                        │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │            EquipmentCapacityStrategyRegistry (18 Built-in Strategies)    ││
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐            ││
│  │  │Compressor  │ │ Separator  │ │   Pump     │ │  Reactor   │            ││
│  │  │ Strategy   │ │  Strategy  │ │ Strategy   │ │  Strategy  │ + custom   ││
│  │  │Pipe, Valve │ │  HX, Tank  │ │ Expander   │ │ PowerGen   │            ││
│  │  │Mixer, Split│ │  Ejector   │ │ Distill.   │ │ Subsea,Well│            ││
│  │  └────────────┘ └────────────┘ └────────────┘ └────────────┘            ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                                     │                                        │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │         ProcessEquipmentBaseClass (Universal Constraint Storage)         ││
│  │  All 144+ equipment types inherit: addCapacityConstraint(),              ││
│  │  getMaxUtilization(), isCapacityExceeded(), getBottleneckConstraint()    ││
│  └─────────────────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## The Two Main Optimizers

### ProcessOptimizationEngine

**Purpose:** Find maximum throughput for given inlet/outlet pressure conditions while respecting equipment constraints.

**Best for:**
- Maximum throughput calculations
- Pressure-constrained optimization
- Lift curve generation
- Equipment bottleneck analysis
- Integration with ProcessSystem/ProcessModule

**Key Features:**
- Works directly with `ProcessSystem` or `ProcessModule`
- Uses equipment capacity strategy plugins
- Supports multiple search algorithms (Binary, Golden-Section, BFGS)
- Auto-detects feed and outlet streams
- Generates sensitivity analysis

```java
// ProcessOptimizationEngine - throughput-focused
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);

// Find max throughput at given pressures
OptimizationResult result = engine.findMaximumThroughput(
    50.0,      // inlet pressure (bara)
    10.0,      // outlet pressure (bara)
    1000.0,    // min flow rate
    100000.0   // max flow rate
);

System.out.println("Max flow: " + result.getOptimalValue() + " kg/hr");
System.out.println("Bottleneck: " + result.getBottleneck());
```

### ProductionOptimizer

**Purpose:** General-purpose optimization with arbitrary objective functions, multiple decision variables, and user-defined constraints.

**Best for:**
- Custom objective functions (not just throughput)
- Multi-variable optimization
- Multi-objective Pareto optimization
- User-defined constraints
- Scenario evaluation and parallelization

**Key Features:**
- Arbitrary objective functions via lambdas/interfaces
- Multiple decision variables (`ManipulatedVariable`)
- Multiple search algorithms (Binary, Golden-Section, Nelder-Mead, PSO)
- Pareto multi-objective optimization
- Parallel scenario evaluation
- Works with any `ProcessSystem`

```java
// ProductionOptimizer - general-purpose
ProductionOptimizer optimizer = new ProductionOptimizer();

// Configure optimization
OptimizationConfig config = new OptimizationConfig(50000.0, 200000.0)
    .tolerance(100.0)
    .searchMode(SearchMode.GOLDEN_SECTION_SCORE)
    .maxIterations(30);

// Define objectives
List<OptimizationObjective> objectives = Arrays.asList(
    new OptimizationObjective("throughput",
        proc -> proc.getUnit("outlet").getFlowRate("kg/hr"),
        1.0, ObjectiveType.MAXIMIZE)
);

// Run optimization
OptimizationResult result = optimizer.optimize(process, feed, config, objectives, null);
System.out.println("Optimal rate: " + result.getOptimalRate() + " kg/hr");
```

---

## When to Use Which Optimizer

| Scenario | Recommended | Why |
|----------|-------------|-----|
| "What's the max flow at P_in=50, P_out=10?" | `ProcessOptimizationEngine` | Designed exactly for this |
| "Find bottleneck equipment" | `ProcessOptimizationEngine` | Has constraint evaluation built-in |
| "Generate Eclipse VFP tables" | `ProcessOptimizationEngine` | Has `EclipseVFPExporter` integration |
| "Minimize operating cost" | `ProductionOptimizer` | Custom objective function support |
| "Optimize pressure AND flow rate together" | `ProductionOptimizer` | Multi-variable support |
| "Trade off throughput vs power consumption" | `ProductionOptimizer.optimizePareto()` | Pareto multi-objective |
| "Increase several producers until the full facility reaches a bottleneck" | `ProcessModelThroughputOptimizer` | Maps producers, loads installed capacities, and records the active bottleneck per case |
| "Evaluate 100 scenarios in parallel" | `ProductionOptimizer` | Has parallel evaluation |
| "Calibrate model to match field data" | `BatchParameterEstimator` | Levenberg-Marquardt for data fitting |

---

## Full ProcessModel Optimization

Use `ProcessModelThroughputOptimizer` for large fixed-equipment studies such as increasing one or more producer feed rates until a separator, compressor, valve, heat exchanger, or export train reaches its installed capacity. It is the ergonomic layer for the common full-facility throughput-to-bottleneck task.

Use `ProcessModelSimulationEvaluator` directly when you need a lower-level black-box bridge to SciPy, NLopt, SQP, Pyomo, or another external optimizer.

The evaluator is deliberately a black-box bridge. It keeps the full plant model intact, lets external optimizers pass a vector of decision variables, runs `ProcessModel.run()`, and returns objective values, constraint margins, feasibility, and the active bottleneck.

| Requirement | Pattern |
|-------------|---------|
| Producer controls | Use `addProducer(...)` with area-qualified addresses such as `wells::feed.flowRate` |
| Scenario-level multipliers | Use `addProducerMultiplier(...)` for variables not exposed by automation |
| Objective | Use `setObjective(...)`, typically export gas, export oil, total sales rate, or power-normalized production |
| Installed equipment limits | Attach `CapacityConstraint` objects or load a CSV table with `loadInstalledCapacities(...)` |
| Search | Use `findMaximumThroughput(lower, upper, tolerance)` for scalar producer-ramp studies |
| Bottleneck reporting | Read `getBestFeasibleCase()`, `getFirstInfeasibleCase()`, and the case table rows |

The typical workflow is:

1. Build each process area as a separate `ProcessSystem` and compose them in a `ProcessModel`.
2. Add installed capacity constraints to equipment whose sizes are fixed.
3. Register producer feed rates or scenario multipliers as throughput controls.
4. Register the objective, for example export gas flow, oil export rate, or total sales flow.
5. Run `findMaximumThroughput(...)` to bracket the first infeasible case and binary-search the maximum feasible multiplier.
6. Export the case table with `ProcessModelThroughputResult.exportToCSV(...)` or serialize it with `toJson()`.
7. Inspect the best feasible case, first infeasible case, and active bottleneck metadata for the recommended operating point.

The high-level API pattern below is covered by the focused unit test `ProcessModelThroughputOptimizerTest`.

```java
ProcessModelThroughputOptimizer optimizer = new ProcessModelThroughputOptimizer(model);
optimizer.addProducer("feed", "wells::feed.flowRate", 1.0, 2.0, "kg/hr");
optimizer.setObjective("exportGas", new ToDoubleFunction<ProcessModel>() {
    @Override
    public double applyAsDouble(ProcessModel processModel) {
        return processModel.getVariableValue("separation::separator.gasOutStream.flowRate", "kg/hr");
    }
}, "kg/hr");
optimizer.loadInstalledCapacities("installed_capacity.csv");

ProcessModelThroughputResult result = optimizer.findMaximumThroughput(1.0, 2.0, 0.01);
ThroughputCaseRow best = result.getBestFeasibleCase();
ThroughputCaseRow firstLimit = result.getFirstInfeasibleCase();
result.exportToCSV("throughput_trace.csv");
```

Active bottleneck rows preserve limit provenance, evidence-quality confidence, the inclusive
scalar validity range, and whether the snapshotted current load is inside that range. Check
`hasConfidence()` and `hasValidityRange()` before reading numeric metadata. JSON uses `null`
for unset values; CSV retains explicit presence flags and blank unset-value cells. These fields are
diagnostics only and do not change feasibility or throughput search.

The installed-capacity table uses one row per equipment limit:

```text
area,equipment,constraint,currentValueAddress,designValue,maxValue,unit,severity,enabled
separation,separator,installedGasCapacity,wells::feed.flowRate,15000,16500,kg/hr,HARD,true
```

For custom optimizers, use the underlying `ProcessModelSimulationEvaluator`. The API pattern below is covered by the focused unit test `ProcessModelSimulationEvaluatorTest`.

```java
ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(model);
evaluator.addParameter("wells::feed.flowRate", 5000.0, 20000.0, "kg/hr");
evaluator.addObjective("exportGas", new ToDoubleFunction<ProcessModel>() {
    @Override
    public double applyAsDouble(ProcessModel processModel) {
        return processModel.getVariableValue("separation::separator.gasOutStream.flowRate", "kg/hr");
    }
}, ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
evaluator.addConstraintUpperBound("feedLimit", new ToDoubleFunction<ProcessModel>() {
    @Override
    public double applyAsDouble(ProcessModel processModel) {
        return processModel.getVariableValue("wells::feed.flowRate", "kg/hr");
    }
}, 15000.0);
evaluator.addEquipmentCapacityConstraints();

ProcessModelSimulationEvaluator.EvaluationResult result = evaluator.evaluate(new double[] {12000.0});
ProcessModelSimulationEvaluator.BottleneckStatus bottleneck = result.getActiveBottleneck();
List<ProcessModelSimulationEvaluator.BottleneckStatus> ranked =
    result.getRankedCapacityConstraints();
List<InstalledEquipmentCapacityEvidence> installed =
    result.getInstalledEquipmentCapacityEvidence();
```

Each successful `evaluate(...)` call samples every registered installed-capacity supplier exactly
once and reuses that value for normalized feasibility, legacy bottleneck ranking, and immutable
`InstalledEquipmentCapacityEvidence`. The evidence rows are ordered by descending utilization
and remain unchanged after later evaluations or equipment mutation, so a case history can reveal an
emerging compressor, separator, pipeline, utility, or export bottleneck before it becomes the
leading constraint.

Do not interpret a normalized value using the equipment unit.
`getNormalizedUtilization()` and `getNormalizedMargin()` use unit `"1"`;
`getCurrentValue()`, `getApplicableLimit()`, `getPhysicalMargin()`, and
`getRequiredRelief()` use `getPhysicalUnit()`. Required relief is a signed-residual conversion
at the sampled point, not a capacity-sizing recommendation, production-loss estimate, shadow price,
economic value, or operating approval. Stable `area::equipment/constraint` identity, equipment
class/reference designation, direct-versus-strategy provenance, type, severity, warning threshold,
installed design/minimum/maximum values, source, confidence, validity range, and numerical status
are retained for Java serialization and JPype. `ThroughputCaseRow`
preserves the same list for every case generated by `ProcessModelThroughputOptimizer`; its JSON
representation includes `rankedCapacityConstraints` with engineering values and evidence metadata.

Use `getEvidenceApplicability()` to distinguish
`WITHIN_VALIDITY_RANGE`, `OUTSIDE_VALIDITY_RANGE`, and `NOT_ASSESSED` results. The engineering
order remains utilization-only: confidence is retained as evidence quality and is never converted
to a safety probability, feasibility adjustment, or ranking weight. Equal-utilization limits retain
model registration order, including the declared order of built-in strategy-generated limits, and
each dynamic limit supplier is sampled once per ranking call.
Enabled limits with undefined (`NaN`) utilization remain visible at the end for diagnosis.
Call `rankCapacityConstraints(model)` directly only when a ranking is needed outside an evaluator
run; it returns the same snapshot shape without adding a process simulation.

`ProcessModelSimulationEvaluator` complements rather than replaces the other optimizers. Use `ProcessOptimizationEngine` for compact throughput cases on one process, `ProductionOptimizer` for existing single-system objective workflows, and `ProcessModelSimulationEvaluator` when the optimization boundary is the full plant model.

---

## Relationship Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           USER CODE                                          │
└─────────┬─────────────────────┬────────────────────────┬─────────────────────┘
          │                     │                        │
          ▼                     ▼                        ▼
┌─────────────────────┐ ┌────────────────────┐ ┌─────────────────────────────┐
│ProcessOptimization  │ │ ProductionOptimizer│ │ BatchParameterEstimator     │
│Engine               │ │                    │ │ (model calibration)         │
│                     │ │• Custom objectives │ │                             │
│• findMaxThroughput()│ │• Multi-variable    │ │• Levenberg-Marquardt        │
│• evaluateConstraint │ │• Pareto multi-obj  │ │• Parameter fitting          │
│• generateLiftCurve()│ │• Parallel eval     │ │• Uncertainty quantification │
└──────────┬──────────┘ └─────────┬──────────┘ └──────────────────────────────┘
           │                      │
           │    ┌─────────────────┘
           │    │
           ▼    ▼
    ┌──────────────────────────────────────────┐
    │            ProcessSystem                  │
    │  (contains equipment, streams, recycles) │
    │                                          │
    │  process.run() → converged state         │
    │  process.getUnit("name") → equipment     │
    └────────────────┬─────────────────────────┘
                     │
                     ▼
    ┌──────────────────────────────────────────┐
    │   Equipment Capacity Strategy Registry   │
    │                                          │
    │  CompressorCapacityStrategy              │
    │  SeparatorCapacityStrategy               │
    │  PumpCapacityStrategy                    │
    │  ... (extensible plugin system)          │
    └────────────────┬─────────────────────────┘
                     │
                     ▼
    ┌──────────────────────────────────────────┐
    │         CapacityConstraint               │
    │                                          │
    │  • name, unit, type                      │
    │  • designValue, maxValue                 │
    │  • getUtilization() → 0.0 to 1.0+        │
    │  • severity (HARD/SOFT)                  │
    └──────────────────────────────────────────┘
```

---

## Key Concepts

### Equipment Capacity Constraints

Equipment constraints define operating limits. Each equipment type has a strategy that extracts constraints:

| Equipment | Typical Constraints |
|-----------|---------------------|
| Compressor | Surge margin, max power, operating envelope, speed limits |
| Separator | Liquid level, residence time, gas/liquid capacity |
| Pump | NPSH margin, max power, flow limits |
| Pipe | Erosional velocity, pressure drop |
| Valve | Cv capacity, choke conditions |

> **⚠️ Important**: Most equipment constraints are **disabled by default** for backward compatibility. The optimizer automatically falls back to traditional capacity methods (`getCapacityMax()`/`getCapacityDuty()`) when no enabled constraints exist. To use multi-constraint capacity analysis, you must explicitly enable constraints:
>
> ```java
separator.useEquinorConstraints();  // Enable Equinor TR3500 constraints
// OR
separator.enableConstraints();       // Enable all constraints
> ```
>
> See [Capacity Constraint Framework - Constraints Disabled by Default](../CAPACITY_CONSTRAINT_FRAMEWORK.md#important-constraints-disabled-by-default) for details.

### Utilization Ratio

The **utilization ratio** is the key metric:

$$\text{utilization} = \frac{\text{actual value}}{\text{design limit}}$$

- `0.0` = not used
- `1.0` = at design limit
- `> 1.0` = exceeds limit (constraint violation)

### Bottleneck Detection

The **bottleneck** is the equipment with the highest utilization ratio:

```java
String bottleneck = engine.findBottleneckEquipment();
// Returns equipment name with highest utilization
```

---

## Search Algorithms

Both optimizers support multiple search algorithms:

| Algorithm | Best For | Convergence | Notes |
|-----------|----------|-------------|-------|
| **Binary Search** | Monotonic problems | Fast | Assumes feasibility is monotonic |
| **Golden Section** | Single variable, non-monotonic | Moderate | Robust, doesn't require derivatives |
| **Nelder-Mead** | Multi-variable (2-10 vars) | Moderate | No gradients needed |
| **PSO (Particle Swarm)** | Global search, many local optima | Slow | Good for non-convex problems |
| **Gradient Descent** | Smooth multi-variable (5-20+) | Fast | **New (Jan 2026)** - Finite-difference gradients |
| **BFGS** | Smooth functions | Fast | Requires gradient approximation |

For full multi-area `ProcessModel` studies, `ProcessModelSimulationEvaluator` keeps forward
differences as the low-cost default and also exposes `FiniteDifferenceMethod.CENTRAL`. Both methods
strictly honor parameter bounds and divide by the actual applied perturbation. Central differences
use a symmetric stencil at interior points and a one-sided fallback at an active bound. Verify
step-size stability before interpreting a local derivative as debottlenecking sensitivity or
shadow-value evidence. `estimateSensitivitiesWithQuality(...)` automates one step-halving check,
returns the fine-step objective gradient and constraint-margin Jacobian, and records the actual
stencil, applied steps, convergence, hard-constraint feasibility, and evaluation errors for every
perturbation. Callers select the acceptable relative-disagreement tolerance and must still check
nearby points and active equipment/control regimes. Its immutable parameter, selected-objective,
and constraint snapshots bind every derivative column and row to names, addresses, units,
directions or types, bounds, hard/soft semantics, capacity origin, and the sampled base values and
margins. This avoids joining archived matrices back to mutable evaluator definitions. The
snapshots preserve raw units; normalize only with declared engineering scales before comparing
unlike constraints.

Before using those derivatives in local bottleneck or operating-action logic, construct a
`SensitivityQualificationPolicy` and call `assessConstraintSensitivities(policy)`. Each immutable
constraint/parameter assessment retains raw and minimizer objective derivatives, the margin
derivative, declared derivative units, stencil, pair-specific coarse/fine disagreements, complete
evidence flags, rejection reasons, and actionable diagnostics. Convergence failures, errors,
non-finite derivatives, unstable refinement, and fixed parameters always reject a pair; policy
controls whether infeasible samples or one-sided stencils are permitted. Permitted cautions remain
flagged. `getAcceptedConstraintSensitivities(policy)` filters accepted pairs without rerunning the
model. This is local numerical qualification, not constraint scaling, ranking, active-set
inference, a KKT multiplier, or a shadow price.

After qualifying the raw local derivatives, use `ConstraintActivityAnalyzer` only when every
constraint has an explicit positive reference value in its declared unit and recorded provenance.
`ConstraintScale.fromSnapshot(...)` binds that reference to the exact constraint identity,
including type, bounds, hard/soft semantics, penalty and capacity origin. An `ActivityPolicy`
retains both the normalized-margin tolerance and the sensitivity qualification policy.
`assess(...)` reports dimensionless base margins, scaled margin derivatives, violated constraints,
and conservative `CANDIDATE_ACTIVE` / `INACTIVE` diagnostics without rerunning the model. Missing,
duplicate, unitless or stale scales fail closed. Candidate active means only that a feasible local
margin is within the declared tolerance; it is not an optimizer active-set proof, ranking, KKT
multiplier, economic shadow price or engineering approval.

### Reversible operating actions

`ProcessModelOperatingAction` defines one simulator-bound action with an immutable stable ID,
display name, area-qualified automation address, unit, provenance, and either inclusive continuous
bounds or exact enumerated discrete values. `inspectCapability(model)` reports whether the target
is readable. `capture(model)`, `apply(model, value)`, and `restore(model, state)` provide
identity-checked candidate mutation with automation read-back verification and explicit rollback
diagnostics, without running the model.

Use `registerWith(ProcessModelSimulationEvaluator)` for external optimization. Registration fails closed when the current model value is outside the candidate domain, while capture/restore remains available for that brownfield baseline. Continuous actions
map directly to bounded evaluator parameters. Discrete actions expose their ordered allowed values
through the returned binding; callers must enumerate them because intermediate vector values fail
closed rather than being silently rounded or interpolated. Re-register actions after evaluator
deserialization because all evaluator callbacks are transient.

This API preserves candidate semantics and restoration only. It does not alter routing topology,
solve a mixed-integer problem, run the process, establish process feasibility, rank actions, or
approve an operating change. After each candidate, explicitly run NeqSim and verify convergence,
constraint residuals, conservation, product quality, equipment limits, and nearby behavior.

### Transactional hydraulic action evaluation

`ProcessModelOperatingActionEvaluator` closes the steady-state validation loop for one action and
explicitly selected reservoir, well, gathering, or pipeline capacity constraints. It establishes a
converged baseline, captures the action state, applies and runs one candidate through
`ProcessModelSimulationEvaluator`, snapshots every exact required hydraulic constraint, then
restores and reruns the baseline before returning. `CandidateEvaluationResult` is immutable and
serializable and retains action identity/provenance, raw and sign-adjusted objectives, registered
constraint values and margins, hydraulic utilization and margin, equipment/design provenance,
confidence, validity applicability, restoration evidence, and diagnostics.

The wrapped simulation evaluator must have no parameters because the transactional wrapper owns
the one candidate write. Objectives and non-equipment constraints may still be registered before
construction. The wrapper adds enabled equipment capacity constraints automatically. Require at
least one exact `area`, `equipment`, and `constraint` binding; a misspelled or disabled constraint,
non-finite value, limit violation, or sample outside an explicitly declared validity range fails
closed with a distinct `Outcome`. An absent validity range is reported as `NOT_ASSESSED` and is not
silently invented.

```java
ProcessModelSimulationEvaluator simulation = new ProcessModelSimulationEvaluator(model);
simulation.addObjective("export gas", processModel -> export.getFlowRate("kg/hr"),
    ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);

ProcessModelOperatingAction rate = ProcessModelOperatingAction
    .continuous("producer-rate", "Producer gas rate", "Subsurface::producer.flowRate",
        0.5, 1.5, "MSm3/day", "approved well operating envelope revision A")
    .withReadBackTolerance(1.0e-5, 0.0,
        "producer flow-control tag resolution in MSm3/day");

ProcessModelOperatingActionEvaluator hydraulic =
    new ProcessModelOperatingActionEvaluator(simulation, rate)
        .requireHydraulicConstraint(
            ProcessModelOperatingActionEvaluator.HydraulicLimitRole.WELL_INFLOW_OUTFLOW,
            "Subsurface", "well", "well drawdown", "installed maximum drawdown basis");

ProcessModelOperatingActionEvaluator.CandidateEvaluationResult candidate =
    hydraulic.evaluate(1.2);
if (!candidate.isBaselineRestored() || !candidate.isBaselineSimulationConverged()) {
  throw new IllegalStateException("The model baseline was not recovered");
}
```

The default action tolerance remains a strict scale-aware floating-point comparison. When a
specific automation conversion or control tag has coarser resolution, declare its absolute and/or
relative tolerance with `withReadBackTolerance` and retain the evidence source. Application
diagnostics report the requested and read-back values, absolute residual, allowed tolerance, and
tolerance provenance. A tolerated write does not prove process feasibility.

This API consumes existing hydraulic and equipment calculations; it does not add a correlation,
change topology, prove conservation beyond the configured model, or approve an operating change.
Use independent `ProcessModel` instances for parallel candidates. Add product, mechanical, safety,
environmental, and market constraints to the wrapped evaluator when those limits are in scope.

### Coupled well-allocation action transactions

Use `ProcessModelOperatingActionSetEvaluator` when one candidate changes two or more independent
automation addresses, such as producer-rate allocation against per-well drawdown and a shared
manifold capacity. The constructor retains a stable set ID, name, provenance, and declaration-ordered
actions. Duplicate action IDs or addresses fail before mutation.

The evaluator establishes one converged baseline and captures every action before the first write.
It applies the complete vector in order, refuses to simulate a partial vector after any rejected
write, snapshots exact required hydraulic constraints, restores all actions in reverse order, and
reruns the baseline. The serializable result exposes immutable per-action requested/read-back values,
residuals, tolerances and provenance together with objective, constraint, hydraulic and restoration
evidence for Java and JPype/Python.

```java
ProcessModelOperatingActionSetEvaluator allocation =
    new ProcessModelOperatingActionSetEvaluator(
        "field-allocation", "Field production allocation",
        "approved well operating envelopes and gathering basis revision A", simulation,
        Arrays.asList(wellAAction, wellBAction))
            .requireHydraulicConstraint(
                ProcessModelOperatingActionEvaluator.HydraulicLimitRole.WELL_INFLOW_OUTFLOW,
                "Subsurface", "well A", "well drawdown", "well A installed drawdown basis")
            .requireHydraulicConstraint(
                ProcessModelOperatingActionEvaluator.HydraulicLimitRole.WELL_INFLOW_OUTFLOW,
                "Subsurface", "well B", "well drawdown", "well B installed drawdown basis")
            .requireHydraulicConstraint(
                ProcessModelOperatingActionEvaluator.HydraulicLimitRole.GATHERING_HYDRAULICS,
                "Gathering", "inlet separator", "installed gathering rate",
                "shared installed gathering capacity");

ProcessModelOperatingActionSetEvaluator.CandidateSetEvaluationResult result =
    allocation.evaluate(new double[] {wellARate, wellBRate});
if (!result.isBaselineRestored() || !result.isBaselineSimulationConverged()) {
  throw new IllegalStateException(result.getDiagnostics().toString());
}
```

A feasible result proves only the configured steady-state simulation and exact constraints at that
candidate. It does not choose an allocation, mutate routing, qualify dynamics, create new hydraulic
physics, or approve an operating change. Check mass and energy balance, product specifications,
mechanical/safety/environmental/market limits, nearby points and evidence validity. Use independent
`ProcessModel` instances for parallel candidates.

### Fixed-total allocation search with complete candidate evidence

`ProcessModelAllocationOptimizer` turns the atomic action-set evaluator into a bounded continuous
allocation search. Every candidate preserves one declared total and moves rate only by pairwise
transfer, so the sum remains constant while per-action bounds and all configured process and
hydraulic constraints are enforced by NeqSim. The optimizer stops immediately if rollback or
restored-baseline convergence fails.

```java
ProcessModelAllocationOptimizer optimizer = new ProcessModelAllocationOptimizer(
    "field-rate-allocation", "Field rate allocation",
    "approved well envelopes and host capacity basis revision A", allocation,
    totalRate, "kg/hr")
        .setInitialAllocation(new double[] {initialWellARate, initialWellBRate})
        .setObjectiveIndex(0)
        .setInitialStepFraction(0.10)
        .setRelativeStepTolerance(1.0e-3)
        .setObjectiveImprovementTolerance(
            1.0e-6, "validated export-rate calculation resolution")
        .setMaximumEvaluations(100);

ProcessModelAllocationOptimizer.AllocationSearchResult search = optimizer.optimize();
if (!search.isModelRecovered()) {
  throw new IllegalStateException(search.getDiagnostics().toString());
}
ProcessModelAllocationOptimizer.CandidateRecord best = search.getBestFeasibleCandidate();
if (best != null) {
  double[] allocationRates = best.getCandidateValues();
  List<ProcessModelOperatingActionEvaluator.HydraulicConstraintSnapshot> limiting =
      search.getRankedHydraulicConstraintsAtBestFeasible();
}
```

The immutable serializable result freezes optimizer/action-set/objective identity, bounds, seed,
budget, transfer and objective tolerances with provenance, every atomic candidate result, the best
feasible candidate, and utilization-ranked hydraulic evidence. It also reports the gap to the best
objective among sampled points. That sampled gap is a search diagnostic, not global production loss,
economic value, or a shadow price.

This first allocation optimizer requires continuous actions with exactly matching units and a
feasible fixed total. Convergence means that no improving feasible pair transfer was found above the
declared step tolerance; it does not prove global optimality. Validate nearby allocations,
conservation, product specifications, rotating-equipment maps, utilities, safety and market limits,
and use independent model/evaluator/optimizer instances for parallel searches.

### Trace-qualified bottleneck-relief evidence

`ProcessModelAllocationBottleneckAnalyzer` reads a completed
`ProcessModelAllocationOptimizer.AllocationSearchResult`; it never runs the simulator or writes
an operating action. It keeps only fully recovered candidates whose selected raw objective improves
on the best feasible sample by more than the declared objective tolerance and which contain at
least one exact finite hard-constraint violation.

```java
ProcessModelAllocationBottleneckAnalyzer analyzer =
    new ProcessModelAllocationBottleneckAnalyzer(
        "field-allocation-relief", "Field allocation bottleneck relief",
        "approved allocation study revision A");
ProcessModelAllocationBottleneckAnalyzer.BottleneckAnalysisResult relief =
    analyzer.analyze(search);

for (ProcessModelAllocationBottleneckAnalyzer.BottleneckReliefOpportunity opportunity :
    relief.getOpportunities()) {
  for (ProcessModelAllocationBottleneckAnalyzer.ConstraintReliefEvidence constraint :
      opportunity.getConstraintRelief()) {
    double requiredRelief = constraint.getRequiredMarginRelief();
    String reliefUnit = constraint.getUnit();
  }
}
```

Candidate results freeze registered objective and constraint definitions beside their sampled values
and margins and retain the complete `getInstalledEquipmentCapacityEvidence()` list, so
serialization or later evaluator/equipment mutation cannot relabel the trace. Installed-capacity
relief comes from the same immutable current/applicable-limit row that supplied feasibility; it no
longer depends on joining to the smaller required-hydraulic-binding list. General constraints use
the magnitude of their negative margin. Missing, invalid, or out-of-validity evidence is reported as
evidence-limited, soft constraints are excluded, and unlike relief units are never aggregated or
ranked.

The analyzer reports a sampled association, action deltas from the best feasible allocation, and an
isolated/coupled/evidence-limited classification. It does not establish causality, capacity-sizing
sufficiency, global optimality, a shadow price, production loss, economic value, or operating
approval.

### ProcessOptimizationEngine Algorithms

```java
engine.setSearchAlgorithm(SearchAlgorithm.GOLDEN_SECTION);
engine.setSearchAlgorithm(SearchAlgorithm.BFGS);
engine.setSearchAlgorithm(SearchAlgorithm.GRADIENT_ACCELERATED);
```

### ProductionOptimizer Algorithms

```java
config.searchMode(SearchMode.BINARY_FEASIBILITY);
config.searchMode(SearchMode.GOLDEN_SECTION_SCORE);
config.searchMode(SearchMode.NELDER_MEAD_SCORE);
config.searchMode(SearchMode.PARTICLE_SWARM_SCORE);
config.searchMode(SearchMode.GRADIENT_DESCENT_SCORE);  // New (Jan 2026)
```

> **January 2026 Update:** ProductionOptimizer now includes `GRADIENT_DESCENT_SCORE` algorithm, configuration validation, stagnation detection, warm start, bounded LRU cache, and infeasibility diagnostics. See [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE.md) for details.

---

## Python usage through neqsim-python

Both optimizers work seamlessly from Python using neqsim-python:

### ProcessOptimizationEngine from Python

```python
from neqsim import jneqsim

# Get classes
ProcessOptimizationEngine = jneqsim.process.util.optimizer.ProcessOptimizationEngine
SearchAlgorithm = ProcessOptimizationEngine.SearchAlgorithm

# Create and configure
engine = ProcessOptimizationEngine(process)
engine.setSearchAlgorithm(SearchAlgorithm.GOLDEN_SECTION)

# Find max throughput
result = engine.findMaximumThroughput(50.0, 10.0, 1000.0, 100000.0)
print(f"Max flow: {result.getOptimalValue():.0f} kg/hr")
print(f"Bottleneck: {result.getBottleneck()}")
```

### ProductionOptimizer from Python

```python
from neqsim import jneqsim
from jpype import JImplements, JOverride

# Get classes
ProductionOptimizer = jneqsim.process.util.optimizer.ProductionOptimizer
OptimizationConfig = ProductionOptimizer.OptimizationConfig
OptimizationObjective = ProductionOptimizer.OptimizationObjective
SearchMode = ProductionOptimizer.SearchMode

# Define objective function as Java interface
@JImplements("java.util.function.ToDoubleFunction")
class ThroughputObjective:
    @JOverride
    def applyAsDouble(self, proc):
        return proc.getUnit("outlet").getFlowRate("kg/hr")

# Configure and run
optimizer = ProductionOptimizer()
config = OptimizationConfig(50000.0, 200000.0) \
    .tolerance(100.0) \
    .searchMode(SearchMode.GOLDEN_SECTION_SCORE)

objectives = [
    OptimizationObjective("throughput", ThroughputObjective(), 1.0)
]

result = optimizer.optimize(process, feed, config, objectives, None)
print(f"Optimal rate: {result.getOptimalRate():.0f} kg/hr")
```

---

## Complete Examples

### Example 1: Find Maximum Compressor Throughput

```java
import neqsim.process.util.optimizer.ProcessOptimizationEngine;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// Create gas system
SystemInterface gas = new SystemSrkEos(288.15, 50.0);
gas.addComponent("methane", 0.9);
gas.addComponent("ethane", 0.1);
gas.setMixingRule("classic");

// Build process
Stream feed = new Stream("feed", gas);
feed.setFlowRate(50000, "kg/hr");
feed.setPressure(50.0, "bara");

Compressor compressor = new Compressor("comp", feed);
compressor.setOutletPressure(100.0);

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(compressor);
process.run();

// Find maximum throughput
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
engine.setFeedStreamName("feed");
engine.setSearchAlgorithm(SearchAlgorithm.GOLDEN_SECTION);

OptimizationResult result = engine.findMaximumThroughput(
    50.0,      // inlet pressure
    100.0,     // outlet pressure
    10000.0,   // min flow
    200000.0   // max flow
);

System.out.println("Maximum throughput: " + result.getOptimalValue() + " kg/hr");
System.out.println("Limited by: " + result.getBottleneck());
```

### Example 2: Multi-Objective Pareto Optimization

```java
import neqsim.process.util.optimizer.ProductionOptimizer;
import neqsim.process.util.optimizer.ProductionOptimizer.*;

ProductionOptimizer optimizer = new ProductionOptimizer();

// Define competing objectives
List<OptimizationObjective> objectives = Arrays.asList(
    new OptimizationObjective("throughput",
        proc -> proc.getUnit("outlet").getFlowRate("kg/hr"),
        1.0, ObjectiveType.MAXIMIZE),
    new OptimizationObjective("power",
        proc -> ((Compressor) proc.getUnit("comp")).getPower("kW"),
        1.0, ObjectiveType.MINIMIZE)
);

// Configure Pareto optimization
OptimizationConfig config = new OptimizationConfig(50000.0, 200000.0)
    .paretoGridSize(20)  // 20 weight combinations
    .tolerance(100.0);

// Generate Pareto front
ParetoResult pareto = optimizer.optimizePareto(process, feed, config, objectives);

System.out.println("Pareto front has " + pareto.getPoints().size() + " solutions");
for (ParetoPoint point : pareto.getPoints()) {
    System.out.printf("Flow: %.0f kg/hr, Power: %.0f kW%n",
        point.getObjectives().get("throughput"),
        point.getObjectives().get("power"));
}
```

---

## YAML Specification Files

The `ProductionOptimizationSpecLoader` class allows loading optimization scenarios from YAML or JSON files, enabling configuration-driven optimization workflows.

### YAML Format

```yaml
scenarios:
  - name: "MaxThroughput"
    process: "myProcess"           # Key in processes map
    feedStream: "wellFeed"         # Key in feeds map
    lowerBound: 50000.0
    upperBound: 200000.0
    rateUnit: "kg/hr"
    tolerance: 100.0
    maxIterations: 30
    searchMode: "GOLDEN_SECTION_SCORE"
    utilizationMarginFraction: 0.05

    objectives:
      - name: "throughput"
        weight: 1.0
        type: "MAXIMIZE"
        metric: "throughputMetric"   # Key in metrics map

    constraints:
      - name: "maxPower"
        metric: "powerMetric"
        limit: 5000.0
        direction: "LESS_THAN"
        severity: "HARD"
        description: "Compressor power limit"
```

### Loading YAML Specs in Java

```java
import neqsim.process.util.optimizer.ProductionOptimizationSpecLoader;

// Create registries mapping spec keys to objects
Map<String, ProcessSystem> processes = new HashMap<>();
processes.put("myProcess", process);

Map<String, StreamInterface> feeds = new HashMap<>();
feeds.put("wellFeed", feed);

Map<String, ToDoubleFunction<ProcessSystem>> metrics = new HashMap<>();
metrics.put("throughputMetric", p -> p.getUnit("outlet").getFlowRate("kg/hr"));
metrics.put("powerMetric", p -> ((Compressor) p.getUnit("comp")).getPower("kW"));

// Load scenarios from YAML
List<ScenarioRequest> scenarios = ProductionOptimizationSpecLoader.load(
    Paths.get("optimization.yaml"), processes, feeds, metrics);

// Run each scenario
ProductionOptimizer optimizer = new ProductionOptimizer();
for (ScenarioRequest scenario : scenarios) {
    OptimizationResult result = optimizer.optimizeScenario(scenario);
    System.out.println(scenario.getName() + ": " + result.getOptimalRate());
}
```

---

## Related Documentation

## Class Summary

| Class | Purpose | Key Method | Documentation |
|-------|---------|------------|---------------|
| `ProcessOptimizationEngine` | Throughput-focused optimization | `findMaximumThroughput()` | [Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE.md) |
| `ProductionOptimizer` | General-purpose optimization | `optimize()`, `optimizePareto()` | [Production Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE.md) |
| `FlowRateOptimizer` | Flow rate for pressure boundaries | `findMaxFlowRate()` | [Flow Rate Optimization](flow-rate-optimization.md) |
| `MultiObjectiveOptimizer` | Pareto front generation | `optimize()` | [Multi-Objective](multi-objective-optimization.md) |
| `BatchStudy` | Parallel parameter sweeps | `run()` | [Batch Studies](batch-studies.md) |
| `ProcessConstraintEvaluator` | Constraint evaluation | `evaluate()` | [Capacity Framework](../CAPACITY_CONSTRAINT_FRAMEWORK.md) |
| `ProcessSimulationEvaluator` | External optimizer interface | `evaluate()` | [External Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION.md) |
| `ProcessModelSimulationEvaluator` | External optimizer interface for multi-area `ProcessModel` studies | `evaluate()` | [External Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION.md) |
| `ProcessModelOperatingActionSetEvaluator` | Atomic coupled-action candidate evaluation | `evaluate(double[])` | [External Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION.md) |
| `ProcessModelAllocationOptimizer` | Fixed-total continuous allocation search with complete candidate evidence | `optimize()` | [External Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION.md) |
| `ProcessModelThroughputOptimizer` | Full-model throughput-to-bottleneck study helper | `findMaximumThroughput()` | [External Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION.md) |
| `InstalledCapacityTableLoader` | Attach fixed equipment limits from CSV | `load()` | [Capacity Framework](../CAPACITY_CONSTRAINT_FRAMEWORK.md) |
| `EclipseVFPExporter` | Eclipse VFP tables | `exportVFPPROD()` | [Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE.md#eclipse-vfp-export) |
| `LiftCurveGenerator` | Lift curve tables | `generateLiftCurve()` | [Flow Rate Optimization](flow-rate-optimization.md) |
| `BatchParameterEstimator` | Model calibration | `solve()` | [Data Reconciliation and Steady-State Detection](data-reconciliation.md) |
| `ProductionOptimizationSpecLoader` | YAML/JSON config loading | `load()` | [YAML Format](#yaml-specification-files) |

---

## Decision Guide

Choose based on your use case:
- **Max throughput at pressures** → `ProcessOptimizationEngine`
- **Custom objectives/multi-variable** → `ProductionOptimizer`
- **Full `ProcessModel` with several process areas and producer ramping** → `ProcessModelThroughputOptimizer`
- **Full `ProcessModel` custom external optimization** → `ProcessModelSimulationEvaluator`
- **Coupled well-rate candidate with mandatory rollback** → `ProcessModelOperatingActionSetEvaluator`
- **Fixed-total continuous allocation across coupled actions** → `ProcessModelAllocationOptimizer`
- **Model calibration** → `BatchParameterEstimator`
