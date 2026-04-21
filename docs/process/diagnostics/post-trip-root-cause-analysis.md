---
title: "Post-Trip Root Cause Analysis & Restart Optimisation"
description: "Automated trip detection, root cause analysis with ranked hypotheses, failure propagation tracing, and optimised restart sequencing for process equipment. Covers TripEventDetector, RootCauseAnalyzer, FailurePropagationTracer, and the restart sub-package."
---

# Post-Trip Root Cause Analysis & Restart Optimisation

The `neqsim.process.diagnostics` package provides automated post-trip analysis for process plants. It detects trip events during dynamic simulation, generates ranked root-cause hypotheses with evidence, traces failure propagation through the flowsheet, and produces optimised restart sequences.

## Overview

When a process trip occurs — a compressor surges, a separator floods, a pressure limit is breached — operators need to quickly answer three questions:

1. **What caused the trip?** (Root Cause Analysis)
2. **How did the failure propagate?** (Failure Tracing)
3. **How do we restart safely and quickly?** (Restart Optimisation)

This package answers all three programmatically, integrating with NeqSim's dynamic simulation, alarm management, and process lifecycle infrastructure.

## Architecture

```
ProcessSystem
  └── TripEventDetector          ← monitors alarms/states during runTransient()
        └── TripEvent            ← immutable trip record
        └── UnifiedEventTimeline ← sorted alarm/state/controller events
        └── ProcessStateSnapshot ← before/after thermodynamic state

RootCauseAnalyzer
  └── TripHypothesis (abstract)
        ├── HydrateFormationHypothesis
        ├── LiquidCarryOverHypothesis
        ├── CompressorSurgeHypothesis
        ├── SeparatorFloodingHypothesis
        ├── HighPressureHypothesis
        └── InstrumentFailureHypothesis
  └── HypothesisResult           ← score + confidence + evidence + action
  └── FailurePropagationTracer   ← upstream/downstream chain
  └── RootCauseReport            ← JSON/text report

restart/
  ├── RestartReadiness           ← pre-restart constraint checking
  ├── RestartConstraintChecker   ← evaluates equipment readiness
  ├── RestartStep                ← single restart action
  ├── RestartSequence            ← ordered list of steps
  ├── RestartSequenceGenerator   ← builds sequence from root cause
  ├── RestartSimulator           ← executes sequence in dynamic sim
  ├── RestartSimulationResult    ← outcome + step records
  └── RestartOptimiser           ← reduces MTTR via step reordering
```

## Quick Start

### Java

```java
import neqsim.process.diagnostics.*;
import neqsim.process.diagnostics.restart.*;

// 1. Get the detector from your process system
TripEventDetector detector = process.getTripEventDetector();
detector.initialize();

// 2. Run dynamic simulation (detector monitors automatically)
process.runTransient(10.0);  // seconds

// 3. Check if a trip occurred
TripEvent trip = detector.getLastTrip();
if (trip != null) {
    // 4. Run root cause analysis
    RootCauseAnalyzer analyzer = new RootCauseAnalyzer(process);
    RootCauseReport report = analyzer.analyzeFromDetector(detector);

    // 5. View results
    System.out.println(report.toTextSummary());
    String json = report.toJson();  // full structured output

    // 6. Generate restart sequence
    RestartSequenceGenerator generator = new RestartSequenceGenerator(process);
    RestartSequence sequence = generator.generate(trip, report);

    // 7. Optimise for minimum MTTR
    RestartOptimiser optimiser = new RestartOptimiser(process);
    RestartOptimiser.OptimisationResult optimised = optimiser.optimise(sequence);
    RestartSequence bestSequence = optimised.getOptimisedSequence();

    // 8. Check restart readiness
    RestartConstraintChecker checker = new RestartConstraintChecker(process);
    RestartReadiness readiness = checker.check(trip, currentTime);
    if (readiness.getStatus() == RestartReadiness.Status.READY) {
        // 9. Execute restart
        RestartSimulator simulator = new RestartSimulator(process);
        RestartSimulationResult result = simulator.execute(bestSequence);
    }
}
```

### Python (Jupyter)

```python
from neqsim import jneqsim

# Get classes
TripEventDetector = jneqsim.process.diagnostics.TripEventDetector
RootCauseAnalyzer = jneqsim.process.diagnostics.RootCauseAnalyzer
RestartSequenceGenerator = jneqsim.process.diagnostics.restart.RestartSequenceGenerator
RestartOptimiser = jneqsim.process.diagnostics.restart.RestartOptimiser

# After building and running a ProcessSystem...
detector = process.getTripEventDetector()
detector.initialize()
process.runTransient(10.0)

trip = detector.getLastTrip()
if trip is not None:
    analyzer = RootCauseAnalyzer(process)
    report = analyzer.analyzeFromDetector(detector)
    print(report.toTextSummary())
```

## Key Classes

### TripType

Enum with 12 trip categories:

| Value | Description |
|-------|-------------|
| `COMPRESSOR_SURGE` | Compressor surge or anti-surge trip |
| `HIGH_PRESSURE` | Pressure exceeded HIHI / PSV activated |
| `HIGH_LEVEL` | Liquid level exceeded HIHI |
| `HIGH_TEMPERATURE` | Temperature exceeded HIHI |
| `LOW_FLOW` | Flow below LOLO / loss of flow |
| `ESD_ACTIVATED` | Emergency shutdown |
| `MANUAL` | Operator-initiated trip |
| `INSTRUMENT_FAILURE` | Sensor or transmitter fault |
| `POWER_LOSS` | Electrical supply failure |
| `DRIVER_OVERLOAD` | Motor or turbine driver overload |
| `HIGH_VIBRATION` | Excessive vibration on rotating equipment |
| `UNKNOWN` | Unclassified trip |

Helper methods: `isRotatingEquipmentRelated()`, `isSafetyInitiated()`.

### TripEvent

Immutable record capturing a single trip occurrence. Built via the Builder pattern:

```java
TripEvent event = new TripEvent.Builder()
    .eventId("TRIP-001")
    .timestamp(1234.5)
    .initiatingEquipment("K-100")
    .tripType(TripType.COMPRESSOR_SURGE)
    .severity(3)
    .description("Compressor K-100 surged at 85% load")
    .addAlarm("HIHI surge margin")
    .addEquipmentValue("surgeMargin", 2.5)
    .build();
```

### UnifiedEventTimeline

Collects and sorts all events (alarms, state changes, controller actions, valve actions, measurements, trips) into a single chronological timeline. Supports filtering by time window, equipment name, and event type.

### ProcessStateSnapshot

Captures the thermodynamic state of the process at the moment of trip using the lifecycle `ProcessSystemState` infrastructure. Provides `diff()` to compare pre-trip ("last good") vs trip states.

### TripHypothesis & HypothesisResult

Abstract base class for root-cause hypotheses. Each implementation evaluates evidence from the snapshot and timeline, returning a `HypothesisResult` with:

- **Score** (0.0 — 1.0): numeric confidence
- **Confidence level**: CONFIRMED (&ge; 0.90), LIKELY (&ge; 0.70), POSSIBLE (&ge; 0.40), UNLIKELY (&ge; 0.15), RULED_OUT (&lt; 0.15)
- **Evidence**: list of supporting observations
- **Recommended action**: what to do about it

Six built-in hypotheses are registered by default:

| Hypothesis | Applicable Trip Type | What It Checks |
|------------|---------------------|----------------|
| `HydrateFormationHypothesis` | Any | Temperature below hydrate formation curve |
| `LiquidCarryOverHypothesis` | HIGH_LEVEL | Liquid in gas outlet streams |
| `CompressorSurgeHypothesis` | COMPRESSOR_SURGE | Operating point vs surge line |
| `SeparatorFloodingHypothesis` | HIGH_LEVEL | Level, gas velocity, inlet momentum |
| `HighPressureHypothesis` | HIGH_PRESSURE | Pressure vs design limits |
| `InstrumentFailureHypothesis` | INSTRUMENT_FAILURE | Sensor drift, frozen values, deviation |

Custom hypotheses can be added via `analyzer.addHypothesis(myHypothesis)`.

### FailurePropagationTracer

Traces failure chains through the flowsheet topology using stream connections:

```java
FailurePropagationTracer tracer = new FailurePropagationTracer(process);
PropagationChain chain = tracer.traceBidirectional("K-100", 3);
// Returns: upstream causes → K-100 → downstream effects
```

### RootCauseReport

Aggregates all analysis results into a structured report with:
- Most likely hypothesis and confidence
- All hypotheses ranked by score
- Failure propagation chain
- Recommended corrective actions
- Full JSON export via `toJson()`
- Human-readable text via `toTextSummary()`

### Restart Sub-Package

The `restart` sub-package handles safe, optimised restart after a trip:

| Class | Purpose |
|-------|---------|
| `RestartReadiness` | Pre-restart constraint check result (READY / READY_WITH_WARNINGS / NOT_READY) |
| `RestartConstraintChecker` | Evaluates equipment constraints (pressure, temperature, level) |
| `RestartStep` | Single restart action (valve open, compressor start, setpoint change, wait) |
| `RestartSequence` | Ordered list of steps with estimated duration |
| `RestartSequenceGenerator` | Builds restart sequence from trip event + root cause report |
| `RestartSimulator` | Executes the sequence using `runTransient()` |
| `RestartSimulationResult` | Outcome (SUCCESS / PARTIAL_SUCCESS / FAILED / RE_TRIPPED) with step records |
| `RestartOptimiser` | Reorders/parallelises steps to minimise Mean Time To Repair (MTTR) |

### RestartStep Action Types

| ActionType | Description |
|------------|-------------|
| `VALVE_ACTION` | Open, close, or adjust a valve |
| `COMPRESSOR_START` | Start a compressor |
| `COMPRESSOR_RAMP` | Ramp compressor to target speed/load |
| `SETPOINT_CHANGE` | Change a controller setpoint |
| `WAIT_DURATION` | Wait a fixed number of seconds |
| `WAIT_CONDITION` | Wait until a process condition is met |
| `OPERATOR_ACTION` | Manual operator intervention required |
| `VERIFICATION` | Verify a process condition before continuing |

## ProcessSystem Integration

The `TripEventDetector` is lazily initialised on `ProcessSystem`:

```java
// No explicit construction needed
TripEventDetector detector = process.getTripEventDetector();
```

This integrates with the existing alarm manager and dynamic simulation infrastructure.

## Extending with Custom Hypotheses

```java
public class MyCustomHypothesis extends TripHypothesis {
    public MyCustomHypothesis() {
        super("Custom Hypothesis", "Checks for my specific failure mode",
              TripType.HIGH_TEMPERATURE);
        // Pass null as TripType to match any trip type
    }

    @Override
    public HypothesisResult evaluate(ProcessStateSnapshot snapshot,
                                      UnifiedEventTimeline timeline) {
        List<String> evidence = new ArrayList<>();
        double score = 0.0;
        // ... evaluate evidence from snapshot and timeline ...
        return new HypothesisResult(getName(), score, evidence, "Recommended action");
    }
}

// Register it
analyzer.addHypothesis(new MyCustomHypothesis());
```

## Related Documentation

- [Dynamic Simulation Guide](../dynamic-simulation.md)
- [Dynamic Simulation Enhancements](../dynamic-simulation-enhancements.md)
- [Process Model Lifecycle](../lifecycle/process_model_lifecycle.md)
- [Process Automation API](../../simulation/process_automation.md)
