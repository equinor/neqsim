---
name: neqsim-pid-process-operations
version: "1.0.0"
description: "P&ID-to-NeqSim operational workflow. USE WHEN: understanding P&ID symbols, converting P&ID topology into NeqSim process simulations, linking equipment and instrument tags to plant historian data via tagreader, evaluating steady-state and dynamic valve/equipment changes, or preparing water-hammer valve-closure handoffs."
last_verified: "2026-05-10"
requires:
  python_packages: [pandas]
---

# P&ID to Process Operations

This skill bridges P&ID interpretation, NeqSim process simulation, and plant
historian data. It is for operational questions such as "what happens if this
valve closes?", "which train is active?", "what inventory is trapped between
these valves?", and "how will pressure, level, flow, flare load, or emissions
change?"

Keep this public skill plant-agnostic. Do not include real facility names,
operator-specific document numbers, internal historian source names, real tag
maps, credentials, or private operating procedures. Put those details in a
private prompt file or local private skill.

## When to Use

- Reading P&ID symbols, line numbers, valves, instruments, drains, vents,
  interlocks, and equipment connections.
- Converting P&ID topology into a NeqSim `ProcessSystem`, `ProcessModel`, or
  `PipingRouteBuilder` route.
- Mapping P&ID instrument tags to plant historian tags read by `tagreader`.
- Determining active train/equipment state from valves, flows, pressures,
  levels, temperatures, and run-status tags.
- Evaluating steady-state process effects of valve position changes, blocked
  outlets, bypasses, recycles, drains, or alternate routing.
- Evaluating dynamic effects of valve actions using `runTransient`, controllers,
  measurement devices, or dedicated blowdown/depressurization models.
- Preparing `WaterHammerStudy` or MCP `runWaterHammer` inputs for fast liquid-line
  valve closure, pump trip, or check-valve slam.

## Required Skill Stack

Use this skill with:

- `neqsim-technical-document-reading` for P&ID image/document extraction,
- `neqsim-process-extraction` for topology to JSON/route conversion,
- `neqsim-plant-data` for tagreader reads, tag maps, and data quality,
- `neqsim-controllability-operability` for steady-state operating envelopes and
  control valve checks,
- `neqsim-dynamic-simulation` for transient valve-action studies,
- `neqsim-water-hammer` for hydraulic surge screening from fast liquid-line events,
- `neqsim-depressurization-mdmt` for blowdown and minimum-temperature cases,
- `neqsim-professional-reporting` for evidence traceability and report outputs.

## P&ID Symbol Interpretation Model

Read a P&ID as a process graph plus operational annotations:

| P&ID object | Graph meaning | NeqSim representation | Historian evidence |
|-------------|---------------|-----------------------|--------------------|
| Vessel, separator, scrubber | inventory and phase split node | `Separator`, `ThreePhaseSeparator`, `Tank` | pressure, level, temperature |
| Compressor, pump, expander | pressure/energy-changing node | `Compressor`, `Pump`, `Expander` | speed, power, suction/discharge P/T, run status |
| Heat exchanger, cooler, heater | heat-duty node | `HeatExchanger`, `Cooler`, `Heater` | inlet/outlet T, utility flow |
| Control valve | pressure-drop and manipulated-variable edge | `ThrottlingValve` | valve position, controller output, upstream/downstream P |
| Manual isolation valve | topology boundary or scenario switch | open/closed edge state or route K value | valve position if instrumented; otherwise drawing/procedure basis |
| Shutdown valve / on-off valve | dynamic event and isolation boundary | valve scenario event, often binary open/closed | limit switch, trip status, position |
| Check valve | one-way edge constraint | route K value and direction flag | reverse-flow indication if available |
| PSV/BDV/vent valve | relief or blowdown path | safety valve, blowdown route, flare source term | flare header P, valve status, event log |
| Drain/vent/purge connection | alternate evacuation route | scenario route or boundary stream | valve status, purge/flare destination |
| Instrument bubble | measurement or controller signal | transmitter/controller device or tag map entry | historian tag and data quality |
| Signal line / interlock | control logic or trip dependency | controller, logic note, dynamic event | controller mode, setpoint, trip status |

When symbol meaning is uncertain, record alternatives and confidence. Never
infer a valve's current open/closed state from a P&ID alone; use live data,
operator input, or the approved procedure basis.

## P&ID Extraction Schema

Extend the standard `PID_EXTRACTION` object with operational semantics:

```json
{
  "pid_operational_model": {
    "equipment_nodes": [
      {"id": "V-101", "type": "separator", "neqsim_type": "Separator"}
    ],
    "process_edges": [
      {
        "id": "L-101",
        "from": "V-101.gasOut",
        "to": "PV-101.inlet",
        "line_number": "L-101",
        "nominal_size_in": 8.0,
        "normal_service": "gas outlet"
      }
    ],
    "valves": [
      {
        "tag": "PV-101",
        "symbol_type": "control_valve",
        "normal_position": "modulating",
        "failsafe": "unknown",
        "neqsim_role": "ThrottlingValve",
        "scenario_actions": ["set outlet pressure", "change Cv", "close"]
      }
    ],
    "instruments": [
      {
        "pid_tag": "PT-101",
        "function": "pressure transmitter",
        "measured_object": "V-101",
        "logical_tag_name": "separator_pressure"
      }
    ],
    "control_links": [
      {"controller": "PIC-101", "measurement": "PT-101", "manipulated_valve": "PV-101"}
    ],
    "operational_boundaries": [
      {"name": "separator outlet isolation", "boundary_valves": ["XV-101", "XV-102"]}
    ]
  }
}
```

## Connecting P&ID Tags to Plant Data

Create a logical tag map first, then bind logical names to private historian
tags in a private skill or task-local `tag_mapping.json`:

```json
{
  "separator_pressure": "PRIVATE_HISTORIAN_TAG",
  "separator_level": "PRIVATE_HISTORIAN_TAG",
  "separator_temperature": "PRIVATE_HISTORIAN_TAG",
  "gas_outlet_valve_position": "PRIVATE_HISTORIAN_TAG",
  "gas_outlet_flow": "PRIVATE_HISTORIAN_TAG",
  "compressor_run_status": "PRIVATE_HISTORIAN_TAG"
}
```

In Java workflows, use the plant-agnostic helpers in
`neqsim.process.operations` so private historian names can stay outside public
models while NeqSim still uses its existing measurement devices and automation
API:

```java
OperationalTagMap tagMap = new OperationalTagMap()
  .addBinding(OperationalTagBinding.builder("separator_pressure")
    .historianTag("PRIVATE_HISTORIAN_TAG")
    .unit("bara")
    .role(InstrumentTagRole.INPUT)
    .build())
  .addBinding(OperationalTagBinding.builder("outlet_valve_position")
    .historianTag("PRIVATE_HISTORIAN_TAG")
    .automationAddress("Outlet Valve.percentValveOpening")
    .unit("%")
    .role(InstrumentTagRole.INPUT)
    .build());

ValidationResult validation = tagMap.validate(process);
Map<String, Double> applied = tagMap.applyFieldData(process, fieldData);
Map<String, Double> modelValues = tagMap.readValues(process);
```

This is a bridge only. Keep using `MeasurementDeviceInterface` tags, tag roles,
`ProcessSystem.setFieldData`, `ProcessSystem.applyFieldInputs`, and
`ProcessAutomation` for the actual model interaction.

For active-state inference, use at least two independent indicators: flow,
pressure, temperature, level movement, valve position, controller output, run
status, speed, power, or trip status. Save raw historian data as CSV inside the
task folder before running calculations.

## Converting to NeqSim

### Steady-State Model

1. Build the base flowsheet from the P&ID graph and process model.
2. Use live plant data to set boundary pressure, temperature, flow, level, and
   selected valve positions where available.
3. Run `process.run()` and compare simulated outputs with historian tags.
4. Save a base-case result before applying any change.

For large plants, use `ProcessModel` with one `ProcessSystem` per area. For
line-only questions, use `PipingRouteBuilder` rather than a full process model.

### Valve or Route Change Scenarios

Define every action as a model delta:

| Action | Steady-state delta | Dynamic delta |
|--------|--------------------|---------------|
| Close isolation valve | remove/disable route or set downstream flow to zero with a bounded case | binary valve event at time `t_event` |
| Partly close control valve | increase pressure drop or reduce Cv/opening | ramp valve opening or controller output |
| Open bypass | add parallel route/mixer/splitter branch | ramp bypass valve open |
| Block outlet | set outlet flow path closed and let upstream pressure/level find constraint | close outlet valve and track P/L/T |
| Open drain/vent | add drain or flare boundary stream | open drain/vent valve and track inventory loss |
| Change controller mode | fix manipulated variable or setpoint | switch AUTO/MAN logic in event schedule |

Always state whether the action is physically possible from the P&ID and whether
additional hidden paths, non-return valves, or interlocks may change the result.

For generic Java studies, represent simple action sequences with
`OperationalScenario` and run them with `OperationalScenarioRunner`. The runner
delegates valve opening changes to existing `SetValveOpeningAction`, variable
writes to `ProcessAutomation`, steady-state calculations to `process.run()`, and
dynamic calculations to `process.runTransient(dt, id)`:

```java
OperationalScenario scenario = OperationalScenario.builder("partly close outlet")
  .addAction(OperationalAction.setValveOpening("Outlet Valve", 15.0))
  .addAction(OperationalAction.setVariable("Outlet Valve.outletPressure", 45.0, "bara"))
  .addAction(OperationalAction.runSteadyState())
  .build();

OperationalScenarioResult result = OperationalScenarioRunner.run(process, scenario);
```

For controller tuning screens based on simulated or historian time series, use
`ControllerTuningStudy.evaluateStepResponse(...)`. It computes mean and maximum
error, IAE, ISE, overshoot, settling time, output saturation fraction, and a
short tuning recommendation without replacing NeqSim's controller classes.

For operating-envelope screening on the same P&ID/tagreader snapshot, use
`OperationalEnvelopeEvaluator` or MCP `runOperationalStudy` with
`action="evaluateOperatingEnvelope"`. Provide `designCapacities` from STID,
line lists, datasheets, or vendor curves when available; provide `fieldData`
through `OperationalTagMap`; provide `marginHistory` when a tagreader trend is
available. The output ranks margins, reports simple trip predictions, and returns
advisory mitigation suggestions without writing to plant systems.

### MCP Access

MCP clients can use `runOperationalStudy` for the same plant-agnostic workflow.
Supported actions are `getSchema`, `validateTagMap`, `applyFieldData`,
`runScenario`, `runEvidencePackage`, and `evaluateControllerResponse`. Use
`runEvidencePackage` when the task needs one auditable output containing field
data application, BENCHMARK tag comparison, base-case bottleneck detection,
scenario bottleneck reports, document evidence references, assumptions, and
quality gates. The tool operates on local simulation copies and does not write
to plant historians or control systems. Governed MCP profiles block it in
read-only digital-twin and enterprise modes.

## Steady-State Evaluation Pattern

Use a base/change comparison table:

```json
{
  "scenario": "close gas outlet valve",
  "mode": "steady_state",
  "base_case": {"pressure_bara": 20.0, "gas_flow_kg_hr": 10000.0},
  "changed_case": {"pressure_bara": 25.0, "gas_flow_kg_hr": 0.0},
  "deltas": {"pressure_bar": 5.0, "gas_flow_kg_hr": -10000.0},
  "constraints_triggered": ["upstream pressure increase"],
  "confidence": "medium"
}
```

Preferred checks:

- mass balance before/after the valve action,
- pressure feasibility at upstream and downstream equipment,
- separator level and residence time,
- compressor surge or recycle effect,
- flare or drain destination capacity,
- hydrate, wax, or low-temperature risks if pressure drops.

## Dynamic Evaluation Pattern

Use dynamic simulation when time response matters: closing a valve, blocked
outlet pressure rise, separator level change, controller response, blowdown,
startup/shutdown, or recycle instability.

Minimum dynamic output:

```json
{
  "scenario": "close outlet valve over 30 s",
  "mode": "dynamic",
  "event_schedule": [
    {"time_s": 300.0, "action": "ramp_close", "object": "PV-101", "duration_s": 30.0}
  ],
  "time_series": "step2_analysis/scenario_timeseries.csv",
  "key_results": {
    "max_pressure_bara": 0.0,
    "min_temperature_C": 0.0,
    "max_level_m": 0.0,
    "peak_flare_flow_kg_s": 0.0,
    "time_to_alarm_s": 0.0
  }
}
```

Run steady state first. Then use `runTransient(dt)` or a dedicated safety model
for blowdown. Use timestep sensitivity if the result depends on fast valve
movement.

## Reporting Requirements

For each operational change study, report:

- P&ID evidence: symbols, route, valves, instruments, and uncertainty,
- historian evidence: tag map, time window, data quality, active state,
- base-case NeqSim result and comparison with plant data,
- action definition and model delta,
- steady-state impact,
- dynamic impact when relevant,
- safety and operability constraints,
- emissions or flare source terms if material is vented or flared,
- limitations and required qualified review before field execution.

## Common Mistakes

| Mistake | Impact | Fix |
|---------|--------|-----|
| Treating every valve symbol as a `ThrottlingValve` | isolation and check valves get wrong physics | classify valve function first |
| Using P&ID normal position as live state | wrong active train and inventory | verify with tagreader or user-provided status |
| Simulating valve closure only as steady state | misses pressure/level transient and alarms | add dynamic case when time response matters |
| Ignoring controllers and interlocks | unrealistic response to valve action | extract control links and event logic |
| Forgetting hidden alternate paths | incorrect isolation boundary | inspect bypasses, drains, vents, check valves, and tie-ins |
| Putting real tag maps in public skills | leaks private operational data | keep tag maps in private prompts or task references |

## Validation Checklist

- [ ] P&ID symbols are classified by function, not only by shape.
- [ ] Topology is represented as nodes and directed edges.
- [ ] Every scenario action has a model delta and evidence source.
- [ ] Historian data and tag maps are saved in the task folder.
- [ ] Base case is compared with live or design data before changes are simulated.
- [ ] Steady-state and dynamic scopes are explicitly separated.
- [ ] Safety, flare, emissions, and low-temperature effects are checked when material is released.
- [ ] Private plant details remain outside public repo files.
