---
title: Energy time-series simulation
description: Run energy networks with renewable, load, price, and emissions profiles and integrate operational KPIs.
---

# Energy time-series simulation

`EnergyTimeSeriesSimulator` executes a `ProcessSystem` and one or more `EnergyBus` objects over fixed-duration intervals. It supports repeated steady-state studies and transient execution for storage, rotating shafts, trips, ramp limits, and other dynamic equipment.

Profiles may be stepwise or linearly interpolated:

```java
EnergyTimeSeriesProfile wind = EnergyTimeSeriesProfile.linear(
    "wind generation",
    new double[] {0.0, 3600.0, 7200.0},
    new double[] {1.0e6, 2.5e6, 0.5e6});

EnergyTimeSeriesProfile demand = EnergyTimeSeriesProfile.step(
    "process demand",
    new double[] {0.0, 3600.0, 7200.0},
    new double[] {1.5e6, 2.0e6, 1.0e6});
```

Bind each profile to the relevant setter:

```java
EnergyTimeSeriesSimulator study = new EnergyTimeSeriesSimulator(process);
study.addEnergyBus(electricalBus);
study.setIntervalSeconds(900.0);
study.setDurationSeconds(24.0 * 3600.0);

study.addProfile(wind, value -> windPort.setDuty(value));
study.addProfile(demand, value -> loadPort.setRequestedPower(value));
```

For transient studies:

```java
study.setExecutionMode(EnergyTimeSeriesSimulator.ExecutionMode.TRANSIENT);
```

The result stores every interval and integrates the network report rates:

```java
EnergyTimeSeriesResult result = study.run();

double servedMWh = result.getServedEnergyMWh();
double unmetMWh = result.getUnmetEnergyMWh();
double curtailedMWh = result.getCurtailedEnergyMWh();
double operatingCost = result.getOperatingCost();
double co2Kg = result.getCo2EmissionsKg();
```

The last interval is shortened automatically when the study duration is not an integer multiple of the interval length. Every interval retains immutable `EnergyNetworkReport` snapshots for audit, plotting, and downstream optimization.

## Recommended uses

- offshore electrical balance with wind, gas turbines, batteries, and process loads;
- variable electricity price and grid-carbon intensity studies;
- renewable curtailment and unmet-demand analysis;
- battery state-of-charge and ramp studies in transient mode;
- utility demand profiles and operating-cost estimates;
- lifecycle or seasonal operating scenarios.

## Scope

The runner performs chronological simulation and KPI integration. It does not itself solve unit commitment, market bidding, or stochastic optimization. Dispatch within each interval follows the configured `EnergyBus` policy.
