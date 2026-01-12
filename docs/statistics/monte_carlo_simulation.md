# Monte Carlo Simulation

Monte Carlo simulation provides uncertainty quantification for fitted parameters by propagating experimental uncertainties through the fitting process.

## Table of Contents
- [Overview](#overview)
- [Theory](#theory)
- [Implementation](#implementation)
- [Running Simulations](#running-simulations)
- [Interpreting Results](#interpreting-results)
- [Integration with Parameter Fitting](#integration-with-parameter-fitting)
- [Examples](#examples)
- [Best Practices](#best-practices)

---

## Overview

**Location:** `neqsim.statistics.montecarlosimulation`

Monte Carlo simulation addresses the question: *Given experimental uncertainties, what is the uncertainty in fitted parameters?*

**Key Concept:** Run many parameter fits with randomly perturbed experimental data to build a distribution of fitted parameter values.

```
Experimental Data ± σ
        ↓
    Random Perturbation (N times)
        ↓
    N Parameter Fits
        ↓
    Parameter Distribution
        ↓
    Mean, StdDev, Confidence Intervals
```

---

## Theory

### Uncertainty Propagation

Given experimental measurements $y_i \pm \sigma_i$, Monte Carlo simulation:

1. Generates $N$ synthetic datasets where each measurement is replaced by:
   $$y_i^{(k)} = y_i + \epsilon_i^{(k)}$$
   where $\epsilon_i^{(k)} \sim \mathcal{N}(0, \sigma_i^2)$

2. Fits parameters $\vec{p}^{(k)}$ to each synthetic dataset

3. Computes statistics from the parameter ensemble:
   - Mean: $\bar{p}_j = \frac{1}{N}\sum_{k=1}^{N} p_j^{(k)}$
   - Standard deviation: $s_j = \sqrt{\frac{1}{N-1}\sum_{k=1}^{N}(p_j^{(k)} - \bar{p}_j)^2}$

### Advantages

- **Model-independent** - Works for any objective function
- **No linearity assumptions** - Valid for highly nonlinear models
- **Handles correlations** - Captures parameter covariance naturally
- **Distribution shape** - Reveals non-Gaussian parameter distributions

### When to Use

| Situation | Recommendation |
|-----------|----------------|
| Linear or mildly nonlinear models | Covariance matrix from Levenberg-Marquardt |
| Highly nonlinear models | Monte Carlo simulation |
| Non-Gaussian errors | Monte Carlo simulation |
| Correlated experimental errors | Monte Carlo with correlated sampling |
| Publication-quality uncertainties | Monte Carlo simulation |

---

## Implementation

### MonteCarloSimulation Class

```java
import neqsim.statistics.montecarlosimulation.MonteCarloSimulation;
import neqsim.statistics.parameterfitting.StatisticsInterface;

public class MonteCarloSimulation {
    private StatisticsInterface baseCase;
    private int numberOfRuns = 50;
    
    // Creates randomized sample sets and re-fits
    public void runSimulation() {
        for (int i = 0; i < numberOfRuns; i++) {
            StatisticsInterface runCase = baseCase.clone();
            runCase.setSampleSet(
                baseCase.getSampleSet().createNewNormalDistributedSet()
            );
            runCase.init();
            runCase.solve();
        }
    }
    
    // Collects fitted parameters from all runs
    public double[][] createReportMatrix() { ... }
}
```

### Sample Randomization

The `SampleSet.createNewNormalDistributedSet()` method:

```java
public SampleSet createNewNormalDistributedSet() {
    SampleSet newSet = new SampleSet();
    Normal normalDist = new Normal(0, 1, new MersenneTwister());
    
    for (SampleValue sample : samples) {
        // Perturb experimental value by its uncertainty
        double perturbedValue = sample.getSampleValue() 
            + normalDist.nextDouble() * sample.getStandardDeviation();
        
        SampleValue newSample = new SampleValue(
            perturbedValue,
            sample.getStandardDeviation(),
            sample.getDependentValues()
        );
        newSample.setFunction(sample.getFunction().clone());
        newSet.add(newSample);
    }
    return newSet;
}
```

Uses the **Colt library's** Normal distribution with Mersenne Twister RNG.

---

## Running Simulations

### Method 1: Via StatisticsBaseClass

The simplest approach uses the built-in method:

```java
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;

// Create and set up optimizer
LevenbergMarquardt optimizer = new LevenbergMarquardt();
optimizer.setSampleSet(sampleSet);

// Fit once to get best-fit parameters
optimizer.solve();
System.out.println("Best fit chi-square: " + optimizer.calcChiSquare());

// Run Monte Carlo simulation
int numberOfRuns = 100;
optimizer.runMonteCarloSimulation(numberOfRuns);
```

### Method 2: Direct MonteCarloSimulation

For more control:

```java
import neqsim.statistics.montecarlosimulation.MonteCarloSimulation;

// Create Monte Carlo simulation
MonteCarloSimulation mc = new MonteCarloSimulation(optimizer);
mc.setNumberOfRuns(100);

// Run simulation
mc.runSimulation();

// Get results matrix
double[][] results = mc.createReportMatrix();
// results[runIndex][parameterIndex]
```

### Configuring Number of Runs

| Purpose | Recommended Runs |
|---------|------------------|
| Quick estimate | 50-100 |
| Standard analysis | 500-1000 |
| Publication quality | 5000-10000 |
| Parameter distribution shape | 10000+ |

More runs provide:
- More stable statistics
- Better distribution characterization
- Slower computation

---

## Interpreting Results

### Report Matrix Structure

```java
double[][] results = mc.createReportMatrix();

// results[i][j] = value of parameter j in run i
int numRuns = results.length;
int numParams = results[0].length;
```

### Computing Statistics

```java
// Calculate mean and standard deviation
double[] means = new double[numParams];
double[] stds = new double[numParams];

for (int j = 0; j < numParams; j++) {
    double sum = 0, sumSq = 0;
    for (int i = 0; i < numRuns; i++) {
        sum += results[i][j];
        sumSq += results[i][j] * results[i][j];
    }
    means[j] = sum / numRuns;
    stds[j] = Math.sqrt(sumSq/numRuns - means[j]*means[j]);
}
```

### Confidence Intervals

For 95% confidence interval (assuming normal distribution):

$$p_j \pm 1.96 \times s_j$$

For small sample sizes, use t-distribution:

$$p_j \pm t_{0.975, N-1} \times s_j$$

### Percentile-Based Intervals

More robust for non-Gaussian distributions:

```java
import java.util.Arrays;

// Sort parameter values
double[] paramJ = new double[numRuns];
for (int i = 0; i < numRuns; i++) {
    paramJ[i] = results[i][j];
}
Arrays.sort(paramJ);

// 95% confidence interval
double lower = paramJ[(int)(0.025 * numRuns)];
double upper = paramJ[(int)(0.975 * numRuns)];
```

### Parameter Correlation

```java
// Calculate correlation between parameters i and j
double sumXY = 0, sumX = 0, sumY = 0, sumX2 = 0, sumY2 = 0;
for (int k = 0; k < numRuns; k++) {
    sumX += results[k][i];
    sumY += results[k][j];
    sumXY += results[k][i] * results[k][j];
    sumX2 += results[k][i] * results[k][i];
    sumY2 += results[k][j] * results[k][j];
}

double correlation = (numRuns*sumXY - sumX*sumY) /
    Math.sqrt((numRuns*sumX2 - sumX*sumX) * (numRuns*sumY2 - sumY*sumY));
```

---

## Integration with Parameter Fitting

### Complete Workflow

```java
import neqsim.statistics.parameterfitting.*;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.*;
import java.util.ArrayList;

// 1. Create objective function
MyFittingFunction function = new MyFittingFunction();
function.setInitialGuess(new double[]{1.0, 100.0});

// 2. Load experimental data with uncertainties
ArrayList<SampleValue> samples = new ArrayList<>();
for (int i = 0; i < data.length; i++) {
    double[] indepVars = {data[i][0]};          // x
    double expValue = data[i][1];               // y
    double uncertainty = data[i][2];            // σy
    
    SampleValue s = new SampleValue(expValue, uncertainty, indepVars);
    s.setFunction(function.clone());
    samples.add(s);
}

// 3. Create sample set and optimizer
SampleSet sampleSet = new SampleSet(samples);
LevenbergMarquardt optimizer = new LevenbergMarquardt();
optimizer.setSampleSet(sampleSet);

// 4. Solve for best-fit parameters
optimizer.solve();
double[] bestFit = function.getFittingParams();
System.out.printf("Best fit: a=%.4f, b=%.4f%n", bestFit[0], bestFit[1]);

// 5. Calculate analytical uncertainties
optimizer.calcCoVarianceMatrix();
optimizer.calcParameterStandardDeviation();
double[] analyticStd = optimizer.parameterStandardDeviation;
System.out.printf("Analytic σ: σa=%.4f, σb=%.4f%n", 
    analyticStd[0], analyticStd[1]);

// 6. Run Monte Carlo simulation
optimizer.runMonteCarloSimulation(500);

// 7. Get Monte Carlo statistics
// (Access via the internal arrays populated by runMonteCarloSimulation)
```

### Comparing Analytical and Monte Carlo Uncertainties

The covariance matrix from Levenberg-Marquardt provides analytical uncertainties:

```java
optimizer.calcCoVarianceMatrix();
optimizer.calcParameterStandardDeviation();
```

Monte Carlo provides empirical uncertainties from the parameter distribution.

**Agreement** indicates the model behaves approximately linearly near the optimum.

**Disagreement** may indicate:
- Strong nonlinearity
- Parameter boundaries affecting results
- Non-Gaussian experimental errors

---

## Examples

### Example 1: Basic Monte Carlo

```java
import neqsim.statistics.parameterfitting.*;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.*;

// Simple linear function: y = a*x + b
public class LinearFunction extends LevenbergMarquardtFunction {
    public LinearFunction() { params = new double[2]; }
    
    @Override
    public double calcValue(double[] dep) {
        return params[0] * dep[0] + params[1];
    }
    
    @Override
    public void setFittingParams(int i, double val) { params[i] = val; }
}

// Main code
LinearFunction func = new LinearFunction();
func.setInitialGuess(new double[]{1.0, 0.0});

// Data with 5% uncertainty
double[][] data = {
    {1.0, 2.1, 0.1},
    {2.0, 4.0, 0.2},
    {3.0, 6.2, 0.3},
    {4.0, 8.1, 0.4},
    {5.0, 9.8, 0.5}
};

ArrayList<SampleValue> samples = new ArrayList<>();
for (double[] row : data) {
    SampleValue s = new SampleValue(row[1], row[2], new double[]{row[0]});
    s.setFunction(func);
    samples.add(s);
}

SampleSet set = new SampleSet(samples);
LevenbergMarquardt opt = new LevenbergMarquardt();
opt.setSampleSet(set);
opt.solve();

System.out.println("Best fit: a=" + func.params[0] + ", b=" + func.params[1]);

// Run Monte Carlo
opt.runMonteCarloSimulation(1000);
```

### Example 2: Thermodynamic Parameter Uncertainty

```java
// Fitting kij for methane-CO2 with uncertainty estimation

public class KijFunction extends LevenbergMarquardtFunction {
    private SystemInterface system;
    private ThermodynamicOperations thermoOps;
    
    public KijFunction(SystemInterface sys) {
        this.system = sys;
        this.thermoOps = new ThermodynamicOperations(sys);
        params = new double[1];
    }
    
    @Override
    public double calcValue(double[] dep) {
        double T = dep[0];
        double P = dep[1];
        
        // Set kij
        system.getPhase(0).getMixingRule()
            .setBinaryInteractionParameter(0, 1, params[0]);
        system.getPhase(1).getMixingRule()
            .setBinaryInteractionParameter(0, 1, params[0]);
        
        system.setTemperature(T);
        system.setPressure(P);
        system.init(0);
        thermoOps.TPflash();
        
        return system.getPhase(1).getComponent(0).getx();
    }
    
    @Override
    public void setFittingParams(int i, double val) { params[i] = val; }
}

// Setup
SystemInterface sys = new SystemSrkEos(280.0, 20.0);
sys.addComponent("methane", 0.9);
sys.addComponent("CO2", 0.1);
sys.setMixingRule("classic");

KijFunction func = new KijFunction(sys);
func.setInitialGuess(new double[]{0.05});

// Experimental VLE data: T, P, x_methane, sigma
double[][] expData = {
    {250.0, 15.0, 0.88, 0.02},
    {260.0, 20.0, 0.84, 0.02},
    {270.0, 25.0, 0.80, 0.03}
};

ArrayList<SampleValue> samples = new ArrayList<>();
for (double[] row : expData) {
    SampleValue s = new SampleValue(row[2], row[3], new double[]{row[0], row[1]});
    s.setFunction(func);
    samples.add(s);
}

SampleSet set = new SampleSet(samples);
LevenbergMarquardt opt = new LevenbergMarquardt();
opt.setSampleSet(set);
opt.solve();

System.out.printf("Fitted kij = %.4f%n", func.params[0]);

// Monte Carlo for uncertainty
opt.runMonteCarloSimulation(200);
opt.calcParameterStandardDeviation();
System.out.printf("kij uncertainty = ±%.4f%n", opt.parameterStandardDeviation[0]);
```

### Example 3: Analyzing Parameter Distribution

```java
import neqsim.statistics.montecarlosimulation.MonteCarloSimulation;

// After fitting...
MonteCarloSimulation mc = new MonteCarloSimulation(optimizer);
mc.setNumberOfRuns(1000);
mc.runSimulation();

double[][] results = mc.createReportMatrix();

// Histogram analysis
int numBins = 20;
double minVal = Double.MAX_VALUE, maxVal = Double.MIN_VALUE;
for (int i = 0; i < results.length; i++) {
    minVal = Math.min(minVal, results[i][0]);
    maxVal = Math.max(maxVal, results[i][0]);
}

int[] histogram = new int[numBins];
double binWidth = (maxVal - minVal) / numBins;

for (int i = 0; i < results.length; i++) {
    int bin = (int)((results[i][0] - minVal) / binWidth);
    if (bin == numBins) bin--;
    histogram[bin]++;
}

// Print histogram
for (int b = 0; b < numBins; b++) {
    double binCenter = minVal + (b + 0.5) * binWidth;
    System.out.printf("%.4f: %s%n", binCenter, StringUtils.repeat("*", histogram[b]/2));
}
```

---

## Best Practices

### Uncertainty Specification

- Use realistic experimental uncertainties
- Include all significant error sources
- Consider systematic vs random errors

### Number of Runs

| Data Size | Model Complexity | Suggested Runs |
|-----------|------------------|----------------|
| <10 points | Simple | 100-500 |
| 10-100 points | Moderate | 500-1000 |
| >100 points | Complex | 1000-5000 |

### Convergence Checking

Ensure each Monte Carlo run converges:

```java
// In a custom Monte Carlo loop
for (int run = 0; run < numRuns; run++) {
    LevenbergMarquardt opt = new LevenbergMarquardt();
    opt.setSampleSet(randomizedSet);
    opt.solve();
    
    // Check convergence
    if (opt.calcChiSquare() > 100 * baseChiSquare) {
        // Skip this run or investigate
        System.out.println("Warning: Run " + run + " may not have converged");
    }
}
```

### Reproducibility

Set random seed for reproducible results:

```java
// The Colt library uses MersenneTwister
// For reproducibility, would need to modify SampleSet implementation
```

### Computational Efficiency

- Start with fewer runs for debugging
- Parallelize if independent (each run is independent)
- Cache expensive calculations in objective function

---

## Limitations

1. **Computational cost** - N fits required
2. **Assumes independent measurements** - Standard implementation
3. **No systematic error propagation** - Only random uncertainties
4. **Memory for large N** - Stores all fitted parameters

---

## References

1. Press, W.H., et al. (2007). Numerical Recipes. Chapter 7: Random Numbers.
2. Anderson, T.W. (2003). An Introduction to Multivariate Statistical Analysis.
3. Efron, B., Tibshirani, R.J. (1993). An Introduction to the Bootstrap. Chapman & Hall.
