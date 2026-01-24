# Batch Studies

> **New to process optimization?** Start with the [Optimization Overview](OPTIMIZATION_OVERVIEW.md) to understand when to use which optimizer.

This document describes the batch study infrastructure for parallel parameter studies and concept screening.

## Related Documentation

| Document | Description |
|----------|-------------|
| [Optimization Overview](OPTIMIZATION_OVERVIEW.md) | When to use which optimizer |
| [Multi-Objective Optimization](multi-objective-optimization.md) | Pareto fronts and trade-offs |
| [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE.md) | ProductionOptimizer examples |

## Overview

Early-phase engineering requires rapid evaluation of many alternatives. The `BatchStudy` class provides:

- **Parameter Sweeps**: Vary design variables systematically
- **Parallel Execution**: Utilize multiple CPU cores
- **Multi-Objective Ranking**: Compare by cost, emissions, efficiency
- **Result Aggregation**: Collect and analyze efficiently

## Table of Contents

- [Usage](#usage)
- [Parameter Variation Methods](#parameter-variation-methods)
- [Supported Parameter Paths](#supported-parameter-paths)
- [Result Analysis](#result-analysis)
- [Multi-Objective Analysis](#multi-objective-analysis)
- [Concept Screening Example](#concept-screening-example)
- [Performance Considerations](#performance-considerations)
- [Best Practices](#best-practices)
- [Python Usage (via JPype)](#python-usage-via-jpype)
  - [Basic Setup](#basic-setup)
  - [Building and Running Batch Study](#building-and-running-batch-study)
  - [Converting to Pandas DataFrame](#converting-to-pandas-dataframe)
  - [Visualizing Results](#visualizing-results)
  - [Concept Screening Example (Python)](#concept-screening-example)

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

---

## Python Usage (via JPype)

BatchStudy is fully accessible from Python using neqsim-python.

### Basic Setup

```python
from neqsim.neqsimpython import jneqsim
import jpype
from jpype import JImplements, JOverride
import pandas as pd
import json

# Import classes
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Compressor = jneqsim.process.equipment.compressor.Compressor
Heater = jneqsim.process.equipment.heatexchanger.Heater
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos

BatchStudy = jneqsim.process.util.optimizer.BatchStudy
Objective = BatchStudy.Objective
```

### Creating a Base Process

```python
# Create fluid
fluid = SystemSrkEos(298.15, 50.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")

# Build base process
base_process = ProcessSystem()

feed = Stream("feed", fluid)
feed.setFlowRate(10000.0, "kg/hr")
feed.setPressure(50.0, "bara")
base_process.add(feed)

heater = Heater("heater", feed)
heater.setOutTemperature(350.0, "K")
base_process.add(heater)

compressor = Compressor("compressor", heater.getOutletStream())
compressor.setOutletPressure(100.0, "bara")
base_process.add(compressor)

base_process.run()
```

### Defining Objective Functions in Python

```python
# Define objective functions using Java interface
@JImplements("java.util.function.ToDoubleFunction")
class PowerObjective:
    @JOverride
    def applyAsDouble(self, proc):
        comp = proc.getUnit("compressor")
        return comp.getPower("kW") if comp else 0.0

@JImplements("java.util.function.ToDoubleFunction")
class ThroughputObjective:
    @JOverride
    def applyAsDouble(self, proc):
        return proc.getUnit("feed").getFlowRate("kg/hr")

@JImplements("java.util.function.ToDoubleFunction")
class EfficiencyObjective:
    @JOverride
    def applyAsDouble(self, proc):
        comp = proc.getUnit("compressor")
        return comp.getPolytropicEfficiency() * 100 if comp else 0.0
```

### Building and Running Batch Study

```python
# Build batch study using builder pattern
study = BatchStudy.builder(base_process) \
    .name("HeaterCompressorStudy") \
    .vary("heater.outletTemperature", 300.0, 400.0, 5) \
    .vary("compressor.outletPressure", 80.0, 120.0, 5) \
    .addObjective("power", Objective.MINIMIZE, PowerObjective()) \
    .addObjective("throughput", Objective.MAXIMIZE, ThroughputObjective()) \
    .parallelism(4) \
    .stopOnFailure(False) \
    .build()

# Run the study
result = study.run()

# Print summary
print(f"Total cases: {result.getTotalCases()}")
print(f"Completed: {result.getCompletedCases()}")
print(f"Failed: {result.getFailedCases()}")
print(f"Runtime: {result.getTotalRuntime()}")
```

### Analyzing Results

```python
# Get best cases
best_power = result.getBestCase("power")
best_throughput = result.getBestCase("throughput")

print(f"\nBest by power: {best_power.objectiveValues.get('power'):.1f} kW")
print(f"Best by throughput: {best_throughput.objectiveValues.get('throughput'):.0f} kg/hr")

# Get all successful results
successful = result.getSuccessfulCases()
print(f"\nSuccessful cases: {len(list(successful))}")

# Get Pareto front for two objectives
pareto_front = result.getParetoFront("power", "throughput")
print(f"Pareto front size: {len(list(pareto_front))}")
```

### Exporting Results

```python
# Export to CSV
result.exportToCSV("batch_results.csv")

# Export to JSON
result.exportToJSON("batch_results.json")

# Get JSON string directly
json_str = result.toJson()
data = json.loads(json_str)
```

### Converting to Pandas DataFrame

```python
import pandas as pd

# Build DataFrame from results
rows = []
for case_result in result.getAllResults():
    row = {
        'failed': case_result.failed,
        'error': case_result.errorMessage if case_result.failed else None
    }
    
    # Add parameters
    for name, value in case_result.parameters.values.items():
        row[f'param_{name}'] = value
    
    # Add objectives (if successful)
    if not case_result.failed:
        for name, value in case_result.objectiveValues.items():
            row[f'obj_{name}'] = value
    
    rows.append(row)

df = pd.DataFrame(rows)
print(df.head())

# Filter successful cases
df_success = df[~df['failed']]
print(f"\nSuccessful cases: {len(df_success)}")

# Find optimal
idx_min_power = df_success['obj_power'].idxmin()
print(f"\nMinimum power case:")
print(df_success.loc[idx_min_power])
```

### Visualizing Results

```python
import matplotlib.pyplot as plt
import numpy as np

# Create scatter plot of parameter study
fig, axes = plt.subplots(1, 2, figsize=(14, 5))

# Plot 1: Power vs parameters
ax1 = axes[0]
if 'param_heater.outletTemperature' in df_success.columns:
    scatter = ax1.scatter(
        df_success['param_heater.outletTemperature'],
        df_success['param_compressor.outletPressure'],
        c=df_success['obj_power'],
        cmap='viridis',
        s=100
    )
    plt.colorbar(scatter, ax=ax1, label='Power (kW)')
    ax1.set_xlabel('Heater Outlet Temperature (K)')
    ax1.set_ylabel('Compressor Outlet Pressure (bara)')
    ax1.set_title('Power Consumption Heat Map')

# Plot 2: Pareto front
ax2 = axes[1]
ax2.scatter(df_success['obj_power'], df_success['obj_throughput'], 
            s=100, alpha=0.6, label='All cases')

# Highlight Pareto front
pareto_rows = []
for case in result.getParetoFront("power", "throughput"):
    pareto_rows.append({
        'power': case.objectiveValues.get('power'),
        'throughput': case.objectiveValues.get('throughput')
    })
df_pareto = pd.DataFrame(pareto_rows)
if not df_pareto.empty:
    ax2.scatter(df_pareto['power'], df_pareto['throughput'],
                s=150, c='red', marker='*', label='Pareto front')

ax2.set_xlabel('Power (kW)')
ax2.set_ylabel('Throughput (kg/hr)')
ax2.set_title('Pareto Front: Power vs Throughput')
ax2.legend()
ax2.grid(True, alpha=0.3)

plt.tight_layout()
plt.savefig('batch_study_results.png', dpi=150)
plt.show()
```

### Using Explicit Parameter Values

```python
# Vary with explicit values instead of range
study = BatchStudy.builder(base_process) \
    .name("ExplicitValuesStudy") \
    .vary("compressor.outletPressure", 80.0, 100.0, 120.0) \
    .vary("heater.outletTemperature", 320.0, 350.0, 380.0) \
    .addObjective("power", Objective.MINIMIZE, PowerObjective()) \
    .parallelism(2) \
    .build()

result = study.run()
print(f"Evaluated {result.getTotalCases()} combinations")
```

### Concept Screening Example

```python
def create_staged_compressor(num_stages, fluid):
    """Create a compressor train with specified stages"""
    process = ProcessSystem()
    
    feed = Stream("feed", fluid)
    feed.setFlowRate(10000.0, "kg/hr")
    feed.setPressure(30.0, "bara")
    process.add(feed)
    
    inlet_stream = feed
    total_ratio = 5.0  # Total pressure ratio
    stage_ratio = total_ratio ** (1.0 / num_stages)
    
    for i in range(num_stages):
        comp = Compressor(f"stage{i+1}", inlet_stream)
        outlet_p = 30.0 * (stage_ratio ** (i + 1))
        comp.setOutletPressure(outlet_p, "bara")
        comp.setPolytropicEfficiency(0.78)
        process.add(comp)
        
        if i < num_stages - 1:  # Add intercooler
            cooler = jneqsim.process.equipment.heatexchanger.Cooler(
                f"cooler{i+1}", comp.getOutletStream())
            cooler.setOutTemperature(308.15)  # 35°C
            process.add(cooler)
            inlet_stream = cooler.getOutletStream()
        else:
            inlet_stream = comp.getOutletStream()
    
    process.run()
    return process

# Screen 1, 2, 3, 4 stage options
concept_results = {}
for stages in range(1, 5):
    concept = create_staged_compressor(stages, fluid.clone())
    
    @JImplements("java.util.function.ToDoubleFunction")
    class TotalPowerObj:
        @JOverride
        def applyAsDouble(self, proc):
            total = 0.0
            for unit in proc.getUnitOperations():
                if unit.getClass().getSimpleName() == "Compressor":
                    total += unit.getPower("kW")
            return total
    
    study = BatchStudy.builder(concept) \
        .name(f"Concept-{stages}-stages") \
        .vary("stage1.outletPressure", 40.0, 60.0, 3) \
        .addObjective("totalPower", Objective.MINIMIZE, TotalPowerObj()) \
        .parallelism(2) \
        .build()
    
    result = study.run()
    concept_results[stages] = result
    
    best = result.getBestCase("totalPower")
    print(f"{stages} stages: Best power = {best.objectiveValues.get('totalPower'):.1f} kW")
```

---

## Related Documentation

- [Optimization Package](README.md) - General optimization capabilities
- [Multi-Objective Optimization](multi-objective-optimization.md) - Pareto fronts
- [Python Optimization Tutorial](../../examples/NeqSim_Python_Optimization.md) - SciPy integration
- [Safety Scenario Generation](../safety/scenario-generation.md) - Generate scenarios for batch studies
- [Future Infrastructure Overview](../future-infrastructure.md) - Full infrastructure overview
