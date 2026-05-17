---
title: Google Colab Quickstart
description: Try NeqSim instantly in Google Colab with no installation. One-click notebooks for thermodynamic and process calculations.
---

# Google Colab Quickstart

Try NeqSim instantly - no installation required!

## One-Click Start

Click to open a ready-to-run notebook:

[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/EvenSol/NeqSim-Colab/blob/master/notebooks/examples_of_NeqSim_in_Colab.ipynb)

## Quick Setup Cell

If starting from a blank notebook, run this cell first:

```python
# Install NeqSim (takes ~30 seconds)
!pip install neqsim -q

# Import NeqSim
from neqsim import jneqsim
print("NeqSim ready!")
```

## Your First Calculation

```python
from neqsim import jneqsim

# Create a natural gas at 25°C and 50 bar
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 25, 50.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")

# Flash calculation
ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

# Results
print(f"Phases: {fluid.getNumberOfPhases()}")
print(f"Density: {fluid.getDensity('kg/m3'):.2f} kg/m³")
print(f"Z-factor: {fluid.getZ():.4f}")
```

## Example Notebooks

| Notebook | Description | Open |
|----------|-------------|------|
| **Introduction** | Getting started examples | [![Open](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/EvenSol/NeqSim-Colab/blob/master/notebooks/examples_of_NeqSim_in_Colab.ipynb) |
| **Reading Properties** | Init levels, density, units | [![Open](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ReadingFluidProperties.ipynb) |
| **PVT Simulation** | Fluid characterization, tuning | [![Open](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/PVT_Simulation_and_Tuning.ipynb) |
| **Process Simulation** | Separators, compressors | [![Open](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/NetworkSolverTutorial.ipynb) |
| **ESP Pump** | Electric submersible pump | [![Open](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ESP_Pump_Tutorial.ipynb) |

## Quick Process Example

```python
from neqsim import jneqsim

# Aliases for cleaner code
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
Compressor = jneqsim.process.equipment.compressor.Compressor

# Create fluid
fluid = SystemSrkEos(273.15 + 30, 50.0)
fluid.addComponent("methane", 0.80)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.addComponent("n-butane", 0.05)
fluid.setMixingRule("classic")

# Build process
process = ProcessSystem()

feed = Stream("Feed", fluid)
feed.setFlowRate(5000, "kg/hr")
process.add(feed)

sep = Separator("Separator", feed)
process.add(sep)

comp = Compressor("Compressor", sep.getGasOutStream())
comp.setOutletPressure(100.0)
process.add(comp)

# Run
process.run()

# Results
print(f"Gas rate: {sep.getGasOutStream().getFlowRate('kg/hr'):.0f} kg/hr")
print(f"Liquid rate: {sep.getLiquidOutStream().getFlowRate('kg/hr'):.0f} kg/hr")
print(f"Compressor power: {comp.getPower('kW'):.1f} kW")
```

## Visualization in Colab

```python
import matplotlib.pyplot as plt
import numpy as np
from neqsim import jneqsim

# Phase envelope calculation
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 25, 50.0)
fluid.addComponent("methane", 0.80)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.addComponent("n-butane", 0.05)
fluid.setMixingRule("classic")

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.calcPTphaseEnvelope()

# Extract data
dew_temps = list(ops.get("dewT"))
dew_press = list(ops.get("dewP"))
bub_temps = list(ops.get("bubT"))
bub_press = list(ops.get("bubP"))

# Plot
plt.figure(figsize=(10, 6))
plt.plot([t - 273.15 for t in dew_temps], dew_press, 'b-', label='Dew line', linewidth=2)
plt.plot([t - 273.15 for t in bub_temps], bub_press, 'r-', label='Bubble line', linewidth=2)
plt.xlabel('Temperature (°C)')
plt.ylabel('Pressure (bara)')
plt.title('Phase Envelope')
plt.legend()
plt.grid(True, alpha=0.3)
plt.show()
```

## Tips for Colab

1. **Runtime disconnects**: Colab may disconnect after idle time. Re-run the install cell if needed.

2. **Suppress warnings**:
   ```python
   import warnings
   warnings.filterwarnings('ignore')
   ```

3. **Save results**: Download results to your Google Drive:
   ```python
   from google.colab import drive
   drive.mount('/content/drive')
   # Save files to /content/drive/MyDrive/
   ```

## Next Steps

- **[Python Quickstart](python-quickstart)** - Local Python installation
- **[Examples](../examples/index)** - More notebook tutorials
- **[Cookbook](../cookbook/index)** - Quick recipes
- **[JavaDoc API](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/index.html)** - Complete API reference
