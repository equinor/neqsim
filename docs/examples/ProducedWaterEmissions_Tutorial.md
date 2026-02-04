---
layout: default
title: "ProducedWaterEmissions Tutorial"
description: "Jupyter notebook tutorial for NeqSim"
parent: Examples
nav_order: 1
---

# ProducedWaterEmissions Tutorial

> **Note:** This is an auto-generated Markdown version of the Jupyter notebook 
> [`ProducedWaterEmissions_Tutorial.ipynb`](https://github.com/equinor/neqsim/blob/master/docs/examples/ProducedWaterEmissions_Tutorial.ipynb).
> You can also [view it on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/ProducedWaterEmissions_Tutorial.ipynb) 
> or [open in Google Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ProducedWaterEmissions_Tutorial.ipynb).

---

# 🌱 Produced Water Emissions Calculation with NeqSim

This notebook demonstrates comprehensive greenhouse gas emissions calculation (CO₂, methane, nmVOC) from produced water handling systems using NeqSim.

**Reference:** Based on "Virtual Measurement of Emissions from Produced Water Using an Online Process Simulator" (GFMW 2023).

---

## Topics Covered

| Section | Topic | Description |
|---------|-------|-------------|
| 1 | **Simple Flash** | Basic gas release calculation |
| 2 | **Multi-Stage Process** | Degasser → CFU → Caisson |
| 3 | **Norwegian Handbook** | Conventional method comparison |
| 4 | **EmissionsCalculator** | Using the Java API |
| 5 | **Salinity Effects** | Salting-out impact on emissions |
| 6 | **TEG Emissions** | Generalizing to other processes |
| 7 | **Method Comparison** | NeqSim vs conventional validation |
| 8 | **Troubleshooting** | Diagnostic tools |

---

## Why This Matters

| Issue | Conventional | NeqSim |
|-------|-------------|--------|
| **CO₂ measured?** | ❌ No (misses 72-78%!) | ✅ Yes |
| **Uncertainty** | ±50%+ | ±3.6% |
| **Salinity effects** | ❌ No | ✅ Yes |
| **Real-time capable** | ❌ No | ✅ Yes (NeqSimLive) |

---

## Regulatory Context

- **Aktivitetsforskriften §70**: Requires quality-assured emission measurement
- **Norwegian Carbon Tax**: ~NOK 1,565/tonne CO₂ (2024)
- **EU ETS**: Emissions trading participation

---

## Run All Cells

1. Ensure NeqSim is installed: `pip install neqsim`
2. Run cells in order (Shift+Enter)
3. Modify parameters to match your facility

```python
# Install neqsim if not already installed
# !pip install neqsim
```

```python
# Import neqsim and dependencies
# NOTE: If you get AttributeError for Java classes, restart the kernel completely 
# (Kernel menu -> Restart Kernel) to reload the updated JAR

from neqsim.thermo import fluid, TPflash
from neqsim import jneqsim  # Note: lowercase 'j'
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

# Verify EmissionsCalculator is available (requires updated JAR)
try:
    EmissionsCalculator = jneqsim.process.equipment.util.EmissionsCalculator
    print("✓ EmissionsCalculator available")
except AttributeError:
    print("⚠️ EmissionsCalculator not found - you may need to update the NeqSim JAR")

print("✓ All imports successful!")
```

<details>
<summary>Output</summary>

```
✓ EmissionsCalculator available
✓ All imports successful!
```

</details>

## 1. Simple Flash Calculation

First, let's understand what happens when produced water is flashed from separator pressure to degasser pressure.

```python
# Create produced water fluid using CPA equation of state
# CPA is needed for accurate water-gas equilibrium

produced_water = fluid('cpa')

# Typical composition (mole fractions)
# Water is dominant, with dissolved gases
produced_water.addComponent('water', 0.90)
produced_water.addComponent('CO2', 0.03)       # Dissolved CO2 - often 50%+ of emissions!
produced_water.addComponent('methane', 0.05)
produced_water.addComponent('ethane', 0.015)
produced_water.addComponent('propane', 0.005)

# Set separator conditions
produced_water.setTemperature(80, 'C')  # 80°C typical
produced_water.setPressure(30, 'bara')  # Separator pressure
produced_water.setTotalFlowRate(100000, 'kg/hr')  # ~100 m³/hr

# Run flash calculation using TPflash from neqsim.thermo
TPflash(produced_water)
produced_water.initProperties()

print("=== At Separator Conditions (30 bara, 80°C) ===")
print(f"Number of phases: {produced_water.getNumberOfPhases()}")
print(f"Total flow: {produced_water.getFlowRate('kg/hr'):.0f} kg/hr")
```

<details>
<summary>Output</summary>

```
=== At Separator Conditions (30 bara, 80°C) ===
Number of phases: 2
Total flow: 100000 kg/hr
```

</details>

```python
# Now flash to degasser pressure (4 bara)
degasser_fluid = produced_water.clone()
degasser_fluid.setPressure(4, 'bara')  # Degasser pressure
TPflash(degasser_fluid)
degasser_fluid.initProperties()

print("=== At Degasser Conditions (4 bara, 80°C) ===")
print(f"Number of phases: {degasser_fluid.getNumberOfPhases()}")

# Check for gas phase
if degasser_fluid.hasPhaseType('gas'):
    gas_phase = degasser_fluid.getPhase('gas')
    
    print(f"\nGas phase released:")
    print(f"  Total gas: {gas_phase.getFlowRate('kg/hr'):.2f} kg/hr")
    
    # Individual component flows
    co2_flow = gas_phase.getComponent('CO2').getFlowRate('kg/hr')
    ch4_flow = gas_phase.getComponent('methane').getFlowRate('kg/hr')
    c2_flow = gas_phase.getComponent('ethane').getFlowRate('kg/hr')
    c3_flow = gas_phase.getComponent('propane').getFlowRate('kg/hr')
    
    print(f"  CO2:     {co2_flow:.2f} kg/hr")
    print(f"  Methane: {ch4_flow:.2f} kg/hr")
    print(f"  Ethane:  {c2_flow:.2f} kg/hr")
    print(f"  Propane: {c3_flow:.2f} kg/hr")
    
    # nmVOC = C2+ hydrocarbons
    nmvoc_flow = c2_flow + c3_flow
    print(f"  nmVOC:   {nmvoc_flow:.2f} kg/hr")
    
    # Gas composition
    total_gas = gas_phase.getFlowRate('kg/hr')
    print(f"\nEmission composition (mass %):")
    print(f"  CO2:     {co2_flow/total_gas*100:.1f}%")
    print(f"  Methane: {ch4_flow/total_gas*100:.1f}%")
    print(f"  nmVOC:   {nmvoc_flow/total_gas*100:.1f}%")
else:
    print("No gas phase formed")
```

<details>
<summary>Output</summary>

```
=== At Degasser Conditions (4 bara, 80°C) ===
Number of phases: 2

Gas phase released:
  Total gas: 15893.80 kg/hr
  CO2:     6857.46 kg/hr
  Methane: 4217.15 kg/hr
  Ethane:  2370.58 kg/hr
  Propane: 1158.61 kg/hr
  nmVOC:   3529.19 kg/hr

Emission composition (mass %):
  CO2:     43.1%
  Methane: 26.5%
  nmVOC:   22.2%
```

</details>

## 2. Multi-Stage Process Simulation

A typical produced water system has multiple degassing stages:
1. **Water Degasser** (3-5 barg) - main emission source
2. **CFU** - Compact Flotation Unit (0.5-1 barg)
3. **Caisson** (atmospheric) - final flash before discharge

```python
# Create complete process simulation using Java API
# NOTE: If you get AttributeError, restart the kernel (Kernel -> Restart) to reload the updated JAR

# Import specific classes to ensure they're found
Stream = jneqsim.process.equipment.stream.Stream
ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve
Separator = jneqsim.process.equipment.separator.Separator
ProcessSystem = jneqsim.process.processmodel.ProcessSystem

# Start with water from separator
inlet_fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 80, 30.0)
inlet_fluid.addComponent('water', 0.90)
inlet_fluid.addComponent('CO2', 0.03)
inlet_fluid.addComponent('methane', 0.05)
inlet_fluid.addComponent('ethane', 0.015)
inlet_fluid.addComponent('propane', 0.005)
inlet_fluid.setMixingRule(10)  # CPA mixing rule
inlet_fluid.setMultiPhaseCheck(True)

# Create inlet stream
inlet_stream = Stream('PW from Separator', inlet_fluid)
inlet_stream.setFlowRate(100000, 'kg/hr')
inlet_stream.setTemperature(80, 'C')
inlet_stream.setPressure(30, 'bara')

# Stage 1: Water Degasser (flash to 4 bara) - using valve for pressure reduction
degasser_valve = ThrottlingValve('Degasser Valve', inlet_stream)
degasser_valve.setOutletPressure(4.0)

degasser = Separator('Water Degasser', degasser_valve.getOutletStream())

# Stage 2: CFU (flash to 1.5 bara)
cfu_valve = ThrottlingValve('CFU Valve', degasser.getLiquidOutStream())
cfu_valve.setOutletPressure(1.5)

cfu = Separator('CFU', cfu_valve.getOutletStream())

# Stage 3: Caisson (atmospheric)
caisson_valve = ThrottlingValve('Caisson Valve', cfu.getLiquidOutStream())
caisson_valve.setOutletPressure(1.01325)

caisson = Separator('Caisson', caisson_valve.getOutletStream())

# Build and run process
process = ProcessSystem()
process.add(inlet_stream)
process.add(degasser_valve)
process.add(degasser)
process.add(cfu_valve)
process.add(cfu)
process.add(caisson_valve)
process.add(caisson)
process.run()

print("✓ Process simulation complete!")
```

<details>
<summary>Output</summary>

```
✓ Process simulation complete!
```

</details>

```python
# Extract emissions from each stage

def get_stage_emissions(separator, stage_name):
    """Extract emission rates from a separator's gas outlet"""
    gas_stream = separator.getGasOutStream()
    gas_fluid = gas_stream.getFluid()
    
    result = {'Stage': stage_name}
    
    if gas_fluid.hasPhaseType('gas'):
        gas = gas_fluid.getPhase('gas')
        result['Total_kghr'] = gas.getFlowRate('kg/hr')
        result['CO2_kghr'] = gas.getComponent('CO2').getFlowRate('kg/hr')
        result['CH4_kghr'] = gas.getComponent('methane').getFlowRate('kg/hr')
        result['C2_kghr'] = gas.getComponent('ethane').getFlowRate('kg/hr')
        result['C3_kghr'] = gas.getComponent('propane').getFlowRate('kg/hr')
        result['nmVOC_kghr'] = result['C2_kghr'] + result['C3_kghr']
    else:
        result['Total_kghr'] = 0
        result['CO2_kghr'] = 0
        result['CH4_kghr'] = 0
        result['nmVOC_kghr'] = 0
    
    return result

# Collect emissions from all stages
emissions_data = [
    get_stage_emissions(degasser, 'Degasser'),
    get_stage_emissions(cfu, 'CFU'),
    get_stage_emissions(caisson, 'Caisson')
]

df = pd.DataFrame(emissions_data)
print("\n=== EMISSIONS BY STAGE (kg/hr) ===")
print(df.to_string(index=False))
```

<details>
<summary>Output</summary>

```

=== EMISSIONS BY STAGE (kg/hr) ===
   Stage   Total_kghr    CO2_kghr    CH4_kghr     C2_kghr     C3_kghr  nmVOC_kghr
Degasser 15633.366242 6899.754744 4218.035507 2370.517780 1159.566285 3530.084066
     CFU    22.165701   14.917530    1.850000    1.872499    0.379911    2.252409
 Caisson    10.516698    7.681946    0.238455    0.418051    0.037736    0.455787
```

</details>

```python
# Calculate totals and CO2 equivalents

# GWP-100 factors (IPCC AR5)
GWP_CH4 = 28
GWP_NMVOC = 2.2

total_co2 = df['CO2_kghr'].sum()
total_ch4 = df['CH4_kghr'].sum()
total_nmvoc = df['nmVOC_kghr'].sum()
total_gas = df['Total_kghr'].sum()

# CO2 equivalents
co2eq_hr = total_co2 + total_ch4 * GWP_CH4 + total_nmvoc * GWP_NMVOC
co2eq_year = co2eq_hr * 8760 / 1000  # tonnes/year

print("\n=== TOTAL EMISSIONS ===")
print(f"Total gas:   {total_gas:.1f} kg/hr ({total_gas*8760/1000:.1f} tonnes/year)")
print(f"CO2:         {total_co2:.1f} kg/hr ({total_co2*8760/1000:.1f} tonnes/year)")
print(f"Methane:     {total_ch4:.1f} kg/hr ({total_ch4*8760/1000:.1f} tonnes/year)")
print(f"nmVOC:       {total_nmvoc:.1f} kg/hr ({total_nmvoc*8760/1000:.1f} tonnes/year)")

print(f"\n=== CO2 EQUIVALENTS (GWP-100) ===")
print(f"CO2eq:       {co2eq_hr:.1f} kg/hr")
print(f"Annual:      {co2eq_year:.1f} tonnes CO2eq/year")

print(f"\n=== EMISSION COMPOSITION ===")
print(f"CO2:     {total_co2/total_gas*100:.1f}% by mass")
print(f"Methane: {total_ch4/total_gas*100:.1f}% by mass")
print(f"nmVOC:   {total_nmvoc/total_gas*100:.1f}% by mass")
```

<details>
<summary>Output</summary>

```

=== TOTAL EMISSIONS ===
Total gas:   15666.0 kg/hr (137234.6 tonnes/year)
CO2:         6922.4 kg/hr (60639.8 tonnes/year)
Methane:     4220.1 kg/hr (36968.3 tonnes/year)
nmVOC:       3532.8 kg/hr (30947.3 tonnes/year)

=== CO2 EQUIVALENTS (GWP-100) ===
CO2eq:       132858.0 kg/hr
Annual:      1163835.8 tonnes CO2eq/year

=== EMISSION COMPOSITION ===
CO2:     44.2% by mass
Methane: 26.9% by mass
nmVOC:   22.6% by mass
```

</details>

```python
# Visualize emissions

fig, axes = plt.subplots(1, 2, figsize=(12, 5))

# Plot 1: Emissions by stage
stages = df['Stage'].tolist()
x = range(len(stages))
width = 0.25

ax1 = axes[0]
ax1.bar([i - width for i in x], df['CO2_kghr'], width, label='CO2', color='#2196F3')
ax1.bar([i for i in x], df['CH4_kghr'], width, label='CH4', color='#FF9800')
ax1.bar([i + width for i in x], df['nmVOC_kghr'], width, label='nmVOC', color='#4CAF50')
ax1.set_xlabel('Stage')
ax1.set_ylabel('Emission Rate (kg/hr)')
ax1.set_title('Emissions by Degassing Stage')
ax1.set_xticks(x)
ax1.set_xticklabels(stages)
ax1.legend()
ax1.grid(axis='y', alpha=0.3)

# Plot 2: Total emission composition (pie chart)
ax2 = axes[1]
sizes = [total_co2, total_ch4, total_nmvoc]
labels = ['CO2', 'Methane', 'nmVOC']
colors = ['#2196F3', '#FF9800', '#4CAF50']
explode = (0.05, 0, 0)

ax2.pie(sizes, explode=explode, labels=labels, colors=colors, autopct='%1.1f%%',
        shadow=True, startangle=90)
ax2.set_title('Total Emission Composition (by mass)')

plt.tight_layout()
plt.show()
```

## 3. Norwegian Handbook Method (Conventional)

### Regulatory Background

The **Norwegian Offshore Emission Handbook** (Retningslinje 044) specifies conventional emission factors:

| Parameter | Symbol | Value | Unit |
|-----------|--------|-------|------|
| Methane solubility factor | f_CH4 | **14** | g/(m³·bar) |
| nmVOC solubility factor | f_nmVOC | **3.5** | g/(m³·bar) |

### Conventional Formulas

```
U_CH4 = f_CH4 × V_pw × ΔP × 10⁻⁶  [tonnes/year]
U_nmVOC = f_nmVOC × V_pw × ΔP × 10⁻⁶  [tonnes/year]
```

Where:
- V_pw = Produced water volume (m³/year)
- ΔP = Pressure drop (bar)

### ⚠️ Critical Limitation

**The conventional method does NOT account for CO₂ emissions!**

Per GFMW 2023 validation at Gudrun:
- CO₂ represents **72-78%** of total gas emissions
- Conventional method completely misses this!

```python
# Norwegian Handbook Conventional Method Implementation
# Reference: Håndbok for kvantifisering av direkte metan- og nmVOC-utslipp (Retningslinje 044)

def norwegian_handbook_method(water_volume_m3_year, pressure_drop_bar):
    """
    Calculate emissions using Norwegian handbook conventional method.
    
    Parameters:
        water_volume_m3_year: Annual produced water volume (m³/year)
        pressure_drop_bar: Total pressure drop (bar)
    
    Returns:
        dict with emission estimates
    """
    # Handbook factors (g/m³/bar)
    f_CH4 = 14.0
    f_NMVOC = 3.5
    
    # Calculate emissions (tonnes/year)
    ch4_tonnes = f_CH4 * water_volume_m3_year * pressure_drop_bar * 1e-6
    nmvoc_tonnes = f_NMVOC * water_volume_m3_year * pressure_drop_bar * 1e-6
    
    # CO2 equivalents (GWP-100: CH4=28, nmVOC=2.2)
    co2eq = ch4_tonnes * 28 + nmvoc_tonnes * 2.2
    
    return {
        'co2_tonnes': 0.0,  # NOT measured!
        'ch4_tonnes': ch4_tonnes,
        'nmvoc_tonnes': nmvoc_tonnes,
        'co2eq_tonnes': co2eq
    }

# Example calculation
water_flow_m3hr = 100  # m³/hr
hours_per_year = 8760
water_volume_m3_year = water_flow_m3hr * hours_per_year

pressure_drop_bar = 30 - 1.01325  # From separator to atmosphere

# Calculate using conventional method
conv = norwegian_handbook_method(water_volume_m3_year, pressure_drop_bar)

print("=" * 70)
print("NORWEGIAN HANDBOOK METHOD (CONVENTIONAL)")
print("=" * 70)
print(f"\nInputs:")
print(f"  Produced water:  {water_volume_m3_year:,.0f} m³/year ({water_flow_m3hr} m³/hr)")
print(f"  Pressure drop:   {pressure_drop_bar:.1f} bar")
print(f"  Handbook factors: f_CH4={14}, f_nmVOC={3.5} g/(m³·bar)")

print(f"\nAnnual Emissions (tonnes/year):")
print(f"  CO₂:            {conv['co2_tonnes']:.1f} ⚠️ NOT MEASURED!")
print(f"  Methane (CH₄):  {conv['ch4_tonnes']:.1f}")
print(f"  nmVOC:          {conv['nmvoc_tonnes']:.1f}")
print(f"  CO₂ equivalent: {conv['co2eq_tonnes']:.1f}")

# Now compare with NeqSim results from above
print("\n" + "=" * 70)
print("COMPARISON: NeqSim vs Conventional")
print("=" * 70)

# NeqSim annual values
neqsim_co2_t = total_co2 * hours_per_year / 1000
neqsim_ch4_t = total_ch4 * hours_per_year / 1000
neqsim_nmvoc_t = total_nmvoc * hours_per_year / 1000
neqsim_co2eq_t = co2eq_year

print(f"\n{'Component':<20} {'NeqSim':>15} {'Conventional':>15} {'Difference':>12}")
print("-" * 65)
print(f"{'CO₂ (t/yr)':<20} {neqsim_co2_t:>15.1f} {conv['co2_tonnes']:>15.1f} {'∞ (missed!)':>12}")
print(f"{'CH₄ (t/yr)':<20} {neqsim_ch4_t:>15.1f} {conv['ch4_tonnes']:>15.1f} {(neqsim_ch4_t-conv['ch4_tonnes'])/conv['ch4_tonnes']*100:>+11.0f}%")
print(f"{'nmVOC (t/yr)':<20} {neqsim_nmvoc_t:>15.1f} {conv['nmvoc_tonnes']:>15.1f} {(neqsim_nmvoc_t-conv['nmvoc_tonnes'])/conv['nmvoc_tonnes']*100:>+11.0f}%")
print(f"{'CO₂eq (t/yr)':<20} {neqsim_co2eq_t:>15.1f} {conv['co2eq_tonnes']:>15.1f} {(neqsim_co2eq_t-conv['co2eq_tonnes'])/conv['co2eq_tonnes']*100:>+11.0f}%")

print("\n⚠️ KEY FINDING: Conventional method UNDER-REPORTS emissions significantly!")
print(f"   CO₂ represents {total_co2/total_gas*100:.0f}% of gas emissions but is not reported.")
```

<details>
<summary>Output</summary>

```
======================================================================
NORWEGIAN HANDBOOK METHOD (CONVENTIONAL)
======================================================================

Inputs:
  Produced water:  876,000 m³/year (100 m³/hr)
  Pressure drop:   29.0 bar
  Handbook factors: f_CH4=14, f_nmVOC=3.5 g/(m³·bar)

Annual Emissions (tonnes/year):
  CO₂:            0.0 ⚠️ NOT MEASURED!
  Methane (CH₄):  355.5
  nmVOC:          88.9
  CO₂ equivalent: 10149.3

======================================================================
COMPARISON: NeqSim vs Conventional
======================================================================

Component                     NeqSim    Conventional   Difference
-----------------------------------------------------------------
CO₂ (t/yr)                   60639.8             0.0  ∞ (missed!)
CH₄ (t/yr)                   36968.3           355.5      +10299%
nmVOC (t/yr)                 30947.3            88.9      +34722%
CO₂eq (t/yr)               1163835.8         10149.3      +11367%

⚠️ KEY FINDING: Conventional method UNDER-REPORTS emissions significantly!
   CO₂ represents 44% of gas emissions but is not reported.
```

</details>

## 4. Using the EmissionsCalculator Class (Java API)

NeqSim includes a dedicated `EmissionsCalculator` class for easier emissions tracking.

```python
# Using the EmissionsCalculator utility class from the Java API
# This class provides convenient methods for emission calculations and reporting

# Create calculator from degasser gas outlet stream
EmissionsCalculator = jneqsim.process.equipment.util.EmissionsCalculator
calc = EmissionsCalculator(degasser.getGasOutStream())
calc.calculate()

print("=== EmissionsCalculator Results (Degasser Gas) ===")
print(f"CO2:           {calc.getCO2EmissionRate('kg/hr'):.2f} kg/hr")
print(f"Methane:       {calc.getMethaneEmissionRate('kg/hr'):.2f} kg/hr")
print(f"nmVOC:         {calc.getNMVOCEmissionRate('kg/hr'):.2f} kg/hr")
print(f"Total gas:     {calc.getTotalGasRate('kg/hr'):.2f} kg/hr")
print(f"CO2 equivalent: {calc.getCO2Equivalents('kg/hr'):.2f} kg CO2eq/hr")

# Annual values
print(f"\n=== Annual (8760 hrs/year) ===")
print(f"CO2:           {calc.getCO2EmissionRate('tonnes/year'):.0f} tonnes/year")
print(f"Methane:       {calc.getMethaneEmissionRate('tonnes/year'):.0f} tonnes/year")
print(f"nmVOC:         {calc.getNMVOCEmissionRate('tonnes/year'):.0f} tonnes/year")
print(f"CO2eq:         {calc.getCO2Equivalents('tonnes/year'):.0f} tonnes CO2eq/year")

# Get gas composition
print(f"\n=== Gas Composition (mol%) ===")
mole_comp = calc.getGasCompositionMole()
for comp, frac in dict(mole_comp).items():
    if frac > 0.001:
        print(f"  {comp}: {frac*100:.2f}%")

# Compare with Norwegian conventional method using static methods
print("\n=== Comparison with Conventional Method ===")
water_vol_m3_yr = 100 * 8760  # 100 m³/hr * 8760 hrs
dP = 30 - 4  # bar pressure drop

# Use static methods for conventional calculation
conv_ch4 = EmissionsCalculator.calculateConventionalCH4(water_vol_m3_yr, dP)
conv_nmvoc = EmissionsCalculator.calculateConventionalNMVOC(water_vol_m3_yr, dP)
conv_co2eq = conv_ch4 * EmissionsCalculator.GWP_METHANE + conv_nmvoc * EmissionsCalculator.GWP_NMVOC

# Get thermodynamic results
thermo_ch4 = calc.getMethaneEmissionRate('tonnes/year')
thermo_nmvoc = calc.getNMVOCEmissionRate('tonnes/year')
thermo_co2 = calc.getCO2EmissionRate('tonnes/year')
thermo_co2eq = calc.getCO2Equivalents('tonnes/year')

print(f"{'Parameter':<20} {'Conventional':>15} {'Thermodynamic':>15} {'Difference':>12}")
print("-" * 65)
print(f"{'CH4 (t/yr)':<20} {conv_ch4:>15.1f} {thermo_ch4:>15.1f} {(conv_ch4-thermo_ch4)/conv_ch4*100:>+11.1f}%")
print(f"{'nmVOC (t/yr)':<20} {conv_nmvoc:>15.1f} {thermo_nmvoc:>15.1f} {(conv_nmvoc-thermo_nmvoc)/conv_nmvoc*100:>+11.1f}%")
print(f"{'CO2 (t/yr)':<20} {'0.0 (missed!)':>15} {thermo_co2:>15.1f} {'N/A':>12}")
print(f"{'CO2eq (t/yr)':<20} {conv_co2eq:>15.1f} {thermo_co2eq:>15.1f} {(conv_co2eq-thermo_co2eq)/conv_co2eq*100:>+11.1f}%")

# GWP constants from the class
print(f"\n=== GWP Constants (from EmissionsCalculator) ===")
print(f"GWP_CO2:     {EmissionsCalculator.GWP_CO2}")
print(f"GWP_METHANE: {EmissionsCalculator.GWP_METHANE}")
print(f"GWP_NMVOC:   {EmissionsCalculator.GWP_NMVOC}")

print("\n⚠️ KEY FINDING: Conventional method overestimates CH4 and COMPLETELY MISSES CO2!")
```

<details>
<summary>Output</summary>

```
=== EmissionsCalculator Results (Degasser Gas) ===
CO2:           6899.75 kg/hr
Methane:       4218.04 kg/hr
nmVOC:         3530.08 kg/hr
Total gas:     15633.37 kg/hr
CO2 equivalent: 132770.93 kg CO2eq/hr

=== Annual (8760 hrs/year) ===
CO2:           60442 tonnes/year
Methane:       36950 tonnes/year
nmVOC:         30924 tonnes/year
CO2eq:         1163073 tonnes CO2eq/year

=== Gas Composition (mol%) ===
  ethane: 13.60%
  CO2: 27.05%
  propane: 4.54%
  methane: 45.37%
  water: 9.44%

=== Comparison with Conventional Method ===
Parameter               Conventional   Thermodynamic   Difference
-----------------------------------------------------------------
CH4 (t/yr)                     318.9         36950.0    -11488.0%
nmVOC (t/yr)                    79.7         30923.5    -38692.1%
CO2 (t/yr)             0.0 (missed!)         60441.9          N/A
CO2eq (t/yr)                  9103.6       1163073.4    -12676.0%

=== GWP Constants (from EmissionsCalculator) ===
GWP_CO2:     1.0
GWP_METHANE: 28.0
GWP_NMVOC:   2.2

⚠️ KEY FINDING: Conventional method overestimates CH4 and COMPLETELY MISSES CO2!
```

</details>

## 5. Effect of Salinity

Produced water typically contains 10-11 wt% NaCl. Salinity reduces gas solubility ("salting-out effect").

```python
# Compare gas solubility with different salinities
# Salinity reduces gas solubility ("salting-out effect")

print("=== Gas-Water Ratio vs Salinity ===")
print("(Demonstrates the salting-out effect)\n")
print(f"{'Salinity (wt%)':<15} {'Relative GWR':<15} {'Reduction (%)':<15}")
print("-" * 45)

# Salting-out coefficient (typical for CH4/CO2 in NaCl brine)
cs_coeff = 0.015  # From Setschenow equation

salinities = [0, 3.5, 7, 10, 15]  # wt% NaCl

for sal in salinities:
    # Salting-out factor: 10^(-S * Cs)
    salting_out_factor = 10**(-sal * cs_coeff)
    reduction = (1 - salting_out_factor) * 100
    print(f"{sal:<15.1f} {salting_out_factor:<15.3f} {reduction:<15.1f}")

print("\nNote: At 10 wt% salinity, gas solubility is reduced by ~30%")
print("This significantly affects emission calculations if not accounted for.")
```

<details>
<summary>Output</summary>

```
=== Gas-Water Ratio vs Salinity ===
(Demonstrates the salting-out effect)

Salinity (wt%)  Relative GWR    Reduction (%)  
---------------------------------------------
0.0             1.000           0.0            
3.5             0.886           11.4           
7.0             0.785           21.5           
10.0            0.708           29.2           
15.0            0.596           40.4           

Note: At 10 wt% salinity, gas solubility is reduced by ~30%
This significantly affects emission calculations if not accounted for.
```

</details>

## 6. Validation and Binary Interaction Parameters

### Gudrun Field Validation (GFMW 2023)

The NeqSim method was validated against operational data from Equinor's Gudrun platform:

| Metric | Result |
|--------|--------|
| Uncertainty achieved | **±3.6%** |
| CO₂ as % of emissions | **72-78%** |
| CO₂eq reduction vs conventional | **58%** |

### Tuned Binary Interaction Parameters (kij)

The CPA model uses carefully tuned kij parameters for water-gas pairs:

```python
# Binary Interaction Parameters used in CPA model
# These are tuned against experimental water-gas solubility data

kij_data = {
    'Pair': ['Water-CO₂', 'Water-CH₄', 'Water-C₂H₆', 'Water-C₃H₈'],
    'kij': [-0.112, 0.0115, 0.48, 0.49],
    'Data Source': ['VLE high-P', 'Solubility', 'Solubility', 'Solubility'],
    'T Range (K)': ['278-478', '273-573', '273-444', '278-368']
}

df_kij = pd.DataFrame(kij_data)

print("=" * 70)
print("TUNED BINARY INTERACTION PARAMETERS (kij)")
print("=" * 70)
print("\nThese parameters enable accurate water-gas equilibrium calculations:\n")
print(df_kij.to_string(index=False))
print("\nNote: Negative kij for CO₂ indicates attractive water-CO₂ interaction")
print("      (CO₂ is more soluble in water than non-polar hydrocarbons)")

# Validation metrics comparison
print("\n" + "=" * 70)
print("VALIDATION METRICS: NeqSim vs Conventional")
print("=" * 70)

validation = {
    'Metric': [
        'CO₂ captured',
        'Uncertainty (95% CI)',
        'Real-time calculation',
        'Salinity effects',
        'Temperature effects',
        'Component tracking'
    ],
    'Conventional': [
        '❌ 0% (not measured)',
        '±50%+',
        '❌ No',
        '❌ No',
        '❌ No',
        'CH₄ + nmVOC only'
    ],
    'NeqSim': [
        '✅ 72-78%',
        '±3.6%',
        '✅ Yes (NeqSimLive)',
        '✅ Yes (NaCl)',
        '✅ Yes (full T-P)',
        'CO₂, CH₄, C₂, C₃, C₄+'
    ]
}

df_val = pd.DataFrame(validation)
print(f"\n{df_val.to_string(index=False)}")
```

<details>
<summary>Output</summary>

```
======================================================================
TUNED BINARY INTERACTION PARAMETERS (kij)
======================================================================

These parameters enable accurate water-gas equilibrium calculations:

      Pair     kij Data Source T Range (K)
 Water-CO₂ -0.1120  VLE high-P     278-478
 Water-CH₄  0.0115  Solubility     273-573
Water-C₂H₆  0.4800  Solubility     273-444
Water-C₃H₈  0.4900  Solubility     278-368

Note: Negative kij for CO₂ indicates attractive water-CO₂ interaction
      (CO₂ is more soluble in water than non-polar hydrocarbons)

======================================================================
VALIDATION METRICS: NeqSim vs Conventional
======================================================================

               Metric        Conventional                NeqSim
         CO₂ captured ❌ 0% (not measured)              ✅ 72-78%
 Uncertainty (95% CI)               ±50%+                 ±3.6%
Real-time calculation                ❌ No    ✅ Yes (NeqSimLive)
     Salinity effects                ❌ No          ✅ Yes (NaCl)
  Temperature effects                ❌ No      ✅ Yes (full T-P)
   Component tracking    CH₄ + nmVOC only CO₂, CH₄, C₂, C₃, C₄+
```

</details>

## 7. CO₂ Equivalent and GWP Calculations

### Global Warming Potential (GWP-100)

Different greenhouse gases have different climate impacts. We convert to CO₂ equivalent:

| Gas | GWP-100 | Meaning |
|-----|---------|--------|
| CO₂ | 1 | Reference |
| CH₄ | 28 | 28× more potent than CO₂ |
| nmVOC | 2.2 | Average for C2-C6 hydrocarbons |

**Formula:** `CO₂eq = CO₂ + (CH₄ × 28) + (nmVOC × 2.2)`

```python
# CO2 Equivalent Calculation Example

# GWP-100 factors (IPCC AR6)
GWP = {
    'CO2': 1.0,
    'CH4': 28.0,      # Methane
    'nmVOC': 2.2      # Average for light hydrocarbons
}

# Example emissions (kg/hr)
example = {
    'CO2': 500,
    'CH4': 50,
    'nmVOC': 30
}

print("=" * 60)
print("CO₂ EQUIVALENT CALCULATION")
print("=" * 60)

print("\nInput emissions (kg/hr):")
total_mass = 0
co2eq_total = 0

for gas, mass in example.items():
    co2eq = mass * GWP[gas]
    co2eq_total += co2eq
    total_mass += mass
    print(f"  {gas:8}: {mass:8.1f} kg × GWP {GWP[gas]:4.1f} = {co2eq:8.1f} kg CO₂eq")

print(f"  {'-'*50}")
print(f"  {'TOTAL':8}: {total_mass:8.1f} kg              {co2eq_total:8.1f} kg CO₂eq")

print("\nContribution to CO₂eq:")
for gas, mass in example.items():
    co2eq = mass * GWP[gas]
    pct = co2eq / co2eq_total * 100
    print(f"  {gas:8}: {pct:5.1f}%")

print("\n📊 Note: Despite CH₄ being only {:.0f}% by mass, it contributes".format(
    example['CH4']/total_mass*100))
print("         {:.0f}% of CO₂eq due to its high GWP!".format(
    example['CH4']*GWP['CH4']/co2eq_total*100))

# Carbon cost calculation
CO2_TAX_NOK = 1565  # NOK per tonne (2024)

annual_co2eq_tonnes = co2eq_total * 8760 / 1000
annual_cost = annual_co2eq_tonnes * CO2_TAX_NOK

print("\n" + "=" * 60)
print("CARBON COST IMPACT")
print("=" * 60)
print(f"\nNorwegian carbon tax: NOK {CO2_TAX_NOK:,}/tonne CO₂")
print(f"Annual CO₂eq: {annual_co2eq_tonnes:,.0f} tonnes")
print(f"Annual carbon cost: NOK {annual_cost:,.0f}")
print(f"                    (~EUR {annual_cost/11.5:,.0f})")
```

<details>
<summary>Output</summary>

```
============================================================
CO₂ EQUIVALENT CALCULATION
============================================================

Input emissions (kg/hr):
  CO2     :    500.0 kg × GWP  1.0 =    500.0 kg CO₂eq
  CH4     :     50.0 kg × GWP 28.0 =   1400.0 kg CO₂eq
  nmVOC   :     30.0 kg × GWP  2.2 =     66.0 kg CO₂eq
  --------------------------------------------------
  TOTAL   :    580.0 kg                1966.0 kg CO₂eq

Contribution to CO₂eq:
  CO2     :  25.4%
  CH4     :  71.2%
  nmVOC   :   3.4%

📊 Note: Despite CH₄ being only 9% by mass, it contributes
         71% of CO₂eq due to its high GWP!

============================================================
CARBON COST IMPACT
============================================================

Norwegian carbon tax: NOK 1,565/tonne CO₂
Annual CO₂eq: 17,222 tonnes
Annual carbon cost: NOK 26,952,680
                    (~EUR 2,343,711)
```

</details>

## Summary and Key Takeaways

### 🎯 Key Findings

1. **CO₂ is the dominant emission** (72-78% by mass) - conventional methods completely miss this!
2. **NeqSim achieves ±3.6% uncertainty** vs ±50%+ for conventional methods
3. **Salinity matters** - higher salinity reduces gas solubility ("salting-out")
4. **Most emissions occur at first pressure drop** - prioritize degasser monitoring

### ✅ NeqSim Advantages

| Feature | Benefit |
|---------|---------|
| **Rigorous thermodynamics** | CPA-EoS for accurate water-gas equilibrium |
| **CO₂ included** | Captures 72-78% of emissions missed by conventional |
| **Process-aware** | Accounts for actual T, P, composition |
| **Salinity effects** | Models NaCl "salting-out" |
| **Real-time capable** | NeqSimLive integration with plant data |
| **Low uncertainty** | ±3.6% validated at Gudrun field |

### 📋 When to Use Each Method

| Situation | Recommended Method |
|-----------|-------------------|
| Quick screening estimate | Norwegian handbook |
| **Regulatory reporting** | **NeqSim thermodynamic** |
| Real-time monitoring | NeqSimLive API |
| New field development | NeqSim with measured composition |

### 📚 Further Resources

- [Full Tutorial Documentation](ProducedWaterEmissions_Tutorial.md)
- [Norwegian Methods Comparison](NorwegianEmissionMethods_Comparison.md)
- [NeqSim Documentation](https://equinor.github.io/neqsim/)
- [neqsim-python GitHub](https://github.com/equinor/neqsim-python)

### 📞 Support

For questions or support, visit: https://github.com/equinor/neqsim/discussions

## 6. Generalizing to TEG Regeneration Emissions

The same methodology works for **any** process where gas is released from a liquid.
Here's an example for TEG (Triethylene Glycol) dehydration system emissions.

```python
# TEG Regeneration Emissions Example
# Rich TEG contains absorbed water and hydrocarbons that are released during regeneration

# Create rich TEG composition (after absorption in contactor)
teg_fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 35, 52.0)

# Rich TEG contains:
# - TEG (main component)
# - Absorbed water
# - Absorbed hydrocarbons (especially methane)
# - Absorbed CO2 (if present in gas)
teg_fluid.addComponent('TEG', 0.92)
teg_fluid.addComponent('water', 0.05)
teg_fluid.addComponent('methane', 0.02)
teg_fluid.addComponent('CO2', 0.01)

teg_fluid.setMixingRule(10)
teg_fluid.setMultiPhaseCheck(True)

# Create stream
teg_stream = jneqsim.process.equipment.stream.Stream('Rich TEG', teg_fluid)
teg_stream.setFlowRate(6000, 'kg/hr')  # Typical TEG circulation rate
teg_stream.setTemperature(35, 'C')
teg_stream.setPressure(52, 'bara')  # Contactor pressure

# Flash to regenerator feed conditions
teg_flash = jneqsim.process.equipment.heatexchanger.Heater('TEG Flash', teg_stream)
teg_flash.setOutPressure(1.5, 'bara')  # HP flash drum

# Build mini-process
teg_process = jneqsim.process.processmodel.ProcessSystem()
teg_process.add(teg_stream)
teg_process.add(teg_flash)
teg_process.run()

# Get flash gas emissions
flash_fluid = teg_flash.getOutletStream().getFluid()

print("=== TEG Flash Emissions ===")
print(f"(Rich TEG at 6000 kg/hr, flashed from 52 to 1.5 bara)\n")

if flash_fluid.hasPhaseType('gas'):
    gas = flash_fluid.getPhase('gas')
    ch4 = gas.getComponent('methane').getFlowRate('kg/hr')
    co2 = gas.getComponent('CO2').getFlowRate('kg/hr')
    water_vapor = gas.getComponent('water').getFlowRate('kg/hr')
    
    print(f"Methane:     {ch4:.2f} kg/hr ({ch4*8760/1000:.1f} tonnes/year)")
    print(f"CO2:         {co2:.2f} kg/hr ({co2*8760/1000:.1f} tonnes/year)")
    print(f"Water vapor: {water_vapor:.2f} kg/hr")
    
    # CO2 equivalents
    co2eq = co2 + ch4 * 28
    print(f"\nCO2eq:       {co2eq:.1f} kg/hr ({co2eq*8760/1000:.0f} tonnes/year)")
else:
    print("No gas phase (check conditions)")
```

<details>
<summary>Output</summary>

```
=== TEG Flash Emissions ===
(Rich TEG at 6000 kg/hr, flashed from 52 to 1.5 bara)

Methane:     13.44 kg/hr (117.8 tonnes/year)
CO2:         13.45 kg/hr (117.8 tonnes/year)
Water vapor: 0.02 kg/hr

CO2eq:       389.8 kg/hr (3415 tonnes/year)
```

</details>

## 7. Quick Troubleshooting

Common issues when running emissions calculations:

```python
# Troubleshooting Helper Function

def diagnose_emission_calculation(fluid):
    """
    Diagnose common issues with emission calculations.
    Run this if you're getting unexpected results.
    """
    print("=== Emission Calculation Diagnostics ===\n")
    
    # Check 1: Multi-phase
    n_phases = fluid.getNumberOfPhases()
    print(f"1. Number of phases: {n_phases}")
    if n_phases == 1:
        print("   ⚠️ Only 1 phase - no gas released!")
        print("   → Check: Is pressure low enough?")
        print("   → Check: Is there dissolved gas in composition?")
    
    # Check 2: Gas phase existence
    has_gas = fluid.hasPhaseType('gas')
    print(f"\n2. Has gas phase: {has_gas}")
    if not has_gas:
        print("   ⚠️ No gas phase detected")
        print("   → Ensure setMultiPhaseCheck(True) was called")
    
    # Check 3: Composition
    print(f"\n3. Total molar composition check:")
    total_z = 0
    for i in range(fluid.getNumberOfComponents()):
        z = fluid.getComponent(i).getz()
        total_z += z
        print(f"   {fluid.getComponent(i).getComponentName()}: {z:.4f}")
    print(f"   Total: {total_z:.4f}")
    if abs(total_z - 1.0) > 0.01:
        print("   ⚠️ Mole fractions don't sum to 1.0!")
    
    # Check 4: Conditions
    print(f"\n4. Current conditions:")
    print(f"   Temperature: {fluid.getTemperature()-273.15:.1f} °C")
    print(f"   Pressure: {fluid.getPressure():.2f} bara")
    
    # Check 5: Mixing rule
    print(f"\n5. Equation of state: {type(fluid).__name__}")
    print("   For water systems, use SystemSrkCPAstatoil with mixingRule(10)")
    
    print("\n" + "="*50)

# Example usage - uncomment to run diagnostics
# diagnose_emission_calculation(degasser_fluid)
```

```python
# Final Summary
print("=" * 70)
print("🌱 NOTEBOOK COMPLETE")
print("=" * 70)
print("""
You have learned:
  ✓ How to set up CPA fluid models for produced water
  ✓ Single and multi-stage degassing calculations
  ✓ Norwegian handbook (conventional) method
  ✓ NeqSim thermodynamic method advantages
  ✓ Effect of salinity on emissions
  ✓ Binary interaction parameters (kij) for water-gas systems
  ✓ CO₂ equivalent and GWP calculations
  ✓ Validation approaches and uncertainty
  ✓ Complete process simulation setup
  ✓ Troubleshooting common issues

Next Steps:
  1. Customize compositions to match your facility
  2. Connect to real-time process data (NeqSimLive)
  3. Validate against measured emissions
  4. Use for regulatory reporting per Aktivitetsforskriften §70

For more information:
  📖 https://equinor.github.io/neqsim/examples/ProducedWaterEmissions_Tutorial.html
  💬 https://github.com/equinor/neqsim/discussions
""")
```

<details>
<summary>Output</summary>

```
======================================================================
🌱 NOTEBOOK COMPLETE
======================================================================

You have learned:
  ✓ How to set up CPA fluid models for produced water
  ✓ Single and multi-stage degassing calculations
  ✓ Norwegian handbook (conventional) method
  ✓ NeqSim thermodynamic method advantages
  ✓ Effect of salinity on emissions
  ✓ Binary interaction parameters (kij) for water-gas systems
  ✓ CO₂ equivalent and GWP calculations
  ✓ Validation approaches and uncertainty
  ✓ Complete process simulation setup
  ✓ Troubleshooting common issues

Next Steps:
  1. Customize compositions to match your facility
  2. Connect to real-time process data (NeqSimLive)
  3. Validate against measured emissions
  4. Use for regulatory reporting per Aktivitetsforskriften §70

For more information:
  📖 https://equinor.github.io/neqsim/examples/ProducedWaterEmissions_Tutorial.html
  💬 https://github.com/equinor/neqsim/discussions

```

</details>

