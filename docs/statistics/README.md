---
title: "Statistics Package"
description: "The NeqSim statistics package provides tools for parameter fitting, uncertainty quantification, and data analysis for thermodynamic model development and validation."
---

# Statistics Package

The NeqSim statistics package provides tools for parameter fitting, uncertainty quantification, and data analysis for thermodynamic model development and validation.

## Table of Contents
- [Overview](#overview)
- [Package Structure](#package-structure)
- [Sub-Documentation](#sub-documentation)
- [Core Concepts](#core-concepts)
- [Quick Start](#quick-start)
- [Integration with Thermodynamic Models](#integration-with-thermodynamic-models)

---

## Overview

The statistics package supports:

1. **Parameter Fitting** - Nonlinear regression using Levenberg-Marquardt algorithm
2. **Monte Carlo Simulation** - Uncertainty propagation and confidence intervals
3. **Data Analysis** - Smoothing, filtering, and statistical analysis
4. **Experimental Data Management** - Sample sets and experimental equipment modeling

**Location:** `neqsim.statistics`

**Key Applications:**
- Fitting binary interaction parameters ($k_{ij}$) to experimental VLE data
- Tuning EoS parameters for custom components
- Uncertainty analysis for thermodynamic predictions
- Regression of transport property correlations

---

## Package Structure

```
statistics/
├── parameterfitting/                    # Core parameter fitting framework
│   ├── StatisticsBaseClass.java        # Abstract base for all fitting
│   ├── StatisticsInterface.java        # Interface definition
│   ├── SampleSet.java                  # Collection of experimental points
│   ├── SampleValue.java                # Single experimental data point
│   ├── BaseFunction.java               # Abstract objective function
│   ├── FunctionInterface.java          # Function interface
│   ├── NumericalDerivative.java        # Numerical differentiation
│   └── nonlinearparameterfitting/      # Nonlinear optimization
│       ├── LevenbergMarquardt.java     # L-M optimizer (least squares)
│       ├── LevenbergMarquardtAbsDev.java   # Absolute deviation
│       ├── LevenbergMarquardtBiasDev.java  # Bias deviation
│       └── LevenbergMarquardtFunction.java # Example function
│
├── montecarlosimulation/               # Uncertainty quantification
│   └── MonteCarloSimulation.java       # MC simulation runner
│
├── dataanalysis/                       # Data processing
│   └── datasmoothing/
│       └── DataSmoother.java           # Savitzky-Golay smoothing
│
├── experimentalsamplecreation/         # Sample generation
│   ├── readdatafromfile/               # File I/O for experimental data
│   └── samplecreator/
│       ├── SampleCreator.java          # Base sample creator
│       └── wettedwallcolumnsamplecreator/  # Specialized creator
│
└── experimentalequipmentdata/          # Equipment modeling
    ├── ExperimentalEquipmentData.java
    └── wettedwallcolumndata/           # Wetted wall column
```

---

## Sub-Documentation

Detailed guides for each major subsystem:

| Guide | Description |
|-------|-------------|
| [Parameter Fitting](parameter_fitting.md) | Levenberg-Marquardt optimization, creating objective functions, bounds |
| [Monte Carlo Simulation](monte_carlo_simulation.md) | Uncertainty propagation, confidence intervals, distribution sampling |
| [Data Analysis](data_analysis.md) | Data smoothing, filtering, statistical measures |

---

## Core Concepts

### Sample Values

A `SampleValue` represents one experimental data point:

```java
// Experimental value with uncertainty
double experimentalValue = 0.5;    // Measured value (e.g., pressure)
double standardDeviation = 0.05;  // Experimental uncertainty
double[] independentVariables = {300.0, 0.1};  // e.g., temperature, composition

SampleValue sample = new SampleValue(
    experimentalValue, 
    standardDeviation, 
    independentVariables
);
```

### Sample Sets

A `SampleSet` is a collection of experimental points:

```java
SampleSet sampleSet = new SampleSet();
sampleSet.add(sample1);
sampleSet.add(sample2);
sampleSet.add(sample3);

// Or from array
SampleValue[] samples = {sample1, sample2, sample3};
SampleSet sampleSet = new SampleSet(samples);
```

### Objective Functions

Functions extend `BaseFunction` or `LevenbergMarquardtFunction`:

```java
public class MyObjectiveFunction extends LevenbergMarquardtFunction {
    
    @Override
    public double calcValue(double[] dependentValues) {
        // params[0], params[1], ... are the fitting parameters
        // dependentValues are the independent variables (T, P, x, ...)
        
        double T = dependentValues[0];
        double x = dependentValues[1];
        
        // Calculate model prediction
        double predicted = params[0] * Math.exp(-params[1] / T) * x;
        
        return predicted;
    }
    
    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
    }
}
```

---

## Quick Start

### Basic Parameter Fitting

```java
import neqsim.statistics.parameterfitting.*;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.*;
import java.util.ArrayList;

// 1. Create objective function
MyObjectiveFunction function = new MyObjectiveFunction();

// 2. Set initial parameter guess
double[] initialGuess = {1.0, 500.0};  // Two parameters to fit
function.setInitialGuess(initialGuess);

// 3. Create experimental samples
ArrayList<SampleValue> samples = new ArrayList<>();

double[] x1 = {300.0, 0.1};  // T=300K, x=0.1
SampleValue s1 = new SampleValue(0.05, 0.005, x1);  // exp=0.05 ± 0.005
s1.setFunction(function);
samples.add(s1);

double[] x2 = {350.0, 0.2};
SampleValue s2 = new SampleValue(0.12, 0.01, x2);
s2.setFunction(function);
samples.add(s2);

// Add more samples...

// 4. Create sample set and optimizer
SampleSet sampleSet = new SampleSet(samples);
LevenbergMarquardt optimizer = new LevenbergMarquardt();
optimizer.setSampleSet(sampleSet);

// 5. Solve
optimizer.solve();

// 6. Get results
double[] fittedParams = sampleSet.getSample(0).getFunction().getFittingParams();
System.out.println("Fitted parameters: " + Arrays.toString(fittedParams));

// 7. Display results
optimizer.displayCurveFit();
optimizer.displayResult();
```

### With Monte Carlo Uncertainty

```java
// After solving...
optimizer.runMonteCarloSimulation(100);  // 100 Monte Carlo runs

// This generates samples with normally distributed perturbations
// around experimental values and re-fits, providing parameter distributions
```

---

## Integration with Thermodynamic Models

### Fitting Binary Interaction Parameters

```java
public class KijFittingFunction extends LevenbergMarquardtFunction {
    
    @Override
    public double calcValue(double[] dependentValues) {
        double temperature = dependentValues[0];
        double pressure = dependentValues[1];
        double x_exp = dependentValues[2];  // Experimental composition
        
        // Set up thermodynamic system
        system.setTemperature(temperature);
        system.setPressure(pressure);
        
        // Set kij from fitting parameters
        ((PhaseEos) system.getPhase(0)).getMixingRule()
            .setBinaryInteractionParameter(0, 1, params[0]);
        ((PhaseEos) system.getPhase(1)).getMixingRule()
            .setBinaryInteractionParameter(0, 1, params[0]);
        
        // Flash calculation
        thermoOps.TPflash();
        
        // Return calculated liquid composition
        return system.getPhase(1).getComponent(0).getx();
    }
    
    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
    }
}
```

### Fitting CPA Association Parameters

```java
public class CPAFittingFunction extends LevenbergMarquardtFunction {
    
    @Override
    public double calcValue(double[] dependentValues) {
        double T = dependentValues[0];
        double P = dependentValues[1];
        
        // params[0] = epsilon (association energy)
        // params[1] = beta (association volume)
        
        system.getComponent("water").setAssociationEnergy(params[0]);
        system.getComponent("water").setAssociationVolume(params[1]);
        
        thermoOps.TPflash();
        
        return system.getPhase(1).getDensity("kg/m3");
    }
    
    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
    }
}
```

---

## Statistical Measures

After fitting, several statistics are available:

```java
// Solve first
optimizer.solve();

// Chi-square statistic
double chiSquare = optimizer.calcChiSquare();

// Absolute deviation statistics
optimizer.calcAbsDev();

// Covariance matrix
optimizer.calcCoVarianceMatrix();

// Parameter standard deviations
optimizer.calcParameterStandardDeviation();

// Parameter correlation matrix
optimizer.calcCorrelationMatrix();

// 95% confidence intervals
optimizer.calcParameterUncertainty();
```

---

## Best Practices

### Initial Guess Selection

Good initial guesses are critical for convergence:
- Use physical reasoning to estimate parameter magnitude
- Start with literature values when available
- Try multiple starting points if convergence fails

### Experimental Uncertainties

Proper uncertainty specification affects:
- Weighting of data points in regression
- Confidence intervals on fitted parameters
- Chi-square goodness-of-fit

### Parameter Bounds

Set physical bounds to prevent unphysical solutions:

```java
double[][] bounds = {
    {0.0, 1.0},     // Parameter 0: between 0 and 1
    {100.0, 1000.0} // Parameter 1: between 100 and 1000
};
function.setBounds(bounds);
```

### Convergence Criteria

```java
optimizer.setMaxNumberOfIterations(100);  // Increase if needed
```

---

## References

1. Press, W.H., et al. (2007). Numerical Recipes: The Art of Scientific Computing. Cambridge University Press.
2. Marquardt, D.W. (1963). An Algorithm for Least-Squares Estimation of Nonlinear Parameters. SIAM J. Appl. Math.
3. Savitzky, A., Golay, M.J.E. (1964). Smoothing and Differentiation of Data by Simplified Least Squares Procedures. Anal. Chem.
