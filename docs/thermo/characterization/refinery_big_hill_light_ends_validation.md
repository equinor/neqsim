---
title: "DOE Big Hill composition-resolved light-end qualification"
description: "Public-data qualification of standard-component C2-C4 ingestion for the DOE Big Hill Sweet crude assay."
---

# DOE Big Hill composition-resolved light-end qualification

This benchmark qualifies a source-traceable C2-C4 composition view for refinery assay light ends. Known molecules are added as NeqSim standard components rather than being sent through a petroleum pseudo-component correlation.

## Sources and provenance

The public U.S. Department of Energy Strategic Petroleum Reserve sources are:

- comprehensive Big Hill Sweet assay workbook:
  <https://www.spr.doe.gov/reports/Assays/2024/BigHillSwAssay.xlsx>
- Big Hill Sweet gas-chromatographic/PIANO workbook:
  <https://www.spr.doe.gov/reports/Assays/2021/BigHillSwPIANO.xlsx>
- DOE assay landing page:
  <https://www.spr.doe.gov/reports/Crude_Oil_Assays.html>
- report date recorded in both workbooks: **24 September 2021**

DOE/SPR distributes these workbooks for informational use and does not provide a separate license statement. The regression freezes only the attributed numerical facts below; it does not redistribute either workbook.

## Composition calculation

The comprehensive assay reports the aggregate C2-C4 cut as **1.70 mass% of whole crude**. The PIANO workbook reports this debutanization composition on a weight basis:

| Component | Reported debutanization wt% | Normalized C2-C4 mass fraction | Mass in a 1 kg whole-crude basis, kg |
| --- | ---: | ---: | ---: |
| ethane | 0.09 | 0.0013507429 | 0.0000229626 |
| propane | 10.38 | 0.1557856821 | 0.0026483566 |
| i-butane | 10.21 | 0.1532342789 | 0.0026049827 |
| n-butane | 45.95 | 0.6896292961 | 0.0117236980 |
| **C2-C4 subset** | **66.63** | **1.0000000000** | **0.0170000000** |

The normalization excludes DOE's separately reported i-pentane, n-pentane, 2,2-dimethylpropane, and C6+ entries. Methane is reported as 0.00 wt% and is not invented. This is a transparent combination of two DOE views, not an independent compositional measurement.

## Java and Python contract

Use `AssayCut.withStandardComponent(componentName)` on a mass-basis cut. During `apply()`, NeqSim preflights each identifier on a clone, reads the standard component's authoritative molar mass from the Java thermodynamic system, converts the configured assay mass to moles, and then adds the known molecule with `SystemInterface.addComponent`.

The same Java behavior is available from Python through JPype. A standard component cannot also carry pseudo-component density, boiling-point, Watson-factor, boundary, or explicit molar-mass inputs. Volume-basis standard-component cuts fail closed because no independent light-end liquid-density conversion is claimed.

## Acceptance and maturity

`OilAssayCharacterisationDoeBigHillLightEndsTest` requires:

- exact reproduction of DOE's 66.63 wt% C2-C4 subset and normalized composition;
- exact reconstruction of the 0.017 kg gas slice on a 1 kg whole-crude basis within 1e-10 kg;
- positive finite standard-component molar masses and mole amounts;
- identical component amounts under reversed source order;
- no petroleum `_PC` component for a known molecule;
- clone retention; and
- fail-closed unknown, duplicate, ambiguous, and volume-basis inputs without original-system mutation.

The implementation is O(n) in the number of assay cuts. The capability is **qualified for DOE Big Hill C2-C4 composition ingestion and mass/mole bookkeeping**.

## Evidence boundary

This benchmark does not validate gas-liquid equilibrium, an EOS or mixing rule, flash recovery, C5-175 degF representative properties, full-crude light-end material balance, atmospheric product yields, vacuum fractionation, blending, or conversion models. The PIANO subset normalization is an inference explicitly recorded above. C5-175 degF representative-property qualification remains the next characterization dependency.
