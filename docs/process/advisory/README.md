---
title: Advisory Systems and Prediction Results
description: This module provides infrastructure for look-ahead predictions that support real-time advisory systems.
---

# Advisory Systems and Prediction Results

This module provides infrastructure for look-ahead predictions that support real-time advisory systems.

## Overview

Modern process operations benefit from predictive capabilities:

- **Look-Ahead Simulation**: Predict process behavior minutes to hours ahead
- **Uncertainty Quantification**: Confidence intervals on predictions
- **Constraint Monitoring**: Early warning of limit violations
- **Operator Guidance**: Actionable recommendations

## Classes

### PredictionResult

Structured output from predictive simulations for advisory systems.

#### Key Features

- **Time Horizon**: Predictions from minutes to days ahead
- **Uncertainty Bounds**: 95% confidence intervals on values
- **Violation Detection**: Identify which limits may be exceeded
- **Explainability**: Human-readable prediction drivers
- **Recommendations**: Suggested operator actions

#### Usage Example

```java
// Run look-ahead simulation (conceptual - requires integration)
PredictionResult prediction = new PredictionResult(
    Duration.ofHours(2), 
    "Base Case"
);

// Add predicted values with uncertainty
prediction.addPredictedValue(
    "separator.pressure",
    new PredictedValue(52.5, 2.1, "bara")  // mean, stddev, unit
);

prediction.addPredictedValue(
    "compressor.power",
    new PredictedValue(4500.0, 150.0, "kW")
);

// Check for constraint violations
if (prediction.hasViolations()) {
    System.out.println(prediction.getViolationSummary());
}

// Get operator guidance
String advice = prediction.getAdvisoryRecommendation();
System.out.println(advice);
```

### PredictedValue

A predicted value with uncertainty quantification.

#### Constructors

```java
// With standard deviation (auto-calculates 95% CI)
PredictedValue value = new PredictedValue(
    50.0,   // mean
    2.5,    // standard deviation
    "bara"  // unit
);

// With explicit confidence bounds
PredictedValue value = new PredictedValue(
    50.0,   // mean
    45.0,   // lower 95% bound
    55.0,   // upper 95% bound
    "bara", // unit
    0.95    // confidence
);

// Deterministic (no uncertainty)
PredictedValue value = PredictedValue.deterministic(50.0, "bara");
```

#### Accessors

```java
double mean = value.getMean();
double stdDev = value.getStandardDeviation();
double lower = value.getLower95();
double upper = value.getUpper95();
String unit = value.getUnit();
double confidence = value.getConfidence();
```

### ConstraintViolation

Predicted constraint violation with timing and severity.

#### Severity Levels

| Level | Description | Response |
|-------|-------------|----------|
| `LOW` | Minor deviation expected | Monitor |
| `MEDIUM` | Significant deviation | Plan response |
| `HIGH` | Equipment limits may be exceeded | Act soon |
| `CRITICAL` | Safety limits may be exceeded | Immediate action |

#### Usage Example

```java
ConstraintViolation violation = new ConstraintViolation(
    "HP Separator Pressure Limit",  // constraint name
    "separator.pressure",            // variable
    85.0,                            // predicted value
    80.0,                            // limit value
    "bara",                          // unit
    Duration.ofMinutes(45),          // time to violation
    Severity.HIGH                    // severity
);

violation.setSuggestedAction("Reduce inlet flow or open bypass");

String description = violation.getDescription();
// "HP Separator Pressure Limit: separator.pressure expected to 
//  reach 85.00 bara (limit: 80.00) in 45 min"
```

## Prediction Status

| Status | Description |
|--------|-------------|
| `SUCCESS` | Prediction completed normally |
| `WARNING` | Completed but violations detected |
| `FAILED` | Prediction did not converge |
| `DATA_QUALITY_ISSUE` | Input data problems detected |

## Integration Patterns

### With Real-Time Data

```java
// Pseudo-code for real-time integration
while (running) {
    // Update process with live data
    updateFromOPC(process, liveData);
    
    // Run prediction
    PredictionResult prediction = runLookAhead(process, Duration.ofHours(1));
    
    // Send to advisory system
    if (prediction.hasViolations()) {
        sendAlert(prediction.getAdvisoryRecommendation());
    }
    
    // Wait for next cycle
    Thread.sleep(60000);
}
```

### With MPC Integration

```java
// Provide prediction to MPC controller
PredictionResult prediction = runLookAhead(process, mpcHorizon);

// Extract predicted values for MPC
Map<String, PredictedValue> values = prediction.getAllPredictedValues();
for (Map.Entry<String, PredictedValue> entry : values.entrySet()) {
    mpc.setDisturbanceForecast(
        entry.getKey(), 
        entry.getValue().getMean()
    );
}
```

## Assumptions and Documentation

```java
PredictionResult prediction = new PredictionResult(Duration.ofHours(2));

// Document assumptions used
prediction.addAssumption("Inlet conditions remain constant");
prediction.addAssumption("No equipment trips");
prediction.addAssumption("Cooling water available at design temperature");

// Set explanation for operators
prediction.setExplanation(
    "Pressure rising due to increasing gas-oil ratio in feed"
);

// Set confidence level
prediction.setOverallConfidence(0.85);
```

## Best Practices

1. **Appropriate Horizons**: Match prediction horizon to decision timescale
2. **Uncertainty Communication**: Always show confidence bounds
3. **Actionable Recommendations**: Provide specific suggested actions
4. **Assumption Transparency**: Document all prediction assumptions
5. **Status Monitoring**: Track prediction quality over time

## Related Documentation

- [Physics Constraint Validation](../ml/) - Validate predictions against physics
- [Safety Scenarios](../safety/) - What-if analysis
- [MPC Integration](../../integration/mpc_integration) - Real-time control integration
