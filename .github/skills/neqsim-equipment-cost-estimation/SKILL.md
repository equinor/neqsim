---
name: neqsim-equipment-cost-estimation
version: "1.0.0"
description: "Equipment-level CAPEX estimation — Turton/Peters/Ulrich/Seider correlations, CEPCI escalation, material/pressure factors, bare-module → grass-roots, AACE class 1–5, location factors, currency conversion. USE WHEN: a task requires a +30%/-30% Class-3/4 cost estimate at the equipment level (vessels, columns, pumps, compressors, HX, piping). Anchors on neqsim.process.costestimation.CostEstimationCalculator."
last_verified: "2026-04-26"
requires:
  java_packages: [neqsim.process.costestimation]
---

# NeqSim Equipment Cost Estimation Skill

Class-3/4 (AACE 18R-97) order-of-magnitude → study estimates for individual
process equipment — the bridge between sized equipment and a project economics
model. Wraps Turton (5e), Peters–Timmerhaus, Ulrich, and Seider correlations
with CEPCI escalation, material factors, pressure factors, and Lang/Hand factors.

## When to Use

- Quickly cost a sized vessel, column, HX, pump, or compressor
- Build a **bare-module** → **total-module** → **grass-roots** stackup
- Apply CEPCI to escalate published correlations to current year
- Compare alternatives (e.g. centrifugal vs reciprocating compressor)
- Feed equipment cost into NPV / IRR via [`neqsim-field-economics`](../neqsim-field-economics/SKILL.md)

> For project-level economics, asset valuation, or fiscal regimes, use
> [`neqsim-field-economics`](../neqsim-field-economics/SKILL.md). This skill is
> equipment-scoped only.

## AACE Cost Estimate Classes

| Class | Maturity      | Accuracy      | Method                        |
| ----- | ------------- | ------------- | ----------------------------- |
| 5     | Concept       | −50% / +100%  | Capacity-factor (six-tenths)  |
| 4     | Pre-FEED      | −30% / +50%   | Equipment-factored (this skill) |
| 3     | FEED          | −20% / +30%   | Sized equipment + bulk MTOs   |
| 2     | Detailed      | −15% / +20%   | Detailed quantity takeoff     |
| 1     | Final         | −10% / +15%   | Vendor quotes                 |

## Pattern 1 — Bare-module Cost for a Vessel

```java
import neqsim.process.costestimation.CostEstimationCalculator;

CostEstimationCalculator cost = new CostEstimationCalculator();
cost.setCurrentCepci(CostEstimationCalculator.CEPCI_2025);   // 840
cost.setMaterialOfConstruction("SS316");                     // sets Fm = 2.1
cost.setLocationByRegion("Norway");                          // location factor

double shellWeight_kg = 8500.0;        // from mechanical design
double designP_barg   = 110.0;
double Cp = cost.calcVerticalVesselCost(shellWeight_kg);
double Cbm = cost.calcBareModuleCost(Cp, designP_barg);      // applies Fp & Fm
double Ctm = cost.calcTotalModuleCost(Cbm);                  // + contingency + fees
double Cgr = cost.calcGrassRootsCost(Ctm);                   // + auxiliary facilities
```

## Pattern 2 — End-to-end via `calculateCostEstimate`

```java
cost.calculateCostEstimate(
    Cp,                  // purchased equipment cost
    designP_barg,        // for Fp
    shellWeight_kg,      // for installation man-hours
    "vertical_vessel"    // equipment category
);
String json = cost.toJson();   // structured output for results.json
```

## Pattern 3 — CEPCI Escalation

```
C_today = C_published × CEPCI_today / CEPCI_published
```

Built-in indices: `CEPCI_2019` (607.5) through `CEPCI_2025` (840). Escalate any
literature correlation that's published with a base year.

## Pattern 4 — Equipment-Specific Methods

| Equipment                | Method                                 | Sizing input          |
| ------------------------ | -------------------------------------- | --------------------- |
| Vertical pressure vessel | `calcVerticalVesselCost(weight_kg)`    | shell weight          |
| Horizontal vessel        | `calcHorizontalVesselCost(weight_kg)`  | shell weight          |
| Distillation column      | `calcColumnShellCost(weight_kg)` + `calcSieveTraysCost(D, N)` | weight + diameter + tray count |
| Shell-tube HX            | `calcShellTubeHeatExchangerCost(area_m2)` | area               |
| Plate HX                 | `calcPlateHeatExchangerCost(area_m2)`  | area                  |
| Air cooler               | `calcAirCoolerCost(area_m2)`           | bare-tube area        |
| Centrifugal pump         | `calcCentrifugalPumpCost(power_kW)`    | shaft power           |
| Centrifugal compressor   | `calcCentrifugalCompressorCost(power_kW)` | shaft power        |
| Reciprocating compressor | `calcReciprocatingCompressorCost(power_kW)` | shaft power      |
| Piping                   | `calcPipingCost(D_m, L_m, schedule)`   | diameter, length      |
| Control valve            | `calcControlValveCost(Cv)`             | Cv                    |

## Pattern 5 — Material and Pressure Factors (Turton)

```java
double Fp = CostEstimationCalculator.getPressureFactor(designP_barg);  // ~1.0–3.0
// Fm constants: FM_CARBON_STEEL=1.0, FM_SS304=1.8, FM_SS316=2.1,
//               FM_SS316L=2.3, FM_MONEL=3.2, FM_HASTELLOY_C=3.8, FM_TITANIUM=4.5
```

## Pattern 6 — Stackup Convention

```
Cp        = purchased equipment, FOB factory          (input)
Cbm       = Cp × (B1 + B2 × Fm × Fp)                  bare module
Ctm       = Cbm × 1.18                                + contingency 15% + fees 3%
Cgr       = Ctm × 1.30                                + 30% auxiliary (utilities, storage, OSBL)
```

## Pattern 7 — Cost Estimate for a Whole Process

```java
double totalCgr = 0.0;
for (ProcessEquipmentInterface eq : process.getUnitOperations()) {
    Cp = costForEquipment(eq, cost);   // dispatch by type
    cost.calculateCostEstimate(Cp, designP, weight, eqType);
    totalCgr += cost.getGrassRootsCost();
}
```

## Common Mistakes

| Mistake                                           | Fix                                                                 |
| ------------------------------------------------- | ------------------------------------------------------------------- |
| Using outdated CEPCI                              | Always set `setCurrentCepci(CEPCI_<year>)` before calculating       |
| Hardcoding Fm = 1                                 | Pick from `FM_*` constants based on actual material of construction |
| Missing pressure factor at high P                 | Use `getPressureFactor(P)` — silently × the cost at HP/HIPPS service |
| Reporting Cp as "installed cost"                  | Cp is FOB only; installed = at minimum Cbm                          |
| Mixing currencies                                 | Use `setCurrency(code, rate)` then read all costs through `convertFromUSD/convertToUSD` |
| Claiming Class-3 accuracy on capacity-factored    | Capacity factor (six-tenths) → Class 5; named in AACE 18R-97        |
| Ignoring location                                 | `setLocationByRegion("Norway")` ≈ 1.3× US Gulf Coast for offshore   |

## Validation Checklist

- [ ] CEPCI year matches the project base year
- [ ] Material of construction set per stream service (e.g. SS316L for wet sour)
- [ ] Pressure factor applied for design P > 10 barg
- [ ] AACE class declared in report (5/4/3) with stated accuracy band
- [ ] Stackup shown: Cp → Cbm → Ctm → Cgr with each multiplier
- [ ] Currency and location factor stated
- [ ] JSON saved via `cost.toJson()` to `results.json` under `capex` section
- [ ] Sensitivity to ±20% on key drivers (compressor power, column trays) reported

## Related Skills

- [`neqsim-field-economics`](../neqsim-field-economics/SKILL.md) — project NPV / IRR / fiscal
- [`neqsim-api-patterns`](../neqsim-api-patterns/SKILL.md) — `CompressorDesignFeasibilityReport` includes integrated cost
- [`neqsim-standards-lookup`](../neqsim-standards-lookup/SKILL.md) — AACE 18R-97
