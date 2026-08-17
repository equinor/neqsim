---
title: Calculators and Setters
description: Configure source-verified calculation callbacks, constant specifications, set-point relations, and composition modifiers in NeqSim process simulations.
---

NeqSim process utilities can calculate custom values, apply constant specifications, copy values
between equipment, or modify a stream composition. They are executable unit operations, not a
string-expression language.

## Choose the appropriate utility

| Utility | Use |
| --- | --- |
| `Calculator` | Run a Java callback against registered input and output equipment |
| `CalculatorLibrary` | Reuse the energy-balance, dew-point-targeting, or anti-surge callback |
| `Setter` | Apply one or more constant pressure or temperature specifications |
| `SetPoint` | Copy or transform a supported value from source equipment to target equipment |
| `MoleFractionControllerUtil` | Add or remove one component to reach a requested outlet composition |
| `FlowSetter` | Match gas, oil, and water reference-condition rates through its internal separation workflow |

`FlowSetter` is not a generic one-line flow assignment. Set a normal stream flow directly with
`Stream.setFlowRate(value, unit)`; use `FlowSetter` only when its phase-rate reconciliation
workflow is the engineering intent.

## Calculator callback modes

`Calculator` registers whole `ProcessEquipmentInterface` objects:

```java
calculator.addInputVariable(inletStream);
calculator.addInputVariable(secondStream);
calculator.setOutputVariable(outletStream);
```

There are two callback overloads:

- `setCalculationMethod(BiConsumer<ArrayList<ProcessEquipmentInterface>, ProcessEquipmentInterface>)`
  exposes the registered inputs and output. Prefer it in a `ProcessSystem` because those
  registrations also describe graph dependencies.
- `setCalculationMethod(Runnable)` captures objects from the enclosing scope. It is concise, but
  the graph cannot infer dependencies from captured variables.

`Calculator` has no `setExpression(String)` method and no property-name overload of
`addInputVariable` or `setOutputVariable`. Convert a formula to explicit Java in one of the
callback overloads.

### Complete Java 8 example

This complete program increases a material stream's flow by 10%. The callback writes and runs its
output so downstream equipment receives an initialized state.

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class CalculatorExample {
  public static void main(String[] args) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");

    Stream adjusted = new Stream("adjusted", fluid.clone());
    adjusted.setFlowRate(0.0, "kg/hr");

    Calculator calculator = new Calculator("flow calculator");
    calculator.addInputVariable(feed);
    calculator.setOutputVariable(adjusted);
    calculator.setCalculationMethod((inputs, output) -> {
      Stream source = (Stream) inputs.get(0);
      Stream target = (Stream) output;
      target.setFlowRate(1.10 * source.getFlowRate("kg/hr"), "kg/hr");
      target.run();
    });

    ProcessSystem process = new ProcessSystem("calculator example");
    process.add(feed);
    process.add(calculator);
    process.run();

    System.out.printf("%.1f kg/hr%n", adjusted.getFlowRate("kg/hr"));
  }
}
```

Expected output:

```text
1100.0 kg/hr
```

### Process graph and recycle behavior

Registered calculator inputs create dependencies into the calculator. A registered output stream
is treated as calculator-produced even when that stream is not separately added as a unit. This
lets optimized execution place downstream consumers after the callback.

When those registered relationships close a material recycle cycle, optimized execution includes
the calculator in the recycle strongly connected component and re-evaluates it as the recycle
state changes. Do not use the `Runnable` form for this case: captured variables do not declare
the graph edges needed to identify the coupling.

The source implementation catches callback exceptions and logs them. A failed callback therefore
does not provide a fail-fast process contract. Validate its output explicitly—for example, require
finite values, expected units, and a material or energy balance—before using the result for an
engineering decision.

## CalculatorLibrary presets

`CalculatorLibrary` exposes three current presets:

| Preset | Inputs and output | Behavior |
| --- | --- | --- |
| `ENERGY_BALANCE` | One or more `Stream` inputs and one `Stream` output | PH-flashes the output at its current pressure to the sum of the input enthalpies |
| `DEW_POINT_TARGETING` | First input `Stream` and output `Stream` | Sets the output temperature to the source hydrocarbon dew point at the output pressure, plus an optional kelvin margin |
| `ANTI_SURGE` | First input `Compressor` and output `Splitter` | Updates split stream 1 in actual `m3/hr` relative to the compressor surge curve |

Use a preset as a normal callback:

```java
Calculator energyBalance = new Calculator("energy balance");
energyBalance.addInputVariable(inlet);
energyBalance.setOutputVariable(outlet);
energyBalance.setCalculationMethod(CalculatorLibrary.energyBalance());
```

Names are case-insensitive and accept camel case, underscores, or hyphens:

```java
calculator.setCalculationMethod(
    CalculatorLibrary.byName("dew-point-targeting"));
```

An unknown name throws `IllegalArgumentException` during configuration. The energy-balance and
dew-point callbacks require the concrete `Stream` class, not an arbitrary equipment type. For
anti-surge design and control boundaries, use the dedicated
[compressor anti-surge guide](../compressor_antisurge_control.md).

## Setter

`Setter` applies each configured parameter to every target equipment. Configure it with
`addTargetEquipment` and `addParameter`:

```java
Setter setter = new Setter("feed conditions");
setter.addTargetEquipment(feed);
setter.addParameter("pressure", "bara", 35.0);
setter.addParameter("temperature", "C", 25.0);
process.add(setter);
```

Current supported combinations are:

| Target | Supported parameter type |
| --- | --- |
| `Stream` | `pressure`, `temperature` |
| `Compressor`, `Pump`, `ThrottlingValve` | `pressure` |
| `Heater` | `temperature` |
| `Cooler` | `pressure`, `temperature` |

Unsupported combinations are logged and skipped. `Setter` does not expose `setEquipment`,
`setProperty`, `setValue`, or `setUnit`.

## SetPoint

`SetPoint` reads a supported source value and writes it to target equipment:

```java
SetPoint pressureCopy = new SetPoint("pressure copy");
pressureCopy.setSourceVariable(sourceStream, "pressure");
pressureCopy.setTargetVariable(targetStream, "pressure");
pressureCopy.setMultiplier(1.0);
pressureCopy.setOffset(0.0);
process.add(pressureCopy);
```

The relation is

$$
y_{\mathrm{target}} = a y_{\mathrm{source}} + b
$$

where `a` is the multiplier and `b` is the offset in the target variable's own unit. The
built-in source path uses NeqSim's default numeric unit for the selected property; use the same
physical quantity on both sides. A custom `setSourceValueCalculator` can supply the source value
when an explicit conversion or derived quantity is required.

Current target support is:

| Target | Variable |
| --- | --- |
| `Stream` | `pressure`, `temperature`, `massFlow`, `molarFlow`, or `flow` |
| `ThrottlingValve`, `Compressor`, `Pump` | `pressure` |
| `Heater`, `Cooler` | `pressure`, `temperature`, or `outTemperature` |

Unsupported stream variables are logged without changing the target. Unsupported non-stream
variables throw at run time.

## MoleFractionControllerUtil

Despite its historical name, `MoleFractionControllerUtil` is a two-port composition modifier,
not a closed-loop PID controller. Its constructor accepts only the inlet stream:

```java
MoleFractionControllerUtil compositionTarget =
    new MoleFractionControllerUtil(feed);
compositionTarget.setMoleFraction("CO2", 0.02);
process.add(compositionTarget);
```

During `run`, it clones the inlet fluid, changes the named component inventory, and performs a TP
flash. Check `getMolesChange()`, outlet composition, and material-accounting intent before using
the result. It has no name-bearing constructor and no `setTargetMoleFraction` method.

## Validation checklist

- Register `Calculator` inputs and output when execution order or recycle iteration matters.
- Initialize or run callback outputs before downstream equipment reads them.
- Assert finite results and balances because callback exceptions are logged rather than
  propagated.
- Use only supported `Setter` and `SetPoint` target-variable combinations.
- Treat composition modification as a material source or sink and account for the component
  change.
- Keep units explicit at every stream or equipment setter.

## Source contracts

- [Calculator.java](../../../../src/main/java/neqsim/process/equipment/util/Calculator.java)
- [CalculatorLibrary.java](../../../../src/main/java/neqsim/process/equipment/util/CalculatorLibrary.java)
- [Setter.java](../../../../src/main/java/neqsim/process/equipment/util/Setter.java)
- [SetPoint.java](../../../../src/main/java/neqsim/process/equipment/util/SetPoint.java)
- [MoleFractionControllerUtil.java](../../../../src/main/java/neqsim/process/equipment/util/MoleFractionControllerUtil.java)
- [ProcessGraphBuilder.java](../../../../src/main/java/neqsim/process/processmodel/graph/ProcessGraphBuilder.java)
- [Calculator/recycle regression](../../../../src/test/java/neqsim/process/processmodel/CalculatorRecycleHybridExecutionTest.java)

## Related documentation

- [Equipment index](../README.md)
- [Recycle utilities](recycles.md)
- [Controllers](../../controllers.md)
- [Process package](../../README.md)
