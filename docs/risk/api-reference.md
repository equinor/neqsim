# API Reference

Complete Java API reference for the Risk Simulation Framework.

---

## Package: `neqsim.process.equipment.failure`

### EquipmentFailureMode

Represents a failure mode for process equipment.

```java
public class EquipmentFailureMode implements Serializable
```

#### Enum: FailureType

```java
public enum FailureType {
    TRIP,           // Equipment stops completely
    DEGRADED,       // Reduced capacity operation
    PARTIAL_FAILURE,// Some functions lost
    FULL_FAILURE,   // Equipment non-functional
    MAINTENANCE,    // Planned shutdown
    BYPASSED        // Flow routed around
}
```

#### Static Factory Methods

| Method | Description |
|--------|-------------|
| `trip(String name)` | Create a trip failure (0% capacity) |
| `trip(String name, String cause)` | Trip with cause description |
| `degraded(String name, double capacity)` | Degraded operation at specified capacity |
| `maintenance(String name, double hours)` | Planned maintenance |
| `builder()` | Create a builder for custom failure modes |

#### Builder Methods

```java
EquipmentFailureMode.builder()
    .name(String)                    // Failure mode name
    .description(String)             // Description
    .type(FailureType)               // Failure type
    .capacityFactor(double)          // 0.0-1.0 capacity fraction
    .efficiencyFactor(double)        // 0.0-1.0 efficiency multiplier
    .mttr(double)                    // Mean time to repair (hours)
    .failureFrequency(double)        // Failures per year
    .requiresImmediateAction(boolean)// Needs immediate response
    .autoRecoverable(boolean)        // Can recover automatically
    .autoRecoveryTime(double)        // Time to auto-recover (seconds)
    .build()
```

#### Instance Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getName()` | `String` | Failure mode name |
| `getDescription()` | `String` | Description |
| `getType()` | `FailureType` | Type of failure |
| `getCapacityFactor()` | `double` | Capacity fraction (0-1) |
| `getCapacityReduction()` | `double` | Capacity loss (1 - factor) |
| `getEfficiencyFactor()` | `double` | Efficiency multiplier |
| `getMttr()` | `double` | Mean time to repair (hours) |
| `getFailureFrequency()` | `double` | Failures per year |
| `isRequiresImmediateAction()` | `boolean` | Needs immediate action |
| `isAutoRecoverable()` | `boolean` | Can recover automatically |

---

### ReliabilityDataSource

Singleton providing OREDA-based reliability data.

```java
public class ReliabilityDataSource
```

#### Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getInstance()` | `ReliabilityDataSource` | Get singleton instance |
| `getMTTF(String equipmentType)` | `double` | Mean time to failure (hours) |
| `getMTTR(String equipmentType)` | `double` | Mean time to repair (hours) |
| `getFailureRate(String equipmentType)` | `double` | Failures per year |
| `getAvailability(String equipmentType)` | `double` | Availability (0-1) |
| `getFailureModes(String equipmentType)` | `List<EquipmentFailureMode>` | Typical failure modes |

---

## Package: `neqsim.process.safety.risk`

### RiskMatrix

5×5 risk matrix for equipment failure analysis.

```java
public class RiskMatrix implements Serializable
```

#### Constructors

```java
RiskMatrix()                          // Empty matrix
RiskMatrix(ProcessSystem process)     // Auto-populate from process
```

#### Enums

```java
public enum ProbabilityCategory {
    VERY_LOW(1), LOW(2), MEDIUM(3), HIGH(4), VERY_HIGH(5)
}

public enum ConsequenceCategory {
    NEGLIGIBLE(1), MINOR(2), MODERATE(3), MAJOR(4), CATASTROPHIC(5)
}

public enum RiskLevel {
    LOW, MEDIUM, HIGH, VERY_HIGH, EXTREME
}
```

#### Configuration Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `setFeedStreamName(String)` | `void` | Set feed stream name |
| `setProductStreamName(String)` | `void` | Set product stream name |
| `setProductPrice(double, String)` | `void` | Set product price and unit |
| `setDowntimeCostPerHour(double)` | `void` | Set fixed downtime cost |
| `setOperatingHoursPerYear(double)` | `void` | Set annual operating hours |

#### Risk Item Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `addRiskItem(String, ProbabilityCategory, ConsequenceCategory, double)` | `void` | Add risk item |
| `getRiskAssessment(String)` | `RiskAssessment` | Get assessment for equipment |
| `buildRiskMatrix()` | `void` | Auto-build from process |

#### Output Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `toVisualization()` | `String` | ASCII visualization |
| `toJson()` | `String` | JSON export |
| `getTotalAnnualRiskCost()` | `double` | Sum of annual risk costs |
| `getHighRiskItems()` | `List<RiskAssessment>` | Items with HIGH+ risk |

---

### OperationalRiskSimulator

Monte Carlo simulator for production availability analysis.

```java
public class OperationalRiskSimulator implements Serializable
```

#### Constructor

```java
OperationalRiskSimulator(ProcessSystem process)
```

#### Configuration Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `setFeedStreamName(String)` | `this` | Set feed stream (chainable) |
| `setProductStreamName(String)` | `this` | Set product stream (chainable) |
| `setRandomSeed(long)` | `this` | Set random seed (chainable) |
| `addEquipmentReliability(String, double, double)` | `void` | Add equipment (name, failureRate, mttr) |
| `addEquipmentReliability(String, double, double, EquipmentFailureMode)` | `void` | With custom failure mode |

#### Simulation Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `runSimulation(int iterations, double days)` | `OperationalRiskResult` | Run Monte Carlo |

---

### OperationalRiskResult

Results from Monte Carlo simulation.

```java
public class OperationalRiskResult implements Serializable
```

#### Production Statistics

| Method | Returns | Description |
|--------|---------|-------------|
| `getExpectedProduction()` | `double` | Mean production (kg) |
| `getP10Production()` | `double` | 10th percentile |
| `getP50Production()` | `double` | 50th percentile (median) |
| `getP90Production()` | `double` | 90th percentile |
| `getStandardDeviation()` | `double` | Standard deviation |
| `getStandardError()` | `double` | Standard error of mean |

#### Availability Statistics

| Method | Returns | Description |
|--------|---------|-------------|
| `getAvailability()` | `double` | Expected availability (%) |
| `getExpectedDowntimeHours()` | `double` | Expected downtime |
| `getExpectedDowntimeEvents()` | `double` | Expected failure count |

#### Confidence Intervals

| Method | Returns | Description |
|--------|---------|-------------|
| `getLowerConfidenceLimit()` | `double` | 95% CI lower bound |
| `getUpperConfidenceLimit()` | `double` | 95% CI upper bound |

#### Output

| Method | Returns | Description |
|--------|---------|-------------|
| `getSummary()` | `String` | Formatted summary |
| `toJson()` | `String` | JSON export |

---

## Package: `neqsim.process.util.optimizer`

### ProductionImpactAnalyzer

Analyzes production loss from equipment failures.

```java
public class ProductionImpactAnalyzer
```

#### Constructor

```java
ProductionImpactAnalyzer(ProcessSystem process)
```

#### Configuration

| Method | Returns | Description |
|--------|---------|-------------|
| `setFeedStreamName(String)` | `void` | Set feed stream |
| `setProductStreamName(String)` | `void` | Set product stream |
| `setProductPrice(double, String)` | `void` | Set price and unit |

#### Analysis Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `analyzeFailureImpact(EquipmentFailureMode)` | `ProductionImpactResult` | Analyze single failure |
| `analyzeMultipleFailures(List<EquipmentFailureMode>)` | `ProductionImpactResult` | Multiple failures |
| `comparePlantStop()` | `ProductionImpactResult` | Complete shutdown baseline |
| `rankEquipmentByCriticality()` | `Map<String, Double>` | Equipment criticality ranking |

---

### ProductionImpactResult

```java
public class ProductionImpactResult
```

| Method | Returns | Description |
|--------|---------|-------------|
| `getNormalProduction()` | `double` | Production before failure |
| `getDegradedProduction()` | `double` | Production after failure |
| `getProductionLoss()` | `double` | Absolute loss (kg/hr) |
| `getPercentLoss()` | `double` | Loss percentage (0-100) |
| `getRevenueImpact()` | `double` | Revenue loss ($/hr) |
| `getAffectedEquipment()` | `List<String>` | Affected equipment list |
| `getCascadeEffects()` | `List<String>` | Cascade effects |
| `toJson()` | `String` | JSON export |

---

### DegradedOperationOptimizer

Optimizes plant operation during equipment outages.

```java
public class DegradedOperationOptimizer
```

#### Constructor

```java
DegradedOperationOptimizer(ProcessSystem process)
```

#### Configuration

| Method | Returns | Description |
|--------|---------|-------------|
| `setObjective(OptimizationObjective)` | `void` | Set optimization goal |
| `addConstraint(OperatingConstraint)` | `void` | Add operating constraint |

#### Optimization Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `optimizeWithEquipmentDown(EquipmentFailureMode)` | `DegradedOperationResult` | Optimize for single failure |
| `optimizeWithMultipleFailures(List<EquipmentFailureMode>)` | `DegradedOperationResult` | Multiple failures |
| `evaluateOperatingModes(EquipmentFailureMode)` | `List<OperatingMode>` | Evaluate possible modes |
| `createRecoveryPlan(EquipmentFailureMode)` | `RecoveryPlan` | Generate recovery plan |

---

### DegradedOperationResult

```java
public class DegradedOperationResult
```

| Method | Returns | Description |
|--------|---------|-------------|
| `getNormalProduction()` | `double` | Production before failure |
| `getOptimalProduction()` | `double` | Optimized degraded production |
| `getProductionRecovery()` | `double` | % of normal achieved |
| `getAdjustments()` | `List<OperatingAdjustment>` | Recommended adjustments |
| `getRecoveryPlan()` | `RecoveryPlan` | Step-by-step recovery |
| `getActiveConstraints()` | `List<OperatingConstraint>` | Active constraints |
| `hasViolatedConstraints()` | `boolean` | Any constraints violated |
| `toJson()` | `String` | JSON export |

---

## Package: `neqsim.process.util.topology`

### ProcessTopologyAnalyzer

Analyzes process graph structure.

```java
public class ProcessTopologyAnalyzer implements Serializable
```

#### Constructor

```java
ProcessTopologyAnalyzer(ProcessSystem process)
```

#### Building Topology

| Method | Returns | Description |
|--------|---------|-------------|
| `buildTopology()` | `void` | Build graph from process |
| `setFunctionalLocation(String, String)` | `void` | Assign STID tag |

#### Querying Topology

| Method | Returns | Description |
|--------|---------|-------------|
| `getNodes()` | `Map<String, EquipmentNode>` | All nodes |
| `getNode(String)` | `EquipmentNode` | Specific node |
| `getEdges()` | `List<ProcessEdge>` | All edges |
| `getTopologicalOrder()` | `Map<String, Integer>` | Topological ordering |
| `getParallelGroups()` | `List<Set<String>>` | Parallel equipment groups |
| `getCriticalPath()` | `List<String>` | Critical path |
| `getCriticalEquipment(double)` | `List<String>` | Equipment above threshold |

#### Navigation

| Method | Returns | Description |
|--------|---------|-------------|
| `getAllUpstreamEquipment(String)` | `Set<String>` | All upstream |
| `getAllDownstreamEquipment(String)` | `Set<String>` | All downstream |
| `getEquipmentByInstallation(String)` | `List<String>` | By installation code |
| `getEquipmentByType(String)` | `List<String>` | By equipment type code |

#### Export

| Method | Returns | Description |
|--------|---------|-------------|
| `toDotGraph()` | `String` | Graphviz DOT format |
| `toJson()` | `String` | JSON export |

---

### FunctionalLocation

STID tag parser and validator.

```java
public class FunctionalLocation implements Serializable, Comparable<FunctionalLocation>
```

#### Constants

```java
// Installation codes
GULLFAKS_A = "1770"
GULLFAKS_B = "1773"
GULLFAKS_C = "1775"
ASGARD_A = "2540"
ASGARD_B = "2541"
TROLL_A = "1910"

// Equipment type codes
TYPE_COMPRESSOR = "KA"
TYPE_PUMP = "PA"
TYPE_VALVE = "VA"
TYPE_SEPARATOR = "VG"
TYPE_HEAT_EXCHANGER = "WA"
TYPE_COOLER = "WC"
```

#### Constructors

```java
FunctionalLocation(String stidTag)
FunctionalLocation(String installation, String type, String number, String suffix)
```

#### Accessors

| Method | Returns | Description |
|--------|---------|-------------|
| `getFullTag()` | `String` | Complete STID tag |
| `getInstallationCode()` | `String` | 4-digit installation |
| `getInstallationName()` | `String` | Human-readable name |
| `getEquipmentTypeCode()` | `String` | 2-char type code |
| `getEquipmentTypeDescription()` | `String` | Human-readable type |
| `getSequentialNumber()` | `String` | 5-digit number |
| `getTrainSuffix()` | `String` | A, B, C... or null |
| `getBaseTag()` | `String` | Tag without suffix |

#### Comparison Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `isParallelTo(FunctionalLocation)` | `boolean` | Same base, different suffix |
| `isSameInstallation(FunctionalLocation)` | `boolean` | Same installation |
| `isSameSystem(FunctionalLocation)` | `boolean` | Same system number |
| `isParallelUnit()` | `boolean` | Has train suffix |

#### Static Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `isValidSTID(String)` | `boolean` | Validate tag format |
| `builder()` | `Builder` | Create builder |

---

### DependencyAnalyzer

Analyzes equipment dependencies and cascade effects.

```java
public class DependencyAnalyzer implements Serializable
```

#### Constructors

```java
DependencyAnalyzer(ProcessSystem process)
DependencyAnalyzer(ProcessSystem process, ProcessTopologyAnalyzer topology)
```

#### Analysis Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `analyzeFailure(String equipment)` | `DependencyResult` | Analyze failure impact |
| `getEquipmentToMonitor(String)` | `Map<String, String>` | Equipment → reason |
| `getCascadeTree(String)` | `Map<String, List<String>>` | Cascade tree |
| `getCascadeTiming(String)` | `Map<String, Double>` | Equipment → minutes |

#### Cross-Installation

| Method | Returns | Description |
|--------|---------|-------------|
| `addCrossInstallationDependency(String, String, String, String)` | `void` | Add by name |
| `addCrossInstallationDependency(FunctionalLocation, FunctionalLocation, String, double)` | `void` | Add by STID |

---

### DependencyResult

```java
public class DependencyResult implements Serializable
```

| Method | Returns | Description |
|--------|---------|-------------|
| `getFailedEquipment()` | `String` | Failed equipment name |
| `getFailedLocation()` | `FunctionalLocation` | STID tag |
| `getDirectlyAffected()` | `List<String>` | Immediate downstream |
| `getIndirectlyAffected()` | `List<String>` | Cascade effects |
| `getIncreasedCriticality()` | `Map<String, Double>` | Equipment → criticality |
| `getEquipmentToWatch()` | `List<String>` | Monitor recommendations |
| `getTotalProductionLoss()` | `double` | Total loss (%) |
| `getCrossInstallationEffects()` | `List<CrossInstallationDependency>` | Cross-platform effects |
| `toJson()` | `String` | JSON export |

---

## Usage Examples

### Complete Risk Analysis Workflow

```java
// 1. Build process
ProcessSystem process = new ProcessSystem();
// ... add equipment ...
process.run();

// 2. Build topology
ProcessTopologyAnalyzer topology = new ProcessTopologyAnalyzer(process);
topology.buildTopology();
topology.setFunctionalLocation("Compressor A", "1775-KA-23011A");

// 3. Analyze dependencies
DependencyAnalyzer deps = new DependencyAnalyzer(process, topology);
DependencyResult depResult = deps.analyzeFailure("Compressor A");

// 4. Build risk matrix
RiskMatrix matrix = new RiskMatrix(process);
matrix.buildRiskMatrix();
System.out.println(matrix.toVisualization());

// 5. Run Monte Carlo
OperationalRiskSimulator sim = new OperationalRiskSimulator(process);
sim.addEquipmentReliability("Compressor A", 0.5, 24);
OperationalRiskResult mcResult = sim.runSimulation(10000, 365);
System.out.println(mcResult.getSummary());

// 6. Optimize degraded operation
DegradedOperationOptimizer opt = new DegradedOperationOptimizer(process);
EquipmentFailureMode failure = EquipmentFailureMode.trip("Compressor A");
DegradedOperationResult optResult = opt.optimizeWithEquipmentDown(failure);
```

---

## See Also

- [Overview](overview.md)
- [Mathematical Reference](mathematical-reference.md)
- [Jupyter Notebook Examples](../../examples/notebooks/)
