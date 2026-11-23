---
title: Naphthenate and Organic Acid Precipitation
description: "Equilibrium model for calcium naphthenate (ARN) deposits and carboxylate soaps from crude oil organic acids, with CO2-controlled pH and regression of parameters to bottle-test data."
keywords: "naphthenate, ARN, tetraacid, organic acids, TAN, calcium soap, carboxylate soap, pH, CO2, flow assurance, parameter fitting"
---

## Overview

Crude oils contain organic (naphthenic) acids. When the produced water pH rises, usually
because CO2 leaves the water as the pressure drops through the process, the acids dissociate at
the oil-water interface and react with cations in the water. Two problems follow:

| Problem | Acid | Product | Where |
|---------|------|---------|-------|
| Calcium naphthenate deposits | ARN tetraacids (C80, about 1230 g/mol), mg/kg levels | Hard, sticky Ca-ARN solid at the interface | Separators, hydrocyclones, desalters |
| Carboxylate soaps, sludge, tight emulsions | Monoprotic acids measured by TAN, 200-500 g/mol | Calcium soap CaA2 and dissolved sodium naphthenate | Separators, produced water |

`NaphthenatePrecipitationModel` (package `neqsim.pvtsimulation.flowassurance`) calculates the
distribution of both acid classes between oil, water and solid at equilibrium.
`NaphthenateModelFitting` regresses the model parameters to laboratory or field data.

## Model

$$\mathrm{HA(oil)} \rightleftharpoons \mathrm{HA(aq)} \qquad P = \frac{[\mathrm{HA}]_{oil}}{[\mathrm{HA}]_{aq}}$$

$$\mathrm{HA(aq)} \rightleftharpoons \mathrm{H^+ + A^-} \qquad K_a = \frac{a_{H^+}\,a_{A^-}}{[\mathrm{HA}]_{aq}}$$

$$\mathrm{CaA_2(s)} \rightleftharpoons \mathrm{Ca^{2+} + 2A^-} \qquad K_{sp} = a_{Ca^{2+}}\,a_{A^-}^2$$

$$\mathrm{H_4A(oil) + 2Ca^{2+}} \rightleftharpoons \mathrm{Ca_2A(s) + 4H^+} \qquad K_{ARN} = \frac{a_{H^+}^4}{[\mathrm{H_4A}]_{oil}\,a_{Ca^{2+}}^2}$$

$$\mathrm{CO_2(g) + H_2O} \rightleftharpoons \mathrm{H^+ + HCO_3^-} \qquad K_1 K_H = \frac{a_{H^+}\,a_{HCO_3^-}}{p_{CO_2}}$$

The monoprotic part has the structure of the model of Mohammed and Sorbie (2010). The ARN part is a
single interfacial reaction because ARN is practically insoluble in water. Ion activity
coefficients are from the Davies equation; $K_1$ and $K_H$ are from Plummer and Busenberg (1982).
$K_{sp}$ and $K_{ARN}$ can be given a van't Hoff temperature dependence.

The water pH is either

- **specified** (`setWaterPH`): a measured separator pH or a buffered bottle test, or
- **calculated from CO2** (`setCO2PartialPressure`): the bicarbonate alkalinity and the CO2
  partial pressure set the pH. Protons released by acid dissociation and salt formation consume
  alkalinity, so pH and precipitation are solved together. The four protons per ARN molecule make
  ARN precipitation very sharp in pH.

### Parameters and their defaults

| Parameter | Default | Basis |
|-----------|---------|-------|
| `PKA` | 4.9 | Measured for naphthenic acids in water (Havre et al., 2003) |
| `LOG_PARTITION_COEFFICIENT` | $4.0 + 0.010\,(M - 300)$ | Engineering default: a 300 g/mol acid is half extracted into an equal volume of water at about pH 9 |
| `PKSP_CALCIUM_SOAP` | 10.0 | Engineering default: soap onset near pH 6.8 for TAN 1 mg KOH/g, 1000 mg/L Ca, 0.5 mol/L ionic strength |
| `PK_ARN` | 14.4 | Engineering default: ARN onset near pH 6.0 for 10 mg/kg ARN, 1000 mg/L Ca |
| `CALCIUM_SOAP_DISSOLUTION_ENTHALPY`, `ARN_FORMATION_ENTHALPY` | 0 J/mol | No temperature dependence unless fitted |

Only pKa is a transferable physical constant. The other three depend on the acid structures of
the crude; their defaults are anchored to typical field onset behaviour, not taken from a
publication. With defaults the model ranks cases and shows sensitivities (pH, calcium, water cut,
CO2 pressure). Quantitative amounts require regression to data. `isCalibrated()` reports which
situation applies and an uncalibrated run carries a warning in `getWarnings()` and `toJson()`.

The solubility products of crystalline fatty-acid calcium soaps are not usable for `PKSP_CALCIUM_SOAP`:
they predict complete precipitation at any produced-water pH, because real naphthenic calcium
soaps are partly oil soluble.

## Predictive use

<!-- naphthenate-screening:start -->
```java
NaphthenatePrecipitationModel model = new NaphthenatePrecipitationModel();
model.setWaterCut(0.5);                               // 1 m3 liquid, 50 % water
model.setOilDensity(850.0);                           // kg/m3
model.setTotalAcidNumber(1.0);                        // mg KOH/g
model.setAcidMolarMass(300.0);                        // g/mol
model.setArnConcentration(10.0);                      // mg/kg oil
model.setWaterComposition(1000.0, 10000.0, 500.0);    // Ca, Na, HCO3- in mg/L
model.setTemperature(333.15);                         // K

double arnOnsetPH = model.calculateArnOnsetPH();              // about 6.0
double soapOnsetPH = model.calculateCalciumSoapOnsetPH();     // about 6.8

// First-stage separator: 5 bara CO2 partial pressure
model.setCO2PartialPressure(5.0);
model.run();
double pHHighPressure = model.getWaterPH();                   // about 5.2, NO_RISK

// Low-pressure separator: 0.2 bara CO2 partial pressure
model.setCO2PartialPressure(0.2);
model.run();
double pHLowPressure = model.getWaterPH();                    // about 6.5
NaphthenatePrecipitationModel.NaphthenateRisk risk = model.getRisk();   // ARN_DEPOSIT_RISK
double arnDeposit = model.getPrecipitatedArnNaphthenate();    // kg per m3 oil
double soap = model.getPrecipitatedCalciumSoap();             // kg per m3 oil
double dissolvedAcid = model.getAqueousOrganicAcidConcentration();   // mg/L in the water
String json = model.toJson();
```
<!-- naphthenate-screening:end -->

`getRisk()` returns `NO_RISK` (more than 0.5 pH units below every onset), `LOW_RISK` (within 0.5
units), `SOAP_RISK` (calcium soap supersaturated) or `ARN_DEPOSIT_RISK` (ARN salt supersaturated).

The CO2 partial pressure and bicarbonate can be taken from a NeqSim process simulation (CO2 mole
fraction in the separator gas times pressure). The pH route here is a carbonate-only estimate;
for brines with significant organic-acid anions (acetate) or H2S use a measured pH or a pH from the
electrolyte models and pass it with `setWaterPH`.

## Calibration to data

Each observation is one experiment: a model holding the experiment conditions, the measured
quantity, and its standard deviation.

| `ObservationType` | Unit | Determines |
|-------------------|------|------------|
| `AQUEOUS_ORGANIC_ACID` | mg/L | pKa + log P (partition tests without calcium) |
| `PRECIPITATED_CALCIUM_SOAP` | kg/m3 oil | pKsp of the calcium soap |
| `CALCIUM_SOAP_ONSET_PH` | pH | pKsp of the calcium soap |
| `PRECIPITATED_ARN_FRACTION` | 0-1 | pK of ARN |
| `ARN_ONSET_PH` | pH | pK of ARN |
| `WATER_PH` | pH | Consistency of the CO2 pH route |

<!-- naphthenate-fitting:start -->
```java
NaphthenatePrecipitationModel bottleTest = new NaphthenatePrecipitationModel();
bottleTest.setWaterCut(0.5);
bottleTest.setOilDensity(850.0);
bottleTest.setTotalAcidNumber(1.0);
bottleTest.setAcidMolarMass(300.0);
bottleTest.setArnConcentration(10.0);
bottleTest.setWaterComposition(1000.0, 10000.0, 500.0);
bottleTest.setTemperature(333.15);

NaphthenateModelFitting fitting = new NaphthenateModelFitting();

// Soap mass (kg/m3 oil) measured at three buffered pH values, standard deviation 0.1 kg/m3
double[] testPH = {7.5, 8.0, 8.5};
double[] soapMass = {2.6, 3.9, 4.4};
for (int i = 0; i < testPH.length; i++) {
  bottleTest.setWaterPH(testPH[i]);
  fitting.addObservation(bottleTest, NaphthenateModelFitting.ObservationType.PRECIPITATED_CALCIUM_SOAP,
      soapMass[i], 0.1);
}
// ARN deposit first seen at pH 6.3 +/- 0.1
fitting.addObservation(bottleTest, NaphthenateModelFitting.ObservationType.ARN_ONSET_PH, 6.3, 0.1);

fitting.addFitParameter(NaphthenatePrecipitationModel.Parameter.PKSP_CALCIUM_SOAP);
fitting.addFitParameter(NaphthenatePrecipitationModel.Parameter.PK_ARN);
fitting.solve();

double pKsp = fitting.getFittedValue(NaphthenatePrecipitationModel.Parameter.PKSP_CALCIUM_SOAP);
double pKspError = fitting.getStandardError(NaphthenatePrecipitationModel.Parameter.PKSP_CALCIUM_SOAP);
double fitQuality = fitting.getRootMeanSquareWeightedResidual();   // near 1 = within stated uncertainty

// Use the fitted parameters for field predictions
NaphthenatePrecipitationModel field = bottleTest.copy();
fitting.applyTo(field);                       // also marks the model as calibrated
field.setCO2PartialPressure(0.2);
field.run();
```
<!-- naphthenate-fitting:end -->

`addObservation` copies the scenario, so one model can be reused with changed conditions.

Fit only parameters the data can determine:

- pKa and log P enter as a sum as long as the water acid is mostly undissociated relative to the
  oil inventory; fit one and keep the other fixed.
- pKsp of the soap is conditional on pKa and log P. Fit the partition first when partition data exist.
- The enthalpies need data at two or more temperatures.
- A parameter the data do not determine returns `NaN` from `getStandardError`.

## Limitations

- Equilibrium only: no interfacial kinetics, no deposition rate, no effect of shear or residence time.
  The precipitated mass is the potential per volume of oil; multiply by the oil rate for an upper
  bound on the deposit formation rate.
- One lumped monoprotic acid. A crude with a wide acid distribution may need separate runs for
  light and heavy acid fractions.
- Only calcium forms solids. Magnesium, barium and iron soaps and sodium soap as a separate phase
  are not included; dissolved naphthenate (`getAqueousOrganicAcidConcentration`) is the indicator for
  sodium-soap emulsion tendency.
- Davies activity coefficients are approximate above 0.5 mol/L ionic strength (a warning is issued).
- The default parameters are engineering defaults. The implementation is verified by
  `NaphthenatePrecipitationModelTest` (carbonate constants at 25 °C, mass balance, monotonic pH
  response, recovery of known parameters from synthetic data, 2026-11-23). It has not been compared
  with published bottle-test or field data sets.

## References

- Mohammed, M.A., Sorbie, K.S. (2010). Thermodynamic modelling of calcium naphthenate formation: model predictions and experimental results. Colloids and Surfaces A, 369, 1-10.
- Havre, T.E., Sjöblom, J., Vindstad, J.E. (2003). Oil/water-partitioning and interfacial behavior of naphthenic acids. Journal of Dispersion Science and Technology, 24(6), 789-801.
- Sjöblom, J., Simon, S., Xu, Z. (2014). The chemistry of tetrameric acids in petroleum. Advances in Colloid and Interface Science, 205, 319-338.
- Turner, M.S., Smith, P.C. (2005). Controls on soap scale formation, including naphthenate soaps - drivers and mitigation. SPE 94339.
- Plummer, L.N., Busenberg, E. (1982). The solubilities of calcite, aragonite and vaterite in CO2-H2O solutions between 0 and 90 °C. Geochimica et Cosmochimica Acta, 46, 1011-1040.

## Related

- [Asphaltene modeling](asphaltene_modeling)
- [Mineral scale formation](../mineral_scale_formation)
- [Emulsion viscosity](emulsion_viscosity_calculator)
