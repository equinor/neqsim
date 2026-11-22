---
title: Thermal utility quality and exergy
description: Screen heating, cooling, and refrigeration duties for temperature feasibility and reversible-work quality.
---

Thermal power is not interchangeable across temperature levels. A 1 MW heat source at 70 °C cannot supply a process duty that requires 150 °C, and 1 MW of refrigeration has a different thermodynamic value from 1 MW of cooling water.

`ThermalUtilityQualityAnalysis` adds two bounded screens to a typed `UtilityEnergyBus`:

1. Temperature-grade feasibility from the utility supply temperature, process temperature, and minimum approach.
2. Reversible-work quality from the logarithmic-mean utility temperature and an environmental reference temperature.

All API temperatures in this guide are absolute temperatures in kelvin.

## Temperature feasibility

For a heating utility, the supply must satisfy

$$T_{\mathrm{supply}}\ge T_{\mathrm{process}}+\Delta T_{\min}$$

For cooling water, chilled water, refrigeration, or ambient cooling, the supply must satisfy

$$T_{\mathrm{supply}}\le T_{\mathrm{process}}-\Delta T_{\min}$$

A typed `ThermalUtilityConsumer` applies the same rule during `validateSetup()` and `run()`. Utility level and temperature grade are separate requirements: matching the steam or cooling-water label alone does not prove feasibility.

## Reversible-exergy screen

The effective utility temperature is the logarithmic mean of positive supply and return temperatures:

$$T_{\mathrm{lm}}=\frac{T_s-T_r}{\ln(T_s/T_r)}$$

The absolute Carnot quality factor relative to environment temperature $T_0$ is

$$\phi=\left|1-\frac{T_0}{T_{\mathrm{lm}}}\right|$$

and the corresponding screening exergy rate for non-negative thermal duty $\dot Q$ is

$$\dot E_x=\dot Q\phi$$

## Executable steam-quality screen

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.energy.ThermalUtilityConsumer;
import neqsim.process.equipment.energy.ThermalUtilityQualityAnalysis;
import neqsim.process.equipment.energy.UtilityEnergyBus;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.UtilityLevel;

public final class ThermalUtilityQualityExample {
  private static final Logger logger =
      LogManager.getLogger(ThermalUtilityQualityExample.class);

  private ThermalUtilityQualityExample() {}

  public static void main(String[] args) {
    UtilityEnergyBus lowPressureSteam = new UtilityEnergyBus(
        "LP steam", UtilityLevel.LOW_PRESSURE_STEAM, 425.0, 383.0);

    boolean heatingFeasible =
        ThermalUtilityQualityAnalysis.canServeProcessTemperature(
            lowPressureSteam, 400.0, 10.0);
    boolean heatingTooCold =
        ThermalUtilityQualityAnalysis.canServeProcessTemperature(
            lowPressureSteam, 420.0, 10.0);

    UtilityEnergyBus coolingWater = new UtilityEnergyBus(
        "cooling water", UtilityLevel.COOLING_WATER, 293.0, 313.0);
    boolean coolingFeasible =
        ThermalUtilityQualityAnalysis.canServeProcessTemperature(
            coolingWater, 310.0, 10.0);
    boolean coolingTooWarm =
        ThermalUtilityQualityAnalysis.canServeProcessTemperature(
            coolingWater, 300.0, 10.0);

    double effectiveTemperature =
        ThermalUtilityQualityAnalysis.getEffectiveTemperature(lowPressureSteam);
    double exergyFactor =
        ThermalUtilityQualityAnalysis.getExergyFactor(lowPressureSteam, 298.15);
    double exergyRateW = ThermalUtilityQualityAnalysis.getExergyRateForDuty(
        lowPressureSteam, 1.5e6, 298.15);

    ThermalUtilityConsumer reboiler = new ThermalUtilityConsumer(
        "reboiler", UtilityLevel.LOW_PRESSURE_STEAM);
    reboiler.connectEnergyStream(
        ThermalUtilityConsumer.INPUT_PORT, lowPressureSteam,
        EnergyPortMode.SPECIFICATION);
    reboiler.setProcessTemperatureRequirement(400.0, 10.0);

    assert heatingFeasible;
    assert !heatingTooCold;
    assert coolingFeasible;
    assert !coolingTooWarm;
    assert effectiveTemperature > 403.0 && effectiveTemperature < 405.0;
    assert exergyFactor > 0.25 && exergyFactor < 0.28;
    assert exergyRateW > 3.8e5 && exergyRateW < 4.2e5;
    assert reboiler.validateSetup().isValid();

    logger.info(
        "Utility-quality screen: Tlm={} K, exergy factor={}, exergy rate={} W",
        effectiveTemperature, exergyFactor, exergyRateW);
  }
}
```

With assertions enabled, the steam case is feasible for a 400 K process with a 10 K minimum approach. It gives an effective temperature near 404 K, a Carnot factor near 0.26, and approximately 0.39 MW of reversible-work quality for a 1.5 MW thermal duty.

## Solved-network KPIs

After an energy-network solve, `getServedExergyRate(...)`, `getUnmetExergyRate(...)`, and `getCurtailedExergyRate(...)` apply the same factor to the bus report. These methods require a completed solve and fail closed when no report exists. They do not perform the network allocation themselves.

## Scope

This is a process-integration screen. The absolute Carnot factor is a positive comparison metric for both hot utilities and refrigeration below the reference temperature; it is not a complete plant exergy balance. The model does not replace pinch analysis, exchanger-area calculation, finite-temperature heat-transfer analysis, steam-header hydraulics, refrigeration-cycle simulation, or equipment and relief design. Use qualified thermodynamic states and independently verified design methods for engineering decisions.
