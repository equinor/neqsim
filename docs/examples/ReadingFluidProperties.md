---
title: Reading Fluid Properties Tutorial
description: Tutorial demonstrating how to calculate and read fluid properties in NeqSim including init levels, density, enthalpy, viscosity, units, and Peneloux volume correction.
---

# Reading Fluid Properties in NeqSim

This tutorial demonstrates how to calculate and read thermodynamic and physical properties from fluids, phases, and components in NeqSim.

## Key Concepts

1. **Property Initialization Levels**: Use `init(0)`, `init(1)`, `init(2)`, `init(3)` for different property depths
2. **Physical Properties**: Call `initPhysicalProperties()` for transport properties (viscosity, conductivity)
3. **Volume Correction**: `getDensity()` without unit gives EoS density; `getDensity("kg/m3")` includes Peneloux correction
4. **Recommended**: Use `initProperties()` which combines `init(2)` + `initPhysicalProperties()`

## Creating a Fluid and Running Flash

First, create a fluid with specified composition, temperature, and pressure, then run a TP flash.

```python
# Import NeqSim - Direct Java Access
from neqsim import jneqsim

# Import commonly used classes
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations

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

## Fugacity Coefficients

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
          f"{comp.getPC():<12.2f} "  # getPC() returns bara
          f"{comp.getAcentricFactor():<12.4f} "
          f"{comp.getMolarMass() * 1000:<12.4f}")
```

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

## JSON Output

Export all fluid properties to JSON format for reporting or data exchange.

```python
import json

# Get fluid properties as JSON
fluid_json = json.loads(str(fluid.toJson()))
print("=== FLUID JSON (excerpt) ===")
print(json.dumps(fluid_json, indent=2)[:2000] + "...")

# Get component properties as JSON
comp_json = json.loads(str(fluid.toCompJson()))
print("\n=== COMPONENT JSON (excerpt) ===")
# Show just first component
first_comp = list(comp_json["properties"].keys())[0]
print(f"Properties for {first_comp}:")
print(json.dumps(comp_json["properties"][first_comp], indent=2))
```

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
- [Reading Fluid Properties Documentation](../thermo/reading_fluid_properties.md)
