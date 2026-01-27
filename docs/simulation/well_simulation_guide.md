# NeqSim Well Simulation Guide

This guide covers NeqSim's well simulation capabilities, providing functionality for
production system modeling including IPR models, VLP correlations, operating point calculation,
lift curve generation, and multi-layer commingled production.

## Overview

NeqSim provides three main classes for well simulation:

| Class                 | Purpose                  | Key Features                                             |
| --------------------- | ------------------------ | -------------------------------------------------------- |
| `WellFlow`          | Inflow Performance (IPR) | Vogel, Fetkovich, Backpressure, Table, Multi-layer       |
| `TubingPerformance` | Vertical Lift (VLP)      | Beggs-Brill, Hagedorn-Brown, Gray, Hasan-Kabir, Duns-Ros |
| `WellSystem`        | Integrated Well Model    | IPR+VLP coupling, Operating point solver, Lift curves    |

## Table of Contents

1. [Inflow Performance Relationships (IPR)](#inflow-performance-relationships-ipr)
2. [Vertical Lift Performance (VLP)](#vertical-lift-performance-vlp)
3. [Operating Point Calculation](#operating-point-calculation)
4. [Lift Curve Generation](#lift-curve-generation)
5. [Multi-Layer Commingled Wells](#multi-layer-commingled-wells)
6. [Temperature Models](#temperature-models)
7. [Integration with Process Simulation](#integration-with-process-simulation)
8. [Complete Examples](#complete-examples)

---

## Inflow Performance Relationships (IPR)

The `WellFlow` class models reservoir-to-wellbore inflow using several IPR models.

### Available IPR Models

#### 1. Production Index (Darcy Flow)

For single-phase or undersaturated liquid flow:

```
q = PI × (P_res² - P_wf²)
```

```java
WellFlow well = new WellFlow("producer");
well.setInletStream(reservoirStream);
well.setWellProductionIndex(1.5e-6); // Sm³/day/bar²
well.setOutletPressure(150.0, "bara");
well.solveFlowFromOutletPressure(true);
well.run();
System.out.println("Flow rate: " + well.getOutletStream().getFlowRate("MSm3/day"));
```

#### 2. Vogel Equation (1968)

For solution-gas-drive reservoirs below bubble point:

```
q/q_max = 1 - 0.2(P_wf/P_res) - 0.8(P_wf/P_res)²
```

```java
// From well test data: 500 Sm³/day at 120 bara, reservoir at 200 bara
well.setVogelIPR(500.0, 120.0, 200.0);
well.setOutletPressure(100.0, "bara");
well.solveFlowFromOutletPressure(true);
well.run();
```

#### 3. Fetkovich Equation (1973)

Empirical model for gas wells:

```
q = C × (P_res² - P_wf²)^n
```

```java
well.setFetkovichIPR(0.012, 0.85);  // C and n coefficients
```

#### 4. Backpressure Equation

Gas wells with non-Darcy (turbulent) flow:

```
P_res² - P_wf² = A×q + B×q²
```

Where A is the Darcy term and B is the non-Darcy (rate-dependent) term.

```java
well.setBackpressureIPR(0.5, 0.001);  // A and B coefficients
```

#### 5. Table-Driven IPR

For measured IPR curves from well tests:

```java
double[] pressures = {50, 80, 100, 120, 150, 180};  // bara
double[] rates = {2.5, 2.0, 1.6, 1.2, 0.7, 0.2};    // MSm³/day
well.setTableIPR(pressures, rates);
```

#### 6. Loading IPR from CSV File

Load IPR curves from external files (e.g., from well test analysis software):

```java
// Load IPR curve from CSV file
WellFlow well = new WellFlow("producer");
well.setInletStream(reservoirStream);
well.loadIPRFromFile("path/to/ipr_curve.csv");
well.run();
```

CSV file format:
```csv
Pwf(bara),Rate(MSm3/day)
50,5.2
80,4.1
100,3.2
120,2.4
150,1.5
180,0.8
200,0.2
```

---

## Vertical Lift Performance (VLP)

The `TubingPerformance` class calculates pressure drop in tubing using multiphase correlations.

### Available VLP Correlations

| Correlation    | Best For           | Flow Patterns |
| -------------- | ------------------ | ------------- |
| Beggs-Brill    | All inclinations   | All patterns  |
| Hagedorn-Brown | Vertical oil wells | Slug, bubble  |
| Gray           | Gas wells          | Mist, annular |
| Hasan-Kabir    | Mechanistic        | All patterns  |
| Duns-Ros       | Gas-liquid         | All patterns  |

### Basic VLP Calculation

```java
import neqsim.process.equipment.pipeline.TubingPerformance;
import neqsim.thermo.system.SystemSrkEos;

// Create tubing model
TubingPerformance tubing = new TubingPerformance("tubing");
tubing.setInletStream(feedStream);
tubing.setDiameter(0.1);          // 100 mm ID
tubing.setLength(3000.0);         // 3000 m TVD
tubing.setInclination(90.0);      // Vertical
tubing.setRoughness(0.00005);     // 50 microns

// Select correlation
tubing.setCorrelationType(TubingPerformance.CorrelationType.BEGGS_BRILL);

// Run calculation
tubing.run();

// Get results
double outletPressure = tubing.getOutletStream().getPressure("bara");
double pressureDrop = tubing.getPressureDrop();
```

### Setting VLP Correlation

```java
// Beggs-Brill (default, all inclinations)
tubing.setCorrelationType(TubingPerformance.CorrelationType.BEGGS_BRILL);

// Hagedorn-Brown (vertical oil wells)
tubing.setCorrelationType(TubingPerformance.CorrelationType.HAGEDORN_BROWN);

// Gray (gas wells)
tubing.setCorrelationType(TubingPerformance.CorrelationType.GRAY);

// Hasan-Kabir (mechanistic)
tubing.setCorrelationType(TubingPerformance.CorrelationType.HASAN_KABIR);

// Duns-Ros (gas-liquid)
tubing.setCorrelationType(TubingPerformance.CorrelationType.DUNS_ROS);
```

### Table-Based VLP

Use pre-calculated or measured VLP curves instead of correlations:

```java
// Set VLP table programmatically
double[] flowRates = {0.5, 1.0, 2.0, 3.0, 4.0, 5.0};  // MSm³/day
double[] bhpValues = {85, 92, 115, 145, 182, 225};    // bara
double whp = 50.0;  // Wellhead pressure (bara)

TubingPerformance tubing = new TubingPerformance("tubing");
tubing.setTableVLP(flowRates, bhpValues, whp);

// Interpolate BHP for a given flow rate
double bhp = tubing.interpolateBHPFromTable(2.5);  // MSm³/day
```

### Loading VLP from CSV File

Load VLP curves from external files (e.g., from PROSPER, Pipesim, or other tools):

```java
TubingPerformance tubing = new TubingPerformance("tubing");
tubing.loadVLPFromFile("path/to/vlp_curve.csv", 50.0);  // WHP = 50 bara

// Use interpolation
double bhp = tubing.interpolateBHPFromTable(3.0);  // Get BHP at 3 MSm³/day
```

CSV file format:
```csv
FlowRate(MSm3/day),BHP(bara)
0.5,85
1.0,92
2.0,115
3.0,145
4.0,182
5.0,225
```

---

## Operating Point Calculation

The `WellSystem` class finds the intersection of IPR and VLP curves using an optimized
bisection algorithm.

### Using WellSystem for Operating Point

```java
import neqsim.process.equipment.reservoir.WellSystem;
import neqsim.process.equipment.stream.Stream;

// Create reservoir stream
Stream reservoirStream = new Stream("reservoir", reservoirFluid);
reservoirStream.setFlowRate(5000.0, "Sm3/day");
reservoirStream.setTemperature(100.0, "C");
reservoirStream.setPressure(280.0, "bara");

// Create well system with inlet stream
WellSystem well = new WellSystem("production_well", reservoirStream);

// Configure IPR model
well.setIPRModel(WellSystem.IPRModel.PRODUCTION_INDEX);
well.setProductionIndex(2.5e-6, "Sm3/day/bar2");

// Configure tubing (VLP)
well.setWellheadPressure(60.0, "bara");
well.setTubingDiameter(4.0, "in");
well.setTubingLength(3000.0, "m");
well.setInclination(85.0);  // degrees from horizontal

// Configure temperature model
well.setBottomHoleTemperature(100.0, "C");
well.setWellheadTemperature(50.0, "C");

// Find operating point
well.run();

// Results
double flowRate = well.getOperatingFlowRate("Sm3/day");
double bhp = well.getBottomHolePressure("bara");
double drawdown = well.getDrawdown("bar");
System.out.println("Operating point: " + flowRate + " Sm³/day at " + bhp + " bara BHP");
System.out.println("Drawdown: " + drawdown + " bar");
```

### IPR Models Available

| Model | Enum Value | Parameters |
| ----- | ---------- | ---------- |
| Production Index | `PRODUCTION_INDEX` | `setProductionIndex(pi, unit)` |
| Vogel (1968) | `VOGEL` | `setVogelParameters(qMax, pwfTest, pRes)` |
| Fetkovich (1973) | `FETKOVICH` | `setFetkovichParameters(C, n, pRes)` |
| Backpressure | `BACKPRESSURE` | `setBackpressureParameters(A, B)` |

### Operating Point Methods

| Method                         | Description                             |
| ------------------------------ | --------------------------------------- |
| `getOperatingFlowRate(unit)` | Flow rate at IPR-VLP intersection       |
| `getBottomHolePressure(unit)` | Bottom-hole pressure at operating point |
| `getWellheadPressure(unit)`  | Wellhead pressure (target constraint)   |
| `getDrawdown(unit)`          | Reservoir pressure - BHP                |
| `getOutletStream()`          | Output stream for downstream connection |

---

## Lift Curve Generation

Generate IPR and VLP curves for nodal analysis.

### Generating VLP Curve (Tubing Performance)

```java
TubingPerformance tubing = new TubingPerformance("tubing");
tubing.setInletStream(gasStream);
tubing.setDiameter(0.1);
tubing.setLength(3000.0);
tubing.setCorrelationType(TubingPerformance.CorrelationType.BEGGS_BRILL);

// Generate VLP curve
double[] flowRates = {0.1, 0.5, 1.0, 2.0, 3.0, 4.0, 5.0};  // MSm³/day
double[][] vlpCurve = tubing.generateVLPCurve(flowRates);

// vlpCurve[0] = flow rates
// vlpCurve[1] = required bottom-hole pressures
for (int i = 0; i < vlpCurve[0].length; i++) {
    System.out.printf("Flow: %.2f MSm³/day, BHP: %.1f bara%n",
        vlpCurve[0][i], vlpCurve[1][i]);
}
```

### Generating IPR Curve

```java
WellSystem well = new WellSystem("producer");
well.setReservoirPressure(280.0, "bara");
well.setProductivityIndex(2.5e-6);
well.setIprModel(WellSystem.IPRModel.PRODUCTION_INDEX);

// Generate IPR curve (flowing BHP vs flow rate)
double minPwf = 50.0;
double maxPwf = 270.0;
int points = 20;
double[][] iprCurve = well.generateIPRCurve(minPwf, maxPwf, points);

// iprCurve[0] = flowing BHP values
// iprCurve[1] = corresponding flow rates
```

### Combined Nodal Analysis Plot Data

```java
WellSystem well = new WellSystem("nodal_analysis");
// ... configure well ...

// Get both curves
double[][] iprCurve = well.generateIPRCurve(50, 270, 20);
double[][] vlpCurve = well.generateVLPCurve(new double[]{0.5, 1.0, 2.0, 3.0, 4.0, 5.0});

// Operating point
well.run();
double opFlow = well.getOperatingFlowRate("MSm3/day");
double opBHP = well.getOperatingBHP("bara");

// Export to CSV or plot
System.out.println("IPR Curve:");
for (int i = 0; i < iprCurve[0].length; i++) {
    System.out.printf("%.1f, %.3f%n", iprCurve[0][i], iprCurve[1][i]);
}
System.out.println("\nVLP Curve:");
for (int i = 0; i < vlpCurve[0].length; i++) {
    System.out.printf("%.3f, %.1f%n", vlpCurve[0][i], vlpCurve[1][i]);
}
System.out.printf("\nOperating Point: %.3f MSm³/day at %.1f bara%n", opFlow, opBHP);
```

---

## Multi-Layer Commingled Wells

Model wells producing from multiple reservoir layers.

### Using WellFlow for Multi-Layer

```java
// Create fluid streams for each layer
SystemInterface layer1Fluid = new SystemSrkEos(80, 200);
layer1Fluid.addComponent("methane", 0.90);
layer1Fluid.addComponent("ethane", 0.07);
layer1Fluid.addComponent("propane", 0.03);
layer1Fluid.setMixingRule("classic");
Stream layer1Stream = new Stream("layer1", layer1Fluid);
layer1Stream.run();

SystemInterface layer2Fluid = new SystemSrkEos(95, 220);
layer2Fluid.addComponent("methane", 0.85);
layer2Fluid.addComponent("ethane", 0.10);
layer2Fluid.addComponent("propane", 0.05);
layer2Fluid.setMixingRule("classic");
Stream layer2Stream = new Stream("layer2", layer2Fluid);
layer2Stream.run();

// Create multi-layer well
WellFlow well = new WellFlow("commingled_well");
well.addLayer("Upper Sand", layer1Stream, 200.0, 1.0e-6);  // 200 bara, PI
well.addLayer("Lower Sand", layer2Stream, 220.0, 1.5e-6);  // 220 bara, PI
well.setOutletPressure(150.0, "bara");  // Common BHP
well.run();

// Get individual layer contributions
double[] layerRates = well.getLayerFlowRates("MSm3/day");
System.out.println("Layer 1 flow: " + layerRates[0] + " MSm³/day");
System.out.println("Layer 2 flow: " + layerRates[1] + " MSm³/day");
System.out.println("Total flow: " + well.getOutletStream().getFlowRate("MSm3/day"));
```

### Using WellSystem for Multi-Layer

```java
WellSystem well = new WellSystem("multi_zone_producer");
well.setWellheadPressure(50.0, "bara");
well.setTubingDiameter(0.1);
well.setTubingLength(3500.0);

// Add layers with different properties
well.addLayer("Zone A", streamA, 250.0, 1.2e-6);
well.addLayer("Zone B", streamB, 280.0, 0.8e-6);
well.addLayer("Zone C", streamC, 265.0, 1.5e-6);

// Find operating point for commingled production
well.run();

double totalFlow = well.getOperatingFlowRate("MSm3/day");
double bhp = well.getOperatingBHP("bara");
double[] zoneFlows = well.getLayerFlowRates("MSm3/day");
```

---

## Temperature Models

Configure wellbore temperature profile for accurate property calculations.

### Available Temperature Models

| Model           | Description               | Use Case               |
| --------------- | ------------------------- | ---------------------- |
| ISOTHERMAL      | Constant temperature      | Quick estimates        |
| LINEAR_GRADIENT | Linear geothermal         | Simple wells           |
| RAMEY           | Ramey (1962) steady-state | Established production |
| HASAN_KABIR     | Energy balance            | Transient, accurate    |

### Setting Temperature Model

```java
// Isothermal (default)
tubing.setTemperatureModel(TubingPerformance.TemperatureModel.ISOTHERMAL);

// Linear gradient (specify surface and BH temperatures)
tubing.setTemperatureModel(TubingPerformance.TemperatureModel.LINEAR_GRADIENT);
tubing.setSurfaceTemperature(25.0);   // °C
tubing.setBottomholeTemperature(90.0);

// Ramey model (needs formation properties)
tubing.setTemperatureModel(TubingPerformance.TemperatureModel.RAMEY);
tubing.setFormationThermalConductivity(2.5);  // W/m·K
tubing.setOverallHeatTransferCoefficient(25.0);

// Hasan-Kabir energy balance
tubing.setTemperatureModel(TubingPerformance.TemperatureModel.HASAN_KABIR);
```

### Ramey Temperature Model

The Ramey (1962) model accounts for:

- Geothermal gradient
- Heat transfer to formation
- Joule-Thomson effects
- Production time dependency

```java
tubing.setTemperatureModel(TubingPerformance.TemperatureModel.RAMEY);
tubing.setGeothermalGradient(0.03);    // °C/m
tubing.setSurfaceTemperature(15.0);     // °C
tubing.setFormationThermalConductivity(2.5);
tubing.setOverallHeatTransferCoefficient(20.0);
tubing.setProductionTime(365.0);        // days
```

---

## Integration with Process Simulation

### Complete Well + Process System

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.reservoir.*;
import neqsim.process.equipment.pipeline.TubingPerformance;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.compressor.Compressor;

// 1. Reservoir and Well
SimpleReservoir reservoir = new SimpleReservoir("reservoir");
reservoir.setReservoirFluid(reservoirFluid);
reservoir.setReservoirPressure(280.0, "bara");
reservoir.setTemperature(85.0, "C");

WellFlow inflow = new WellFlow("IPR");
inflow.setInletStream(reservoir.getOutletStream());
inflow.setWellProductionIndex(2.0e-6);

// 2. Tubing
TubingPerformance tubing = new TubingPerformance("tubing");
tubing.setInletStream(inflow.getOutletStream());
tubing.setDiameter(0.1);
tubing.setLength(3000.0);
tubing.setCorrelationType(TubingPerformance.CorrelationType.BEGGS_BRILL);

// 3. Surface Facilities
Separator separator = new Separator("HP_sep");
separator.setInletStream(tubing.getOutletStream());

Compressor compressor = new Compressor("export_comp");
compressor.setInletStream(separator.getGasOutStream());
compressor.setOutletPressure(150.0, "bara");

// 4. Build Process System
ProcessSystem plant = new ProcessSystem();
plant.add(reservoir);
plant.add(inflow);
plant.add(tubing);
plant.add(separator);
plant.add(compressor);

// 5. Run
plant.run();

// 6. Results
System.out.println("Wellhead pressure: " + tubing.getOutletStream().getPressure("bara"));
System.out.println("Gas export rate: " + compressor.getOutletStream().getFlowRate("MSm3/day"));
```

### Coupling with WellFlowlineNetwork

```java
// Multiple wells feeding a gathering network
WellFlowlineNetwork network = new WellFlowlineNetwork("field_network");

// Add wells
network.addWell(well1);
network.addWell(well2);
network.addWell(well3);

// Set manifold back-pressure
network.setManifoldPressure(40.0, "bara");

// Solve network
network.run();

// Get individual well rates
for (WellFlow well : network.getWells()) {
    System.out.println(well.getName() + ": " + 
        well.getOutletStream().getFlowRate("MSm3/day") + " MSm³/day");
}
```

---

## Complete Examples

### Example 1: Gas Well Analysis

```java
// Rich gas well with Vogel IPR and Beggs-Brill VLP
SystemInterface gas = new SystemSrkEos(85, 250);
gas.addComponent("nitrogen", 0.02);
gas.addComponent("CO2", 0.03);
gas.addComponent("methane", 0.80);
gas.addComponent("ethane", 0.08);
gas.addComponent("propane", 0.04);
gas.addComponent("n-butane", 0.02);
gas.addComponent("n-pentane", 0.01);
gas.setMixingRule("classic");
gas.init(0);

Stream reservoir = new Stream("reservoir", gas);
reservoir.setFlowRate(3.0, "MSm3/day");
reservoir.run();

// Well system
WellSystem well = new WellSystem("gas_producer");
well.setInletStream(reservoir);
well.setReservoirPressure(250.0, "bara");
well.setProductivityIndex(3.0e-6);
well.setWellheadPressure(50.0, "bara");
well.setTubingDiameter(0.088);  // 3.5" tubing
well.setTubingLength(2800.0);
well.setVlpCorrelation(WellSystem.VLPCorrelation.GRAY);
well.run();

System.out.println("=== Gas Well Operating Point ===");
System.out.println("Flow rate: " + well.getOperatingFlowRate("MSm3/day") + " MSm³/day");
System.out.println("BHP: " + well.getOperatingBHP("bara") + " bara");
System.out.println("Drawdown: " + well.getDrawdown() + " bar");
```

### Example 2: Oil Well with Artificial Lift Comparison

```java
// Compare production with different wellhead pressures (simulating lift)
WellSystem well = new WellSystem("oil_producer");
well.setReservoirPressure(180.0, "bara");
well.setProductivityIndex(5.0e-6);
well.setTubingDiameter(0.076);  // 3" tubing
well.setTubingLength(2000.0);
well.setVlpCorrelation(WellSystem.VLPCorrelation.HAGEDORN_BROWN);

System.out.println("Wellhead Pressure | Flow Rate | BHP");
for (double whp = 10; whp <= 50; whp += 10) {
    well.setWellheadPressure(whp, "bara");
    well.run();
    System.out.printf("%17.0f | %9.2f | %6.1f%n",
        whp, well.getOperatingFlowRate("Sm3/day"), well.getOperatingBHP("bara"));
}
```

### Example 3: Multi-Zone Completion

```java
// Three-zone commingled gas well
WellSystem well = new WellSystem("multizone_gas");
well.setWellheadPressure(45.0, "bara");
well.setTubingDiameter(0.1);
well.setTubingLength(3200.0);

// Zone A: Shallow gas, high perm
well.addLayer("Zone_A", shallowStream, 180.0, 4.0e-6);
// Zone B: Middle, moderate perm
well.addLayer("Zone_B", middleStream, 220.0, 2.0e-6);
// Zone C: Deep, low perm but high pressure
well.addLayer("Zone_C", deepStream, 280.0, 0.8e-6);

well.run();

System.out.println("=== Multi-Zone Production ===");
System.out.println("Total rate: " + well.getOperatingFlowRate("MSm3/day") + " MSm³/day");
double[] zoneFlows = well.getLayerFlowRates("MSm3/day");
System.out.println("Zone A: " + zoneFlows[0] + " MSm³/day");
System.out.println("Zone B: " + zoneFlows[1] + " MSm³/day");
System.out.println("Zone C: " + zoneFlows[2] + " MSm³/day");
```

---

## API Reference Summary

### WellFlow (IPR)

| Method                                | Description                  |
| ------------------------------------- | ---------------------------- |
| `setWellProductionIndex(pi)`        | Set productivity index       |
| `setVogelIPR(qTest, pwfTest, pRes)` | Configure Vogel IPR          |
| `setFetkovichIPR(c, n)`             | Configure Fetkovich IPR      |
| `setBackpressureIPR(a, b)`          | Configure Backpressure IPR   |
| `setTableIPR(pwf[], rate[])`        | Set table-driven IPR         |
| `addLayer(name, stream, pRes, pi)`  | Add reservoir layer          |
| `getLayerFlowRates(unit)`           | Get layer flow contributions |

### TubingPerformance (VLP)

| Method                         | Description                               |
| ------------------------------ | ----------------------------------------- |
| `setDiameter(d)`             | Set tubing ID (meters)                    |
| `setLength(L)`               | Set tubing length (meters)                |
| `setInclination(angle)`      | Set inclination (degrees from horizontal) |
| `setRoughness(eps)`          | Set pipe roughness (meters)               |
| `setCorrelationType(type)`   | Select VLP correlation                    |
| `setTemperatureModel(model)` | Select temperature model                  |
| `generateVLPCurve(rates)`    | Generate lift curve                       |
| `getPressureDrop()`          | Get calculated pressure drop              |

### WellSystem (Integrated)

| Method                            | Description                |
| --------------------------------- | -------------------------- |
| `setReservoirPressure(p, unit)` | Set reservoir pressure     |
| `setProductivityIndex(pi)`      | Set well PI                |
| `setWellheadPressure(p, unit)`  | Set tubing outlet pressure |
| `setIprModel(model)`            | Select IPR model           |
| `setVlpCorrelation(corr)`       | Select VLP correlation     |
| `getOperatingFlowRate(unit)`    | Get operating flow rate    |
| `getOperatingBHP(unit)`         | Get operating BHP          |
| `generateIPRCurve(min, max, n)` | Generate IPR curve         |
| `generateVLPCurve(rates)`       | Generate VLP curve         |

---

## Complete Production System Example

For a comprehensive example demonstrating the full integration of well simulation with
downstream processing, see [WellToOilStabilizationExample.java](examples/WellToOilStabilizationExample.java).

This example includes:
- **Reservoir**: SimpleReservoir with oil, gas, and water phases
- **Integrated Well System**: WellSystem with Vogel IPR model and optimized VLP solver
- **Flowline**: 5 km pipeline with PipeBeggsAndBrills multiphase flow
- **Choke Valve**: ThrottlingValve with outlet pressure control
- **Oil Stabilization**: Three-stage separation train (HP/MP/LP)
- **Gas Compression**: Multi-stage compression with intercooling
- **Process System**: Complete flowsheet integration

### Using WellSystem in ProcessSystem

The `WellSystem` class integrates seamlessly with `ProcessSystem` for building complete
production flowsheets. It uses an optimized IPR+VLP solver with:

- **Bisection algorithm** for robust convergence
- **Simplified VLP correlation** for fast pressure drop calculation
- **Direct outlet stream access** via `getOutletStream()`

```java
// Create reservoir and reservoir stream
SimpleReservoir reservoir = new SimpleReservoir("Main Reservoir");
reservoir.setReservoirFluid(reservoirFluid, 1e6, 10.0, 10.0);

Stream reservoirStream = new Stream("Reservoir Stream", reservoir.getReservoirFluid());
reservoirStream.setFlowRate(5000.0, "Sm3/day");
reservoirStream.setTemperature(100.0, "C");
reservoirStream.setPressure(250.0, "bara");

// Create WellSystem with integrated IPR and VLP
WellSystem well = new WellSystem("Producer-1", reservoirStream);

// Configure IPR model (Vogel for solution gas drive)
well.setIPRModel(WellSystem.IPRModel.VOGEL);
well.setVogelParameters(8000.0, 180.0, 250.0); // qMax, testPwf, Pr

// Configure VLP (tubing)
well.setTubingLength(2500.0, "m");
well.setTubingDiameter(4.0, "in");
well.setWellheadPressure(80.0, "bara");
well.setBottomHoleTemperature(100.0, "C");
well.setWellheadTemperature(65.0, "C");

// Connect downstream equipment
PipeBeggsAndBrills flowline = new PipeBeggsAndBrills("Flowline");
flowline.setInletStream(well.getOutletStream());
flowline.setLength(5000.0);
flowline.setDiameter(0.2);

// Build complete ProcessSystem
ProcessSystem process = new ProcessSystem();
process.add(well);          // WellSystem as first equipment
process.add(flowline);
process.add(inletChoke);
process.add(hpSeparator);
// ... add remaining equipment

// Run complete simulation
process.run();

// Access well operating point results
double opRate = well.getOperatingFlowRate("Sm3/day");
double bhp = well.getBottomHolePressure("bara");
double drawdown = well.getDrawdown("bar");
```

### Performance Considerations

The `WellSystem` solver is optimized for speed:

| Feature | Description |
| ------- | ----------- |
| Simplified VLP | Uses hydrostatic + friction correlation instead of full TubingPerformance iteration |
| Bisection solver | Robust convergence in ~15-20 iterations |
| Single flash per iteration | Minimizes thermodynamic calculations |
| Typical solve time | < 1 second for complex fluids |

For detailed VLP calculations with full correlation support, use `TubingPerformance` directly.

### VLP Solver Modes

`WellSystem` supports multiple VLP solver modes for different accuracy/speed tradeoffs:

```java
import neqsim.process.equipment.reservoir.WellSystem.VLPSolverMode;

// Default: Fast simplified solver (hydrostatic + friction)
well.setVLPSolverMode(VLPSolverMode.SIMPLIFIED);

// Traditional correlations (via TubingPerformance)
well.setVLPSolverMode(VLPSolverMode.BEGGS_BRILL);
well.setVLPSolverMode(VLPSolverMode.HAGEDORN_BROWN);
well.setVLPSolverMode(VLPSolverMode.GRAY);
well.setVLPSolverMode(VLPSolverMode.HASAN_KABIR);
well.setVLPSolverMode(VLPSolverMode.DUNS_ROS);

// Advanced multiphase models
well.setVLPSolverMode(VLPSolverMode.DRIFT_FLUX);  // Drift-flux with slip
well.setVLPSolverMode(VLPSolverMode.TWO_FLUID);   // Separate momentum equations
```

| VLP Solver Mode | Description | Speed | Accuracy |
| --------------- | ----------- | ----- | -------- |
| SIMPLIFIED | Hydrostatic + friction correlation | Fastest | Approximate |
| BEGGS_BRILL | Beggs & Brill empirical correlation | Medium | Good for general use |
| HAGEDORN_BROWN | Hagedorn-Brown for vertical wells | Medium | Good for oil wells |
| GRAY | Gray correlation for gas wells | Medium | Good for gas wells |
| HASAN_KABIR | Mechanistic model | Slow | High accuracy |
| DUNS_ROS | Duns & Ros correlation | Medium | Good for gas-liquid |
| DRIFT_FLUX | Accounts for phase slip velocity | Medium | Better for high GOR |
| TWO_FLUID | Separate gas/liquid momentum | Slowest | Highest accuracy |

---

## See Also

- [Wells Documentation](../process/equipment/wells.md) - Well models and IPR
- [Pipeline Modeling](../process/PipeBeggsAndBrills.md) - PipeBeggsAndBrills correlation
- [Process System](../process/processmodel/process_system.md) - ProcessSystem class
- [NeqSim Modules](../modules.md) - Module overview

## References

1. Vogel, J.V. (1968). "Inflow Performance Relationships for Solution-Gas Drive Wells"
2. Fetkovich, M.J. (1973). "The Isochronal Testing of Oil Wells"
3. Beggs, H.D. and Brill, J.P. (1973). "A Study of Two-Phase Flow in Inclined Pipes"
4. Hagedorn, A.R. and Brown, K.E. (1965). "Experimental Study of Pressure Gradients..."
5. Gray, H.E. (1974). "Vertical Flow Correlation in Gas Wells"
6. Hasan, A.R. and Kabir, C.S. (2002). "Fluid Flow and Heat Transfer in Wellbores"
7. Ramey, H.J. (1962). "Wellbore Heat Transmission"
