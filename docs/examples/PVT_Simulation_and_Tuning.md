---
layout: default
title: "PVT Simulation and Tuning"
description: "Jupyter notebook tutorial for NeqSim"
parent: Examples
nav_order: 1
---

# PVT Simulation and Tuning

> **Note:** This is an auto-generated Markdown version of the Jupyter notebook 
> [`PVT_Simulation_and_Tuning.ipynb`](https://github.com/equinor/neqsim/blob/master/docs/examples/PVT_Simulation_and_Tuning.ipynb).
> You can also [view it on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/PVT_Simulation_and_Tuning.ipynb) 
> or [open in Google Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/PVT_Simulation_and_Tuning.ipynb).

---

```python
# Import NeqSim Java classes via direct Java access
from neqsim import jneqsim
import matplotlib.pyplot as plt
import numpy as np

print("NeqSim loaded successfully!")
```

## 2. Creating a Reservoir Fluid <a name="fluid-creation"></a>

We'll create a typical North Sea gas condensate with composition analysis up to C6 and a C7+ fraction.

```python
# Create an SRK-Peneloux equation of state system
# Initial conditions: reservoir temperature and pressure
reservoir_temp_K = 373.15  # 100°C
reservoir_pres_bara = 350.0  # 350 bara

fluid = jneqsim.thermo.system.SystemSrkEos(reservoir_temp_K, reservoir_pres_bara)

# Add light and intermediate components (mole fractions)
fluid.addComponent("nitrogen", 0.005)
fluid.addComponent("CO2", 0.025)
fluid.addComponent("methane", 0.65)
fluid.addComponent("ethane", 0.08)
fluid.addComponent("propane", 0.05)
fluid.addComponent("i-butane", 0.01)
fluid.addComponent("n-butane", 0.02)
fluid.addComponent("i-pentane", 0.008)
fluid.addComponent("n-pentane", 0.012)
fluid.addComponent("n-hexane", 0.015)

# Add C7+ fraction
# Parameters: name, mole fraction, molecular weight (kg/mol), specific gravity
c7plus_molefrac = 0.125
c7plus_mw = 0.180  # 180 g/mol = 0.180 kg/mol
c7plus_sg = 0.82   # Specific gravity

fluid.addPlusFraction("C7+", c7plus_molefrac, c7plus_mw, c7plus_sg)

# Set mixing rule (2 = classic with database BIPs)
fluid.setMixingRule(2)

# Enable multiphase calculations
fluid.setMultiPhaseCheck(True)

print("=== Base Fluid Composition ===")
print(f"Number of components: {fluid.getNumberOfComponents()}")
print(f"C7+ molecular weight: {c7plus_mw * 1000:.0f} g/mol")
print(f"C7+ specific gravity: {c7plus_sg:.2f}")
```

## 3. Plus Fraction Characterization <a name="characterization"></a>

The C7+ fraction needs to be split into pseudo-components for accurate phase behavior predictions.

### Whitson Gamma Distribution

$$p(M) = \frac{(M - \eta)^{\alpha - 1}}{\beta^\alpha \cdot \Gamma(\alpha)} \exp\left(-\frac{M - \eta}{\beta}\right)$$

Where:
- $\alpha$ = shape parameter (typically 1.0)
- $\eta$ = minimum molecular weight (typically 84 g/mol for C7)
- $\beta$ = scale parameter (calculated from average MW)

```python
# Configure plus fraction characterization
characterization = fluid.getCharacterization()

# Use Whitson Gamma model for splitting
characterization.setPlusFractionModel("Whitson Gamma Model")

# Set the number of pseudo-components for C7+
characterization.setNumberOfPseudoComponents(12)

# Perform the characterization
characterization.characterisePlusFraction()

# Create database and initialize
fluid.createDatabase(True)
fluid.setMixingRule(2)

print("=== Characterized Fluid ===")
print(f"Total components after characterization: {fluid.getNumberOfComponents()}")
print("\nPseudo-component properties:")
print("-" * 60)
print(f"{'Component':<12} {'Mole%':>8} {'MW (g/mol)':>12} {'Tc (K)':>10} {'Pc (bar)':>10}")
print("-" * 60)

for i in range(fluid.getNumberOfComponents()):
    comp = fluid.getComponent(i)
    name = comp.getComponentName()
    molfrac = comp.getz() * 100
    mw = comp.getMolarMass() * 1000  # Convert to g/mol
    tc = comp.getTC()
    pc = comp.getPC()
    
    if molfrac > 0.01:  # Only show significant components
        print(f"{name:<12} {molfrac:>8.3f} {mw:>12.1f} {tc:>10.1f} {pc:>10.2f}")
```

## 4. Constant Composition Expansion (CCE) <a name="cce"></a>

CCE simulates depressurization at constant temperature without removing any material.

**Key outputs:**
- Saturation pressure (bubble/dew point)
- Relative volume (V/Vsat)
- Liquid/gas compressibility

```python
# Create CCE simulation
cce = jneqsim.pvtsimulation.simulation.ConstantMassExpansion(fluid)

# Set experimental temperature (must match lab conditions)
cce.setTemperature(373.15)  # 100°C in Kelvin

# Run the simulation
cce.runCalc()

# Get results
pressures = list(cce.getPressures())
relative_volumes = list(cce.getRelativeVolume())
sat_pressure = cce.getSaturationPressure()

print("=== CCE Results ===")
print(f"Saturation Pressure: {sat_pressure:.1f} bara")
print(f"\n{'Pressure (bara)':>15} {'Rel. Volume':>15}")
print("-" * 32)
for p, v in zip(pressures, relative_volumes):
    if p > 0:
        print(f"{p:>15.1f} {v:>15.4f}")
```

```python
# Plot CCE results
fig, ax = plt.subplots(figsize=(10, 6))

# Filter valid data
valid_p = [p for p in pressures if p > 0]
valid_v = [v for p, v in zip(pressures, relative_volumes) if p > 0]

ax.plot(valid_p, valid_v, 'b-o', linewidth=2, markersize=6, label='Calculated')
ax.axvline(x=sat_pressure, color='r', linestyle='--', linewidth=2, 
           label=f'Saturation P = {sat_pressure:.1f} bara')

ax.set_xlabel('Pressure (bara)', fontsize=12)
ax.set_ylabel('Relative Volume (V/Vsat)', fontsize=12)
ax.set_title('Constant Composition Expansion (CCE)', fontsize=14)
ax.legend()
ax.grid(True, alpha=0.3)
ax.invert_xaxis()  # Decreasing pressure

plt.tight_layout()
plt.show()
```

## 5. Constant Volume Depletion (CVD) <a name="cvd"></a>

CVD is the standard experiment for gas condensate reservoirs. Gas is removed at each pressure step to maintain constant volume.

**Key outputs:**
- Liquid dropout curve
- Gas Z-factor
- Produced gas composition

```python
# Create a fresh fluid for CVD (clone the base fluid)
cvd_fluid = fluid.clone()

# Create CVD simulation
cvd = jneqsim.pvtsimulation.simulation.ConstantVolumeDepletion(cvd_fluid)

# Set temperature
cvd.setTemperature(373.15)

# Define pressure stages
pressures_cvd = [350.0, 300.0, 250.0, 200.0, 150.0, 100.0, 50.0]
cvd.setPressures(pressures_cvd)

# Run simulation
cvd.runCalc()

# Get results
liquid_dropout = list(cvd.getLiquidVolume())  # Liquid volume percentage
z_factors = list(cvd.getZgas())  # Gas Z-factor

print("=== CVD Results ===")
print(f"{'Pressure':>12} {'Liquid %':>12} {'Z-factor':>12}")
print("-" * 38)
for i, p in enumerate(pressures_cvd):
    if i < len(liquid_dropout) and i < len(z_factors):
        print(f"{p:>12.1f} {liquid_dropout[i]:>12.2f} {z_factors[i]:>12.4f}")
```

```python
# Plot CVD liquid dropout curve
fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))

# Liquid dropout curve
n_points = min(len(pressures_cvd), len(liquid_dropout))
ax1.plot(pressures_cvd[:n_points], liquid_dropout[:n_points], 'g-o', linewidth=2, markersize=8)
ax1.set_xlabel('Pressure (bara)', fontsize=12)
ax1.set_ylabel('Liquid Dropout (%)', fontsize=12)
ax1.set_title('CVD Liquid Dropout Curve', fontsize=14)
ax1.grid(True, alpha=0.3)
ax1.invert_xaxis()

# Z-factor
n_points_z = min(len(pressures_cvd), len(z_factors))
ax2.plot(pressures_cvd[:n_points_z], z_factors[:n_points_z], 'b-s', linewidth=2, markersize=8)
ax2.set_xlabel('Pressure (bara)', fontsize=12)
ax2.set_ylabel('Gas Z-factor', fontsize=12)
ax2.set_title('CVD Gas Compressibility Factor', fontsize=14)
ax2.grid(True, alpha=0.3)
ax2.invert_xaxis()

plt.tight_layout()
plt.show()
```

## 6. Differential Liberation (DLE) <a name="dle"></a>

DLE is the standard experiment for black oil reservoirs. Gas is removed at each step and oil properties are measured.

**Key outputs:**
- Solution gas-oil ratio (Rs)
- Oil formation volume factor (Bo)
- Oil density

```python
# Create a black oil fluid for DLE
black_oil = jneqsim.thermo.system.SystemSrkEos(373.15, 250.0)

# Black oil composition (more heavy components)
black_oil.addComponent("nitrogen", 0.002)
black_oil.addComponent("CO2", 0.01)
black_oil.addComponent("methane", 0.35)
black_oil.addComponent("ethane", 0.05)
black_oil.addComponent("propane", 0.04)
black_oil.addComponent("n-butane", 0.03)
black_oil.addComponent("n-pentane", 0.02)
black_oil.addComponent("n-hexane", 0.02)

# Heavier C7+ fraction for black oil
black_oil.addPlusFraction("C7+", 0.478, 0.250, 0.86)  # MW=250 g/mol, SG=0.86

# Characterize
black_oil.getCharacterization().setPlusFractionModel("Whitson Gamma Model")
black_oil.getCharacterization().setNumberOfPseudoComponents(8)
black_oil.getCharacterization().characterisePlusFraction()
black_oil.createDatabase(True)
black_oil.setMixingRule(2)
black_oil.setMultiPhaseCheck(True)

print(f"Black oil fluid created with {black_oil.getNumberOfComponents()} components")
```

```python
# Create DLE simulation
dle = jneqsim.pvtsimulation.simulation.DifferentialLiberation(black_oil)

# Set temperature
dle.setTemperature(373.15)

# Define pressure stages
pressures_dle = [250.0, 200.0, 150.0, 100.0, 50.0, 1.01325]  # Down to atmospheric
dle.setPressures(pressures_dle)

# Run simulation
dle.runCalc()

# Get results
rs_values = list(dle.getRs())    # Solution GOR (Sm3/Sm3)
bo_values = list(dle.getBo())    # Oil FVF (m3/Sm3)

print("=== DLE Results ===")
print(f"{'Pressure (bara)':>15} {'Rs (Sm3/Sm3)':>15} {'Bo (m3/Sm3)':>15}")
print("-" * 47)
for i, p in enumerate(pressures_dle):
    if i < len(rs_values) and i < len(bo_values):
        print(f"{p:>15.1f} {rs_values[i]:>15.2f} {bo_values[i]:>15.4f}")
```

```python
# Plot DLE results
fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))

n_dle = min(len(pressures_dle), len(rs_values), len(bo_values))

# Rs curve
ax1.plot(pressures_dle[:n_dle], rs_values[:n_dle], 'r-o', linewidth=2, markersize=8)
ax1.set_xlabel('Pressure (bara)', fontsize=12)
ax1.set_ylabel('Solution GOR, Rs (Sm³/Sm³)', fontsize=12)
ax1.set_title('Differential Liberation - Solution GOR', fontsize=14)
ax1.grid(True, alpha=0.3)
ax1.invert_xaxis()

# Bo curve  
ax2.plot(pressures_dle[:n_dle], bo_values[:n_dle], 'b-s', linewidth=2, markersize=8)
ax2.set_xlabel('Pressure (bara)', fontsize=12)
ax2.set_ylabel('Oil FVF, Bo (m³/Sm³)', fontsize=12)
ax2.set_title('Differential Liberation - Formation Volume Factor', fontsize=14)
ax2.grid(True, alpha=0.3)
ax2.invert_xaxis()

plt.tight_layout()
plt.show()
```

## 7. Separator Test <a name="separator"></a>

The separator test simulates multi-stage surface separation to determine stock tank oil properties and field GOR.

```python
# Create separator test simulation
sep_test = jneqsim.pvtsimulation.simulation.SeparatorTest(black_oil.clone())

# Define separator stages (temperature in K, pressure in bara)
# Stage 1: High pressure separator
# Stage 2: Low pressure separator  
# Stage 3: Stock tank
sep_temps = [323.15, 303.15, 288.15]  # 50°C, 30°C, 15°C
sep_pressures = [50.0, 5.0, 1.01325]  # 50 bar, 5 bar, atmospheric

sep_test.setSeparatorConditions(sep_temps, sep_pressures)

# Run simulation
sep_test.runCalc()

# Get results
gor_array = list(sep_test.getGOR())
bo_sep = list(sep_test.getBofactor())

print("=== Separator Test Results ===")
print(f"\nSeparator configuration:")
for i, (t, p) in enumerate(zip(sep_temps, sep_pressures)):
    print(f"  Stage {i+1}: T = {t-273.15:.0f}°C, P = {p:.1f} bara")

if len(gor_array) > 0:
    print(f"\nField GOR: {gor_array[0]:.1f} Sm³/Sm³")
if len(bo_sep) > 0:
    print(f"Oil FVF at separator: {bo_sep[0]:.4f} m³/Sm³")
```

## 8. PVT Regression Framework <a name="regression"></a>

The new PVT Regression Framework allows automatic tuning of EOS parameters to match experimental data.

### Available Regression Parameters

| Parameter | Description | Typical Effect |
|-----------|-------------|----------------|
| `BIP_METHANE_C7PLUS` | Methane/C7+ binary interaction | Saturation pressure |
| `VOLUME_SHIFT_C7PLUS` | Peneloux volume translation | Liquid density |
| `TC_MULTIPLIER_C7PLUS` | Critical temperature adjustment | Phase envelope shape |
| `OMEGA_MULTIPLIER_C7PLUS` | Acentric factor adjustment | Vapor pressure curve |

```python
# Create a base fluid for regression
reg_fluid = jneqsim.thermo.system.SystemSrkEos(373.15, 200.0)

# Gas condensate composition
reg_fluid.addComponent("methane", 0.70)
reg_fluid.addComponent("ethane", 0.08)
reg_fluid.addComponent("propane", 0.05)
reg_fluid.addComponent("n-butane", 0.03)
reg_fluid.addComponent("n-pentane", 0.02)
reg_fluid.addPlusFraction("C7+", 0.12, 0.160, 0.81)

# Characterize
reg_fluid.getCharacterization().setNumberOfPseudoComponents(6)
reg_fluid.getCharacterization().characterisePlusFraction()
reg_fluid.createDatabase(True)
reg_fluid.setMixingRule(2)
reg_fluid.setMultiPhaseCheck(True)

print("Base fluid for regression created")
print(f"Number of components: {reg_fluid.getNumberOfComponents()}")
```

```python
# Create PVT Regression instance
regression = jneqsim.pvtsimulation.regression.PVTRegression(reg_fluid)

# Define "experimental" CCE data (simulated for this example)
# In practice, this would come from laboratory measurements
exp_pressures = [300.0, 250.0, 220.0, 200.0, 180.0, 150.0, 120.0, 100.0]
exp_rel_volumes = [0.965, 0.982, 0.995, 1.000, 1.025, 1.095, 1.210, 1.350]
exp_temperature = 373.15  # 100°C

# Add CCE data to regression
regression.addCCEData(exp_pressures, exp_rel_volumes, exp_temperature)

print("=== Experimental CCE Data ===")
print(f"{'Pressure (bara)':>15} {'Rel. Volume':>15}")
print("-" * 32)
for p, v in zip(exp_pressures, exp_rel_volumes):
    print(f"{p:>15.1f} {v:>15.3f}")
```

```python
# Access RegressionParameter enum
RegressionParameter = jneqsim.pvtsimulation.regression.RegressionParameter

# Add regression parameters with bounds
# BIP between methane and C7+ (primary tuning parameter for saturation pressure)
regression.addRegressionParameter(
    RegressionParameter.BIP_METHANE_C7PLUS, 
    0.0,    # Lower bound
    0.10,   # Upper bound  
    0.03    # Initial guess
)

# Volume shift for density matching
regression.addRegressionParameter(
    RegressionParameter.VOLUME_SHIFT_C7PLUS,
    0.85,   # Lower bound
    1.15,   # Upper bound
    1.0     # Initial guess
)

print("Regression parameters configured:")
print("  - BIP_METHANE_C7PLUS: [0.0, 0.10], initial = 0.03")
print("  - VOLUME_SHIFT_C7PLUS: [0.85, 1.15], initial = 1.0")
```

```python
# Configure regression settings
regression.setMaxIterations(50)
regression.setVerbose(False)  # Set to True for detailed output

# Set experiment weights (optional)
ExperimentType = jneqsim.pvtsimulation.regression.ExperimentType
regression.setExperimentWeight(ExperimentType.CCE, 1.0)

print("Regression settings configured")
print("  Max iterations: 50")
print("  CCE weight: 1.0")
```

```python
# Run the regression
# Note: This uses Levenberg-Marquardt optimization
print("Running PVT regression...")
print("(This may take a moment)")

try:
    result = regression.runRegression()
    
    # Get optimized parameter values
    opt_bip = result.getOptimizedValue(RegressionParameter.BIP_METHANE_C7PLUS)
    opt_vshift = result.getOptimizedValue(RegressionParameter.VOLUME_SHIFT_C7PLUS)
    
    print("\n=== Regression Results ===")
    print(f"Optimized BIP (CH4-C7+): {opt_bip:.6f}")
    print(f"Optimized Volume Shift: {opt_vshift:.6f}")
    print(f"Final Chi-Square: {result.getFinalChiSquare():.6f}")
    
    # Get uncertainty analysis
    uncertainty = result.getUncertainty()
    if uncertainty is not None:
        print(f"\nDegrees of freedom: {uncertainty.getDegreesOfFreedom()}")
        
except Exception as e:
    print(f"Regression encountered an issue: {e}")
    print("This is expected with simplified test data.")
```

## 9. Advanced: Multi-Parameter Tuning <a name="advanced"></a>

For comprehensive EOS tuning, multiple data types can be combined with appropriate weights.

### Recommended Tuning Strategy

| Step | Parameter | Target Data | Goal |
|------|-----------|-------------|------|
| 1 | BIP (CH4-C7+) | CCE saturation pressure | Match Psat |
| 2 | Volume shift | DLE oil density | Match liquid density |
| 3 | Tc multiplier | CVD liquid dropout | Match phase envelope |
| 4 | ω multiplier | Separator GOR | Fine-tune volatility |

```python
# Example: Multi-experiment regression setup
# (Demonstrating API - actual execution depends on data quality)

multi_reg = jneqsim.pvtsimulation.regression.PVTRegression(reg_fluid.clone())

# Add CCE data
multi_reg.addCCEData(
    [300.0, 250.0, 200.0, 150.0, 100.0],
    [0.97, 1.00, 1.08, 1.25, 1.50],
    373.15
)

# Add DLE data
multi_reg.addDLEData(
    [250.0, 200.0, 150.0, 100.0],      # Pressures
    [150.0, 120.0, 85.0, 50.0],        # Rs (Sm3/Sm3)
    [1.45, 1.38, 1.28, 1.18],          # Bo (m3/Sm3)
    [720.0, 740.0, 760.0, 780.0],      # Oil density (kg/m3)
    373.15                              # Temperature (K)
)

# Add multiple regression parameters
multi_reg.addRegressionParameter(RegressionParameter.BIP_METHANE_C7PLUS)
multi_reg.addRegressionParameter(RegressionParameter.VOLUME_SHIFT_C7PLUS)
multi_reg.addRegressionParameter(RegressionParameter.TC_MULTIPLIER_C7PLUS)

# Set weights: prioritize DLE for black oil
multi_reg.setExperimentWeight(ExperimentType.CCE, 1.0)
multi_reg.setExperimentWeight(ExperimentType.DLE, 2.0)  # Higher weight

print("Multi-experiment regression configured:")
print("  - CCE data: 5 points (weight = 1.0)")
print("  - DLE data: 4 points (weight = 2.0)")
print("  - Parameters: BIP, Volume Shift, Tc Multiplier")
```

## 10. Common Fluid Characterization <a name="common-characterization"></a>

When working with multiple reservoir samples (e.g., from different wells or zones), it's often necessary to use **identical pseudo-component definitions** across all fluids. This is required for:

- **Compositional reservoir simulation** - All fluids must share the same component slate
- **Consistent PVT matching** - Multiple samples tuned to common basis
- **Production allocation** - Tracking contributions from different zones

### NeqSim Solution: `PseudoComponentCombiner`

NeqSim provides the `PseudoComponentCombiner` utility class with two key methods:

| Method | Purpose |
|--------|---------|
| `characterizeToReference(source, reference)` | Match source fluid to reference's PC structure |
| `combineReservoirFluids(n, fluids...)` | Combine multiple fluids with common PCs |

**Reference:** Pedersen et al., "Phase Behavior of Petroleum Reservoir Fluids", Chapter 5.5-5.6

```python
# Example: Characterize a source fluid to match a reference fluid's pseudo-components
PseudoComponentCombiner = jneqsim.thermo.characterization.PseudoComponentCombiner

# Create a REFERENCE fluid (defines the "master" PC structure)
reference_fluid = jneqsim.thermo.system.SystemPrEos(298.15, 50.0)
reference_fluid.addComponent("methane", 0.7)
reference_fluid.addComponent("ethane", 0.1)

# Add pseudo-components to reference (these define the "standard" characterization)
reference_fluid.addTBPfraction("C7", 0.10, 0.090, 0.78)
reference_fluid.addTBPfraction("C10", 0.07, 0.140, 0.82)
reference_fluid.addTBPfraction("C15", 0.03, 0.220, 0.86)

print("=== Reference Fluid (defines PC structure) ===")
print(f"Components: {reference_fluid.getNumberOfComponents()}")
```

```python
# Create a SOURCE fluid (different characterization, needs to be matched)
source_fluid = jneqsim.thermo.system.SystemPrEos(298.15, 50.0)
source_fluid.addComponent("methane", 0.65)
source_fluid.addComponent("ethane", 0.12)
source_fluid.addComponent("propane", 0.03)

# Source has different PC structure (4 pseudo-components vs. 3 in reference)
source_fluid.addTBPfraction("S1", 0.08, 0.085, 0.77)
source_fluid.addTBPfraction("S2", 0.05, 0.110, 0.80)
source_fluid.addTBPfraction("S3", 0.04, 0.160, 0.83)
source_fluid.addTBPfraction("S4", 0.03, 0.250, 0.87)

print("=== Source Fluid (to be characterized) ===")
print(f"Components: {source_fluid.getNumberOfComponents()}")
print("Source pseudo-components: S1, S2, S3, S4 (4 PCs)")
print("Target pseudo-components: C7, C10, C15 (3 PCs from reference)")
```

```python
# Characterize source to match reference's PC structure
characterized_fluid = PseudoComponentCombiner.characterizeToReference(source_fluid, reference_fluid)

print("=== Characterized Fluid ===")
print(f"Total components: {characterized_fluid.getNumberOfComponents()}")
print(f"\\nComponent names: {list(characterized_fluid.getComponentNames())}")
print("\\nNote: Source's 4 PCs are now redistributed into reference's 3 PC structure!")
print("The characterized fluid can now be used in the same reservoir model as the reference.")
```

### Combining Multiple Fluids

When you have multiple samples from different wells/zones, you can combine them into a single fluid with a common characterization:

```python
# Combine multiple reservoir fluids into one with common PC structure
# This uses Pedersen's mixing rules (Chapter 5.5)

# Create two different fluids (e.g., from Well A and Well B)
well_a_fluid = jneqsim.thermo.system.SystemPrEos(298.15, 50.0)
well_a_fluid.addComponent("methane", 0.70)
well_a_fluid.addComponent("ethane", 0.10)
well_a_fluid.addTBPfraction("C7", 0.12, 0.095, 0.79)
well_a_fluid.addTBPfraction("C12", 0.08, 0.170, 0.84)

well_b_fluid = jneqsim.thermo.system.SystemPrEos(298.15, 50.0)
well_b_fluid.addComponent("methane", 0.60)
well_b_fluid.addComponent("ethane", 0.15)
well_b_fluid.addTBPfraction("C8", 0.10, 0.110, 0.81)
well_b_fluid.addTBPfraction("C15", 0.10, 0.210, 0.86)
well_b_fluid.addTBPfraction("C20", 0.05, 0.280, 0.88)

# Combine into a single fluid with 4 pseudo-components
combined_fluid = PseudoComponentCombiner.combineReservoirFluids(
    4,  # Target number of pseudo-components
    well_a_fluid, 
    well_b_fluid
)

print("=== Combined Reservoir Fluid ===")
print(f"Total components: {combined_fluid.getNumberOfComponents()}")
print(f"Components: {list(combined_fluid.getComponentNames())}")
print("\\nBoth Well A and Well B fluids now share the same 4-PC structure!")
```

### NEW: BIP Transfer and Characterization Options

NeqSim now supports **transferring binary interaction parameters** from a reference fluid when matching characterizations. This ensures consistent phase behavior across multiple fluid samples.

Available options in `CharacterizationOptions`:

| Option | Description |
|--------|-------------|
| `transferBinaryInteractionParameters` | Copy BIPs from reference fluid |
| `normalizeComposition` | Normalize mole fractions to sum to 1.0 |
| `generateValidationReport` | Log before/after comparison report |
| `namingScheme` | REFERENCE, SEQUENTIAL, or CARBON_NUMBER |

```python
# Example: Transfer BIPs when matching characterization
CharacterizationOptions = jneqsim.thermo.characterization.CharacterizationOptions

# Create reference fluid with custom BIPs
ref_with_bips = jneqsim.thermo.system.SystemPrEos(298.15, 50.0)
ref_with_bips.addComponent("methane", 0.70)
ref_with_bips.addComponent("ethane", 0.10)
ref_with_bips.addTBPfraction("C7", 0.10, 0.090, 0.78)
ref_with_bips.addTBPfraction("C10", 0.07, 0.140, 0.82)
ref_with_bips.addTBPfraction("C15", 0.03, 0.220, 0.86)
ref_with_bips.createDatabase(True)
ref_with_bips.setMixingRule(2)

# Set custom binary interaction parameters (tuned to lab data)
ref_with_bips.setBinaryInteractionParameter("methane", "C7_PC", 0.05)
ref_with_bips.setBinaryInteractionParameter("methane", "C10_PC", 0.06)
ref_with_bips.setBinaryInteractionParameter("methane", "C15_PC", 0.07)

print("Reference fluid has custom BIPs for methane-PC interactions")
```

```python
# Characterize another fluid WITH BIP transfer
new_sample = jneqsim.thermo.system.SystemPrEos(298.15, 50.0)
new_sample.addComponent("methane", 0.65)
new_sample.addComponent("ethane", 0.12)
new_sample.addTBPfraction("S1", 0.08, 0.085, 0.77)
new_sample.addTBPfraction("S2", 0.15, 0.180, 0.84)
new_sample.createDatabase(True)
new_sample.setMixingRule(2)

# Create options with BIP transfer enabled
options = CharacterizationOptions.builder() \
    .transferBinaryInteractionParameters(True) \
    .generateValidationReport(True) \
    .build()

# Characterize with options - BIPs will be copied from reference
matched_with_bips = PseudoComponentCombiner.characterizeToReference(
    new_sample, 
    ref_with_bips, 
    options
)

print("=== Characterized with BIP Transfer ===")
print(f"Components: {list(matched_with_bips.getComponentNames())}")
print("BIPs from reference fluid have been transferred!")
```

```python
# Alternative: Use fluent API
# The Characterise class now has characterizeToReference() method

fluent_matched = new_sample.getCharacterization() \
    .characterizeToReference(ref_with_bips, options)

print("=== Fluent API Characterization ===")
print(f"Result components: {fluent_matched.getNumberOfComponents()}")
```

```python
# Generate and display regression summary
print("="*60)
print("PVT REGRESSION FRAMEWORK - SUMMARY")
print("="*60)
print("""
The NeqSim PVT Regression Framework provides:

✓ Multi-objective optimization (CCE, CVD, DLE, Separator)
✓ Levenberg-Marquardt algorithm for robust convergence
✓ 11 tunable parameters (BIPs, volume shifts, critical props)
✓ Configurable experiment weights
✓ Uncertainty quantification (confidence intervals)

Key Classes:
  - PVTRegression: Main framework class
  - RegressionParameter: Enum of tunable parameters
  - ExperimentType: Enum of experiment types
  - RegressionResult: Container for results + uncertainty

Workflow:
  1. Create base fluid with C7+ characterization
  2. Instantiate PVTRegression with fluid
  3. Add experimental data (CCE, DLE, CVD, Separator)
  4. Configure regression parameters with bounds
  5. Set experiment weights
  6. Run regression
  7. Extract tuned fluid and uncertainty analysis
""")
print("="*60)
```

---

## References

1. Whitson, C.H. (1983). "Characterizing Hydrocarbon Plus Fractions." SPE Journal, 23(4), 683-694.

2. Pedersen, K.S., Christensen, P.L. (2007). "Phase Behavior of Petroleum Reservoir Fluids." CRC Press.

3. Whitson, C.H., Brulé, M.R. (2000). "Phase Behavior." SPE Monograph Series, Vol. 20.

4. NeqSim Documentation: [docs/fluid_characterization_mathematics.md](../fluid_characterization_mathematics.md)

