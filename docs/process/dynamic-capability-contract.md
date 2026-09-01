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
| `DYNAMIC_LUMPED` | Audited lumped state such as vessel inventory, thermal storage, actuator position, rotating inertia, or rate-limited converter output. |
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
report.getActivationCounts();
report.getExecutionIssues();
report.getBlockingIssues();
report.getReviewItems();
report.getUnverifiedActivationElements();
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

## Runtime activation is separate from state ownership

`DynamicCapability` answers **what kind of state an implementation can own**. `DynamicActivationStatus` answers a
different question: **is that stateful path actually active for this runtime configuration?** Keeping these dimensions
separate prevents a `DYNAMIC_LUMPED` or `DYNAMIC_DISTRIBUTED` label from being mistaken for proof that the current case is
executing the state equations.

| Activation status | Meaning |
|---|---|
| `NOT_APPLICABLE` | The audited element is algebraic and has no independent stateful path to activate. |
| `ACTIVE` | A type-specific audit verifies that the current configuration selects the stateful path. |
| `INACTIVE` | A type-specific audit verifies that the current configuration selects a non-stateful path. |
| `INCOMPLETE_CONFIGURATION` | Dynamic execution is requested or inherent, but a required physical/runtime prerequisite is missing. |
| `UNVERIFIED` | The element owns audited state, but its runtime activation rules have not yet been audited explicitly. |

Activation is intentionally type-specific rather than inferred from `calculateSteadyState` alone. Current audited
examples illustrate why:

- `HeatExchanger` enters its wall-energy transient path only when `dynamicModelEnabled` is true and both wall mass and
  heat-transfer area are positive. Requesting dynamic mode while those prerequisites are missing is reported as
  `INCOMPLETE_CONFIGURATION` and blocks strict preflight.
- `BatteryStorage.runTransient(...)` always evaluates stored-energy and ramped-power state. A positive storage capacity
  is therefore an activation prerequisite, while the inherited `calculateSteadyState` flag does not decide whether the
  battery state is integrated.
- `Expander` uses `calculateSteadyState` directly: true selects algebraic expansion thermodynamics for the timestep;
  false activates nozzle-position, recovered-power and shaft-speed/inertia state integration.

Other state-owning families remain `UNVERIFIED` until their actual runtime branch conditions and physical prerequisites
are audited. `UNVERIFIED` is visible through `getUnverifiedActivationElements()` but is not yet a strict-preflight failure;
this lets the Phase-0 inventory improve incrementally without silently promoting generic flags to engineering evidence.
Activation status is also not quantitative maturity: an `ACTIVE` implementation can still lack conservation,
timestep/mesh, benchmark, safety or OTS qualification.

## Strict transient preflight

The report also provides an explicit, opt-in preflight for workflows that must not continue with known unsupported,
incompletely configured, unaudited, or execution-contract-incomplete transient configurations:

```java
DynamicCapabilityReport report = DynamicCapabilityReport.from(process);

if (!report.isStrictPreflightReady()) {
  for (String issue : report.getStrictPreflightIssues()) {
    logger.warn("Dynamic preflight: {}", issue);
  }
}

report.assertStrictTransientReady();
```

Strict preflight combines known unsupported runtime configurations, type-specific `INCOMPLETE_CONFIGURATION` activation
findings, `UNCLASSIFIED_DYNAMIC` review items, and known process-level execution modes whose numerical/error semantics are
not yet safe for strict professional qualification. `getExecutionIssues()` exposes those process-level findings
separately so they are not confused with equipment state ownership or activation.

Adaptive transient execution remains explicitly blocked by the current Phase-0 contract:
`runTransientAdaptive(...)` mutates one full timestep and derives a following timestep recommendation without a
full-step/two-half-step error estimate, rejected-step restore, retry, and one accepted-step commit boundary.

## Identity-preserving transient step transactions

`ProcessSystem` and `ProcessModel` expose an opt-in transaction boundary for equipment families that implement the
typed `TransientStateParticipant<S extends Serializable>` contract. The participant captures its complete mutable
one-step state and restores that state to the same object instance. A stable, area-local state identity provides
diagnostic and replay provenance while rollback remains keyed by Java object identity.

```java
TransientTransactionCoverage coverage = process.getTransientTransactionCoverage();
coverage.assertComplete(); // fails before any trial mutation

process.runTransientTransactional(dt, physicalStepId);
```

`runTransientTransactional(...)` captures all participants, the process clock, timestep counter, calculation
identifier, measurement history, alarm-history bookkeeping, and the attached event scheduler's pending/fired
bookkeeping. It commits only after the full event/equipment/controller/measurement/alarm step succeeds. Runtime failures
close the transaction and restore captured state in reverse participant order. `ProcessModel` coordinates the same
boundary across areas in insertion order and rolls back areas in reverse order, so a later-area failure does not leave an
earlier area advanced. Its commit uses a non-mutating prepare phase across every child transaction before accepting any
child, preventing a later area's identity/structure failure from leaving an earlier area partially committed.

Coverage is quantitative: `getProcessElementCount()`, `getParticipantCount()`, and `getBlockingIssues()` distinguish
complete participation from API presence. Equipment-attached controllers are included even when they are not registered
as standalone process controllers. Duplicate registration of the same object is counted once; duplicate or changing
state identities are rejected. Systems containing a mutable process element without the participant contract, or a
configured recycle whose equipment or shared `RecycleController` state cannot be restored in place, fail before opening a trial.

Concrete `TransferFunctionBlock` and `LogicBlock` instances are covered participants. Transfer-function snapshots
include both lag states, the complete circular dead-time buffer and index, output, physical-step identifier, tuning,
biases, activity, unit, name, and the original transmitter binding. Logic snapshots include evaluated output,
physical-step identifier, comparison tolerance, activity, unit, name, and an identity-preserving copy of the input-list
membership. Both blocks reject duplicate advancement for the same non-null physical-step identifier and allow exact
continuation after rollback or Java serialization. Subclasses fail closed until they extend the snapshot for any
additional mutable state.

This is an additive compatibility boundary. Legacy `runTransient(...)`, `reset()`, and
`runTransientAdaptive(...)` retain their current behavior. Built-in equipment must adopt the participant contract
family by family with inventory, controller, conservation, timestep, and replay evidence before transactional execution
becomes available for those flowsheets. An open transaction is an in-memory trial and is deliberately not serialized as
a restart checkpoint.

Scheduler rollback restores pending/fired membership, not arbitrary external side effects from event or alarm callbacks.
Actions used inside a rejectable trial must mutate covered participants or defer external effects until commit. Passing
transaction coverage proves rollback mechanics only; it does not establish physical validation, numerical convergence,
safety approval, or OTS qualification.

Parallel transient execution is no longer blocked solely because of worker error handling. Equipment worker exceptions
now propagate to the caller, stop later dependency levels, and prevent the controller, measurement/alarm/history,
timestep-counter, and calculation-identifier commit phases from running. The original runtime exception type and message
are preserved when it crosses the worker future.

This fail-loud boundary is deliberately narrower than transaction or rollback. The process clock and due-event effects
occur before equipment execution, and state already mutated by a same-level sibling or earlier equipment is not restored.
Parallel mode therefore remains unsuitable as evidence of whole-step rollback, transient recycle convergence, or
qualified adaptive/stiff integration. Passing strict preflight means only that the former swallowed-worker-failure defect
is absent.

Execution diagnostics are collected recursively through initialized process modules and preserve `ProcessModel` area
paths. They make remaining limitations explicit instead of presenting an execution mode as quantitatively qualified.

Strict preflight does **not** reject an audited dynamic unit merely because the unit is intentionally inactive, and it
does not yet reject a state-owning family solely because activation remains `UNVERIFIED`. The preflight is not
automatically called by `runTransient(...)`; it is an explicit qualification gate so existing transient APIs and mixed
algebraic/dynamic models remain backwards compatible.

Passing strict preflight only means that the capability audit found no currently known unsupported/incomplete
configuration, unqualified process execution mode, or unaudited custom transient method. It does not establish
conservation accuracy, timestep independence, pressure-flow correctness, control/safety fidelity, OTS real-time
performance, or engineering approval.

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
identity if they are also registered as standalone controllers. Process-level execution issues are area-qualified by the
same report so an unsafe execution mode in one area does not become an untraceable plant-wide diagnostic.

## Nested process modules

`DynamicCapabilityReport` recursively inspects initialized `ModuleInterface` contents instead of stopping at the module
container. Nested entries retain a deterministic container path:

```text
separation train::Inlet separator
topside::separation train::HP gas scrubber
```

The module container itself remains visible in the report but is classified as `ALGEBRAIC`. The established
`ProcessModuleBaseClass.runTransient(...)` implementation delegates transient execution to its internal `ProcessSystem`;
the container does not own an additional independent physical inventory merely because that delegating method is an
override. Its instantiated child operations are audited recursively, and any `UNCLASSIFIED_DYNAMIC` child remains a
strict-preflight review item. Module initialization, restart and error-propagation semantics still require their own
engineering qualification, but the composite container must not create a false capability-review blocker on top of its
children.

The report never initializes a module as a side effect. A module whose internal `ProcessSystem` has not yet been built
can only expose the container at audit time. Initialize/build the module through its normal model lifecycle before using
the report as a complete nested-unit inventory.

Identity-based de-duplication prevents the same process element or nested `ProcessSystem` object from being counted
repeatedly and prevents accidental recursive container cycles from causing unbounded traversal.

## Initial audited mapping

The initial contract intentionally classifies only core implementations whose current source contains clear stored-state
semantics:

- algebraic: standard `Stream` execution, composite module containers, `EnergyNetworkSolver`, ISO-5167 `Orifice`, and
  `WellFlow` IPR pressure-flow relations;
- lumped: separators, tanks, two-stream heat exchangers, compressors/expanders, pumps, throttling/control valves,
  `EnergyConverter`-based motors/generators/gearboxes/inverters/transformers, and `BatteryStorage`;
- distributed: `OnePhasePipeLine`, `TwoFluidPipe`, drift-flux `TransientPipe`, and `WaterHammerPipe`;
- boundary: `SimpleReservoir`;
- control: registered controllers and measurement devices.

The `OnePhasePipeLine` distributed classification is supported by merged ProcessSystem-level quantitative evidence for
the conservative one-phase, positive-flow finite-volume path: the pipeline owns spatial hydraulic/species state,
component inventories and accepted-step diagnostics, and its ProcessSystem snapshot/event path has verified conservation,
boundedness, synchronized thermodynamic composition, clocks and calculation identifiers. The classification describes
**distributed state ownership**, not blanket validity of every pipeline mode. Legacy staged compositional transport,
zero/reversed flow, phase appearance and multiphase operation remain outside that evidence until separately qualified.

`EnergyConverter` owns the previous useful-output state when a finite ramp rate is configured. Its transient ramp is
therefore classified as `DYNAMIC_LUMPED`. Repeated nonlinear/refinement evaluations with the same non-null physical-step
identifier recompute from the output that existed at the start of that step, so refinement cannot consume the ramp a
second time or advance the converter clock again. A new physical-step identifier captures the previously accepted output
as the next step's starting state. Infinite ramp rate remains a memoryless runtime configuration, illustrating why state
ownership and runtime activation/maturity are separate dimensions.

`BatteryStorage` owns stored electrical energy in Wh plus the ramp-limited converter-power state. Same-ID refinement
restores both values to physical-step start before recomputation. Runtime activation is independent of the inherited
generic steady-state flag; a non-positive storage capacity is instead surfaced as incomplete configuration by the
activation audit. This classification is an execution/state-ownership statement, not an electrochemical-fidelity claim.

`Expander` owns nozzle/guide-vane position, ramped recovered shaft power and rotor speed/inertia when its dynamic branch
is selected. Same-ID refinement restores those states to physical-step start before re-solving the same timestep. With
`calculateSteadyState=true`, the expander performs the algebraic thermodynamic calculation without integrating those
states, so the activation report records the stateful path as inactive.

`Stream` and stream subclasses that inherit the standard `Stream.runTransient(...)` boundary are classified as
`ALGEBRAIC`: that method re-evaluates the stream and advances its execution clock, but does not integrate stored physical
state. A stream subclass that provides its own transient override remains `UNCLASSIFIED_DYNAMIC` until its state and
equations are audited.

`WellFlow` is also algebraic: its IPR/Vogel/Fetkovich/backpressure/table calculations map the current reservoir/well
pressure-flow boundary without owning an additional transient inventory, and it inherits the standard physical-step
clock contract. `Orifice` is a custom pressure-driven transient relation, but still owns no accumulation. Its ISO-5167
relation is re-evaluated for every requested refinement while its local execution clock and calculation ID follow the
same A/refine-A/B physical-step contract as other algebraic equipment, including the negligible-flow early return.

Other custom transient implementations remain `UNCLASSIFIED_DYNAMIC` until their state variables, conservation equations,
initialization, timestep constraints, event behaviour, snapshot/restart semantics, and quantitative validation are
reviewed. The mapping is expected to expand as that audit is completed.

## Event-scheduler rollback boundary

Transactional timestep rejection must restore more than process thermodynamic state. The event scheduler itself owns
mutable bookkeeping: a due event moves from the pending queue into the fired-event log. `EventScheduler.snapshot()` and
`restore(snapshot)` provide an explicit checkpoint for that pending/fired membership so a trial step can recover the
scheduler state that existed before the trial.

```java
EventScheduler.Snapshot eventState = scheduler.snapshot();

// trial work that may move events from pending -> fired

scheduler.restore(eventState);
```

This is deliberately **not** presented as complete event rollback. Restoring scheduler membership cannot reverse an
external side effect already performed by an event `Runnable`: a file write, network call, external DCS command, or
mutation of an object that is not part of the separately restored process state remains observable. Therefore a qualified
rejected adaptive trial must either defer externally visible event actions until the physical timestep is accepted, or
execute only actions whose complete mutated state participates in the same transaction. This boundary is especially
important for safety replay, virtual commissioning and OTS integration.

The scheduler attached to `ProcessSystem` is a transient runtime service and is not automatically serialized by
`ProcessSystem.copy()`/Java serialization. Scheduler checkpointing must therefore be coordinated explicitly by the future
whole-step transaction boundary rather than assumed to come from the process graph copy.

## What this does not solve

This contract does not yet provide the plant-wide vector ODE/DAE solver required for strongly coupled professional
dynamics. In particular, it does not add global pressure-flow residual assembly, sparse Jacobians, whole-model timestep
rejection/rollback, event localization, or multi-rate pipeline subcycling. The strict execution diagnostics keep the adaptive blocker explicit, while the parallel failure contract documents its
remaining non-transactional boundary. Neither provides the missing whole-step transaction. Those capabilities build on
this contract so the solver can reason explicitly about which objects own dynamic state, which objects are algebraic
constraints, and which execution modes have qualified transaction/error semantics.

The report also does not certify that a model is suitable for a control, relief, HIPPS/SIS, HAZOP, DEXPI/P&ID, virtual
commissioning, OTS, or other safety-critical study. Those studies require scenario-specific modelling, validation
evidence, engineering limits, deterministic timing/replay evidence where applicable, and appropriate review.


## Base PID and anti-surge controller transaction coverage

The concrete `ControllerDeviceBaseClass` participates in identity-preserving step transactions. Its snapshot covers the
name and calculation identifier, PID error history, integral and filtered-derivative state,
output/manual/bumpless-transfer state, clock and performance metrics, event log, gain schedule, transmitter binding,
setpoint/tuning/limits, activation state, and engineering reference binding. Gain-schedule arrays and event-log
membership are defensively copied. Protected fail-closed extension hooks let a qualified concrete subclass append its
state without weakening the default subclass blocker.

The concrete `AntiSurgeController` is the first qualified extension. Its appended snapshot covers compressor and recycle
valve bindings, all PI/predictive/actuator/emergency configuration, integral and margin-rate histories, predicted margin,
target and actual controller output, and both the actual and target recycle-valve opening. Rejected steps therefore
restore the external valve command as well as controller memory. A repeated non-null physical-step identifier is
idempotent, and exact continuation is covered for one `ProcessSystem`, coordinated two-area `ProcessModel` rollback,
and Java-serialization restart.

Coverage remains deliberately fail-closed for every other controller subclass and for descendants of
`AntiSurgeController`. A subclass inherits the marker interface but reports an incomplete-coverage blocker until its
concrete class explicitly extends the snapshot for every subclass-owned mutation and external command side effect.
Passing this rollback gate establishes transaction mechanics only; it does not qualify loop tuning, scan-time fidelity,
compressor or valve physics, deterministic external I/O, machinery protection, safety action, vendor certification, or
OTS behavior.


## Local signal-modifying transmitter transaction coverage

The concrete `PressureTransmitter`, `TemperatureTransmitter`, and `DifferentialPressureTransmitter` participate when
registered in a `ProcessSystem`. Their snapshots preserve the one- or two-stream binding and the inherited measurement
configuration, instrument tags and field value, condition-analysis state, discrete-delay queue, first-order-filter
memory, injected-fault accumulator, alarm configuration, and mutable alarm state. The Java `Random` generator is captured
independently, including its cached Gaussian value, so a rejected noisy sample replays exactly rather than merely
repeating the same nominal process input. The alarm object is restored in place to preserve references held by alarm and
operator interfaces.

A transmitter consumed by a controller must also be registered as a process measurement device; the controller's
transmitter reference does not transfer ownership of the transmitter's delay, filter, noise, fault, or alarm state.
Coverage fails closed for subclasses of these transmitter types until they extend the snapshot for subclass fields. It
also blocks online-signal mode because database reads and their externally visible timing do not yet have a rejected-step
commit/defer contract. Other measurement-device families are not transaction participants merely because they inherit
signal configuration; each family must first demonstrate that its measured-value path actually advances that mutable
signal state and that all subclass-owned state is covered.

This coverage establishes in-memory rollback, deterministic replay, and Java-serialization mechanics. It does not qualify
sensor accuracy, sample-time or network jitter, alarm or trip integrity, external historian/DCS writes, safety action,
virtual commissioning, or OTS behavior.

## Local process-quality instrument transaction coverage

The concrete local `MolarMassAnalyser`, `WaterContentAnalyser`, `CompositionAnalyzer`, `FlowRatioMeter`, and
`ImpurityMonitor` families participate when registered as measurement devices in a `ProcessSystem`. Each snapshot
includes its original stream binding or bindings plus all inherited signal state described above. The flow-ratio snapshot
also preserves the selected mass, mole, or volume basis. The impurity-monitor snapshot defensively copies the primary
component and ordered tracked-component/threshold map, then restores the original map object in place.

Rejected trials therefore restore Gaussian continuation, drift, first-order-filter memory, sample delay, alarm state and
event-editable configuration together with analyser-specific bindings and configuration. Coverage is quantitative across
one process system and coordinated multi-area process models, and restart evidence serializes both the participant and
its closed snapshot. Concrete descendants and online-signal operation remain fail-closed.

This tranche is limited to local instruments whose production measured-value path actually calls
`applySignalModifiers(...)`. `VolumeFlowTransmitter` and the total/oil/water level-transmitter families still return
raw values without advancing that inherited signal state, so they are intentionally not presented as transaction
participants. Other state-owning analysers remain unaudited until all device-specific iteration, cache,
configuration and binding state is covered.

Passing this rollback gate does not qualify laboratory or online analyser accuracy, phase-sampling fidelity, wet-gas or
metering standards, allocation, emissions or export-spec decisions, alarm/trip integrity, external I/O, safety action,
virtual commissioning or OTS use.

## Differential-pressure primary flow-meter transaction coverage

The concrete local `OrificeFlowMeter`, `NozzleFlowMeter`, `VenturiFlowMeter`, `ConeFlowMeter`, and
`WedgeFlowMeter` families participate when registered as measurement devices in a `ProcessSystem`. The common
snapshot preserves stable transaction identity, the stream and optional differential-pressure-transmitter bindings,
geometry, explicit differential pressure, density/isentropic-exponent/viscosity overrides, the last accepted pipe
Reynolds number, and all inherited signal/alarm state.

Subtype snapshots preserve orifice tapping and wet-gas settings, nozzle type, Venturi discharge and wet-gas settings, and
wedge geometry state. Orifice and Venturi wet-gas result caches are derived from snapshotted inputs, so rollback
invalidates them and deterministically recomputes rather than retaining a result from a rejected trial. Cone meters
explicitly record that they own no additional mutable state beyond the common snapshot.

Coverage is quantitative across one `ProcessSystem` and coordinated multi-area `ProcessModel` rollback, exact replay
of Reynolds iteration plus noise/drift/filter/delay state, a nearby differential-pressure square-root trend, and
Java-serialization restart with wet-gas cache invalidation. Concrete descendants and online-signal operation fail closed.
A linked `DifferentialPressureTransmitter` must be registered separately in the same process transaction because its
own signal state is not owned by the primary flow meter.

This transaction gate does not alter or newly qualify ISO 5167 or ISO/TR 11583 physics, wet-gas correlation validity,
meter uncertainty, installation effects, sampling accuracy, allocation/fiscal metering, external I/O, alarm/trip
integrity, safety action, virtual commissioning or OTS use.


## Local fire-and-gas detector transaction coverage

Concrete local `GasDetector` and `FireDetector` instances participate when registered as
measurement devices in a `ProcessSystem`. Their snapshots include stable transaction identity,
all inherited measurement/alarm state, and every detector field that an event or operator action
can change. Gas snapshots preserve detector type, concentration, species, location, LEL and
configured response time. Fire snapshots preserve the fire latch, signal level, threshold,
configured delay and location.

A `ProcessSystem` transaction already snapshots its `EventScheduler` pending/fired membership.
Combining that bookkeeping snapshot with registered detector participants closes the in-memory
side effect for scheduled fire/gas actions: rejection restores both the event boundary and detector
state, while replay reproduces the same action and accepted commit retains it. Quantitative tests
cover coordinated multi-area rollback, replay, commit, delayed-alarm continuation, object identity,
fail-closed subclass/online modes, foreign-snapshot rejection and Java-serialization restart.

Only registered, in-memory detector state is covered. Arbitrary event callbacks may mutate other
objects or external systems and remain outside the transaction unless those objects participate
independently. The detector response-time and detection-delay values are currently configuration
metadata rather than integrated physical sensor dynamics. This gate does not qualify detector
placement, fire/gas dispersion, voting, reliability, alarm/trip integrity, ESD action, external
I/O, safety integrity, certification, virtual commissioning or OTS use.

## Local manual push-button transaction coverage

A concrete local `PushButton` participates when registered as a measurement device in a
`ProcessSystem`. Its snapshot preserves stable transaction identity, the pushed latch, optional
blowdown-valve binding, automatic-activation configuration, logic-binding list and complete
inherited measurement/alarm state. A lazily recreated empty logic-binding list also keeps older
serialized buttons restartable after the field was introduced as transient state.

Together with the existing `EventScheduler` snapshot, this closes the local side effect of a
scheduled manual push: rejection restores both pending/fired event membership and the button,
replay produces the same latch and alarm continuation, and accepted commit retains the push.
Quantitative evidence covers two coordinated `ProcessModel` areas, binding/configuration rollback,
alarm delay, exact binary replay, stable identity, foreign/null snapshots and Java-serialization
restart.

Automatic activation of a linked `BlowdownValve` and every linked `ProcessLogic` remain
fail-closed because those paths mutate equipment or sequence state that does not yet have complete
transaction coverage. A valve may remain bound with automatic activation disabled; then pushing
the button changes only the snapshotted local latch. Concrete descendants and online-signal mode
also fail closed. This gate proves numerical rollback mechanics only; it does not qualify manual
input reliability, ESD action, safety integrity, external DCS/I/O, virtual commissioning or OTS
use.

## Thermodynamic-limit analyser transaction coverage

Concrete local `CricondenbarAnalyser`, `HydrateEquilibriumTemperatureAnalyser`,
`HydrocarbonDewPointAnalyser`, and `WaterDewPointAnalyser` instances participate when registered
as measurement devices in a `ProcessSystem`. Every snapshot preserves stable transaction identity,
the original stream binding and complete inherited measurement/alarm state. The hydrate analyser
also preserves its reference pressure; both dew-point analysers preserve reference pressure and
method.

Together with the existing `EventScheduler` snapshot, this closes scheduled in-memory changes to
those analyser bindings and settings. Quantitative evidence covers four coordinated
`ProcessModel` areas, exact reference-pressure and method rollback, deterministic Bukacek result
replay, delayed-alarm continuation, commit, foreign/null snapshots and Java-serialization restart
for every family member.

Concrete descendants and online/external-I/O operation fail closed. The thermodynamic calculations
continue to use temporary fluid clones and are unchanged by this transaction contract. This gate
does not newly validate phase-envelope, dew-point, hydrate or empirical-correlation physics, fluid
characterization, sampling, analyser accuracy, alarm/trip integrity, external I/O, virtual
commissioning or OTS use.

## State-mutating derived-instrument transaction coverage

Concrete local `pHProbe`, `SoftSensor`, and `FlowInducedVibrationAnalyser` instances participate
when registered as measurement devices in a `ProcessSystem`. Their snapshots include stable
transaction identity and inherited measurement/alarm state. Device-specific state covers the pH
probe's stream/reactive-system bindings, alkalinity, reaction scratch objects and last cached
input/result; the soft sensor's stream binding, selected property, defensively copied input map,
last estimate and sensitivity vector; and the vibration analyser's pipe binding, support and
method configuration, segment set and implicitly selected segment.

Quantitative tests place one participant in each of three coordinated `ProcessModel` areas.
Rejection restores original bindings, configuration and exact cached or derived readings, replay
is deterministic, and commit retains accepted edits. Restart evidence serializes each participant
together with its closed snapshot. Concrete descendants, online-signal operation, null snapshots
and snapshots belonging to another participant fail closed.

This tranche covers transaction mechanics for state already mutated by production read paths. It
does not newly qualify aqueous reaction chemistry, soft-sensor estimate accuracy, FIV correlations
or pipe physics, sampling, external I/O, alarm/trip integrity, safety action, virtual commissioning
or OTS use. `VirtualFlowMeter` remains outside this gate because its wall-clock result timestamps
need a separate deterministic time/provenance design. `SevereSlugAnalyser` remains owned by the
multiphase physics scope in issue #2907.

## Local model-predictive-controller transaction coverage

A concrete local `ModelPredictiveController` participates when registered as a controller in a
`ProcessSystem`. Its immutable snapshot covers the controller name, stable transaction identity,
transmitter binding, set point, unit, activation and reverse-action configuration, output and move
limits, process model, quadratic weights, prediction horizon, last physical-step identity,
measurement/control history and current response.

Multivariable state is included rather than treated as an independent side channel: control names,
current/previous vectors, limits, weights and preferred values are defensively copied. Each quality
constraint snapshots its measurement binding, unit, limit/margin, control/composition/rate
sensitivities and mutable observed/predicted values. Feedforward composition/rate state, predicted
quality values, moving-horizon sample windows and the last identified model are also restored.
Quality constraints and moving-horizon estimates are serializable so restart preserves the same
configured optimization problem and estimator continuation.

Quantitative evidence covers exact single-input and multivariable rejected-trial replay,
physical-step idempotence, coordinated two-area `ProcessModel` rollback, moving-horizon
continuation, foreign/null snapshot rejection and Java-serialization restart. Concrete descendants
fail closed until they extend the snapshot for their own state. Online/external-I/O transmitter or
quality-measurement bindings also fail closed because their timing and side effects do not yet have
a rejected-step commit/defer contract.

This gate establishes deterministic in-memory rollback and restart mechanics only. It does not
qualify the MPC model structure, optimization quality, constraint tuning, plant identification,
closed-loop stability, scan-time fidelity, external DCS commands, safety action, virtual
commissioning or OTS use.


## Shared recycle-controller transaction coverage

`ProcessSystem` now captures its shared `RecycleController` orchestration state in the same
identity-preserving step transaction as registered process elements. The controller snapshot preserves a stable
provenance identity, recycle registration and priority order, current/minimum/maximum priority, coordinated-acceleration
configuration, accepted-state seed membership, and the complete Broyden continuation state. Rollback restores the same
controller and accelerator instances. Java serialization also retains coordinated Broyden and accepted-seed continuation
instead of silently restarting those solver memories.

The controller snapshot is orchestration state, so it is intentionally outside the quantitative process-element and
participant counts reported by `TransientTransactionCoverage`. Every configured `Recycle` remains a separate
participant: its checkpoint preserves the same inlet, mixed, last-iteration and outlet stream identities while restoring
independent thermodynamic clones, calculation identifiers, clocks, activation flags, convergence residuals and tolerances,
adaptive/Wegstein memory, local Broyden continuation, priority and low-flow tuning. Java serialization retains the stable
recycle identity and Broyden continuation rather than silently cold-starting the solver.

`ProcessEquipmentBaseClass` supplies a protected reusable checkpoint for simulation identity/clock, activation,
low-flow ownership, specification, report/properties, capacity-analysis enablement and IEC 81346 designation. It remains
fail-closed when a concrete equipment snapshot would omit independently mutable attached controllers, active energy ports,
failure state, design conditions or runtime capacity constraints. `Recycle` also fails closed for subclasses and
incompletely connected streams. These diagnostics prevent a nominal participant count from overstating rollback coverage.

Coverage includes exact in-memory rollback of controller priority and coordinated Broyden state plus recycle-owned stream,
base-equipment, convergence and local acceleration state; stable object identity; defensive thermodynamic/matrix/vector
copies; foreign/null snapshot rejection; recycle-registration restoration; and Java-serialization continuation. It does
not qualify thermodynamic model accuracy, convergence of every recycle topology, strongly coupled DAE behaviour, timestep
independence, external side effects, safety action, virtual commissioning or OTS use.

## Local stream-source transaction coverage

Concrete local `Stream` objects participate in the same identity-preserving transaction. A stream checkpoint captures
its thermodynamic system defensively, run/cache inputs, property-initialization mode, gas-quality
configuration and reusable base-equipment state. Rollback retains the registered stream object and restores an independent
thermodynamic clone; property-derived criconden and vapour-pressure caches are invalidated so a rejected trial cannot leak
a stale result. Stream clones receive a distinct transaction identity, while Java serialization retains the original
identity and restart state.

A fully connected registered `Stream` plus classic `Recycle` reports quantitative 2/2 participant coverage and rolls both
objects back together.

Wrapper `Stream` objects remain fail-closed because their run path delegates fluid mutation to a separately owned stream.
`VirtualStream` also remains fail-closed: every run constructs a new output `Stream` and increments legacy process-global
stream-numbering state, which cannot be safely rewound across concurrent models. Stream subclasses, attached controllers,
energy ports, failure state, design conditions and runtime capacity constraints remain blocked until their additional
ownership is composed explicitly. This coverage establishes transaction and restart mechanics only; it does not qualify
source-boundary physics, stream flash accuracy, property-package accuracy, adaptive full-step/two-half-step rejection,
external I/O, safety action, virtual commissioning or OTS use.

## Transaction-scoped scheduled events

An `EventScheduler` action can affect more than scheduler bookkeeping, so ordinary `scheduleEvent(...)` callbacks now
fail the `ProcessSystem` transaction preflight while they remain pending. Transactional execution requires
`scheduleTransactionalEvent(time, label, action, stateIdentities...)`, with the stable identity of every registered
`TransientStateParticipant` the callback may mutate. Coverage fails before trial execution when the declaration is
missing, empty, duplicated or names an absent/incomplete participant. Scheduler replacement and trial-added unscoped
events are also rejected during commit preparation; rollback restores the original scheduler identity plus pending/fired
membership.

For a shared multi-area scheduler, `ProcessModel` validates declarations against the union of completely covered
participant identities in every child area and passes that same frozen identity scope into each area transaction. This
allows one scheduled action to target a registered participant across an area boundary without weakening standalone
`ProcessSystem` preflight checks. Area clocks and object identities remain coordinated by the existing two-phase model
transaction.

The declaration is a contract, not a sandbox. A scoped callback must not mutate undeclared objects, perform external I/O,
publish externally visible events or schedule an unscoped callback. Such effects cannot be inferred from a Java
`Runnable` or undone by participant snapshots. Java serialization of scheduler snapshots retains the declared identity
list, while the callback itself must still implement `Serializable` for checkpoint/restart. This gate establishes
fail-closed in-memory orchestration and deterministic replay only; it does not qualify event localization, external
side-effect compensation, safety action, virtual commissioning, OTS behavior or adaptive timestep rejection.
