---
title: Industrial Process Optimization Baseline
description: Capability inventory, restriction coverage, and frozen benchmark contract for large ProcessSystem and ProcessModel optimization.
---

# Industrial Process Optimization Baseline

This document freezes the starting point for industrial-scale optimization in
[roadmap #3154](https://github.com/equinor/neqsim/issues/3154). It distinguishes an
implemented calculation from evidence that is safe to use for optimizer acceptance and defines
the benchmark contract for later performance and solver changes.

The audited source baseline is NeqSim `master` commit
`e537b6c24eb8a5137e79bdd8956820804a37bcc6` (23 August 2026). The inventory is a source and test
audit, not a claim that every plant restriction below has been qualified for design or operations.

## Engineering question and stop boundary

The engineering question is: which current NeqSim APIs can represent, solve, constrain, and report
a large multi-area plant without losing constraint identity, units, provenance, convergence state,
or the ability to restore the original plant?

This baseline freezes terminology, coverage, benchmark cases, measurements, and acceptance gates.
It does not change process calculations, equipment correlations, convergence algorithms, caching,
parallel scheduling, or optimizer search behavior. Those changes require measured before/after
evidence against this contract and remain owned by their corresponding roadmap increments.

## Qualification levels

| Level | Meaning |
|---|---|
| **Optimizer-qualified** | The full model produces finite, applicable evidence with stable identity and units; failures are visible and the candidate can be rejected deterministically. |
| **Composable** | A NeqSim calculation or equipment model exists and can be registered as a constraint, but no complete plant-wide evidence contract is supplied automatically. |
| **Calculation only** | A relevant calculation exists, but it is not yet connected to steady-state optimizer acceptance. |
| **Gap / owner handoff** | The required evidence or solver behavior is absent, incomplete, or belongs to another roadmap. |

Only optimizer-qualified evidence may accept a candidate. Advisory calculations may screen or
explain an operating point, but they must not silently become safety, integrity, or design limits.

## Current reusable foundation

| Capability | Current API or implementation | Qualification | Important boundary |
|---|---|---|---|
| Single-area equipment capacity | `CapacityConstrainedEquipment`, `CapacityConstraint`, `EquipmentCapacityStrategyRegistry` | Composable | Many equipment constraints are disabled until an installed/design basis is configured. |
| Whole-plant capacity aggregation | `ProcessModel.getConstrainedEquipment()`, `findBottleneck()`, `getCapacityUtilizationSummary()`, `getUtilizationSnapshotJson()` | Composable | The legacy JSON snapshot is not a complete immutable evidence ledger for all plant restrictions. |
| Installed-equipment evidence | `InstalledEquipmentCapacityEvidence` | Optimizer-qualified for sampled installed constraints | Preserves area/equipment/constraint identity, physical and normalized units, origin, provenance, confidence, validity, applicability, numerical status, and feasibility. |
| Process-boundary evidence | `ProcessBoundaryConstraintEvidence` | Optimizer-qualified when registered with complete metadata | Covers injection, receiving capacity, export capacity, product quality, and nomination; it does not auto-discover plant boundaries. |
| Full-model candidate replay | `ProcessModelSimulationEvaluator` | Optimizer-qualified for finite inputs and sampled outputs | Replays the complete candidate on the `ProcessModel`; incomplete convergence must still be rejected by the calling solver. |
| Operating actions | `ProcessModelOperatingAction`, `ProcessModelOperatingActionEvaluator`, `ProcessModelOperatingActionSetEvaluator` | Optimizer-qualified for their tested transactional scopes | Supports continuous and discrete actions with provenance and restoration; it is not yet a general plant optimization orchestrator. |
| Throughput and debottleneck studies | `ProcessModelThroughputOptimizer`, `ProcessModelDebottleneckStudy`, `ProcessModelAllocationOptimizer` | Composable | Results depend on the registered evidence set and do not make missing constraints safe. |
| Multi-area adapter for `ProductionOptimizer` | `ProcessModelOptimizationView` | Composable | It logs failed cross-area convergence and currently permits the legacy optimizer to continue; this path is not fail-closed. |
| Convergence evidence | `ProcessModel.runUntilConverged()`, iteration count, boundary errors, convergence report JSON | Composable | Candidate acceptance needs one common policy for process convergence, residuals, unit failures, and balances. |
| Process profiling | `ProcessSystem.getExecutionProfile()`, elapsed time, graph/recycle reports; `ProcessModel` convergence diagnostics | Calculation only | End-to-end equipment/area execution and flash/property attribution are not yet one benchmark record. |
| Power and energy networks | `ProcessModel.getPower(unit)`, `EnergyBus`, `MechanicalShaft`, energy-network and drive-train classes | Composable | No plant-wide shared-resource constraint identity yet combines total power with common-shaft driver/torque/speed evidence. |
| Python/JPype access | Public Java beans, JSON reports, examples using `jpype` | Composable | A compact, schema-versioned complete plant result remains a later roadmap item. |

The merged foundation in
[#2941](https://github.com/equinor/neqsim/issues/2941) supplies stable decision, constraint,
boundary, sensitivity, action, throughput, and debottleneck evidence. New work must extend those
objects or adapt existing equipment calculations; it must not introduce a competing evidence
model.

## Plant restriction coverage

The following matrix records steady-state optimizer coverage. Dynamic protection calculations and
mechanical-design calculations are useful evidence sources, but remain advisory until sampled with
an explicit limit basis, provenance, applicability, and failure status.

| Restriction family | Existing calculation or constraint | Current qualification | Missing industrial contract |
|---|---|---|---|
| Piping pressure and pressure drop | Pipe models and `PipeCapacityStrategy.pressureDrop` | Composable | Stable line/segment identity, inlet/outlet pressure limits, governing phase/basis, and failed-hydraulics status in the plant snapshot. |
| Piping velocity and erosion | `PipeCapacityStrategy.velocity`, mechanical-design velocity limits | Composable | Separate actual/standard basis, phase-specific and mixture velocity, erosion basis/provenance, validity range, and direction-aware margin. |
| Piping FIV | LOF and FRMS analyzers and pipeline constraints/examples | Calculation only / composable where registered | Unified finite evidence, method/version, wall/geometry basis, applicability, and explicit advisory-versus-hard policy. |
| Piping thermal and phase envelope | Pipeline thermal models, stream phase state, hydrate/wax/corrosion tools | Calculation only | Arrival-temperature, phase-fraction, hydrate/wax/corrosion limits with sampling status and owner-qualified validity. |
| Gathering, routing, and export hydraulics | Network actions, boundary evidence, pipe equipment | Composable | One network constraint registry covering pressure, capacity, line-up availability, and line/route identity. |
| Compressor map envelope | Speed, power, surge margin, stonewall margin, and discharge-temperature constraints | Composable | Installed-map provenance, corrected-flow/head basis, interpolation/extrapolation status, and complete evidence for chartless or out-of-map points. |
| Compressor driver and total power | Equipment power constraints, `ProcessModel.getPower(unit)`, power-generation capacity | Composable | Shared driver identity, plant total-power budget, fuel/electrical basis, reserve/spinning margin, and load-shed priority. |
| Common-shaft trains | `MechanicalShaft`, `EnergyBus`, motor-assisted and motor drive trains | Calculation only | Common speed, driver power, torque, gearbox, train availability, and per-casing map constraints evaluated as one resource. |
| Two-phase separators | Gas load factor and liquid level; mechanical-design retention and internals calculations | Composable | Gas, liquid, residence, settling, carry-over/carry-under, level, inlet momentum, demister, and slug limits in one immutable snapshot. |
| Three-phase separators | Gas load factor, level, three-phase process and mechanical-design calculations | Composable | Independent oil/water residence and settling, interface/boot/weir limits, oil-in-water, water-in-oil, slug volume, and phase-availability status. |
| Pumps | Power, NPSH margin, and flow-rate constraints | Composable | Installed curve/basis, operating envelope, minimum continuous stable flow, driver/shared-power identity, and non-calculable NPSH status. |
| Valves and manifolds | Opening/Cv and equipment capacity strategies | Composable | Installed trim/line-up identity, choked/cavitating/flashing status, noise/erosion advisory evidence, and availability. |
| Heat transfer | Duty and outlet-temperature constraints; exchanger design calculations | Composable | Shared heating/cooling utility budgets, approach/pinch, fouling/design basis, temperature cross, and unavailable-duty status. |
| Columns and treating | Flooding, weir loading, tray pressure drop, condenser/reboiler duty strategies | Composable | Internals-qualified evidence, product specification coupling, solvent/regeneration utilities, and owner-roadmap convergence behavior. |
| Fuel, cooling, heating, injection, and chemicals | Energy buses, utility equipment, arbitrary boundary constraints | Composable | First-class shared-resource identity, allocation, priority, units/basis, and one plant-wide utilization snapshot. |
| Flare, produced water, and emissions | Flare, water-treatment, chemistry, and emissions calculations | Calculation only / composable where registered | Steady-state capacity/specification evidence with provenance; relief and upset design remain advisory and outside production optimization authority. |
| Product quality and export | Boundary kinds for product quality/export/nomination and network quality results | Optimizer-qualified when explicitly registered | Discovery, standard-condition identity, contract period, uncertainty, and a complete default set of export-quality observables. |
| Configuration and availability | Discrete operating actions and process lifecycle state | Composable | Mutually exclusive line-ups, maintenance/availability state, minimum run rules, restoration proof, and discrete-search orchestration. |
| Safety and integrity | Mechanical design, diagnostics, alarms, safety and flow-assurance tools | Advisory | No optimizer may infer permission to operate. Only caller-approved, explicit limits can be hard constraints; protection and design verification remain independent. |

## Frozen benchmark matrix

Each benchmark is a deterministic synthetic or public-data case. Proprietary plant data is not
required. Case builders must assign stable area, equipment, boundary, action, resource, and
constraint identifiers and must state every physical unit and rate basis.

| ID | Required case | Minimum scope | Required bottleneck or failure transitions |
|---|---|---|---|
| S | Current production-optimization guide case | Existing small flowsheet and documented decision variables | Feasible point, one installed-equipment limit, one invalid candidate. |
| M | Medium multi-train plant | 25–50 units, at least two trains and one recycle | Piping to compressor to separator or total-power bottleneck as one continuous variable changes. |
| L | Large multi-area plant | At least 150 units, six areas, cross-area boundaries, recycles, shared resources | At least four ordered bottleneck changes and one export-quality constraint. |
| C | Common-shaft compressor train | At least two compressor casings on one driver/shaft | Individual map limit, shared shaft/driver power or torque limit, and unavailable casing/line-up. |
| B | Bottleneck-shift suite | Reusable variants of M or L | Piping, compressor, shaft/total power, separator, and export-quality each become binding in a controlled variant. |
| R | Infeasible restoration | Any case with a deliberately invalid action and failed solve | Exception, non-finite proposal, incomplete convergence, invalid evidence, and restoration failure are each rejected. |

Every applicable case is executed in these modes:

1. **Cold:** newly constructed model and empty optimizer cache.
2. **Unchanged:** repeat the identical candidate without changing the constraint set.
3. **Nearby state:** small continuous perturbation that preserves topology and validity ranges.
4. **Constraint change:** identical process state with a changed limit, validity, or resource availability.
5. **Discrete line-up:** a reversible topology or availability action followed by full replay.

The harness must use a fixed JVM, heap, thermodynamic model, fluid definition, seed, warm-up policy,
measurement tool, sample count, and operating-system description. Wall-clock comparisons require at
least five measured forks or a statistically justified alternative. Report median, dispersion, and
raw samples; a single timing is not evidence of improvement.

## Frozen measurement record

One record is emitted for every case, mode, candidate, and repetition. It must contain:

- exact NeqSim commit, case schema version, case identity, seed, JVM/OS/CPU and heap settings;
- unit, area, connection, recycle, boundary, action, decision-variable, objective, constraint, and
  shared-resource counts;
- run outcome, process and area convergence, iteration counts, maximum residual and worst boundary;
- failed unit and exception, finite-evidence status, feasibility, rejection reason, and restoration
  result;
- objective values, constraint values and margins, units, basis, provenance, confidence, validity,
  applicability, and ranked active/binding constraints;
- equipment and area execution counts, attributable flash/property work when available, and cache
  hits, misses, invalidations, and calculation identity;
- feed/product mass and energy balances and configured tolerances;
- elapsed time, allocated bytes or a documented memory proxy, peak used heap, and serialized result
  size; and
- repeatability against the prior identical run and the expected bottleneck transition.

Unknown metrics are recorded as unavailable with a reason; they are never written as zero. A
benchmark PR must preserve raw machine-readable records and a concise human-readable comparison.

## Executed S/M baseline

Cases S and M now have a maintained test-only harness and a reproducible five-fork reference record
at exact commit `f3a2cf5f0891322ab2462817f0c06d0d9409f1f6`. Case M contains 27 units, three parallel
compression trains, and one tail recycle. The checked-in runner preserves the exact harness schema,
validates every mode and acceptance threshold, derives statistics from the raw reports, and records
canonical SHA-256 digests. The harness exercises cold, unchanged, nearby-state, constraint-change,
discrete-line-up, restoration, and non-finite-candidate rejection modes without changing production
execution or solver behavior.

See the [Industrial S/M Benchmark Evidence](industrial-sm-benchmark) for the exact command,
environment, raw samples, acceptance criteria, and unavailable metrics. The record intentionally
does not claim the complete M bottleneck-shift requirement: it observes an export-pipe constraint
and a controlled installed feed-pipe limit, while the ordered piping/compressor/separator shift
remains dependent on the next stable plant constraint-identity increment.

## Acceptance gates for later increments

A later implementation may claim improvement only when all applicable gates pass:

1. **Correctness:** the full NeqSim model is replayed; thermodynamic consistency, mass/energy
   balances, constraint identity/units/basis/provenance, and expected bottleneck transitions pass.
2. **Fail closed:** exception, non-finite value, incomplete convergence, stale evidence/cache,
   invalid applicability, or incomplete rollback cannot produce an accepted candidate.
3. **Isolation:** concurrent candidates never share mutable process state; deterministic repeated
   runs produce equivalent evidence and leave the caller's baseline state unchanged when promised.
4. **Performance:** end-to-end L-case improvement is statistically supported without a material
   regression in S or M. Kernel-only microbenchmarks cannot satisfy this gate.
5. **Scalability:** result size and memory are bounded or scale predictably with the recorded plant
   dimensions; caches have explicit identity, invalidation, and capacity policies.
6. **Compatibility:** changes are additive, Java 8 compatible, serializable where required, and
   directly usable through JPype without custom Java callbacks for ordinary reporting.
7. **Documentation:** public behavior, assumptions, units, limitations, examples, and benchmark
   evidence are updated in the same pull request.

## Dependency and owner handoffs

- Execution profiling, dirty/dependency scheduling, recycle/convergence orchestration, caching,
  allocation, and end-to-end performance gates coordinate with
  [#2939](https://github.com/equinor/neqsim/issues/2939).
- Dynamic equipment and plant behavior coordinates with
  [#2911](https://github.com/equinor/neqsim/issues/2911).
- Column internals and column solver evidence coordinate with
  [#2936](https://github.com/equinor/neqsim/issues/2936).
- Flash internals and flash performance coordinate with
  [#2937](https://github.com/equinor/neqsim/issues/2937).
- Pipeline/two-fluid numerical internals stay with their owner roadmap; #3154 consumes their
  qualified outputs rather than implementing competing hydraulic kernels.
- Agent/server exposure coordinates with
  [#3153](https://github.com/equinor/neqsim/issues/3153) after the Java evidence and result schemas
  are stable.

## Stable plant constraint registration contract

`PlantConstraintScope`, `PlantConstraintDefinition`, `PlantConstraintParticipant`, and
`PlantConstraintRegistry` provide the dependency-ready identity layer for the restriction inventory
above. A constraint is addressed independently of Java object identity at equipment, stream, area,
model, shared-resource, or coupled-group scope. Registration retains unit, basis, provenance, owner,
reference, calculation method, confidence, validity, severity, and enablement. Disabled and incomplete
registrations remain visible instead of disappearing from the audit trail.

Registration is deterministic across insertion order and Java serialization. Aggregation is explicit,
participants are ordered by stable identity, and unlike units or bases are rejected unless the caller
supplies a finite conversion. The equipment adapter copies the established #2941
`CapacityConstraint` metadata without evaluating or retaining its mutable value supplier.

`PlantConstraintSample`, `PlantConstraintEvidence`, and `PlantUtilizationSnapshot` now provide the
next additive post-solve layer. A snapshot retains one row for every registry definition, requires an
exact calculation identity, validates finite unit/basis-consistent evidence, and fails closed for
missing, stale, out-of-validity, exception, metadata-mismatched, or incomplete-convergence evidence.
It exposes deterministic complete coverage and bottleneck ladders without retaining callbacks or
mutable process state. Existing installed-equipment and boundary evidence are adapted rather than
recalculated or duplicated.

This snapshot layer does not aggregate total power, coordinate a common shaft, compute separator or
piping physics, alter process solving, or accept an optimizer proposal. Those operations remain later
increments and must provide qualified samples after a complete full-model solve.

## Next dependency-ready increments

1. Add first-class total-power/shared-utility evidence, using the complete immutable utilization
   snapshot without adding execution-layer scheduling or caching.
2. Add a common-shaft compressor-train
   resource constraint.
3. Complete separator and piping evidence adapters before adding fail-closed scalable evaluation
   and solver orchestration.
4. Expose the stable result through Java JSON and Python/JPype, then execute M, L, C, B, and R as
   maintained workflows.

Until those increments are merged and measured, this page is the authoritative baseline contract,
not a declaration that industrial-scale optimization is complete.
