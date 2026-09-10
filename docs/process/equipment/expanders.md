---
title: Expanders and Turbines
description: Runnable gas expansion, power recovery, NGL separation, and shaft power accounting examples using the current NeqSim APIs.
---

Gas expanders recover shaft work while reducing pressure. Each example below
defines its fluid, flow, and equipment. Place its imports at file level and its
statements inside a Java method. `MathAndExpanderDocumentationTest` compiles and
runs the marked examples directly from this page.

## Table of Contents

- [Overview](#overview)
- [Expander Class](#expander-class)
- [Turboexpander](#turboexpander)
- [Power Recovery](#power-recovery)
- [Compander Systems](#compander-systems)
- [Examples](#examples)

## Overview

| Class | Purpose |
|---|---|
| `neqsim.process.equipment.expander.Expander` | Expansion at a specified outlet pressure and efficiency |
| `neqsim.process.equipment.expander.TurboExpanderCompressor` | Integrated expander/compressor with design data and speed matching |
| `neqsim.process.equipment.expander.MapTurboExpanderCompressor` | Integrated machine using performance maps |
| `neqsim.process.equipment.stream.MechanicalShaft` | Shared shaft power balance and rotational dynamics |

There are no `TurboExpander` or `ExpanderCompressorModule` classes.
Use `Expander` for a simple thermodynamic expansion and the integrated
classes when machine matching is required.

## Expander Class

### Basic Usage

`new Expander(name, inlet)` takes a solved inlet stream.
`setOutletPressure(value, "bara")` specifies absolute discharge pressure and
`setIsentropicEfficiency(value)` takes a fraction between zero and one.
Read temperature and pressure through `getOutletStream()`.

### Outlet Specification

The examples use outlet pressure and efficiency as independent specifications.
Do not assume every inherited compressor setting is implemented by the
expander's solver. For an integrated machine with an outlet-temperature
specification, see the
[turboexpander/compressor model](../../simulation/turboexpander_compressor_model).

## Turboexpander

A simple turboexpansion is modeled by `Expander`, including expansion into
a two-phase outlet. Use a downstream `Separator` when the resulting phases
need separate material streams.

### Shaft Coupling

For `TurboExpanderCompressor`, the compressor feed is supplied through
`setCompressorFeedStream` and the expander pressure through
`setExpanderOutPressure` (bara). Its design speed, efficiencies, geometry,
and maps must represent the actual machine. The
[integrated-machine guide](../../simulation/turboexpander_compressor_model)
documents that workflow.

## Power Recovery

### Isentropic Power

For specific enthalpies in J/kg and mass flow in kg/s, the positive magnitude
of ideal recovered power is

$$P_{s}=\dot m(h_{in}-h_{out,s})$$

### Actual Power

$$P_{recovered}=\eta_s P_s$$

`Expander.getPower("kW")` uses a **negative** value for work extracted from
the gas. Therefore, display `-expander.getPower("kW")` as positive recovered
power. A compressor's consumed power is positive. Do not subtract an already
negative expander value from compressor demand and call that recovered power.

### Temperature Drop

An expander and a throttling valve follow different thermodynamic paths.
The comparison example below evaluates both at identical inlet and outlet
pressures. Cooling depends on the fluid and conditions; the displayed trend
is a result of that case, not a guarantee for every fluid.

### Capacity Utilization

`setRatedRecoveredPower` takes kW and creates a `recoveredPower` constraint
based on the magnitude of expander power. Without an installed rating there
is no recovered-power rating check. Read the specific constraint for that
utilization; other constraints may still limit the machine. See the
[capacity framework](../CAPACITY_CONSTRAINT_FRAMEWORK#expanders-turbo-expanders).

## Compander Systems

For already solved equipment, `MechanicalShaft.setGeneratedPower` and
`setConsumedPower` take watts. `getNetPower("kW")` is positive for a surplus
and negative for a deficit. The third example performs this accounting only:
it does not change a compressor pressure or solve a speed match. Use the
integrated machine when that coupling is needed.

## Examples

### Example 1: Simple Expander

SRK gas at 320 K and 80 bara expands to 20 bara with 85% isentropic
efficiency. The output should cool, recover work, and conserve mass.

<!-- doc-test: expander-basic -->
```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.expander.Expander;

SystemSrkEos gas = new SystemSrkEos(320.0, 80.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.07);
gas.addComponent("propane", 0.03);
gas.setMixingRule("classic");

Stream feed = new Stream("HP Gas", gas);
feed.setFlowRate(50000.0, "kg/hr");
feed.run();

Expander expander = new Expander("EX-100", feed);
expander.setOutletPressure(20.0, "bara");
expander.setIsentropicEfficiency(0.85);
expander.run();

double outletTemperatureC = expander.getOutletStream().getTemperature("C");
double recoveredPowerKW = -expander.getPower("kW");
double outletMassFlow = expander.getOutletStream().getFlowRate("kg/hr");
expander.setRatedRecoveredPower(5000.0);
double powerUtilization = expander.getCapacityConstraints()
    .get("recoveredPower").getUtilization();
```

### Example 2: NGL Recovery with Turboexpander

The cooler can create liquid before expansion. This simplified equilibrium
example does not qualify inlet liquid tolerance or separation internals for a
real turboexpander. Include upstream separation and a machine operating
envelope for an equipment study.

<!-- doc-test: expander-ngl -->
```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.separator.Separator;

SystemSrkEos richGas = new SystemSrkEos(300.0, 70.0);
richGas.addComponent("nitrogen", 0.02);
richGas.addComponent("methane", 0.75);
richGas.addComponent("ethane", 0.10);
richGas.addComponent("propane", 0.08);
richGas.addComponent("n-butane", 0.05);
richGas.setMixingRule("classic");

Stream feed = new Stream("Rich Gas", richGas);
feed.setFlowRate(100000.0, "Sm3/day");
feed.run();

Cooler precooler = new Cooler("Pre-cooler", feed);
precooler.setOutTemperature(280.0);
precooler.run();
Separator inletSeparator = new Separator("Expander inlet separator", precooler.getOutletStream());
inletSeparator.run();

Expander expander = new Expander("TEX-100", inletSeparator.getGasOutStream());
expander.setOutletPressure(25.0, "bara");
expander.setIsentropicEfficiency(0.82);
expander.run();

Separator coldSeparator = new Separator("Cold Separator", expander.getOutletStream());
coldSeparator.run();
double nglKgPerHour = coldSeparator.getLiquidOutStream().getFlowRate("kg/hr");
double totalOutletKgPerHour = inletSeparator.getLiquidOutStream().getFlowRate("kg/hr")
    + coldSeparator.getGasOutStream().getFlowRate("kg/hr") + nglKgPerHour;
```

The mass balance includes liquid recovered in both separators. Report an
actual liquid volume with `"m3/hr"` only with its operating conditions;
standard gas-equivalent volume is a different quantity.

### Example 3: Expander vs JT Valve Comparison

Two independent feeds prevent shared fluid state between alternative paths.
The additional compressor represents a separately specified shaft load.

<!-- doc-test: expander-shaft -->
```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.MechanicalShaft;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.compressor.Compressor;

SystemSrkEos gas = new SystemSrkEos(300.0, 60.0);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.10);
gas.addComponent("propane", 0.05);
gas.setMixingRule("classic");

Stream valveFeed = new Stream("Valve feed", gas);
valveFeed.setFlowRate(10000.0, "kg/hr");
valveFeed.run();
Stream expanderFeed = new Stream("Expander feed", gas.clone());
expanderFeed.setFlowRate(10000.0, "kg/hr");
expanderFeed.run();

ThrottlingValve valve = new ThrottlingValve("JT Valve", valveFeed);
valve.setOutletPressure(15.0, "bara");
valve.run();
Expander expander = new Expander("Expander", expanderFeed);
expander.setOutletPressure(15.0, "bara");
expander.setIsentropicEfficiency(0.85);
expander.run();

Compressor compressor = new Compressor("Independent shaft load", valveFeed);
compressor.setOutletPressure(80.0, "bara");
compressor.setIsentropicEfficiency(0.75);
compressor.run();

MechanicalShaft shaft = new MechanicalShaft("Power accounting");
shaft.setGeneratedPower(expander.getName(), -expander.getPower());
shaft.setConsumedPower(compressor.getName(), compressor.getPower());
double shaftSurplusKW = shaft.getNetPower("kW");
double extraCoolingK = valve.getOutletStream().getTemperature("K")
    - expander.getOutletStream().getTemperature("K");
```

## Related Documentation

- [Equipment Index](index.md)
- [Compressors](compressors)
- [Valves](valves)
- [Heat Exchangers](heat_exchangers)
