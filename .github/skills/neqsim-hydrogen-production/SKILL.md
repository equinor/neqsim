---
name: neqsim-hydrogen-production
description: "Hydrogen production routes (SMR/ATR/POX, blue-H2 WGS/capture/compression chains, electrolysis, ammonia cracking) with NeqSim. USE WHEN: modeling fired SMR reformers, ATR and POX syngas generators, water-gas shift, pressure-swing adsorption (PSA), CO2 capture/compression/export placeholders, H2 drying/compression/export, water electrolyzers (PEM/Alkaline/SOEC/AEM), stack I-V curves, hydrogen plant cost estimation, para/ortho hydrogen conversion, catalyst deactivation, or blue/green H2 flowsheets. Covers ReformerFurnace, CatalyticTubeReformer, WaterGasShiftReactor, ComponentCaptureUnit, BlueHydrogenPlantBuilder, AutothermalReformer, PartialOxidationReactor, PSACascade, Electrolyzer, ParaOrthoH2Correction, CatalystDeactivationKinetics, and cost estimates."
last_verified: "2026-05-27"
---

# Hydrogen Production with NeqSim

Guide for modeling hydrogen production routes — steam methane reforming (SMR),
autothermal reforming (ATR), partial oxidation (POX), PSA purification (blue
H2), and water electrolysis (green/pink H2). Companion to
`neqsim-ccs-hydrogen` (transport/storage/blending) and `neqsim-reaction-engineering`
(reformer kinetics).

## When to Use This Skill

- SMR fired reformer, ATR, and POX plant flowsheets with WGS + PSA
- Oxygen-to-carbon and steam-to-carbon envelope screening
- Reformer furnace heat-balance and tube-wall checks
- POX/ATR quench, refractory, soot-risk, and H2/CO screening
- PSA bed sizing and purity/recovery analysis
- Electrolyzer technology selection (PEM vs Alkaline vs SOEC vs AEM)
- Stack voltage modeling from current density (I-V curves)
- Specific energy consumption (kWh/kg H₂)
- Hydrogen plant CAPEX estimation
- Cryogenic para/ortho H₂ conversion screening
- Catalyst activity decay for SMR/WGS/ammonia cracking studies
- Blue vs green H₂ techno-economic comparison

## Applicable Standards

| Standard | Scope |
|---|---|
| ISO 14687 | Hydrogen fuel quality (PEM-FC grade ≥ 99.97% H₂) |
| ISO 22734 | Industrial water electrolyzers — safety |
| IEC 62282 | Fuel cell technologies (electrochemistry conventions) |
| API 941 | Steels for H₂ service (Nelson curves) |
| ASME B31.12 | Hydrogen piping and pipelines |
| EIGA Doc 121 | H₂ generator design (SMR/electrolysis) |
| IRENA 2022 | Green H₂ cost benchmarks |
| IEA Global H₂ Review 2023 | Capacity factors, USD/kW benchmarks |

## Core Classes (Thermochemical route templates)

| Class | Package | Purpose |
|---|---|---|
| `CatalyticTubeReformer` | `neqsim.process.equipment.reactor` | Tube-side SMR equilibrium model with duty, pressure-drop, tube-wall, heat-flux, and catalyst-activity screening |
| `ReformerFurnace` | `neqsim.process.equipment.reactor` | Fired SMR furnace coupling `FurnaceBurner` combustion heat to reformer-tube duty |
| `SyngasBurnerZone` | `neqsim.process.equipment.reactor` | Oxygen-blown ATR/POX burner-zone model with O2/C envelope and flame-temperature screening |
| `AutothermalReformer` | `neqsim.process.equipment.reactor` | ATR template with O2/C and S/C controls, burner zone, catalytic equilibrium zone, and soot-risk metric |
| `PartialOxidationReactor` | `neqsim.process.equipment.reactor` | POX template with O2/C control, refractory warning, fast quench, and H2/CO output |
| `QuenchSection` | `neqsim.process.equipment.reactor` | Rapid syngas cooling model with heat-removed and quench-severity outputs |
| `WaterGasShiftReactor` | `neqsim.process.equipment.reactor` | HT/LT WGS equilibrium wrapper with CO conversion, H2 gain, CO2 formation, duty, and WGS ratio reporting |
| `ComponentCaptureUnit` | `neqsim.process.equipment.splitter` | Selective component-capture placeholder for CO2 capture, H2 drying, and other screening separations |
| `SMRHydrogenPlantBuilder` | `neqsim.process.hydrogen` | Screening plant builder for methane/steam feed, fired reformer, and optional PSA |
| `ATRHydrogenPlantBuilder` | `neqsim.process.hydrogen` | Screening plant builder for methane/steam/oxygen feed, ATR, and optional PSA |
| `POXHydrogenPlantBuilder` | `neqsim.process.hydrogen` | Screening plant builder for POX syngas or hydrogen studies with optional PSA |
| `BlueHydrogenPlantBuilder` | `neqsim.process.hydrogen` | Full screening chain for SMR + HT/LT WGS + CO2 capture/compression + PSA + H2 drying/compression + carbon intensity |

### Thermochemical maturity map

| Technology | Implemented NeqSim pattern | Maturity | Remaining high-fidelity scope |
|---|---|---:|---|
| Steam methane reforming (SMR) | `ReformerFurnace` + `CatalyticTubeReformer` + `FurnaceBurner` | 4/5 | Detailed radiant-box view factors, burner CFD, tube metallurgy/vendor rating |
| Autothermal reforming (ATR) | `AutothermalReformer` with `SyngasBurnerZone` + catalytic `GibbsReactor` | 4/5 | Burner aerodynamics, oxygen-mixing CFD, rate-based catalyst bed calibration |
| Partial oxidation (POX) | `PartialOxidationReactor` + `SyngasBurnerZone` + `QuenchSection` | 3/5 | Fast-quench kinetics, refractory thermal model, soot/coke kinetics |

## Core Classes (Horizon 1)

| Class | Package | Purpose |
|---|---|---|
| `PressureSwingAdsorptionBed` | `neqsim.process.equipment.adsorber` | H₂-tuned single PSA bed (AC or Zeolite 13X) |
| `AdsorptionCycleController` | `neqsim.process.equipment.adsorber` | Multi-bed Skarstrom-style cycling (pre-existing) |
| `Electrolyzer` | `neqsim.process.equipment.electrolyzer` | Stack model with technology defaults and Faradaic efficiency |
| `ElectrolyzerTechnology` | same | Enum PEM / ALKALINE / SOEC / AEM with default voltage, current density, T, P, η_F |
| `ElectrolyzerIVCharacteristic` | same | Tafel + ohmic voltage model; tech-specific defaults |
| `ElectrolyzerCostEstimate` | `neqsim.process.costestimation.electrolyzer` | Specific-CAPEX × scale-factor × CEPCI |

## Core Classes (Horizon 1.5)

| Class | Package | Purpose |
|---|---|---|
| `PSACascade` | `neqsim.process.equipment.adsorber` | Multi-bed Skarstrom cascade (2/4/6/8/10/12 beds) with recovery uplift from pressure equalisation |
| `PSACostEstimate` | `neqsim.process.costestimation.adsorber` | Per-bed vessel + valve skid + sorbent inventory × CEPCI |

## Core Classes (Horizon 3 foundations)

| Class | Package | Purpose |
|---|---|---|
| `ParaOrthoH2Correction` | `neqsim.thermo.util.hydrogen` | Equilibrium para fraction, normal-to-equilibrium heat release, Cp correction, thermal-conductivity factor, conversion time |
| `CatalystDeactivationKinetics` | `neqsim.process.equipment.reactor` | First-order activity decay for sulfur/chloride poisoning, coking, and thermal sintering |

## Recipe 0 — SMR / ATR / POX route builders

Use the route builders when a task needs a runnable screening flowsheet quickly.
They create feeds with correct trace syngas products, set SRK + classic mixing,
wire the reactor model, and optionally add a PSA cascade.

```java
ProcessSystem smr = new SMRHydrogenPlantBuilder().setName("SMR screening")
    .setMethaneFeedMolePerSec(100.0)
    .setSteamToCarbonRatio(3.0)
    .setIncludePsa(true)
    .build();
smr.run();

ReformerFurnace furnace =
    (ReformerFurnace) smr.getUnit("SMR screening reformer furnace");
double dutyKW = furnace.getTubeHeatDemandKW();
double heatBalance = furnace.getHeatBalanceRatio();

ProcessSystem atr = new ATRHydrogenPlantBuilder().setName("ATR screening")
    .setMethaneFeedMolePerSec(100.0)
    .setSteamToCarbonRatio(1.5)
    .setOxygenToCarbonRatio(0.60)
    .setIncludePsa(true)
    .build();
atr.run();

ProcessSystem pox = new POXHydrogenPlantBuilder().setName("POX screening")
    .setMethaneFeedMolePerSec(100.0)
    .setOxygenToCarbonRatio(0.55)
    .setSteamToCarbonRatio(0.20)
    .build();
pox.run();
```

Key route outputs:

- `ReformerFurnace`: `getSyngasOutStream()`, `getFlueGasOutStream()`,
  `getTubeHeatDemandKW()`, `getAvailableRadiantHeatKW()`, `getHeatBalanceRatio()`.
- `CatalyticTubeReformer`: `getMethaneConversion()`, `getHeatDuty("kW")`,
  `getTubeWallTemperature()`, `isTubeWallTemperatureAcceptable()`.
- `AutothermalReformer`: `getOxygenToCarbonRatio()`, `getSteamToCarbonRatio()`,
  `getMethaneConversion()`, `getSootRiskIndex()`, `getBurnerZone()`.
- `PartialOxidationReactor`: `getHydrogenToCarbonMonoxideRatio()`,
  `getRefractoryWarning()`, `getQuenchSection()`, `getSootRiskIndex()`.
- Every new route unit exposes `getResults()` and `toJson()` for agent reports.

## Recipe 1 — Blue H₂ full chain (SMR + WGS + capture + PSA + export)

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

**Notes**
- The default sequence is SMR furnace → HT WGS → LT WGS → cooler/KO → CO2
  capture → CO2 compressor → PSA → H2 dryer → H2 compressor.
- `WaterGasShiftReactor` treats methane, nitrogen, and oxygen as inert and
  reports CO conversion, H2 gain, CO2 formation, heat duty, and WGS ratio.
- `ComponentCaptureUnit` is a screening placeholder. Use it for CO2 capture and
  H2 drying when detailed amine, membrane, or molecular-sieve packages are not
  yet available.
- Carbon-intensity reporting counts residual direct carbon as CO2 equivalent and
  reports both residual and gross intensity per kg H2 product.

## Recipe 1b — PSA-only purification block

```java
PressureSwingAdsorptionBed psa = new PressureSwingAdsorptionBed("PSA", koVap);
psa.setSorbent(PressureSwingAdsorptionBed.SorbentType.ACTIVATED_CARBON);
psa.setRecoveryTarget(0.88);
psa.run();

double purityH2 = psa.getH2Purity();
double recoveryH2 = psa.getH2Recovery();
double[] tailComp = psa.getTailGasComposition();
```

Use the standalone PSA block when a shifted syngas stream is already available
from a custom flowsheet.

## Recipe 2 — Green H₂ Electrolyzer

```java
// Water feed to the stack — molar flow sets the H₂ production target.
SystemInterface water = new Fluid().create("water");
Stream feed = new Stream("water", water);
feed.setFlowRate(100.0, "mole/sec");      // ~10 kg H₂/hr at η_F = 1
feed.setTemperature(298.15, "K");
feed.setPressure(1.0, "bara");
feed.run();

Electrolyzer el = new Electrolyzer("PEM stack", feed);
el.setTechnology(ElectrolyzerTechnology.PEM);  // applies V/j/T/P/η_F defaults
el.setIVCharacteristic(new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.PEM));
el.run();

double power_kW = el.getStackPower();
double sec      = el.getSpecificEnergyConsumption_kWh_per_kg_H2();
double V_cell   = el.getCellVoltage();   // recomputed from I-V at run time
```

**Technology selector**

| Tech | V_cell | j (A/cm²) | T (°C) | P (bara) | η_F | Specific energy (kWh/kg) |
|---|---|---|---|---|---|---|
| PEM | 1.80 | 2.0 | 80 | 30 | 0.65 | ~45–55 |
| ALKALINE | 1.85 | 0.4 | 80 | 7 | 0.62 | ~50–60 |
| SOEC | 1.30 | 1.0 | 800 | 1 | 0.85 | ~35–40 (HHV) |
| AEM | 1.85 | 0.8 | 60 | 10 | 0.60 | ~55–65 |

Sources: IRENA 2022 Hydrogen Decarbonisation Pathways; Buttler & Spliethoff RSER 82 (2018); IEA Global H₂ Review 2023.

**I-V curve internals**

`getCellVoltage(j, T_K) = E_rev(T) + A · log10(j / j0) + R · j`

- `E_rev(T) = 1.229 V + (-0.85 mV/K) · (T - 298.15)` (Larminie & Dicks)
- Tafel slope `A`, exchange current `j0`, ASR `R` are technology defaults
- Below `j0` the model returns `E_rev` (no spurious negative overpotential)

## Recipe 4 — Multi-bed PSA cascade

```java
PSACascade cascade = new PSACascade("H2-PSA", koVap);
cascade.setConfiguration(PSACascade.CascadeConfiguration.BEDS_6);  // 6 beds, 2 PEQ
cascade.setSorbent(PressureSwingAdsorptionBed.SorbentType.ACTIVATED_CARBON);
cascade.setPerBedRecoveryTarget(0.82);   // single-bed equilibrium recovery
cascade.setCycleTime(300.0);             // seconds per bed cycle
cascade.run();

double purity   = cascade.getH2Purity();        // > 99.9 %
double recovery = cascade.getH2Recovery();      // single-bed + cascade uplift
Stream tail     = cascade.getTailGasStream();   // for SMR fuel-gas balance
```

**Cascade uplift table** (pressure equalisation steps → recovery gain over a single bed):

| Configuration | Beds | Equalisations | Uplift |
|---|---|---|---|
| `BEDS_2` | 2 | 0 | +0.00 |
| `BEDS_4` | 4 | 1 | +0.05 |
| `BEDS_6` | 6 | 2 | +0.08 |
| `BEDS_8` | 8 | 3 | +0.10 |
| `BEDS_10` | 10 | 4 | +0.11 |
| `BEDS_12` | 12 | 5 | +0.12 |

Total cascade recovery is capped at **0.93** (industrial benchmark for H₂ PSA on shifted syngas).

## Recipe 5 — PSA cascade CAPEX

```java
PSACostEstimate cost = new PSACostEstimate(cascade);   // derives N_beds, sorbent, mass
cost.calculateCostEstimate();
double usd = cost.getPurchasedEquipmentCost();
```

- Reference per-bed vessel cost: USD 250 000 at 2 m × 4 m TL-TL, scale exponent 0.6.
- Valve skid: USD 60 000 per bed (manifold + actuators + cycle controller).
- Sorbent inventory: USD 4/kg AC or USD 10/kg Zeolite 13X.
- Balance-of-plant strip (`setIncludeBalanceOfPlant(false)`) removes ~25 % for vessel-only quotes.
- CEPCI 2024 = 800 reference; multiply by `CostEstimationCalculator.getCurrentCepci()/800`.

## Recipe 6 — Para/ortho H₂ correction for cryogenic screening

```java
double para20K = ParaOrthoH2Correction.getEquilibriumParaFraction(20.0);
double heatJPerKg = ParaOrthoH2Correction.getNormalToEquilibriumHeatJPerKg(20.0);
double cpCorrection = ParaOrthoH2Correction.getCpCorrectionJPerKgK(40.0);
double conductivityFactor = ParaOrthoH2Correction.getThermalConductivityCorrectionFactor(20.0);
double tauSeconds = ParaOrthoH2Correction.estimateEquilibrationTimeSeconds(
    77.0, ParaOrthoH2Correction.ConversionCatalyst.HYDROUS_FERRIC_OXIDE);
```

- Normal hydrogen is 25% para; equilibrium hydrogen approaches >99% para near 20 K.
- `getNormalToEquilibriumHeatJPerKg(T)` returns positive exothermic heat release.
- `getCpCorrectionJPerKgK(T)` is equilibrium minus frozen-normal rotational heat capacity.
- Thermal-conductivity correction factors are bounded screening multipliers for normal-H₂ correlations.

## Recipe 7 — Catalyst deactivation activity factor

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

- Families: `NICKEL_REFORMING`, `IRON_CHROMIUM_HT_SHIFT`, `COPPER_ZINC_LT_SHIFT`,
  `RUTHENIUM_AMMONIA_CRACKING`.
- Mechanisms: sulfur poisoning, chloride poisoning, coking, and thermal sintering.
- Use vendor/lab/historian coefficients before detailed run-length guarantees.

## Recipe 3 — Electrolyzer CAPEX

```java
el.initMechanicalDesign();
ElectrolyzerMechanicalDesign mech =
    (ElectrolyzerMechanicalDesign) el.getMechanicalDesign();
mech.calcDesign();   // populates totalPowerKW

ElectrolyzerCostEstimate cost = new ElectrolyzerCostEstimate(mech);
cost.setTechnology("PEM");
double usd = cost.getPurchasedEquipmentCost();
```

- Specific CAPEX (2024 USD/kW, CEPCI 800): PEM 1250, Alkaline 800, SOEC 2500, AEM 1500.
- Scale exponent 0.85 vs reference 1 MW.
- `setIncludeBalanceOfPlant(false)` strips ~35% for stack-only quotes.
- Numbers are AACE Class 4–5 — do not use for FID without vendor budget quotes.

## Color taxonomy and route map

| Color | Route | NeqSim primitives |
|---|---|---|
| Grey | SMR no CCS | GibbsReactor + WGS + `PressureSwingAdsorptionBed` |
| Blue | SMR/ATR + CCS | Add CO₂ capture (amine/MEA) → see `neqsim-ccs-hydrogen` |
| Green | Renewable electrolysis | `Electrolyzer` with PEM/Alkaline + renewable power feed |
| Pink | Nuclear electrolysis | Same `Electrolyzer`, accounting differs |
| Turquoise | Methane pyrolysis | Not yet — Horizon 2 |

## Common pitfalls

- **Thermochemical scope**: `ReformerFurnace`, `AutothermalReformer`, and
  `PartialOxidationReactor` are route-screening models, not vendor reactor
  designs. Use them for heat balance, O2/C and S/C envelopes, soot/refractory
  warnings, and handoff to WGS/PSA/CO2 capture. Use vendor data or detailed CFD
  for radiant-box, burner, and tube-rating guarantees.
- **Trace products**: Gibbs syngas models need product components present in the
  feed. The route builders and `HydrogenProductionUtils.ensureSyngasComponents()`
  add `hydrogen`, `CO`, and `CO2` at trace levels.
- **ATR/POX controls**: ratio controls rebuild the controlled inlet clone from
  methane moles before running. Disable ratio control only when a measured or
  externally generated feed composition must be preserved exactly.
- **`PressureSwingAdsorptionBed.run()` mass balance**: product purity is computed
  from `feedH2 × recovery` ÷ remaining light gases. Always set the recovery target
  before `run()`; default is 0.85.
- **Electrolyzer voltage**: when no `ElectrolyzerTechnology` is set, the legacy
  fixed-voltage path (V = 1.23 V, η_F = 1.0) stays active for backward compatibility.
- **Specific energy**: `getSpecificEnergyConsumption_kWh_per_kg_H2()` is
  `stackPower_kW / (n_H2 · MW_H2 · 3600)`. Below ~33 kWh/kg means the model is
  predicting >100% LHV efficiency — recheck inputs.
- **Cost estimate**: requires `mech.calcDesign()` before construction so
  `totalPowerKW > 0`.
- **Para/ortho scope**: `ParaOrthoH2Correction` is a screening correction, not a
  complete liquefaction process model.
- **Catalyst life scope**: `CatalystDeactivationKinetics` default coefficients are
  order-of-magnitude values. Tune them to vendor or historian data for plant-specific forecasts.

## Tests for verification

| Test | Coverage |
|---|---|
| `PressureSwingAdsorptionBedTest` | Defaults, recovery cap, mass balance, composition, sorbent switch |
| `PSACascadeTest` | Cascade uplift, bed-count monotonicity, 0.93 cap, tail-gas mass balance |
| `PSACostEstimateTest` | Bed-count linearity, sorbent ordering, BoP toggle, order of magnitude |
| `ParaOrthoH2CorrectionTest` | Para equilibrium limits, conversion heat, Cp correction, conductivity factor, catalyst time ranking |
| `CatalystDeactivationKineticsTest` | Catalyst-family sensitivity, coking, sintering, dominant mechanism, CatalystBed activity update |
| `ElectrolyzerTechnologyTest` | Per-tech default consistency |
| `ElectrolyzerIVCharacteristicTest` | E_rev vs T, Tafel monotonicity, technology ordering |
| `ElectrolyzerTest` | Backward compat + η_F + I-V + specific-energy band |
| `ElectrolyzerCostEstimateTest` | Per-tech ordering, BoP toggle, scale economy |
| `HydrogenProductionReactorTest` | SMR tube/furnace metrics, ATR ratio controls, POX quench/refractory metrics, equipment factory aliases |
| `HydrogenPlantBuilderTest` | Runnable SMR, ATR, POX, and blue-H2 plant builder templates |

## Deferred (Horizon 2/3)

- Rate-based amine absorber for CO₂ capture upstream of blue H₂
- Full cryogenic H₂ liquefaction train with expanders and heat integration
- High-fidelity reformer radiant-box view factors, burner CFD, and vendor tube-rating integration
- Ammonia cracking kinetics for H₂ delivery from NH₃
- Hydrogen LCA per production step
- LOHC and photo-electrolysis
