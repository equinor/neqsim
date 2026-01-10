---
layout: default
title: "MPC Integration Tutorial"
description: "Jupyter notebook tutorial for NeqSim"
parent: Examples
nav_order: 1
---

# MPC Integration Tutorial

> **Note:** This is an auto-generated Markdown version of the Jupyter notebook 
> [`MPC_Integration_Tutorial.ipynb`](https://github.com/equinor/neqsim/blob/master/docs/examples/MPC_Integration_Tutorial.ipynb).
> You can also [view it on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/MPC_Integration_Tutorial.ipynb) 
> or [open in Google Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/MPC_Integration_Tutorial.ipynb).

---

## 1. Setup and Process Creation

First, we'll create a simple gas separation process with a control valve and separator.

```python
import jpype
import jpype.imports

# Start JVM with NeqSim
if not jpype.isJVMStarted():
    jpype.startJVM(classpath=['path/to/neqsim.jar'])

# Import NeqSim classes
from neqsim.thermo.system import SystemSrkEos
from neqsim.process.processmodel import ProcessSystem
from neqsim.process.equipment.stream import Stream
from neqsim.process.equipment.valve import ThrottlingValve
from neqsim.process.equipment.separator import Separator
from neqsim.process.mpc import ProcessLinkedMPC, StateSpaceExporter
```

```python
# Create fluid system
fluid = SystemSrkEos(298.15, 50.0)
fluid.addComponent("methane", 0.80)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.addComponent("n-pentane", 0.05)
fluid.setMixingRule("classic")

# Build process
process = ProcessSystem()

# Feed stream
feed = Stream("feed", fluid)
feed.setFlowRate(1000.0, "kg/hr")
feed.setTemperature(25.0, "C")
feed.setPressure(50.0, "bara")

# Inlet valve (our MV)
valve = ThrottlingValve("inlet_valve", feed)
valve.setOutletPressure(30.0)

# Separator (our CV is the pressure)
separator = Separator("separator", valve.getOutletStream())

# Add to process and run
process.add(feed)
process.add(valve)
process.add(separator)
process.run()

print(f"Initial separator pressure: {separator.getPressure():.2f} bar")
print(f"Initial valve opening: {valve.getPercentValveOpening():.1f}%")
```

## 2. MPC Configuration

Now we'll create a ProcessLinkedMPC controller and configure it.

```python
# Create MPC controller
mpc = ProcessLinkedMPC("separatorPressureControl", process)

# Add Manipulated Variable (MV)
# - Equipment: inlet_valve
# - Property: opening (valve position 0-1)
# - Bounds: 0% to 100%
# - Rate limit: 5% per sample
mpc.addMV("inlet_valve", "opening", 0.0, 1.0, 0.05)

# Add Controlled Variable (CV)
# - Equipment: separator  
# - Property: pressure
# - Setpoint: 30 bar
mpc.addCV("separator", "pressure", 30.0)

# Add hard constraints on separator pressure
mpc.setConstraint("separator", "pressure", 25.0, 35.0)

# Configure controller horizons
mpc.setSampleTime(60.0)        # 60 second sample time
mpc.setPredictionHorizon(20)   # 20 samples = 20 minutes ahead
mpc.setControlHorizon(5)       # 5 control moves

print(mpc.getConfigurationSummary())
```

## 3. Model Identification

The MPC needs a process model to predict future behavior. We'll use automatic linearization.

```python
# Identify process model using linearization
mpc.identifyModel(60.0)  # 60 second sample time

# Check results
result = mpc.getLinearizationResult()
print(f"Model identified: {mpc.isModelIdentified()}")
print(f"Number of MVs: {result.getNumMV()}")
print(f"Number of CVs: {result.getNumCV()}")
print()

# Get the gain matrix
gains = result.getGainMatrix()
print(f"Gain (pressure/valve): {gains[0][0]:.4f} bar/%")
print()

# Operating point
print(f"MV operating point: {result.getMVOperatingPoint('inlet_valve.opening'):.3f}")
print(f"CV operating point: {result.getCVOperatingPoint('separator.pressure'):.2f} bar")
```

## 4. Control Simulation

Let's simulate the controller responding to a setpoint change.

```python
import matplotlib.pyplot as plt
import numpy as np

# Storage for results
times = []
mv_values = []
cv_values = []
setpoints = []

# Initial conditions
current_setpoint = 30.0
sample_time = mpc.getSampleTime()

# Simulate 60 steps (1 hour)
for step in range(60):
    time = step * sample_time
    
    # Setpoint change at step 20 (20 minutes)
    if step == 20:
        current_setpoint = 32.0
        mpc.setSetpoint("separator.pressure", current_setpoint)
    
    # Execute control step
    moves = mpc.step()
    
    # Record data
    times.append(time / 60)  # Convert to minutes
    mv_values.append(moves[0] * 100)  # Convert to %
    cv_values.append(mpc.getCurrentCVs()[0])
    setpoints.append(current_setpoint)

# Plot results
fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(10, 6), sharex=True)

ax1.plot(times, cv_values, 'b-', label='Pressure (CV)', linewidth=2)
ax1.plot(times, setpoints, 'r--', label='Setpoint', linewidth=2)
ax1.set_ylabel('Pressure [bar]')
ax1.legend()
ax1.grid(True)
ax1.set_title('MPC Control Response')

ax2.plot(times, mv_values, 'g-', label='Valve Opening (MV)', linewidth=2)
ax2.set_xlabel('Time [minutes]')
ax2.set_ylabel('Valve Opening [%]')
ax2.legend()
ax2.grid(True)

plt.tight_layout()
plt.show()

print(f"Final pressure: {cv_values[-1]:.2f} bar")
print(f"Final valve opening: {mv_values[-1]:.1f}%")
```

## 5. Model Export

Export the identified model for use with external MPC tools.

```python
# Get the state-space exporter
exporter = mpc.exportModel()

# Generate discrete state-space model
ss_model = exporter.toDiscreteStateSpace(60.0)  # 60 second sample time

print("State-Space Model Summary:")
print(ss_model.toString())
print()

# Access matrices
A = np.array(ss_model.getA())
B = np.array(ss_model.getB())
C = np.array(ss_model.getC())
D = np.array(ss_model.getD())

print("A matrix:", A)
print("B matrix:", B)
print("C matrix:", C)
print("D matrix:", D)
```

```python
# Export to JSON
exporter.exportJSON("separator_model.json")
print("Exported to separator_model.json")

# Export to MATLAB
exporter.exportMATLAB("separator_model.m")
print("Exported to separator_model.m")

# Load and display JSON
import json
with open("separator_model.json") as f:
    model_data = json.load(f)
    
print("\nJSON model contents:")
print(json.dumps(model_data, indent=2))
```

## 6. Advanced: Nonlinear Prediction

For highly nonlinear processes, use full NeqSim simulation for prediction.

```python
from neqsim.process.mpc import NonlinearPredictor, ManipulatedVariable, ControlledVariable

# Create nonlinear predictor
predictor = NonlinearPredictor(process, 60.0, 20)  # 60s sample, 20 steps

# Add variables (need to create fresh variable objects)
mv = ManipulatedVariable("valve.opening", valve, "opening")
cv = ControlledVariable("sep.pressure", separator, "pressure")
predictor.addMV(mv)
predictor.addCV(cv)

# Predict with constant valve position
result = predictor.predictConstant()

# Get predicted trajectory
pressure_pred = np.array(result.getCVTrajectory("sep.pressure"))
time_pred = np.arange(len(pressure_pred)) * 60 / 60  # minutes

plt.figure(figsize=(10, 4))
plt.plot(time_pred, pressure_pred, 'b-o', label='Predicted Pressure')
plt.xlabel('Time [minutes]')
plt.ylabel('Pressure [bar]')
plt.title('Nonlinear Prediction (Constant Valve Position)')
plt.grid(True)
plt.legend()
plt.show()
```

```python
# Compare linear vs nonlinear predictions
from neqsim.process.mpc import ProcessLinearizer, LinearizationResult

# Get linear prediction (from state-space model)
x = np.array([cv.readValue()])  # Current state
u = np.array([mv.readValue()])  # Current input

linear_pred = []
for k in range(20):
    y = ss_model.getOutput(x, u)
    linear_pred.append(y[0])
    x = ss_model.stepState(x, u)

# Get nonlinear prediction
nonlinear_pred = np.array(result.getCVTrajectory("sep.pressure"))

# Plot comparison
time = np.arange(20)
plt.figure(figsize=(10, 4))
plt.plot(time, linear_pred, 'b--', label='Linear Model', linewidth=2)
plt.plot(time, nonlinear_pred, 'r-', label='Nonlinear (NeqSim)', linewidth=2)
plt.xlabel('Step')
plt.ylabel('Pressure [bar]')
plt.title('Linear vs Nonlinear Prediction Comparison')
plt.grid(True)
plt.legend()
plt.show()

# Calculate prediction error
mse = np.mean((np.array(linear_pred) - nonlinear_pred)**2)
print(f"Mean Squared Error: {mse:.6f}")
```

## 7. Industrial Control System Integration

Export models and configurations for integration with industrial MPC platforms.

```python
from neqsim.process.mpc import IndustrialMPCExporter, ControllerDataExchange

# Create industrial exporter
exporter = mpc.createIndustrialExporter()
exporter.setTagPrefix("UNIT1.separator")
exporter.setApplicationName("GasProcessing")

# Export step response model in CSV format
exporter.exportStepResponseCSV("step_responses.csv")
print("Exported step response coefficients")

# Export gain matrix
exporter.exportGainMatrix("gains.csv")
print("Exported gain matrix")

# Export comprehensive configuration
exporter.exportComprehensiveConfiguration("mpc_config.json")
print("Exported comprehensive configuration")

# Display the step response file
import pandas as pd
df = pd.read_csv("step_responses.csv")
print("\nStep Response Coefficients (first 10 rows):")
print(df.head(10))
```

```python
# Real-time data exchange interface
exchange = mpc.createDataExchange()
exchange.setTagPrefix("UNIT1.MPC")

# Simulate one control cycle
import numpy as np

# Get current values
mv_values = np.array([0.5])   # 50% valve opening
cv_values = np.array([30.0])  # 30 bar pressure
dv_values = np.array([])      # No disturbances

# Update exchange with current process state
exchange.updateInputs(mv_values, cv_values, dv_values)
exchange.updateSetpoints(np.array([30.0]))
exchange.updateLimits(
    np.array([25.0]),  # CV low limits
    np.array([35.0]),  # CV high limits
    np.array([0.0]),   # MV low limits
    np.array([1.0])    # MV high limits
)

# Execute controller
exchange.execute()

# Get outputs
output = exchange.getOutputs()
print(f"Execution status: {output.getStatus()}")
print(f"MV targets: {list(output.getMvTargets())}")
print(f"CV predictions: {list(output.getCvPredictions())}")
print(f"Timestamp: {output.getTimestamp()}")
```

## 8. Soft Sensor Export

Export soft-sensor configurations for calculation engines.

```python
from neqsim.process.mpc import SoftSensorExporter

# Create soft sensor exporter
soft_exporter = SoftSensorExporter(process)
soft_exporter.setTagPrefix("UNIT1")
soft_exporter.setApplicationName("GasProcessing")

# Add sensors for thermodynamic properties
soft_exporter.addDensitySensor("sep_gas_density", "separator", "gas outlet")
soft_exporter.addPhaseFractionSensor("sep_gas_frac", "separator")

# Export configuration
soft_exporter.exportConfiguration("soft_sensors.json")
print("Exported soft sensor configuration")

# Display the configuration
with open("soft_sensors.json") as f:
    config = json.load(f)
print(json.dumps(config, indent=2))
```

## 9. Nonlinear MPC with State Variables

For complex nonlinear systems like wells or reactors.

```python
from neqsim.process.mpc import SubrModlExporter, StateVariable

# Create MPC with state variables for a hypothetical well model
# (Using our separator as a stand-in for demonstration)
mpc_nonlinear = ProcessLinkedMPC("wellController", process)
mpc_nonlinear.addMV("inlet_valve", "opening", 0.0, 1.0)
mpc_nonlinear.addCV("separator", "pressure", 30.0)

# Add state variables (SVR) - internal model states
# Note: These are for tracking model states, not for control
flow_in = mpc_nonlinear.addSVR("feed", "flowRate", "qin")
flow_in.setBiasTfilt(30.0)   # 30 second bias filter
flow_in.setBiasTpred(120.0)  # 2 minute prediction decay

# Create SubrModl exporter for nonlinear MPC systems
exporter = mpc_nonlinear.createSubrModlExporter()
exporter.setModelName("WellModel")

# Add model parameters
exporter.addParameter("Volume", 100.0, "m3", "Well volume")
exporter.addParameter("Height", 2000.0, "m", "Well height")
exporter.addParameter("Density", 700.0, "kg/m3", "Oil density")
exporter.addParameter("ProductionIndex", 8.0, "m3/h/bar")

# Add SubrXvr definitions for variable linking
exporter.addSubrXvr("Pdownhole", "pdh", "Downhole pressure", 147.7)
exporter.addSubrXvr("Pwellhead", "pwh", "Wellhead pressure", 10.4)

# Export configuration files
exporter.exportConfiguration("well_config.txt")
exporter.exportMPCConfiguration("nmpc_config.txt", True)  # SQP solver
exporter.exportJSON("well_model.json")

print("Exported nonlinear MPC configuration files")

# Display the configuration
with open("well_config.txt") as f:
    print("\n=== Well Configuration ===")
    print(f.read())
```

## Summary

This notebook demonstrated:

1. **ProcessLinkedMPC** - High-level interface for connecting MPC to NeqSim processes
2. **Model Identification** - Automatic linearization using `identifyModel()`
3. **Control Simulation** - Using `step()` to calculate and apply control moves
4. **Model Export** - Exporting to JSON/MATLAB for external tools
5. **Nonlinear Prediction** - Using full simulation for more accurate predictions
6. **Industrial Integration** - Step response export and real-time data exchange
7. **Soft Sensors** - Exporting estimator configurations
8. **Nonlinear MPC** - State variables and SubrModl export

### Key Classes

**Core MPC:**
- `ProcessLinkedMPC` - Main bridge class
- `ManipulatedVariable` - Controller inputs (valves, heaters, etc.)
- `ControlledVariable` - Controller outputs (pressure, temperature, etc.)
- `DisturbanceVariable` - Measured disturbances (feedforward)
- `StateVariable` - Internal model states for nonlinear MPC

**Model Identification:**
- `ProcessLinearizer` - Automatic Jacobian calculation
- `StepResponseGenerator` - Detailed step testing
- `NonlinearPredictor` - Full simulation prediction

**Export & Integration:**
- `StateSpaceExporter` - JSON/MATLAB/CSV export
- `IndustrialMPCExporter` - Step response models for industrial MPC
- `ControllerDataExchange` - Real-time PCS interface
- `SoftSensorExporter` - Estimator configurations
- `SubrModlExporter` - Nonlinear model export

### Next Steps

- Try adding multiple MVs and CVs for MIMO control
- Experiment with zone control for level optimization
- Use step response identification for more accurate models
- Integrate with external MPC solvers via JSON export
- Configure real-time data exchange with OPC/Modbus

