---
title: Energy streams and equipment ports
description: Connect heat, shaft-work, and electrical duties between NeqSim unit operations.
---

# Energy streams and equipment ports

`EnergyStream` carries a power rate between process equipment. Its canonical unit is watt (W), while unit-aware APIs accept `W`, `kW`, `MW`, `hp`, and `BTU/hr`.

Typed `EnergyPort` metadata separates three concepts:

- `EnergyType`: `HEAT`, `SHAFT_WORK`, `ELECTRICAL`, or legacy `UNSPECIFIED`.
- `EnergyPortDirection`: physical flow relative to the equipment boundary.
- `EnergyPortMode`: whether the equipment calculates the duty, reads it as a specification, or leaves it to a balance solver.

Calculation mode controls process-graph ordering. An equipment port in `CALCULATED` mode is scheduled before a unit reading the same stream through a `SPECIFICATION` port, even if units were added to the process in the opposite order.

## Energy-driven pump

A connected shaft-work stream makes `Pump` calculate its outlet pressure from available shaft power, inlet volumetric flow, and efficiency:

```java
SystemInterface water = new SystemSrkEos(298.15, 2.0);
water.addComponent("water", 1.0);
water.setMixingRule("classic");

Stream feed = new Stream("pump feed", water);
feed.setFlowRate(100000.0, "kg/hr");
feed.run();

EnergyStream shaft = new EnergyStream("pump shaft", EnergyType.SHAFT_WORK);
shaft.setPower(100.0, "kW");

Pump pump = new Pump("energy-driven pump", feed);
pump.setIsentropicEfficiency(0.75);
pump.setEnergyStream(shaft);
pump.run();

double outletPressure = pump.getOutletStream().getPressure("bara");
EnergyPortMode mode = pump.getEnergyPort("shaftPower").getMode();
```

Without an externally connected energy stream, `Pump` keeps its existing pressure-specified behavior and publishes the calculated shaft duty through `getEnergyStream()`.

## Multi-party buses and shafts

Use `EnergyBus` when several producers or consumers share a heat or electrical network. Named contributions are signed: positive values inject power and negative values withdraw power.

```java
EnergyBus grid = new EnergyBus("main electrical bus", EnergyType.ELECTRICAL);
grid.setContribution("solar", 2.0, "MW");
grid.setContribution("electrolyzer", -1.5, "MW");
double reserve = grid.getNetPower("kW");
```

`MechanicalShaft` is a shaft-work bus with convenience methods for generation and loads:

```java
MechanicalShaft shaftTrain = new MechanicalShaft("expander-compressor shaft");
shaftTrain.setMechanicalEfficiency(0.98);
shaftTrain.setGeneratedPower("expander", 10.0e6);
shaftTrain.setConsumedPower("compressor", 8.0e6);
double sparePower = shaftTrain.getNetPower("MW");
```

Point-to-point `EnergyStream` connections reject multiple calculated producers or specification consumers during graph construction. Use `EnergyBus` for intentional multi-party distribution.

## Equipment coverage

| Equipment group | Typed port | Supported role |
|---|---|---|
| Pump, Compressor | `shaftPower` input | Calculated duty or external power specification |
| Expander, SteamTurbine | `shaftPower` output | Calculated shaft power |
| GasTurbine | `shaftPower` and `exhaustHeat` outputs | Calculated power/heat; shaft output can also be a fuel-sizing specification |
| GasTurbineUnit | `shaftPower` output | Calculated delivered shaft power at the active demand and site limit |
| CombinedCycleSystem | `electricalPower` output | Calculated combined-cycle generation |
| Heater, Cooler | `heatDuty` bidirectional | Calculated duty or legacy external duty specification |
| Condenser | `heatDuty` output | Calculated heat removal |
| Reboiler | `heatDuty` input | Calculated duty or legacy external duty specification |
| SolarPanel, WindTurbine, WindFarm, FuelCell | `electricalPower` output | Calculated generation |
| BatteryStorage | `electricalPower` bidirectional | Calculated charge/discharge |
| Electrolyzer | `electricalPower` input | Feed-calculated demand or connected power-driven specification |
| CO2Electrolyzer, BioFeedstockPreparation | `electricalPower` input | Calculated demand |
| AmmoniaSynthesisReactor | `reactionHeat` output | Calculated reaction heat |
| StirredTankReactor | `heatDuty` bidirectional; `agitatorPower` input | Calculated or specified heat duty and calculated electrical demand |

## Reboiler duty reporting

A `Reboiler` now publishes its calculated heat duty through `getEnergyStream()`. The stream is typed as `HEAT`, and `getDuty("kW")` or `getEnergyFlow("MW")` can be used for utility summaries and downstream coupling.

## Compatibility

Existing `getDuty()`, `setDuty(double)`, equality, and `setEnergyStream(EnergyStream)` behavior remain available. Legacy equipment that encodes direction in a signed duty keeps that convention until it is migrated to typed ports. New integrations should declare an `EnergyType` and use named ports so type validation and graph scheduling are available.
