---
title: "DOE Big Hill atmospheric fractionation qualification"
description: "Rigorous-column integration benchmarks for bounded and complete modeled DOE Big Hill Sweet assay slates."
---

# DOE Big Hill atmospheric fractionation qualification

Issue #3305 separates refinery validation into explicit quality gates. The first DOE Big Hill increment qualified refinery-assay bookkeeping. These benchmarks advance the next gate by sending public assay representations through NeqSim's rigorous `DistillationColumn` and checking process-level conservation, boiling-order separation, and repeatability.

## Public source and evidence boundary

The source is the U.S. Department of Energy Strategic Petroleum Reserve **Big Hill Sweet**, sample **MLI 009**, assay date **1998-05-04**:

- GovInfo SPR Crude Oil Comprehensive Analysis: <https://www.govinfo.gov/content/pkg/CFR-2000-title10-vol4/pdf/CFR-2000-title10-vol4-part625-appA.pdf>
- DOE SPR *Crude Oil Assay Manual*, 5th edition, 1 August 2024: <https://www.spr.doe.gov/reports/docs/CrudeOilAssayManual.pdf>
- DOE SPR assay landing page: <https://www.spr.doe.gov/reports/Crude_Oil_Assays.html>

The DOE manual documents ASTM D2892 atmospheric/light-vacuum distillation followed by D5236 for the residuum. It states that the distillation fractions are measured on a mass-percent basis and that volume-percent values are calculated using each fraction's specific gravity.

This benchmark uses the **measured mass-yield basis** for the five intervals that have finite lower and upper boiling boundaries in the public table:

| Cut range (degF) | Weight % of whole assay | SG 60/60 F |
| --- | ---: | ---: |
| 175-250 | 8.6 | 0.7815 |
| 250-375 | 15.2 | 0.8305 |
| 375-530 | 15.2 | 0.8623 |
| 530-650 | 11.1 | 0.9226 |
| 650-1050 | 30.3 | 0.9477 |

The five bounded cuts are normalized on their own basis. The C5-/175 degF light tail and 1050 degF+ vacuum residuum are **not** included because assigning finite boiling limits to those open-ended fractions would invent data that the published table does not provide.

Accordingly, this first tier is an **integration and numerical-robustness qualification**, not an independent validation of full crude-column product yields. It does not claim that the normalized five-cut slate represents the complete Big Hill crude.

## Column benchmark

`DoeBigHillAtmosphericFractionationTest` performs the following workflow using public NeqSim APIs:

1. Build an SRK system from the normalized measured DOE mass yields, cut specific gravities, and finite boiling ranges using `OilAssayCharacterisation`.
2. Generate the five real NeqSim TBP pseudo-components.
3. Feed the resulting broad-boiling slate to an eight-tray `DistillationColumn` with a reboiler, partial condenser, and one liquid side draw.
4. Solve at near-atmospheric pressure with the residual-monitored `MESH_RESIDUAL` column solver; the solved state must satisfy the active full-MESH residual gate and guarded fallback products are not accepted.
5. Re-run the same initialized column to qualify repeatability rather than accepting a one-off solution.

The operating point is deliberately a reproducible **screening case**, not a reconstruction of a proprietary or historical refinery design:

| Quantity | Benchmark value |
| --- | ---: |
| Feed flow | 5000 kg/h |
| Feed temperature | 550 K |
| Feed pressure | 1.5 bara |
| Internal trays | 8 |
| Feed tray | 4, bottom-up |
| Top pressure | 1.2 bara |
| Bottom pressure | 1.5 bara |
| Condenser mode | Partial |
| Condenser temperature | Solved; no fixed set point |
| Reboiler temperature | 600 K |
| Condenser reflux ratio | 1.0 |
| Liquid side draw | 10% of tray-4 liquid traffic |

The condenser remains partial and no fixed condenser-temperature specification is imposed. Top pressure and reflux ratio define the terminal controls while the top temperature remains a solved equilibrium/energy-balance result. Fixing both condenser temperature and reflux ratio overconstrains this broad-boiling screening case and can leave a thermally converged-looking profile with open tray material balances. The 600 K equilibrium-reboiler set point lies inside the bounded assay range and below the representative normal-boiling temperature of the heaviest cut, preserving both vapor traffic and a positive heavy bottoms product.

`MESH_RESIDUAL` uses inside-out initialization followed by rigorous residual monitoring. Interior tray temperatures remain solver variables governed by the stage energy balances. No tuning to a commercial process simulator is used.

## Acceptance contract

The regression requires all of the following on each accepted solve:

- the column reports a solved state from `MESH_RESIDUAL`, not guarded fallback products;
- the full MESH infinity norm satisfies the solver's active residual tolerance;
- the maximum normalized per-tray component material imbalance satisfies the column's dedicated tolerance;
- overhead, liquid side draw, and bottoms are finite and strictly positive;
- external mass closure is within **5%**;
- `DistillationColumn.getMassBalanceError()` is finite and no greater than **5%**;
- `DistillationColumn.getEnergyBalanceError()` is finite and no greater than **5%**, with the energy-balance convergence gate enabled;
- every pseudo-component closes across overhead + side draw + bottoms within **5%** on a molar-flow basis;
- the lightest bounded DOE cut is enriched in overhead relative to bottoms;
- the heaviest bounded DOE cut is enriched in bottoms relative to overhead;
- composition-weighted representative boiling points order as **overhead < side draw < bottoms**;
- repeated-solve product mass flows and representative boiling points agree within **1%**;
- the JUnit benchmark has a 120 s upper execution bound to fail closed on pathological solver stalls without treating shared-runner wall time as a performance claim.

The 5% process-balance gates are screening tolerances for this first broad-boiling integration case. They are intentionally much looser than the `1e-10` pseudo-component creation mass-closure gate because the column itself is an iterative process solver. Tighter refinery-specific balance gates should be introduced only after this public heavy-slate case establishes a stable baseline.

## Complete modeled slate benchmark

`DoeBigHillCompleteAtmosphericFractionationTest` adds a second integration tier using the reusable `DoeBigHillSweetAssay` factory. Its primary sources are the official DOE SPR [Big Hill Sweet comprehensive assay](https://www.spr.doe.gov/reports/Assays/2024/BigHillSwAssay.xlsx), reported 24 September 2021, and companion [PIANO workbook](https://www.spr.doe.gov/reports/Assays/2021/BigHillSwPIANO.xlsx).

The complete modeled feed contains all 12 components qualified by the characterization campaign:

- ethane, propane, i-butane, and n-butane allocated across the 1.70 mass% gas slice by normalizing DOE's reported C2-C4 PIANO subset;
- the C5-175 degF cut with its PIANO-derived number-average molar mass and published upper boundary;
- six bounded 175-1050 degF petroleum cuts;
- the 1050 degF+ residue with its published lower boundary, specific gravity, and Watson factor.

The light-end allocation and zero sulfur or nitrogen values used where DOE leaves a cell blank remain explicit modeling assumptions. They are not additional measurements.

The complete-slate screening point deliberately reuses the proven bounded-column topology:

| Quantity | Complete-slate benchmark value |
| --- | ---: |
| Feed flow | 5000 kg/h |
| Feed temperature | 550 K |
| Feed pressure | 1.5 bara |
| Internal trays | 8 |
| Feed tray | 4, bottom-up |
| Top pressure | 1.2 bara |
| Bottom pressure | 1.5 bara |
| Condenser mode | Partial |
| Reboiler temperature | 650 K |
| Condenser reflux ratio | 1.0 |
| Liquid side draw | 10% of tray-4 liquid traffic |

The test requires all 12 components to reach the column, a non-fallback `MESH_RESIDUAL` solution, positive overhead/side-draw/bottoms products, external mass closure, enforced energy closure, internal tray material closure, and per-component molar conservation. It also requires C2-C4 enrichment toward the overhead, 1050 degF+ enrichment toward the bottoms, composition-weighted normal-boiling-point ordering of **overhead < side draw < bottoms**, and 1% repeated-solve agreement for product flows and boiling descriptors. A 120 s timeout fails closed on a solver stall.

The complete-slate case is still an integration qualification. The DOE workbooks do not publish a matching atmospheric-column tray count, feed condition, pressure profile, furnace duty, stripping steam, reflux, pump-around duties, or product specifications. Therefore agreement with the DOE assay cut table would not by itself validate simulated plant product yields.

## What this benchmark advances

This increment exercises, in one regression:

`DOE assay facts -> reusable complete modeled slate -> standard and TBP components -> Stream -> rigorous DistillationColumn -> three refinery-style product draws`

That closes an important integration gap between the characterization foundation and the refinery fractionation workstream. It also protects against future changes that would make heavy pseudo-components impossible to use in a near-atmospheric column even when assay bookkeeping still passes.

## Remaining scientific gaps

These benchmarks do **not** validate:

- the normalized C2-C4 allocation as a complete measured gas analysis;
- generated pseudo-component molecular weights, critical properties, or acentric factors against independent laboratory property data;
- full-crude atmospheric product yields or cut-point recovery;
- pump-around heat duties, side strippers, or a refinery preheat/furnace train;
- vacuum fractionation;
- ASTM D86/D1160-to-TBP conversions;
- refinery conversion units.

The next scientific gate should use a public atmospheric operating case that includes enough column design and operating information to compare product yields and boiling ranges without tuning to an under-specified target. Only after that gate should #3305 advance to vacuum fractionation or conversion-unit models.
