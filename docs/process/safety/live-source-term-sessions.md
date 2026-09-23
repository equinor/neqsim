---
title: Live safety source terms from process models
description: Connect steady-state and transient ProcessSystem and ProcessModel calculations to versioned safety-source frames with explicit ownership, clocks, failure handling and synchronous delivery.
---

# Live safety source terms from process models

`SourceTermSession` connects a caller-owned NeqSim process to the
[release-flow models](release-flow-models) and [neutral exchange contract](source-term-contract).
It supports a single `ProcessSystem` and an area-based `ProcessModel` without an external safety
software runtime. New APIs are additive. The independent legacy gas blowdown integration was
corrected for inventory/energy conservation in #3905; see the
[compatibility note](release-dispersion-scenarios#conservative-gas-blowdown-and-compatibility).

## Single process and live capture

These calls are executed in `SourceTermSessionTest.singleProcessDocumentationAndExternalLiveCapture`.
The example assumes a configured process containing a stream named `feed`.

```java
SourceTermSession session = new SourceTermSession("study-1", process);
session.addSource("feed-opening", "feed", 0.01, 0.62, 101325.0,
    new HomogeneousEquilibriumReleaseModel());
List<SourceTermFrame> delivered = new ArrayList<SourceTermFrame>();
Consumer<SourceTermFrame> listener = delivered::add;
session.subscribe(listener);
List<SourceTermFrame> initial = session.runSteadyState();

// An external owner can update inputs and solve before capture.
UUID externalId = UUID.randomUUID();
feed.setPressure(4.5, "bara");
process.run(externalId);
List<SourceTermFrame> current = session.capture(
    Collections.singletonMap(SourceTermSession.SINGLE_AREA, externalId));
session.unsubscribe(listener);
```

The short registration overload samples the named unit's `getFluid()`: a stream's flowing fluid,
or a separator's vessel fluid. Check the particular equipment API before interpreting its fluid
as inventory. Registration verifies area/unit ownership and opening geometry. Replacing an area or
unit object after registration requires a new session; the previous binding becomes stale.

Use `toNdjson()` on each delivered frame for a self-contained live record. A receiver can write to
a file, application queue or authenticated transport. Delivery is synchronous: slow receivers
slow the caller and no unbounded background queue is created. A receiver exception is logged and
counted by `getDeliveryFailureCount()`; other receivers and the returned batch remain available.
Delivery is attempted once, with no hidden retry. Use sequence numbers to detect gaps and define
retry/deduplication in the application. For long-running use, replace the example's growing list
with a bounded queue or streaming sink and choose an explicit overflow policy.

Callbacks must not mutate the process. Session operations cannot be reentered from callbacks.
The caller must serialize **all** access to the process, including field-input updates and other
references to the same model. Synchronizing the session cannot make external model mutation safe.

## ProcessModel and dynamic operation

The area-qualified overload takes `outletIndex = -1` for equipment fluid, or a zero-based index
from `getOutletStreams()` for an explicitly selected outlet. The following APIs are exercised in
`processModelDocumentationPreservesAreaIdentityAndStepsComposition`.

```java
SourceTermSession session = new SourceTermSession("study-1", model);
session.addSource("a-opening", "train-a", "feed", -1, 0.01, 0.62, 101325.0,
    new HomogeneousEquilibriumReleaseModel());
session.addSource("b-opening", "train-b", "feed", -1, 0.01, 0.62, 101325.0,
    new HomogeneousEquilibriumReleaseModel());
List<SourceTermFrame> initial = session.runSteadyState();
List<SourceTermFrame> next = session.step(0.25);
```

Steady operation calls `ProcessModel.run()`, preserving its cross-area convergence algorithm.
Each area may have a different actual calculation UUID. The frame's calculation UUID correlates
the sampling batch; `areaCalculationId` and `equipmentCalculationId` preserve underlying execution
identity. Transient operation calls the native `runTransient(dt, id)` and requires aligned area
clocks and completed area IDs. All sources are cloned before any release model is evaluated.
Frames are returned and delivered in registration order, with a monotonically increasing sequence
across the session.

Configure each equipment's actual transient behavior before stepping. The session does not
turn an algebraic equipment model into a dynamic one. The real-vessel regression
`realSeparatorInventoryDynamicsReachSourceFramesForBothContainers` uses a separator with
`setCalculateSteadyState(false)`, applies heat, advances its volume/internal-energy dynamics and
checks that vessel and gas-outlet source frames reproduce the changed thermodynamic state.
It runs for both containers. Another regression changes feed composition between steps.

Simulation time is read from the process, never inferred from wall-clock elapsed time.
Positive finite timesteps are required. Wall-clock pacing, deadlines and transport timeouts
belong to the application; this API makes no hard real-time guarantee.

## Failure and freshness semantics

| Condition | Result |
|---|---|
| Successful process and release calculation | Current source payload; inspect model diagnostics and evidence level. |
| Release model fails for one source | That source is invalid; other registered sources can succeed. |
| Process run or transient step fails | Diagnostic frames without source payload; session becomes faulted. |
| External UUID, area set, ownership, activity or clocks mismatch | Stale frames without source payload. |
| Direct stream inputs changed since its calculation | That source is stale until solved again. |
| Hypothetical opening disabled | Disabled frame; no numeric source payload. |
| Receiver fails | Delivery-failure count increases; other receivers continue. |

After an owned process failure, `step` and `capture` refuse further work.
Native transient execution can mutate some equipment and clocks before failing; this adapter
does **not** claim transactional rollback. Failure-frame simulation time is the last coherent
session time, not the partially advanced process time. Restore/reinitialize the process state,
then call `runSteadyState()` to recover, or create a new session after validated restoration.
A steady rerun alone is not a rollback of dynamic inventory.

External capture requires the UUID of **each successfully completed** area calculation.
For a model, supply a map keyed by its exact area names. The external owner remains responsible
for successful execution, aggregate convergence and synchronization. Never pass a retained old
UUID after an external failure: arbitrary in-place equipment mutation cannot always be detected.
Equipment-owned outlet freshness follows its owner's calculation contract; an outlet's own UUID
is recorded where available but is not assumed to equal the area UUID. Some native units use
separate outlet flash identifiers. When an already captured area UUID is reused, current stream
inputs are compared against the captured inputs. Native transient pressure feedback is retained
in that captured state; it is not mistaken for a later external input edit. Direct source-stream
input-cache changes are additionally checked.
Zero-flow direct streams without a current calculable state produce stale frames.

Consumers must also enforce their own maximum wall-clock age and delivery timeout. A schema
status of valid does not guarantee a recently received frame is still current on a remote receiver.

## Physical meaning and isolation

Each source registered with `addSource` is an **instantaneous hypothetical opening** evaluated from the sampled fluid.
Use `addLongPipeSource` for a hypothetical one-sided pipe segment; it retains explicit length
and Darcy friction in every immutable request and exported frame. Select a finite-pipe model
explicitly—short-opening models reject this geometry.
The source does not remove mass or energy from the connected process. Its provenance explicitly
records `HYPOTHETICAL_OPENING_NO_INVENTORY_FEEDBACK`. To calculate a depleting release, model the
physical discharge and inventory balance in the process; sample the resulting trajectory and
check component/energy conservation and timestep sensitivity. Do not apply this source rate as
an additional loss when the process already includes that discharge.

For opt-in two-way depletion, use [ReleaseInventory and `addInventorySource`](coupled-release-inventory).
The inventory removes component mass and stagnation enthalpy during native process stepping;
the session exports the updated instantaneous source and cumulative balance provenance.
The compatibility path reports `COUPLED_RIGID_ADIABATIC_GAS_INVENTORY`. The explicit
phase-selected overload reports `COUPLED_RIGID_ADIABATIC_PHASE_SELECTED_INVENTORY` plus
`inventoryWithdrawalPhase`. Both are bounded to a rigid adiabatic equilibrium inventory and
have no automatic entrainment, phase or release-model fallback.

A sampled mass rate is not a timestep average or an integrated release mass. Retain the initial
frame, integrate only over valid intervals using a documented quadrature, and refine timesteps
around openings, closures and phase transitions. Do not interpolate across failed, stale or
disabled intervals as if their missing values were zero.

`setEnabled(sourceId, false)` disables source export only, including for a coupled inventory.
Use `ReleaseInventory.setReleaseEnabled(false)` to stop its physical withdrawal. An upstream shutdown or
isolation valve closing does not empty trapped inventory or necessarily stop an existing leak.
Represent those actions in the process model and use its event scheduling/control facilities.
Specify source position/orientation through `SourceTermFrame.withLocation` when spatial boundary
conditions are needed. No coordinates, isolation assumptions, weather or consequence models
are inferred.

## Validation and limits

Run `SourceTermSessionTest`, `SourceTermFrameTest`, `ReleaseFlowModelTest` and `LeakModelTest`,
then `devtools/validate_source_term_contract.py`. The tests cover both containers, real vessel
transients, evolving composition, per-area provenance, external capture, fail-closed lifecycle,
invalid steps, disabled openings and callback isolation. These are synthetic software regressions,
not independent engineering qualification.

The selected homogeneous-equilibrium model retains its documented applicability and limitations,
including unsupported solid-risk cases and invalid unresolved mixture flashes. No automatic
fallback to a different model is performed. This implementation supplies an auditable foundation;
relaxation/non-equilibrium discharge models, multiphase inventory coupling and experimental
qualification require separately reviewed extensions. [Uncertainty ensembles](source-term-uncertainty)
evaluate complete joint inputs after the corresponding process states have been solved.
The architecture and foundational APIs have merged; that does not establish qualification.
The [completion status](source-term-platform-status) tracks remaining work, including the Colab demonstration.
