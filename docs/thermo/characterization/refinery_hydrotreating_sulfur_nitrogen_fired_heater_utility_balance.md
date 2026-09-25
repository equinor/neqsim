---
title: Coupled sulfur/nitrogen fired-heater utility balance
description: Convert qualified hydrotreating heating duty to caller-owned fuel, cost, and emissions receipts.
---

# Coupled sulfur/nitrogen fired-heater utility balance

`RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance` converts the
external heating requirement from a qualified coupled thermal-duty receipt to
fuel input, furnace loss, fuel mass, cost, and indirect-emissions rates. Every
new assumption is caller-owned and returned in the immutable receipt.

The calculation uses furnace efficiency as delivered heat divided by fuel LHV
input. Fuel LHV is in MJ/kg, price is in caller-owned currency units per kg, and
the emissions factor is in kg CO2e/kg fuel. Its explicit closure is:

```text
fuel chemical power * furnace efficiency = delivered external heating duty
```

```java
RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance heater =
    RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance.calculate(
        thermalDuty, furnaceEfficiency, fuelLhvMjPerKg, fuelPricePerKg,
        fuelEmissionsKgCo2ePerKg);

double fuelKgPerHour = heater.getFuelMassFlowKgPerHour();
double fuelCostPerTonneFeed = heater.getFuelCostPerTonneFeed();
double emissionsPerTonneFeed =
    heater.getFuelEmissionsKgCo2EquivalentPerTonneFeed();
```

For the public 1000 kg/h DOE/OEDI Big Hill material case, the upstream caller
scenario requires 0.871822041 MW external heating. Illustrative caller inputs of
85% efficiency, 50 MJ/kg fuel LHV, 0.40 currency units/kg fuel, and 3.0 kg
CO2e/kg fuel give 1.025672990 MW fuel input, 73.848455268 kg/h fuel,
29.539382107 currency units/h, and 221.545365804 kg CO2e/h. These values qualify
the arithmetic only. They are not defaults, measurements, recommendations,
market prices, or fitted furnace correlations.

The material basis inherits the public DOE/OEDI Big Hill assay and the EIA,
AIChE, and NIST evidence qualified by the upstream coupled receipts. The new
scenario values are not sourced properties of that assay.

This receipt does not select a fuel, define a lifecycle boundary, calculate fuel
composition or combustion stoichiometry, predict flue gas, account for excess
air, estimate direct stack emissions, calculate radiation or convection losses,
size a furnace, model heat transfer, predict coking, price cooling, or optimize
heat integration. Those effects require independent engineering qualification.
