---
layout: default
title: NeqSim Documentation
---

# NeqSim Documentation

**NeqSim** (Non-Equilibrium Simulator) is a comprehensive Java library for thermodynamic, physical property, and process simulation developed by [Equinor](https://www.equinor.com/).

[![Java CI](https://github.com/equinor/neqsim/actions/workflows/java_build.yml/badge.svg)](https://github.com/equinor/neqsim/actions)
[![Maven Central](https://img.shields.io/maven-central/v/com.equinor.neqsim/neqsim.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.equinor.neqsim%22%20AND%20a:%22neqsim%22)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

---

## Quick Navigation

<div class="grid-container">

### 📚 Core Documentation
- [**Getting Started**](wiki/getting_started.md) - Installation and first steps
- [**Modules Overview**](modules.md) - Architecture and package structure
- [**Reference Manual Index**](REFERENCE_MANUAL_INDEX.md) - Complete API documentation

### 🔬 Thermodynamics
- [**Thermo Package**](thermo/README.md) - Equations of state, mixing rules, fluids
- [**Thermodynamic Operations**](thermodynamicoperations/README.md) - Flash calculations, phase envelopes
- [**Physical Properties**](physical_properties/README.md) - Viscosity, conductivity, diffusivity

### 🏭 Process Simulation
- [**Process Equipment**](process/README.md) - Separators, compressors, heat exchangers
- [**Fluid Mechanics**](fluidmechanics/README.md) - Pipeline flow, pressure drop
- [**Safety Systems**](safety/README.md) - Relief valves, flare systems

### 📊 Applications
- [**PVT Simulation**](pvtsimulation/README.md) - Reservoir fluid characterization
- [**Black Oil Models**](blackoil/README.md) - Simplified correlations
- [**Field Development**](fielddevelopment/README.md) - Integrated workflows

</div>

---

## Quick Start Example

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create a natural gas fluid
SystemInterface gas = new SystemSrkEos(298.15, 50.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.05);
gas.addComponent("propane", 0.03);
gas.addComponent("CO2", 0.02);
gas.setMixingRule("classic");

// Perform flash calculation
ThermodynamicOperations ops = new ThermodynamicOperations(gas);
ops.TPflash();

// Get properties
System.out.println("Density: " + gas.getDensity("kg/m3") + " kg/m³");
System.out.println("Compressibility: " + gas.getZ());
```

---

## Documentation Sections

| Section | Description |
|---------|-------------|
| [Thermodynamics](thermo/README.md) | Equations of state, phase behavior, component properties |
| [Process Simulation](process/README.md) | Unit operations, process systems, controllers |
| [Physical Properties](physical_properties/README.md) | Transport properties, interfacial tension |
| [PVT Simulation](pvtsimulation/README.md) | Reservoir fluid characterization, tuning |
| [Fluid Mechanics](fluidmechanics/README.md) | Pipeline flow, multiphase modeling |
| [Examples](examples/) | Tutorials and code samples |
| [Development](development/README.md) | Contributing guidelines, developer setup |

---

## Interactive Reference Manual

The [**Interactive Reference Manual**](manual/neqsim_reference_manual.html) provides a searchable, navigable guide to all NeqSim packages with:

- Complete package hierarchy
- Class and interface listings
- Usage examples and code snippets
- Cross-referenced links to source documentation

---

## Python Integration

NeqSim is also available for Python through [neqsim-python](https://github.com/equinor/neqsim-python):

```python
from neqsim.thermo import TPflash, fluid

# Create and flash a natural gas
gas = fluid("srk")
gas.addComponent("methane", 0.9)
gas.addComponent("ethane", 0.1)
gas.setTemperature(298.15, "K")
gas.setPressure(50.0, "bara")

TPflash(gas)
print(f"Gas density: {gas.getDensity('kg/m3'):.2f} kg/m³")
```

---

## Resources

- [GitHub Repository](https://github.com/equinor/neqsim)
- [Maven Central](https://search.maven.org/artifact/com.equinor.neqsim/neqsim)
- [Issue Tracker](https://github.com/equinor/neqsim/issues)
- [Discussions](https://github.com/equinor/neqsim/discussions)

---

<footer>
NeqSim is developed and maintained by <a href="https://www.equinor.com/">Equinor</a> and contributors.
Licensed under the <a href="https://opensource.org/licenses/Apache-2.0">Apache 2.0 License</a>.
</footer>
