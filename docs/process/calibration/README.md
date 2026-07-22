---
title: Process Calibration Framework
description: The `neqsim.process.calibration` package provides tools for online parameter estimation and calibration of process simulations using real-time measurement data. This is essential for digital twin appl...
---

# Process Calibration Framework

The `neqsim.process.calibration` package provides tools for online parameter estimation and calibration of process simulations using real-time measurement data. This is essential for digital twin applications where simulation model parameters must be continuously updated to match actual plant behavior.

## Table of Contents

- [Overview](#overview)
- [Key Components](#key-components)
- [EnKF Parameter Estimator](#enkf-parameter-estimator)
- [Estimation Test Harness](#estimation-test-harness)
- [Usage Patterns](#usage-patterns)
- [Well Routing Example](#well-routing-example)
- [API Reference](#api-reference)
- [Best Practices](#best-practices)

---

## Overview

When deploying NeqSim process simulations as digital twins or soft sensors, model parameters (heat transfer coefficients, valve Cv values, fouling factors, etc.) often need to be tuned to match real plant data. The calibration framework provides:

1. **Online Estimation**: Sequential algorithms that update parameter estimates as new measurements arrive
2. **Uncertainty Quantification**: Confidence intervals on estimated parameters
3. **Anomaly Detection**: Identification of measurement outliers or model mismatches
4. **Pre-deployment Testing**: Validation framework to test estimation algorithms before deployment

### When to Use This Framework

| Scenario | Recommended Approach |
|----------|---------------------|
| Single parameter tuning | `Adjuster` class |
| Multiple parameters, batch data | `BatchParameterEstimator` (Levenberg-Marquardt) |
| Live streaming data, uncertainty needed | `EnKFParameterEstimator` |
| Validate before deployment | `EstimationTestHarness` |

---

## Key Components

### Integration with Existing NeqSim APIs

The calibration framework integrates seamlessly with existing NeqSim components:

| Component | Purpose | Usage in Calibration |
|-----------|---------|---------------------|
| `ProcessVariableAccessor` | Path-based access to any process variable | Read measurements, write parameters |
| `ProcessSensitivityAnalyzer` | Compute sensitivities/Jacobians | Optional: improve convergence |
| `ManipulatedVariable` | Variable with bounds and rate limits | Pattern for parameter constraints |
| `CalibrationResult` | Standard result container | Return type for API compatibility |
| `OnlineCalibrator` | Basic online calibration | Base patterns for streaming |
| `BatchParameterEstimator` | Levenberg-Marquardt batch optimization | Batch/historical data fitting |
| `BatchResult` | Batch estimation result container | Contains estimates, uncertainties, statistics |

### Path Notation

Variables are accessed using dot-notation paths:

```
Equipment.property
Equipment.stream.property
Equipment.outputPort.fluid.property
```

Examples:
- `"Pipe1.heatTransferCoefficient"` - heat transfer coefficient of Pipe1
- `"HPManifold.outletStream.temperature"` - outlet temperature of HP manifold
- `"Separator.gasOutStream.pressure"` - gas outlet pressure of separator

---

## Batch Parameter Estimator

The `BatchParameterEstimator` uses the **Levenberg-Marquardt** algorithm for batch (offline) parameter estimation. This is ideal for:

- **Historical/batch data**: Processes all data points simultaneously
- **Multiple parameters**: Efficiently estimates many parameters at once
- **Uncertainty quantification**: Provides parameter standard deviations from covariance matrix
- **Best-fit optimization**: Minimizes weighted sum of squared residuals

### Algorithm Overview

The Levenberg-Marquardt algorithm combines gradient descent with Gauss-Newton methods:

$$(\mathbf{J}^T \mathbf{W} \mathbf{J} + \lambda \mathbf{I}) \Delta\vec{p} = \mathbf{J}^T \mathbf{W} \vec{r}$$

where:
- $\mathbf{J}$ = Jacobian matrix of partial derivatives
- $\mathbf{W}$ = weight matrix (diagonal, $W_{ii} = 1/\sigma_i^2$)
- $\lambda$ = damping parameter (adapts during iteration)
- $\vec{r}$ = residual vector

The damping parameter adapts:
- Large $\lambda$ → gradient descent (slow but stable)
- Small $\lambda$ → Gauss-Newton (fast near minimum)

### Basic Usage

```java
import neqsim.process.calibration.BatchParameterEstimator;
import neqsim.process.calibration.BatchResult;
import neqsim.process.processmodel.ProcessSystem;
import java.util.Map;
import java.util.HashMap;

// 1. Create your process system
ProcessSystem process = buildYourProcess();

// 2. Create the batch estimator
BatchParameterEstimator estimator = new BatchParameterEstimator(process);

// 3. Define tunable parameters
estimator.addTunableParameter(
    "Pipe1.heatTransferCoefficient",  // path to parameter
    "W/(m2·K)",                        // unit
    1.0,                               // minimum bound
    100.0,                             // maximum bound
    15.0                               // initial guess
);

estimator.addTunableParameter(
    "Pipe2.heatTransferCoefficient",
    "W/(m2·K)",
    1.0, 100.0, 15.0
);

// 4. Define measured variables
estimator.addMeasuredVariable(
    "Manifold.outletStream.temperature",  // path to measurement
    "C",                                   // unit
    0.5                                    // measurement noise std dev
);

// 5. Add historical data points
for (HistoricalRecord record : historicalData) {
    Map<String, Double> conditions = new HashMap<>();
    conditions.put("feedStream.flowRate", record.getFlowRate());
    conditions.put("feedStream.temperature", record.getInletTemp());
    
    Map<String, Double> measurements = new HashMap<>();
    measurements.put("Manifold.outletStream.temperature", record.getOutletTemp());
    
    estimator.addDataPoint(conditions, measurements);
}

// 6. Configure and solve
estimator.setMaxIterations(100);
BatchResult result = estimator.solve();

// 7. Use results
result.printSummary();
double[] estimates = result.getEstimates();
double[] uncertainties = result.getUncertainties();
double[] lowerCI = result.getConfidenceIntervalLower();
double[] upperCI = result.getConfidenceIntervalUpper();

// 8. Get statistics
double chiSquare = result.getChiSquare();
double rmse = result.getRMSE();
double rSquared = result.getRSquared();
```

### Configuration Options

| Method | Description | Default |
|--------|-------------|---------|
| `setMaxIterations(int)` | Maximum L-M iterations | 100 |
| `setUseAnalyticalJacobian(boolean)` | Use ProcessSensitivityAnalyzer for derivatives | false |

### Result Object: `BatchResult`

| Method | Returns | Description |
|--------|---------|-------------|
| `getEstimates()` | `double[]` | Optimized parameter values |
| `getUncertainties()` | `double[]` | Standard deviations from covariance |
| `getConfidenceIntervalLower()` | `double[]` | 95% CI lower bounds |
| `getConfidenceIntervalUpper()` | `double[]` | 95% CI upper bounds |
| `getChiSquare()` | `double` | Sum of squared weighted residuals |
| `getRMSE()` | `double` | Root mean square error |
| `getRSquared()` | `double` | Coefficient of determination |
| `isConverged()` | `boolean` | Whether optimization converged |
| `getIterations()` | `int` | Number of iterations used |
| `getCovarianceMatrix()` | `double[][]` | Parameter covariance matrix |
| `getCorrelationMatrix()` | `double[][]` | Parameter correlation matrix |
| `toCalibrationResult()` | `CalibrationResult` | Convert to standard format |

### Jacobian Computation

The `BatchParameterEstimator` supports two methods for computing the Jacobian:

1. **Numerical differentiation** (default): Uses Romberg extrapolation via `NumericalDerivative`
2. **Analytical Jacobian**: Uses `ProcessSensitivityAnalyzer` which can:
   - Reuse Broyden Jacobians from recycle convergence
   - Apply chain rule through process structure
   - Fall back to finite differences only when necessary

To enable analytical Jacobian:

```java
estimator.setUseAnalyticalJacobian(true);
```

### Monte Carlo Uncertainty Analysis

For more robust uncertainty quantification, run Monte Carlo simulation:

```java
BatchResult result = estimator.solve();
estimator.runMonteCarloSimulation(1000);  // 1000 MC runs
```

### Comparison: BatchParameterEstimator vs EnKFParameterEstimator

| Aspect | BatchParameterEstimator | EnKFParameterEstimator |
|--------|------------------------|------------------------|
| **Data** | Batch/historical | Streaming/sequential |
| **Best for** | Offline calibration | Live digital twins |
| **Algorithm** | Levenberg-Marquardt | Ensemble Kalman Filter |
| **Uncertainty** | Covariance matrix | Ensemble-based |
| **Drift tracking** | No | Yes |
| **Convergence** | Full optimization | Sequential updates |

---

## EnKF Parameter Estimator

The `EnKFParameterEstimator` implements the **Ensemble Kalman Filter** algorithm for sequential parameter estimation. This is ideal for:

- **Live data streams**: Processes one measurement set at a time
- **Nonlinear models**: Handles nonlinearity through ensemble propagation
- **Uncertainty quantification**: Provides confidence intervals automatically
- **Drift detection**: Tracks parameter changes over time

### Algorithm Overview

The Ensemble Kalman Filter maintains an ensemble (collection) of parameter estimates. Each update step:

1. **Prediction**: Add process noise to ensemble members (accounts for parameter drift)
2. **Simulation**: Run the process model for each ensemble member
3. **Compute Covariances**: Calculate cross-covariance between parameters and outputs
4. **Kalman Gain**: Compute optimal update gain
5. **Update**: Adjust ensemble based on measurement innovation
6. **Statistics**: Calculate mean and standard deviation of ensemble

```
┌─────────────────────────────────────────────────────────────────┐
│                     EnKF Update Cycle                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌──────────┐     ┌──────────┐     ┌──────────┐               │
│   │ Ensemble │────▶│ Simulate │────▶│Predictions│              │
│   │ Members  │     │  Model   │     │   y_i     │              │
│   └──────────┘     └──────────┘     └─────┬─────┘              │
│        │                                   │                    │
│        │                                   ▼                    │
│        │           ┌──────────────────────────────────┐        │
│        │           │ Compute Covariances              │        │
│        │           │ Pxy = Cov(params, predictions)   │        │
│        │           │ Pyy = Cov(predictions) + R       │        │
│        │           └──────────────┬───────────────────┘        │
│        │                          │                             │
│        │                          ▼                             │
│        │           ┌──────────────────────────┐                │
│        │           │ Kalman Gain K = Pxy/Pyy  │                │
│        │           └──────────────┬───────────┘                │
│        │                          │                             │
│        ▼                          ▼                             │
│   ┌──────────────────────────────────────────────────┐         │
│   │ Update: param_new = param + K * (y_meas - y_pred)│         │
│   └──────────────────────────────────────────────────┘         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Basic Usage

```java
import neqsim.process.calibration.EnKFParameterEstimator;
import neqsim.process.calibration.EnKFParameterEstimator.EnKFResult;
import neqsim.process.processmodel.ProcessSystem;
import java.util.Map;
import java.util.HashMap;

// 1. Create your process system
ProcessSystem process = buildYourProcess();

// 2. Create the estimator
EnKFParameterEstimator estimator = new EnKFParameterEstimator(process);

// 3. Define tunable parameters (what you want to estimate)
estimator.addTunableParameter(
    "Pipe1.heatTransferCoefficient",  // path to parameter
    "W/(m2·K)",                        // unit
    1.0,                               // minimum bound
    100.0,                             // maximum bound
    15.0                               // initial guess
);

estimator.addTunableParameter(
    "Pipe2.heatTransferCoefficient",
    "W/(m2·K)",
    1.0, 100.0, 15.0
);

// 4. Define measured variables (what you observe)
estimator.addMeasuredVariable(
    "Manifold.outletStream.temperature",  // path to measurement
    "C",                                   // unit
    0.5                                    // measurement noise std dev
);

// 5. Configure and initialize
estimator.setProcessNoise(0.3);        // parameter drift rate
estimator.setMaxChangePerUpdate(5.0);  // rate limiting
estimator.initialize(50, 42);          // 50 ensemble members, seed 42

// 6. In your live data loop
while (running) {
    // Get measurements from plant
    Map<String, Double> measurements = new HashMap<>();
    measurements.put("Manifold.outletStream.temperature", readFromPlant());
    
    // Update estimates
    EnKFResult result = estimator.update(measurements);
    
    // Use results
    double[] estimates = result.getEstimates();
    double[] uncertainties = result.getUncertainties();
    double rmse = result.getRMSE();
    
    if (result.isAnomalyDetected()) {
        System.out.println("Warning: Measurement anomaly detected!");
    }
}
```

### Configuration Options

| Method | Description | Default |
|--------|-------------|---------|
| `setProcessNoise(double)` | Parameter drift rate (std dev per step) | 0.3 |
| `setMaxChangePerUpdate(double)` | Maximum parameter change per update | 5.0 |
| `initialize(int, long)` | Ensemble size and random seed | Required |

### Result Object: `EnKFResult`

| Method | Returns | Description |
|--------|---------|-------------|
| `getEstimates()` | `double[]` | Current parameter estimates |
| `getUncertainties()` | `double[]` | Standard deviations (1-sigma) |
| `getConfidenceIntervalLower()` | `double[]` | 95% CI lower bounds |
| `getConfidenceIntervalUpper()` | `double[]` | 95% CI upper bounds |
| `getMeasurements()` | `double[]` | Measurements used |
| `getPredictions()` | `double[]` | Model predictions |
| `getRMSE()` | `double` | Prediction RMSE |
| `isAnomalyDetected()` | `boolean` | True if measurement anomaly |
| `toCalibrationResult(String[])` | `CalibrationResult` | Convert to standard format |

---

## Estimation Test Harness

The `EstimationTestHarness` provides a framework for validating estimation algorithms before deploying them with real plant data. It generates synthetic measurements using known "ground truth" parameter values and verifies the estimator can recover these values.

### Why Pre-deployment Testing?

Before deploying an estimator to production:

1. **Verify Convergence**: Ensure the estimator finds the correct parameters
2. **Test Robustness**: Check performance under different noise levels
3. **Validate Uncertainty**: Verify confidence intervals have correct coverage
4. **Check Drift Tracking**: Test detection of slowly changing parameters
5. **Statistical Validation**: Monte Carlo testing for confidence

### Basic Usage

```java
import neqsim.process.calibration.EstimationTestHarness;
import neqsim.process.calibration.EstimationTestHarness.TestReport;
import neqsim.process.calibration.EnKFParameterEstimator;

// 1. Create test harness with your process
EstimationTestHarness harness = new EstimationTestHarness(processSystem);
harness.setSeed(42);  // for reproducibility

// 2. Define parameters with TRUE values (ground truth)
harness.addParameter(
    "Pipe1.heatTransferCoefficient",
    12.0,    // TRUE value (what we want to recover)
    1.0,     // min bound
    50.0     // max bound
);

harness.addParameter("Pipe2.heatTransferCoefficient", 18.0, 1.0, 50.0);

// 3. Define measurements
harness.addMeasurement(
    "Manifold.outletStream.temperature",
    "C",
    0.5    // measurement noise std dev
);

// 4. Create and configure the estimator to test
EnKFParameterEstimator estimator = new EnKFParameterEstimator(processSystem);
estimator.addTunableParameter("Pipe1.heatTransferCoefficient", "W/(m2·K)", 1.0, 50.0, 20.0);
estimator.addTunableParameter("Pipe2.heatTransferCoefficient", "W/(m2·K)", 1.0, 50.0, 20.0);
estimator.addMeasuredVariable("Manifold.outletStream.temperature", "C", 0.5);
estimator.initialize(50, 42);

// 5. Run convergence test
TestReport report = harness.runConvergenceTest(estimator, 100);

// 6. Evaluate results
report.printSummary();

if (report.passes(1.0, 0.9, 50)) {  // RMSE<1.0, >90% coverage, converge in 50 steps
    System.out.println("✓ Ready for deployment!");
} else {
    System.out.println("✗ Needs tuning");
}
```

### Test Types

#### 1. Convergence Test

Verifies the estimator converges to true parameter values:

```java
TestReport report = harness.runConvergenceTest(estimator, numSteps);
TestReport report = harness.runConvergenceTest(estimator, numSteps, noiseMultiplier, progressCallback);
```

#### 2. Noise Robustness Test

Tests performance across multiple noise levels:

```java
double[] noiseLevels = {0.5, 1.0, 2.0, 5.0};
Map<Double, TestReport> reports = harness.runNoiseRobustnessTest(
    estimator, 50, noiseLevels
);

for (Map.Entry<Double, TestReport> entry : reports.entrySet()) {
    System.out.printf("Noise %.1fx: RMSE=%.4f%n", 
        entry.getKey(), entry.getValue().getRMSE());
}
```

#### 3. Drift Tracking Test

Tests ability to track slowly changing parameters:

```java
TestReport report = harness.runDriftTrackingTest(
    estimator,
    100,    // num steps
    0,      // which parameter drifts (index)
    0.1     // drift rate per step
);
```

#### 4. Monte Carlo Validation

Statistical validation across many trials:

```java
MonteCarloReport mcReport = harness.runMonteCarloValidation(
    () -> {
        EnKFParameterEstimator est = new EnKFParameterEstimator(process);
        // configure estimator...
        return est;
    },
    100,   // number of trials
    50     // steps per trial
);

mcReport.printSummary();
// Mean RMSE: 0.5432 ± 0.1234
// 95th percentile RMSE: 0.7890
// Mean coverage: 94.5%
// Success rate: 98.0%
```

### TestReport Metrics

| Metric | Description | Target |
|--------|-------------|--------|
| `getRMSE()` | Root mean square error of estimates | < 1-2% of parameter range |
| `getMeanAbsoluteError()` | Average absolute error | < 1-2% of parameter range |
| `getMaxError()` | Maximum error across parameters | < 5% of range |
| `getStepsToConverge()` | Steps until <10% relative error | < 50 typically |
| `getCoverageRate()` | Fraction of true values in 95% CI | > 0.90 |

---

## Usage Patterns

### Pattern 1: Simple Single-Manifold Calibration

```java
// For a simple case with one output measurement
EnKFParameterEstimator estimator = new EnKFParameterEstimator(process)
    .addTunableParameter("Pipe1.heatTransferCoefficient", "W/(m2·K)", 1, 100, 15)
    .addTunableParameter("Pipe2.heatTransferCoefficient", "W/(m2·K)", 1, 100, 15)
    .addMeasuredVariable("Manifold.temperature", "C", 0.5);

estimator.initialize(30, 42);
```

### Pattern 2: Multi-Manifold with Routing Changes

When you have fewer measurements than parameters, change routing over time to improve observability (see [Well Routing Example](#well-routing-example)).

### Pattern 3: Production System with Validation

```java
// 1. Build process
ProcessSystem process = buildProductionNetwork();

// 2. Create and configure estimator
EnKFParameterEstimator estimator = new EnKFParameterEstimator(process);
configureEstimator(estimator);
estimator.initialize(50, 42);

// 3. Validate before deployment
EstimationTestHarness harness = new EstimationTestHarness(process);
configureHarness(harness);

TestReport report = harness.runConvergenceTest(estimator, 100);
if (!report.passes(1.0, 0.9, 50)) {
    throw new RuntimeException("Estimator validation failed!");
}

// 4. Deploy
estimator.reset();  // Fresh start for production
while (running) {
    Map<String, Double> meas = getPlantMeasurements();
    EnKFResult result = estimator.update(meas);
    // Use estimates...
}
```

### Pattern 4: Converting to CalibrationResult

For compatibility with other NeqSim calibration APIs:

```java
EnKFResult result = estimator.update(measurements);

// Convert to standard CalibrationResult
CalibrationResult calResult = result.toCalibrationResult(
    estimator.getParameterNames()
);

// Or get directly from estimator
CalibrationResult calResult = estimator.toCalibrationResult();
```

---

## Well Routing Example

This section provides a complete example of using the calibration framework for a multi-well production network where well routing changes are used to improve parameter observability.

### Problem Statement

Consider a production network with:
- 8 wells, each with its own subsea pipeline
- 2 manifolds: High Pressure (HP) and Low Pressure (LP)
- Wells can be routed to either manifold via 3-way valves
- **Goal**: Estimate individual heat transfer coefficients for each pipeline

**Challenge**: With only 2 temperature measurements (HP and LP manifold outlets) but 8 unknown parameters, the system is underdetermined. However, by systematically changing routing configurations over time, we accumulate enough independent equations to solve for all parameters.

### Mathematical Concept

Each routing configuration provides a different set of equations:

```
Configuration 1: Wells 1-4 → HP, Wells 5-8 → LP
  T_HP = f(U1, U2, U3, U4)
  T_LP = f(U5, U6, U7, U8)

Configuration 2: Wells 1,2,5,6 → HP, Wells 3,4,7,8 → LP
  T_HP = f(U1, U2, U5, U6)
  T_LP = f(U3, U4, U7, U8)

Configuration 3: Only Well 1 → HP, rest → LP
  T_HP = f(U1)  ← Very informative for U1!
  T_LP = f(U2, U3, U4, U5, U6, U7, U8)
```

The EnKF naturally accumulates information across these configurations.

### Complete Example Code

See the full implementation below:

```java
package neqsim.process.calibration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Complete example: Multi-well heat transfer estimation with dynamic routing.
 */
public class WellRoutingEstimationExample {
    
    private static final int NUM_WELLS = 8;
    private static final double SEA_TEMPERATURE = 4.0;  // °C
    
    // Process equipment
    private ProcessSystem process;
    private PipeBeggsAndBrills[] pipes;
    private Splitter[] splitters;
    private Mixer hpManifold;
    private Mixer lpManifold;
    
    // Routing state (0 = HP, 1 = LP)
    private int[] currentRouting = new int[NUM_WELLS];
    
    // True values (for testing)
    private double[] trueHeatTransferCoeffs = {12.0, 15.0, 18.0, 14.0, 16.0, 20.0, 13.0, 17.0};
    
    /**
     * Creates well fluid.
     */
    private SystemInterface createWellFluid(double temperature, double pressure) {
        SystemInterface fluid = new SystemSrkEos(273.15 + temperature, pressure);
        fluid.addComponent("methane", 0.82);
        fluid.addComponent("ethane", 0.09);
        fluid.addComponent("propane", 0.05);
        fluid.addComponent("n-butane", 0.025);
        fluid.addComponent("n-pentane", 0.015);
        fluid.setMixingRule("classic");
        return fluid;
    }
    
    /**
     * Builds the production network.
     */
    public void buildNetwork() {
        double wellTemp = 70.0;
        double[] pressures = {100.0, 95.0, 92.0, 88.0, 96.0, 90.0, 94.0, 86.0};
        double[] flowRates = {50000, 45000, 55000, 48000, 52000, 46000, 51000, 44000};
        double[] pipeLengths = {8000, 8500, 7500, 9000, 7800, 8200, 8800, 9200};
        
        process = new ProcessSystem();
        pipes = new PipeBeggsAndBrills[NUM_WELLS];
        splitters = new Splitter[NUM_WELLS];
        
        for (int i = 0; i < NUM_WELLS; i++) {
            // Create well stream
            SystemInterface fluid = createWellFluid(wellTemp, pressures[i]);
            Stream wellStream = new Stream("Well" + (i + 1), fluid);
            wellStream.setFlowRate(flowRates[i], "kg/hr");
            process.add(wellStream);
            
            // Create pipeline
            pipes[i] = new PipeBeggsAndBrills("Pipe" + (i + 1), wellStream);
            pipes[i].setLength(pipeLengths[i]);
            pipes[i].setDiameter(0.1524);
            pipes[i].setRunIsothermal(false);
            pipes[i].setNumberOfIncrements(20);
            pipes[i].setConstantSurfaceTemperature(SEA_TEMPERATURE, "C");
            pipes[i].setHeatTransferCoefficient(trueHeatTransferCoeffs[i]);
            process.add(pipes[i]);
            
            // Create splitter for routing
            splitters[i] = new Splitter("Splitter" + (i + 1), pipes[i].getOutletStream(), 2);
            process.add(splitters[i]);
        }
        
        // Create manifolds
        hpManifold = new Mixer("HP Manifold");
        lpManifold = new Mixer("LP Manifold");
        
        for (int i = 0; i < NUM_WELLS; i++) {
            hpManifold.addStream(splitters[i].getSplitStream(0));
            lpManifold.addStream(splitters[i].getSplitStream(1));
        }
        
        process.add(hpManifold);
        process.add(lpManifold);
        
        // Initialize routing (all to HP)
        setRouting(new int[]{0, 0, 0, 0, 0, 0, 0, 0});
    }
    
    /**
     * Sets well routing configuration.
     * @param routing array where 0=HP, 1=LP for each well
     */
    public void setRouting(int[] routing) {
        for (int i = 0; i < NUM_WELLS; i++) {
            if (routing[i] == 0) {
                // Route to HP
                splitters[i].setSplitFactors(new double[]{1.0, 0.0});
            } else {
                // Route to LP
                splitters[i].setSplitFactors(new double[]{0.0, 1.0});
            }
            currentRouting[i] = routing[i];
        }
    }
    
    /**
     * Gets current measurements from the process.
     */
    public Map<String, Double> getMeasurements() {
        process.run();
        
        Map<String, Double> measurements = new HashMap<>();
        measurements.put("HP Manifold.outletStream.temperature", 
            hpManifold.getOutletStream().getTemperature("C"));
        measurements.put("LP Manifold.outletStream.temperature", 
            lpManifold.getOutletStream().getTemperature("C"));
        
        return measurements;
    }
    
    /**
     * Main estimation loop with dynamic routing.
     */
    public void runEstimation() {
        // Build the network
        buildNetwork();
        
        // Create estimator
        EnKFParameterEstimator estimator = new EnKFParameterEstimator(process);
        
        // Add all pipe heat transfer coefficients as tunable parameters
        for (int i = 0; i < NUM_WELLS; i++) {
            estimator.addTunableParameter(
                "Pipe" + (i + 1) + ".heatTransferCoefficient",
                "W/(m2·K)",
                1.0,    // min
                100.0,  // max
                15.0    // initial guess
            );
        }
        
        // Add manifold temperature measurements
        estimator.addMeasuredVariable(
            "HP Manifold.outletStream.temperature", "C", 0.5
        );
        estimator.addMeasuredVariable(
            "LP Manifold.outletStream.temperature", "C", 0.5
        );
        
        // Configure and initialize
        estimator.setProcessNoise(0.2);
        estimator.setMaxChangePerUpdate(3.0);
        estimator.initialize(50, 42);
        
        // Routing schedule for observability
        int[][] routingSchedule = {
            {0, 0, 0, 0, 1, 1, 1, 1},  // Block: 1-4 HP, 5-8 LP
            {1, 1, 1, 1, 0, 0, 0, 0},  // Inverse block
            {0, 1, 0, 1, 0, 1, 0, 1},  // Alternating
            {1, 0, 1, 0, 1, 0, 1, 0},  // Inverse alternating
            {0, 0, 1, 1, 0, 0, 1, 1},  // Pairs
            {0, 1, 1, 1, 1, 1, 1, 1},  // Isolate well 1
            {1, 0, 1, 1, 1, 1, 1, 1},  // Isolate well 2
            {1, 1, 0, 1, 1, 1, 1, 1},  // Isolate well 3
            // ... continue for wells 4-8
        };
        
        int stepsPerRouting = 5;
        int totalSteps = 80;
        
        System.out.println("Starting dynamic routing estimation...\n");
        System.out.println("True values: " + Arrays.toString(trueHeatTransferCoeffs));
        System.out.println();
        
        for (int step = 0; step < totalSteps; step++) {
            // Change routing every N steps
            if (step % stepsPerRouting == 0) {
                int routeIdx = (step / stepsPerRouting) % routingSchedule.length;
                setRouting(routingSchedule[routeIdx]);
                System.out.printf("Step %d: Changed routing to %s%n", 
                    step, Arrays.toString(currentRouting));
            }
            
            // Get measurements
            Map<String, Double> measurements = getMeasurements();
            
            // Update estimator
            EnKFParameterEstimator.EnKFResult result = estimator.update(measurements);
            
            // Print progress every 10 steps
            if (step % 10 == 0 || step == totalSteps - 1) {
                System.out.printf("Step %d: RMSE=%.4f%n", step, result.getRMSE());
                double[] est = result.getEstimates();
                double[] unc = result.getUncertainties();
                
                System.out.print("  Estimates: [");
                for (int i = 0; i < est.length; i++) {
                    System.out.printf("%.1f±%.1f", est[i], unc[i]);
                    if (i < est.length - 1) System.out.print(", ");
                }
                System.out.println("]");
            }
        }
        
        // Final results
        System.out.println("\n=== Final Results ===");
        double[] finalEst = estimator.getEstimates();
        double[] finalUnc = estimator.getUncertainties();
        
        System.out.printf("%-10s %10s %10s %10s %10s%n", 
            "Well", "True", "Estimate", "Error%", "95% CI");
        printSeparator(55);  // Java 8 compatible helper method
        
        for (int i = 0; i < NUM_WELLS; i++) {
            double error = Math.abs(finalEst[i] - trueHeatTransferCoeffs[i]);
            double errorPct = 100 * error / trueHeatTransferCoeffs[i];
            System.out.printf("Well %d     %10.2f %10.2f %9.1f%% %10s%n",
                i + 1,
                trueHeatTransferCoeffs[i],
                finalEst[i],
                errorPct,
                String.format("%.1f-%.1f", 
                    finalEst[i] - 1.96 * finalUnc[i],
                    finalEst[i] + 1.96 * finalUnc[i])
            );
        }
    }
    
    public static void main(String[] args) {
        WellRoutingEstimationExample example = new WellRoutingEstimationExample();
        example.runEstimation();
    }
}
```

### Key Design Decisions

1. **Routing Schedule**: Configurations are designed to maximize observability:
   - Block patterns give aggregate information
   - Single-well isolation gives most precise individual estimates
   - Alternating patterns test different combinations

2. **Steps Per Routing**: 5-10 steps per configuration allows the EnKF to extract information before switching

3. **Process Noise**: Set slightly lower (0.2) since parameters don't change, only routing

4. **Rate Limiting**: Prevents wild swings during routing transitions

---

## API Reference

### EnKFParameterEstimator

```java
// Construction
EnKFParameterEstimator(ProcessSystem processSystem)

// Configuration (chainable)
addTunableParameter(String path, String unit, double min, double max, double initial)
addTunableParameter(String path, String unit, double min, double max, double initial, double initialUncertainty)
addMeasuredVariable(String path, String unit, double noiseStd)
setProcessNoise(double std)
setMaxChangePerUpdate(double maxChange)

// Initialization
void initialize(int ensembleSize, long seed)

// Operation
EnKFResult update(Map<String, Double> measurements)
void reset()

// Results
double[] getEstimates()
double[] getUncertainties()
String[] getParameterNames()
String[] getMeasurementNames()
List<EnKFResult> getHistory()
int getUpdateCount()
CalibrationResult toCalibrationResult()
```

### EstimationTestHarness

```java
// Construction
EstimationTestHarness(ProcessSystem processSystem)

// Configuration (chainable)
setSeed(long seed)
addParameter(String path, double trueValue)
addParameter(String path, double trueValue, double minBound, double maxBound)
addMeasurement(String path, String unit, double noiseStd)

// Testing
Map<String, Double> generateMeasurement(double noiseMultiplier)
TestReport runConvergenceTest(EnKFParameterEstimator estimator, int numSteps)
TestReport runConvergenceTest(EnKFParameterEstimator estimator, int numSteps, double noiseMultiplier, Consumer<Integer> callback)
Map<Double, TestReport> runNoiseRobustnessTest(EnKFParameterEstimator estimator, int stepsPerLevel, double[] noiseLevels)
TestReport runDriftTrackingTest(EnKFParameterEstimator estimator, int numSteps, int driftingParamIndex, double driftRate)
MonteCarloReport runMonteCarloValidation(Supplier<EnKFParameterEstimator> factory, int numTrials, int stepsPerTrial)
```

### TestReport

```java
String getTestName()
double getRMSE()
double getMeanAbsoluteError()
double getMaxError()
int getStepsToConverge()
double getCoverageRate()
double[] getFinalEstimates()
double[] getTrueValues()
boolean passes(double maxRMSE, double minCoverage, int maxConvergenceSteps)
void printSummary()
```

---

## Best Practices

### 1. Ensemble Size

- **Minimum**: 20 members
- **Recommended**: 30-50 members for most cases
- **Large systems**: 50-100 members for many parameters

### 2. Process Noise Tuning

| Scenario | Process Noise |
|----------|---------------|
| Parameters truly constant | 0.1 - 0.2 |
| Slow drift expected | 0.3 - 0.5 |
| Fast changes possible | 0.5 - 1.0 |

### 3. Measurement Noise Specification

- Use realistic values based on sensor specifications
- Underestimating noise → overconfident estimates
- Overestimating noise → slow convergence

### 4. Observability Requirements

For $n$ parameters and $m$ measurements:
- If $m \geq n$: System may be observable from single configuration
- If $m < n$: Need routing changes or additional measurements
- Rule of thumb: Need $n/m$ distinct routing configurations minimum

### 5. Pre-deployment Checklist

- [ ] Convergence test passes (RMSE < threshold)
- [ ] Coverage rate > 90% (95% CI contains true values)
- [ ] Noise robustness verified at expected noise levels
- [ ] Drift tracking works if parameters may change
- [ ] Monte Carlo success rate > 95%

### 6. Anomaly Handling

When `result.isAnomalyDetected()` returns `true`:
1. Log the event
2. Check for sensor malfunction
3. Consider increased uncertainty or measurement rejection
4. Monitor for persistent anomalies (model mismatch)

---

## See Also

- [Adjusters](../equipment/util/adjusters) - Variable adjustment patterns
