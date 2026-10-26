# Public MEG assessments: numerical consistency is not measured accuracy

This fixture is an assessment of unchanged ordinary SRK and PR with Huron-Vidal
mixing rule 4. It introduces no interaction coefficient, pure-component parameter,
alpha function or empirical acceptance tolerance. It must not be used to claim
that the default inhibitor model is physically qualified.

## Sources and literal data

- Kruger, Danielsen, Kontogeorgis, Solbraa and von Solms (2018),
  DOI https://doi.org/10.1021/acs.jced.8b00115.
  [Institutional peer-reviewed manuscript and supporting information](https://backend.orbit.dtu.dk/ws/files/146468436/Ternary_vapor_liquid_equilibrium_measurements_and_modeling_of_ethylene_glycol_1_water_2_methane_3_systems_at_6_and_12.5_MPa.pdf):
  Tables 2, 4 and S1, PDF pages 10, 16 and 39, visually verified.
  PDF SHA256: `886bbc0f7b0986cb2b4c804a24417f29b34b534a77d829118bcc4b85a4f37704`.
  The CSV joins the literal feed, observation and uncertainty rows by experiment ID.
- Pure-MEG vapor pressures, DOI https://doi.org/10.1021/je9004905,
  [NIST ThermoML record](https://trc.nist.gov/ThermoML/10.1021/je9004905.html),
  dataset 1, compound 1, property 1.
  Raw JSON SHA256: `08636922c070cda355ca62c4c0061c6e6c329e7f283a727cd7de4f3025a40f75`.
  The 15 point pressures and compiler-expanded 95% uncertainties are preserved.

Only measurement facts are transcribed. Manuscripts and images are not redistributed.
The uncertainty and comparison protocols are also recorded in
[issue #4573](https://github.com/equinor/neqsim/issues/4573#issuecomment-6417703485).

## Units, missing values and feed basis

Component order in the source is MEG (1), water (2), methane (3).
Pressure is MPa in the ternary CSV; the assessment multiplies it by 10 to obtain
absolute bara. Vapor mole fractions are ppm times 1e-6.
The liquid-water column is 100 times a **mass** fraction, whereas x3 is a methane
**mole** fraction. The assessment does not convert or score liquid-water composition:
the sampled/degassed-liquid basis needs explicit reconciliation before doing so.

Table S1 composition uncertainties are expanded 3-sigma (0.997 confidence), not
standard or 95% uncertainties. Convert U3_x3_times1e4 by 1e-4 and vapor uncertainties
by 1e-6. Standard instrument uncertainties are u(T)=0.05 K and u(p)=0.002p.
Do not replace point uncertainties with group averages from the paper.

The source intentionally withholds six high-temperature vapor-MEG observations
(rows 4-6 and 10-12). Their '-' markers remain missing and reports use NaN.
They are not zero, below-detection-limit observations or permitted figure estimates.
Source liquid-water uncertainty prints zero in rows 15 and 16; this is retained as
reported, without an infinite fitting weight or claim of an exact measurement.

Seven rounded feeds sum to 0.999 or 1.001. The assessment records the original sum
and explicitly divides each feed by that sum. It uses the actual T/P coordinates
and measured overall feed, not an invented equimolar combination of measured
endpoints. Feed uncertainty and rounding remain limitations of the comparison.
The earlier diagnostic's eight +/-0.0005 feed corners are sensitivity probes,
not a confidence region or a proven global bound.

## Executable assessment and acceptance boundary

Run `HuronVidalMegExperimentalAssessmentTest` through Maven. It writes four CSVs
under `target/hv-meg-assessment/`: SRK/PR mixture and pure assessments.
Each report contains calculated and measured values, source uncertainty, signed
relative error and numerical qualification. Reports deliberately do not label
experimental accuracy as passed. Missing MEG observations remain NaN.

The tests require all five native ThermodynamicModelTest boolean identities,
step-converged numerical derivatives at the existing HV setting 1e-6, component
balances 1e-10, phase/composition normalization 1e-12, and log-fugacity equality 1e-8.
They cover fresh, repeated, nearby changed, returned and reversed-component-order
states using independently configured fluids. Pure saturation additionally requires
distinct mechanically stable roots and the EOS pressure equation for both phases.
The native checks are asserted; logging-only runTest() is not used.

AARD is 100 times mean(abs(prediction/measurement - 1)), separately for each
observable and EOS. Count only ten published vapor-MEG observations; water-in-gas
and methane-in-liquid each have sixteen. A pointwise comparison with reported
uncertainty does not propagate feed or other experimental input uncertainty.

## Recorded unqualified baseline

The native assessment on master 9ef36902865a1f505b1da8c0d1e70d78f3a56a0c gave:

| Observable | Points | SRK AARD (%) | PR AARD (%) |
| --- | ---: | ---: | ---: |
| Vapor MEG | 10 | 88.473 | 171.180 |
| Vapor water | 16 | 28.990 | 20.390 |
| Liquid methane | 16 | 59.149 | 50.686 |
| Pure MEG pressure | 15 | 18.135 | 44.115 |

These values are diagnostic evidence, **not golden accuracy tolerances**.
No test asserts that retaining these deviations constitutes success.
Pure Tc=720 K, Pc=90 bara and acentric factor=0.5347 were used by those defaults.
The native pure pressures matched the independent ordinary-cubic reconstruction;
this does not validate source-specific Mathias-Copeman coefficients or attribute
all mixture error to the interaction fit.

## Remaining qualification

Reconcile source alpha, critical properties, directed HV conventions and complete
EOS-specific tuples before introducing any explicit opt-in parameter family.
Never substitute ordinary NRTL, MHV1/MHV2, CPA or PRSV coefficients.
Published CPA coefficients in the 2018 paper are not HV inputs.

These ternary data contain no liquid hydrocarbon phase and do not qualify oil
partition, methanol/TEG/heavy-fraction coverage or independent hydrate dissociation.
Ice, brine and extrapolation remain outside this fixture.
The tests do not resolve mutable sibling-clone HV state (#4575), and their runtime
is not evidence of classical-rule/HV flash overhead or process throughput.
