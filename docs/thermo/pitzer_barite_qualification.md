---
title: "Pitzer Barite Mixing Qualification Report"
description: "Frozen Na–Ba–Cl–SO4 feeds, exact catalog gaps, primary literature, and remaining gates for full-Pitzer barite precipitation."
keywords: "Pitzer, barite, barium, sulfate, formation water, precipitation, parameter coverage, qualification"
---

## Decision and scope

**Full-Pitzer barite precipitation in Na–Ba–Cl–SO4 water remains unqualified.**
This report implements the actionable-report alternative in [issue #4351](https://github.com/equinor/neqsim/issues/4351),
coordinated under [#3144](https://github.com/equinor/neqsim/issues/3144).
It does not claim completion of the issue's independent equilibrium-validation acceptance criteria.

The audit starts from NeqSim 3.25.0 master `459b9eccf22f335ce88a6c3ec85338844b5a95c4`.
The reported exception is a correct refusal of an unsupported topology, not evidence that the
TP-flash algorithm should be bypassed. No binary coefficient, theta, psi, equilibrium constant,
solver tolerance, legacy opt-out, or scientific qualification level is changed here.
The standalone `PITZER_BINARY` screening calculation is not full mixed-electrolyte qualification.

## Two different parameter inventories

Default selection first considers the pinned PHREEQC catalog. If any required row is absent,
it retains the legacy dataset; the latter's mixed-ion coverage check then refuses calculation.
Consequently the exception's six missing *legacy interactions* are not the list of missing
*PHREEQC source rows*. Combining rows from these two datasets without a coherent convention and
validation record would hide that distinction.

The candidate source is PHREEQC commit `b0b3be767158ccc3322d2c816625cf470045e67e`, database blob
`324f852784be84650b77bd7f07f8316aafd8188b`, represented by the bundled resource under
`src/main/resources/neqsim/thermo/phase/phreeqc/`. Its required-row inventory is:

| Interaction | Pinned PHREEQC source status | Required action |
|---|---|---|
| Na+/Cl-, Na+/SO4--, Ba++/Cl- | Explicit B0, B1 and C0 rows exist | Retain the complete source temperature functions and charge normalization |
| Ba++/SO4-- | No explicit B0, B1 or C0 row | Select and review a coherent binary/association convention; establish whether beta2 and nondefault alpha are required |
| theta Ba++/Na+ | Explicit constant 0.07 | Source coverage only; retain its associated binary/model convention |
| theta Cl-/SO4-- | Explicit constant 0.03 | Source coverage only |
| psi Na+/Cl-/SO4-- | Explicit constant zero | Present source row; distinguish it from an absent row |
| psi Ba++/Na+/Cl- | Absent | Obtain a traceable coefficient or explicitly justified model convention |
| psi Ba++/Na+/SO4-- | Absent | Obtain a traceable coefficient or explicitly justified model convention |
| psi Ba++/Cl-/SO4-- | Absent | Obtain a traceable coefficient or explicitly justified model convention |

There are **six missing required catalog rows**: three binary-family rows and three psi rows.
The legacy table separately contains a zero-valued Ba++/SO4-- binary labelled `Monnin1999`,
but lacks both theta and all four psi interactions. A source label alone does not establish that
its binary convention can be combined with this PHREEQC subset. No such combination is adopted.

## Read-only preflight API

After constructing a `SystemPitzer`, adding the complete feed, setting mixing rule `classic`,
and calling `init(0)`, use `getMissingPhreeqcPitzerCatalogRows()`. The corresponding phase API is
`PitzerParameterDatasets.getMissingPhreeqcPitzerCatalogRows(phase)`.
It returns an immutable sorted list with family and alphabetically sorted species names.
For the issue feed it reports:

- `B0:[Ba++, SO4--]`
- `B1:[Ba++, SO4--]`
- `C0:[Ba++, SO4--]`
- `PSI:[Ba++, Cl-, Na+]`
- `PSI:[Ba++, Cl-, SO4--]`
- `PSI:[Ba++, Na+, SO4--]`

This traverses the same requirements as automatic catalog selection, including neutral lambda/zeta
rows, and excludes EOS-role hydrocarbons by the same rule. It neither initializes properties nor
changes the selected dataset or user parameters. Reinitialize the system with `init(0)` after a
composition change. The audit follows the selector's active-phase-amount threshold of `1e-20` mol;
it is not a new molality-based applicability rule. It audits the aqueous role, not arbitrary feed
species that may later be introduced by chemistry.

An empty list means only that the required source rows are present. It does not certify the
model, select a dataset, establish a valid range, or include optional beta2/alpha and higher-order
neutral-family qualification. Use `getPitzerParameterQualification()` for selected-dataset evidence
and `requirePitzerDatasetValidationFor(MINERAL_SATURATION_AND_PRECIPITATION)` for the separate
publication gate. Existing fail-fast selection and property calculations retain their behavior;
the all-row diagnostic is evaluated only when explicitly requested.

## Frozen public feed matrix

The machine-readable fixture is
[`barite-qualification-cases.csv`](../../src/test/resources/neqsim/thermo/phase/barite-qualification-cases.csv).
All seven feeds use **1 kg water, 298.15 K and 1.01325 bara**. Concentrations below are mol/kg water;
water moles are calculated from the runtime water molar mass, not rounded to 55.508 mol.
The exact original issue feed with 55.508 mol water is tested separately.

| Case | Na+ | Ba++ | Cl- | SO4-- | Initial ionic strength, mol/kg water |
|---|---:|---:|---:|---:|---:|
| very_dilute | 0.001002 | 0.000001 | 0.001002 | 0.000001 | 0.001006 |
| dilute | 0.102 | 0.001 | 0.102 | 0.001 | 0.106 |
| issue_molality_basis | 1.0 | 0.001 | 1.0 | 0.001 | 1.004 |
| brine_3m | 3.002 | 0.001 | 3.002 | 0.001 | 3.006 |
| brine_5m | 5.002 | 0.001 | 5.002 | 0.001 | 5.006 |
| barium_excess | 1.0004 | 0.002 | 1.004 | 0.0002 | 1.0066 |
| sulfate_excess | 1.004 | 0.0002 | 1.0004 | 0.002 | 1.0066 |

Every feed satisfies $m_{Na}+2m_{Ba}-m_{Cl}-2m_{SO4}=0$.
Initial ionic strength is $I=\frac12\sum_i m_i z_i^2$ for these fully dissociated inputs.
These are **qualification candidates and fail-closed regressions**, not a validated operating range.
No pressure correction or high-temperature barite evidence is established by this matrix.

Use the dimensionless molality activity $a_i=\gamma_i m_i/m^\circ$, where
$m^\circ=1\,\mathrm{mol\,kg^{-1}}$ and gamma is referenced to infinite dilution.
For the dissolution reaction $\mathrm{BaSO_4(s)}\rightleftharpoons\mathrm{Ba^{2+}}+\mathrm{SO_4^{2-}}$,
$SI=\log_{10}(a_{Ba}a_{SO4}/K_{sp})$ uses unit activity for pure barite.
Total dissolved barium or sulfate must not be substituted for free-ion activity when complexes
are present. Freeze treatment of BaSO4(aq), NaSO4-, HSO4-, acid/base chemistry and the solid
standard state before comparing models. Double-counting ion pairing in both association reactions
and fitted Pitzer interactions is not acceptable.

## Primary evidence and outstanding provenance

The [USGS PHREEQC PITZER documentation](https://water.usgs.gov/water-resources/software/PHREEQC/documentation/phreeqc3-html/phreeqc3-37.htm)
defines the coefficient families, temperature expansion and nonsymmetric mixing convention.
It warns that parameter changes must preserve thermodynamic consistency. Missing coefficients
within an existing temperature row are distinct from an entirely absent interaction row.
MacInnes scaling affects reported single-ion coefficients; compare neutral activity products or
use an explicitly matched reporting convention.

[Dideriksen et al. (2024), Applied Geochemistry 171, 106033](https://doi.org/10.1016/j.apgeochem.2024.106033)
([author-hosted paper](https://jrodriguezblanco.wordpress.com/wp-content/uploads/2025/03/2024-dideriksen-et-al.-2024.pdf)),
sections 3.1.5–3.1.6, is a relevant candidate model source. It evaluates binary and mixing
parameters against barite solubility, discusses Pitzer (1975), Monnin and Galinier (1988), and
Paige et al. (1998), and supplies a combined database in its supplementary material. Its reported
accuracy depends on temperature, salinity and experimental material. This is a research lead,
not validation of NeqSim's current coefficient combination. The supplementary database,
redistribution terms, complete species/reaction conventions and every required row must be
retrieved and reviewed before adoption; coefficients are not transcribed or fitted here.

## Evidence status and completion procedure

| Acceptance item | Current evidence | Remaining work |
|---|---|---|
| Public dilute-to-brine feeds | Frozen seven-case CSV and exact original reproducer | Retain unchanged when adding supported parameters |
| Explicit parameter topology | Complete required-row audit above | Review all six absent rows and optional 2–2 terms under one coherent model |
| Pre-precipitation SI | Blocked by missing interactions | Independent matched-model SI for every frozen feed |
| Equilibrium dissolved Ba/SO4 and solid mass | Blocked; no numerical results claimed | Compare free ions, total dissolved elements and precipitated BaSO4 with qualified independent evidence |
| Charge and inventory | Balanced input and unchanged component inventory on repeated rejected calls | Close elemental and charge balances after successful equilibrium |
| Nonnegative solids and complementarity | Not demonstrated by rejection | Require $n_s\ge0$, $SI\le0$ and $n_sSI=0$ within predeclared numerical tolerances |
| Deterministic repeat and dissolution | Repeated diagnostics/rejections only | Repeat successful precipitation, then dilute/add undersaturated water and demonstrate dissolution with retained solid inventory |
| Domain and uncertainty | No qualified full-mixture T/P/composition envelope | Report calibration versus held-out data, model residuals and measurement uncertainties separately |

For the independent comparison, pin executable version, database hash, species mapping, solvent
mass and pressure convention. First reproduce the chosen published model in IPhreeqc or another
independently qualified implementation, then compare NeqSim with **the same** activity and solid
standard states. Preserve raw SI, free/total concentrations and solid-mass results. A PHREEQC run
that relies on omitted interactions is not evidence for filling NeqSim's missing rows with zero.
Use held-out measured solubility to assess accuracy separately from code-to-code agreement.
Do not widen a tolerance to disguise an unreviewed parameter or speciation mismatch.

`PitzerBariteQualificationTest` verifies the current blockers and inventory contract. Existing
catalog, neutral, mixed-electrolyte and qualification suites remain the regression controls.
The issue should remain open until the independent equilibrium and continuation gates above are
fulfilled. The report and preflight API can be reviewed and merged separately from parameter adoption.
