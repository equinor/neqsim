---
title: Python Quickstart
description: Get started with NeqSim in Python. Installation, first flash calculation, and first process simulation in under 5 minutes.
---

# Python Quickstart

Get NeqSim running in Python in under 5 minutes.

## Step 1: Install neqsim-python (1 minute)

```bash
pip install neqsim
```

This installs the Python package with bundled Java libraries. Requires Java 8+ installed.

## Step 2: First Flash Calculation (2 minutes)

```python
# Import NeqSim - Direct Java Access via jneqsim gateway
from neqsim import jneqsim

# Create a natural gas at 25°C and 50 bar
# Note: Temperature in KELVIN!
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 25, 50.0)

# Add components (mole fractions)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)

# IMPORTANT: Always set mixing rule
fluid.setMixingRule("classic")

# Run flash calculation
ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()

# Initialize properties (required for most property access)
fluid.initProperties()

# Print results
print(f"Number of phases: {fluid.getNumberOfPhases()}")
print(f"Density: {fluid.getDensity('kg/m3'):.2f} kg/m³")
print(f"Z-factor: {fluid.getZ():.4f}")
print(f"Molar mass: {fluid.getMolarMass('kg/mol') * 1000:.2f} g/mol")

# Pretty print full results
fluid.prettyPrint()
```

## Step 3: First Process Simulation (2 minutes)

```python
from neqsim import jneqsim

# Convenient class imports
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
Compressor = jneqsim.process.equipment.compressor.Compressor

# 1. Create feed fluid (30°C, 50 bara)
fluid = SystemSrkEos(273.15 + 30, 50.0)
fluid.addComponent("methane", 0.70)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.10)
fluid.addComponent("n-butane", 0.05)
fluid.addComponent("n-pentane", 0.05)
fluid.setMixingRule("classic")

# 2. Create process system
process = ProcessSystem()

# 3. Add feed stream
feed = Stream("Feed", fluid)
feed.setFlowRate(10000, "kg/hr")
process.add(feed)

# 4. Add separator
separator = Separator("HP Separator", feed)
process.add(separator)

# 5. Add compressor on gas outlet
compressor = Compressor("Gas Compressor", separator.getGasOutStream())
compressor.setOutletPressure(80.0)  # bara
compressor.setIsentropicEfficiency(0.75)
process.add(compressor)

# 6. Run the process
process.run()

# 7. Print results
print("=== SEPARATOR ===")
print(f"Gas out: {separator.getGasOutStream().getFlowRate('kg/hr'):.1f} kg/hr")
print(f"Liquid out: {separator.getLiquidOutStream().getFlowRate('kg/hr'):.1f} kg/hr")

print("\n=== COMPRESSOR ===")
print(f"Power: {compressor.getPower('kW'):.1f} kW")
print(f"Outlet T: {compressor.getOutletStream().getTemperature() - 273.15:.1f} °C")
```

## Common Import Patterns

```python
from neqsim import jneqsim

# Thermodynamic systems
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
SystemPrEos = jneqsim.thermo.system.SystemPrEos
SystemSrkCPAstatoil = jneqsim.thermo.system.SystemSrkCPAstatoil

# Process equipment
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
ThreePhaseSeparator = jneqsim.process.equipment.separator.ThreePhaseSeparator
Compressor = jneqsim.process.equipment.compressor.Compressor
Heater = jneqsim.process.equipment.heatexchanger.Heater
Cooler = jneqsim.process.equipment.heatexchanger.Cooler
ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve
Mixer = jneqsim.process.equipment.mixer.Mixer
Splitter = jneqsim.process.equipment.splitter.Splitter

# Pipelines
AdiabaticPipe = jneqsim.process.equipment.pipeline.AdiabaticPipe
PipeBeggsAndBrills = jneqsim.process.equipment.pipeline.PipeBeggsAndBrills
```

## Common Gotchas

| Issue | Solution |
|-------|----------|
| `TypeError: unsupported format string` | Java String needs conversion: `str(java_string)` |
| `AttributeError: 'NoneType'` | JVM not started - use `from neqsim import jneqsim` |
| Wrong density values | Use `getDensity("kg/m3")` with unit for Peneloux correction |
| Temperature seems wrong | NeqSim uses **Kelvin**. Convert: `T_K = T_C + 273.15` |
| `JVMNotFoundException` | Install Java 8+ and set JAVA_HOME |

### Java String Conversion

When using Java strings in Python f-strings with format specifiers:

```python
# WRONG - causes TypeError
comp_name = fluid.getComponent(0).getComponentName()
print(f"{comp_name:<12}")  # Error!

# CORRECT - convert to Python string
comp_name = str(fluid.getComponent(0).getComponentName())
print(f"{comp_name:<12}")  # Works!
```

## Working with Units

```python
# Temperature: always Kelvin internally
temp_kelvin = fluid.getTemperature()
temp_celsius = fluid.getTemperature() - 273.15

# Setting with units
stream.setTemperature(30.0, "C")      # Celsius
stream.setPressure(50.0, "bara")      # bara

# Getting with units
density = fluid.getDensity("kg/m3")   # IMPORTANT: includes Peneloux correction
viscosity = fluid.getViscosity("cP")
enthalpy = fluid.getEnthalpy("kJ/kg")
```

## Next Steps

- **[Reading Fluid Properties](../thermo/reading_fluid_properties.md)** - Init levels and property access
- **[Python Examples](../examples/index.md)** - Jupyter notebook tutorials
- **[Cookbook](../cookbook/index.md)** - Quick recipes for common tasks
- **[JavaDoc API](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/index.html)** - Complete API reference (Java methods work in Python too!)

## Jupyter Notebook Tips

```python
# Suppress Java logging in notebooks
import logging
logging.getLogger('jpype').setLevel(logging.WARNING)

# Display fluid as table
import pandas as pd

def fluid_to_dataframe(fluid):
    """Convert fluid composition to pandas DataFrame."""
    data = []
    for i in range(fluid.getNumberOfComponents()):
        comp = fluid.getComponent(i)
        data.append({
            'Component': str(comp.getComponentName()),
            'z': comp.getz(),
            'MW': comp.getMolarMass() * 1000
        })
    return pd.DataFrame(data)

df = fluid_to_dataframe(fluid)
display(df)
```
