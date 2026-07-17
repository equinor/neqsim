---
name: neqsim-pid-process-operations
version: "1.8.0"
description: "Bidirectional P&ID/NeqSim workflow. USE WHEN: understanding P&ID symbols, converting P&ID topology into NeqSim simulations, generating governed DEXPI engineering packages from ProcessSystem or ProcessModel, linking tags to historian data, evaluating valve/equipment changes, or preparing water-hammer and blowdown/flare handoffs."
last_verified: "2026-07-17"
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
- Generating governed DEXPI engineering packages from a simulated `ProcessSystem`
  or multi-area `ProcessModel`, including available mechanical, piping, PSV,
  blowdown/flare and materials calculations.

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

### Generating a governed DEXPI engineering package

When the direction is NeqSim-to-P&ID, use the engineering project layer rather
than calling `DexpiXmlWriter` alone. It adds a versioned design basis,
standards traceability, deterministic control/safeguarding proposals, approval
state, validation findings, and referenced compressor-map datasets:

```java
EngineeringProject project = NorsokOffshoreEngineeringBuilder
    .from("Gas compression engineering model", process)
    .registerProposedInstruments(true)
    .build();

EngineeringValidationReport validation = project.validate();
DexpiEngineeringExporter.ExportResult files =
    DexpiEngineeringExporter.export(project, Paths.get("engineering-package"));
```

The generated `plant.dexpi.xml` is a native DEXPI 2.0 semantic process model and
is validated against the bundled official DEXPI XML schema during export.
`plant-proteus.xml` is the backward-compatible graphical P&ID containing
requirement-linked instrumentation functions, logic functions, information
flows, control/protective valves, and equipment associations. It references
`engineering-manifest.json`, the proposed
`cause-and-effect.json`, simulation-backed `engineering-calculations.json`, and
any `datasets/<tag>-compressor-map.json` sidecars.
Use `plant-pydexpi.xml` for pyDEXPI import and inspect
`interoperability-report.json`; native schema validity, semantic-profile
validity, pyDEXPI import and commercial-CAE round-trip are distinct gates.
Do not flatten large vendor maps into P&ID attributes.

Do not call the Proteus graphical file DEXPI 2.0. Antisurge recycle topology is
connected discharge-to-suction; PSV/BDV proposals use dedicated equipment
nozzles and a separate relief/blowdown network. Treat every
`UnresolvedBoundary=YES` as a required line, flare-header, vent, drain or utility
tie-in before engineering completion.
Represent controlled tie-ins with `EngineeringBoundary` so process inlet/outlet,
flare, vent, closed-drain, utility and recycle connections become directional
off-page connectors in native DEXPI rather than free-text assumptions.

For a full process-to-engineering run, follow the eight controlled stages in
`docs/integration/process-to-engineering-simulator.md`. Require convergence of
both physical design variables and process values. Use typed equipment,
network-piping, valve/instrument, safety, materials and mechanical calculation
modules for independent evidence. Compile the final project with
`EngineeringDeliverableCompiler`; do not assemble datasheets or registers from
ad-hoc notebook dictionaries when coordinated compiler artifacts are available.
Use `examples/notebooks/process_to_engineering_simulator.ipynb` for the closed
loop and coordinated-package pattern, and
`examples/notebooks/engineering_roadmap_steps_1_to_8.ipynb` for executable typed
calculation examples across equipment, piping, valve/instrument, safety,
materials and preliminary mechanics.

When qualifying rather than screening a typed calculation, build an
`EngineeringCalculationContext` with `productionQualification=true`, controlled
standard references and evidence references. In this mode, do not accept
equipment screening defaults, reference-diameter piping scaling, an unresolved
valve failure position, or an unreferenced two-phase relief method. Use
`ReliefSizingCalculation` for governed gas/liquid/steam orifice selection and
only for two-phase flow when a specialist mass-flux result and controlled method
reference have been supplied.

Treat `unresolved-engineering-actions.json` as a mandatory review input. A
calculated DEXPI package is never equivalent to HAZOP/LOPA acceptance, vendor
certification, code mechanical design, final metallurgy approval or
construction authorization.

Also inspect `engineering-production-readiness.json`. Do not describe a package
as production-ready merely because the design loop converged or package
validation passed. `QUALIFIED_FEED_SUPPORT` additionally requires independent
benchmarks for every executed method version, project method qualifications, an
explicit no-hidden-default auto-configuration result, named-tool DEXPI
round-trip evidence, approved safety-lifecycle evidence, three accepted pilots,
and release-quality evidence. Even that level always retains
`fitnessForConstruction=false` and does not grant final engineering approval.

For the first complete inlet-separator/compressor/cooler/export facility slice,
run `ProductionVerticalSlicePreflight.assess` first and use
`ProductionVerticalSliceSimulator.runStrictAndCompile` for controlled execution. Inspect
`engineering-vertical-slice-qualification.json`. Require the declared topology,
ten case types, converged physical design, active compressor surge and
stonewall curves, zero map extrapolation, executable dynamic safe-state tests,
coupled PSV/blowdown/flare capacity, controlled standards and evidence. Add the
map check through the revision-controlled policy with
`addCompressorOperatingEnvelope`. Passing all vertical-slice gates means only a
controlled pilot; never translate it to FEED approval or fitness for
construction.

For JPype workflows, use `VerticalSliceCaseMatrixFactory` to create explicit,
evidence-linked scalar boundary conditions for every required case type. Do not
treat those steady boundary changes as substitutes for transient initiating
events, HAZOP credibility decisions, or shutdown logic.

Retain `engineering-vertical-slice-execution-manifest.json` with the package.
Its SHA-256 fingerprint binds the project and policy revisions, case inputs,
dynamic scenarios, coupled safety studies, standards and evidence. A fingerprint
change requires recalculation and revision-impact review; it never grants
engineering approval.

Also inspect `engineering-qualification-plan.json`. Use its exact method keys
and open actions to drive `EngineeringBenchmarkDataset`,
`DexpiToolQualificationRunner`, `EngineeringPilotQualificationRunner`, and
`EngineeringReleaseQualificationRunner`. These runners convert actual
measurements and named external results into evidence; they do not perform an
independent review or create an acceptance record. Keep missing external
evidence open.

Use
`examples/notebooks/engineering_production_qualification_workflow.ipynb` as the
API pattern for the executable qualification workflows. Its data are synthetic
and must never be promoted to project evidence.

The calculation handoff automatically runs available equipment mechanical-design
and materials-screening models. It can run API 520/521 PSV sizing from attached
credible relief scenarios, and readiness-gated dynamic blowdown, flare load,
radiation, capacity, and header checks from a
`DynamicBlowdownFlareStudyDataSource`. A blocked-outlet PSV screen may be
generated from simulated full inflow only when declared design and relief-set
pressures exist. Treat missing-input statuses as data gaps; never fill them with
generic values merely to obtain a size.

For an engineering-readiness package, attach the governed project inputs that
correspond to the source documents:

- `LineDesignInput` for line-list dimensions, schedule, wall, material, piping
  class, corrosion allowance, design conditions and evidence reference;
- `ReliefScenarioBasis` for hazard-review-required API 521 causes, with matching
  scenarios in `OverpressureProtectionStudy`;
- `SafetyFunctionDesign` for LOPA/SRS-backed target SIL, sensor/logic/final-element
  failure data, MooN voting, proof-test interval, diagnostic coverage and beta factor;
- `ShutdownSequence` for cause/effect actions, safe positions, timing budget,
  linked requirement IDs, HAZOP/SRS references and reset/restart definition;
- `ReliefDeviceDesignInput` for the selected device, inlet/outlet geometry,
  allowable losses, two-phase method, fire zone, concurrency group and evidence;
- `EngineeringEvidenceRecord` for revision-controlled HAZOP, LOPA, SRS,
  line-list, vendor and calculation records linked to equipment and requirements;
- `EmergencyShutdownTestResult` from `EmergencyShutdownTestRunner`, linked to
  the applicable sequence with `addShutdownVerificationResult(...)`.

Inspect `engineeringReadiness` in `engineering-calculations.json`. It reports
coverage percentage, missing-input count, severity, responsible discipline and
approval state. Never interpret 100% calculation/evidence coverage as engineering
approval or fitness for construction. Failed or blocked calculation objects must
not be counted as completed merely because they are present in the handoff.

Also inspect `engineeringCoverageMatrix`, `installedReliefDeviceVerification`,
`reliefDisposalNetworkLoads`, `engineeringEvidenceStatus`, and the dynamic result
inside `shutdownSequenceVerification`. Review `dexpi-validation.json`, every CSV/JSON
under `registers/`, and `package-manifest.json`. If the project provides its
controlled DEXPI XSD, call `DexpiEngineeringValidator.validate(dexpiFile, xsdFile)`;
do not silently download or choose a schema edition.

For multi-area models, call
`NorsokOffshoreEngineeringBuilder.fromProcessModel(...)` and export each returned
`EngineeringProject` to its own area directory. Use
`examples/notebooks/dexpi_engineering_full_processsystem.ipynb` and
`examples/notebooks/dexpi_engineering_processmodel.ipynb` as the executable
reference patterns. Never bypass the governed exporter with `DexpiXmlWriter`
when the requested output includes safety, instrumentation, sizing or standards
traceability.

Safety governance is mandatory: rule-generated trips use `SIL_UNASSIGNED` and
`REVIEW_REQUIRED`; generated controller tuning, trip set points, and voting
architectures remain `NOT_ASSIGNED` unless a controlled `SafetyFunctionDesign`
supplies them. Never derive SIL from equipment type, a generic tag template, or
normal operating conditions. Set a SIL target only from an identified
HAZOP/LOPA/QRA record and approve it through the project SRS workflow per IEC
61511. See `neqsim-process-safety` for the risk-analysis handoff.

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

### Governed engineering simulation

When the result will feed DEXPI or discipline deliverables, prefer the
engineering-simulator contracts over mutating the live process:

1. Put normal, maximum, turndown, startup, shutdown, trip, blocked-outlet, fire,
   and utility-loss configurations in `EngineeringCaseSet`.
2. Run them with `EngineeringCaseRunner`; require convergence and retain both
   definition and result fingerprints.
3. Implement new discipline calculations as
   `EngineeringCalculationModule<I, O>` so readiness blockers, method version,
   inputs, evidence, standards, uncertainty, and review state are explicit.
4. Use `CoupledReliefBlowdownFlareCalculation` when steady relief groups and
   dynamic blowdown share a disposal system. Never infer credible causes or
   concurrency groups from topology alone.
5. Use `DynamicSafetyScenarioRunner` for response-time verification. Build
   logic through a `LogicFactory` against equipment in the isolated process
   copy; never retain valve/controller references from the live base model.
6. Use `EngineeringSimulationRunner` to execute all configured layers before
   generating the governed DEXPI package.

For process-to-engineering work, configure discipline modules with
`ProcessToEngineeringDesignBuilder` and execute `ProcessToEngineeringSimulator`.
Use `InletCompressionExportReferenceFacility.build()` as the executable acceptance
fixture when extending the first production vertical slice. Require directed stream
paths for the main process, anti-surge recycle, separator liquid outlet, PSV and BDV;
tag/type presence alone is insufficient. Run its strict preflight, common design
loop, dynamic scenario, coupled flare calculation and package compilation after any
change to engineering orchestration.
For revision-controlled automatic configuration, use an explicit
`EngineeringAutoConfigurationPolicy` and require
`EngineeringAutoConfigurator.Result.isExecutionReady()` before running or
publishing results. Inspect its dependency graph, configuration fingerprint and
revision impact; never suppress an unconfigured recognized equipment blocker.
For a `ProcessModel`, use `ProcessModelEngineeringSimulator` with one policy and
controlled case set per area, then review shared-stream dependencies in
`process-model-engineering-manifest.json`.
Represent utility, flare, blowdown, electrical and other common systems with
`EngineeringSharedSystemPolicy`; require controlled concurrency and evidence
references. Use `runIncremental` only with a retained baseline result and review
both `executedAreas` and `reusedAreas`. Never reuse an area invalidated through
a shared stream or shared-system dependency. Validate the root manifest with
`ProcessModelEngineeringPackageValidator` before release.
The `EngineeringDesignLoop` must converge both case simulations and physical
design variables. Use `EngineeringDesignUpdate.Applier` only against the
isolated working process; the source `ProcessSystem` must remain unchanged.
Prefer discrete pipe, Cv, driver, area, and volume candidates so the result is a
selectable engineering proposal rather than an unpurchasable continuous size.
Compile DEXPI only after the loop has run so the canonical graph, equipment
registers, calculation package, and exchange model use the designed process
copy. Preserve `fitnessForConstruction=false` until accountable approvals are
recorded.

Dynamic pass/fail verifies the supplied model and acceptance criteria. It does
not infer SIL, approve the SRS, establish independence, or authorize a field
action.

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
- [ ] Process-to-engineering loops rerun all cases after geometry or rating changes.
- [ ] DEXPI and registers use the designed process copy and retain approval boundaries.
