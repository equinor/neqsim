---
name: neqsim-pipeline-and-terminal-networks
description: "Guides agents through NeqSim pipeline and terminal networks. USE WHEN: users ask to solve a looped gathering/export network, optimize well and choke settings, apply nominations or quality limits, track gas linepack or transient composition, blend crude, or schedule terminal tanks and cargoes. Anchors: LoopedPipeNetwork, NetworkOptimizer, TransientGasNetwork, TransientCompositionalPipeNetwork, NetworkQualityProfile, OilNetworkSchedule."
last_verified: "2026-10-20"
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

## Supplied VFPPROD pipeline hydraulics

Use `VfpPipeline` when approved OLGA/Prosper/NeqSim VFPPROD tables define pipeline
hydraulics between process areas. Read `docs/process/equipment/vfp_pipeline.md`.
Load `VfpTable` and explicitly set outlet temperature and a `VfpTableArguments`
provider. The provider returns total gas Sm3/day, WGR and canonical GOR; the unit
converts GOR to OGR only for a declared OGR table. `StandardConditionsFlash` uses
EOS phase volumes at required constructor T/P, which differs from the ideal-molar
stream Sm3/day conversion. Match provider metering/separator definitions first.
Forward mode inverts THP; backward mode reports required BHP without mutating the
inlet. Tables are range-limited and have no network balancing or thermal model.
Synthetic tests qualify interpolation/software contracts, not field hydraulics.

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
Use `NetworkHydraulicModelComparison` to replay selected canonical edges with both
Beggs-Brill and two-fluid hydraulics. Its normalized position/pressure/temperature/
holdup/phase-velocity profiles include segmented terrain routes, and
`compareMeshSensitivity` evaluates selected edges on detached meshes without changing
the caller topology. Inspect every convergence status and mass-balance residual; these
comparisons are integration evidence until independently qualified.
Use `FieldNetworkTopology` for stable production/injection node, edge, tag and port
identity over that same graph. Its typed `addChoke`, `addPump`,
`addPumpDifferentialPressure` and `addCompressor` methods create the existing canonical
network elements atomically; they do not add a second solver. Rebind external equipment
and fluids after JSON replay, and distinguish synthetic conservation/runtime evidence
from qualified pump/compressor maps or field hydraulics.
For screening design and cost, pass that same topology to
`SubseaProductionSystem.createSurfDesignBasis` or `estimateSurfCosts`. The immutable
`FieldNetworkSurfDesignBasis` reads typed equipment counts and exact canonical edge
geometry without rebuilding hydraulics; the estimator prices physical line edges
separately while retaining length-weighted summary getters. Use
`NetworkDecisionVariable.pipeDiameter` with
`SubseaProductionSystem.createSurfCapitalCostObjective` for deterministic all-discrete
hydraulic/CAPEX screening. The optimizer rejects incomplete enumeration and mixed
continuous/discrete searches. Register `SURFCostEstimator.LineDesign` by canonical
edge ID with `SubseaProductionSystem.setLineDesign` when route-specific OD, wall,
material, installation, rigid/flexible and insulation inputs are available. Rigid
designs are checked against hydraulic ID and reuse existing pipeline mechanical
screening; they are not current-edition DNV, vendor or AFC qualification.
Use the typed `LineDesign.insulation(MaterialType, thicknessMm)` overload plus
`thermalBoundary` for physical steady heat-transfer input, then call
`SubseaProductionSystem.applyLineThermalDesigns` on the same topology. The legacy
string insulation overload is cost metadata only. Flexible lines require an explicit
qualified U-value. Add the returned `createMinimumOutletTemperatureConstraints`
constraints to the existing optimizer; they fail closed without converged thermal state.
Uniform design input must not replace an existing route-dependent U/ambient profile.
Attach complete `DnvStF101PipelineDesignInput` and
`DnvRpF109OnBottomStabilityInput` objects to `LineDesign` when structural and
stability screening is required. Evaluate them with
`assessDnvStF101LineDesigns` and `assessOnBottomStabilityLineDesigns`; all
material, fabrication, load, environmental and soil values remain explicit
project inputs outside the hydraulic topology. Treat results as review-required
screening, never conformity, independent verification or AFC approval.
For exact staged rigid-route studies, pass complete `LineDesign` alternatives to
`createLineDesignDecisionVariable` and add `createLineDesignScreeningConstraints`
to the existing `NetworkOptimizer`. One selection transaction updates canonical
hydraulic ID, wall, thermal, material/installation/cost and attached screening
input while preserving the edge hydraulic model. Candidate indices are categorical,
minimum-temperature requirements must be invariant, and flexible/vendor designs are
outside this contract. Never combine this selector with a second diameter variable
on the same edge or treat a passing screen as qualified design.
For coherent direct, brownfield and redundant-loop line-ups, register named
`FieldWellNetworkProcessUnit.RouteConfiguration` options on the existing process-unit
adapter. `configuration.<selector>.selection` is a categorical `ProcessAutomation`
input: one write atomically assigns exact zero/one availability to the complete
controlled edge set and preserves all per-edge hydraulic models. Do not emulate a
multi-edge line-up with independent availability actions; that permits partial
configurations during candidate application and restoration.
The same selector works for live `WellFlow` injection networks. Update a zone's
lifecycle boundary through `WellFlow.ReservoirLayer.setReservoirPressure(...)` so
the layer pressure and its reservoir stream remain synchronized; do not change the
public pressure field alone in a repeated coupled calculation.
Layered `WellFlow.copy()` retains independent zones and fluids. Whole-process copying
preserves internal stream aliases, but independently copied wells do not establish
shared reservoir ownership. Old serialized layered wells omitted their zones and
must be rebuilt from inputs; do not infer missing zones from a stored outlet stream.
The legacy multi-zone injection calculation uses squared-pressure differences and
gas-standard MSm3/day for every fluid, not a qualified linear liquid-injectivity law.
Keep that rate-basis migration separate from copy/replay validation.
For explicitly calibrated single-liquid injection, use `WellFlow.addLiquidInjectionZone`
with kg/(s bar) coefficients and read mass units from `getLayerFlowRates` and
`getOutOfZoneRate`. The inlet owns injected composition; zone streams are reservoir
boundary metadata. Do not mix this builder with legacy zones or infer a coefficient
conversion from gas-standard volume. Phase/finite-input guards, copy and same-topology
shared-header acceptance are software evidence, not field injectivity or map qualification.
Keep the inherited shared-reservoir fixture's legacy basis and extrapolated-map limitation
visible until independently justified inputs replace them. See the well guide's
explicit-liquid-mass-injectivity section for the tested example and migration boundary.
Register configured rotating-equipment maps with
`FieldNetworkTopology.addPumpWithCurve(...)` and `addCompressorWithChart(...)` so the
existing `Pump`/`Compressor` remains the chart owner under the canonical edge identity.
`ProcessModel.getPower(...)` includes solved pump/compressor demand inside standalone
`LoopedPipeNetwork` and wrapped `FieldWellNetworkProcessUnit` equipment; use that total
for shared host-power evidence and fail closed on non-finite map results. Registration
requires an enabled map before graph mutation, and a chart-backed compressor calculation
failure must remain infeasible rather than falling through to simplified polytropic physics.
Recheck chart availability and the equipment's `solved()` contract for every mapped
calculation. Finite forward results from a failed pressure-target speed search are not
accepted operating points; rejected results must invalidate power and clear stored thermal state.
For curve-backed pump edges, require the registered model and enabled chart on every
positive-flow evaluation; a missing runtime binding must not create a fixed-pressure
replacement. Invalid pump results must clear head, NPSH, limit residuals and thermal state
as well as power. Exercise rejection and recovery on the same graph. This acceptance
guard does not qualify extrapolated curves or replace operating-region constraints.
Keep `NetworkPipe.setPumpMinimumFlowKgS` (stable operating limit in kg/s) separate
from the backing `Pump.setMinimumFlow` (numerical bypass threshold in kg/hr). Never
copy or convert the stable limit into the bypass setting. Preserve the configured
bypass; a bypassed positive-flow pump must not contribute accepted zero-power pumping
evidence. Converged flow below the stable limit is still an operating violation:
inspect its residual and use the existing `NetworkConstraints.edgeFlow` with explicit
kg/hr bounds in an optimization study. A chart status alone does not check that limit.
Default `PumpChart` status follows the requested-speed reduced flow: use
`checkSurge2(flow, speed)` rather than the legacy reference-speed `checkSurge1`.
Its slope/high-flow/BEP diagnostics are curve heuristics, not an approved operating
envelope or speed-extrapolation guard. `NetworkPumpOperatingStatusTest` covers
same-edge speed/demand migration and restoration with affinity and host-power checks;
it does not qualify the alternative spline map or the extrapolated lifecycle fixture.
For `PumpChartAlternativeMapLookupExtrapolate`, BEP/status use the requested-speed
absolute-flow efficiency surface, not default-chart Q/N scaling. The bounded 51-point
global-flow scan is approximate and may extrapolate narrower individual curves; it is
recomputed after mutable map updates. Keep its global-flow low/high heuristics separate
from explicit qualified operating constraints. Same-graph regression evidence for this
formalism does not qualify the lifecycle fixture, vendor maps or injectivity laws.
For repeated quasi-steady field-to-host studies, reuse the live model and evaluator but
sample constraints again after each reservoir boundary or host-limit update. The ten-well
`FieldToFacilityOptimizationAcceptanceTest` exercises moving separator, scrubber and
shared-power limits without rebuilding topology or maps. Frozen evidence describes its
original calculation only; it is not acceptance of a later period. Its mapped injection
utility is not a live injector, and prescribed pressure steps are not a reservoir forecast.
Use `FieldWellNetworkProcessUnitTest` for the separate live two-zone injection period
case: synchronized layer/stream pressure updates, allocation and fracture checks, fresh
shared-power rejection/recovery, and original-boundary rate/power restoration.
Its shared-reservoir acceptance case also composes production and live injection with
one `SimpleReservoir`. Keep reservoir advancement outside steady candidate evaluations:
sample current host limits first, transfer the converged well rates (not the production
inlet placeholder rate) to the reservoir streams, and advance inventory once per accepted
period. Preserve identical ordered component slates for the reservoir's component-wise
balance, and synchronize injection-layer pressures with the reservoir owner afterward.
Do not also advance the same reservoir through a second network forecast. The example
is a synthetic explicit quasi-steady conservation check, not a qualified forecast or
liquid-injectivity law; persisted lifecycle revalidation remains owned by #4188/#3154.
For accepted-step refinement, reuse the same fixture with independently initialized
time partitions and compare actual accepted states at common physical times. Include
cumulative produced/injected mass and component inventory, not just endpoint pressure;
retain rejected-trial non-advancement and cumulative component closure at every step.
The one-day 4/8/16/32-step regression tests a smooth explicit coupling trajectory, not
adaptive local-error control, complete map operating envelopes or two-fluid transients.
Its inherited pump curve is extrapolated below the tabulated minimum flow; retain that
limitation until a separate operating-region qualification replaces the synthetic fixture.
The dedicated `TwoFluidPipeNetwork` already owns storage-node transients; do not
replace it. Live well/injection coupling must reuse WellSystem/WellFlow physics.

## Class map

| Class family | Package | What it does | Verified entry points / result methods |
|---|---|---|---|
| Steady looped network: `LoopedPipeNetwork`, `LoopDetector`, `NetworkLoop` | `neqsim.process.equipment.network` | Node-edge gas or multiphase gathering/export model; discovers loops and solves hydraulic operating points. | `setFluidTemplate`, `addSourceNode`, `addJunctionNode`, `addFixedPressureSinkNode`, `addPipe`, `run`, `getNodePressure`, `getTotalSinkFlow` |
| Hydraulic details: `NetworkLinearSolver`, `NetworkPipe`, pipe-model bindings | `neqsim.process.equipment.network` | Linear solve helpers and per-edge hydraulic state; edges may use Darcy-Weisbach or a configured `PipeBeggsAndBrills` model. | `NetworkLinearSolver.solve`, `NetworkLinearSolver.solveGaussian`, `NetworkPipe.getFlowRate`, `NetworkPipe.getBBModel` |
| Well and facility elements | `neqsim.process.equipment.network` | IPR, tubing, choke, compressor, pump, regulator, artificial-lift, and related source/edge elements within the network. | `addWellIPR`, `addChoke`; configure through `NetworkPipe` element-specific setters |
| Typed field/SURF identity: `FieldNetworkTopology` | `neqsim.process.equipment.network` | Stable production/injection roles, equipment tags and ports over the canonical network, including typed choke, pump and compressor construction. | `addPipe`, `addChoke`, `addPump`, `addPumpDifferentialPressure`, `addCompressor`, `validateForExecution`, `copyDefinition` |
| Live field route line-ups: `FieldWellNetworkProcessUnit`, `RouteConfiguration` | `neqsim.process.equipment.network` | Runs the live well/network coupler in a process model and atomically switches named multi-edge direct, brownfield or redundant configurations on the same canonical graph. | `registerExclusiveRouteSelector`, `registerRouteConfigurationSelector`, `getRouteConfigurationNames`, `setRouteConfigurationSelection` |
| Canonical SURF design basis: `FieldNetworkSurfDesignBasis`, `SubseaProductionSystem` | `neqsim.process.fielddevelopment.subsea` | Read-only typed equipment and exact route geometry plus edge-keyed mechanical/cost/thermal selections into existing Class 4 and steady network contracts. | `createSurfDesignBasis`, `setLineDesign`, `createLineDesignDecisionVariable`, `createLineDesignScreeningConstraints`, `applyLineThermalDesigns`, `assessDnvStF101LineDesigns`, `assessOnBottomStabilityLineDesigns`, `estimateSurfCosts`, `createSurfCapitalCostObjective` |
| SURF route cost/design: `SURFCostEstimator.LineDesign`, `PipeMechanicalDesignCalculator`, `MultilayerThermalCalculator`, DNV screening kernels | `neqsim.process.mechanicaldesign.subsea`, `neqsim.process.mechanicaldesign.pipeline`, `neqsim.process.equipment.pipeline` | Route-specific OD, wall, material, installation, typed insulation and explicit structural/environmental input with existing mechanical/thermal screening and MTO output. | `LineDesign.builder`, `dnvStF101Input`, `onBottomStabilityInput`, `resolveOverallHeatTransferCoefficientWm2K`, `getDetailedEstimateResult` |
| Constrained optimization: `NetworkOptimizer`, `NetworkConstraints`, `NetworkObjectives`, `NetworkDecisionVariable` | `neqsim.process.equipment.network` | Bounded operating search plus complete fail-closed enumeration of all-discrete physical designs. | `NetworkDecisionVariable.pipeDiameter`, `NetworkDecisionVariable.customDiscrete`, `NetworkOptimizer.optimize`, `optimizeMultiObjective` |
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

- `PipeBeggsAndBrills` transient outlets require a TP flash after composition assignment and before `initProperties()`. Property initialization alone does not restore equilibrium phase fractions after `setMolarComposition(...)` resets phase information. `PipeBeggsAndBrillsTransientSystemTest` checks repeated gas/oil/water outlet updates against a separately flashed inlet template.
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
