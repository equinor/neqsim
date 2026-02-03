---
title: MPC Integration for NeqSim Process Systems
description: This document describes the Model Predictive Control (MPC) integration package for NeqSim, which bridges the gap between rigorous process simulation and advanced control systems.
---

# MPC Integration for NeqSim Process Systems

This document describes the Model Predictive Control (MPC) integration package for NeqSim, which bridges the gap between rigorous process simulation and advanced control systems.

## Overview

The `neqsim.process.mpc` package provides seamless integration between NeqSim's thermodynamic process simulation (`ProcessSystem`) and Model Predictive Control. It enables:

- **Automatic Model Identification** - Calculate Jacobian matrices or step responses directly from the simulation
- **Variable Binding** - Link MPC variables (MVs, CVs, DVs) to process equipment properties
- **Nonlinear Prediction** - Use full NeqSim simulation for multi-step prediction
- **Model Export** - Export state-space models to JSON, CSV, or MATLAB format

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           ProcessLinkedMPC                                   │
│       (High-level bridge between ProcessSystem and MPC)                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ Manipulated │  │ Controlled  │  │ Disturbance │  │    State    │         │
│  │  Variable   │  │  Variable   │  │  Variable   │  │  Variable   │         │
│  │    (MV)     │  │    (CV)     │  │    (DV)     │  │   (SVR)     │         │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘         │
│         │                │                │                │                 │
│         └────────────────┴────────────────┴────────────────┘                 │
│                                    │                                         │
│         ┌──────────────────────────┼──────────────────────────┐              │
│         ▼                          ▼                          ▼              │
│  ┌─────────────────┐  ┌─────────────────────────┐  ┌─────────────────┐      │
│  │ ProcessLinear-  │  │    StepResponse-        │  │   Nonlinear-    │      │
│  │     izer        │  │      Generator          │  │   Predictor     │      │
│  └────────┬────────┘  └───────────┬─────────────┘  └────────┬────────┘      │
│           │                       │                          │               │
│           └───────────────────────┼──────────────────────────┘               │
│                                   ▼                                          │
│                    ┌───────────────────────────┐                             │
│                    │   LinearizationResult     │                             │
│                    │  (Gain matrices + OP)     │                             │
│                    └─────────────┬─────────────┘                             │
│                                  │                                           │
│  ┌───────────────────────────────┼───────────────────────────────┐           │
│  │                               │                               │           │
│  ▼                               ▼                               ▼           │
│ ┌──────────────┐  ┌────────────────────────┐  ┌────────────────────┐        │
│ │ StateSpace-  │  │ IndustrialMPC-         │  │   SubrModl-        │        │
│ │  Exporter    │  │   Exporter             │  │    Exporter        │        │
│ │(JSON/MATLAB) │  │(Step Response/Config)  │  │ (Nonlinear Model)  │        │
│ └──────────────┘  └────────────────────────┘  └────────────────────┘        │
│                               │                                              │
│  ┌────────────────────────────┴────────────────────────────┐                 │
│  │                                                         │                 │
│  ▼                                                         ▼                 │
│ ┌─────────────────────────┐               ┌─────────────────────────┐       │
│ │ ControllerDataExchange  │               │   SoftSensorExporter    │       │
│ │   (Real-time PCS I/O)   │               │  (Estimator Configs)    │       │
│ └─────────────────────────┘               └─────────────────────────┘       │
│                                                                              │
└──────────────────────────────────┬───────────────────────────────────────────┘
                                   │
                                   ▼
                        ┌─────────────────────┐
                        │   ProcessSystem     │
                        │  (NeqSim Simulation)│
                        └─────────────────────┘
```

## Quick Start

### Basic MPC Setup

```java
import neqsim.process.mpc.*;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemSrkEos;

// Create fluid
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.8);
fluid.addComponent("ethane", 0.15);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");

// Build process
ProcessSystem process = new ProcessSystem();

Stream feed = new Stream("feed", fluid);
feed.setFlowRate(1000.0, "kg/hr");
feed.setTemperature(25.0, "C");
feed.setPressure(50.0, "bara");

ThrottlingValve valve = new ThrottlingValve("inlet_valve", feed);
valve.setOutletPressure(30.0);

Separator separator = new Separator("separator", valve.getOutletStream());

process.add(feed);
process.add(valve);
process.add(separator);
process.run();

// Create linked MPC
ProcessLinkedMPC mpc = new ProcessLinkedMPC("pressureController", process);

// Define variables
mpc.addMV("inlet_valve", "opening", 0.0, 1.0);  // Valve opening 0-100%
mpc.addCV("separator", "pressure", 30.0);        // Control to 30 bar

// Configure controller
mpc.setSampleTime(60.0);          // 60 second sample time
mpc.setPredictionHorizon(20);     // 20 samples = 20 minutes
mpc.setControlHorizon(5);         // 5 control moves

// Identify model
mpc.identifyModel(60.0);

// Control loop
for (int step = 0; step < 100; step++) {
    double[] moves = mpc.step();
    System.out.printf("Step %d: MV=%.3f CV=%.2f%n", 
        step, moves[0], mpc.getCurrentCVs()[0]);
}
```

## Variable Types

### Manipulated Variables (MVs)

MVs are process inputs that the controller can adjust:

```java
// Create MV with basic bounds
ManipulatedVariable mv = mpc.addMV("valve", "opening", 0.0, 1.0);

// Create MV with rate limit
ManipulatedVariable mv2 = mpc.addMV("heater", "duty", 0.0, 1000.0, 100.0);
// Rate limit: max 100 kW change per sample

// Set move suppression cost
mpc.setMoveSuppressionWeight("valve.opening", 0.5);
```

**Supported MV Properties:**
- `opening` - Valve opening (0-1)
- `duty` - Heater/cooler duty (kW)
- `flowRate` - Stream flow rate
- `speed` - Compressor/pump speed
- `outletPressure` - Pressure controller setpoint

### Controlled Variables (CVs)

CVs are process outputs that we want to control:

```java
// Setpoint control
ControlledVariable cv = mpc.addCV("separator", "pressure", 30.0);
cv.setWeight(1.0);  // CV priority

// Zone control (CV only needs to stay within bounds)
ControlledVariable cv2 = mpc.addCVZone("separator", "liquidLevel", 30.0, 70.0);

// Hard constraints
mpc.setConstraint("separator", "pressure", 25.0, 35.0);
```

**Supported CV Properties:**
- `pressure` - Equipment pressure (bar)
- `temperature` - Stream/equipment temperature (°C or K)
- `liquidLevel` - Separator liquid level (%)
- `flowRate` - Stream flow rate
- `quality` - Vapor fraction

### Disturbance Variables (DVs)

DVs are measured but uncontrolled disturbances for feedforward:

```java
DisturbanceVariable dv = mpc.addDV("feed", "flowRate");
```

## Model Identification

### Linearization Method

Fast identification using finite differences:

```java
mpc.identifyModel(60.0);  // 60 second sample time

// Access results
LinearizationResult result = mpc.getLinearizationResult();
double[][] gains = result.getGainMatrix();
double gain = result.getGain("separator.pressure", "valve.opening");
```

### Step Response Method

More accurate for highly nonlinear processes:

```java
mpc.identifyModelFromStepTests(
    60.0,    // Sample time (seconds)
    600.0,   // Test duration per step (seconds)
    5.0      // Step size (% of range)
);
```

### Direct Linearizer Access

For more control over the identification process:

```java
ProcessLinearizer linearizer = new ProcessLinearizer(process);
linearizer.setRelativePerturbation(0.01);  // 1% perturbation
linearizer.setUseCentralDifference(true);

// Add variables
linearizer.addMV(new ManipulatedVariable("valve.opening", valve, "opening"));
linearizer.addCV(new ControlledVariable("sep.pressure", separator, "pressure"));

// Linearize
LinearizationResult result = linearizer.linearize();

// Check linearity
boolean isLinear = linearizer.checkLinearity(0.05);  // 5% tolerance
```

## Model Export

Export models for external MPC systems:

```java
StateSpaceExporter exporter = mpc.exportModel();

// Generate discrete state-space model
StateSpaceExporter.StateSpaceModel model = exporter.toDiscreteStateSpace(60.0);

// Export to JSON (for Python)
exporter.exportJSON("process_model.json");

// Export to MATLAB
exporter.exportMATLAB("process_model.m");

// Export to CSV
exporter.exportCSV("model_");  // Creates model_A.csv, model_B.csv, etc.
```

### JSON Format

```json
{
  "sampleTime": 60.0,
  "sampleTimeUnit": "seconds",
  "numStates": 2,
  "numInputs": 1,
  "numOutputs": 2,
  "inputNames": ["valve.opening"],
  "outputNames": ["separator.pressure", "separator.liquidLevel"],
  "A": [[0.95, 0.0], [0.0, 0.98]],
  "B": [[0.5], [0.1]],
  "C": [[1.0, 0.0], [0.0, 1.0]],
  "D": [[0.0], [0.0]]
}
```

### MATLAB Format

```matlab
% NeqSim State-Space Model Export
A = [0.95 0.0; 0.0 0.98];
B = [0.5; 0.1];
C = [1.0 0.0; 0.0 1.0];
D = [0.0; 0.0];
Ts = 60.0;

sys = ss(A, B, C, D, Ts);
sys.InputName = {'valve.opening'};
sys.OutputName = {'separator.pressure', 'separator.liquidLevel'};
```

## Nonlinear Prediction

For highly nonlinear processes, use full NeqSim simulation for prediction:

```java
// Enable nonlinear prediction
mpc.setUseNonlinearPrediction(true);
mpc.identifyModel(60.0);

// Now calculate() uses full simulation
double[] moves = mpc.calculate();
```

### Direct Predictor Access

```java
NonlinearPredictor predictor = new NonlinearPredictor(process, 60.0, 20);

// Add variables
predictor.addMV(new ManipulatedVariable("valve.opening", valve, "opening"));
predictor.addCV(new ControlledVariable("sep.pressure", separator, "pressure"));

// Create trajectory
NonlinearPredictor.MVTrajectory trajectory = new NonlinearPredictor.MVTrajectory();
double[] valveTrajectory = new double[20];
Arrays.fill(valveTrajectory, 0.6);  // Constant 60% opening
trajectory.addMV("valve.opening", valveTrajectory);

// Predict
NonlinearPredictor.PredictionResult result = predictor.predict(trajectory);
double[] pressurePrediction = result.getCVTrajectory("separator.pressure");
```

## Advanced Features

### Model Updating

Enable automatic model updates during operation:

```java
mpc.setModelUpdateInterval(100);  // Re-linearize every 100 steps
```

### Constraint Handling

```java
// CV hard constraints (must be satisfied)
ControlledVariable cv = mpc.getControlledVariables().get(0);
cv.setHardConstraints(true);
cv.setMinValue(25.0);
cv.setMaxValue(35.0);

// CV soft constraints (penalty-based)
cv.setHardConstraints(false);
cv.setConstraintViolationCost(100.0);
```

### Zone Control

```java
// CV only penalized when outside zone
ControlledVariable cv = mpc.addCVZone("tank", "level", 30.0, 70.0);
// Controller only acts when level leaves 30-70% zone
```

## Step Response Generator

For detailed model identification:

```java
StepResponseGenerator generator = new StepResponseGenerator(process);

// Add variables
generator.addMV(mv);
generator.addCV(cv);

// Configure
generator.setStepDuration(600.0);     // 10 minutes per test
generator.setStepSize(0.05);          // 5% steps
generator.setSampleInterval(10.0);    // 10 second samples
generator.setBidirectional(true);     // Test both directions

// Generate all responses
StepResponseGenerator.StepResponseMatrix matrix = generator.generateAllResponses();

// Access individual responses
StepResponse response = matrix.get("separator.pressure", "valve.opening");
double gain = response.getGain();
double timeConstant = response.getTimeConstant();
double deadTime = response.getDeadTime();

// Export for DMC
StateSpaceExporter exporter = new StateSpaceExporter(matrix);
exporter.exportStepCoefficients("dmc_model.csv", 60);
```

## Integration with AI Platforms

The MPC integration package is designed for seamless integration with AI/ML platforms:

### Python Integration via neqsim-python

```python
import jpype
import neqsim

# Access MPC classes
ProcessLinkedMPC = jpype.JClass('neqsim.process.mpc.ProcessLinkedMPC')
StateSpaceExporter = jpype.JClass('neqsim.process.mpc.StateSpaceExporter')

# Create MPC (assuming process is already set up)
mpc = ProcessLinkedMPC("controller", process)
mpc.addMV("valve", "opening", 0.0, 1.0)
mpc.addCV("separator", "pressure", 30.0)
mpc.identifyModel(60.0)

# Export model
exporter = mpc.exportModel()
exporter.exportJSON("model.json")

# Load in Python for custom optimization
import json
with open("model.json") as f:
    model = json.load(f)

A = np.array(model['A'])
B = np.array(model['B'])
# Use with scipy.signal, python-control, or custom MPC
```

### Real-Time Control Loop

```java
// Configure for real-time
mpc.setSampleTime(1.0);  // 1 second
mpc.setPredictionHorizon(60);
mpc.setControlHorizon(10);
mpc.identifyModel(1.0);

// Real-time loop
ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
executor.scheduleAtFixedRate(() -> {
    try {
        // Read current measurements (from OPC, etc.)
        updateProcessMeasurements(process);
        
        // Calculate and apply control
        mpc.step();
        
        // Write outputs (to OPC, etc.)
        writeProcessOutputs(mpc.getLastMoves());
    } catch (Exception e) {
        logger.error("Control error", e);
    }
}, 0, 1000, TimeUnit.MILLISECONDS);
```

## Best Practices

1. **Start with linearization** - Use `identifyModel()` first; only switch to step response testing if needed for accuracy.

2. **Check linearity** - Use `ProcessLinearizer.checkLinearity()` to validate the linearization accuracy.

3. **Conservative tuning** - Start with larger prediction horizons and move suppression weights, then tighten.

4. **Model updates** - Enable periodic re-linearization for processes with significant nonlinearity.

5. **Constraint margins** - Leave margin between operational constraints and safety limits.

6. **Test in simulation** - Validate controller behavior thoroughly before connecting to real equipment.

## Package Structure

```
neqsim/process/mpc/
├── package-info.java           # Package documentation
├── MPCVariable.java            # Base class for MPC variables
├── ManipulatedVariable.java    # Manipulated variable (MV)
├── ControlledVariable.java     # Controlled variable (CV)
├── DisturbanceVariable.java    # Disturbance variable (DV)
├── StateVariable.java          # State variable (SVR) for nonlinear MPC
├── ProcessLinearizer.java      # Automatic Jacobian calculation
├── LinearizationResult.java    # Gain matrices container
├── StepResponse.java           # Single MV-CV step response
├── StepResponseGenerator.java  # Automated step testing
├── NonlinearPredictor.java     # Full simulation prediction
├── StateSpaceExporter.java     # Model export (JSON/CSV/MATLAB)
├── ProcessLinkedMPC.java       # Main bridge class
├── IndustrialMPCExporter.java  # Step response & config export for industrial MPC
├── ControllerDataExchange.java # Real-time data exchange interface
├── SoftSensorExporter.java     # Soft-sensor/estimator configurations
└── SubrModlExporter.java       # Nonlinear model export (SubrModl format)
```

## Industrial Control System Integration

The MPC package includes comprehensive support for integration with industrial MPC platforms.

### Step Response Model Export

Export models in formats compatible with industrial MPC systems:

```java
IndustrialMPCExporter exporter = mpc.createIndustrialExporter();
exporter.setTagPrefix("UNIT1.separator");
exporter.setApplicationName("GasProcessing");

// Export step response coefficients in CSV format
exporter.exportStepResponseCSV("step_responses.csv");

// Export gain matrix
exporter.exportGainMatrix("gains.csv");

// Export complete object structure for configuration
exporter.exportObjectStructure("controller_config.json");

// Export comprehensive configuration with all model data
exporter.exportComprehensiveConfiguration("mpc_config.json");
```

### Real-Time Data Exchange

Interface with Process Control Systems (PCS) using standardized data exchange:

```java
ControllerDataExchange exchange = mpc.createDataExchange();
exchange.setTagPrefix("UNIT1.MPC");

// Control loop with external controller
while (running) {
    // Update inputs from process measurements
    exchange.updateInputs(mvValues, cvValues, dvValues);
    exchange.updateSetpoints(setpoints);
    exchange.updateLimits(cvLowLimits, cvHighLimits, mvLowLimits, mvHighLimits);

    // Execute and get outputs
    exchange.execute();
    ControllerDataExchange.ControllerOutput output = exchange.getOutputs();

    // Check quality and status
    if (output.getStatus() == ControllerDataExchange.ExecutionStatus.SUCCESS) {
        applyMVs(output.getMvTargets());
    }
}
```

**Quality Flags:**
- `GOOD` - Valid measurement
- `BAD` - Invalid/failed measurement
- `UNCERTAIN` - Quality questionable
- `MANUAL` - Manually entered value
- `CLAMPED` - Value at limit

**Execution Status:**
- `READY` - Controller ready for execution
- `SUCCESS` - Execution completed successfully
- `WARNING` - Completed with warnings
- `FAILED` - Execution failed
- `MODEL_STALE` - Model needs update

### Soft Sensor Export

Export soft-sensor configurations for industrial calculation engines:

```java
SoftSensorExporter softExporter = new SoftSensorExporter(process);
softExporter.setTagPrefix("UNIT1");
softExporter.setApplicationName("GasProcessing");

// Add sensors for thermodynamic properties
softExporter.addDensitySensor("sep_gas_density", "separator", "gas outlet");
softExporter.addViscositySensor("sep_oil_visc", "separator", "oil outlet");
softExporter.addPhaseFractionSensor("sep_gas_frac", "separator");
softExporter.addCompositionEstimator("sep_comp", "separator", "gas outlet",
    new String[]{"methane", "ethane", "propane"});

// Export in JSON and CVT formats
softExporter.exportConfiguration("soft_sensors.json");
softExporter.exportCVTFormat("soft_sensors.cvt");
```

### Nonlinear MPC with State Variables

For nonlinear MPC systems that use programmed model objects:

```java
// Create MPC with state variables
ProcessLinkedMPC mpc = new ProcessLinkedMPC("wellController", process);
mpc.addMV("choke", "opening", 0.0, 1.0);
mpc.addCV("well", "pressure", 50.0);
mpc.addDV("reservoir", "pressure");

// Add state variables (SVR) for internal model states
StateVariable flowIn = mpc.addSVR("well", "flowIn", "qin");
StateVariable flowOut = mpc.addSVR("well", "flowOut", "qout");
mpc.addSVR("choke", "cv", "cv");

// Configure bias handling for state estimation
flowIn.setBiasTfilt(30.0);   // 30 second filter on bias
flowIn.setBiasTpred(120.0);  // 2 minute prediction decay

// Export for nonlinear MPC system
SubrModlExporter exporter = mpc.createSubrModlExporter();
exporter.setModelName("WellModel");
exporter.addParameter("Volume", 100.0, "m3");
exporter.addParameter("Height", 2000.0, "m");
exporter.addParameter("Density", 700.0, "kg/m3");
exporter.addParameter("Compressibility", 500.0, "bar");
exporter.addParameter("ProductionIndex", 8.0, "m3/h/bar");

// Export configuration files
exporter.exportConfiguration("well_config.txt");
exporter.exportMPCConfiguration("mpc_config.txt", true); // true = SQP solver
exporter.exportIndexTable("well_ixid.cpp");
exporter.exportJSON("well_model.json");
```

**Generated Configuration Example:**

```
WellModelProc:    NonlinearSQP
         Volume=  100
         Height=  2000
        Density=  700
Compressibility=  500
ProductionIndex=  8

  SubrXvr:       Pdownhole
         Text1=  "Downhole pressure"
         Text2=  ""
         DtaIx=  "pdh"
          Init=  147.7

  SubrXvr:       Pwellhead
         Text1=  "Wellhead pressure"
         Text2=  ""
         DtaIx=  "pwh"
          Init=  10.4
```

**MPC Configuration Parameters:**

| Parameter | Description | Typical Value |
|-----------|-------------|---------------|
| SteadySolver | Steady-state solver type | SQP or QP |
| IterOpt | Enable iterative optimization | ON/OFF |
| IterNewSens | Recalculate sensitivities each iteration | ON/OFF |
| IterQpMax | Maximum QP iterations | 10 |
| IterLineMax | Maximum line search iterations | 10 |
| LinErrorLim | Linearization error limit | 0.2 |
| MajItLim | Major iteration limit (SQP) | 200 |
| FuncPrec | Function precision | 1e-08 |
| FeTol | Constraint feasibility tolerance | 1e-03 |
| OptimTol | Optimality tolerance | 1e-05 |

## See Also

- [ModelPredictiveController](../controllerdevice) - Underlying MPC implementation
- [ProcessSystem](../processmodel) - NeqSim process simulation
- [AI Platform Integration](ai_platform_integration.md) - Broader AI integration guide
