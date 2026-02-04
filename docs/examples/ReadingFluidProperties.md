---
layout: default
title: "ReadingFluidProperties"
description: "Jupyter notebook tutorial for NeqSim"
parent: Examples
nav_order: 1
---

# ReadingFluidProperties

> **Note:** This is an auto-generated Markdown version of the Jupyter notebook 
> [`ReadingFluidProperties.ipynb`](https://github.com/equinor/neqsim/blob/master/docs/examples/ReadingFluidProperties.ipynb).
> You can also [view it on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/ReadingFluidProperties.ipynb) 
> or [open in Google Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ReadingFluidProperties.ipynb).

---

# Reading Fluid Properties in NeqSim

This notebook demonstrates how to calculate and read thermodynamic and physical properties from fluids, phases, and components in NeqSim.

## Key Concepts

1. **Property Initialization Levels**: Use `init(0)`, `init(1)`, `init(2)`, `init(3)` for different property depths
2. **Physical Properties**: Call `initPhysicalProperties()` for transport properties (viscosity, conductivity)
3. **Volume Correction**: `getDensity()` without unit gives EoS density; `getDensity("kg/m3")` includes Peneloux correction
4. **Recommended**: Use `initProperties()` which combines `init(2)` + `initPhysicalProperties()`

```python
# Import NeqSim - Direct Java Access
from neqsim import jneqsim

# Import commonly used classes
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations
```

## Creating a Fluid and Running Flash

First, create a fluid with specified composition, temperature, and pressure, then run a TP flash.

```python
# Create a natural gas fluid
# Temperature in Kelvin, Pressure in bara
fluid = SystemSrkEos(273.15 + 25.0, 50.0)  # 25°C, 50 bara

# Add components (mole fractions)
fluid.addComponent("nitrogen", 0.02)
fluid.addComponent("CO2", 0.03)
fluid.addComponent("methane", 0.70)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.08)
fluid.addComponent("n-butane", 0.04)
fluid.addComponent("n-pentane", 0.02)
fluid.addComponent("n-hexane", 0.01)

# IMPORTANT: Always set a mixing rule!
fluid.setMixingRule("classic")

# Run TP flash to determine phase equilibrium
ops = ThermodynamicOperations(fluid)
ops.TPflash()

print(f"Number of phases after flash: {fluid.getNumberOfPhases()}")
```

<details>
<summary>Output</summary>

```
Number of phases after flash: 2
```

</details>

## Property Initialization Levels

NeqSim uses different initialization levels to compute properties:

| Level | Method | Properties Available |
|-------|--------|---------------------|
| 0 | `init(0)` | Feed composition, mole fractions |
| 1 | `init(1)` | + Density, fugacities, Z-factor |
| 2 | `init(2)` | + Enthalpy, entropy, Cp, Cv |
| 3 | `init(3)` | + Composition derivatives |
| - | `initPhysicalProperties()` | + Viscosity, conductivity |
| - | `initProperties()` | All of the above (init(2) + physical) |

```python
# Initialize ALL properties - RECOMMENDED approach
fluid.initProperties()

print("Properties initialized!")
print(f"Temperature: {fluid.getTemperature() - 273.15:.2f} °C")
print(f"Pressure: {fluid.getPressure():.2f} bara")
```

<details>
<summary>Output</summary>

```
Properties initialized!
Temperature: 25.00 °C
Pressure: 50.00 bara
```

</details>

## Reading Fluid-Level Properties

Fluid-level properties represent the overall mixture, weighted across all phases.

```python
print("=== FLUID PROPERTIES ===")
print(f"Molar mass: {fluid.getMolarMass('gr/mol'):.4f} gr/mol")
print(f"Enthalpy: {fluid.getEnthalpy('J/mol'):.2f} J/mol")
print(f"Entropy: {fluid.getEntropy('J/molK'):.4f} J/molK")
print(f"Cp: {fluid.getCp('kJ/kgK'):.4f} kJ/kgK")
print(f"Cv: {fluid.getCv('kJ/kgK'):.4f} kJ/kgK")
print(f"Gamma (Cp/Cv): {fluid.getGamma():.4f}")
```

<details>
<summary>Output</summary>

```
=== FLUID PROPERTIES ===
Molar mass: 24.2751 gr/mol
Enthalpy: -1611.72 J/mol
Entropy: -26.5401 J/molK
Cp: 2.4850 kJ/kgK
Cv: 1.6373 kJ/kgK
Gamma (Cp/Cv): 1.5178
```

</details>

## Reading Phase Properties

Phase properties are accessed via `fluid.getPhase(...)`. Phases are ordered by density (lightest first).

```python
for i in range(fluid.getNumberOfPhases()):
    phase = fluid.getPhase(i)
    phase_type = str(phase.getType())
    
    print(f"\n=== PHASE {i}: {phase_type} ===")
    print(f"Phase fraction (mole): {phase.getMoleFraction():.4f}")
    print(f"Z-factor: {phase.getZ():.6f}")
    print(f"Molar mass: {phase.getMolarMass('gr/mol'):.4f} gr/mol")
    
    # Thermodynamic properties
    print(f"\nThermodynamic Properties:")
    print(f"  Enthalpy: {phase.getEnthalpy('J/mol'):.2f} J/mol")
    print(f"  Entropy: {phase.getEntropy('J/molK'):.4f} J/molK")
    print(f"  Cp: {phase.getCp('kJ/kgK'):.4f} kJ/kgK")
    print(f"  Speed of sound: {phase.getSoundSpeed():.2f} m/s")
    
    # Transport properties (require initPhysicalProperties)
    print(f"\nTransport Properties:")
    print(f"  Viscosity: {phase.getViscosity('cP'):.6f} cP")
    print(f"  Thermal conductivity: {phase.getThermalConductivity('W/mK'):.6f} W/mK")
```

<details>
<summary>Output</summary>

```

=== PHASE 0: GAS ===
Phase fraction (mole): 0.9225
Z-factor: 0.836506
Molar mass: 22.2555 gr/mol

Thermodynamic Properties:
  Enthalpy: -478.26 J/mol
  Entropy: -24.3853 J/molK
  Cp: 2.4556 kJ/kgK
  Speed of sound: 346.68 m/s

Transport Properties:
  Viscosity: 0.012602 cP
  Thermal conductivity: 0.035397 W/mK

=== PHASE 1: OIL ===
Phase fraction (mole): 0.0775
Z-factor: 0.203061
Molar mass: 48.3214 gr/mol

Thermodynamic Properties:
  Enthalpy: -15106.74 J/mol
  Entropy: -52.1955 J/molK
  Cp: 2.6463 kJ/kgK
  Speed of sound: 624.54 m/s

Transport Properties:
  Viscosity: 0.111444 cP
  Thermal conductivity: 0.092635 W/mK
```

</details>

## Understanding Density and Volume Correction

**CRITICAL DIFFERENCE:**
- `getDensity()` (no unit) → Returns EoS density **WITHOUT** Peneloux volume correction
- `getDensity("kg/m3")` (with unit) → Returns density **WITH** Peneloux volume correction

The Peneloux correction improves liquid density predictions from cubic EoS.

```python
print("=== DENSITY COMPARISON ===")
for i in range(fluid.getNumberOfPhases()):
    phase = fluid.getPhase(i)
    phase_type = str(phase.getType())
    
    # WITHOUT Peneloux correction (raw EoS)
    density_no_corr = phase.getDensity()
    
    # WITH Peneloux correction (recommended)
    density_with_corr = phase.getDensity("kg/m3")
    
    diff_percent = (density_no_corr - density_with_corr) / density_with_corr * 100
    
    print(f"\nPhase {i} ({phase_type}):")
    print(f"  Density (no correction): {density_no_corr:.4f} kg/m3")
    print(f"  Density (with correction): {density_with_corr:.4f} kg/m3")
    print(f"  Difference: {diff_percent:.2f}%")

print("\n⚠️ ALWAYS use getDensity('kg/m3') for accurate density values!")
```

<details>
<summary>Output</summary>

```
=== DENSITY COMPARISON ===

Phase 0 (GAS):
  Density (no correction): 53.6622 kg/m3
  Density (with correction): 53.8706 kg/m3
  Difference: -0.39%

Phase 1 (OIL):
  Density (no correction): 479.9697 kg/m3
  Density (with correction): 515.1178 kg/m3
  Difference: -6.82%

⚠️ ALWAYS use getDensity('kg/m3') for accurate density values!
```

</details>

## Reading Component Properties

Component properties are accessed via `fluid.getPhase(...).getComponent(...)`.

- `getx()` → Mole fraction in **this phase**
- `getz()` → Mole fraction in **total fluid** (overall composition)

```python
print("=== COMPONENT PROPERTIES ===")
print("\nOverall composition (z) vs Phase compositions (x):")
print(f"{'Component':<12} {'z (total)':<12} {'x (gas)':<12} {'x (oil)':<12} {'K-value':<12}")
print("-" * 60)

gas_phase = fluid.getPhase("gas") if fluid.hasPhaseType("gas") else None
oil_phase = fluid.getPhase("oil") if fluid.hasPhaseType("oil") else None

for i in range(fluid.getNumberOfComponents()):
    comp_name = str(fluid.getComponent(i).getComponentName())  # Convert to Python string
    z = fluid.getComponent(i).getz()
    
    x_gas = gas_phase.getComponent(i).getx() if gas_phase else 0
    x_oil = oil_phase.getComponent(i).getx() if oil_phase else 0
    
    # K-value = y/x (gas composition / liquid composition)
    k_value = x_gas / x_oil if x_oil > 1e-10 else float('inf')
    
    print(f"{comp_name:<12} {z:<12.6f} {x_gas:<12.6f} {x_oil:<12.6f} {k_value:<12.4f}")
```

<details>
<summary>Output</summary>

```
=== COMPONENT PROPERTIES ===

Overall composition (z) vs Phase compositions (x):
Component    z (total)    x (gas)      x (oil)      K-value     
------------------------------------------------------------
nitrogen     0.020000     0.021474     0.002454     8.7518      
CO2          0.030000     0.031036     0.017666     1.7568      
methane      0.700000     0.741289     0.208407     3.5569      
ethane       0.100000     0.099369     0.107509     0.9243      
propane      0.080000     0.069830     0.201091     0.3473      
n-butane     0.040000     0.026506     0.200661     0.1321      
n-pentane    0.020000     0.008337     0.158855     0.0525      
n-hexane     0.010000     0.002159     0.103357     0.0209      
```

</details>

```python
print("\n=== FUGACITY COEFFICIENTS ===")
print(f"{'Component':<12} {'φ (gas)':<15} {'φ (oil)':<15}")
print("-" * 42)

for i in range(fluid.getNumberOfComponents()):
    comp_name = str(fluid.getComponent(i).getComponentName())  # Convert to Python string
    
    phi_gas = gas_phase.getComponent(i).getFugacityCoefficient() if gas_phase else 0
    phi_oil = oil_phase.getComponent(i).getFugacityCoefficient() if oil_phase else 0
    
    print(f"{comp_name:<12} {phi_gas:<15.6f} {phi_oil:<15.6f}")
```

<details>
<summary>Output</summary>

```

=== FUGACITY COEFFICIENTS ===
Component    φ (gas)         φ (oil)        
------------------------------------------
nitrogen     1.098474        9.613682       
CO2          0.819822        1.440273       
methane      0.935675        3.328125       
ethane       0.700605        0.647561       
propane      0.553571        0.192229       
n-butane     0.439506        0.058056       
n-pentane    0.348610        0.018297       
n-hexane     0.275949        0.005764       
```

</details>

## Pure Component Properties

```python
print("=== PURE COMPONENT PROPERTIES ===")
print(f"{'Component':<12} {'Tc (K)':<12} {'Pc (bara)':<12} {'ω':<12} {'MW (g/mol)':<12}")
print("-" * 60)

for i in range(fluid.getNumberOfComponents()):
    comp = fluid.getComponent(i)
    comp_name = str(comp.getComponentName())  # Convert to Python string
    print(f"{comp_name:<12} "
          f"{comp.getTC():<12.2f} "
          f"{comp.getPC():<12.2f} "  # getPC() returns bara according to docs
          f"{comp.getAcentricFactor():<12.4f} "
          f"{comp.getMolarMass() * 1000:<12.4f}")
```

<details>
<summary>Output</summary>

```
=== PURE COMPONENT PROPERTIES ===
Component    Tc (K)       Pc (bara)    ω            MW (g/mol)  
------------------------------------------------------------
nitrogen     126.10       33.94        0.0403       28.0135     
CO2          304.19       73.81        0.2276       44.0100     
methane      190.56       45.99        0.0115       16.0430     
ethane       305.32       48.72        0.0995       30.0700     
propane      369.83       42.48        0.1523       44.0970     
n-butane     425.12       37.96        0.2002       58.1230     
n-pentane    469.70       33.70        0.2515       72.1500     
n-hexane     507.60       30.25        0.3013       86.1770     
```

</details>

## Interfacial Tension

```python
if fluid.getNumberOfPhases() > 1:
    print("=== INTERFACIAL PROPERTIES ===")
    
    if fluid.hasPhaseType("gas") and fluid.hasPhaseType("oil"):
        ift = fluid.getInterfacialTension("gas", "oil")
        print(f"Gas-Oil interfacial tension: {ift * 1000:.4f} mN/m")
else:
    print("Single phase - no interfacial tension calculated")
```

<details>
<summary>Output</summary>

```
=== INTERFACIAL PROPERTIES ===
Gas-Oil interfacial tension: 5.4182 mN/m
```

</details>

## Specifying Units

NeqSim supports multiple unit systems. When you specify a unit, the method returns the value in that unit.

Common unit options:
- Density: `"kg/m3"`, `"mol/m3"`, `"lb/ft3"`
- Enthalpy: `"J"`, `"J/mol"`, `"kJ/kg"`
- Viscosity: `"kg/msec"`, `"Pas"`, `"cP"`
- Flow rate: `"kg/hr"`, `"Sm3/hr"`, `"mole/hr"`

```python
phase = fluid.getPhase(0)

print("=== UNIT EXAMPLES ===")
print(f"\nDensity in different units:")
print(f"  {phase.getDensity('kg/m3'):.4f} kg/m³")
print(f"  {phase.getDensity('mol/m3'):.4f} mol/m³")

print(f"\nEnthalpy in different units:")
print(f"  {phase.getEnthalpy('J'):.2f} J")
print(f"  {phase.getEnthalpy('J/mol'):.2f} J/mol")
print(f"  {phase.getEnthalpy('kJ/kg'):.4f} kJ/kg")

print(f"\nViscosity in different units:")
print(f"  {phase.getViscosity():.6e} kg/(m·s)  [default]")
print(f"  {phase.getViscosity('Pas'):.6e} Pa·s")
print(f"  {phase.getViscosity('cP'):.6f} cP (centipoise)")
```

<details>
<summary>Output</summary>

```
=== UNIT EXAMPLES ===

Density in different units:
  53.8706 kg/m³
  2420.5557 mol/m³

Enthalpy in different units:
  -441.20 J
  -478.26 J/mol
  -21.4896 kJ/kg

Viscosity in different units:
  1.260166e-05 kg/(m·s)  [default]
  1.260166e-05 Pa·s
  0.012602 cP (centipoise)
```

</details>

## Global Unit System Switching

```python
# Access Units class
Units = jneqsim.util.unit.Units

print("=== UNIT SYSTEM COMPARISON ===")

# Metric units (default)
Units.activateMetricUnits()
print(f"\nMetric: P = {fluid.getPressure():.2f} bara, T = {fluid.getTemperature() - 273.15:.2f} °C")

# SI units
Units.activateSIUnits()
print(f"SI: P = {fluid.getPressure():.0f} Pa, T = {fluid.getTemperature():.2f} K")

# Field units
Units.activateFieldUnits()
print(f"Field: P = {fluid.getPressure():.2f} psia, T = {fluid.getTemperature():.2f} °F")

# Reset to metric
Units.activateMetricUnits()
```

<details>
<summary>Output</summary>

```
=== UNIT SYSTEM COMPARISON ===

Metric: P = 50.00 bara, T = 25.00 °C
SI: P = 50 Pa, T = 298.15 K
Field: P = 50.00 psia, T = 298.15 °F
```

</details>

## JSON Output

Export all fluid properties to JSON format for reporting or data exchange.

```python
import json

# Get fluid properties as JSON
fluid_json = json.loads(str(fluid.toJson()))
print("=== FLUID JSON (excerpt) ===")
print(json.dumps(fluid_json, indent=2)[:2000] + "...")
```

<details>
<summary>Output</summary>

```
=== FLUID JSON (excerpt) ===
{
  "name": "DefaultName",
  "properties": {
    "oil": {
      "molar mass": {
        "value": "48.321411283948144",
        "unit": "gr/mol"
      },
      "density": {
        "value": "515.1178470851919",
        "unit": "kg/m3"
      },
      "flow rate": {
        "value": "0.026166189899031442",
        "unit": "m3/hr"
      }
    },
    "gas": {
      "molar mass": {
        "value": "22.255458536729908",
        "unit": "gr/mol"
      },
      "density": {
        "value": "53.8705772717092",
        "unit": "kg/m3"
      },
      "flow rate": {
        "value": "1.3720246623680477",
        "unit": "m3/hr"
      }
    },
    "overall": {
      "molar mass": {
        "value": "24.27512",
        "unit": "gr/mol"
      },
      "density": {
        "value": "63.08687223672405",
        "unit": "kg/m3"
      },
      "flow rate": {
        "value": "1.3852395736482304",
        "unit": "m3/hr"
      }
    }
  },
  "composition": {
    "oil": {
      "ethane": {
        "value": "0.10750900569602004",
        "unit": "mole fraction"
      },
      "n-hexane": {
        "value": "0.10335726278709773",
        "unit": "mole fraction"
      },
      "nitrogen": {
        "value": "0.002453622219314129",
        "unit": "mole fraction"
      },
      "CO2": {
        "value": "0.01766605771785503",
        "unit": "mole fraction"
      },
      "propane": {
        "value": "0.20109095127027124",
        "unit": "mole fraction"
      },
      "methane": {
        "value": "0.2084073493960257",
        "unit": "mole fraction"
      },
      "n-pentane": {
        "value": "0.15885493166004552",
        "unit": "mole fraction"
      },
      "n-butane": {
        "value": "0.20066081925337073",
        "unit": "mole fraction"
      }
    },
    "gas": {
      "ethane": {
        "value": "0.09936931440891883",
        "unit": "mole fraction"
      },
      "n-hexane": {
        "value": "0.002158871247919012",
        "unit": "mole fraction"
      },
      "nitrog...
```

</details>

```python
# Get component properties as JSON
comp_json = json.loads(str(fluid.toCompJson()))
print("\n=== COMPONENT JSON (excerpt) ===")
# Show just first component
first_comp = list(comp_json["properties"].keys())[0]
print(f"Properties for {first_comp}:")
print(json.dumps(comp_json["properties"][first_comp], indent=2))
```

<details>
<summary>Output</summary>

```

=== COMPONENT JSON (excerpt) ===
Properties for ethane:
{
  "Acentric Factor": {
    "value": "0.0995",
    "unit": "-"
  },
  "Critical Temperature": {
    "value": "32.170000000000016",
    "unit": "C"
  },
  "Critical Pressure": {
    "value": "48.72",
    "unit": "bara"
  },
  "Weigth Fraction": {
    "value": "0.1238716842594393",
    "unit": "-"
  },
  "Mole Fraction": {
    "value": "0.1",
    "unit": "-"
  },
  "Normal Liquid Density": {
    "value": "544.459352",
    "unit": "kg/m3"
  }
}
```

</details>

## Summary: Best Practices

1. **Always call `initProperties()`** after flash to ensure all properties are available

2. **Use `getDensity("kg/m3")` with a unit** to get Peneloux-corrected density

3. **Check phase existence** before accessing: `fluid.hasPhaseType("gas")`

4. **Understand mole fractions**:
   - `getz()` = overall (feed) composition
   - `getx()` = composition within a specific phase

5. **For transport properties**, `initPhysicalProperties()` must have been called (included in `initProperties()`)

6. **Temperature is in Kelvin** by default when getting from fluid object

## References

- [SystemInterface API](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermo/system/SystemInterface.java)
- [PhaseInterface API](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermo/phase/PhaseInterface.java)
- [ComponentInterface API](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermo/component/ComponentInterface.java)
- [Reading Properties Documentation](../thermo/reading_fluid_properties.md)

