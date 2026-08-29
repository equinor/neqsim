---
title: "DOE Big Hill Sweet refinery assay validation"
description: "Public-data qualification of NeqSim refinery assay volume-to-mass bookkeeping using a Strategic Petroleum Reserve Big Hill Sweet crude assay."
---

# DOE Big Hill Sweet refinery assay validation

Issue #3305 requires refinery characterization to be qualified against public refinery data before atmospheric/vacuum fractionation and conversion-unit models are expanded. This page freezes the first external refinery-assay benchmark used by the campaign.

## Source and provenance

The benchmark uses the U.S. Department of Energy Strategic Petroleum Reserve (SPR) **Big Hill Sweet**, sample **MLI 009**, assay date **1998-05-04**, published in Exhibit D of the SPR Standard Sales Provisions in the Code of Federal Regulations archive:

- GovInfo source: <https://www.govinfo.gov/content/pkg/CFR-2004-title10-vol4/pdf/CFR-2004-title10-vol4-chapII-subchapI.pdf>
- DOE SPR Crude Oil Assay Manual, 5th edition, 1 August 2024: <https://www.spr.doe.gov/reports/docs/CrudeOilAssayManual.pdf>
- DOE SPR assay landing page: <https://www.spr.doe.gov/reports/Crude_Oil_Assays.html>

The 2024 DOE manual documents atmospheric/light-vacuum fractionation by ASTM D2892 followed by D5236 for the residuum. It also states that distillation fractions are measured on a **mass-percent basis** and that volume-percent values are calculated using each fraction's specific gravity. The paired volume/weight yields and density information therefore provide a transparent public-source consistency qualification for NeqSim's volume-to-mass assay bookkeeping.

The volume and weight yields are **not statistically independent measurements**: DOE states that volume percentages are derived from measured mass percentages using fraction specific gravity. The regression is intentionally a bookkeeping/provenance qualification, not an independent validation of a petroleum-property correlation.

No ASTM equation or proprietary commercial-simulator implementation is reproduced. The regression freezes only numerical assay facts openly published by the U.S. Government and cites their public source.

## Frozen validation slice

The first regression intentionally uses only the bounded **175-1050 degF** distillate slice. Each included interval has finite lower/upper cut temperatures plus published volume yield, weight yield, specific gravity, and API gravity. The unbounded light and 1050 degF+ tails are excluded rather than assigning invented boiling boundaries.

| Cut range (degF) | Volume % of whole assay | Weight % of whole assay | SG 60/60 F | API gravity |
| --- | ---: | ---: | ---: | ---: |
| 175-250 | 9.8 | 8.6 | 0.7815 | 49.6 |
| 250-375 | 15.4 | 15.2 | 0.8305 | 38.9 |
| 375-530 | 15.5 | 15.2 | 0.8623 | 32.6 |
| 530-650 | 10.8 | 11.1 | 0.9226 | 21.9 |
| 650-1050 | 27.8 | 30.3 | 0.9477 | 17.8 |

The same source reports whole-crude specific gravity **0.8451** and API gravity **35.9 degAPI**. Those values provide an additional consistency check on the conventional relation `API = 141.5 / SG - 131.5`.

## Acceptance contract

`OilAssayCharacterisationDoeBigHillTest` qualifies three separate things:

1. Published SG/API pairs agree with the conventional API relation within **0.05 degAPI**, consistent with the source's one-decimal API reporting.
2. After normalizing the bounded 175-1050 degF slice on its own basis, NeqSim's density-based conversion

   $$w_i = \frac{v_i SG_i}{\sum_j v_j SG_j}$$

   must reproduce the normalized DOE weight-yield shape with maximum absolute deviation below **0.007 mass fraction** (0.7 percentage points).
3. Supplying the same cuts through the public API-gravity input path must reproduce the mass-fraction shape obtained from the four-decimal specific-gravity values within **5e-5 mass fraction**. This tolerance reflects the DOE table's one-decimal API rounding, not a thermodynamic-model uncertainty.

For the frozen values, the largest pre-CI volume-to-mass deviation is approximately **0.00667 mass fraction**. The largest specific-gravity versus rounded-API mass-shape deviation is approximately **4.27e-5 mass fraction**. These tolerances are therefore data-agreement gates rather than machine-precision tolerances.

The test then applies the normalized slice through `OilAssayCharacterisation`, requires finite positive pseudo-component molar masses and mole amounts, and requires reconstructed assay mass closure to `1e-10`.

## What this validates

This benchmark provides public-source evidence for:

- refinery cut basis handling;
- SG/API consistency of the published source data;
- equivalence of the specific-gravity and API-gravity user input paths within source reporting precision;
- liquid-volume to mass-fraction conversion;
- preservation of bounded cut ranges;
- pseudo-component generation from a real refinery-assay slice;
- exact reconstructed mass closure after pseudo-component creation.

## What this does not validate

This increment deliberately does **not** claim validation of:

- ASTM D86/D1160-to-TBP conversion;
- the molecular-weight, critical-property, acentric-factor, or EOS correlations for each pseudo-component;
- the light-ends or 1050 degF+ residue representation;
- atmospheric or vacuum column product yields;
- vapor-liquid equilibrium or flash-root changes;
- hydrotreating, reforming, FCC, hydrocracking, or other conversion-unit models.

Those remain separate #3305 quality gates. The next dependency-ready increment should extend this public-data foundation into a complete crude/pseudo-component slate and a reproducible atmospheric-fractionation case with mass/energy closure, product yields/boiling ranges, convergence/repeatability, and runtime evidence.
