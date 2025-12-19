# Process Optimization Framework

The `neqsim.process.calibration` package provides a comprehensive optimization framework for parameter estimation in process simulations. This document details the **Levenberg-Marquardt batch optimization** capabilities for fitting process model parameters to historical or batch data.

## Table of Contents

- [Overview](#overview)
- [Mathematical Background](#mathematical-background)
- [Architecture](#architecture)
- [BatchParameterEstimator](#batchparameterestimator)
- [ProcessSimulationFunction](#processsimulationfunction)
- [BatchResult](#batchresult)
- [Derivative Computation](#derivative-computation)
- [Usage Examples](#usage-examples)
- [Performance Considerations](#performance-considerations)
- [Troubleshooting](#troubleshooting)

---

## Overview

The batch optimization framework solves the **nonlinear least squares** problem of finding process parameters that minimize the discrepancy between model predictions and measured data:

$$\min_{\vec{p}} \sum_{i=1}^{N} \left( \frac{y_i^{meas} - y_i^{model}(\vec{p})}{\sigma_i} \right)^2$$

where:
- $\vec{p}$ = vector of tunable parameters
- $y_i^{meas}$ = measured values
- $y_i^{model}(\vec{p})$ = model predictions
- $\sigma_i$ = measurement uncertainties
- $N$ = number of data points

### Key Features

| Feature | Description |
|---------|-------------|
| **Levenberg-Marquardt** | Robust optimization combining gradient descent and Gauss-Newton |
| **Parameter Bounds** | Enforces physical constraints on parameters |
| **Weighted Residuals** | Accounts for measurement uncertainties |
| **Uncertainty Quantification** | Provides parameter standard deviations from covariance matrix |
| **Jacobian Options** | Numerical (Romberg) or analytical (ProcessSensitivityAnalyzer) |
| **Path-based Access** | Uses dot-notation to access any process variable |

### When to Use Batch Optimization

| Scenario | Recommendation |
|----------|----------------|
| Offline calibration with historical data | ✅ Use BatchParameterEstimator |
| Multiple parameters to estimate | ✅ Use BatchParameterEstimator |
| Need uncertainty quantification | ✅ Use BatchParameterEstimator |
| Live streaming data | ❌ Use EnKFParameterEstimator instead |
| Single parameter adjustment | ❌ Use Adjuster instead |

---

## Mathematical Background

### Levenberg-Marquardt Algorithm

The Levenberg-Marquardt (L-M) algorithm is an iterative method that interpolates between:
- **Gradient Descent**: Slow but stable, works far from minimum
- **Gauss-Newton**: Fast near minimum but can be unstable

#### Update Equation

At each iteration, the parameter update $\Delta\vec{p}$ is computed by solving:

$$(\mathbf{J}^T \mathbf{W} \mathbf{J} + \lambda \mathbf{I}) \Delta\vec{p} = \mathbf{J}^T \mathbf{W} \vec{r}$$

where:
- $\mathbf{J}$ = Jacobian matrix, $J_{ij} = \partial y_i / \partial p_j$
- $\mathbf{W}$ = weight matrix, $W_{ii} = 1/\sigma_i^2$
- $\lambda$ = damping parameter (Marquardt parameter)
- $\mathbf{I}$ = identity matrix
- $\vec{r}$ = residual vector, $r_i = y_i^{meas} - y_i^{model}$

#### Damping Parameter Adaptation

The damping parameter $\lambda$ adapts based on iteration success:

| Condition | Action | Effect |
|-----------|--------|--------|
| Residual decreased | $\lambda \leftarrow \lambda / 10$ | More Gauss-Newton |
| Residual increased | $\lambda \leftarrow \lambda \times 10$ | More gradient descent |

Starting value: $\lambda_0 = 0.001$

#### Convergence Criteria

The algorithm terminates when:
1. **Relative change**: $\|\Delta\vec{p}\| / \|\vec{p}\| < \epsilon_{rel}$ (default $10^{-6}$)
2. **Absolute residual**: $\|\vec{r}\| < \epsilon_{abs}$ (default $10^{-10}$)
3. **Maximum iterations**: Reached limit (default 100)

### Uncertainty Quantification

After convergence, parameter uncertainties are estimated from the **covariance matrix**:

$$\mathbf{C} = s^2 (\mathbf{J}^T \mathbf{W} \mathbf{J})^{-1}$$

where $s^2$ is the estimated variance of the residuals:

$$s^2 = \frac{\chi^2}{N - p}$$

- $\chi^2$ = sum of squared weighted residuals
- $N$ = number of data points
- $p$ = number of parameters

Parameter standard deviations: $\sigma_{p_j} = \sqrt{C_{jj}}$

95% Confidence intervals: $p_j \pm 1.96 \cdot \sigma_{p_j}$

### Goodness of Fit Statistics

| Statistic | Formula | Interpretation |
|-----------|---------|----------------|
| **Chi-Square** | $\chi^2 = \sum_i (r_i/\sigma_i)^2$ | Should be $\approx N-p$ for good fit |
| **RMSE** | $\sqrt{\sum_i r_i^2 / N}$ | Average prediction error |
| **R-Squared** | $1 - SS_{res}/SS_{tot}$ | Fraction of variance explained |
| **Correlation Matrix** | $\rho_{ij} = C_{ij}/\sqrt{C_{ii}C_{jj}}$ | Parameter correlations |

---

## Architecture

The optimization framework consists of three main classes:

```
┌─────────────────────────────────────────────────────────────────────┐
│                      BatchParameterEstimator                        │
│  (User-facing API: fluent configuration, solve(), result access)   │
└─────────────────────────────────────┬───────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     ProcessSimulationFunction                        │
│  (Bridge: extends LevenbergMarquardtFunction, runs ProcessSystem)   │
└─────────────────────────────────────┬───────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        LevenbergMarquardt                            │
│  (Core optimizer: neqsim.statistics.parameterfitting)               │
└─────────────────────────────────────────────────────────────────────┘
```

### Class Responsibilities

| Class | Responsibility |
|-------|---------------|
| `BatchParameterEstimator` | User API, configuration, data management, result packaging |
| `ProcessSimulationFunction` | Runs process simulation, computes residuals, interfaces with optimizer |
| `BatchResult` | Result container with statistics, uncertainties, formatting |
| `LevenbergMarquardt` | Core optimization algorithm |
| `SampleSet` / `SampleValue` | Data point containers used by optimizer |

---

## BatchParameterEstimator

The main user-facing class for batch parameter estimation.

### Class Diagram

```
BatchParameterEstimator
├── Fields
│   ├── processSystem: ProcessSystem
│   ├── tunableParameters: List<TunableParameter>
│   ├── measuredVariables: List<MeasuredVariable>
│   ├── dataPoints: List<DataPoint>
│   ├── maxIterations: int
│   └── useAnalyticalJacobian: boolean
├── Methods
│   ├── addTunableParameter(path, unit, min, max, initial)
│   ├── addMeasuredVariable(path, unit, noiseStdDev)
│   ├── addDataPoint(conditions, measurements)
│   ├── setMaxIterations(int)
│   ├── setUseAnalyticalJacobian(boolean)
│   └── solve(): BatchResult
└── Inner Classes
    ├── TunableParameter
    ├── MeasuredVariable
    └── DataPoint
```

### Constructor

```java
public BatchParameterEstimator(ProcessSystem processSystem)
```

Creates a new estimator for the given process system. The process system should be fully configured with all equipment and streams before creating the estimator.

### Configuration Methods

#### addTunableParameter

```java
public BatchParameterEstimator addTunableParameter(
    String path,           // Dot-notation path to parameter
    String unit,           // Unit of the parameter
    double minValue,       // Lower bound
    double maxValue,       // Upper bound
    double initialGuess    // Starting value for optimization
)
```

Defines a parameter to be estimated. The path uses dot-notation to access any settable property:

| Example Path | Description |
|--------------|-------------|
| `"Pipe1.heatTransferCoefficient"` | Heat transfer coefficient |
| `"Valve1.Cv"` | Valve flow coefficient |
| `"HeatExchanger.UA"` | Overall heat transfer coefficient |
| `"Separator.internalDiameter"` | Equipment dimension |

#### addMeasuredVariable

```java
public BatchParameterEstimator addMeasuredVariable(
    String path,           // Dot-notation path to measured variable
    String unit,           // Unit of measurement
    double noiseStdDev     // Expected measurement noise (1-sigma)
)
```

Defines a variable that is measured and used for fitting. The noise standard deviation affects the weighting of this measurement in the objective function.

#### addDataPoint

```java
public BatchParameterEstimator addDataPoint(
    Map<String, Double> conditions,    // Operating conditions
    Map<String, Double> measurements   // Measured values
)
```

Adds a data point from historical data. Conditions are variables that define the operating state (e.g., feed flow rate, inlet temperature). Measurements are the observed values to fit.

### Solve Method

```java
public BatchResult solve()
```

Runs the Levenberg-Marquardt optimization and returns a `BatchResult` containing:
- Optimized parameter values
- Parameter uncertainties
- Goodness-of-fit statistics
- Convergence information

---

## ProcessSimulationFunction

Bridge class that connects the process simulation to the Levenberg-Marquardt optimizer.

### Purpose

The `ProcessSimulationFunction` extends `LevenbergMarquardtFunction` and implements the required interface:

1. **`calcValue()`**: Runs the process simulation and returns the weighted residual
2. **Parameter access**: Sets tunable parameters on the process equipment
3. **Prediction access**: Reads predicted values from the process model

### Key Methods

```java
// Add a parameter to be optimized
public void addParameter(String path, double minValue, double maxValue)

// Add a measurement for comparison
public void addMeasurement(String path, double weight)

// Set operating conditions for current data point
public void setConditions(Map<String, Double> conditions)

// Get current predictions from the model
public double[] getPredictions()
```

### Internal Flow

```
┌────────────────────────────────────────────────────────────────┐
│                    calcValue() method                          │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  1. Read current parameter values from optimizer               │
│     params[0], params[1], ...                                  │
│                                                                │
│  2. Set parameters on process equipment via reflection         │
│     equipment.setHeatTransferCoefficient(params[0])           │
│                                                                │
│  3. Run process simulation                                     │
│     processSystem.run()                                        │
│                                                                │
│  4. Read predictions from process model                        │
│     y_pred = outlet.getTemperature("C")                       │
│                                                                │
│  5. Compute weighted residual for current sample               │
│     residual = (y_meas - y_pred) / sigma                      │
│                                                                │
│  6. Return residual to optimizer                               │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### Path Resolution

Variables are accessed using Java reflection through the path notation:

```java
// Path: "Pipe1.heatTransferCoefficient"
// Resolves to:
ProcessEquipmentInterface equipment = processSystem.getUnit("Pipe1");
Method setter = equipment.getClass().getMethod("setHeatTransferCoefficient", double.class);
setter.invoke(equipment, value);
```

Supported path patterns:
- `"EquipmentName.property"` - Direct property access
- `"EquipmentName.stream.property"` - Stream property access
- `"EquipmentName.outputPort.fluid.property"` - Nested object access

---

## BatchResult

Container for optimization results with comprehensive statistics and formatting.

### Result Access Methods

```java
// Parameter estimates
double[] getEstimates()                    // Optimized values
String[] getParameterNames()               // Parameter paths

// Uncertainty quantification
double[] getUncertainties()                // Standard deviations
double[] getConfidenceIntervalLower()      // 95% CI lower bounds
double[] getConfidenceIntervalUpper()      // 95% CI upper bounds
double[][] getCovarianceMatrix()           // Full covariance
double[][] getCorrelationMatrix()          // Correlation coefficients

// Goodness of fit
double getChiSquare()                      // Sum of squared weighted residuals
double getRMSE()                           // Root mean square error
double getRSquared()                       // Coefficient of determination
int getDegreesOfFreedom()                  // N - p

// Convergence information
boolean isConverged()                      // Did optimization converge?
int getIterations()                        // Number of iterations used
double getFinalResidual()                  // Final objective function value
```

### Formatting Methods

```java
// Print formatted summary to console
void printSummary()

// Get formatted string representation
String toString()

// Convert to standard CalibrationResult for API compatibility
CalibrationResult toCalibrationResult()
```

### Example Output

```
═══════════════════════════════════════════════════════════════════
                    BATCH PARAMETER ESTIMATION RESULTS
═══════════════════════════════════════════════════════════════════

Convergence: ✓ Converged in 12 iterations

Parameter Estimates:
───────────────────────────────────────────────────────────────────
  Parameter                              Estimate    Std.Dev     95% CI
  Pipe1.heatTransferCoefficient          12.34       0.45       [11.46, 13.22]
  Pipe2.heatTransferCoefficient          18.76       0.62       [17.54, 19.98]

Goodness of Fit:
───────────────────────────────────────────────────────────────────
  Chi-Square:     24.56
  RMSE:           0.234
  R-Squared:      0.987
  DOF:            23

Correlation Matrix:
───────────────────────────────────────────────────────────────────
              Pipe1.hTC    Pipe2.hTC
  Pipe1.hTC   1.000        -0.234
  Pipe2.hTC   -0.234       1.000

═══════════════════════════════════════════════════════════════════
```

---

## Derivative Computation

The Levenberg-Marquardt algorithm requires the Jacobian matrix $\mathbf{J}$ of partial derivatives. NeqSim supports two methods:

### 1. Numerical Derivatives (Default)

Uses **Romberg extrapolation** for high-accuracy numerical differentiation:

```java
// Internally uses NumericalDerivative class
double derivative = NumericalDerivative.calcDerivative(
    function,     // The function to differentiate
    x,            // Point at which to evaluate
    h             // Initial step size
);
```

**Romberg extrapolation** improves accuracy by:
1. Computing finite differences at multiple step sizes
2. Extrapolating to zero step size using Richardson extrapolation
3. Achieving $O(h^{2n})$ accuracy for $n$ extrapolation levels

Advantages:
- Works for any process model
- No additional implementation required
- Robust to model discontinuities

Disadvantages:
- Requires multiple model evaluations per derivative
- Slower for large parameter sets

### 2. Analytical Jacobian (Optional)

Uses `ProcessSensitivityAnalyzer` for more efficient derivative computation:

```java
estimator.setUseAnalyticalJacobian(true);
```

The `ProcessSensitivityAnalyzer` provides:

| Feature | Description |
|---------|-------------|
| **Broyden Jacobian Reuse** | Reuses Jacobians from recycle loop convergence |
| **Chain Rule** | Propagates sensitivities through process structure |
| **Selective Finite Differences** | Falls back only when necessary |
| **Fluent API** | Easy configuration of sensitivities |

Example usage:

```java
ProcessSensitivityAnalyzer analyzer = new ProcessSensitivityAnalyzer(processSystem);

// Define input perturbations
analyzer.addInputPerturbation("Pipe1.heatTransferCoefficient", 0.01);

// Define outputs of interest
analyzer.addOutputVariable("Manifold.outletStream.temperature");

// Compute Jacobian
double[][] jacobian = analyzer.computeJacobian();
```

### Choosing a Method

| Scenario | Recommendation |
|----------|----------------|
| Few parameters (< 5) | Numerical (default) |
| Many parameters (> 10) | Analytical |
| Process has recycles | Analytical (reuses Broyden) |
| Simple linear process | Either |
| Debugging/validation | Numerical (more robust) |

---

## Usage Examples

### Example 1: Heat Exchanger Calibration

Estimate the overall heat transfer coefficient (UA) of a heat exchanger:

```java
import neqsim.process.calibration.BatchParameterEstimator;
import neqsim.process.calibration.BatchResult;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create process system
SystemInterface hotFluid = new SystemSrkEos(373.0, 50.0);
hotFluid.addComponent("water", 1.0);
hotFluid.setMixingRule("classic");

Stream hotInlet = new Stream("HotInlet", hotFluid);
hotInlet.setFlowRate(1000.0, "kg/hr");

// ... create cold stream similarly ...

HeatExchanger hx = new HeatExchanger("HX1");
hx.setFeedStream(0, hotInlet);
hx.setFeedStream(1, coldInlet);

ProcessSystem process = new ProcessSystem();
process.add(hotInlet);
process.add(coldInlet);
process.add(hx);
process.run();

// Create estimator
BatchParameterEstimator estimator = new BatchParameterEstimator(process);

// Define parameter to estimate
estimator.addTunableParameter(
    "HX1.UA",           // Overall heat transfer coefficient
    "W/K",              // Units
    100.0,              // Minimum
    10000.0,            // Maximum
    1000.0              // Initial guess
);

// Define measurements
estimator.addMeasuredVariable("HX1.coldOutStream.temperature", "C", 0.5);
estimator.addMeasuredVariable("HX1.hotOutStream.temperature", "C", 0.5);

// Add historical data
for (DataRecord record : historicalData) {
    Map<String, Double> conditions = new HashMap<>();
    conditions.put("HotInlet.flowRate", record.hotFlow);
    conditions.put("ColdInlet.flowRate", record.coldFlow);
    conditions.put("HotInlet.temperature", record.hotInletT);
    conditions.put("ColdInlet.temperature", record.coldInletT);
    
    Map<String, Double> measurements = new HashMap<>();
    measurements.put("HX1.coldOutStream.temperature", record.coldOutletT);
    measurements.put("HX1.hotOutStream.temperature", record.hotOutletT);
    
    estimator.addDataPoint(conditions, measurements);
}

// Solve
BatchResult result = estimator.solve();
result.printSummary();

System.out.println("Estimated UA: " + result.getEstimates()[0] + " W/K");
System.out.println("Uncertainty: ±" + result.getUncertainties()[0] + " W/K");
```

### Example 2: Multi-Parameter Pipe Network

Estimate heat transfer coefficients for multiple pipes:

```java
BatchParameterEstimator estimator = new BatchParameterEstimator(pipeNetwork);

// Add multiple parameters
String[] pipeNames = {"Pipe1", "Pipe2", "Pipe3", "Pipe4"};
for (String pipe : pipeNames) {
    estimator.addTunableParameter(
        pipe + ".heatTransferCoefficient",
        "W/(m2·K)",
        1.0, 100.0, 15.0
    );
}

// Add measurements at each pipe outlet
for (String pipe : pipeNames) {
    estimator.addMeasuredVariable(
        pipe + ".outletStream.temperature",
        "C",
        0.3
    );
}

// Add data and solve
// ... (add data points as before)

BatchResult result = estimator.solve();

// Check parameter correlations
double[][] corr = result.getCorrelationMatrix();
for (int i = 0; i < pipeNames.length; i++) {
    for (int j = i + 1; j < pipeNames.length; j++) {
        if (Math.abs(corr[i][j]) > 0.8) {
            System.out.println("Warning: High correlation between " 
                + pipeNames[i] + " and " + pipeNames[j] 
                + ": " + corr[i][j]);
        }
    }
}
```

### Example 3: Valve Cv Estimation with Pressure Data

```java
BatchParameterEstimator estimator = new BatchParameterEstimator(process);

estimator.addTunableParameter(
    "ControlValve.Cv",
    "USG/min",
    10.0, 500.0, 100.0
);

estimator.addMeasuredVariable("ControlValve.outletStream.pressure", "bara", 0.1);
estimator.addMeasuredVariable("ControlValve.outletStream.temperature", "C", 0.5);

// Add data from plant historian
for (PlantRecord record : plantData) {
    Map<String, Double> conditions = new HashMap<>();
    conditions.put("FeedStream.flowRate", record.flowRate);
    conditions.put("FeedStream.pressure", record.inletPressure);
    conditions.put("FeedStream.temperature", record.inletTemp);
    conditions.put("ControlValve.percentOpen", record.valvePosition);
    
    Map<String, Double> measurements = new HashMap<>();
    measurements.put("ControlValve.outletStream.pressure", record.outletPressure);
    measurements.put("ControlValve.outletStream.temperature", record.outletTemp);
    
    estimator.addDataPoint(conditions, measurements);
}

BatchResult result = estimator.solve();

if (result.isConverged()) {
    System.out.println("Estimated Cv: " + result.getEstimates()[0]);
    System.out.println("R-squared: " + result.getRSquared());
} else {
    System.out.println("Optimization did not converge");
    System.out.println("Final residual: " + result.getFinalResidual());
}
```

---

## Performance Considerations

### Reducing Model Evaluations

Each Levenberg-Marquardt iteration requires:
- 1 function evaluation for the residual
- $2 \times p$ evaluations for numerical Jacobian (forward differences)
- Or $p$ evaluations with analytical Jacobian

Strategies to reduce computation:

1. **Use analytical Jacobian** for many parameters
2. **Good initial guesses** reduce iterations
3. **Tight bounds** constrain search space
4. **Appropriate step sizes** for numerical derivatives

### Memory Usage

The covariance matrix requires $O(p^2)$ storage. For very large parameter sets:
- Consider parameter grouping
- Use iterative solvers for the normal equations
- Trade off uncertainty quantification for speed

### Parallel Evaluation

For independent data points, consider:
- Parallelizing residual computation across data points
- Using thread pools for ensemble-based methods

---

## Troubleshooting

### Common Issues

| Problem | Possible Cause | Solution |
|---------|---------------|----------|
| No convergence | Poor initial guess | Use physical reasoning for better starting point |
| No convergence | Parameters at bounds | Widen bounds or check if model is appropriate |
| Large uncertainties | Insufficient data | Add more data points |
| Large uncertainties | Parameters highly correlated | Reduce parameter set or add constraints |
| High correlation | Parameters not identifiable | Measure additional variables |
| Negative R-squared | Model fundamentally wrong | Check model structure |
| Slow convergence | Stiff problem | Reduce step size for numerical derivatives |

### Debugging Tips

1. **Check residuals**: Plot residuals vs. predictions to identify systematic errors
2. **Inspect Jacobian**: Look for zero columns (insensitive parameters)
3. **Correlation matrix**: High correlations (> 0.9) indicate identifiability issues
4. **Chi-square test**: $\chi^2 >> N-p$ suggests model mismatch or underestimated uncertainties
5. **Sensitivity analysis**: Use `ProcessSensitivityAnalyzer` to verify sensitivities

### Error Messages

| Error | Meaning | Action |
|-------|---------|--------|
| `"Path not found: ..."` | Invalid equipment or property path | Check spelling, verify equipment exists |
| `"Cannot set property: ..."` | Property is read-only or wrong type | Use a different property or check method signature |
| `"Singular matrix"` | Jacobian is rank-deficient | Remove correlated parameters |
| `"Maximum iterations exceeded"` | Did not converge | Improve initial guess, check model |

---

## Related Documentation

- [Process Calibration Framework](../calibration/README.md) - EnKF estimator and test harness
- [Process Sensitivity Analyzer](../util/sensitivity.md) - Jacobian computation utilities
- [Numerical Derivatives](../../statistics/README.md) - Romberg extrapolation details
- [NeqSim Process Module](../README.md) - Process simulation overview

---

## References

1. Marquardt, D.W. (1963). "An Algorithm for Least-Squares Estimation of Nonlinear Parameters". *SIAM Journal on Applied Mathematics*.
2. Press, W.H. et al. (2007). *Numerical Recipes: The Art of Scientific Computing*, 3rd ed. Cambridge University Press.
3. Nocedal, J. and Wright, S.J. (2006). *Numerical Optimization*, 2nd ed. Springer.
