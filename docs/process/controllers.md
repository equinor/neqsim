---
title: Process Controllers and Logic
description: Runnable examples of adjusters, recycles, setters, calculators, PID controllers, and dynamic control blocks in NeqSim.
---

# Process Controllers and Logic

Use steady-state utilities to satisfy process specifications and dynamic controllers to update
actuators from measured signals. These have different execution and connection requirements.

## Table of Contents

- [Overview](#overview)
- [Named Controller Map](#named-controller-map)
- [Adjusters](#adjusters)
- [Recycles](#recycles)
- [Setters](#setters)
- [Calculators](#calculators)
- [PID Controllers](#pid-controllers)
- [Native Dynamic Control Blocks](#native-dynamic-control-blocks)
- [Process Logic](#process-logic)

## Overview

| Class | Package | Purpose |
| --- | --- | --- |
| `Adjuster` | `neqsim.process.equipment.util` | Iterate a manipulated variable toward a target |
| `Recycle` | `neqsim.process.equipment.util` | Update a tear stream until a recycle converges |
| `Setter` | `neqsim.process.equipment.util` | Apply supported constant pressure or temperature specifications |
| `Calculator` | `neqsim.process.equipment.util` | Execute a Java calculation callback |
| `ControllerDeviceBaseClass` | `neqsim.process.controllerdevice` | PID control using a measurement device |
| `TransferFunctionBlock`, `LogicBlock` | `neqsim.process.controllerdevice` | Signal dynamics and Boolean decisions |

Each Java block below is a complete Java 8 program. Save it using its public class name and run
with NeqSim and its dependencies on the classpath. Enable assertions with `java -ea` to check the
stated results. `ControllersAndWellsDocumentationTest` compiles and executes these exact blocks.

## Named Controller Map

Equipment supports tagged controller registration through `addController(tag, controller)`,
`getController(tag)`, and `getControllers()`. `setController(controller)` selects the legacy
primary controller and also registers it under its name. The first `addController` call also
selects a primary controller when none exists.

The map records associations; it does not implement arbitration between two outputs that both
request the same valve position. `ThrottlingValve` executes its primary controller. Use an
explicit control structure when several measurements must determine one actuator command.

Registering a controller with `ProcessSystem.add(controller)` makes it available to the
system-level transient scan. Equipment-owned controllers that already executed for the step's
UUID are not integrated a second time. System-level registration alone does not connect the
controller response to an actuator. The [PID example](#pid-controllers) demonstrates both the
actuator connection and registration.

## Adjusters

An adjuster is a steady-state specification solver. Register it after the equipment whose
result it measures. For a direct temperature specification, a heater's `setOutTemperature`
is sufficient. To solve for the required duty, use explicit getter, setter, and measurement
callbacks:

```java
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class HeaterDutyAdjusterExample {
  public static void main(String[] args) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Heater heater = new Heater("heater", feed);
    heater.setDuty(10000.0); // W

    Adjuster adjuster = new Adjuster("outlet temperature specification");
    adjuster.setAdjustedEquipment(heater);
    adjuster.setTargetEquipment(heater);
    adjuster.setAdjustedValueGetter(() -> heater.getDuty());
    adjuster.setAdjustedValueSetter(value -> heater.setDuty(value));
    adjuster.setTargetValueCalculator(() -> heater.getOutletStream().getTemperature("C"));
    adjuster.setTargetValue(80.0); // C, matching the measurement callback
    adjuster.setMinAdjustedValue(0.0); // W, matching the manipulated variable
    adjuster.setMaxAdjustedValue(100000.0);
    adjuster.setTolerance(1.0e-5); // C

    ProcessSystem process = new ProcessSystem("heater specification");
    process.add(feed);
    process.add(heater);
    process.add(adjuster);
    process.run();

    // Check the physical target too: an adjuster can stop at a bound.
    assert adjuster.solved();
    assert Math.abs(heater.getOutletStream().getTemperature("C") - 80.0) < 1.0e-4;
    assert heater.getDuty() > 0.0 && heater.getDuty() < 100000.0;
    assert Math.abs(heater.getOutletStream().getFlowRate("kg/hr") - 1000.0) < 1.0e-6;
  }
}
```

The string-based API supports selected stream properties; it is not general property reflection.
For example, adjusted `"flow"` needs an explicit flow unit. A target `"temperature"` does not
select a built-in temperature measurement in the current `Adjuster`, so the callback above is
essential. Use callbacks for heater duty, component fractions, and other custom quantities.
See [Adjusters](equipment/util/adjusters.md) for the supported property contract.

## Recycles

Connect the recycle outlet to a **stream** that already feeds the upstream mixer. A mixer itself
is not a valid argument to `Recycle.setOutletStream`. This example recycles 25% of the mixed
flow and exports 75%. At convergence, export equals the fresh feed and recycle is one third of
that fresh feed.

```java
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.AccelerationMethod;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class MaterialRecycleExample {
  public static void main(String[] args) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("fresh feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Stream tear = new Stream("recycle guess", fluid.clone());
    tear.setFlowRate(100.0, "kg/hr");

    Mixer mixer = new Mixer("mixer");
    mixer.addStream(feed);
    mixer.addStream(tear);
    Splitter splitter = new Splitter("product and recycle", mixer.getOutletStream());
    splitter.setSplitFactors(new double[] {0.75, 0.25});
    Recycle recycle = new Recycle("recycle");
    recycle.addStream(splitter.getSplitStream(1));
    recycle.setOutletStream(tear);
    recycle.setTolerance(1.0e-8);
    recycle.setMaxIterations(100);
    recycle.setAccelerationMethod(AccelerationMethod.DIRECT_SUBSTITUTION);

    ProcessSystem process = new ProcessSystem("material recycle");
    process.add(feed);
    process.add(tear);
    process.add(mixer);
    process.add(splitter);
    process.add(recycle);
    process.run();

    assert recycle.solved();
    assert Math.abs(splitter.getSplitStream(0).getFlowRate("kg/hr") - 1000.0) < 1.0e-3;
    assert Math.abs(tear.getFlowRate("kg/hr") - 1000.0 / 3.0) < 1.0e-3;
    assert Math.abs(tear.getPressure("bara") - 20.0) < 1.0e-6;
  }
}
```

`DIRECT_SUBSTITUTION` is the default acceleration method. `WEGSTEIN` and `BROYDEN` are opt-in
alternatives; their benefit depends on the coupled variables and flowsheet. Current Wegstein
acceleration operates on composition, so it does not accelerate the pure-methane flow balance
above. Its default q bounds are -5 to 0, with a two-iteration warm-up. Both `ProcessSystem`
and `ProcessModel` also provide `setRecycleAccelerationMethod` to update all their recycle units.
See [Recycle Acceleration](../simulation/recycle_acceleration_guide.md) for tuning and diagnostics.

## Setters

`Setter` applies supported constant specifications with `addTargetEquipment` and `addParameter`.
It does not flash the target after changing its inputs: run the target afterward. Assign flow
directly with `Stream.setFlowRate`. Define feed composition on the thermodynamic system, in
component insertion order when using `setMolarComposition`.

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Setter;
import neqsim.thermo.system.SystemSrkEos;

public class ConstantSpecificationExample {
  public static void main(String[] args) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("CO2", 0.10);
    fluid.setMixingRule("classic");
    fluid.setMolarComposition(new double[] {0.98, 0.02});
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");

    Setter setter = new Setter("feed specifications");
    setter.addTargetEquipment(feed);
    setter.addParameter("temperature", "C", 40.0);
    setter.addParameter("pressure", "bara", 30.0);
    setter.run();
    feed.run();

    assert Math.abs(feed.getTemperature("C") - 40.0) < 1.0e-8;
    assert Math.abs(feed.getPressure("bara") - 30.0) < 1.0e-8;
    assert Math.abs(feed.getFlowRate("kg/hr") - 1000.0) < 1.0e-6;
    assert Math.abs(feed.getFluid().getComponent("CO2").getz() - 0.02) < 1.0e-10;
  }
}
```

Composition specification changes the input definition. To model physical injection or removal,
include the corresponding material streams and equipment in the flowsheet. The available
`MoleFractionControllerUtil` modifies component inventory and is described in
[Calculators and Setters](equipment/util/calculators.md); there is no `MoleFractionSetter` class.

## Calculators

Use `Calculator.setCalculationMethod` with a Java callback. Register whole equipment objects
as inputs and output; `setExpression` and property-name overloads are not part of this API.
Here a separate auxiliary-feed specification is set to 10% of a main-feed mass rate.

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class FlowSpecificationCalculatorExample {
  public static void main(String[] args) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream mainFeed = new Stream("main feed", fluid);
    mainFeed.setFlowRate(1000.0, "kg/hr");
    Stream auxiliaryFeed = new Stream("auxiliary feed", fluid.clone());
    auxiliaryFeed.setFlowRate(1.0, "kg/hr");

    Calculator calculator = new Calculator("auxiliary feed specification");
    calculator.addInputVariable(mainFeed);
    calculator.setOutputVariable(auxiliaryFeed);
    calculator.setCalculationMethod((inputs, output) -> {
      Stream source = (Stream) inputs.get(0);
      Stream target = (Stream) output;
      target.setFlowRate(0.10 * source.getFlowRate("kg/hr"), "kg/hr");
      target.run();
    });

    ProcessSystem process = new ProcessSystem("calculated feed specification");
    process.add(mainFeed);
    process.add(calculator);
    process.run();

    assert Math.abs(auxiliaryFeed.getFlowRate("kg/hr") - 100.0) < 1.0e-6;
    assert Math.abs(mainFeed.getFlowRate("kg/hr") - 1000.0) < 1.0e-6;
    assert Math.abs(auxiliaryFeed.getTemperature("C") - 25.0) < 1.0e-6;
  }
}
```

This callback establishes the rate of an independent source; splitting one feed into products
requires a `Splitter`. Registered calculator inputs and outputs also describe graph dependencies.
Validate callback results explicitly, because callback exceptions are logged by `Calculator`.

## PID Controllers

Use `ControllerDeviceBaseClass` with a transmitter. `setControllerParameters(Kp, Ti, Td)` takes
gain, integral time in seconds, and derivative time in seconds. `Ti` is not an integral gain.
With an explicit engineering unit, the controller uses measurement minus set point as its error;
`setReverseActing(true)` reverses the output response. Select the action from the actual process
and actuator response.

The example initializes a valve at 50% opening, then performs one dynamic step with pressure
above set point. The controller opens the valve to 51%. The imposed feed pressure stays fixed:
this verifies wiring and action, while pressure regulation requires upstream inventory dynamics.

```java
import java.util.UUID;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class PressureControllerWiringExample {
  public static void main(String[] args) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 25.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    ThrottlingValve valve = new ThrottlingValve("pressure valve", feed);
    valve.setOutletPressure(10.0, "bara");
    valve.setPercentValveOpening(50.0);

    PressureTransmitter transmitter = new PressureTransmitter("PT-100", feed);
    transmitter.setUnit("bara");
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("PC-100");
    controller.setTransmitter(transmitter);
    controller.setControllerSetPoint(20.0, "bara");
    controller.setControllerParameters(2.0, 10.0, 0.0);
    controller.setReverseActing(false);
    controller.setOutputLimits(0.0, 100.0);
    valve.setController(controller);

    ProcessSystem process = new ProcessSystem("pressure controller wiring");
    process.add(feed);
    process.add(valve);
    process.add(controller);
    process.run(); // Initialize outlet state and valve sizing before the dynamic step.
    valve.setCalculateSteadyState(false);
    process.runTransient(1.0, UUID.randomUUID());

    assert valve.getController("PC-100") == controller;
    assert valve.getControllers().contains(controller);
    assert Math.abs(controller.getMeasuredValue("bara") - 25.0) < 1.0e-8;
    assert Math.abs(controller.getResponse() - 51.0) < 1.0e-8;
    assert Math.abs(valve.getPercentValveOpening() - 51.0) < 1.0e-8;
    assert valve.getOutletStream().getFlowRate("kg/hr") > 0.0;
  }
}
```

For time series, call `process.runTransient(dt, UUID.randomUUID())` once per physical step,
with `dt` in seconds. Read the measured value, `getControllerSetPoint`, and `getResponse` for
results. Reuse a UUID only for repeated evaluations of the same physical step.

## Native Dynamic Control Blocks

`TransferFunctionBlock` supplies first-order lag, lead-lag, dead-time, and second-order signal
dynamics. `LogicBlock` evaluates threshold, fixed, or chained Boolean inputs. Both read a
transmitter's configured measurement unit, so set that unit explicitly.

```java
import java.util.UUID;
import neqsim.process.controllerdevice.LogicBlock;
import neqsim.process.controllerdevice.TransferFunctionBlock;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class DynamicControlBlocksExample {
  public static void main(String[] args) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("measured stream", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    PressureTransmitter transmitter = new PressureTransmitter("PT-100", feed);
    transmitter.setUnit("bara");

    TransferFunctionBlock lag = new TransferFunctionBlock(
        "pressure filter", TransferFunctionBlock.Type.FIRST_ORDER_LAG);
    lag.setTransmitter(transmitter);
    lag.setLagTime(5.0); // s
    LogicBlock highPressure = new LogicBlock("high pressure", LogicBlock.Operator.AND);
    highPressure.addInput(transmitter, 30.0, LogicBlock.Comparator.GREATER_EQUAL);

    ProcessSystem process = new ProcessSystem("signal dynamics");
    process.add(feed);
    process.add(lag);
    process.add(highPressure);
    process.run();
    process.runTransient(1.0, UUID.randomUUID());
    assert Math.abs(lag.getOutput() - 20.0) < 1.0e-8;
    assert !highPressure.getOutputBoolean();

    feed.setPressure(32.0, "bara");
    process.runTransient(1.0, UUID.randomUUID());
    // Backward-Euler first-order lag: 20 + (32 - 20) * 1 / (5 + 1) = 22 bara.
    assert Math.abs(lag.getOutput() - 22.0) < 1.0e-8;
    assert highPressure.getOutputBoolean();
  }
}
```

Both concrete block classes participate in `ProcessSystem` and multi-area `ProcessModel`
transient-step transactions. A rejected step restores dynamic state, delay buffers, output,
calculation identity, configuration, and original transmitter/input bindings. Replaying that
physical-step identifier after rollback reproduces the control-block continuation. Repeated
evaluation with an already accepted identifier is ignored. `TransferFunctionBlock.reset()` also
clears the remembered identifier so a run can restart from its initial state.

Subclasses must supply snapshots for their own mutable state to obtain transaction coverage.
The transaction mechanism does not validate tuning or safety integrity, or defer external
side effects from callbacks.

## Process Logic

A `LogicBlock` produces a Boolean signal; it does not automatically shut a valve. Connect the
signal to an explicit action or sequence. The implemented `neqsim.process.logic` subpackages
contain `StartupLogic`, `ShutdownLogic`, conditions, and actions for these workflows. See
[Advanced Process Logic](../simulation/advanced_process_logic.md) for sequence integration.

### Alarm Integration

Configure limits on a measurement device using `AlarmConfig`, then register that measurement
with `ProcessAlarmManager`. Alarm values and thresholds must use the same engineering unit.
There is no equipment/property overload of `addAlarm` as previously shown on this page.
See the [Alarm System Guide](../safety/alarm_system_guide.md) for configuration, evaluation,
acknowledgment, and history.

## Related Documentation

- [ProcessSystem](processmodel/process_system.md) - Named controllers and process execution
- [Dynamic Simulation Guide](../simulation/dynamic_simulation_guide.md) - Equipment dynamics and controller scans
- [Dynamic Simulation Helper](dynamic-simulation.md) - Instrumentation for dynamic simulation
- [Adjusters](equipment/util/adjusters.md) - Supported property names and callbacks
- [Recycles](equipment/util/recycles.md) - Tear streams and convergence
- [Calculators and Setters](equipment/util/calculators.md) - Calculation callbacks and specifications
