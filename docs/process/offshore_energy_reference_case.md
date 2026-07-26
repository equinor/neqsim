---
title: Offshore energy reference case
description: Reproduce a 24-hour offshore wind and gas-turbine electrical benchmark with fixed acceptance KPIs.
---

# Offshore energy reference case

`OffshoreEnergyReferenceCase` is a deterministic industrial regression benchmark for NeqSim energy networks. It represents a 24-hour offshore electrical system using six four-hour intervals.

## System

- variable offshore wind with zero marginal operating cost and zero direct emissions;
- 15 MW available gas-turbine generation;
- critical process load with highest demand priority;
- flexible process load with lower demand priority;
- wind accepted before gas generation;
- gas generation cost: 90 currency units/MWh;
- gas generation emissions: 450 kg CO₂-equivalent/MWh.

The wind profile deliberately exceeds total demand in one interval to verify source-specific wind curtailment. Gas capacity is sufficient to prevent unmet demand in every interval. The generic time-series result also reports all unused offered generation, including unused gas-turbine capacity, as total curtailed supply.

## Running the benchmark

```java
EnergyTimeSeriesResult result = OffshoreEnergyReferenceCase.run24HourCase();
OffshoreEnergyReferenceCase.requireAcceptanceCriteria(result);

double acceptedWind = OffshoreEnergyReferenceCase.getWindGeneratedEnergyMWh(result);
double acceptedGas = OffshoreEnergyReferenceCase.getGasGeneratedEnergyMWh(result);
double curtailedWind = OffshoreEnergyReferenceCase.getWindCurtailedEnergyMWh(result);
```

## Published acceptance values

| KPI | Expected value |
|---|---:|
| Duration | 24 h |
| Served energy | 312 MWh |
| Unmet energy | 0 MWh |
| Total curtailed offered supply | 196 MWh |
| Curtailed wind energy | 4 MWh |
| Gas-generated energy | 168 MWh |
| Wind-generated energy | 144 MWh |
| Operating cost | 15,120 currency units |
| CO₂-equivalent emissions | 75,600 kg |

The total curtailed-supply KPI includes both the 4 MWh of excess wind and 192 MWh of unused available gas-turbine capacity. Source-specific helpers separate accepted and curtailed energy for the wind and gas participants.

The benchmark validates exact breakpoint application for step profiles, source and load priority, chronological integration, cost and emissions accounting, aggregate and source-specific curtailment, shortage reporting, immutable interval history, and repeatable numerical results.

## Scope

The case is a transparent regression benchmark, not a design basis. It excludes electrical load flow, voltage stability, spinning reserve criteria, gas-turbine ambient derating, startup commitment, battery degradation, maintenance availability, and stochastic wind uncertainty.
