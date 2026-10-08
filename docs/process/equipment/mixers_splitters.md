---
title: Mixers and Splitters
description: Combine and divide NeqSim process streams with mass, component, pressure, and energy checks.
---

Mixers combine material streams. Splitters divide one stream by flow or by component without
introducing a hydraulic pressure-drop model. The public classes are in
`neqsim.process.equipment.mixer` and `neqsim.process.equipment.splitter`.

## Complete executable example

This Java 8 example mixes two gas feeds, checks the inlet-pressure diagnostic, creates a 70/30
proportional split, and routes methane separately with `ComponentSplitter`. Temperature is in K,
pressure is absolute bara, and mass flow is in kg/h.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.splitter.ComponentSplitter;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class MixerSplitterExample {
  private static final Logger logger = LogManager.getLogger(MixerSplitterExample.class);

  private MixerSplitterExample() {}

  public static void main(String[] args) {
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
    mixer.setPressureMismatchTolerance(0.5);
    mixer.addStream(richGas);
    mixer.addStream(leanGas);
    mixer.run();

    StreamInterface mixedGas = mixer.getOutletStream();
    double mixedFlowKgPerHour = mixedGas.getFlowRate("kg/hr");
    double inletEnthalpyRate = richGas.getFluid().getEnthalpy()
        + leanGas.getFluid().getEnthalpy();
    double mixedEnthalpyRate = mixedGas.getFluid().getEnthalpy();
    assert Math.abs(mixedFlowKgPerHour - 8000.0) < 1.0e-6;
    assert Math.abs(mixer.getInletPressureSpread() - 2.0) < 1.0e-10;
    assert mixer.isPressureMismatch();
    assert Math.abs(mixedGas.getPressure("bara") - 30.0) < 1.0e-10;
    assert Math.abs(mixedEnthalpyRate - inletEnthalpyRate)
        / Math.max(1.0, Math.abs(inletEnthalpyRate)) < 1.0e-6;

    Splitter splitter = new Splitter("SP-100", mixedGas, 2);
    splitter.setSplitFactors(new double[] {7.0, 3.0});
    splitter.run();
    StreamInterface product = splitter.getSplitStream(0);
    StreamInterface recycle = splitter.getSplitStream(1);
    assert Math.abs(product.getFlowRate("kg/hr") - 5600.0) < 1.0e-6;
    assert Math.abs(recycle.getFlowRate("kg/hr") - 2400.0) < 1.0e-6;
    assert Math.abs(product.getFlowRate("kg/hr") + recycle.getFlowRate("kg/hr")
        - mixedFlowKgPerHour) < 1.0e-6;
    assert Double.isFinite(product.getFluid().getEnthalpy());
    assert Double.isFinite(recycle.getFluid().getEnthalpy());

    double feedMethaneMoles = mixedGas.getFluid().getComponent("methane").getNumberOfmoles();
    ComponentSplitter componentSplitter = new ComponentSplitter("CS-100", mixedGas);
    componentSplitter.setSplitFactors(new double[] {1.0, 0.0, 0.0});
    componentSplitter.run();
    StreamInterface methaneOutlet = componentSplitter.getSplitStream(0);
    StreamInterface liquidsOutlet = componentSplitter.getSplitStream(1);
    double routedMethaneMoles =
        methaneOutlet.getFluid().getComponent("methane").getNumberOfmoles();
    double retainedMethaneMoles =
        liquidsOutlet.getFluid().getComponent("methane").getNumberOfmoles();
    assert Math.abs(routedMethaneMoles - feedMethaneMoles) < 1.0e-8;
    assert Math.abs(retainedMethaneMoles) < 1.0e-12;
    assert Double.isFinite(methaneOutlet.getFluid().getEnthalpy());
    assert Double.isFinite(liquidsOutlet.getFluid().getEnthalpy());

    logger.info(
        "mixedFlow={} kg/h productFlow={} kg/h recycleFlow={} kg/h pressureSpread={} bar",
        mixedFlowKgPerHour,
        product.getFlowRate("kg/hr"),
        recycle.getFlowRate("kg/hr"),
        mixer.getInletPressureSpread());
  }
}
```

The documentation regression compiles this exact fence with `-source 8 -target 8` and executes it
with assertions enabled. The calculated temperature, composition, and enthalpy depend on the
selected thermodynamic model, mixing rule, feed states, and flow rates.

## Mixer behavior

In its default mode, `Mixer` applies total mass, component, and enthalpy balances:

$$\dot{m}_{\mathrm{out}}=\sum_i\dot{m}_i$$

$$\dot{H}_{\mathrm{out}}=\sum_i\dot{H}_i$$

For component $j$, the outlet mole fraction is molar-flow weighted:

$$x_{j,\mathrm{out}}=\frac{\sum_i\dot{n}_i x_{j,i}}{\sum_i\dot{n}_i}$$

Here, $\dot{m}$ is mass flow, $\dot{n}$ is molar flow, $\dot{H}$ is enthalpy flow, and $x_j$ is
mole fraction. The result is independent of inlet insertion order. NeqSim selects a stable active
inlet as the thermodynamic template and retains multiphase checking when any active inlet requests
it, unless the mixer explicitly disables that check.

### Pressure behavior

`Mixer` sets its outlet pressure to the lowest active inlet pressure. It does not calculate the
hydraulic loss required to equalize unequal feeds. `setPressureMismatchTolerance(double)`,
`isPressureMismatch()`, `getInletPressureSpread()`, `getMinInletPressure()`, and
`getMaxInletPressure()` expose that engineering diagnostic in bar/bara.

Use an upstream valve, compressor, pump, or pipeline when pressure loss or pressure equalization is
part of the model. `setOutletTemperature(double)` expects K and replaces the default
enthalpy-balanced calculation with a specified-temperature TP flash; use a downstream `Heater` or
`Cooler` when an auditable duty is required.

`StaticMixer` is a backward-compatible class name for the standard mixer implementation. It does
not add a pressure-drop calculation.

## Proportional and specified-flow splitting

`Splitter` clones the inlet thermodynamic state and composition into each outlet and changes the
amount of material. For normalized factors $f_k$:

$$\dot{m}_k=f_k\dot{m}_{\mathrm{in}},\qquad\sum_k f_k=1$$

`setSplitFactors` treats its values as relative weights, clamps negative weights to zero, and
normalizes the positive remainder. At least one value must be positive; `{7.0, 3.0}` therefore
means a 70/30 split.

Use `setFlowRates` when outlet rates are specified. `Splitter.REMAINDER` marks material left after
fixed demands. Multiple remainder outlets share the available flow equally. If positive fixed
demands exceed the feed, NeqSim scales them to the available flow and gives remainder outlets zero
flow.

Positive-flow proportional branches preserve composition and, after property initialization,
molar enthalpy and entropy. Their enthalpy and entropy rates sum to the feed rates. A zero-flow
branch remains a valid topology connection, but its molar thermodynamic getters are not qualified
for caloric balances; issue #4073 retains that residual-property and condenser/reboiler duty
boundary.

## Component-selective splitting

`ComponentSplitter` always creates two outlets. Each factor is the fraction of the corresponding
feed component routed to outlet zero; outlet one receives the remainder. `validateSetup()` requires
one finite factor in $[0,1]$ for every feed component, and input and returned factor arrays are
defensively copied.

The equipment preserves component inventories and equilibrates each positive-flow outlet at the
feed temperature and pressure. A composition-selective split at imposed T/P is not an adiabatic
energy balance. Compare total outlet and inlet enthalpy rates when estimating the external heat
requirement.

An exactly empty outlet retains an initialized reference composition and phase state so downstream
topology can execute without a trace-component workaround. That reference state does not give the
empty branch a physical molar enthalpy or component inventory.

## Validation checklist

- Run every inlet stream before a stand-alone mixer or splitter.
- Check mixer mass, component, and enthalpy closure in energy-balanced mode.
- Inspect pressure-mismatch evidence instead of treating a mixer as a hydraulic model.
- Confirm that splitter outlet rates sum to the feed rate.
- Confirm every `ComponentSplitter` factor follows the feed component order.
- Exclude zero-flow thermodynamic getters from caloric balances.
- Add equipment to a `ProcessSystem` in upstream-to-downstream order.

## Related documentation

- [Equipment index](index)
- [Streams](streams)
- [Heat exchangers](heat_exchangers)
- [Valves](valves)
- [Controllers and recycles](../controllers)
