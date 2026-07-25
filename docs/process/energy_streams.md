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

## Reboiler duty reporting

A `Reboiler` now publishes its calculated heat duty through `getEnergyStream()`. The stream is typed as `HEAT`, and `getDuty("kW")` or `getEnergyFlow("MW")` can be used for utility summaries and downstream coupling.

## Compatibility

Existing `getDuty()`, `setDuty(double)`, equality, and `setEnergyStream(EnergyStream)` behavior remain available. Legacy equipment that encodes direction in a signed duty keeps that convention until it is migrated to typed ports. New integrations should declare an `EnergyType` and use named ports so type validation and graph scheduling are available.
