---
title: Automatic Scenario Generation
description: This document describes the automatic safety scenario generation infrastructure added to NeqSim.
---

# Automatic Scenario Generation

This document describes the automatic safety scenario generation infrastructure added to NeqSim.

## Overview

Process safety analysis requires systematic evaluation of failure modes. The `AutomaticScenarioGenerator` class automatically identifies potential failures from process topology and generates scenarios for what-if analysis.

## Key Features

- **Equipment-Specific Failures**: Identifies applicable failure modes per equipment type
- **Single Failures**: Generate one failure at a time
- **Combination Scenarios**: Generate multiple simultaneous failures
- **HAZOP Mapping**: Links failures to standard deviation types

## Usage

### Basic Usage

```java
ProcessSystem process = new ProcessSystem();
// ... configure process with valves, compressors, coolers, etc. ...

AutomaticScenarioGenerator generator = new AutomaticScenarioGenerator(process);

// Enable specific failure modes
generator.addFailureModes(
    FailureMode.COOLING_LOSS,
    FailureMode.VALVE_STUCK_CLOSED,
    FailureMode.COMPRESSOR_TRIP
);

// Generate single-failure scenarios
List<ProcessSafetyScenario> singleFailures = generator.generateSingleFailures();

// Generate combination scenarios (up to 2 simultaneous failures)
List<ProcessSafetyScenario> combinations = generator.generateCombinations(2);
```

### Convenience Methods on ProcessSystem

```java
// Quick scenario generation
List<ProcessSafetyScenario> scenarios = process.generateSafetyScenarios();

// With combination depth
List<ProcessSafetyScenario> combinations = process.generateCombinationScenarios(2);
```

## Failure Modes

| Mode | Description | HAZOP Deviation | Applicable To |
|------|-------------|-----------------|---------------|
| `COOLING_LOSS` | Complete loss of cooling | No flow | Coolers |
| `HEATING_LOSS` | Complete loss of heating | No flow | Heaters |
| `VALVE_STUCK_CLOSED` | Valve stuck closed | No flow | Valves |
| `VALVE_STUCK_OPEN` | Valve stuck open | More flow | Valves |
| `VALVE_CONTROL_FAILURE` | Control valve failure | Other | Control valves |
| `COMPRESSOR_TRIP` | Compressor emergency stop | No flow | Compressors |
| `PUMP_TRIP` | Pump emergency stop | No flow | Pumps |
| `BLOCKED_OUTLET` | Downstream blockage | No flow | Separators |
| `POWER_FAILURE` | Loss of electrical power | Other | All electrical |
| `INSTRUMENT_FAILURE` | Instrument/control failure | Other | All |
| `EXTERNAL_FIRE` | Fire exposure | High temperature | All |
| `LOSS_OF_CONTAINMENT` | Leak or rupture | Less pressure | All |

## HAZOP Deviations

| Deviation | Description |
|-----------|-------------|
| `NO_FLOW` | Complete loss of flow |
| `LESS_FLOW` | Reduced flow rate |
| `MORE_FLOW` | Increased flow rate |
| `REVERSE_FLOW` | Flow in wrong direction |
| `HIGH_PRESSURE` | Pressure above normal |
| `LOW_PRESSURE` / `LESS_PRESSURE` | Pressure below normal |
| `HIGH_TEMPERATURE` | Temperature above normal |
| `LOW_TEMPERATURE` | Temperature below normal |
| `HIGH_LEVEL` | Liquid level too high |
| `LOW_LEVEL` | Liquid level too low |
| `CONTAMINATION` | Unwanted substance present |
| `CORROSION` | Material degradation |

## Running Scenarios

### Manual Execution

```java
// Generate scenarios
List<ProcessSafetyScenario> scenarios = generator.generateSingleFailures();

for (ProcessSafetyScenario scenario : scenarios) {
    // Create a copy of the process for each scenario
    ProcessSystem copy = process.copy();
    
    // Apply the scenario
    scenario.applyTo(copy);
    
    // Run simulation
    try {
        copy.run();
        
        // Analyze results
        analyzeScenarioResults(scenario, copy);
        
    } catch (Exception e) {
        // Scenario caused simulation failure - important finding!
        recordFailedScenario(scenario, e);
    }
}
```

### Automated Execution with ScenarioRunResult

The generator provides built-in scenario execution capabilities:

```java
// Run all single-failure scenarios automatically
List<ScenarioRunResult> results = generator.runAllSingleFailures();

// Or run specific scenarios
List<ProcessSafetyScenario> scenarios = generator.generateSingleFailures();
List<ScenarioRunResult> results = generator.runScenarios(scenarios);

// Get execution summary
String summary = generator.summarizeResults(results);
System.out.println(summary);
```

#### ScenarioRunResult

Each result contains:

| Field | Type | Description |
|-------|------|-------------|
| `scenario` | ProcessSafetyScenario | The scenario that was run |
| `successful` | boolean | Whether simulation completed without error |
| `errorMessage` | String | Error message if failed (null otherwise) |
| `resultValues` | Map<String, Double> | Key results: pressures, temperatures, levels |
| `executionTimeMs` | long | Execution time in milliseconds |

#### Example Summary Output

```
=== Scenario Execution Summary ===
Total scenarios: 12
Successful: 10 (83.3%)
Failed: 2 (16.7%)

Failed scenarios:
  - COOLING_LOSS on Cooler-1: Simulation did not converge
  - COMPRESSOR_TRIP on MainCompressor: Exceeded iteration limit
```

## Failure Mode Summary

```java
String summary = generator.getFailureModeSummary();
System.out.println(summary);

// Output:
// Failure Mode Analysis Summary
// =============================
// Total potential failures: 42
//
// By Equipment Type:
//   ThrottlingValve: 12 failure modes
//   Compressor: 8 failure modes
//   Cooler: 6 failure modes
//   Separator: 8 failure modes
//   Pump: 8 failure modes
```

## Integration with Existing Safety Framework

The `AutomaticScenarioGenerator` complements the existing safety framework:

- **ProcessSafetyScenario**: Manual scenario definition
- **SafetyValve, RuptureDisk**: Physical safety equipment
- **AutomaticScenarioGenerator**: Automated scenario identification

## Best Practices

1. **Systematic Coverage**: Enable all relevant failure modes for comprehensive analysis
2. **Prioritization**: Focus detailed analysis on high-consequence scenarios
3. **Combination Limits**: Limit combination depth to 2-3 for practical analysis
4. **Documentation**: Record all scenario results for safety case
5. **Regular Updates**: Regenerate scenarios after process modifications

## Related Documentation

- [Safety Systems Package](README.md) - Safety equipment modeling
- [Batch Studies](../optimization/batch-studies.md) - Run many scenarios in parallel
- [Future Infrastructure Overview](../future-infrastructure.md) - Full infrastructure overview
