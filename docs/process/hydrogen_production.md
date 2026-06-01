---
title: Hydrogen Production with NeqSim
description: Modeling guide for hydrogen production routes — SMR fired reformers, ATR and POX syngas generators, blue-H2 WGS/capture/compression chains, pressure-swing adsorption (PSA), and water electrolysis. Covers ReformerFurnace, CatalyticTubeReformer, WaterGasShiftReactor, AutothermalReformer, PartialOxidationReactor, PSACascade, BlueHydrogenPlantBuilder, Electrolyzer, CAPEX estimation, para/ortho hydrogen corrections, and catalyst deactivation screening.
---

# Hydrogen Production with NeqSim

NeqSim covers thermochemical and electrochemical hydrogen production routes natively:

1. **Grey / blue H₂** — steam methane reforming (SMR), autothermal reforming
  (ATR), or partial oxidation (POX), followed by optional water-gas shift,
  **pressure-swing adsorption (PSA)** purification, and downstream CO₂ capture.
2. **Green / pink H₂** — water electrolysis using PEM, alkaline, SOEC, or AEM
  stacks.

This page documents the hydrogen-production route models, PSA cascade and PSA
CAPEX additions, electrolysis stack models, and foundation utilities for
cryogenic H₂ spin-isomer corrections and catalyst life screening. Companion
guides: [CO₂ injection well analysis](co2_injection_well_analysis.md) and
[reaction engineering](../chemicalreactions/index.md) reformer kinetics.

## Applicable standards

| Standard | Scope |
|---|---|
| ISO 14687 | Hydrogen fuel quality (PEM-FC grade ≥ 99.97% H₂) |
| ISO 22734 | Industrial water electrolyzers — safety |
| IEC 62282 | Fuel cell technologies (electrochemistry conventions) |
| API 941 | Steels for H₂ service (Nelson curves) |
| ASME B31.12 | Hydrogen piping and pipelines |
| EIGA Doc 121 | H₂ generator design (SMR / electrolysis) |
| IRENA 2022 | Green H₂ cost benchmarks |
| IEA Global H₂ Review 2023 | Capacity factors, USD/kW benchmarks |

## Core classes

| Class | Package | Purpose |
|---|---|---|
| `CatalyticTubeReformer` | `neqsim.process.equipment.reactor` | Tube-side SMR equilibrium model with duty, pressure-drop, tube-wall, heat-flux, and catalyst activity screening |
| `ReformerFurnace` | `neqsim.process.equipment.reactor` | Fired SMR furnace coupling combustion heat from `FurnaceBurner` to catalytic tube reformer duty |
| `SyngasBurnerZone` | `neqsim.process.equipment.reactor` | Oxygen-blown ATR/POX burner-zone model with O₂/C envelope and flame-temperature screening |
| `AutothermalReformer` | `neqsim.process.equipment.reactor` | ATR template with O₂/C and S/C controls, burner zone, catalytic equilibrium zone, and soot-risk metric |
| `PartialOxidationReactor` | `neqsim.process.equipment.reactor` | POX template with O₂/C control, refractory-temperature warning, fast-quench section, and H₂/CO metric |
| `QuenchSection` | `neqsim.process.equipment.reactor` | Rapid syngas cooling model with heat removed and quench severity outputs |
| `WaterGasShiftReactor` | `neqsim.process.equipment.reactor` | HT/LT WGS equilibrium wrapper with CO conversion, H2 gain, CO2 formation, duty, and WGS ratio reporting |
| `ComponentCaptureUnit` | `neqsim.process.equipment.splitter` | Selective component capture placeholder for CO2 capture, H2 drying, and other screening separations |
| `PressureSwingAdsorptionBed` | `neqsim.process.equipment.adsorber` | H₂-tuned single PSA bed (AC or Zeolite 13X) |
| `PSACascade` | `neqsim.process.equipment.adsorber` | Multi-bed Skarstrom cascade with pressure-equalisation recovery uplift |
| `Electrolyzer` | `neqsim.process.equipment.electrolyzer` | Stack model with technology defaults and Faradaic efficiency |
| `ElectrolyzerTechnology` | `neqsim.process.equipment.electrolyzer` | Enum PEM / ALKALINE / SOEC / AEM with default V, j, T, P, η_F |
| `ElectrolyzerIVCharacteristic` | `neqsim.process.equipment.electrolyzer` | Tafel + ohmic voltage model |
| `ElectrolyzerCostEstimate` | `neqsim.process.costestimation.electrolyzer` | Specific-CAPEX × scale × CEPCI |
| `PSACostEstimate` | `neqsim.process.costestimation.adsorber` | PSA vessel + switching valve skid + sorbent inventory CAPEX |
| `ParaOrthoH2Correction` | `neqsim.thermo.util.hydrogen` | Equilibrium para fraction, conversion heat, Cp and thermal-conductivity correction factors |
| `CatalystDeactivationKinetics` | `neqsim.process.equipment.reactor` | Sulfur/chloride/coking/sintering activity decay for H₂-production catalysts |
| `SMRHydrogenPlantBuilder` | `neqsim.process.hydrogen` | Screening plant builder for methane/steam feed, fired reformer, and optional PSA |
| `ATRHydrogenPlantBuilder` | `neqsim.process.hydrogen` | Screening plant builder for methane/steam/oxygen feed, ATR, and optional PSA |
| `POXHydrogenPlantBuilder` | `neqsim.process.hydrogen` | Screening plant builder for POX syngas or hydrogen route studies with optional PSA |
| `BlueHydrogenPlantBuilder` | `neqsim.process.hydrogen` | Full screening chain for SMR + HT/LT WGS + CO2 capture/compression + PSA + H2 drying/compression + carbon intensity |

## Thermochemical reformer route templates

The thermochemical route models are screening-grade process equipment: they use
NeqSim equilibrium reactors and local engineering metrics to make route studies
repeatable before users add detailed WGS, heat recovery, burner layout, amine
capture, or vendor reactor design data.

| Technology | NeqSim pattern | Maturity | Remaining high-fidelity scope |
|---|---|---:|---|
| Steam methane reforming (SMR) | `ReformerFurnace` + `CatalyticTubeReformer` + `FurnaceBurner` | 4/5 | Detailed radiant-box view factors, burner CFD, tube metallurgy/vendor rating |
| Autothermal reforming (ATR) | `AutothermalReformer` with `SyngasBurnerZone` + catalytic `GibbsReactor` | 4/5 | Burner aerodynamics, oxygen-mixing CFD, rate-based catalyst bed calibration |
| Partial oxidation (POX) | `PartialOxidationReactor` + `SyngasBurnerZone` + `QuenchSection` | 3/5 | Fast-quench kinetics, refractory thermal model, soot/coke kinetics |

### SMR fired reformer builder

```java
ProcessSystem process = new SMRHydrogenPlantBuilder().setName("SMR screening")
  .setMethaneFeedMolePerSec(100.0)
  .setSteamToCarbonRatio(3.0)
  .setIncludePsa(true)
  .build();
process.run();

ReformerFurnace furnace =
  (ReformerFurnace) process.getUnit("SMR screening reformer furnace");
double tubeDutyKW = furnace.getTubeHeatDemandKW();
double heatBalance = furnace.getHeatBalanceRatio();
double methaneConversion = furnace.getTubeReformer().getMethaneConversion();
```

`ReformerFurnace` exposes stable syngas and flue-gas outlet streams, so downstream
equipment such as WGS reactors, heat exchangers, PSA, or CO₂-capture units can be
wired from `getSyngasOutStream()` and `getFlueGasOutStream()`.

### ATR and POX builders

```java
ProcessSystem atrProcess = new ATRHydrogenPlantBuilder().setName("ATR screening")
  .setMethaneFeedMolePerSec(100.0)
  .setSteamToCarbonRatio(1.5)
  .setOxygenToCarbonRatio(0.60)
  .setIncludePsa(true)
  .build();
atrProcess.run();

AutothermalReformer atr =
  (AutothermalReformer) atrProcess.getUnit("ATR screening autothermal reformer");
double atrConversion = atr.getMethaneConversion();
double atrSootRisk = atr.getSootRiskIndex();

ProcessSystem poxProcess = new POXHydrogenPlantBuilder().setName("POX screening")
  .setMethaneFeedMolePerSec(100.0)
  .setOxygenToCarbonRatio(0.55)
  .setSteamToCarbonRatio(0.20)
  .build();
```

ATR and POX both rebuild the controlled inlet composition from the methane basis
before running, so `setOxygenToCarbonTarget()` and `setSteamToCarbonTarget()`
apply deterministically even when the starting stream has a different O₂/C or
S/C ratio. `toJson()` on each unit returns the key route-screening metrics.

## Blue H₂ — full process chain

`BlueHydrogenPlantBuilder` now creates a complete screening flowsheet for a
blue-H2 front end. The default route is:

1. methane/steam feed to fired SMR furnace
2. high-temperature WGS and low-temperature WGS equilibrium stages
3. shifted-gas cooler and condensate knock-out
4. selective CO2 capture placeholder plus CO2 export compressor
5. PSA cascade for H2 purification
6. H2 dryer placeholder and H2 export compressor
7. residual and gross carbon-intensity reporting

```java
BlueHydrogenPlantBuilder builder = new BlueHydrogenPlantBuilder()
  .setName("Blue H2 screening")
  .setMethaneFeedMolePerSec(100.0)
  .setSteamToCarbonRatio(3.0)
  .setCo2CaptureFraction(0.90)
  .setCo2ExportPressure(110.0)
  .setH2ExportPressure(100.0)
  .setIncludePsa(true);

ProcessSystem process = builder.build();
process.run();

double h2KgPerHr = builder.getHydrogenProductMassFlowKgPerHour();
double capturedCo2KgPerHr = builder.getCapturedCo2MassFlowKgPerHour();
double residualIntensity = builder.getCarbonIntensityKgCO2PerKgH2();
double grossIntensity = builder.getGrossCarbonIntensityKgCO2PerKgH2();
String resultsJson = builder.toJson();
```

The CO2 capture and H2 dryer blocks use `ComponentCaptureUnit`: a deterministic
component-removal placeholder that routes a selected component fraction to a
captured stream and the remainder to a treated stream. Replace these with amine,
membrane, or molecular-sieve models when project-specific design data are
available.

`WaterGasShiftReactor` is also available as a standalone unit. It treats methane,
nitrogen, and oxygen as inert while equilibrating CO, water, CO2, and H2. Key
outputs are `getCarbonMonoxideConversion()`, `getHydrogenMoleFlowGain()`,
`getCarbonDioxideMoleFlowFormation()`, `getHeatDutyKW()`, and
`getWgsEquilibriumRatio()`.

### PSA purification

After SMR + HT/LT WGS + cooler + knock-out drum, the wet syngas is purified to
fuel-cell grade in a PSA bed:

```java
PressureSwingAdsorptionBed psa = new PressureSwingAdsorptionBed("PSA", koVap);
psa.setSorbent(PressureSwingAdsorptionBed.SorbentType.ACTIVATED_CARBON);
psa.setRecoveryTarget(0.88);   // typical 0.80–0.90
psa.run();

double purityH2  = psa.getH2Purity();              // > 0.999 for FC grade
double recovery  = psa.getH2Recovery();            // operating value
double[] tail    = psa.getTailGasComposition();    // for furnace LHV
double tailMoles = psa.getTailGasMoleFlow();       // mol/s for fuel-gas balance
```

Notes:

- Sorbent options today: `ACTIVATED_CARBON` ("AC") and `ZEOLITE_13X`. Zeolite 5A
  is not in the bundled isotherm database.
- Recovery target is capped at $(0, 1]$; product H₂ is the unadsorbed light end.
- Tail gas is the right source term for the fuel-gas balance to the SMR furnace.

### Multi-bed PSA cascade

Industrial H₂ PSA plants use multiple beds so adsorption, blowdown, purge,
repressurisation, and pressure-equalisation steps can run continuously. The
`PSACascade` class wraps one `PressureSwingAdsorptionBed` template and applies a
cycle-averaged recovery uplift for the configured number of beds:

```java
PSACascade psa = new PSACascade("H2 PSA", koVap);
psa.setConfiguration(PSACascade.CascadeConfiguration.BEDS_8);
psa.setSorbent(PressureSwingAdsorptionBed.SorbentType.ACTIVATED_CARBON);
psa.setPerBedRecoveryTarget(0.85);
psa.setCycleTime(300.0);
psa.setBedDiameter(2.0);
psa.setBedLength(4.0);
psa.run();

double purityH2 = psa.getH2Purity();
double recovery = psa.getH2Recovery();
Stream tailGas = psa.getTailGasStream();
```

| Configuration | Beds | Pressure equalisation steps | Recovery uplift |
|---|---:|---:|---:|
| `BEDS_2` | 2 | 0 | 0.00 |
| `BEDS_4` | 4 | 1 | 0.05 |
| `BEDS_6` | 6 | 2 | 0.08 |
| `BEDS_8` | 8 | 3 | 0.10 |
| `BEDS_10` | 10 | 4 | 0.11 |
| `BEDS_12` | 12 | 5 | 0.12 |

Cascade recovery is `perBedRecoveryTarget + uplift`, capped at 0.93. Purity is
taken from the template bed because pressure equalisation mainly recovers H₂
that would otherwise leave with tail gas; it does not change sorbent selectivity.

## Benchmark validation

Hydrogen route tests now include benchmark-style envelopes rather than only
smoke tests. `HydrogenProductionBenchmarkTest` runs SMR, ATR, POX, and the full
blue-H2 chain and checks that conversion, O2/C, H2/CO, PSA/capture behavior,
H2 product rate, and carbon-intensity metrics remain in physically credible
screening ranges. These tests are deliberately range-based so improved EOS or
reactor solvers can move the answer without breaking validation, while large
model regressions are still caught.

## Green H₂ — water electrolysis

Set up a stream of liquid water and let the technology selector apply the
voltage, current density, temperature, pressure, and Faradaic efficiency
defaults:

```java
Electrolyzer el = new Electrolyzer("PEM stack", waterFeed);
el.setTechnology(ElectrolyzerTechnology.PEM);
el.setIVCharacteristic(new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.PEM));
el.run();

double powerKW = el.getStackPower();
double sec     = el.getSpecificEnergyConsumption_kWh_per_kg_H2();
double Vcell   = el.getCellVoltage();   // recomputed from I-V at run time
```

### Technology selector

| Tech | $V_{cell}$ (V) | $j$ (A/cm²) | $T$ (°C) | $P$ (bara) | $\eta_F$ | SEC (kWh/kg H₂) |
|---|---|---|---|---|---|---|
| PEM | 1.80 | 2.0 | 80 | 30 | 0.65 | ~45–55 |
| ALKALINE | 1.85 | 0.4 | 80 | 7 | 0.62 | ~50–60 |
| SOEC | 1.30 | 1.0 | 800 | 1 | 0.85 | ~35–40 (HHV) |
| AEM | 1.85 | 0.8 | 60 | 10 | 0.60 | ~55–65 |

Sources: IRENA 2022 Hydrogen Decarbonisation Pathways; Buttler & Spliethoff,
RSER 82 (2018); IEA Global Hydrogen Review 2023.

### I-V model

$$
V_{cell}(j, T) = E_{rev}(T) + A \cdot \log_{10}\!\left(\frac{j}{j_0}\right) + R \cdot j
$$

with the Larminie & Dicks reversible voltage:

$$
E_{rev}(T) = 1.229\;\text{V} + (-0.85\;\text{mV/K}) \cdot (T - 298.15\;\text{K})
$$

Tafel slope $A$, exchange current density $j_0$, and area-specific resistance
$R$ are technology-specific defaults inside `ElectrolyzerIVCharacteristic`.
Below $j_0$ the model returns $E_{rev}$ (no spurious negative overpotential).

## Electrolyzer CAPEX estimate

```java
el.initMechanicalDesign();
ElectrolyzerMechanicalDesign mech =
    (ElectrolyzerMechanicalDesign) el.getMechanicalDesign();
mech.calcDesign();   // populates totalPowerKW

ElectrolyzerCostEstimate cost = new ElectrolyzerCostEstimate(mech);
cost.setTechnology("PEM");
double usd = cost.getPurchasedEquipmentCost();
```

Defaults (2024 USD/kW at CEPCI = 800): PEM 1250, Alkaline 800, SOEC 2500,
AEM 1500. Scale exponent 0.85 vs reference 1 MW; `setIncludeBalanceOfPlant(false)`
strips ~35% for stack-only quotes. AACE Class 4–5 — do not use for FID without
vendor budget quotes.

## PSA CAPEX estimate

`PSACostEstimate` estimates PSA skid purchased equipment cost from the bed count,
per-bed vessel volume, switching valve skid, sorbent inventory, and CEPCI ratio.
The constructor from `PSACascade` is the simplest path because it reads bed count,
sorbent, diameter, and length directly from the cascade template bed:

```java
PSACascade psa = new PSACascade("H2 PSA", koVap);
psa.setConfiguration(PSACascade.CascadeConfiguration.BEDS_8);
psa.setSorbent(PressureSwingAdsorptionBed.SorbentType.ZEOLITE_13X);
psa.setBedDiameter(2.2);
psa.setBedLength(5.0);

PSACostEstimate cost = new PSACostEstimate(psa);
double purchasedUsd = cost.getPurchasedEquipmentCost();

cost.setIncludeBalanceOfPlant(false);
double stackOnlyUsd = cost.getPurchasedEquipmentCost();
```

Cost basis: USD 250 000 reference vessel at 2 m diameter × 4 m tangent length,
scale exponent 0.6, USD 60 000 switching-valve skid per bed, activated carbon at
USD 4/kg, Zeolite 13X at USD 10/kg, and CEPCI 800 reference. This is an AACE
Class 4–5 screening estimate.

## Cryogenic H₂ spin-isomer corrections

Normal hydrogen is approximately 25% para and 75% ortho at ambient temperature.
In liquid-hydrogen service the equilibrium mixture shifts strongly toward
para-hydrogen, releasing conversion heat that must be removed in liquefaction
and storage systems. `ParaOrthoH2Correction` provides a compact screening model:

```java
double para20K = ParaOrthoH2Correction.getEquilibriumParaFraction(20.0);
double heatJPerKg = ParaOrthoH2Correction.getNormalToEquilibriumHeatJPerKg(20.0);
double cpCorrection = ParaOrthoH2Correction.getCpCorrectionJPerKgK(40.0);
double conductivityFactor = ParaOrthoH2Correction.getThermalConductivityCorrectionFactor(20.0);
double tauSeconds = ParaOrthoH2Correction.estimateEquilibrationTimeSeconds(
  77.0, ParaOrthoH2Correction.ConversionCatalyst.HYDROUS_FERRIC_OXIDE);
```

The partition-function model approaches 25% para at warm temperatures and >99%
para near 20 K. The conversion heat is positive for exothermic conversion from
normal hydrogen to the lower-energy equilibrium mixture. Thermal-conductivity
factors are bounded screening multipliers for normal-H₂ correlations, not a
replacement for detailed Leachman/NIST transport data.

## Catalyst deactivation screening

Hydrogen-production catalysts can lose activity through sulfur poisoning,
chloride poisoning, coking, and thermal sintering. `CatalystDeactivationKinetics`
provides first-order screening rates for nickel SMR catalysts, iron-chromium
HT-shift catalysts, copper-zinc LT-shift catalysts, and ruthenium ammonia-cracking
catalysts:

```java
CatalystBed bed = new CatalystBed();
CatalystDeactivationKinetics kinetics = new CatalystDeactivationKinetics(
  CatalystDeactivationKinetics.CatalystFamily.NICKEL_REFORMING)
    .setTemperature(973.15)
    .setSulfurPpmv(0.05)
    .setCarbonPotential(0.5)
    .setSteamToCarbonRatio(2.5)
    .setOperationHours(8000.0);

double activity = kinetics.applyTo(bed);
double timeTo80 = kinetics.estimateTimeToActivity(0.80);
String mechanism = kinetics.getDominantMechanism();
```

Use this as an early design or digital-twin input for activity-factor studies.
Replace the default coefficients with vendor or plant-history data before using
the result for guaranteed catalyst run length.

## Color taxonomy

| Color | Route | NeqSim primitives |
|---|---|---|
| Grey | SMR, no CCS | `GibbsReactor` + WGS + `PressureSwingAdsorptionBed` |
| Blue | SMR/ATR + CCS | Add CO₂ capture and compression downstream — see [CO₂ injection well analysis](co2_injection_well_analysis.md) |
| Green | Renewable electrolysis | `Electrolyzer` (PEM / Alkaline) + renewable power |
| Pink | Nuclear electrolysis | Same `Electrolyzer`, accounting differs |
| Turquoise | Methane pyrolysis | Horizon 2 — not yet implemented |

## Common pitfalls

- **Thermochemical scope**: SMR/ATR/POX classes are screening models. They
  capture route-level heat balance, conversion, oxygen/steam ratios, soot risk,
  quench severity, and refractory/tube-temperature warnings, but they do not
  replace vendor reformer design, CFD, or rate-based catalyst calibration.
- **Syngas product species**: equilibrium reformer feeds must include trace
  product species (`hydrogen`, `CO`, `CO2`) before a Gibbs calculation. The
  hydrogen plant builders and reactor helpers add these automatically.
- **ATR/POX ratio controls**: with ratio control enabled, O₂/C and S/C targets
  are applied to a controlled clone of the inlet feed on a methane mole basis.
  Disable ratio control only when the feed composition itself is the source of
  truth for a case study.
- **PSA mass balance**: product purity is computed from
  $\text{feedH}_2 \times \text{recovery}$ divided by remaining light gases.
  Always set the recovery target before `run()`; default is 0.85.
- **Backward compatibility**: when no `ElectrolyzerTechnology` is set, the
  legacy fixed-voltage path ($V = 1.23$ V, $\eta_F = 1.0$) stays active and the
  pre-existing `testElectrolyzer` energy-duty assertion is unchanged.
- **Specific energy sanity check**:
  `getSpecificEnergyConsumption_kWh_per_kg_H2()` evaluates
  $\text{powerKW} / (\dot{n}_{H_2} \cdot M_{H_2} \cdot 3600)$. A result below
  ~33 kWh/kg implies > 100% LHV efficiency — recheck inputs.
- **Cost estimate**: requires `mech.calcDesign()` before constructing the
  `ElectrolyzerCostEstimate` so `totalPowerKW > 0`.
- **PSA cascade estimate**: `PSACostEstimate(PSACascade)` uses the template bed
  geometry. Set `setBedDiameter()` and `setBedLength()` before constructing the
  cost object if the default 2 m × 4 m bed is not appropriate.
- **Para/ortho correction scope**: `ParaOrthoH2Correction` gives screening
  corrections for cryogenic service. Detailed liquefaction design still needs a
  full heat-exchanger/expander flowsheet with validated hydrogen property data.
- **Catalyst deactivation scope**: default deactivation constants are
  conservative screening values. Use vendor, laboratory, or historian-tuned
  coefficients for life guarantees.

## Tests

| Test class | Coverage |
|---|---|
| `PressureSwingAdsorptionBedTest` | Defaults, recovery cap, mass balance, composition, sorbent switch |
| `ElectrolyzerTechnologyTest` | Per-tech default consistency |
| `ElectrolyzerIVCharacteristicTest` | $E_{rev}$ vs $T$, Tafel monotonicity, technology ordering |
| `ElectrolyzerTest` | Backward compat + $\eta_F$ + I-V + specific-energy band |
| `ElectrolyzerCostEstimateTest` | Per-tech ordering, BoP toggle, scale economy |
| `PSACascadeTest` | Bed-count uplift, 0.93 recovery cap, tail-gas balance, invalid inputs |
| `PSACostEstimateTest` | Bed-count cost scaling, sorbent cost ordering, BoP toggle, cascade constructor |
| `ParaOrthoH2CorrectionTest` | Equilibrium para fraction, conversion heat, Cp correction, conductivity factor, catalyst time ranking |
| `CatalystDeactivationKineticsTest` | Catalyst-family sensitivity, coking, sintering, dominant mechanism, CatalystBed activity update |
| `HydrogenProductionReactorTest` | SMR tube/furnace metrics, WGS metrics, ATR ratio controls, POX quench/refractory metrics, equipment factory aliases |
| `ComponentCaptureUnitTest` | Selective component capture, actual removal fraction, and mass balance |
| `HydrogenPlantBuilderTest` | Runnable SMR, ATR, POX, and full blue-H₂ plant builder templates |
| `HydrogenProductionBenchmarkTest` | Benchmark envelopes for SMR, ATR, POX, PSA/capture, full blue-H2 chain, and carbon-intensity metrics |

## Deferred to Horizon 2 / 3

- Rate-based amine absorber and membrane packages to replace the blue-H2 CO2 capture placeholder
- Full cryogenic H₂ liquefaction train with expanders and heat integration
- High-fidelity reformer radiant-box view factors, burner CFD, and vendor tube-rating integration
- Ammonia cracking kinetics for H₂ delivery from NH₃
- Hydrogen LCA per production step
- LOHC and photo-electrolysis

## Related documentation

- [CO₂ injection well analysis](co2_injection_well_analysis.md)
- [Reaction engineering](../chemicalreactions/index.md)
- [Cost estimation framework](COST_ESTIMATION_FRAMEWORK.md)
- [Mechanical design](mechanical_design.md)
