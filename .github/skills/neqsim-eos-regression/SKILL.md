---
name: neqsim-eos-regression
description: "EOS parameter regression workflow for fitting equation-of-state models to experimental data. USE WHEN: tuning binary interaction parameters (kij), matching saturation pressures, fitting PVT data (CME, CVD, DL), characterizing C7+ fractions, or validating EOS predictions against lab measurements. Covers SRK, PR, CPA parameter fitting strategies."
---

# EOS Parameter Regression Workflow

Guide for fitting equation-of-state parameters to experimental data using NeqSim.

## When to Use This Skill

- Fitting binary interaction parameters (kij) to VLE/LLE data
- Matching saturation pressure (bubble/dew point) to lab measurements
- Tuning C7+ characterization to PVT experiments (CME, CVD, DL)
- Validating EOS predictions against NIST or experimental data
- Selecting the best EOS for a specific fluid system

## Regression Strategy

### Step 1: Select Base EOS

| Fluid System | Recommended EOS | Reason |
|-------------|----------------|--------|
| Light hydrocarbons (C1-C6) | SRK or PR | Well-characterized, reliable kij |
| Oil with C7+ | PR with volume correction | Better liquid density |
| Water + hydrocarbons | SRK-CPA | Handles hydrogen bonding |
| CO2 + hydrocarbons | PR or SRK | Good CO2 fugacity |
| Glycol systems (MEG, TEG) | SRK-CPA | Polar + associating |
| Electrolytes | Electrolyte-CPA | Ion interactions |

### Step 2: Gather Experimental Data

Required data types by priority:
1. **Saturation pressure** (bubble/dew point) at reservoir temperature — most critical
2. **Liquid density** at reservoir conditions — validates volume translation
3. **GOR/Rs** from separator tests — validates phase split
4. **Viscosity** — validates transport property correlations
5. **Compositional data** from CVD/DL — validates K-values

### Step 3: Set Up Regression in NeqSim

```java
// Create fluid
SystemInterface fluid = new SystemPrEos(273.15 + 100.0, 200.0);
fluid.addComponent("methane", 0.70);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-heptane", 0.15);
fluid.setMixingRule("classic");

// Access binary interaction parameters
fluid.setMixingRule("classic");

// Get current kij
double kij = fluid.getPhase(0).getMixingRule()
    .getBinaryInteractionParameter(0, 3);  // methane-nC7

// Set modified kij
fluid.getPhase(0).getMixingRule()
    .setBinaryInteractionParameter(0, 3, 0.02);
fluid.getPhase(1).getMixingRule()
    .setBinaryInteractionParameter(0, 3, 0.02);
```

### Step 4: Define Objective Function

```java
// Calculate saturation pressure
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.bubblePointPressureFlash(false);
double calcPsat = fluid.getPressure("bara");

// Compare with experimental
double expPsat = 250.0;  // bara (from lab)
double error = Math.abs(calcPsat - expPsat) / expPsat * 100.0;
```

### Step 5: Iterate and Optimize

```python
# Python regression loop using scipy
from scipy.optimize import minimize_scalar
from neqsim import jneqsim

def objective(kij_value):
    fluid = jneqsim.thermo.system.SystemPrEos(373.15, 200.0)
    fluid.addComponent("methane", 0.70)
    fluid.addComponent("n-heptane", 0.30)
    fluid.setMixingRule("classic")
    fluid.getPhase(0).getMixingRule().setBinaryInteractionParameter(0, 1, kij_value)
    fluid.getPhase(1).getMixingRule().setBinaryInteractionParameter(0, 1, kij_value)

    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
    ops.bubblePointPressureFlash(False)
    calc_psat = fluid.getPressure("bara")

    exp_psat = 250.0
    return (calc_psat - exp_psat) ** 2

result = minimize_scalar(objective, bounds=(-0.05, 0.10), method='bounded')
print(f"Optimized kij = {result.x:.4f}")
```

## PVT Experiment Matching

### Constant Mass Expansion (CME)

```java
// Match relative volume vs pressure
SystemInterface fluid = createCharacterizedFluid();
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

double[] pressures = {400, 350, 300, 250, 200, 150, 100};  // bara
double[] expRelVol = {0.95, 0.97, 0.99, 1.00, 1.05, 1.15, 1.35};

for (int i = 0; i < pressures.length; i++) {
    fluid.setPressure(pressures[i], "bara");
    ops.TPflash();
    fluid.initProperties();
    double calcVol = fluid.getVolume("m3");
    // Compare with expRelVol[i]
}
```

### Constant Volume Depletion (CVD)

```java
// Match gas Z-factor and liquid dropout
SystemInterface fluid = createCharacterizedFluid();
neqsim.pvtsimulation.simulation.ConstantVolumeDepletion cvd =
    new neqsim.pvtsimulation.simulation.ConstantVolumeDepletion(fluid);
cvd.setPressures(new double[]{350, 300, 250, 200, 150, 100});
cvd.setTemperature(373.15);
cvd.runCalc();
double[] calcZgas = cvd.getZgas();
double[] calcLiqVol = cvd.getRelativeVolume();
```

## C7+ Characterization Tuning

### TBP Fraction Setup

```java
SystemInterface fluid = new SystemPrEos(273.15 + 100.0, 200.0);
fluid.addComponent("methane", 0.60);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-butane", 0.03);
fluid.addComponent("n-pentane", 0.02);

// TBP fractions: (name, moleFrac, MW_kg/mol, density_g/cm3)
fluid.addTBPfraction("C7", 0.05, 92.0 / 1000, 0.727);
fluid.addTBPfraction("C8", 0.04, 104.0 / 1000, 0.749);
fluid.addTBPfraction("C9", 0.03, 119.0 / 1000, 0.768);
fluid.addPlusFraction("C10+", 0.10, 200.0 / 1000, 0.830);

fluid.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(6);
fluid.setMixingRule("classic");
fluid.getCharacterization().characterisePlusFraction();
```

### Tuning Parameters

| Parameter | Effect | Typical Range |
|-----------|--------|---------------|
| Plus fraction MW | Shifts Psat, GOR | ±10-20% of measured |
| Plus fraction density | Adjusts liquid density | ±2-5% of measured |
| Number of lumped components | Accuracy vs speed | 3-12 fractions |
| kij (C1-C7+) | Fine-tunes Psat | -0.02 to +0.05 |

## Validation Checklist

- [ ] Saturation pressure within ±2% of experimental
- [ ] Liquid density within ±3% of measured
- [ ] GOR within ±5% of separator test
- [ ] Z-factor within ±2% of CVD data
- [ ] Oil formation volume factor within ±3%
- [ ] Viscosity within ±20% (transport properties have larger uncertainty)

## Common Pitfalls

1. **Overfitting**: Don't tune more parameters than you have independent data points
2. **Non-unique solutions**: Multiple kij sets can match Psat — validate with additional data
3. **Temperature extrapolation**: Fitted parameters may not work at different temperatures
4. **Compositional shift**: Parameters fitted to one composition may fail for depleted fluids
5. **Forgetting `setMixingRule`**: Must call before accessing kij parameters
6. **Wrong units**: MW in kg/mol (not g/mol) for `addTBPfraction`
