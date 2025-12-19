# Batch Studies

This document describes the batch study infrastructure for parallel parameter studies and concept screening.

## Overview

Early-phase engineering requires rapid evaluation of many alternatives. The `BatchStudy` class provides:

- **Parameter Sweeps**: Vary design variables systematically
- **Parallel Execution**: Utilize multiple CPU cores
- **Multi-Objective Ranking**: Compare by cost, emissions, efficiency
- **Result Aggregation**: Collect and analyze efficiently

## Usage

### Basic Usage

```java
ProcessSystem baseCase = new ProcessSystem();
// ... configure base case ...

// Build a batch study
BatchStudy study = BatchStudy.builder(baseCase)
    // Vary parameters
    .vary("heater.duty", 1.0e6, 5.0e6, 5)       // 5 values from 1-5 MW
    .vary("compressor.pressure", 30.0, 80.0, 6)  // 6 values from 30-80 bar
    
    // Define objectives
    .addObjective("power", Objective.MINIMIZE, 
        process -> process.getTotalPowerConsumption())
    .addObjective("throughput", Objective.MAXIMIZE,
        process -> process.getThroughput())
    .addObjective("emissions", Objective.MINIMIZE,
        process -> process.getTotalCO2Emissions())
    
    // Configure execution
    .parallelism(8)
    .name("HeaterCompressorStudy")
    .stopOnFailure(false)
    
    .build();

// Run the study
BatchStudyResult result = study.run();

// Analyze results
System.out.println("Total cases: " + result.getTotalCases());
System.out.println("Completed: " + result.getCompletedCases());
System.out.println("Failed: " + result.getFailedCases());

// Export results
result.exportToCSV("batch_results.csv");
```

### Convenience Method on ProcessSystem

```java
// Quick batch study creation
BatchStudy.Builder studyBuilder = process.createBatchStudy();
```

## Parameter Variation Methods

```java
// Method 1: Range with steps
.vary("parameter", min, max, steps)
// Example: .vary("pressure", 10.0, 50.0, 5)
// Generates: [10.0, 20.0, 30.0, 40.0, 50.0]

// Method 2: Explicit values (varargs)
.vary("parameter", value1, value2, value3)
// Example: .vary("pressure", 10.0, 25.0, 50.0)
// Uses exactly those values
```

## Supported Parameter Paths

Parameters are specified as `equipment.property`:

| Property | Equipment Types | Example |
|----------|-----------------|---------|
| `duty` | Heaters, Coolers | `heater.duty` |
| `pressure` | Valves, Separators | `valve.pressure` |
| `outletPressure` | Valves, Compressors, Pumps | `compressor.outletPressure` |
| `opening` | Valves | `valve.opening` |
| `percentValveOpening` | Valves | `valve.percentValveOpening` |
| `cv` | Valves | `valve.cv` |
| `outletTemperature` | Heaters, Coolers | `heater.outletTemperature` |
| `polytropicEfficiency` | Compressors | `compressor.polytropicEfficiency` |
| `isentropicEfficiency` | Compressors | `compressor.isentropicEfficiency` |
| `temperature` | Streams | `stream.temperature` |
| `flowRate` | Streams | `stream.flowRate` |
| `internalDiameter` | Separators | `separator.internalDiameter` |

*Note: The parameter path system is extensible for additional properties.*

## Result Analysis

### BatchStudyResult

```java
// Summary statistics
int total = result.getTotalCases();
int completed = result.getCompletedCases();
int failed = result.getFailedCases();
Duration runtime = result.getTotalRuntime();

// Find best cases
CaseResult bestByPower = result.getBestCase("power");
CaseResult bestByEmissions = result.getBestCase("emissions");

// Get all results
List<CaseResult> allResults = result.getAllResults();

// Filter successful cases
List<CaseResult> successful = result.getSuccessfulCases();

// Export
result.exportToCSV("results.csv");
result.exportToJSON("results.json");
String json = result.toJson();  // Get as JSON string

// Pareto front analysis (non-dominated solutions)
List<CaseResult> paretoFront = result.getParetoFront("power", "emissions");
```

### CaseResult

```java
CaseResult caseResult = ...;

// Parameter values used
Map<String, Double> params = caseResult.parameters.values;

// Check status
boolean failed = caseResult.failed;
String error = caseResult.errorMessage;

// Objective values
Map<String, Double> objectives = caseResult.objectiveValues;
double power = objectives.get("power");

// Runtime
Duration caseRuntime = caseResult.runtime;
```

## Multi-Objective Analysis

```java
// Define multiple objectives
BatchStudy study = BatchStudy.builder(baseCase)
    .vary("pressure", 20.0, 80.0, 7)
    .addObjective("capex", Objective.MINIMIZE, this::estimateCAPEX)
    .addObjective("opex", Objective.MINIMIZE, this::estimateOPEX)
    .addObjective("emissions", Objective.MINIMIZE, 
        p -> p.getEmissions().getTotalCO2e("ton/yr"))
    .addObjective("recovery", Objective.MAXIMIZE, this::calculateRecovery)
    .build();

BatchStudyResult result = study.run();

// Pareto analysis
List<CaseResult> paretoFront = result.getParetoFront(
    "capex", "emissions"  // Trade-off these objectives
);
```

## Integration Examples

### With Emissions Tracking

```java
.addObjective("co2", Objective.MINIMIZE, process -> {
    EmissionsTracker tracker = new EmissionsTracker(process);
    return tracker.calculateEmissions().getTotalCO2e("ton/yr");
})
```

### With Safety Scenarios

```java
// Run batch study for each safety scenario
for (ProcessSafetyScenario scenario : scenarios) {
    ProcessSystem scenarioCase = baseCase.copy();
    scenario.applyTo(scenarioCase);
    
    BatchStudy study = BatchStudy.builder(scenarioCase)
        .vary("pressure", 20.0, 80.0, 5)
        .addObjective("safety_margin", Objective.MAXIMIZE, 
            this::calculateSafetyMargin)
        .build();
    
    BatchStudyResult result = study.run();
    // Analyze results for this scenario
}
```

## Concept Screening Example

```java
// Screen compressor staging options
for (int stages = 1; stages <= 4; stages++) {
    ProcessSystem concept = createCompressorConcept(stages);
    
    BatchStudy study = BatchStudy.builder(concept)
        .name("Concept-" + stages + "-stages")
        .vary("totalPressureRatio", 3.0, 10.0, 8)
        .addObjective("power", Objective.MINIMIZE, this::getTotalPower)
        .addObjective("capex", Objective.MINIMIZE, this::estimateCAPEX)
        .parallelism(4)
        .build();
    
    BatchStudyResult result = study.run();
    conceptResults.put(stages, result);
}

// Compare concepts
for (var entry : conceptResults.entrySet()) {
    CaseResult best = entry.getValue().getBestCase("power");
    System.out.printf("%d stages: %.0f kW power%n", 
        entry.getKey(), 
        best.objectiveValues.get("power"));
}
```

## Performance Considerations

| Factor | Recommendation |
|--------|----------------|
| Parallelism | Start with CPU cores, adjust based on memory |
| Case Count | Thousands OK, millions need distribution |
| Memory | Each case clones the process system |
| Timeout | Consider case-level timeouts for robustness |

## Best Practices

1. **Start Small**: Test with few cases before large sweeps
2. **Log Progress**: Monitor completion for long studies
3. **Handle Failures**: Decide continue vs stop strategy
4. **Export Results**: Always save before analysis
5. **Version Control**: Track study configurations

## Related Documentation

- [Optimization Package](README.md) - General optimization capabilities
- [Safety Scenario Generation](../safety/scenario-generation.md) - Generate scenarios for batch studies
- [Future Infrastructure Overview](../future-infrastructure.md) - Full infrastructure overview
