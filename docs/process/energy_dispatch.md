---
title: Energy network dispatch strategies
description: Select priority, minimum-cost, or minimum-emissions generation dispatch while preserving load priorities and balancing reserves.
---

# Energy network dispatch strategies

`EnergyBus` uses deterministic priority/proportional dispatch by default. Lower priority numbers are selected first, and equal-priority participants share available power in proportion to their offers or requests.

Professional process-energy studies can optionally select a generation merit order:

- `PRIORITY_PROPORTIONAL`: existing default behavior.
- `MINIMUM_COST`: lower `energyPricePerMWh` sources are accepted first.
- `MINIMUM_EMISSIONS`: lower `emissionFactorKgPerMWh` sources are accepted first.

The selected policy applies separately to normal generation and balancing generation. Balancing equipment therefore remains reserve and does not displace normal sources merely because its marginal cost or emission factor is lower. Consumer priority and proportional shortage allocation are unchanged.

## Minimum-cost dispatch

```java
EnergyBus grid = new EnergyBus("electrical grid", EnergyType.ELECTRICAL);

gridImport.setEnergyPricePerMWh(80.0);
windGeneration.setEnergyPricePerMWh(5.0);

gridImport.connect(grid);
windGeneration.connect(grid);
compressorLoad.connect(grid);

grid.setDispatchStrategy(EnergyDispatchStrategy.MINIMUM_COST);
EnergyNetworkReport report = grid.solveBalance();

double operatingCostPerHour = report.getOperatingCostPerHour();
double curtailedPower = report.getCurtailedSupply();
```

Sources with equal objective value and equal priority share accepted generation proportionally. Priority remains a secondary discriminator when two sources have the same price or emission factor.

## Minimum-emissions dispatch

```java
gasTurbinePort.setEmissionFactorKgPerMWh(500.0);
gridPort.setEmissionFactorKgPerMWh(40.0);

grid.setDispatchStrategy(EnergyDispatchStrategy.MINIMUM_EMISSIONS);
EnergyNetworkReport report = grid.solveBalance();

double emissionsKgPerHour = report.getCo2EmissionRate();
```

## Scope

The strategies are deterministic merit-order policies, not a mixed-integer unit-commitment optimizer. They do not model startup cost, minimum uptime, ramping between steady-state cases, spinning reserve requirements, network losses, AC load flow, or market settlement. Those constraints belong in a higher-level time-series or optimization layer.
