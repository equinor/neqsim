# Calculators and Setters

Documentation for calculator and setter equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Calculator Class](#calculator-class)
- [Setter Class](#setter-class)
- [Flow Setter](#flow-setter)
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

Performs custom calculations based on process variables.

### Basic Usage

```java
import neqsim.process.equipment.util.Calculator;

// Create calculator
Calculator calc = new Calculator("Duty Calculator");

// Add input variables
calc.addInputVariable(stream1);
calc.addInputVariable(stream2);

// Set output variable
calc.setOutputVariable(heater, "duty");

// Set calculation expression
calc.setExpression("(flow1 * cp1 + flow2 * cp2) * deltaT");

// Add to process
process.add(calc);
```

### Input Variables

```java
// Add streams as inputs
calc.addInputVariable(stream, "stream1");

// Add equipment properties
calc.addInputVariable(separator, "pressure", "sepP");
calc.addInputVariable(compressor, "power", "compPower");
```

### Expressions

```java
// Mathematical expressions
calc.setExpression("flow * 1.1");  // 10% margin
calc.setExpression("max(value1, value2)");
calc.setExpression("sqrt(a^2 + b^2)");

// Conditional expressions
calc.setExpression("if(temp > 100, duty1, duty2)");
```

---

## Calculator Library

Pre-built calculations for common tasks:

```java
import neqsim.process.equipment.util.CalculatorLibrary;

// Heat duty calculation
double duty = CalculatorLibrary.calculateHeatDuty(stream, deltaT);

// Compressor power
double power = CalculatorLibrary.calculateCompressorPower(
    flow, pressureRatio, efficiency);

// Pressure drop
double dp = CalculatorLibrary.calculatePressureDrop(
    flow, diameter, length, roughness);
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

Stores and manages process set points.

```java
import neqsim.process.equipment.util.SetPoint;

// Create set point
SetPoint tempSP = new SetPoint("Temperature SP");
tempSP.setValue(80.0);
tempSP.setUnit("C");
tempSP.setHighLimit(120.0);
tempSP.setLowLimit(40.0);

// Use in adjuster
adjuster.setTargetValue(tempSP);
```

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

- [Adjusters](adjusters.md) - Iterative adjustment
- [Recycles](recycles.md) - Recycle handling
- [Process Controllers](../../controllers.md) - Control systems
