---
title: "Current NeqSim Engineering Capabilities"
description: "Current-state map of implemented NeqSim engineering workflows, discipline modules, safety verification, qualification, deliverables, exchange, and model lifecycle APIs."
keywords: "NeqSim engineering capabilities, engineering design loop, production readiness, DEXPI, CFIHOS, safety lifecycle, model package, impact analysis"
---

# Current NeqSim Engineering Capabilities

NeqSim now implements the complete software workflow from a governed process model to calculated, traceable, and
review-ready engineering evidence. The workflow contracts are implemented and regression-tested. The maturity of an
individual result still depends on the selected method, its applicability envelope, project inputs, independent
evidence, vendor information, and accountable approval.

This distinction is important: the engineering framework is operational, while built-in screening or preliminary
methods remain review-required until their exact versions are qualified for the intended service.

## Choose the correct entry point

| Need | Primary API | Result |
| --- | --- | --- |
| Run controlled cases without sizing | `EngineeringCaseRunner` | Isolated case results, fingerprints, numerical health, and governing envelope |
| Build a custom iterative design workflow | `EngineeringDesignLoop` | Iterations, selected variables, applied updates, constraints, convergence, and designed process copy |
| Engineer one `ProcessSystem` with explicit modules | `ProcessToEngineeringDesignBuilder` and `ProcessToEngineeringSimulator` | Closed process/design loop with controlled cases and discipline modules |
| Configure one system from a controlled policy | `EngineeringAutoConfigurationPolicy` and `EngineeringAutoConfigurator` | Equipment coverage, module dependencies, blockers, policy fingerprint, and invalidation scope |
| Coordinate several process areas | `ProcessModelEngineeringSimulator` | Per-area packages, shared-stream and shared-system dependencies, incremental reruns, and root manifest |
| Qualify a controlled facility slice | `ProductionVerticalSliceSimulator` | Preflight, execution manifest, nine-gate qualification, and controlled-pilot status |
| Compile a coordinated package | `EngineeringDeliverableCompiler` | Canonical graph, cases, calculations, registers, DEXPI, readiness, schemas, manifests, and model package |
| Generate a review-required P&ID proposal | `PidDesignSynthesizer` and `PidEngineeringPackageExporter` | P&ID model, completeness report, HAZOP preparation, DEXPI representations, and package manifest |
| Assess production-engineering evidence | `EngineeringProductionReadinessAssessment` | Fail-closed readiness gates and exact qualification backlog |
| Manage a governed model revision | `NeqSimModelPackage`, `ModelChangeEvent`, and `GeneralizedImpactAnalyzer` | Integrity-protected revision, durable change event, and recalculation/reapproval work list |

For most single-system studies, start with `EngineeringProject`, add controlled `EngineeringDesignCase` definitions,
configure explicit modules or an explicit auto-configuration policy, and call `ProcessToEngineeringSimulator.run(...)`.

## Implemented engineering workflow

The current workflow provides:

1. a revisioned project, design basis, standards, requirements, evidence, approvals, and tagged process model;
2. isolated normal, capacity, turndown, environmental, trip, shutdown, settle-out, fire, blocked-outlet, and blowdown
   case definitions;
3. deterministic case execution, numerical-health assessment, metric limits, and governing-value selection;
4. an iterative design loop that applies selected dimensions and ratings to an isolated copy, reruns the cases, and
   requires stable design variables, stable process values, converged cases, and satisfied constraints;
5. explicit discipline modules and policy-driven orchestration without hidden engineering limits;
6. dynamic control and safeguarding evidence linked to relief, blowdown, flare, compressor-trip, and facility-response
   studies;
7. a canonical engineering graph and calculation DAG connecting values, methods, cases, evidence, requirements,
   approvals, and generated documents;
8. coordinated registers, datasheets, reports, DEXPI exchanges, CFIHOS staging, schemas, validation, and manifests;
9. production-readiness, method-applicability, independent-benchmark, pilot, release, named-tool, and external-evidence
   gates; and
10. portable model packages, model-change events, graph differences, impact propagation, incremental reruns, and
    reapproval scope.

## Discipline coverage

| Area | Design-loop implementation | Higher-assurance or qualification interface |
| --- | --- | --- |
| Separation and inventory | `SeparatorProcessDesignModule`, `InventoryEquipmentDesignModule` | Controlled design cases, retention/capacity evidence, detailed vessel and internals review |
| Compressors | `CompressorPackageDesignModule`, `CompressorOperatingEnvelopeDesignModule` | `CompressorProtectionQualificationCalculation` for maps, startup, rundown, anti-surge, rotor, settle-out, and vendor constraints |
| Pumps | `PumpPackageDesignModule` | Driver margin, NPSH, operating-envelope, transient, and vendor review evidence |
| Heat exchange | `HeatExchangerPreliminaryDesignModule` | Duty/area basis, utility envelope, detailed thermal/mechanical design, and vendor evidence |
| Columns and packaged systems | `RatedCapacityDesignModule` with controlled metrics | Project-specific rating, hydraulics, internals, utility, control, and vendor methods |
| Lines and networks | `LineHydraulicDesignModule`, `PipingNetworkDesignModule` | `PipingNetworkDesignCalculation` and `TransientPipingQualificationCalculation` |
| Valves and instruments | `ControlValveDesignModule`, `InstrumentRangeAndResponseDesignModule` | `ValveInstrumentQualificationCalculation` for actuator, leakage, response, range, uncertainty, thermowell, installation, and logic evidence |
| Relief and process safety | `ProcessSafetyDesignModule`, `ReliefDeviceDesignModule` | `ReliefSizingCalculation`, coupled relief/blowdown/flare studies, and `FlareConsequenceCalculation` |
| Materials and mechanical | `MaterialSelectionDesignModule`, `PressureEquipmentMechanicalDesignModule` | `MechanicalIntegrityQualificationCalculation` for pressure, buckling, fatigue, loads, nozzles, MDMT, corrosion, fabrication, and NDE evidence |

The design-loop modules select preliminary dimensions and ratings from declared candidates. Detailed calculations can
be attached as typed `EngineeringCalculationModule` results and can replace or extend supplied modules without
changing the case, provenance, graph, package, or revision contracts.

## Control, safety, and safety lifecycle

The implemented safety path is more than a list of static safeguards:

- `SafetyScenarioEngineCalculation` retains scenario type, credibility, governing group, evidence, loads, and
  calculation readiness;
- dynamic safety scenarios verify logic actions, response criteria, ESD isolation, blowdown, and safe-state timing;
- `FacilitySafetyResponseStudy` joins dynamic protection, compressor-trip, disposal-system, and process-limit evidence;
- `SafetyFunctionReliabilityStudy` and `SafetyFunctionDegradedModeAssessment` cover reliability and bypass/degraded
  operating modes;
- `NcsSafetyFunctionStudy` provides a preliminary NCS ESD/HIPPS SIL and transient-verification handoff;
- `HazopLopaSrsWorkflow` connects reviewed HAZOP nodes, eligible IPL credit, residual frequency, and a draft SRS;
- `PidHazopStudyRunner` prepares equipment nodes and deviations from the governed P&ID proposal; and
- `SafetyStudyRevalidationPlanner` identifies safety-study work invalidated by a controlled model change.

These APIs calculate and structure evidence. They do not decide scenario credibility, approve safeguards, assign SIL,
close a HAZOP action, approve an SRS, or authorize operation. See
[HAZOP and LOPA to draft SRS handoff](../process/safety/hazop-lopa-srs-handoff) and the
[safety integration workflow](../integration/process-to-engineering-simulator#safety-and-scenario-integration).

## Multi-area and shared-system engineering

`ProcessModelEngineeringSimulator` coordinates one explicit policy and case set per process area. It records shared
stream identities and accepts an `EngineeringSharedSystemPolicy` for reviewed electrical demand, utility demand,
flare/blowdown concurrency, or other cross-area design values.

`runIncremental(...)` compares model, policy, case, and coordination fingerprints. It reruns changed areas and every
connected dependent, while reusing only unaffected baseline results. `ProcessModelEngineeringPackageValidator`
checks the area packages and root `process-model-engineering-manifest.json`.

Topology alone does not establish simultaneity, scenario credibility, or design allocation. Those remain explicit
project inputs.

## Deliverables and exchange

`EngineeringDeliverableCompiler` creates one coordinated package from the designed process copy and canonical graph.
The current package includes:

- graph, connectivity, calculation DAG, diagram layout, case matrix, governing envelope, numerical-health report,
  discipline package, automation plan, approval ledger, and revision diff;
- equipment, line, valve, instrument, I/O, alarm/trip, relief, utility, materials, external-evidence, and action
  registers or reports;
- equipment and PSV datasheets, shutdown narratives, flare/blowdown evidence, production-readiness assessment, and
  qualification plan;
- native DEXPI 2.0 Plant and Process exchange, Proteus/pyDEXPI representations, structural round-trip evidence, and
  named-tool qualification interfaces;
- versioned JSON schemas, package validation report, compiler and DEXPI manifests, and SHA-256 file digests; and
- `neqsim-model-package.json`, which inventories the governed artifacts and protects their integrity.

`Cfihos20HandoverExporter` can then stage canonical identities and properties through a project-controlled CFIHOS 2.0
mapping. DEXPI conformance, CFIHOS staging, and successful target loading are separate gates.

See [Engineering Deliverables and Handover](deliverables-and-handover) and the
[DEXPI Engineering Guide](dexpi-guide).

## Qualification and readiness

`EngineeringProductionReadinessAssessment` evaluates the current project and supplied
`EngineeringProductionReadinessBasis`. Its maturity levels are:

| Level | Meaning |
| --- | --- |
| `NOT_READY` | The closed engineering workflow or essential evidence is absent |
| `EXPERIMENTAL` | The design loop ran, but qualification gates remain open |
| `VALIDATED_PRELIMINARY` | The implemented technical and validation gates passed, while full release evidence remains incomplete |
| `QUALIFIED_FEED_SUPPORT` | Every configured readiness gate passed for the declared FEED-support purpose |

The assessment can gate:

- exact executed `method@version` keys against independent benchmarks and project qualifications;
- machine-evaluable intended use and applicability envelopes;
- explicit automatic-configuration coverage and absence of hidden defaults;
- named-product DEXPI semantic round trips;
- three independently reviewed pilot scopes and release-quality evidence;
- numerical health, distributed piping transients, compressor protection, valve/instrument qualification, detailed
  mechanical integrity, and flare consequence evidence;
- HAZOP, LOPA, SRS, SIF, and shutdown completeness; and
- immutable external receipts and document hashes for accountable approval, vendor guarantees, independent
  validation, and construction-authority evidence.

`QUALIFIED_FEED_SUPPORT` is not FEED approval. The assessment always retains
`fitnessForConstruction=false` and `finalEngineeringApprovalGranted=false`.

## Revision and impact workflow

The current lifecycle workflow is:

1. compile a revisioned `NeqSimModelPackage` with identity, dependencies, software versions, artifact inventory, and
   SHA-256 hashes;
2. compare canonical graphs with `EngineeringGraph.compareTo(...)`;
3. convert the difference to a deterministic `ModelChangeEvent` with revision, subjects, evidence references,
   idempotency key, and payload fingerprint;
4. run `GeneralizedImpactAnalyzer` to propagate through calculation, document, synchronization, validation, and
   approval relationships;
5. recalculate, regenerate, revalidate, or reapprove the returned scope; and
6. compile the next revision and retain the impact report and earlier decision history.

For multi-area models, use this with `ProcessModelEngineeringSimulator.runIncremental(...)` so unchanged areas are
reused only when their dependencies and fingerprints remain valid.

## Current boundary

NeqSim provides implemented process physics, orchestration, engineering calculations, evidence structures,
qualification gates, exchange, and lifecycle control. A project must still supply and govern:

- the process design basis, credible operating and accidental scenarios, and accepted simultaneity assumptions;
- validated method applicability, independent benchmarks, uncertainty, and company/project rule packs;
- vendor guarantees, detailed mechanical and specialist analyses, material/corrosion decisions, and target-system
  qualification;
- multidisciplinary HAZOP/LOPA/SRS decisions and accountable discipline approvals; and
- regulatory, construction, commissioning, and operating authorization.

The software fails closed when required controlled evidence is missing; it does not fabricate an approval to make a
package appear complete.

## Executable proof points

- [Process-to-engineering simulator notebook](https://github.com/equinor/neqsim/blob/master/examples/notebooks/process_to_engineering_simulator.ipynb)
- [Controlled-pilot vertical slice](https://github.com/equinor/neqsim/blob/master/examples/notebooks/engineering_vertical_slice_controlled_pilot.ipynb)
- [Production qualification workflow](https://github.com/equinor/neqsim/blob/master/examples/notebooks/engineering_production_qualification_workflow.ipynb)
- [Qualified reference facility](https://github.com/equinor/neqsim/blob/master/examples/notebooks/engineering_qualified_reference_facility.ipynb)
- [Complete offshore process engineering study](https://github.com/equinor/neqsim/blob/master/examples/notebooks/complete_offshore_process_engineering_study.ipynb)

Return to the [Engineering documentation hub](./).
