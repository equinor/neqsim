---
name: neqsim-process-synthesis-research
description: "Guides agents through ProcessResearcher, ProcessResearchSpec, ProcessCandidateGenerator, ProcessSynthesisGraph, ProcessSynthesisTemplateLibrary, and ProcessSuperstructureExporter. USE WHEN: a user wants candidate flowsheets generated from feed/product goals, bounded process-network paths compared, reaction routes screened, or candidate JSON/Pyomo-GDP skeletons prepared for external optimization."
last_verified: "2026-10-03"
---

# Process Synthesis Research

## When to use this

Use `ProcessResearcher` when the topology is not yet selected and the task starts from a feed composition plus one or more product targets. The package builds a bounded candidate set, prunes invalid routes, optionally runs each candidate through NeqSim's process JSON builder, scores results, and returns ranked candidates with warnings and assumptions.

Use `neqsim-optimization-and-doe` instead when an existing flowsheet topology is fixed and the question is which setpoints, equipment variables, or operating conditions optimize it. A generated candidate's internal bounded decision-variable grid is a screening layer, not the same as a general external optimizer.

The API is documented in `docs/process/optimization/process-researcher.md`, and `ProcessResearcherTest` covers generated candidates, graph paths, the curated conditioning library, pruning, metrics, and exports. No `ProcessResearcher`-named runner was found under `src/main/java/neqsim/mcp`; the entry point is the Java API (also reachable through the Java classes exposed by `neqsim` Python).

### What search means here

- `ProcessCandidateGenerator` creates candidates from allowed unit types, reaction options, operation options, and optionally curated templates.
- `ProcessSynthesisGraph` enumerates bounded material-operation paths using `MaterialNode`, `OperationOption`, and product targets. The test confirms a two-operation compression/separation path is generated as `process-network-graph`.
- `ProcessSynthesisTemplateLibrary` adds curated patterns such as compression, aftercooling, and polishing separation when enabled. This is template/path enumeration, not unconstrained flowsheet topology search.
- `ProcessSynthesisFeasibilityPruner` rejects invalid specs and disconnected material paths before simulation. `ProcessCandidateEvaluator` runs surviving JSON definitions and may grid-screen decision variables.
- `ProcessResearcher` ranks/marks candidates using configured product-oriented scoring and reported metrics; it does not call a general MINLP solver to search all unit combinations and continuous variables jointly.
- `ProcessSuperstructureExporter` emits a reduced superstructure JSON and a Pyomo/GDP starter skeleton. The skeleton requires a caller-owned formulation, solver, constraints, and validation; NeqSim does not execute the external MINLP for this workflow.

## Class map

| Class or family | Package | What it does | Key methods verified |
|---|---|---|---|
| `ProcessResearcher` | `neqsim.process.research` | Runs generation, optional candidate evaluation, sorting, and dominance marking. | `ProcessResearcher()`, `research(ProcessResearchSpec)` |
| `ProcessResearchSpec` | `neqsim.process.research` | Declarative feed, targets, allowed operations, candidate/search caps, scoring, and optional metric modules. | `builder`, `setName`, `setFluidModel`, `setFeedTemperature`, `setFeedPressure`, `setFeedFlowRate`, `addFeedComponent`, `addProductTarget`, `addAllowedUnitType`, `build` |
| Product target / decision variable / synthesis constraints | `neqsim.process.research` | Defines material/stream/component goals, bounded decision variables/grid levels, and feasibility limits. | `ProductTarget.setMaterialName`, `setStreamRole`, `setComponentName`; `DecisionVariable.setGridLevels` |
| `ProcessCandidateGenerator`, `ProcessCandidateEvaluator` | `neqsim.process.research` | Generates JSON candidate definitions and optionally simulates/ranks them. | `generate`; evaluator `evaluate` (called by researcher when enabled) |
| `ProcessSynthesisGraph`, `MaterialNode`, `OperationOption` | `neqsim.process.research` | Models available materials and directed operations; generates material-continuous operation paths. | `addInputMaterial`, `addOutputMaterial`, `setProperty`, `addMaterialNode`, `addOperationOption` |
| `ProcessSynthesisTemplateLibrary` | `neqsim.process.research` | Adds curated conditioning templates to candidate path generation. | Enabled with `setIncludeSynthesisLibrary(true)` on the spec |
| `ReactionOption` | `neqsim.process.research` | Declares a candidate reaction route and stoichiometric coefficients for reactor candidate generation. | `setReactorType`, `setExpectedProductComponent`, `setReactorTemperature`, `addStoichiometricCoefficient` |
| `ProcessSynthesisFeasibilityPruner` | `neqsim.process.research` | Checks required reactants, targets, and path/material continuity before rigorous simulation. | `validateSpec`; `checkOperationPath` |
| `ProcessResearchMetrics`, weights, economic assumptions, robustness scenarios | `neqsim.process.research` | Accumulates product, energy, utility, cost proxy, emissions, complexity, and robustness metrics/weights. | Configure through spec builder and its nested configuration objects |
| `ProcessResearchResult` | `neqsim.process.research` | Retains successful and failed candidates, sorted results, messages, best-feasible and non-dominated views. | `getCandidates`, `getMessages`, `getBestCandidate`, `getNonDominatedCandidates` |
| `ProcessCandidate` | `neqsim.process.research` | Holds generated definition, evaluated process, score, objective values, errors, assumptions, path, and dominance metadata. | `getGenerationMethod`, `getSynthesisPath`, `getJsonDefinition`, `getProcessSystem`, `getMetrics`, `getObjectiveValues`, `isFeasible`, `getErrors`, `getScore` |
| `ProcessSuperstructureExporter` | `neqsim.process.research` | Produces external-optimizer handoff artifacts; it is not the optimizer. | `toJson`, `toPyomoSkeleton` |

## Build pattern

This source-tested example enumerates a separator candidate, simulates it, and ranks it. Add project-specific product limits, realistic feed state, allowed unit choices, and decision bounds before relying on a ranking.

```java
import neqsim.process.research.ProcessResearcher;
import neqsim.process.research.ProcessResearchResult;
import neqsim.process.research.ProcessResearchSpec;
import neqsim.process.research.ProcessCandidate;

ProcessResearchSpec spec = ProcessResearchSpec.builder()
    .setName("gas product from hydrocarbon feed")
    .setFluidModel("SRK")
    .setFeedTemperature(298.15)
    .setFeedPressure(20.0)
    .setFeedFlowRate(1000.0, "kg/hr")
    .addFeedComponent("methane", 0.90)
    .addFeedComponent("n-heptane", 0.10)
    .addProductTarget(new ProcessResearchSpec.ProductTarget("gas product")
        .setStreamRole("gas").setComponentName("methane").setMinFlowRate(1.0))
    .addAllowedUnitType("Separator")
    .addDecisionVariable(new ProcessResearchSpec.DecisionVariable(
        "feed", "flowRate", 1000.0, 2000.0, "kg/hr").setGridLevels(2))
    .build();

ProcessResearchResult result = new ProcessResearcher().research(spec);
ProcessCandidate best = result.getBestCandidate();
if (best == null || !best.isFeasible()) {
  throw new IllegalStateException("No feasible candidate; inspect result messages and candidate errors");
}
System.out.println(best.getGenerationMethod());
System.out.println(best.getScore());
System.out.println(best.getJsonDefinition());
```

Equivalent Java-class lookup through the NeqSim Python bridge:

```python
from neqsim import jneqsim

ProcessResearcher = jneqsim.process.research.ProcessResearcher
ProcessResearchSpec = jneqsim.process.research.ProcessResearchSpec

spec = (ProcessResearchSpec.builder()
    .setName("gas product from hydrocarbon feed")
    .setFluidModel("SRK")
    .setFeedTemperature(298.15)
    .setFeedPressure(20.0)
    .setFeedFlowRate(1000.0, "kg/hr")
    .addFeedComponent("methane", 0.90)
    .addFeedComponent("n-heptane", 0.10)
    .addProductTarget(ProcessResearchSpec.ProductTarget("gas product")
        .setStreamRole("gas").setComponentName("methane").setMinFlowRate(1.0))
    .addAllowedUnitType("Separator")
    .addDecisionVariable(ProcessResearchSpec.DecisionVariable(
        "feed", "flowRate", 1000.0, 2000.0, "kg/hr").setGridLevels(2))
    .build())

result = ProcessResearcher().research(spec)
best = result.getBestCandidate()
if best is None or not best.isFeasible():
    raise RuntimeError("No feasible candidate; inspect result messages and candidate errors")
print(best.getGenerationMethod(), best.getScore())
print(best.getJsonDefinition())
```

For graph synthesis, declare each `OperationOption` with material inputs/outputs and optional equipment properties, add matching `MaterialNode` and target material names, then run `research(spec)`. Set `setEvaluateCandidates(false)` when deliberately testing generation only; generated candidates then have not been rigorously simulated.

## Result extraction

| Output | Exact accessor | Units / interpretation |
|---|---|---|
| Candidate ordering | `ProcessResearchResult.getCandidates()` | Feasible candidates sort before infeasible; score descending within each group |
| Best / Pareto-like set | `getBestCandidate()`, `getNonDominatedCandidates()` | Best feasible may be `null`; non-dominated filtering is not a globally solved Pareto front |
| Candidate status | `isFeasible()`, `isOptimized()`, `isDominated()`, `getErrors()`, `getWarnings()` | Feasibility and evaluation are separate; failed candidates are retained for diagnosis |
| Search provenance | `getGenerationMethod()`, `getSynthesisPath()`, `getJsonDefinition()` | Test values include `reaction-route` and `process-network-graph`; path is the enumerated operation chain |
| Simulation result | `getProcessSystem()` | Available only for evaluated candidate(s); test asserts a feasible evaluated candidate has a process |
| Objectives/metrics | `getScore()`, `getObjectiveValues()`, `getMetrics()` | Examples include `feed.flowRate` in `kg/hr`, `totalPower_kW`, `heatingDuty_kW`, `capitalCostProxy_USD`, `annualOperatingCostProxy_USD_per_yr`, and `emissions_kgCO2e_per_hr` |
| Study-level diagnostics | `getMessages()` | Includes specification issues, empty-generation guidance, and other study-level notices |
| External formulation handoff | `ProcessSuperstructureExporter.toJson(spec)`, `toPyomoSkeleton(spec)` | JSON plus a Pyomo/GDP skeleton, not a solver result |

## Gotchas

- Candidate generation is bounded by `maxCandidates`, `maxSynthesisDepth`, and `maxOptimizationCases`; a candidate omitted by those limits is not evidence of infeasibility.
- The search space is allowed operations plus generated/curated paths. It is not unrestricted invention of unit operations or a full-space MINLP over all NeqSim equipment.
- Evaluation is controlled by `setEvaluateCandidates`. With it false, generated JSON/path existence does not prove convergence, mass balance, product quality, or feasibility.
- A reaction route is a candidate definition, not a reaction-kinetics discovery. The tested hydrogen route uses a declared `ReactionOption`; a separate JSON-builder test checks that `GibbsReactor` can be instantiated.
- The built-in ranking's product score and secondary energy penalty are not a user-specified economic optimization unless scoring weights, enabled metric modules, and hard constraints are configured and checked.
- Cost and emissions outputs are named proxies/estimates. They are not a vendor quote, life-cycle assessment, tax model, or approved investment case.
- `getBestCandidate()` returns `null` when no candidate is feasible. Inspect all retained candidates' errors/messages before changing constraints or declaring no process route exists.
- Feasibility pruning deliberately rejects broken material continuity and missing reaction reactants. Correct the declared feed/material/operation graph; do not disable the pruner just to obtain a candidate.
- Candidate JSON must still be checked for unit properties and run warnings. Use the normal process modeling and troubleshooting skills for convergence and physical validation.
- `toPyomoSkeleton()` includes a `Disjunct` starter, but does not create a complete objective/constraint model, select a solver, or execute Pyomo/GDP.

## Validation / benchmarks

- Use the `ProcessResearcherTest` patterns: one small evaluated case, one graph-path generation case, one `setEvaluateCandidates(false)` generation-only case, and explicit checks of no-candidate/infeasible behavior.
- For graph paths, assert every operation input is reachable from the feed and every target material is reached; inspect `getSynthesisPath()` and the generated JSON before running the simulation.
- For every evaluated candidate, inspect `getErrors()`, warnings, `isFeasible()`, the resulting `ProcessSystem`, product phase/component flow, and total/component mass and energy balances.
- Benchmark candidate predictions against independent reference/process data or a trusted detailed model. Preserve the exact feed, EOS, mixing rule, candidate-generation method, decision grid, and objective weights.
- Compare shortlisted candidates with `neqsim-optimization-and-doe` only after fixing topology; that skill's flowsheet optimizer is for operating decisions within an established process structure.
- Treat exported Pyomo/GDP results as unverified until the external formulation is completed, the selected solver converges, and the chosen candidate is rebuilt and checked in NeqSim.

## Related skills

- `neqsim-optimization-and-doe`
- `neqsim-process-modeling`
- `neqsim-reaction-engineering`
- `neqsim-process-extraction`
- `neqsim-input-validation`
- `neqsim-troubleshooting`
- `neqsim-professional-reporting`