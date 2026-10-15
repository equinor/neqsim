---
title: "Dielectric property data and validity"
description: "Source-qualified pure-liquid dielectric coefficients, temperature bounds, unavailable records, and limitations of the component reference property."
---

# Dielectric property data and validity

The component dielectric property is a **dimensionless static relative permittivity**.
Its five database coefficients evaluate

$$\varepsilon_r(T)=A+B/T+CT+DT^2+ET^3,$$

where $T$ is in kelvin. Consequently, the coefficient units are $1$, K,
K$^{-1}$, K$^{-2}$, and K$^{-3}$, respectively. `DIELECTRICPARAMETER1` through
`DIELECTRICPARAMETER5` contain $A$ through $E$. Temperature derivatives must be
derivatives of this same expression.

## Scope of the correction

The audit for [issue #4334](https://github.com/equinor/neqsim/issues/4334) inspected
all 389 records in `COMP.csv` and 76,704 records in `COMP_EXT.csv`. Fourteen named
records in each table shared the malformed vector
`[-19.2905, 29814.5, -0.019678, 0.013189, 0.031144]`, which evaluates to about
826,674.56 at 298.15 K. These values are not a usable correlation for those
chemically different species. The audit also found a water-correlation copy in
`i-butane`, a slightly different malformed vector in `PG`, and invalid dielectric
coefficient fields for five olefins.

This change updates 22 records in canonical `COMP.csv`: 11 receive bounded
reference correlations and 11 are explicitly unavailable. The count includes the
deliberate update of canonical `MEG` alongside its two aliases. The raw
`COMP_EXT.csv` remains a legacy input and retains its original dielectric fields.
The existing `NeqSimDataBase.useExtendedComponentDatabase(true)` loader calls
`includeMissingStandardComponents()`, which synchronizes shared neutral-component
properties from canonical `COMP.csv` while preserving the extended rows' `ID`
and `COMPINDEX`. Consequently, these same 22 corrections apply in the loaded
extended database. Tests check that loaded mode, rather than asserting that the
raw extended resource has been repaired. A custom loading path that bypasses
this synchronization does not qualify the legacy coefficients; coefficient
identity validation rejects them.

The headerless
historical fixture `src/test/resources/neqsim/thermodynamicoperations/COMP.csv`
contains 15 of these names and receives the same dielectric-field corrections.
Its six additional stale malformed records (`methanol`, `methanolPVTsim`,
`ethanol`, `ethanolPVTsim`, `DEG`, and `TEG`) are synchronized to the existing
production dielectric coefficients, bringing the historical update to 21 rows.
That synchronization does not add source certification to those legacy fits.
All other fields, original quoting, row order, and line endings are retained.
The historical fixture retains other old identity fields and is not an
authoritative component-identity source.

`src/main/resources/data/DielectricReferenceData.csv` supplies the component name,
inclusive temperature bounds, status, source identifier, and exact expected
coefficients in columns `NAME,TMIN_K,TMAX_K,STATUS,SOURCE,A,B,C,D,E`.
A `bounded` record is qualified only within its stated interval. An
`unavailable` record has all five coefficients set to `NaN`, bounds `0,0`, and a
reason identifier. Runtime access rejects an unavailable or out-of-range
reference rather than returning a fabricated dielectric value. No universal
permittivity ceiling or replacement constant defines source validity.
The coefficient identity is checked as well: an older serialized fluid or a
custom database carrying different coefficients must not acquire a published
source designation merely by sharing the component name. Its mismatched
reference data are rejected with a diagnostic.

Records without this metadata retain their legacy treatment; their absence is
**not** a claim of experimental qualification. In particular, neutral records
`neon`, `H2O2`, `NH2SO3H`, `NaHSO4`, and `asphaltene` still have all-zero legacy
coefficient fields. Charged species also have zero placeholders. This correction
does not assign pure-solvent permittivities to ions or qualify the many legacy
constant values and generated pseudocomponents.

## Qualified coefficients

The canonical coefficients, also used by the loaded extended database, are listed
below. $B=E=0$ for every active replacement.
Extra digits preserve the independent fits and their analytic derivatives; they
do not imply measurement precision beyond the cited uncertainty.

| Component name | $A$ | $C$ (K$^{-1}$) | $D$ (K$^{-2}$) | Valid $T$ (K) | Source identifier |
|---|---:|---:|---:|---:|---|
| `nitrogen` | 1.654535 | -0.0029 | 0 | 63.15–78.15 | `NBS_CIRC514_NITROGEN` |
| `oxygen` | 1.69936 | -0.0024 | 0 | 55.15–90.15 | `NBS_CIRC514_OXYGEN` |
| `n-pentane` | 2.31304 | -0.00160 | 0 | 223.15–303.15 | `NBS_CIRC514_NPENTANE` |
| `n-hexane` | 2.3443825 | -0.00155 | 0 | 263.15–323.15 | `NBS_CIRC514_NHEXANE` |
| `acetone` | 84.2749207384 | -0.324015234899 | 0.000370286760220 | 278.15–313.15 | `NPL_MAT23_ACETONE_FIT` |
| `1-propanol` | 89.8320207351 | -0.324880653866 | 0.000308977685522 | 278.15–323.15 | `NPL_MAT23_PROPAN1OL_FIT` |
| `i-propanol` | 110.480622357 | -0.450888571429 | 0.000485714285714 | 283.15–323.15 | `NPL_MAT23_PROPAN2OL_FIT` |
| `MEG`, `MEGPVTsim18`, `MEGPVTsim19` | 188.375196080 | -0.766010851064 | 0.000908510638298 | 278.15–323.15 | `NPL_MAT23_ETHANEDIOL_FIT` |
| `ammonia` | 143.00357625 | -0.770683333333333 | 0.00116666666666667 | 278.15–308.15 | `NBS_CIRC514_AMMONIA_FIT` |

### NBS liquid references

Maryott and Smith, *Table of Dielectric Constants of Pure Liquids*,
[NBS Circular 514 (1951)](https://nvlpubs.nist.gov/nistpubs/Legacy/circ/nbscircular514.pdf),
sections 2.4–2.5 and the substance tables, supplies the original linear forms.
The reference is the low-frequency liquid limit: normally atmospheric pressure,
or the liquid vapor pressure above its normal boiling point. It is not a gas
correlation. In particular, room-temperature nitrogen and oxygen are outside
their liquid-reference ranges.

The conversion is $A=\varepsilon_{r,0}-C T_0$. NBS lists its slope column with a
factor of $10^2$, and temperatures in degrees Celsius. The verified anchors are
nitrogen 1.4511 at 70.15 K, oxygen 1.507 at 80.15 K, n-pentane 1.844 at 293.15 K,
and n-hexane 1.890 at 293.15 K. Source locations are printed pages 3, 16, and 21
(PDF pages 11, 24, and 29, counting from one). The tabulated slope validity limits
are used without extension. These are translations of published correlations;
anchor agreement alone is not independent experimental validation. NBS assigns
accuracy from significant figures, generally 0.5% or better for four figures;
its uncertainty convention is not a modern coverage-factor interval.

The ammonia quadratic was fitted independently to the liquid values at 5, 15,
and 35 degrees Celsius from printed page 3. The separate 25-degree point was
withheld: prediction 16.93333 versus reference 16.9, absolute error 0.03334.
The three-significant-figure source data have estimated accuracy of about 2% or
better. The underlying measurement is Grubb, Chittum, and Hunt,
[*Liquid Ammonia as a Solvent. VI. The Dielectric Constant of Liquid Ammonia*
(1936)](https://doi.org/10.1021/ja01296a026). These temperatures require liquid
ammonia under pressure; the range does not qualify a gas or supercritical model.

### NPL polar-liquid fits

Gregory and Clarke, [*NPL Report MAT 23*, revised January 2012](https://eprintspublications.npl.co.uk/4347/1/MAT23.pdf),
printed pages 14 and 18, provides the measured static values and the
recommended zero-frequency limits. The static measurements are described in
[Gregory and Clarke (2005), DOI 10.1088/0957-0233/16/7/013](https://doi.org/10.1088/0957-0233/16/7/013).
They concern high-purity liquids near ambient pressure; these temperature-only
fits do not introduce a pressure dependence. NPL's reported uncertainties use
coverage factor $k=2$ and omit the separate temperature uncertainty.

Each quadratic was fitted by unweighted least squares in a centered temperature
coordinate and then expanded into the existing Kelvin polynomial. The following
temperature selection separates fitting from validation; temperatures here are
degrees Celsius.

| Component | Fit temperatures | Withheld temperatures | Maximum withheld absolute error | Reference uncertainty at worst point |
|---|---|---|---:|---:|
| Acetone | 5, 15, 25, 40 | 10, 20, 30, 35 | 0.020974 | 0.04 |
| 1-propanol | 5, 15, 25, 35, 50 | 10, 20, 30, 40, 45 | 0.014217 | 0.04 |
| Ethanediol (MEG) | 5, 15, 25, 35, 50 | 10, 20, 30, 40, 45 | 0.039894 | 0.06 |
| 2-propanol | 10, 20, 30, 40, 50 | 15, 25, 35, 45 | 0.075000 | 0.10 |

Representative withheld checks are acetone 20.07903 versus 20.10 at 303.15 K,
1-propanol 17.74578 versus 17.76 at 318.15 K, MEG 37.59011 versus 37.63 at
313.15 K, and 2-propanol 19.22500 versus 19.30 at 298.15 K. Every withheld
residual is below its corresponding reported measurement uncertainty. These are
independent of the fitted points, but are from the same experimental programme;
they are not a second-laboratory validation. Maximum fitting residuals are
0.004997, 0.014938, 0.003192, and 0.066572, respectively.

For acetone, NPL flags the 45- and 50-degree measurements as possibly affected
by bubbles. They were excluded from both the fit and the accepted range. For
2-propanol, the static values are inferred from microwave measurements, not
direct admittance-cell measurements. The fit uses the recommended double-Debye
static limits at 10–25 degrees and Debye-Gamma limits at 30–50 degrees. A single
smooth temperature polynomial represents those selected static limits; no
frequency-model switch occurs at runtime. This distinction and NPL's source
uncertainties must be retained when interpreting the data.

### Deliberate MEG change

The production records `MEG`, `MEGPVTsim18`, and `MEGPVTsim19` all identify
CAS 107-21-1, formula C2H6O2, and molar mass 62.069 g/mol. Their dielectric
coefficients and metadata therefore agree. Their EOS and other property fields
remain unchanged.

The previous canonical MEG expression was $-18.46+16744/T$, giving about 37.70
at 298.15 K. The NPL-based replacement gives 40.74968 there. This is an
intentional reference-data change, not an alias-only repair or an assertion that
the old and new values agree. Electrolyte calculations calibrated against the
previous MEG reference may change. The new pure-liquid data alone do not
requalify those fitted electrolyte models.

## Explicitly unavailable records

| Names | Source/reason identifier | Reason |
|---|---|---|
| `n-butane`, `nbutanePVTsim`, `i-butane`, `i-pentane` | `NO_QUALIFIED_T_ONLY_REFERENCE` | No source-qualified temperature-only correlation was established in this change. |
| `glycerol` | `IDENTITY_MISMATCH_TEG` | The record is named glycerol but its identity and bulk properties describe triethylene glycol. |
| `PG`, `ethylene`, `propene`, `cis-butene`, `trans-butene`, `iso-butene` | `MALFORMED_DIELECTRIC_COEFFICIENTS` | The existing fields produce physically invalid liquid permittivities. |

The glycerol row carries CAS 112-27-6, C6H14O4, and molar mass 150.175 g/mol,
whereas [glycerol is CAS 56-81-5 and C3H8O3](https://webbook.nist.gov/cgi/cbook.cgi?ID=C56815&Units=SI).
Its liquid density and normal boiling point also duplicate the TEG record.
Installing a real glycerol dielectric correlation into that row would leave an
internally inconsistent component. The entire component identity requires a
separate repair; no isolated CAS or molecular-weight edit is made here.

The additional invalid vectors found by the full-table audit are recorded below
to make the quarantine reproducible. All entries are the original $A,B,C,D,E$.

| Name | Original coefficient vector | Original value at 298.15 K |
|---|---|---:|
| `PG` | `[-19.29054, 29814.53, -0.0196785, 0.013189, 0.031144]` | 826674.5622 |
| `ethylene` | `[9.627551137738643e-6, 3450, 13556.576228246831, 0, 0]` | 4041904.7738 |
| `propene` | `[1.05617471008485e-5, 3103, 18504.676899839127, 0, 0]` | 5517179.8252 |
| `cis-butene` | `[1.1258595759827093e-5, 7410, 23322.437236388218, 0, 0]` | 6953609.5153 |
| `trans-butene` | `[1.0933788266236188e-5, 9860, 22984.99404781112, 0, 0]` | 6853009.0460 |
| `iso-butene` | `[1.0947338969965856e-5, 6020, 22077.0419048734, 0, 0]` | 6582290.2351 |

These records are not replaced with a common hydrocarbon constant. A gas-density
model and a liquid reference at an explicitly stated pressure are different
physical properties; a room-temperature value cannot qualify a broad
temperature-only correlation.

## Provenance and remaining engineering scope

Only factual property values, source identifiers, and independently calculated
fit coefficients are included. No report pages, figures, or complete source
table layouts are redistributed. NBS is a US government technical publication;
the [NIST publication reuse statement](https://www.nist.gov/open/copyright-fair-use-and-licensing-statements-srd-data-software-and-technical-series-publications)
describes its reuse terms. NPL MAT 23 retains Crown copyright; its prose and
presentation are not copied. The source citations and fitting method are
provided so the numerical work can be checked against the original reports.

Issue #4334 remains open for the larger physical-model work. The corrected
component function is a restricted **pure-liquid reference**, not a
density-dependent mixture permittivity. A pressure/density-aware gas, dense
fluid, and mixture model still needs a defined reference-state contract,
thermodynamically consistent composition and temperature derivatives, and
qualified data. Mixture rules, electrolyte salting-out, hydrate/brine/MEG
predictions, and mixed-solvent electrolyte calibration require separate
end-to-end validation. A successful pure-component property test does not
establish those capabilities.

See also [component database fields](component_database_guide.md),
[electrolyte CPA](ElectrolyteCPAModel.md), and
[electrolyte Born derivatives](ElectrolyteBornDerivatives.md).

## Coordinated integration assessment

This is partial remediation and is **not ready to merge**. The property branch was
updated to master `a346faa1f13adfc95c99dff18e90a348d8725d28`. Its eleven focused property,
derivative, source-domain, alias, serialization and ordinary-SRK controls pass
using `pomJava8.xml` on OpenJDK 17. Actual Java 8 execution remains a hosted gate.

The candidate production changes were applied separately to #4306 head
`e7ddb9e7dc14a62f93179ec61b350c9bd12fba1b`, which includes #4332's derivative
foundation and #4333's shared mixing correction. The unchanged #4277 consumer
test was taken from `40df2730f0e6d38142dc82e7607cc8971906810c`. The 27-case
integration check reports **6 passes, 1 physical assertion failure, 20 domain
errors, zero skips**. Full Java 8 source/target compilation passed. All Java and
database inputs were verified against the exact remote source; two binary gzip
benchmark documents were unavailable through the connector and were not used.

That integration snapshot also contained direct edits to raw `COMP_EXT.csv`; the
final candidate uses the existing canonical-to-extended runtime synchronization
instead. The selected solver cases use the standard database. Final property
tests verify both loaded modes and rejection when a raw extended import bypasses
synchronization; the exact earlier overlay hashes are retained in the record.

The 11 hydrate cases encounter MEG trial temperatures of 261.8, 268.15 or
273.15 K, outside the NPL interval. Room-temperature nitrogen and unavailable
n-butane also cause explicit errors. These are unresolved integration blockers,
not passing physical benchmarks. No original feed, tolerance or physical
assertion was changed, and no peer branch was updated with the candidate.

The remaining NaCl/CaCl2 salting failure was audited independently at 298.15 K
and 30 bara using the original feeds. Both three-phase states conserve every
component to below $1.4\times10^{-16}$ on a total-feed-fraction basis, have zero
phase charge, and satisfy molecular log-fugacity equality within
$5.1\times10^{-12}$. Aqueous permittivity is now 78.285 and 78.272. Nevertheless,
propane molality increases from 0.0006652335 to 0.0007462391 mol/kg water.
Normalizing by gas fugacity still gives a mixed-salt/NaCl ratio of **1.121622**.
Thus the remaining 12.16% increase is model behavior, not inadequate flash
convergence or a mole-fraction denominator artifact. This evidence does not
identify which interaction parameter should change or justify a fit.

The [machine-readable qualification record](validation/issue-4334-dielectric-qualification.json)
retains exact heads, input hashes, every selected test outcome, original feed
inventories, phase compositions and the normalized diagnostic. Required next
steps are a qualified process-temperature nonpolar reference, verified low-temperature
MEG data, resolution of the glycerol identity, and independent mixed-salt model
validation. Any later fit needs held-out experimental checks. Once those gates
are met, integrate with #4332 before qualifying #4306 and then #4277; regenerate
shared engineering coverage from each final combined tree. #4333 is already merged.
