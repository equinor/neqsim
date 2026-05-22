---
name: design control systems
description: "Designs process control systems using NeqSim's dynamic simulation infrastructure — PID controller tuning, ControllerTuningStudy metrics, control loop architecture, measurement device selection, alarm/trip configuration, cascade/feedforward strategies, P&ID valve scenarios, and control narrative generation."
argument-hint: "Describe the control requirement — e.g., 'design level control for HP separator with 2m diameter', 'tune pressure controller for gas export compressor', 'cascade temperature control for heat exchanger', or 'generate control narrative for 3-stage separation'."
---

## Skills to Load

Loaded skills: neqsim-dynamic-simulation, neqsim-controllability-operability, neqsim-api-patterns, neqsim-standards-lookup, neqsim-pid-process-operations

ALWAYS read these skills before proceeding:
- `.github/skills/neqsim-dynamic-simulation/SKILL.md` — Dynamic sim, controllers, transmitters
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

## Control Loop Design Workflow

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
| Compressor | Suction pressure | Recycle valve | PI |
| Compressor | Discharge pressure | Speed/guide vanes | PI |
| Heat exchanger | Outlet temperature | Utility flow valve | PID |
| Distillation | Reflux drum level | Distillate valve | PI |
| Distillation | Column pressure | Condenser duty | PI |
| Pipeline | Inlet pressure | Choke valve | PI |

### Step 2: Select Measurement Devices

```java
import neqsim.process.measurementdevice.*;

// Pressure
PressureTransmitter PT = new PressureTransmitter("PT-100", separator);
PT.setUnit("bara");
PT.setMaximumValue(100.0);
PT.setMinimumValue(0.0);

// Level
LevelTransmitter LT = new LevelTransmitter("LT-100", separator);
LT.setUnit("m");

// Temperature
TemperatureTransmitter TT = new TemperatureTransmitter("TT-100", heater);
TT.setUnit("C");

// Flow
VolumeFlowTransmitter FT = new VolumeFlowTransmitter("FT-100", stream);
FT.setUnit("kg/hr");
```

### Step 3: Configure Controllers

```java
import neqsim.process.controllerdevice.*;

// Level controller
ControllerDeviceInterface LC = new ControllerDeviceBaseClass();
LC.setControllerSetPoint(1.5);    // Target level in meters
LC.setTransmitter(LT);
LC.setReverseActing(true);        // Level up -> open valve
LC.setControllerParameters(1.0, 120.0, 0.0);  // Kp, Ti, Td

liquidValve.addController("LC-100", LC);

// Pressure controller
ControllerDeviceInterface PC = new ControllerDeviceBaseClass();
PC.setControllerSetPoint(50.0);   // Target pressure bara
PC.setTransmitter(PT);
PC.setReverseActing(false);       // Pressure up -> open valve
PC.setControllerParameters(0.8, 60.0, 5.0);

gasValve.addController("PC-100", PC);
```

### Step 4: Alarm Configuration

```java
// High-level alarm
AlarmConfig HLA = new AlarmConfig();
HLA.setAlarmType(AlarmConfig.AlarmType.HIGH);
HLA.setAlarmSetPoint(1.8);    // m
HLA.setDeadband(0.05);
LT.addAlarm("LAH-100", HLA);

// High-high level trip (ESD)
AlarmConfig HHLA = new AlarmConfig();
HHLA.setAlarmType(AlarmConfig.AlarmType.HIGH_HIGH);
HHLA.setAlarmSetPoint(2.0);
HHLA.setSafetyFunction(true);  // SIF
LT.addAlarm("LAHH-100", HHLA);
```

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

For each control loop, document:

```markdown
### LC-100: HP Separator Level Control

**Objective**: Maintain liquid level in HP Separator at 1.5 m ± 0.2 m

**Controlled Variable**: Liquid level (LT-100)
**Manipulated Variable**: Liquid outlet valve (LV-100)
**Controller Type**: PI, reverse-acting
**Parameters**: Kp = 1.0, Ti = 120 s

**Normal Operation**:
- Setpoint: 1.5 m
- Operating range: 1.0 - 2.0 m

**Alarms**:
- LAL-100: Low level alarm at 0.8 m
- LAH-100: High level alarm at 1.8 m
- LAHH-100: High-high level trip at 2.0 m (closes inlet SDV)

**Failsafe**: Valve fails open on air failure (FO)
```

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

1. **Direct vs reverse acting**: Level controllers are typically reverse-acting; pressure controllers are typically direct-acting
2. **Integral windup**: Large sustained errors cause integral term to accumulate — use anti-windup
3. **Timestep too large**: Dynamic sim may oscillate — reduce to 0.1-0.5 s
4. **Missing steady state**: Always run `process.run()` before `runTransient()`
5. **Transmitter range**: Set min/max to cover expected operating range
6. **Split range**: Multiple valves on one controller need coordinated ranges
