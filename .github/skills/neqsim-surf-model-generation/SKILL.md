---
name: neqsim-surf-model-generation
version: "1.0.0"
description: "Generates the executable NeqSim SURF model from a surf_study.v1 spec: FieldNetworkTopology over LoopedPipeNetwork, live WellFlow wells, FieldWellNetworkProcessUnit with a host separator, SubseaProductionSystem line designs, umbilical networks, ID mapping manifest and an editable Python builder. USE WHEN: a subsea network must be built from structured inputs, concepts compared, or a user-built model adopted without overwriting equipment."
last_verified: "2026-10-25"
requires:
  python_packages: [jpype1, pyyaml]
---

# SURF model generation

Library: `devtools/surf_spec.py` (specification), `surf_model.py` (plan, executor, builder emission, mapping manifest), `surf_run.py`
(execution and extraction). It creates and wires **existing** NeqSim owners and adds no solver. Read `neqsim-surf-design-study` first.

## Contract

`surf_spec.check_spec(spec)` returns `(findings, model)`; `surf_model.build_plan(spec, model, concept, case, domain)` returns an ordered
operation list; `surf_model.execute_plan(plan)` returns a `Domain` (`topology`, `coupler`, `unit`, `wells`, `sps`); `emit_python(plan, ...)`
writes a stand-alone builder whose literal plan equals the generated plan.

| Spec item | NeqSim owner |
|-----------|--------------|
| nodes (wells, manifolds, PLEM/PLET, host, source) | `FieldNetworkTopology.addLiveWellNode/addJunction/addFixedPressureSink/addPressureSource` |
| edges (jumper, flowline, trunkline, riser, pump) | `addPipe(..., PipeModelType)` + `setMultiphaseSegments`, `setElevationProfile`; `addPump` |
| thermal design | `SubseaProductionSystem.setLineDesign` + `applyLineThermalDesigns` (U-value on the bore area) |
| producers | `WellFlow` productivity index bound with `FieldWellNetworkCoupler.bindProductionWell` |
| injectors | `WellFlow.addLiquidInjectionZone` (kg/(s bar)) bound with `bindInjectionWell` |
| process composition | `FieldWellNetworkProcessUnit` + `Separator` in a `ProcessSystem`, added to a `ProcessModel` |
| structural screening | `LineDesign.dnvStF101Input` through `assessDnvStF101LineDesigns` (all inputs explicit, none defaulted) |
| chemical tubes | one Darcy-Weisbach `LoopedPipeNetwork` per tube |

Services are separate hydraulic domains (different fluids); identity is shared through the mapping manifest
(`model/mapping_manifest.json`: spec ID -> hydraulic node/edge, equipment binding, line design, drawings, quantities).

## Things that bite

- Every edge needs unique `from_port`/`to_port` per node; duplicates are a validation error.
- Set a flowing temperature on **every** node that starts a thermal path: a junction downstream of a pump is 15 degC by default.
- Live wells need an initial node pressure that gives a positive first rate (injectors: the pump discharge pressure; zero initial flow makes the thermal coupling NaN).
- `FieldWellNetworkProcessUnit.getOutletStream` exists only after the first `run`; build the host separator after it.
- `LoopedPipeNetwork.setTolerance` is in Pa (use about 100 Pa), not bar.
- `LineDesign.installationMethod` accepts only S-lay, J-lay and Reel-lay (cost basis); the free-text method is documentation.
- A rigid `LineDesign` must match the hydraulic ID within 2 mm (checked by `SubseaProductionSystem`); a DNV-ST-F101 input needs the exact OD and wall.
- A `BEGGS_BRILL_FALLBACK_DARCY` status means no distributed profile: the case is unusable, not a Darcy result.
- JSON replay (`topology.toJson()`) is a definition, not a restart state; runtime wells, fluids and bindings are rebound by the builder.

## Topology variants

A concept is a set of active edges (`edges[].concepts`) plus `edge_overrides`; IDs stay stable and each concept is generated and solved separately.
`FieldWellNetworkProcessUnit.registerRouteConfigurationSelector` supports availability-based line-ups on one superset graph for operations
studies; the study toolkit builds filtered concepts so cost, MTO and drawings stay concept-exact. Unsupported variants are rejected, not ignored.

## Adopting a user-built model

Use the spec only for identity and checks: register existing nodes/edges with `FieldNetworkTopology.registerExistingNode/registerExistingEdge`
and bind equipment with `bindEquipment`; never regenerate or overwrite detailed equipment. Verify these method names against the Java source before use.
