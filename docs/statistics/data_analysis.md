# Data Analysis

The data analysis subsystem provides tools for preprocessing, smoothing, and statistical analysis of experimental data.

## Table of Contents
- [Overview](#overview)
- [Data Smoothing](#data-smoothing)
- [Sample Creation](#sample-creation)
- [Experimental Equipment Data](#experimental-equipment-data)
- [Statistical Measures](#statistical-measures)
- [Examples](#examples)

---

## Overview

**Location:** `neqsim.statistics.dataanalysis`

The data analysis package provides:

| Component | Purpose |
|-----------|---------|
| `DataSmoother` | Savitzky-Golay smoothing filter |
| `SampleCreator` | Generate samples from equipment data |
| `ExperimentalEquipmentData` | Interface for equipment measurements |

---

## Data Smoothing

### Savitzky-Golay Filter

**Location:** `neqsim.statistics.dataanalysis.datasmoothing.DataSmoother`

The Savitzky-Golay filter smooths data by fitting local polynomials, preserving signal shape better than simple moving averages.

#### Algorithm

For each data point, fit a polynomial of degree $m$ to a window of $n_L$ points left and $n_R$ points right:

$$y_{smooth}(i) = \sum_{j=-n_L}^{n_R} c_j \cdot y(i+j)$$

where coefficients $c_j$ are computed from the polynomial fit.

#### Basic Usage

```java
import neqsim.statistics.dataanalysis.datasmoothing.DataSmoother;

// Create smoother with window size and polynomial order
int nl = 3;  // Points to the left
int nr = 3;  // Points to the right
int m = 2;   // Polynomial order (quadratic)
int ld = 0;  // Derivative order (0 = smooth, 1 = first derivative)

DataSmoother smoother = new DataSmoother(nl, nr, m, ld);
```

#### Complete Example

```java
// Raw noisy data
double[] rawData = {1.2, 2.1, 2.8, 4.2, 4.9, 6.1, 6.8, 8.2, 8.9, 10.1};

// Create smoother
DataSmoother smoother = new DataSmoother(2, 2, 2, 0);

// Set input data
smoother.setInputNumbers(rawData);

// Run smoothing
smoother.runSmoothing();

// Get smoothed output
double[] smoothedData = smoother.getSmoothedNumbers();

// Print results
for (int i = 0; i < rawData.length; i++) {
    System.out.printf("Raw: %.2f -> Smoothed: %.2f%n", 
        rawData[i], smoothedData[i]);
}
```

#### Parameters

| Parameter | Symbol | Description |
|-----------|--------|-------------|
| `nl` | $n_L$ | Number of points to the left of center |
| `nr` | $n_R$ | Number of points to the right of center |
| `m` | $m$ | Polynomial order (typically 2-4) |
| `ld` | $l_d$ | Derivative order (0=smooth, 1=1st deriv, etc.) |

#### Coefficient Calculation

The `findCoefs()` method computes Savitzky-Golay coefficients:

```java
// Internal coefficient calculation
private void findCoefs() {
    // Uses least-squares polynomial fitting
    // Coefficients stored in coefs[] array
    
    int np = nl + nr + 1;  // Total points in window
    
    // Solve normal equations for polynomial coefficients
    // Returns convolution weights for smoothing
}
```

#### Derivative Estimation

Set `ld > 0` to compute derivatives while smoothing:

```java
// Compute first derivative (slope)
DataSmoother derivSmoother = new DataSmoother(3, 3, 3, 1);
derivSmoother.setInputNumbers(data);
derivSmoother.runSmoothing();
double[] derivatives = derivSmoother.getSmoothedNumbers();

// Compute second derivative (curvature)
DataSmoother deriv2Smoother = new DataSmoother(4, 4, 4, 2);
deriv2Smoother.setInputNumbers(data);
deriv2Smoother.runSmoothing();
double[] secondDerivatives = deriv2Smoother.getSmoothedNumbers();
```

#### Window Size Guidelines

| Data Characteristic | Recommended Window | Polynomial Order |
|--------------------|-------------------|------------------|
| Low noise | nl=2, nr=2 | 2 |
| Moderate noise | nl=4, nr=4 | 2-3 |
| High noise | nl=6, nr=6 | 3-4 |
| Preserving peaks | nl=2, nr=2 | 2 |
| Smooth trends | nl=5, nr=5 | 4 |

**Important:** Window size ($n_L + n_R + 1$) must be greater than polynomial order ($m$).

#### Edge Handling

Edge points cannot use the full window. The implementation handles boundaries by:
- Using asymmetric windows near edges
- Maintaining coefficient recalculation for reduced windows

---

## Sample Creation

### SampleCreator Class

**Location:** `neqsim.statistics.experimentalsamplecreation.samplecreator.SampleCreator`

Creates `SampleValue` objects from experimental equipment data for use in parameter fitting.

```java
import neqsim.statistics.experimentalsamplecreation.samplecreator.SampleCreator;
import neqsim.thermo.system.SystemInterface;

// Link thermodynamic system with equipment data
SampleCreator creator = new SampleCreator();
creator.setThermoSystem(system);
creator.setEquipmentData(equipmentData);

// Create samples
creator.createSamples();
```

### Wetted Wall Column Sample Creator

**Location:** `neqsim.statistics.experimentalsamplecreation.samplecreator.wettedwallcolumnsamplecreator`

Specialized creator for mass transfer experiments:

```java
import neqsim.statistics.experimentalsamplecreation.samplecreator
    .wettedwallcolumnsamplecreator.WettedWallColumnSampleCreator;

WettedWallColumnSampleCreator creator = new WettedWallColumnSampleCreator();
creator.setThermoSystem(system);
creator.setWettedWallColumnData(columnData);
creator.createSamples();

ArrayList<SampleValue> samples = creator.getSamples();
```

---

## Experimental Equipment Data

### ExperimentalEquipmentData Interface

**Location:** `neqsim.statistics.experimentalequipmentdata`

Base interface for experimental equipment measurements:

```java
public interface ExperimentalEquipmentData {
    double[] getMeasuredValues();
    double[] getUncertainties();
    double[] getOperatingConditions();
    String getEquipmentType();
}
```

### Wetted Wall Column Data

**Location:** `neqsim.statistics.experimentalequipmentdata.wettedwallcolumndata`

For mass transfer coefficient measurements:

```java
import neqsim.statistics.experimentalequipmentdata
    .wettedwallcolumndata.WettedWallColumnData;

WettedWallColumnData data = new WettedWallColumnData();
data.setGasFlowRate(0.5);        // [m³/h]
data.setLiquidFlowRate(0.1);     // [L/min]
data.setColumnHeight(0.5);       // [m]
data.setColumnDiameter(0.02);    // [m]
data.setTemperature(298.15);     // [K]
data.setPressure(1.0);           // [bar]
data.setMeasuredKla(0.05);       // Mass transfer coefficient
data.setKlaUncertainty(0.005);   // Uncertainty
```

---

## Statistical Measures

### Chi-Square Statistic

Calculated in `StatisticsBaseClass`:

```java
public double calcChiSquare() {
    double chiSq = 0.0;
    for (int i = 0; i < sampleSet.getLength(); i++) {
        SampleValue sample = sampleSet.getSample(i);
        double exp = sample.getSampleValue();
        double calc = sample.getFunction().calcValue(sample.getDependentValues());
        double sigma = sample.getStandardDeviation();
        
        chiSq += Math.pow((exp - calc) / sigma, 2);
    }
    return chiSq;
}
```

### Absolute Deviation

```java
public void calcAbsDev() {
    absdev = 0.0;
    for (int i = 0; i < sampleSet.getLength(); i++) {
        SampleValue sample = sampleSet.getSample(i);
        double exp = sample.getSampleValue();
        double calc = sample.getFunction().calcValue(sample.getDependentValues());
        
        absdev += Math.abs(exp - calc);
    }
    absdev /= sampleSet.getLength();
}
```

### Bias Deviation

```java
public void calcBiasDev() {
    biasdev = 0.0;
    for (int i = 0; i < sampleSet.getLength(); i++) {
        SampleValue sample = sampleSet.getSample(i);
        double exp = sample.getSampleValue();
        double calc = sample.getFunction().calcValue(sample.getDependentValues());
        
        biasdev += (exp - calc);
    }
    biasdev /= sampleSet.getLength();
}
```

### Relative Deviations

Average Absolute Relative Deviation (AARD):

$$AARD = \frac{1}{N}\sum_{i=1}^{N}\left|\frac{y_i^{exp} - y_i^{calc}}{y_i^{exp}}\right| \times 100\%$$

```java
public double calcAARD() {
    double aard = 0.0;
    for (int i = 0; i < sampleSet.getLength(); i++) {
        SampleValue sample = sampleSet.getSample(i);
        double exp = sample.getSampleValue();
        double calc = sample.getFunction().calcValue(sample.getDependentValues());
        
        if (Math.abs(exp) > 1e-10) {
            aard += Math.abs((exp - calc) / exp);
        }
    }
    return 100.0 * aard / sampleSet.getLength();
}
```

### Covariance Matrix

The covariance matrix $\mathbf{C}$ is computed from the Hessian approximation:

$$\mathbf{C} = (\mathbf{J}^T \mathbf{W} \mathbf{J})^{-1}$$

```java
public void calcCoVarianceMatrix() {
    // Build alpha matrix (J'WJ)
    calcAlphaMatrix();
    
    // Invert to get covariance
    Matrix alphaMatrix = new Matrix(alpha);
    Matrix covariance = alphaMatrix.inverse();
    coVarianceMatrix = covariance.getArray();
}
```

### Correlation Matrix

Parameter correlation from covariance:

$$\rho_{ij} = \frac{C_{ij}}{\sqrt{C_{ii}C_{jj}}}$$

```java
public void calcCorrelationMatrix() {
    calcCoVarianceMatrix();
    
    int n = coVarianceMatrix.length;
    parameterCorrelationMatrix = new double[n][n];
    
    for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
            parameterCorrelationMatrix[i][j] = coVarianceMatrix[i][j] /
                Math.sqrt(coVarianceMatrix[i][i] * coVarianceMatrix[j][j]);
        }
    }
}
```

---

## Examples

### Example 1: Smoothing Noisy Thermodynamic Data

```java
import neqsim.statistics.dataanalysis.datasmoothing.DataSmoother;

// Noisy vapor pressure data
double[] temperatures = {300, 310, 320, 330, 340, 350, 360, 370, 380, 390};
double[] pressures = {0.52, 0.88, 1.45, 2.32, 3.58, 5.45, 7.89, 11.2, 15.4, 21.1};

// Add simulated noise
java.util.Random rng = new java.util.Random(42);
double[] noisyPressures = new double[pressures.length];
for (int i = 0; i < pressures.length; i++) {
    noisyPressures[i] = pressures[i] * (1 + 0.05 * rng.nextGaussian());
}

// Smooth the data
DataSmoother smoother = new DataSmoother(2, 2, 2, 0);
smoother.setInputNumbers(noisyPressures);
smoother.runSmoothing();
double[] smoothed = smoother.getSmoothedNumbers();

// Compare
System.out.println("T(K)\tNoisy P\tSmoothed P\tTrue P");
for (int i = 0; i < temperatures.length; i++) {
    System.out.printf("%.0f\t%.3f\t%.3f\t\t%.3f%n",
        temperatures[i], noisyPressures[i], smoothed[i], pressures[i]);
}
```

### Example 2: Computing Data Quality Metrics

```java
import neqsim.statistics.parameterfitting.*;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.*;

// After fitting...
optimizer.solve();

// Calculate all statistics
double chiSq = optimizer.calcChiSquare();
optimizer.calcAbsDev();
optimizer.calcCoVarianceMatrix();
optimizer.calcCorrelationMatrix();
optimizer.calcParameterStandardDeviation();

// Report
System.out.println("=== Fitting Statistics ===");
System.out.printf("Chi-square: %.4f%n", chiSq);
System.out.printf("Reduced chi-square: %.4f%n", 
    chiSq / (sampleSet.getLength() - numParams));
System.out.printf("Absolute deviation: %.4f%n", optimizer.absdev);

System.out.println("\nParameter values and uncertainties:");
double[] params = function.getFittingParams();
for (int i = 0; i < params.length; i++) {
    System.out.printf("  p[%d] = %.6f ± %.6f%n", 
        i, params[i], optimizer.parameterStandardDeviation[i]);
}

System.out.println("\nParameter correlations:");
for (int i = 0; i < params.length; i++) {
    for (int j = 0; j < params.length; j++) {
        System.out.printf("%.3f ", optimizer.parameterCorrelationMatrix[i][j]);
    }
    System.out.println();
}
```

### Example 3: Preprocessing Experimental Data

```java
import neqsim.statistics.dataanalysis.datasmoothing.DataSmoother;
import neqsim.statistics.parameterfitting.*;

// Raw experimental data with noise
double[][] rawData = {
    {300.0, 0.52, 0.03},  // T, P_measured, P_uncertainty
    {310.0, 0.91, 0.05},
    {320.0, 1.38, 0.07},
    // ... more data
};

// Extract pressure values
double[] pressures = new double[rawData.length];
for (int i = 0; i < rawData.length; i++) {
    pressures[i] = rawData[i][1];
}

// Smooth pressures
DataSmoother smoother = new DataSmoother(2, 2, 2, 0);
smoother.setInputNumbers(pressures);
smoother.runSmoothing();
double[] smoothedPressures = smoother.getSmoothedNumbers();

// Create samples with smoothed values
ArrayList<SampleValue> samples = new ArrayList<>();
for (int i = 0; i < rawData.length; i++) {
    double[] dep = {rawData[i][0]};  // Temperature
    double value = smoothedPressures[i];  // Smoothed pressure
    double sigma = rawData[i][2];  // Original uncertainty
    
    SampleValue s = new SampleValue(value, sigma, dep);
    s.setFunction(myFunction);
    samples.add(s);
}

// Proceed with fitting...
```

### Example 4: Derivative Estimation

```java
// Estimate heat capacity from enthalpy vs temperature
double[] temperatures = {300, 320, 340, 360, 380, 400};  // K
double[] enthalpies = {2000, 2200, 2420, 2660, 2920, 3200};  // J/mol

// Compute dH/dT using Savitzky-Golay derivative
DataSmoother derivSmoother = new DataSmoother(1, 1, 2, 1);
derivSmoother.setInputNumbers(enthalpies);
derivSmoother.runSmoothing();
double[] rawDerivatives = derivSmoother.getSmoothedNumbers();

// Scale by temperature spacing
double dT = temperatures[1] - temperatures[0];  // Assuming uniform spacing
double[] heatCapacity = new double[rawDerivatives.length];
for (int i = 0; i < rawDerivatives.length; i++) {
    heatCapacity[i] = rawDerivatives[i] / dT;
}

System.out.println("T(K)\tCp (J/mol/K)");
for (int i = 0; i < temperatures.length; i++) {
    System.out.printf("%.0f\t%.2f%n", temperatures[i], heatCapacity[i]);
}
```

---

## Best Practices

### Data Preprocessing

1. **Outlier detection** - Remove or down-weight outliers before fitting
2. **Smoothing choice** - Use S-G for preserving peak shapes
3. **Uncertainty estimation** - Propagate smoothing effects to uncertainties

### Window Selection

- Larger windows → more smoothing, potential signal distortion
- Smaller windows → less smoothing, more noise passes through
- Always test visually before final analysis

### Derivative Estimation

- Use S-G derivatives instead of finite differences
- Higher polynomial order for smoother derivatives
- Validate against known analytical derivatives

---

## References

1. Savitzky, A., Golay, M.J.E. (1964). Smoothing and Differentiation of Data by Simplified Least Squares Procedures. Analytical Chemistry, 36(8), 1627-1639.
2. Press, W.H., et al. (2007). Numerical Recipes: The Art of Scientific Computing. Chapter 14: Interpolation and Extrapolation.
3. Orfanidis, S.J. (1996). Introduction to Signal Processing. Prentice Hall.
