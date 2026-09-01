---
title: Thermal utility header hydraulics
description: Screen steam, hot-oil, cooling-water, chilled-water, and refrigeration headers for velocity, pressure drop, and pump power.
---

# Thermal utility header hydraulics

`ThermalUtilityHydraulicModel` combines utility mass flow with a single-header Darcy-Weisbach pressure-drop calculation. It is intended for early sizing, bottleneck checks, and utility-network screening.

```java
ThermalUtilityHydraulicModel header = new ThermalUtilityHydraulicModel();
header.setGeometry(1000.0, 0.30, 4.5e-5);
header.setFluidProperties(998.0, 1.0e-3);
header.setLocalLossCoefficient(8.0);
header.setPumpEfficiency(0.80);
```

Mass flow may come directly from a thermodynamic utility bus:

```java
double massFlow = coolingWaterBus.getServedMassFlow();
double velocity = header.getVelocity(massFlow);
double reynolds = header.getReynoldsNumber(massFlow);
double pressureDropPa = header.getPressureDrop(massFlow);
double pumpPowerW = header.getPumpPower(massFlow);
```

The model uses the laminar relation `64/Re` below Reynolds number 2300 and the Haaland approximation for turbulent flow. Aggregate valves, bends, strainers, and exchangers can be represented by one local-loss coefficient.

Capacity limits support quick debottlenecking:

```java
header.setCapacityLimits(2.5, 3.0e5);
boolean acceptable = header.isWithinCapacity(massFlow);
double maximumMassFlow = header.getMaximumMassFlow();
```

## Fluid properties

Density and dynamic viscosity must represent the relevant utility condition. Obtain these from NeqSim, steam tables, vendor data, or another qualified property source. For compressible steam headers, use representative properties only for screening.

## Scope

This is a single equivalent-header model. It does not replace branched network balancing, compressible Fanno flow, flashing or condensation, two-phase pressure drop, control-valve sizing, water hammer, or detailed pump and compressor curves.
