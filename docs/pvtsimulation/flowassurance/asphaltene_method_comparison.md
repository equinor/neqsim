---
title: Asphaltene Method Comparison
description: "NeqSim provides two complementary approaches for asphaltene stability analysis."
---

# Asphaltene Method Comparison

## Overview

NeqSim provides two complementary approaches for asphaltene stability analysis:

1. **De Boer Screening**: Fast, empirical correlation for initial assessment
2. **CPA Thermodynamic Modeling**: Rigorous EOS-based onset calculations

This document explains when to use each method and how to compare their results.

## Method Summary

| Aspect | De Boer | CPA |
|--------|---------|-----|
| **Speed** | Milliseconds | Seconds to minutes |
| **Input Required** | P_res, P_bub, ρ | Full composition + properties |
| **Output** | Risk category | Onset P, T, amounts |
| **Accuracy** | Conservative screening | Predictive (if tuned) |
| **Composition Effects** | No | Yes |
| **Temperature Effects** | No | Yes |
| **Injection Effects** | No | Yes |

## Decision Framework

### When to Use De Boer Only

✅ Early field screening with limited data  
✅ Quick portfolio risk ranking  
✅ Conservative go/no-go decisions  
✅ Baseline risk communication  

### When to Use CPA Only

✅ Detailed well/facility design  
✅ Operating envelope definition  
✅ Gas injection impact assessment  
✅ Inhibitor effectiveness evaluation  

### When to Use Both

✅ Field development planning (screen then analyze)  
✅ Validating thermodynamic model predictions  
✅ Communicating risk to non-technical stakeholders  
✅ Comprehensive flow assurance studies  

## Using AsphalteneMethodComparison

### Basic Comparison

```java
import neqsim.pvtsimulation.flowassurance.AsphalteneMethodComparison;
import neqsim.thermo.system.SystemSrkCPAstatoil;

// Create CPA fluid
SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(373.15, 350.0);
fluid.addComponent("methane", 0.40);
fluid.addComponent("n-heptane", 0.50);
fluid.addComponent("asphaltene", 0.10);
fluid.setMixingRule("classic");
fluid.init(0);
fluid.init(1);

// Create comparison
AsphalteneMethodComparison comparison = new AsphalteneMethodComparison(fluid);
comparison.setReservoirPressure(350.0);
comparison.setBubblePointPressure(150.0);
comparison.setInSituDensity(720.0);

// Run comparison
String report = comparison.runComparison();
System.out.println(report);
```

### Sample Output

```
=================================================
ASPHALTENE STABILITY: METHOD COMPARISON REPORT
=================================================

FLUID INFORMATION
-----------------
Temperature: 100.0 °C
Pressure: 350.0 bar
Number of Components: 3

DE BOER SCREENING RESULTS
-------------------------
Undersaturation: 200.0 bar
In-situ Density: 720.0 kg/m³
Risk Level: MODERATE_PROBLEM
Risk Index: 0.55

CPA THERMODYNAMIC RESULTS
-------------------------
Asphaltene Onset Pressure: 185.0 bar
Above Bubble Point: Yes
Precipitation Expected: Yes
Onset Margin: 35.0 bar above bubble point

COMPARISON SUMMARY
------------------
De Boer Risk: MODERATE
CPA Prediction: Precipitation at 185 bar

Agreement: CONSISTENT
Both methods indicate asphaltene precipitation 
risk during normal production.

RECOMMENDATIONS
---------------
1. Consider asphaltene inhibitor injection
2. Design for periodic wellbore cleanout
3. Monitor production for pressure decline
4. Validate with laboratory AOP test
```

### Quick Summary

For rapid assessment:

```java
String summary = comparison.getQuickSummary();
System.out.println(summary);
```

Output:
```
De Boer: MODERATE_PROBLEM | CPA Onset: 185 bar | Agreement: CONSISTENT
```

## Interpretation Guidelines

### Consistent Results

| De Boer | CPA | Interpretation |
|---------|-----|----------------|
| NO_PROBLEM | No onset found | Low risk, minimal mitigation |
| SLIGHT_PROBLEM | Onset near P_bub | Monitor during late life |
| MODERATE_PROBLEM | Onset > P_bub | Active mitigation needed |
| SEVERE_PROBLEM | Onset >> P_bub | Significant risk, design for it |

### Inconsistent Results

| De Boer | CPA | Possible Causes |
|---------|-----|-----------------|
| HIGH | No onset | De Boer conservative; unusual composition |
| LOW | Onset found | Light oil with high asphaltene; check composition |

When results disagree:
1. **Review input data quality**
2. **Check CPA model tuning**
3. **Consider laboratory validation**
4. **Use more conservative result for design**

## Example Workflows

### Workflow 1: Field Development Screening

```java
// Step 1: De Boer screening of all fluids
List<String> needsDetailedAnalysis = new ArrayList<>();

for (FluidSample sample : allSamples) {
    DeBoerAsphalteneScreening screening = new DeBoerAsphalteneScreening(
        sample.getReservoirPressure(),
        sample.getBubblePoint(),
        sample.getInSituDensity()
    );
    
    String risk = screening.evaluateRisk();
    
    if (!risk.equals("NO_PROBLEM")) {
        needsDetailedAnalysis.add(sample.getName());
    }
}

// Step 2: CPA analysis only for flagged samples
for (String sampleName : needsDetailedAnalysis) {
    // Create detailed CPA model
    SystemSrkCPAstatoil fluid = createCPAFluid(sampleName);
    
    AsphalteneStabilityAnalyzer analyzer = 
        new AsphalteneStabilityAnalyzer(fluid);
    
    double onsetP = analyzer.calculateOnsetPressure();
    
    System.out.printf("Sample %s: Onset at %.1f bar%n", 
                      sampleName, onsetP);
}
```

### Workflow 2: Operating Envelope Definition

```java
// Use CPA to define safe operating window
AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(fluid);

// Generate precipitation boundary
double[][] envelope = analyzer.generatePrecipitationEnvelope(
    280.0, 420.0, 10.0  // Temperature range [K]
);

// Define operating envelope with safety margin
double safetyMargin = 20.0;  // bar below onset

System.out.println("Safe Operating Envelope:");
System.out.println("T [°C], Max P [bar]");
for (int i = 0; i < envelope[0].length; i++) {
    double T_C = envelope[0][i] - 273.15;
    double maxSafeP = envelope[1][i] - safetyMargin;
    System.out.printf("%.0f, %.1f%n", T_C, maxSafeP);
}
```

### Workflow 3: Blending Compatibility

```java
// Base fluid
SystemSrkCPAstatoil baseFluid = createFluid("FieldA");

// Blend fluid  
SystemSrkCPAstatoil blendFluid = createFluid("FieldB");

// Test blend ratios
double[] blendRatios = {0.0, 0.25, 0.50, 0.75, 1.0};

System.out.println("Blend Compatibility Study");
System.out.println("-------------------------");

for (double ratio : blendRatios) {
    SystemSrkCPAstatoil mixed = blendFluids(baseFluid, blendFluid, ratio);
    
    AsphalteneStabilityAnalyzer analyzer = 
        new AsphalteneStabilityAnalyzer(mixed);
    
    double onsetP = analyzer.calculateOnsetPressure();
    String deBoer = analyzer.deBoerScreening();
    
    System.out.printf("%.0f%% Field B: Onset = %.1f bar, De Boer = %s%n",
                      ratio * 100, onsetP, deBoer);
}
```

## Model Validation

### Laboratory Data Requirements

For CPA model tuning:

| Test | Purpose | Priority |
|------|---------|----------|
| **AOP Test** | Onset pressure at reservoir T | Required |
| **HPM Analysis** | Onset detection and quantification | Recommended |
| **SARA Analysis** | Composition for characterization | Required |
| **Titration** | Onset with n-heptane at ambient | Optional |
| **Density** | For De Boer screening | Required |

### Automated Parameter Tuning

NeqSim provides the `AsphalteneOnsetFitting` class for automated CPA parameter tuning:

```java
import neqsim.pvtsimulation.util.parameterfitting.AsphalteneOnsetFitting;

// Create fitter with your fluid system
AsphalteneOnsetFitting fitter = new AsphalteneOnsetFitting(fluid);

// Add experimental AOP data from lab tests
fitter.addOnsetPointCelsius(80.0, 350.0);   // 80°C, 350 bar
fitter.addOnsetPointCelsius(100.0, 320.0);  // 100°C, 320 bar
fitter.addOnsetPointCelsius(120.0, 280.0);  // 120°C, 280 bar

// Set initial parameter guesses based on oil type
// Light oils: epsilon/R=3000, kappa=0.01
// Heavy oils: epsilon/R=4000, kappa=0.003
fitter.setInitialGuess(3500.0, 0.005);

// Run Levenberg-Marquardt optimization
boolean success = fitter.solve();

if (success) {
    // Get fitted parameters
    double epsilonR = fitter.getFittedAssociationEnergy();
    double kappa = fitter.getFittedAssociationVolume();
    
    System.out.printf("Fitted ε/R: %.1f K%n", epsilonR);
    System.out.printf("Fitted κ: %.4f%n", kappa);
    
    // Predict onset at new temperature
    double predictedAOP = fitter.calculateOnsetPressure(393.15);  // 120°C
    System.out.printf("Predicted AOP at 120°C: %.1f bar%n", predictedAOP);
}
```

### Manual Tuning Process

For manual iteration without the automated fitter:

```java
// Experimental AOP
double measuredAOP = 195.0;  // bar at 100°C

// Initial CPA prediction
AsphalteneStabilityAnalyzer analyzer = 
    new AsphalteneStabilityAnalyzer(fluid);
double predictedAOP = analyzer.calculateOnsetPressure();

// Calculate error
double error = predictedAOP - measuredAOP;
System.out.printf("Initial error: %.1f bar%n", error);

// Adjust asphaltene parameters to match
// Options: molecular weight, critical properties, kij values
// Iterate until error < tolerance
```

## Performance Comparison

### Calculation Times (Typical)

| Operation | Time | Notes |
|-----------|------|-------|
| De Boer screening | < 1 ms | Single correlation evaluation |
| De Boer batch (1000 samples) | < 100 ms | Highly parallelizable |
| CPA single flash | 10-100 ms | Depends on composition |
| CPA onset pressure | 1-5 s | Multiple flashes + bisection |
| CPA full envelope | 10-60 s | Many onset calculations |
| Parameter fitting (3 points) | 10-60 s | Depends on convergence |
| Full comparison report | 2-10 s | Both methods + formatting |

### When Speed Matters

```java
// For real-time monitoring: Use De Boer only
if (isRealTimeMonitoring) {
    DeBoerAsphalteneScreening screening = 
        new DeBoerAsphalteneScreening(pRes, pBub, density);
    return screening.calculateRiskIndex();
}

// For batch screening: Use De Boer first
if (numberOfSamples > 100) {
    // Screen with De Boer
    List<Sample> flagged = deBoerScreen(samples);
    
    // CPA only on flagged samples
    for (Sample s : flagged) {
        runCPAAnalysis(s);
    }
}
```

## Best Practices

### Do's

✅ **Start with De Boer** for initial assessment  
✅ **Validate CPA** with experimental data before design  
✅ **Use both** for comprehensive studies  
✅ **Document assumptions** in both methods  
✅ **Apply safety margins** to predicted onset  

### Don'ts

❌ **Don't use CPA without tuning** for critical decisions  
❌ **Don't ignore De Boer warnings** even if CPA looks safe  
❌ **Don't over-interpret** small onset pressure differences  
❌ **Don't extrapolate** beyond validated conditions  

## Example: Complete Comparison

```java
import neqsim.pvtsimulation.flowassurance.*;
import neqsim.thermo.system.SystemSrkCPAstatoil;

public class AsphalteneStudy {
    public static void main(String[] args) {
        // Create realistic oil composition
        SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(373.15, 350.0);
        
        // Light ends
        fluid.addComponent("nitrogen", 0.01);
        fluid.addComponent("CO2", 0.02);
        fluid.addComponent("methane", 0.35);
        fluid.addComponent("ethane", 0.08);
        fluid.addComponent("propane", 0.05);
        
        // Intermediates
        fluid.addComponent("n-butane", 0.03);
        fluid.addComponent("n-pentane", 0.03);
        fluid.addComponent("n-hexane", 0.05);
        fluid.addComponent("n-heptane", 0.15);
        fluid.addComponent("n-decane", 0.10);
        
        // Heavy ends
        fluid.addComponent("n-C15", 0.08);
        fluid.addComponent("asphaltene", 0.05);
        
        fluid.setMixingRule("classic");
        fluid.init(0);
        fluid.init(1);
        
        // Reservoir conditions
        double pRes = 350.0;    // bar
        double pBub = 150.0;    // bar
        double density = 720.0; // kg/m³
        
        // Run full comparison
        AsphalteneMethodComparison comparison = 
            new AsphalteneMethodComparison(fluid);
        comparison.setReservoirPressure(pRes);
        comparison.setBubblePointPressure(pBub);
        comparison.setInSituDensity(density);
        
        // Generate reports
        System.out.println(comparison.runComparison());
        
        System.out.println("\n" + StringUtils.repeat("=", 50));
        System.out.println("QUICK REFERENCE");
        System.out.println(StringUtils.repeat("=", 50));
        System.out.println(comparison.getQuickSummary());
    }
}
```

## See Also

- [Asphaltene Modeling Overview](asphaltene_modeling)
- [CPA-Based Calculations](asphaltene_cpa_calculations)
- [De Boer Screening](asphaltene_deboer_screening)
