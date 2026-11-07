# Agentic compressor process, controls and P&ID workflow

Use this workflow when the requested deliverable is a simulated gas-compression
package with a scrubber, cooling, anti-surge recycle, instrumentation, safeguards
and professional P&IDs. It coordinates existing NeqSim agents and native APIs;
it is not a separate controller framework or a PLC code generator.

## Freeze the engineering contract

Start with `@solve-task` and the task workflow. Retain the request, exact NeqSim
revision, input provenance, assumptions, gaps and output location. Obtain or
explicitly assume composition and water basis, EOS/mixing rule, inlet P/T/flow,
discharge boundary, cooler service and recycle take-off/return locations. Record
gas-flow reference conditions and distinguish bara, bar gauge, actual m3/h,
standard volume and mass flow.

Freeze the vendor-map source or label the generated map synthetic. Define speed,
power and map limits, protected volumes, valve capacity/travel/failure modes,
pressure/temperature/level envelopes, event duration, sampling and acceptance
criteria before the first fault run. A generated map, default anti-surge margin
or plausible trip threshold does not establish an OEM or project requirement.

## Delegate by engineering responsibility

| Owner | Work | Handoff and required check |
| --- | --- | --- |
| `@process-model` | Build the actual scrubber/compressor/cooler and recycle material graph; initialize and converge steady state | Named source objects, exact ports/streams, thermodynamic basis, inventories, component/mass/energy closure and steady convergence |
| `@rotating-equipment` | Establish map domain, head/efficiency, surge and stonewall boundaries, driver/speed limits and recycle duty | Operating-point/range evidence, map provenance, actual versus standard flow basis, supported dynamic-machine scope |
| `@control-system` | Add actual measurements, anti-surge and process loops; configure actuators, mode/priority and timing | Measurement-to-controller-to-final-element identities, controller input definition, readback, loop/control narrative and executable cases |
| `@safety-depressuring` | Review initiating causes, protection, permissives, reset/bypass, isolation and relief/disposal requirements | Cause/effect matrix with distinct proposed versus executed functions, native response criteria, remaining SRS/LOPA/vendor/relief gaps |
| `@pid-quality-review` | Review the regenerated native exchange, every drawing sheet and source bindings independently | Content-bound topology/semantic/layout/visual and standards evidence; concrete defects returned to the generating owner |

The task owner integrates these handoffs. Do not launch duplicate implementations
of the same loop or let the generating owner issue an independent approval of
their own P&ID. Use the existing P&ID process-operations workflow for synthesis,
bindings, native DEXPI 2.0.1 export and professional drawings.

## Native entry points and discovery

Discover `instrumented-compressor-automation-study` with the repository's
`devtools/capability_search.py` using the selected Python interpreter. Inspect the
current public signatures before constructing Java/JPype objects. The route is
`inspectApi`: it does not add a curated `runDynamic` JSON operation.

| Entry point | Responsibility |
| --- | --- |
| `neqsim.process.engineering.CompressorAutomationWorkflow` | Public packaged runner for strict design input, configured native cases, automation registers and coordinated DEXPI/P&ID export |
| `neqsim.process.util.scenario.CompressorAutomationStudy` | Native train construction, exposed process/units/measurements/controllers/protection and repeatable steady/dynamic cases |
| `neqsim.process.engineering.pid.PidNativeControlSystem` | Actual control-instrument import/binding and canonical configuration apply/readback on an isolated native copy |
| `PidSafetyFunctionImporter` / `PidSafetyFunctionValidator` | Supported actual native safety-channel/action import and strict configured-response evidence |
| `ProfessionalPidPackageExporter` | Existing coordinated professional native exchange, drawings, registers and review inputs |

The packaged runner's exact positional contract is
`<design.json|-> <new-output-dir> [canonical-pid.json]`. Invoke
`neqsim.process.engineering.CompressorAutomationWorkflow` with the build/runtime
classpath and Java module options in the
[public runner guide](../../../../docs/process/safety/compressor-automation-workflow.md).
A dash chooses the public design defaults; it does not read JSON from stdin.
The output directory must be new. Retain previous evidence and use a different
directory for each revision or retry.

Supply the strict design object described by the guide. Unknown/duplicate keys,
nulls, numeric strings and nonfinite scalar/composition values are rejected.
Accepted physical input, actual steady readback and configured native readback
are distinct artifacts. The default CLI executes every public profile; an
explicit programmatic subset is disclosed by `allPublicProfilesSelected` and
must not be described as the full acceptance matrix.

Passing an optional canonical P&ID applies supported control settings to the
native copies and reruns current evidence. Unsupported safety-policy changes
fail readback: change explicit native `Design` trip inputs and regenerate the
source/proposal for supported trip-design revisions rather than silently
ignoring an edited SIF. Exact raw native results and strict JSON artifacts have
separate fingerprints; nonfinite native values become declared null/path/kind
records, never invented zeros or protection passes.

Use `CompressorAutomationStudy.create(Design)` to obtain the supported `Train`,
and `run(Train, CaseDefinition)` for declared cases. Retain the exposed native
objects for composition with a larger process. Consume the current canonical
settings in the configured-copy factory; never rerun a hard-coded original
configuration after changing the P&ID.

After the normal repository Maven bootstrap, the focused software gate is
`./mvnw -Dtest=CompressorAutomationStudyTest,PidNativeControlSystemTest,CompressorAutomationWorkflowTest test`.
Those tests are executable source/API examples and guard regressions; their
passing status does not replace the task-specific design, fault, balance,
refinement, native-exchange or independent drawing evidence below.

## Build and simulate actual equipment

1. Build an explicit suction scrubber, compressor, aftercooler and physically
   connected anti-surge valve/return. Identify every pressure boundary and any
   discharge separator, check valve, isolation, vent or relief path. A numerical
   `Recycle` helper is not an additional physical item on the P&ID.
2. Converge the native steady model and retain inlet/outlet streams, cooler duty,
   compressor power, speed/head, phase splits, actual recycle flow and balance
   residuals. Reject nonfinite states, invalid map points and unconverged recycles.
3. Audit dynamic capability and execution order for the actual units. Separate
   finite inventories, dynamic driver/actuator states and algebraic equipment.
   Give each controller/application one execution owner per physical step; do
   not run the same PI or actuator state twice through overlapping registrations.
   A steady pressure target or a prescribed recycle split is not evidence of
   finite-volume pressure response or anti-surge valve authority.
4. Run the anti-surge loop against the actual hydraulic return. Observe the
   manipulated valve and actual compressor suction/recycle flow together; a
   100% command without positive effective recycle is a failed protection path.
5. Preserve supported calculation identity, initialization, clone/snapshot and
   restart semantics. Report uncovered helpers before claiming whole-train
   rollback, rejected-step safety or checkpoint/restart equivalence.

Use `CompressorAntiSurgeApplication`, `AntiSurgeController`,
`AntiSurgeRecycleCalculator` and the compressor chart/driver APIs within their
documented roles. Steady recycle sizing is not a replacement for the transient
application or the explicit valve network.

## Create and reconcile instrumentation

Produce a requirement-to-device-to-test crosswalk, not only a list of tags.
Review at least the following functions for applicability; each requires a
governed setting and an explicit supported/unresolved disposition:

| Function | Source and action to establish |
| --- | --- |
| Anti-surge control | Suction-flow and pressure/temperature or equivalent mapped input; defined surge coordinate and correction; anti-surge controller and fast recycle-valve action |
| Compressor capacity/pressure control | Suction/discharge pressure as selected; speed, guide-vane or other actually supported manipulated variable, with anti-surge priority |
| Suction scrubber level control | Actual vessel-level measurement, controller and liquid drain route; level units/geometry and valve authority |
| Cooler temperature control | Actual outlet temperature and executed cooling manipulated variable; distinguish duty/setpoint control from a simulated utility-valve loop |
| Protective functions | Applicable high pressure/temperature, scrubber high-high level, manual trip, driver trip and isolation; required sequence, latching, confirmation and reset |
| Mechanical and auxiliary protection | Check-valve/reverse-flow, relief, vibration, speed, bearing/seal/lube/cooling signals as supported by the actual model and equipment data |

Never create a simulated transmitter or signal solely because a drawing rule
proposes one. If an anti-surge application reads a compressor-derived metric
directly, declare that computational source. FT/PT/TT symbols receive executed
signal-path credit only when actual measurement objects feed the tested path.
Likewise, a fixed cooler outlet temperature does not prove utility-valve control,
and an algebraic stopped-machine approximation does not prove rotor coastdown,
reverse flow or mechanical-surge dynamics.

Keep independent BPCS/protection requirements separate from object uniqueness:
distinct Java objects do not establish installed sensor independence, common-cause
resistance or a SIL. Proposed vibration/seal/lube signals without a runtime
measurement remain proposed requirements; do not make their absence disappear.

## Govern settings through the canonical model

Follow the canonical `PidDesignModel`/`PidSimulationBindings` contract and the
applicable native importers/validators. Import from the actual held measurements,
controllers, actions and physical source graph; retain exact object/stream
identities and signal/material domains.

For each proposed revision:

1. Copy the source process and keep the original unchanged.
2. Apply every governed P&ID setting to the supported native objects. Retain
   explicit units, control direction, setpoints, tuning/margins, valve travel,
   trip/voting/qualification, delays, latching and failure settings as applicable.
3. Read back and validate the actual native configuration, execution mode and
   topology. Reject unsupported settings, wrong endpoints and name-only matches.
4. Fingerprint the canonical/source/configured/scenario revisions and bind the
   full newly executed result window. Rebinding without applying or rerunning is
   insufficient; earlier passing receipts become stale after relevant changes.
5. Regenerate the package from the current canonical model. Do not patch SVG,
   native XML, registers or result verdicts to match an intended proposal.

The existing `PidSafetyFunctionImporter`/`PidSafetyFunctionValidator` and passive
PSV counterparts have bounded contracts. Do not force an unsupported compressor
driver action, controller family or relief topology through them by renaming it.
Use a supported compressor workflow API when available, or return the precise
missing implementation to the source owner.

## Execute an acceptance matrix

Specify the initiating cause, native action, observation point, allowed delay,
window, sampling/refinement and expected outcome for every case. Include the
following where supported by the frozen model:

- Normal design, nearby duty and turndown steady/dynamic cases.
- Throughput reduction or load rejection that actually approaches the surge
  control boundary, including a no-protection comparator and slow/stuck recycle
  actuator cases. Compare actuator travel, recycle flow and operating margin.
- High discharge pressure/temperature, scrubber high-high level and manual trip.
- Driver/protection failure or inhibit/bypass, and a rejected reset/restart when a
  permissive is unsatisfied; explicit rearm/retrip after a valid reset.
- Loss of cooling, sensor fault and check-valve/reverse-flow cases only where
  those mechanisms are implemented. Otherwise retain them as missing coverage.
- Representative half-timestep repeats and clone/replay/restart checks within
  the actual supported transient-state coverage.

Record source values and held/qualified inputs, controller demand, actual valve
travel, driver state/speed, suction/discharge P/T, scrubber level, recycle flow,
surge/map coordinates and triggered actions at accepted physical steps. Evaluate
command issuance, final-element response and protected-process response as
separate criteria. No-demand monitoring is not a successful response on demand.
An expected faulted FAIL is a passing regression assertion, not a protection PASS.

Keep mass/component/energy/inventory closure, solver convergence, timestep
sensitivity and performance measurements separate from control or shutdown
success. Retain failed criteria and incomplete windows; do not relax a declared
limit or substitute endpoint-only sampling to obtain approval.

## Deliver automation-engineering outputs

Retain a coordinated package containing:

- Runnable native model and configuration, convergence/balance records and raw
  steady/dynamic results, with the exact source revision and reproduction steps.
- Current DEXPI 2.0.1 native exchange, canonical model/bindings, source-ID and
  material/measurement/signal crosswalks, actual vector PFD/P&ID and sheet index.
- Equipment/line/instrument/valve/loop registers and an I/O schedule that records
  source attachment, variable/unit, signal direction/type, engineering range and
  proposed/executed status; absent hardware properties remain unresolved.
- Control narratives with mode/priority, failure behavior and anti-surge/capacity
  interaction; alarm and cause/effect matrices; permissive, bypass, latching and
  reset/restart tables; acceptance-case-to-function mapping and failure findings.
- Independent current-hash topology/layout/visual reviews for all final sheets
  and a controlled standards/applicability register with unresolved actions.

Check native full inventories and every branch/loop, not merely tag counts.
Verify the physical anti-surge take-off and return, scrubber gas/liquid outlets,
cooler process/utility separation, measurement tapping, valve flow direction and
reciprocal continuations. Run the existing strict PFD/layout and P&ID QC tools,
then inspect the exact final files. Regeneration invalidates affected evidence.

## Standards and release boundaries

Review current applicable editions of ISO 15519/10628/14617, IEC 62424 and
ANSI/ISA-5.1 for information, representation and instrumentation; API 617/670,
IEC 61511/61508 and relevant relief/installation requirements for their distinct
equipment/protection scopes. Record controlled sources and applicability rather
than inferring compliance from standard names, a schema-valid DEXPI file or an
attractive drawing. ISO/API/IEC references do not supply missing vendor maps,
trip settings, allowable pressures, valve response or SIL allocation.

The agent may prepare design proposals, native simulations and test evidence.
PLC/DCS application deployment, vendor verification, SRS/SIL/LOPA acceptance,
installed independence, relief adequacy and construction/operating approval need
their own evidence and accountable review. Unsupported or unreviewed items stay
HOLD. Do not label a package construction-ready or standards-conformant from
automatic tests alone.
