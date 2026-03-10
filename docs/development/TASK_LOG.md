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
**Type:** A (Property) | B (Process) | C (PVT) | D (Standards) | E (Feature) | F (Design)
**Keywords:** comma, separated, search, terms
**Solution:** Where the code lives (test file, notebook, source file)
**Notes:** Key decisions, gotchas, or results worth remembering
```

---

## Log

<!-- Add new entries at the top. Most recent first. -->

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

---

<!--
TEMPLATE — copy this block for each new entry:

### YYYY-MM-DD — Title
**Type:**
**Keywords:**
**Solution:**
**Notes:**

-->
