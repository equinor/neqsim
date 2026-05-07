---
title: Process Researcher
description: Generate, simulate, optimize, and rank candidate process flowsheets from feed and product targets, including reaction-route candidates.
---

The process researcher is an early-stage process synthesis API. It answers a
different question than a normal process optimizer:

| Question | Main API |
|----------|----------|
| Tune variables in one existing flowsheet | `ProductionOptimizer`, `SQPoptimizer`, `ProcessSimulationEvaluator` |
| Generate and rank possible flowsheets from feed and product targets | `ProcessResearcher` |

The implementation is intentionally hybrid. NeqSim remains the rigorous
simulation engine, while the process researcher builds a material-operation
superstructure, generates candidate flowsheet JSON, rejects weak or failed
candidates, applies bounded variable screening, records heat/cost/emissions
metrics, and ranks the surviving processes.

## State-Of-Art Positioning

State-of-art process synthesis is not one monolithic optimizer. In practice it
combines several layers:

| Layer | What `ProcessResearcher` does |
|-------|-------------------------------|
| Process-network representation | Uses `MaterialNode`, `OperationOption`, `ReactionOption`, and `ProcessSynthesisGraph` for P-graph-style material-operation paths |
| Feasibility pruning | Applies `ProcessSynthesisFeasibilityPruner` before rigorous simulation to reject missing reactants, invalid targets, and impossible graph paths |
| Curated synthesis library | Uses `ProcessSynthesisTemplateLibrary` to add common conditioning trains such as compression, aftercooling, separation, heating, cooling, and pressure letdown |
| Rigorous validation | Converts every surviving candidate to NeqSim JSON and runs `ProcessSystem.fromJsonAndRun` |
| Continuous variable screening | Uses `ProcessSimulationEvaluator` for bounded grid screening of decision variables |
| Multi-objective ranking | Uses `ProcessResearchMetrics` with product flow, purity, power, hot/cold utility, cost proxy, emissions, complexity, and robustness terms |
| Heat integration | Optionally runs `PinchAnalysis.fromProcessSystem` to estimate minimum hot/cold utility and heat recovery |
| Acceptance constraints | Rejects simulated candidates that exceed hard limits for equipment count, power, utilities, cost proxy, emissions, or operating cost |
| External superstructure optimization | Exports reduced JSON and a Pyomo/GDP starter through `ProcessSuperstructureExporter` for MINLP/GDP workflows |
| Decision transparency | Keeps assumptions, warnings, failed candidates, synthesis paths, metrics, and dominance flags in `ProcessCandidate` |

That makes the API a state-of-art foundation: it combines graph enumeration,
physics-based pruning, rigorous simulation, multi-objective ranking, heat
integration, and external optimizer handoff. It still deliberately avoids
claiming unrestricted chemistry discovery or native full-space MINLP over every
NeqSim unit model.

## When To Use It

Use `ProcessResearcher` when you know the feed and desired products, but still
want to screen possible process structures:

- phase split vs compression vs reactor-plus-separation candidates,
- product recovery or purity screening,
- reaction-route candidates using `GibbsReactor`, `PlugFlowReactor`, or
  `StirredTankReactor`,
- early candidate ranking before a detailed design study,
- AI-assisted process synthesis where every suggestion must be validated by a
  deterministic NeqSim process simulation.

Use the lower-level optimization tools directly when the flowsheet topology is
already fixed.

## Architecture

The API lives in `neqsim.process.research` and uses existing NeqSim building
blocks:

| Layer | Classes | Role |
|-------|---------|------|
| Specification | `ProcessResearchSpec`, `ProductTarget`, `DecisionVariable` | Feed, product, allowed units, objective, search bounds |
| Candidate generation | `ProcessCandidateGenerator`, `ProcessSynthesisGraph`, `OperationOption`, `ReactionOption` | Build template and graph-enumerated NeqSim JSON definitions |
| Template library | `ProcessSynthesisTemplateLibrary` | Add curated conditioning operations when the study asks for automatic topology expansion |
| Feasibility pruning | `ProcessSynthesisFeasibilityPruner` | Reject invalid specs, broken graph paths, and reaction routes before simulation |
| Simulation and ranking | `ProcessCandidateEvaluator`, `ProcessResearcher` | Run `ProcessSystem.fromJsonAndRun`, score, mark dominated candidates, and sort |
| Metrics and constraints | `ProcessResearchMetrics`, `ScoringWeights`, `SynthesisConstraints`, `EconomicAssumptions` | Product, energy, heat integration, cost proxy, emissions, complexity, robustness, hard acceptance limits |
| External optimization | `ProcessSuperstructureExporter` | JSON and Pyomo/GDP skeleton for external superstructure optimization |
| Results | `ProcessCandidate`, `ProcessResearchResult` | JSON, process system, score, warnings, errors, metrics, dominance flags |

Generated candidates are regular NeqSim JSON process definitions. This keeps the
feature compatible with the [JSON process builder](../../integration/web_api_json_process_builder.md)
and with extracted process descriptions from documents or AI agents.

## Java Example

This example screens a gas-product candidate from a hydrocarbon feed and lets the
researcher choose between two feed-flow levels.

```java
ProcessResearchSpec spec = ProcessResearchSpec.builder()
    .setName("gas product from hydrocarbon feed")
    .setFluidModel("SRK")
    .setFeedTemperature(298.15)
    .setFeedPressure(20.0)
    .setFeedFlowRate(1000.0, "kg/hr")
    .addFeedComponent("methane", 0.90)
    .addFeedComponent("n-heptane", 0.10)
    .addProductTarget(new ProcessResearchSpec.ProductTarget("gas product")
        .setStreamRole("gas")
        .setComponentName("methane")
        .setMinFlowRate(1.0))
    .addAllowedUnitType("Separator")
    .addDecisionVariable(new ProcessResearchSpec.DecisionVariable(
        "feed", "flowRate", 1000.0, 2000.0, "kg/hr").setGridLevels(2))
    .build();

ProcessResearchResult result = new ProcessResearcher().research(spec);
ProcessCandidate best = result.getBestCandidate();

System.out.println(best.getName());
System.out.println(best.getScore());
System.out.println(best.getJsonDefinition());
```

The example is covered by `ProcessResearcherTest` so the documented API is kept
compilable.

## Graph-Based Synthesis

Use material names when you want the researcher to enumerate process-network
paths instead of only testing one operation at a time.

```java
OperationOption compression = new OperationOption("compression", "Compressor")
  .addInputMaterial("feed gas")
  .addOutputMaterial("compressed gas")
  .setProperty("outletPressure", 40.0, "bara");

OperationOption separation = new OperationOption("polishing separator", "Separator")
  .addInputMaterial("compressed gas")
  .addOutputMaterial("sales gas");

ProcessResearchSpec spec = ProcessResearchSpec.builder()
  .setFeedMaterialName("feed gas")
  .addFeedComponent("methane", 0.90)
  .addFeedComponent("n-heptane", 0.10)
  .addProductTarget(new ProcessResearchSpec.ProductTarget("sales gas")
    .setMaterialName("sales gas")
    .setStreamRole("gas")
    .setComponentName("methane"))
  .addAllowedUnitType("Compressor")
  .addAllowedUnitType("Separator")
  .addOperationOption(compression)
  .addOperationOption(separation)
  .build();
```

The graph layer enumerates bounded paths such as feed gas to compressed gas to
sales gas. The resulting candidate still runs as a normal NeqSim `ProcessSystem`.

## Curated Synthesis Library

For broader early screening, enable the built-in template library. It adds common
conditioning operations to the graph search without requiring every operation to
be written by hand.

```java
ProcessResearchSpec spec = ProcessResearchSpec.builder()
  .setName("template library screen")
  .setFeedMaterialName("raw gas")
  .setFeedTemperature(310.0)
  .setFeedPressure(25.0)
  .addFeedComponent("methane", 0.85)
  .addFeedComponent("n-heptane", 0.15)
  .addProductTarget(new ProcessResearchSpec.ProductTarget("sales gas")
    .setMaterialName("sales gas")
    .setStreamRole("gas")
    .setComponentName("methane"))
  .addAllowedUnitType("Compressor")
  .addAllowedUnitType("Cooler")
  .addAllowedUnitType("Separator")
  .setIncludeSynthesisLibrary(true)
  .build();
```

With those allowed units, the library contributes a compression, aftercooling,
and polishing-separator train. The generated graph paths are still checked for
serial material continuity before simulation.

## Multi-Objective Ranking

The default score remains product-focused for backwards compatibility. For
concept screening, add explicit penalties and enable the metric modules that are
relevant to the study.

```java
ProcessResearchSpec.ScoringWeights weights = new ProcessResearchSpec.ScoringWeights()
  .setElectricPowerPenalty(0.01)
  .setHotUtilityPenalty(0.01)
  .setComplexityPenalty(1.0);

ProcessResearchSpec spec = ProcessResearchSpec.builder()
  .addFeedComponent("methane", 1.0)
  .addProductTarget(new ProcessResearchSpec.ProductTarget("heated gas")
    .setStreamReference("feed heater.outlet")
    .setComponentName("methane"))
  .addAllowedUnitType("Heater")
  .addOperationOption(new OperationOption("feed heater", "Heater")
    .setProperty("outletTemperature", 330.0, "K"))
  .setScoringWeights(weights)
  .setIncludeHeatIntegration(true)
  .setIncludeCostEstimate(true)
  .setIncludeEmissionEstimate(true)
  .build();
```

Candidate metrics are available from `best.getMetrics().asMap()` and are also
mirrored into `best.getObjectiveValues()` for notebook and agent workflows.

## Hard Acceptance Constraints

Ranking penalties are useful when a candidate is merely unattractive. Hard
constraints are used when a candidate should be rejected from the feasible set.

```java
ProcessResearchSpec.SynthesisConstraints constraints =
    new ProcessResearchSpec.SynthesisConstraints()
        .setMaxEquipmentCount(5.0)
        .setMaxTotalPowerKW(2000.0)
        .setMaxEmissionsKgCO2ePerHr(800.0);

ProcessResearchSpec spec = ProcessResearchSpec.builder()
  .addFeedComponent("methane", 1.0)
  .addProductTarget(new ProcessResearchSpec.ProductTarget("gas")
    .setStreamRole("gas")
    .setComponentName("methane"))
  .setSynthesisConstraints(constraints)
  .build();
```

Candidates exceeding a hard limit are marked infeasible and retain an error
message identifying the violated metric.

## Reaction-Route Candidates

Reaction candidates are generated from `ReactionOption`. The first implementation
does not attempt unrestricted chemistry discovery. Instead, it provides a curated
route hook for reaction families where the user or a higher-level agent supplies
the plausible route and operating window.

```java
ReactionOption reaction = new ReactionOption("steam methane reforming screen")
    .setReactorType("GibbsReactor")
    .setExpectedProductComponent("hydrogen")
    .setReactorTemperature(1000.0)
    .addStoichiometricCoefficient("methane", -1.0)
    .addStoichiometricCoefficient("water", -1.0)
    .addStoichiometricCoefficient("hydrogen", 3.0)
    .addStoichiometricCoefficient("CO", 1.0);
```

The generated candidate is a preheater, reactor, and separator sequence when the
allowed units include `Heater`, `GibbsReactor`, and `Separator`. For kinetic
routes, use `PlugFlowReactor` or `StirredTankReactor` after adding the required
reaction data to the process model.

Reaction routes are checked before simulation. A route with a negative
stoichiometric coefficient for a reactant not present in the feed is marked as a
pruned candidate with an error message instead of being sent to the simulator.

## External Optimizer Handoff

For larger synthesis studies, export the reduced superstructure and solve the
discrete topology problem in an external GDP/MINLP environment. NeqSim then
evaluates selected candidate flowsheets rigorously.

```java
ProcessSuperstructureExporter exporter = new ProcessSuperstructureExporter();
String superstructureJson = exporter.toJson(spec);
String pyomoSkeleton = exporter.toPyomoSkeleton(spec);
```

The exported model is intentionally a reduced representation: material nodes,
operation choices, reaction routes, decision-variable bounds, and scoring
weights. It is the correct interface for Pyomo GDPopt, SCIP, BARON, Couenne,
Bonmin, or logic-based decomposition workflows.

## Literature Basis

The design follows mature process-synthesis practice rather than a single large
black-box optimizer:

- Rudd, Powers, and Siirola, *Process Synthesis*, 1973.
- Douglas, *Conceptual Design of Chemical Processes*, 1988.
- Friedler, Tarjan, Huang, and Fan, combinatorial algorithms for process
  synthesis, 1992. This is the P-graph/process-network foundation.
- Raman and Grossmann, logic-based integer programming and generalized
  disjunctive programming, 1994.
- Biegler, Grossmann, and Westerberg, *Systematic Methods of Chemical Process
  Design*, 1997.
- Smith, *Chemical Process Design and Integration*, 2005/2016.
- Mencarelli and coauthors, reviews of superstructure optimization in process
  systems engineering, around 2020.
- Reaction Mechanism Generator and retrosynthesis literature for curated
  reaction-network generation and route planning.

The practical conclusion from this literature is to combine templates,
P-graph-style candidate enumeration, dominance pruning, rigorous simulation, and
continuous optimization. Full native MINLP/GDP over detailed NeqSim unit models is
not the recommended first step because phase changes, recycle convergence, and
equipment model failures make the optimization problem non-smooth.

## Recommended Workflow

1. Define feed, target products, product roles, and allowed units in
   `ProcessResearchSpec`.
2. Add curated `ReactionOption` objects for chemistry routes that are in scope.
3. Add `OperationOption` objects for custom process-network steps.
4. Add bounded `DecisionVariable` objects only for variables with physically
   meaningful limits.
5. Run `ProcessResearcher.research(spec)`.
6. Inspect `ProcessResearchResult.getBestCandidate()` and the warnings/errors on
   rejected candidates.
7. Use the generated JSON as a starting point for detailed process optimization,
   mechanical design, cost estimation, safety, and operability review.

## Current Scope And Gaps

Implemented now:

- feed/product specification,
- graph-based material-operation candidate generation,
- curated synthesis template library,
- pre-simulation feasibility pruning,
- material-continuity checks for graph paths,
- built-in separator and gas-compression candidates,
- explicit operation-option candidates,
- reaction-route candidates,
- JSON generation through the existing process builder format,
- simulation and ranking of generated candidates,
- bounded grid screening with `ProcessSimulationEvaluator`,
- structured multi-objective metrics,
- optional pinch-analysis heat-integration metrics,
- cost and emissions screening proxies,
- hard synthesis acceptance constraints,
- robustness scenario hooks,
- dominated-candidate marking,
- external superstructure JSON and Pyomo/GDP skeleton export,
- generic JSON factory support for `GibbsReactor`, `PlugFlowReactor`, and
  `StirredTankReactor`.

Important gaps to keep explicit:

- no unrestricted reaction-network generation,
- no native GDP/MINLP superstructure solver,
- no automatic heat-exchanger-network synthesis beyond pinch targeting yet,
- cost and emissions are screening proxies unless detailed design/cost classes
  are run downstream,
- no automatic recycle-structure synthesis beyond generated JSON candidates.

The intended next step is to add a richer operation library and dominance rules,
then bridge simplified superstructures to external MINLP/GDP tools while keeping
NeqSim as the final rigorous evaluator.

## Related Documentation

- [Optimization Overview](OPTIMIZATION_OVERVIEW.md)
- [Optimization and Constraints Guide](OPTIMIZATION_AND_CONSTRAINTS.md)
- [Batch Studies](batch-studies.md)
- [External Optimizer Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION.md)
- [Reactors](../equipment/reactors.md)
- [Process Design Guide](../process_design_guide.md)
