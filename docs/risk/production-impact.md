# Production Impact Analysis

Production Impact Analysis quantifies how equipment failures affect plant output, enabling prioritization of maintenance and investment decisions.

---

## Overview

When equipment fails, production is affected in several ways:

1. **Direct loss** - Equipment output stops or reduces
2. **Cascade effects** - Downstream equipment starved
3. **Bottleneck shifts** - Different equipment becomes limiting
4. **Quality changes** - Product specifications may change

The `ProductionImpactAnalyzer` uses NeqSim simulation to calculate these effects accurately.

---

## Key Metrics

### Production Loss Percentage

$$\text{Loss}_\% = \frac{P_{\text{normal}} - P_{\text{degraded}}}{P_{\text{normal}}} \times 100\%$$

### Revenue Impact

$$\text{Revenue Loss} = P_{\text{loss}} \times \text{Price} \times \text{Duration}$$

### Criticality Index

$$CI = \frac{\text{Production Loss}_\%}{\text{max(Production Loss across all equipment)}}$$

Equipment with $CI > 0.8$ is considered "critical".

---

## Using ProductionImpactAnalyzer

### Basic Analysis

```java
// Create analyzer
ProductionImpactAnalyzer analyzer = new ProductionImpactAnalyzer(processSystem);

// Configure streams
analyzer.setFeedStreamName("Well Feed");
analyzer.setProductStreamName("Export Gas");
analyzer.setProductPrice(500.0, "USD/tonne");

// Analyze a specific failure
EquipmentFailureMode compressorTrip = EquipmentFailureMode.trip("HP Compressor");
ProductionImpactResult result = analyzer.analyzeFailureImpact(compressorTrip);

// Get results
System.out.println("Production loss: " + result.getPercentLoss() + "%");
System.out.println("Revenue impact: $" + result.getRevenueImpact() + "/hour");
System.out.println("Affected equipment: " + result.getAffectedEquipment());
```

### Analyzing All Equipment

```java
// Rank all equipment by criticality
Map<String, Double> criticality = analyzer.rankEquipmentByCriticality();

System.out.println("Equipment Criticality Ranking:");
for (Map.Entry<String, Double> entry : criticality.entrySet()) {
    String status = entry.getValue() > 80 ? "⚠️ CRITICAL" : "";
    System.out.printf("  %s: %.1f%% %s%n", 
        entry.getKey(), entry.getValue(), status);
}
```

### Comparing Scenarios

```java
// Compare failure to complete plant stop
ProductionImpactResult failure = analyzer.analyzeFailureImpact(compressorTrip);
ProductionImpactResult plantStop = analyzer.comparePlantStop();

double severityRatio = failure.getPercentLoss() / plantStop.getPercentLoss();
System.out.println("Severity vs plant stop: " + (severityRatio * 100) + "%");
```

---

## ProductionImpactResult

The result object contains comprehensive impact data:

```java
public class ProductionImpactResult {
    // Production metrics
    double getNormalProduction();      // kg/hr before failure
    double getDegradedProduction();    // kg/hr after failure
    double getProductionLoss();        // kg/hr lost
    double getPercentLoss();           // 0-100%
    
    // Economic metrics
    double getRevenueImpact();         // $/hr
    double getEstimatedDailyCost();    // $/day
    
    // Affected equipment
    List<String> getAffectedEquipment();
    List<String> getCascadeEffects();
    
    // Quality impacts (if applicable)
    Map<String, Double> getQualityChanges();
    
    // Bottleneck analysis
    String getNewBottleneck();
    double getBottleneckCapacity();
}
```

---

## Impact Categories

### Direct Impact

Equipment's direct contribution to production:

```java
// For a compressor
double throughput = compressor.getInletStream().getFlowRate("kg/hr");
double directImpact = throughput;  // If compressor trips
```

### Cascade Impact

Downstream equipment affected by upstream failure:

```
HP Separator trips
    └─► HP Compressor starved (no gas feed)
        └─► Export Cooler no flow
            └─► Export Pipeline empty
```

```java
// Cascade analysis
List<String> cascade = result.getCascadeEffects();
// Returns: [HP Compressor, Export Cooler, Export Pipeline]
```

### Parallel Train Impact

When one train of parallel equipment fails:

```
Normal: Train A (50%) + Train B (50%) = 100%
Failure: Train A (0%) + Train B (50%) = 50%
```

```java
// Parallel train analysis
if (topology.hasParallelEquipment("Compressor A")) {
    List<String> parallel = topology.getParallelEquipment("Compressor A");
    // Can redistribute load to Train B
}
```

---

## Analysis Methods

### 1. Single Equipment Failure

```java
EquipmentFailureMode failure = EquipmentFailureMode.trip("Equipment Name");
ProductionImpactResult result = analyzer.analyzeFailureImpact(failure);
```

### 2. Multiple Equipment Failures

```java
List<EquipmentFailureMode> failures = Arrays.asList(
    EquipmentFailureMode.trip("Compressor A"),
    EquipmentFailureMode.degraded("Pump B", 0.5)
);

ProductionImpactResult result = analyzer.analyzeMultipleFailures(failures);
```

### 3. Degraded Operation Analysis

```java
// What if compressor runs at 70% capacity?
EquipmentFailureMode degraded = EquipmentFailureMode.builder()
    .name("Compressor fouling")
    .type(FailureType.DEGRADED)
    .capacityFactor(0.7)
    .build();

ProductionImpactResult result = analyzer.analyzeFailureImpact(degraded);
System.out.println("At 70% capacity: " + result.getPercentLoss() + "% production loss");
```

### 4. Sensitivity Analysis

```java
// How does production change with compressor capacity?
double[] capacities = {1.0, 0.9, 0.8, 0.7, 0.6, 0.5};

for (double cap : capacities) {
    EquipmentFailureMode mode = EquipmentFailureMode.degraded("HP Compressor", cap);
    ProductionImpactResult result = analyzer.analyzeFailureImpact(mode);
    System.out.printf("Capacity %.0f%%: Production loss %.1f%%%n", 
        cap * 100, result.getPercentLoss());
}
```

Output:
```
Capacity 100%: Production loss 0.0%
Capacity 90%: Production loss 8.5%
Capacity 80%: Production loss 18.2%
Capacity 70%: Production loss 28.9%
Capacity 60%: Production loss 40.1%
Capacity 50%: Production loss 50.0%
```

---

## Economic Analysis

### Revenue Impact Calculation

```java
// Set economic parameters
analyzer.setProductPrice(500.0, "USD/tonne");  // Gas price
analyzer.setDowntimeCostPerHour(10000.0);       // Fixed costs

ProductionImpactResult result = analyzer.analyzeFailureImpact(failure);

// Get economic impact
double productionLoss = result.getProductionLoss();      // kg/hr
double revenueRate = productionLoss * 0.5 / 1000;        // USD/hr (at $500/tonne)
double fixedCosts = analyzer.getDowntimeCostPerHour();   // USD/hr
double totalHourlyCost = revenueRate + fixedCosts;       // Total USD/hr
```

### Annual Impact Projection

$$\text{Annual Cost} = \lambda \times \text{MTTR} \times \text{Hourly Cost}$$

```java
double failureRate = 0.5;    // per year
double mttr = 24.0;          // hours
double hourlyCost = 50000.0; // USD/hr

double annualImpact = failureRate * mttr * hourlyCost;  // $600,000/year
```

---

## Visualization

### Impact Summary Table

```java
// Generate summary for all equipment
String table = analyzer.generateImpactSummary();
```

Output:
```
╔════════════════════════╦═══════════╦═══════════════╦═══════════════╗
║ Equipment              ║ Loss (%)  ║ Revenue/hr    ║ Criticality   ║
╠════════════════════════╬═══════════╬═══════════════╬═══════════════╣
║ HP Compressor          ║   85.2%   ║   $42,600     ║ ⚠️ CRITICAL   ║
║ LP Compressor          ║   65.4%   ║   $32,700     ║ ⚠️ CRITICAL   ║
║ HP Separator           ║  100.0%   ║   $50,000     ║ ⚠️ CRITICAL   ║
║ Export Pump            ║   45.0%   ║   $22,500     ║ HIGH          ║
║ Condensate Pump        ║   12.5%   ║    $6,250     ║ MEDIUM        ║
║ Inlet Cooler           ║   18.3%   ║    $9,150     ║ MEDIUM        ║
╚════════════════════════╩═══════════╩═══════════════╩═══════════════╝
```

### JSON Export

```java
String json = result.toJson();
```

```json
{
  "equipment": "HP Compressor",
  "failureMode": "TRIP",
  "normalProduction": {
    "value": 50000,
    "unit": "kg/hr"
  },
  "degradedProduction": {
    "value": 7400,
    "unit": "kg/hr"
  },
  "productionLoss": {
    "value": 42600,
    "unit": "kg/hr",
    "percent": 85.2
  },
  "revenueImpact": {
    "hourly": 42600,
    "daily": 1022400,
    "currency": "USD"
  },
  "affectedEquipment": [
    "Export Cooler",
    "Export Pipeline"
  ],
  "cascadeEffects": [
    {
      "equipment": "Export Cooler",
      "effect": "No flow",
      "delay": "Immediate"
    }
  ]
}
```

---

## Integration with Other Tools

### With Risk Matrix

```java
// Populate risk matrix with impact data
RiskMatrix matrix = new RiskMatrix(process);

for (String equipment : analyzer.getAllEquipment()) {
    EquipmentFailureMode failure = EquipmentFailureMode.trip(equipment);
    ProductionImpactResult impact = analyzer.analyzeFailureImpact(failure);
    
    ConsequenceCategory consequence = 
        ConsequenceCategory.fromProductionLoss(impact.getPercentLoss());
    
    // Add to risk matrix
    matrix.addRiskItem(equipment, probability, consequence, impact.getRevenueImpact());
}
```

### With Topology Analysis

```java
// Consider topology for cascade effects
ProcessTopologyAnalyzer topology = new ProcessTopologyAnalyzer(process);
topology.buildTopology();

// Find all downstream equipment
List<String> downstream = topology.getDownstreamEquipment("HP Separator");
// All downstream equipment will be affected by separator failure
```

---

## Best Practices

1. **Validate baseline** - Ensure normal production matches design
2. **Include all products** - Gas, oil, condensate may have different values
3. **Consider quality** - Off-spec product may have reduced value
4. **Account for startup** - Production ramp-up after repair
5. **Include cascade effects** - Use topology analysis
6. **Update prices** - Use current market prices
7. **Document assumptions** - Record all economic parameters

---

## See Also

- [Degraded Operation Optimization](degraded-operation.md)
- [Risk Matrix](risk-matrix.md)
- [Topology Analysis](topology.md)
- [API Reference](api-reference.md#productionimpactanalyzer)
