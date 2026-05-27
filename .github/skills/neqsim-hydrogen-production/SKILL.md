---
name: neqsim-hydrogen-production
description: "Hydrogen production routes (SMR/ATR, electrolysis, ammonia cracking) with NeqSim. USE WHEN: modeling pressure-swing adsorption (PSA) purification of syngas, water electrolyzers (PEM/Alkaline/SOEC/AEM), stack I-V curves, hydrogen plant cost estimation, para/ortho hydrogen conversion, catalyst deactivation, or blue/green H2 plant flowsheets. Covers PressureSwingAdsorptionBed, PSACascade, Electrolyzer, ParaOrthoH2Correction, CatalystDeactivationKinetics, and cost estimates."
last_verified: "2026-05-27"
---

# Hydrogen Production with NeqSim

Guide for modeling hydrogen production routes — steam methane reforming with
PSA purification (blue H2) and water electrolysis (green/pink H2). Companion to
`neqsim-ccs-hydrogen` (transport/storage/blending) and `neqsim-reaction-engineering`
(reformer kinetics).

## When to Use This Skill

- SMR / ATR plant flowsheets with WGS + PSA
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

## Recipe 1 — Blue H₂ (SMR + WGS + PSA)

```java
// 1. Natural gas + steam feed
SystemInterface ng = new SystemSrkEos(298.15, 30.0);
ng.addComponent("methane", 0.95);
ng.addComponent("ethane", 0.04);
ng.addComponent("nitrogen", 0.01);
ng.setMixingRule("classic");
Stream ngFeed = new Stream("NG", ng);
ngFeed.setFlowRate(1000.0, "kg/hr");
ngFeed.run();

// ... add steam, GibbsReactor for SMR @ 850 °C / 25 bara,
// HT-WGS @ 400 °C, LT-WGS @ 220 °C, cooler, KO drum ...

// 2. PSA purification — feed wet syngas, take H₂ overhead.
PressureSwingAdsorptionBed psa = new PressureSwingAdsorptionBed("PSA", koVap);
psa.setSorbent(PressureSwingAdsorptionBed.SorbentType.ACTIVATED_CARBON);
psa.setRecoveryTarget(0.88);   // H₂ recovery, 0.80–0.90 typical
psa.run();

double purityH2 = psa.getH2Purity();          // >0.999 for fuel-cell grade
double recoveryH2 = psa.getH2Recovery();      // operating value
double[] tailComp = psa.getTailGasComposition();  // for furnace LHV
```

**Notes**
- Sorbent options today: `ACTIVATED_CARBON` ("AC") and `ZEOLITE_13X`. Zeolite 5A
  is not in the bundled isotherm database.
- Recovery target is capped at `(0, 1]`; product H₂ is the unadsorbed light end.
- Tail gas is delivered as a mole-flow vector via `getTailGasMoleFlow()` and is
  the right source term for fuel-gas balance to the SMR furnace.

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

## Deferred (Horizon 2/3)

- Rate-based amine absorber for CO₂ capture upstream of blue H₂
- Full cryogenic H₂ liquefaction train with expanders and heat integration
- Reformer furnace radiation coupling
- Ammonia cracking kinetics for H₂ delivery from NH₃
- Hydrogen LCA per production step
- LOHC and photo-electrolysis
