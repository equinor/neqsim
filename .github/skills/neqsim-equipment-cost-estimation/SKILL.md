---
name: neqsim-equipment-cost-estimation
version: "1.0.0"
description: "Equipment-level CAPEX estimation — Turton/Peters/Ulrich/Seider correlations, CEPCI escalation, material/pressure factors, bare-module to grass-roots, AACE class 1-5, location factors, currency conversion, and mechanical-design-driven topside screening. USE WHEN: a task requires a +30%/-30% Class-3/4 cost estimate at the equipment level (vessels, columns, pumps, compressors, HX, piping, filters, reactors, mixers, splitters, manifolds). Anchors on neqsim.process.costestimation.CostEstimationCalculator."
last_verified: "2026-06-28"
requires:
  java_packages: [neqsim.process.costestimation]
---

# NeqSim Equipment Cost Estimation Skill

Class-3/4 (AACE 18R-97) order-of-magnitude to study estimates for individual
process equipment - the bridge between sized equipment and a project economics
model. Wraps Turton (5e), Peters–Timmerhaus, Ulrich, and Seider correlations
with CEPCI escalation, material factors, pressure factors, and Lang/Hand factors.
The preferred NeqSim path is mechanical-design driven: call each unit's
`initMechanicalDesign()`, `calcDesign()`, then
`getMechanicalDesign().getCostEstimate().calculateCostEstimate()` or let
`ProcessCostEstimate.calculateAllCosts()` do that rollup for a full flowsheet.

## When to Use

- Quickly cost a sized vessel, column, HX, pump, compressor, filter, reactor, mixer, splitter, or manifold
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

## Pattern 3 — Volume-Based Vessel Cost

For screening work, use the volume-based methods when the mechanical design
knows vessel diameter and length but the shell weight is not yet reliable.
These are now the preferred basis for separator, absorber, column, filter, and
reactor vessel screening.

```java
double vesselVolume_m3 = Math.PI * diameter_m * diameter_m / 4.0 * tanTanLength_m;
double verticalCp = cost.calcVerticalVesselCostByVolume(vesselVolume_m3);
double horizontalCp = cost.calcHorizontalVesselCostByVolume(vesselVolume_m3);
```

Keep the legacy `calcVerticalVesselCost(weight_kg)` and
`calcHorizontalVesselCost(weight_kg)` methods for cases where the mechanical
design has already produced a defensible shell weight.

## Pattern 4 — CEPCI Escalation

```
C_today = C_published × CEPCI_today / CEPCI_published
```

Built-in indices: `CEPCI_2019` (607.5) through `CEPCI_2025` (840). Escalate any
literature correlation that's published with a base year.

## Pattern 5 — Equipment-Specific Methods

| Equipment                | Method                                 | Sizing input          |
| ------------------------ | -------------------------------------- | --------------------- |
| Vertical pressure vessel | `calcVerticalVesselCost(weight_kg)`    | shell weight          |
| Horizontal vessel        | `calcHorizontalVesselCost(weight_kg)`  | shell weight          |
| Vertical vessel screening | `calcVerticalVesselCostByVolume(volume_m3)` | vessel volume    |
| Horizontal vessel screening | `calcHorizontalVesselCostByVolume(volume_m3)` | vessel volume |
| Distillation column      | `calcColumnShellCost(weight_kg)` + `calcSieveTraysCost(D, N)` | weight + diameter + tray count |
| Shell-tube HX            | `calcShellTubeHeatExchangerCost(area_m2)` | area               |
| Plate HX                 | `calcPlateHeatExchangerCost(area_m2)`  | area                  |
| Air cooler               | `calcAirCoolerCost(area_m2)`           | bare-tube area        |
| Centrifugal pump         | `calcCentrifugalPumpCost(power_kW)`    | shaft power           |
| Centrifugal compressor   | `calcCentrifugalCompressorCost(power_kW)` | shaft power        |
| Reciprocating compressor | `calcReciprocatingCompressorCost(power_kW)` | shaft power      |
| Piping                   | `calcPipingCost(D_m, L_m, schedule)`   | diameter, length      |
| Control valve            | `calcControlValveCost(Cv)`             | Cv                    |

## Pattern 6 — Mechanical-Design Estimator Wiring

Equipment-specific mechanical designs own their estimator selection. This is
what makes full-process rollups work: `ProcessCostEstimate` does not need to
know every unit's detailed sizing correlation; it asks the unit for its
mechanical design and cost estimate.

```java
unit.initMechanicalDesign();
MechanicalDesign design = unit.getMechanicalDesign();
design.calcDesign();
design.getCostEstimate().calculateCostEstimate();
double grassRootsCostUSD = design.getCostEstimate().getGrassRootsCost();
```

Current topside coverage includes specialized mechanical-design/cost paths for
separators, scrubbers, columns, absorbers, adsorbers, mercury removal, pumps,
compressors, expanders, ejectors, heat exchangers, BAHX units, tanks, pipelines,
valves, mixers, splitters, filters, reactors, and manifolds. Header-like units
such as mixers and splitters should calculate dry weight during `calcDesign()`;
filter/reactor/manifold estimators reuse the mechanical design outputs instead
of duplicating sizing logic.

## Pattern 7 — Material and Pressure Factors (Turton)

```java
double Fp = CostEstimationCalculator.getPressureFactor(designP_barg);  // ~1.0–3.0
// Fm constants: FM_CARBON_STEEL=1.0, FM_SS304=1.8, FM_SS316=2.1,
//               FM_SS316L=2.3, FM_MONEL=3.2, FM_HASTELLOY_C=3.8, FM_TITANIUM=4.5
```

## Pattern 8 — Stackup Convention

```
Cp        = purchased equipment, FOB factory          (input)
Cbm       = Cp × (B1 + B2 × Fm × Fp)                  bare module
Ctm       = Cbm × 1.18                                + contingency 15% + fees 3%
Cgr       = Ctm × 1.30                                + 30% auxiliary (utilities, storage, OSBL)
```

## Pattern 9 — Cost Estimate for a Whole Process

```java
ProcessCostEstimate processCost = new ProcessCostEstimate(process);
processCost.calculateAllCosts();
double totalCgr = processCost.getTotalGrassRootsCost();
```

Use this rollup for reservoir-to-market screening once the flowsheet contains
unit operations for the major equipment. It initializes each unit's mechanical
design, runs `calcDesign()`, calls the attached estimator, and aggregates the
result by equipment category.

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
| Adding a new mechanical design but leaving the base estimator | Assign the unit-specific `costEstimate` in the mechanical design constructor |
| Passing shell weight to a volume correlation       | Use `calc*ByVolume(volume_m3)` only with vessel volume               |

## Validation Checklist

- [ ] CEPCI year matches the project base year
- [ ] Material of construction set per stream service (e.g. SS316L for wet sour)
- [ ] Pressure factor applied for design P > 10 barg
- [ ] AACE class declared in report (5/4/3) with stated accuracy band
- [ ] Stackup shown: Cp → Cbm → Ctm → Cgr with each multiplier
- [ ] Currency and location factor stated
- [ ] JSON saved via `cost.toJson()` to `results.json` under `capex` section
- [ ] Sensitivity to ±20% on key drivers (compressor power, column trays) reported
- [ ] Full-process screens include major reservoir-to-market equipment rather than only topside separators and compressors
- [ ] New equipment classes expose a specialized mechanical design and estimator before relying on `ProcessCostEstimate`

## Related Skills

- [`neqsim-field-economics`](../neqsim-field-economics/SKILL.md) — project NPV / IRR / fiscal
- [`neqsim-api-patterns`](../neqsim-api-patterns/SKILL.md) — `CompressorDesignFeasibilityReport` includes integrated cost
- [`neqsim-standards-lookup`](../neqsim-standards-lookup/SKILL.md) — AACE 18R-97
