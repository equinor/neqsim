---
title: Mixers and Splitters
description: Combine and divide NeqSim process streams with mass, component, and energy checks.
---

Mixers combine material streams, while splitters divide one stream without changing its
thermodynamic state or composition. The public classes are in
`neqsim.process.equipment.mixer` and `neqsim.process.equipment.splitter`.

## Capabilities

| Class | Use |
| --- | --- |
| `Mixer` | Combine two or more streams and calculate an outlet state |
| `StaticMixer` | Use the alternative static-mixing implementation |
| `Splitter` | Divide one stream by relative factors or specified outlet flow rates |

The stream accessors return `StreamInterface`. Keep that interface type unless a downstream API
specifically requires the concrete `Stream` class.

## Mixer

In its default mode, `Mixer` applies total mass and component balances and calculates the outlet
temperature from an enthalpy balance:

$$
\dot{m}_{\mathrm{out}}=\sum_i \dot{m}_i
$$

$$
\dot{H}_{\mathrm{out}}=\sum_i \dot{H}_i
$$

For component $j$, the outlet mole fraction is molar-flow weighted:

$$
x_{j,\mathrm{out}}=
\frac{\sum_i \dot{n}_i x_{j,i}}{\sum_i \dot{n}_i}
$$

Here, $\dot{m}$ is mass flow, $\dot{n}$ is molar flow, $\dot{H}$ is enthalpy
flow, and $x_j$ is mole fraction.

### Complete mixer example

```java
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemSrkEos richFluid = new SystemSrkEos(300.0, 30.0);
richFluid.addComponent("methane", 0.80);
richFluid.addComponent("ethane", 0.15);
richFluid.addComponent("propane", 0.05);
richFluid.setMixingRule("classic");

Stream richGas = new Stream("rich gas", richFluid);
richGas.setFlowRate(5000.0, "kg/hr");
richGas.run();

SystemSrkEos leanFluid = new SystemSrkEos(310.0, 32.0);
leanFluid.addComponent("methane", 0.95);
leanFluid.addComponent("ethane", 0.04);
leanFluid.addComponent("propane", 0.01);
leanFluid.setMixingRule("classic");

Stream leanGas = new Stream("lean gas", leanFluid);
leanGas.setFlowRate(3000.0, "kg/hr");
leanGas.run();

Mixer mixer = new Mixer("M-100");
mixer.addStream(richGas);
mixer.addStream(leanGas);
mixer.run();

StreamInterface mixedGas = mixer.getOutletStream();
double mixedFlowKgPerHour = mixedGas.getFlowRate("kg/hr");
double mixedTemperatureC = mixedGas.getTemperature("C");
```

The example produces $8000\ \mathrm{kg/h}$. A focused documentation test verifies the flow,
enthalpy closure, component result, and pressure diagnostics.

### Pressure behavior and diagnostics

`Mixer` sets the outlet pressure to the lowest active inlet pressure. It does not provide an
independent `setOutletPressure` specification. If active inlet pressures differ by more than the
configured tolerance, the calculation continues at the lowest pressure and records a diagnostic:

```java
mixer.setPressureMismatchTolerance(0.5);
mixer.run();

boolean pressureMismatch = mixer.isPressureMismatch();
double pressureSpreadBar = mixer.getInletPressureSpread();
double minimumInletPressureBara = mixer.getMinInletPressure();
double maximumInletPressureBara = mixer.getMaxInletPressure();
```

A material mixer is not a hydraulic pressure-drop model. Use an upstream valve, compressor, pump,
or pipeline model when pressure equalization or pressure loss must be represented explicitly.

### Specified outlet temperature

`setOutletTemperature(double)` is available and expects kelvin. It changes the calculation from
the default enthalpy-balanced mode to a specified-temperature TP flash:

```java
mixer.setOutletTemperature(305.15);
mixer.run();
```

Use this mode only when the outlet temperature is an imposed boundary condition. For an auditable
heating or cooling duty, retain the energy-balanced mixer and add a downstream `Heater` or
`Cooler`.

## Splitter

`Splitter` clones the inlet thermodynamic state and composition into each outlet and changes only
the amount of material. For normalized split factors $f_k$:

$$
\dot{m}_k=f_k\dot{m}_{\mathrm{in}},
\qquad
\sum_k f_k=1
$$

### Relative split factors

`setSplitFactors` accepts nonnegative relative weights and normalizes them. The values do not have
to sum to one. For example, `{7.0, 3.0}` becomes `{0.7, 0.3}`.

```java
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.StreamInterface;

Splitter splitter = new Splitter("SP-100", mixedGas, 2);
splitter.setSplitFactors(new double[] {7.0, 3.0});
splitter.run();

StreamInterface product = splitter.getSplitStream(0);
StreamInterface recycle = splitter.getSplitStream(1);
```

Negative factor weights are clamped to zero before normalization. Supply at least one positive
weight.

### Specified flows and remainder outlets

Use `setFlowRates` when one or more outlet flow rates are known. `Splitter.REMAINDER` (equal to
`-1.0`) marks an outlet that receives material left after the fixed demands:

```java
Splitter distributor = new Splitter("distribution splitter", mixedGas, 2);
distributor.setFlowRates(
    new double[] {2500.0, Splitter.REMAINDER},
    "kg/hr");
distributor.run();

StreamInterface fixedDemand = distributor.getSplitStream(0);
StreamInterface remainingFlow = distributor.getSplitStream(1);
```

If several outlets use `Splitter.REMAINDER`, they share the leftover flow equally. If fixed
positive demands exceed the inlet flow, NeqSim scales them proportionally to the available flow
and assigns zero to remainder outlets. Other negative fixed-flow values are invalid and are
clamped to zero.

## Static mixer

`StaticMixer` supports the same multi-inlet connection pattern but uses its own mixing
implementation:

```java
import neqsim.process.equipment.mixer.StaticMixer;
import neqsim.process.equipment.stream.StreamInterface;

StaticMixer staticMixer = new StaticMixer("MX-101");
staticMixer.addStream(richGas);
staticMixer.addStream(leanGas);
staticMixer.run();

StreamInterface staticMixerOutlet = staticMixer.getOutletStream();
```

`StaticMixer` does not expose a `setPressureDrop` method. Add a valve or pipe model when the
pressure loss through a physical mixing element is part of the engineering question.

## Validation checklist

- Run every inlet stream before running a stand-alone mixer or splitter.
- Check mixer mass and enthalpy closure in the default energy-balanced mode.
- Inspect mixer pressure-mismatch diagnostics; do not silently mix materially different
  pressures.
- Check that splitter outlet flows sum to the inlet flow.
- Use `StreamInterface` for mixer and splitter outlet accessors.
- Add equipment to a `ProcessSystem` in upstream-to-downstream order for integrated simulations.

## Related documentation

- [Equipment index](index.md)
- [Streams](streams)
- [Heat exchangers](heat_exchangers)
- [Valves](valves)
- [Controllers and recycles](../controllers)
