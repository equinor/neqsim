---
title: "Task Log"
description: "Chronological record of engineering tasks solved in the NeqSim repo. Searchable by keywords, task type, and equipment. Provides memory across sessions."
---

# Task Log

> **Purpose:** Persistent memory across sessions. Every solved task gets an entry here
> so future sessions can find prior solutions instead of starting from scratch.
>
> **How to use:** Search this file for keywords before starting a new task.
> If a similar task was solved before, start from that solution.

## Entry Format

```
### YYYY-MM-DD — Short task title
**Type:** A (Property) | B (Process) | C (PVT) | D (Standards) | E (Feature) | F (Design) | G (Workflow)
**Keywords:** comma, separated, search, terms
**Solution:** Where the code lives (test file, notebook, source file)
**Notes:** Key decisions, gotchas, or results worth remembering
```

---

## Log

<!-- Add new entries at the top. Most recent first. -->

### 2026-04-22 — Kollsnes scrubber drainage-head method + standardised comparison plots + dual reports
**Type:** F (Design) / E (Feature)
**Keywords:** scrubber, drainage head, GasScrubberMechanicalDesign, computeDrainageHead, DrainageHeadResult, TR3500, ConformityRuleSet, mesh dP, cyclone dP, comparison plots, MSm3/d, Kollsnes
**Solution:**
- `src/main/java/neqsim/process/mechanicaldesign/separator/DrainageHeadResult.java` — new value class (mesh dP, cyclone dP-to-drain, total dP, required mm, available mm, %)
- `src/main/java/neqsim/process/mechanicaldesign/separator/GasScrubberMechanicalDesign.java` — new `computeDrainageHead()` returns `DrainageHeadResult`; `getResponse()` extended with `drainageHead` map
- `src/main/java/neqsim/process/mechanicaldesign/separator/conformity/ConformityRuleSet.java` — TR3500 emits `drainage-head` (mm), `drainage-head-pct` (% of available), `cyclone-dp-to-drain` (mbar)
- `task_solve/2026-04-20_kollsnes_scrubber_performance_analysis/step2_analysis/06_scrubber_tables.ipynb` — MSm³/d via `n·Vm·24/1e6`; standardised five-metric comparison plots (k, cyclone momentum, drainage %, P, Q in MSm³/d); UTF-8-safe HTML output
- `task_solve/.../step3_report/generate_scrubber_reports.py` — produces `Report_detailed.html` (full 9 tables + drainage analysis + comparison figures) and `Report_summary.html` (1-page exec brief); writes `results.json`
**Notes:**
- Required Maven build via Adoptium-JDK because the Adoptium-JRE `java.exe` lacks `javac`. Set `JAVA_HOME=C:\Users\pdup\AppData\Roaming\Code\User\globalStorage\pleiades.java-extension-pack-jdk\java\latest` before launching plexus-classworlds.
- Drainage check: `h_req = (dp_mesh + dp_cyc·f_to_drain) / ((ρ_liq − ρ_gas)·g)` vs `h_avail = z_cyc_deck − z_HHLL`. Default `eu_mesh = 0.5` if `mistEliminatorDpCoeff` not set.
- Conformity headline (33 cases): k-factor FAIL 33/33; drainage-head-pct PASS 33/33 (worst 29.9 % at VAx02 / Tog 3 / Historic peak). Drainage is NOT the limiting failure mode — re-entrainment is.
- Two new figures (`scrubber_comparison_bars.png`, `scrubber_flow_vs_drainage.png`) are mandatory for any future scrubber comparison.
- jinja2 was missing on the agent box — pandas `.style` accessor requires it. `pip install --user jinja2`.

### 2026-04-21 — Kollsnes scrubber 9-table performance deliverable
**Type:** F (Design) / G (Workflow)
**Keywords:** scrubber, GasScrubberMechanicalDesign, TR3500, ConformityReport, Kollsnes, VA301, VA302, VA303, Sulzer reference spreadsheet, mesh pad, demisting cyclones, inlet momentum, k-factor, historic peak
**Solution:**
- `task_solve/2026-04-20_kollsnes_scrubber_performance_analysis/step2_analysis/06_scrubber_tables.ipynb` — 33 (scrubber × case) conformity runs + 9 reference-style tables + Excel/HTML export (private task folder)
- Docs: `docs/process/equipment/separators.md` — new section "Gas Scrubber Mechanical Design and Conformity Checking" with Java+Python workflow, multi-case screening pattern, and usage constraints
- Test: `DocExamplesCompilationTest#testGasScrubberConformityCheckDoc` verifies the documented API path end-to-end
**Notes:**
- 11 cases (9 normal + 2 historic peaks; Tog1-H1 dropped because PIC5063A is missing in the long-history export — per user instruction "If you are missing data, best would be to disregard that historic point")
- Output layout mirrors the Sulzer `Scrubber calculations Rev02_SNA 27B-VG60` spreadsheet: one table per (scrubber × train), columns = operating cases, rows grouped into Process conditions / Geometry / Process results / Conformity
- TR3500 conformity outcome across 33 combos: k-factor FAIL 33/33 (expected for swirldeck-fitted vessels); inlet-momentum FAIL 21/PASS 11/WARN 1; mesh-k PASS 22/WARN 11; drainage-head PASS 33/33; cyclone-dp PASS 27/WARN 6
- **Never modify the TR3500 limits** (K=0.15, ρv²=15000, mesh-K=0.27) — define a new `ConformityRuleSet` subclass instead
- Efficiency and carry-over rows are deferred — table schema already reserves placeholder rows

### 2026-07-04 — Compressor Sealing
**Type:** B (Process)
**Keywords:** bacalhau, bics, components, compressor, connected, equipment, flow, fpso, model, pr78
**Solution:** task_solve/2026-07-04 Compressor sealing/
**Notes:** The unified NeqSim model replicates the Bacalhau FPSO process in a single connected ProcessSystem. Temperature MAE is 11.7%, flow MAE 9.5%, MW MAE 14.6%. Seal gas JT at 48 bar gives 15.8C (UniSim: 18.2C, -2.3C difference), confirming 1.

### 2026-04-16 — Asgard A Full Process Model with Plant Data Integration
**Type:** B (Process)
**Keywords:** appropriate, asgard, both, compression, converged, data, explicitly, full, integration, liquid
**Solution:** task_solve/2026-04-16_asgard_a_full_process_model_with_plant_data_integration/step2_analysis/01_asgard_a_detailed_process_model.ipynb, task_solve/2026-04-16_asgard_a_full_process_model_with_plant_data_integration/step2_analysis/01_asgard_a_process_model.ipynb, task_solve/2026-04-16_asgard_a_full_process_model_with_plant_data_integration/step2_analysis/02_asgard_a_full_process_with_recycles.ipynb
**Notes:** Full Åsgard A process model with recycles converged. Scrubber liquids recycled to appropriate separation stages. Both RI trains modeled explicitly. Total compression power: 62.9 MW.

### 2026-04-16 — Smeaheia CO2 Injection - Hydrogen Accumulation in Wells
**Type:** B (Process)
**Keywords:** accumulation, hydrogen, injection, smeaheia, wells
**Solution:** task_solve/2026-04-16_smeaheia_co2_injection_hydrogen_accumulation_in_wells/step2_analysis/01_h2_accumulation_analysis.ipynb
**Notes:** Task folder created; analysis in progress.

### 2026-04-16 — Smeaheia CO2 Injection Hydrogen Accumulation Risk Assessment
**Type:** B (Process)
**Keywords:** accumulation, assessment, bara, classic, component, conditions, critical, dense, drops, equation
**Solution:** task_solve/2026-04-16_smeaheia_co2_injection_hydrogen_accumulation_risk_assessment/step2_analysis/01_hydrogen_accumulation_analysis.ipynb, task_solve/2026-04-16_smeaheia_co2_injection_hydrogen_accumulation_risk_assessment/step2_analysis/02_plant_comparison_report.ipynb
**Notes:** The Smeaheia CO2 injection well operates safely in dense single phase under all normal operating conditions (110-200 bara, 4-45°C).

### 2026-04-13 — Elemental sulfur deposition in gas turbine Heidrun
**Type:** B (Process)
**Keywords:** approximately, assumed, causes, cooling, deposition, drop, elemental, fuel, heidrun, huron
**Solution:** task_solve/2026-04-13_elemental_sulfur_deposition_in_gas_turbine_heidrun/step2_analysis/01_sulfur_deposition_heidrun.ipynb
**Notes:** NeqSim thermodynamic modelling shows the S₈ deposition onset temperature is approximately 4°C at 40 bar for the assumed Heidrun fuel gas (20 ppm H₂S). A 20-bar pressure drop causes ~10.5°C JT cooling, giving an outlet of 19.

### 2026-04-13 — FLNG feedgas process design and analysis
**Type:** F (Design)
**Keywords:** achieved, amine, benzene, both, case, cases, comp, component, design, essentially
**Solution:** task_solve/2026-04-13_flng_feedgas_process_design_and_analysis/step2_analysis/01_flng_process.ipynb
**Notes:** 1. CO2 removal to 50 ppm achieved for both Lean and Rich cases via amine unit (modelled as component splitter). 2.

### 2026-04-10 — Oseberg Grane crude oil blending into Sture blend
**Type:** B (Process)
**Keywords:** blend, blending, crude, decreases, e300, exceed, fluid, fraction, grane, gravity
**Solution:** task_solve/2026-04-10_oseberg_grane_crude_oil_blending_into_sture_blend/step2_analysis/01_oseberg_grane_sture_blending_2030.ipynb
**Notes:** The Oseberg-Grane blend API gravity decreases monotonically with increasing Grane fraction. To maintain the historical Sture Blend specification of ~37° API, the Grane fraction should not exceed the interpolated threshold.

### 2026-04-10 — Out of Zone Injection - NeqSim Implementation Discussion
**Type:** E (Feature)
**Keywords:** discussion, implementation, injection, zone
**Solution:** task_solve/2026-04-10_out_of_zone_injection_neqsim_implementation_discussion/
**Notes:** Task folder created; analysis in progress.

### 2026-04-09 — MIMEE NeqSim Code Review - Methane Emissions
**Type:** E (Feature)
**Keywords:** code, emissions, methane, mimee, review
**Solution:** task_solve/2026-04-09_mimee_neqsim_code_review_methane_emissions/step2_analysis/01_reference_implementation.ipynb, task_solve/2026-04-09_mimee_neqsim_code_review_methane_emissions/step2_analysis/02_detailed_method_comparison.ipynb, task_solve/2026-04-09_mimee_neqsim_code_review_methane_emissions/step2_analysis/04_gas_composition_sensitivity.ipynb
**Notes:** Key results: deviation average percent: 38.0; deviation range percent: 8.4% to 80.1%; offshore norge equivalent temp C: 60-65; crossover temp C: ~40; neqsim factor at 20C g per m3 bar: 25.21.

### 2026-04-08 — R510 SG Condensation UniSim NeqSim Comparison
**Type:** D (Standards)
**Keywords:** carcara, characterization, comparison, component, components, compositions, condensation, e300, equinor, feed
**Solution:** task_solve/2026-04-08_r510_sg_condensation_unisim_neqsim_comparison/step2_analysis/01_comparison.ipynb
**Notes:** The E300 import successfully reproduces the UniSim 31-component fluid characterization in NeqSim. Molecular weights match to within 0.3% for all 12 streams tested (feed, gas, oil compositions). The feed flash vapour fraction differs by 3.

### 2026-04-08 — R510 SG condensation UniSim to NeqSim conversion
**Type:** B (Process)
**Keywords:** condensation, conversion, r510, unisim
**Solution:** task_solve/2026-04-08_r510_sg_condensation_unisim_to_neqsim_conversion/step2_analysis/01_unisim_neqsim_comparison.ipynb
**Notes:** Task folder created; analysis in progress.

### 2026-04-08 — Troll A early phase sprint paper
**Type:** B (Process)
**Keywords:** early, paper, phase, sprint, troll
**Solution:** task_solve/2026-04-08_troll_a_early_phase_sprint_paper/
**Notes:** Task folder created; analysis in progress.

### 2026-04-07 — Bacalhau compressor dry gas seal condensation analysis
**Type:** B (Process)
**Keywords:** alkane, bacalhau, caused, causes, components, compressor, condensation, continuous, during, envelope
**Solution:** task_solve/2026-04-07_bacalhau_compressor_dry_gas_seal_condensation_analysis/step2_analysis/01_seal_gas_condensation_analysis.ipynb, task_solve/2026-04-07_bacalhau_compressor_dry_gas_seal_condensation_analysis/step2_analysis/02_seal_gas_system_process_model.ipynb, task_solve/2026-04-07_bacalhau_compressor_dry_gas_seal_condensation_analysis/step2_analysis/03_dry_gas_seal_analyzer_java.ipynb
**Notes:** The dry gas seal failures on the GIC (BCL-304/E, 3rd stage) are caused by TWO condensation mechanisms: (1) PRIMARY — continuous seal leakage during standstill causes gas to expand isenthalpically from 421 barg to 1.

### 2026-04-07 — Bacalhau Injection Compressor Dry Gas Seal Failure Analysis
**Type:** B (Process)
**Keywords:** bacalhau, compressor, failure, injection, seal
**Solution:** task_solve/2026-04-07_bacalhau_injection_compressor_dry_gas_seal_failure_analysis/
**Notes:** Task folder created; analysis in progress.

### 2026-04-06 — Advanced Electrolyte EOS Development and Scientific Paper
**Type:** A (Property)
**Keywords:** advanced, average, calculation, corrected, counter, cross, development, dilution, discovered, electrolyte
**Solution:** task_solve/2026-04-06_advanced_electrolyte_eos_development_and_scientific_paper/step2_analysis/01_electrolyte_model_comparison.ipynb
**Notes:** Discovered fundamental reference state bug in getActivityCoefficient(k): counter-ions retained in reference, weakening DH ~3x. Corrected 2-arg calculation reduces average MAE from 16.9% to 4.2%. Cross-ion Cl- W0 consistency improved from 6x to 1.

### 2026-04-06 — TEG Dehydration Sizing for 50 MMSCFD Wet Gas
**Type:** B (Process)
**Keywords:** dehydration, mmscfd, sizing
**Solution:** task_solve/2026-04-06_teg_dehydration_sizing_for_50_mmscfd_wet_gas/step2_analysis/01_TEG_dehydration_sizing.ipynb
**Notes:** Task folder created; analysis in progress.

### 2026-04-05 — Wax Formation Models Comparison and Improvement
**Type:** D (Standards)
**Keywords:** comparison, formation, improvement, models
**Solution:** task_solve/2026-04-05_wax_formation_models_comparison_and_improvement/
**Notes:** Task folder created; analysis in progress.

### 2026-03-30 — NeqSim Library Review - High Impact Fixes and Updates
**Type:** E (Feature)
**Keywords:** fixes, high, impact, library, review, updates
**Solution:** task_solve/2026-03-30_neqsim_library_review_high_impact_fixes_and_updates/
**Notes:** Task folder created; analysis in progress.

### 2026-03-27 — Hydrogen Blending in Norwegian Export Gas Quality Analysis
**Type:** B (Process)
**Keywords:** binding, blending, compositions, constraint, density, export, fraction, hydrogen, index, lean
**Solution:** task_solve/2026-03-27_hydrogen_blending_in_norwegian_export_gas_quality_analysis/step2_analysis/01_h2_blending_gas_quality.ipynb
**Notes:** Relative density — not the Wobbe index — is the binding constraint for H₂ blending in Norwegian export gas. Lean gas (Troll-type) tolerates max ~8 mol% H₂ before violating EN 16726 rel. density limit (0.555); medium gas ~18%; rich gas ~27%.

### 2026-03-27 — Kristin Process Model from Existing Simulator
**Type:** B (Process)
**Keywords:** compression, compressors, condensate, existing, export, feed, five, handle, kristin, model
**Solution:** task_solve/2026-03-27_kristin_process_model_from_existing_simulator/step2_analysis/01_kristin_process_model.ipynb
**Notes:** The Kristin process model runs successfully in NeqSim with PR EOS. Feed of 100,000 kg/hr is separated into export gas and export oil. Five compressors handle recompression and export compression.

### 2026-03-26 — Smeaheia CO2 injection hydrogen accumulation in wells
**Type:** B (Process)
**Keywords:** accumulation, bara, bulk, component, enrichment, factors, hydrogen, injection, mixing, phase
**Solution:** task_solve/2026-03-26_smeaheia_co2_injection_hydrogen_accumulation_in_wells/step2_analysis/01_hydrogen_accumulation_analysis.ipynb
**Notes:** Hydrogen accumulation in the gas phase is a REAL thermodynamic phenomenon. At T=4 C the two-phase region spans 42-58 bara. H2 enrichment factors reach 5-8x (3.9-5.9 mol% H2 in gas vs 0.75% in bulk). K_H2 = 11.9 at 50 bara.

### 2026-03-24 — NeqSim Pseudo-Component Characterization Documentation
**Type:** E (Feature)
**Keywords:** characterization, component, documentation, pseudo
**Solution:** task_solve/2026-03-24_neqsim_pseudo_component_characterization_documentation/
**Notes:** Task folder created; analysis in progress.

### 2026-03-24 — Snohvit Probabilistic NPV Monte Carlo Analysis
**Type:** G (Workflow)
**Keywords:** analytical, appraisal, carlo, field, function, hvit, influential, model, monte, musd
**Solution:** task_solve/2026-03-24_snohvit_probabilistic_npv_monte_carlo_analysis/step2_analysis/01_snohvit_mc_npv.ipynb
**Notes:** The Snøhvit field NPV has a P50 of 135 MUSD with a P90-P10 range of -1505 to 1866 MUSD. The most influential uncertainty is Gas price Pg (Spearman ρ = +0.747). The appraisal well analysis shows a net VoI of -88 MUSD; recommendation: DO NOT DRILL.

### 2026-03-23 — FLNG Class A Concept Study Offshore Tanzania 3000m
**Type:** G (Workflow)
**Keywords:** benchmarks, c3mr, capex, challenges, class, concept, contr, depth, economic, faces
**Solution:** task_solve/2026-03-23_flng_class_a_concept_study_offshore_tanzania_3000m/step2_analysis/01_reservoir_fluid_pvt.ipynb, task_solve/2026-03-23_flng_class_a_concept_study_offshore_tanzania_3000m/step2_analysis/02_flng_process_simulation.ipynb, task_solve/2026-03-23_flng_class_a_concept_study_offshore_tanzania_3000m/step2_analysis/03_capex_economics.ipynb
**Notes:** The FLNG Tanzania concept at 3000m water depth faces significant economic challenges. Total CAPEX of $6201M ($1772/tonne) is at the upper end of FLNG benchmarks.

### 2026-03-23 — Smeaheia CO2 injection hydrogen accumulation analysis
**Type:** B (Process)
**Keywords:** accumulation, beckm, concern, confirmed, cross, engineering, feasibility, four, gerg, hydrogen
**Solution:** task_solve/2026-03-23_smeaheia_co2_injection_hydrogen_accumulation_analysis/step2_analysis/01_hydrogen_accumulation_analysis.ipynb, task_solve/2026-03-23_smeaheia_co2_injection_hydrogen_accumulation_analysis/step2_analysis/02_wellbore_simulation.ipynb, task_solve/2026-03-23_smeaheia_co2_injection_hydrogen_accumulation_analysis/step2_analysis/03_nip_enhanced_analysis.ipynb
**Notes:** Hydrogen accumulation in the gas phase is a CONFIRMED engineering concern for Smeaheia, validated by four independent EOS models and cross-referenced with the Smeaheia feasibility study which itself identified two-phase CO₂ risk at the Beta structure...

### 2026-03-21 — compressor_train_analysis
**Type:** B (Process)
**Keywords:** above, assessed, baseline, booster, capex, compressor, compressordesignfeasibilityreport, east, extends, feasibility
**Solution:** task_solve/2026-03-21_compressor_train_analysis/step2_analysis/Pablos_flowasscalcs.ipynb, task_solve/2026-03-21_compressor_train_analysis/step2_analysis/Untitled-1.ipynb, task_solve/2026-03-21_compressor_train_analysis/step2_analysis/Untitled-2.ipynb
**Notes:** The booster compressor + precooler installation is FEASIBLE and extends Troll East production from 2041 to 2050 (+9 years). Incremental recovery of 131 GSm³ (107% above baseline) at a CAPEX of ~$36M. Value of incremental gas at 1.

### 2026-03-20 — H2 properties data comparison
**Type:** D (Standards)
**Keywords:** aard, agrees, closely, compared, comparison, data, densitometer, density, enhanced, experimental
**Solution:** task_solve/2026-03-20_h2_properties_data_comparison/step2_analysis/01_h2_density_comparison.ipynb
**Notes:** Overall AARD: REFPROP=1.8408%, NeqSim Std GERG-2008=2.0790%, NeqSim GERG-2008-H2=2.2120%, NeqSim SRK=1.9948%. NeqSim standard GERG-2008 agrees closely with REFPROP (AARD=0.6221%).

### 2026-03-19 — Utsira Nord Floating Wind Class A Concept Study
**Type:** G (Workflow)
**Keywords:** class, commercial, concept, cost, costs, current, farm, floati, floating, foundation
**Solution:** task_solve/2026-03-19_utsira_nord_floating_wind_class_a_concept_study/step2_analysis/01_design_basis_and_site.ipynb, task_solve/2026-03-19_utsira_nord_floating_wind_class_a_concept_study/step2_analysis/02_wind_resource_and_aep.ipynb, task_solve/2026-03-19_utsira_nord_floating_wind_class_a_concept_study/step2_analysis/03_electrical_system_design.ipynb
**Notes:** The project LCOE of ~2141 NOK/MWh (202 EUR/MWh) reflects the pre-commercial cost level of floating offshore wind. At current costs, the project requires substantial government CfD/subsidy support (~2000 NOK/MWh) to achieve economic viability.

### 2026-03-18 — umoe_composites_300bar_cng_tank_filling_temperature
**Type:** B (Process)
**Keywords:** approximately, classic, composites, filling, hours, lean, limit, maximum, methane, mixing
**Solution:** task_solve/2026-03-18_umoe_composites_300bar_cng_tank_filling_temperature/step2_analysis/01_filling_simulation.ipynb, task_solve/2026-03-18_umoe_composites_300bar_cng_tank_filling_temperature/step2_analysis/02_literature_review.ipynb, task_solve/2026-03-18_umoe_composites_300bar_cng_tank_filling_temperature/step2_analysis/03_benchmark_validation.ipynb
**Notes:** Filling the Umoe Composites 300 bar Type IV tank from 20.0 to 300.0 bar at 247.5 Sm3/day takes approximately 52 hours. The maximum gas temperature reaches 31.0 C, which is within the ISO 11119-3 limit of 85.0 C with a margin of 54.0 C.

### 2026-03-12 — turboexpander modification evaluation for future operation
**Type:** B (Process)
**Keywords:** across, barg, below, class, declining, decreases, drops, evaluation, feasible, future
**Solution:** task_solve/2026-03-12_turboexpander_modification_evaluation_for_future_operation/step2_analysis/01_tex_performance.ipynb, task_solve/2026-03-12_turboexpander_modification_evaluation_for_future_operation/step2_analysis/02_seal_gas_condensation.ipynb, task_solve/2026-03-12_turboexpander_modification_evaluation_for_future_operation/step2_analysis/03_uncertainty_risk.ipynb
**Notes:** TEX PERFORMANCE: Feasible 2025-2029 (inlet P > 48 barg). Infeasible from 2030 when pressure ratio drops below ~1.05. Speed DECREASES from ~6950 to ~4500 rpm â€” no overspeed risk. TEX provides 5-8 degC advantage over JT valve through 2029.

### 2026-03-11 — TPG4230 Field Development and Operations Learning Material
**Type:** G (Workflow)
**Keywords:** aspects, assurance, chain, characterization, complete, compress, course, covering, covers, design
**Solution:** task_solve/2026-03-11_tpg4230_field_development_and_operations_learning_material/step2_analysis/Module_01_Introduction_and_Value_Chain.ipynb, task_solve/2026-03-11_tpg4230_field_development_and_operations_learning_material/step2_analysis/Module_02_Flow_Performance.ipynb, task_solve/2026-03-11_tpg4230_field_development_and_operations_learning_material/step2_analysis/Module_03_Oil_Gas_Processing.ipynb
**Notes:** The learning material covers all key aspects of field development: reservoir fluid characterization, production flow performance, oil and gas processing, flow assurance, separator design, gas compression and pipeline hydraulics, production scheduling...

### 2026-03-07 — Ultima Thule Field Development - Class A Study
**Type:** G (Workflow)
**Keywords:** class, development, field, study, thule, ultima
**Solution:** task_solve/2026-03-07_UltimaThule_ClassA_study/
**Notes:** Task folder created; analysis in progress.


### 2025-07-24 — Process Optimization Enhancements: NIP-03, NIP-06, NIP-08, NIP-09 Implementation
**Type:** E (Feature)
**Keywords:** process optimization, rate-based absorber, SQP optimizer, multiphase flow, Hagedorn-Brown, Mukherjee-Brill, multi-variable adjuster, Onda correlation, Billet-Schultes, mass transfer, enhancement factor, damped successive substitution
**Solution:** src/main/java/neqsim/process/equipment/absorber/RateBasedAbsorber.java, src/main/java/neqsim/process/util/optimizer/SQPoptimizer.java, src/main/java/neqsim/process/equipment/pipeline/PipeHagedornBrown.java, src/main/java/neqsim/process/equipment/pipeline/PipeMukherjeeAndBrill.java, examples/notebooks/process_optimization_enhancements.ipynb
**Notes:** Implemented four NIPs from process optimization review. NIP-06: RateBasedAbsorber with Onda 1968 and Billet-Schultes 1999 mass transfer correlations, Hatta/Van Krevelen enhancement factors (6 tests). NIP-08: SQPoptimizer with active-set SQP for constrained process optimization (5 tests). NIP-03: PipeHagedornBrown and PipeMukherjeeAndBrill multiphase flow correlations (8 tests). NIP-09: MultiVariableAdjuster convergence fix — replaced Broyden accelerator with damped successive substitution (α=0.1) after Broyden caused oscillation/divergence due to wrong Jacobian sign. All 23 tests pass. Docs updated: absorbers.md, sqp_optimizer.md, multiphase_flow_correlations.md, adjusters.md, CHANGELOG_AGENT_NOTES.md.

### 2026-04-17 — cDFT Surface Tension: Kernel Correction, Mixture Extension, Paper
**Type:** E (Feature)
**Keywords:** cDFT, surface tension, interfacial tension, density functional theory, kernel range, mixture IFT, Peng-Robinson, SRK, predictive, lambda correlation, acentric factor, critical correction, Miqueu, gradient theory, Parachor, Fluid Phase Equilibria
**Solution:** src/main/java/neqsim/thermo/util/LCSF/surfacetension/CDFTSurfaceTension.java, task_solve/2026-04-17_cdft_surface_tension_paper_kernel_correction_and_mixture_extension/, neqsim-paperlab/papers/cdft_surface_tension_2026/
**Notes:** Implemented predictive cDFT surface tension from cubic EOS. Three proposals: (A) kernel range correction λ(ω) = 0.749 − 0.740ω reduces AAD from 41.5% to 9.5% for 8 pure components (beats GT 12.8%, Parachor 17.6%; Miqueu GT 2.2% still best). (B) Mixture solver with shared-δ tanh profiles and cross kernels achieves 37.6% AAD for CH4/C3H8 at 277.6K (vs 61.7% Parachor). (C) Paper manuscript for Fluid Phase Equilibria with 4 figures, 3 tables. 27+ tests across 6 test classes. Key insight: critical exponent correction (1−Tr)^(−0.24) essential near Tc.
### 2026-04-18 — Systematic "hardcoded phase 0" bug elimination in two-phase flow
**Type:** E (Feature/Bugfix)
**Keywords:** two-phase flow, mass transfer, heat transfer, friction factor, Reynolds number, velocity, phase index, interphase transport, Krishna-Standart, film model, non-equilibrium, pipeline condensation, stratified flow, slug flow, droplet flow, stirred cell
**Solution:** src/main/java/neqsim/fluidmechanics/flownode/ (multiple files across 3 rounds)
**Notes:** Found and fixed 29 bugs across 3 audit rounds. Root cause: methods accepting `int phase`/`int phaseNum` parameters internally called `getReynoldsNumber()`, `getVelocity()`, or `calcWallFrictionFactor(0, node)` without passing the phase index through. These default to phase 0 (gas), making liquid-phase (1) calculations use incorrect gas-phase Reynolds numbers, velocities, and friction factors. Fixed files: NonEquilibriumFluidBoundary, ReactiveKrishnaStandartFilmModel, KrishnaStandartFilmModel, TwoPhaseFixedStaggeredGridSolver, InterphaseStratifiedFlow, TwoPhaseFlowNode, InterphaseDropletFlow, InterphaseSlugFlow, InterphaseTransportCoefficientBaseClass, MultiPhaseFlowNode, InterphasePipeFlow, InterphaseStirredCellFlow. Also added NaN guards, divide-by-zero protections, convergence fixes, and dead code cleanup. All fluidmechanics tests pass.

### 2026-06-18 — TwoFluidPipe transient & pressure gradient benchmark and fixes
**Type:** E (Feature)
**Keywords:** TwoFluidPipe, transient, multiphase, two-fluid model, pressure gradient, benchmark, McAdams viscosity, Beggs Brill, holdup, friction factor, Haaland, pipeline
**Solution:** src/main/java/neqsim/process/equipment/pipeline/TwoFluidPipe.java, src/test/java/neqsim/process/equipment/pipeline/TwoFluidPipeBenchmarkTest.java
**Notes:** Fixed transient inlet BC override (isTransientMode flag), outlet pressure capture bug, improved viscosity model (McAdams quality-based harmonic averaging). Added 19 benchmark tests in 8 categories validating against PipeBeggsAndBrills and literature. Single-phase gas ratio 0.98, two-phase GLR sweep 0.81–1.33, vertical riser gravity 1.04 bar matches ρgH, D⁻⁵ diameter scaling ratio 33.7. Transient holdup evolution 0.19→0.09 after flow step-change now works correctly.

### 2026-03-14 — Fix IEC 60534 gas valve sizing: use standard volumetric flow (issue #1918)
**Type:** D (Standards)
**Keywords:** valve sizing, IEC 60534, Cv, Kv, gas valve, standard flow, actual flow, control valve, throttling valve, choked flow, N9
**Solution:** src/main/java/neqsim/process/mechanicaldesign/valve/ControlValveSizing_IEC_60534.java, src/main/java/neqsim/process/mechanicaldesign/valve/ControlValveSizing_IEC_60534_full.java, src/test/java/neqsim/process/mechanicaldesign/valve/ControlValveSizingTest.java
**Notes:** Gas valve Cv was severely underestimated (~98% too low at 50 bara) because IEC 60534 equation was applied with actual volumetric flow instead of standard volumetric flow (273.15 K, 101.325 kPa). Fix: convert Q_actual to Q_std = Q_actual × (P₁/P_std) × (T_std/T₁) / Z before applying the IEC formula. Fixed in sizeControlValveGas(), calculateFlowRateFromKvAndValveOpeningGas(), and calculateValveOpeningFromFlowRateGas() in both base and _full classes. Added regression test matching Python fluids library result (Cv ≈ 16.2 for 10000 kg/hr methane at 50 bara). Liquid valves were not affected.

### 2026-03-10 — Process architecture improvements: stream introspection, named controllers, connections, unified elements
**Type:** E (Feature)
**Keywords:** architecture, ProcessElementInterface, ProcessConnection, MultiPortEquipment, getInletStreams, getOutletStreams, controller map, named controllers, runTransient, controller scan, getAllElements, DEXPI, topology, stream introspection, connections
**Solution:** src/main/java/neqsim/process/ProcessElementInterface.java, src/main/java/neqsim/process/equipment/MultiPortEquipment.java, src/main/java/neqsim/process/processmodel/ProcessConnection.java, src/main/java/neqsim/process/equipment/ProcessEquipmentInterface.java, src/main/java/neqsim/process/equipment/ProcessEquipmentBaseClass.java, src/test/java/neqsim/process/processmodel/ProcessArchitectureTest.java
**Notes:** Six backward-compatible architecture improvements motivated by DEXPI integration friction. (1) Stream introspection: `getInletStreams()`/`getOutletStreams()` on ProcessEquipmentInterface with default empty lists; overridden in TwoPortEquipment, Separator, ThreePhaseSeparator, Mixer, Splitter — all return unmodifiable lists. (2) Named controller map: `addController(tag, ctrl)`, `getController(tag)`, `getControllers()` on ProcessEquipmentBaseClass alongside legacy `setController()`. (3) ProcessElementInterface: unified marker extending NamedInterface + Serializable; adopted by ProcessEquipmentInterface, MeasurementDeviceInterface, ControllerDeviceInterface. (4) Controller scan in runTransient: explicit loop over system-level controllerDevices after equipment loop. (5) ProcessConnection: typed connection metadata (MATERIAL/ENERGY/SIGNAL) with `ProcessSystem.connect()` and `getConnections()`. (6) MultiPortEquipment: abstract base class for multi-inlet/outlet equipment. 173 tests passing (14 architecture + 42 DEXPI + 117 core process). Documentation updated in process_system.md, extending_process_equipment.md, controllers.md, dynamic_simulation_guide.md, CODE_PATTERNS.md, CONTEXT.md.

### 2026-03-10 — DEXPI review: multi-outlet nozzles, stream identity matching, namespace support
**Type:** E (Feature)
**Keywords:** DEXPI, multi-outlet, nozzle, separator, stream identity, connection, pass-through, namespace, absorber, stripper, column subtype, instrument rename, DexpiStreamUtils, DexpiXmlWriter, DexpiEquipmentFactory, DexpiSimulationBuilder
**Solution:** src/main/java/neqsim/process/processmodel/dexpi/DexpiStreamUtils.java, src/main/java/neqsim/process/processmodel/dexpi/DexpiXmlWriter.java, src/main/java/neqsim/process/processmodel/dexpi/DexpiEquipmentFactory.java, src/main/java/neqsim/process/processmodel/dexpi/DexpiSimulationBuilder.java, src/main/java/neqsim/process/processmodel/dexpi/DexpiXmlReader.java
**Notes:** Implemented all 11 recommendations from DEXPI code review. (1) DexpiStreamUtils: shared outlet-stream resolution utility replacing duplicated reflection-based code. (2) Reflection removal: outlet access via TwoPortEquipment/Separator casts instead of Method.invoke. (3) Multi-outlet nozzles: writer creates 2 outlet nozzles for Separator, 3 for ThreePhaseSeparator. (4) Stream identity matching: connections built by matching System.identityHashCode of inlet/outlet streams; registerPassThroughStreams handles wrapper Streams that delegate getFluid(). (5) Column subtype: DexpiEquipmentFactory detects "absorb"/"strip" in DEXPI class to configure condenser/reboiler flags. (6) Namespace-aware parsing: setNamespaceAware(boolean) on builder and reader. (7) Instrument renaming: applyAutoInstrumentation now calls setName() on transmitters and controllers (ControllerDeviceBaseClass cast). 4 new tests (cyclic topology, multi-outlet separator, 2 round-trip profile); 68 tests total, all passing.

### 2026-03-10 — DEXPI round-trip export, cycle detection, and column support
**Type:** E (Feature)
**Keywords:** DEXPI, P&ID, round-trip, export, XML writer, connection, nozzle, reverse mapping, cycle detection, distillation column, simulation results, instrument wiring, DexpiXmlWriter, DexpiXmlWriterTest
**Solution:** src/main/java/neqsim/process/processmodel/dexpi/DexpiXmlWriter.java, src/test/java/neqsim/process/processmodel/dexpi/DexpiXmlWriterTest.java, src/main/java/neqsim/process/processmodel/dexpi/DexpiTopologyResolver.java, src/main/java/neqsim/process/processmodel/dexpi/DexpiEquipmentFactory.java, src/main/java/neqsim/process/processmodel/dexpi/DexpiSimulationBuilder.java
**Notes:** Completed 7 round-trip (DEXPI↔NeqSim) improvements. DexpiXmlWriter gains Connection/Nozzle export (buildConnections, appendNozzle, appendConnectionSystem), native equipment reverse mapping (reverseMapComponentClass maps Separator→VesselForStorage, Compressor→CompressorUnit, etc.), and simulation results export (appendSimulationResults writes temperature/pressure/flow as GenericAttributes). DexpiTopologyResolver gains hasCycle() via DFS-based detection. DexpiEquipmentFactory gains createColumn() for DistillationColumn instantiation with NumberOfTrays and FeedTray sizing attributes. DexpiSimulationBuilder instrument tag wiring replaced setName() calls with logging-based tag association (ControllerDeviceInterface/MeasurementDeviceInterface lack setName). Fixed instanceof ordering bug: Cooler extends Heater, so Cooler must be checked before Heater in reverseMapComponentClass. 15 new tests (11 in DexpiXmlWriterTest, 2 cycle-detection, 2 column-creation); 64 tests total, all passing.

### 2026-03-10 — DEXPI topology resolver, equipment factory, and simulation builder
**Type:** E (Feature)
**Keywords:** DEXPI, P&ID, topology, nozzle, connection, equipment factory, simulation builder, mapping loader, sizing, DexpiTopologyResolver, DexpiEquipmentFactory, DexpiSimulationBuilder, DexpiMappingLoader, GenericAttribute, Kahn, topological sort
**Solution:** src/main/java/neqsim/process/processmodel/dexpi/DexpiTopologyResolver.java, src/main/java/neqsim/process/processmodel/dexpi/DexpiEquipmentFactory.java, src/main/java/neqsim/process/processmodel/dexpi/DexpiSimulationBuilder.java, src/main/java/neqsim/process/processmodel/dexpi/DexpiMappingLoader.java
**Notes:** Resolved 7 critical gaps in DEXPI implementation. DexpiTopologyResolver parses Nozzle/Connection/Equipment XML elements into a directed graph, collapses inline piping components (valves, reducers) to equipment-level edges, and produces topological ordering via Kahn's algorithm. DexpiEquipmentFactory converts DexpiProcessUnit placeholders to real NeqSim equipment (Separator, Compressor, Pump, HeatExchanger, Heater, Cooler, Valve, Expander, Mixer, Splitter) with sizing attributes applied. DexpiSimulationBuilder is a fluent builder API: setFluidTemplate/setFeedPressure/setFeedTemperature/setFeedFlowRate/setAutoInstrument → build() returns runnable ProcessSystem. DexpiMappingLoader provides thread-safe cached loading of .properties mapping files from classpath. DexpiMetadata expanded with 10 sizing constants. DexpiProcessUnit gains sizingAttributes map and dexpiId. Equipment mapping expanded from ~30 to ~65 entries; piping component mapping from ~15 to ~28. 49 tests across 7 test classes (all passing).

### 2026-03-10 — DynamicProcessHelper utility for steady-state to dynamic conversion
**Type:** E (Feature)
**Keywords:** dynamic, transient, simulation, DynamicProcessHelper, transmitter, PID, controller, instrument, auto-instrument, pressure, level, flow, temperature, control loop, PC, LC, FC, TC, runTransient, setCalculateSteadyState
**Solution:** src/main/java/neqsim/process/util/DynamicProcessHelper.java, src/test/java/neqsim/process/util/DynamicProcessHelperTest.java, docs/process/dynamic-simulation.md
**Notes:** Utility that converts a sized steady-state ProcessSystem into a dynamic simulation. Auto-creates transmitters (PT, LT, TT) and PID controllers (PC, LC, WLC) by scanning equipment and matching stream identity to downstream valves. Handles Separator, ThreePhaseSeparator, Compressor, Heater, Cooler. Convenience methods for addFlowController() and addTemperatureController(). Default PID tuning with per-type customization. Key gotcha: transmitters implement MeasurementDeviceInterface (NOT ProcessEquipmentInterface), so ProcessSystem.add(MeasurementDeviceInterface) must be used. 10 tests passing.

### 2026-03-10 — Implement InstrumentDesign framework
**Type:** E (Feature)
**Keywords:** instrument, design, ISA, SIL, I/O, DCS, SIS, instrumentation, ISA-5.1, IEC 61508, IEC 61511, API 670, safety, compressor, separator, heat exchanger, pipeline, valve, tag number, cabinet sizing, cost estimation
**Solution:** src/main/java/neqsim/process/instrumentdesign/
**Notes:** Mirrors ElectricalDesign pattern. Base class InstrumentDesign with InstrumentSpecification (ISA-5.1 data sheets) and InstrumentList (I/O counting, cost aggregation, tag generation). Equipment-specific designs for separator (PT×2 + PSH + TT + LT×2 + LSH + LSLL + ZT×2; three-phase adds interface LT + water ZT), compressor (API 617/670 suite: ~18 instruments including VT×4 vibration probes, anti-surge FT/FCV, bearing TTs, lube oil PT/PSLL), heat exchanger (auto-detects shell-and-tube/air cooler/electric heater), pipeline (pig detection ZS×2, leak detection PSLL, metering FT), and valve (ZT + ZC; safety valves add XV + ZSO/ZSC). System-level SystemInstrumentDesign aggregates across ProcessSystem and sizes DCS (~16 ch/card, ~16 cards/cab), SIS (~8 ch/card, ~8 cards/cab), and marshalling cabinets. Integrated via ProcessEquipmentInterface.getInstrumentDesign() and ProcessSystem.getSystemInstrumentDesign(). 12 tests passing.

### 2026-03-09 — H₂S/CO₂ Distribution Between Gas, Oil, Water — EOS Model Comparison
**Type:** A (Property)
**Keywords:** H2S, CO2, acid gas, distribution, solubility, water, oil, gas, produced water, brine, salinity, SRK, PR, CPA, electrolyte CPA, chemical reactions, three-phase, salting-out, pH, NACE MR0175, sour service, model selection, decision matrix, Duan Sun, Carroll Mather, Soreide Whitson, Monte Carlo, benchmark
**Solution:** task_solve/2026-03-09_h2s_co2_distribution_gas_oil_water_produced_water_eos_comparison/
**Notes:** Systematic comparison of 4 EOS models (SRK, PR, SRK-CPA, Electrolyte-CPA) across 10 scenarios for acid gas partitioning. Critical findings: (1) SRK/PR give near-zero CO₂ solubility in water — unsuitable for acid gas-water systems; (2) Only Electrolyte-CPA correctly predicts three phases (gas/oil/aqueous); (3) chemicalReactionInit() is mandatory for pH, salting-out, and ionic speciation; (4) H₂S shows retrograde solubility (max 60-70°C); (5) Water content is dominant sensitivity (1.42% swing in Monte Carlo). Benchmark: 5/5 tests PASS (13.8-25.0% deviation vs Duan & Sun 2003, Carroll & Mather 1991). Monte Carlo N=300: H₂S aqueous P10/P50/P90 = 0.51/0.92/1.35%. Decision matrix maps 12 applications to recommended models. 6 NIPs proposed (acid gas report, produced water builder, pH calculator, salting-out DB, compliance checker, model advisor).

### 2026-03-09 — CO2 Corrosion Analyzer with Electrolyte CPA pH
**Type:** E (Feature)
**Keywords:** corrosion, CO2, pH, electrolyte CPA, de Waard-Milliams, NORSOK M-506, chemical reaction equilibrium, H3O+, carbonic acid, HCO3-, CCS, pipeline, corrosion rate, scale, FeCO3, inhibitor, brine, NaCl, severity, aqueous speciation
**Solution:** `src/main/java/neqsim/pvtsimulation/flowassurance/CO2CorrosionAnalyzer.java`, `src/test/java/neqsim/pvtsimulation/flowassurance/CO2CorrosionAnalyzerTest.java`, `examples/notebooks/CO2_Corrosion_Analysis_ElectrolyteCPA.ipynb`
**Notes:** Facade class coupling electrolyte CPA EOS (SystemElectrolyteCPAstatoil) with de Waard-Milliams corrosion model and ScalePredictionCalculator. Key insight: must call `chemicalReactionInit()` → `createDatabase(true)` → `setMixingRule(10)` → `init(0)` to enable aqueous chemical equilibrium (CO2 + 2H2O → HCO3- + H3O+). Without this, pH returns 7.0 (no H3O+ component). The analyzer auto-creates the electrolyte system, runs flash with chemical reactions, extracts rigorous pH from H3O+ activity, and feeds it into the corrosion model. Supports temperature/pressure sweeps, brine (Na+/Cl-), inhibitor efficiency, and JSON reporting. 12/12 tests passing.

### 2026-03-09 — Water Solubility in Gas and Liquid CO2 Phase Behaviour
**Type:** A (Property)
**Keywords:** water, solubility, CO2, CPA, SRK, PR, equation of state, phase equilibrium, CCS, carbon capture, dehydration, pipeline, gas phase, liquid phase, supercritical, mutual solubility, Wiebe, Gaddy, Bamberger, Spycher, King, Song, Kobayashi, benchmark, validation, ISO 27913, ppmv, water content
**Solution:** task_solve/2026-03-09_water_solubility_in_gas_and_liquid_co2_phase_behaviour/
**Notes:** Investigated water solubility in CO2 across gas, liquid, and supercritical conditions (5-200 bar, 10-80 C) using CPA EOS (SystemSrkCPAstatoil, mixing rule 10). Key findings: gas-phase water content decreases with pressure (Raoult's law dilution), liquid CO2 has low solubility (1000-3000 ppmv), characteristic minimum at CO2 saturation pressure. Benchmark: 9/13 points within 30% tolerance, mean error 25.2%. CPA under-predicts at 60 C (40-50% error vs Bamberger data). CPA outperforms SRK/PR at high pressures. Monte Carlo (N=300): P10/P50/P90 = 2986/3616/4253 ppmv for CCS conditions — dehydration always required (100% exceed 500 ppmv ISO limit). Tornado: EOS model uncertainty dominates (1729 ppmv swing), then pressure (1068), then temperature (945). Overall risk: Medium (2 high, 3 medium, 2 low).

### 2026-03-09 — Sulfur Deposition Analysis
**Type:** B (Process)
**Keywords:** sulfur, S8, deposition, desublimation, Joule-Thomson, JT cooling, backflow, letdown, valve, H2S, Draupner, pressure reduction, solid flash, GibbsReactor, SulfurDepositionAnalyser, preheating, mitigation
**Solution:** task_solve/2026-03-09_draupner_backflow_letdown_sulfur_deposition_analysis/
**Notes:** Analysed elemental sulfur deposition in a backflow letdown system (70→15 bara). JT cooling gives ~-20°C outlet (JT coeff 0.46-0.58 K/bar). 100% of Monte Carlo scenarios (N=300) produce solid S8. Primary mechanism is desublimation, not chemical reaction. Air ingress (O2) contributes via H2S oxidation at Gibbs equilibrium. Preheating helps but S8 solid persists even at 100°C preheat with >0.01 ppb S8 feed. Used SRK EOS, ThrottlingValve, TPSolidflash, GibbsReactor, SulfurDepositionAnalyser. 9/9 benchmarks PASS (JT coefficients within 15%, S8 solubility within literature order-of-magnitude). Overall risk: High (5 high, 4 medium, 1 low risks).

### 2026-03-08 — Mercury Removal in LNG Pre-Treatment — NeqSim Chemisorption Model
**Type:** B (Process), F (Design)
**Keywords:** mercury, Hg, removal, guard bed, chemisorption, CuS, sorbent, adsorber, NTU, Ergun, packed bed, LNG, pre-treatment, Snøhvit, HLNG, mass transfer zone, breakthrough, transient, bed lifetime, mechanical design, ASME VIII, cost estimation, CAPEX, OPEX, sorbent replacement, fuel gas strategy
**Solution:** `src/main/java/neqsim/process/equipment/adsorber/MercuryRemovalBed.java`, `src/test/java/neqsim/process/equipment/adsorber/MercuryRemovalBedTest.java`, `task_solve/2026-03-08_mercury_removal_lng_pretreatment/`
**Notes:**
- MercuryRemovalBed: NTU-based steady-state + cell-by-cell transient PDE (upwind scheme, CFL sub-stepping), Ergun pressure drop, Arrhenius kinetics, bypass/degradation, bed lifetime estimation
- MercuryRemovalMechanicalDesign: ASME VIII Div 1, SA-516-70, hoop stress wall thickness, weight breakdown, BOM
- MercuryRemovalCostEstimate: Factored CAPEX (PEC→BMC→TMC→GRC), sorbent replacement OPEX
- 24/24 unit tests passing covering construction, steady-state, transient, degradation, lifetime, JSON, mechdesign, cost
- Benchmark validated against analytical NTU formula, hand-calculated Ergun dP, literature bed lifetime (Carnell 2007, Eckersley 2010)
- Monte Carlo 250 iterations with full NeqSim simulation per iteration; tornado on 6 uncertain parameters

### 2026-03-08 — NeqSim-based Monte Carlo uncertainty and risk evaluation for NPV
**Type:** G (Workflow)
**Keywords:** uncertainty, Monte Carlo, risk, NPV, GIP, resource estimate, tornado, sensitivity, ISO 31000, risk matrix, NeqSim simulation, SRK EOS, SimpleReservoir, Beggs & Brill, SURFCostEstimator, triangular distribution, P10 P50 P90, field development
**Solution:** `task_solve/2026-03-07_npv_calculation_of_field_development_subsea_tieback/step2_analysis/03_uncertainty_risk_analysis.ipynb`, `step3_report/generate_report.py` (Sections 9-10)
**Notes:**
- Full NeqSim process simulation (SRK EOS, SimpleReservoir, WellFlow, Beggs & Brill pipeline) in every Monte Carlo iteration
- 7 uncertain parameters: GIP volume (0.65-1.45 GSm3), reservoir pressure (120-170 bara), plateau rate (7-12 MSm3/d), gas price, CAPEX multiplier, OPEX, discount rate
- N=200 iterations, ~5.5 min total runtime with full NeqSim re-simulation per iteration
- Results: P10=-22, P50=3,352, P90=7,086 MNOK; P(NPV<0)=10.5%
- Resource estimate uncertainty: GIP P10=105, P50=135, P90=169 GSm3; Recovery P10=45%, P50=57%, P90=66%
- Tornado: gas price is dominant driver (swing 10,990 MNOK), followed by discount rate (5,744) and plateau rate (3,833)
- Risk register: 8 risks across Market/Technical/Cost/Schedule/HSE/Regulatory, overall: High
- Report generator updated with Sections 9 (Uncertainty Analysis) and 10 (Risk Evaluation) — auto-populated from results.json
- Updated AGENTS.md and copilot-instructions.md to make uncertainty/risk MANDATORY for all AI tasks

### 2026-03-04 — Sulfur deposition and corrosion analysis system
**Type:** E (Feature)
**Keywords:** sulfur, S8, H2S, deposition, precipitation, solubility, Gibbs reactor, Claus, FeS, corrosion, NACE, sour gas, solid flash, TPSolidflash, SulfurDepositionAnalyser, GibbsReactor, SO2, pipeline, subsea, onshore
**Solution:** `src/main/java/neqsim/process/equipment/reactor/SulfurDepositionAnalyser.java`, `src/test/java/neqsim/process/equipment/reactor/SulfurDepositionAnalyserTest.java`, `examples/sulfurtask/SulfurDepositionAnalysis.ipynb`, `docs/chemicalreactions/sulfur_deposition_analysis.md`
**Notes:**
- New `SulfurDepositionAnalyser` unit operation combining Gibbs equilibrium, TP-solid flash, temperature sweep, and corrosion assessment in a single run()
- Added FeS, Fe2O3, FeS2 species to GibbsReactDatabase.csv
- Corrosion module: NACE MR0175 sour severity classification, FeS/SO2/H2SO4 risk assessment
- Temperature sweep identifies sulfur deposition onset temperature
- 6 tests passing (solubility, equilibrium, corrosion, full analysis, edge cases, JSON output)
- Jupyter notebook (15 sections): solubility maps, saturation envelope, Gibbs reactor sweeps, O2/H2S sensitivity, pipeline simulation, onshore processing risk, H2S sensitivity
- Uses neqsim_dev_setup for notebook JVM bootstrap

### 2026-03-07 — CNG tank filling and emptying temperature estimation (workflow test)
**Type:** B (Process)
**Keywords:** CNG, tank, filling, emptying, depressurization, pressurization, temperature, wall temperature, MDMT, heat transfer, transient wall, VesselDepressurization, X80 steel, energy balance, Churchill-Chu, natural convection
**Solution:** `task_solve/2026-03-07_cng_tank_filling_and_emptying_temperature_estimation/step2_analysis/CNG_Tank_Temperature_Estimation.ipynb`
**Notes:**
- Full end-to-end test of solve.task workflow: task creation, scope/research, simulation notebook, results.json, Word+HTML reports
- Tank: 19m height, 1.066m OD, 33.5mm wall (X80 steel), vertical with hemispheric caps
- Gas: lean natural gas (90% CH4, 5% C2, 2% C3, 0.5% iC4, 0.5% nC4, 1% N2, 1% CO2), SRK EOS
- Filling: 20→250 bar at 1783.4 Sm3/day, duration 51.2 hr, max gas T = 31.0°C
- Emptying: 250→20 bar at 1783.4 Sm3/day, duration 50.8 hr, min gas T = 1.4°C, min wall T = 2.0°C
- MDMT check: margin 48.0°C above -46°C, PASS
- HTC model comparison: ADIABATIC min T = -91.7°C, CALCULATED = 1.7°C, TRANSIENT_WALL = 1.4°C
- No liquid dropout during emptying
- dt=10s constant for VU-flash stability, recordInterval=60 (every 10 min)
- Report auto-generated from results.json + task_spec.md via generate_report.py

### 2025-07-17 — CNG tank temperature estimation improvements and Jupyter notebooks
**Type:** E (Feature)
**Keywords:** CNG, tank, filling, emptying, depressurization, VU-flash, heat transfer, Churchill-Chu, Gnielinski, natural convection, mixed convection, transient wall, VesselDepressurization, temperature estimation, MDMT
**Solution:** `src/main/java/neqsim/process/equipment/tank/VesselDepressurization.java`, `examples/CNGtankmodelling/CNG_FillingSimulation.ipynb`, `examples/CNGtankmodelling/CNG_EmptyingSimulation.ipynb`, `examples/CNGtankmodelling/CNG_GasProperties_HTC.ipynb`
**Notes:**
- 6 Java improvements to VesselDepressurization: fixed flow rate filling, filling energy balance with VU-flash, external HTC (Churchill-Chu + Gnielinski), target pressure control, hemispheric geometry, mole-scaling fix
- Bug fixes: (1) Cp*1000 in 3 HTC methods inflated coefficients 1000x, (2) OptimizedVUflash static variables contaminating between calls, (3) temperature guards for non-physical VU-flash results
- Critical finding: VU-flash convergence fails when dt switches from 10s to 60s at low pressure — use constant dt=10s
- Filling: 20→250 bar in 52 hr, gas T: 15→30°C, no liquid dropout
- Emptying: 250→20 bar in 57 hr, min gas T: -0.4°C, MDMT check passed (-46°C margin)
- Ambient sensitivity: even at -20°C ambient, min wall T = -34.4°C (above MDMT)
- HT model comparison: ADIABATIC=-94°C, CALCULATED=+9°C, TRANSIENT_WALL=-0.4°C
- Cp notebook bug: getMolarMass() returns kg/mol, so * 1000 in Cp conversion was wrong (same root cause as Java Cp*1000 bug)
- 41 unit tests passing, devtools workflow used (target/classes, no JAR packaging)

### 2026-03-01 — Well mechanical design and cost estimation system
**Type:** F (Design)
**Keywords:** well, subsea, casing, tubing, mechanical design, NORSOK D-010, API 5CT, cost estimation, drilling, completion, barrier verification, WellMechanicalDesign, WellDesignCalculator, WellCostEstimator
**Solution:** `src/main/java/neqsim/process/mechanicaldesign/subsea/WellMechanicalDesign.java`, `WellDesignCalculator.java`, `WellCostEstimator.java`, `src/test/java/.../WellMechanicalDesignTest.java`
**Notes:**
- SubseaWell was the only subsea equipment type WITHOUT a mechanical design class
- Added WellType, CompletionType, RigType enums to SubseaWell
- Three-layer pattern: SubseaWell → WellMechanicalDesign → WellDesignCalculator + WellCostEstimator
- Casing design: burst/collapse/tension per API Bull 5C3, supports 14 casing grades (H40 through 25Cr)
- Well barrier verification per NORSOK D-010 two-barrier principle
- Cost estimation with regional factors (Norway 1.35x, GOM 1.0x, etc.)
- Wired into FieldDevelopmentCostEstimator via setWellParameters()
- CSV data files: WellCostData.csv, CasingProperties.csv
- 21 tests all passing
- Documentation: docs/process/well_mechanical_design.md

### 2026-03-01 — Task log and context system created
**Type:** E (Feature)
**Keywords:** context, documentation, workflow, onboarding, task-solving
**Solution:** `CONTEXT.md`, `docs/development/TASK_SOLVING_GUIDE.md`, `docs/development/TASK_LOG.md`
**Notes:** Created a 3-file context system to make repo-based task solving faster:
- `CONTEXT.md` — 60-second orientation (repo map, patterns, constraints)
- `TASK_SOLVING_GUIDE.md` — workflow for classifying and solving tasks
- `TASK_LOG.md` — this file, persistent memory across sessions

---

### 2026-03-10 — Electrical design: equipment-specific classes and system integration
**Type:** E (Feature)
**Keywords:** electrical design, separator, heater, cooler, pipeline, heat tracing, cathodic protection, system electrical design, load list, transformer sizing, emergency generator
**Solution:** `src/main/java/neqsim/process/electricaldesign/separator/SeparatorElectricalDesign.java`, `heatexchanger/HeatExchangerElectricalDesign.java`, `pipeline/PipelineElectricalDesign.java`, `system/SystemElectricalDesign.java`
**Notes:**
- Implemented Phases 2-3 of ELECTRICAL_DESIGN_PROPOSAL.md
- SeparatorElectricalDesign: models control valves, instrumentation, lighting, optional heat tracing (no shaft power)
- HeatExchangerElectricalDesign: auto-detects type (ELECTRIC_HEATER / AIR_COOLER / SHELL_AND_TUBE) from equipment class
- PipelineElectricalDesign: heat tracing (W/m × length), cathodic protection, instrumentation
- SystemElectricalDesign: plant-wide aggregation with utility/UPS loads, main transformer and emergency generator sizing
- Integrated into Separator (eager init), Heater/Cooler (lazy init), AdiabaticPipe and PipeBeggsAndBrills (lazy init)
- Added ProcessSystem.getSystemElectricalDesign() for one-call plant electrical summary
- 24 unit tests all passing in ElectricalDesignTest

### 2026-04-16 — Review Dynamic Process and Control Functionality
**Type:** G (Workflow)
**Keywords:** dynamic simulation, transient, PID controller, MPC, HYSYS Dynamics, K-Spice, safety chain, HIPPS, ESD, blowdown, VU-flash, DynamicProcessHelper, ProcessEventBus, alarm, runTransient, control, measurement device, valve, split-range, override, bumpless transfer, sequence control, SFC, distillation dynamics, heat exchanger dynamics, rotor inertia
**Solution:** task_solve/2026-04-16_review_dynamic_process_and_control_functionality/step1_scope_and_research/analysis.md
**Notes:** Comprehensive 45-feature comparison across 7 categories against 5 commercial simulators. NeqSim scores 63/90 (70%) vs HYSYS 79/90 (88%). Leads in 8 areas (VU-flash, safety chain, Monte Carlo risk, auto-instrumentation, water hammer, DEXPI export, event bus, specialised analysers). 10 NIPs proposed with 4-phase roadmap. Key gaps: explicit Euler only (no implicit/adaptive), no dynamic HX/column, no bumpless transfer, no split-range/override, no sequence control.

---

<!--
TEMPLATE — copy this block for each new entry:

### YYYY-MM-DD — Title
**Type:**
**Keywords:**
**Solution:**
**Notes:**

-->
