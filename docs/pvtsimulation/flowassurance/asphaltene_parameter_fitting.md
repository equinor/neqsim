---
title: Asphaltene Parameter Fitting
description: The `AsphalteneOnsetFitting` class provides automated CPA parameter tuning using the Levenberg-Marquardt optimization algorithm. This enables matching CPA model predictions to experimental asphaltene ...
---

# Asphaltene Parameter Fitting

## Overview

The `AsphalteneOnsetFitting` class provides automated CPA parameter tuning using the Levenberg-Marquardt optimization algorithm. This enables matching CPA model predictions to experimental asphaltene onset pressure (AOP) measurements.

## Why Parameter Fitting?

CPA parameters for asphaltenes (association energy ε/R and volume κ) vary between crude oils and must be tuned to experimental data for accurate predictions:

| Parameter | Effect on Onset Pressure |
|-----------|--------------------------|
| Higher ε/R | Earlier precipitation (higher onset P) |
| Higher κ | Stronger aggregation tendency |
| Higher MW | Reduced solubility |

Default parameters provide reasonable estimates, but tuning to measured AOP data improves prediction accuracy significantly.

## Quick Start

```java
import neqsim.pvtsimulation.util.parameterfitting.AsphalteneOnsetFitting;
import neqsim.thermo.system.SystemSrkCPAstatoil;

// 1. Create fluid with asphaltene
SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(373.15, 200.0);
fluid.addComponent("methane", 0.30);
fluid.addTBPfraction("C7", 0.50, 0.150, 0.80);
fluid.addComponent("asphaltene", 0.05);
fluid.setMixingRule("classic");

// 2. Create fitter and add experimental data
AsphalteneOnsetFitting fitter = new AsphalteneOnsetFitting(fluid);
fitter.addOnsetPointCelsius(80.0, 350.0);   // T=80°C, P=350 bar
fitter.addOnsetPointCelsius(100.0, 320.0);  // T=100°C, P=320 bar
fitter.addOnsetPointCelsius(120.0, 280.0);  // T=120°C, P=280 bar

// 3. Set initial guesses and run fitting
fitter.setInitialGuess(3500.0, 0.005);  // ε/R [K], κ [-]
boolean success = fitter.solve();

// 4. Get results
if (success) {
    System.out.println("Fitted ε/R: " + fitter.getFittedAssociationEnergy() + " K");
    System.out.println("Fitted κ: " + fitter.getFittedAssociationVolume());
}
```

## Detailed API

### Constructor

```java
// Create fitter from existing fluid system
AsphalteneOnsetFitting fitter = new AsphalteneOnsetFitting(fluid);
```

The fluid system must:
- Be a CPA system (e.g., `SystemSrkCPAstatoil`)
- Contain an asphaltene component
- Have a mixing rule set

### Adding Experimental Data

```java
// Add point with temperature in Kelvin
fitter.addOnsetPoint(373.15, 320.0);  // T [K], P [bar]

// Add point with temperature in Celsius (convenience method)
fitter.addOnsetPointCelsius(100.0, 320.0);  // T [°C], P [bar]

// Add point with measurement uncertainty
fitter.addOnsetPoint(373.15, 320.0, 10.0);  // T [K], P [bar], stdDev [bar]

// Clear all data points
fitter.clearData();
```

**Recommended**: Use at least 3 data points at different temperatures for robust fitting.

### Setting Initial Guesses

```java
// Two parameters: association energy and volume
fitter.setInitialGuess(3500.0, 0.005);  // ε/R [K], κ [-]

// Single parameter (when fitting only one)
fitter.setInitialGuess(3500.0);
```

### Recommended Initial Guesses

| Oil Type | API Gravity | ε/R [K] | κ |
|----------|-------------|---------|---|
| Light oil | >35° | 2500-3500 | 0.005-0.015 |
| Medium oil | 25-35° | 3000-4000 | 0.003-0.008 |
| Heavy oil | <25° | 3500-4500 | 0.002-0.005 |

### Fitting Parameter Types

Control which parameters are fitted:

```java
import neqsim.pvtsimulation.util.parameterfitting.AsphalteneOnsetFunction.FittingParameterType;

// Fit both association parameters (default)
fitter.setParameterType(FittingParameterType.ASSOCIATION_PARAMETERS);
fitter.setInitialGuess(3500.0, 0.005);

// Fit only association energy
fitter.setParameterType(FittingParameterType.ASSOCIATION_ENERGY);
fitter.setInitialGuess(3500.0);

// Fit only association volume
fitter.setParameterType(FittingParameterType.ASSOCIATION_VOLUME);
fitter.setInitialGuess(0.005);

// Fit binary interaction parameter
fitter.setParameterType(FittingParameterType.BINARY_INTERACTION);
fitter.setInitialGuess(0.0);

// Fit molar mass
fitter.setParameterType(FittingParameterType.MOLAR_MASS);
fitter.setInitialGuess(750.0);
```

### Configuring Pressure Search

```java
// Set pressure range for onset calculation
fitter.setPressureRange(
    500.0,  // Start pressure [bar] - search starts here
    10.0,   // Min pressure [bar] - search stops here
    10.0    // Pressure step [bar] - coarse search step
);
```

### Running the Fit

```java
boolean success = fitter.solve();

if (success) {
    // Fitting converged
    double[] params = fitter.getFittedParameters();
} else {
    // Fitting failed - check initial guesses or data quality
}
```

### Getting Results

```java
// Get fitted parameters
double epsilonR = fitter.getFittedAssociationEnergy();
double kappa = fitter.getFittedAssociationVolume();

// Get all fitted parameters as array
double[] params = fitter.getFittedParameters();

// Check if fitting was successful
boolean success = fitter.isSolved();
```

### Predicting Onset at New Conditions

```java
// Calculate onset pressure at a new temperature
double onsetP = fitter.calculateOnsetPressure(393.15);  // T [K]

// Get the tuned fluid system
SystemInterface tunedFluid = fitter.getTunedSystem();
```

## Algorithm Details

### Levenberg-Marquardt Optimization

The fitting uses the Levenberg-Marquardt algorithm to minimize:

$$
\chi^2 = \sum_{i=1}^{n} \frac{(P_{\text{calc},i} - P_{\text{exp},i})^2}{\sigma_i^2}
$$

Where:
- $P_{\text{calc},i}$ = calculated onset pressure at temperature $T_i$
- $P_{\text{exp},i}$ = measured onset pressure at temperature $T_i$
- $\sigma_i$ = measurement uncertainty

### Convergence Criteria

- Maximum iterations: 50 (configurable)
- Parameter tolerance: 1e-6
- Function tolerance: 1e-8

### Onset Pressure Calculation

For each temperature, the onset pressure is found by:

1. Starting at high pressure (single liquid phase)
2. Decreasing pressure in steps
3. Detecting when a second phase appears
4. Bisecting to find exact onset point

## Best Practices

### Data Quality

✅ Use at least 3 onset points at different temperatures  
✅ Include temperature range spanning expected field conditions  
✅ Use reliable measurement techniques (HPM, light scattering)  
✅ Specify measurement uncertainty for weighted fitting  

### Initial Guesses

✅ Start with literature values for similar oils  
✅ Use the "heavy" guess for conservative prediction  
✅ Try multiple initial guesses if fitting fails  

### Validation

✅ Check that fitted parameters are physically reasonable  
✅ Validate predictions at conditions not used in fitting  
✅ Compare with De Boer screening for consistency  

## Troubleshooting

### Fitting Fails to Converge

**Possible causes:**
1. Poor initial guesses
2. Inconsistent experimental data
3. Fluid system not properly initialized

**Solutions:**
- Try different initial guesses
- Check experimental data consistency
- Ensure mixing rule is set: `fluid.setMixingRule("classic")`

### Fitted Parameters Outside Physical Range

**Expected ranges:**
- ε/R: 2000-5000 K
- κ: 0.001-0.1

**If outside range:**
- Check experimental data quality
- Consider simpler fitting (single parameter)
- Review fluid composition

### Poor Prediction Accuracy

**Possible causes:**
1. Limited temperature range in training data
2. Extrapolating beyond calibration range
3. Composition effects not captured

**Solutions:**
- Add more experimental data points
- Limit predictions to calibrated range
- Consider refitting for different conditions

## Example: Complete Fitting Workflow

```java
import neqsim.pvtsimulation.util.parameterfitting.AsphalteneOnsetFitting;
import neqsim.thermo.system.SystemSrkCPAstatoil;

public class AsphalteneParameterTuning {
    public static void main(String[] args) {
        // Create realistic oil composition
        SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(373.15, 350.0);
        fluid.addComponent("methane", 0.35);
        fluid.addComponent("ethane", 0.08);
        fluid.addComponent("propane", 0.05);
        fluid.addTBPfraction("C7", 0.35, 0.100, 0.75);
        fluid.addTBPfraction("C15", 0.12, 0.200, 0.85);
        fluid.addComponent("asphaltene", 0.05);
        fluid.setMixingRule("classic");
        fluid.init(0);
        
        // Create fitter
        AsphalteneOnsetFitting fitter = new AsphalteneOnsetFitting(fluid);
        
        // Add experimental AOP data from lab tests
        // (Temperature in Celsius, Pressure in bar)
        fitter.addOnsetPointCelsius(60.0, 380.0);   // Cool conditions
        fitter.addOnsetPointCelsius(80.0, 350.0);   // Intermediate
        fitter.addOnsetPointCelsius(100.0, 320.0);  // Reservoir T
        fitter.addOnsetPointCelsius(120.0, 280.0);  // Hot conditions
        
        // Set initial guesses (medium oil)
        fitter.setInitialGuess(3500.0, 0.005);
        
        // Configure pressure search range
        fitter.setPressureRange(500.0, 10.0, 10.0);
        
        // Run fitting
        System.out.println("Starting parameter fitting...");
        boolean success = fitter.solve();
        
        if (success) {
            System.out.println("\n=== FITTING RESULTS ===");
            System.out.printf("Fitted ε/R: %.1f K%n", fitter.getFittedAssociationEnergy());
            System.out.printf("Fitted κ: %.5f%n", fitter.getFittedAssociationVolume());
            
            // Validate: predict onset at test temperatures
            System.out.println("\n=== VALIDATION ===");
            double[] testTemps = {333.15, 353.15, 373.15, 393.15};  // 60-120°C
            double[] measuredP = {380.0, 350.0, 320.0, 280.0};
            
            for (int i = 0; i < testTemps.length; i++) {
                double predP = fitter.calculateOnsetPressure(testTemps[i]);
                double error = predP - measuredP[i];
                System.out.printf("T = %.0f°C: Measured = %.0f bar, Predicted = %.1f bar (Δ = %.1f)%n",
                    testTemps[i] - 273.15, measuredP[i], predP, error);
            }
            
            // Predict at new conditions
            System.out.println("\n=== PREDICTIONS ===");
            double newOnset = fitter.calculateOnsetPressure(413.15);  // 140°C
            System.out.printf("Predicted AOP at 140°C: %.1f bar%n", newOnset);
        } else {
            System.out.println("Fitting failed - try different initial guesses");
        }
    }
}
```

## See Also

- [CPA-Based Calculations](asphaltene_cpa_calculations) - Theory and implementation
- [Method Comparison](asphaltene_method_comparison) - When to use CPA vs De Boer
- [Validation](asphaltene_validation) - Model validation results

## References

1. Li, Z., and Firoozabadi, A. (2010). "Modeling Asphaltene Precipitation by n-Alkanes from Heavy Oils and Bitumens Using Cubic-Plus-Association Equation of State." Energy & Fuels, 24, 1106-1113.

2. Vargas, F.M., Gonzalez, D.L., Hirasaki, G.J., and Chapman, W.G. (2009). "Modeling Asphaltene Phase Behavior in Crude Oil Systems Using the Perturbed Chain Form of the Statistical Associating Fluid Theory (PC-SAFT) Equation of State." Energy & Fuels, 23, 1140-1146.

3. Gonzalez, D.L., Ting, P.D., Hirasaki, G.J., and Chapman, W.G. (2005). "Prediction of Asphaltene Instability under Gas Injection with the PC-SAFT Equation of State." Energy & Fuels, 19, 1230-1234.
