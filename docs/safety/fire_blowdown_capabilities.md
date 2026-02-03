---
title: Fire/blowdown helper capabilities
description: This note summarizes the current fire, heat-transfer, and structural integrity helpers available in NeqSim and how to apply them in process simulations.
---

# Fire/blowdown helper capabilities

This note summarizes the current fire, heat-transfer, and structural integrity helpers available in NeqSim and how to apply them in process simulations.

## Fire heat-load modelling
- **API 521 pool fire:** `FireHeatLoadCalculator.api521PoolFireHeatLoad(wettedArea, F)` returns the classic total heat input (W) using wetted area and environment factor.
- **Generalized Stefan–Boltzmann radiation:** `FireHeatLoadCalculator.generalizedStefanBoltzmannHeatFlux(...)` calculates radiative heat flux (W/m²) from emissivity, view/configuration factor, and flame temperature so callers can compose pool or jet fire scenarios and include shielding/angle effects.

## Heat-transfer / wall-temperature treatment
- `FireHeatTransferCalculator` solves a steady 1-D wall model for wetted and unwetted regions using caller-supplied internal/external film coefficients.
- `SurfaceTemperatureResult` reports inner/outer metal temperatures and heat flux for each region so process models can track thermal response during depressurization.

## Structural integrity / rupture logic
- `VesselRuptureCalculator` provides thin-wall von-Mises stress plus helpers to compute rupture margin or boolean likelihood when allowable tensile strength is supplied (optionally temperature-dependent when paired with wall temperatures from the heat-transfer step).

## Integration with process equipment
- Separators expose wetted and dry surface areas (`getWettedArea()`, `getUnwettedArea()`), allowing fire heat loads to follow liquid level during blowdown.
- `SeparatorFireExposure.evaluate(...)` (also available via `separator.evaluateFireExposure(...)`) wraps area lookup, heat-load estimation, wall temperatures, and rupture checks into a single call to simplify use inside dynamic process simulations.
- Fire heat input is based on the separator’s geometry and process temperature, not on how much gas is flowing to the flare; flare-rate changes will not alter the radiative or pool-fire heat loads unless they change level/area or temperature.
- Apply fire heat as a separator duty via `separator.setDuty(fireResult.totalFireHeat())`; the separator’s `runTransient` call will consume that duty in its energy balance so the process temperature responds to fire loading without manual temperature edits.
- The runnable `SeparatorFireDepressurizationExample` demonstrates end-to-end use by routing a separator blowdown to a flare while evaluating fire loads, wall temperatures, and rupture margin over time.
