---
title: Thermodynamics Recipes
description: Quick recipes for thermodynamic calculations in NeqSim - fluids, flash, properties, phase envelopes, and more.
---

# Thermodynamics Recipes

Copy-paste solutions for common thermodynamic tasks.

## Table of Contents

- [Creating Fluids](#creating-fluids)
- [Flash Calculations](#flash-calculations)
- [Reading Properties](#reading-properties)
- [Phase Envelopes](#phase-envelopes)
- [Choosing an EoS](#which-eos-should-i-use)
- [Export and Import](#export-and-import)

---

## Creating Fluids

### Create Natural Gas

```python
from neqsim import jneqsim

# Create at 25°C (298.15 K) and 50 bara
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 25, 50.0)
fluid.addComponent("nitrogen", 0.02)
fluid.addComponent("CO2", 0.01)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.06)
fluid.addComponent("propane", 0.03)
fluid.addComponent("n-butane", 0.02)
fluid.addComponent("n-pentane", 0.01)
fluid.setMixingRule("classic")
```

**Gotcha**: Always call `setMixingRule()` after adding all components!

### Create Oil with Plus Fraction

```python
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemPrEos(273.15 + 60, 200.0)
fluid.addComponent("nitrogen", 0.5)
fluid.addComponent("CO2", 2.0)
fluid.addComponent("methane", 45.0)
fluid.addComponent("ethane", 8.0)
fluid.addComponent("propane", 5.0)
fluid.addComponent("n-butane", 3.0)
fluid.addComponent("n-pentane", 2.0)
fluid.addComponent("n-hexane", 2.0)

# Add C7+ as pseudo-component
fluid.addTBPfraction("C7+", 32.5, 0.220, 0.82)  # mole%, MW, SG

fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)
```

### Create CO₂-Rich Fluid (CPA)

```python
from neqsim import jneqsim

# Use CPA for CO2 with water
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 25, 100.0)
fluid.addComponent("CO2", 0.95)
fluid.addComponent("methane", 0.03)
fluid.addComponent("water", 0.02)
fluid.setMixingRule(10)  # CPA mixing rule
```

---

## Flash Calculations

### Run TP Flash

```python
from neqsim import jneqsim

# After creating fluid...
ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()

# IMPORTANT: Initialize properties after flash
fluid.initProperties()

print(f"Phases: {fluid.getNumberOfPhases()}")
```

### Run PH Flash (Given Pressure and Enthalpy)

```python
# First, get enthalpy at known conditions
fluid.initProperties()
H_initial = fluid.getEnthalpy("J")

# Change pressure, calculate new T
new_pressure = 30.0  # bara
ops.PHflash(new_pressure * 1e5, H_initial)  # P in Pa!

print(f"New temperature: {fluid.getTemperature() - 273.15:.2f} °C")
```

### Run PS Flash (Isentropic)

```python
# Get entropy at initial conditions
fluid.initProperties()
S_initial = fluid.getEntropy("J/K")

# Isentropic expansion
new_pressure = 20.0  # bara
ops.PSflash(new_pressure * 1e5, S_initial)

print(f"New temperature: {fluid.getTemperature() - 273.15:.2f} °C")
```

---

## Reading Properties

### Get Density (Correctly!)

```python
# WRONG - Returns EoS density WITHOUT Peneloux correction
density_wrong = fluid.getDensity()

# CORRECT - Returns density WITH Peneloux volume correction
density_correct = fluid.getDensity("kg/m3")

print(f"Without correction: {density_wrong:.2f} kg/m³")
print(f"With correction: {density_correct:.2f} kg/m³")
```

**Gotcha**: Always pass a unit string to `getDensity()` to get the corrected value!

### Get All Common Properties

```python
# Make sure to initialize first
fluid.initProperties()

# Bulk properties
print(f"Temperature: {fluid.getTemperature() - 273.15:.2f} °C")
print(f"Pressure: {fluid.getPressure():.2f} bara")
print(f"Density: {fluid.getDensity('kg/m3'):.2f} kg/m³")
print(f"Molar mass: {fluid.getMolarMass('kg/mol') * 1000:.2f} g/mol")
print(f"Z-factor: {fluid.getZ():.4f}")
print(f"Enthalpy: {fluid.getEnthalpy('kJ/kg'):.2f} kJ/kg")
print(f"Entropy: {fluid.getEntropy('J/kgK'):.2f} J/kgK")
print(f"Cp: {fluid.getCp('kJ/kgK'):.4f} kJ/kgK")
print(f"Cv: {fluid.getCv('kJ/kgK'):.4f} kJ/kgK")
print(f"Speed of sound: {fluid.getSoundSpeed('m/s'):.2f} m/s")

# Transport properties
print(f"Viscosity: {fluid.getViscosity('cP'):.4f} cP")
print(f"Thermal cond: {fluid.getThermalConductivity('W/mK'):.4f} W/mK")
```

### Get Phase-Specific Properties

```python
# Check available phases
if fluid.hasPhaseType("gas"):
    gas = fluid.getPhase("gas")
    print(f"Gas density: {gas.getDensity('kg/m3'):.2f} kg/m³")
    print(f"Gas viscosity: {gas.getViscosity('cP'):.4f} cP")
    print(f"Gas fraction: {gas.getBeta():.4f} (mole)")

if fluid.hasPhaseType("oil"):
    oil = fluid.getPhase("oil")
    print(f"Oil density: {oil.getDensity('kg/m3'):.2f} kg/m³")
    print(f"Oil viscosity: {oil.getViscosity('cP'):.4f} cP")
```

### Get Component Properties

```python
for i in range(fluid.getNumberOfComponents()):
    comp = fluid.getComponent(i)
    name = str(comp.getComponentName())
    z = comp.getz()  # Overall mole fraction
    
    print(f"{name}: z={z:.4f}, Tc={comp.getTC():.1f} K, Pc={comp.getPC():.1f} bara")
```

---

## Phase Envelopes

### Calculate Phase Envelope

```python
from neqsim import jneqsim
import matplotlib.pyplot as plt

# Create fluid
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 25, 50.0)
fluid.addComponent("methane", 0.80)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.addComponent("n-butane", 0.05)
fluid.setMixingRule("classic")

# Calculate envelope
ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.calcPTphaseEnvelope()

# Extract points
dew_T = [t - 273.15 for t in list(ops.get("dewT"))]
dew_P = list(ops.get("dewP"))
bub_T = [t - 273.15 for t in list(ops.get("bubT"))]
bub_P = list(ops.get("bubP"))

# Plot
plt.figure(figsize=(10, 6))
plt.plot(dew_T, dew_P, 'b-', label='Dew line', linewidth=2)
plt.plot(bub_T, bub_P, 'r-', label='Bubble line', linewidth=2)
plt.xlabel('Temperature (°C)')
plt.ylabel('Pressure (bara)')
plt.title('Phase Envelope')
plt.legend()
plt.grid(True)
plt.show()
```

### Get Cricondenbar and Cricondentherm

```python
ops.calcPTphaseEnvelope()

cricondenbar_P = ops.get("cricondenbar")[1]  # Pressure
cricondenbar_T = ops.get("cricondenbar")[0] - 273.15  # Temperature in °C

cricondentherm_P = ops.get("cricondentherm")[1]
cricondentherm_T = ops.get("cricondentherm")[0] - 273.15

print(f"Cricondenbar: {cricondenbar_P:.2f} bara at {cricondenbar_T:.2f} °C")
print(f"Cricondentherm: {cricondentherm_T:.2f} °C at {cricondentherm_P:.2f} bara")
```

---

## Which EoS Should I Use?

| Fluid Type | Recommended EoS | NeqSim Class |
|------------|-----------------|--------------|
| **Dry gas** | SRK or PR | `SystemSrkEos`, `SystemPrEos` |
| **Gas condensate** | PR with Peneloux | `SystemPrEos` |
| **Black oil** | PR or SRK | `SystemPrEos`, `SystemSrkEos` |
| **Heavy oil** | PR or CPA | `SystemPrEos`, `SystemSrkCPAstatoil` |
| **CO₂ systems** | CPA | `SystemSrkCPAstatoil` |
| **CO₂ + water** | CPA or Electrolyte-CPA | `SystemSrkCPAstatoil` |
| **Natural gas (custody)** | GERG-2008 | `SystemGERG2008` |
| **LNG** | GERG-2008 or PR | `SystemGERG2008`, `SystemPrEos` |
| **Aqueous systems** | CPA | `SystemSrkCPAstatoil` |
| **Electrolytes** | Electrolyte-CPA | See electrolyte guide |

### Quick Selection Code

```python
from neqsim import jneqsim

# For dry gas / gas condensate
fluid = jneqsim.thermo.system.SystemSrkEos(T, P)

# For black oil
fluid = jneqsim.thermo.system.SystemPrEos(T, P)

# For CO2/water systems
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(T, P)

# For custody transfer (natural gas)
fluid = jneqsim.thermo.system.SystemGERG2008(T, P)
```

---

## Export and Import

### Export to JSON

```python
# Full fluid state as JSON
json_str = fluid.toJson()
print(json_str)

# Component-specific JSON
comp_json = fluid.getComponent(0).toJson()
```

### Export to Python Dictionary

```python
import json

# Parse JSON to dict
data = json.loads(fluid.toJson())
print(f"Temperature: {data['temperature']['value']} {data['temperature']['unit']}")
```

### Clone a Fluid

```python
# Create a copy (independent of original)
fluid_copy = fluid.clone()

# Modify copy without affecting original
fluid_copy.setTemperature(300.0)
```

---

## See Also

- **[Reading Fluid Properties Guide](../thermo/reading_fluid_properties)** - Comprehensive property access
- **[Thermodynamic Models](../thermo/thermodynamic_models)** - All EoS details
- **[JavaDoc: SystemInterface](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/neqsim/thermo/system/SystemInterface.html)** - Complete API
