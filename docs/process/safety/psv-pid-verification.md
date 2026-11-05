---
title: "Passive PSV Simulation and P&ID Feedback"
description: "Configure, simulate and verify a native passive PSV branch through canonical P&ID and DEXPI 2.0.1 evidence."
---

# Passive PSV simulation and P&ID feedback

This workflow adds an actual `SafetyReliefValve` branch to the bounded synthetic V100/V101 example. It keeps the existing BPCS, ESD, HIPPS, manual input and instrument identities. The pressure-relief valve is a self-acting mechanical device: it receives no invented PLC signal, pressure transmitter or safety-function wrapper.

`PidPressureReliefImporter.fromNativeValve` accepts the current `PidDesignModel`, current `PidSimulationBindings`, the actual configured process copy, the relief/protected-equipment/tee tags and a registered capacity-basis evidence identifier. It returns a copied canonical graph. Existing proposals survive; an existing PSV is reused only through its exact actual binding and compatible governed settings. Import conflicts leave the supplied graph unchanged.

`PidPressureReliefValidator.applyCanonicalConfiguration` reads the canonical mechanical settings into a pristine isolated native copy. `validateConfigured` then checks actual valve class, exact stream-object route, pressure/Cv units, the complete native response/hydraulic readback, the declared capacity basis, source/graph fingerprints and execution order. A changed proposal invalidates old bindings. Rebinding alone cannot legitimize an ignored change: native settings must be applied, read back and rerun. A receipt from the old configuration cannot be reused for the new one. The receipt separately fingerprints the actual steady versus finite transient dispatch mode. Only `FINITE_NATIVE_TRANSIENT` can receive dynamic response evidence; a steady proposal can still be imported without implying finite response qualification.

The initial supported backend is an exact native `SafetyReliefValve` with `ValveType.CONVENTIONAL`, with linear native trim, native gas choking enabled, no external controller or second parent actuator, imposed positive backpressure and UUID physical-step idempotency enabled. The physical path is an actual separator gas outlet, an actual one-inlet/two-outlet `Splitter`, the normal outlet valve and the PSV discharge to an explicit stream battery limit. Pilot/balanced valve types, unknown trim curves, ambiguous consumers, disconnected ports and an undeclared discharge network are rejected. This is a bounded contract, not a generic flare-network solver.

## Governed values and observed state

The canonical `simulationPressureReliefConfiguration` attribute retains explicit bara and US Cv units, set/reseat pressures and the modulating ramp reference, overpressure and blowdown fractions, rated Cv, Kd/backpressure multipliers, valve type and opening law, opening/closing time constants, minimum dwell periods, lift-rate and seating tolerances. It also retains the supported native trim/hydraulic policy readback. The `fullLiftPressureBar` field is the MODULATING law's ramp reference; it is unused by SNAP, which commands full opening at set pressure subject to actual lag/rate/dwell. Derived ramp-reference and reseat values must agree with the configured native law. The pressure fractions use the declared absolute set-pressure basis; this synthetic convention is not a claim of equivalence to a certified gauge-pressure vendor setting.

`simulationPressureReliefRoute` retains the actual protected unit, tee, source stream, branch inlet, PSV outlet/disposal identity and positive backpressure. A case-specific boundary disturbance or mechanical fault must be declared as a scenario override; it cannot silently replace the healthy canonical design.

Static mechanical configuration is separate from observed pressure, lift, latch/dwell state, flow and timers. The native result records sampled upstream pressure, downstream pressure, lift and actual relieving mass flow. Full-lift Cv and physical lift are applied once in the native hydraulic calculation. A synthetic rated Cv assumption is not a certified mass-flow rating, API 520/521 sizing, certified orifice area or proof of relief-system adequacy. Missing vendor data, fluid-case coverage, disposal/flare analysis and accountable engineering review remain HOLD.

## Execute, retain failures and regenerate

Build the final proposal, rebuild bindings, freeze a configured-copy receipt and register its evidence reference on the project. Repeat validation inside the actual strict scenario factory and require the same receipt before stepping the native model. Execute through `EngineeringSimulationRunner.runStrict` so the project owns the typed scenario result and its source/revision evidence.

`PidPressureReliefValidator.attachResult` checks the scenario ID, receipt reference, exact criterion definitions, actual native configuration and required full post-event sampled envelope. `MAINTAIN_BOUNDS` criteria assess every observed sample in their declared window; a first reached pressure value does not prove a subsequent safety envelope. Reach-by-deadline opening/flow/reseat criteria remain separate. The relief trace must use scenario-elapsed seconds, include the trigger/end samples and match every native maintained-criterion timestamp and monitored sample count. One deduplicated native sample is retained per observed clock. A partial trace or endpoints without the intervening samples remain INCOMPLETE even when the native scenario completed. Evidence distinguishes unexecuted, incomplete, no-demand, demand without lift, lift without positive flow, observed relief flow, and opening followed by observed reseating. A required native failure remains `OBSERVED_FAIL`, including a negative control whose expected-fault assertion succeeds.

Pressure/response criteria, component/mass conservation and numerical energy criteria have separate verdicts. Passing a trip or relief-pressure test cannot clear a failed numerical energy criterion or qualify a different hazard. Sampled results do not establish continuous-time safety, SIL, installed independence or engineering approval.

Export the physical project source with the canonical graph and fresh bindings through `ProfessionalPidPackageExporter`. The resulting native DEXPI 2.0.1 contains the actual tee and passive PSV once, with actual material ports and discharge route. The canonical PID is the instrumentation authority; generic requirement proposals remain in engineering records rather than becoming extra unbound native instruments. The authored relief symbol and real tee are also rendered on the P&ID/PFD. Numerical coupling/scan helpers stay in the isolated case configuration and are not drawn as hardware.

Run the native P&ID quality review, strict external PFD/layout checks, full native ID/port/line/signal inventory and an independent visual review of each resulting drawing. These software checks are distinct from controlled ISO 15519/10628/14617, IEC 62424, ISA 5.1 and relief-design requirements review. No PCA symbol payload is imported or claimed by this example.

## Native absolute-to-gauge pressure mapping

The DEXPI 2.0.1 `SafetyValveOrFitting.SetPressureHigh` property is typed with
`PressureGaugeUnit`. Canonical/native simulation pressures use bara. The optional canonical
`simulationAtmosphericReferenceBara` must therefore be explicitly positive and finite before
this property can be assigned. The example declares the synthetic reference 1.01325 bara:
42.2 bara becomes 41.18675 bar gauge, serialized with the official
`Core/PhysicalQuantities.PressureGaugeUnit.Bar` unit. This reference is included in the
configuration fingerprint and native-ID projection crosswalk. Real imports have no default.

If the reference is absent, the native gauge field remains unassigned with a recorded HOLD;
the absolute canonical setting survives in the sidecar. The modulating-ramp reference, reseat pressure and lift
timing/Cv retain their canonical basis. The official `SetPressureLow` property denotes
low-pressure activation and is never used for reseat pressure. A schema-valid quantity is
not a certified vendor set-pressure or code accumulation assessment.

## Recompute the public example

After compiling repository test classes and obtaining the test dependency classpath, run
`neqsim.process.engineering.pid.AdvancedPressureReliefPidExample` with a new output directory
and optional timestep. The default example runs four strict profiles: nominal, relief demand,
failed closed, and recovery/repeated demand. It preserves the failed closed result.

On JDK 11 or newer, use the XStream module opens already declared in the repository Maven
profiles when invoking the example directly: `java.base/java.util`, `java.base/java.lang`,
`java.base/java.io`, and `java.base/java.lang.reflect`, each opened to `ALL-UNNAMED`. These
options are needed for complete native model persistence; omitting the new PSV or its
mechanical-design data is not an acceptable serialization workaround. Java 8 does not use
module-open options.

`PidNativePressureReliefTest` covers import/readback/routes, unsupported hardware/trim, native
quantity conversion and passive drawing/data identity. `PidPressureReliefScenarioTest` runs
actual strict cases, checks no-demand/failed outcomes and rejects partial relief traces. Its
canonical response edit changes opening lag/rate from 4 s/0.2 per second to 0.15 s/3 per second,
rejects stale receipts, and reruns the same pressure/deadline policy. The retained slow FAIL
and fast PASS do not grant energy, certified capacity or standards credit.

## Gas choking and capacity iterations

This bounded gas-relief contract requires native gas choking enabled. High pressure-ratio
subcritical Cv extrapolation is rejected for response credit. Keep any earlier uncapped
pressure/flow runs as superseded diagnostics. Capacity selection must retain the declared
Cv candidates and fixed pressure/response criteria; enabling choking can change the verdict
and is not a drawing-only or annotation-only change. A revised selected Cv is an explicit
capacity assumption, not a certified orifice or vendor rating.
