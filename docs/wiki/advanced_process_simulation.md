# Advanced Process Simulation

This guide covers advanced features of NeqSim's process simulation capabilities, including recycles, control systems, and dynamic simulation.

## 1. Recycles

In process simulation, a recycle loop occurs when a downstream stream is fed back to an upstream unit. NeqSim handles this using the `Recycle` class, which iterates until the properties of the recycled stream converge.

### How it Works
The `Recycle` unit operation compares the properties (flow rate, composition, temperature, pressure) of the stream from the previous iteration with the current iteration. If the difference is within a specified tolerance, the loop is considered converged.

### Example Usage

```java
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.mixer.StaticMixer;
// ... other imports

// 1. Create Feed and Recycle Streams
Stream feed = new Stream("Feed", fluid);
Stream recycleStream = new Stream("Recycle Stream", fluid.clone()); // Initial guess

// 2. Mix Feed and Recycle
StaticMixer mixer = new StaticMixer("Mixer");
mixer.addStream(feed);
mixer.addStream(recycleStream);

// ... Process units (e.g., Compressor, Cooler, Separator) ...
Separator separator = new Separator("Separator", cooler.getOutletStream());

// 3. Define the Recycle Unit
// The input to the Recycle unit is the stream you want to recycle (e.g., liquid from separator)
Recycle recycle = new Recycle("Recycle Controller");
recycle.addStream(separator.getLiquidOutStream());

// 4. Connect Recycle Output back to Mixer
// IMPORTANT: The output of the Recycle unit is what you connect to the upstream mixer
recycleStream = recycle.getOutletStream(); 
mixer.replaceStream(1, recycleStream); // Or set it up initially if possible

// 5. Add to ProcessSystem
process.add(feed);
process.add(mixer);
// ... add other units ...
process.add(recycle); // Add recycle last or where appropriate in sequence

// 6. Run
process.run();
```

### Tuning
You can adjust the tolerance and maximum iterations:
```java
recycle.setTolerance(1e-6);
recycle.setMaximumIterations(50);
```

## 2. Controllers (PID)

NeqSim allows you to add PID controllers to automate the operation of equipment, such as valves or compressors, to maintain a specific setpoint (e.g., pressure, flow, level).

### Components
*   **ControllerDevice**: The PID controller logic.
*   **Transmitter**: Measures the process variable (PV).
*   **Control Element**: The equipment being adjusted (OP), usually a valve.

### Example: Flow Control

```java
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.measurementdevice.VolumeFlowTransmitter;
import neqsim.process.equipment.valve.ThrottlingValve;

// 1. Create the Stream and Valve
Stream stream = new Stream("Stream", fluid);
ThrottlingValve valve = new ThrottlingValve("Control Valve", stream);

// 2. Create a Transmitter
// Measures flow rate of the stream
VolumeFlowTransmitter flowTransmitter = new VolumeFlowTransmitter(stream);
flowTransmitter.setUnit("kg/hr");
flowTransmitter.setMaximumValue(100.0);
flowTransmitter.setMinimumValue(0.0);

// 3. Create the Controller
ControllerDeviceBaseClass flowController = new ControllerDeviceBaseClass();
flowController.setTransmitter(flowTransmitter);
flowController.setReverseActing(true); // Action depends on process physics
flowController.setControllerSetPoint(50.0); // Target Flow
flowController.setControllerParameters(0.5, 100.0, 0.0); // Kp, Ti, Td

// 4. Assign Controller to Valve
valve.setController(flowController);

// 5. Add to ProcessSystem
process.add(stream);
process.add(valve);
process.add(flowTransmitter); // Transmitter must be added to system

// 6. Run
process.run();
```

## 3. Dynamic Simulation

NeqSim supports dynamic (transient) simulation, allowing you to model how the process changes over time. This is useful for studying startup/shutdown, control system tuning, and buffer tank sizing.

### Setup for Dynamics
1.  **Enable Dynamic Calculation**: Some units need explicit flags (e.g., `separator.setCalculateSteadyState(false)`).
2.  **Geometry**: Units like separators and tanks require physical dimensions (diameter, length) to calculate volume and levels.
3.  **Time Step**: Set the simulation time step.

### Example Loop

```java
// ... Setup system with valves, separators, controllers ...

// Configure unit for dynamics
separator.setCalculateSteadyState(false); // Enable dynamic level calculation
separator.setInternalDiameter(2.0);
separator.setSeparatorLength(5.0);

// Run steady state first to get initial condition
process.run();

// Dynamic Loop
double timeStep = 10.0; // seconds
process.setTimeStep(timeStep);

for (int i = 0; i < 100; i++) {
    process.runTransient();
    
    // Log results
    double time = i * timeStep;
    double level = separator.getLiquidLevel();
    double pressure = separator.getGasOutStream().getPressure();
    
    System.out.println("Time: " + time + " s, Level: " + level + ", Pressure: " + pressure);
}
```

### Key Methods
*   `process.setTimeStep(double seconds)`: Sets the integration step.
*   `process.runTransient()`: Advances the simulation by one time step.
*   `unit.setCalculateSteadyState(boolean)`: Toggles between steady-state (mass balance) and dynamic (accumulation) modes for specific equipment.
