---
title: Hydrogen Production with NeqSim
description: Modeling guide for hydrogen production routes — pressure-swing adsorption (PSA) purification of SMR/WGS syngas (blue H2) and water electrolysis (green/pink H2). Covers PressureSwingAdsorptionBed, Electrolyzer with PEM/Alkaline/SOEC/AEM technology selector, I-V characteristic (Tafel + ohmic), and CAPEX estimation.
---

# Hydrogen Production with NeqSim

NeqSim covers two hydrogen production routes natively:

1. **Blue H₂** — steam methane reforming (SMR) or autothermal reforming (ATR)
   with water-gas shift (WGS) followed by **pressure-swing adsorption (PSA)**
   purification.
2. **Green / pink H₂** — water electrolysis using PEM, alkaline, SOEC, or AEM
   stacks.

This page documents the Horizon-1 classes added on
[PR #2221](https://github.com/equinor/neqsim/pull/2221). Companion guides:
[CCS and hydrogen transport](../ccs_hydrogen/index.md) and the
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
| `PressureSwingAdsorptionBed` | `neqsim.process.equipment.adsorber` | H₂-tuned single PSA bed (AC or Zeolite 13X) |
| `Electrolyzer` | `neqsim.process.equipment.electrolyzer` | Stack model with technology defaults and Faradaic efficiency |
| `ElectrolyzerTechnology` | `neqsim.process.equipment.electrolyzer` | Enum PEM / ALKALINE / SOEC / AEM with default V, j, T, P, η_F |
| `ElectrolyzerIVCharacteristic` | `neqsim.process.equipment.electrolyzer` | Tafel + ohmic voltage model |
| `ElectrolyzerCostEstimate` | `neqsim.process.costestimation.electrolyzer` | Specific-CAPEX × scale × CEPCI |

## Blue H₂ — PSA purification

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

## CAPEX estimate

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

## Color taxonomy

| Color | Route | NeqSim primitives |
|---|---|---|
| Grey | SMR, no CCS | `GibbsReactor` + WGS + `PressureSwingAdsorptionBed` |
| Blue | SMR/ATR + CCS | Add CO₂ capture upstream of stack — see [CCS hydrogen](../ccs_hydrogen/index.md) |
| Green | Renewable electrolysis | `Electrolyzer` (PEM / Alkaline) + renewable power |
| Pink | Nuclear electrolysis | Same `Electrolyzer`, accounting differs |
| Turquoise | Methane pyrolysis | Horizon 2 — not yet implemented |

## Common pitfalls

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

## Tests

| Test class | Coverage |
|---|---|
| `PressureSwingAdsorptionBedTest` | Defaults, recovery cap, mass balance, composition, sorbent switch |
| `ElectrolyzerTechnologyTest` | Per-tech default consistency |
| `ElectrolyzerIVCharacteristicTest` | $E_{rev}$ vs $T$, Tafel monotonicity, technology ordering |
| `ElectrolyzerTest` | Backward compat + $\eta_F$ + I-V + specific-energy band |
| `ElectrolyzerCostEstimateTest` | Per-tech ordering, BoP toggle, scale economy |

## Deferred to Horizon 1.5 / 2

- `PSACascade` multi-bed Skarstrom cycling and `PSACostEstimate`
- Rate-based amine absorber for CO₂ capture upstream of blue H₂
- Cryogenic H₂ liquefaction with ortho/para conversion
- Reformer furnace radiation coupling
- Ammonia cracking kinetics for H₂ delivery from NH₃
- Hydrogen LCA per production step
- LOHC, photo-electrolysis, catalyst deactivation

## Related documentation

- [CCS and hydrogen transport](../ccs_hydrogen/index.md)
- [Reaction engineering](../chemicalreactions/index.md)
- [Cost estimation framework](COST_ESTIMATION_FRAMEWORK.md)
- [Mechanical design](mechanical_design.md)
