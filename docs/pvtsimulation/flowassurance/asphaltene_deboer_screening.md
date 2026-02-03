---
title: De Boer Asphaltene Screening
description: The **De Boer correlation** is an empirical screening method developed from field observations of asphaltene problems in producing oil fields. It provides a quick, conservative assessment of asphalten...
---

# De Boer Asphaltene Screening

## Overview

The **De Boer correlation** is an empirical screening method developed from field observations of asphaltene problems in producing oil fields. It provides a quick, conservative assessment of asphaltene precipitation risk without requiring detailed thermodynamic modeling.

## Theory

### De Boer Plot

De Boer et al. (1995) analyzed field data and found that asphaltene problems correlate with two parameters:

1. **Undersaturation Pressure** ($\Delta P$): The difference between reservoir pressure and bubble point
2. **In-situ Oil Density** ($\rho$): Density of the live oil at reservoir conditions

$$
\Delta P = P_{\text{reservoir}} - P_{\text{bubble}}
$$

The plot divides the $\Delta P$ vs $\rho$ space into risk zones:

```
Undersaturation ΔP [bar]
    ^
400 |     SEVERE      |
    |   PROBLEM       |
300 |                 | MODERATE
    |  ─────────────  | PROBLEM
200 |                 |
    |    SLIGHT       |
100 |    PROBLEM      |
    |  ───────────────┼───────────────
  0 |    NO PROBLEM   |
    +─────────────────┼───────────────> ρ [kg/m³]
                    700            800
```

### Risk Zone Boundaries

The correlation uses empirical boundaries:

| Zone | Description | Boundary |
|------|-------------|----------|
| **No Problem** | Low risk | $\Delta P < f_1(\rho)$ |
| **Slight Problem** | Minor risk | $f_1(\rho) < \Delta P < f_2(\rho)$ |
| **Moderate Problem** | Moderate risk | $f_2(\rho) < \Delta P < f_3(\rho)$ |
| **Severe Problem** | High risk | $\Delta P > f_3(\rho)$ |

Where $f_i(\rho)$ are linear functions of density.

### Physical Interpretation

- **High undersaturation + Low density** = Severe risk
  - Light, gas-rich oils become significantly denser as pressure drops
  - Large solubility parameter change destabilizes asphaltenes
  
- **Low undersaturation + High density** = Low risk
  - Heavy oils change less during production
  - Asphaltenes more naturally stable

## Implementation

### Basic Usage

```java
import neqsim.pvtsimulation.flowassurance.DeBoerAsphalteneScreening;

// Create screening with reservoir data
DeBoerAsphalteneScreening screening = new DeBoerAsphalteneScreening(
    350.0,  // Reservoir pressure [bar]
    150.0,  // Bubble point pressure [bar]
    720.0   // In-situ oil density [kg/m³]
);

// Get risk assessment
String riskLevel = screening.evaluateRisk();
System.out.println("Risk Level: " + riskLevel);
```

### Risk Levels

The method returns one of four risk categories:

```java
public enum RiskLevel {
    NO_PROBLEM,        // No action needed
    SLIGHT_PROBLEM,    // Monitor during production
    MODERATE_PROBLEM,  // Consider mitigation
    SEVERE_PROBLEM     // Mitigation required
}

// Usage
String risk = screening.evaluateRisk();
switch (risk) {
    case "NO_PROBLEM":
        System.out.println("No asphaltene issues expected");
        break;
    case "SLIGHT_PROBLEM":
        System.out.println("Minor issues possible - monitor");
        break;
    case "MODERATE_PROBLEM":
        System.out.println("Significant risk - plan mitigation");
        break;
    case "SEVERE_PROBLEM":
        System.out.println("High risk - mitigation required");
        break;
}
```

### Quantitative Risk Index

For more nuanced assessment:

```java
double riskIndex = screening.calculateRiskIndex();
```

The risk index interpretation:

| Index Value | Interpretation |
|-------------|----------------|
| < 0 | Stable, no precipitation expected |
| 0 - 0.3 | Low risk |
| 0.3 - 0.7 | Moderate risk |
| > 0.7 | High risk, likely problems |

### Detailed Report

```java
String report = screening.performScreening();
System.out.println(report);
```

Output:
```
De Boer Asphaltene Screening Results
====================================
Reservoir Pressure: 350.0 bar
Bubble Point Pressure: 150.0 bar
Undersaturation: 200.0 bar
In-situ Density: 720.0 kg/m³

Risk Assessment: MODERATE_PROBLEM
Risk Index: 0.55

Recommendation: Moderate asphaltene risk. 
Consider preventive measures and monitoring plan.
```

## Advanced Usage

### Sensitivity Analysis

Evaluate risk over a range of conditions:

```java
import neqsim.pvtsimulation.flowassurance.DeBoerAsphalteneScreening;

// Base case
double bubblePoint = 150.0;
double density = 720.0;

// Pressure depletion scenario
System.out.println("Pressure Depletion Analysis:");
for (double pRes = 400.0; pRes >= 160.0; pRes -= 20.0) {
    DeBoerAsphalteneScreening screening = 
        new DeBoerAsphalteneScreening(pRes, bubblePoint, density);
    
    double deltaP = pRes - bubblePoint;
    String risk = screening.evaluateRisk();
    
    System.out.printf("P_res = %.0f bar, ΔP = %.0f bar: %s%n", 
                      pRes, deltaP, risk);
}
```

### Generate Plot Data

Create data for visualization:

```java
double[][] plotData = screening.generatePlotData(
    650.0,   // Min density [kg/m³]
    850.0,   // Max density [kg/m³]
    25.0     // Density step [kg/m³]
);

// plotData[0] = density values
// plotData[1] = undersaturation values for "slight problem" boundary
// plotData[2] = undersaturation values for "moderate problem" boundary
// plotData[3] = undersaturation values for "severe problem" boundary

// Export for plotting
System.out.println("Density,Slight,Moderate,Severe");
for (int i = 0; i < plotData[0].length; i++) {
    System.out.printf("%.1f,%.1f,%.1f,%.1f%n",
        plotData[0][i], plotData[1][i], plotData[2][i], plotData[3][i]);
}
```

### Batch Screening

Screen multiple samples:

```java
// Sample data: [reservoir P, bubble P, density]
double[][] samples = {
    {350.0, 150.0, 720.0},  // Sample A
    {280.0, 100.0, 780.0},  // Sample B
    {400.0, 200.0, 690.0},  // Sample C
    {300.0, 180.0, 750.0}   // Sample D
};

String[] sampleNames = {"A", "B", "C", "D"};

System.out.println("Sample | ΔP [bar] | ρ [kg/m³] | Risk Level");
System.out.println("-------+----------+-----------+----------------");

for (int i = 0; i < samples.length; i++) {
    DeBoerAsphalteneScreening screening = new DeBoerAsphalteneScreening(
        samples[i][0], samples[i][1], samples[i][2]
    );
    
    double deltaP = samples[i][0] - samples[i][1];
    String risk = screening.evaluateRisk();
    
    System.out.printf("   %s   |  %5.0f   |   %5.0f   | %s%n",
        sampleNames[i], deltaP, samples[i][2], risk);
}
```

## Input Requirements

### Required Data

| Parameter | Unit | How to Obtain |
|-----------|------|---------------|
| Reservoir Pressure | bar | Formation test (RFT/MDT) |
| Bubble Point Pressure | bar | PVT lab test or correlation |
| In-situ Density | kg/m³ | PVT lab or flash calculation |

### Estimating In-situ Density

If density at reservoir conditions is not available:

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create fluid from composition
SystemSrkEos fluid = new SystemSrkEos(373.15, 350.0);  // Reservoir T, P
fluid.addComponent("methane", 0.40);
fluid.addComponent("n-heptane", 0.55);
fluid.addComponent("n-decane", 0.05);
fluid.setMixingRule("classic");

// Flash to get density
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Get liquid density
double density = fluid.getPhase("oil").getDensity("kg/m3");
System.out.println("In-situ density: " + density + " kg/m³");
```

## Limitations

### What De Boer Cannot Do

1. **No onset pressure prediction**: Cannot tell *when* precipitation starts
2. **No quantity estimation**: Cannot predict *how much* precipitates
3. **No temperature effects**: Only considers isothermal depletion
4. **No composition sensitivity**: Cannot evaluate blending/injection effects
5. **No remediation modeling**: Cannot assess inhibitor effectiveness

### When to Use CPA Instead

Use thermodynamic modeling (CPA) when:
- Precise onset pressure is needed
- Evaluating gas injection (CO₂, hydrocarbon)
- Assessing blending compatibility
- Designing inhibitor treatments
- Temperature effects are significant

## Validation

### Field Data Comparison

De Boer was validated against:
- Gulf of Mexico fields
- North Sea developments
- Middle East reservoirs

The correlation correctly predicted:
- 85% of fields with problems
- 90% of fields without problems

### Conservative Bias

The method is intentionally **conservative**:
- May predict problems where none occur
- Rarely misses actual problems
- Suitable for screening (not design)

## Example: Field Development Screening

```java
import neqsim.pvtsimulation.flowassurance.DeBoerAsphalteneScreening;

// Field data for multiple reservoirs
String[] reservoirs = {"Alpha", "Beta", "Gamma", "Delta"};
double[] pRes = {380.0, 320.0, 290.0, 410.0};  // Reservoir pressure [bar]
double[] pBub = {180.0, 150.0, 120.0, 220.0};  // Bubble point [bar]
double[] rho = {710.0, 750.0, 800.0, 680.0};   // In-situ density [kg/m³]

System.out.println("Field Development Asphaltene Screening");
System.out.println("======================================\n");

int highRiskCount = 0;

for (int i = 0; i < reservoirs.length; i++) {
    DeBoerAsphalteneScreening screening = 
        new DeBoerAsphalteneScreening(pRes[i], pBub[i], rho[i]);
    
    String risk = screening.evaluateRisk();
    double riskIndex = screening.calculateRiskIndex();
    
    System.out.printf("Reservoir %s:%n", reservoirs[i]);
    System.out.printf("  P_res = %.0f bar, P_bub = %.0f bar, ρ = %.0f kg/m³%n",
                      pRes[i], pBub[i], rho[i]);
    System.out.printf("  Risk: %s (index: %.2f)%n%n", risk, riskIndex);
    
    if (risk.equals("SEVERE_PROBLEM") || risk.equals("MODERATE_PROBLEM")) {
        highRiskCount++;
    }
}

System.out.printf("Summary: %d of %d reservoirs require further analysis%n",
                  highRiskCount, reservoirs.length);
```

## References

1. De Boer, R.B., Leerlooyer, K., Eigner, M.R.P., and van Bergen, A.R.D. (1995). "Screening of Crude Oils for Asphalt Precipitation: Theory, Practice, and the Selection of Inhibitors." SPE Production & Facilities, 10(1), 55-61.

2. Hammami, A., and Ratulowski, J. (2007). "Precipitation and Deposition of Asphaltenes in Production Systems: A Flow Assurance Overview." In Asphaltenes, Heavy Oils, and Petroleomics, Springer.

3. Akbarzadeh, K., et al. (2007). "Asphaltenes—Problematic but Rich in Potential." Oilfield Review, 19(2), 22-43.

## See Also

- [Asphaltene Modeling Overview](asphaltene_modeling.md)
- [CPA-Based Calculations](asphaltene_cpa_calculations.md)
- [Method Comparison](asphaltene_method_comparison.md)
