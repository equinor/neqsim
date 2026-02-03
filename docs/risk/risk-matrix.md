---
layout: default
title: Risk Matrix
parent: Risk Framework
---

# Risk Matrix

The Risk Matrix provides a visual representation of equipment risks by combining probability (failure frequency) with consequence (production impact). It follows ISO 31000 and NORSOK Z-013 guidelines.

---

## Risk Matrix Structure

### 5×5 Matrix Layout

```
                        CONSEQUENCE
                 1      2       3        4         5
              Neglig. Minor  Moderate  Major  Catastrophic
           ┌────────┬────────┬────────┬────────┬────────┐
    5 VH   │ MEDIUM │  HIGH  │ V.HIGH │EXTREME │EXTREME │
           ├────────┼────────┼────────┼────────┼────────┤
P   4 H    │  LOW   │ MEDIUM │  HIGH  │ V.HIGH │EXTREME │
R          ├────────┼────────┼────────┼────────┼────────┤
O   3 M    │  LOW   │  LOW   │ MEDIUM │  HIGH  │ V.HIGH │
B          ├────────┼────────┼────────┼────────┼────────┤
    2 L    │  LOW   │  LOW   │  LOW   │ MEDIUM │  HIGH  │
           ├────────┼────────┼────────┼────────┼────────┤
    1 VL   │  LOW   │  LOW   │  LOW   │  LOW   │ MEDIUM │
           └────────┴────────┴────────┴────────┴────────┘
```

### Color Coding

| Risk Level | Color | Action Required |
|------------|-------|-----------------|
| **LOW** | 🟢 Green | Accept, monitor periodically |
| **MEDIUM** | 🟡 Yellow | Monitor, plan mitigation |
| **HIGH** | 🟠 Orange | Active mitigation required |
| **VERY_HIGH** | 🔴 Red | Immediate action required |
| **EXTREME** | ⚫ Black | Unacceptable, must mitigate |

---

## Probability Categories

Based on failure frequency (failures per year):

| Category | Level | Frequency Range | Typical Causes |
|----------|-------|-----------------|----------------|
| **VERY_LOW** | 1 | < 0.1/year | Rare events, design failures |
| **LOW** | 2 | 0.1 - 0.5/year | Infrequent issues |
| **MEDIUM** | 3 | 0.5 - 1.0/year | Annual occurrence |
| **HIGH** | 4 | 1.0 - 2.0/year | Frequent issues |
| **VERY_HIGH** | 5 | > 2.0/year | Chronic problems |

### Probability Calculation

From MTTF (Mean Time To Failure):

$$\lambda = \frac{8760}{\text{MTTF (hours)}} \text{ failures/year}$$

```java
// Map failure rate to category
ProbabilityCategory prob = ProbabilityCategory.fromFrequency(failuresPerYear);
```

---

## Consequence Categories

Based on production loss percentage:

| Category | Level | Production Loss | Economic Impact |
|----------|-------|-----------------|-----------------|
| **NEGLIGIBLE** | 1 | < 5% | Minor revenue loss |
| **MINOR** | 2 | 5 - 20% | Noticeable impact |
| **MODERATE** | 3 | 20 - 50% | Significant loss |
| **MAJOR** | 4 | 50 - 80% | Severe impact |
| **CATASTROPHIC** | 5 | > 80% | Plant stop |

### Consequence Calculation

Production loss is calculated by NeqSim simulation:

$$\text{Loss} = \frac{\text{Normal Production} - \text{Degraded Production}}{\text{Normal Production}} \times 100\%$$

```java
// Map production loss to category
ConsequenceCategory cons = ConsequenceCategory.fromProductionLoss(lossPercent);
```

---

## Risk Level Determination

### Risk Score

$$\text{Risk Score} = P \times C$$

Where $P$ is probability level (1-5) and $C$ is consequence level (1-5).

### Risk Level Mapping

| Score Range | Risk Level |
|-------------|------------|
| 1 - 4 | LOW |
| 5 - 9 | MEDIUM |
| 10 - 14 | HIGH |
| 15 - 19 | VERY_HIGH |
| 20 - 25 | EXTREME |

```java
RiskLevel level = RiskLevel.fromScore(probability.getLevel() * consequence.getLevel());
```

---

## Economic Cost Calculation

### Annual Risk Cost

The expected annual cost of a risk:

$$C_{\text{annual}} = \lambda \times (C_{\text{production}} + C_{\text{downtime}} + C_{\text{repair}})$$

Where:
- $\lambda$ = failure frequency (per year)
- $C_{\text{production}}$ = lost production cost
- $C_{\text{downtime}}$ = fixed downtime costs
- $C_{\text{repair}}$ = repair costs

### Production Loss Cost

$$C_{\text{production}} = \text{MTTR} \times \text{Flow Rate} \times \text{Price} \times \text{Loss Factor}$$

### Downtime Cost

$$C_{\text{downtime}} = \text{MTTR} \times \text{Downtime Rate ($/hour)}$$

### Example Calculation

```java
// Cost parameters
double productPrice = 500.0;  // USD per tonne
double downtimeCostPerHour = 10000.0;  // USD
double repairCost = 50000.0;  // USD

// Risk event parameters
double failureRate = 0.5;  // per year
double mttr = 24.0;  // hours
double productionLoss = 100.0;  // tonnes

// Calculate annual risk cost
double productionCost = productionLoss * productPrice;  // $50,000 per event
double downtimeCost = mttr * downtimeCostPerHour;       // $240,000 per event
double totalEventCost = productionCost + downtimeCost + repairCost;  // $340,000

double annualRiskCost = failureRate * totalEventCost;   // $170,000/year
```

---

## Creating a Risk Matrix

### Basic Usage

```java
// Create risk matrix for a process
RiskMatrix matrix = new RiskMatrix(processSystem);
matrix.setFeedStreamName("Well Feed");
matrix.setProductStreamName("Export Gas");

// Set economic parameters
matrix.setProductPrice(500.0, "USD/tonne");
matrix.setDowntimeCostPerHour(10000.0);
matrix.setOperatingHoursPerYear(8000.0);

// Build the matrix (auto-populates from process)
matrix.buildRiskMatrix();
```

### Manual Risk Items

```java
// Create empty matrix
RiskMatrix matrix = new RiskMatrix();

// Add risk items manually
matrix.addRiskItem("Compressor Trip", 
    ProbabilityCategory.MEDIUM,      // 0.5-1.0 failures/year
    ConsequenceCategory.MAJOR,       // 50-80% production loss
    500000.0);                       // $500k estimated cost

matrix.addRiskItem("Pump Seal Leak",
    ProbabilityCategory.HIGH,        // 1-2 failures/year
    ConsequenceCategory.MINOR,       // 5-20% production loss
    50000.0);

matrix.addRiskItem("Separator Level Trip",
    ProbabilityCategory.LOW,
    ConsequenceCategory.CATASTROPHIC,
    1000000.0);
```

### From Reliability Data

```java
// Auto-populate from reliability data source
ReliabilityDataSource reliability = ReliabilityDataSource.getInstance();

for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
    String name = equipment.getName();
    String type = equipment.getClass().getSimpleName();
    
    // Get reliability data
    double failureRate = reliability.getFailureRate(type);
    ProbabilityCategory prob = ProbabilityCategory.fromFrequency(failureRate);
    
    // Simulate to get consequence
    EquipmentFailureMode failure = EquipmentFailureMode.trip(name);
    ProductionImpactResult impact = analyzer.analyzeFailureImpact(failure);
    ConsequenceCategory cons = ConsequenceCategory.fromProductionLoss(impact.getPercentLoss());
    
    // Add to matrix
    matrix.addRiskItem(name + " Trip", prob, cons, impact.getRevenueImpact());
}
```

---

## Output Formats

### ASCII Visualization

```java
String visualization = matrix.toVisualization();
System.out.println(visualization);
```

Output:
```
═══════════════════════════════════════════════════════════════════════
                           RISK MATRIX
═══════════════════════════════════════════════════════════════════════

                         CONSEQUENCE
              Negligible  Minor    Moderate   Major   Catastrophic
              (1)         (2)      (3)        (4)     (5)
           ┌──────────┬──────────┬──────────┬──────────┬──────────┐
  Very High│  MEDIUM  │   HIGH   │ VERY HIGH│ EXTREME  │ EXTREME  │
  (5)      │          │          │          │          │          │
           ├──────────┼──────────┼──────────┼──────────┼──────────┤
P High     │   LOW    │  MEDIUM  │   HIGH   │ VERY HIGH│ EXTREME  │
R (4)      │          │          │    [1]   │          │          │
O          ├──────────┼──────────┼──────────┼──────────┼──────────┤
B Medium   │   LOW    │   LOW    │  MEDIUM  │   HIGH   │ VERY HIGH│
A (3)      │          │          │          │    [2]   │          │
B          ├──────────┼──────────┼──────────┼──────────┼──────────┤
I Low      │   LOW    │   LOW    │   LOW    │  MEDIUM  │   HIGH   │
L (2)      │          │          │          │          │    [1]   │
I          ├──────────┼──────────┼──────────┼──────────┼──────────┤
T Very Low │   LOW    │   LOW    │   LOW    │   LOW    │  MEDIUM  │
Y (1)      │          │          │          │          │          │
           └──────────┴──────────┴──────────┴──────────┴──────────┘

LEGEND: [n] = number of risk items in cell

RISK ITEMS BY LEVEL:
═══════════════════════════════════════════════════════════════════════
EXTREME (0 items):
  (none)

VERY_HIGH (0 items):
  (none)

HIGH (3 items):
  • Compressor A Trip (P:4, C:3) - Annual Cost: $125,000
  • Compressor B Trip (P:3, C:4) - Annual Cost: $180,000
  • Separator Level Trip (P:2, C:5) - Annual Cost: $95,000
═══════════════════════════════════════════════════════════════════════
```

### JSON Export

```java
String json = matrix.toJson();
```

```json
{
  "matrixSize": 5,
  "probabilityCategories": ["VERY_LOW", "LOW", "MEDIUM", "HIGH", "VERY_HIGH"],
  "consequenceCategories": ["NEGLIGIBLE", "MINOR", "MODERATE", "MAJOR", "CATASTROPHIC"],
  "riskItems": [
    {
      "name": "Compressor A Trip",
      "probability": "HIGH",
      "probabilityLevel": 4,
      "consequence": "MODERATE",
      "consequenceLevel": 3,
      "riskLevel": "HIGH",
      "riskScore": 12,
      "estimatedCost": 125000.0,
      "annualRiskCost": 62500.0
    }
  ],
  "summary": {
    "totalItems": 10,
    "byRiskLevel": {
      "LOW": 4,
      "MEDIUM": 3,
      "HIGH": 2,
      "VERY_HIGH": 1,
      "EXTREME": 0
    },
    "totalAnnualRiskCost": 450000.0
  }
}
```

---

## Risk Mitigation Analysis

### Comparing Scenarios

```java
// Current state
RiskMatrix current = new RiskMatrix(process);
current.buildRiskMatrix();

// With mitigation (e.g., add redundant compressor)
process.add(redundantCompressor);
RiskMatrix mitigated = new RiskMatrix(process);
mitigated.buildRiskMatrix();

// Compare
double currentRisk = current.getTotalAnnualRiskCost();
double mitigatedRisk = mitigated.getTotalAnnualRiskCost();
double riskReduction = currentRisk - mitigatedRisk;

System.out.println("Risk reduction: $" + riskReduction + "/year");
```

### Cost-Benefit Analysis

$$\text{ROI} = \frac{\text{Annual Risk Reduction} - \text{Annual Mitigation Cost}}{\text{Mitigation Investment}}$$

```java
double investmentCost = 5000000.0;  // New compressor
double annualMaintenance = 100000.0;
double annualRiskReduction = 300000.0;

double netAnnualBenefit = annualRiskReduction - annualMaintenance;  // $200,000
double paybackPeriod = investmentCost / netAnnualBenefit;  // 25 years
```

---

## Best Practices

1. **Use consistent categories** across your organization
2. **Validate probability data** with historical failure records
3. **Simulate consequences** rather than estimating
4. **Review annually** and update with actual performance
5. **Document assumptions** for audit trails
6. **Consider cascading effects** (dependency analysis)
7. **Include all cost components** (production, downtime, repair, environmental)

---

## See Also

- [Monte Carlo Simulation](monte-carlo)
- [Production Impact Analysis](production-impact)
- [Mathematical Reference](mathematical-reference)
- [API Reference](api-reference#riskmatrix)
