---
title: Heat exchanger automation with native P&ID and DEXPI
description: Automatic actual instrumentation, conservative fluid and wall dynamics, and source-bound PID tuning for a two-circuit exchanger.
---

# Heat exchanger automation with native P&ID and DEXPI

`HeatExchangerAutomationWorkflow` connects the existing native instrumentation,
controller, canonical P&ID, DEXPI 2.0.1 and drawing exporters. The same live TT,
PID and utility valve objects drive the simulation and the engineering package.
The native four-port exchanger drawing keeps the process and utility circuits
separate, including actual upstream valves and stream-mounted measurements.

The complete executable natural-gas/reference-water example is
[`HeatExchangerAutomationWorkflowTest`](../../../src/test/java/neqsim/process/engineering/HeatExchangerAutomationWorkflowTest.java).
Its tests build, instrument, export, reload, tune and revise the public example;
the test data are illustrative study inputs.

## Automatic workflow

1. Build the two feeds and an actual `ThrottlingValve` on the utility inlet.
   Give every material stream and equipment item a unique tag. Put the feeds,
   valve and exchanger in that execution order in a `ProcessSystem`.
2. Use `DynamicHeatExchanger` for the two-circuit thermal model. Supply wall
   mass and heat capacity, nominal fluid volumes, area and both film coefficients.
   Use reference water or seawater for the utility as described in
   [reference cooling water](reference_cooling_water).
3. Call `HeatExchangerAutomationWorkflow.instrument` with the actual exchanger
   tag, utility valve tag, project identity and `Settings`. It initializes the
   thermal state and creates nine actual devices: TT and PT at all four ports,
   plus an FT at the utility inlet. A native outlet-temperature TIC manipulates
   the existing utility valve. Settings allocate two adjacent numbers per circuit.
4. Build the current canonical graph with `PidNativeControlSystem.fromProcess`
   and explicit bindings with `PidNativeControlSystem.bind`.
5. Call `HeatExchangerControllerTuning.tune` with a `Definition` containing the
   controller tag, temperature target, timestep, duration, final hold window,
   temperature tolerance, maximum thermal residual and an array of Kp/Ti/Td
   candidates. Each candidate runs the actual native loop on an independent
   process copy. Read the candidate failures, traces, saturation and response metrics.
6. Apply an accepted recommendation through `applyRecommendation` with an
   explicit new revision. Reimport the graph and bindings after revision.
7. Call `HeatExchangerAutomationWorkflow.export` with explicit DEXPI provenance,
   a new output directory and the optional executed tuning result.

The initial automated loop uses Celsius, engineering-unit PID gains and a
5–100% valve range. It assumes increasing cooling-water flow reduces the
controlled outlet temperature. Verify this physical sign and valve authority for
the actual case. A heating service, cascade loop, split range, bypass or another
actuator needs its own explicit design rather than this cooling-loop helper.

## Thermal model and units

`DynamicHeatExchanger` extends `HeatExchanger` but uses the same three-node
model for both `run` and `runTransient`: two completely mixed, single-phase
fluid inventories exchanging heat with a metal wall. For each fluid $i$,

$$
M_i\frac{h_i^{n+1}-h_i^n}{\Delta t}
=\dot m_i(h_{i,\mathrm{in}}-h_i^{n+1})
-K_i(T_i^{n+1}-T_w^{n+1}),
$$

and for the wall,

$$
M_w C_w\frac{T_w^{n+1}-T_w^n}{\Delta t}
=\sum_i K_i(T_i^{n+1}-T_w^{n+1}),\qquad K_i=\alpha_i A.
$$

The nonlinear backward-Euler solve evaluates actual NeqSim enthalpy and Cp,
using a bounded Newton iteration and EJML. The reported thermal ledger includes
both fluid enthalpy inventories and the wall. It is a constant-pressure thermal
model, with mass flow in kg/s, enthalpy in J/kg, wall Cp in J/(kg K), area in m²,
film coefficients in W/(m² K), nominal volumes in m³ and time in seconds.

`run` initializes each fixed fluid mass from its solved density and nominal
volume. The volumes are initialization inputs; constant geometric volume,
thermal expansion and pressure evolution are not solved. Zero nominal fluid
volume gives an algebraic fluid node. Both inlet flows must stay positive.
Pressure, composition, salinity and supported thermodynamic model parameters
remain fixed during transient execution. Reinitialize deliberately with `run`
after changing the inventory basis. The audited fluid classes are `SystemSrkEos`,
`SystemPrEos`, `SystemWaterIF97` and `SystemSeawater`, with classical cubic-EOS
mixing for the EOS fluids. Other classes and mixing rules fail closed.
Composition comparison ignores normalization roundoff below 1e-12 mole fraction.

The inherited steady NTU/UA specification is not used by this subclass. This is
not a distributed counterflow, pressure-drop, two-phase, phase-change or
hydraulic-hammer model. Legacy `HeatExchanger` behavior remains available.

## Evidence, repeatability and restart

`getLastThermalBalance` exposes interval accumulation, boundary energy and
residual in joules. Reusing a physical UUID recomputes from that interval's
retained start state and advances the clock once; use a new UUID for each next
interval. Changing the duration for an existing UUID is rejected. Native
snapshots restore the existing outlet objects and retained physical interval.

For dynamic continuation, save the process with `saveToNeqsim` and reload with
`NeqSimXtream.openNeqsim`, casting the returned object to `ProcessSystem`.
`ProcessSystem.loadFromNeqsim` intentionally calls `run` and reinitializes steady
state, so it is not a dynamic-restart operation.

Tuning freezes the complete serialized source process after normalizing only
the selected controller's gains and setpoint on an isolated copy. This includes
thermodynamic parameters, controller history, sensor sample holds, valve state
and all process clocks. Changed source state or non-tunable policy invalidates
the evidence. The hash is a same-build state guard, not a durable cross-version
interchange identifier. Candidates share one frozen initial state and differ
only in the declared gains and target.

Acceptance requires the declared final hold window and thermal residual limit.
The lowest integral absolute error among passing candidates is recommended.
No passing candidate gives `NO_ACCEPTABLE_CANDIDATE`. A recommendation covers
the declared case and timestep; establish refinement and disturbance/saturation
performance for a plant-specific tuning study.

## Coordinated package

The exporter prepares a private package and moves it to a new directory, using
an atomic move where supported. It includes native PFD/P&ID PDFs,
SVG and layout companions, DEXPI XML, canonical P&ID, explicit object bindings,
automation registers, saved native simulation model, thermal configuration,
manifest and optional tuning trace/readback. The tuning receipt states whether
the exported actual gains match the tested candidate.

DEXPI carries typed plant and signal identities. The full executable control
policy and transient settings remain in the coordinated canonical sidecar and
native model; XML alone is not PLC parameter transfer. Import/export and
simulation validation do not establish receiving-tool or hardware qualification.

Missing line design, relief sizing, valve fail action and safety functions remain
explicit HOLD items. Automatic basic instrumentation does not infer those design
requirements or construction approval. See the existing
[heat exchanger models](heat_exchangers) for other thermal formulations.
