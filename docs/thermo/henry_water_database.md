---
title: Pure-water Henry database and missing-data contract
description: Sourced Henry coefficients, molality and mole-fraction conventions, and qualification limits
---

# Pure-water Henry database

The Henry columns in `COMP.csv` are backed by the 76 selected rows in
`src/main/resources/data/HenryWaterSource.json`. Each row records the component,
CAS identity, source solubility constant, temperature slope and reference number.
The matching bibliography is `HenryWaterReferences.bib` in the same directory.
`HenryWaterCoverage.csv` inventories all 389 rows: 76 imported, 64 remaining
literature candidates, 140 estimated/other-source candidates, 59 ionic rows,
49 without an exact CAS match in the archive, and water itself. A candidate match
is a research lead, not validated data. All other rows contain zero coefficients and represent **unavailable data**.
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

The two-parameter expressions are **local van't Hoff approximations about
298.15 K**, not newly fitted experimental data. Their individual experimental
temperature ranges and uncertainty are not qualified by this import. Tests at
288.15, 298.15 and 308.15 K verify arithmetic and derivatives, not experimental
accuracy throughout that interval. Do not use this coverage as an unrestricted
high-temperature, brine, mixed-solvent or reactive-absorption model. Existing
qualified IAPWS selection remains preferred for supported aqueous gases.

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
identity, provenance coverage and polynomial conversion. Supply the downloaded
`henry_5.0.0_f90.zip` as an argument to additionally verify each selected value
against the original Fortran source. `HenryWaterDatabaseTest` checks database
loading, source values, finite-difference derivatives, standard states and absence.

[Issue #4044](https://github.com/equinor/neqsim/issues/4044) tracks the remaining
component-by-component source review, reference-only data, experimental range and
uncertainty qualification, reactive-species distinctions and application-level
validation. New regression fits must include underlying data, units, residuals,
range, provenance and independent holdout evidence; do not fit missing slopes
from a single reference value or hide an unsupported species behind a placeholder.
