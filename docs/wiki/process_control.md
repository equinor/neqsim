---
title: "Process control framework"
description: "NeqSim contains a flexible process control framework for dynamic simulations."
---

# Process control framework

NeqSim contains a flexible process control framework for dynamic simulations.
The framework provides:

- **PID controllers** through `ControllerDeviceBaseClass` implementing proportional,
  integral and derivative actions with anti-windup, derivative filtering and
  configurable output limits.
- **Auto‑tuning and gain scheduling** for adapting controller parameters to
  different operating conditions.
- **Event logging and performance metrics** such as integral absolute error and
  settling time for evaluating controller behaviour.
- **Advanced control structures** – cascade, ratio and feed‑forward – built on the
  common `ControlStructureInterface` for multi‑loop coordination.
- **Measurement devices** with explicit unit handling plus optional Gaussian noise
  and sample delay to emulate realistic transmitters.

See the unit tests in `src/test/java/neqsim/process/controllerdevice` for examples
of how the controllers and control structures are used in simulations.

## Model predictive control

The [`ModelPredictiveController`](../../src/main/java/neqsim/process/controllerdevice/ModelPredictiveController.java)
class adds multivariable model predictive control (MPC) to the framework. The
controller uses a first-order process model with configurable gain, time
constant, bias and prediction horizon to calculate an optimal control move that
balances tracking accuracy, absolute energy usage and aggressive movement. MPC
integrates with the rest of the process-control package through the common
`ControllerDeviceInterface`, allowing it to replace or work alongside
traditional PID loops.

### Single-loop quick start

1. **Provide a measurement** – connect a
   `MeasurementDeviceInterface` (for example a temperature or pressure
   transmitter) via `setTransmitter`. The MPC will read samples from the device
   whenever `runTransient` is invoked.
2. **Instantiate and parameterise** – create the controller, call
   `setControllerSetPoint`, describe the internal process model with
   `setProcessModel` and `setProcessBias`, then choose a prediction horizon and
   tuning weights with `setPredictionHorizon` and `setWeights`.
3. **Apply limits and preferences** – use `setOutputLimits` to cap the actuator
   and `setPreferredControlValue` to encode an economic target such as minimum
   heater duty.
4. **Execute in the simulation** – call `runTransient(previousControl, dt)`
   every control interval. The return value from `getResponse()` is the new
   manipulated-variable value to apply to the process.

```java
ModelPredictiveController controller = new ModelPredictiveController("heaterMpc");
controller.setTransmitter(temperatureSensor);
controller.setControllerSetPoint(328.15, "K");
controller.setProcessModel(0.18, 45.0);   // gain, time constant [s]
controller.setProcessBias(298.15);
controller.setPredictionHorizon(20);
controller.setWeights(1.0, 0.03, 0.2);    // tracking, energy, move penalties
controller.setPreferredControlValue(20.0);
controller.setOutputLimits(0.0, 100.0);

double manipulated = controller.getResponse();
for (double t = 0.0; t < 1800.0; t += 5.0) {
  controller.runTransient(manipulated, 5.0);
  manipulated = controller.getResponse();
  heater.setDuty(manipulated, "kW");
}
```

The single-input mode automatically handles reverse-acting processes via
`setReverseActing(true)` and can be paused/resumed with `setActive(false)`.

### Multivariable optimisation

For flowsheets with several manipulated variables the MPC is configured with an
ordered control vector:

```java
controller.configureControls("dewPointCooler", "stabiliserHeater", "compressorSpeed");
controller.setInitialControlValues(6.0, 65.0, 0.78);
controller.setControlLimits("dewPointCooler", -10.0, 25.0);
controller.setControlLimits("stabiliserHeater", 40.0, 90.0);
controller.setControlLimits("compressorSpeed", 0.5, 1.05);
controller.setControlWeights(0.6, 0.2, 0.05);   // energy usage penalty
controller.setMoveWeights(0.2, 0.05, 0.02);     // movement smoothing
controller.setPreferredControlVector(0.0, 55.0, 0.8);
```

`getControlVector()` returns the most recent actuation proposal for all
manipulated variables, while `setPrimaryControlIndex` determines which entry is
exposed via `getResponse()` for backwards compatibility with controller
structures expecting a single output.

### Quality constraints and product specifications

MPC quality constraints describe how key product indicators respond to each
manipulated variable and to feed composition/rate changes. Limits are handled
as soft constraints, letting the optimiser trade off specification margin and
energy usage.

```java
ModelPredictiveController.QualityConstraint wobbeConstraint =
    ModelPredictiveController.QualityConstraint.builder("WobbeIndex")
        .measurement(wobbeTransmitter)
        .unit("MJ/Sm3")
        .limit(51.7)
        .margin(0.2)
        .controlSensitivity(0.04, -0.01, 0.03)
        .compositionSensitivity("nitrogen", -2.8)
        .rateSensitivity(0.005)
        .build();

controller.addQualityConstraint(wobbeConstraint);
```

The controller stores predicted specification values for diagnostics via
`getPredictedQuality`. Call `clearQualityConstraints()` when the control
structure changes or before reconfiguring sensitivities.

### Feedforward updates

`updateFeedConditions` injects the expected upstream composition and rate into
the next optimisation. Supplying these predictions enables proactive responses
to known feed changes and improves constraint tracking on multivariate systems.

```java
Map<String, Double> composition = new HashMap<>();
composition.put("methane", 0.82);
composition.put("ethane", 0.08);
composition.put("propane", 0.03);
controller.updateFeedConditions(composition, 12.4);    // kmol/hr
```

### Moving horizon estimation

When the underlying process characteristics drift, enable the embedded moving
horizon estimator so the internal model follows the plant:

```java
controller.enableMovingHorizonEstimation(60);   // keep the last 60 samples
```

After the estimator has gathered enough samples `getLastMovingHorizonEstimate()`
returns identified gain, time constant, bias and prediction error. Call
`clearMovingHorizonHistory()` to restart the identification window, or
`disableMovingHorizonEstimation()` to lock the controller to its current model.

### Using plant measurements alongside the model

Digital twins are most valuable when they continuously reconcile with plant
data. The MPC supports blending measured values from a facility with simulated
predictions:

- **Primary loop samples** – call
  `ingestPlantSample(measurement, appliedControl, dt)` each time a fresh
  transmitter value arrives from the plant. The controller uses the injected
  sample as the baseline for optimisation and feeds it into the moving-horizon
  estimator, allowing the internal model to track real-world drift even when no
  NeqSim `MeasurementDeviceInterface` is configured.
- **Product quality updates** – for laboratory or analyser results that arrive
  more slowly than the simulation, use `updateQualityMeasurement("wobbe", value)`
  (or the map-based overload) to store the real measurement against the relevant
  constraint. The MPC then combines that measured baseline with the process
  sensitivities and feedforward model to predict how upcoming moves will affect
  the specification.

This approach allows existing plant instrumentation to update the MPC while the
NeqSim model still contributes predictive behaviour for future disturbances.

### Diagnostics and best practices

- Use `getLastSampledValue()`, `getLastAppliedControl()` and
  `getPredictionHorizon()` to verify tuning.
- Clamp manual interventions through `setControlLimits` and
  `setOutputLimits` to protect equipment.
- Combine MPC with conventional structures such as cascade controllers by
  wiring the MPC output into an inner PID loop through the shared
  `ControllerDeviceInterface`.
- Review `MovingHorizonEstimationExampleTest` and
  `OffshoreProcessMpcIntegrationTest` in the test suite for end-to-end
  demonstrations covering adaptive tuning and constrained optimisation.
