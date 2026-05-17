---
title: Calculators and Setters
description: Documentation for calculator and setter equipment in NeqSim process simulation.
---

# Calculators and Setters

Documentation for calculator and setter equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Calculator Class](#calculator-class)
- [Functional Interface Mode](#functional-interface-mode)
- [Calculator Library](#calculator-library)
- [Setter Class](#setter-class)
- [Flow Setter](#flow-setter)
- [Set Point Class](#set-point-class)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process.equipment.util`

**Classes:**
| Class | Description |
|-------|-------------|
| `Calculator` | Custom calculation unit |
| `CalculatorLibrary` | Pre-built calculation functions |
| `Setter` | Variable setter |
| `FlowSetter` | Flow rate setter |
| `MoleFractionControllerUtil` | Composition control |

---

## Calculator Class

Performs custom calculations based on process variables. The Calculator supports two configuration modes:
1. **Standard Mode** - Uses expression strings (limited support)
2. **Functional Interface Mode** - Uses Java lambdas for full flexibility

### Basic Usage

```java
import neqsim.process.equipment.util.Calculator;

// Create calculator
Calculator calc = new Calculator("Duty Calculator");

// Add input variables
calc.addInputVariable(stream1);
calc.addInputVariable(stream2);

// Set output variable
calc.setOutputVariable(heater);

// Add to process
process.add(calc);
```

### Adding Multiple Inputs

```java
// Add streams individually
calc.addInputVariable(stream1);
calc.addInputVariable(stream2);

// Or add multiple at once using varargs
calc.addInputVariable(stream1, stream2, stream3);
```

---

## Functional Interface Mode

The Calculator class supports lambda expressions for defining custom calculation logic. This provides full flexibility to implement any calculation without expression parsing limitations.

### Available Methods

| Method | Signature | Description |
|--------|-----------|-------------|
| `setCalculationMethod` | `BiConsumer<ArrayList<ProcessEquipmentInterface>, ProcessEquipmentInterface>` | Full access to registered inputs and output |
| `setCalculationMethod` | `Runnable` | Simple lambda that captures variables from enclosing scope |

### BiConsumer Pattern (Recommended)

Use this pattern when you want to work with formally registered input/output variables:

```java
Calculator calculator = new Calculator("Energy Calculator");
calculator.addInputVariable(inletStream);
calculator.setOutputVariable(outletStream);

calculator.setCalculationMethod((inputs, output) -> {
    Stream in = (Stream) inputs.get(0);
    Stream out = (Stream) output;
    double energy = in.LCV() * in.getFlowRate("Sm3/hr");
    out.setTemperature(350.0, "K");
});

calculator.run();
```

### Multiple Inputs Example

```java
Calculator calculator = new Calculator("Total Flow Calculator");

// Add multiple inputs using varargs
calculator.addInputVariable(inletStream1, inletStream2);
calculator.setOutputVariable(outletStream);

calculator.setCalculationMethod((inputs, output) -> {
    double totalFlow = 0.0;
    for (ProcessEquipmentInterface input : inputs) {
        totalFlow += ((Stream) input).getFlowRate("kg/hr");
    }
    ((Stream) output).setFlowRate(totalFlow, "kg/hr");
});

calculator.run();
```

### Runnable Pattern (Simple)

Use this pattern when you want to capture equipment directly in the lambda closure:

```java
Calculator calculator = new Calculator("Energy Calculator");
// No need to register inputs/outputs - capture them directly

calculator.setCalculationMethod(() -> {
    double energy = inletStream.LCV() * inletStream.getFlowRate("Sm3/hr");
    outletStream.setTemperature(350.0, "K");
});

calculator.run();
```

### When to Use Each Pattern

| Pattern | Use When |
|---------|----------|
| `BiConsumer` | Building reusable calculations, working with variable number of inputs |
| `Runnable` | Quick calculations, capturing specific equipment from scope |

---

## Calculator Library

Pre-built calculation presets for common thermodynamic operations. These presets provide declarative building blocks that encourage consistent logic across simulations.

### Available Presets

| Preset | Description |
|--------|-------------|
| `ENERGY_BALANCE` | Flashes output stream to match summed input enthalpy |
| `DEW_POINT_TARGETING` | Sets output temperature to hydrocarbon dew point |

### Using Presets

```java
import neqsim.process.equipment.util.CalculatorLibrary;

// Using the preset directly
Calculator calculator = new Calculator("Energy Balance");
calculator.addInputVariable(inlet);
calculator.setOutputVariable(outlet);
calculator.setCalculationMethod(CalculatorLibrary.energyBalance());
calculator.run();
```

### Energy Balance Preset

Performs an enthalpy-based energy balance. The output stream is flashed at its current pressure to match the summed input enthalpies:

```java
SystemSrkEos fluid = new SystemSrkEos(280.0, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.1);
fluid.createDatabase(true);
fluid.setMixingRule(2);

Stream inlet = new Stream("inlet", fluid);
inlet.setTemperature(280.0, "K");
inlet.setPressure(50.0, "bara");
inlet.run();

Stream outlet = new Stream("outlet", fluid.clone());
outlet.setTemperature(320.0, "K");
outlet.setPressure(50.0, "bara");
outlet.run();

Calculator calculator = new Calculator("Energy Balance");
calculator.addInputVariable(inlet);
calculator.setOutputVariable(outlet);
calculator.setCalculationMethod(CalculatorLibrary.energyBalance());
calculator.run();

// Outlet enthalpy now matches inlet enthalpy
```

### Dew Point Targeting Preset

Sets the output stream temperature to the hydrocarbon dew point of the first input stream at the output stream's pressure:

```java
Stream source = new Stream("source", fluid);
source.setPressure(15.0, "bara");
source.run();

Stream target = new Stream("target", fluid.clone());
target.setPressure(12.0, "bara");
target.run();

Calculator calculator = new Calculator("Dew Point Targeter");
calculator.addInputVariable(source);
calculator.setOutputVariable(target);
calculator.setCalculationMethod(CalculatorLibrary.byName("dewPointTargeting"));
calculator.run();

// Target temperature now equals dew point at 12 bara
```

### Dew Point with Margin

Add a safety margin above the dew point:

```java
// Add 5 K margin above dew point
calculator.setCalculationMethod(CalculatorLibrary.dewPointTargeting(5.0));
```

### Resolving Presets by Name

Useful for declarative configuration or AI-generated instructions:

```java
// Case-insensitive, supports various formats
CalculatorLibrary.byName("energyBalance");
CalculatorLibrary.byName("ENERGY_BALANCE");
CalculatorLibrary.byName("energy-balance");
CalculatorLibrary.byName("dewPointTargeting");
```

---

## Setter Class

Sets process variables to specific values.

### Basic Usage

```java
import neqsim.process.equipment.util.Setter;

// Create setter
Setter setter = new Setter("Temperature Setter");

// Set target equipment and property
setter.setEquipment(heater);
setter.setProperty("outTemperature");
setter.setValue(80.0);
setter.setUnit("C");

// Add to process
process.add(setter);
```

### Use Cases

```java
// Set valve position
Setter valveSetter = new Setter("Valve Opener");
valveSetter.setEquipment(valve);
valveSetter.setProperty("percentValveOpening");
valveSetter.setValue(75.0);

// Set compressor speed
Setter speedSetter = new Setter("Speed Setter");
speedSetter.setEquipment(compressor);
speedSetter.setProperty("speed");
speedSetter.setValue(5000.0);
```

---

## Flow Setter

Specifically for setting flow rates.

### Basic Usage

```java
import neqsim.process.equipment.util.FlowSetter;

// Create flow setter
FlowSetter flowSetter = new FlowSetter("Production Rate", stream);
flowSetter.setFlowRate(10000.0, "kg/hr");

// Add to process
process.add(flowSetter);
```

### With Ramping

```java
// Ramp flow rate over time
flowSetter.setRampRate(1000.0, "kg/hr/min");
flowSetter.setTargetFlowRate(20000.0, "kg/hr");
```

---

## Mole Fraction Controller

Control stream composition.

### Basic Usage

```java
import neqsim.process.equipment.util.MoleFractionControllerUtil;

// Control CO2 content
MoleFractionControllerUtil co2Control = 
    new MoleFractionControllerUtil("CO2 Spec", stream);
co2Control.setTargetMoleFraction("CO2", 0.02);  // 2 mol%

// Add to process
process.add(co2Control);
```

---

## Usage Examples

### Production Optimizer

```java
// Calculator to maximize production
Calculator optimizer = new Calculator("Production Optimizer");
optimizer.addInputVariable(separator, "pressure");
optimizer.addInputVariable(feedStream, "flowRate");
optimizer.setOutputVariable(exportValve, "percentValveOpening");
optimizer.setExpression("calculateOptimalOpening(pressure, flowRate)");
process.add(optimizer);
```

### Cascade Control

```java
// Primary controller output sets secondary setpoint
Calculator cascade = new Calculator("Cascade");
cascade.addInputVariable(temperatureController, "output");
cascade.setOutputVariable(flowController, "setpoint");
cascade.setExpression("output * flowGain + flowBias");
process.add(cascade);
```

### Ratio Control

```java
// Maintain fuel/air ratio
Calculator ratioCalc = new Calculator("F/A Ratio");
ratioCalc.addInputVariable(fuelStream, "flowRate");
ratioCalc.setOutputVariable(airDamper, "position");
ratioCalc.setExpression("fuelFlow * stoichRatio * excessAir");
process.add(ratioCalc);
```

### Duty Calculation

```java
// Calculate required cooling duty
Calculator dutyCalc = new Calculator("Cooling Duty");
dutyCalc.addInputVariable(inletStream, "temperature");
dutyCalc.addInputVariable(inletStream, "flowRate");
dutyCalc.addInputVariable(inletStream, "heatCapacity");
dutyCalc.setOutputVariable(cooler, "duty");
dutyCalc.setExpression("-flowRate * heatCapacity * (targetTemp - inletTemp)");
process.add(dutyCalc);
```

---

## Set Point Class

Sets the value of a variable in a target equipment based on a source equipment. Used for feed-forward control or copying values between equipment.

### Basic Usage

```java
import neqsim.process.equipment.util.SetPoint;

// Create set point to copy pressure
SetPoint setPoint = new SetPoint("Pressure Copy");
setPoint.setSourceVariable(sourceStream, "pressure");
setPoint.setTargetVariable(targetStream, "pressure");

// Add to process
process.add(setPoint);
```

### Supported Target Variables

| Equipment Type | Supported Variables |
|----------------|---------------------|
| `Stream` | `pressure`, `temperature` |
| `ThrottlingValve` | `pressure` (outlet) |
| `Compressor` | `pressure` (outlet) |
| `Pump` | `pressure` (outlet) |
| `Heater`/`Cooler` | `pressure`, `temperature` |

### Functional Interface Mode

Use `setSourceValueCalculator` to define a custom function that calculates the value to set on the target equipment:

```java
SetPoint setPoint = new SetPoint("Custom SetPoint");
setPoint.setSourceVariable(sourceStream);
setPoint.setTargetVariable(targetStream, "pressure");

// Set target pressure based on source temperature: P = T / 10.0
setPoint.setSourceValueCalculator((equipment) -> {
    Stream s = (Stream) equipment;
    return s.getTemperature("K") / 10.0;
});

setPoint.run();
// Target pressure is now 30.0 bara (if source temp = 300 K)
```

### Method Signature

| Method | Type | Description |
|--------|------|-------------|
| `setSourceValueCalculator` | `Function<ProcessEquipmentInterface, Double>` | Custom function to compute the value to set |

### When to Use Functional Mode

| Use Case | Example |
|----------|---------|
| Non-linear relationships | Pressure = f(temperature, flow) |
| Unit conversions | Convert from source units to target units |
| Computed ratios | Set valve to percentage of max flow |
| Conditional logic | Different values based on operating mode |

---

## Stream Transition

Smooth transition between operating states.

```java
import neqsim.process.equipment.util.StreamTransition;

// Create transition
StreamTransition transition = new StreamTransition("Startup Ramp");
transition.setStream(feedStream);
transition.setInitialFlowRate(0.0, "kg/hr");
transition.setFinalFlowRate(10000.0, "kg/hr");
transition.setTransitionTime(3600.0);  // 1 hour ramp

// Run transition
for (double t = 0; t < 3600; t += 60) {
    transition.setTime(t);
    process.run();
}
```

---

## Related Documentation

- [Adjusters](adjusters) - Iterative adjustment
- [Recycles](recycles) - Recycle handling
- [Process Controllers](../../controllers) - Control systems
