---
name: neqsim-pipeline-and-terminal-networks
description: "Guides agents through NeqSim pipeline and terminal networks. USE WHEN: users ask to solve a looped gathering/export network, optimize well and choke settings, apply nominations or quality limits, track gas linepack or transient composition, blend crude, or schedule terminal tanks and cargoes. Anchors: LoopedPipeNetwork, NetworkOptimizer, TransientGasNetwork, TransientCompositionalPipeNetwork, NetworkQualityProfile, OilNetworkSchedule."
last_verified: "2026-10-03"
---

# Pipeline and Terminal Networks

## When to use this

- Solve a gas gathering, ring-main, trunkline, or export network with fixed-pressure or demand boundaries.
- Optimize well IPR, choke, compressor, pump, regulator, or routing decisions already represented by network elements.
- Schedule pressure, rate, availability, and quality nominations over multiple periods.
- Track linepack and composition response to time-varying gas sources.
- Blend crude assays and schedule tank receipts, inventory, and cargo loading.
- Use the dedicated transient two-fluid network when the case needs gas-liquid pipe inventory states.

Do not substitute the field-development `NetworkSolver` or a `Manifold` for `LoopedPipeNetwork` without checking the question. They are distinct APIs. Use a `Manifold` for stream routing in a conventional process flowsheet; the network package solves its own node-edge model.

## Well/SURF hydraulic fidelity

For campaign #4228, extend the detailed `neqsim.process.equipment.network.LoopedPipeNetwork`
graph instead of creating a competing SURF solver. Review
`docs/fielddevelopment/SURF_NETWORK_INTEGRATION.md` and coordinate equipment/design
with `neqsim-subsea-and-wells` and the field-development agent.
Use `NetworkPipe.setHydraulicModelType` for PIPE/MULTIPHASE_PIPE edge overrides
(DARCY_WEISBACH, BEGGS_BRILL, TWO_FLUID); select NEWTON_RAPHSON and inspect
`getHydraulicModelStatus` plus `getTwoFluidModel().getSteadyStateConvergenceReport()`.
Changing fidelity preserves identity and geometry. This is steady-state composition;
it does not qualify mixed-fidelity transients or directly bind a live WellSystem.
The dedicated `TwoFluidPipeNetwork` already owns storage-node transients; do not
replace it. Live well/injection coupling must reuse WellSystem/WellFlow physics.

## Class map

| Class family | Package | What it does | Verified entry points / result methods |
|---|---|---|---|
| Steady looped network: `LoopedPipeNetwork`, `LoopDetector`, `NetworkLoop` | `neqsim.process.equipment.network` | Node-edge gas or multiphase gathering/export model; discovers loops and solves hydraulic operating points. | `setFluidTemplate`, `addSourceNode`, `addJunctionNode`, `addFixedPressureSinkNode`, `addPipe`, `run`, `getNodePressure`, `getTotalSinkFlow` |
| Hydraulic details: `NetworkLinearSolver`, `NetworkPipe`, pipe-model bindings | `neqsim.process.equipment.network` | Linear solve helpers and per-edge hydraulic state; edges may use Darcy-Weisbach or a configured `PipeBeggsAndBrills` model. | `NetworkLinearSolver.solve`, `NetworkLinearSolver.solveGaussian`, `NetworkPipe.getFlowRate`, `NetworkPipe.getBBModel` |
| Well and facility elements | `neqsim.process.equipment.network` | IPR, tubing, choke, compressor, pump, regulator, artificial-lift, and related source/edge elements within the network. | `addWellIPR`, `addChoke`; configure through `NetworkPipe` element-specific setters |
| Constrained optimization: `NetworkOptimizer`, `NetworkConstraints`, `NetworkObjectives`, `NetworkDecisionVariable` | `neqsim.process.equipment.network` | Bounded decision search, objectives, constraints, and production-versus-power Pareto candidates. | `createOptimizer`, `optimizeProductionNLP`, `optimizeMultiObjective`, `NetworkOptimizer.optimize` |
| Period and nomination planning: `NetworkPlanningHorizon`, `NetworkNomination`, `NetworkPeriod`, `NetworkAvailabilitySchedule` | `neqsim.process.equipment.network` | Period-indexed demand/supply basis, edge availability, and planning-horizon feasibility inputs. | `NetworkNomination.getValue(int)`, `NetworkNomination.getUnit`, `NetworkPeriod.getStart`, `NetworkPeriod.getEnd` |
| Quality tracking and mixing: `NetworkQualitySpecification`, `NetworkQualityProfile`, `NetworkQualityLimit`, `NetworkQualityEvaluator`, `NetworkQualityComplianceReport`, `NetworkMixingResult` | `neqsim.process.equipment.network` | Point-specific calculated and measured attributes, component limits, mixing evidence, and compliance status. | `NetworkQualityProfile.addUpperLimit`, `addRange`, `addMeasuredAttributeLimit`, `LoopedPipeNetwork.evaluateQualityProfiles`, `report.isCompliant`, `report.getResults` |
| Gas linepack and transient history: `GasLinepackState`, `TransientGasNetwork`, `TransientGasNetworkHistory`, `TransientGasNetworkStepReport` | `neqsim.process.equipment.network` | Advances edge inventory and solves transient gas pressure, flow, composition, and conservation histories. | `GasLinepackState.fromSolvedState`, `GasLinepackState.advance`, `setSourceSchedule`, `setFixedPressureBoundary`, `run`, `getHistory` |
| Prescribed-flow composition transport: `TransientCompositionalPipeNetwork`, `TransientCompositionalPipeNetworkHistory`, `TransientSpeciesConservationReport` | `neqsim.process.equipment.network` | Tracks named component mass fractions through finite-volume gas-pipe cells and junctions. | `addNode`, `addPipe`, `setSourceSchedule`, `run`, `getSpeciesHistory`, `getNodeMassFractionHistory` |
| Two-fluid transient pipes: `TwoFluidPipeNetwork`, `TwoFluidPipe` | `neqsim.process.equipment.network` and `neqsim.process.equipment.pipeline` | Couples network nodes to two-fluid pipe state and reports phase mass balances. | `addCompressibleNode`, `addFixedPressureNode`, `addPipe`, `runTransient`, `getNodePressurePa`, `getLastBalanceReport` |
| Oil terminal, cargo, and assay: `OilNetworkSchedule`, `OilTerminalNode`, `OilTerminalTank`, `CrudeAssay`, `CrudeParcel`, `CargoNomination`, `CrudeBlendResult` | `neqsim.process.equipment.network` | Receipts, segregated or mixed inventory, compatible assay blending, and cargo scheduling by period/berth. | `addHourlyPeriods`, `addReceipt`, `addCargoNomination`, `OilNetworkSchedule.optimize`, `CrudeAssay.blend`, `getMassBalanceResidualKg` |
| Benchmarks and adjacent equipment: `NetworkValidationBenchmarks`, `Manifold`, `PipeBeggsAndBrills` | `neqsim.process.equipment.network`, `neqsim.process.equipment.manifold`, `neqsim.process.equipment.pipeline` | Reproducible network checks; ordinary process-manifold routing and standalone multiphase pipe calculations remain separate. | `NetworkValidationBenchmarks.runAllBenchmarks`, `Manifold.addStream`, `PipeBeggsAndBrills.run` |

### Family data flow

- **Steady looped solve:** fluid template and node/edge data -> loop detection -> selected hydraulic solver -> node/edge flow and pressure results -> mass-balance review.
- **Constrained optimization and nomination:** solved network plus bounded decision variables/objective/constraints -> candidate evaluations -> selected operating point or period schedule. A feasible candidate is not an operating approval.
- **Quality and blending:** node fluid plus a versioned quality profile and explicit reference conditions -> per-attribute results -> compliance report. Missing measured values remain `NOT_CALCULABLE`; they are not inferred from EOS composition.
- **Gas linepack transient:** pipe geometry and initial fluid state plus source event schedules and boundary pressures -> timestep pressure/flow/linepack/composition -> step conservation reports.
- **Oil terminal and cargo:** common-slate assays -> mass-bearing parcels -> tank receipt/withdrawal and availability constraints -> cargo loading, ending inventory, component balance, and quality reports.
- **Compositional transient:** single-gas-phase pipe states plus named-component source events -> finite-volume cell inventories -> node/edge composition histories and conservation reports.

## Build pattern

This tested pattern creates a two-well gathering network and optimizes choke openings. Pressures passed to node helpers are bara; geometry is SI; source flow is kg/hr. The source template needs a database and a mixing rule.

```java
SystemInterface gas = new SystemSrkEos(298.15, 50.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.07);
gas.addComponent("propane", 0.03);
gas.createDatabase(true);
gas.setMixingRule("classic");
gas.init(0);
gas.init(1);

LoopedPipeNetwork network = new LoopedPipeNetwork("gathering");
network.setFluidTemplate(gas);
network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
network.setMaxIterations(200);
network.setTolerance(100.0);
network.addSourceNode("res", 200.0, 0.0);
network.addJunctionNode("wellhead");
network.addJunctionNode("manifold");
network.addFixedPressureSinkNode("export", 50.0);
network.addWellIPR("res", "wellhead", "ipr", 5.0e-6, false);
network.addChoke("wellhead", "manifold", "choke", 50.0, 80.0);
network.addPipe("manifold", "export", "export", 20000.0, 0.30, 0.00005);

NetworkOptimizer.OptimizationResult result = network.optimizeProductionNLP();
if (!result.converged) {
  throw new IllegalStateException(result.message);
}
double productionKgHr = result.totalProductionKgHr;
```

Equivalent Python class lookup:

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
LoopedPipeNetwork = jneqsim.process.equipment.network.LoopedPipeNetwork

gas = SystemSrkEos(298.15, 50.0)
gas.addComponent("methane", 0.90)
gas.addComponent("ethane", 0.07)
gas.addComponent("propane", 0.03)
gas.createDatabase(True)
gas.setMixingRule("classic")
gas.init(0)
gas.init(1)

network = LoopedPipeNetwork("gathering")
network.setFluidTemplate(gas)
network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON)
network.setMaxIterations(200)
network.setTolerance(100.0)
network.addSourceNode("res", 200.0, 0.0)
network.addJunctionNode("wellhead")
network.addJunctionNode("manifold")
network.addFixedPressureSinkNode("export", 50.0)
network.addWellIPR("res", "wellhead", "ipr", 5.0e-6, False)
network.addChoke("wellhead", "manifold", "choke", 50.0, 80.0)
network.addPipe("manifold", "export", "export", 20000.0, 0.30, 0.00005)
result = network.optimizeProductionNLP()
production_kg_hr = result.totalProductionKgHr
```

For transient gas, follow `TransientGasNetworkTest`: define nodes and pipe cells, supply `setSourceSchedule(...)`, set a fixed-pressure boundary with an explicit unit, then call `run(durationSeconds, timeStepSeconds)`. That workflow is distinct from the optimizer example above.

## Result extraction

- `LoopedPipeNetwork.getNodePressure(name)` returns **bara**; `getTotalSinkFlow()` returns **kg/s**. Convert the latter to kg/hr only when needed.
- `NetworkOptimizer.OptimizationResult.totalProductionKgHr` is **kg/hr**; `totalCompressorPowerKW` is **kW**; inspect `converged` and `message` before using candidate values.
- `TransientGasNetworkHistory.getNodePressureBaraHistory(name)`, `getEdgeInletMassFlowKgSHistory(name)`, `getEdgeOutletMassFlowKgSHistory(name)`, and `getEdgeLinepackKgHistory(name)` are explicitly unit-labelled arrays.
- Composition histories from `getNodeMassFractionHistory(node, component)` are mass fractions, not mole fractions.
- `NetworkQualityComplianceReport.isCompliant()` and `getResults()` expose status, value, margin, method, and `NOT_CALCULABLE` cases.
- `OilNetworkScheduleResult.getMassBalanceResidualKg()` and `getMaxComponentBalanceResidualKg()` are closure checks in kg; `getCargoes()` and `getTerminalInventories()` provide the scheduled outcomes.

## Gotchas

- `LoopedPipeNetwork.run()` needs at least one edge and a fluid template; with no pipes it returns without solving. It throws if the template is missing.
- The node helpers take bara, while `NetworkNode.setPressure` is an internal SI-Pa value. Pipe dimensions are m; network pipe rates are internally kg/s. Do not mix helper and internal-node units.
- A fixed-pressure sink asks the solver to find deliverable flow. A rate sink has a prescribed demand. Do not specify both interpretations for one delivery point.
- An IEC valve choke model requires `SolverType.NEWTON_RAPHSON`; the source throws an explicit `IllegalStateException` otherwise.
- Transient gas/composition schedules use seconds and kg/s. Reverse flow and phase appearance are rejected in the prescribed-flow composition solver; the gas transient solver also rejects infeasible source pressure and edge capacity.
- Quality profiles cannot calculate a measured property such as assay sulfur without supplying the measured attribute. Explicitly report `NOT_CALCULABLE` rather than treating it as a pass.
- Oil terminal assays must use the same `commonSlateId` before blending; segregated tanks do not silently mix parcels to satisfy a cargo.
- No `validateSetup()` override is present in this network family. Validate topology, convergence, source/sink balance, and each step report directly.

## Validation / benchmarks

- `NetworkValidationBenchmarks.runAllBenchmarks()` includes analytical flow cases, solver cross-checks, pressure behavior, and sparse/dense linear-solver comparisons. Benchmark cases are validation examples, not a universal uncertainty bound.
- Regression tests include `LoopedPipeNetworkTest`, `NetworkOptimizerTest`, `NetworkQualitySpecificationTest`, `TransientGasNetworkTest`, `TransientCompositionalPipeNetworkTest`, `OilNetworkScheduleTest`, and `NetworkValidationBenchmarkTest`.
- Transient results should be checked for per-step convergence and mass/component closure. Refine both pipe cells and timestep and compare common-time histories before relying on a pulse peak or arrival time.
- These models do not establish pipeline code compliance, site-specific operating limits, or acceptance of a cargo specification. Supply controlled limits and independent review.

## Related skills

- `neqsim-production-optimization` — existing `LoopedPipeNetwork` optimization recipe; this skill adds planning, quality, terminal, and transient workflows.
- `neqsim-field-development` — its field-development `NetworkSolver` is a separate API.
- `neqsim-process-modeling` — process-system `Manifold` routing and equipment wiring.
- `neqsim-flow-assurance` — hydrate, corrosion, and pipeline threat assessment.
- `neqsim-api-patterns` — fluid initialization and units.