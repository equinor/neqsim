---
title: "Getting Started with NeqSim"
description: "Use this page as a launchpad into the NeqSim documentation. It mirrors the high-level structure from the Colab introduction notebook and links directly to reference guides and examples."
---

# Getting Started with NeqSim

Use this page as a launchpad into the NeqSim documentation. It mirrors the high-level structure from the Colab introduction notebook and links directly to reference guides and examples.

## Table of Contents
- [Quick Start](#quick-start)
- [Set up NeqSim locally](#set-up-neqsim-locally)
- [Your First Calculation](#your-first-calculation)
- [Fundamentals and thermodynamics](#fundamentals-and-thermodynamics)
- [Fluid characterization and PVT workflows](#fluid-characterization-and-pvt-workflows)
- [Process simulation](#process-simulation)
- [Pipeline and multiphase flow](#pipeline-and-multiphase-flow)
- [Dynamic behavior and process safety](#dynamic-behavior-and-process-safety)
- [Unit operations and equipment models](#unit-operations-and-equipment-models)
- [Integration, control, and automation](#integration-control-and-automation)
- [Examples and tutorials](#examples-and-tutorials)

---

## Quick Start

### Using Maven (Recommended)

Add NeqSim as a dependency in your `pom.xml`:

```xml
<dependency>
    <groupId>com.equinor.neqsim</groupId>
    <artifactId>neqsim</artifactId>
    <version>3.0.0</version>
</dependency>
```

### Direct JAR Download

Download the shaded JAR from the [releases page](https://github.com/equinor/neqsimsource/releases) and add to your classpath.

---

## Set up NeqSim locally

Clone the repository and build with the Maven wrapper:

```bash
git clone https://github.com/equinor/neqsim.git
cd neqsim
./mvnw install
```

On Windows:
```cmd
mvnw.cmd install
```

The command downloads dependencies, compiles the project, and runs the test suite. For environment notes and troubleshooting tips, see the [README](../../) and [developer setup guide](../development/DEVELOPER_SETUP).

### Requirements
- Java 8 or higher (Java 11+ recommended)
- Maven 3.6+ (included via wrapper)

---

## Your First Calculation

### Simple Flash Calculation

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class FirstCalculation {
    public static void main(String[] args) {
        // 1. Create a natural gas system at 25°C and 50 bar
        SystemSrkEos gas = new SystemSrkEos(298.15, 50.0);
        gas.addComponent("methane", 0.90);
        gas.addComponent("ethane", 0.06);
        gas.addComponent("propane", 0.03);
        gas.addComponent("n-butane", 0.01);
        gas.setMixingRule("classic");
        
        // 2. Perform flash calculation
        ThermodynamicOperations ops = new ThermodynamicOperations(gas);
        ops.TPflash();
        
        // 3. Print results
        System.out.println("Number of phases: " + gas.getNumberOfPhases());
        System.out.println("Density: " + gas.getDensity("kg/m3") + " kg/m³");
        System.out.println("Z-factor: " + gas.getPhase("gas").getZ());
        System.out.println("Molecular weight: " + gas.getMolarMass() * 1000 + " g/mol");
    }
}
```

### Simple Process Simulation

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class FirstProcess {
    public static void main(String[] args) {
        // 1. Create fluid
        SystemSrkEos fluid = new SystemSrkEos(320.0, 100.0);
        fluid.addComponent("methane", 0.80);
        fluid.addComponent("ethane", 0.10);
        fluid.addComponent("propane", 0.05);
        fluid.addComponent("n-pentane", 0.05);
        fluid.setMixingRule("classic");
        
        // 2. Create stream
        Stream feed = new Stream("Feed", fluid);
        feed.setFlowRate(10000.0, "kg/hr");
        feed.setTemperature(50.0, "C");
        feed.setPressure(100.0, "bara");
        
        // 3. Add equipment
        ThrottlingValve valve = new ThrottlingValve("Valve", feed);
        valve.setOutletPressure(20.0, "bara");
        
        Separator separator = new Separator("Separator", valve.getOutletStream());
        
        // 4. Build and run process
        ProcessSystem process = new ProcessSystem();
        process.add(feed);
        process.add(valve);
        process.add(separator);
        
        // Use runOptimized() for best performance (auto-selects strategy)
        process.runOptimized();
        
        // 5. Results
        System.out.println("Gas rate: " + separator.getGasOutStream().getFlowRate("kg/hr") + " kg/hr");
        System.out.println("Liquid rate: " + separator.getLiquidOutStream().getFlowRate("kg/hr") + " kg/hr");
    }
}
```

### Execution Strategies

NeqSim provides optimized execution strategies for complex process simulations:

| Method | Best For | Speedup |
|--------|----------|---------|
| `run()` | Simple processes | baseline |
| `runOptimized()` | **Recommended** | 28-40% |
| `runParallel()` | Feed-forward (no recycles) | 40-57% |
| `runHybrid()` | Complex recycle processes | 38% |

```java
// Recommended - auto-selects best strategy based on process topology
process.runOptimized();

// Analyze process structure
System.out.println(process.getExecutionPartitionInfo());
```

See [ProcessSystem documentation](../process/processmodel/process_system) for details.

---

## Fundamentals and thermodynamics

### Equations of State
NeqSim supports multiple equations of state for different applications:

| EOS | Class | Best For |
|-----|-------|----------|
| SRK | `SystemSrkEos` | General hydrocarbon systems |
| Peng-Robinson | `SystemPrEos` | Reservoir/liquid density |
| SRK-CPA | `SystemSrkCPAstatoil` | Water, glycols, alcohols |
| GERG-2008 | `SystemGERG2008Eos` | Natural gas custody transfer |

### Documentation Links
- Read the [Thermodynamics Guide](thermodynamics_guide) for an overview of models, correlations, and implementation notes.
- Explore validated calculations in [Flash equations and tests](flash_equations_and_tests) and the [Thermodynamics of gas processing](process_simulation#thermodynamics).
- Review property-focused workflows in [Property flash workflows](property_flash_workflows) and viscosity models in [Viscosity models](viscosity_models).
- See [Steam Tables IF97](steam_tables_if97) for water/steam calculations.

---

## Fluid characterization and PVT workflows

### Heavy Fraction Handling
For oils with C7+ fractions:

```java
SystemSrkEos oil = new SystemSrkEos(350.0, 100.0);
oil.addComponent("methane", 10.0);
oil.addComponent("ethane", 5.0);
// ... light components ...

// Add TBP fractions
oil.addTBPfraction("C7", 5.0, 0.092, 730.0);
oil.addTBPfraction("C8", 4.0, 0.104, 750.0);
oil.addPlusFraction("C10+", 20.0, 0.200, 820.0);

// Characterize
oil.getCharacterization().setTBPModel("PedersenSRK");
oil.getCharacterization().characterisePlusFraction();
```

### Documentation Links
- Follow [Fluid Characterization](fluid_characterization) for setting up equations of state and component data.
- Use the [PVT simulation workflows](pvt_simulation_workflows) and [Black-oil flash playbook](black_oil_flash_playbook) for reservoir-focused setups.
- See [TBP Fraction Models](tbp_fraction_models) for detailed characterization methods.
- See [Gas quality standards from tests](gas_quality_standards_from_tests) for handling analytical measurements.

---

## Process simulation

### Available Equipment
NeqSim includes 50+ unit operations:

| Category | Equipment |
|----------|-----------|
| **Separation** | Separator, ThreePhaseSeparator, DistillationColumn, MembraneSeparator |
| **Compression** | Compressor, Pump, Expander, Ejector |
| **Heat Transfer** | Heater, Cooler, HeatExchanger |
| **Flow Control** | ThrottlingValve, FlowRateController |
| **Pipelines** | PipeBeggsAndBrills, AdiabaticPipe, WaterHammerPipe |
| **Specialty** | Electrolyzer, WindTurbine, SolarPanel, Battery |

### Documentation Links
- Start with the [Process Simulation Guide](process_simulation) for steady-state modeling patterns.
- Dive deeper into [Advanced process simulation](advanced_process_simulation) and [Logical unit operations](logical_unit_operations) for custom flowsheets.
- Consult the [Modules overview](../modules) and [Process calculator](../simulation/process_calculator) when wiring NeqSim into larger systems.

---

## Pipeline and multiphase flow

### Pipeline Models

```java
PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("Pipeline", inletStream);
pipeline.setLength(10000.0);           // 10 km
pipeline.setDiameter(0.2);             // 8 inch
pipeline.setElevation(50.0);           // 50m elevation gain
pipeline.setPipeWallRoughness(4.5e-5); // Steel
pipeline.run();
```

### Documentation Links
- See the [Pipeline Index](pipeline_index) for all pipeline documentation
- [Beggs and Brill Correlation](beggs_and_brill_correlation) for multiphase pressure drop
- [Pipeline Heat Transfer](pipeline_heat_transfer) for non-adiabatic flow
- [Multiphase Transient Model](multiphase_transient_model) for dynamic simulation
- [Water Hammer Implementation](water_hammer_implementation) for fast transients

---

## Dynamic behavior and process safety

### Safety Systems
NeqSim provides comprehensive safety simulation:

```java
// PSV sizing example
ValveController psv = new ValveController("PSV-001");
psv.setMaxPressure(50.0, "bara");
psv.setReliefPressure(55.0, "bara");
```

### Documentation Links
- Study dynamic blowdown and protection behavior in [ESD blowdown systems](../safety/ESD_BLOWDOWN_SYSTEM), [PSV dynamic sizing](psv_dynamic_sizing_example), and [HIPPS implementation](../safety/hipps_implementation).
- Review layered safety topics in [Integrated safety systems](../safety/INTEGRATED_SAFETY_SYSTEMS), [HIPPS summary](../safety/HIPPS_SUMMARY), and [Layered safety architecture](../safety/layered_safety_architecture).
- For alarm logic and shutdown sequencing, see [Alarm system guide](../safety/alarm_system_guide), [SIS logic implementation](../safety/sis_logic_implementation), and [Integration safety chain tests](../safety/integration_safety_chain_tests).
- See [Process Transient Simulation Guide](process_transient_simulation_guide) for dynamic simulations.

---

## Unit operations and equipment models

### Equipment Categories
- **Compressors**: [Compressor calculations](../process/equipment/compressors), performance curves, staging
- **Pumps**: [Pump usage guide](pump_usage_guide), [Pump theory](pump_theory_and_implementation)
- **Separation**: [Distillation column](distillation_column), [Membrane separation](membrane_separation)
- **Heat Exchange**: [Air cooler](air_cooler), [Water cooler](water_cooler), [Steam heater](steam_heater)
- **Metering**: [Flow meter models](flow_meter_models), [Venturi calculation](venturi_calculation)
- **Specialty**: [Battery storage](battery_storage), [Solar panel](solar_panel), [Gibbs reactor](gibbs_reactor)

### Documentation Links
- Browse individual equipment pages such as [Distillation column](distillation_column), [Air cooler](air_cooler), [Water cooler](water_cooler), and [Heat exchanger mechanical design](heat_exchanger_mechanical_design).
- For specialized models, see [Flow meter models](flow_meter_models), [Battery storage unit](battery_storage), [Solar panel](solar_panel), and [Pump usage guide](pump_usage_guide).
- Additional unit operations and mechanical details are covered in the [Process logic enhancements](../simulation/ProcessLogicEnhancements) series.

---

## Integration, control, and automation

### PID Control Example

```java
ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass();
controller.setControllerSetPoint(50.0);
controller.setControllerParameters(0.5, 100.0, 0.0); // Kp, Ti, Td
valve.setController(controller);
```

### Documentation Links
- Connect NeqSim to control systems using the [Process control framework](process_control) and [Real-time integration guide](../integration/REAL_TIME_INTEGRATION_GUIDE).
- Learn about runtime flexibility in [Runtime logic flexibility](../simulation/RuntimeLogicFlexibility) and alarm handling in [Alarm triggered logic example](../safety/alarm_triggered_logic_example).
- For AI/ML integration, see [AI Platform Integration](../integration/ai_platform_integration) and [ML Integration](../integration/ml_integration).
- For MPC, see [MPC Integration](../integration/mpc_integration) and [Industrial MPC Integration](../integration/neqsim_industrial_mpc_integration).
- For scripting and hybrid workflows, see [Java simulations from Colab notebooks](java_simulation_from_colab_notebooks) and [Java/Python usage examples](usage_examples).

---

## Examples and tutorials

### Jupyter Notebooks
- [ESP Pump Tutorial](../examples/ESP_Pump_Tutorial.ipynb)
- [PVT Simulation and Tuning](../examples/PVT_Simulation_and_Tuning.ipynb)
- [MPC Integration Tutorial](../examples/MPC_Integration_Tutorial.ipynb)
- [AI Platform Integration](../examples/AIPlatformIntegration.ipynb)
- [Graph-Based Simulation](../examples/GraphBasedProcessSimulation.ipynb)

### Documentation Links
- Work through the [Usage examples](usage_examples) for end-to-end flows in both Java and Python.
- Try the [Process transient simulation guide](process_transient_simulation_guide) and [Process simulation using NeqSim](process_simulation) for hands-on modeling patterns.
- Explore extended topics such as [Process automation and logic implementation summary](../simulation/process_logic_implementation_summary) and integration tests in [Test overview](test-overview).

### External Resources
- [NeqSim Colab Demo](https://colab.research.google.com/drive/1JiszeCxfpcJZT2vejVWuNWGmd9SJdNC7)
- [Java Test Examples](https://github.com/equinor/neqsim/tree/master/src/test/java/neqsim)
- [NeqSim Python](https://github.com/equinor/neqsimpython)
- [NeqSim Matlab](https://github.com/equinor/neqsimmatlab)
