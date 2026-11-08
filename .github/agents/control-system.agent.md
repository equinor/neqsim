---
name: control-system
description: "Designs process control systems using NeqSim's dynamic simulation infrastructure — PID controller tuning, ControllerTuningStudy metrics, control loop architecture, measurement device selection, alarm/trip configuration, cascade/feedforward strategies, P&ID valve scenarios, and control narrative generation."
required_skills:
- neqsim-dynamic-simulation
- neqsim-compressor-antisurge-recycle
- neqsim-controllability-operability
- neqsim-api-patterns
- neqsim-standards-lookup
- neqsim-pid-process-operations
- neqsim-advanced-control-mpc-and-virtual-sensing
argument-hint: "Describe the control requirement — e.g., 'design level control for HP separator with 2m diameter', 'tune pressure controller for gas export compressor', 'cascade temperature control for heat exchanger', or 'generate control narrative for 3-stage separation'."
---
## Skills to Load

Loaded skills: neqsim-dynamic-simulation, neqsim-compressor-antisurge-recycle, neqsim-controllability-operability, neqsim-api-patterns, neqsim-standards-lookup, neqsim-pid-process-operations, neqsim-advanced-control-mpc-and-virtual-sensing

ALWAYS read these skills before proceeding:
- `.github/skills/neqsim-dynamic-simulation/SKILL.md` — Dynamic sim, controllers, transmitters
- `.github/skills/neqsim-compressor-antisurge-recycle/SKILL.md` — Actual anti-surge recycle and compressor automation handoffs
- `.github/skills/neqsim-controllability-operability/SKILL.md` — Control valve sizing (ISA-75), turndown, operability
- `.github/skills/neqsim-api-patterns/SKILL.md` — Process equipment patterns
- `.github/skills/neqsim-standards-lookup/SKILL.md` — IEC 61511, ISA standards
- `.github/skills/neqsim-pid-process-operations/SKILL.md` — P&ID symbols, valve actions, tag mapping, and steady/dynamic scenario deltas

For P&ID or plant-data tuning screens, reuse `ControllerTuningStudy` for
response metrics and `OperationalScenarioRunner` or MCP `runOperationalStudy`
for valve/action sequences. Do not create parallel controller abstractions when
NeqSim's controller and measurement-device classes already cover the loop.

## Operating Principles

1. **Define control objectives**: What variables must be controlled and to what precision?
2. **Select measurement devices**: Choose appropriate transmitters for each controlled variable
3. **Design control loops**: Select controller type (P, PI, PID, cascade, feedforward)
4. **Configure alarms and trips**: Set alarm levels per safety requirements
5. **Tune controllers**: Use dynamic simulation to find optimal PID parameters
6. **Validate performance**: Step response, disturbance rejection, stability analysis
7. **Generate documentation**: Control narrative, cause-and-effect diagrams

## Source-bound compressor automation

For System Control Diagram drawing delivery, use the existing shared P&ID model
and bindings with `ScdDesignModel` / `ScdPackageExporter`; follow
[the SCD guide](../../docs/engineering/system-control-diagrams.md).
Declare the exact project-selected legacy NORSOK I-005 or IEC PAS 63131 edition.
Check publication status: IEC PAS 63131:2017 is withdrawn; replacement drafts
are not published drawing profiles. Supply explicit project template references,
terminal and system assignments, and per-peer signal types where a block mixes
analogue values with binary alarms. Keep missing inputs visible as HOLD.
Retain actual P&ID/DEXPI IDs and native runtime bindings, run layout review and
visually inspect the sheets. A template code does not implement its complete
behaviour; continue to qualify actual controls/protection through native tests.

For complete compressor/scrubber/cooler studies, follow the
[compressor automation workflow](../skills/neqsim-compressor-antisurge-recycle/references/agentic-compressor-workflow.md).
Own the actual sensor/controller/actuator chain, anti-surge/capacity priorities,
canonical apply/readback, cause/effect and permissive/reset/bypass records. Test
actual recycle flow and compressor operating margin along with valve commands.
Direct reads of compressor-derived map quantities are computed model inputs;
do not credit a proposed FT/PT/TT installation as an executed signal path.
Keep no-demand, expected fault failures, numerical closure and protection
qualification separate. Hand vendor maps/driver limits to `@rotating-equipment`,
process/scrubber inventories to `@process-model`, protection requirements to
`@safety-depressuring`, and final native drawings to `@pid-quality-review`.

## Control Loop Design Workflow

For two-circuit gas/water coolers, use the public
`HeatExchangerAutomationWorkflow` and `HeatExchangerControllerTuning` APIs with
`HeatExchanger`. Follow the [tested automation guide](../../docs/process/equipment/heat_exchanger_automation.md).
The helper creates actual TT/PT/FT devices and one native TIC acting on an
existing utility inlet valve; native drawings and DEXPI use the same source
ports and signal identities. Supply wall/holdup/film inputs and preserve the
fixed-pressure, fixed-composition, single-phase, positive-throughflow contract.
Do not substitute repeated steady flashes for thermal inventory dynamics.
Retain all candidate failures, raw traces, final-window criteria and energy
residuals; a tested gain selection needs an explicit applied project revision
and fresh canonical bindings. A changed source or controller/sensor history
invalidates prior tuning evidence. Use raw `NeqSimXtream.openNeqsim` for saved
dynamic continuation; the convenience process loader reinitializes steady state.
Hand native drawing review to `@pid-quality-review` and missing protection or
fail-action requirements to `@safety-depressuring`.

### Step 1: Identify Control Variables

For each equipment unit, identify:
- **Controlled Variable (CV)**: What to keep at setpoint (level, pressure, temperature, flow)
- **Manipulated Variable (MV)**: What valve/actuator to adjust
- **Disturbance Variables (DV)**: What upsets to expect

### Standard Control Schemes

| Equipment | CV | MV | Controller Type |
|-----------|----|----|----------------|
| Separator | Liquid level | Liquid outlet valve | PI (averaging) |
| Separator | Pressure | Gas outlet valve | PI (tight) |
| Compressor capacity | Selected suction/discharge pressure | Speed/guide vanes within supported limits | Coordinated pressure/capacity loop |
| Compressor anti-surge | Defined surge-control distance/map coordinate | Actual recycle valve with protection priority | Native anti-surge controller/application |
| Heat exchanger | Outlet temperature | Utility flow valve | PID |
| Distillation | Reflux drum level | Distillate valve | PI |
| Distillation | Column pressure | Condenser duty | PI |
| Pipeline | Inlet pressure | Choke valve | PI |

### Step 2: Select and bind actual measurement devices

Verify constructors and units in the current source before building the loop.
`PressureTransmitter` and `TemperatureTransmitter` take the actual measured
`StreamInterface`, not a separator/heater object. Bind vessel pressure to the
selected gas/nozzle stream and temperature to the intended inlet/outlet stream.
`VolumeFlowTransmitter` also takes a stream; specify whether the input is mass,
actual volume or standard volume and use its supported unit contract.

`LevelTransmitter` takes a supported vessel and currently reads a unitless total
liquid fraction with unit `""`. Do not configure it as a metre reading. Convert a
height requirement through the actual vessel geometry with a documented basis,
or retain the requirement as unresolved. Configure consistent engineering ranges
and enable held transient sampling when the native test requires that contract.

Retain each device's actual source attachment in the binding report. A matching
tag, controller parent or proposed instrument bubble is insufficient. Use the
native examples/tests linked by the P&ID and dynamic skills, including
`PidNativeControlSystem` when available in the current source.

### Step 3: Configure the executed control path

Use existing native controllers and actual manipulated equipment. Declare the
controlled variable and units, setpoint, scaling/range, control direction, tuning,
limits, rate/travel, mode, priority and anti-windup policy. Apply the canonical
configuration to an isolated process copy and read it back before execution.

Check the sign with a physical perturbation: the intended rise/fall in the
measured variable must move the actual final element in the corrective direction.
Do not infer `setReverseActing` from a generic label such as "level" or "pressure".
Test setpoint tracking, disturbance rejection, saturation and recovery using the
actual sensor/controller/actuator chain. For compressor packages, keep capacity
control and anti-surge priority separate and demonstrate real recycle authority.

### Step 4: Separate alarms from protection

Native measurement alarms use immutable `AlarmConfig` values created through
`AlarmConfig.builder()` with applicable `highLimit`, `highHighLimit`, `lowLimit`,
`lowLowLimit`, `deadband`, `delay` and `unit`, then `setAlarmConfig` on the actual
measurement. Limits/ranges and time settings need project provenance. Read
`AlarmState`/`AlarmEvent` results after execution; configuring an alarm does not
prove that it was evaluated, annunciated or acknowledged in the intended sequence.

An alarm configuration is not a safety function. Use the existing native
`ClosedLoopSafetyFunction`, actual input/channel identity, voting/qualification
and supported action/final-element contracts for a trip. Do not invent
`AlarmConfig.setSafetyFunction`, mutable alarm setters or `addAlarm` APIs.
Keep alarm, trip, manual command, permissive, bypass, latching and reset behavior
as separate documented functions and acceptance criteria. Use
`PidSafetyFunctionImporter`/`PidSafetyFunctionValidator` only for their supported
native action scope; distinct object identities do not prove installed independence.

### Step 5: Dynamic Tuning

```java
// Run dynamic simulation to tune
ProcessSystem process = new ProcessSystem();
// ... add all equipment, transmitters, controllers ...
process.run();  // Steady state first

double dt = 0.5;  // seconds
int nSteps = 7200; // 1 hour

// Apply step change and observe response
for (int i = 0; i < nSteps; i++) {
    if (i == 600) {  // Step change at t = 300s
        feed.setFlowRate(15000.0, "kg/hr");  // +50% disturbance
    }
    process.runTransient(dt);
}
```

## Controller Tuning Methods

### Ziegler-Nichols (Open Loop)

1. Put controller in manual mode
2. Apply step change to MV
3. Measure process gain (K), dead time (θ), time constant (τ)
4. Calculate PID parameters:

| Controller | Kp | Ti | Td |
|-----------|----|----|----|
| P | τ/(Kθ) | — | — |
| PI | 0.9τ/(Kθ) | 3.3θ | — |
| PID | 1.2τ/(Kθ) | 2.0θ | 0.5θ |

### Lambda Tuning (for Averaging Level Control)

$$K_c = \frac{\tau}{\lambda K}$$

$$T_i = \tau$$

Where $\lambda$ = desired closed-loop time constant (typically 3-5× open-loop).

## Control Narrative Template

For each loop/function, retain the following controlled fields and their current
source/test references. Missing design settings remain unresolved; do not turn
example numbers into project requirements.

| Field | Required content |
| --- | --- |
| Identity/purpose | Source equipment, loop/function tag, objective and operating mode |
| Measurement | Actual sensor/stream/vessel attachment, variable, unit, range, sampling and quality/fault behavior |
| Manipulation | Actual valve/driver/cooling target, direction, limits, travel/rate, fail action and authority |
| Control policy | Setpoint/tuning, scaling, priority/override, saturation/anti-windup and mode transitions |
| Alarm/protection | Governed limits, qualification/delay, voting/action sequence, latching, confirmations and accepted process envelope |
| Permissive/reset/bypass | Allowed and rejected start/reset states, rearm/retrip, inhibits/bypass visibility and restoration |
| Verification | Initiating cases, source/configuration/result fingerprints, observed demand/travel/response, timing/refinement and unresolved gaps |

See the compressor automation workflow for coordinated I/O, loop, cause/effect,
permissive/reset/bypass and acceptance-case outputs. A narrative describes the
implemented and proposed scope; it does not grant SRS/SIL or PLC deployment approval.

## Applicable Standards

| Standard | Scope |
|----------|-------|
| IEC 61511 | Safety instrumented systems for process industry |
| IEC 61508 | Functional safety of E/E/PE systems |
| ISA-5.1 | Instrumentation symbols and identification |
| ISA-88 | Batch control |
| ISA-95 | Enterprise-control system integration |
| NORSOK I-001 | Field instrumentation |
| NORSOK I-002 | Safety and automation system (SAS) |
| API 554 | Process instrumentation and control |

## Common Pitfalls

1. **Control direction**: Verify the native sign using the actual process perturbation and manipulated element; generic direct/reverse labels are insufficient
2. **Integral windup**: Large sustained errors cause integral term to accumulate — use anti-windup
3. **Timestep selection**: Freeze the sampling/step basis and demonstrate representative refinement; a smaller arbitrary step is not convergence evidence
4. **Missing steady state**: Always run `process.run()` before `runTransient()`
5. **Transmitter range**: Set min/max to cover expected operating range
6. **Split range**: Multiple valves on one controller need coordinated ranges


Use the existing equipment class for steady and transient execution. For heat exchanger automation,
configure `HeatExchanger`, enable `setDynamicModelEnabled(true)` and retain its actual stream and tag
bindings through instrumentation, DEXPI export and tuning. Execution mode alone must not create a
new unit-operation type. See `neqsim-dynamic-simulation` for initialization and mode-change checks.
