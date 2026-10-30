---
name: neqsim-water-hammer
description: "Water hammer and liquid hammer screening workflow for NeqSim. USE WHEN: evaluating fast valve closures, pump trips, check-valve slam, hydraulic surge, STID/P&ID route extraction, tagreader event windows, or MCP runWaterHammer studies. Covers WaterHammerPipe, WaterHammerStudy, STID route geometry, tagreader field-data overrides, pressure envelopes, Joukowsky checks, and screening limitations."
last_verified: "2026-10-29"
---

# Water Hammer and Liquid Hammer Screening

Use this skill when a task involves hydraulic surge from fast valve movement,
pump trip, check-valve slam, ESD closure, or other rapid liquid-flow changes.
The core model is `WaterHammerPipe`; the workflow facade is
`WaterHammerStudy`; MCP clients use `runWaterHammer`.

## When To Use

- Fast ESD or isolation-valve closure in water, condensate, oil, MEG, glycol, or
  other liquid-rich lines.
- Pump trip, pump start, check-valve slam, or sudden flow setpoint changes.
- STID/P&ID/E3D/stress-isometric route screening before a detailed surge study.
- Tagreader event-window replay where measured pressure, flow, temperature, and
  valve position should override design-basis values.
- Ranking which lines need detailed vendor or specialist surge analysis.

Do not use this as final pipe-stress approval. Treat it as a fast screening and
evidence-pack workflow.

For **Safety Shutdown**, read the current
[kernel and prescribed-travel guide](../../../docs/wiki/water_hammer_implementation.md)
and [campaign acceptance](https://github.com/equinor/neqsim/issues/4707) first.
The single-line kernel integrates characteristic fluxes over physical cells.
Use cell mean pressure and accepted-substep inlet/outlet exchanges for its linear
acoustic volume ledger; nodal pressure/flow are face traces, not inventory means.
Report first-order space/time refinement and boundary endpoint-hold quadrature.
A PASS acoustic balance does not qualify full EOS conservation, pump coastdown,
disc slam, network coupling, cavitation, support loads or integrated safety logic.
Reuse the canonical coordinator and existing ESD APIs for future integration.

## Forward acoustic pump coastdown

`AcousticPumpBoundary` optionally couples one declared reference-speed curve
through existing `PumpChart` affinity scaling and a prescribed actual-speed
table to upstream `PUMP`. Supply explicit positive speed limits, original
forward-flow data, head units and suction pressure with matching velocity and
elevation bases. Configure before `run()` and reinitialize after replacing the
boundary. Speed and valve knots share the existing conservative pipe clock.
An explicit empty `eventSchedule` selects a pump-only study.

Use the [complete API/JSON contract and executed evidence](../../../docs/wiki/water_hammer_implementation.md#safety-shutdown-forward-pump-coastdown).
Read `pumpHydraulicWork_J` as accepted interval hydraulic work, not shaft/EOS
energy. Study errors retain last accepted time/cells/work/internal outlet history;
never report an unsupported transition as a completed trip. Do not extrapolate
clamped forward curves or invent stopped/reverse/check-valve continuations.
Actual-speed data must have declared provenance; the public example is synthetic.
Full trips, shaft inertia, disc/slam, NPSH/cavitation and integrated Safety Shutdown
remain NOT_EVALUATED. `neqsim-dynamic-simulation` owns the canonical coordinator
and ESD handoff; this boundary adds no independent scheduler or solver.

## Workflow

1. Extract line geometry from STID/P&ID/E3D/stress isometrics: length, internal
   diameter, wall thickness, roughness/piping class, elevation change, fittings,
   valves, and source references.
2. Extract or infer event data: initiating valve, start time, closure/opening
   duration, start/end opening, pump trip status, and relevant controller action.
3. Pull plant data with tagreader when available: inlet pressure, temperature,
   flow rate, valve opening, pump speed/status, and downstream pressure.
4. Run `WaterHammerStudy` or MCP `runWaterHammer` with the equivalent route,
   field-data overrides, and event schedule.
5. Report wave speed, Courant time step, wave round-trip time, Joukowsky surge,
   max/min pressure envelope, design-pressure margin, assumptions, and evidence.
6. Recommend detailed surge analysis when peak pressure approaches design
   pressure, the route has varying diameters/branches, vapor cavities are likely,
   or support loads/stress checks are required.

## MCP Input Pattern

```json
{
  "studyName": "Safety Shutdown valve closure screening",
  "model": "SRK",
  "temperature_C": 20.0,
  "pressure_bara": 45.0,
  "components": {"water": 1.0},
  "flowRate": {"value": 120000.0, "unit": "kg/hr"},
  "designPressure_bara": 95.0,
  "pipe": {
    "length_m": 1200.0,
    "diameter_m": 0.2032,
    "wallThickness_m": 0.0127,
    "roughness_m": 4.6e-5,
    "elevation_m": 8.0,
    "numberOfNodes": 80
  },
  "fieldData": {
    "inletPressure_bara": 46.0,
    "inletTemperature_C": 19.0,
    "flowRate_kg_hr": 118000.0,
    "valveOpening": 1.0
  },
  "eventSchedule": [
    {
      "type": "VALVE_CLOSURE",
      "startTime_s": 0.10,
      "duration_s": 0.15,
      "startOpening": 1.0,
      "endOpening": 0.0
    }
  ],
  "simulationTime_s": 4.0,
  "sourceReferences": [
    "generic STID line-list row",
    "generic tagreader event window"
  ]
}
```

For route extraction, use `stidRoute.segments` with segment lengths, diameters,
wall thickness, roughness, elevation change, and `minorLosses` K values. The
study facade aggregates a serial route into an equivalent single line for fast
screening and warns when diameters vary.

## Result Interpretation

- `maxPressure_bara` and `minPressure_bara`: pressure envelope extrema during
  the transient.
- `pressureSurge_bar`: calculated surge above the initial outlet pressure.
- `joukowskySurgeEstimate_bar`: independent screening estimate using rho times
  wave speed times velocity change.
- `waveRoundTripTime_s`: acoustic round-trip time; closure faster than this is
  a fast-closure case.
- `maxStableTimeStep_s`: Courant time-step limit used to control numerical
  stability.
- `designPressureExceeded`: true when the supplied design pressure is exceeded.

## Standards And Review Triggers

Use applicable pipeline and piping standards as context: DNV-ST-F101,
NORSOK L-001, ASME B31.3, ASME B31.4, ASME B31.8, API 14E, and company piping
classes. Escalate to detailed study if any of these apply:

- Peak pressure exceeds design pressure or MAOP.
- Surge margin is small after including instrument uncertainty and model
  uncertainty.
- The route has branches, non-return valves, pump curves, relief devices, or
  vapor-cavity risk.
- Pipe stress, supports, nozzle loads, or valve actuator closure curves matter.
- The line contains multiphase fluid; simplify only with explicit assumptions.

## Related Skills

- `neqsim-pid-process-operations` for valve actions and operational scenarios.
- `neqsim-stid-retriever` and `neqsim-technical-document-reading` for route and
  evidence extraction.
- `neqsim-plant-data` for tagreader snapshots and event windows.
- `neqsim-process-safety` for risk framing and escalation into safety studies.
- `neqsim-flow-assurance` for broader pipeline operating-envelope work.

## Ideal translating acoustic check valve

`AcousticLiftCheckValve` is experimental: equal pressure/swept area, signed
orifice flow, declared translating mass/spring/preload/damping and perfectly
inelastic numerical contact. Supply independent initial force/flow equilibrium;
no automatic parameter calibration. `WaterHammerStudy` accepts `liftCheckValve`
SI fields and an empty eventSchedule. Keep accepted interval jet plus swept
flow; omitting swept flow violates pressure work. Separate physical damping,
BE numerical loss and impact loss. Refine contact and pressure together; do not
claim continuous grazing-event accuracy or vendor slam from numerical contact.

Read the [API, equations, public executed case and limits](../../../docs/wiki/water_hammer_implementation.md#safety-shutdown-ideal-translating-check-valve).
Use defensive `getLiftHistory()` and aligned named study diagnostics. Rejection
retains last accepted local cells/disc/clock/work; serialization is local replay,
not global rollback. Forward-pump domains remain unchanged. Full trips, swing
discs, flow-force/added-mass, rebound, cavitation, structural loads and integrated
Safety Shutdown remain NOT_EVALUATED. Hand off shared stepping/logic to
`neqsim-dynamic-simulation`; add no parallel scheduler or generic solver.
