# Process Simulation in NeqSim

NeqSim allows you to model complex process plants by connecting unit operations (like separators, compressors, heat exchangers) with streams. The core class for managing these simulations is `ProcessSystem`.

## Basic Concepts

*   **ProcessSystem**: The container for all unit operations and streams. It manages the execution order and solving of the process.
*   **Stream**: Represents a flow of fluid. It holds a reference to a thermodynamic system (fluid object) and defines flow rate, temperature, and pressure.
*   **Unit Operation**: Equipment that modifies streams (e.g., `Separator`, `Compressor`, `Heater`) or measures properties.
*   **Process Module**: A grouped set of unit operations that perform a specific task (e.g., TEG Dehydration, Separation Train).

## Step-by-Step Guide

### 1. Define the Fluid
First, create a thermodynamic system using a fluid package (e.g., SRK, CPA).

```java
SystemInterface fluid = new SystemSrkEos(298.15, 50.0); // 298.15 K, 50 bar
fluid.addComponent("methane", 90.0);
fluid.addComponent("ethane", 5.0);
fluid.addComponent("propane", 3.0);
fluid.addComponent("n-heptane", 2.0);
fluid.setMixingRule("classic");
```

### 2. Create the Feed Stream
Create a `Stream` object using the fluid. This acts as the inlet to your process.

```java
Stream feedStream = new Stream("Feed Stream", fluid);
feedStream.setFlowRate(1000.0, "kg/hr");
feedStream.setTemperature(30.0, "C");
feedStream.setPressure(50.0, "bara");
```

### 3. Add Unit Operations
Instantiate unit operations and connect them. Most units take an input stream in their constructor or via a `setInletStream` method.

```java
// Add a valve to reduce pressure
ThrottlingValve valve = new ThrottlingValve("Inlet Valve", feedStream);
valve.setOutletPressure(30.0); // bar

// Add a separator connected to the valve outlet
Separator separator = new Separator("Test Separator", valve.getOutletStream());
```

### 4. Create the Process System
Add all operations to a `ProcessSystem`.

```java
ProcessSystem process = new ProcessSystem();
process.add(feedStream);
process.add(valve);
process.add(separator);
```

### 5. Run the Simulation
Execute the simulation. NeqSim will solve the units in the order they were added (or automatically sort them if needed).

```java
process.run();
```

### 6. Retrieve Results
Access the results from the unit operations or streams.

```java
double gasRate = separator.getGasOutStream().getFlowRate("kg/hr");
double liquidRate = separator.getLiquidOutStream().getFlowRate("kg/hr");
System.out.println("Gas Rate: " + gasRate);
System.out.println("Liquid Rate: " + liquidRate);
```

## Process Modules

Process modules are pre-configured collections of unit operations designed to perform standard processing tasks. They are useful for quickly building complex plants without defining every single component.

### Using a Standard Module
NeqSim comes with several built-in modules in `neqsim.process.processmodel.processmodules`.

**Example: TEG Dehydration Module**

```java
import neqsim.process.processmodel.processmodules.GlycolDehydrationlModule;

// ... (Define feed stream as above) ...

// Initialize the module
GlycolDehydrationlModule tegModule = new GlycolDehydrationlModule("TEG Plant");
tegModule.addInputStream("GasFeed", separator.getGasOutStream());
tegModule.addInputStream("TEGFeed", tegFeedStream); // Assuming you created a TEG feed stream

// Configure module parameters
tegModule.setSpecification("water content", 50.0); // ppm

// Add to process system
process.add(tegModule);

// Run
process.run();
```

### Creating Custom Modules
You can create your own modules by extending `ProcessModuleBaseClass`. This allows you to encapsulate complex logic and reuse it across different projects.

```java
public class MyCustomModule extends ProcessModuleBaseClass {
    public MyCustomModule(String name) {
        super(name);
    }

    @Override
    public void initializeModule() {
        // Define internal units and connections here
        // operations.add(...)
    }
    
    // Implement other abstract methods...
}
```

## Advanced Features

*   **Recycles**: Use the `Recycle` class to close loops in the process.
*   **Controllers**: Add PID controllers to adjust parameters dynamically during the simulation.
*   **Dynamic Simulation**: `ProcessSystem` supports dynamic simulation for time-dependent analysis.
*   **Bottleneck Analysis**: Analyze capacity utilization and identify system bottlenecks. See [Bottleneck Analysis](bottleneck_analysis.md).

For more examples, check the `src/test/java/neqsim/process` folder in the source code.
