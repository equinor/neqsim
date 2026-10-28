# Source-compatible SRK-HV MEG qualification

`HuronVidalLiteratureReproductionTest` extends the existing explicit Folas/Derawi
MEG/n-heptane reproduction with native consistency, state-history and pure-alpha
checks. These files record calculated diagnostics on master
`f2806f569472e4c09e494703febd3085545d51df` plus the accompanying qualification test,
using OpenJDK 17.0.20+8. No production parameter, alpha implementation, tolerance
or default is changed, and no parameters are fitted to these observations.

## Source and model contract

- [Derawi thesis (2002)](https://backend.orbit.dtu.dk/ws/portalfiles/portal/5653725/PhD_Derawi.pdf),
  Table 3.3 (printed page 34 / PDF page 46), Tables 4.1 and 5.5: seven measured
  MEG/n-heptane liquid-liquid states, and the paired MEG pure configuration.
  Table 3.3 reports 100 times mass fraction; the literal inputs divide by 100.
  Reported relative measurement uncertainties are 2% for MEG and 5% for hydrocarbon.
- [Folas thesis](https://backend.orbit.dtu.dk/ws/portalfiles/portal/123926886/CDocuments_and_SettingsalbDesktopPh.D._Georgios_K._Folas.pdf.pdf),
  Tables 6.2 and 6.6: directed volume-weighted modified-NRTL HV coefficients and
  published correlation errors. This is infinite-pressure SRK-HV, not ordinary
  low-pressure NRTL or an interchangeable PR fit.
- Pure-MEG measurements: DOI https://doi.org/10.1021/je9004905,
  [NIST ThermoML](https://trc.nist.gov/ThermoML/10.1021/je9004905.html), dataset 1,
  compound 1, property 1. The existing literal
  [15-point input](../hv-meg-qualification/pure-meg-2009.csv) and its source audit
  remain unchanged. Pressure and compiler-expanded 95% point uncertainties are kPa.
  The original JSON SHA256 is
  `08636922c070cda355ca62c4c0061c6e6c329e7f283a727cd7de4f3025a40f75`.

The test-local MEG configuration is Tc=720 K, Pc=82 bara and attractive term 4
with dimensionless Mathias-Copeman coefficients (1.1121, 0.9679, -1.4200), applied
to every allocated phase. The source alpha fitted reduced-temperature range is
0.4–0.9; the pure-pressure observations span approximately 0.427–0.534.
The binary model uses ordinary SRK n-heptane properties, rule 4, multiphase
checking, and a 0.5/0.5 mol feed at 1 bara. With MEG first, D12=(g12-g22)/R=430 K,
D21=(g21-g11)/R=2595 K and alpha=0.13; both temperature offsets are zero and both
directed pair flags select HV. Reversed order maps by component identity.
These are the already catalogued source values, not newly calibrated tuples.

## Executable numerical protocol

Run `HuronVidalLiteratureReproductionTest` with Maven. It writes both CSVs under
`target/hv-source-compatible/`. The checked-in CSVs are recorded results, not
golden predictions substituted for measurements.

All five `ThermodynamicModelTest` fugacity, composition Gibbs-Duhem, Hessian,
pressure and temperature identities are asserted at 1e-8. `checkNumerically()`
is asserted at the established mixed absolute/relative 1e-6 tolerance, including
its step-refinement requirement. Serialized source state must remain byte-identical
after the numerical diagnostic. No logging-only `runTest()` is accepted.
The LLE states also require component balance 1e-10, phase/composition
normalization 1e-12 and log-fugacity equality 1e-8.

Both component orders traverse 315.95, 315.95, 329.75, 351.85 and 315.95 K,
comparing both liquid compositions with independent fresh configurations at
max(1e-13 absolute, 1e-8 relative). Independent fixed-composition liquid systems
at T +/- 0.001 and 0.0005 K resolve 28 temperature derivatives (seven states,
two liquid phases, two components); coarse/fine and analytical differences must
agree within 1e-6 times max(1, abs(analytical derivative)). These are numerical
tolerances, not experimental acceptance bands.

Pure saturation additionally requires two distinct mechanically stable roots,
the EOS pressure equation for both phases and log-fugacity equality 1e-8.
The initial direct bubble-state numerical diagnostic failed at 307.7 K:
phase 0/component 0/mole derivative had analytical 0, coarse -2.9335270513883067,
fine 0. The bubble solver sets beta0=1e-10, so its tiny phase inventory leaves
the central mole difference dominated by roundoff. No tolerance or checker was
changed. Pure coexistence permits arbitrary phase fraction: the test explicitly
sets beta=0.5 at the computed saturation pressure, reinitializes, asserts that
pressure is unchanged and then performs all native checks. Both original and
validation phase fractions are reported. This conditions the pure derivative
check; it does not claim qualification of the infinitesimal-amount stencil.
Mixed LLE phase fractions are never adjusted.

## Measured errors and limits

| Observable | Points | AARD (%) | Interpretation |
| --- | --- | --- | --- |
| MEG mass fraction in heptane | 7 | 21.2619328 | Reproduces the published 21.2%; exceeds the stated 2% measurement uncertainty |
| Heptane mass fraction in MEG | 7 | 6.7065626 | Reproduces the published 6.7%; exceeds the stated 5% measurement uncertainty |
| Pure-MEG vapor pressure | 15 | 5.8616355 | All 15 predictions inside the supplied compiler-expanded 95% point-pressure uncertainty |

`MEG-heptane.csv` preserves both directional measured/calculated mass fractions
and signed relative errors. `pure-MEG.csv` preserves calculated/measured kPa,
point uncertainty, signed error, pointwise uncertainty comparison, exact pure
parameters, original/validation beta and numerical outcome. AARD is
100 times mean(abs(prediction/measurement - 1)). Pointwise pressure uncertainty
does not propagate temperature, feed, pure-property or fitted-parameter uncertainty.
No empirical accuracy gate is introduced.

Pure-pressure agreement helps reconcile the source alpha with its binary fit;
it does not qualify gas/oil/water inhibitor partition, reverse gas solubility,
water/methanol/TEG/heavy-fraction pairs, PR parameter transfer or hydrate
dissociation. Ice, brine and extrapolation beyond the measured ranges are outside
this evidence. Missing water-HV pure-alpha provenance and original PR/water-MEG
tables remain campaign dependencies. Existing default-model error reports remain
valid for their different configuration.

This test/documentation batch changes no runtime implementation. It makes no
new timing or allocation claim; the matched classical/HV performance evidence
and its noise limitations remain in the merged allocation milestone #4660.
