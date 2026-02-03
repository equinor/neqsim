---
title: "Usage Examples"
description: "Comprehensive examples demonstrating NeqSim capabilities for thermodynamic calculations and process simulation."
---

# Usage Examples

Comprehensive examples demonstrating NeqSim capabilities for thermodynamic calculations and process simulation.

## Table of Contents
- [Thermodynamic Calculations](#thermodynamic-calculations)
  - [Basic TP Flash](#basic-tp-flash)
  - [Phase Envelope](#phase-envelope)
  - [Dew Point Calculations](#dew-point-calculations)
  - [Physical Properties](#physical-properties)
- [Fluid Creation](#fluid-creation)
  - [Simple Gas Mixture](#simple-gas-mixture)
  - [Oil with Heavy Fractions](#oil-with-heavy-fractions)
  - [Aqueous Systems with CPA](#aqueous-systems-with-cpa)
- [Process Simulation](#process-simulation)
  - [Simple Separation](#simple-separation)
  - [Compression System](#compression-system)
  - [Heat Exchanger](#heat-exchanger)
  - [Complete Gas Processing Plant](#complete-gas-processing-plant)
- [Pipeline Calculations](#pipeline-calculations)
- [Specialized Equipment](#specialized-equipment)

---

## Thermodynamic Calculations

### Basic TP Flash

The most common thermodynamic calculation - determining phase equilibrium at fixed temperature and pressure.

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create a natural gas system
SystemSrkEos gas = new SystemSrkEos(298.15, 50.0);  // 25°C, 50 bar
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.08);
gas.addComponent("propane", 0.04);
gas.addComponent("n-butane", 0.02);
gas.addComponent("n-pentane", 0.01);
gas.setMixingRule("classic");

// Perform TP flash
ThermodynamicOperations ops = new ThermodynamicOperations(gas);
ops.TPflash();

// Print results
System.out.println("Number of phases: " + gas.getNumberOfPhases());
System.out.println("Gas fraction: " + gas.getPhaseFraction("gas", "mole"));
System.out.println("Liquid fraction: " + gas.getPhaseFraction("oil", "mole"));
System.out.println("Gas density: " + gas.getPhase("gas").getDensity("kg/m3") + " kg/m³");
```

### Phase Envelope

Calculate the complete phase envelope (bubble and dew point curves).

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.07);
fluid.addComponent("n-heptane", 0.03);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.calcPTphaseEnvelope();

// Get cricondenbar (maximum pressure point)
double cricondenbarP = ops.get("cricondenbar")[0];
double cricondenbarT = ops.get("cricondenbar")[1];
System.out.println("Cricondenbar: " + cricondenbarP + " bar at " + cricondenbarT + " K");

// Get cricondentherm (maximum temperature point)
double cricondentT = ops.get("cricondentherm")[1];
double cricondentP = ops.get("cricondentherm")[0];
System.out.println("Cricondentherm: " + cricondentT + " K at " + cricondentP + " bar");
```

### Dew Point Calculations

Calculate hydrocarbon and water dew points.

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Use CPA for accurate water modeling
SystemSrkCPAstatoil gas = new SystemSrkCPAstatoil(298.15, 70.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.05);
gas.addComponent("propane", 0.03);
gas.addComponent("water", 0.02);
gas.setMixingRule(10);  // CPA mixing rule

ThermodynamicOperations ops = new ThermodynamicOperations(gas);

// Hydrocarbon dew point at fixed pressure
ops.dewPointTemperatureFlash();
System.out.println("HC Dew Point at 70 bar: " + (gas.getTemperature() - 273.15) + " °C");

// Water dew point
ops.waterDewPointTemperatureFlash();
System.out.println("Water Dew Point: " + (gas.getTemperature() - 273.15) + " °C");
```

### Physical Properties

Calculate comprehensive physical properties after flash.

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

SystemSrkEos fluid = new SystemSrkEos(300.0, 30.0);
fluid.addComponent("methane", 0.95);
fluid.addComponent("CO2", 0.05);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Gas phase properties
System.out.println("=== Gas Phase Properties ===");
System.out.println("Density: " + fluid.getPhase("gas").getDensity("kg/m3") + " kg/m³");
System.out.println("Viscosity: " + fluid.getPhase("gas").getViscosity("cP") + " cP");
System.out.println("Thermal Conductivity: " + fluid.getPhase("gas").getThermalConductivity("W/mK") + " W/m·K");
System.out.println("Cp: " + fluid.getPhase("gas").getCp("kJ/kgK") + " kJ/kg·K");
System.out.println("Cv: " + fluid.getPhase("gas").getCv("kJ/kgK") + " kJ/kg·K");
System.out.println("Z-factor: " + fluid.getPhase("gas").getZ());
System.out.println("Speed of Sound: " + fluid.getPhase("gas").getSoundSpeed() + " m/s");
System.out.println("Molecular Weight: " + fluid.getPhase("gas").getMolarMass() * 1000 + " g/mol");
System.out.println("Enthalpy: " + fluid.getPhase("gas").getEnthalpy("kJ/kg") + " kJ/kg");
System.out.println("Entropy: " + fluid.getPhase("gas").getEntropy("kJ/kgK") + " kJ/kg·K");
```

---

## Fluid Creation

### Simple Gas Mixture

```java
import neqsim.thermo.system.SystemSrkEos;

// Standard SRK for natural gas
SystemSrkEos gas = new SystemSrkEos(298.15, 50.0);
gas.addComponent("nitrogen", 0.02);
gas.addComponent("CO2", 0.03);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.06);
gas.addComponent("propane", 0.03);
gas.addComponent("i-butane", 0.005);
gas.addComponent("n-butane", 0.005);
gas.setMixingRule("classic");
```

### Oil with Heavy Fractions

```java
import neqsim.thermo.system.SystemSrkEos;

SystemSrkEos oil = new SystemSrkEos(350.0, 100.0);

// Light components
oil.addComponent("methane", 10.0);
oil.addComponent("ethane", 5.0);
oil.addComponent("propane", 4.0);
oil.addComponent("n-butane", 3.0);
oil.addComponent("n-pentane", 2.0);
oil.addComponent("n-hexane", 2.0);

// Heavy fractions (TBP cuts)
// addTBPfraction(name, moles, molarMass_kg/mol, density_kg/m3)
oil.addTBPfraction("C7", 5.0, 0.092, 730.0);
oil.addTBPfraction("C8", 4.0, 0.104, 750.0);
oil.addTBPfraction("C9", 3.0, 0.117, 770.0);

// Plus fraction
oil.addPlusFraction("C10+", 20.0, 0.200, 820.0);

// Set mixing rule and characterize
oil.setMixingRule("classic");

// Run characterization to split plus fraction
oil.getCharacterization().setTBPModel("PedersenSRK");
oil.getCharacterization().setPlusFractionModel("Pedersen");
oil.getCharacterization().characterisePlusFraction();
```

### Aqueous Systems with CPA

For systems with water, glycols, or alcohols, use CPA equation of state.

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;

// Gas with MEG injection for hydrate inhibition
SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(280.0, 100.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.04);
fluid.addComponent("water", 0.02);
fluid.addComponent("MEG", 0.01);
fluid.setMixingRule(10);  // CPA mixing rule

// Calculate hydrate equilibrium
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.hydrateFormationTemperature();
System.out.println("Hydrate formation temperature: " + (fluid.getTemperature() - 273.15) + " °C");
```

---

## Process Simulation

### Simple Separation

Two-stage separation with pressure reduction.

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// Create feed fluid
SystemSrkEos fluid = new SystemSrkEos(350.0, 150.0);
fluid.addComponent("methane", 70.0);
fluid.addComponent("ethane", 10.0);
fluid.addComponent("propane", 8.0);
fluid.addComponent("n-butane", 5.0);
fluid.addComponent("n-pentane", 4.0);
fluid.addComponent("n-heptane", 3.0);
fluid.setMixingRule("classic");

// Create feed stream
Stream wellStream = new Stream("Well Stream", fluid);
wellStream.setFlowRate(10000.0, "kg/hr");
wellStream.setTemperature(80.0, "C");
wellStream.setPressure(150.0, "bara");

// First stage separation
ThrottlingValve chokeValve = new ThrottlingValve("Choke Valve", wellStream);
chokeValve.setOutletPressure(50.0, "bara");

Separator hpSeparator = new Separator("HP Separator", chokeValve.getOutletStream());

// Second stage separation
ThrottlingValve lpValve = new ThrottlingValve("LP Valve", hpSeparator.getLiquidOutStream());
lpValve.setOutletPressure(5.0, "bara");

Separator lpSeparator = new Separator("LP Separator", lpValve.getOutletStream());

// Build and run process
ProcessSystem process = new ProcessSystem();
process.add(wellStream);
process.add(chokeValve);
process.add(hpSeparator);
process.add(lpValve);
process.add(lpSeparator);
process.run();

// Results
System.out.println("=== HP Separator ===");
System.out.println("Gas rate: " + hpSeparator.getGasOutStream().getFlowRate("kg/hr") + " kg/hr");
System.out.println("Oil rate: " + hpSeparator.getLiquidOutStream().getFlowRate("kg/hr") + " kg/hr");

System.out.println("=== LP Separator ===");
System.out.println("Flash gas: " + lpSeparator.getGasOutStream().getFlowRate("kg/hr") + " kg/hr");
System.out.println("Stabilized oil: " + lpSeparator.getLiquidOutStream().getFlowRate("kg/hr") + " kg/hr");
```

### Compression System

Multi-stage compression with intercooling.

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// Create gas feed
SystemSrkEos gas = new SystemSrkEos(298.15, 5.0);
gas.addComponent("methane", 0.95);
gas.addComponent("ethane", 0.03);
gas.addComponent("propane", 0.02);
gas.setMixingRule("classic");

Stream feed = new Stream("Feed Gas", gas);
feed.setFlowRate(50000.0, "Sm3/day");
feed.setTemperature(30.0, "C");
feed.setPressure(5.0, "bara");

// Stage 1: 5 -> 20 bar
Compressor stage1 = new Compressor("Stage 1", feed);
stage1.setOutletPressure(20.0, "bara");
stage1.setPolytropicEfficiency(0.78);
stage1.setUsePolytropicCalc(true);

Cooler intercooler1 = new Cooler("Intercooler 1", stage1.getOutletStream());
intercooler1.setOutTemperature(35.0, "C");

Separator scrubber1 = new Separator("Scrubber 1", intercooler1.getOutletStream());

// Stage 2: 20 -> 80 bar
Compressor stage2 = new Compressor("Stage 2", scrubber1.getGasOutStream());
stage2.setOutletPressure(80.0, "bara");
stage2.setPolytropicEfficiency(0.78);
stage2.setUsePolytropicCalc(true);

Cooler aftercooler = new Cooler("Aftercooler", stage2.getOutletStream());
aftercooler.setOutTemperature(40.0, "C");

// Build process
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(stage1);
process.add(intercooler1);
process.add(scrubber1);
process.add(stage2);
process.add(aftercooler);
process.run();

// Results
System.out.println("Stage 1 power: " + stage1.getPower("kW") + " kW");
System.out.println("Stage 1 outlet T: " + stage1.getOutletStream().getTemperature("C") + " °C");
System.out.println("Stage 2 power: " + stage2.getPower("kW") + " kW");
System.out.println("Stage 2 outlet T: " + stage2.getOutletStream().getTemperature("C") + " °C");
System.out.println("Total power: " + (stage1.getPower("kW") + stage2.getPower("kW")) + " kW");
```

### Heat Exchanger

Shell and tube heat exchanger with two streams.

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// Hot stream (gas to be cooled)
SystemSrkEos hotFluid = new SystemSrkEos(373.15, 50.0);
hotFluid.addComponent("methane", 0.9);
hotFluid.addComponent("ethane", 0.1);
hotFluid.setMixingRule("classic");

Stream hotStream = new Stream("Hot Gas", hotFluid);
hotStream.setFlowRate(5000.0, "kg/hr");
hotStream.setTemperature(100.0, "C");
hotStream.setPressure(50.0, "bara");

// Cold stream (cooling water)
SystemSrkEos coldFluid = new SystemSrkEos(293.15, 3.0);
coldFluid.addComponent("water", 1.0);
coldFluid.setMixingRule("classic");

Stream coldStream = new Stream("Cooling Water", coldFluid);
coldStream.setFlowRate(20000.0, "kg/hr");
coldStream.setTemperature(20.0, "C");
coldStream.setPressure(3.0, "bara");

// Heat exchanger
HeatExchanger hx = new HeatExchanger("Gas Cooler");
hx.setFeedStream(0, hotStream);
hx.setFeedStream(1, coldStream);
hx.setUAvalue(5000.0);  // W/K

ProcessSystem process = new ProcessSystem();
process.add(hotStream);
process.add(coldStream);
process.add(hx);
process.run();

System.out.println("Hot stream outlet T: " + hx.getOutStream(0).getTemperature("C") + " °C");
System.out.println("Cold stream outlet T: " + hx.getOutStream(1).getTemperature("C") + " °C");
System.out.println("Duty: " + hx.getDuty() / 1000.0 + " kW");
```

### Complete Gas Processing Plant

A more complete example with multiple unit operations.

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.separator.*;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.*;
import neqsim.process.equipment.mixer.StaticMixer;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// Rich gas feed
SystemSrkEos richGas = new SystemSrkEos(310.0, 70.0);
richGas.addComponent("nitrogen", 0.01);
richGas.addComponent("CO2", 0.02);
richGas.addComponent("methane", 0.75);
richGas.addComponent("ethane", 0.10);
richGas.addComponent("propane", 0.06);
richGas.addComponent("i-butane", 0.02);
richGas.addComponent("n-butane", 0.02);
richGas.addComponent("i-pentane", 0.01);
richGas.addComponent("n-pentane", 0.01);
richGas.setMixingRule("classic");

Stream feed = new Stream("Rich Gas Feed", richGas);
feed.setFlowRate(100000.0, "Sm3/day");
feed.setTemperature(35.0, "C");
feed.setPressure(70.0, "bara");

// Inlet scrubber
Separator inletScrubber = new Separator("Inlet Scrubber", feed);

// Gas cooling
Cooler gasCooler = new Cooler("Gas Cooler", inletScrubber.getGasOutStream());
gasCooler.setOutTemperature(-20.0, "C");

// Cold separator (NGL recovery)
Separator coldSeparator = new Separator("Cold Separator", gasCooler.getOutletStream());

// Sales gas compression
Compressor salesCompressor = new Compressor("Sales Gas Compressor", coldSeparator.getGasOutStream());
salesCompressor.setOutletPressure(150.0, "bara");
salesCompressor.setIsentropicEfficiency(0.75);

// Build and run
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(inletScrubber);
process.add(gasCooler);
process.add(coldSeparator);
process.add(salesCompressor);
process.run();

// Results
System.out.println("=== Gas Processing Results ===");
System.out.println("Feed rate: " + feed.getFlowRate("MSm3/day") + " MSm³/day");
System.out.println("Sales gas rate: " + salesCompressor.getOutletStream().getFlowRate("MSm3/day") + " MSm³/day");
System.out.println("NGL rate: " + coldSeparator.getLiquidOutStream().getFlowRate("kg/hr") + " kg/hr");
System.out.println("Compressor power: " + salesCompressor.getPower("MW") + " MW");
```

---

## Pipeline Calculations

Multiphase pipeline pressure drop calculation.

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// Multiphase fluid
SystemSrkEos fluid = new SystemSrkEos(320.0, 80.0);
fluid.addComponent("methane", 0.75);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.08);
fluid.addComponent("n-pentane", 0.05);
fluid.addComponent("n-heptane", 0.02);
fluid.setMixingRule("classic");

Stream inlet = new Stream("Pipeline Inlet", fluid);
inlet.setFlowRate(50000.0, "kg/hr");
inlet.setTemperature(45.0, "C");
inlet.setPressure(80.0, "bara");

// Pipeline
PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("Export Pipeline", inlet);
pipeline.setLength(50000.0);           // 50 km
pipeline.setDiameter(0.3048);          // 12 inch
pipeline.setElevation(100.0);          // 100m elevation gain
pipeline.setPipeWallRoughness(4.5e-5); // Steel pipe
pipeline.setNumberOfIncrements(20);

ProcessSystem process = new ProcessSystem();
process.add(inlet);
process.add(pipeline);
process.run();

System.out.println("=== Pipeline Results ===");
System.out.println("Inlet pressure: " + inlet.getPressure("bara") + " bara");
System.out.println("Outlet pressure: " + pipeline.getOutletStream().getPressure("bara") + " bara");
System.out.println("Pressure drop: " + (inlet.getPressure("bara") - pipeline.getOutletStream().getPressure("bara")) + " bar");
System.out.println("Outlet temperature: " + pipeline.getOutletStream().getTemperature("C") + " °C");
System.out.println("Flow regime: " + pipeline.getFlowRegime());
System.out.println("Liquid holdup: " + pipeline.getLiquidHoldup());
```

---

## Specialized Equipment

### Wind Turbine

The `WindTurbine` unit converts kinetic energy in wind into electrical power using a simple
actuator-disk formulation. Air density is assumed constant at 1.225 kg/m³ and all inefficiencies
are lumped into the power coefficient.

```java
import neqsim.process.equipment.powergeneration.WindTurbine;

WindTurbine turbine = new WindTurbine("turbine");
turbine.setWindSpeed(12.0);    // m/s
turbine.setRotorArea(50.0);    // m²
turbine.setPowerCoefficient(0.4);
turbine.run();

System.out.println("Power produced: " + turbine.getPower() + " W");
```

### Electrolyzer

Water electrolysis for hydrogen production.

```java
import neqsim.process.equipment.electrolyzer.Electrolyzer;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Water feed
SystemSrkEos water = new SystemSrkEos(298.15, 1.0);
water.addComponent("water", 1.0);
water.setMixingRule("classic");

Stream waterFeed = new Stream("Water Feed", water);
waterFeed.setFlowRate(100.0, "kg/hr");

Electrolyzer electrolyzer = new Electrolyzer("PEM Electrolyzer", waterFeed);
electrolyzer.setEfficiency(0.70);
electrolyzer.run();

System.out.println("H2 production rate: " + electrolyzer.getHydrogenOutStream().getFlowRate("kg/hr") + " kg/hr");
System.out.println("O2 production rate: " + electrolyzer.getOxygenOutStream().getFlowRate("kg/hr") + " kg/hr");
System.out.println("Power consumption: " + electrolyzer.getPower("kW") + " kW");
```

### Membrane Separator

Gas separation using membrane technology.

```java
import neqsim.process.equipment.separator.MembraneSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

SystemSrkEos gas = new SystemSrkEos(298.15, 50.0);
gas.addComponent("CO2", 0.30);
gas.addComponent("methane", 0.70);
gas.setMixingRule("classic");

Stream feed = new Stream("Feed", gas);
feed.setFlowRate(1000.0, "Sm3/hr");

MembraneSeparator membrane = new MembraneSeparator("CO2 Membrane", feed);
membrane.setRelativePermability("CO2", 20.0);
membrane.setRelativePermability("methane", 1.0);
membrane.setMembranePressureDrop(30.0, "bara");
membrane.run();

System.out.println("Permeate CO2: " + membrane.getPermeateStream().getFluid().getComponent("CO2").getx() * 100 + " mol%");
System.out.println("Retentate CO2: " + membrane.getRetentateStream().getFluid().getComponent("CO2").getx() * 100 + " mol%");
```

---

## Additional Resources

For more examples, see:
- [Java Test Suite](https://github.com/equinor/neqsim/tree/master/src/test/java/neqsim) - Extensive test cases demonstrating functionality
- [Process Simulation Guide](process_simulation.md) - Detailed process simulation documentation
- [Thermodynamics Guide](thermodynamics_guide.md) - Complete thermodynamic modeling guide
- [Pipeline Index](pipeline_index.md) - Pipeline modeling documentation
- [Example Notebooks](https://github.com/equinor/neqsim/tree/master/docs/examples) - Jupyter notebook examples
