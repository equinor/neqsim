---
title: NeqSim Industrial MPC Integration Guide
description: This document describes how NeqSim thermodynamic and process simulation capabilities can be integrated with industrial Model Predictive Control (MPC) systems for real-time optimization and production ...
---

# NeqSim Industrial MPC Integration Guide

This document describes how NeqSim thermodynamic and process simulation capabilities can be integrated with industrial Model Predictive Control (MPC) systems for real-time optimization and production optimization.

## Table of Contents

1. [Overview](#overview)
2. [Integration Architecture](#integration-architecture)
3. [Model Generation Workflow](#model-generation-workflow)
4. [Integration Patterns](#integration-patterns)
5. [Production Optimization](#production-optimization)
6. [Bottleneck Analysis and Resolution](#bottleneck-analysis-and-resolution)
7. [Soft Sensor Integration](#soft-sensor-integration)
8. [Gain Scheduling](#gain-scheduling)
9. [Model Validation](#model-validation)
10. [Implementation Examples](#implementation-examples)

---

## Overview

### The Complementary Roles

**NeqSim** and industrial MPC systems serve complementary roles in process control and optimization:

| Aspect | NeqSim | Industrial MPC |
|--------|--------|----------------|
| **Primary Function** | Rigorous thermodynamic calculations | Real-time control execution |
| **Execution Time** | Seconds to minutes | Milliseconds |
| **Model Type** | First-principles, nonlinear | Linear/simplified nonlinear |
| **Usage** | Offline analysis, model generation | Online control, optimization |
| **Accuracy** | High-fidelity physics | Operational accuracy |

### Integration Benefits

- **Physics-Based Models**: NeqSim provides thermodynamically rigorous models for MPC
- **Automatic Linearization**: Calculate local steady-state gains at a solved operating point
- **Property Estimation**: Accurate phase behavior, densities, enthalpies for soft sensors
- **Operating Envelope**: Define safe operating regions based on thermodynamic limits
- **Production Optimization**: Maximize throughput while respecting constraints

---

## Integration Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        ENGINEERING WORKSTATION                          │
│  ┌─────────────────┐    ┌──────────────────┐    ┌──────────────────┐   │
│  │   NeqSim        │───▶│  Model Export    │───▶│  MPC Config      │   │
│  │   Process       │    │  (Step Response, │    │  Files           │   │
│  │   Simulation    │    │   SubrModl, etc) │    │                  │   │
│  └─────────────────┘    └──────────────────┘    └────────┬─────────┘   │
└───────────────────────────────────────────────────────────┼─────────────┘
                                                            │
                    ┌───────────────────────────────────────▼─────────────┐
                    │              INDUSTRIAL MPC SYSTEM                  │
                    │  ┌─────────────────────────────────────────────┐   │
                    │  │              MPC Controller                  │   │
                    │  │  ┌──────────┐  ┌──────────┐  ┌───────────┐  │   │
                    │  │  │ Linear   │  │ Nonlinear│  │ Production│  │   │
                    │  │  │ MPC      │  │ MPC      │  │ Optimizer │  │   │
                    │  │  │ (ExprModl│  │ (SubrModl│  │           │  │   │
                    │  │  │  style)  │  │  style)  │  │           │  │   │
                    │  │  └──────────┘  └──────────┘  └───────────┘  │   │
                    │  └─────────────────────────────────────────────┘   │
                    │                         │                          │
                    │  ┌─────────────────────▼───────────────────────┐   │
                    │  │            Soft Sensors / Estimators         │   │
                    │  │  (Property tables, correlations from NeqSim) │   │
                    │  └──────────────────────────────────────────────┘   │
                    └─────────────────────────────────────────────────────┘
                                              │
                    ┌─────────────────────────▼─────────────────────────┐
                    │                 PROCESS CONTROL SYSTEM            │
                    │           (DCS / PLC / Safety Systems)            │
                    └───────────────────────────────────────────────────┘
                                              │
                    ┌─────────────────────────▼─────────────────────────┐
                    │                    PROCESS PLANT                   │
                    │  (Separators, Compressors, Heat Exchangers, etc)  │
                    └───────────────────────────────────────────────────┘
```

---

## Model Generation Workflow

The fragments below use the imports and equipment names in the [complete example](#complete-separator-control-example).
Run them inside a method accepting `String outputDirectory` and declaring
`throws Exception` for file export. These examples
identify **steady-state gains**. The exporter's first-order time constant is an explicit
assumption; validate dynamics and dead time against plant data or a transient model before
using the model for control. Declaring a disturbance variable does not identify its gains:
`ProcessLinearizer` uses the sensitivities supplied by `setCvSensitivity`.

### Step 1: Build NeqSim Process Model

```java
Path output = Files.createDirectories(Paths.get(outputDirectory));
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-butane", 0.03);
fluid.addComponent("n-pentane", 0.02);
fluid.setMixingRule("classic");

ProcessSystem process = new ProcessSystem();
Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(500.0, "kg/hr");
Separator separator = new Separator("HP Separator", feed);
separator.setInternalDiameter(1.5);
StreamInterface gasProduct = separator.getGasOutStream();
gasProduct.setName("Gas Product");
process.add(feed);
process.add(separator);
process.add(gasProduct); // Register the outlet so the MPC can resolve it by name.
process.run();
```

### Step 2: Configure MPC Variables

```java
ProcessLinkedMPC mpc = new ProcessLinkedMPC("HP_Separator_MPC", process);

// Bounds and setpoints use the units configured on each variable.
mpc.addMV("Feed", "flowRate", 200.0, 800.0).setUnit("kg/hr");
mpc.addMV("Feed", "pressure", 30.0, 70.0).setUnit("bara");
mpc.addCVZone("Gas Product", "flowRate", 100.0, 600.0).setUnit("kg/hr");
mpc.addCVZone("Gas Product", "pressure", 45.0, 55.0).setUnit("bara");
mpc.setConstraint("Gas Product", "flowRate", 0.0, 650.0);
mpc.setConstraint("Gas Product", "pressure", 30.0, 70.0);

DisturbanceVariable feedTemperature = mpc.addDV("Feed", "temperature");
feedTemperature.setUnit("K");
```

`addMV`, `addCV`, and `addDV` take the name of a registered process unit. Set the
unit on the returned variable. The current bound-variable readers support stream
flow, pressure, and temperature; they do not read arbitrary separator level or
component-fraction properties. A level-control model needs an appropriate dynamic
inventory model and measurement/control integration.

### Step 3: Generate Step Response Models

```java
mpc.setPredictionHorizon(30);
mpc.setControlHorizon(10);
mpc.identifyModel(10.0); // Sample interval in seconds; identifies steady-state gains.
if (!mpc.getLinearizationResult().isSuccessful()) {
    throw new IllegalStateException(mpc.getLinearizationResult().getErrorMessage());
}

IndustrialMPCExporter exporter = mpc.createIndustrialExporter();
exporter.setDefaultTimeConstant(60.0); // Assumed first-order dynamics, in seconds.
exporter.exportStepResponseModel(output.resolve("separator_mpc_model.json").toString());
exporter.exportStepResponseCSV(output.resolve("separator_mpc_model.csv").toString());
exporter.exportComprehensiveConfiguration(output.resolve("separator_mpc_config.json").toString());
```

`exportStepResponseModel` writes JSON; use `exportStepResponseCSV` for CSV.

---

## Integration Patterns

### Pattern 1: Offline Model Generation → Online Execution

The most common integration pattern where NeqSim generates models offline that are executed in real-time by the industrial MPC.

```
┌─────────────────┐     Model Files      ┌──────────────────┐
│     NeqSim      │ ─────────────────▶  │  Industrial MPC   │
│ (Engineering)   │  CSV, JSON, Config  │  (Real-time)      │
└─────────────────┘                      └──────────────────┘
     Offline                                   Online
   (minutes)                               (milliseconds)
```

**Use Cases:**
- Initial MPC commissioning
- Model updates during turnarounds
- Operating point changes

### Pattern 2: Property Table Lookup

Build a table explicitly by solving the thermodynamic system at each grid point.
The example writes bulk mixture density and specific enthalpy; a phase-specific
sensor must also record phase identity and handle phase appearance or disappearance.
Add `java.io.BufferedWriter`, `java.nio.charset.StandardCharsets`,
`java.nio.file.Files`, `java.nio.file.Paths`, and `java.util.Locale` to the imports.

```java
try (BufferedWriter writer = Files.newBufferedWriter(output.resolve("property_table.csv"),
        StandardCharsets.UTF_8)) {
    writer.write("pressure_bara,temperature_K,density_kg_m3,enthalpy_J_kg");
    writer.newLine();
    for (double pressure : new double[] {20.0, 50.0, 80.0}) {
        for (double temperature : new double[] {280.0, 300.0, 320.0}) {
            SystemInterface sample = fluid.clone();
            sample.setPressure(pressure, "bara");
            sample.setTemperature(temperature, "K");
            new ThermodynamicOperations(sample).TPflash();
            sample.initProperties();
            writer.write(String.format(Locale.ROOT, "%.1f,%.1f,%.8g,%.8g",
                pressure, temperature, sample.getDensity("kg/m3"),
                sample.getEnthalpy("J/kg")));
            writer.newLine();
        }
    }
}
```

Validate interpolation errors, phase boundaries, composition, and grid coverage before
using a table online. `SoftSensorExporter` exports sensor definitions; it does not
calculate a lookup table or fit property correlations.

### Pattern 3: Gain Scheduling

Different operating regions require different model gains. NeqSim calculates models at multiple operating points.

```java
double[] pressures = {35.0, 50.0, 65.0}; // bara, inside the 30-70 bara MV bounds
double[] temperatures = {280.0, 300.0, 320.0}; // K
double basePressure = feed.getPressure("bara");
double baseTemperature = feed.getTemperature("K");
try {
    for (double pressure : pressures) {
        for (double temperature : temperatures) {
            feed.setPressure(pressure, "bara");
            feed.setTemperature(temperature, "K");
            process.run();
            mpc.identifyModel(10.0);
            String filename = String.format(Locale.ROOT, "model_P%.0f_T%.0f.csv", pressure, temperature);
            exporter.exportStepResponseCSV(output.resolve(filename).toString());
        }
    }
} finally {
    feed.setPressure(basePressure, "bara");
    feed.setTemperature(baseTemperature, "K");
    process.run();
    mpc.identifyModel(10.0);
}
```

The industrial MPC selects the appropriate model based on current operating conditions.

### Pattern 4: Nonlinear MPC with Steady-State Solver

`SubrModlExporter` writes model configuration and variable mappings. An external
runtime still needs the nonlinear calculation and a plant-specific adapter; a
configuration file alone does not supply an executable controller.

```java
StateVariable gasFlowState = mpc.addSVR("Gas Product", "flowRate", "gas_flow");
gasFlowState.setUnit("kg/hr");
gasFlowState.setModelValue(gasProduct.getFlowRate("kg/hr"));

SubrModlExporter subrModl = mpc.createSubrModlExporter();
subrModl.setModelName("HP_Separator_NL");
subrModl.exportConfiguration(output.resolve("separator_subrmodl.cnf").toString());
subrModl.exportMPCConfiguration(output.resolve("separator_smpc.cnf").toString(), true);
subrModl.exportJSON(output.resolve("separator_subrmodl.json").toString());
```

Refresh state model values explicitly when the process is rerun. `StateVariable`
stores model and measured values; setting a property name does not implement a
new dynamic state or an automatic measurement connection.

---

## Production Optimization

### Overview

Industrial MPC systems excel at **production optimization** - maximizing throughput while respecting all process constraints. NeqSim provides the physics-based models that enable accurate constraint handling.

### Optimization Hierarchy

```
┌─────────────────────────────────────────────────────────────────┐
│                    PRODUCTION OPTIMIZATION                       │
│                    (Economic Objective)                          │
│         Maximize: Revenue - Operating Costs                      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    INDUSTRIAL MPC                                │
│                    (Constraint Handling)                         │
│         Subject to: Equipment limits, Quality specs,            │
│                     Safety constraints, Environmental           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    NEQSIM MODELS                                 │
│                    (Physical Constraints)                        │
│         Provides: Thermodynamic limits, Phase boundaries,       │
│                   Property calculations, Equipment models       │
└─────────────────────────────────────────────────────────────────┘
```

### Optimization Variables

The industrial MPC optimizes by pushing the process toward constraints while maintaining stability:

| Variable Type | NeqSim Contribution | MPC Usage |
|---------------|---------------------|-----------|
| **Throughput** | Maximum flow capacity | Maximize within limits |
| **Quality** | Composition calculations | Constraint satisfaction |
| **Energy** | Enthalpy, heat duties | Cost minimization |
| **Efficiency** | Compressor curves, pump efficiency | Optimal setpoints |

### Example: Separator Train Optimization

Use `ProductionOptimizer` for a steady-state throughput target, as shown in
[Production Optimization Setup](#production-optimization-setup). Supply equipment
limits from the installed design and evaluate their physical meaning at the current
operating point. For example:

```java
double gasVelocity = separator.getGasSuperficialVelocity(); // m/s
double allowableGasVelocity = separator.getMaxAllowableGasVelocity(); // m/s
if (!Double.isFinite(allowableGasVelocity) || allowableGasVelocity <= 0.0) {
    throw new IllegalStateException("A valid separator capacity basis is required");
}
boolean gasVelocityWithinLimit = gasVelocity <= allowableGasVelocity;

// Illustrative installed gas mass-rate limit, independently specified in kg/hr.
// This configures an existing CV; it does not convert velocity into mass flow.
mpc.setConstraint("Gas Product", "flowRate", 0.0, 650.0);
exporter.exportVariableConfiguration(output.resolve("separator_constraints.json").toString());
```

These checks do not establish liquid carryover or residence-time acceptance.
Those constraints need vessel geometry, inventories, and a selected separation model.
`getMaxAllowableGasVelocity` uses the configured design K-factor and assumes a liquid
density of 1000 kg/m³ when no liquid phase is present; review that assumption for sizing.

---

## Bottleneck Analysis and Resolution

### What is Bottleneck Analysis?

Bottleneck analysis identifies which constraints are limiting production and quantifies the value of relaxing each constraint.

### NeqSim's Role in Bottleneck Analysis

1. **Equipment Capacity Modeling**
   - Separator flooding velocity
   - Compressor surge/choke limits
   - Heat exchanger duty limits
   - Pump cavitation limits

2. **Thermodynamic Constraints**
   - Phase envelope boundaries
   - Hydrate formation curves
   - Dew point specifications
   - Flash point limits

3. **Quality Specifications**
   - Composition targets
   - Water content limits
   - H2S specifications
   - Heating value requirements

### Bottleneck Resolution Workflow

```
┌─────────────────────────────────────────────────────────────────┐
│   Step 1: IDENTIFY ACTIVE CONSTRAINTS                            │
│   Industrial MPC reports which constraints are limiting          │
│   production (shadow prices / Lagrange multipliers)              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│   Step 2: ANALYZE WITH NEQSIM                                    │
│   Use rigorous simulation to understand constraint physics:      │
│   - What causes the limit?                                       │
│   - How sensitive is it to operating conditions?                 │
│   - What would happen if constraint is violated?                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│   Step 3: EVALUATE DEBOTTLENECKING OPTIONS                       │
│   NeqSim simulates "what-if" scenarios:                          │
│   - Increase equipment size                                      │
│   - Change operating pressure                                    │
│   - Add parallel equipment                                       │
│   - Modify feed conditions                                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│   Step 4: UPDATE MPC MODELS                                      │
│   After physical changes, regenerate models with NeqSim          │
│   and deploy updated MPC configuration                           │
└─────────────────────────────────────────────────────────────────┘
```

### Example: Compressor Bottleneck

This fragment requires a solved compressor named `Export_Compressor` with a loaded
performance map. Add `Compressor` and `CompressorChartInterface` from
`neqsim.process.equipment.compressor` to the imports.

```java
Compressor compressor = (Compressor) process.getUnit("Export_Compressor");
CompressorChartInterface chart = compressor.getCompressorChart();
double surgeLimit = chart.getSurgeFlowAtSpeed(compressor.getSpeed());
double chokeLimit = chart.getStoneWallFlowAtSpeed(compressor.getSpeed());
double currentFlow = compressor.getInletStream().getFlowRate("m3/hr");
if (!Double.isFinite(surgeLimit) || !Double.isFinite(chokeLimit)
        || surgeLimit <= 0.0 || chokeLimit <= surgeLimit) {
    throw new IllegalStateException("Load valid surge and stonewall map data first");
}
double surgeMargin = 100.0 * (currentFlow - surgeLimit) / surgeLimit;
double chokeMargin = 100.0 * (chokeLimit - currentFlow) / chokeLimit;
logger.info("Surge margin: {}%; choke margin: {}%", surgeMargin, chokeMargin);
```

The flow basis is actual inlet m³/h, matching the map; do not compare map flow with
kg/h. To investigate higher inlet pressure or cooler gas, change the upstream feed
or cooler, rerun the process, and recalculate both limits and margins. Changing only
a downstream inlet stream can be overwritten by the upstream calculation.

### Bottleneck Value Calculation

The industrial MPC calculates the economic value (shadow price) of each constraint:

| Constraint | Shadow Price | Interpretation |
|------------|--------------|----------------|
| Compressor Power | $500/MW | Each additional MW enables $500/hr more production |
| Separator Pressure | $100/bar | Relaxing pressure by 1 bar gains $100/hr |
| Export Quality | $200/ppm | Each ppm H2S relaxation worth $200/hr |

NeqSim can validate these shadow prices by simulating the actual production gain when constraints are relaxed.

---

## Soft Sensor Integration

### Phase Properties

```java
ThermodynamicOperations thermoOps = new ThermodynamicOperations(fluid);
thermoOps.TPflash();
fluid.initProperties();

if (fluid.hasPhaseType("gas")) {
    double gasCompressibility = fluid.getPhase("gas").getZ();
    double gasViscosity = fluid.getPhase("gas").getViscosity("cP");
    logger.info("Gas Z: {}; viscosity: {} cP", gasCompressibility, gasViscosity);
}
if (fluid.hasPhaseType("oil")) {
    logger.info("Liquid density: {} kg/m3", fluid.getPhase("oil").getDensity("kg/m3"));
}
if (fluid.hasPhaseType("gas") && fluid.hasPhaseType("oil")) {
    int gasPhase = fluid.getPhaseNumberOfPhase("gas");
    int oilPhase = fluid.getPhaseNumberOfPhase("oil");
    double surfaceTension = fluid.getInterphaseProperties()
        .getSurfaceTension(gasPhase, oilPhase, "mN/m");
    logger.info("Gas/oil surface tension: {} mN/m", surfaceTension);
}
```

### Molecular Weight Estimation

```java
SoftSensorExporter softSensor = exporter.createSoftSensorExporter();
softSensor.addMolecularWeightSensor("Gas_MW", "Gas Product");
softSensor.addDensitySensor("Gas_Density", "Gas Product", "kg/m3");
softSensor.exportConfiguration(output.resolve("gas_soft_sensors.json").toString());
```

The export describes inputs, units, and equipment mappings. Calculate the values
from the current process state, or develop and validate a lookup/correlation separately.

### Heating Value Calculation

Add `neqsim.standards.gasquality.Standard_ISO6976_2016` to the imports. Heating
values are calculated by a gas-quality standard, not by methods on a phase.

```java
if (!fluid.hasPhaseType("gas")) {
    throw new IllegalStateException("A gas phase is required for gas sales properties");
}
SystemInterface salesGas = fluid.phaseToSystem("gas");
Standard_ISO6976_2016 gasQuality = new Standard_ISO6976_2016(salesGas, 15.0, 15.0, "volume");
gasQuality.calculate();
double gcv = gasQuality.getValue("SuperiorCalorificValue"); // kJ/m3 at reference conditions
double ncv = gasQuality.getValue("InferiorCalorificValue"); // kJ/m3 at reference conditions
double wobbeIndex = gasQuality.getValue("SuperiorWobbeIndex"); // kJ/m3
```

This example uses 15 °C for the volume and combustion reference temperatures;
select the reference conditions required by the sales contract.

---

## Gain Scheduling

### Operating Point Identification

```java
String[] operatingNames = {"Low_Rate", "Normal", "High_Rate"};
// Columns: mass flow (kg/hr), pressure (bara), temperature (K).
double[][] operatingPoints = {
    {250.0, 40.0, 290.0},
    {500.0, 50.0, 300.0},
    {750.0, 60.0, 310.0}
};
double originalFlow = feed.getFlowRate("kg/hr");
double originalPressure = feed.getPressure("bara");
double originalTemperature = feed.getTemperature("K");
try {
    for (int point = 0; point < operatingPoints.length; point++) {
        feed.setFlowRate(operatingPoints[point][0], "kg/hr");
        feed.setPressure(operatingPoints[point][1], "bara");
        feed.setTemperature(operatingPoints[point][2], "K");
        process.run();
        mpc.identifyModel(10.0);
        exporter.exportStepResponseCSV(output.resolve("model_" + operatingNames[point] + ".csv").toString());
    }
} finally {
    feed.setFlowRate(originalFlow, "kg/hr");
    feed.setPressure(originalPressure, "bara");
    feed.setTemperature(originalTemperature, "K");
    process.run();
    mpc.identifyModel(10.0);
}
```

Keep each operating point and its linearization perturbations inside the MV bounds.
Recalculate disturbance sensitivities at each point when using feedforward control.

### Model Selection Logic

The industrial MPC uses operating conditions to select the appropriate model:

```
IF (flow < 375 kg/hr) THEN
    USE model_Low_Rate
ELSE IF (flow < 625 kg/hr) THEN
    USE model_Normal
ELSE
    USE model_High_Rate
```

---

## Model Validation

### Continuous Model Monitoring

The surrounding application must apply measured conditions, supply MPC predictions,
and decide acceptable error limits. The following helper compares already ordered
outputs and uses an explicit tolerance per output, in that output's engineering unit.

```java
public class ModelValidator {
    public static boolean withinTolerance(double[] simulated, double[] predicted,
            double[] tolerances) {
        if (simulated.length != predicted.length || simulated.length != tolerances.length) {
            throw new IllegalArgumentException("Output and tolerance dimensions must match");
        }
        for (int index = 0; index < simulated.length; index++) {
            if (!Double.isFinite(simulated[index]) || !Double.isFinite(predicted[index])
                    || !Double.isFinite(tolerances[index]) || tolerances[index] < 0.0
                    || Math.abs(simulated[index] - predicted[index]) > tolerances[index]) {
                return false;
            }
        }
        return true;
    }
}
```

### Bias Detection

```java
StateVariable gasFlowBias = new StateVariable("Gas_Flow", gasProduct, "flowRate");
gasFlowBias.setUnit("kg/hr");
gasFlowBias.setModelValue(gasProduct.getFlowRate("kg/hr"));
gasFlowBias.setBiasTfilt(0.0); // Unfiltered residual for this example.
// Synthetic measurement for demonstration; replace with a timestamp-aligned plant value.
gasFlowBias.setMeasuredValue(gasProduct.getFlowRate("kg/hr") + 5.0);
logger.info("Gas flow measurement-minus-model bias: {} kg/hr", gasFlowBias.getBias());
```

Update the model and measured values explicitly for each comparison. Filtering and
prediction settings require separate tuning and verification of their update cadence.

---

## Implementation Examples

### Complete Separator Control Example

Save as `SeparatorMPCIntegration.java` and run with an optional output directory
argument (default `mpc-output`). The example identifies flow/pressure gains, exports
model and sensor configuration files, and returns the configured MPC for reuse.
It does not execute a closed-loop level controller. The first-order time constant
of 60 s is an illustrative export assumption, not an identified separator response.

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mpc.DisturbanceVariable;
import neqsim.process.mpc.IndustrialMPCExporter;
import neqsim.process.mpc.ProcessDerivativeCalculator;
import neqsim.process.mpc.ProcessLinkedMPC;
import neqsim.process.mpc.SoftSensorExporter;
import neqsim.process.mpc.StateVariable;
import neqsim.process.mpc.SubrModlExporter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SeparatorMPCIntegration {
    private static final Logger logger = LogManager.getLogger(SeparatorMPCIntegration.class);

    public static ProcessLinkedMPC configureAndExport(String outputDirectory) throws Exception {
        Path output = Files.createDirectories(Paths.get(outputDirectory));
        SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
        fluid.addComponent("methane", 0.80);
        fluid.addComponent("ethane", 0.10);
        fluid.addComponent("propane", 0.05);
        fluid.addComponent("n-butane", 0.03);
        fluid.addComponent("n-pentane", 0.02);
        fluid.setMixingRule("classic");

        ProcessSystem process = new ProcessSystem();
        Stream feed = new Stream("Feed", fluid);
        feed.setFlowRate(500.0, "kg/hr");
        Separator separator = new Separator("HP Separator", feed);
        separator.setInternalDiameter(1.5);
        StreamInterface gasProduct = separator.getGasOutStream();
        gasProduct.setName("Gas Product");
        process.add(feed);
        process.add(separator);
        process.add(gasProduct);
        process.run();

        ProcessLinkedMPC mpc = new ProcessLinkedMPC("HP_Sep_MPC", process);
        mpc.addMV("Feed", "flowRate", 200.0, 800.0).setUnit("kg/hr");
        mpc.addMV("Feed", "pressure", 30.0, 70.0).setUnit("bara");
        mpc.addCVZone("Gas Product", "flowRate", 100.0, 600.0).setUnit("kg/hr");
        mpc.addCVZone("Gas Product", "pressure", 45.0, 55.0).setUnit("bara");
        mpc.setConstraint("Gas Product", "flowRate", 0.0, 650.0);
        mpc.setConstraint("Gas Product", "pressure", 30.0, 70.0);

        // Identify the temperature disturbance's steady-state sensitivities explicitly.
        ProcessDerivativeCalculator derivative = new ProcessDerivativeCalculator(process);
        derivative.addInputVariable("Feed.temperature", "K");
        derivative.addOutputVariable("Gas Product.flowRate", "kg/hr");
        derivative.addOutputVariable("Gas Product.pressure", "bara");
        double[][] temperatureGains = derivative.calculateJacobian();
        if (!Double.isFinite(temperatureGains[0][0]) || !Double.isFinite(temperatureGains[1][0])) {
            throw new IllegalStateException("Temperature sensitivities must be finite");
        }
        DisturbanceVariable temperature = mpc.addDV("Feed", "temperature");
        temperature.setUnit("K");
        temperature.setCvSensitivity(temperatureGains[0][0], temperatureGains[1][0]);

        mpc.setPredictionHorizon(30);
        mpc.setControlHorizon(10);
        mpc.identifyModel(10.0);
        if (!mpc.getLinearizationResult().isSuccessful()) {
            throw new IllegalStateException(mpc.getLinearizationResult().getErrorMessage());
        }

        IndustrialMPCExporter exporter = mpc.createIndustrialExporter();
        exporter.setApplicationName("HP_Separator");
        exporter.setDefaultTimeConstant(60.0);
        exporter.exportStepResponseModel(output.resolve("hp_sep_model.json").toString());
        exporter.exportStepResponseCSV(output.resolve("hp_sep_model.csv").toString());
        exporter.exportComprehensiveConfiguration(output.resolve("hp_sep_config.json").toString());

        SoftSensorExporter softSensor = exporter.createSoftSensorExporter();
        softSensor.addDensitySensor("Gas_Density", "Gas Product", "kg/m3");
        softSensor.addMolecularWeightSensor("Gas_MW", "Gas Product");
        softSensor.exportConfiguration(output.resolve("hp_sep_sensors.json").toString());

        StateVariable gasFlowState = mpc.addSVR("Gas Product", "flowRate", "gas_flow");
        gasFlowState.setUnit("kg/hr");
        gasFlowState.setModelValue(gasProduct.getFlowRate("kg/hr"));
        SubrModlExporter subrModl = mpc.createSubrModlExporter();
        subrModl.setModelName("HP_Sep_NL");
        subrModl.exportConfiguration(output.resolve("hp_sep_subrmodl.cnf").toString());
        logger.info("MPC integration files written to {}", output.toAbsolutePath());
        return mpc;
    }

    public static void main(String[] args) throws Exception {
        configureAndExport(args.length > 0 ? args[0] : "mpc-output");
    }
}
```

### Production Optimization Setup

Use `ProductionOptimizer` to calculate a steady-state operating target. `ProcessLinkedMPC`
does not expose the objective/weight/priority/export methods formerly shown in this section.
Pass the approved target into the configured MPC manipulated-variable workflow separately.
The method below requires a solved process with a registered feed named `feed`; add the
plant's product, power, equipment, and environmental constraints before using its result.

```java
import java.util.Collections;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer;
import neqsim.process.util.optimizer.ProductionOptimizer.*;

public class ProductionOptimization {
    public static OptimizationResult calculateTarget(ProcessSystem process) {
        StreamInterface feed = (StreamInterface) process.getUnit("feed");
        OptimizationConfig config = new OptimizationConfig(1000.0, 20000.0)
            .rateUnit("kg/hr")
            .searchMode(SearchMode.BINARY_FEASIBILITY)
            .tolerance(10.0)
            .maxIterations(30);
        OptimizationConstraint installedFeedLimit = OptimizationConstraint.lessThan(
            "Installed feed capacity",
            p -> ((StreamInterface) p.getUnit("feed")).getFlowRate("kg/hr"),
            15000.0,
            ConstraintSeverity.HARD,
            100.0,
            "Maximum installed feed mass rate in kg/hr"
        );
        OptimizationResult result = new ProductionOptimizer().optimize(
            process, feed, config, Collections.emptyList(),
            Collections.singletonList(installedFeedLimit)
        );
        if (!result.isFeasible()) {
            throw new IllegalStateException(result.getInfeasibilityDiagnosis());
        }
        return result;
    }
}
```

---

## Derivative Calculation for AI and MPC

### The Derivative Challenge

AI software and MPC systems typically require derivatives (gradients, Jacobians) of process variables for:
- **Gradient-based optimization**: Finding optimal setpoints
- **Model Predictive Control**: Computing control moves
- **Sensitivity analysis**: Understanding process behavior
- **Machine learning**: Training neural networks with physics-informed gradients

### Why Analytical Derivatives Are Difficult

Many NeqSim thermodynamic derivatives are analytical. End-to-end process derivatives
can still be difficult to obtain analytically because:

1. **Complex equation chains**: Fugacity → Activity Coefficient → Compressibility → Mixing Rules → Pure Component Parameters
2. **Iterative algorithms**: Flash calculations use iterative solvers where derivatives require implicit function theorem
3. **Phase transitions**: Discontinuities at phase boundaries
4. **Conditional logic**: Different correlations for different phases

### NeqSim's Derivative Calculator

NeqSim provides an efficient numerical derivative calculator optimized for process simulations:

```java
import neqsim.process.mpc.ProcessDerivativeCalculator;

// Create calculator
ProcessDerivativeCalculator calc = new ProcessDerivativeCalculator(process);

// Define input variables (what we perturb)
calc.addInputVariable("Feed.flowRate", "kg/hr");
calc.addInputVariable("Feed.pressure", "bara");
calc.addInputVariable("Feed.temperature", "K");

// Define output variables (what we measure)
calc.addOutputVariable("HP Separator.gasOutStream.flowRate", "kg/hr");
calc.addOutputVariable("HP Separator.gasOutStream.pressure", "bara");

// Calculate full Jacobian matrix
double[][] jacobian = calc.calculateJacobian();
// jacobian[i][j] = ∂output_i / ∂input_j
```

### Derivative Methods

| Method | Formula | Accuracy | Cost |
|--------|---------|----------|------|
| Forward Difference | (f(x+h) - f(x)) / h | O(h) | N+1 evaluations |
| Central Difference | (f(x+h) - f(x-h)) / 2h | O(h²) | 2N evaluations |
| 5-Point Stencil | Higher-order formula | O(h⁴) | 4N evaluations |

```java
// Select derivative method
calc.setMethod(ProcessDerivativeCalculator.DerivativeMethod.CENTRAL_DIFFERENCE);

// Adjust step size (relative)
calc.setRelativeStepSize(1e-4);  // 0.01% perturbation
```

### Automatic Step Size Selection

The calculator automatically selects appropriate step sizes based on variable type:

| Variable Type | Minimum Step | Rationale |
|---------------|--------------|-----------|
| Pressure | 0.01 bar | Avoid numerical noise |
| Temperature | 0.1 K | Sufficient for property changes |
| Flow Rate | 0.001 kg/hr | Very small flows need care |
| Composition | 1e-6 | Mole fractions are small numbers |

### Single Derivative

```java
// Get one specific derivative
double dGasFlow_dFeedFlow = calc.getDerivative(
    "HP Separator.gasOutStream.flowRate",  // output
    "Feed.flowRate"                      // input
);
```

### Gradient (One Output, All Inputs)

```java
// Get gradient of one output w.r.t. all inputs
double[] gradient = calc.getGradient("HP Separator.gasOutStream.flowRate");
// gradient[0] = ∂gasFlow/∂feedFlow
// gradient[1] = ∂gasFlow/∂feedPressure
// gradient[2] = ∂gasFlow/∂feedTemperature
```

### Hessian (Second Derivatives)

The current `ProcessDerivativeCalculator.calculateHessian` implementation is not
suitable for this example: three inputs and two outputs trigger an array bounds
error, and the selected-output index is not used in the second-derivative loop.
Use the validated Jacobian/gradient workflow above. If an external optimizer needs
a Hessian, compute and validate second derivatives of its scalar objective separately;
do not assume this helper returns that objective's Hessian. The implementation defect
and reproducer are tracked in [issue #3616](https://github.com/equinor/neqsim/issues/3616).

### Export for External Systems

```java
// Export Jacobian to JSON for AI/ML systems
String json = calc.exportJacobianToJSON();

// Export to CSV for spreadsheet analysis
calc.exportJacobianToCSV(output.resolve("jacobian.csv").toString());
```

### JSON Output Format

Illustrative structure for two inputs; numerical values depend on the solved process.

```json
{
  "inputs": ["Feed.flowRate", "Feed.pressure"],
  "outputs": ["HP Separator.gasOutStream.flowRate", "HP Separator.gasOutStream.pressure"],
  "baseInputValues": [100.0, 50.0],
  "baseOutputValues": [85.2, 50.0],
  "jacobian": [
    [0.852, -0.023],
    [0.0, 1.0]
  ]
}
```

### Best Practices for AI Integration

1. **Cache derivatives**: Recompute only when operating point changes significantly
2. **Use central differences**: More accurate than forward differences
3. **Validate step sizes**: Too small causes numerical noise, too large causes truncation error
4. **Monitor for phase changes**: Derivatives may jump at phase boundaries
5. **Smooth gradients**: For ML training, consider averaging over nearby operating points

---

## Summary

The integration of NeqSim with industrial MPC systems creates a powerful combination for process control and optimization:

| Capability | NeqSim Role | Industrial MPC Role |
|------------|-------------|---------------------|
| **Model Generation** | Create physics-based models | Execute models in real-time |
| **Constraint Handling** | Define thermodynamic limits | Satisfy constraints online |
| **Production Optimization** | Quantify capacity limits | Push to optimal constraints |
| **Bottleneck Analysis** | Identify physical causes | Calculate economic value |
| **Soft Sensors** | Provide property calculations | Fast lookup/interpolation |
| **Model Validation** | Rigorous reference | Bias detection/correction |

This complementary approach combines the accuracy of first-principles thermodynamic modeling with the speed and robustness of industrial control systems.

---

## References

- NeqSim Documentation: [https://equinor.github.io/neqsim/](https://equinor.github.io/neqsim/)
- NeqSim MPC Package: `neqsim.process.mpc`
- Example notebooks: `docs/examples/MPC_Integration_Tutorial.ipynb`

---

*Document Version: 1.1*
*Examples checked against NeqSim 3.20.0 development source, September 2026.*
