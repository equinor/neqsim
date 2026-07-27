---
title: Google Colab Quickstart
description: Try NeqSim in Google Colab with a one-cell install and no local setup. One-click thermodynamic and process examples.
---

Try NeqSim in a clean Google Colab runtime. The setup cell installs the current
PyPI release; the examples below then run without a local Java or Python setup.

## One-Click Start

Click to open a ready-to-run notebook:

[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/EvenSol/NeqSim-Colab/blob/master/notebooks/examples_of_NeqSim_in_Colab.ipynb)

## Quick Setup Cell

If starting from a blank notebook, run this cell first:

```python
# Install NeqSim (takes ~30 seconds)
!pip install neqsim -q

# Import NeqSim and report the installed version
from importlib.metadata import version
from neqsim import jneqsim

print(f"NeqSim {version('neqsim')} ready!")
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

The repository notebooks below do not repeat the installation command. In a
fresh Colab runtime, run the **Quick Setup Cell** above as the first cell before
running their NeqSim imports.

| Notebook | Description | Open |
|----------|-------------|------|
| **Introduction** | Getting started examples | [![Open](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/EvenSol/NeqSim-Colab/blob/master/notebooks/examples_of_NeqSim_in_Colab.ipynb) |
| **Reading Properties** | Init levels, density, units | [![Open](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ReadingFluidProperties.ipynb) |
| **PVT Simulation** | Fluid characterization, tuning | [![Open](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/PVT_Simulation_and_Tuning.ipynb) |
| **Gathering Network Solver** | Multi-well pressure-flow allocation | [![Open](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/NetworkSolverTutorial.ipynb) |
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

# Create a two-phase feed at 30°C and 50 bara
fluid = SystemSrkEos(273.15 + 30, 50.0)
fluid.addComponent("methane", 0.70)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.10)
fluid.addComponent("n-butane", 0.05)
fluid.addComponent("n-pentane", 0.05)
fluid.setMixingRule("classic")

# Build process
process = ProcessSystem()

feed = Stream("Feed", fluid)
feed.setFlowRate(10000.0, "kg/hr")
feed.setTemperature(30.0, "C")
feed.setPressure(50.0, "bara")
process.add(feed)

sep = Separator("Separator", feed)
process.add(sep)

comp = Compressor("Compressor", sep.getGasOutStream())
comp.setOutletPressure(100.0, "bara")
comp.setIsentropicEfficiency(0.75)
process.add(comp)

# Run
process.run()

# Results
print(f"Gas rate: {sep.getGasOutStream().getFlowRate('kg/hr'):.0f} kg/hr")
print(f"Liquid rate: {sep.getLiquidOutStream().getFlowRate('kg/hr'):.0f} kg/hr")
print(f"Compressor power: {comp.getPower('kW'):.1f} kW")
```

With NeqSim 3.16.0, this case gives approximately 7,588 kg/h gas,
2,412 kg/h liquid, and 202.2 kW compressor power. Small differences can occur
between releases, but both separator products and the compressor power must be
positive and the two product mass flows must close to the 10,000 kg/h feed.

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
plt.plot(
    [temperature - 273.15 for temperature in dew_temps],
    dew_press,
    "b-",
    label="Dew line",
    linewidth=2,
)
plt.plot(
    [temperature - 273.15 for temperature in bub_temps],
    bub_press,
    "r-",
    label="Bubble line",
    linewidth=2,
)
plt.xlabel("Temperature (°C)")
plt.ylabel("Pressure (bara)")
plt.title("Phase envelope")
plt.legend()
plt.grid(True, alpha=0.3)
plt.show()
```

## Tips for Colab

1. **Runtime disconnects**: Colab may disconnect after idle time. Re-run the install cell if needed.

2. **Review warnings**: Do not hide warnings from flashes or process equipment;
   they can indicate convergence or validity problems. To reduce only Python
   gateway logging, use:

   ```python
   import logging

   logging.getLogger("jpype").setLevel(logging.WARNING)
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
