---
layout: default
title: Condition-Based Reliability
parent: Risk Framework
---

# P6: Condition-Based Reliability

## Overview

The Condition-Based Reliability package integrates equipment health monitoring with reliability analysis, enabling predictive maintenance and dynamic risk assessment based on real-time condition data.

## Key Concepts

Traditional reliability analysis uses fixed failure rates (MTBF). Condition-based reliability adjusts these rates based on actual equipment health indicators:

- **Health Index**: Overall equipment condition (0-1 scale)
- **Adjusted MTBF**: Reduced MTBF based on degradation
- **Remaining Useful Life (RUL)**: Estimated time until maintenance required

## Key Classes

### ConditionBasedReliability

```java
ConditionBasedReliability cbr = new ConditionBasedReliability(
    "C-200",              // Equipment ID
    "Export Compressor"   // Equipment name
);

// Set baseline reliability
cbr.setBaselineMTBF(12000);  // hours (from OREDA or similar)
cbr.setInstallationDate("2020-01-15");
cbr.setOperatingHours(15000);
```

### Adding Condition Indicators

Define monitoring parameters with their healthy and failure thresholds:

```java
// addIndicator(name, healthyValue, failureThreshold, degradationModel)

// Vibration (increasing is bad)
cbr.addIndicator("vibration", 5.0, 15.0, 
    ConditionBasedReliability.DegradationModel.LINEAR);

// Bearing temperature (increasing is bad)  
cbr.addIndicator("bearing_temp", 60.0, 90.0, 
    ConditionBasedReliability.DegradationModel.EXPONENTIAL);

// Oil particulate count (increasing is bad)
cbr.addIndicator("oil_particulates", 0.0, 100.0, 
    ConditionBasedReliability.DegradationModel.LINEAR);

// Efficiency (decreasing is bad)
cbr.addIndicator("efficiency", 85.0, 70.0, 
    ConditionBasedReliability.DegradationModel.LINEAR);
```

### Degradation Models

| Model | Description | Use Case |
|-------|-------------|----------|
| LINEAR | Constant degradation rate | Most mechanical wear |
| EXPONENTIAL | Accelerating degradation | Bearing failures, fatigue |
| WEIBULL | Bathtub curve | General equipment |
| LOGARITHMIC | Rapid initial, then slowing | Erosion, corrosion |

### Setting Weights

Assign importance to each indicator:

```java
cbr.setIndicatorWeight("vibration", 0.35);      // Most important
cbr.setIndicatorWeight("bearing_temp", 0.25);
cbr.setIndicatorWeight("oil_particulates", 0.20);
cbr.setIndicatorWeight("efficiency", 0.20);
```

## Updating Conditions

### Real-time Updates

```java
Map<String, Double> currentConditions = new HashMap<>();
currentConditions.put("vibration", 8.5);        // Elevated
currentConditions.put("bearing_temp", 72.0);    // Slightly elevated
currentConditions.put("oil_particulates", 35.0); // Moderate
currentConditions.put("efficiency", 80.0);      // Slightly degraded

cbr.updateConditions(currentConditions);
```

### Historical Data

```java
// Add historical readings for trend analysis
cbr.addHistoricalReading("vibration", 5.0, "2024-01-01");
cbr.addHistoricalReading("vibration", 5.5, "2024-02-01");
cbr.addHistoricalReading("vibration", 6.2, "2024-03-01");
cbr.addHistoricalReading("vibration", 7.0, "2024-04-01");
cbr.addHistoricalReading("vibration", 8.5, "2024-05-01");
```

## Health Assessment

### Health Index

```java
double healthIndex = cbr.calculateHealthIndex();
System.out.println("Health Index: " + (healthIndex * 100) + "%");

// Health categories
if (healthIndex > 0.8) {
    System.out.println("Status: Good");
} else if (healthIndex > 0.5) {
    System.out.println("Status: Monitor closely");
} else if (healthIndex > 0.3) {
    System.out.println("Status: Plan maintenance");
} else {
    System.out.println("Status: Critical - immediate action");
}
```

### Individual Indicator Status

```java
for (ConditionBasedReliability.ConditionIndicator indicator : cbr.getIndicators()) {
    double normalized = indicator.getNormalizedValue();  // 0 = healthy, 1 = failed
    
    String status;
    if (normalized < 0.3) status = "🟢 Good";
    else if (normalized < 0.7) status = "🟡 Warning";
    else status = "🔴 Critical";
    
    System.out.println(indicator.getName() + ": " + 
        indicator.getCurrentValue() + " (" + status + ")");
}
```

### Adjusted MTBF

```java
double adjustedMTBF = cbr.calculateAdjustedMTBF();
double baseline = cbr.getBaselineMTBF();

System.out.println("Baseline MTBF: " + baseline + " hours");
System.out.println("Adjusted MTBF: " + adjustedMTBF + " hours");
System.out.println("Reliability reduction: " + 
    ((1 - adjustedMTBF/baseline) * 100) + "%");
```

### Remaining Useful Life

```java
double rul = cbr.estimateRUL();
System.out.println("Estimated RUL: " + rul + " hours");
System.out.println("Days remaining: " + (rul / 24));

// With confidence interval
double[] rulCI = cbr.estimateRULWithConfidence();
System.out.println("RUL (P10-P90): " + rulCI[0] + " - " + rulCI[2] + " hours");
```

## Trend Analysis

```java
ConditionBasedReliability.TrendAnalysis trend = cbr.calculateTrend("vibration");

System.out.println("Trend Direction: " + trend.getDirection());  // INCREASING, STABLE, DECREASING
System.out.println("Rate of Change: " + trend.getRateOfChange() + " per month");
System.out.println("Time to Alarm: " + trend.getTimeToThreshold() + " days");
System.out.println("Confidence: " + (trend.getConfidence() * 100) + "%");
```

## Failure Probability

```java
// Probability of failure within time horizon
double failProb30d = cbr.calculateFailureProbability(30 * 24);  // 30 days
double failProb90d = cbr.calculateFailureProbability(90 * 24);  // 90 days

System.out.println("Failure probability (30 days): " + (failProb30d * 100) + "%");
System.out.println("Failure probability (90 days): " + (failProb90d * 100) + "%");
```

## Recommendations

```java
String action = cbr.getRecommendedAction();
System.out.println("Recommended Action: " + action);

// Possible recommendations:
// - "Continue normal operation"
// - "Increase monitoring frequency"
// - "Schedule maintenance within 30 days"
// - "Plan maintenance within 14 days"
// - "Immediate maintenance required"
```

## Integration with Risk Analysis

### With Dynamic Simulation

```java
// Update equipment MTBF based on condition
DynamicRiskSimulator sim = new DynamicRiskSimulator("Condition-Based Risk");
sim.setBaseProductionRate(100.0);

// Use adjusted MTBF instead of baseline
double adjustedMTBF = cbr.calculateAdjustedMTBF();
sim.addEquipment("Compressor", adjustedMTBF, 72, 1.0);

DynamicRiskResult result = sim.runSimulation();
```

### With Real-time Monitor

```java
RealTimeRiskMonitor monitor = new RealTimeRiskMonitor("Platform", "P-001");
monitor.registerEquipment("C-200", "Compressor", cbr.getBaselineMTBF());

// Update health from CBR
double health = cbr.calculateHealthIndex();
monitor.updateEquipmentHealth("C-200", health);
```

## Use Cases

### 1. Predictive Maintenance Scheduling

```java
// Find all equipment with RUL < 30 days
List<ConditionBasedReliability> equipmentList = getAllEquipmentCBR();
List<MaintenanceTask> schedule = new ArrayList<>();

for (ConditionBasedReliability eq : equipmentList) {
    double rul = eq.estimateRUL();
    if (rul < 30 * 24) {  // Less than 30 days
        MaintenanceTask task = new MaintenanceTask();
        task.equipment = eq.getEquipmentId();
        task.priority = (rul < 7 * 24) ? "HIGH" : "MEDIUM";
        task.recommendedDate = calculateDate(rul * 0.8);  // 80% of RUL
        schedule.add(task);
    }
}

schedule.sort((a, b) -> Double.compare(a.rul, b.rul));
```

### 2. Spare Parts Planning

```java
// Estimate parts needed based on RUL
Map<String, Integer> sparesNeeded = new HashMap<>();

for (ConditionBasedReliability eq : equipmentList) {
    double failProb90d = eq.calculateFailureProbability(90 * 24);
    
    if (failProb90d > 0.3) {
        // High probability of needing spares
        String[] parts = getEquipmentParts(eq.getEquipmentId());
        for (String part : parts) {
            sparesNeeded.merge(part, 1, Integer::sum);
        }
    }
}
```

### 3. Risk-Based Inspection

```java
// Prioritize inspections based on health degradation
List<InspectionTask> inspections = new ArrayList<>();

for (ConditionBasedReliability eq : equipmentList) {
    double health = eq.calculateHealthIndex();
    double degradationRate = eq.calculateTrend("vibration").getRateOfChange();
    
    double priority = (1 - health) * 0.6 + degradationRate * 0.4;
    
    InspectionTask task = new InspectionTask();
    task.equipment = eq.getEquipmentId();
    task.priority = priority;
    task.inspectionType = determineInspectionType(eq);
    inspections.add(task);
}

inspections.sort((a, b) -> Double.compare(b.priority, a.priority));
```

## Output Format

### JSON Export

```java
String json = cbr.toJson();
```

Example output:
```json
{
  "equipmentId": "C-200",
  "equipmentName": "Export Compressor",
  "baselineMTBF": 12000,
  "operatingHours": 15000,
  "healthAssessment": {
    "healthIndex": 0.68,
    "adjustedMTBF": 8160,
    "estimatedRUL": 2450,
    "recommendedAction": "Schedule maintenance within 30 days"
  },
  "indicators": [
    {
      "name": "vibration",
      "currentValue": 8.5,
      "healthyValue": 5.0,
      "failureThreshold": 15.0,
      "normalizedValue": 0.35,
      "weight": 0.35,
      "trend": {
        "direction": "INCREASING",
        "ratePerMonth": 0.7,
        "timeToThreshold": 45
      }
    }
  ],
  "failureProbability": {
    "30days": 0.12,
    "90days": 0.35,
    "180days": 0.58
  }
}
```

## Best Practices

1. **Indicator Selection**: Choose indicators that directly correlate with failure modes

2. **Threshold Setting**: Use OEM recommendations and historical failure data

3. **Weight Calibration**: Adjust weights based on failure mode analysis (FMEA)

4. **Data Quality**: Ensure sensor calibration and data validation

5. **Baseline Updates**: Recalibrate after major maintenance

6. **Integration**: Connect to existing CMMS/EAM systems

## Standards References

- ISO 14224: Collection and exchange of reliability data
- ISO 17359: Condition monitoring and diagnostics
- ISO 13379: Condition monitoring approaches
- NORSOK Z-008: Risk based maintenance
- API 691: Risk-based machinery management
