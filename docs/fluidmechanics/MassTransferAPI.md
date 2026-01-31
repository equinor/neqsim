# Mass Transfer API Documentation

This document provides comprehensive API documentation for NeqSim's enhanced mass transfer model for two-phase pipe flow, including detailed method descriptions, parameters, examples, and literature references.

## Table of Contents

1. [Overview](#overview)
2. [MassTransferConfig](#masstransferconfig)
3. [InterfacialAreaCalculator](#interfacialareacalculator)
4. [MassTransferCoefficientCalculator](#masstransfercoefficientcalculator)
5. [TwoPhaseFixedStaggeredGridSolver Extensions](#twophasefixedstaggeredgridsolver-extensions)
6. [Complete Examples](#complete-examples)
7. [Literature References](#literature-references)

---

## Overview

The mass transfer model provides tools for simulating interphase mass transfer in two-phase pipe flow, including:

- **Evaporation**: Liquid components transferring to gas phase
- **Dissolution**: Gas components dissolving into liquid phase
- **Complete phase transitions**: Handling total evaporation or dissolution
- **Three-phase systems**: Gas-oil-water configurations

### Architecture

```
TwoPhaseFixedStaggeredGridSolver
    ├── MassTransferConfig (configuration parameters)
    └── FlowNode.getFluidBoundary()
            └── KrishnaStandartFilmModel
                    ├── InterfacialAreaCalculator (interfacial area)
                    └── MassTransferCoefficientCalculator (kL, kG)
```

---

## MassTransferConfig

Configuration class for mass transfer calculations with user-configurable parameters.

**Package:** `neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver`

### Factory Methods

| Method | Description | Use Case |
|--------|-------------|----------|
| `MassTransferConfig()` | Default configuration | General two-phase flow |
| `MassTransferConfig.forEvaporation()` | Optimized for evaporation | Complete liquid evaporation |
| `MassTransferConfig.forDissolution()` | Optimized for dissolution | Complete gas dissolution |
| `MassTransferConfig.forThreePhase()` | Three-phase systems | Gas-oil-water |
| `MassTransferConfig.forHighAccuracy()` | Research/validation | High-fidelity simulations |

### Configuration Parameters

#### Transfer Limits

| Parameter | Default | Description | Valid Range |
|-----------|---------|-------------|-------------|
| `maxTransferFractionBidirectional` | 0.9 | Max fraction transferable in bidirectional mode | 0.1 - 0.99 |
| `maxTransferFractionDirectional` | 0.5 | Max fraction transferable in directional modes | 0.1 - 0.99 |
| `useAdaptiveLimiting` | true | Enable Courant-like adaptive limiting | true/false |

#### Convergence

| Parameter | Default | Description | Valid Range |
|-----------|---------|-------------|-------------|
| `convergenceTolerance` | 1e-4 | Tolerance for mass transfer calculations | > 1e-10 |
| `maxIterations` | 100 | Maximum solver iterations | ≥ 10 |
| `minIterations` | 5 | Minimum iterations before convergence check | ≥ 1 |

#### Stability

| Parameter | Default | Description | Valid Range |
|-----------|---------|-------------|-------------|
| `minMolesFraction` | 1e-15 | Minimum mole fraction threshold | ≥ 1e-20 |
| `absoluteMinMoles` | 1e-20 | Absolute minimum moles | ≥ 1e-30 |
| `maxTemperatureChangePerNode` | 50.0 K | Max temperature change per node | ≥ 1.0 K |
| `maxPhaseDepletionPerNode` | 0.95 | Max phase fraction that can deplete per node | 0.5 - 0.99 |
| `allowPhaseDisappearance` | true | Allow complete phase disappearance | true/false |

#### Model Options

| Parameter | Default | Description | Reference |
|-----------|---------|-------------|-----------|
| `includeMarangoniEffect` | false | Surface tension gradient correction | Springer & Pigford (1970) |
| `includeEntrainment` | true | Droplet entrainment in annular flow | Ishii & Mishima (1989) |
| `includeWaveEnhancement` | true | Wave enhancement for stratified flow | Tzotzi & Andritsos (2013) |
| `includeTurbulenceEffects` | true | Turbulence enhancement of kL | Lamont & Scott (1970) |
| `coupledHeatMassTransfer` | true | Iterative heat-mass coupling | - |
| `coupledIterations` | 10 | Outer iterations for coupling | ≥ 1 |

#### Three-Phase Options

| Parameter | Default | Description |
|-----------|---------|-------------|
| `enableThreePhase` | false | Enable three-phase mass transfer |
| `aqueousPhaseIndex` | 2 | Index of aqueous phase |
| `organicPhaseIndex` | 1 | Index of oil/organic phase |

#### Diagnostics

| Parameter | Default | Description |
|-----------|---------|-------------|
| `enableDiagnostics` | false | Enable convergence logging |
| `detectStalls` | true | Detect convergence stalls |
| `stallDetectionWindow` | 5 | Iterations for stall detection |

### Example: Configure for Evaporation

```java
import neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.MassTransferConfig;

// Use factory method
MassTransferConfig config = MassTransferConfig.forEvaporation();

// Or customize manually
MassTransferConfig customConfig = new MassTransferConfig();
customConfig.setMaxTransferFractionBidirectional(0.85);
customConfig.setMaxPhaseDepletionPerNode(0.98);
customConfig.setAllowPhaseDisappearance(true);
customConfig.setIncludeWaveEnhancement(true);
customConfig.setCoupledHeatMassTransfer(true);
customConfig.setEnableDiagnostics(true);
```

### Example: Configure for Three-Phase

```java
MassTransferConfig config = MassTransferConfig.forThreePhase();
// Verify phase indices match your system
config.setAqueousPhaseIndex(2);  // Water phase
config.setOrganicPhaseIndex(1);  // Oil phase
config.setConvergenceTolerance(1e-5);  // Tighter tolerance
```

---

## InterfacialAreaCalculator

Utility class for calculating interfacial area per unit volume (m²/m³ = 1/m) in two-phase flow.

**Package:** `neqsim.fluidmechanics.flownode`

### Flow Pattern Models

| Flow Pattern | Model | Formula/Approach |
|--------------|-------|------------------|
| STRATIFIED | Flat interface | a = Si/A, chord length at interface |
| STRATIFIED_WAVY | Flat + wave enhancement | Kelvin-Helmholtz instability |
| ANNULAR | Film interface | a = 4/(D·√(1-αL)) |
| ANNULAR + entrainment | Film + droplets | Ishii & Mishima (1989) |
| SLUG | Taylor bubble + slug | Weighted average |
| BUBBLE | Sauter mean diameter | a = 6·αG/d32 |
| DROPLET | Weber number criterion | a = 6·αL/d32 |
| CHURN | Annular + bubble blend | Intermediate |

### Core Methods

#### `calculateInterfacialArea`

Calculates interfacial area for standard flow patterns.

```java
public static double calculateInterfacialArea(
    FlowPattern flowPattern,  // Flow regime
    double diameter,          // Pipe diameter (m)
    double liquidHoldup,      // Liquid holdup (0-1)
    double rhoG,              // Gas density (kg/m³)
    double rhoL,              // Liquid density (kg/m³)
    double usg,               // Superficial gas velocity (m/s)
    double usl,               // Superficial liquid velocity (m/s)
    double sigma              // Surface tension (N/m)
)
// Returns: interfacial area per unit volume (1/m)
```

**Example:**
```java
import neqsim.fluidmechanics.flownode.InterfacialAreaCalculator;
import neqsim.fluidmechanics.flownode.FlowPattern;

double diameter = 0.1;      // 100 mm pipe
double liquidHoldup = 0.3;  // 30% liquid
double rhoG = 50.0;         // kg/m³
double rhoL = 800.0;        // kg/m³
double usg = 5.0;           // m/s
double usl = 0.5;           // m/s
double sigma = 0.025;       // N/m

double area = InterfacialAreaCalculator.calculateInterfacialArea(
    FlowPattern.STRATIFIED_WAVY, diameter, liquidHoldup, 
    rhoG, rhoL, usg, usl, sigma);

System.out.println("Interfacial area: " + area + " m²/m³");
// Expected: ~10-50 m²/m³ for stratified wavy
```

### Enhanced Methods

#### `calculateStratifiedWavyArea`

Calculates stratified wavy area with Kelvin-Helmholtz wave enhancement.

```java
public static double calculateStratifiedWavyArea(
    double diameter,          // Pipe diameter (m)
    double liquidHoldup,      // Liquid holdup (0-1)
    double usg,               // Superficial gas velocity (m/s)
    double usl,               // Superficial liquid velocity (m/s)
    double rhoG,              // Gas density (kg/m³)
    double rhoL,              // Liquid density (kg/m³)
    double sigma              // Surface tension (N/m)
)
// Returns: interfacial area with wave enhancement (1/m)
```

**Physics:**
- Checks Kelvin-Helmholtz instability criterion
- Critical velocity: Vcrit = √(σ·Δρ·g / (ρG·ρL))
- Wave amplitude estimation when Vrel > Vcrit
- Enhancement factor capped at 3.5× (literature recommendation)

**Reference:** Tzotzi, C., Andritsos, N. (2013). Interfacial shear stress in wavy stratified gas-liquid flow. *Chemical Engineering Science*, 86, 49-57.

**Example:**
```java
double areaWavy = InterfacialAreaCalculator.calculateStratifiedWavyArea(
    0.1,     // diameter
    0.25,    // liquidHoldup  
    8.0,     // usg (high velocity for waves)
    0.3,     // usl
    30.0,    // rhoG
    750.0,   // rhoL
    0.020    // sigma
);
// Expect enhancement factor 1.5-3.5× compared to flat stratified
```

#### `calculateAnnularAreaWithEntrainment`

Calculates annular flow area including entrained droplets.

```java
public static double calculateAnnularAreaWithEntrainment(
    double diameter,          // Pipe diameter (m)
    double liquidHoldup,      // Liquid holdup (0-1)
    double rhoG,              // Gas density (kg/m³)
    double rhoL,              // Liquid density (kg/m³)
    double usg,               // Superficial gas velocity (m/s)
    double muL,               // Liquid viscosity (Pa·s)
    double sigma              // Surface tension (N/m)
)
// Returns: interfacial area with entrainment (1/m)
```

**Physics:**
- Gas Weber number: WeG = ρG·usg²·D/σ
- Entrainment fraction: E = tanh(7.25e-7·WeG^1.25·ReL^0.25)
- Droplet Sauter diameter: d32/D = 0.069·WeG^(-0.5)
- Total area = film area + droplet area

**Reference:** Ishii, M., Mishima, K. (1989). Droplet entrainment correlation in annular two-phase flow. *Int. J. Heat Mass Transfer*, 32(10), 1835-1846.

**Example:**
```java
double areaAnnular = InterfacialAreaCalculator.calculateAnnularAreaWithEntrainment(
    0.05,      // diameter (50 mm)
    0.05,      // liquidHoldup (thin film)
    80.0,      // rhoG (high pressure gas)
    800.0,     // rhoL
    15.0,      // usg (high velocity)
    0.001,     // muL
    0.015      // sigma
);
// Expect significant contribution from entrained droplets at high WeG
```

#### `calculateEnhancedInterfacialArea`

Comprehensive method with all enhancement options.

```java
public static double calculateEnhancedInterfacialArea(
    FlowPattern flowPattern,
    double diameter,
    double liquidHoldup,
    double rhoG,
    double rhoL,
    double usg,
    double usl,
    double muL,
    double sigma,
    boolean includeWaveEnhancement,
    boolean includeEntrainment
)
// Returns: interfacial area with selected enhancements (1/m)
```

### Validation Methods

#### `getExpectedInterfacialAreaRange`

Returns literature-based expected ranges for validation.

```java
public static double[] getExpectedInterfacialAreaRange(
    FlowPattern flowPattern,
    double diameter
)
// Returns: [min, typical, max] interfacial area (1/m)
```

**Expected Ranges by Flow Pattern:**

| Flow Pattern | Min | Typical | Max |
|--------------|-----|---------|-----|
| Stratified | 2/D | 5/D | 15/D |
| Annular | 8/D | 50/D | 300/D |
| Slug | 5/D | 30/D | 100/D |
| Bubble | 50 | 200 | 1000 |
| Droplet | 100 | 500 | 2000 |
| Churn | 20/D | 80/D | 200/D |

**Example: Validate Calculation**
```java
FlowPattern pattern = FlowPattern.STRATIFIED_WAVY;
double D = 0.1;

double calculatedArea = InterfacialAreaCalculator.calculateInterfacialArea(
    pattern, D, 0.3, 50, 800, 5, 0.5, 0.025);

double[] expected = InterfacialAreaCalculator.getExpectedInterfacialAreaRange(pattern, D);
System.out.printf("Calculated: %.1f m²/m³%n", calculatedArea);
System.out.printf("Expected range: %.1f - %.1f m²/m³%n", expected[0], expected[2]);

if (calculatedArea >= expected[0] && calculatedArea <= expected[2]) {
    System.out.println("✓ Within literature range");
} else {
    System.out.println("⚠ Outside expected range - verify inputs");
}
```

---

## MassTransferCoefficientCalculator

Utility class for calculating mass transfer coefficients (kL, kG) in two-phase pipe flow.

**Package:** `neqsim.fluidmechanics.flownode`

### Core Correlations

| Correlation | Application | Formula |
|-------------|-------------|---------|
| Dittus-Boelter | Turbulent pipe flow | Sh = 0.023·Re^0.8·Sc^0.33 |
| Ranz-Marshall | Spheres (bubbles/droplets) | Sh = 2 + 0.6·Re^0.5·Sc^0.33 |
| Solbraa | Stratified gas-liquid | Flow-pattern specific |
| Vivian-Peaceman | Falling film | Sh = 0.0096·Re^0.87·Sc^0.5 |

### Liquid-Side Methods

#### `calculateLiquidMassTransferCoefficient`

Standard liquid-side mass transfer coefficient.

```java
public static double calculateLiquidMassTransferCoefficient(
    FlowPattern flowPattern,  // Flow regime
    double diameter,          // Pipe diameter (m)
    double liquidHoldup,      // Liquid holdup (0-1)
    double usg,               // Superficial gas velocity (m/s)
    double usl,               // Superficial liquid velocity (m/s)
    double rhoL,              // Liquid density (kg/m³)
    double muL,               // Liquid viscosity (Pa·s)
    double diffL              // Liquid diffusivity (m²/s)
)
// Returns: kL (m/s), always ≥ 0
```

**Example:**
```java
import neqsim.fluidmechanics.flownode.MassTransferCoefficientCalculator;

double kL = MassTransferCoefficientCalculator.calculateLiquidMassTransferCoefficient(
    FlowPattern.STRATIFIED,
    0.1,      // diameter (m)
    0.3,      // liquidHoldup
    5.0,      // usg (m/s)
    0.5,      // usl (m/s)
    800.0,    // rhoL (kg/m³)
    0.001,    // muL (Pa·s)
    2e-9      // diffL (m²/s) - typical for gas in liquid
);

System.out.printf("kL = %.2e m/s%n", kL);
// Expected: 1e-5 to 2e-4 m/s for stratified flow
```

#### `calculateLiquidMassTransferCoefficientWithTurbulence`

Enhanced kL with turbulence correction.

```java
public static double calculateLiquidMassTransferCoefficientWithTurbulence(
    FlowPattern flowPattern,
    double diameter,
    double liquidHoldup,
    double usg,
    double usl,
    double rhoL,
    double muL,
    double diffL,
    double turbulentIntensity  // Turbulent intensity (0-1, typical 0.05-0.2)
)
// Returns: enhanced kL (m/s)
```

**Physics:**
Enhancement factor = 1 + 2.5·Tu·√Re  
Capped at 5× enhancement (literature limit)

**Reference:** Lamont, J.C., Scott, D.S. (1970). An eddy cell model of mass transfer into the surface of a turbulent liquid. *AIChE Journal*, 16(4), 513-519.

**Example:**
```java
double turbulentIntensity = 0.15;  // 15% turbulence

double kL_enhanced = MassTransferCoefficientCalculator
    .calculateLiquidMassTransferCoefficientWithTurbulence(
        FlowPattern.SLUG,  // High turbulence flow pattern
        0.1, 0.4, 3.0, 1.0, 800.0, 0.001, 2e-9,
        turbulentIntensity);

// Compare to base
double kL_base = MassTransferCoefficientCalculator
    .calculateLiquidMassTransferCoefficient(
        FlowPattern.SLUG, 0.1, 0.4, 3.0, 1.0, 800.0, 0.001, 2e-9);

System.out.printf("Base kL: %.2e m/s%n", kL_base);
System.out.printf("Enhanced kL: %.2e m/s%n", kL_enhanced);
System.out.printf("Enhancement factor: %.2f×%n", kL_enhanced / kL_base);
```

#### `applyMarangoniCorrection`

Corrects kL for surface-active components.

```java
public static double applyMarangoniCorrection(
    double kLBase,                // Base kL (m/s)
    double surfaceTensionGradient, // dσ/dc (N·m/mol)
    double diffL,                 // Liquid diffusivity (m²/s)
    double muL                    // Liquid viscosity (Pa·s)
)
// Returns: corrected kL (m/s)
```

**Physics:**
Marangoni number: Ma = |dσ/dc|·D / (μ·kL²)  
Correction: kL_corrected = kL / (1 + 0.35·√|Ma|)  
Correction limited to 10× reduction

**Reference:** Springer, T.G., Pigford, R.L. (1970). Influence of surface turbulence and surfactants on gas transport through liquid interfaces. *Ind. Eng. Chem. Fundam.*, 9(3), 458-465.

**Example: Surfactant Effect**
```java
double kL_base = 1e-4;  // m/s
double dSigma_dC = 0.05;  // N·m/mol (strong surfactant)
double diffL = 2e-9;  // m²/s
double muL = 0.001;  // Pa·s

double kL_corrected = MassTransferCoefficientCalculator.applyMarangoniCorrection(
    kL_base, dSigma_dC, diffL, muL);

System.out.printf("Without surfactant: kL = %.2e m/s%n", kL_base);
System.out.printf("With surfactant: kL = %.2e m/s%n", kL_corrected);
System.out.printf("Reduction: %.0f%%%n", (1 - kL_corrected/kL_base) * 100);
```

### Gas-Side Methods

#### `calculateGasMassTransferCoefficient`

Standard gas-side mass transfer coefficient.

```java
public static double calculateGasMassTransferCoefficient(
    FlowPattern flowPattern,  // Flow regime
    double diameter,          // Pipe diameter (m)
    double liquidHoldup,      // Liquid holdup (0-1)
    double usg,               // Superficial gas velocity (m/s)
    double rhoG,              // Gas density (kg/m³)
    double muG,               // Gas viscosity (Pa·s)
    double diffG              // Gas diffusivity (m²/s)
)
// Returns: kG (m/s), always ≥ 0
```

**Example:**
```java
double kG = MassTransferCoefficientCalculator.calculateGasMassTransferCoefficient(
    FlowPattern.STRATIFIED,
    0.1,       // diameter (m)
    0.3,       // liquidHoldup
    5.0,       // usg (m/s)
    50.0,      // rhoG (kg/m³)
    1.5e-5,    // muG (Pa·s)
    1e-5       // diffG (m²/s) - typical for gas-gas diffusion
);

System.out.printf("kG = %.2e m/s%n", kG);
// Expected: 1e-3 to 5e-2 m/s for stratified flow
```

### Combined Methods

#### `calculateEnhancedLiquidMassTransferCoefficient`

Comprehensive calculation with all enhancement options.

```java
public static double calculateEnhancedLiquidMassTransferCoefficient(
    FlowPattern flowPattern,
    double diameter,
    double liquidHoldup,
    double usg,
    double usl,
    double rhoL,
    double muL,
    double diffL,
    double turbulentIntensity,      // 0-1
    double surfaceTensionGradient,  // N·m/mol, 0 to disable
    boolean includeTurbulence,
    boolean includeMarangoni
)
// Returns: fully enhanced kL (m/s)
```

**Example:**
```java
double kL_full = MassTransferCoefficientCalculator.calculateEnhancedLiquidMassTransferCoefficient(
    FlowPattern.ANNULAR,
    0.05,      // diameter
    0.1,       // liquidHoldup
    12.0,      // usg
    0.3,       // usl
    850.0,     // rhoL
    0.0008,    // muL
    1.5e-9,    // diffL
    0.12,      // turbulentIntensity
    0.02,      // surfaceTensionGradient
    true,      // includeTurbulence
    true       // includeMarangoni
);
```

### Utility Methods

#### `estimateTurbulentIntensity`

Estimates turbulent intensity from flow pattern and Reynolds number.

```java
public static double estimateTurbulentIntensity(
    FlowPattern flowPattern,
    double re                 // Reynolds number
)
// Returns: estimated turbulent intensity (0-1)
```

**Typical Values:**

| Flow Pattern | Multiplier vs Base |
|--------------|-------------------|
| Stratified | 0.8× |
| Stratified Wavy | 1.2× |
| Annular | 1.5× |
| Slug | 2.0× |
| Churn | 2.5× |
| Bubble | 1.8× |

Where base Tu ≈ 0.16·Re^(-1/8) for developed turbulent flow.

### Validation Methods

#### `getExpectedMassTransferCoefficientRange`

Returns literature-based expected ranges.

```java
public static double[] getExpectedMassTransferCoefficientRange(
    FlowPattern flowPattern,
    int phase                 // 0 = gas, 1 = liquid
)
// Returns: [min, typical, max] (m/s)
```

#### `validateAgainstLiterature`

Checks if calculated value is within expected range.

```java
public static boolean validateAgainstLiterature(
    double calculated,        // Calculated kL or kG
    FlowPattern flowPattern,
    int phase                 // 0 = gas, 1 = liquid
)
// Returns: true if within expected range
```

---

## TwoPhaseFixedStaggeredGridSolver Extensions

New methods added to the solver for phase tracking and diagnostics.

### Phase Tracking Methods

#### `isGasPhaseCompletelyDissolved`

Checks if gas phase has completely dissolved.

```java
public boolean isGasPhaseCompletelyDissolved()
// Returns: true if gas phase has disappeared
```

#### `isLiquidPhaseCompletelyEvaporated`

Checks if liquid phase has completely evaporated.

```java
public boolean isLiquidPhaseCompletelyEvaporated()
// Returns: true if liquid phase has disappeared
```

### Summary Methods

#### `getMassTransferSummary`

Returns overall mass transfer statistics.

```java
public double[] getMassTransferSummary()
// Returns: [totalDissolution, totalEvaporation, netTransfer] in moles
```

#### `getMassBalanceError`

Calculates mass balance closure error.

```java
public double getMassBalanceError()
// Returns: relative mass balance error (should be < 0.01 for 1%)
```

### Validation Methods

#### `validateMassTransferAgainstLiterature`

Generates validation report comparing to literature.

```java
public String validateMassTransferAgainstLiterature()
// Returns: formatted validation report string
```

---

## Complete Examples

### Example 1: Water Evaporation into Dry Nitrogen

Simulating evaporation of water into flowing nitrogen gas.

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.processimulation.processequipment.stream.Stream;
import neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.*;

// Create fluid system
SystemInterface fluid = new SystemSrkEos(293.15, 1.01325);  // 20°C, 1 atm
fluid.addComponent("nitrogen", 0.95);
fluid.addComponent("water", 0.05);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

// Create inlet stream
Stream inlet = new Stream("inlet", fluid);
inlet.setFlowRate(100.0, "kg/hr");
inlet.run();

// Configure mass transfer for evaporation
MassTransferConfig config = MassTransferConfig.forEvaporation();
config.setEnableDiagnostics(true);
config.setMaxPhaseDepletionPerNode(0.99);  // Allow near-complete evaporation

// Create pipe with mass transfer
// ... (pipe setup code)

// After simulation
System.out.println("Gas phase dissolved: " + solver.isGasPhaseCompletelyDissolved());
System.out.println("Liquid phase evaporated: " + solver.isLiquidPhaseCompletelyEvaporated());

double[] summary = solver.getMassTransferSummary();
System.out.printf("Total dissolved: %.4f mol%n", summary[0]);
System.out.printf("Total evaporated: %.4f mol%n", summary[1]);
System.out.printf("Net transfer: %.4f mol%n", summary[2]);

// Validate against literature
System.out.println(solver.validateMassTransferAgainstLiterature());
```

### Example 2: CO₂ Dissolution into MEA Solution

CO₂ absorption in amine solvent.

```java
import neqsim.fluidmechanics.flownode.*;

// Flow conditions
FlowPattern pattern = FlowPattern.ANNULAR;
double D = 0.025;          // 25 mm tube
double holdup = 0.15;      // Thin liquid film
double usg = 2.0;          // m/s
double usl = 0.1;          // m/s

// Fluid properties (MEA solution)
double rhoL = 1020.0;      // kg/m³
double muL = 0.002;        // Pa·s
double diffL = 1.5e-9;     // m²/s (CO2 in MEA)
double sigma = 0.050;      // N/m

// Calculate interfacial area
double area = InterfacialAreaCalculator.calculateAnnularAreaWithEntrainment(
    D, holdup, 50.0, rhoL, usg, muL, sigma);
System.out.printf("Interfacial area: %.1f m²/m³%n", area);

// Calculate mass transfer coefficient with enhancement
double turbulentIntensity = MassTransferCoefficientCalculator.estimateTurbulentIntensity(
    pattern, rhoL * usl * D / muL);
System.out.printf("Estimated turbulent intensity: %.3f%n", turbulentIntensity);

double kL = MassTransferCoefficientCalculator.calculateLiquidMassTransferCoefficientWithTurbulence(
    pattern, D, holdup, usg, usl, rhoL, muL, diffL, turbulentIntensity);
System.out.printf("kL: %.2e m/s%n", kL);

// Calculate volumetric mass transfer coefficient
double kLa = kL * area;
System.out.printf("kL·a: %.4f 1/s%n", kLa);

// Validate
double[] expectedKL = MassTransferCoefficientCalculator.getExpectedMassTransferCoefficientRange(
    pattern, 1);
System.out.printf("Expected kL range: %.2e - %.2e m/s%n", expectedKL[0], expectedKL[2]);
```

### Example 3: Three-Phase Gas-Oil-Water System

```java
// Configure for three-phase
MassTransferConfig config = MassTransferConfig.forThreePhase();
config.setAqueousPhaseIndex(2);   // Water
config.setOrganicPhaseIndex(1);   // Oil
config.setConvergenceTolerance(1e-5);
config.setCoupledIterations(15);
config.setEnableDiagnostics(true);

// Log configuration
System.out.println(config.toString());
```

### Example 4: Literature Validation

```java
import neqsim.fluidmechanics.flownode.*;

// Test case: Stratified flow, 100mm pipe
FlowPattern pattern = FlowPattern.STRATIFIED_WAVY;
double D = 0.1;

// Calculate and validate interfacial area
double area = InterfacialAreaCalculator.calculateInterfacialArea(
    pattern, D, 0.3, 50, 800, 5, 0.5, 0.025);
double[] areaRange = InterfacialAreaCalculator.getExpectedInterfacialAreaRange(pattern, D);

System.out.println("=== Interfacial Area Validation ===");
System.out.printf("Calculated: %.1f m²/m³%n", area);
System.out.printf("Literature range: %.1f - %.1f m²/m³%n", areaRange[0], areaRange[2]);
System.out.printf("Status: %s%n", 
    (area >= areaRange[0] && area <= areaRange[2]) ? "✓ PASS" : "✗ FAIL");

// Calculate and validate kL
double kL = MassTransferCoefficientCalculator.calculateLiquidMassTransferCoefficient(
    pattern, D, 0.3, 5, 0.5, 800, 0.001, 2e-9);
boolean kLValid = MassTransferCoefficientCalculator.validateAgainstLiterature(kL, pattern, 1);

System.out.println("\n=== Mass Transfer Coefficient Validation ===");
System.out.printf("Calculated kL: %.2e m/s%n", kL);
System.out.printf("Status: %s%n", kLValid ? "✓ PASS" : "✗ FAIL");
```

---

## Literature References

### Interfacial Area Correlations

1. **Tzotzi, C., Andritsos, N. (2013)**  
   *Interfacial shear stress in wavy stratified gas-liquid flow.*  
   Chemical Engineering Science, 86, 49-57.  
   *Used for: Wave enhancement in stratified flow*

2. **Ishii, M., Mishima, K. (1989)**  
   *Droplet entrainment correlation in annular two-phase flow.*  
   International Journal of Heat and Mass Transfer, 32(10), 1835-1846.  
   *Used for: Entrainment in annular flow*

3. **Hewitt, G.F., Hall-Taylor, N.S. (1970)**  
   *Annular Two-Phase Flow.*  
   Pergamon Press.  
   *Used for: Annular flow interfacial area validation*

### Mass Transfer Coefficient Correlations

4. **Lamont, J.C., Scott, D.S. (1970)**  
   *An eddy cell model of mass transfer into the surface of a turbulent liquid.*  
   AIChE Journal, 16(4), 513-519.  
   *Used for: Turbulence enhancement of kL*

5. **Springer, T.G., Pigford, R.L. (1970)**  
   *Influence of surface turbulence and surfactants on gas transport through liquid interfaces.*  
   Industrial & Engineering Chemistry Fundamentals, 9(3), 458-465.  
   *Used for: Marangoni effect correction*

6. **Solbraa, E. (2002)**  
   *Measurement and modelling of absorption of carbon dioxide into methyldiethanolamine solutions at high pressures.*  
   PhD thesis, NTNU.  
   *Used for: Stratified flow kL correlations and validation data*

### General References

7. **Krishna, R., Standart, G.L. (1976)**  
   *Mass and energy transfer in multicomponent systems.*  
   Chemical Engineering Communications, 3(4-5), 201-275.  
   *Used for: Multicomponent diffusion theory*

8. **Taylor, R., Krishna, R. (1993)**  
   *Multicomponent Mass Transfer.*  
   Wiley.  
   *Used for: Film model theory*

9. **Perry's Chemical Engineers' Handbook, 8th Edition**  
   *Used for: Expected kL ranges and validation*

---

## See Also

- [MASS_TRANSFER_MODEL_IMPROVEMENTS.md](MASS_TRANSFER_MODEL_IMPROVEMENTS.md) - Technical review document
- [EvaporationDissolutionTutorial.md](EvaporationDissolutionTutorial.md) - Tutorial with practical examples
- [InterphaseHeatMassTransfer.md](InterphaseHeatMassTransfer.md) - Complete theory for interphase transfer
- [mass_transfer.md](mass_transfer.md) - Diffusivity models and correlations
