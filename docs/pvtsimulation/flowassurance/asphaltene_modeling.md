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

Fast, conservative screening based on field correlations. See [De Boer Screening](asphaltene_deboer_screening.md).

**Advantages:**
- Quick calculation (milliseconds)
- No fluid characterization needed
- Conservative for screening purposes

**Limitations:**
- No onset pressure prediction
- No compositional effects
- Cannot model remediation strategies

### 2. Thermodynamic Modeling (CPA EOS)

Rigorous equation of state approach. See [CPA Calculations](asphaltene_cpa_calculations.md).

**Advantages:**
- Predicts onset pressure/temperature
- Captures compositional effects
- Can model inhibitor effectiveness

**Limitations:**
- Requires fluid characterization
- More computationally intensive
- Needs tuning to experimental data

### 3. Combined Approach

Use De Boer for initial screening, then CPA for detailed analysis of flagged cases. See [Method Comparison](asphaltene_method_comparison.md).

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
