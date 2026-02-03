---
title: Fire and blowdown calculation enhancements
description: This note summarizes how to extend NeqSim blowdown calculations with rigorous fire exposure models.
---

# Fire and blowdown calculation enhancements

This note summarizes how to extend NeqSim blowdown calculations with rigorous fire exposure models.

## Heat transfer modelling (wetted vs. unwetted)
- Use the `FireHeatTransferCalculator` utility to compute inner/outer wall temperatures with
  separate film coefficients for wetted and unwetted regions. The calculator solves a 1-D thermal
  resistance network so that external fire temperatures and internal boiling/convective coefficients
  can be applied consistently. Wetted and dry areas can be pulled directly from a `Separator` via
  `getWettedArea()` / `getUnwettedArea()`, so dynamic level changes during blowdown feed into the
  fire heat flux sizing automatically.
- Representative coefficients:
  - Wetted zones: high internal film coefficients (nucleate boiling/forced convection)
  - Unwetted zones: lower internal coefficients to reflect vapor-side natural convection
- The `SurfaceTemperatureResult` object reports heat flux and both metal surface temperatures so
  material-strength checks can be driven by actual wall metal temperature.

## Fire heat loads
- **Legacy API 521 pool fire**: `FireHeatLoadCalculator.api521PoolFireHeatLoad(wettedArea, F)` gives
  the classic correlation in Watts using the 6.19e6·F·A^0.82 metric form.
- **Generalized Stefan–Boltzmann**: `FireHeatLoadCalculator.generalizedStefanBoltzmannHeatFlux(...)`
  provides radiative heat flux using emissivity and view/configuration factors. Combine with
  convective terms for jet fires or shielding adjustments.
- **Live flare radiation**: `SeparatorFireExposure.evaluate(config, flare, distanceM)` can fold in the
  actual flare heat duty and radiant fraction (via `Flare.estimateRadiationHeatFlux`) at a specified
  horizontal distance so you can compare environmental fire sizing vs. radiation from the burning
  gas itself.

## Vessel rupture assessment (Scandpower guideline)
- Use `VesselRuptureCalculator.vonMisesStress(P, r, t)` to derive von Mises stress from hoop and
  axial components for thin-walled vessels.
- Compare against material allowable tensile strength via `ruptureMargin` or
  `isRuptureLikely` to flag impending rupture. Feeding these checks with the metal temperatures from
  the heat-transfer calculator allows temperature-dependent strength curves to be implemented later.

These utilities are designed to plug into existing blowdown scenarios and flare models so that
transient depressurization can be tracked alongside external fire loads and structural integrity.
The helper `SeparatorFireExposure.evaluate(...)` (or `separator.evaluateFireExposure(...)`) wraps
area lookup, heat-load estimation, wall temperatures, and rupture checks into a single call so the
fire calculations can be dropped into a process simulation loop without hand-wiring each piece.
If you want the separator inventory to warm up from the calculated fire load, set the duty on the
separator (`separator.setDuty(fireResult.totalFireHeat())`) and call `separator.runTransient(...)`
so the energy balance absorbs that heat during the timestep.

## Separator fire blowdown worked example
The runnable `SeparatorFireDepressurizationExample` (`src/main/java/neqsim/process/util/example/SeparatorFireDepressurizationExample.java`)
illustrates how to couple a separator depressurization to the flare with the fire utilities:
- Dynamic separator blowdown via `BlowdownValve` + `Orifice` feeding a `Flare`
- API 521 pool-fire loads plus generalized Stefan–Boltzmann radiative flux
- Wetted vs. unwetted wall temperatures from `FireHeatTransferCalculator`
- Von Mises rupture margin from `VesselRuptureCalculator`
- Optional separator temperature rise using `separator.setDuty(fireResult.totalFireHeat())`

To run the illustration:
```bash
mvn -pl . -Dexec.mainClass="neqsim.process.util.example.SeparatorFireDepressurizationExample" exec:java
```
The output prints separator pressure, flow to flare, wall temperatures, rupture margin, and fire
heat metrics at each timestep so the fire impact on depressurization can be reviewed end-to-end.
