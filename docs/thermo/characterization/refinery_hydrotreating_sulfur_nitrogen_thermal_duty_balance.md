---
title: Coupled sulfur/nitrogen thermal-duty balance
description: Combine caller-owned reaction-heat factors and sensible heating in an auditable hydrotreating duty receipt.
---

# Coupled sulfur/nitrogen thermal-duty balance

`RefineryHydrotreatingSulfurNitrogenThermalDutyBalance` converts explicit
caller-supplied sulfur- and nitrogen-removal heat-release factors to MW and offsets
them against a caller-supplied non-reaction sensible-heating duty. The calculation
inherits the qualified external product distribution and feed rate without
re-solving or mutating the upstream receipts.

The receipt uses MJ/kg removed for each caller-owned reaction-heat factor, MW for
the sensible-heating requirement, and MWh per tonne liquid feed for normalized
results. Positive net external duty means heating; negative net external duty means
cooling. Its explicit closure is:

```text
external heating - external cooling + reaction heat = sensible heating
```

```java
RefineryHydrotreatingSulfurNitrogenThermalDutyBalance duty =
    RefineryHydrotreatingSulfurNitrogenThermalDutyBalance.calculate(
        distribution, sulfurHeatReleaseMjPerKg, nitrogenHeatReleaseMjPerKg,
        sensibleHeatingMw);

double reactionHeatMw = duty.getTotalReactionHeatReleaseMegaWatt();
double externalHeatingMw = duty.getExternalHeatingDutyMegaWatt();
double externalCoolingMw = duty.getExternalCoolingDutyMegaWatt();
```

For the public 1000 kg/h DOE/OEDI Big Hill material case, illustrative caller
inputs of 100 MJ/kg sulfur removed, 50 MJ/kg nitrogen removed, and 1 MW sensible
heating give 0.113106096 MW sulfur reaction heat, 0.015071863 MW nitrogen reaction
heat, 0.128177959 MW total reaction heat, and 0.871822041 MW external heating.
These values qualify the arithmetic only. They are not defaults, measurements,
recommendations, or fitted heat-of-reaction correlations.

The material basis inherits the public DOE/OEDI Big Hill assay and the EIA, AIChE,
and NIST evidence qualified by the upstream coupled receipts. Every new thermal
input remains caller-owned, unit-explicit, and visible in the returned receipt.

This receipt does not calculate stream enthalpy, temperature, pressure, phase
equilibrium, furnace efficiency, reactor heat transfer, compressor/electric duty,
steam demand, heat integration, kinetics, catalyst behavior, product yield,
emissions, optimization, or equipment design. Those effects require independent
engineering qualification.
