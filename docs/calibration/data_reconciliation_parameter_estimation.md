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

For a measurement vector $\mathbf{y}$, diagonal covariance matrix
$\mathbf{V}=\mathrm{diag}(\sigma_i^2)$, and linear constraints
$\mathbf{A}\hat{\mathbf{x}}=0$, the engine uses the weighted least-squares
solution:

$$\hat{\mathbf{x}}=\mathbf{y}-\mathbf{V}\mathbf{A}^{T}(\mathbf{A}\mathbf{V}\mathbf{A}^{T})^{-1}\mathbf{A}\mathbf{y}$$

Each `ReconciliationVariable` stores one measured value and its positive
standard uncertainty $\sigma_i$ in the same engineering unit.

```java
DataReconciliationEngine recon = new DataReconciliationEngine();

// Add measured variables: name, value, standard uncertainty
recon.addVariable(new ReconciliationVariable("flow_in1", 5000.0, 100.0).setUnit("kg/hr"));
recon.addVariable(new ReconciliationVariable("flow_in2", 5100.0, 100.0).setUnit("kg/hr"));
recon.addVariable(new ReconciliationVariable("flow_out", 10200.0, 150.0).setUnit("kg/hr"));

// Mass balance: flow_in1 + flow_in2 - flow_out = 0
recon.addConstraint(new double[]{1.0, 1.0, -1.0});

ReconciliationResult result = recon.reconcile();
double chiSquare = result.getChiSquareStatistic();

// Iterate over all variables, or retrieve one by its unique name.
for (ReconciliationVariable variable : recon.getVariables()) {
    double reconciledValue = variable.getReconciledValue();
}
ReconciliationVariable inlet = recon.getVariable("flow_in1");

// Alternatively, allow the engine to eliminate at most one gross error.
ReconciliationResult grossErrorResult = recon.reconcileWithGrossErrorElimination(1);
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

See the example notebook: [data_reconciliation_parameter_estimation.ipynb](https://github.com/equinor/neqsim/blob/master/examples/notebooks/data_reconciliation_parameter_estimation.ipynb)

```python
import os
import sys
from pathlib import Path

import jpype


def find_neqsim_project_root():
    configured_root = os.environ.get("NEQSIM_PROJECT_ROOT")
    candidates = []
    if configured_root:
        candidates.append(Path(configured_root).resolve())

    current_directory = Path.cwd().resolve()
    candidates.extend([current_directory, *current_directory.parents])

    for candidate in candidates:
        has_project_file = (candidate / "pom.xml").exists()
        has_devtools = (
            candidate / "devtools" / "neqsim_dev_setup.py"
        ).exists()
        if has_project_file and has_devtools:
            return candidate

    raise RuntimeError(
        "Could not find the NeqSim project root. Set NEQSIM_PROJECT_ROOT."
    )


PROJECT_ROOT = find_neqsim_project_root()
sys.path.insert(0, str(PROJECT_ROOT / "devtools"))

from neqsim_dev_setup import neqsim_classes, neqsim_init

ns = neqsim_init(project_root=PROJECT_ROOT, recompile=False, verbose=False)
ns = neqsim_classes(ns)

DataReconciliationEngine = ns.JClass(
    "neqsim.process.util.reconciliation.DataReconciliationEngine"
)
ReconciliationVariable = ns.JClass(
    "neqsim.process.util.reconciliation.ReconciliationVariable"
)
BatchParameterEstimator = ns.JClass(
    "neqsim.process.calibration.BatchParameterEstimator"
)
HashMap = ns.JClass("java.util.HashMap")

recon = DataReconciliationEngine()
recon.addVariable(ReconciliationVariable("feed", 1000.0, 20.0))
recon.addVariable(ReconciliationVariable("gas", 600.0, 15.0))
recon.addVariable(ReconciliationVariable("liquid", 390.0, 10.0))
recon.addConstraint([1.0, -1.0, -1.0])
reconciliation_result = recon.reconcile()

assert reconciliation_result.isConverged()
assert reconciliation_result.getChiSquareStatistic() >= 0.0
assert recon.getVariable("feed").getName() == "feed"

for variable in recon.getVariables():
    print(variable.getName(), variable.getReconciledValue())

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
