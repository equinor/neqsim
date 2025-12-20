# CPA-Based Asphaltene Calculations

## Overview

The **Cubic Plus Association (CPA)** equation of state extends classical cubic equations (SRK or PR) with an association term to handle self-associating and cross-associating compounds. This makes CPA particularly suitable for asphaltene modeling, where polar interactions and molecular aggregation are important.

## Theory

### CPA Equation of State

The CPA pressure equation combines cubic and association contributions:

$$
P = P_{\text{cubic}} + P_{\text{assoc}}
$$

Where:
- $P_{\text{cubic}}$ = SRK or Peng-Robinson cubic term
- $P_{\text{assoc}}$ = Association contribution from Wertheim's theory

### Association in Asphaltene Modeling

Asphaltenes self-associate through:
- **Hydrogen bonding** (O-H, N-H groups)
- **π-π stacking** of aromatic cores
- **Acid-base interactions**

CPA captures these through association parameters:
- **Association energy** ($\epsilon$): Strength of association
- **Association volume** ($\beta$): Probability of association
- **Association scheme**: Number and type of association sites

### Solid Phase Modeling

Asphaltene precipitation is modeled as a solid phase formation. At the **onset conditions**, the fugacity of asphaltene in the liquid phase equals that in the solid phase:

$$
f_{\text{asph}}^{L}(T, P, x) = f_{\text{asph}}^{S}(T, P)
$$

## Implementation Classes

### ThermodynamicOperations Methods

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create CPA fluid system
SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(373.15, 250.0);
fluid.addComponent("methane", 0.40);
fluid.addComponent("propane", 0.10);
fluid.addComponent("n-heptane", 0.45);
fluid.addComponent("asphaltene", 0.05);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

// Calculate asphaltene onset pressure
// Searches from current pressure down to find precipitation
ops.asphalteneOnsetPressure();
double onsetP = fluid.getPressure();

// Calculate asphaltene onset temperature at fixed pressure
fluid.setPressure(200.0);
ops.asphalteneOnsetTemperature();
double onsetT = fluid.getTemperature();
```

### AsphalteneOnsetPressureFlash

Direct flash operation for onset pressure calculation:

```java
import neqsim.thermodynamicoperations.flashops.saturationops.AsphalteneOnsetPressureFlash;

// Create flash operation
AsphalteneOnsetPressureFlash flash = new AsphalteneOnsetPressureFlash(fluid);

// Configure search range
flash.setMaxPressure(400.0);  // Start pressure [bar]
flash.setMinPressure(1.0);    // End pressure [bar]
flash.setPressureStep(10.0);  // Coarse search step [bar]
flash.setTolerance(0.1);      // Bisection tolerance [bar]

// Run calculation
flash.run();

// Get results
if (flash.isOnsetFound()) {
    double onsetPressure = flash.getOnsetPressure();
    System.out.println("Onset Pressure: " + onsetPressure + " bar");
} else {
    System.out.println("No onset found in search range");
}
```

### AsphalteneOnsetTemperatureFlash

Temperature-based onset calculation:

```java
import neqsim.thermodynamicoperations.flashops.saturationops.AsphalteneOnsetTemperatureFlash;

// Create flash operation at fixed pressure
AsphalteneOnsetTemperatureFlash flash = new AsphalteneOnsetTemperatureFlash(fluid);

// Configure search range
flash.setMinTemperature(273.15);  // 0°C
flash.setMaxTemperature(473.15);  // 200°C
flash.setTemperatureStep(5.0);    // Search step [K]
flash.setTolerance(0.1);          // Tolerance [K]

// Run calculation
flash.run();

if (flash.isOnsetFound()) {
    double onsetTemp = flash.getOnsetTemperature();
    System.out.println("Onset Temperature: " + (onsetTemp - 273.15) + " °C");
}
```

### AsphalteneStabilityAnalyzer

High-level API combining multiple analysis methods:

```java
import neqsim.pvtsimulation.flowassurance.AsphalteneStabilityAnalyzer;

// Create analyzer with fluid
AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(fluid);

// Configure conditions
analyzer.setReservoirPressure(350.0);      // bar
analyzer.setReservoirTemperature(373.15);  // K
analyzer.setBubblePointPressure(150.0);    // bar

// Quick screening (De Boer)
String screening = analyzer.deBoerScreening();
System.out.println("De Boer: " + screening);

// Thermodynamic onset pressure
double onsetP = analyzer.calculateOnsetPressure();
System.out.println("CPA Onset Pressure: " + onsetP + " bar");

// Comprehensive assessment
String fullReport = analyzer.comprehensiveAssessment();
System.out.println(fullReport);
```

## Fluid Setup for CPA

### Recommended System Type

```java
// Use SystemSrkCPAstatoil for asphaltene calculations
SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(T_kelvin, P_bar);
```

### Adding Components

```java
// Light ends
fluid.addComponent("methane", 0.40);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.05);

// Intermediates
fluid.addComponent("n-heptane", 0.30);
fluid.addComponent("n-decane", 0.10);

// Asphaltene pseudo-component
fluid.addComponent("asphaltene", 0.05);

// Set mixing rule (required for CPA)
fluid.setMixingRule("classic");
```

### Custom Asphaltene Properties

For tuned asphaltene parameters:

```java
// After adding components, modify asphaltene properties
int aspIndex = fluid.getComponentIndex("asphaltene");

// Adjust molecular weight if needed
fluid.getComponent(aspIndex).setMolarMass(750.0 / 1000.0);  // kg/mol

// Adjust critical properties (affects solubility)
fluid.getComponent(aspIndex).setTC(900.0);  // Critical temperature [K]
fluid.getComponent(aspIndex).setPC(15.0);   // Critical pressure [bar]
```

## Generating Precipitation Envelope

Map the full precipitation boundary in P-T space:

```java
import neqsim.pvtsimulation.flowassurance.AsphalteneStabilityAnalyzer;
import java.util.Map;

AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(fluid);

// Generate envelope from Tmin to Tmax
double[][] envelope = analyzer.generatePrecipitationEnvelope(
    280.0,   // Min temperature [K]
    400.0,   // Max temperature [K]
    10.0     // Temperature step [K]
);

// envelope[0] = temperatures
// envelope[1] = onset pressures

for (int i = 0; i < envelope[0].length; i++) {
    double T_C = envelope[0][i] - 273.15;
    double P_bar = envelope[1][i];
    System.out.printf("T = %.1f°C, P_onset = %.1f bar%n", T_C, P_bar);
}
```

## Algorithm Details

### Onset Pressure Algorithm

1. **Coarse Search**: Decrease pressure in steps, checking for solid phase
2. **Bisection Refinement**: When onset bracket found, bisect to tolerance
3. **Solid Detection**: Check if solid phase fraction exceeds threshold

```
P_start (e.g., 400 bar)
    |
    v
[Coarse search: decrease by step size]
    |
    v
Solid phase appears? --> No --> Continue decreasing
    |
    Yes
    v
[Bisection between last two points]
    |
    v
Converged to tolerance --> Report onset pressure
```

### Solid Phase Check

```java
// After flash calculation
if (fluid.getNumberOfPhases() > 2 || hasSolidPhase(fluid)) {
    // Asphaltene precipitation detected
}

private boolean hasSolidPhase(SystemInterface fluid) {
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
        String phaseType = fluid.getPhase(i).getPhaseTypeName();
        if (phaseType.contains("solid") || phaseType.contains("wax")) {
            return true;
        }
    }
    return false;
}
```

## Tuning to Experimental Data

### Automated Parameter Fitting with AsphalteneOnsetFitting

NeqSim provides the `AsphalteneOnsetFitting` class to automatically tune CPA asphaltene parameters to match experimental onset pressure measurements using the Levenberg-Marquardt optimization algorithm.

```java
import neqsim.pvtsimulation.util.parameterfitting.AsphalteneOnsetFitting;
import neqsim.thermo.system.SystemSrkCPAstatoil;

// Step 1: Create fluid system with asphaltene
SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(373.15, 200.0);
fluid.addComponent("methane", 0.30);
fluid.addTBPfraction("C7", 0.30, 0.100, 0.75);
fluid.addComponent("asphaltene", 0.05);
fluid.setMixingRule("classic");

// Step 2: Create fitter and add experimental onset data
AsphalteneOnsetFitting fitter = new AsphalteneOnsetFitting(fluid);
fitter.addOnsetPoint(353.15, 350.0);  // T=80°C, P_onset=350 bar
fitter.addOnsetPoint(373.15, 320.0);  // T=100°C, P_onset=320 bar
fitter.addOnsetPoint(393.15, 280.0);  // T=120°C, P_onset=280 bar

// Step 3: Set initial parameter guesses
fitter.setInitialGuess(3500.0, 0.005);  // epsilon/R=3500K, kappa=0.005

// Step 4: Configure pressure search range (optional)
fitter.setPressureRange(500.0, 10.0, 10.0);

// Step 5: Run fitting
boolean success = fitter.solve();

// Step 6: Get fitted parameters
if (success) {
    double epsilonR = fitter.getFittedAssociationEnergy();
    double kappa = fitter.getFittedAssociationVolume();
    System.out.println("Fitted ε/R: " + epsilonR + " K");
    System.out.println("Fitted κ: " + kappa);
    
    // Step 7: Predict onset at new conditions
    double onsetP = fitter.calculateOnsetPressure(400.0);  // T=400K
    System.out.println("Predicted onset at 400K: " + onsetP + " bar");
}
```

### Typical CPA Parameters for Asphaltenes

Based on literature (Li & Firoozabadi, Vargas et al.):

| Parameter | Typical Range | Units | Notes |
|-----------|---------------|-------|-------|
| Molar Mass | 500 - 1500 | g/mol | Polydisperse distribution |
| Association Energy (ε/R) | 2500 - 4500 | K | Controls aggregation strength |
| Association Volume (κ) | 0.001 - 0.05 | - | Probability of association |
| Association Scheme | 1A (single site) | - | Mimics π-π stacking |
| Critical Temperature (Tc) | 700 - 900 | K | Affects phase behavior |
| Critical Pressure (Pc) | 5 - 15 | bar | Affects phase behavior |
| Acentric Factor (ω) | 1.0 - 2.0 | - | Shape factor |

### Starting Guess Recommendations

| Oil Type | API Gravity | ε/R [K] | κ |
|----------|-------------|---------|---|
| Light oils | >35° | 3000 | 0.01 |
| Medium oils | 25-35° | 3500 | 0.005 |
| Heavy oils | <25° | 4000 | 0.003 |

### Fitting Parameter Types

The fitter supports different parameter types:

```java
import neqsim.pvtsimulation.util.parameterfitting.AsphalteneOnsetFunction.FittingParameterType;

// Fit only association energy
fitter.setParameterType(FittingParameterType.ASSOCIATION_ENERGY);
fitter.setInitialGuess(3500.0);

// Fit only association volume
fitter.setParameterType(FittingParameterType.ASSOCIATION_VOLUME);
fitter.setInitialGuess(0.005);

// Fit both association parameters (default)
fitter.setParameterType(FittingParameterType.ASSOCIATION_PARAMETERS);
fitter.setInitialGuess(3500.0, 0.005);

// Fit binary interaction parameter
fitter.setParameterType(FittingParameterType.BINARY_INTERACTION);
fitter.setInitialGuess(0.0);

// Fit molar mass
fitter.setParameterType(FittingParameterType.MOLAR_MASS);
fitter.setInitialGuess(750.0);
```

### Manual Onset Pressure Matching

For manual tuning without the fitter:

```java
// Target: Match experimental AOP of 180 bar

// Strategy 1: Adjust asphaltene critical properties
// Higher Tc/Pc = lower solubility = higher onset pressure

// Strategy 2: Adjust association parameters
// Stronger association = earlier precipitation

// Strategy 3: Adjust binary interaction parameters
fluid.setMixingRule("classic");  // k_ij values
```

### Sensitivity Analysis

Key parameters affecting onset pressure:
1. **Asphaltene molecular weight**: Higher MW → earlier precipitation
2. **Critical temperature**: Higher Tc → lower solubility
3. **Association energy**: Stronger → earlier aggregation
4. **Binary k_ij with light ends**: Higher → less miscibility

## Performance Considerations

### Calculation Speed

| Operation | Typical Time | Notes |
|-----------|--------------|-------|
| Single flash | 10-100 ms | Depends on composition complexity |
| Onset pressure | 1-5 seconds | Multiple flashes + bisection |
| Full envelope | 10-60 seconds | Multiple onset calculations |

### Optimization Tips

```java
// Reduce search range if approximate onset known
flash.setMaxPressure(250.0);  // Instead of 400
flash.setMinPressure(100.0);  // Instead of 1

// Use larger tolerance for initial screening
flash.setTolerance(1.0);  // Instead of 0.1

// Larger steps for coarse search
flash.setPressureStep(20.0);  // Instead of 10
```

## Common Issues and Solutions

### No Onset Found

- **Cause**: Fluid too stable or search range incorrect
- **Solution**: Extend search range or check fluid composition

### Convergence Issues

- **Cause**: Near-critical conditions or complex phase behavior
- **Solution**: Adjust initial conditions or use damped flash

### Unrealistic Onset Pressure

- **Cause**: Asphaltene parameters not tuned
- **Solution**: Match to experimental AOP data

## Example: Complete Workflow

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.pvtsimulation.flowassurance.AsphalteneStabilityAnalyzer;

// 1. Create and characterize fluid
SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(373.15, 350.0);
fluid.addComponent("methane", 0.35);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-heptane", 0.40);
fluid.addComponent("n-decane", 0.07);
fluid.addComponent("asphaltene", 0.05);
fluid.setMixingRule("classic");
fluid.init(0);
fluid.init(1);

// 2. Create analyzer
AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(fluid);
analyzer.setReservoirPressure(350.0);
analyzer.setReservoirTemperature(373.15);
analyzer.setBubblePointPressure(150.0);

// 3. Quick screening
System.out.println("=== De Boer Screening ===");
System.out.println(analyzer.deBoerScreening());

// 4. Calculate onset pressure
System.out.println("\n=== CPA Onset Pressure ===");
double onsetP = analyzer.calculateOnsetPressure();
System.out.println("Onset Pressure: " + onsetP + " bar");

// 5. Generate envelope
System.out.println("\n=== Precipitation Envelope ===");
double[][] envelope = analyzer.generatePrecipitationEnvelope(300.0, 400.0, 20.0);
for (int i = 0; i < envelope[0].length; i++) {
    System.out.printf("T = %.1f K, P_onset = %.1f bar%n", 
                      envelope[0][i], envelope[1][i]);
}

// 6. Full report
System.out.println("\n=== Comprehensive Assessment ===");
System.out.println(analyzer.comprehensiveAssessment());
```

## See Also

- [Asphaltene Modeling Overview](asphaltene_modeling.md)
- [De Boer Screening](asphaltene_deboer_screening.md)
- [Method Comparison](asphaltene_method_comparison.md)
- [CPA Equation of State](../../thermo/cpa_eos.md)
