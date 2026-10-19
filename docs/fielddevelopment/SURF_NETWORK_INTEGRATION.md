---
title: "Well and SURF Network Integration"
description: "Current functionality, integration decisions, hydraulic fidelity and qualification gaps for production and injection networks."
---

This is the implementation and capability audit for [campaign #4228](https://github.com/equinor/neqsim/issues/4228).
The baseline for the shared-reservoir acceptance extension is `932604c3240549ccad1f9ce5ffbe6f20528f631e`.
Source implementation, regression evidence and field qualification are different maturity levels.
Merged increments supply edge-local hydraulic fidelity, typed field/equipment identity, live well
pressure-rate coupling, same-topology hydraulic comparison and a guarded steady-to-transient handoff.
Merged design increments bind explicit DNV-ST-F101 limit-state and DNV-RP-F109 on-bottom-stability
inputs to those same route identities and independently check the steady thermal resistance and
arrival-temperature equations. The specialist screening kernels remain the calculation owners;
none of this completes field, vendor, independent-verification or AFC qualification.

## Current functionality and reuse decisions

| Existing functionality | Current scope and evidence | Integration decision / remaining gap |
|---|---|---|
| `WellFlow`, `WellSystem`, `TubingPerformance` | Production/injection IPR, layered completions and fracture constraints in `WellFlow`; production nodal IPR/VLP with simplified and full correlation modes in `WellSystem`. `FieldWellNetworkCoupler` now binds both APIs to canonical free-pressure field nodes. | Reuse the well physics and their convergence diagnostics. Continue qualification of coupled well/network inner convergence; the existing network IPR edge remains a separate screening API. |
| `LoopedPipeNetwork` | Named graph, NR pressure/flow equilibrium, branch/loop topology, IPR/choke/tubing/pump/compressor edges, signed flow, route profiles, local fluids, conservative component/enthalpy mixing, JSON definitions, host coupling, optimization and reservoir attachments. See network operating-point, pump, optimizer and composition tests. | Extend this detailed steady network and its identities. Use its existing solver, thermodynamic coupling and optimizer; do not create a competing field graph or optimizer. |
| `WellFlowlineNetwork` | `WellFlow` + Beggs-Brill branches, manifold mixing and host endpoint pressure iteration. One downstream manifold connection per node. | Retain its convenient gathering API; do not treat it as a general split/loop/injection graph. A compatibility builder can later target the detailed graph. |
| `PipeFlowNetwork` | Compositional `OnePhasePipeLine` TDMA tree network with mixer nodes. | Keep its single-phase distributed use cases. It does not become multiphase just by changing the topology labels. |
| `NetworkSolver` (fielddevelopment/network) | Star gathering model using `WellSystem`; its flowline loss is an approximate rate/diameter estimate despite a Beggs-Brill description. Capacity and choke allocation are screening calculations. | Keep compatibility and classify as screening. Do not reuse that pressure-drop estimate as the detailed SURF hydraulic kernel. |
| `NetworkNewtonSolver`, `WellBranch`, `FlowlineBranch`, `IntegratedProductionModel` | Separate fast lifecycle/optimization graph in surface volumetric units; reduced deliverability curves and fitted quadratic flowline resistance. | Preserve the fast reduced-order workflow. Supply validated surrogates from detailed results later; do not confuse Sm3/day with the detailed graph's kg/s or treat one-point fits as full hydraulics. |
| `SubseaProductionSystem`, `TiebackRouteNetwork` | Direct/cluster/daisy-chain/template builders and cost/geometry metadata; forward equipment execution with configured rates/choke pressures. Route network is explicitly screening metadata. | Reuse architecture/design equipment and route provenance. Add a mapping to detailed network identities rather than assuming architecture labels already provide coupled pressure/flow solution. |
| `SubseaTree`, `SubseaManifold`, `SubseaJumper`, `PLET`, `PLEM`, risers, mechanical-design classes and SURF cost estimators | Existing process/design objects and design/cost screening workflows. | Keep real equipment as owners of geometry, mechanical design and cost. Template/manifold/PLEM labels alone add no junction physics; junction conservation belongs to the network. |
| `TwoFluidPipe`, `TwoFluidPipeNetwork` | Mechanistic pipe steady/transient states; a storage-node transient network with phase mass-balance tests. The latter explicitly excludes algebraic zero-volume junctions, component/enthalpy mixing, reverse boundary composition and branch subcycling. | Use `TwoFluidPipe` for steady resistance evaluation here. Reuse the existing transient network in #2911; do not build a second transient engine or claim general mixed-fidelity dynamics. |
| `TransientGasNetwork`, `TransientCompositionalPipeNetwork` | Dedicated gas linepack and bounded prescribed-flow species transport. | Retain their documented applicability. Neither qualifies general oil/gas/water dynamic SURF networks. |
| `FieldLifecycleModel`, `FieldLifecycleSimulator` | Existing SURF + facility `ProcessSystem`/`ProcessModel` composition, reservoir and injection streams, lifecycle economics and constraints. | Connect solved network outlets to these existing streams and areas. Preserve existing detailed user-built equipment instead of regenerating it. |
| Agent/MCP inventory | Pipe tools, API discovery and capability evidence distinguish source presence from executable operations. | Register discovery and regression evidence without claiming the instance-based network builder is already an MCP invocation contract; coordinate exposure with #3153. |

## First implementation: topology-independent pipe fidelity

`LoopedPipeNetwork.NetworkPipe.setHydraulicModelType(...)` overrides an individual
`PIPE` or `MULTIPHASE_PIPE` edge with `DARCY_WEISBACH`, `BEGGS_BRILL` or `TWO_FLUID`.
Null removes the override. Node names, edge names, direction, fluid assignments,
geometry and host connections remain the same when fidelity changes.

Use `NEWTON_RAPHSON` for explicit edge overrides. Unsupported solver combinations
fail before solving. Non-pipe elements retain their own physics: the `TUBING`
network element remains its existing simplified VLP, and `WellSystem` full VLP
selection remains separate. Setting a pipe override does not upgrade either one.

Legacy definitions without overrides retain their defaults. Explicit multiphase
edges retain Beggs-Brill even when the ordinary pipe default is Darcy-Weisbach.
Overrides survive `toJson()` / `fromJson()`; the reconstructed graph still needs
its fluid template/source assignments. JSON replay preserves a definition, not
accepted transient inventory or all external equipment objects.

The two-fluid resistance calculation receives physical upstream composition,
pressure, temperature, trial mass rate and route geometry. It solves for outlet
pressure; imposing the network's downstream trial pressure would mask the pressure
residual. Reverse flow reverses the geometry/thermal profile traversal and the
head-loss sign. Route elevations are sampled onto `multiphaseSegments` finite
volumes; refine the mesh to resolve terrain. Temperature and U profiles are sampled
at cell midpoints when thermal coupling is enabled.

The existing global composition/thermal coupling must be enabled when different
well fluids or thermally coupled junction states are important. No additional
mixing algorithm is introduced by hydraulic model selection.

`getEffectiveHydraulicModelType(...)` and `getHydraulicModelStatus()` expose the
selection and evaluation evidence. A failed, unconverged, pressure-floor-limited
or wall-clock-limited two-fluid calculation throws and cannot silently substitute
Darcy-Weisbach. `getTwoFluidModel()` provides the last steady pipe and convergence/
holdup/phase-velocity profiles. `createInitializedTwoFluidPipe()` only succeeds for
`TWO_FLUID_CONVERGED` evidence and returns an independent deep copy retaining the
accepted steady fields. Zero-flow evaluation uses the existing static
Darcy/hydrostatic screening calculation and reports `ZERO_FLOW_STATIC`.
Legacy Beggs-Brill fallback remains available and reports `BEGGS_BRILL_FALLBACK_DARCY`.
Inspect this status before accepting an engineering result.

### Tested Java example

This synthetic four-source template example is exercised by
`NetworkHydraulicFidelityTest.fourWellTemplateUsesMixedFidelity`. The sources are
pressure boundaries, not solved IPR/VLP wells. Geometry is in metres, pressure in
bara, source/sink helper rates in kg/hr, and edge rates in kg/s.

```java
LoopedPipeNetwork network = new LoopedPipeNetwork("four well template");
SystemInterface gas = new SystemSrkEos(298.15, 100.0);
gas.addComponent("methane", 0.95);
gas.addComponent("ethane", 0.05);
gas.setMixingRule("classic");
network.setFluidTemplate(gas);
network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
network.setTolerance(0.001);
network.setMaxIterations(80);
network.addJunctionNode("template");
network.addSinkNode("host", 14400.0);
for (int index = 0; index < 4; index++) {
  String well = "well " + index;
  network.addSourceNode(well, 100.0, 3600.0);
  network.getNode(well).setTemperature(298.15);
  LoopedPipeNetwork.NetworkPipe branch =
      network.addPipe(well, "template", "branch " + index, 50.0, 0.3);
  branch.setMultiphaseSegments(4);
  branch.setHydraulicModelType(LoopedPipeNetwork.PipeModelType.BEGGS_BRILL);
}
LoopedPipeNetwork.NetworkPipe trunk =
    network.addPipe("template", "host", "trunk", 100.0, 0.5);
trunk.setMultiphaseSegments(4);
trunk.setHydraulicModelType(LoopedPipeNetwork.PipeModelType.TWO_FLUID);
network.run();
```

The tests check branch symmetry, a 4 kg/s trunk rate, manifold/whole-network mass
balance, pressure ordering, JSON replay, reverse traversal, standalone pipe
agreement, stale Beggs-Brill geometry and failure propagation. Standalone agreement
is integration evidence, not an independent physical benchmark.

## Same-topology hydraulic comparison and steady initialization

`NetworkHydraulicModelComparison` accepts a `FieldNetworkTopology` and selected
canonical edge IDs. It creates two detached definition copies, restores the fluid
template and only the explicitly assigned node fluids, and changes the selected edges
to Beggs-Brill or two-fluid hydraulics. All unselected edges retain their configured
models, so a mixed-fidelity field can be qualified without reconstructing its graph.
The caller's topology is neither run nor mutated.

Both complete network solves and every requested edge must converge with the requested
model. Beggs-Brill fallback and stale or failed two-fluid evidence are rejected. The
result records each model's edge rate and pressure drop, cell-centre distance and represented
cell length (m), pressure (Pa), temperature (K), liquid-holdup and superficial gas/liquid
velocity (m/s) profiles, plus network mass-balance residuals. Distance is measured from the
physical inlet for forward and reverse flow. A route-profiled Beggs-Brill edge is solved as
sequential local-slope segments; their accepted boundary results are aggregated onto one
monotonic cell-centre route profile. Reported mean holdup and phase velocities are weighted by
represented cell length rather than sample count. The requested Beggs-Brill increments are
distributed across route legs with at least one increment per leg, including non-divisible meshes.

`compareMeshSensitivity(...)` replays the same topology at two or more requested segment counts.
It applies the count only to selected edges on detached definitions, executes the normal
fail-closed comparison at every mesh and returns each full result by count. Unselected edge
fidelity and mesh settings remain unchanged, and the caller topology is not run or mutated.

```java
NetworkHydraulicModelComparison comparison =
    new NetworkHydraulicModelComparison(fieldTopology);
NetworkHydraulicModelComparison.Result result = comparison.compare(
    Arrays.asList("flowline", "riser"), UUID.randomUUID());
NetworkHydraulicModelComparison.EdgeComparison flowline =
    result.getEdgeComparison("flowline");

double pressureDifference = flowline.getRelativePressureDropDifference();
double temperatureDifferenceK = flowline.getOutletTemperatureDifferenceK();
double holdupDifference = flowline.getAverageLiquidHoldupDifference();

TwoFluidPipe initialized = result.createInitializedTwoFluidPipe("flowline");
initialized.setTransactionalTransientEnabled(true);
initialized.runTransient(0.001, UUID.randomUUID());

NetworkHydraulicModelComparison.MeshSensitivityResult mesh =
    comparison.compareMeshSensitivity(
        Arrays.asList("flowline"), Arrays.asList(4, 8, 10), UUID.randomUUID());
NetworkHydraulicModelComparison.EdgeComparison fine =
    mesh.getResult(10).getEdgeComparison("flowline");
```

`NetworkHydraulicModelComparisonTest` exercises two different SRK well fluids through
a conservative template, an undulating 20 km flowline and a 500 m riser at 2.0 kg/s,
plus a nearby 1.5 kg/s point. It checks both network mass balances, physical and
distance-aligned profile bounds, cross-model engineering bounds for all five quantities,
4/8/10-segment sensitivity, preservation of the original topology/model selection,
clone independence and one accepted transactional transient step. This is evidence-ladder
level 3/4 (conservation and engineering bounds), not an independent correlation benchmark.
Transient multi-edge junction conservation, energy/component closure, representative
large-field runtime and independent field data remain open qualification work.

## Typed field and equipment identity

`FieldNetworkTopology` is a semantic view over the same `LoopedPipeNetwork`; it is
not another solver or connectivity graph. A field node or edge ID is exactly the
wrapped hydraulic node or edge name. The view adds:

- typed production, injection and shared services;
- well, tree, template, manifold, PLEM, PLET, host and brownfield node roles;
- wellbore, choke, jumper, flowline, trunkline, pipeline, riser, header, pump,
  compressor and tie-in edge roles;
- stable equipment tags, endpoint port names and declared forward/bidirectional
  operation for DEXPI/P&ID and agent-facing identity;
- optional runtime binding to existing NeqSim process equipment, so subsea equipment
  and mechanical design remain the owners of geometry, design and cost;
- pre-execution diagnostics for missing classification, duplicate port use, service
  mismatch, isolated/disconnected topology, edge-role/solver incompatibility and
  cycles unsupported by the selected solver, plus undeclared reverse flow after a
  converged solve;
- JSON replay and definition copying of hydraulic topology plus semantic identity.

The builder requires hydraulic fidelity explicitly for every new pipe-like edge and
delegates it to `NetworkPipe.setHydraulicModelType`. Production, injection, direct
tieback, daisy-chain, clustered and multi-template layouts are therefore composed
with the same node/edge builder instead of architecture-specific physics. Existing
graphs can be registered without reconstruction; unclassified existing nodes or
edges fail validation. Runtime equipment objects and external fluids are deliberately
not duplicated by JSON replay and must be rebound after loading.

`FieldNetworkTopologyTest` exercises multi-template production plus injection
definitions, PLEM/manifold bindings, direct production and injection execution,
conservative mass flow, identity/port/service errors, loop diagnostics and replay.
These tests qualify the identity/integration contract; they do not independently
qualify B&B or two-fluid correlations or field design.

### Powered injection and field-scale acceptance

`FieldNetworkTopology` now creates typed choke, fixed-outlet-pressure pump,
fixed-differential-pressure pump and compressor edges directly through the existing
`LoopedPipeNetwork` equations. The stable field edge and canonical hydraulic element
are created atomically, with validated opening/efficiency/pressure inputs and explicit
service, direction and endpoint ports. Callers no longer need to create an untyped raw
edge and then register it. JSON replay preserves the element type and its serializable
operating definition; externally configured pump/compressor maps and runtime equipment
still follow the documented rebind contract.

`FieldNetworkScaleAcceptanceTest` adds three synthetic integration cases:

- twelve wells on three daisy-chained templates plus a brownfield satellite tie-in,
  PLEM, PLET and host riser. Differing but component-compatible source fluids mix at
  conservative junctions; selected routes use Darcy-Weisbach, Beggs-Brill and two-fluid
  hydraulics on the same topology. The solve and replay close field mass balance and
  reproduce the host operating point;
- a 6 kg/s water-injection pump feeding three 2 kg/s injectors through a 160 bara shared
  header, plus a CO2-rich 3 kg/s compressor-discharge boundary feeding two 1.5 kg/s gas
  injectors. The water case checks pump power and the expected hydrostatic pressure gain
  to deeper wells; the gas case checks conservative shared-header allocation;
- fifty wells on five templates, retaining a deliberately broad 60 s CI guard. The
  reference local run completed the solve in about 3.0 s.

These cases qualify canonical composition, conservation, replay and a synthetic runtime
envelope. They do not qualify the simplified compressor equation or pump/compressor
maps, CO2 phase behavior across a compression train, injection-well injectivity, field
data, or independent B&B/two-fluid accuracy. The CO2 case therefore starts at an
explicit compressor-discharge boundary rather than presenting an unqualified compressor
train as validated.

### Canonical topology to SURF design and cost

`SubseaProductionSystem.createSurfDesignBasis(topology)` derives an immutable
`FieldNetworkSurfDesignBasis` from the same typed nodes and exact `LoopedPipeNetwork`
edge geometry used by hydraulics. It counts production and injection wells, trees,
templates/manifolds, PLETs, PLEMs, jumpers and production/injection risers, and aggregates
the physical jumper, infield-flowline, tie-in, trunkline, pipeline and riser lengths and
diameters. It is a read-only design view, not another graph or solver.

`SubseaProductionSystem.estimateSurfCosts(topology)` routes that basis into the existing
`SURFCostEstimator`. Direct tiebacks now carry zero manifold cost, while multi-template
and multi-manifold layouts price the actual number of distribution units instead of an
implicit single manifold. The detailed `CostEstimateResult` retains the Class 4 estimate
basis, material take-off and a quality flag explaining aggregation. Every canonical
physical edge is now priced separately under its stable equipment tag, exact route length
and hydraulic diameter; the length-weighted getters remain available only as summary
metrics. The hydraulic diameter is a nominal Class 4 cost diameter, not a completed
outside-diameter or wall-thickness design. Different manifold designs, route-specific
installation methods, umbilical routing, material selection, supplier quotations and AFC
cost therefore still require detailed design.

### Route-specific mechanical and installation basis

`SURFCostEstimator.LineDesign` supplements one canonical physical line edge with an
immutable cost/design basis: outer diameter, explicit or screening-calculated wall
thickness, API 5L material grade, design pressure and legacy screening code, installation
method, rigid/flexible construction and insulation type/thickness. Register it by canonical
edge ID with `SubseaProductionSystem.setLineDesign(...)`. The system rejects missing or
non-SURF identities and checks that a rigid OD/wall combination agrees with the hydraulic
inside diameter before costing. Flexible-pipe layer geometry remains supplier-specific and
is therefore not subjected to the rigid steel consistency check.

Rigid routes reuse `PipeMechanicalDesignCalculator` for screening wall thickness and steel
weight instead of duplicating the material-strength table. The resulting existing
`CostEstimateResult`/material-take-off contract records route-specific steel grade, coating,
insulation volume, field welds and installation. Unconfigured routes retain the previous
nominal hydraulic-diameter Class 4 basis, so adoption can be incremental.

```java
SURFCostEstimator.LineDesign flowlineDesign =
    SURFCostEstimator.LineDesign.builder(8.625)
        .wallThicknessMm(8.0)
        .materialGrade("X52")
        .designPressureBar(140.0)
        .installationMethod("J-lay")
        .insulation(RadialThermalLayer.MaterialType.PU_FOAM, 30.0)
        .thermalBoundary(4.0, 300.0, 100.0)
        .minimumOutletTemperatureC(20.0)
        .build();

subsea.setLineDesign("production-flowline", flowlineDesign);
subsea.applyLineThermalDesigns(topology);
for (NetworkConstraint constraint :
    subsea.createMinimumOutletTemperatureConstraints(topology, true)) {
  optimizer.addConstraint(constraint);
}
CostEstimateResult estimate = subsea.estimateSurfCosts(topology);
```

The typed insulation overload is the physical thermal input. The legacy string overload remains
cost metadata and is never mapped to conductivity by name. For a rigid line,
`resolveOverallHeatTransferCoefficientWm2K()` reuses `MultilayerThermalCalculator` with the resolved
inside diameter, wall, typed insulation and configured inner/outer film coefficients. Its result is
on the pipe-bore area basis used by `LoopedPipeNetwork`. A flexible line must provide an explicit
qualified U-value with `overallHeatTransferCoefficientWm2K(...)`; the rigid steel radial stack is not
applied to flexible construction.

`applyLineThermalDesigns(topology)` validates stable edge identity and rigid ID consistency, writes
wall, U-value and ambient temperature to the exact existing `NetworkPipe`, enables the existing
thermal-hydraulic coupling, and leaves per-edge hydraulic fidelity unchanged. It fails rather than
replace an existing route-dependent U-value or ambient-temperature profile. Use those existing
profiles directly where seabed conditions or insulation vary along the route.

`NetworkConstraints.edgeMinimumOutletTemperature(...)` and the `SubseaProductionSystem` constraint
list evaluate the solved physical outlet in kelvin. They fail closed for an unconverged network,
disabled thermal coupling, stagnant edge or non-finite temperature. `SubseaLineThermalDesignTest`
checks canonical identity, unchanged Darcy fidelity, heat loss to a 4 degrees Celsius ambient and
controlled pass/fail thresholds and an independent plug-flow energy equation at the solved pressure
and mass rate. `SURFCostEstimatorTest` checks both the insulation-thickness trend and the U-value
against an independently assembled cylindrical resistance network. These analytical checks raise
the implementation evidence level; they are not qualification against measured field arrival
temperatures or a commercial simulator.

For a rigid line, `LineDesign.dnvStF101Input(...)` attaches a complete
`DnvStF101PipelineDesignInput`; `LineDesign.onBottomStabilityInput(...)` attaches a complete
`DnvRpF109OnBottomStabilityInput`. `SubseaProductionSystem.assessDnvStF101LineDesigns(...)` and
`assessOnBottomStabilityLineDesigns(...)` validate the canonical edge and OD/wall/ID consistency,
then invoke the existing fail-closed engineering kernels and return immutable typed calculation
results keyed by edge ID. Each result retains readiness findings, standard/input provenance,
review-required status and the assessment when calculation is permitted.
Environmental loads and soil data remain outside the hydraulic topology.

No structural value is inferred from hydraulic pressure, depth, material labels or route geometry.
The DNV-ST-F101 route requires an explicit wall thickness and an exact matching OD/wall input;
flexible lines cannot use that steel limit-state contract. DNV-RP-F109 input must carry the exact
hydrodynamic OD plus explicit environmental, hydrodynamic, soil, safety-factor and response-model
evidence. Incomplete input throws before calculation; a valid but overloaded case returns a failed
screening assessment rather than being clipped or approved. Every assessment retains its explicit
engineering-approval-required boundary.

A zero wall thickness still asks the legacy calculator only for a rounded-up Class 4 cost basis. It
cannot be promoted into DNV input. The typed calculations are option-screening implementations, not
clause-complete conformity, installation analysis, flexible-pipe qualification, independent
verification or AFC design.

`SubseaProductionSystem.createLineDesignDecisionVariable(...)` exposes a deterministic list of
complete rigid `LineDesign` alternatives as zero-based categorical indices to the existing
all-discrete optimizer. One transactional selection updates the same canonical edge's hydraulic ID,
explicit wall, optional uniform thermal basis, material/installation/cost basis and attached
DNV-ST-F101/F109 inputs. It does not alter the edge's Darcy-Weisbach, Beggs-Brill or two-fluid model.
Every candidate must carry an explicit wall and the same presence of thermal and screening
contracts; minimum outlet temperature is a design requirement and cannot vary between alternatives.
Flexible-pipe alternatives remain supplier-specific and are rejected by this contract.

Add the returned `createLineDesignScreeningConstraints(...)` constraints to fail closed when a
selected ST-F101 or F109 calculation is blocked, non-finite or above utilization one. The normal
minimum outlet-temperature, hydraulic, conservation and cost constraints/objectives remain
composable. A passing screening candidate still requires engineering review and does not establish
DNV conformity, vendor qualification or AFC approval. Candidate lists are validated before the
initial design is applied, and the selector restores the complete initial route state after
individual candidate evaluation or an all-infeasible enumeration.

### Discrete hydraulic and SURF design

`NetworkDecisionVariable.pipeDiameter(...)` registers increasing physical diameter
candidates on an existing canonical edge. When every registered design variable is
discrete, the existing `NetworkOptimizer` evaluates the complete Cartesian product in
deterministic order, runs the configured edge-local hydraulics, checks normal network
constraints and retains the best feasible candidate. It refuses to start if the complete
design space exceeds `maxEvaluations`, and it rejects mixed continuous-operation and
discrete-design variables so a partial or relaxed search cannot be reported as a discrete
optimum. Use staged design and operating optimization when both are required.

`SubseaProductionSystem.createSurfCapitalCostObjective(topology, weight)` contributes
negative route-specific SURF CAPEX to the same composable objective contract used by
`NetworkOptimizer`. The optimizer therefore changes the diameter on the hydraulic edge,
solves that exact topology, verifies pressure/flow/conservation constraints, and prices
the resulting geometry without rebuilding the field. The synthetic two-route acceptance
case enumerates four designs and selects the least-cost combination that meets delivery:
the long segment is upsized while the short segment remains small. This is deterministic
hydraulic and Class 4 screening evidence, not independent cost or hydraulic qualification.

For a coupled route-design enumeration, register complete candidates instead of an independent
diameter variable:

```java
NetworkDecisionVariable routeDesign = subsea.createLineDesignDecisionVariable(
    topology, "production-flowline", Arrays.asList(smallDesign, largeDesign), 0);
optimizer.addDecisionVariable(routeDesign);
optimizer.addObjective(subsea.createSurfCapitalCostObjective(topology, 1.0e-6));
for (NetworkConstraint constraint :
    subsea.createLineDesignScreeningConstraints(topology, true)) {
  optimizer.addConstraint(constraint);
}
```

`NetworkDecisionVariable.customDiscrete(...)` is the generic adapter used underneath. It preserves
the existing optimizer's complete Cartesian enumeration, evaluation budget and restoration rules;
domain state remains owned by the transaction supplied by the caller. Do not register both a
complete line-design selector and a separate diameter variable for the same edge.

## Live well pressure-rate coupling

`FieldWellNetworkCoupler` binds normal `WellSystem` and `WellFlow` equipment to
free-pressure production/injection well nodes created by
`FieldNetworkTopology.addLiveWellNode`. It does not add a graph, well correlation or
hydraulic solver. In each outer iteration the live well is evaluated at the current
well-node pressure, its rate is applied as a conservative node supply (production) or
demand (injection), and the existing `LoopedPipeNetwork` Newton-Raphson solver updates
the field pressures and flows.

Production `WellSystem` bindings retain their full IPR/VLP calculation and expose the
inner convergence flag, pressure residual and iteration count in the coupled result.
Production `WellFlow` bindings reuse the selected IPR model in pressure-to-rate mode.
Injection `WellFlow` bindings reuse multi-zone injectivity, zone allocation and
fracture-pressure checks. Configured production drawdown/minimum-BHP constraints and
injection fracture limits fail the coupled acceptance result instead of silently
clipping rate. A well below the configured rate threshold is reported as shut in;
an otherwise pressure-indeterminate zero-flow branch does not claim coupled
convergence.

Injection coupling fails closed when the well inlet and network fluid do not use the
same thermodynamic model, mixing rule and component identity set. Production source
fluids are registered on their canonical nodes so existing conservative component
mixing remains the owner of commingling. Runtime well objects and fluids remain outside
JSON replay and must be rebound after loading a field definition.

The immutable coupling report includes outer iteration count, maximum rate and
well-node pressure residuals, the hydraulic residual, network mass-balance residual,
and per-well status. `FieldWellNetworkCouplerTest` covers live `WellSystem`, production
and multi-zone injection `WellFlow`, repeated execution with a monotonic host
backpressure response, conservation, shut-in, BHP/drawdown and fracture limits,
incompatible injection fluids, and semantic binding errors. This is integration and
conservation evidence, not independent IPR/VLP, injectivity or field qualification.

### Process-system and optimization integration

`FieldWellNetworkProcessUnit` is the process-equipment adapter for the same coupled
topology. Add it to a `ProcessSystem` before downstream separators, scrubbers,
splitters and compressors. Its fixed-pressure sink streams are stable process stream
identities, so the downstream facility remains connected across repeated optimizer
runs. The unit is solved only when the complete outer coupling result is converged,
all aggregate and per-well values are finite, and every live well reports accepted
inner convergence. Iteration limits, physical well limits or incomplete evidence
therefore leave the unit unsolved instead of exposing a partially accepted candidate.

The adapter exposes the canonical `LoopedPipeNetwork` to `ProcessAutomation` rather
than adding another network-control model. Existing addresses for choke opening,
edge availability, regulator pressure, compressor speed and pump work remain
available under the process-unit name. A copied unit rebuilds the topology definition,
copies the bound `WellSystem`/`WellFlow` objects and rebinds them before execution;
accepted runtime state is not shared with the source unit.

For a well that can be routed to mutually exclusive separator pressure levels, register
the existing alternative network edges with
`FieldWellNetworkProcessUnit.registerExclusiveRouteSelector(...)`. Automation then
exposes `route.<selector>.selection` as a zero-based discrete input. One verified write
atomically makes the selected edge fully available and every alternative unavailable;
invalid fractional selections are rejected without changing the line-up. A zero edge
availability is an exact closed route in the Newton network solve, while fractional
availability remains a hydraulic derating. The selected HP or LP fixed-pressure sink
therefore feeds back through the same live well/network coupling instead of acting as a
downstream flow split. Copies preserve selector definitions but keep independent edge
state.

For a line-up that changes several edges together, construct named
`FieldWellNetworkProcessUnit.RouteConfiguration` objects and register them with
`registerRouteConfigurationSelector(...)`. Automation exposes
`configuration.<selector>.selection`. Each option lists the controlled edges that are
fully available; every other edge appearing in any option is exactly closed. This
supports direct versus brownfield routes and an option with both paths open as one
redundant loop without rebuilding the topology. Registration rejects missing edges,
duplicate line-ups, selector overlap and a current state that does not match exactly one
declared option. A selection snapshots every controlled availability, applies and reads
back the complete line-up, and restores the snapshot after any failure. Edge geometry,
equipment identity and Darcy-Weisbach/Beggs-Brill/two-fluid selection are never changed.
The focused live-well acceptance case checks direct, higher-resistance brownfield and
dual-path configurations, exact restoration, positive flow in both redundant paths and
network mass conservation. This is synthetic integration evidence, not an outage or
brownfield field qualification.

The same line-up contract is exercised for a live two-zone water injector. Primary,
brownfield and dual-path injection configurations feed one canonical live injection-well
node and preserve the existing `WellFlow` zone allocation contract.
The injection source now includes a typed `FieldNetworkTopology.addPumpWithCurve(...)`
edge backed by the existing `Pump` and `PumpChart` model. The corresponding
`addCompressorWithChart(...)` builder registers an existing mapped `Compressor` on the
same canonical topology. Both builders require an enabled chart before mutating the graph,
preserve the configured model object, stable equipment tag and edge identity, and leave
route-local hydraulic selection unchanged. Fixed-pressure and fixed-differential-pressure
pump builders remain available for screening cases.
The direct `LoopedPipeNetwork` mapped-equipment builders apply the same enabled-chart
precondition before graph mutation. If a configured compressor chart fails during the
network solve, the edge reports non-finite power and an infeasible chart status; it does
not silently continue with the simplified polytropic compressor equations.
Each subsequent evaluation also requires the chart to remain enabled, the compressor's
existing `solved()` contract to pass, and finite non-negative power with positive finite
outlet pressure and temperature. A pressure-target speed search that returns a finite
forward state but misses its target therefore remains infeasible. Rejected results clear
the edge's previous thermal state so later network and shared-power checks cannot reuse it.

Curve-backed pumps apply the same acceptance boundary on every positive-flow evaluation:
the registered `Pump` and enabled chart must still be present. A missing model (including
an unbound replayed curve definition) cannot create a replacement fixed-pressure pump.
All forward pump modes require an active pump, `solved()`, finite non-negative shaft power and positive
finite outlet pressure and temperature. Invalid results and exceptions report `INFEASIBLE`
and `Pump-INFEASIBLE`, invalidate power, head, NPSH and operating-limit residuals, and clear
the previously accepted inlet/outlet thermal state. Configured limits and map identity are
retained so a corrected input can recover through the normal network solve. The explicit
reverse-flow/check-valve and stopped-edge policies remain unchanged.

`MappedPumpAcceptanceTest` exercises accepted, rejected and recovered solves on the same
graph for disabled charts, missing models, unsolved finite results, invalid power/outlet
values and thrown calculations. Recovered demand is 1 kg/s with 1 kW from a controlled
test double; these values test the acceptance contract, not a physical pump benchmark.
`NetworkPumpTest` supplies adjacent real-model curve and fixed-pressure regressions.
This guard does not prohibit chart extrapolation or establish OEM operating regions,
NPSH qualification, injectivity validity or a complete pump constraint envelope.

Keep two different minimum-flow settings separate. The canonical edge's
`setPumpMinimumFlowKgS` records a minimum stable operating flow in **kg/s** and its
violation residual; it does not shut down the pump or create a recirculation path.
The backing `Pump.setMinimumFlow` instead configures a numerical low-flow bypass in
**kg/hr**. The network preserves that configured bypass threshold rather than copying
or converting the stable operating limit into it. A positive-flow evaluation that
bypasses the backing pump is rejected as `INFEASIBLE`, with unavailable power and
thermal evidence, because a zero-work bypass is not a valid pressure-rise calculation.
Lowering the bypass threshold permits a fresh solve on the same graph.

For the default reduced-curve `PumpChart`, speed-changing studies must evaluate the
positive-head-slope diagnostic, high-flow boundary and BEP comparison at the requested
speed. `PumpOperatingStatusTest` verifies their reduced-flow similarity, and
`NetworkPumpOperatingStatusTest` checks speed/demand migration, status propagation and
restoration using one canonical edge and its existing pump. It also checks mass closure,
head/power affinity scaling and canonical host-power aggregation. This is synthetic
integration evidence, not an independently qualified operating envelope; the inherited
shared-reservoir fixture's below-map limitation remains unchanged. The alternative
spline-map implementation and optimization boundary refinement (#4188/#3154) remain
separate workstreams.

Convergence is not operating-envelope acceptance: inspect the stable-flow residual
even when the curve's operating-status label is normal. For an optimization study,
register the existing `NetworkConstraints.edgeFlow` with the stable lower limit and
the separately supplied upper limit, both converted to **kg/hr**, alongside convergence
and other required constraints. `NetworkPumpMinimumFlowTest` exercises below-limit
fixed-pressure and differential-pressure calculations against standalone pumps,
mapped-pump limit changes without changing hydraulics, and explicit bypass rejection
and recovery through shared host-power accounting. The mapped case also checks shaft work
against mass flow times pressure rise divided by physical density and efficiency; the
default fixed-pressure mode instead uses the EOS isentropic enthalpy change. These are
synthetic software and same-model consistency checks, not minimum-continuous-stable-flow, OEM-map, recirculation,
injectivity or NPSH qualification. No new optimizer or automatic acceptance policy is added.

`LoopedPipeNetwork.getPower(...)` aggregates the solved shaft power of its compressor and
pump edges. `FieldWellNetworkProcessUnit` exposes that same demand, and normal
`ProcessSystem` / `ProcessModel` power aggregation includes both standalone canonical
networks and wrapped live well networks. Shared host-power evidence can therefore include
mapped injection pumps and mapped production compressors without copying them into a
parallel facility model. Non-finite rotating-equipment power propagates as non-finite total
demand, and an unsolved network containing rotating equipment likewise reports non-finite
power, so a shared-resource constraint fails closed.

`WellFlow.ReservoirLayer.setReservoirPressure(...)` updates the layer boundary and its
owning reservoir stream together for controlled repeated lifecycle solves. It does not
change injectivity, fracture pressure, composition, topology or hydraulic fidelity. The
synthetic acceptance checks the live pump curve and finite pump power, `ProcessModel`
shared-power visibility, selected-path flow, dual-path flow, zone allocation, fracture limits,
exact route restoration, mass conservation and the expected lower injectivity at higher
reservoir pressure. The curve is synthetic integration input; this is not vendor-map,
injectivity, fracture, outage or brownfield qualification.

The synthetic `FieldToFacilityOptimizationAcceptanceTest` connects two live wells and
well chokes through exclusive per-well routes to distinct 45 bara HP and 30 bara LP
fixed-pressure sinks. Those stable sink streams feed separators, compressor-suction
scrubbers and mapped compressors on a common shaft before a pressure-controlled 100 bara
gas-export boundary. The workflow exercises configured
well/per-route-line/separator/scrubber operating limits, compressor surge/stonewall
evidence, driver and gearbox power, a shared plant-power budget, transactional discrete
route actions, qualified export-pressure evidence, exact action restoration and an
operating-envelope slice. A second deterministic acceptance case instantiates ten live
wells with twenty alternative route edges and verifies complete well, line, vessel,
shaft, shared-power and export-boundary evidence without changing the two-well physical
model or introducing a separate optimizer. The lower
pressure route must reduce well backpressure and increase deliverability. A route
rejected by compressor or other hard evidence remains rejected; the test does not relax
physical limits to obtain a feasible optimizer result.

### Repeated production periods and moving host limits

`FieldToFacilityOptimizationAcceptanceTest.replaysLifecyclePeriodsWithMovingHostLimits`
reuses the ten-well fixture, one `ProcessModelSimulationEvaluator` and one
`ProcessModelOperatingActionSetEvaluator`. It changes each production well's existing
reservoir inlet stream by -1, -2 and -3 bara relative to its initial pressure; these
are prescribed quasi-steady boundaries, not calculated reservoir depletion or elapsed
time. The lean gas is SRK/classic, 90/7/3 mole percent methane/ethane/propane, initially
330.15 K. All chokes remain at 70%, with alternating HP/LP routes. The graph, edge
hydraulics, map objects, driver ratings and evidence tolerances are not rebuilt or retuned.

| Period | Additional temporary host restriction | Required behavior |
|---|---|---|
| 1 | HP separator gas-handling limit set to 95% of its current inlet mass rate | Fresh vessel evidence rejects the unchanged action vector |
| 2 | LP suction-scrubber gas-handling limit set to 95% of its current inlet mass rate | Fresh scrubber evidence rejects the unchanged action vector |
| 3 | Shared host shaft-power budget set to 95% of current demand | Fresh shared-resource evidence rejects the unchanged action vector |

Before each restriction, the test requires a converged, feasible operating point and
export reduction exceeding 0.1% from the preceding pressure boundary. Every evaluation
must reconverge after restoring its actions and close the field mass balance to
1e-6 kg/s. Power evidence must match the current `ProcessModel` total, including both
production compressors and the mapped injection utility pump. Each period restores
the original host limits and requires a new feasible evaluation. Finally it restores
the reservoir boundaries and checks export recovery within 1e-5 relative tolerance.
Earlier immutable evidence remains historical evidence; it must not be reused as
acceptance of the changed period. New power samples carry new calculation identifiers.
This is a lower-level repeated-solve regression, not reuse of an accepted search across
periods. `ProcessModelOperatingEnvelopeLifecycleStudy` still requires independently
configured period evaluators and fresh optimize/replay/slice evidence as described in
the [optimization guide](../process/optimization/OPTIMIZATION_OVERVIEW.md).

Run the fixture with `./mvnw -Dtest=FieldToFacilityOptimizationAcceptanceTest test`.
Enable the test class's Log4j logger at INFO to retain per-period export in kg/hr and
shared power in kW (the default test logging configuration is silent). Its maps, limits and
depletion steps are synthetic integration inputs. The injection utility here is a
fixed-pressure liquid pumping network, **not** a live reservoir injection well; the
separate `FieldWellNetworkProcessUnitTest` covers controlled live multi-zone injection.
Its `replaysLiveInjectionPeriodsWithMovingHostPowerBudget` case holds the primary route
and synthetic pump curve fixed while raising both zone pressures by 5, 10 and 15 bara.
It updates each layer through `setReservoirPressure`, checks the owning stream is
synchronized, requires injection reduction beyond 0.1% per step, 1e-8 kg/s network mass
closure, normalized two-zone allocation and no flagged fracture risk. Each period's
fresh pump-power evidence rejects a temporary budget at 95% of demand and becomes
feasible again when the budget is restored. Finally the original pressures recover
rate and power within 1e-4 relative tolerance. Run it with
`./mvnw -Dtest=FieldWellNetworkProcessUnitTest test`.
These checks establish repeated-solve conservation and expected trends, not vendor-map
qualification, a production forecast, coupled reservoir material balance, optimal
lifecycle economics or transient validation. Durable candidate storage and fresh replay
remain owned by the #4188/#3154 optimization workflow (including merged #4312); this
fixture adds no competing persistence or optimization layer.

Local Linux OpenJDK 17.0.20 execution on 2026-10-15, integrating these tests with
#4342 head `2273331300be18472dc684f6ddadd898b409eb20`, produced the following rounded
values. They are reproducibility observations, not calibrated reference data or new
hard-coded test targets; the acceptance criteria above remain the gates.

| Production pressure decrement (bara) | Export (kg/hr) | Total host power (kW) | Temporary restriction feasible? |
|---|---|---|---|
| 1 | 772.171922 | 49.446343 | No — HP separator |
| 2 | 763.585471 | 49.298065 | No — LP suction scrubber |
| 3 | 755.035017 | 49.149784 | No — shared power |

### Shared-reservoir production and injection acceptance

`FieldWellNetworkProcessUnitTest.conservesSharedReservoirAcrossProductionInjectionPeriods`
connects the existing production and live two-zone injection fixtures to one `SimpleReservoir`.
The reservoir owns all component inventory and its constant-volume, constant-temperature
material balance; the two canonical field networks own pressure/rate equilibrium. A normal
`ProcessModel` combines the production/host and injection `ProcessSystem` areas. The host
compressor and injection pump both use synthetic charts and contribute to one current
shared shaft-power sample. No new reservoir model, scheduler, optimizer or evidence store
is introduced.

The initial reservoir uses SRK/classic at 330.15 K and 150 bara, with 1,000 m3 gas and
100 m3 aqueous phase volumes. The base gas proportions are 90/7/3 methane/ethane/propane;
water is added before resetting the mixing rule and flashing the inventory. Injection
is pure water at 298.15 K, retaining the reservoir's complete ordered component slate
with zero hydrocarbon fractions. Both completion zones receive the same reservoir
pressure; they represent parallel injectivity allocations, not independent geological
reservoirs. The initial injection-node pressure guess is above reservoir pressure so
the initial live well is not shut in. The existing Darcy pipe choices, geometry, route
selectors, injectivity parameters, fracture limits and charts remain unchanged during
the periods.

Each period first runs the normal steady evaluator. A temporary power budget at 95%
of current compressor-plus-pump demand must reject the candidate; restoring the budget
must recover feasibility and produce fresh evidence. Those steady trials must leave
reservoir time and every component inventory exactly unchanged. Only after recovery
does the test transfer the converged production and injection mass rates to the existing
reservoir streams and call `SimpleReservoir.runTransient` once. In particular, the
production `WellFlow` inlet stream's placeholder rate is not the solved production rate.
The explicit component check is

$$n_{i,k+1}=n_{i,k}+\Delta t_k(\dot n_{i,\mathrm{inj}}-\dot n_{i,\mathrm{prod}}).$$

Here $n_i$ is component inventory in mol, $\dot n_i$ is molar flow in mol/s, and
$\Delta t$ is seconds. The 60, 120 and 240 s committed steps close each component to
`max(1e-8 mol, 1e-10 * abs(expected inventory))`; both network mass residuals must be
below 1e-8 kg/s. Following each step, the reservoir-owned producer stream and both
injection-layer pressures feed the next converged solve. Production deliverability
must change in the same direction as reservoir pressure, and injection acceptance in
the opposite direction. Zone allocation, fracture flags, route selection and chart
identity are also checked.

The initial Linux/OpenJDK 17 run produced the following post-step observations. These
small changes are expected for this short, low-rate synthetic case; the conservation
and trend assertions above, not these rounded observations, are the regression gates.

| Elapsed time (s) | Reservoir pressure (bara) | Production (kg/s) | Injection (kg/s) | Shared shaft power (kW) |
| --- | --- | --- | --- | --- |
| 60 | 149.998762 | 0.017727467 | 0.014271266 | 3.004354 |
| 180 | 149.996287 | 0.017726814 | 0.014271868 | 3.004328 |
| 420 | 149.991337 | 0.017725513 | 0.014273199 | 3.004280 |

This is a runnable synthetic coupling example and conservation/trend regression, not
a calibrated reservoir forecast, qualified liquid injectivity law, OEM-map qualification,
energy-balance qualification or timestep-refinement study. Holding accepted rates during
each inventory step is an explicit quasi-steady approximation, not an implicit coupled
transient solve. Reservoir advancement must remain outside steady candidate evaluation;
production/injection physics must not also be advanced through a second network forecast.
Persistent period acceptance and changed-input revalidation remain owned by #4188/#3154
and their existing ledger, including #4359.

### Shared-reservoir accepted-step refinement

`FieldWellNetworkProcessUnitTest.refinesSharedReservoirAcceptedStepsAtCommonTimes`
reuses that same fixture and accepted-step sequence over one day. Four independently
initialized runs use 4, 8, 16 and 32 equal steps (21,600, 10,800, 5,400 and 2,700 s).
Only the time partition changes: reservoir volumes, thermodynamics, topology, route
selection, well laws, synthetic maps, physical limits and solver tolerances are identical.
Every step retains the earlier convergence, shared-power rejection/recovery, unchanged
trial inventory, per-component balance and pressure-response assertions. A second balance
check accumulates component inflow minus outflow from the initial inventory, avoiding
reliance solely on a succession of locally closed steps.

The four common checkpoints are 6, 12, 18 and 24 hours. Actual accepted states are compared
without interpolation or Richardson extrapolation. The dimensionless discrepancy is the
maximum relative difference between adjacent resolutions over pressure, both well rates,
shared shaft power, cumulative production/injection mass and every component inventory,
at every checkpoint. The finer value is the denominator; all quantities are positive in
this fixture. Each halving must reduce this maximum discrepancy by at least 25%, and the
finest pair must differ by less than 0.1%. Differences must remain above 1e-10 so a
roundoff-level comparison cannot be presented as resolved refinement evidence.

On baseline `f5487f2cb13295fb20d30024b8d63697df927997`, Linux OpenJDK 17.0.20 with
`pomJava8.xml` produced these observations (not replacement hard-coded targets):

| Compared step counts | Maximum common-time relative difference | As percent |
| --- | --- | --- |
| 4 / 8 | 0.002058959 | 0.205896% |
| 8 / 16 | 0.001023590 | 0.102359% |
| 16 / 32 | 0.000510329 | 0.051033% |

At 24 hours, reservoir pressures were 148.240896, 148.244626, 148.246482 and
148.247408 bara respectively. The finest run ended at 0.017270646 kg/s production,
0.014731784 kg/s injection and 2.987162 kW shared shaft power. The roughly twofold
discrepancy reduction is consistent with the explicit first-order coupling in this
case; it is not a proof of order or an error bound against an exact physical solution.

This checks the explicit reservoir/network coupling for this smooth synthetic trajectory,
not universal step-size suitability, adaptive local-error control, reservoir forecast
accuracy, two-fluid pipe transients, or transient energy closure. The fixture tests its
declared shared-power constraint; it does not qualify the complete operating envelope of
the synthetic compressor/pump maps or a liquid injectivity law. In particular, the inherited
pump fixture emits below-minimum-tabulated-flow warnings: its curve is extrapolated and
strategy capacity constraints are disabled. Passing refinement does not establish a feasible
pump operating region. Preserve this limitation when reusing the numerical evidence.
Keep #2911's adaptive
state/physical-step-identity policy and #3298's failed transient qualification gate separate.
Lifecycle period-change reporting remains in the #4188/#3154 lane, including #4370.

## Dependency-ordered continuation

The alternative spline-map pump's BEP/status prerequisite is checked separately from the
default reduced-curve implementation: its efficiency interpolation is at absolute flow,
not constant Q/N. Speed-aware BEP/status now sample that existing efficiency surface and
refresh after map changes. The reference-speed method still uses the first supplied curve,
and the existing 51-point/global-flow scan and extrapolation limitations remain explicit.
Same-network tests cover reference/intermediate speed migration and restoration, physical
work, mass balance and host power with synthetic maps. This diagnostic consistency does
not qualify the inherited shared-reservoir fixture's extrapolated operating region.
Consume the sampled-region transitions merged in #4447 and lifecycle work owned by #4188/#3154
when map and injectivity constraints have an explicit engineering basis; do not create a
second transition detector or interpret a heuristic `OPTIMAL` status as operating approval.

1. Extend the field-scale cases to live `WellSystem`/`WellFlow` production and injection
   boundaries, qualified pump/compressor maps, shared host power/capacity constraints and
   controlled reservoir-pressure updates without duplicating reservoir ownership.
   The canonical multi-edge line-up contract now supports branch/loop outage and brownfield
   actions through #4188/#3154 optimization and is exercised on live production and
   multi-zone injection wells with controlled reservoir-pressure updates. Typed configured
   pump/compressor maps and shared `ProcessModel` power accounting are now supported.
   Ten-well production and separate live two-zone injection periods now have synthetic
   moving separator, scrubber and shared-power acceptance coverage. A shared-reservoir
   production/live-injection case now checks component inventory and accepted-step ownership.
   The one-day shared-reservoir test additionally checks common-time timestep refinement
   for its smooth synthetic trajectory. Its exact accepted pressure, production/injection,
   shared-power and cumulative-throughput samples now map into schema-v3 lifecycle state
   metrics with stable IDs, units and provenance; the persistent guard reconstructs every
   adjacent-period delta. Next qualify the maps and injectivity laws and add
   map-region/driver-limit migration and discontinuous-event refinement without creating a
   second scheduler or optimizer.
2. Qualify steady arrival temperature, DNV-ST-F101 and DNV-RP-F109 route cases against independent
   field, laboratory or commercial-simulator evidence. The software contract for staged
   OD/ID/wall/material/installation/insulation/stability alternatives now exists with synthetic
   fail-closed acceptance evidence; independent qualification is still required before design use.
   Then couple qualified alternatives to multi-period lifecycle economics and reduced-order
   surrogates.
3. Coordinate conservative transient junction/component/energy integration and steady
   initialization with #2911. Enable dynamics only within a quantitatively tested scope.
4. Add reviewed Java/Python builders, agent/MCP routes (#3153) and DEXPI identity export
   (#2899/#1332). Use synthetic/public acceptance cases and retain reproducible results.

Related guides: [production networks](../process/equipment/production_well_networks.md),
[gas network coupling](../process/gas_network_operations.md),
[integrated production](INTEGRATED_PRODUCTION_MODELLING.md), and
[field lifecycle](FIELD_LIFECYCLE_SIMULATION.md).
