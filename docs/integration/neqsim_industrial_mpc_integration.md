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
- **Automatic Linearization**: Generate step response models at any operating point
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

### Step 1: Build NeqSim Process Model

```java
// Create thermodynamic system
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");

// Build process flowsheet
ProcessSystem process = new ProcessSystem();
Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(100.0, "kg/hr");

Separator separator = new Separator("HP Separator", feed);
process.add(feed);
process.add(separator);
process.run();
```

### Step 2: Configure MPC Variables

```java
// Create MPC bridge
ProcessLinkedMPC mpc = new ProcessLinkedMPC("HP_Separator_MPC", process);

// Define manipulated variables (MVs)
mpc.addMV("Feed_Flow", feed, "flowRate", 50.0, 150.0, "kg/hr");
mpc.addMV("Separator_Pressure", separator, "pressure", 30.0, 70.0, "bara");

// Define controlled variables (CVs)
mpc.addCV("Gas_Rate", separator.getGasOutStream(), "flowRate", 40.0, 60.0, "kg/hr");
mpc.addCV("Liquid_Level", separator, "liquidLevel", 0.3, 0.7, "fraction");

// Define disturbance variables (DVs)
mpc.addDV("Feed_Temperature", feed, "temperature", "C");
```

### Step 3: Generate Step Response Models

```java
// Configure linearization
mpc.setLinearizationStepSize(0.05);  // 5% step
mpc.setSettlingTime(600.0);           // 10 minutes
mpc.setSamplingTime(10.0);            // 10 seconds

// Generate step responses
mpc.generateStepResponses();

// Export for industrial MPC
IndustrialMPCExporter exporter = mpc.createIndustrialExporter();
exporter.exportStepResponseModel("separator_mpc_model.csv");
exporter.exportMPCConfiguration("separator_mpc_config.json");
```

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

NeqSim pre-calculates property tables that industrial soft sensors use for fast lookups.

```java
// Generate property table
SoftSensorExporter softSensor = mpc.createSoftSensorExporter();

// Configure property grid
softSensor.addPropertyDimension("pressure", 20.0, 80.0, 10);    // 10 points
softSensor.addPropertyDimension("temperature", 273.0, 373.0, 10);

// Export lookup tables
softSensor.exportLookupTable("density", "density_table.csv");
softSensor.exportLookupTable("viscosity", "viscosity_table.csv");
softSensor.exportLookupTable("enthalpy", "enthalpy_table.csv");
```

**Advantages:**
- Sub-millisecond property lookups
- No real-time NeqSim dependency
- Validated thermodynamic accuracy

### Pattern 3: Gain Scheduling

Different operating regions require different model gains. NeqSim calculates models at multiple operating points.

```java
// Define operating points
double[] pressures = {30.0, 50.0, 70.0};  // bara
double[] temperatures = {280.0, 300.0, 320.0};  // K

// Generate models at each operating point
for (double P : pressures) {
    for (double T : temperatures) {
        feed.setPressure(P, "bara");
        feed.setTemperature(T, "K");
        process.run();
        
        mpc.generateStepResponses();
        String filename = String.format("model_P%.0f_T%.0f.csv", P, T);
        exporter.exportStepResponseModel(filename);
    }
}
```

The industrial MPC selects the appropriate model based on current operating conditions.

### Pattern 4: Nonlinear MPC with Steady-State Solver

For nonlinear MPC applications, NeqSim can provide steady-state solutions.

```java
// Configure for nonlinear MPC
SubrModlExporter subrModl = mpc.createSubrModlExporter();

// Add state variables for estimation
mpc.addSVR("Liquid_Composition", separator, "liquidComposition", 0.0, 1.0);

// Export SubrModl configuration
subrModl.exportConfiguration("separator_subrmodl.cnf");
subrModl.exportMPCConfiguration("separator_smpc.json");
```

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

```java
// Define economic objective
mpc.setOptimizationObjective(OptimizationType.MAXIMIZE_THROUGHPUT);

// NeqSim provides constraint models:
// 1. Maximum gas velocity (flooding limit)
double maxGasVelocity = separator.getMaxGasVelocity();  // m/s

// 2. Minimum residence time
double minResidenceTime = separator.getMinResidenceTime();  // seconds

// 3. Liquid carryover limit
double maxLiquidInGas = separator.getMaxLiquidCarryover();  // ppm

// Export constraints to MPC
exporter.addConstraint("Gas_Velocity", 0, maxGasVelocity, "m/s");
exporter.addConstraint("Residence_Time", minResidenceTime, 1e6, "s");
exporter.addConstraint("Liquid_Carryover", 0, maxLiquidInGas, "ppm");
```

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

```java
// Identify compressor as bottleneck
Compressor compressor = (Compressor) process.getUnit("Export_Compressor");

// Analyze compressor performance
CompressorChart chart = compressor.getCompressorChart();
double surgeLimit = chart.getSurgeFlow();
double chokeLimit = chart.getChokeFlow();
double currentFlow = compressor.getInletStream().getFlowRate("kg/hr");

// Calculate margin to constraints
double surgeMargin = (currentFlow - surgeLimit) / surgeLimit * 100;  // %
double chokeMargin = (chokeLimit - currentFlow) / chokeLimit * 100;  // %

// If near choke (bottleneck), simulate options:
if (chokeMargin < 10) {
    System.out.println("Compressor approaching choke limit!");
    
    // Option 1: Increase inlet pressure
    double newInletPressure = compressor.getInletPressure() * 1.1;
    compressor.setInletPressure(newInletPressure);
    process.run();
    double newChokeMargin = // recalculate
    
    // Option 2: Cool the inlet gas
    // Option 3: Install parallel compressor
}

// Generate updated MPC model with new operating point
mpc.generateStepResponses();
exporter.exportStepResponseModel("compressor_updated.csv");
```

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
// Calculate phase properties for soft sensor
ThermodynamicOperations thermoOps = new ThermodynamicOperations(fluid);
thermoOps.TPflash();

double gasCompressibility = fluid.getPhase("gas").getZ();
double liquidDensity = fluid.getPhase("oil").getDensity("kg/m3");
double gasViscosity = fluid.getPhase("gas").getViscosity("cP");
double surfaceTension = fluid.getInterphaseProperties().getSurfaceTension("mN/m");
```

### Molecular Weight Estimation

```java
// Export molecular weight correlation
SoftSensorExporter exporter = mpc.createSoftSensorExporter();
exporter.setFluid(fluid);

// Generate MW as function of composition and conditions
exporter.exportCorrelation("molecularWeight", 
    new String[]{"C1_fraction", "C2_fraction", "temperature", "pressure"},
    "mw_correlation.csv");
```

### Heating Value Calculation

```java
// Calculate heating values for gas sales
double GCV = fluid.getPhase("gas").getGCV();  // Gross calorific value
double NCV = fluid.getPhase("gas").getNCV();  // Net calorific value
double wobbeIndex = fluid.getPhase("gas").getWobbeIndex();
```

---

## Gain Scheduling

### Operating Point Identification

```java
// Define key operating variables that affect gains
List<OperatingPoint> operatingPoints = new ArrayList<>();

// Low throughput
operatingPoints.add(new OperatingPoint(
    "Low_Rate", 50.0, 40.0, 290.0));  // flow, pressure, temp

// Normal operation
operatingPoints.add(new OperatingPoint(
    "Normal", 100.0, 50.0, 300.0));

// High throughput
operatingPoints.add(new OperatingPoint(
    "High_Rate", 150.0, 60.0, 310.0));

// Generate model at each point
for (OperatingPoint op : operatingPoints) {
    configureProcess(process, op);
    mpc.generateStepResponses();
    exporter.exportStepResponseModel("model_" + op.getName() + ".csv");
}
```

### Model Selection Logic

The industrial MPC uses operating conditions to select the appropriate model:

```
IF (flow < 75 kg/hr) THEN
    USE model_Low_Rate
ELSE IF (flow < 125 kg/hr) THEN
    USE model_Normal
ELSE
    USE model_High_Rate
```

---

## Model Validation

### Continuous Model Monitoring

A background service can use NeqSim to validate MPC model predictions:

```java
// Compare MPC prediction with NeqSim simulation
public class ModelValidator {
    private ProcessSystem neqsimModel;
    private double[] mpcPrediction;
    
    public ValidationResult validate(ProcessData currentData) {
        // Apply current conditions to NeqSim model
        applyConditions(neqsimModel, currentData);
        neqsimModel.run();
        
        // Compare outputs
        double[] neqsimOutput = getOutputs(neqsimModel);
        double[] errors = new double[neqsimOutput.length];
        
        for (int i = 0; i < errors.length; i++) {
            errors[i] = Math.abs(neqsimOutput[i] - mpcPrediction[i]);
        }
        
        return new ValidationResult(errors, isModelValid(errors));
    }
}
```

### Bias Detection

```java
// State variable with bias tracking
StateVariable liquidLevel = new StateVariable("Liquid_Level", 
    separator, "liquidLevel", 0.0, 1.0, "fraction");

// Configure bias estimation
liquidLevel.setBiasTfilt(300.0);   // 5-minute filter
liquidLevel.setBiasTpred(600.0);   // 10-minute prediction horizon

// Monitor bias evolution
if (Math.abs(liquidLevel.getBias()) > 0.05) {
    System.out.println("Significant model bias detected - consider model update");
}
```

---

## Implementation Examples

### Complete Separator Control Example

```java
import neqsim.process.ProcessSystem;
import neqsim.process.equipment.*;
import neqsim.process.mpc.*;
import neqsim.thermo.system.*;

public class SeparatorMPCIntegration {
    
    public static void main(String[] args) {
        // 1. Build process model
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
        feed.setTemperature(25.0, "C");
        feed.setPressure(50.0, "bara");
        
        Separator hpSep = new Separator("HP_Separator", feed);
        hpSep.setInternalDiameter(1.5);
        
        process.add(feed);
        process.add(hpSep);
        process.run();
        
        // 2. Configure MPC
        ProcessLinkedMPC mpc = new ProcessLinkedMPC("HP_Sep_MPC", process);
        
        // Manipulated Variables
        mpc.addMV("Feed_Flow", feed, "flowRate", 200.0, 800.0, "kg/hr");
        mpc.addMV("Operating_Pressure", hpSep, "pressure", 30.0, 70.0, "bara");
        
        // Controlled Variables
        mpc.addCV("Gas_Production", hpSep.getGasOutStream(), 
                  "flowRate", 100.0, 400.0, "kg/hr");
        mpc.addCV("Liquid_Level", hpSep, 
                  "liquidLevel", 0.3, 0.7, "fraction");
        
        // Disturbance Variables
        mpc.addDV("Feed_Temperature", feed, "temperature", "C");
        mpc.addDV("Feed_Composition_C1", feed, "methane_fraction", "mol/mol");
        
        // 3. Generate models
        mpc.setLinearizationStepSize(0.05);
        mpc.setSettlingTime(600.0);
        mpc.setSamplingTime(10.0);
        mpc.generateStepResponses();
        
        // 4. Export for industrial MPC
        IndustrialMPCExporter exporter = mpc.createIndustrialExporter();
        exporter.setModelName("HP_Separator");
        exporter.setDescription("High Pressure Separator MPC Model");
        
        // Step response model
        exporter.exportStepResponseModel("hp_sep_model.csv");
        
        // MPC configuration
        exporter.setPredictionHorizon(30);
        exporter.setControlHorizon(10);
        exporter.setExecutionInterval(10.0);
        exporter.exportMPCConfiguration("hp_sep_config.json");
        
        // 5. Export soft sensors
        SoftSensorExporter softSensor = mpc.createSoftSensorExporter();
        softSensor.setFluid(fluid);
        softSensor.exportCalculation("gas_density", "gas_density_calc.csv");
        softSensor.exportCalculation("liquid_density", "liquid_density_calc.csv");
        
        // 6. Export for nonlinear MPC (optional)
        SubrModlExporter subrModl = mpc.createSubrModlExporter();
        subrModl.setModelName("HP_Sep_NL");
        subrModl.exportConfiguration("hp_sep_subrmodl.cnf");
        
        System.out.println("MPC integration files generated successfully!");
    }
}
```

### Production Optimization Setup

```java
// Configure for production optimization
public class ProductionOptimization {
    
    public static void configureOptimization(ProcessLinkedMPC mpc) {
        // Set optimization objective
        mpc.setOptimizationObjective(OptimizationType.MAXIMIZE_THROUGHPUT);
        
        // Define economic weights
        mpc.setEconomicWeight("Gas_Production", 1.0);    // $/kg
        mpc.setEconomicWeight("Oil_Production", 1.5);    // $/kg
        mpc.setEconomicWeight("Power_Consumption", -0.1); // $/kWh
        
        // Configure constraints for optimizer
        mpc.setConstraintPriority("Safety_Limits", Priority.HARD);
        mpc.setConstraintPriority("Environmental", Priority.HARD);
        mpc.setConstraintPriority("Quality_Specs", Priority.SOFT);
        mpc.setConstraintPriority("Equipment_Limits", Priority.SOFT);
        
        // Export optimization configuration
        IndustrialMPCExporter exporter = mpc.createIndustrialExporter();
        exporter.setOptimizationEnabled(true);
        exporter.exportOptimizationConfig("production_opt.json");
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

In thermodynamic simulators like NeqSim, analytical derivatives are impractical because:

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
calc.addOutputVariable("Separator.gasOutStream.flowRate", "kg/hr");
calc.addOutputVariable("Separator.liquidLevel", "fraction");

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
    "Separator.gasOutStream.flowRate",  // output
    "Feed.flowRate"                      // input
);
```

### Gradient (One Output, All Inputs)

```java
// Get gradient of one output w.r.t. all inputs
double[] gradient = calc.getGradient("Separator.gasOutStream.flowRate");
// gradient[0] = ∂gasFlow/∂feedFlow
// gradient[1] = ∂gasFlow/∂feedPressure
// gradient[2] = ∂gasFlow/∂feedTemperature
```

### Hessian (Second Derivatives)

```java
// Get Hessian matrix for optimization
double[][] hessian = calc.calculateHessian("Separator.gasOutStream.flowRate");
// hessian[i][j] = ∂²gasFlow / ∂input_i ∂input_j
```

### Export for External Systems

```java
// Export Jacobian to JSON for AI/ML systems
String json = calc.exportJacobianToJSON();

// Export to CSV for spreadsheet analysis
calc.exportJacobianToCSV("jacobian.csv");
```

### JSON Output Format

```json
{
  "inputs": ["Feed.flowRate", "Feed.pressure"],
  "outputs": ["Separator.gasOutStream.flowRate", "Separator.liquidLevel"],
  "baseInputValues": [100.0, 50.0],
  "baseOutputValues": [85.2, 0.45],
  "jacobian": [
    [0.852, -0.023],
    [0.001, 0.015]
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

*Document Version: 1.0*  
*Last Updated: December 2024*
