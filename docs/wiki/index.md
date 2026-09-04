---
title: "NeqSim Wiki"
description: "Welcome to the NeqSim documentation. This comprehensive wiki provides guides, tutorials, and reference materials for using the library and contributing to development."
---


Welcome to the NeqSim documentation. This comprehensive wiki provides guides, tutorials, and reference materials for using the library and contributing to development.

---

## About NeqSim

**NeqSim (Non-Equilibrium Simulator)** is a Java library for estimating fluid properties and process design. The library contains models for:

- **Phase behavior** using rigorous equations of state (SRK, PR, CPA, GERG-2008)
- **Physical properties** (viscosity, density, thermal conductivity, interfacial tension)
- **Process equipment** (separators, compressors, heat exchangers, columns, and other unit operations)
- **Pipeline flow** (two-phase, multiphase, transient simulation)
- **Flow assurance** (hydrates, wax, asphaltene, scaling)

Development was initiated at the [Norwegian University of Science and Technology (NTNU)](https://www.ntnu.edu/employees/even.solbraa). NeqSim is part of the [NeqSim project](https://equinor.github.io/neqsimhome/).

---

## Quick Start

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class NeqSimQuickStart {
  private static final Logger logger = LogManager.getLogger(NeqSimQuickStart.class);

  private NeqSimQuickStart() {}

  public static void main(String[] args) {
    SystemInterface gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.05);
    gas.addComponent("propane", 0.03);
    gas.addComponent("CO2", 0.02);
    gas.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(gas);
    operations.TPflash();
    gas.initProperties();

    logger.info("Density: {} kg/m3", gas.getDensity("kg/m3"));
    logger.info("Compressibility: {}", gas.getZ());
  }
}
```

---

## 🚀 Getting Started

| Guide | Description |
|-------|-------------|
| [Getting Started](getting_started) | Installation, first calculations, and basic concepts |
| [Usage Examples](usage_examples) | Comprehensive code examples |
| [FAQ](faq) | Frequently asked questions |
| [GitHub Guide](Getting-started-with-NeqSim-and-Github) | Complete documentation index |

---

## 🧪 Thermodynamics & Phase Behavior

| Guide | Description |
|-------|-------------|
| [Thermodynamics Guide](thermodynamics_guide) | Equations of state, flash calculations, mixing rules |
| [Fluid Characterization](fluid_characterization) | Plus fractions, pseudo-components, TBP modeling |
| [Flash Equations & Tests](flash_equations_and_tests) | Flash calculations validated by tests |
| [Property Flash Workflows](property_flash_workflows) | PH, PS, UV flash calculations |

---

## ⚙️ Process Simulation

| Guide | Description |
|-------|-------------|
| [Process Simulation Guide](process_simulation) | Building flowsheets, running simulations |
| [Advanced Process Simulation](advanced_process_simulation) | Recycles, adjusters, complex systems |
| [Logical Unit Operations](logical_unit_operations) | Controllers, splitters, recycles |
| [Transient Simulation Guide](process_transient_simulation_guide) | Dynamic process modeling |
| [Process Control Framework](process_control) | PID controllers, automation |
| [Bottleneck Analysis](bottleneck_analysis) | Capacity constraints, production optimization |

---

## 🔧 Equipment Models

| Equipment | Documentation |
|-----------|---------------|
| [Distillation Column](distillation_column) | Sequential, damped, inside-out solvers |
| [Gibbs Reactor](gibbs_reactor) | Chemical equilibrium reactor |
| [Flow Meter Models](flow_meter_models) | Orifice, venturi, ultrasonic meters |
| [Air Cooler](air_cooler) | Air-cooled heat exchanger |
| [Heat Exchanger Design](heat_exchanger_mechanical_design) | Mechanical design methods |
| [Water Cooler](water_cooler) | Water-cooled systems |
| [Steam Heater](steam_heater) | Steam heating systems |
| [Battery Storage](battery_storage) | Energy storage unit |
| [Solar Panel](solar_panel) | Solar power generation |

---

## 📊 PVT & Reservoir

| Guide | Description |
|-------|-------------|
| [PVT Simulation Workflows](pvt_simulation_workflows) | CVD, CCE, DL simulations |
| [Black-Oil Flash Playbook](black_oil_flash_playbook) | Black-oil modeling techniques |
| [Humid Air Mathematics](humid_air_math) | Psychrometric calculations |

---

## 📏 Standards & Quality

| Guide | Description |
|-------|-------------|
| [Gas Quality Standards](gas_quality_standards_from_tests) | ISO 6976, GPA standards |

---

## 🔌 Integration & Tools

| Guide | Description |
|-------|-------------|
| [Java from Colab](java_simulation_from_colab_notebooks) | Running NeqSim in Google Colab |
| [JUnit Test Overview](test-overview) | Test suite structure |

---

## Installation

**Maven:**
```xml
<dependency>
    <groupId>com.equinor.neqsim</groupId>
    <artifactId>neqsim</artifactId>
    <version>3.19.0</version>
</dependency>
```

**Download:** [GitHub Releases](https://github.com/equinor/neqsim/releases)

---

## Resources

- **JavaDoc**: [API Documentation](https://equinor.github.io/neqsim/javadoc/index.html)
- **Source Code**: [github.com/equinor/neqsim](https://github.com/equinor/neqsim)
- **Issues**: [Report bugs or request features](https://github.com/equinor/neqsim/issues)
- **Discussions**: [Ask questions](https://github.com/equinor/neqsim/discussions)
