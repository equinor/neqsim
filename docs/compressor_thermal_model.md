---
title: Compressor Thermal Model and Catalog
description: Reduced-order thermal resistance/capacitance network for estimating compressor metal temperatures (shaft, impeller, casing, dry-gas seal, bearings). Covers the governing lumped-node energy balance, steady-state and transient solvers, the JSON compressor catalog workflow, and condensate/sulfur-deposition screening.
---

# Compressor thermal model and catalog

`CompressorThermalModel` is a reduced-order thermal resistance/capacitance network for estimating temperatures at locations that are not represented by the process-gas outlet temperature. Standard locations include the inlet shaft, impeller, casing, dry-gas seal, radial bearing and thrust bearing. Fluid boundaries include suction gas, discharge gas, seal gas, lube oil and ambient air.

The model is intended for screening, sensitivity analysis, transient studies and fitting to measured temperatures. It is not a substitute for an OEM finite-element or conjugate heat-transfer model.

## Governing equation

For every non-boundary node $i$, NeqSim solves

$$
C_i \frac{dT_i}{dt} = Q_i + \sum_j G_{ij}(T_j - T_i)
$$

where $C_i$ is the lumped heat capacity in J/K, $Q_i$ is local heat generation in W, and $G_{ij}$ is an effective thermal conductance in W/K. The steady-state solver sets the storage term to zero. The transient solver uses implicit Euler, which remains stable for time steps larger than the shortest thermal time constant, although accuracy still depends on time-step selection.

Bearing-loss heat can be supplied by `CompressorMechanicalLosses`. A configurable fraction of gas-compression power can be deposited at the impeller node. That fraction is a fitted parameter, not a universal physical constant.

## Catalog workflow

```java
CompressorCatalog catalog = CompressorCatalog.createDefaultCatalog();
compressor.initMechanicalLosses(120.0);
compressor.applyCatalogEntry(catalog, "generic-centrifugal-single-stage");
compressor.run();

double shaftTemperature = compressor.getThermalModel()
    .getTemperature(CompressorThermalModel.INLET_SHAFT, "C");
Map<String, Double> temperatures = compressor.getThermalModel().getTemperatureProfile("C");
```

`CompressorCatalog` is JSON-backed. It can hold generic templates or private machine definitions and records the machine-specific inputs required for calibration. Applying an entry makes an independent copy, so changing one compressor does not alter the catalog or another compressor.

The built-in catalog values are deliberately generic. Replace at least:

- thermal conductances, using OEM geometry, material data, heat-transfer analysis or fitted measurements;
- node heat capacities, using the participating metal mass and heat capacity;
- bearing losses, using vendor curves or an oil-side heat balance;
- seal-gas and lube-oil temperatures and flows;
- the fraction of compression power transferred to rotor metal.

## Condensate evaporation and sulfur-deposition screening

A shaft can be warmer than the suction gas because it is connected conductively to the impeller/rotor and receives heat from bearings, seals and the compressed gas. If entrained condensate wets this location, compare the calculated metal temperature with a NeqSim flash of a representative condensate at local pressure. A higher equilibrium vapor fraction at the metal temperature indicates an evaporation-driving force. Evaporation can concentrate dissolved elemental sulfur and other low-volatility material at the surface.

This comparison does not by itself predict a deposition rate. A defensible sulfur assessment also needs entrained liquid rate and droplet size, condensate composition, sulfur solubility versus pressure and temperature, residence time, mass transfer, surface temperature distribution, and chemical sulfur production. The thermal model exposes the metal-temperature input needed by that assessment.

## Literature basis and limitations

- [API Standard 617](https://www.api.org/~/media/files/publications/whats%20new/617_e8%20pa.pdf) defines the scope and minimum requirements for process centrifugal compressors. It is used here to identify relevant compressor and auxiliary-system boundaries; it does not provide the generic conductances in the templates.
- [API Standard 692](https://www.api.org/-/media/files/publications/2025-catalog/06_refining_2025.pdf) defines dry-gas sealing-system scope. It does not provide a universal seal-face or shaft-temperature correlation.
- Lo et al., [Parameter Estimation of the Thermal Network Model of a Machine Tool Spindle](https://doi.org/10.3390/s18020656), demonstrates a calibrated resistance/capacitance network with shaft, housing and bearing temperatures for both steady and transient prediction.
- Lin et al., [Experimental and Numerical Analysis of the Impeller Backside Cavity in a Centrifugal Compressor](https://doi.org/10.3390/en15020420), shows measured radial temperature gradients and operating-speed effects in a compressor impeller backside cavity. This supports retaining distinct impeller, casing and shaft nodes instead of assigning the discharge-gas temperature to all metal.
- Stahl et al., [Heat Transfer Effects on a Rotor-Centrifugal Compressor](https://gpps.global/wp-content/uploads/2021/02/GPPS-TC-2019_paper_54.pdf), compares heat-transfer models with experimental compressor data and illustrates that heat transfer affects rotor-compressor behavior.
- Ding et al., [Theoretical analysis and experiment on gas film temperature in a spiral groove dry gas seal](https://doi.org/10.1016/j.ijheatmasstransfer.2015.12.024), reports measured seal temperature distributions and shows why a seal location should not simply be assigned the supply-gas temperature.

The network is linear in conductance and uses one temperature per lumped node. Radiation, temperature-dependent properties, rotationally dependent convection, contact resistance, detailed oil-film behavior, spatial gradients within a node and droplet evaporation kinetics are not calculated unless the user represents them through fitted links, additional nodes or an external model.
