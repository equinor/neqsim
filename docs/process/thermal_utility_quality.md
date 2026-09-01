---
title: Thermal utility quality and exergy
description: Screen heating, cooling, and refrigeration duties for temperature feasibility and reversible-work quality.
---

# Thermal utility quality and exergy

Thermal power is not interchangeable across temperature levels. A 1 MW heat source at 70 °C cannot supply a process duty that requires 150 °C, and 1 MW of refrigeration has a different thermodynamic value from 1 MW of cooling water.

`ThermalUtilityQualityAnalysis` adds two screening calculations to a typed `UtilityEnergyBus`:

1. **Temperature-grade feasibility** using a process temperature and minimum approach.
2. **Minimum reversible exergy rate** using a logarithmic-mean utility temperature and a reference environment.

## Temperature feasibility

For a heating utility:

\[
T_{supply} \ge T_{process} + \Delta T_{min}
\]

For cooling water, chilled water, refrigeration, or ambient cooling:

\[
T_{supply} \le T_{process} - \Delta T_{min}
\]

```java
UtilityEnergyBus lpSteam = new UtilityEnergyBus(
    "LP steam", UtilityLevel.LOW_PRESSURE_STEAM,
    425.0, 383.0);

boolean feasible = ThermalUtilityQualityAnalysis.canServeProcessTemperature(
    lpSteam, 400.0, 10.0);
```

A `ThermalUtilityConsumer` can enforce the same rule during validation and execution:

```java
ThermalUtilityConsumer reboiler = new ThermalUtilityConsumer(
    "reboiler", UtilityLevel.LOW_PRESSURE_STEAM);
reboiler.connectEnergyStream(
    ThermalUtilityConsumer.INPUT_PORT,
    lpSteam,
    EnergyPortMode.SPECIFICATION);
reboiler.setProcessTemperatureRequirement(400.0, 10.0);
```

If the connected utility grade cannot satisfy the requirement, `validateSetup()` reports an actionable error and `run()` rejects the physically infeasible allocation.

## Reversible exergy screening

The effective utility temperature is calculated as the logarithmic mean of supply and return temperatures:

\[
T_{lm} = \frac{T_s - T_r}{\ln(T_s/T_r)}
\]

The reversible-work quality factor relative to environment temperature \(T_0\) is:

\[
\phi = \left|1 - \frac{T_0}{T_{lm}}\right|
\]

and the exergy-rate screening metric is:

\[
\dot E_x = \dot Q\,\phi
\]

```java
double exergyFactor = ThermalUtilityQualityAnalysis.getExergyFactor(
    lpSteam, 298.15);

double servedExergy = ThermalUtilityQualityAnalysis.getServedExergyRate(
    lpSteam, 298.15);

double curtailedExergy = ThermalUtilityQualityAnalysis.getCurtailedExergyRate(
    lpSteam, 298.15);
```

The absolute Carnot factor provides a positive screening metric for both hot utilities and refrigeration below the reference temperature. It is useful for comparing utility grades, heat-recovery opportunities, and curtailment quality.

## Scope

This is a process-integration screening model. It does not replace a full entropy balance, pinch analysis, exchanger area calculation, steam-header hydraulic model, or refrigeration-cycle simulation. Use qualified thermodynamic states and detailed equipment models for design decisions.
