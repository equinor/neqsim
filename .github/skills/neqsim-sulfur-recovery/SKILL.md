---
name: neqsim-sulfur-recovery
description: "Guides NeqSim agents through sulfur-recovery models anchored on SulfurRecoveryUnit, SulfurRecoveryProcessBuilder, ClausReactionFurnace, ClausCatalyticConverter, and SulfurRecoveryPerformance. USE WHEN: building a Claus sulfur-recovery train, choosing straight-through, split-flow, or oxygen-enriched operation, adding tail-gas treatment and recycle, checking sulfur balance or recovery, tuning furnace/converter inputs, or estimating sulfur product and stack SO2."
last_verified: "2026-10-03"
---

# Sulfur Recovery with NeqSim

## When to use this

- Build an integrated Claus sulfur-recovery process from an acid-gas stream.
- Compare straight-through, split-flow, and oxygen-enriched configurations.
- Select one to three catalytic stages, add condensers, tail-gas treatment, or incineration.
- Inspect elemental sulfur product, sulfur balance, H2S/SO2 ratio, oxygen demand, and convergence.
- Study furnace, converter, sulfur-condensing, or sub-dew-point equipment independently.
- Calibrate reduced kinetic settings against vendor or plant data before using results for rating.

This is an integrated process-screening model. It is not a licensor guarantee, detailed furnace CFD,
catalyst-vendor rating, emissions permit determination, or mechanical design package.
`ClausReactionFurnace` defaults to a reduced kinetic mechanism. Its `EQUILIBRIUM` option uses the
NeqSim Gibbs reactor as a chemistry kernel; that does not make the complete train a validated
equilibrium Claus design.

## Class map

| Class | Package | What it does | Verified key setters/getters |
|---|---|---|---|
| `SulfurRecoveryProcessBuilder` | `neqsim.process.equipment.reactor.sulfurrecovery` | Configures integrated train topology | `catalyticStages(int)`, `tailGasTreatment(boolean)`, `incinerator(boolean)`, `configuration(Configuration)`, `build()` |
| `SulfurRecoveryUnit` | same | Runs the integrated SRU, air control, and optional tail-gas recycle | `setConfiguration(Configuration)`, `setNumberOfCatalyticStages(int)`, `getPerformance()`, `getCombinedSulfurProductStream()` |
| `SulfurRecoveryPerformance` | same | Immutable run KPIs | `getSulfurRecoveryPercent()`, `getOverallSulfurRecoveryPercent()`, `getSulfurBalanceRelativeError()`, convergence flags |
| `ClausReactionFurnace` | same | Furnace chemistry and energy calculation | `setModelMode(ModelMode)`, `setEnergyMode(EnergyMode)`, `setResidenceTime(double)`, `getFurnaceTemperature()` |
| `ClausCatalyticConverter` | same | Finite-rate Claus reaction and COS/CS2 hydrolysis | `setCatalystType(CatalystType)`, `setGasHourlySpaceVelocity(double)`, conversion getters |
| `SulfurCondenser` | same | Cools gas and removes elemental sulfur | `setOutletTemperature(double,String)`, `getCondensedSulfur(String)`, `getHeatDuty(String)` |
| `ReactiveWasteHeatBoiler` | same | Reacting waste-heat cooling and steam estimate | `setOutletTemperature(double,String)`, `getHeatDuty(String)`, `getSteamProduction(String)` |
| `SubDewPointSulfurReactor` | same | Adsorption/regeneration sulfur-bed model | `setBedMode(BedMode)`, `setSulfurCapacity(double,String)`, `getStoredSulfur(String)` |
| `TailGasTreatmentUnit` | same | Hydrogenation/hydrolysis, H2S absorption, acid-gas recycle | `setReactorTemperature(double,String)`, `getAcidGasRecycleStream()`, `getHydrogenationConversion()` |
| `ThermalIncinerator` | same | Oxidizes sulfur compounds in tail gas | `setTemperature(double,String)`, `setResidenceTime(double)`, `getSo2Emission(String)` |
| `AirDemandController` | same | Calculates/feedback-trims oxidant demand | `calculateOxygenDemand(SystemInterface)`, `setTargetH2SToSO2Ratio(double)` |

All rows are in the `neqsim.process.equipment.reactor.sulfurrecovery` package. Getters in
`SulfurRecoveryPerformance` include units in their names where applicable; see Result extraction.

## Build pattern

This is the tested straight-through, two-catalytic-stage pattern. Stream temperature and pressure
constructor values are K and bara; component amounts are relative mole amounts.

```java
import neqsim.process.equipment.reactor.sulfurrecovery.SulfurRecoveryProcessBuilder;
import neqsim.process.equipment.reactor.sulfurrecovery.SulfurRecoveryUnit;
import neqsim.process.equipment.reactor.sulfurrecovery.SulfurRecoveryPerformance;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface fluid = new SystemSrkEos(313.15, 2.0);
fluid.addComponent("H2S", 10.0);
fluid.addComponent("CO2", 2.0);
fluid.addComponent("methane", 0.05);
fluid.addComponent("water", 1.0);
fluid.setMixingRule("classic");

Stream acidGas = new Stream("acid gas", fluid);
acidGas.setFlowRate(1000.0, "kg/hr");
acidGas.run();

SulfurRecoveryUnit unit = new SulfurRecoveryProcessBuilder("SRU", acidGas)
    .catalyticStages(2)
    .incinerator(true)
    .build();
unit.run();
SulfurRecoveryPerformance performance = unit.getPerformance();
double recoveredKgPerHour = performance.getRecoveredSulfurKgPerHour();
double recoveryPercent = performance.getSulfurRecoveryPercent();
```

Equivalent Python class lookup pattern:

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
Builder = jneqsim.process.equipment.reactor.sulfurrecovery.SulfurRecoveryProcessBuilder

fluid = SystemSrkEos(313.15, 2.0)
fluid.addComponent("H2S", 10.0)
fluid.addComponent("CO2", 2.0)
fluid.addComponent("methane", 0.05)
fluid.addComponent("water", 1.0)
fluid.setMixingRule("classic")
acid_gas = Stream("acid gas", fluid)
acid_gas.setFlowRate(1000.0, "kg/hr")
acid_gas.run()

unit = Builder("SRU", acid_gas).catalyticStages(2).incinerator(True).build()
unit.run()
performance = unit.getPerformance()
recovery_percent = performance.getSulfurRecoveryPercent()
```

To add tail-gas treatment, set `.tailGasTreatment(true)` on the builder. This introduces a recycle;
read `performance.isRecycleConverged()` as well as `performance.isAirControlConverged()`. The
builder also exposes `.configuration(SulfurRecoveryUnit.Configuration.OXYGEN_ENRICHED)` and
`.configuration(SulfurRecoveryUnit.Configuration.SPLIT_FLOW)`; split-flow fraction and oxygen
fraction can be set on the builder before `build()`.

## Result extraction

| Getter | Meaning and units |
|---|---|
| `getFeedSulfurKgPerHour()` | Fresh-feed sulfur rate, kg/h |
| `getRecoveredSulfurKgPerHour()` | Condensed elemental sulfur product, kg/h |
| `getSulfurRecoveryPercent()` | Claus section recovery relative to fresh-feed sulfur, % |
| `getOverallSulfurRecoveryPercent()` | Recovery including sulfur captured by tail-gas treatment, % |
| `getSulfurBalanceRelativeError()` | Relative sulfur-atom closure error, dimensionless |
| `getTailGasH2SToSO2Ratio()` | Final Claus tail-gas molar ratio, dimensionless |
| `getOxygenDemandMoles()` | Oxygen demand on stream molar-flow basis |
| `getFurnaceTemperatureK()` | Furnace outlet temperature, K |
| `getStackSO2KgPerHour()` | Incinerator stack SO2 rate, kg/h |
| `isAirControlConverged()` / `isRecycleConverged()` | Separate control-loop status flags |

Equipment-level outputs include `SulfurCondenser.getCondensedSulfur(String)` and
`getHeatDuty(String)`, `ReactiveWasteHeatBoiler.getSteamProduction(String)`,
`TailGasTreatmentUnit.getAbsorbedH2SMoles()`, and `ThermalIncinerator.getSo2Emission(String)`.
Use the unit strings supported by each getter; do not infer units from an unlabelled scalar.

## Gotchas

- The default furnace model is `REDUCED_KINETIC`, not equilibrium. Rate constants, residence time,
  catalyst activity, and calibration factors materially affect conversion. Fit calibration factors
  to plant/vendor data before treating output as a design prediction.
- In `ClausReactionFurnace.ModelMode.EQUILIBRIUM`, source sets `getH2SOxidizedMoles()` to
  `Double.NaN`; do not put this output into KPI arithmetic without checking the mode.
- Check both air-control and recycle convergence flags. A `run()` call completing is not proof that
  either iteration met its requested tolerance.
- Use Kelvin and bara for fluid construction. Equipment setters that take a unit string accept
  units explicitly; `setResidenceTime` uses seconds and space velocity uses 1/h.
- Set a thermodynamic mixing rule before flashing. The tested feed uses SRK with `"classic"`.
  Run the feed stream before constructing/running the train, as the tests do.
- Provide the expected acid-gas species (including water and relevant sulfur species) at usable
  levels. Trace constituents and the selected stream basis affect air demand and closure.
- `SulfurRecoveryUnit.getSulfurRecoveryPercent()` is Claus-only; use
  `getOverallSulfurRecoveryPercent()` when tail-gas capture is enabled.
- Sub-dew-point sulfur storage is a simplified bed inventory model, not cyclic adsorption-bed
  thermal design. `SulfurCondenser` is a process screening model, not a detailed condenser rating.

## Validation / benchmarks

- Close sulfur atoms around the complete train. The end-to-end test requires
  `abs(getSulfurBalanceRelativeError()) < 1e-5` and recovery no greater than approximately 100%.
- For a conventional case, check final Claus H2S/SO2 ratio against the air-control target; the test
  targets 2.0 with a 0.05 tolerance.
- Check the outlet temperatures, pressure drops, non-negative sulfur product, and both convergence
  flags. Compare independent stream sulfur flows, not just the performance summary.
- As contextual targets only, Claus-only recovery is commonly about 94-98% across two- to
  three-stage configurations; dedicated tail-gas treatment can target above 99.5%. Actual results
  depend on configuration, feed, catalyst, and licensor design. References: GPSA Engineering Data
  Book sulfur-recovery discussion and Sulphur Experts Claus/TGTU technical guidance. These are not
  NeqSim acceptance guarantees or standard clauses.
- The Java regression tests establish material-balance and trend behavior for representative feeds;
  they do not establish a general plant-performance benchmark or emissions compliance.

## Related skills

- `neqsim-process-modeling` — assemble and validate the surrounding process flowsheet.
- `neqsim-reaction-engineering` — Gibbs/equilibrium and reaction-model context.
- `neqsim-hydrogen-production` — PSA and sulfur-poisoning context in hydrogen chains.
- `neqsim-api-patterns` — fluid setup, units, and property initialization.
- `neqsim-input-validation` — screen composition, units, and physical input bounds.
- `neqsim-troubleshooting` — investigate convergence and flash failures.
- `neqsim-flow-assurance` — sulfur deposition is a separate downstream concern.
- `neqsim-acid-gas-treating` — upstream H2S/CO2 removal and acid-gas feed preparation.