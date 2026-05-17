---
title: "Lessons Learned"
description: "Searchable database of practical engineering lessons extracted from 45+ solved tasks. Covers EOS selection gotchas, convergence failures, API quirks, and design insights."
---

# Lessons Learned

> **Purpose:** Practical lessons extracted from solved engineering tasks. Search this
> file before starting new work — many common mistakes and insights are documented here.
>
> **How to add:** After solving a task, append new lessons to the relevant category table.
> Keep entries concise and actionable.

---

## Thermodynamics & EOS

| Date | Lesson | Task Reference |
|------|--------|----------------|
| 2026-03-09 | SRK/PR give ~1000x lower water solubility in gas than reality (~0.02 vs ~125 mg/L at 5 bara, 20 C). **Use SRK-CPA** (`SystemSrkCPAstatoil`) for any system with water + hydrocarbon/CO2 — SRK ignores hydrogen bonding. | h2s_co2_distribution |
| 2026-03-09 | For produced water with TDS > 30,000 ppm, **Electrolyte-CPA** is essential. Salting-out reduces acid gas solubility by 20-60% at high salinity. | h2s_co2_distribution |
| 2026-03-09 | Water solubility in CO2 has a characteristic minimum at the CO2 saturation pressure (gas-liquid transition). CPA captures this but has ~30% mean error vs experimental data. | water_solubility_co2 |
| 2026-03-20 | NeqSim GERG-2008 matches REFPROP within 0.62% AARD for H2-containing mixtures. SRK performs surprisingly well for H2 density (AARD 2.0%). | h2_properties_data_comparison |
| 2026-03-23 | SRK and GERG-2008 give very different H2 K-values in CO2-rich systems: SRK predicts max 6.2 mol% H2 in gas; GERG-2008 predicts up to 21 mol%. **Always cross-validate with multiple EOS** for CO2+H2 systems. | smeaheia_co2_injection |
| 2026-03-27 | For H2 blending in export gas, **relative density** (not Wobbe index) is the binding EN 16726 constraint. Lean gas tolerates only ~8 mol% H2. Use `Standard_ISO6976` for gas quality calculations. | hydrogen_blending_export_gas |
| 2026-04-06 | Bug in electrolyte activity coefficient: `getActivityCoefficient(k)` (1-arg) retains counter-ions in reference phase, weakening Debye-Huckel by ~3x. **Use `getActivityCoefficient(k, waterIndex)`** with binary refPhase. Reduces MAE from 16.9% to 4.2%. | electrolyte_eos_development |
| 2026-04-09 | Standard SRK gives ~1000x lower methane solubility in water than SRK-CPA. For methane emission calculations from produced water degassing, **SRK-CPA is mandatory**. | mimee_code_review |
| 2026-04-13 | For sulfur deposition modeling, use SRK with **Huron-Vidal mixing rule** (mixing rule 2) — required for polar sulfur species. Standard "classic" mixing rule does not handle S8 polarity. | sulfur_heidrun_gt |
| 2026-04-16 | For CO2 water dew point analysis: use `SystemSrkCPAstatoil` with `waterDewPointTemperatureFlash()`. CO2 hydrates form at HIGHER temperature than water condensation — if dryers fail, hydrates form first. | co2_water_dew_point_snohvit |

## Process Simulation

| Date | Lesson | Task Reference |
|------|--------|----------------|
| 2026-03-09 | Elemental sulfur (S8) deposition from JT cooling follows "5 C per 10 bar" heuristic — confirmed by NeqSim. S8 solubility drops 5 orders of magnitude from wellhead to downstream letdown. Use `TPSolidflash`. | draupner_sulfur |
| 2026-03-27 | 3-stage separation + recompression with 3 recycle loops converges for gas condensate (PR EOS). Mass balance achievable within 0.015%. **Key: use unique equipment names** in ProcessSystem. | kristin_process_model |
| 2026-04-07 | `getBeta()` returns the **first-phase** mole fraction — for water-bearing oil streams this is the AQUEOUS phase, not gas! **Use `hasPhaseType("gas")` and phase-specific getters.** | bacalhau_dry_gas_seal |
| 2026-04-09 | CO2 misattribution in vent gas: at 10% CO2, ~14% of reported "methane" is actually CO2. Use composition-specific emission factors, not blanket volumetric factors. | mimee_code_review |
| 2026-04-13 | FLNG LNG spec: N2 < 1 ppm requires a dedicated Nitrogen Rejection Unit (cryogenic distillation), not simple end-flash. Rich gas with benzene > 207 ppm needs a scrub column. | flng_feedgas |
| 2026-04-16 | Full platform model (62+ MW compression): scrubber liquid recycles (LP KO to 3rd sep, MP KO to 2nd sep, HP KO to inlet sep) are **essential** for mass balance closure. Single ProcessSystem with recycles works; mass balance within 0.013%. | asgard_a_process_model |

## Dynamic Simulation

| Date | Lesson | Task Reference |
|------|--------|----------------|
| 2026-03-07 | VU-flash CNG tank simulation requires **dt <= 10s** for stability; larger timesteps cause divergence in the VesselDepressurization energy balance. | cng_tank_filling |
| 2026-03-18 | Composite tank walls (k=0.6 W/mK, Biot ~2.0) need the transient wall model. Steel walls (k=45 W/mK, Bi=0.027) use lumped capacitance. The 75x conductivity difference dominates over orientation effects (only 0.3 C difference). | umoe_composites_cng |
| 2026-04-16 | NeqSim dynamic simulation scores 81% vs HYSYS Dynamics 88%. Best-in-class: VU-flash separator dynamics, HIPPS 2oo3 voting, DynamicRiskSimulator. Key gaps: implicit integration, adaptive timestep, rate-based absorber. | review_dynamic_control |

## API & Code Gotchas

| Date | Lesson | Task Reference |
|------|--------|----------------|
| 2026-03-07 | `initProperties()` **MUST** be called after TPflash before reading transport properties (viscosity, thermal conductivity). `init(3)` alone does NOT initialize transport properties — they return zero. | cng_tank_filling |
| 2026-04-08 | UniSim to NeqSim: C7+ acentric factors are NOT exported by UniSim COM API. Lee-Kesler correlation gives ~3.6% vapour fraction deviation. PR-LK alpha function (UniSim) vs standard PR (NeqSim) causes ~7% flow discrepancy that propagates through the gas train. | r510_unisim_neqsim |
| 2026-03-12 | Turboexpander isentropic efficiency cannot be extracted from UniSim COM — must be assumed (typically 82-85%). Calibration requires TEX outlet temperature tag, which is often missing. | turboexpander_modification |

## Mechanical Design

| Date | Lesson | Task Reference |
|------|--------|----------------|
| 2026-04-07 | Dry gas seal condensation: isenthalpic expansion from 421 barg to 1.5 barg through 0.23mm seal clearance produces massive JT cooling and liquid condensation. Accumulated condensate flashes on restart generating 55-275 bar impact pressures. **Model with PH-flash sweep, not simple TP flash.** | bacalhau_dry_gas_seal |

## PVT & Characterization

| Date | Lesson | Task Reference |
|------|--------|----------------|
| 2026-04-10 | Crude oil blending with TBP pseudo-fractions: `addFluid()` handles fraction merging correctly for PR EOS. Viscosity mixing is log-weighted, not linear — pure API gravity interpolation underestimates blend viscosity. Flash at stock-tank conditions (15 C, 1.013 bara) for property reporting. | oseberg_grane_blending |

## Economics & Field Development

| Date | Lesson | Task Reference |
|------|--------|----------------|
| 2026-03-07 | Norwegian NCS post-2022 tax: corporate (22%) and petroleum (56%) taxes are on **independent** bases, not cascaded. Uplift deduction (5.5%/yr x 4 yrs) applies only to petroleum tax base. Getting this wrong changes NPV by billions. | npv_field_development |
| 2026-03-23 | FLNG at 3000m water depth: breakeven LNG price $27.1/MMBtu vs market $12/MMBtu — uneconomic. CAPEX $1772/tonne at upper end of FLNG benchmarks. Deepwater + fiscal regime can kill projects even with large reserves (3 Tcf). | flng_tanzania |
| 2026-03-24 | Gas price is the dominant NPV uncertainty (Spearman rho = +0.75), followed by CAPEX factor (rho = -0.50). Gas-in-place only ranks 3rd. **Market risk dominates subsurface risk** for field development economics. | snohvit_npv_monte_carlo |
| 2026-03-26 | H2 in CO2 injection streams: K_H2 reaches 11.9 at 50 bara/4 C. During shutdown when wellhead pressure drops below ~58 bara, two-phase forms and H2 enriches from 0.75% to 5-7% (exceeds 4% LFL). Manageable: re-dissolves when WHP raised above 60 bara on restart. | smeaheia_h2_wells |

---

## How to Add New Lessons

After completing a task, add entries to the relevant category table above:

```markdown
| YYYY-MM-DD | Concise lesson with **key point bolded**. Include the why and the fix. | task_folder_slug |
```

Keep lessons:
- **Actionable**: What should someone do differently?
- **Specific**: Include numbers, thresholds, class names
- **Cross-referenced**: Link to the task folder for full context
