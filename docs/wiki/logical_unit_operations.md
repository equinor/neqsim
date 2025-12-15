# Logical Unit Operations

NeqSim provides several "logical" unit operations that do not represent physical equipment but are used to control the simulation, transfer data, or perform calculations. These include `Calculator`, `Adjuster`, `SetPoint`, and `Recycle`.

## Calculator

The `Calculator` unit operation allows for custom calculations and data manipulation within a process simulation. It is useful for calculating derived properties or implementing simple control logic. Custom lambdas are the preferred hook for AI-generated logic because they let you keep the same simulator graph while swapping in new behavior at runtime.

### Usage

1.  **Create**: `Calculator calc = new Calculator("name");`
2.  **Add Inputs**: `calc.addInputVariable(inputUnit);`
3.  **Set Output**: `calc.setOutputVariable(outputUnit);`
4.  **Define Logic**: Use `setCalculationMethod` with a lambda expression.

### Example

```java
Calculator energyCalc = new Calculator("Energy Calc");
energyCalc.addInputVariable(inletStream);
energyCalc.setOutputVariable(outletStream);

energyCalc.setCalculationMethod((inputs, output) -> {
    Stream in = (Stream) inputs.get(0);
    Stream out = (Stream) output;
    double energy = in.LCV() * in.getFlowRate("Sm3/hr");
    // Adjust outlet temperature based on energy
    out.setTemperature(300.0 + energy / 1e5, "K");
});
```

### Declarative presets (energy balance, dew point targeting)

For frequently reused logic you can rely on `CalculatorLibrary` presets instead of hand-written lambdas. This makes it easier to reference calculations declaratively (e.g., from an AI agent or configuration file):

```java
Calculator presetCalc = new Calculator("dew point targeter");
presetCalc.addInputVariable(feedStream);
presetCalc.setOutputVariable(targetStream);

// Apply by enum or by name
presetCalc.setCalculationMethod(CalculatorLibrary.preset(CalculatorLibrary.Preset.DEW_POINT_TARGETING));
// presetCalc.setCalculationMethod(CalculatorLibrary.byName("dewPointTargeting"));
```

Available presets:

- **ENERGY_BALANCE** – matches the output stream enthalpy to the sum of input enthalpies by flashing at the output pressure.
- **DEW_POINT_TARGETING** – sets the output stream temperature to the source stream dew point at the output pressure (optionally with a temperature margin via `CalculatorLibrary.dewPointTargeting(double marginKelvin)`).

## Adjuster

The `Adjuster` is used to vary a parameter in one unit operation (the "adjusted variable") to achieve a specific value in another unit operation (the "target variable"). It is essentially a single-variable solver. Use lambdas for the getters/setters to keep the hook flexible for AI-generated control logic.

### Standard Usage

You can specify standard properties like "pressure", "temperature", "flow", etc.

```java
Adjuster adjuster = new Adjuster("Pressure Adjuster");
adjuster.setAdjustedVariable(inletStream, "flow", "kg/hr");
adjuster.setTargetVariable(outletStream, "pressure", 50.0, "bara");
```

### Custom Target Calculation

You can also define a custom function to calculate the target value from the target equipment. This is useful if the variable you want to control is not a standard property.

```java
adjuster.setTargetValueCalculator((equipment) -> {
    Stream s = (Stream) equipment;
    // Control based on a custom metric, e.g., Flow * Temperature
    return s.getFlowRate("kg/hr") * s.getTemperature("K");
});
```

### Custom Adjusted Variable

You can also define custom logic for how to read and write the adjusted variable. This allows you to manipulate parameters that are not standard properties.

```java
// Define how to read the current value of the adjusted variable
adjuster.setAdjustedValueGetter((equipment) -> {
    return ((Stream) equipment).getTemperature("K");
});

// Define how to set the new value of the adjusted variable
adjuster.setAdjustedValueSetter((equipment, val) -> {
    ((Stream) equipment).setTemperature(val, "K");
});
```

## SetPoint

The `SetPoint` unit operation sets the value of a variable in a target unit operation equal to the value of a variable in a source unit operation. It is used for feed-forward control or simply copying values.

### Standard Usage

```java
SetPoint setPoint = new SetPoint("Pressure Copy");
setPoint.setSourceVariable(sourceStream, "pressure");
setPoint.setTargetVariable(targetStream, "pressure");
```

### Custom Source Calculation

You can define a custom function to calculate the value to be set on the target equipment, based on the source equipment.

```java
setPoint.setSourceValueCalculator((equipment) -> {
    Stream s = (Stream) equipment;
    // Set target pressure to be 10% of source pressure
    return s.getPressure("bara") * 0.1;
});
```

## Recycle

The `Recycle` unit operation is used to close loops in a process simulation. It compares the inlet and outlet streams of the recycle block and iterates until they converge within a specified tolerance.

```java
Recycle recycle = new Recycle("Recycle");
recycle.addStream(recycleStream);
recycle.setTolerance(1e-6);
```
