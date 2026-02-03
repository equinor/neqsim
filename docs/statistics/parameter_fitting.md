---
title: "Parameter Fitting"
description: "The parameter fitting subsystem provides robust nonlinear regression capabilities for thermodynamic model calibration."
---

# Parameter Fitting

The parameter fitting subsystem provides robust nonlinear regression capabilities for thermodynamic model calibration.

## Table of Contents
- [Overview](#overview)
- [Levenberg-Marquardt Algorithm](#levenberg-marquardt-algorithm)
- [Creating Objective Functions](#creating-objective-functions)
- [Sample Data Management](#sample-data-management)
- [Parameter Bounds](#parameter-bounds)
- [Convergence and Diagnostics](#convergence-and-diagnostics)
- [Statistical Output](#statistical-output)
- [Algorithm Variants](#algorithm-variants)
- [Numerical Derivatives](#numerical-derivatives)
- [Examples](#examples)

---

## Overview

**Location:** `neqsim.statistics.parameterfitting`

The fitting framework minimizes the weighted sum of squared residuals:

$$\chi^2 = \sum_{i=1}^{N} \left( \frac{y_i^{\text{exp}} - y_i^{\text{calc}}(\vec{p})}{\sigma_i} \right)^2$$

where:
- $y_i^{\text{exp}}$ = experimental value
- $y_i^{\text{calc}}$ = calculated value from model
- $\sigma_i$ = standard deviation (uncertainty)
- $\vec{p}$ = parameter vector

---

## Levenberg-Marquardt Algorithm

The Levenberg-Marquardt (L-M) algorithm combines gradient descent with Gauss-Newton methods for robust nonlinear optimization.

### Algorithm Overview

At each iteration, the algorithm solves:

$$(\mathbf{J}^T \mathbf{W} \mathbf{J} + \lambda \mathbf{I}) \Delta\vec{p} = \mathbf{J}^T \mathbf{W} \vec{r}$$

where:
- $\mathbf{J}$ = Jacobian matrix of partial derivatives
- $\mathbf{W}$ = weight matrix (diagonal, $W_{ii} = 1/\sigma_i^2$)
- $\lambda$ = damping parameter
- $\vec{r}$ = residual vector

The damping parameter $\lambda$ adapts during iteration:
- Large $\lambda$ → gradient descent (slow but stable)
- Small $\lambda$ → Gauss-Newton (fast near minimum)

### Implementation

```java
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.statistics.parameterfitting.SampleSet;

// Create optimizer
LevenbergMarquardt optimizer = new LevenbergMarquardt();

// Set sample data
optimizer.setSampleSet(sampleSet);

// Optional: configure iterations
optimizer.setMaxNumberOfIterations(100);  // default: 50

// Solve
optimizer.solve();

// Display results
optimizer.displayResult();
optimizer.displayCurveFit();
```

### Solve Loop Internals

The `solve()` method:
1. Calculates initial $\chi^2$ and matrices
2. Iteratively:
   - Computes trial parameters with current $\lambda$
   - Checks parameter bounds
   - Evaluates new $\chi^2$
   - Accepts/rejects step and updates $\lambda$
3. Terminates when $|\Delta\chi^2/\chi^2| < \epsilon$ or max iterations

---

## Creating Objective Functions

### Extending LevenbergMarquardtFunction

```java
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtFunction;

public class MyFittingFunction extends LevenbergMarquardtFunction {
    
    public MyFittingFunction() {
        // Initialize parameter count
        params = new double[2];
    }
    
    @Override
    public double calcValue(double[] dependentValues) {
        // dependentValues: independent variables from SampleValue
        // params[]: fitting parameters
        
        double x = dependentValues[0];
        double y = dependentValues[1];
        
        // Model equation: z = a * exp(-b * x) + c * y
        double calculated = params[0] * Math.exp(-params[1] * x) + params[2] * y;
        
        return calculated;
    }
    
    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
    }
}
```

### With Thermodynamic System

For thermodynamic fitting, functions can include system and thermoOps:

```java
public class VLEFittingFunction extends LevenbergMarquardtFunction {
    
    public VLEFittingFunction(SystemInterface system, ThermodynamicOperations thermoOps) {
        this.system = system;
        this.thermoOps = thermoOps;
        params = new double[1];  // One kij parameter
    }
    
    @Override
    public double calcValue(double[] dependentValues) {
        double T = dependentValues[0];  // Temperature [K]
        double P = dependentValues[1];  // Pressure [bar]
        
        // Set kij from fitting parameter
        system.getPhase(0).getMixingRule()
            .setBinaryInteractionParameter(0, 1, params[0]);
        system.getPhase(1).getMixingRule()
            .setBinaryInteractionParameter(0, 1, params[0]);
        
        // Set conditions and flash
        system.setTemperature(T);
        system.setPressure(P);
        system.init(0);
        thermoOps.TPflash();
        
        // Return calculated property (e.g., liquid composition)
        return system.getPhase(1).getComponent(0).getx();
    }
    
    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
    }
}
```

### Initial Guess

Always set an initial guess before solving:

```java
function.setInitialGuess(new double[]{0.1, 500.0, 1.0});
```

---

## Sample Data Management

### SampleValue Class

Each data point is a `SampleValue`:

```java
import neqsim.statistics.parameterfitting.SampleValue;

// Constructor: SampleValue(value, stdDev, independentVariables)
double[] independentVars = {300.0, 10.0, 0.5};  // T, P, x
SampleValue sample = new SampleValue(0.25, 0.01, independentVars);

// Attach objective function
sample.setFunction(myFunction);
```

#### Key Methods

| Method | Description |
|--------|-------------|
| `getSampleValue()` | Get experimental value |
| `getStandardDeviation()` | Get experimental uncertainty |
| `getDependentValues()` | Get independent variables array |
| `setFunction(function)` | Attach objective function |
| `getFunction()` | Retrieve attached function |

### SampleSet Class

Collection of samples:

```java
import neqsim.statistics.parameterfitting.SampleSet;
import java.util.ArrayList;

// From ArrayList
ArrayList<SampleValue> samples = new ArrayList<>();
samples.add(sample1);
samples.add(sample2);
SampleSet sampleSet = new SampleSet(samples);

// From array
SampleValue[] arr = {sample1, sample2, sample3};
SampleSet sampleSet = new SampleSet(arr);

// Access samples
SampleValue s = sampleSet.getSample(0);
int n = sampleSet.getLength();
```

#### Key Methods

| Method | Description |
|--------|-------------|
| `add(sample)` | Add a sample to the set |
| `getSample(i)` | Get sample at index i |
| `getLength()` | Number of samples |
| `createNewNormalDistributedSet()` | Clone with randomized values (for Monte Carlo) |

---

## Parameter Bounds

Constrain parameters to physically meaningful ranges:

```java
// bounds[paramIndex] = {lowerBound, upperBound}
double[][] bounds = new double[3][2];

bounds[0] = new double[]{0.0, 10.0};      // 0 ≤ param0 ≤ 10
bounds[1] = new double[]{100.0, 2000.0};  // 100 ≤ param1 ≤ 2000
bounds[2] = new double[]{-1.0, 1.0};      // -1 ≤ param2 ≤ 1

function.setBounds(bounds);
```

During optimization, if a parameter exceeds its bounds, it is clamped to the boundary value.

### Typical Bounds for Thermodynamic Parameters

| Parameter | Typical Range |
|-----------|---------------|
| Binary interaction $k_{ij}$ | [-0.5, 0.5] |
| Association energy $\epsilon$ | [500, 10000] K |
| Association volume $\beta$ | [0.001, 0.1] |
| Critical temperature ratio | [0.5, 2.0] |

---

## Convergence and Diagnostics

### Iteration Control

```java
// Set maximum iterations (default: 50)
optimizer.setMaxNumberOfIterations(100);

// Check convergence
optimizer.solve();  // Returns when converged or max iterations
```

### Chi-Square Calculation

```java
// Calculate current chi-square
double chiSq = optimizer.calcChiSquare();

// Reduced chi-square
int dof = sampleSet.getLength() - numParameters;
double reducedChiSq = chiSq / dof;
```

### Goodness-of-Fit Interpretation

| Reduced $\chi^2$ | Interpretation |
|------------------|----------------|
| ≈ 1 | Good fit |
| << 1 | Uncertainties overestimated |
| >> 1 | Poor fit or underestimated uncertainties |

---

## Statistical Output

After successful fitting, obtain statistical measures:

### Covariance Matrix

```java
optimizer.calcCoVarianceMatrix();
// Access: optimizer.coVarianceMatrix[i][j]
```

The covariance matrix provides parameter uncertainties and correlations.

### Parameter Standard Deviations

```java
optimizer.calcParameterStandardDeviation();
// Access: optimizer.parameterStandardDeviation[i]
```

Standard deviation from diagonal of covariance matrix: $\sigma_i = \sqrt{C_{ii}}$

### Correlation Matrix

```java
optimizer.calcCorrelationMatrix();
// Access: optimizer.parameterCorrelationMatrix[i][j]
```

Correlation: $\rho_{ij} = \frac{C_{ij}}{\sqrt{C_{ii} C_{jj}}}$

### Parameter Uncertainty

```java
optimizer.calcParameterUncertainty();
```

Provides confidence intervals (typically 95%) on fitted parameters.

### Display Methods

```java
// Summary statistics
optimizer.displayResult();

// Fitted vs experimental comparison
optimizer.displayCurveFit();

// Raw parameter values
double[] params = sampleSet.getSample(0).getFunction().getFittingParams();
```

---

## Algorithm Variants

### LevenbergMarquardtAbsDev

Minimizes sum of absolute deviations instead of squared deviations:

$$\text{SAD} = \sum_{i=1}^{N} |y_i^{\text{exp}} - y_i^{\text{calc}}|$$

More robust to outliers:

```java
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtAbsDev;

LevenbergMarquardtAbsDev optimizer = new LevenbergMarquardtAbsDev();
optimizer.setSampleSet(sampleSet);
optimizer.solve();
```

### LevenbergMarquardtBiasDev

Minimizes bias deviation, useful when systematic errors are suspected:

$$\text{Bias} = \frac{1}{N} \sum_{i=1}^{N} (y_i^{\text{exp}} - y_i^{\text{calc}})$$

```java
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtBiasDev;

LevenbergMarquardtBiasDev optimizer = new LevenbergMarquardtBiasDev();
optimizer.setSampleSet(sampleSet);
optimizer.solve();
```

---

## Numerical Derivatives

Derivatives are computed numerically using Ridders' extrapolation method.

### Implementation

```java
import neqsim.statistics.parameterfitting.NumericalDerivative;

// Compute derivative of sample prediction w.r.t. parameter
double deriv = NumericalDerivative.calcDerivative(
    statisticsObject,  // The StatisticsBaseClass
    sampleNumber,      // Index of sample
    parameterNumber    // Index of parameter
);
```

### Algorithm Details

Ridders' method:
1. Evaluates function at progressively smaller step sizes
2. Uses polynomial extrapolation to estimate limit
3. Provides error estimate

Parameters in implementation:
- `CON = 1.4` - step reduction factor
- `NTAB = 10` - maximum extrapolation iterations
- Initial step size `h = 0.01`

---

## Examples

### Example 1: Simple Polynomial Fit

```java
// Fit: y = a*x^2 + b*x + c
public class PolynomialFunction extends LevenbergMarquardtFunction {
    
    public PolynomialFunction() {
        params = new double[3];
    }
    
    @Override
    public double calcValue(double[] dependentValues) {
        double x = dependentValues[0];
        return params[0]*x*x + params[1]*x + params[2];
    }
    
    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
    }
}

// Usage
PolynomialFunction func = new PolynomialFunction();
func.setInitialGuess(new double[]{1.0, 1.0, 0.0});

ArrayList<SampleValue> samples = new ArrayList<>();
// Add data: y vs x with uncertainties
double[][] data = {
    {0.0, 0.5, 0.05},  // x, y, sigma
    {1.0, 2.3, 0.1},
    {2.0, 5.1, 0.1},
    {3.0, 9.8, 0.2}
};

for (double[] row : data) {
    SampleValue s = new SampleValue(row[1], row[2], new double[]{row[0]});
    s.setFunction(func);
    samples.add(s);
}

SampleSet set = new SampleSet(samples);
LevenbergMarquardt opt = new LevenbergMarquardt();
opt.setSampleSet(set);
opt.solve();
opt.displayResult();
```

### Example 2: Fitting Binary Interaction Parameters

```java
// Create thermodynamic system
SystemInterface system = new SystemSrkEos(280.0, 10.0);
system.addComponent("methane", 0.9);
system.addComponent("CO2", 0.1);
system.setMixingRule("classic");

ThermodynamicOperations thermoOps = new ThermodynamicOperations(system);

// Create fitting function
VLEFittingFunction func = new VLEFittingFunction(system, thermoOps);
func.setInitialGuess(new double[]{0.1});  // Initial kij guess

// Experimental VLE data: [T(K), P(bar), x_methane]
double[][] expData = {
    {250.0, 15.0, 0.85, 0.02},  // T, P, x, sigma
    {260.0, 20.0, 0.82, 0.02},
    {270.0, 25.0, 0.78, 0.03},
    {280.0, 30.0, 0.75, 0.02}
};

ArrayList<SampleValue> samples = new ArrayList<>();
for (double[] row : expData) {
    double[] dep = {row[0], row[1]};  // T, P as independent vars
    SampleValue s = new SampleValue(row[2], row[3], dep);
    s.setFunction(func);
    samples.add(s);
}

SampleSet set = new SampleSet(samples);
LevenbergMarquardt opt = new LevenbergMarquardt();
opt.setSampleSet(set);
opt.solve();

// Get fitted kij
double kij = func.getFittingParams()[0];
System.out.println("Fitted kij = " + kij);

// Run Monte Carlo for uncertainty
opt.runMonteCarloSimulation(50);
```

### Example 3: Multi-Parameter Fitting

```java
// Fit Antoine equation: ln(Psat) = A - B/(C + T)
public class AntoineFunction extends LevenbergMarquardtFunction {
    
    public AntoineFunction() {
        params = new double[3];
    }
    
    @Override
    public double calcValue(double[] dependentValues) {
        double T = dependentValues[0];  // Temperature in K
        return Math.exp(params[0] - params[1]/(params[2] + T));
    }
    
    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
    }
}

// Set bounds for Antoine parameters
double[][] bounds = {
    {0.0, 50.0},      // A
    {0.0, 10000.0},   // B
    {-300.0, 0.0}     // C (typically negative for Antoine)
};
antoineFunc.setBounds(bounds);
```

---

## Troubleshooting

### Poor Convergence

1. **Check initial guess** - Start closer to expected solution
2. **Check bounds** - Ensure solution is within bounds
3. **Increase iterations** - `setMaxNumberOfIterations(200)`
4. **Check data quality** - Outliers can prevent convergence

### Singular Matrix Errors

1. **Insufficient data** - Need more data points than parameters
2. **Collinear data** - Independent variables too correlated
3. **Poorly scaled parameters** - Normalize parameter magnitudes

### Large Chi-Square

1. **Model inadequate** - Try different model form
2. **Underestimated uncertainties** - Review experimental errors
3. **Systematic errors** - Check for bias in data

---

## References

1. Marquardt, D.W. (1963). An Algorithm for Least-Squares Estimation of Nonlinear Parameters. SIAM J. Appl. Math., 11(2), 431-441.
2. Press, W.H., et al. (2007). Numerical Recipes. Chapter 15: Modeling of Data.
3. Poling, B.E., Prausnitz, J.M., O'Connell, J.P. (2001). The Properties of Gases and Liquids. 5th Edition. McGraw-Hill.
