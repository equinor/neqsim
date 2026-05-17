---
title: "Process Calculator"
description: "The `Calculator` unit operation in NeqSim provides a flexible way to perform custom calculations and data manipulation within a process simulation. It allows users to define arbitrary logic that can r..."
---

# Process Calculator

The `Calculator` unit operation in NeqSim provides a flexible way to perform custom calculations and data manipulation within a process simulation. It allows users to define arbitrary logic that can read properties from input process equipment and modify properties of output process equipment. Custom lambdas are the recommended hook for AI-generated calculations so you can swap in new behavior without rebuilding the process topology.

This is particularly useful for:
- Calculating derived properties (e.g., total energy, efficiency).
- Transferring data between unconnected parts of a process.
- Implementing simple control logic or adjustments based on calculated values (e.g., adjusting a stream temperature based on combustion energy).

## Usage

The `Calculator` class is located in `neqsim.process.equipment.util`.

### Basic Setup

1.  **Create the Calculator**: Instantiate the `Calculator` with a name.
2.  **Add Inputs**: Use `addInputVariable()` to add one or more process equipment objects (e.g., Streams) that will be used in the calculation.
3.  **Set Output**: Use `setOutputVariable()` to set the target process equipment that will be modified by the calculation.
4.  **Define Logic**: Use `setCalculationMethod()` to define the custom calculation logic. This method accepts a `BiConsumer<ArrayList<ProcessEquipmentInterface>, ProcessEquipmentInterface>`, which can be easily implemented using a Java Lambda expression or a declarative preset.

### Example: Energy Calculation and Temperature Adjustment

The following example demonstrates how to calculate the total energy of an inlet stream (based on Lower Calorific Value) and use that energy to adjust the temperature of an outlet stream.

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Calculator;
import neqsim.thermo.system.SystemSrkEos;

public class CalculatorExample {
    public static void main(String[] args) {
        // 1. Setup the simulation objects
        SystemSrkEos testSystem = new SystemSrkEos(298.15, 10.0);
        testSystem.addComponent("methane", 100.0);
        
        Stream inletStream = new Stream("inlet stream", testSystem);
        inletStream.setFlowRate(1000.0, "kg/hr");
        inletStream.run();

        Stream outletStream = new Stream("outlet stream", testSystem.clone());
        outletStream.setFlowRate(1000.0, "kg/hr");
        outletStream.setTemperature(20.0, "C");
        outletStream.run();

        // 2. Create the Calculator
        Calculator energyCalculator = new Calculator("Energy Calculator");

        // 3. Configure Inputs and Outputs
        energyCalculator.addInputVariable(inletStream);
        energyCalculator.setOutputVariable(outletStream);

        // 4. Define the Custom Calculation Logic
        energyCalculator.setCalculationMethod((inputs, output) -> {
            Stream in = (Stream) inputs.get(0);
            Stream out = (Stream) output;

            // Calculate total energy flow (Energy = LCV * FlowRate)
            // LCV() returns J/Sm3, so we multiply by Sm3/hr to get J/hr
            double lcv = in.LCV(); // J/Sm3
            double flowRate = in.getFlowRate("Sm3/hr");
            double totalEnergyFlow = lcv * flowRate; // J/hr

            // Example logic: Assume we burn this gas and heat the outlet stream.
            // Let's say we want to set the outlet temperature based on this energy.
            // (This is a simplified example logic)
            
            double targetTemperature = 300.0 + (totalEnergyFlow / 1.0e7); 
            
            out.setTemperature(targetTemperature, "K");
            
            System.out.println("Calculated Energy Flow: " + totalEnergyFlow + " J/hr");
            System.out.println("Adjusted Outlet Temperature: " + targetTemperature + " K");
        });

        // 5. Run the Calculator
        // In a ProcessSystem, this would happen automatically when the system is run.
        energyCalculator.run();
        
        // Verify the result
        System.out.println("Final Outlet Temperature: " + outletStream.getTemperature("K") + " K");
    }
}
```

### Declarative presets for common calculations

When you want standardized behavior without re-implementing a lambda, use the presets in `CalculatorLibrary`:

```java
Calculator preset = new Calculator("energy balancer");
preset.addInputVariable(inletStream);
preset.setOutputVariable(outletStream);

// Resolve by enum
preset.setCalculationMethod(CalculatorLibrary.preset(CalculatorLibrary.Preset.ENERGY_BALANCE));

// ...or dynamically by name from metadata/AI text
// preset.setCalculationMethod(CalculatorLibrary.byName("energyBalance"));
```

Available presets:

- **ENERGY_BALANCE**: flashes the output stream at its current pressure so its enthalpy equals the sum of input enthalpies.
- **DEW_POINT_TARGETING**: sets the output stream temperature to the hydrocarbon dew point of the first input stream at the output pressure. Use `CalculatorLibrary.dewPointTargeting(double marginKelvin)` to add a temperature margin above dew point.

## API Reference

### `Calculator`

*   `addInputVariable(ProcessEquipmentInterface unit)`: Adds a unit operation to the list of inputs available during calculation.
*   `setOutputVariable(ProcessEquipmentInterface unit)`: Sets the primary unit operation that will be modified by the calculation.
*   `setCalculationMethod(BiConsumer<ArrayList<ProcessEquipmentInterface>, ProcessEquipmentInterface> method)`: Sets the custom logic to be executed when `run()` is called.
    *   The lambda receives two arguments:
        1.  `inputs`: An `ArrayList` of the input equipment added via `addInputVariable`.
        2.  `output`: The output equipment set via `setOutputVariable`.

## Related Functionality

Similar flexibility has been added to `Adjuster` and `SetPoint` classes.

### `Adjuster`

The `Adjuster` class can now use a custom function to calculate the current value of the target variable, instead of relying on hardcoded property strings.

*   `setTargetValueCalculator(Function<ProcessEquipmentInterface, Double> calculator)`: Sets a function that calculates the current value from the target equipment.

### `SetPoint`

The `SetPoint` class can now use a custom function to calculate the value to be set on the target equipment, based on the source equipment.

*   `setSourceValueCalculator(Function<ProcessEquipmentInterface, Double> calculator)`: Sets a function that calculates the value to set from the source equipment.

