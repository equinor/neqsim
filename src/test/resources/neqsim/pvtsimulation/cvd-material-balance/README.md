# Whitson–Torp NS-1 primary numerical accounting receipt

Source: C.H. Whitson and S.B. Torp, *Evaluating Constant-Volume Depletion Data*,
JPT 35(3), 610–620 (1983), DOI [10.2118/10067-PA](https://doi.org/10.2118/10067-PA).
Table 1, printed p614/PDF p5. The full author-uploaded scan was retrieved from
[this public author upload](https://www.researchgate.net/profile/Curtis-Whitson/publication/250088007_Evaluating_Constant-Volume_Depletion_Data/links/5443d4860cf2a6a049ab0443/Evaluating-Constant-Volume-Depletion-Data.pdf)
without authentication, on 2026-11-03. SHA256:
`99c7245e552e1b57389294c78f9433f48d1e71545db1aca15e8cb04acc2f1b91`,
1,028,236 bytes, 11 PDF pages. The scan is SPE copyrighted; public access is not
an open license. The PDF is not redistributed in this repository. These files
attribute independently transcribed numerical facts and original audit code;
no permission to redistribute the paper is inferred.

Table 1 was visually checked against the rendered page, including n-butane
**1.31 mol% at 4314.7 psia** (OCR misread 1.81). `ns1-table1.csv` admits pressure,
gas Z, cumulative production, liquid cell-volume fraction and all 11 vapor
compositions at seven pressures. `ns1-final-liquid.csv` preserves both the
calculated and experimental final liquid columns. No row deletion, composition
normalization, pseudo-component substitution, EOS regression or digitization of
plots was applied. Source C7+ molar masses and specific gravities are not inputs
to this component-mole accounting and are not silently treated as EOS constants.

The sample is the publication's NS-1 North Sea rich condensate, recombined from
separator samples. All pressures are **absolute psia**; the Java fixture converts
using 6894.757293168 Pa/psi. Temperature is 280°F = 410.9277777777778 K. Z is
vapor compressibility. Production is percent of initial moles. Liquid volume is
percent of the initial saturated all-vapor cell volume. The CVD protocol retains
liquid and withdraws equilibrium vapor at each endpoint to restore that fixed
cell volume. Initial zero liquid/production entries are exact protocol values.
Vapor analyses were laboratory-based, smoothed/adjusted in the publication;
these are not raw chromatograms. Whole-sample/raw-analysis uncertainty is missing.

The paper's later separator calculations use 1014.7 psia/155°F,
264.7 psia/80°F and 14.7 psia/60°F. This accounting needs no separator or standard
volume conversion; those reported conditions do not turn calculated separator
plots into admitted exact measured data. Table 3 characterized/adjusted heavy
properties and Table 4 calculated liquid densities are not measured holdouts.
There is no associated measured field history in these fixtures.

Run from the repository root with the selected Python interpreter (standard
library only):

```
python3 src/test/resources/neqsim/pvtsimulation/cvd-material-balance/independent_reference.py
```

The output reproduces `reference-results.json`, including all six depletion
stages and 66 component rows. It uses decimal precision 50, with outward-rounded
interval operations. Normal and `-O` output must be byte-identical; validation
uses exceptions, not removable Python assertions. No NeqSim output enters this
reference. The nominal equations and protocol are documented in the
[guide](../../../../../../docs/pvtsimulation/laboratory_cvd_material_balance.md).

Tolerances frozen before implementation: compare Java amounts with decimal
reference at 2e-15 mol per initial mol and liquid fractions at 2e-14. Compare the
paper's calculated final-liquid column at half its printed last digit,
0.0005 mol% = 0.000005 mole fraction. The separate conservative source-rounding
screen uses pressure ±0.05 psia, Z ±0.0005, vapor ±0.005 mol%, production ±0.0005
percentage point and liquid volume ±0.05 percentage point; protocol zeros fixed.
Experimental final composition has ±0.005 mol% printing resolution. Independent
boxes allow impossible correlated combinations and are deliberately conservative;
no bin is a measurement sigma or a statistical confidence interval.

The nominal final liquid matches all 11 published calculated values to the frozen
half-digit (maximum error 0.000447811 mol%). Experimental methane and C7+ remain
outside even the conservative rounding-only envelope. The Java final-analysis
bounds use the larger distance to either propagated endpoint plus experimental
half-digit; these symmetric envelopes retain the same two failures. This is a
source-consistency admission blocker requiring raw analysis, uncertainty and
smoothing provenance reconciliation, not proof of measurement invalidity. The
calculated column cannot be relabeled independent experimental accuracy. The
JUnit test must retain this failed report, not tune or loosen it to pass.

The API checks produced, retained, vapor and liquid component sums independently
against their total amounts. A biased composition column must not pass merely
because its errors cancel in the liquid difference. A separate analytic
cancellation probe failed before these total checks and passes with them; the
regression retains raw non-unit inputs as failed diagnostics without normalizing
or changing the frozen inventory tolerance.

The arithmetic implementation supports only the stated protocol. It does not
qualify wet/critical phase behavior, original/tuned EOS predictions, reservoir
volume, aquifer/posterior uncertainty, recoverable reserves, field holdouts or
the separate Tran2025 full-equation/benchmark acceptance in #4740.
