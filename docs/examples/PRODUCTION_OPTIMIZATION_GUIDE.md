# Production Optimization Guide

This guide provides comprehensive examples for setting up and running production optimization simulations in NeqSim, covering both Java and Python implementations.

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [DESIGN_FRAMEWORK.md](../process/DESIGN_FRAMEWORK.md) | **AutoSizeable interface, ProcessTemplates, and DesignOptimizer** |
| [OPTIMIZATION_IMPROVEMENT_PROPOSAL.md](../process/OPTIMIZATION_IMPROVEMENT_PROPOSAL.md) | Optimization framework roadmap and implementation status |
| [CAPACITY_CONSTRAINT_FRAMEWORK.md](../process/CAPACITY_CONSTRAINT_FRAMEWORK.md) | Multi-constraint equipment and bottleneck detection |

---

## Overview

NeqSim provides a powerful production optimization framework that combines:

| Component | Description |
|-----------|-------------|
| **ProductionOptimizer** | Core optimization engine with multiple search algorithms |
| **CapacityConstrainedEquipment** | Multi-constraint interface for equipment limits |
| **ProcessSystem.getBottleneck()** | Unified bottleneck detection (single & multi-constraint) |
| **ProcessSystem.findBottleneck()** | Detailed constraint analysis with remediation hints |

### Key Features

- **Multiple search algorithms**: Binary feasibility, Golden-section, Nelder-Mead, Particle-swarm
- **Hard & soft constraints**: Enforce limits or penalize violations
- **Equipment-specific utilization limits**: Configure per equipment or type
- **Scenario comparison**: Run multiple what-if scenarios
- **JSON reporting**: Machine-readable optimization results

---

## Quick Start (Java)

### Basic Production Rate Optimization

```java
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer;
import neqsim.process.util.optimizer.ProductionOptimizer.*;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

// 1. Create fluid system
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-butane", 0.02);
fluid.setMixingRule("classic");

// 2. Build process
ProcessSystem process = new ProcessSystem();

Stream feed = new Stream("Well Feed", fluid);
feed.setFlowRate(5000.0, "kg/hr");
process.add(feed);

Separator separator = new Separator("HP Separator");
separator.setInletStream(feed);
separator.initMechanicalDesign();
separator.getMechanicalDesign().setMaxDesignGassVolFlow(2000.0); // Sm3/hr limit
process.add(separator);

Compressor compressor = new Compressor("Gas Compressor");
compressor.setInletStream(separator.getGasOutStream());
compressor.setOutletPressure(100.0, "bara");
compressor.setMaximumSpeed(11000.0);  // RPM limit
compressor.setMaximumPower(500.0);    // kW limit
process.add(compressor);

process.run();

// 3. Configure optimization
OptimizationConfig config = new OptimizationConfig(1000.0, 20000.0)  // kg/hr range
    .rateUnit("kg/hr")
    .tolerance(10.0)
    .maxIterations(20)
    .defaultUtilizationLimit(0.95)  // 95% max utilization
    .utilizationLimitForType(Compressor.class, 0.90)  // 90% for compressors
    .searchMode(SearchMode.BINARY_FEASIBILITY);

// 4. Run optimization
OptimizationResult result = ProductionOptimizer.optimize(process, feed, config);

// 5. Report results
System.out.println("Optimal production rate: " + result.getOptimalRate() + " " + result.getRateUnit());
System.out.println("Bottleneck: " + result.getBottleneck().getName());
System.out.println("Bottleneck utilization: " + (result.getBottleneckUtilization() * 100) + "%");
System.out.println("Feasible: " + result.isFeasible());
```

---

## Equipment Constraints and Active Bottlenecks

### Which Equipment Can Restrict Production?

**Any equipment implementing `CapacityConstrainedEquipment` can become the bottleneck**, not just compressors. The optimizer checks ALL equipment constraints and reports the one limiting production.

| Equipment | Implemented Constraints | Typical Bottleneck Scenarios |
|-----------|------------------------|------------------------------|
| **Separator** | `gasLoadFactor`, `liquidResidenceTime` | High gas rates, high liquid rates |
| **Compressor** | `speed`, `power`, `surgeMargin`, `stonewallMargin` | High compression duty, variable conditions |
| **Pump** | `npshMargin`, `power`, `flowRate` | High liquid rates, cavitation risk |
| **ThrottlingValve** | `valveOpening`, `cvUtilization` | Large pressure drops, high flows |
| **Pipeline** | `velocity`, `pressureDrop`, `FIV_LOF`, `FIV_FRMS` | Long pipelines, high velocities |
| **Heater/Cooler** | `duty`, `outletTemperature` | High thermal loads |

### What is an "Active Constraint"?

An **active constraint** is the specific equipment limit that prevents increasing production further. It's the "binding" constraint at the current operating point.

```java
// After optimization, identify the active constraint
ProcessEquipmentInterface bottleneck = result.getBottleneck();

if (bottleneck instanceof CapacityConstrainedEquipment) {
    CapacityConstrainedEquipment constrained = (CapacityConstrainedEquipment) bottleneck;
    CapacityConstraint active = constrained.getBottleneckConstraint();
    
    System.out.println("=== ACTIVE CONSTRAINT ===");
    System.out.println("Equipment: " + bottleneck.getName());
    System.out.println("Constraint: " + active.getName());
    System.out.println("Current value: " + active.getCurrentValue() + " " + active.getUnit());
    System.out.println("Design limit: " + active.getDesignValue() + " " + active.getUnit());
    System.out.println("Utilization: " + active.getUtilizationPercent() + "%");
    System.out.println("Type: " + active.getConstraintType());  // HARD, SOFT, or DESIGN
}
```

**Example scenarios where different equipment is the bottleneck:**

| Scenario | Bottleneck | Active Constraint | Reason |
|----------|------------|-------------------|--------|
| High GOR well | Separator | gasLoadFactor | K-factor limit exceeded |
| Low reservoir pressure | Compressor | power | Compressor at max driver power |
| Long export line | Pipeline | velocity | Erosional velocity limit |
| High water cut | Pump | npshMargin | Insufficient NPSH available |
| Restricted outlet | Valve | valveOpening | Valve fully open (90%) |
| Cold ambient | Heater | duty | Maximum heating capacity |

### How Constraints Are Established

Constraints come from three sources:

#### 1. Auto-Sizing (Recommended)

When you call `autoSize()`, constraints are automatically created based on design calculations:

```java
// Separator - sets gasLoadFactor constraint from K-factor sizing
Separator sep = new Separator("HP-Sep", feed);
sep.autoSize(1.2);  // Creates constraint: gasLoadFactor = design K-factor

// Compressor - sets speed, power, surge constraints + generates curves
Compressor comp = new Compressor("Export", gasStream);
comp.setOutletPressure(100.0);
comp.autoSize(1.2);  // Creates constraints: speed, power, surgeMargin
                     // Also generates compressor curves and sets solveSpeed=true

// Valve - sets valveOpening and Cv constraints
ThrottlingValve valve = new ThrottlingValve("HP-Valve", stream);
valve.setOutletPressure(30.0);
valve.autoSize(1.2);  // Creates constraints: valveOpening, cvUtilization
```

#### 2. Manual Configuration

Set limits directly on equipment:

```java
// Compressor limits
Compressor comp = new Compressor("K-100", stream);
comp.setMaximumSpeed(11000.0);    // Creates HARD speed constraint
comp.setMaximumPower(2000.0);     // Creates HARD power constraint
comp.setSurgeMargin(10.0);        // Creates SOFT surge margin constraint

// Separator limits
Separator sep = new Separator("V-100", feed);
sep.setDesignGasLoadFactor(0.08);  // Creates DESIGN gasLoadFactor constraint

// Pipeline limits
Pipeline pipe = new PipeBeggsAndBrills("L-100", stream);
pipe.setMaxLOF(1.0);               // Creates SOFT FIV_LOF constraint
pipe.setMaxFRMS(100000.0);         // Creates SOFT FIV_FRMS constraint
```

#### 3. Mechanical Design Integration

When `initMechanicalDesign()` is called, constraints use design values:

```java
Separator sep = new Separator("HP-Sep", feed);
sep.initMechanicalDesign();
sep.getMechanicalDesign().setMaxDesignGassVolFlow(5000.0);  // m³/hr
sep.getMechanicalDesign().setMaxDesignPressure(100.0);      // bara
// These values feed into constraint limits
```

### Constraint Types and Their Behavior

| Type | Meaning | Optimization Behavior | Example |
|------|---------|----------------------|---------|
| **HARD** | Physical or safety limit - cannot exceed | Optimization stops before exceeding | Compressor trip speed, vessel MAWP |
| **SOFT** | Operational limit - penalty for exceeding | Can exceed with warning/penalty | Efficiency degradation zone |
| **DESIGN** | Normal operating envelope | Target for optimal operation | Design K-factor, rated capacity |

### Full Process Example: Finding Active Constraint

```java
// Build a realistic process
ProcessSystem process = new ProcessSystem();

// Feed from reservoir
Stream wellFeed = new Stream("Well Feed", reservoirFluid);
wellFeed.setFlowRate(20000.0, "kg/hr");

// Three-phase separation
ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP Separator", wellFeed);
hpSep.autoSize(1.2);  // Constraints: gasLoadFactor, liquidResidenceTime

// Gas compression
Compressor gasComp = new Compressor("Gas Compressor", hpSep.getGasOutStream());
gasComp.setOutletPressure(100.0);
gasComp.autoSize(1.2);  // Constraints: speed, power, surgeMargin + curves

// Export pipeline
PipeBeggsAndBrills exportPipe = new PipeBeggsAndBrills("Export Pipeline", gasComp.getOutletStream());
exportPipe.setLength(50000.0);  // 50 km
exportPipe.setDiameter(0.4);    // 16 inch
exportPipe.autoSize(1.2);       // Constraints: velocity, pressureDrop, FIV_LOF, FIV_FRMS

// Liquid pump
Pump oilPump = new Pump("Oil Pump", hpSep.getOilOutStream());
oilPump.setOutletPressure(15.0);
// Pump has: npshMargin, power, flowRate constraints

process.add(wellFeed);
process.add(hpSep);
process.add(gasComp);
process.add(exportPipe);
process.add(oilPump);
process.run();

// Run optimization
OptimizationConfig config = new OptimizationConfig(5000.0, 50000.0)
    .rateUnit("kg/hr")
    .defaultUtilizationLimit(0.95);

OptimizationResult result = ProductionOptimizer.optimize(process, wellFeed, config);

// Report ALL equipment constraints and identify the active one
System.out.println("=== CONSTRAINT STATUS FOR ALL EQUIPMENT ===\n");

for (CapacityConstrainedEquipment equip : process.getConstrainedEquipment()) {
    ProcessEquipmentInterface unit = (ProcessEquipmentInterface) equip;
    boolean isBottleneck = unit.equals(result.getBottleneck());
    
    System.out.println(unit.getName() + (isBottleneck ? " ⭐ BOTTLENECK" : "") + ":");
    
    CapacityConstraint limitingConstraint = equip.getBottleneckConstraint();
    
    for (CapacityConstraint c : equip.getCapacityConstraints().values()) {
        boolean isActive = c.equals(limitingConstraint) && isBottleneck;
        String marker = isActive ? " ◀ ACTIVE" : "";
        String status = c.isViolated() ? "⚠️" : c.isNearLimit() ? "⚡" : "✓";
        
        System.out.printf("  %s %-18s: %7.2f / %7.2f %-6s (%5.1f%%)%s%n",
            status,
            c.getName(),
            c.getCurrentValue(),
            c.getDesignValue(),
            c.getUnit(),
            c.getUtilizationPercent(),
            marker);
    }
    System.out.println();
}

System.out.println("=== OPTIMIZATION RESULT ===");
System.out.println("Optimal production rate: " + result.getOptimalRate() + " kg/hr");
System.out.println("Bottleneck equipment: " + result.getBottleneck().getName());
System.out.println("Feasible: " + result.isFeasible());
```

**Example output:**

```
=== CONSTRAINT STATUS FOR ALL EQUIPMENT ===

HP Separator:
  ✓ gasLoadFactor     :    0.07 /    0.08 m/s    (87.5%)
  ✓ liquidResidence   :    2.80 /    3.00 min    (93.3%)

Gas Compressor ⭐ BOTTLENECK:
  ⚡ speed             : 9800.00 / 10000.00 RPM    (98.0%) ◀ ACTIVE
  ✓ power             : 1650.00 /  2000.00 kW     (82.5%)
  ✓ surgeMargin       :   12.00 /   10.00 %      (83.3%)

Export Pipeline:
  ✓ velocity          :   12.50 /   15.00 m/s    (83.3%)
  ✓ pressureDrop      :    3.80 /    5.00 bara   (76.0%)
  ✓ FIV_LOF           :    0.28 /    1.00 -      (28.0%)

Oil Pump:
  ✓ npshMargin        :    2.50 /    0.60 m      (24.0%)
  ✓ power             :   85.00 /  150.00 kW     (56.7%)

=== OPTIMIZATION RESULT ===
Optimal production rate: 28500 kg/hr
Bottleneck equipment: Gas Compressor
Feasible: true
```

In this example, the **Gas Compressor** is the bottleneck with **speed** as the active constraint at 98% utilization.

---

## Capacity Constraint Framework

### Setting Up Multi-Constraint Equipment

Equipment implementing `CapacityConstrainedEquipment` can have multiple constraints:

```java
// Separator with gas load factor constraint
Separator separator = new Separator("HP Separator");
separator.setDesignGasLoadFactor(0.15);  // K-factor limit

// Access constraints
for (CapacityConstraint constraint : separator.getCapacityConstraints()) {
    System.out.println("Constraint: " + constraint.getName());
    System.out.println("  Type: " + constraint.getConstraintType());
    System.out.println("  Current: " + constraint.getCurrentValue());
    System.out.println("  Limit: " + constraint.getLimitValue());
    System.out.println("  Utilization: " + (constraint.getUtilization() * 100) + "%");
    System.out.println("  Is Violated: " + constraint.isViolated());
}

// Check overall utilization
double maxUtil = separator.getMaxUtilization();
CapacityConstraint limiting = separator.getLimitingConstraint();
System.out.println("Limiting constraint: " + limiting.getName() + " at " + (maxUtil * 100) + "%");
```

### Compressor with Multiple Constraints

```java
Compressor compressor = new Compressor("Export Compressor");
compressor.setMaximumSpeed(11000.0);      // HARD constraint - RPM
compressor.setMaximumPower(2000.0);       // HARD constraint - kW
compressor.setSurgeMargin(10.0);          // SOFT constraint - %

// After running, check all constraints
process.run();

for (CapacityConstraint c : compressor.getCapacityConstraints()) {
    String status = c.isViolated() ? "⚠️ EXCEEDED" : "✓ OK";
    System.out.printf("%s: %.1f / %.1f (%.0f%%) %s%n",
        c.getName(), c.getCurrentValue(), c.getLimitValue(),
        c.getUtilization() * 100, status);
}
```

---

## Advanced Optimization Configurations

### With Custom Objectives and Constraints

```java
import java.util.Arrays;
import java.util.List;

// Define objectives
List<OptimizationObjective> objectives = Arrays.asList(
    new OptimizationObjective("Production", 
        ps -> ((Stream) ps.getUnit("Well Feed")).getFlowRate("kg/hr"), 
        1.0, ObjectiveType.MAXIMIZE),
    new OptimizationObjective("Efficiency",
        ps -> ((Compressor) ps.getUnit("Gas Compressor")).getPolytropicEfficiency(),
        0.5, ObjectiveType.MAXIMIZE)
);

// Define constraints
List<OptimizationConstraint> constraints = Arrays.asList(
    OptimizationConstraint.lessThan("Max Export Pressure",
        ps -> ((Stream) ps.getUnit("Export Gas")).getPressure("bara"),
        105.0, ConstraintSeverity.HARD, 10.0, "Export pipeline limit"),
    OptimizationConstraint.greaterThan("Min Separator Temp",
        ps -> ((Separator) ps.getUnit("HP Separator")).getGasOutStream().getTemperature("C"),
        -10.0, ConstraintSeverity.SOFT, 5.0, "Hydrate prevention")
);

// Run with objectives and constraints
OptimizationResult result = ProductionOptimizer.optimize(
    process, feed, config, objectives, constraints);

// Check constraint statuses
for (ConstraintStatus status : result.getConstraintStatuses()) {
    System.out.printf("%s: margin=%.2f, violated=%s%n",
        status.getName(), status.getMargin(), status.violated());
}
```

### Scenario Comparison

```java
import java.util.Arrays;
import java.util.List;

// Define scenarios
List<ScenarioRequest> scenarios = Arrays.asList(
    new ScenarioRequest("Summer", process.copy(), feed, 
        new OptimizationConfig(1000.0, 20000.0).rateUnit("kg/hr"),
        objectives, constraints),
    new ScenarioRequest("Winter", processWinter.copy(), feedWinter,
        new OptimizationConfig(1000.0, 25000.0).rateUnit("kg/hr"),
        objectives, constraints)
);

// Define KPIs for comparison
List<ScenarioKpi> kpis = Arrays.asList(
    new ScenarioKpi("Max Rate", "kg/hr", r -> r.getOptimalRate()),
    new ScenarioKpi("Bottleneck Util", "%", r -> r.getBottleneckUtilization() * 100)
);

// Run comparison
ScenarioComparisonResult comparison = ProductionOptimizer.compareScenarios(scenarios, kpis);

// Print results
for (ScenarioResult sr : comparison.getResults()) {
    System.out.printf("Scenario '%s': %.0f kg/hr (bottleneck: %s)%n",
        sr.getName(), sr.getResult().getOptimalRate(), 
        sr.getResult().getBottleneck().getName());
}
```

### Search Algorithms

```java
// Binary search (default) - fast for monotonic responses
config.searchMode(SearchMode.BINARY_FEASIBILITY);

// Golden-section - handles non-monotonic responses
config.searchMode(SearchMode.GOLDEN_SECTION_SCORE);

// Nelder-Mead simplex - multi-dimensional optimization
config.searchMode(SearchMode.NELDER_MEAD_SCORE);

// Particle-swarm - global optimization
config.searchMode(SearchMode.PARTICLE_SWARM_SCORE)
    .swarmSize(8)
    .inertiaWeight(0.6)
    .cognitiveWeight(1.2)
    .socialWeight(1.2);
```

---

## ProcessSystem Bottleneck Analysis

### Basic Bottleneck Detection

```java
// Run the process
process.run();

// Get bottleneck (unified - checks both single and multi-constraint)
ProcessEquipmentInterface bottleneck = process.getBottleneck();
System.out.println("Bottleneck: " + bottleneck.getName());
System.out.printf("Utilization: %.1f%%%n", process.getBottleneckUtilization() * 100);
```

### Detailed Constraint Analysis

```java
import neqsim.process.equipment.capacity.BottleneckResult;
import neqsim.process.equipment.capacity.CapacityConstraint;

// Get detailed bottleneck result
BottleneckResult bottleneckResult = process.findBottleneck();

System.out.println("Bottleneck Equipment: " + bottleneckResult.getEquipmentName());
System.out.println("Limiting Constraint: " + bottleneckResult.getLimitingConstraintName());
System.out.printf("Utilization: %.1f%%%n", bottleneckResult.getUtilization() * 100);

// Iterate all constraints on bottleneck
for (CapacityConstraint c : bottleneckResult.getAllConstraints()) {
    System.out.printf("  - %s: %.1f%% (%s)%n", 
        c.getName(), c.getUtilization() * 100, c.getConstraintType());
}
```

### Capacity Utilization Summary

```java
import java.util.Map;

// Get all equipment utilizations
Map<String, Double> utilizations = process.getCapacityUtilizationSummary();

System.out.println("=== Capacity Utilization Summary ===");
for (Map.Entry<String, Double> entry : utilizations.entrySet()) {
    String bar = createProgressBar(entry.getValue());
    System.out.printf("%s: %s %.0f%%%n", entry.getKey(), bar, entry.getValue() * 100);
}

// Check for equipment near limits
double threshold = 0.85; // 85%
for (ProcessEquipmentInterface equip : process.getEquipmentNearCapacityLimit(threshold)) {
    System.out.println("⚠️ Near limit: " + equip.getName());
}

// Check for any overloaded equipment
if (process.isAnyEquipmentOverloaded()) {
    System.out.println("❌ Equipment overloaded!");
}
if (process.isAnyHardLimitExceeded()) {
    System.out.println("🛑 HARD limit exceeded - system unsafe!");
}
```

---

## Python Examples (neqsim-python)

NeqSim Python uses JPype for direct Java access. All Java classes are available through the `neqsim` package.

### Basic Setup

```python
# Install neqsim-python: pip install neqsim

import neqsim
from neqsim.thermo import fluid
from neqsim.process import stream, separator, compressor, processSystem
```

### Production Optimization in Python

```python
import jpype
import jpype.imports
from jpype.types import *

# Ensure JVM is started (neqsim does this automatically)
import neqsim

# Import Java classes directly
from neqsim.thermo.system import SystemSrkEos
from neqsim.process.equipment.stream import Stream
from neqsim.process.equipment.separator import Separator  
from neqsim.process.equipment.compressor import Compressor
from neqsim.process.processmodel import ProcessSystem
from neqsim.process.util.optimizer import ProductionOptimizer

# Create fluid
fluid = SystemSrkEos(298.15, 50.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.08)
fluid.addComponent("propane", 0.05)
fluid.addComponent("n-butane", 0.02)
fluid.setMixingRule("classic")

# Build process
process = ProcessSystem()

feed = Stream("Well Feed", fluid)
feed.setFlowRate(5000.0, "kg/hr")
process.add(feed)

sep = Separator("HP Separator")
sep.setInletStream(feed)
process.add(sep)

comp = Compressor("Gas Compressor")
comp.setInletStream(sep.getGasOutStream())
comp.setOutletPressure(100.0, "bara")
comp.setMaximumSpeed(11000.0)
comp.setMaximumPower(500.0)
process.add(comp)

process.run()

# Configure optimization
OptConfig = ProductionOptimizer.OptimizationConfig
SearchMode = ProductionOptimizer.SearchMode

config = OptConfig(1000.0, 20000.0) \
    .rateUnit("kg/hr") \
    .tolerance(10.0) \
    .maxIterations(20) \
    .defaultUtilizationLimit(0.95) \
    .searchMode(SearchMode.BINARY_FEASIBILITY)

# Run optimization
result = ProductionOptimizer.optimize(process, feed, config)

# Print results
print(f"Optimal rate: {result.getOptimalRate():.0f} {result.getRateUnit()}")
print(f"Bottleneck: {result.getBottleneck().getName()}")
print(f"Utilization: {result.getBottleneckUtilization() * 100:.1f}%")
print(f"Feasible: {result.isFeasible()}")
```

### Multi-Constraint Analysis in Python

```python
from neqsim.process.equipment.capacity import BottleneckResult, CapacityConstraint

# Get detailed bottleneck
bottleneck_result = process.findBottleneck()

print(f"Bottleneck: {bottleneck_result.getEquipmentName()}")
print(f"Limiting: {bottleneck_result.getLimitingConstraintName()}")
print(f"Utilization: {bottleneck_result.getUtilization() * 100:.1f}%")

# Check all constraints
for constraint in bottleneck_result.getAllConstraints():
    status = "⚠️ VIOLATED" if constraint.isViolated() else "✓ OK"
    print(f"  {constraint.getName()}: {constraint.getUtilization()*100:.0f}% {status}")
```

### Capacity Summary in Python

```python
# Get utilization summary
utilizations = process.getCapacityUtilizationSummary()

print("=== Capacity Utilization ===")
for name, util in utilizations.items():
    bar = "█" * int(util * 20) + "░" * (20 - int(util * 20))
    print(f"{name}: [{bar}] {util*100:.0f}%")

# Check near-limit equipment
threshold = 0.85
near_limit = process.getEquipmentNearCapacityLimit(threshold)
for equip in near_limit:
    print(f"⚠️ Near limit: {equip.getName()}")
```

### Scenario Comparison in Python

```python
from java.util import Arrays, ArrayList

# Create scenarios
ScenarioRequest = ProductionOptimizer.ScenarioRequest
ScenarioKpi = ProductionOptimizer.ScenarioKpi

scenarios = ArrayList()

# Scenario 1: Base case
config1 = OptConfig(1000.0, 20000.0).rateUnit("kg/hr")
scenarios.add(ScenarioRequest("Base Case", process, feed, config1, None, None))

# Scenario 2: High pressure export
process2 = process.copy()
comp2 = process2.getUnit("Gas Compressor")
comp2.setOutletPressure(120.0, "bara")
config2 = OptConfig(1000.0, 18000.0).rateUnit("kg/hr")
feed2 = process2.getUnit("Well Feed")
scenarios.add(ScenarioRequest("High Pressure", process2, feed2, config2, None, None))

# Define KPIs
kpis = ArrayList()
kpis.add(ScenarioKpi("Max Rate", "kg/hr", lambda r: r.getOptimalRate()))
kpis.add(ScenarioKpi("Bottleneck Util", "%", lambda r: r.getBottleneckUtilization() * 100))

# Compare
comparison = ProductionOptimizer.compareScenarios(scenarios, kpis)

for sr in comparison.getResults():
    print(f"{sr.getName()}: {sr.getResult().getOptimalRate():.0f} kg/hr")
```

---

## Integration with External Systems

### JSON Export for Dashboards

```java
// Get optimization summary as structured data
OptimizationSummary summary = ProductionOptimizer.optimizeSummary(process, feed, config);

// Convert to JSON using Gson
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

Gson gson = new GsonBuilder().setPrettyPrinting().create();
String json = gson.toJson(summary);
System.out.println(json);
```

### Iteration History for Plotting

```java
// Get full result with history
OptimizationResult result = ProductionOptimizer.optimize(process, feed, config);

// Export iteration history for plotting
List<IterationRecord> history = result.getIterationHistory();

System.out.println("Rate,Bottleneck,Utilization,Feasible,Score");
for (IterationRecord record : history) {
    System.out.printf("%.1f,%s,%.3f,%s,%.4f%n",
        record.getRate(),
        record.getBottleneckName(),
        record.getBottleneckUtilization(),
        record.isFeasible(),
        record.getScore());
}
```

### Real-Time Optimization Loop

```python
import time

# Continuous optimization loop
while True:
    # Update feed conditions from real-time data
    feed.setTemperature(get_realtime_temp(), "C")
    feed.setPressure(get_realtime_pressure(), "bara")
    
    # Re-run process
    process.run()
    
    # Check constraints
    if process.isAnyHardLimitExceeded():
        print("🛑 ALARM: Hard limit exceeded!")
        trigger_alarm()
    
    # Get current bottleneck
    bottleneck = process.getBottleneck()
    util = process.getBottleneckUtilization()
    
    # Log to historian
    log_to_historian({
        "bottleneck": bottleneck.getName(),
        "utilization": util,
        "timestamp": time.time()
    })
    
    # Run periodic optimization
    if should_optimize():
        result = ProductionOptimizer.optimize(process, feed, config)
        recommend_setpoint(result.getOptimalRate())
    
    time.sleep(60)  # 1-minute interval
```

---

## Equipment Support Matrix

| Equipment | getCapacityDuty() | getCapacityMax() | CapacityConstrainedEquipment |
|-----------|-------------------|------------------|------------------------------|
| Separator | ✅ Liquid level fraction | ✅ 1.0 (100% fill) | ✅ Gas load factor constraint |
| Compressor | ✅ Power (kW) | ✅ Max power | ✅ Speed, power, surge, stonewall margin |
| Pump | ✅ Power (kW) | ✅ Max power | ✅ Capacity constraints |
| Heater/Cooler | ✅ Duty (kW) | ✅ Max duty | ✅ Duty constraint |
| HeatExchanger | ✅ Duty (kW) | ✅ Max duty | ✅ Duty constraint |
| Valve | ✅ Opening (%) | ✅ Max opening | ✅ (only if max < 100% set) |
| Pipe | ✅ Superficial velocity | ✅ Max velocity | ✅ Velocity constraint |
| DistillationColumn | ✅ Fs factor | ✅ Max Fs factor | ❌ (planned) |
| Manifold | ✅ Velocity | ✅ Erosional velocity | ✅ FIV analysis |

**Notes:**
- **Separator**: Uses liquid level fraction for optimization (0.7 = 70% filled). Gas load factor (K-factor) available via `getGasLoadFactor()` for sizing.
- **Valve**: Only tracked if max opening < 100% is explicitly set. A fully open valve is normal, not overutilized.
- **Compressor**: Min speed constraint correctly handles utilization (below minimum speed = violation).

---

## Best Practices

### 1. Set Realistic Equipment Limits

```java
// Always set mechanical design limits
separator.initMechanicalDesign();
separator.getMechanicalDesign().setMaxDesignGassVolFlow(2000.0);

// Or use K-factor approach
separator.setDesignGasLoadFactor(0.15);

// Set compressor limits
compressor.setMaximumSpeed(11000.0);
compressor.setMaximumPower(500.0);
```

### 2. Use Appropriate Utilization Margins

```java
// Conservative (debottlenecking studies)
config.defaultUtilizationLimit(0.80);

// Normal operations
config.defaultUtilizationLimit(0.95);

// Stress testing
config.defaultUtilizationLimit(1.05);  // Allow some overage with penalties
```

### 3. Configure Equipment-Specific Limits

```java
config.utilizationLimitForName("Critical Compressor", 0.85)
      .utilizationLimitForType(Separator.class, 0.90)
      .utilizationLimitForType(Compressor.class, 0.88);
```

### 4. Use Hard Constraints for Safety

### 5. Compressor Curves with Optimization

When using compressor performance curves with the optimizer, follow this setup sequence:

```java
// 1. Create and run compressor to establish design point
Compressor compressor = new Compressor("Export Compressor", gasScrubber.getGasOutStream());
compressor.setOutletPressure(80.0, "bara");
compressor.setPolytropicEfficiency(0.78);
compressor.setUsePolytropicCalc(true);
process.add(compressor);
process.run();

// 2. Generate compressor chart at design point
CompressorChartGenerator chartGen = new CompressorChartGenerator(compressor);
chartGen.setChartType("interpolate and extrapolate");
CompressorChartInterface chart = chartGen.generateCompressorChart("normal curves", 5);
compressor.setCompressorChart(chart);
compressor.getCompressorChart().setUseCompressorChart(true);

// 3. IMPORTANT: Set max speed higher than operating speed
// This defines the available headroom for optimization
double designSpeed = compressor.getSpeed();
compressor.setMaximumSpeed(designSpeed * 1.15);  // 15% speed margin

// 4. Re-run process and reinitialize constraints
process.run();
compressor.reinitializeCapacityConstraints();  // Updates constraints with curve limits

// 5. Now optimize - bounds must respect surge/stonewall
double lowerBound = currentRate * 0.96;  // Stay above surge
double upperBound = currentRate * 1.10;  // Stay below stonewall

OptimizationConfig config = new OptimizationConfig(lowerBound, upperBound)
    .rateUnit("kg/hr")
    .capacityRuleForType(Compressor.class, new CapacityRule(
        unit -> ((CapacityConstrainedEquipment) unit).getMaxUtilization(),
        unit -> 1.0))
    .utilizationLimitForType(Compressor.class, 1.0);  // 100% since getMaxUtilization is a ratio

OptimizationResult result = optimizer.optimize(process, feedStream, config,
    Collections.emptyList(), Collections.emptyList());
```

**Key Points:**
- Call `reinitializeCapacityConstraints()` after setting compressor charts to update speed/surge constraints
- Set `setMaximumSpeed()` to define available headroom (typically 10-15% above design)
- Use realistic search bounds that respect compressor surge/stonewall limits
- Compressor constraints include: speed, min speed, power, surge margin, stonewall margin

```java
// HARD constraints cannot be violated
OptimizationConstraint.lessThan("Max Pressure",
    ps -> ps.getUnit("Export").getPressure("bara"),
    150.0, ConstraintSeverity.HARD, 100.0, "Pipeline MAWP");

// SOFT constraints add penalties but allow operation
OptimizationConstraint.greaterThan("Target Temperature",
    ps -> ps.getUnit("Cooler").getOutStream().getTemperature("C"),
    35.0, ConstraintSeverity.SOFT, 5.0, "Target export temp");
```

### 5. Validate Before Optimization

```java
// Check that all equipment is properly configured
for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
    if (unit.getCapacityMax() <= 0) {
        System.out.println("Warning: " + unit.getName() + " has no capacity limit set");
    }
}
```

---

## Troubleshooting

### Problem: Optimization finds no feasible solution

**Possible causes:**
1. Lower bound is too high
2. Equipment limits are too tight
3. Hard constraints cannot be satisfied

**Solution:**
```java
// Check constraints at minimum rate
feed.setFlowRate(config.lowerBound, "kg/hr");
process.run();

for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
    double util = unit.getCapacityDuty() / unit.getCapacityMax();
    if (util > 1.0) {
        System.out.println("Already exceeded at min rate: " + unit.getName());
    }
}
```

### Problem: Bottleneck changes unexpectedly

**Solution:** Use iteration history to understand the search:
```java
for (IterationRecord r : result.getIterationHistory()) {
    System.out.printf("Rate=%.0f, Bottleneck=%s, Util=%.1f%%%n",
        r.getRate(), r.getBottleneckName(), r.getBottleneckUtilization() * 100);
}
```

### Problem: Slow optimization

**Solutions:**
1. Reduce search range
2. Increase tolerance
3. Use binary search for monotonic problems
4. Enable caching

```java
config.tolerance(50.0)  // Coarser tolerance
      .maxIterations(15)
      .enableCaching(true)
      .searchMode(SearchMode.BINARY_FEASIBILITY);
```

---

## Related Documentation

- [Bottleneck Analysis](../wiki/bottleneck_analysis.md) - Detailed bottleneck detection API
- [Capacity Constraint Framework](../process/CAPACITY_CONSTRAINT_FRAMEWORK.md) - Multi-constraint architecture
- [Process Simulation Guide](../wiki/process_simulation.md) - Building process models
- [Advanced Process Simulation](../wiki/advanced_process_simulation.md) - Recycles and complex systems

---

## API Reference

### ProductionOptimizer

| Method | Description |
|--------|-------------|
| `optimize(ProcessSystem, StreamInterface, OptimizationConfig)` | Find optimal feed rate |
| `optimize(ProcessSystem, StreamInterface, OptimizationConfig, List<Objective>, List<Constraint>)` | With objectives/constraints |
| `optimizeSummary(...)` | Returns lightweight summary |
| `compareScenarios(List<ScenarioRequest>, List<ScenarioKpi>)` | Compare multiple scenarios |

### ProcessSystem

| Method | Description |
|--------|-------------|
| `getBottleneck()` | Get equipment with highest utilization |
| `getBottleneckUtilization()` | Get utilization of bottleneck |
| `findBottleneck()` | Get detailed BottleneckResult |
| `getConstrainedEquipment()` | Get all CapacityConstrainedEquipment |
| `isAnyEquipmentOverloaded()` | Check if any utilization > 100% |
| `isAnyHardLimitExceeded()` | Check if any HARD constraint violated |
| `getCapacityUtilizationSummary()` | Map of equipment name → utilization |
| `getEquipmentNearCapacityLimit(threshold)` | Equipment above threshold |

### CapacityConstrainedEquipment

| Method | Description |
|--------|-------------|
| `getCapacityConstraints()` | List all constraints |
| `getLimitingConstraint()` | Get highest-utilization constraint |
| `getMaxUtilization()` | Get maximum utilization across constraints |
| `isOverloaded()` | Any constraint > 100% |
| `isHardLimitExceeded()` | Any HARD constraint violated |

