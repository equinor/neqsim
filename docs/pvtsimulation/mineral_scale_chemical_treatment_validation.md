---
title: "High-Salinity Mineral Scale and Production-Chemical Validation"
description: "Pitzer activity validation, coupled mineral precipitation, pH-stabiliser and H2S-scavenger scenarios, and root-cause-analysis guidance."
---

# High-Salinity Mineral Scale and Production-Chemical Validation

This workflow combines five distinct engineering questions. Keeping the layers separate is essential: a scale inhibitor
changes nucleation and growth, while pH adjustment changes carbonate thermodynamics, and an H2S scavenger consumes a
reactive contaminant and may create a non-mineral spent product.

| Layer | NeqSim class | Result |
|---|---|---|
| Aqueous screening chemistry | `ScalePredictionCalculator` | Free-ion concentrations and T/P-corrected Ksp |
| High-salinity activity | `PitzerScaleActivityModel` | Ion-specific activity coefficients for NaCl-dominated brines |
| Competing minerals | `MultiMineralScaleEquilibrium` | Coupled BaSO4, SrSO4, CaSO4, CaCO3 and FeCO3 precipitation |
| Chemical treatment | `ProductionChemicalScaleScenario` | Before/after pH, dissolved H2S and thermodynamic SI |
| Explainable diagnosis | `RootCauseAnalyser` | Ranked treatment-induced and untreated root-cause hypotheses |

## Choosing the activity model

`MultiMineralScaleEquilibrium.ActivityModel` provides three screening choices:

| Model | Recommended use | Important limitation |
|---|---|---|
| `DAVIES` | Dilute brines, approximately I <= 0.5 mol/kg | Diverges at oilfield-brine salinity |
| `BDOT` | Intermediate screening and comparison | Empirical common divalent coefficient; not ion-specific |
| `PITZER_BINARY` | NaCl-dominated high-salinity brines | Binary/equivalent-I trace-ion mapping, not the full theta/psi mixture model |

For compositionally complex brines, benchmark the screening result against PHREEQC with its Pitzer database or against
a fully speciated NeqSim electrolyte-EOS calculation. Do not label `PITZER_BINARY` as full multicomponent Pitzer.

```java
ScalePredictionCalculator water = new ScalePredictionCalculator();
water.setTemperatureCelsius(80.0);
water.setPressureBara(150.0);
water.setSodiumConcentration(70000.0);   // mg/L
water.setCalciumConcentration(3000.0);
water.setBariumConcentration(200.0);
water.setSulphateConcentration(1000.0);
water.setBicarbonateConcentration(500.0);
water.setTotalDissolvedSolids(150000.0);
water.setPH(7.0);

MultiMineralScaleEquilibrium equilibrium = new MultiMineralScaleEquilibrium(water)
    .setActivityModel(MultiMineralScaleEquilibrium.ActivityModel.PITZER_BINARY)
    .setPitzerIonicStrengthMolal(3.0) // density-corrected laboratory value, when available
    .solve();
```

The solver reports the coefficient used for each ion, each mineral's cation-anion activity-coefficient product, the
initial SI, equilibrium SI, precipitated mass, and residual shared-ion inventory.

## Published-data activity benchmark

The regression dataset contains all 29 NaCl points from Hamer and Wu (1972), Table 16, from 0.001 to 6.0 mol/kg at 25
degrees C. The test evaluates the temperature-dependent PHREEQC binary parameters without fitting them to the benchmark.

| Model | RMSE in mean activity coefficient, m >= 0.5 mol/kg |
|---|---:|
| Pitzer binary | 0.000962 |
| B-dot defaults | 0.05049 |
| Davies | 1.00446 |

Across all 29 points, Pitzer mean absolute error is 0.000707 and maximum absolute error is 0.00336. The data are stored
in `src/test/resources/neqsim/pvtsimulation/flowassurance/nacl_mean_activity_hamer_wu_1972.csv` and exercised by
`PitzerScaleActivityModelTest`. Existing `ScaleKspLiteratureBenchmarkTest` separately checks the mineral solubility
correlations, including the calcite value log10(Ksp) = -8.48 at 25 degrees C.

Primary sources:

- [Pitzer (1973), theoretical basis and equations](https://doi.org/10.1021/j100621a026)
- [Hamer and Wu (1972), critically evaluated electrolyte activity data](https://doi.org/10.1063/1.3253108)
- [USGS PHREEQC Pitzer model documentation](https://water.usgs.gov/water-resources/software/PHREEQC/documentation/phreeqc3-html/phreeqc3-37.htm)
- [USGS PHREEQC `pitzer.dat` parameter database](https://github.com/phreeqc-dev/phreeqc3/blob/master/database/pitzer.dat)
- [Plummer and Busenberg (1982), carbonate equilibria and calcite solubility](https://doi.org/10.1016/0016-7037(82)90056-4)

## Production-chemical scenarios

```java
ProductionChemicalScaleScenario scenario = new ProductionChemicalScaleScenario()
    .setTemperatureCelsius(60.0)
    .setPressureBara(50.0)
    .setPH(6.5)
    .setCalciumMgL(2500.0)
    .setSodiumMgL(50000.0)
    .setSulphateMgL(500.0)
    .setBicarbonateMgL(1000.0)
    .setTotalDissolvedSolidsMgL(100000.0)
    .setDissolvedH2SMgL(100.0)
    .setActivityModel(MultiMineralScaleEquilibrium.ActivityModel.PITZER_BINARY)
    .addChemical(ProductionChemical.phStabilizer("MDEA", 1000.0))
    .addChemical(ProductionChemical.h2sScavenger("MEA-triazine", 1000.0))
    .addChemical(ProductionChemical.scaleInhibitor("phosphonate", 20.0));
scenario.evaluate();
```

The pH treatment solves a closed aqueous carbonate-alkalinity balance using active-product equivalents. NaOH, sodium
carbonate, MDEA, MEA and HCl have explicit equivalent models. The H2S calculation uses active mass and practical
stoichiometry for MEA/MMA triazine, iron and glyoxal. MEA-triazine also raises a spent-product/dithiazine evidence flag,
consistent with the experimentally established stepwise H2S reaction chemistry.

The calculation does **not** infer a pH shift from an H2S scavenger's neat-product pH. Commercial formulations and
reaction products are vendor-specific. An unrecognised active remains in the audit report and raises an unsupported-model
warning rather than receiving an assumed effect.

For a qualified proprietary product, override the family default with
`setH2SCapacityKgPerKgActive(...)` or `setAlkalinityCapacityMolEqPerKgActive(...)`. These values are included in the
JSON audit trail.

Primary treatment sources:

- [Wylde et al. (2020), temperature- and pH-dependent MEA-triazine reaction kinetics](https://doi.org/10.1021/acs.energyfuels.0c01402)
- [Raman/DFT study of the H2S reaction of MEA-triazine](https://doi.org/10.1021/acs.iecr.1c00852)
- [Reaction mechanism and dithiazine products](https://doi.org/10.1021/acsomega.2c08103)

## Inhibitor semantics

`ScaleControlAssessor` now calls its empirical treatment output a `kineticRiskIndex`. The thermodynamic SI remains
available through `getThermodynamicSaturationIndex` and is unchanged by inhibitor dose. Use MIC, dynamic tube-blocking,
static bottle and vendor qualification data to decide whether a particular inhibitor controls a supersaturated brine.

## Root-cause evidence

Attach the evaluated or configured scenario to the RCA:

```java
RootCauseAnalyser rca = new RootCauseAnalyser();
rca.setChemicalTreatmentScenario(scenario);
rca.addSymptom(new Symptom(Symptom.Category.DEPOSIT,
    "Deposit downstream of pH stabiliser and scavenger injection"));
rca.analyse();
```

The analyser can add three explainable candidates:

- `CHEMICAL_INDUCED_CARBONATE_SCALE` when base dosing materially increases calcite SI;
- `H2S_SCAVENGER_UNDER_CAPACITY` when dissolved load exceeds active stoichiometric capacity;
- `SCAVENGER_SPENT_PRODUCT_DEPOSIT` when a triazine treatment and deposit symptom coexist.

These are competing hypotheses, not automatic conclusions. Close the RCA with deposit XRD/SEM/organic analysis,
treated-water pH and alkalinity, actual chemical active concentration, injection history, temperature profile and water
mixing history.
