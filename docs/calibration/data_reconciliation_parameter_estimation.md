---
title: "Data Reconciliation and Parameter Estimation Workflow"
description: "Complete guide to matching NeqSim process models to plant data using DataReconciliationEngine and BatchParameterEstimator. Covers measurement reconciliation, gross error detection, Levenberg-Marquardt parameter fitting, and online tracking with EnKF."
---

# Data Reconciliation and Parameter Estimation

This guide describes the complete workflow for tuning a NeqSim process model to match plant measurements — from raw DCS data to calibrated model parameters.

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                    Plant / DCS / Historian                        │
│   Measurements: temperatures, pressures, flow rates, power       │
└──────────────────────────┬───────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│  Step 1: SteadyStateDetector                                     │
│  Confirm measurements are at steady state before reconciliation  │
└──────────────────────────┬───────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│  Step 2: DataReconciliationEngine                                │
│  - Close mass/energy balances (WLS)                              │
│  - Detect gross errors (normalized residual test)                │
│  - Remove bad sensors, re-reconcile                              │
└──────────────────────────┬───────────────────────────────────────┘
                           │  Reconciled measurements
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│  Step 3: BatchParameterEstimator                                 │
│  - Levenberg-Marquardt optimization                              │
│  - Tune: efficiency, UA, k-values, heat transfer coefficients    │
│  - Output: parameter estimates ± uncertainties, R², chi-square   │
└──────────────────────────┬───────────────────────────────────────┘
                           │  Tuned parameters
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│  Step 4: Update ProcessSystem                                    │
│  Apply fitted parameters, run model as calibrated predictor      │
└──────────────────────────────────────────────────────────────────┘
```

## Java Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `DataReconciliationEngine` | `neqsim.process.util.reconciliation` | WLS data reconciliation with gross error detection |
| `SteadyStateDetector` | `neqsim.process.util.reconciliation` | Confirms steady state before reconciliation |
| `BatchParameterEstimator` | `neqsim.process.calibration` | Offline batch fitting via Levenberg-Marquardt |
| `EnKFParameterEstimator` | `neqsim.process.calibration` | Online tracking via Ensemble Kalman Filter |
| `BatchResult` | `neqsim.process.calibration` | Result container with statistics |
| `ProcessSimulationFunction` | `neqsim.process.calibration` | Bridges ProcessSystem to L-M optimizer |

## Step-by-Step Usage

### 1. Data Reconciliation

```java
DataReconciliationEngine recon = new DataReconciliationEngine();

// Add measured variables: name, value, uncertainty
recon.addVariable("flow_in1", 5000.0, 100.0);
recon.addVariable("flow_in2", 5100.0, 100.0);
recon.addVariable("flow_out", 10200.0, 150.0);

// Mass balance: flow_in1 + flow_in2 - flow_out = 0
recon.addConstraint(new double[]{1.0, 1.0, -1.0});

ReconciliationResult result = recon.reconcile();
// Or with automatic gross error elimination:
// ReconciliationResult result = recon.reconcileWithGrossErrorElimination();
```

### 2. Batch Parameter Estimation

```java
// Build your process model
ProcessSystem process = buildYourProcess();

// Create estimator
BatchParameterEstimator estimator = new BatchParameterEstimator(process);

// Define tunable parameters: path, unit, lower, upper, initial_guess
estimator.addTunableParameter("comp.polytropicEfficiency", "", 0.50, 0.95, 0.65);

// Define measurements: path, unit, measurement_std_dev
estimator.addMeasuredVariable("comp.outletStream.temperature", "K", 0.5);

// Add data points from plant (or reconciled data)
for (PlantRecord record : plantData) {
    Map<String, Double> conditions = new HashMap<>();
    conditions.put("comp.outletPressure", record.getDischargePressure());

    Map<String, Double> measurements = new HashMap<>();
    measurements.put("comp.outletStream.temperature", record.getOutletTemp());

    estimator.addDataPoint(conditions, measurements);
}

// Solve
estimator.setMaxIterations(100);
BatchResult result = estimator.solve();

// Use results
result.printSummary();
double efficiency = result.getEstimate(0);
double uncertainty = result.getUncertainty(0);
double rSquared = result.getRSquared();
```

### 3. Apply Tuned Parameters

```java
// Update process with estimated values
Map<String, Double> params = result.toMap();
for (Map.Entry<String, Double> entry : params.entrySet()) {
    // Apply each parameter to the process...
    comp.setPolytropicEfficiency(entry.getValue());
}

// Model is now calibrated — use for prediction
process.run();
```

## Property Path Conventions

The `BatchParameterEstimator` uses reflection-based property paths to access equipment getters and setters:

| Path Pattern | Resolution |
|-------------|-----------|
| `"comp.polytropicEfficiency"` | `getUnit("comp").setPolytropicEfficiency(double)` |
| `"comp.outletPressure"` | `getUnit("comp").setOutletPressure(double)` |
| `"comp.outletStream.temperature"` | `getUnit("comp").getOutletStream().getTemperature()` |
| `"heater1.outletTemperature"` | `getUnit("heater1").setOutletTemperature(double)` |
| `"mixer.outletStream.temperature"` | `getUnit("mixer").getOutletStream().getTemperature()` |

### Compatible Equipment Parameters

| Equipment | Tunable Parameters | Measurable Outputs |
|-----------|-------------------|-------------------|
| Compressor | `polytropicEfficiency`, `isentropicEfficiency`, `outletPressure` | `outletStream.temperature`, `outletStream.pressure`, `power` |
| Heater/Cooler | `outletTemperature`, `duty` | `outletStream.temperature`, `duty` |
| Valve | `outletPressure` | `outletStream.temperature` |

**Limitation:** Only single-argument `(double)` setters work as condition paths. Two-argument setters like `setFlowRate(double, String)` are not accessible through the reflection path.

## Python Usage

See the example notebook: [data_reconciliation_parameter_estimation.ipynb](../../examples/notebooks/data_reconciliation_parameter_estimation.ipynb)

```python
from neqsim_dev_setup import neqsim_init
ns = neqsim_init(project_root="path/to/neqsim2")

import jpype
BatchParameterEstimator = jpype.JClass("neqsim.process.calibration.BatchParameterEstimator")
HashMap = jpype.JClass("java.util.HashMap")

estimator = BatchParameterEstimator(process)
estimator.addTunableParameter("comp.polytropicEfficiency", "", 0.50, 0.95, 0.65)
estimator.addMeasuredVariable("comp.outletStream.temperature", "K", 0.3)

for p_out, t_meas in plant_data:
    conditions = HashMap()
    conditions.put("comp.outletPressure", jpype.JDouble(p_out))
    measurements = HashMap()
    measurements.put("comp.outletStream.temperature", jpype.JDouble(t_meas))
    estimator.addDataPoint(conditions, measurements)

result = estimator.solve()
print(f"Efficiency: {result.getEstimate(0):.4f} ± {result.getUncertainty(0):.4f}")
```

## Batch vs Online Estimation

| Feature | BatchParameterEstimator | EnKFParameterEstimator |
|---------|------------------------|----------------------|
| Algorithm | Levenberg-Marquardt | Ensemble Kalman Filter |
| Data | Historical / batch | Live streaming |
| Parameters | Many | Any (≤2 measurements) |
| Uncertainty | Covariance matrix | Ensemble spread |
| Use case | Periodic calibration | Continuous tracking |
| Convergence | Global (may take 5-100 iterations) | Sequential (one step per measurement) |

## Integration Test Verification

The `BatchParameterEstimator.solve()` has been verified end-to-end:

- **Single-parameter test**: Recovers compressor polytropic efficiency (true=0.75, initial guess=0.60) in 5 iterations with R²=1.000000
- **Two-parameter test**: Recovers two heater temperatures simultaneously

Test class: `BatchParameterEstimatorIntegrationTest` (tagged `@Tag("slow")`)

Run with: `mvnw test -Dtest=BatchParameterEstimatorIntegrationTest -DexcludedTestGroups=none`
