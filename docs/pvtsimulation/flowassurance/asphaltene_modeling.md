---
title: Asphaltene Modeling in NeqSim
description: Asphaltenes are the heaviest and most polar fraction of crude oil, defined operationally as the fraction soluble in aromatic solvents (toluene) but insoluble in paraffinic solvents (n-heptane or n-pen...
---

# Asphaltene Modeling in NeqSim

## Introduction

Asphaltenes are the heaviest and most polar fraction of crude oil, defined operationally as the fraction soluble in aromatic solvents (toluene) but insoluble in paraffinic solvents (n-heptane or n-pentane). Asphaltene precipitation during production can cause:

- **Wellbore plugging** - Reduced production rates or complete blockage
- **Reservoir damage** - Near-wellbore permeability reduction
- **Pipeline deposition** - Flow restriction and increased pressure drop
- **Equipment fouling** - Reduced heat transfer and separation efficiency

NeqSim provides multiple approaches to assess asphaltene stability, ranging from simple empirical correlations to rigorous thermodynamic modeling.

## Asphaltene Structure and Properties

### Molecular Characteristics

| Property | Typical Range | Notes |
|----------|---------------|-------|
| Molecular Weight | 500 - 10,000 g/mol | Polydisperse distribution |
| H/C Ratio | 0.9 - 1.2 | Lower than other oil fractions |
| Heteroatom Content | 0.5 - 10 wt% | N, S, O, metals (V, Ni) |
| Aromaticity | 40 - 60% | Polyaromatic core structures |

### Colloidal Model

Asphaltenes exist in crude oil as colloidal particles stabilized by resins (maltenes). The **Colloidal Instability Index (CII)** quantifies this balance:

$$
\text{CII} = \frac{\text{Saturates} + \text{Asphaltenes}}{\text{Aromatics} + \text{Resins}}
$$

Where:
- **CII < 0.7**: Stable oil (asphaltenes well-dispersed)
- **CII 0.7 - 0.9**: Metastable (precipitation possible under stress)
- **CII > 0.9**: Unstable oil (high precipitation risk)

### Resin-to-Asphaltene Ratio

Another stability indicator:

$$
\text{R/A} = \frac{\text{Resins wt\%}}{\text{Asphaltenes wt\%}}
$$

- **R/A > 3**: Generally stable
- **R/A 1.5 - 3**: Moderate risk
- **R/A < 1.5**: High precipitation risk

## SARA Analysis

SARA fractionation separates crude oil into four pseudo-component groups:

| Fraction | Description | Typical Range |
|----------|-------------|---------------|
| **S**aturates | Alkanes and cycloalkanes | 40-80% |
| **A**romatics | Mono/poly-aromatic hydrocarbons | 10-35% |
| **R**esins | Polar, heteroatom-containing | 5-25% |
| **A**sphaltenes | Heaviest polar fraction | 0-15% |

### Using SARA in NeqSim

```java
import neqsim.thermo.characterization.AsphalteneCharacterization;

// Create characterization from SARA analysis
AsphalteneCharacterization sara = new AsphalteneCharacterization(
    0.45,  // Saturates weight fraction
    0.30,  // Aromatics weight fraction
    0.20,  // Resins weight fraction
    0.05   // Asphaltenes weight fraction
);

// Get stability indicators
double cii = sara.getColloidalInstabilityIndex();
double ra = sara.getResinToAsphalteneRatio();
String stability = sara.evaluateStability();

System.out.println("CII: " + cii);
System.out.println("R/A Ratio: " + ra);
System.out.println("Stability: " + stability);
```

## Precipitation Triggers

Asphaltene precipitation occurs when the solubility parameter of the oil phase changes sufficiently to destabilize the asphaltene colloids:

### Pressure Depletion

As pressure drops below the bubble point:
1. Light components evolve into vapor phase
2. Remaining liquid becomes denser and more aliphatic
3. Asphaltene solubility decreases
4. Maximum instability typically occurs near bubble point

### Temperature Changes

- **Cooling**: Can increase or decrease stability (system-dependent)
- **Heating**: Generally increases asphaltene solubility

### Composition Changes

- **Gas injection** (CO₂, hydrocarbon gas): Reduces solubility
- **Blending with light crudes**: Dilution destabilizes asphaltenes
- **Acid stimulation**: pH changes affect polar interactions

## Modeling Approaches in NeqSim

### 1. Empirical Screening (De Boer)

Fast, conservative screening based on field correlations. See [De Boer Screening](asphaltene_deboer_screening).

**Advantages:**
- Quick calculation (milliseconds)
- No fluid characterization needed
- Conservative for screening purposes

**Limitations:**
- No onset pressure prediction
- No compositional effects
- Cannot model remediation strategies

### 2. Thermodynamic Modeling (CPA EOS)

Rigorous equation of state approach. See [CPA Calculations](asphaltene_cpa_calculations).

**Advantages:**
- Predicts onset pressure/temperature
- Captures compositional effects
- Can model inhibitor effectiveness
- Uses `PhaseType.ASPHALTENE` for accurate phase identification

**Limitations:**
- Requires fluid characterization
- More computationally intensive
- Needs tuning to experimental data

### PhaseType.ASPHALTENE

NeqSim uses a dedicated `PhaseType.ASPHALTENE` enum value to distinguish precipitated asphaltenes from other solid phases (wax, hydrate). This enables:

- **Accurate phase identification** in multi-phase flash calculations
- **Correct physical property calculations** using asphaltene-specific correlations
- **Proper phase ordering** (asphaltene as heaviest phase)
- **Easy API access** via `fluid.hasPhaseType("asphaltene")`

```java
import neqsim.thermo.phase.PhaseType;

// Check for asphaltene precipitation
if (fluid.hasPhaseType(PhaseType.ASPHALTENE)) {
    PhaseInterface asphaltene = fluid.getPhaseOfType("asphaltene");

    // Access asphaltene phase properties
    double density = asphaltene.getDensity("kg/m3");        // ~1150 kg/m³
    double Cp = asphaltene.getCp("kJ/kgK");                 // ~0.9 kJ/kgK
    double viscosity = asphaltene.getViscosity("Pa*s");     // ~10,000 Pa·s
    double thermalCond = asphaltene.getThermalConductivity("W/mK"); // ~0.20 W/mK
    double soundSpeed = asphaltene.getSoundSpeed("m/s");    // ~1745 m/s
}
```

### 3. Pedersen Classical Cubic EOS Approach

A simpler approach using classical cubic equations of state (SRK/PR) without association terms, developed by K.S. Pedersen and presented at GOTECH Dubai 2025.

**Reference:** Pedersen, K.S. (2025). "The Mechanisms Behind Asphaltene Precipitation – Successfully Handled by a Classical Cubic Equation of State." SPE-224534-MS, GOTECH, Dubai.

#### Theoretical Background

The key insight from Pedersen's work is that asphaltene precipitation can be successfully modeled as a **liquid-liquid phase split** using classical cubic equations of state. The approach treats asphaltene as a heavy pseudo-component characterized using the same correlations as C7+ fractions.

**Critical Property Correlations:**

$$T_c = a_0 + a_1 \ln(M) + a_2 M + \frac{a_3}{M}$$

$$\ln(P_c) = b_0 + b_1 \rho^{0.25} + \frac{b_2}{M} + \frac{b_3}{M^2}$$

$$\omega = c_0 + c_1 \ln(M) + c_2 \rho + c_3 M$$

Where:
- $M$ = molecular weight (g/mol)
- $\rho$ = density (g/cm³)
- $T_c$ = critical temperature (K)
- $P_c$ = critical pressure (bar)
- $\omega$ = acentric factor

#### Basic Usage

```java
import neqsim.thermo.characterization.PedersenAsphalteneCharacterization;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create SRK system (classical cubic EOS)
SystemSrkEos fluid = new SystemSrkEos(373.15, 200.0);
fluid.addComponent("methane", 0.40);
fluid.addComponent("n-heptane", 0.45);
fluid.addComponent("nC20", 0.10);

// Create Pedersen asphaltene characterization
PedersenAsphalteneCharacterization asphChar = new PedersenAsphalteneCharacterization();
asphChar.setAsphalteneMW(750.0);        // Molecular weight (g/mol)
asphChar.setAsphalteneDensity(1.10);    // Density (g/cm³)
asphChar.addAsphalteneToSystem(fluid, 0.05);  // Add 0.05 mol

// IMPORTANT: Set mixing rule AFTER adding all components
fluid.setMixingRule("classic");
fluid.init(0);

// Print characterization results
System.out.println(asphChar.toString());
```

#### Complete Oil Characterization with TBP Fractions

For realistic oil systems, combine asphaltene characterization with TBP (True Boiling Point) fraction characterization:

```java
import neqsim.thermo.characterization.PedersenAsphalteneCharacterization;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create system and set Pedersen TBP model
SystemInterface oil = new SystemSrkEos(373.15, 200.0);
oil.getCharacterization().setTBPModel("PedersenSRK");

// Add light components (defined compounds)
oil.addComponent("nitrogen", 0.005);
oil.addComponent("CO2", 0.02);
oil.addComponent("methane", 0.35);
oil.addComponent("ethane", 0.08);
oil.addComponent("propane", 0.05);
oil.addComponent("i-butane", 0.01);
oil.addComponent("n-butane", 0.02);
oil.addComponent("i-pentane", 0.015);
oil.addComponent("n-pentane", 0.015);
oil.addComponent("n-hexane", 0.02);

// Add C7+ fractions as TBP pseudo-components
// Parameters: name, moles, MW (kg/mol), density (g/cm³)
oil.addTBPfraction("C7", 0.10, 96.0 / 1000.0, 0.738);
oil.addTBPfraction("C8", 0.08, 107.0 / 1000.0, 0.765);
oil.addTBPfraction("C9", 0.06, 121.0 / 1000.0, 0.781);
oil.addTBPfraction("C10", 0.04, 134.0 / 1000.0, 0.792);
oil.addTBPfraction("C11-C15", 0.06, 180.0 / 1000.0, 0.825);
oil.addTBPfraction("C16-C20", 0.03, 260.0 / 1000.0, 0.865);
oil.addTBPfraction("C21+", 0.02, 450.0 / 1000.0, 0.920);

// Add asphaltene using Pedersen characterization
PedersenAsphalteneCharacterization asphChar = new PedersenAsphalteneCharacterization();
asphChar.setAsphalteneMW(850.0);
asphChar.setAsphalteneDensity(1.12);
asphChar.addAsphalteneToSystem(oil, 0.015);

// Set mixing rule and initialize
oil.setMixingRule("classic");
oil.init(0);

// Perform flash calculation
ThermodynamicOperations ops = new ThermodynamicOperations(oil);
ops.TPflash();
oil.prettyPrint();
```

#### Tuning to Experimental Data

The class provides tuning multipliers for fitting to experimental onset pressure data:

```java
// Create characterization
PedersenAsphalteneCharacterization asphChar = new PedersenAsphalteneCharacterization();
asphChar.setAsphalteneMW(750.0);
asphChar.setAsphalteneDensity(1.10);

// Apply tuning multipliers to match experimental onset pressure
asphChar.setTcMultiplier(1.03);     // Increase Tc by 3%
asphChar.setPcMultiplier(0.97);     // Decrease Pc by 3%
asphChar.setOmegaMultiplier(1.05);  // Increase ω by 5%

// Or use convenience method for all at once
asphChar.setTuningParameters(1.03, 0.97, 1.05);

// Characterize and add to system
asphChar.characterize();
asphChar.addAsphalteneToSystem(fluid, 0.05);

// Reset to defaults if needed
asphChar.resetTuningParameters();
```

**Tuning Guidelines:**

| Parameter | Effect on Onset Pressure | Typical Range |
|-----------|-------------------------|---------------|
| `tcMultiplier` | Higher Tc → lower onset pressure | 0.95 - 1.10 |
| `pcMultiplier` | Higher Pc → higher onset pressure | 0.85 - 1.15 |
| `omegaMultiplier` | Higher ω → complex effects | 0.90 - 1.20 |

#### Distributed Asphaltene Pseudo-Components

For heavy oils, represent asphaltene as multiple pseudo-components with distributed MW:

```java
// Add distributed asphaltene (3 pseudo-components)
asphChar.setAsphalteneMW(1000.0);   // Average MW
asphChar.setAsphalteneDensity(1.15);
asphChar.addDistributedAsphaltene(heavyOil, 0.08, 3);

// This creates 3 components: Asph_1_PC, Asph_2_PC, Asph_3_PC
// with MW ranging from 0.5x to 2x the average MW
```

#### Typical Property Ranges

Based on the Pedersen correlations, asphaltene components show these typical properties:

| Property | Light Asphaltene (MW=500) | Medium (MW=750) | Heavy (MW=1500) |
|----------|--------------------------|-----------------|-----------------|
| Tc | 950-1000 K | 990-1010 K | 1040-1060 K |
| Pc | 17-19 bar | 15-17 bar | 14-16 bar |
| ω | 0.9-1.0 | 0.9-1.0 | 1.2-1.4 |
| Tb | 700-800 K | 800-850 K | 900-950 K |

**Advantages:**
- Uses standard cubic EOS (simpler than CPA)
- Consistent with Pedersen C7+ characterization already in NeqSim
- Models precipitation as liquid-liquid split
- Tunable to experimental data
- Suitable for routine engineering calculations

**Limitations:**
- Does not capture association effects explicitly
- May require different tuning than CPA models
- L-L split detection depends on flash algorithm convergence

### 4. Flory-Huggins Regular Solution Model

The Flory-Huggins model treats the oil as a solvent and asphaltenes as a dissolved
polymer, predicting precipitation from the mismatch in solubility parameters. This
is one of the most widely used approaches in the petroleum industry (Hirschberg et al., 1984).

**Key equation:**

$$\ln(\phi_a) + \left(1 - \frac{V_a}{V_L}\right)(1 - \phi_a) + \chi (1 - \phi_a)^2 = 0$$

Where the Flory-Huggins interaction parameter is:

$$\chi = \frac{V_a}{RT}(\delta_a - \delta_L)^2$$

**Advantages:**
- Predicts onset pressure from first principles
- Can be configured from API gravity or SARA fractions
- Produces precipitation curves (wt% vs pressure)
- Moderate computational cost

**Limitations:**
- Regular solution approximation (no association)
- Default Lian et al. (1994) solubility parameter coefficients fail for live oils — use `calibrateCorrelation()` (see below)
- Requires tuning for specific oil types

#### Basic Usage

```java
import neqsim.pvtsimulation.flowassurance.FloryHugginsAsphalteneModel;
import neqsim.thermo.system.SystemSrkEos;

// Create a fluid system
SystemSrkEos fluid = new SystemSrkEos(373.15, 350.0);
fluid.addComponent("methane", 0.40);
fluid.addComponent("n-heptane", 0.50);
fluid.addComponent("nC20", 0.10);
fluid.setMixingRule("classic");

// Create FH model
FloryHugginsAsphalteneModel fh =
    new FloryHugginsAsphalteneModel(fluid, 373.15);

// Configure from API gravity (sets MW, solubility parameter, density, molar volume)
fh.configureFromAPIGravity(30.0);

// IMPORTANT: Calibrate the A coefficient using physics-based approach
// This uses the Flory-Huggins critical chi condition to set A from first principles
fh.calibrateCorrelation(373.15);

// Calculate onset pressure
double onsetP = fh.calculateOnsetPressure(373.15, 500.0, 50.0);
System.out.println("FH Onset Pressure: " + onsetP + " bar");
```

#### API Gravity Configuration

The `configureFromAPIGravity()` method uses continuous correlations to set
asphaltene MW, solubility parameter, and molar volume:

| API Gravity | Asphaltene MW (g/mol) | Solubility Parameter (MPa$^{0.5}$) |
|-------------|----------------------|-------------------------------------|
| 20 (heavy)  | ~2000                | ~20.2                               |
| 30 (medium) | ~1000                | ~20.8                               |
| 35 (light)  | ~750                 | ~21.1                               |
| 40 (light)  | ~550                 | ~21.4                               |

#### SARA Configuration

```java
// Refine FH model with SARA data (after API configuration)
fh.configureFromSARA(0.45, 0.30, 0.15, 0.10);
// R/A ratio > 1.5 indicates good peptization
// Very high saturate fraction (>75% of sat+aro) worsens solvency
```

#### Physics-Based Calibration (`calibrateCorrelation`)

The default Lian et al. (1994) correlation coefficients ($A = 0.017347$, $B = 2.904$) were determined from dead oil measurements and produce liquid solubility parameters ($\delta_L \approx 13$–$15$ MPa$^{0.5}$) far below typical asphaltene values ($\delta_A \approx 19$–$23$ MPa$^{0.5}$) for live reservoir oils. This causes the model to trivially predict onset at the reservoir pressure itself.

The `calibrateCorrelation()` method resolves this by using the Flory-Huggins critical interaction parameter $\chi_c$ to set the $A$ coefficient from first principles:

1. Flashes the fluid at reservoir P/T to get actual oil density and liquid molar volume $V_L$
2. Computes $\chi_c = r(1 + 1/\sqrt{r})^2 / 2$ from the volume ratio $r = V_a / V_L$
3. Converts to a critical solubility parameter gap $\Delta\delta_c$
4. Sets $A$ so that $(\delta_A - \delta_L)$ is 0.35 MPa$^{0.5}$ below the critical gap at reservoir conditions

```java
// After configureFromAPIGravity or manual parameter setup:
fh.calibrateCorrelation(373.15);  // Pass reservoir temperature in Kelvin

// The calibrated A coefficient now gives realistic onset predictions
double onset = fh.calculateOnsetPressure(373.15, 500.0, 50.0);
// Returns onset between bubble point and reservoir pressure
```

**When to use calibrateCorrelation:**
- Always for live reservoir oil onset predictions
- After `configureFromAPIGravity()` and/or `configureFromSARA()`
- Not needed for dead oil dilution titration modeling (default coefficients are adequate)

#### Precipitation Curves

```java
// Generate precipitation curve (wt% precipitated vs pressure)
double[][] curve = fh.generatePrecipitationCurve(373.15, 500.0, 50.0, 50);
// curve[0] = pressures (bar), curve[1] = precipitated fraction
```

### 5. Refractive Index Screening

The refractive index (RI) method assesses asphaltene stability based on the
difference between the crude oil RI and RI at the flocculation onset (Buckley et al., 1998).

```java
import neqsim.pvtsimulation.flowassurance.RefractiveIndexAsphalteneScreening;

// From measured RI values
RefractiveIndexAsphalteneScreening riScreen =
    new RefractiveIndexAsphalteneScreening(1.505, 1.490);
RefractiveIndexAsphalteneScreening.RIStability stability =
    riScreen.evaluateStability();
double margin = riScreen.getRIStabilityMargin();

// Or estimate RI from density
riScreen.estimateRIFromDensity(750.0);  // kg/m3
```

**Stability classification:**

| RI Margin (RI_oil - RI_onset) | Stability |
|-------------------------------|-----------|
| > 0.03                        | Very Stable |
| 0.02 - 0.03                   | Stable |
| 0.01 - 0.02                   | Marginal |
| 0 - 0.01                      | Unstable |
| < 0                           | Highly Unstable |

### 6. Multi-Method Benchmark Framework

The `AsphalteneMultiMethodBenchmark` class provides a unified framework for
comparing all five prediction methods side-by-side:

```java
import neqsim.pvtsimulation.flowassurance.AsphalteneMultiMethodBenchmark;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

// Create systems
SystemSrkCPAstatoil cpaFluid = new SystemSrkCPAstatoil(373.15, 350.0);
cpaFluid.addComponent("methane", 0.40);
cpaFluid.addComponent("n-heptane", 0.50);
cpaFluid.addComponent("asphaltene", 0.10);
cpaFluid.setMixingRule("classic");

SystemSrkEos cubicFluid = new SystemSrkEos(373.15, 350.0);
cubicFluid.addComponent("methane", 0.40);
cubicFluid.addComponent("n-heptane", 0.50);
cubicFluid.addComponent("nC20", 0.10);
cubicFluid.setMixingRule("classic");

// Create benchmark
AsphalteneMultiMethodBenchmark benchmark =
    new AsphalteneMultiMethodBenchmark(cpaFluid, cubicFluid, 350.0, 373.15);
benchmark.setSARAFractions(0.45, 0.30, 0.15, 0.10);
benchmark.setAPIGravity(30.0);
benchmark.setInSituDensity(750.0);

// Run all methods
benchmark.runAllMethods();

// Access individual results
AsphalteneMultiMethodBenchmark.MethodResult fhResult =
    benchmark.getMethodResult("FloryHuggins");
double fhOnset = fhResult.onsetPressure;
String fhRisk = fhResult.riskLevel;

// Get complete JSON report
String json = benchmark.toJSON();
```

#### Built-in Literature Cases

The benchmark includes 7 literature validation cases:

```java
List<AsphalteneMultiMethodBenchmark.LiteratureCase> cases =
    benchmark.getLiteratureCases();
for (AsphalteneMultiMethodBenchmark.LiteratureCase lc : cases) {
    System.out.println(lc.label + ": P_onset=" + lc.measuredOnsetPressure + " bar");
}
```

#### Error Statistics

```java
// Get error statistics for a method across all literature cases
Map<String, Double> stats = benchmark.getErrorStatistics();
// Returns: AAD, AARD_pct, RMSD, maxError, bias
```

### 7. Improved Pedersen Method (kij Tuning)

The Pedersen cubic EOS approach has been improved with:

- **Kesler-Lee/Edmister acentric factor** correlation (replacing the original linear)
- **MW-scaled binary interaction parameters** (kij) between asphaltene and oil components
- **Proper component addition** via `addAsphalteneToSystem()`

```java
import neqsim.thermo.characterization.PedersenAsphalteneCharacterization;

PedersenAsphalteneCharacterization pedersen =
    new PedersenAsphalteneCharacterization();
pedersen.setAsphalteneMW(1200.0);
pedersen.setAsphalteneDensity(1.15);

// Add asphaltene to system
pedersen.addAsphalteneToSystem(cubicFluid, 0.01);
cubicFluid.setMixingRule("classic");
cubicFluid.init(0);

// Apply MW-scaled kij (1.5x for methane, 1.2x for ethane/propane, 0.5x for heavy)
pedersen.applyAsphalteneKij(cubicFluid, 0.08);

// Find onset pressure via pressure sweep
double onset = pedersen.calculateOnsetPressure(cubicFluid, 500.0, 50.0);
```

### 8. Combined Approach

Use De Boer for initial screening, then CPA, Flory-Huggins, or Pedersen for
detailed analysis of flagged cases. The `AsphalteneMultiMethodBenchmark` provides
all methods in a single API. See [Method Comparison](asphaltene_method_comparison).

## Best Practices

### For Field Development

1. **Early Screening**: Use De Boer on all fluids
2. **Focused Analysis**: Apply CPA to flagged samples
3. **Lab Validation**: Confirm predictions with HPM/AOP tests
4. **Parameter Tuning**: Use `AsphalteneOnsetFitting` to match measured onset data
5. **Monitoring Strategy**: Plan for real-time detection

### For Process Design

1. **Operating Envelope**: Generate precipitation PT curves
2. **Injection Studies**: Model CO₂/gas injection effects
3. **Blending Optimization**: Predict compatibility issues
4. **Inhibitor Screening**: Evaluate chemical effectiveness

### Recommended Workflow

```
┌─────────────────────────────────────────────────────────────────┐
│                    ASPHALTENE ANALYSIS WORKFLOW                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Step 1: DE BOER SCREENING                                      │
│  • Fast empirical screening                                     │
│  • Input: P_res, P_bub, density                                 │
│  • Output: Risk category (NO/SLIGHT/MODERATE/SEVERE)            │
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              │                               │
              ▼                               ▼
     ┌──────────────┐              ┌──────────────────────┐
     │  LOW RISK    │              │  ELEVATED RISK       │
     │  • Monitor   │              │  • Proceed to CPA    │
     │  • Document  │              │  analysis            │
     └──────────────┘              └──────────────────────┘
                                              │
                                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Step 2: CPA THERMODYNAMIC ANALYSIS                             │
│  • Create fluid with asphaltene component                       │
│  • Calculate onset pressure/temperature                         │
│  • Generate precipitation envelope                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Step 3: LABORATORY VALIDATION                                  │
│  • Measure AOP at multiple temperatures                         │
│  • SARA analysis for composition                                │
│  • HPM microscopy for onset detection                           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Step 4: PARAMETER TUNING (AsphalteneOnsetFitting)              │
│  • Fit CPA parameters to lab data                               │
│  • Validate predictions at other conditions                     │
│  • Document fitted parameters for field use                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Step 5: FIELD APPLICATION                                      │
│  • Predict onset during depletion/injection                     │
│  • Design mitigation strategies                                 │
│  • Establish monitoring program                                 │
└─────────────────────────────────────────────────────────────────┘
```

## References

1. De Boer, R.B., et al. (1995). "Screening of Crude Oils for Asphalt Precipitation: Theory, Practice, and the Selection of Inhibitors." SPE Production & Facilities. **SPE-24987-PA**

2. Mullins, O.C. (2010). "The Modified Yen Model." Energy & Fuels, 24(4), 2179-2207.

3. Leontaritis, K.J., and Mansoori, G.A. (1988). "Asphaltene Deposition: A Survey of Field Experiences and Research Approaches." Journal of Petroleum Science and Engineering.

4. Victorov, A.I., and Firoozabadi, A. (1996). "Thermodynamic Micellization Model of Asphaltene Precipitation from Petroleum Fluids." AIChE Journal.

5. Kontogeorgis, G.M., and Folas, G.K. (2010). "Thermodynamic Models for Industrial Applications." Wiley.

6. Li, Z., and Firoozabadi, A. (2010). "Modeling Asphaltene Precipitation by n-Alkanes from Heavy Oils and Bitumens Using Cubic-Plus-Association Equation of State." Energy & Fuels, 24, 1106-1113.

7. Vargas, F.M., et al. (2009). "Development of a General Method for Modeling Asphaltene Stability." Energy & Fuels, 23, 1140-1146.

8. Pedersen, K.S. (2025). "The Mechanisms Behind Asphaltene Precipitation – Successfully Handled by a Classical Cubic Equation of State." SPE-224534-MS, GOTECH, Dubai.

9. Pedersen, K.S., Christensen, P.L. (2007). "Phase Behavior of Petroleum Reservoir Fluids." CRC Press.

10. Pedersen, K.S., Fredenslund, A., Thomassen, P. (1989). "Properties of Oils and Natural Gases." Gulf Publishing.

11. Hirschberg, A., de Jong, L.N.J., Schipper, B.A., Meijer, J.G. (1984). "Influence of Temperature and Pressure on Asphaltene Flocculation." SPE Journal, 24(3), 283-293.

12. Buckley, J.S., Hirasaki, G.J., Liu, Y., Von Drasek, S., Wang, J.X., Gill, B.S. (1998). "Asphaltene Precipitation and Solvent Properties of Crude Oils." Petroleum Science and Technology, 16(3-4), 251-285.

13. Akbarzadeh, K., Alboudwarej, H., Svrcek, W.Y., Yarranton, H.W. (2005). "A generalized regular solution model for asphaltene precipitation from n-alkane diluted heavy oils and bitumens." Fluid Phase Equilibria, 232, 159-170.

14. Kesler, M.G., Lee, B.I. (1976). "Improve Prediction of Enthalpy of Fractions." Hydrocarbon Processing, 55(3), 153-158.
