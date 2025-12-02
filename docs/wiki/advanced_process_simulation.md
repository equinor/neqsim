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

## 4. Combining Process Systems (ProcessModel)

For large simulations, it is often better to split the plant into smaller, manageable `ProcessSystem` objects (e.g., "Inlet Separation", "Gas Compression", "Oil Stabilization") and then combine them into a single `ProcessModel`.

### Benefits
*   **Modularity**: Develop and test sections independently.
*   **Organization**: Keeps large flowsheets structured.
*   **Execution Control**: The `ProcessModel` manages the execution of sub-systems.

### Example

```java
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

// 1. Create Individual Process Systems
ProcessSystem inletSystem = new ProcessSystem();
inletSystem.setName("Inlet Section");
// ... add units to inletSystem ...

ProcessSystem compressionSystem = new ProcessSystem();
compressionSystem.setName("Compression Section");
// ... add units to compressionSystem ...

// 2. Connect Systems
// Typically, a stream from the first system is used as input to the second
Stream gasFromInlet = (Stream) inletSystem.getUnit("Inlet Separator").getGasOutStream();
Compressor compressor = new Compressor("1st Stage Compressor", gasFromInlet);
compressionSystem.add(compressor);

// 3. Create ProcessModel
ProcessModel plantModel = new ProcessModel();
plantModel.add("Inlet", inletSystem);
plantModel.add("Compression", compressionSystem);

// 4. Run the Full Model
plantModel.run();
```

The `ProcessModel` will execute the added systems in the order they were added (or based on internal logic if configured). It ensures that data flows correctly between the connected systems.

## 5. Reusable templates and composition helpers

To reduce boilerplate when assembling larger flowsheets, reuse the `Recycle`, controller, and `ProcessModel` patterns as pre-made building blocks. The following templates can be copied as-is or combined inside a `ProcessModel` catalog to let automated agents stitch together flowsheets without rewiring every unit manually.

### Template: Inlet separator train with recycle

**Building blocks:** feed stream → choke valve (optional) → inlet cooler → three-phase separator → level/pressure controllers → recycle loop.

**Why this helps:** captures the standard inlet handling motif (cooling, phase split, level trim) while providing a ready-made recycle loop for gas reprocessing or compressor suction stabilization.

```java
// Streams
Stream feed = new Stream("Feed", feedFluid);
Stream recycleStream = new Stream("Recycle Seed", feedFluid.clone());

// Front-end conditioning
ThrottlingValve choke = new ThrottlingValve("Choke", feed);
Cooler inletCooler = new Cooler("Inlet Cooler", choke.getOutletStream());
Separator inletSep = new Separator("Inlet Separator", inletCooler.getOutletStream());

// Controllers
LevelTransmitter levelTI = new LevelTransmitter(inletSep);
ControllerDeviceBaseClass levelController = new ControllerDeviceBaseClass();
levelController.setTransmitter(levelTI);
levelController.setControllerSetPoint(0.6); // 60% level
levelController.setReverseActing(true);
ThrottlingValve levelValve = new ThrottlingValve("Liquid LV", inletSep.getLiquidOutStream());
levelValve.setController(levelController);

PressureTransmitter pTI = new PressureTransmitter(inletSep);
ControllerDeviceBaseClass pressureController = new ControllerDeviceBaseClass();
pressureController.setTransmitter(pTI);
pressureController.setControllerSetPoint(50.0); // bara
pressureController.setReverseActing(false);
ThrottlingValve pressureValve = new ThrottlingValve("Gas PCV", inletSep.getGasOutStream());
pressureValve.setController(pressureController);

// Recycle
Recycle recycle = new Recycle("Separator Gas Recycle");
recycle.addStream(pressureValve.getOutletStream());
recycle.setTolerance(1e-6);
recycle.setMaximumIterations(50);

// Stitch recycle back to the front-end mixer (or directly to choke/cooler)
StaticMixer frontMixer = new StaticMixer("Front Mixer");
frontMixer.addStream(feed);
frontMixer.addStream(recycle.getOutletStream());
choke.setOutletStream(frontMixer.getOutStream());

// Register in a ProcessSystem
ProcessSystem inletSystem = new ProcessSystem();
inletSystem.add(feed, recycleStream, choke, inletCooler, inletSep, levelTI, levelValve, pTI, pressureValve, recycle, frontMixer);
```

**Composition hints:**
- Expose `inletSep.getGasOutStream()` and `inletSep.getOilOutStream()` as outputs so other templates (e.g., gas compression or stabilizer) can consume them.
- When embedding inside a `ProcessModel`, add this system first so downstream templates can reference the separator gas as an upstream dependency.

### Template: Two-stage compression with interstage cooling

**Building blocks:** gas feed → stage 1 compressor → interstage cooler + separator (optional) → stage 2 compressor → aftercooler → pressure controller or recycle.

**Why this helps:** standardizes multi-stage compression including thermal conditioning between stages and hooks for surge/recycle control.

```java
// Assume gasFeed is provided by an upstream template (e.g., inlet separator train)
Compressor comp1 = new Compressor("1st Stage", gasFeed);
Cooler intercooler = new Cooler("Interstage Cooler", comp1.getOutletStream());
Separator interSep = new Separator("Interstage Separator", intercooler.getOutletStream());

Compressor comp2 = new Compressor("2nd Stage", interSep.getGasOutStream());
Cooler afterCooler = new Cooler("Aftercooler", comp2.getOutletStream());

// Discharge pressure control via recycle
PressureTransmitter dischargePT = new PressureTransmitter(afterCooler);
ControllerDeviceBaseClass dischargePC = new ControllerDeviceBaseClass();
dischargePC.setTransmitter(dischargePT);
dischargePC.setControllerSetPoint(100.0); // bara
dischargePC.setReverseActing(false);

ThrottlingValve recycleValve = new ThrottlingValve("Discharge Recycle Valve", afterCooler.getOutletStream());
recycleValve.setController(dischargePC);

Recycle dischargeRecycle = new Recycle("Compression Recycle");
dischargeRecycle.addStream(recycleValve.getOutletStream());
dischargeRecycle.setTolerance(1e-7);
dischargeRecycle.setMaximumIterations(75);

// Tie recycle back to first-stage suction
StaticMixer suctionMixer = new StaticMixer("Suction Mixer");
suctionMixer.addStream(gasFeed);
suctionMixer.addStream(dischargeRecycle.getOutletStream());
comp1.setInletStream(suctionMixer.getOutStream());

// Organize as a ProcessSystem for catalog reuse
ProcessSystem compressionSystem = new ProcessSystem();
compressionSystem.add(comp1, intercooler, interSep, comp2, afterCooler, dischargePT, recycleValve, dischargeRecycle, suctionMixer);
```

**Composition hints:**
- When combined with the inlet separator template, connect `gasFeed` to `inletSep.getGasOutStream()` and merge the `ProcessSystem` instances using `ProcessModel`.
- Expose optional outputs (e.g., `interSep.getOilOutStream()`) for condensate handling templates.
- PID tuning parameters (`setControllerParameters`) can be kept in a shared catalog so AI agents can swap them without editing structure.
