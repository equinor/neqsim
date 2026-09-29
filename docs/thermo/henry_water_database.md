---
title: Pure-water Henry database and missing-data contract
description: Sourced Henry coefficients, molality and mole-fraction conventions, and qualification limits
---

The Henry columns in `COMP.csv` are backed by 82 selected rows in
`src/main/resources/data/HenryWaterSource.json`. Each row records the component,
CAS identity, source solubility constant, temperature slope and reference number.
The matching bibliography is `HenryWaterReferences.bib` in the same directory.
`HenryWaterCoverage.csv` inventories all 389 rows: 82 imported correlations
(78 distinct database identities plus four exact-identity aliases), 28 qualified
reference-temperature-only points, 30 remaining literature candidates,
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

`HenryWaterReferencePointCatalog` exposes immutable lookups by exact CAS number or
the exact NeqSim component name. A point stores source identity, convention, units,
reference temperature and pressure, bibliography, license, uncertainty note and
validity statement. Its temperature-taking getter succeeds only at exactly
298.15 K; all other temperatures and every temperature derivative return `NaN`.
These points are intentionally not copied into `COMP.csv`, do not make
`hasHenryCorrelation()` true, and do not enter GE, Pitzer or IAPWS dispatch. A
single value therefore cannot silently become a constant polynomial with a fake
zero slope.

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
