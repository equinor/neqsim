---
title: Pure-water Henry database and missing-data contract
description: Sourced Henry coefficients, molality and mole-fraction conventions, and qualification limits
---

The Henry columns in `COMP.csv` are backed by 83 selected rows in
`src/main/resources/data/HenryWaterSource.json`. Each row records the component,
CAS identity, source solubility constant, temperature slope and reference number.
The matching bibliography is `HenryWaterReferences.bib` in the same directory.
`HenryWaterCoverage.csv` inventories all 389 rows: 83 imported correlations
(79 distinct database identities plus four exact-identity aliases), 35 qualified
reference-temperature-only points, 22 remaining literature candidates,
140 estimated/other-source candidates, 59 ionic rows, 49 without an exact CAS
match in the archive, and water itself. A candidate match is a research lead,
not validated data. Rows without a dispatched correlation contain zero
coefficients and represent **unavailable correlation data**.
Zero coefficients are not a physical constant, and the former `900,0,0,0`
overflow sentinels have been removed.

## Source and limitations

Source: Rolf Sander, *Compilation of Henry's law constants (version 5.0.0) for
water as solvent*, Atmospheric Chemistry and Physics 23, 10901–12440 (2023),
[doi:10.5194/acp-23-10901-2023](https://doi.org/10.5194/acp-23-10901-2023).
The compilation is distributed under CC BY 4.0; this is a selected and converted
subset, not the original database. Source values are from `Hsbp.f90` in the
[version 5.0.0 download](https://www.henrys-law.org/henry/download.html).

Selections use literature-review entries (type L) with a reported temperature
slope. The selected reference is explicit; the compilation can contain other,
conflicting values. These values have limited precision. The current selection
covers neutral hydrocarbons, common gases, refrigerants and selected solvents.
Neutral data are never assigned to a charged component merely because it shares
a CAS number. Reactive acids, amines, hydrated species and reference-only data
without slopes need separate qualification. No claim of complete coverage is made.

Four legacy PVTsim-named rows (`methanolPVTsim`, `propanePVTsim`,
`ethanolPVTsim` and `nbutanePVTsim`) have the same CAS number, molecular formula
and InChIKey as their qualified canonical component. They therefore reuse the
same molecular Henry expression and source record. The inherited type-L record is
Sander reference 3500, Burkholder et al. (2019), JPL Publication 19-5. The
implemented numerical subset remains attributed to the CC BY 4.0 Sander
compilation; no JPL report text is reproduced. The machine-readable record gives
no numerical uncertainty or primary experimental range, so published precision
and the local 298.15 K van't Hoff scope are retained. This does not assert that
their other pure-component parameters are identical. The two MEG PVTsim rows
remain unavailable because the canonical MEG row has not yet passed source,
definition and range qualification.

The neutral `n-pentane` and `i-pentane` rows are exact CAS and InChIKey
matches to Sander's pentane and 2-methylbutane records; NeqSim's `nC5` and
`iC5` formula labels are abbreviations for `C5H12`. The selected type-L
rows are the highest-ranked slope-bearing Brockbank (2013) value for n-pentane
and the only slope-bearing type-L value, from Plyasunov and Shock (2000), for
isopentane. The raw values are respectively 7.3e-4 and 7.9e-4 mol kg^-1
atm^-1, with local slopes 3900 and 3000 K at 298.15 K. Neither Sander row
contains a numerical uncertainty. Other type-L rows provide an explicit source
spread: n-pentane values span 7.3e-4 to 8.8e-4 with slopes from 3400 to 3900 K;
the alternate isopentane point is 7.4e-4 and has no slope. These comparisons are
not fitted uncertainty intervals.

The Brockbank thesis is publicly readable but its record identifies only an
institutional copyright policy, not a permissive reuse license; the Plyasunov
article is publisher-copyrighted. NeqSim reproduces only the numerical facts from
the CC BY 4.0 Sander compilation. Both expressions remain local van't Hoff
descriptions about 298.15 K (and 0.1 MPa where stated), not qualified finite
extrapolation ranges or independent new regressions.

Elemental `mercury` is an exact CAS, formula, neutral-charge and InChIKey match
to Sander's water-solvent record. The selected type-L JPL Evaluation 19 row is
`Hsbp = 0.13 mol kg^-1 atm^-1` with a 2600 K local slope at 298.15 K. The first
independent measured row, Andersson et al. (2008), reports the same value and
slope at compilation precision; Sanemasa (1975) reports 0.13 and 2500 K. This
agreement is validation, not a refit or uncertainty interval. The Sander machine
row reports no numerical uncertainty or finite experimental range, so the
implemented expression remains a local van't Hoff approximation. Only numerical
facts from the CC BY 4.0 compilation are reproduced; no JPL or publisher text is
copied.

The two-parameter expressions are **local van't Hoff approximations about
298.15 K**, not newly fitted experimental data. Their individual experimental
temperature ranges and uncertainty are not qualified by this import. Tests at
288.15, 298.15 and 308.15 K verify arithmetic and derivatives, not experimental
accuracy throughout that interval. Do not use this coverage as an unrestricted
high-temperature, brine, mixed-solvent or reactive-absorption model. Existing
qualified IAPWS selection remains preferred for supported aqueous gases.

## Qualified reference-temperature-only points

`HenryWaterReferencePoints.json` contains 28 exact-CAS neutral hydrocarbon points
from the type-L entries attributed by Sander to Plyasunov and Shock (2000),
[doi:10.1016/S0016-7037(99)00330-0](https://doi.org/10.1016/S0016-7037(99)00330-0).
That source evaluates hydration thermodynamics at 298.15 K and 0.1 MPa. The
machine-readable Sander rows do not encode a numerical uncertainty, so the catalog
retains their published precision and states the uncertainty limitation rather
than inventing one. The set covers selected branched C6-C9 alkanes, C10-C14
n-alkanes, cycloalkanes, pentenes/heptenes and alkylbenzenes. It excludes reactive
species, ions, aliases and estimated (Q/E) rows.

Six additional type-L points attributed by Sander to Brockbank (2013) cover
4-methylheptane, cis-2-pentene, cis-2-heptene, heptylbenzene, octylbenzene
and nonylbenzene. The selected raw `Hsbp` values are respectively 2.7e-4,
4.5e-3, 2.4e-3, 2.7e-2, 1.9e-2 and 1.5e-2 mol kg^-1 atm^-1. Brockbank's
public thesis describes critically evaluated recommended values and group-contribution
methods at 298.15 K and 100 kPa; these six values are retained only as type-L
reference points, not characterized as new measurements or NeqSim fits. The
ScholarsArchive record links institutional copyright terms rather than a permissive
data license, so NeqSim reproduces numerical facts only from the CC BY 4.0 Sander
compilation.

The exact-identity `4-ethyltoluene` row adds one type-L point from Mackay and
Shiu (1981), Sander reference 479. The Sander machine row reports
`Hsbp = 0.20 mol kg^-1 atm^-1` at 298.15 K, equivalent to
`Hm = 5.06625 bar kg mol^-1`. Independent vapor-pressure/aqueous-solubility
rows span 0.16 to 0.20 mol kg^-1 atm^-1 at compilation precision. This spread
is validation evidence, not a fitted uncertainty interval. Neither the machine
row nor the compilation entry reports a total reference pressure; the catalog
therefore stores that field as unavailable instead of assigning 0.1 MPa. The
point remains exact-temperature-only and does not define a slope or derivative.
Mackay and Shiu's review is publisher-copyrighted, so only numerical facts from
the CC BY 4.0 Sander compilation are reproduced.

Each catalog row can override original-reference citation, URL, rights,
uncertainty, identity basis, and point conditions. Exact CAS and molecular identity
are required. Four rows also match the Sander InChIKey exactly; the two cis-alkene
NeqSim rows have the same connectivity block and exact cis CAS/name but omit the
stereochemical InChIKey layer. That limitation is explicit rather than silently
treated as an exact key match. None of the six rows receives a temperature slope.

`HenryWaterReferencePointCatalog` exposes immutable lookups by exact CAS number or
the exact NeqSim component name. A point stores source identity, convention, units,
reference temperature and pressure, bibliography, license, uncertainty note and
validity statement. Its temperature-taking getter succeeds only at exactly
298.15 K; all other temperatures and every temperature derivative return `NaN`.
When the source does not report a total reference pressure, the pressure getter
also returns `NaN`; this does not alter the reported partial-pressure Henry
convention.
These points are intentionally not copied into `COMP.csv`, do not make
`hasHenryCorrelation()` true, and do not enter GE, Pitzer or IAPWS dispatch. A
single value therefore cannot silently become a constant polynomial with a fake
zero slope.

## Fail-closed candidate dispositions

`HenryWaterCandidateDispositions.json` records the component-specific decision for
all 22 exact-CAS literature candidates that are not admitted as correlations or
reference points. The file records water as solvent, source record and type
inventory, identity assessment, Henry-definition risk, original-source rights
boundary and the evidence required before admission. It contains no coefficient,
reference value, fitted slope, pressure assumption or uncertainty estimate.

The dispositions are deliberately more specific than a generic candidate label:

- three reactive amines (`MDEA`, `MEA` and `Piperazine`) require joint
  neutral-species Henry and reaction-standard-state qualification with unchanged
  reactive-VLE benchmarks;
- five acid/base species require intrinsic neutral-solute separation from
  pH-dependent total analytical uptake;
- five hydrolyzing or dimerizing inorganic species require species-resolved
  equilibrium evidence. In particular, the Sander chlorine record identifies
  prominent recommended values as effective
  `([Cl2] + [HOCl]) / p(Cl2)` at 101325 Pa rather than an infinite-dilution
  intrinsic `Cl2` constant;
- formaldehyde, ethylene oxide and hydrogen peroxide require hydration,
  hydrolysis or decomposition controls. The measured ethylene-oxide and
  hydrogen-peroxide rows remain research leads, not dispatched data;
- `MEG`, its two PVTsim aliases and `PG` require mixed-solvent validation.
  Their pure-water solute records cannot be applied silently to glycol
  solvent-role models, and the compiled MEG and PG values have material source
  spread; and
- `para-hydrogen` and `ortho-hydrogen` require spin-isomer-specific data.
  The exact-CAS source record identifies ordinary `H2`, not a para/ortho
  fraction or conversion equilibrium.

The Sander v5.0.0 compilation is the CC BY 4.0 identity and source-inventory
basis. Underlying publications retain their own rights and must be reviewed for
species definition, units, pressure, temperature range and uncertainty before
numerical facts are adopted. Every disposition remains fail-closed:
`COMP.csv`, `HenryWaterSource.json`, `HenryWaterReferencePoints.json` and
all model dispatch are unchanged. The audit checks that all 22 rows remain
outside both correlation and reference-point catalogs and that the two MEG
aliases provide no independent evidence.

## Fail-closed estimate and other-source dispositions

`HenryWaterEstimateDispositions.json` records all 140 exact-CAS rows whose
Sander inventory status is `estimate_or_other_source_requires_review`. These
are research leads, not approved numerical data. The manifest carries each
component name, CAS, formula, available InChIKey, source-record URL, source-type
codes and a scientific admission boundary. It contains no Henry value, fitted
slope, assumed pressure, uncertainty or validity range.

The component-specific inventory is partitioned into four fail-closed groups:

- 125 hydrocarbons have source-code inventories but no selected type-L local
  expression or campaign-qualified measured/reference-only fact. Admission
  requires a primary or redistributable measured dataset with explicit
  intrinsic water convention, units, pressure and temperature conditions,
  uncertainty and finite validity range;
- `TEG` and `DEG` are held at the glycol solvent-role boundary. Solute-in-water
  evidence cannot be transferred silently into mixed-solvent glycol models;
- the row named `glycerol` is an identity conflict, not evidence for glycerol.
  Its stored CAS 112-27-6, formula C6H14O4 and InChIKey identify `TEG`; and
- 12 acids or reactive inorganic rows require species-resolved treatment of
  dissociation, hydrolysis, reaction, decomposition or molecular/allotrope
  identity before an intrinsic neutral-solute coefficient can be defined.

Five duplicate-identity aliases are explicit: `glycerol`/`TEG`,
`H2SO4`/`sulfuric acid`, `HNO3`/`nitric acid`, and the mojibake aliases for
`NH2OH` and `N2H4`. Aliases provide no independent data. The audit requires
all 140 rows to match `COMP.csv` and the coverage inventory exactly, remain
neutral, stay outside both numerical catalogs, and carry no numerical Henry
fields.

Sander v5.0.0 supplies the CC BY 4.0 identity and source-type inventory.
Underlying publications retain their own rights; source-type codes do not
establish permission, intrinsic/effective convention, units, pressure basis,
uncertainty or range. A single later-qualified value must use the existing
reference-temperature-only semantics rather than an invented zero slope.
`COMP.csv`, IAPWS paths, GE/Pitzer dispatch, and calibrated reactive models are
unchanged by these dispositions.

## Fail-closed unmatched-identity dispositions

`HenryWaterUnmatchedDispositions.json` records all 49 rows for which the
reviewed Sander v5.0.0 source archive has no exact-CAS record. No source
publication or numerical value is selected for these rows. The manifest carries
the database identity, component role, exact archive-search result and the
evidence boundary that would have to be crossed before any future admission.

The inventory separates materially different reasons for staying fail-closed:

- 32 identified hydrocarbons have database molecular identities but no exact-CAS
  archive record. Name, formula, structural analogy or a neighboring homologue
  is not evidence for transfer;
- `ice` and `seawater` are a condensed water phase and a solvent mixture,
  not neutral molecular solutes in water;
- `default`, two placeholder-CAS cyclic components and `asphaltene` lack a
  unique molecular identity suitable for a molecular Henry coefficient;
- `DEA` and `H+PZCOO-` cross the reactive-amine/model-species boundary and
  require joint species and reaction-standard-state qualification;
- seven salts or reactive inorganic rows require electrolyte, dissociation,
  solubility or solid-equilibrium treatment rather than a neutral formula-unit
  Henry constant; and
- `S8` and `sulfur(S8)` require allotrope- and species-resolved
  phase/solubility evidence. The latter duplicates the former CAS and InChIKey
  while carrying a non-molecular formula label, so it is not independent
  evidence.

A stored zero ionic charge does not turn a salt, reaction-model aggregate,
condensed phase or pseudocomponent into an independently volatile neutral
molecular solute. Future admission requires exact identity, water as solvent,
an intrinsic Henry definition, traceable source rights, units, conditions,
uncertainty and a finite validity range. A qualified single value must use the
existing reference-temperature-only semantics; a temperature correlation
requires suitable independent data, residuals and validation.

The manifest contains no coefficient, fitted slope, reference value, pressure
assumption or validity range. The audit requires all 49 rows to remain outside
the correlation, reference-point and prior disposition catalogs, to match
`COMP.csv` and the coverage inventory exactly, and to preserve their explicit
component-role boundary. `COMP.csv`, IAPWS paths, GE/Pitzer dispatch and
calibrated reactive models are unchanged.

## Units and equations

The source reports $H_s^{bp}=m/p$ in mol/(kg atm), with slope $B$ in K:

$$H_s^{bp}(T)=H_s^{bp}(298.15)\exp[B(1/T-1/298.15)].$$

The historical component polynomial includes a water-molar-mass factor. Its
actual reference is on the **molality scale**, despite older Javadocs saying bar:

$$H_m(T)=1.802\exp[h_0+h_1/T+h_2\ln T+h_3T].$$

Here $H_m=p/m$ has units bar kg/mol. Conversion from the source is exact apart
from its published rounding:

$$H_m(T)=1.01325/H_s^{bp}(T).$$

Thus $h_0=\ln[1.01325/(1.802H_s^{bp}(298.15))]+B/298.15$, $h_1=-B$,
and $h_2=h_3=0$. At infinite dilution in water, the mole-fraction reference is
$H_x=H_m/M_w$, using $M_w=0.01801528$ kg/mol. Generic GE fallback references
perform this conversion; Pitzer retains $H_m$ and its existing molality activity
factor. Desmukh-Mather and Kent-Eisenberg retain their empirical calibrated
reference convention: changing that convention independently of reaction constants
breaks reactive VLE benchmarks. Their standard-state requalification is separate
campaign work. The derivative getter returns $dH_m/dT$, not $d\ln H_m/dT$.

## API and compatibility

### Integrated EOS gas/oil and GE liquid references

The hybrid architecture assigns the gas and hydrocarbon liquid to EOS phases and
the aqueous or polar liquid to its selected GE phase. Henry data describe a
solute in a specified solvent; they are not a pure-fluid equation of state.
For a water-free generic GE liquid with an applicable vapor-pressure correlation,
the pure-liquid reference is used even if the database tags the component as a
solute. Supercritical gas and dense hydrocarbon/CO2 cases use their model-owned
EOS roles. A Pitzer liquid remains a water-solvent model: a pure methanol liquid
requires a suitable liquid model rather than a water Henry coefficient.

Dissolved hydrocarbons in water use a Henry solute reference even when their
pure-fluid database row is tagged as a solvent. Molecular formulas retain this
decision when phase initialization has replaced the hydrocarbon classification.
Missing Henry data retain the explicit finite unsupported-solute limit; the
fallback does not establish hydrocarbon solubility.

For a generic aqueous GE solute, the implemented reference is

$$\phi_i=(\gamma_i/\gamma_i^{\infty,w})H_{x,i}/P.$$

The pure-water infinite-dilution calculation retains the owning phase's component
indices and interaction parameters, including user-set NRTL parameters. Other
species are reduced to numerical traces in an isolated clone. It does not reload
a different binary model from the database or mutate the source composition.
The corresponding temperature derivative includes the reference normalization:

$$\frac{\partial\ln\phi_i}{\partial T}=\frac{\partial\ln\gamma_i}{\partial T}-\frac{\partial\ln\gamma_i^{\infty,w}}{\partial T}+\frac{\partial\ln H_{x,i}}{\partial T}.$$

NRTL publishes its analytical activity derivative. Models without an analytical
activity derivative use isolated fixed-composition differences for that activity
term. Pitzer neutral species use their molality activity directly, so this
symmetric-to-Henry normalization is not subtracted from their derivative.
Desmukh-Mather and Kent-Eisenberg differentiate their existing empirical Henry
references. Desmukh-Mather includes its water Poynting pressure and temperature
terms; both amine models' constant ionic fugacity coefficients have zero
pressure and temperature derivatives. These corrections do not change reaction
constants, parameter qualification, or Henry data admission.

`GeneralEosGeFluidTest` exercises pure-fluid and multicomponent phase roles;
`SystemHybridEosGeFlashTest` covers phase restoration, reordering, reactive
conservation and process composition. `GeHenryReferenceTopologyTest` and
`ReactiveGeDerivativeConsistencyTest` compare dilute-reference and fugacity
derivatives with independent analytical limits and fixed-composition differences.
Numerical consistency does not replace experimental qualification of a GE
parameter set, a mixed solvent, a reactive Henry definition, or pressure effects
absent from the selected model.

- `hasHenryCorrelation()` distinguishes available parameters from absence; it
  does not assert temperature-range or reaction-model qualification.
- `getHenryCoef(T)` retains the polynomial's molality convention. It returns
  `NaN` for missing/sentinel parameters, invalid temperature, overflow or underflow.
- `getHenryCoefdT(T)` returns `NaN` when the corresponding coefficient is unavailable.
- GE callers retain their explicit finite unsupported-solute limit. That numerical
  limit is a model fallback, never measured insolubility or a database coefficient.
- User-supplied four-parameter correlations remain supported in the same
  molality convention. Existing all-zero overrides now mean unavailable.

Data corrections change raw coefficient values and unsupported-solute behavior.
The generic GE fallback conversion also corrects the former standard-state
mismatch. Applications that relied on placeholders or assumed that this raw API
returned a mole-fraction constant need migration. Pitzer brine/reaction fits
require regression review when their underlying Henry reference changes.

## Reproduction and remaining work

Run `python3 devtools/check_henry_water_data.py` to check every compiled row,
identity, exact-alias equivalence, provenance coverage, point-only separation and polynomial conversion.
Supply the downloaded `henry_5.0.0_f90.zip` as an argument to additionally verify
each selected correlation and reference point against the original Fortran source.
`HenryWaterDatabaseTest` checks database loading, source values, finite-difference
derivatives, standard states and absence. `HenryWaterReferencePointCatalogTest`
checks exact-CAS identity, immutability, point-only evaluation, unit conversion,
cloning and serialization.

[Issue #4044](https://github.com/equinor/neqsim/issues/4044) tracks the remaining
component-by-component source review, reference-only data, experimental range and
uncertainty qualification, reactive-species distinctions and application-level
validation. New regression fits must include underlying data, units, residuals,
range, provenance and independent holdout evidence; do not fit missing slopes
from a single reference value or hide an unsupported species behind a placeholder.
