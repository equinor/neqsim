---
name: neqsim-heat-integration
version: "1.0.0"
description: "Pinch analysis and heat integration — composite curves, ΔTmin selection, MER targeting, grand composite, HEN synthesis, retrofit. USE WHEN: a task involves reducing utility cost, evaluating heat recovery, sizing utility duties, or comparing process alternatives on energy efficiency. Anchors on neqsim.process.equipment.heatexchanger.heatintegration.PinchAnalysis."
last_verified: "2026-04-26"
requires:
  java_packages: [neqsim.process.equipment.heatexchanger.heatintegration]
---

# NeqSim Heat Integration Skill

Linnhoff-method pinch analysis to determine minimum hot/cold utility duties,
the pinch temperature, composite & grand-composite curves, and to evaluate
heat-exchanger network (HEN) opportunities.

## When to Use

- Determining MER (Minimum Energy Requirement) for a flowsheet
- Setting utility duties before equipment sizing
- Retrofit analysis — quantify potential savings vs. existing HEN
- Comparing process alternatives on energy footprint
- Sizing utility headers (steam, cooling water) consistently across a plant

Standard reference: **B. Linnhoff & E. Hindmarsh (1983), Chem. Eng. Sci.**;
operational guidance from **Smith — Chemical Process Design and Integration (2nd ed.)**.

## Core Concept

```
Hot streams (need cooling)  ──┐
                              ├── shifted by ΔTmin/2 → temperature intervals → cascade → MER
Cold streams (need heating) ──┘
```

The pinch divides the system in two:
- **Above pinch** — heat sink (only hot utility allowed)
- **Below pinch** — heat source (only cold utility allowed)
- **Across pinch** — every kW transferred costs 1 kW hot + 1 kW cold

Three golden rules — **never** violate any of them:

1. No external heating below the pinch
2. No external cooling above the pinch
3. No heat transfer across the pinch

## Pattern 1 — Streams Defined Manually

```java
import neqsim.process.equipment.heatexchanger.heatintegration.PinchAnalysis;

PinchAnalysis pinch = new PinchAnalysis(10.0);   // ΔTmin in °C
// addHotStream(name, supplyT_C, targetT_C, mCp_kW_per_K)
pinch.addHotStream("H1 — reactor effluent",  180.0,  80.0, 30.0);
pinch.addHotStream("H2 — flash gas",         150.0,  50.0, 15.0);
pinch.addColdStream("C1 — feed preheat",      30.0, 140.0, 20.0);
pinch.addColdStream("C2 — reboiler feed",     60.0, 120.0, 25.0);
pinch.run();

double Qh   = pinch.getMinimumHeatingUtility();    // kW
double Qc   = pinch.getMinimumCoolingUtility();    // kW
double Tpinch = pinch.getPinchTemperatureC();
```

## Pattern 2 — Auto-Extract from a ProcessSystem

```java
PinchAnalysis pinch = PinchAnalysis.fromProcessSystem(process, 10.0);
pinch.run();
```

This walks every `Heater`, `Cooler`, and `HeatExchanger` and registers their duties as streams.

## Pattern 3 — Choosing ΔTmin

| Service                          | Typical ΔTmin |
| -------------------------------- | -------------- |
| Gas–gas refinery                 | 15–25 °C       |
| Gas–liquid                       | 8–15 °C        |
| Liquid–liquid                    | 5–10 °C        |
| Cryogenic                        | 1–3 °C         |
| Steam reboiler / cooling water   | 8–10 °C        |

Trade-off: lower ΔTmin → less utility (good OPEX) but bigger HX area (worse CAPEX).
Optimum is found from **supertargeting** — sweep ΔTmin and plot total-annual-cost.

## Pattern 4 — Composite Curves for Visualization

```java
// (after pinch.run())
double[] Th  = pinch.getHotCompositeT();
double[] Qh_ = pinch.getHotCompositeQ();
double[] Tc  = pinch.getColdCompositeT();
double[] Qc_ = pinch.getColdCompositeQ();
// Plot T vs Q → composite curves; the overlap = recoverable, the tails = utility
```

Plot the **grand composite curve** (`getGrandCompositeQ/T()`) to choose utility levels (HP/MP/LP steam, CW, refrigeration).

## Pattern 5 — Retrofit Diagnostics

```java
double existingUtility = sumExistingHotUtilities(process);   // user supplies
double saving = existingUtility - pinch.getMinimumHeatingUtility();
double saving_pct = 100.0 * saving / existingUtility;
```

Identify cross-pinch transfer with stream-level diagnostics (each violation costs double).

## Common Mistakes

| Mistake                                                 | Fix                                                                       |
| ------------------------------------------------------- | ------------------------------------------------------------------------- |
| Using mass flow instead of mCp                          | mCp = ṁ × cp (kW/K); for phase-change, treat as multiple linear segments  |
| Single ΔTmin for both gas–gas and liquid–liquid         | Use stream-individual film coefficients or split into zones               |
| Counting reboiler/condenser duties as process streams   | They are utilities — exclude or model as utility curves above pinch       |
| Ignoring soft constraints (forbidden matches)           | Note them in the report; pinch gives the target, HEN respects constraints |
| Cross-pinch transfer in MER design                      | Re-route hot stream to above-pinch cold stream; never quench across pinch |
| Reporting only utility numbers without ΔTmin            | Always state ΔTmin assumption; results scale strongly with it             |

## Validation Checklist

- [ ] ΔTmin chosen and justified (table above + sensitivity sweep)
- [ ] Three golden rules satisfied in any proposed HEN
- [ ] Composite curves plotted; pinch temperature reported
- [ ] Hot + Cold utility duties cross-checked: ΣQ_hot − ΣQ_cold = enthalpy balance
- [ ] Retrofit savings quantified vs. base case
- [ ] Utility level selection justified by grand composite curve
- [ ] Result saved to `results.json` under `heat_integration` with `min_hot_utility_kW`, `min_cold_utility_kW`, `pinch_T_C`, `delta_T_min_C`

## Related Skills

- [`neqsim-power-generation`](../neqsim-power-generation/SKILL.md) — utility-side: HRSG, steam levels
- [`neqsim-equipment-cost-estimation`](../neqsim-equipment-cost-estimation/SKILL.md) — HX area→cost for supertargeting
- [`neqsim-platform-modeling`](../neqsim-platform-modeling/SKILL.md) — apply pinch across multiple process areas
