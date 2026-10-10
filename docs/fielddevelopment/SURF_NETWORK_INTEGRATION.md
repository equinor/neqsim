---
title: "Well and SURF Network Integration"
description: "Current functionality, integration decisions, hydraulic fidelity and qualification gaps for production and injection networks."
---

This is the implementation and capability audit for [campaign #4228](https://github.com/equinor/neqsim/issues/4228).
The current baseline is `ffde2f43443ae65d57d02497e31fefb71b637555`.
Source implementation, regression evidence and field qualification are different maturity levels.
Merged increments supply edge-local hydraulic fidelity, typed field/equipment identity, live well
pressure-rate coupling, same-topology hydraulic comparison and a guarded steady-to-transient handoff.
The current increment adds typed powered/control-edge builders and representative multi-template,
daisy-chain, brownfield, injection and large-field acceptance evidence; none of these completes
independent field qualification.

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
basis, material take-off and a quality flag explaining aggregation. Topology geometry is
reduced to length-weighted line diameters and representative per-unit manifold slots and
weight because the current estimator accepts one scalar basis per line category. Mixed
diameters, different manifold designs, route-specific installation methods, umbilical
routing, wall-thickness design and supplier quotations therefore require disaggregated
detailed design; this bridge must not be presented as vendor qualification or AFC cost.

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

## Dependency-ordered continuation

1. Extend the field-scale cases to live `WellSystem`/`WellFlow` production and injection
   boundaries, qualified pump/compressor maps, shared host power/capacity constraints and
   controlled reservoir-pressure updates without duplicating reservoir ownership.
   Add branch/loop outage and brownfield impact studies through #4188/#3154 optimization.
2. Extend the topology-derived `SubseaProductionSystem` screening basis to disaggregated
   route/equipment design cases, then connect it to `NetworkOptimizer` / process
   optimization, detailed lifecycle models and reduced-order surrogates.
3. Coordinate conservative transient junction/component/energy integration and steady
   initialization with #2911. Enable dynamics only within a quantitatively tested scope.
4. Add reviewed Java/Python builders, agent/MCP routes (#3153) and DEXPI identity export
   (#2899/#1332). Use synthetic/public acceptance cases and retain reproducible results.

Related guides: [production networks](../process/equipment/production_well_networks.md),
[gas network coupling](../process/gas_network_operations.md),
[integrated production](INTEGRATED_PRODUCTION_MODELLING.md), and
[field lifecycle](FIELD_LIFECYCLE_SIMULATION.md).
