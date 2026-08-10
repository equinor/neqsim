---
title: Dynamic capability contract
description: Machine-readable classification and audit of algebraic, lumped, distributed, boundary, and control-system transient behaviour in ProcessSystem and ProcessModel.
---

NeqSim distinguishes **participation in a transient flowsheet** from **having audited dynamic state**. This is important
because an algebraic unit operation can be re-evaluated at every physical timestep without containing inventory, inertia,
or another state that is integrated through time.

The capability contract is a Phase-0 foundation for professional dynamic simulation on both `ProcessSystem` and
multi-area `ProcessModel`. It is diagnostic metadata, not a claim of commercial-simulator parity, quantitative model
validation, standards conformance, or accountable safety approval.

## Capability categories

| Capability | Meaning |
|---|---|
| `ALGEBRAIC` | No audited stored physical state. The element may still be solved as an algebraic relation at each timestep. |
| `DYNAMIC_LUMPED` | Audited lumped state such as vessel inventory, thermal storage, actuator position, or rotating inertia. |
| `DYNAMIC_DISTRIBUTED` | Spatially distributed transient state, for example finite-volume or method-of-characteristics pipeline state. |
| `BOUNDARY_DYNAMIC` | Time-varying boundary/source state such as reservoir depletion or another imposed dynamic boundary. |
| `CONTROL_DYNAMIC` | Controller, transmitter, signal, logic, or sampled control-system transient state. |
| `UNCLASSIFIED_DYNAMIC` | A custom `runTransient` implementation exists, but its state/equations have not yet been audited. |
| `UNSUPPORTED_DYNAMIC` | The element explicitly declares that a transient interpretation is unsupported. |

`UNCLASSIFIED_DYNAMIC` is deliberately conservative. The presence of a `runTransient()` override is not sufficient
evidence that an implementation is a physically complete dynamic model.

## Inspect a ProcessSystem

```java
import neqsim.process.dynamics.DynamicCapabilityReport;

DynamicCapabilityReport report = DynamicCapabilityReport.from(process);

report.getCapabilityCounts();
report.getBlockingIssues();
report.getReviewItems();
report.getInactiveAuditedDynamicElements();
String json = report.toJson();
```

A normal algebraic element is **not** a blocking issue. It becomes a blocking configuration only when the caller sets
`calculateSteadyState = false` even though the element has no audited difference-equation implementation. This catches a
common failure mode where a flowsheet appears to be configured for dynamics but the selected unit cannot execute the
requested dynamic path.

`getInactiveAuditedDynamicElements()` has the opposite purpose: it identifies equipment that has audited dynamic state
but is currently left in its steady-state/algebraic mode. This is not automatically wrong. Mixed algebraic/dynamic
flowsheets are normal, but the list makes the modelling choice explicit for engineering review.

## Strict transient preflight

The report also provides an explicit, opt-in preflight for workflows that must not continue with unaudited custom
transient implementations:

```java
DynamicCapabilityReport report = DynamicCapabilityReport.from(process);

if (!report.isStrictPreflightReady()) {
  for (String issue : report.getStrictPreflightIssues()) {
    logger.warn("Dynamic preflight: {}", issue);
  }
}

report.assertStrictTransientReady();
```

Strict preflight combines known unsupported runtime configurations with `UNCLASSIFIED_DYNAMIC` review items. It does
**not** reject an audited dynamic unit merely because the unit is intentionally left in steady-state mode. The preflight
is not automatically called by `runTransient(...)`; it is an explicit qualification gate so existing transient APIs and
mixed algebraic/dynamic models remain backwards compatible.

Passing strict preflight only means that the capability audit found no known unsupported configuration or unaudited
custom transient method. It does not establish conservation accuracy, timestep independence, pressure-flow correctness,
control/safety fidelity, OTS real-time performance, or engineering approval.

## Physical-step versus refinement identity

A non-null calculation identifier supplied to `ProcessSystem.runTransient(dt, id)` or `ProcessModel.runTransient(dt, id)`
identifies **one physical timestep**. Stateful equipment and controllers use that identifier to prevent repeated
algebraic/nonlinear evaluations inside the same physical timestep from advancing clocks, controller integrals or other
mutable state more than once. Therefore:

- repeated evaluations/refinements inside physical step A reuse physical-step ID A;
- the next physical timestep uses a different physical-step ID B;
- a fixed UUID must not be reused across an outer time-marching loop;
- `runTransient(dt)` is safe for ordinary loops because it creates a fresh UUID for each call;
- deterministic safety/OTS/replay workflows can use `TransientStepIdentifier.deterministicPhysicalStep(scope, index)`.

```java
import neqsim.process.dynamics.TransientStepIdentifier;

for (long step = 0; step < 600; step++) {
  java.util.UUID physicalStepId =
      TransientStepIdentifier.deterministicPhysicalStep("startup-case-A", step);
  process.runTransient(1.0, physicalStepId);
}
```

Do **not** create one UUID before the loop and pass it to every timestep. That pattern can suppress later controller
updates because built-in controller execution is idempotent for one physical-step identifier.

`TransientStepIdentifier.deterministicEvaluation(physicalStepId, evaluationIndex)` provides a separate deterministic
identity for nonlinear/refinement diagnostics. It is metadata for residual histories and solver bookkeeping; it must not
replace the parent physical-step ID in equipment/controller `runTransient` calls. This preserves the A/refine-A/B
contract: refinements share A for state idempotency while their diagnostic evaluations can still be distinguished.

`DynamicSafetyScenarioRunner` uses deterministic physical-step identifiers derived from scenario ID and step index so a
replayed scenario is reproducible without freezing controllers after its first timestep.

## Inspect a ProcessModel

```java
DynamicCapabilityReport plantReport = DynamicCapabilityReport.from(processModel);

for (DynamicCapabilityReport.Entry entry : plantReport.getEntries()) {
  String object = entry.getQualifiedName();  // e.g. "compression::K-100"
  DynamicCapability capability = entry.getCapability();
}
```

Multi-area reports preserve the process-area identity so identical equipment or stream names in separate areas do not
collapse into one audit entry. Controllers attached directly to equipment are included and de-duplicated by object
identity if they are also registered as standalone controllers.

## Nested process modules

`DynamicCapabilityReport` recursively inspects initialized `ModuleInterface` contents instead of stopping at the module
container. Nested entries retain a deterministic container path:

```text
separation train::Inlet separator
topside::separation train::HP gas scrubber
```

The module container itself remains visible in the report. Current `ProcessModuleBaseClass.runTransient(...)` delegates
to its internal `ProcessSystem`, but the campaign has not yet qualified all module-level initialization, state ownership,
restart, error propagation, or nested transient semantics; modules therefore remain reviewable according to their own
capability classification while their instantiated child operations are audited independently.

The report never initializes a module as a side effect. A module whose internal `ProcessSystem` has not yet been built
can only expose the container at audit time. Initialize/build the module through its normal model lifecycle before using
the report as a complete nested-unit inventory.

Identity-based de-duplication prevents the same process element or nested `ProcessSystem` object from being counted
repeatedly and prevents accidental recursive container cycles from causing unbounded traversal.

## Initial audited mapping

The initial contract intentionally classifies only core implementations whose current source contains clear stored-state
semantics:

- lumped: separators, tanks, two-stream heat exchangers, compressors, pumps, and throttling/control valves;
- distributed: `TwoFluidPipe`, drift-flux `TransientPipe`, and `WaterHammerPipe`;
- boundary: `SimpleReservoir`;
- control: registered controllers and measurement devices.

`Stream` and stream subclasses that inherit the standard `Stream.runTransient(...)` boundary are classified as
`ALGEBRAIC`: that method re-evaluates the stream and advances its execution clock, but does not integrate stored physical
state. A stream subclass that provides its own transient override remains `UNCLASSIFIED_DYNAMIC` until its state and
equations are audited.

Other custom transient implementations remain `UNCLASSIFIED_DYNAMIC` until their state variables, conservation equations,
initialization, timestep constraints, event behaviour, snapshot/restart semantics, and quantitative validation are
reviewed. The mapping is expected to expand as that audit is completed.

## What this does not solve

This contract does not yet provide the plant-wide vector ODE/DAE solver required for strongly coupled professional
dynamics. In particular, it does not add global pressure-flow residual assembly, sparse Jacobians, whole-model timestep
rejection/rollback, event localization, or multi-rate pipeline subcycling. Those capabilities build on this contract so
the solver can reason explicitly about which objects own dynamic state and which objects are algebraic constraints.

The report also does not certify that a model is suitable for a control, relief, HIPPS/SIS, HAZOP, DEXPI/P&ID, virtual
commissioning, OTS, or other safety-critical study. Those studies require scenario-specific modelling, validation
evidence, engineering limits, deterministic timing/replay evidence where applicable, and appropriate review.
