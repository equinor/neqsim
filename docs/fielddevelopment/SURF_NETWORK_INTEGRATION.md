---
title: "Well and SURF Network Integration"
description: "Current functionality, integration decisions, hydraulic fidelity and qualification gaps for production and injection networks."
---

This is the implementation and capability audit for [campaign #4228](https://github.com/equinor/neqsim/issues/4228).
The baseline inspected was `237364c074ddb0c0f191f00d52528a9cf19ffdc0`.
Source implementation, regression evidence and field qualification are different maturity levels.
This first increment supplies edge-local steady hydraulic fidelity; it does not complete the campaign.

## Current functionality and reuse decisions

| Existing functionality | Current scope and evidence | Integration decision / remaining gap |
|---|---|---|
| `WellFlow`, `WellSystem`, `TubingPerformance` | Production/injection IPR, layered completions and fracture constraints in `WellFlow`; production nodal IPR/VLP with simplified and full correlation modes in `WellSystem`. See `WellFlow` and `WellSystem` tests. | Reuse the well physics and their convergence diagnostics. A live `WellSystem` pressure/rate boundary in the general network remains to be implemented; the existing network IPR edge is not the same API. |
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
holdup/phase-velocity profiles. These are steady trial results, not dynamic state
owned by the field network. Zero-flow evaluation uses the existing static
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
is integration evidence, not an independent physical benchmark. Public multiphase
B&B versus two-fluid comparisons, mesh sensitivity, energy/component closure,
large-field performance and the 20 km/riser campaign cases remain qualification work.

## Dependency-ordered continuation

1. Add typed field/equipment identity and builders as a view over the existing graph,
   including templates, PLEM/PLET and injection-source/host roles. Validate connectivity,
   duplicate identity and solver applicability before equipment execution.
2. Bind live `WellSystem` and production/injection `WellFlow` pressure/rate contracts.
   Reuse full IPR/VLP/injectivity and report inner well residuals; include reservoir
   pressure updates, shut-in, fracture/BHP limits and incompatible fluid diagnostics.
3. Qualify multi-template, daisy-chain, branches/loops and brownfield networks with
   differing well fluids, B&B/two-fluid comparison and representative field sizes.
   Include water, gas and CO2 injection with pump/compressor and shared host constraints.
4. Map the same geometry/equipment to existing SURF design/cost and `NetworkOptimizer`
   / process optimization, then detailed lifecycle models and reduced-order surrogates.
5. Coordinate conservative transient junction/component/energy integration and steady
   initialization with #2911. Enable dynamics only within a quantitatively tested scope.
6. Add reviewed Java/Python builders, agent/MCP routes (#3153) and DEXPI identity export
   (#2899/#1332). Use synthetic/public acceptance cases and retain reproducible results.

Related guides: [production networks](../process/equipment/production_well_networks.md),
[gas network coupling](../process/gas_network_operations.md),
[integrated production](INTEGRATED_PRODUCTION_MODELLING.md), and
[field lifecycle](FIELD_LIFECYCLE_SIMULATION.md).
