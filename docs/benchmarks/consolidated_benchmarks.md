---
title: "Consolidated Benchmark Results"
description: "Summary of all benchmark validations performed across NeqSim task-solving studies. Covers thermodynamic properties, phase equilibria, process simulation, cost estimation, and plant data comparisons."
---

# Consolidated Benchmark Results

This page summarizes benchmark validations from all task-solving studies in
`task_solve/`. Each entry links to the source task for full details.

For the curated trust dashboard with acceptance criteria, see [index.md](index.md).

---

## 1. Thermodynamic Property Validation (vs NIST)

| Property | Fluid | Conditions | NeqSim EOS | Max Error | Source Task |
|----------|-------|-----------|------------|-----------|-------------|
| Density | Methane | 200-350 bar, 0-50 °C | SRK | < 5% | `2026-03-07_cng_tank_filling` |
| Cp | Methane | 200-350 bar, 0-50 °C | SRK | < 8% | `2026-03-07_cng_tank_filling` |
| Viscosity | Methane | 200-350 bar, 0-50 °C | SRK | < 10% | `2026-03-07_cng_tank_filling` |
| Thermal conductivity | Methane | 200-350 bar, 0-50 °C | SRK | < 15% | `2026-03-07_cng_tank_filling` |
| Density | Natural gas | Pipeline conditions | SRK | < 3% | `2026-03-07_npv_subsea_tieback` |
| Z-factor | Natural gas | Pipeline conditions | SRK | < 2% | `2026-03-07_npv_subsea_tieback` |

**Key finding:** SRK EOS performs well for gas-phase density and compressibility
at pipeline conditions (< 3% error). Transport properties (viscosity, thermal
conductivity) show higher deviations at extreme pressures — consider PR or
GERG-2008 for custody transfer accuracy.

---

## 2. Phase Equilibria and Solubility

| System | Property | Reference Data | NeqSim EOS | Points | Pass Rate | Tolerance | Source Task |
|--------|----------|---------------|------------|--------|-----------|-----------|-------------|
| CO2–Water | CO2 solubility in water | Published experimental | SRK-CPA, Electrolyte-CPA | Multiple | Good | — | `2026-03-09_h2s_co2_distribution` |
| H2S–Water | H2S solubility in water | Published experimental | SRK-CPA | Multiple | Good | — | `2026-03-09_h2s_co2_distribution` |
| CO2–Water | Water solubility in CO2 | Wiebe & Gaddy (1941) | SRK-CPA | 13 | 69% (9/13) | 30% | `2026-03-09_water_solubility_co2` |
| CH4–Water | Methane solubility in water | Culberson & McKetta (1951), Lekvam & Bishnoi (1997), IUPAC | SRK-CPA | 18 | Good | NCS conditions | `2026-04-09_mimee_methane_emissions` |

**Key finding:** SRK-CPA handles gas solubility in water well for engineering
purposes. Water-in-CO2 predictions at high pressure show larger deviations
(30% tolerance needed) — use Electrolyte-CPA for improved accuracy in CCS
applications.

---

## 3. Process Simulation Benchmarks

| Equipment | Method | Reference | Max Error | Source Task |
|-----------|--------|-----------|-----------|-------------|
| Adsorber (mercury removal) | NTU-efficiency | Analytical formula | < 1% | `2026-03-08_mercury_removal_lng` |
| Packed bed | Ergun equation dP | Hand calculation | < 2% | `2026-03-08_mercury_removal_lng` |
| Packed bed | Bed lifetime | Mass balance estimate | Good agreement | `2026-03-08_mercury_removal_lng` |
| Pipeline | Pressure drop (Beggs & Brill) | Textbook correlation | < 5% | `2026-03-07_npv_subsea_tieback` |
| Sulfur deposition | Arrhenius S8 solubility | Literature correlation | Reasonable | `2026-03-09_draupner_sulfur` |
| Compressor train | Polytropic efficiency | Booster benchmarks | < 3% | `2026-03-21_compressor_train` |

**Key finding:** NeqSim process equipment models agree well with hand
calculations and textbook correlations. Pipeline pressure drop with
Beggs & Brill shows < 5% deviation from published correlations.

---

## 4. Cost Estimation Benchmarks

| Category | Reference Data | Agreement | Source Task |
|----------|---------------|-----------|-------------|
| SURF cost (NCS) | Industry benchmarks | Within typical range | `2026-03-07_npv_subsea_tieback` |
| Norwegian tax model | Hand calculation | Exact match | `2026-03-07_npv_subsea_tieback` |
| Well mechanical design | API 5C3 formulas | Verified per standard | `2026-03-07_npv_subsea_tieback` |

---

## 5. Plant Data Validation

| Facility | Measured Property | Data Source | Agreement | Source Task |
|----------|------------------|-------------|-----------|-------------|
| Generic platform | Methane emissions (dissolved gas) | Historian data | Good agreement at NCS conditions | `2026-04-09_mimee_methane_emissions` |
| Production facilities | Emission factors | Plant data comparison | Within operational range | `2026-04-17_mimee_code_update_verification` |

**Key finding:** NeqSim methane solubility predictions align with plant
historian data at typical Norwegian Continental Shelf conditions (50-150 bar,
30-80 °C, produced water systems).

---

## 6. Renewable Energy and Concept Studies

| Study | Benchmark Type | Reference | Source Task |
|-------|---------------|-----------|-------------|
| CNG tank filling | Temperature prediction | Churchill-Chu natural convection | `2026-03-07_cng_tank_filling` |
| CNG tank (composite) | Density predictions | NIST WebBook | `2026-03-18_composites_cng_tank` |
| Floating wind | Concept study metrics | Industry benchmarks | `2026-03-19_utsira_nord_wind` |

---

## Summary Statistics

| Category | Tasks | Total Data Points | Typical Accuracy |
|----------|-------|-------------------|-----------------|
| Thermo properties vs NIST | 4 | ~30 | < 5% (density), < 15% (transport) |
| Phase equilibria | 3 | ~40 | < 10% (gas-in-water), < 30% (water-in-gas) |
| Process simulation | 4 | ~15 | < 5% |
| Cost estimation | 1 | 3 | Within industry range |
| Plant data | 2 | ~20 | Within operational tolerance |

---

## Contributing Benchmarks

When completing a task with benchmark validation:
1. Add `benchmark_validation` key to `results.json`
2. Add a row to the appropriate table in this file
3. Include the source task folder name for traceability
4. State the reference data source, number of points, and tolerance
