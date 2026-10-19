---
title: CPA-Based Asphaltene Calculations
description: The **Cubic Plus Association (CPA)** equation of state extends classical cubic equations (SRK or PR) with an association term to handle self-associating and cross-associating compounds. This makes CPA...
---

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

The onset search requires a named, characterized asphaltene component with positive
inventory. A heavy `C20` TBP fraction alone is insufficient. Missing or zero asphaltene
inventory and invalid ranges/steps raise `IllegalArgumentException`; no generic fraction
is invented. The pressure search requires an initially undersaturated fluid. An already
precipitated pressure start, failed TP flash, unconverged bracket or exhausted search
raises `IllegalStateException`. These failures must not be interpreted as no precipitation.

A completed scan without a sampled transition returns `NaN` with `isOnsetFound() == false`.
Every run clears its previous result. Both searches include the range endpoint and
allow at most 100 coarse steps; use a step large enough to cover the requested range.
A coarse scan can miss a narrow precipitation interval, so a no-onset result applies
only to the sampled states. The existing refinement widths are 0.1 bara and 0.5 K.

These examples use a **synthetic SRK solid-equilibrium fixture** to demonstrate the
numerical API. They are exercised by `AsphalteneOnsetFlashTest`; their finite onsets do
not establish empirical crude-oil accuracy or qualify CPA association parameters.
Project fluids require characterized properties and independent onset data.

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

SystemSrkEos fluid = new SystemSrkEos(450.0, 500.0);
fluid.addComponent("methane", 0.30);
fluid.addComponent("n-heptane", 0.65);
fluid.addComponent("asphaltene", 0.05);
fluid.setMixingRule("classic");
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
double onsetP = ops.asphalteneOnsetPressure(500.0, 200.0);

fluid.setPressure(500.0);
fluid.setTemperature(500.0);
double onsetT = ops.asphalteneOnsetTemperature(500.0, 350.0, 550.0);
```

Read the returned value or the operation getter. On success, the supplied fluid is
flashed at that same reported onset coordinate. The pressure operation's precipitated
fraction describes that final state, rather than a state arbitrarily 1 bar below it.
A scan with no transition leaves the fluid at its sampled range limit. After a failed
calculation the supplied fluid may remain at a trial state; do not reuse it as an
accepted equilibrium result without a successful flash.

### AsphalteneOnsetPressureFlash

Configure the start and lower bound in the constructor, in bara:

```java
import neqsim.thermodynamicoperations.flashops.saturationops.AsphalteneOnsetPressureFlash;

fluid.setTemperature(450.0);
AsphalteneOnsetPressureFlash pressure = new AsphalteneOnsetPressureFlash(fluid, 500.0, 200.0);
pressure.setPressureStep(25.0);
pressure.run();
double onsetPressure = pressure.getOnsetPressure();
boolean pressureOnsetFound = pressure.isOnsetFound();
```

### AsphalteneOnsetTemperatureFlash

The temperature search detects precipitation or dissolution when the sampled states
change classification. Bounds and start are in kelvin; `setSearchDecreasing(false)`
selects increasing temperature.

```java
import neqsim.thermodynamicoperations.flashops.saturationops.AsphalteneOnsetTemperatureFlash;

fluid.setPressure(500.0);
AsphalteneOnsetTemperatureFlash temperature =
    new AsphalteneOnsetTemperatureFlash(fluid, 500.0, 350.0, 550.0);
temperature.setTemperatureStep(25.0);
temperature.run();
double onsetTemperature = temperature.getOnsetTemperature();
boolean temperatureOnsetFound = temperature.isOnsetFound();
```

### AsphalteneStabilityAnalyzer

The direct onset methods propagate the same explicit characterization/search errors.
`comprehensiveAssessment` and `AsphalteneMethodComparison` retain available empirical/SARA
screening and report a failed onset
calculation as `Unavailable`, with its cause, rather than claiming no onset was found.

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

NeqSim uses `PhaseType.ASPHALTENE` to distinguish asphaltene precipitation from other solid phases (wax, hydrate). This enables accurate phase identification in multi-phase flash calculations.

```java
import neqsim.thermo.phase.PhaseType;

// After flash calculation - check for asphaltene precipitation
// Method 1: Using PhaseType enum (recommended)
if (fluid.hasPhaseType(PhaseType.ASPHALTENE)) {
    PhaseInterface asphaltene = fluid.getPhaseOfType("asphaltene");
    double precipitatedFraction = asphaltene.getBeta();
    System.out.println("Asphaltene precipitated: " + (precipitatedFraction * 100) + "%");
}

// Method 2: Using string-based lookup
if (fluid.hasPhaseType("asphaltene")) {
    PhaseInterface asphaltene = fluid.getPhaseOfType("asphaltene");
    // Access asphaltene phase properties
    double density = asphaltene.getDensity("kg/m3");       // ~1150 kg/m³
    double viscosity = asphaltene.getViscosity("Pa*s");    // ~10,000 Pa·s
    double thermalCond = asphaltene.getThermalConductivity("W/mK"); // ~0.20 W/mK
}

// Method 3: Legacy approach (also checks for generic solid)
private boolean hasAsphaltenePhase(SystemInterface fluid) {
    // Check for specific asphaltene phase type
    if (fluid.hasPhaseType("asphaltene")) {
        return true;
    }
    // Fallback: check solid phase for asphaltene component
    if (fluid.hasPhaseType("solid")) {
        PhaseInterface solid = fluid.getPhaseOfType("solid");
        for (int i = 0; i < solid.getNumberOfComponents(); i++) {
            if (solid.getComponent(i).getComponentName().toLowerCase().contains("asphaltene")) {
                return true;
            }
        }
    }
    return false;
}
```

### Asphaltene Phase Properties

When asphaltene precipitates, it forms a distinct phase with `PhaseType.ASPHALTENE`. The physical properties are calculated using literature-based correlations:

| Property | Value | Unit | Notes |
|----------|-------|------|-------|
| Density | ~1150 | kg/m³ | Literature-based, temperature-independent |
| Heat Capacity (Cp) | ~0.9 | kJ/kgK | EOS-based calculation |
| Thermal Conductivity | 0.20 | W/mK | Typical for organic solids |
| Viscosity | ~10,000 | Pa·s | Arrhenius correlation at 350K |
| Speed of Sound | ~1745 | m/s | EOS-based calculation |

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

The 1A and 2A self-association matrices allow equivalent sites on different
molecules to bond. A donor/acceptor charge test must not suppress these
self-interactions. Cross-solvation between different components retains the
existing donor/acceptor selection rules. The `CpaEquivalentSiteAssociationTest`
checks a nonzero bonded fraction for the configured asphaltene and H2S rows and
preserves the 2B/4C selection rules. Association still requires a positive site
count and fitted energy/volume parameters; a scheme label alone does not activate
association. In particular, inactive acid database rows are not fitted by simply
changing their site counts.

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

- [Asphaltene Modeling Overview](asphaltene_modeling)
- [De Boer Screening](asphaltene_deboer_screening)
- [Method Comparison](asphaltene_method_comparison)
- [Electrolyte CPA Model](../../thermo/ElectrolyteCPAModel)
