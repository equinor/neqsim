# Aqueous MEG total-pressure assessment

Fujita and Kikuchi (1989), DOI https://doi.org/10.11322/tjsrae.6.183,
Table 1, printed page 184. The primary publisher PDF is
https://www.jstage.jst.go.jp/article/tjsrae/6/2/6_2_183/_pdf
(206,535 bytes; SHA256
`1011af0d18684e98ac6c5af28a919d8fd159a1e81d97dc6683183c0a2235b2fe`).
The complete table and nearby equations were image-verified in campaign receipt
https://github.com/equinor/neqsim/issues/4576#issuecomment-7175159154.
Only measurement facts are transcribed; the manuscript/images are not redistributed.

## Literal data and units

The CSV follows the printed left column top to bottom, then the right column.
There are 42 observations: 36 aqueous-MEG pressures and six pure-water controls.
The first column is liquid MEG **mass percent**, not mole fraction or vapor composition.
Temperatures are degrees Celsius and pressures are absolute Torr. Convert with
`T_K = t_degC + 273.15` and `p_kPa = p_Torr * 101.325 / 760`.
Native component molecular weights convert the measured liquid mass basis into moles.
No measured vapor composition or overall feed is supplied by this source.

Actual mixture ranges are 263.12 to 333.15 K and 20.5 to 51.4 mass percent MEG.
Five rows are near 293.1 K, inside the retained Kruger vapor-MEG temperature window.
This does **not** supply missing pure-MEG vapor pressures or high-pressure partition data.
Figure 2 freezing-point crosses come from other references; they are not additional
pressure measurements. Ice, brine, hydrate dissociation and extrapolation are unqualified.

Instrument accuracy is reported as 0.05 Torr and 0.05 K, with approximately one
mass-percentage-point concentration accuracy. The authors estimate broader apparatus
pressure error of 0.15 Torr after water controls. No coverage factor or probability
distribution is supplied: do not relabel these values as expanded 95% uncertainty,
invent covariance or turn the correlation error into measurement uncertainty.

## Source transcription controls

The primary English abstract prints a plus sign before the fraction in its pressure
correlation, while body equation 6 prints minus. The body equation is

`p_Torr = 760 * 10**(5.351 - 6.4e-4*y - (1817 + 0.008*y*(y+10))/(t+240))`.

It reproduces all 42 rows with 0.809171898% AARD and at/above 20 degrees Celsius
has maximum absolute relative error 1.348116022%. The abstract-plus version is
incompatible with the table by at least 1.69e12-fold. OCR also loses the minus
in the first 20.5 mass percent observation: the image reads **-4.82 degrees Celsius**.
This is an internal source erratum, not evidence of a NeqSim equation defect.
The correlation is a transcription control fitted to the same source, never an
independent experimental validation or a replacement for the literal rows.

## Executable numerical and physical boundaries

`HuronVidalAqueousMegPressureAssessmentTest` assesses ordinary SRK and PR with both
classical rule 2 and HV rule 4. Enhanced stability checking is explicitly enabled
for the constructed tie-line feed. All native pure properties/alpha functions and
interaction selections remain unchanged. No source-paired MEG Mathias-Copeman
configuration is combined with an unidentified source-water alpha profile.

The test retains measured and predicted total pressure, both phase MEG mole
fractions, signed errors, pure-property inputs and numerical residuals under
`target/hv-meg-aqueous/`. Numerical checks use fresh, repeated, changed, returned
and reversed-component states. Distinct stable roots, EOS pressure closure,
component balance at 1e-10, normalization at 1e-12 and log-fugacity equality at
1e-8 are required. All five `ThermodynamicModelTest` boolean identities and
`checkNumerically()` at the established HV accuracy of 1e-6 are asserted.

The bubble-point vapor inventory is infinitesimal. Numerical mole differences
instead use the computed tie-line endpoints with equal phase amounts, followed
by an enhanced-stability TP flash. This is an explicitly constructed numerical-validation feed,
not a measured feed or an alteration of the measured liquid composition.
Pure-water coexistence likewise uses equal phase amounts at its saturation pressure.

No assertion treats experimental disagreement as success. No coefficient, alpha,
formulation or physical accuracy tolerance is fitted or activated. Total pressure
does not determine the separate vapor-MEG error or water partial pressure.
Whole-source holdout status was frozen before any new candidate selection;
independence from historical parameter fits still requires a training-lineage audit.
Retained Kruger inhibitor-partition and reverse-solubility controls remain separate.
The existing performance matrices retain their original heads and do not establish
new timing results for this assessment or current master.

## Reproduced numerical cause and bounded repair

Before repair, a liquid homogeneous reference selected trial indices 0 to 1,
but the ordinary stability loop tested `j >= end`. It executed neither trial.
At 268.33 K, SRK rule 2/4 and PR rule 4 cold tie-line feeds therefore returned
one phase despite a distinct, fugacity-equal split with lower dimensionless Gibbs
energy than either homogeneous root. Both multiphase and enhanced flags were tested.
The direction repair uses the existing enhanced/automatic-LLE filtering predicate.
Legacy traversal and tangent-plane reset behavior remain unchanged; this does not
repair every legacy/default phase-discovery limitation. `FlashStabilityTrialOrderTest`
requires actual iterations for both reference orders. No gamma, HV coefficients,
first derivatives or solver tolerances change.

## Measured pressure errors and pure-alpha follow-through

The first complete native assessment gives the following diagnostic errors,
which are not accepted physical tolerances:

| Model | 36 mixture pressure AARD | Six pure-water AARD |
| --- | ---: | ---: |
| SRK rule 2 | 19.2273% | 23.0342% |
| SRK rule 4 | 27.1591% | 23.0342% |
| PR rule 2 | 13.0602% | 13.7854% |
| PR rule 4 | 16.9356% | 13.7854% |

The same six water measurements are assessed using the already retained
EOS-specific 2017 Twu water inputs from `hv-co2-source`, without fitting. Declared
Tc=647.096 K and Pc=220.64 bara are reference inputs, not exact source-author
configuration. SRK L/M/N=(0.36117,0.87308,2.4389); PR=(0.40274,0.87000,1.8869),
with the existing source-paired PR cubic constants. Pure-only AARD becomes 1.0455%
for SRK and 0.9381% for PR. Input/parameter uncertainty is not propagated and not
every deviation is thereby inside the reported apparatus error.

This advances compatible pure-water source adoption as a staged assessment only.
The Twu water function is not transferred into a fitted water-MEG or light-gas
inhibitor tuple. That needs compatible source conventions or an identifiable bounded
fit with frozen source-family holdouts and both-direction partition controls.
Missing low-temperature pure-MEG measurements still prevent attributing all
vapor-MEG error to interactions. No default pure property changes.

## Endpoint precision and an adverse classical control

The first cold TP result must already discover two interior phases and meet
1e-10 component balance, 1e-12 normalization and 1e-8 log-fugacity closure.
A detached clone then receives five native `TPmultiflash.solveBeta()` calls
without a stability rerun, phase addition, or composition/beta seeding. Phase
count/types must persist and dimensionless Gibbs energy may not increase beyond
1e-10 roundoff. The same saturation solve is repeated once before constructing
the feed; the pressure change is bounded by 1e-8 bara. These are test-only
precision checks, not new production tolerances or a replacement for cold discovery.
All other controls require recovered beta 0.5 within 1e-8 and component endpoints
within max(1e-13 absolute, 1e-8 relative). The retained first-call study motivated
refinement; it did not justify relaxing those endpoint bounds.

Exactly one source row is explicitly classified as a metastable classical bubble
branch: ordinary SRK rule 2 at 263.12 K, 40.9 mass percent MEG (row 28).
Five independent pressure starts reproduce bubble pressure about
0.00169683096464 bara and liquid xMEG=0.1672640663. A cold enhanced TP flash
finds a different liquid xMEG about 0.21462219 with gas beta about 0.61090579.
Both have fugacity closure, but the cold split has lower G/(nRT) by about
2.47e-6; the alternate liquid has tangent-plane distance about -6.36e-6
relative to the bubble liquid. The local composition curvature at the source
liquid is positive, with a negative-curvature interval between the two minima:
this is a metastability witness, not a spinodal classification at the source.
The nearby 263.32 K history control is classified by the same explicit composition
and T condition. It instead finds a higher-G stationary branch (positive TPD),
which remains an explicit cold-discovery limitation under #2937. Its closure and
derivative checks do not certify physical equilibrium. No global solver repair is
implied by the bounded trial-order fix.

This row's raw pressure/error remains in the unfiltered 36-row SRK rule 2 AARD.
It is **not qualified physical equilibrium**. Its report disposition differs from
the 167 other source/EOS/rule observations. The lower-G candidate is not claimed
to be the global equilibrium. All HV source states recover their saturation
endpoints after refinement. No phase-discovery or parameter change is inferred
from this adverse classical control. Campaign #4576 owns the physical follow-through;
#5303 owns the independently reproduced enhanced trial-order defect.

History CSV disposition codes are 0 for recovered endpoints, 1 for the source
metastable bubble branch, and 2 for the nearby higher-G cold branch. All 840
history results are retained. At the nearby point the paired baseline is one
phase with G/(nRT)=-0.5716253470; the candidate is two phases at -0.5729408574,
while the feasible source-composition tie-line is lower still at -0.5729415980.
The candidate improves on master but remains higher than the feasible split by
7.41e-7. See #2937 comment 7204198317. This limitation is explicitly diagnostic;
passing derivative or balance tests is not an assertion of global equilibrium.

The ordinary rule 4 water/MEG selection here is the existing database behavior,
assessed without endorsement of its unresolved coefficient lineage. This run
activates no dormant tuple and does not reinterpret ordinary NRTL coefficients
as source-proven infinite-pressure HV modified-NRTL data. The seven recovered
PR water/light-gas tuples and existing staged profiles are preserved unchanged.
The total-pressure observations cannot replace reverse gas solubility or inhibitor
partition holdouts. Ice, brine, independent hydrate dissociation and extrapolation
beyond the measured liquid ranges remain unqualified.

## Current timing and profiling receipt

The unchanged selected-pair benchmark completed 96 independent JVMs, 960 rounds
and 984,000 measured operations, with two forks, reversed baseline/candidate/control
order, matched profiles and a separate JVM for every profile/rule/operation/size.
The fifteen-component cases include full flashes; five-component cases include
init1, init3 and full flash. All unselected pairs stay CLASSICAL. The balanced
cold enhanced-mode cases above are separate from this default-path overhead control.

Independent audit reconstructs all 18,000 retained phase/component rows. Same-rule
baseline/candidate/control vectors are bit-identical; allocations also match in
every cell. Maximum balance, normalization and log-fugacity residuals are
4.11e-15, 2.22e-16 and 8.48e-12. Small feeds are GAS/AQUEOUS and large feeds
GAS/OIL/AQUEOUS. No build, test or other simulation from this run deliberately
overlapped the timing matrix; lightweight metadata/text work continued. Hardware
was shared and unreserved.

| Declared profile | Components | Operation | Candidate HV/CLASSICAL ratio, two forks |
| --- | ---: | --- | ---: |
| PR | 5 | init1 | 7.26, 10.32 |
| PR | 5 | init3 | 14.25, 17.37 |
| PR | 5 | flash + init3 | 7.16, 4.36 |
| PR | 15 | flash + init3 | 21.54, 29.17 |
| SRK 2017 Twu | 5 | init1 | 12.14, 21.09 |
| SRK 2017 Twu | 5 | init3 | 23.27, 24.97 |
| SRK 2017 Twu | 5 | flash + init3 | 12.89, 11.08 |
| SRK 2017 Twu | 15 | flash + init3 | 46.72, 47.30 |

These compare different thermodynamics/solver work, not isolated evaluator overhead.
For matched same-rule work, candidate/baseline ranges 0.846–1.673, while the
identical-baseline control itself ranges 0.748–2.084. Neither a speedup nor
statistical non-inferiority is established. Both favorable and adverse runs remain
in `performance-raw.zip`; `evidence/performance-summary.json` reports every fork,
allocation count, topology and residual. The archive includes the exact plan,
source/class/classpath pins, native process logs and independent raw-data auditor.

Separate whole-harness JFR runs, excluded from timing, record the HV activity
evaluator `getGammaWithRowPureTerms` as the first NeqSim frame in 1,140/1,210 PR
and 1,746/1,961 SRK execution samples. Full extracted execution-sample events and
profiled result states are retained in `profiling-samples.zip`; these include
startup/warmup and are descriptive samples rather than exact cost fractions.
The existing r69 scratch-storage optimization remains rejected. No state sharing,
physical shortcut or tolerance change is introduced to improve a timing number.

The full-multiphase-flag control also retains the nearby higher-G classical branch;
its paired source/nearby baseline/candidate results are in `evidence/`. The final
post-refinement guard requires interior phase amounts as well as closure, and
passes the four affected cases on both Java 8 and Java 17.

Gibbs comparisons use `g* = sum_p beta_p sum_i x_i (ln(x_i)+ln(phi_i))`.
The common pure references and pressure terms cancel for the same balanced feed
at the same T/P. The alternate-liquid tangent-plane witness is
`sum_i x_alt,i [ln(x_alt,i*phi_alt,i)-ln(x_ref,i*phi_ref,i)]`. A negative value
rejects that reference branch as global equilibrium; it does not prove that the
observed alternative is the global minimum. The raw curvature table includes
nearby composition points for an independent finite-difference cross-check.
