---
title: Coupled sulfur/nitrogen fired-heater heat-recovery credit balance
description: Convert utilized recovered heat to caller-owned avoided and net fired-heater receipts.
---

# Coupled sulfur/nitrogen fired-heater heat-recovery credit balance

`RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance`
consumes a qualified heat-recovery balance and a caller-owned utilization
fraction. It reports utilized and unutilized recovered heat plus avoided and net
fuel input, fuel mass, fuel cost, and fuel emissions.

The receipt reuses the qualified fired-heater efficiency, fuel LHV, caller-owned
price, and caller-owned emissions factor. Its explicit closures are:

```text
recovered heat = utilized recovered heat + unutilized recovered heat
original fuel power = avoided fuel power + net fuel power
```

```java
RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance credit =
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance.calculate(
        recovery, callerSpecifiedUtilizationFraction);

double avoidedFuelKgPerHour = credit.getAvoidedFuelMassFlowKgPerHour();
double netFuelCostPerHour = credit.getNetFuelCostPerHour();
double netFuelEmissionsKgCo2ePerHour =
    credit.getNetFuelEmissionsKgCo2EquivalentPerHour();
```

For the public 1000 kg/h DOE/OEDI Big Hill chain, the qualified 60% recovery
scenario provides 0.054679523 MW recovered heat. An illustrative caller-owned
75% utilization fraction gives 0.041009642 MW utilized heat, 0.013669881 MW
unutilized heat, 3.473757952 kg/h avoided fuel, 1.389503181 caller-currency
units/h avoided fuel cost, and 10.421273857 kg CO2e/h avoided caller-scenario
fuel emissions. Net fuel use is 70.374697316 kg/h. These scenario values qualify
the arithmetic only; they are not defaults, measurements, recommendations,
market prices, or lifecycle factors.

U.S. Department of Energy process-heating guidance identifies recovered exhaust
heat as an opportunity to reduce furnace fuel use while emphasizing that
practical recovery depends on the process and equipment. This receipt therefore
keeps utilization caller-owned and fails closed if utilized recovered heat
exceeds the qualified delivered heater duty.

The receipt does not size an exchanger or HRSG, predict temperature or pressure
drop, perform pinch or heat-network analysis, establish availability, model
operability, define a lifecycle boundary, select a market price, apply a carbon
price, or establish dew-point or corrosion limits. Those decisions require
independent engineering qualification.

Sources accessed 2026-09-27:

- [U.S. DOE: Waste Heat Reduction and Recovery for Improving Furnace Efficiency](https://www.energy.gov/sites/prod/files/2014/05/f15/35876.pdf)
- [U.S. DOE: Install Waste Heat Recovery Systems for Fuel-Fired Furnaces](https://www.energy.gov/sites/prod/files/2014/05/f16/install_waste_heat_process_htgts8.pdf)
