---
title: Energy unit commitment constraints
description: Model startup, minimum load, ramps, minimum up/down time, startup cost, and startup emissions.
---

# Energy unit commitment constraints

`CommittedEnergyGenerator` represents a dispatchable electrical, shaft-work, heat, or other typed generation unit whose output cannot change freely between chronological intervals.

```java
CommittedEnergyGenerator turbine = new CommittedEnergyGenerator(
    "gas turbine generator", EnergyType.ELECTRICAL);

turbine.setPowerLimits(5.0e6, 25.0e6);
turbine.setRampRates(1.0e6, 2.0e6);
turbine.setMinimumUpDownTimes(4.0 * 3600.0, 2.0 * 3600.0);
turbine.setStartupPenalty(150000.0, 5000.0);
turbine.connectEnergyStream(
    CommittedEnergyGenerator.OUTPUT_PORT,
    electricalBus,
    EnergyPortMode.CALCULATED);
```

Initialize the chronological state before a study:

```java
turbine.initializeCommitment(false, 3.0 * 3600.0, 0.0);
```

A positive requested power is an on-command; zero is an off-command:

```java
turbine.setRequestedPower(20.0e6);
turbine.runTransient(900.0, calculationId);
```

The latest immutable `StepResult` reports whether the unit started, stopped, or was blocked by minimum up/down time. The unit also tracks cumulative startup count, cost, and CO2-equivalent emissions.

```java
CommittedEnergyGenerator.StepResult step = turbine.getLastStepResult();
double output = step.getGeneratedPower();
int starts = turbine.getStartupCount();
double startupCost = turbine.getCumulativeStartupCost();
double startupEmissions = turbine.getCumulativeStartupEmissionsKg();
```

During an enforced minimum-up period, an off-command retains at least the minimum stable target. During minimum-down time, an on-command remains blocked. Ramp limits apply between chronological steps.

## Scope

This class enforces one unit's chronological constraints. It does not choose the globally optimal on/off schedule. Combine it with time-series profiles, merit-order dispatch, or a higher-level mixed-integer optimizer for multi-unit commitment studies. Startup trajectories below minimum stable load and detailed thermal-state startup models require equipment-specific dynamic models.
