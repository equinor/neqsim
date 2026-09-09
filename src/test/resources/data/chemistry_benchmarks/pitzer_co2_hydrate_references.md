# CO2 hydrate dissociation reference data

`pitzer_co2_hydrate_dissociation.csv` transcribes all 57 measured points from the two tables below.
Temperatures are the experimental dissociation temperatures, **not the authors' calculated values**.
Pressure remains in the source unit MPa; the test multiplies by 10 for bara.

| Source key | Primary source | Tables | License |
| --- | --- | --- | --- |
| Burgass2023 | Burgass, Chapoy, Askvik, Neeraas and Li (2023), *CO2 hydrate formation in NaCl systems and undersaturated aqueous solutions*, STET 78, 8, [DOI 10.2516/stet/2023005](https://doi.org/10.2516/stet/2023005) | Table 4, all 29 points | CC BY 4.0 |
| Jibril2025 | Jibril, Burgass, Chapoy and Ahmadi (2025), *CO2 and CO2-Rich Stream Hydrate Formation in Equilibrium with Brines in the Context of CO2 Injection*, JCED 70, 2869–2878, [DOI 10.1021/acs.jced.5c00173](https://doi.org/10.1021/acs.jced.5c00173) | Table 4 compositions; Table 5, all 28 pure-CO2 points | CC BY 4.0 |

Author-hosted versions of record, accessed 2026-09-09:
[Burgass PDF](https://pure.hw.ac.uk/ws/portalfiles/portal/92579149/stet20220163.pdf),
[Jibril PDF](https://pure.hw.ac.uk/ws/portalfiles/portal/151538865/jibril-et-al-2025-co2-and-co2-rich-stream-hydrate-formation-in-equilibrium-with-brines-in-the-context-of-co2-injection.pdf).
Tables were checked visually against the PDFs. The expanded temperature uncertainties are 0.4 K and 0.22 K,
respectively. The 1 K engineering comparison criterion is separate from these measurement uncertainties.

## Composition and phase basis

Mass percent refers to salt/(water + salts), excluding CO2. Formula-unit masses use anhydrous NaCl, KCl, CaCl2
and MgCl2. Convert using `mol salt per kg water = salt_mass_parts / water_mass_parts / molar_mass_kg_per_mol`.
For Jibril Brine2, use the reported 88.17 water, 8.69 NaCl, 0.17 KCl, 2.59 CaCl2 and 0.39 MgCl2 mass parts.
The rounded parts sum to 100.01 although the source labels the brine 11.83 wt%. Do not silently change the water
fraction to force a sum of 100. The other brines have total salt contents 13.65, 21.44 and 27.30 wt%.

The source reports saturated CO2 systems, with V = CO2 vapor, L = liquid CO2, Lw = aqueous brine, H = hydrate.
Tests use excess CO2 (10 mol per kg initial water), check that a CO2-rich phase remains, and retain all ions in
the aqueous phase. The reported aqueous *phase fraction* is not a dissolved CO2 feed mole fraction. The separate
undersaturated measurements in Burgass Table 5 and mixed-gas measurements in Jibril Table 6 are not part of this fixture.

All CO2-cation-Cl zeta terms and the missing K-Mg theta are **explicitly zero for screening**, with no parameter
fitting. Zero-amount ions are retained in the test's pure-water limit to keep the same CO2 self-interaction dataset.
The manual dataset is not scientifically qualified by loading these coefficients.

## Reproduction and known gaps

Run `mvnw.cmd test -Dtest=PitzerHydrateSalinityValidationTest -DexcludedTestGroups=benchmark` (or `./mvnw` on Unix).
The test writes `target/pitzer-hydrate-reference-assessment.csv` and `target/pitzer-hydrate-concentration-grid.csv`.
Every reference row receives a status. A passing regression suite does **not** mean all 57 measurements pass validation:

- 15 roots are computed: 9 meet the 1 K temperature criterion and 6 mixed-brine points exceed it.
- 2 above-limit measurements have no computed root within the supported Henry range (NaCl5 at 17.36 bara;
  Brine4 at 30.06 bara). These are reported as unavailable.
- 35 measurements are below the 274.19 K CO2 Henry-reference limit.
- 5 initial 25 wt% NaCl points involve salt precipitation not modeled here. The source discusses NaCl dihydrate
  formation and a resulting salt concentration near 23.2 wt%; the initial concentration cannot be treated as
  a stable single-liquid brine. These points are also colder than the Henry limit.
- The 5 wt% NaCl point at 42.26 bara meets the temperature criterion but has a phase mismatch: the source reports
  VLwH while SRK selects liquid CO2 at the calculated temperature. This is retained separately in the output.

The accounting assertions intentionally preserve these known gaps. If a future model change improves them,
review the new evidence and update the accounting and documentation together; never raise the acceptance criterion
or delete observations just to make a benchmark pass.

## Additional MgCl2 suppression comparison

Ren et al. (2025), *Thermodynamic Inhibition by Chlorides (KCl, NaCl, CaCl2, and MgCl2) on CO2 Hydrates:
Implication on Hydrate-Based CO2 Sequestration*, Energy & Fuels 39, 12606–12619,
[DOI 10.1021/acs.energyfuels.5c02264](https://doi.org/10.1021/acs.energyfuels.5c02264), reports two measured
MgCl2 temperature suppressions in its public abstract: 0.96 K at 200 mM and 1.57 K at 400 mM, both at 3 MPa.
The publisher's freely available supporting-information Table S2 maps those solutions to 1.90 and 3.81 wt%.
The test uses these reported mass percentages; it does not identify molarity with molality. This is a two-point
suppression check using the same neutral parameter reference for fresh and saline solutions. It does not qualify
an absolute MgCl2 equilibrium curve, other pressures or concentrated MgCl2 solutions. No measurement uncertainty
is inferred from the abstract. The paper and SI were consulted through the publisher's indexed public text;
the full paywalled article is not redistributed.
