---
title: "CO2–KCl interaction provenance"
description: "Source basis, model semantics, holdout diagnostics, and limitations for the electrolyte-CPA carbon-dioxide–potassium interaction."
---

`SystemElectrolyteCPAstatoil` uses a potassium-specific CO2 short-range
interaction to reproduce the sign and magnitude of KCl salting-out near the
single calibration state. The change corrects the previous strong salting-in
response. It is not an empirical hydrate-temperature offset and does not alter
the hydrate model, phase-equilibrium tolerances, or experimental fixtures.

## Parameter and model semantics

The existing electrolyte-CPA Born-diameter convention is retained. An attempted
global change to the Solbraa (2002) Appendix C characteristic diameters was
rejected because exact-base controls showed that it changed NaCl reactive-state
conservation and methane/NaCl high-salinity hydrate behavior, systems outside
this KCl-specific scope. The Onda calibration below therefore applies to the
current NeqSim model convention and must not be transferred to a different Born
formulation without refitting and independent validation.

The symmetric CO2/K+ coefficients are
`W0 = -2.2981e-4 m3/mol`, `W1 = 0 m3 K/mol`, and
`W2 = 0 m3/mol`. The mixing rule evaluates

$$
W_{ij}(T)=W_0+W_1(1/T-1/298.15)
 +W_2[(298.15-T)/T+\ln(T/298.15)].
$$

These are short-range electrolyte-CPA coefficients, not dimensionless cubic-EOS
binary interaction parameters. The CO2/Cl− pair retains its legacy
`W0 = 1.27e-4 m3/mol`. KCl data constrain the sum of the two gas–ion
contributions; retaining chloride is therefore a fixed allocation convention,
not an independent ion measurement.

The specialized default applies only to `PhaseElectrolyteCPAstatoil`, and only
while both shared CO2 gas–ion constants retain their library defaults. Explicit
pair setters and explicit changes to either shared constant take precedence.
ScRK and other electrolyte-CPA phase implementations retain their existing
defaults.

## Calibration source

The only calibration target is the Onda et al. (1970) Table 3 correlation at
298.15 K:

$$
k_s=-0.2277-0.0362+0.3416=0.0777\ \mathrm{L/mol}
$$

in the source convention
$\log_{10}(\alpha_{water}/\alpha_{salt})=k_s I$, with solution molarity
$I=c_{KCl}$. The executable check evaluates infinite-dilution CO2 at 1 mol KCl
per kilogram of water and converts to solution concentration using the modeled
physical density. Onda's value is a global least-squares ion contribution
derived from several gases and salts, including compiled literature data; it is
not a direct raw CO2–KCl series.

`W1 = W2 = 0` is a zero-order temperature extrapolation. Onda alone does not
qualify that extrapolation over the 269–363 K holdout range.

## Independent holdouts

No He–Morse (1993), Yasunishi–Yoshida (1979), Dholabhai et al. (1993), mixed-salt,
or Burgass point is used to choose the coefficient. Repository diagnostics keep
those sources separate:

- He–Morse CO2 molality states test the 273.15 K KCl concentration response at
  matched CO2 partial pressure.
- Yasunishi–Yoshida Ostwald coefficients test an independent 298.15 K
  concentration basis.
- Dholabhai K10 and K15 points test hydrate equilibrium at low and high pressure
  without a temperature shift.
- NaCl, CaCl2, mixed-salt, and Burgass datasets remain controls for neighboring
  chemistry and hydrate behavior.

Passing numerical checks establishes state construction, conservation, and the
correct salting-out direction. It does not show agreement within experimental
uncertainty. The generated CSV diagnostics report residuals without converting
them into a relaxed acceptance tolerance.

On the implementation head, the six 273.15 K He–Morse states decrease
monotonically with KCl and have 29.38% absolute relative deviation on the
reported molality basis. All seven Yasunishi–Yoshida concentrations are matched;
their Ostwald coefficients decrease monotonically and have 17.69% absolute
relative deviation. The eight Dholabhai hydrate holdouts give K10 RMSE 0.179 K
(maximum absolute error 0.298 K) and K15 RMSE 0.247 K (maximum absolute error
0.348 K). These figures describe this exact model head; they are not acceptance
tolerances and remain subject to independent review.

The salt-only KCl water-activity diagnostic remains distinct from the dissolved
CO2 correction. At 273.15 K it differs from the Archer reference by −0.00215 for
K10 and −0.00467 for K15 before CO2 is added. The saturated Burgass NaCl control
retains five points within 1 K and a sixth error of −1.121 K at 194.77 bara.

Electrolyte-CPA association iterations reject non-finite Hessian corrections before
changing site fractions, allowing the volume solver to retry without poisoning the
final brine state. This numerical safeguard does not refit the interaction parameters
or relax the component-balance and fugacity checks.

## Full-family Dholabhai benchmark

`CO2BrineHydrateFullFamilyAssessmentTest` retains **110 measured endpoints**
from **28 recipe labels**, not just the eight K10/K15 states. The fixture
`co2_brine_hydrate_dholabhai1993_full.csv` preserves Table I wet-basis mass
percentages, original temperatures in K, pressures in MPa and Table III/IV
membership (54/56 points). Family counts are pure water 4, NaCl 25, KCl 21,
CaCl2 21, NaCl-KCl 17 and NaCl-CaCl2 22. Recipe labels such as Na10-1/Na10-2
retain their distinct measured compositions; nominal concentrations are not
substituted. The fast test freezes the complete fixture hash and checks all
eight overlaps against the existing K10/K15 fixture.

Source: Dholabhai, Kalogerakis and Bishnoi (1993), DOI
[10.1021/je00012a045](https://doi.org/10.1021/je00012a045), Tables I, III and IV,
pp. 651-652. All adopted numerical values were visually checked against the
user-supplied paper and reconciled with the prior
[executed Colab transcription](https://github.com/EvenSol/NeqSim-Colab/blob/1246257860896406e36e0e3f515db045429630cc/notebooks/flowassurance/co2_hydrate_brines_and_drilling_fluids.ipynb).
The ACS article is copyrighted: only factual values and source identifiers are
retained, not the PDF, figures or article prose. The fixture is a numerical
benchmark transcription, not a claim that the article has an open license.
Pressure is converted by **1 MPa = 10 bar**. The paper added atmospheric
pressure to corrected gauge readings, so no second atmospheric correction is
made. Reported RTD accuracy of +/-0.06 K and gauge accuracy of +/-15 kPa are
instrument specifications, not a combined endpoint uncertainty or an accuracy
pass band.

The unchanged nonreactive electrolyte-CPA/GF construction uses 1 kg water,
10 mol CO2, mixing rule 10 and the actual three-salt recipe, with independent
cold construction and a 278.15 K hydrate-search guess at every pressure. Salt
amounts use the total water-plus-all-salts denominator. The output retains
calculated minus measured temperature, aqueous water-activity proxy, dissolved
CO2 fraction, guest and water fugacities, phase labels, native saturation,
original-feed closure and numerical disposition for **every** endpoint.
Per-family and per-recipe bias, MAE, RMSE and maximum error are recomputable
from accepted raw rows; rejected rows remain present and invalidate the final
numerical assertion. Opposing mixed-salt recipe errors must not be hidden by
a pooled signed mean.

Numerical acceptance reuses the existing hydrate endpoint gates: GAS+AQUEOUS,
hydrate residual <=1e-6, molecular log-fugacity residual <=1e-8, reconstructed
component and original-input mole-fraction residuals <=1e-10, phase charge
<=1e-10, composition normalization <=1e-9, phase-fraction normalization <=1e-12
and nonaqueous ion mole fraction <=1e-40. Completed and failed rows are written
before the final assertion. There is **no experimental-error assertion** and
no fitted parameter, temperature correction or new tolerance. The previous
28-state contribution assessment and Burgass saturated/finite-inventory
controls remain separate and unchanged. Model-only water-activity/fugacity
splits are not independent high-pressure observations or causal temperature
corrections.

Five synthetic-seawater points are excluded explicitly because Table II
requires additional species beyond this three-salt construction; they are not
replaced by NaCl. Ice stability, salt precipitation, hydrate kinetics, MEG and
commercial-mud qualification remain outside scope. Extending coverage to 110
points does not itself qualify the model or close independent scientific review.

The initial assessment on production base
`015fdaa24914c0c8cb587846e6da7a4594fc56fc` passed all 110 numerical endpoints,
with the following experimental residuals (calculated minus measured):

| Family | Points | Bias / K | RMSE / K | Maximum absolute error / K |
|---|---:|---:|---:|---:|
| Pure water | 4 | 0.363967 | 0.383012 | 0.524395 |
| NaCl | 25 | 0.158030 | 0.324867 | 0.777350 |
| KCl | 21 | 0.097114 | 0.259109 | 0.539193 |
| CaCl2 | 21 | 0.444887 | 0.760814 | 2.145498 |
| NaCl-KCl | 17 | -0.907341 | 1.278908 | 2.286861 |
| NaCl-CaCl2 | 22 | -0.015232 | 0.510562 | 1.186522 |

The concentrated Na7K10 and Na15K5 recipe biases are -1.619736 K and
-2.013034 K, respectively; KCl-only agreement does not qualify mixed brines.
Ca20 has +1.611812 K bias and a +2.145498 K maximum error. The near-zero
NaCl-CaCl2 family bias hides opposing Na5Ca15 (+0.559971 K) and Na15Ca5
(-0.944962 K) recipe biases. These observed discrepancies remain validation
evidence, not new fitting targets or acceptance tolerances.

## Salt-free CO2 baseline discriminator

`CO2WaterBaselineReferenceAssessmentTest` compares nineteen evaluated liquid CO2
fractions from the [IUPAC–NIST CO2/water evaluation, system 62_1](https://srdata.nist.gov/solubility/sol_detail.aspx?sysID=62_1),
Table 1 **Model** column, at 273.15–363.15 K and **101.325 kPa CO2 partial
pressure**. This includes the 323.15, 348.15 and 363.15 K temperatures of the
He–Morse KCl series, so salt-free absolute bias can be separated from its KCl
salting slope without fitting a production parameter. These are evaluated model values, not raw measurements. The fixture
`co2_water_carroll1991_evaluation.csv` records the source, units and reuse basis;
only nineteen factual values are retained, not an article or figure. Carroll,
Slupsky and Mather (1991), [DOI 10.1063/1.555900](https://doi.org/10.1063/1.555900),
primary Table 4 was checked at overlapping temperatures. The displayed web
Eq. (1) has a normalization inconsistency with its table and is not used.

The nonreactive electrolyte-CPA calculation uses 1 kg water, 10 mol CO2,
explicit zero K+/Cl-, mixing rule 10 and multiphase checking. Total pressure
is solved with a bracketed secant/bisection iteration until
`y_CO2 * P = 1.01325 bara` within 1e-10 bara. Each trial flashes a fresh fluid
with the same construction and feed inventory; this avoids reusing a collapsed
phase state during pressure changes. Trial states must contain gas and aqueous
phases. Total pressure and guest fugacity are reported separately. Three constructions per temperature retain baseline,
explicit database reset and reversed component insertion order: **57 calculated
records from nineteen independent reference states**, not 57 independent observations.
`apparent_f_over_x_bara` is a finite-composition endpoint ratio, not a newly
qualified infinite-dilution Henry constant.

Frozen numerical gates are molecular log-fugacity residual <= 1e-8, original
component balance <= 1e-12 (relative for molecular components; absolute mol for
zero-ion inputs), phase-fraction/composition normalization <= 1e-12, charge
residual <= 1e-12 mol, partial-pressure mismatch <= 1e-7 bara and identical-repeat
CO2 mole-fraction difference <= 1e-10. Returned phases must be GAS+AQUEOUS.
All completed records are written before the final numerical assertion, including
when a later trial raises an exception; an incomplete assessment remains failed. The
test supplies **no physical-accuracy pass band**; signed relative reference
residuals are retained even when numerical gates pass.

The previously retained 273.15–298.15 K exact-master subset has a -27.30% to
-19.25% salt-free solubility residual. The extended 303.15–363.15 K rows retain
signed residuals as diagnostic evidence; exact-head hosted results must be
inspected before drawing a temperature-dependent conclusion. Runtime diagnostics identify rule `ClassicSRKT2`, CO2
2A/two sites and water 4C/four sites, cross beta 0.075 and cross energy
8327.5 J/mol. Stored zero cross energy selects the combining rule; it does not
disable association. The default binary coefficients give
`k(T) = -0.27686 + 0.001121 * T[K]`. These are observed legacy values, not a
source-authenticated recommendation. Complete joint parameter provenance and
independent scientific review remain required before changing this model.

The explicit-zero-ion construction is deliberate. The separate 21-record
ion-omitted/ordinary-CPA [precision diagnostic](https://github.com/equinor/neqsim/issues/4234#issuecomment-6262818833)
retains ten component-balance failures at the same 1e-12 gate. This passing
subset neither erases those failures nor qualifies other constructions.
Full-family experimental qualification and independent review of #4367 remain
open despite the expanded diagnostic coverage; no legacy parameter, hydrate
equation or tolerance is changed here.

## Matched-temperature water and guest fugacity partition

`CO2BrineHydrateMeasuredStateAssessmentTest` reuses the same source-audited
110-point fixture without adding measurements or changing any recipe. At each
original measured temperature and absolute pressure it independently calculates
the actual brine and a salt-free CO2/water comparator, both with 1 kg water and
10 mol CO2. The nonreactive electrolyte-CPA, mixing-rule-10 and native GF hydrate
conventions are unchanged. The comparator contains dissolved CO2; it is not a
CO2-free pure-liquid reference.

Let `faq` and `fhyd` denote aqueous and hydrate water fugacity in bara. The
dimensionless quantities are:

- `R_brine = ln(faq_brine / fhyd_brine)`;
- `R_water = ln(faq_water / fhyd_water)`;
- `A = ln(faq_brine / faq_water)`;
- `G = -ln(fhyd_brine / fhyd_water)`;
- `R_brine = R_water + A + G`.

At common T/P and hydrate structure the GF empty-lattice and pressure prefactor
is shared, so G isolates the model's guest-fugacity/occupancy contrast. Structure
indices are retained rather than silently assuming a common structure. A is a
model aqueous-water fugacity contrast, not an independently measured salt-only
water activity. R_water still combines neutral CO2/water and hydrate-reference
limitations; this algebra does not uniquely identify a physically incorrect
parameter or validate the shared hydrate reference. No residual is converted
into an empirical temperature offset.

The assessment also independently cold-repeats every brine and calculates its
own native hydrate root from a 278.15 K search guess. It then cold-checks the
fixed-state calculation at that root. This gives 440 fixed-state fluid checks
plus 110 native root searches; no CSV from another producer is an input. The
native `setFug()` reference initialization is followed by unit hydrate-water
composition, matching the production temperature operation. A regression test
deliberately substitutes the feed water fraction and verifies that the same
root-consistency gate rejects that finite but invalid reference.

The CSV retains all 110 pairs, the original measured T/P, independently
calculated root temperature, raw water and CO2 guest fugacities, dissolved CO2
mole fractions, selected structures, phase identity, original-feed/total and
reconstructed closure, charge, molecular fugacity, normalization, ion leakage,
phase trial distances, root residual, repeat difference and disposition. CO2
mole fraction is not molality. Known measured inputs and any raw root
temperature remain in full-width failed records. All outcomes are written
before the final numerical assertion; an exception or numerical rejection
cannot become accepted physical evidence.

Frozen gates remain GAS+AQUEOUS, finite non-negative normalized fluid states,
original-feed/total/reconstructed fractional closure <= 1e-10, charge <= 1e-10,
molecular log-fugacity <= 1e-8, beta normalization <= 1e-12, composition
normalization <= 1e-9 and nonaqueous ion fraction <= 1e-40. Identity closure and
the independent cold repeat are <= 1e-12; native-root `1 - fhyd/faq` is <= 1e-6.
Hydrate equality is intentionally not required at measured temperature: that
residual is the diagnostic observable. No experimental-error pass band or
small-G assertion is introduced.

The prior [executed investigation](https://github.com/equinor/neqsim/issues/4234#issuecomment-6284035457)
on master `9d6366f3af86ac79522a533b6c7da3696fc4b310` found structure I for
all paired states, maximum absolute G about 6.65e-6 and maximum `abs(G/A)`
about 7.68e-5 across 106 saline points. These are observed model-only contrasts,
not physical qualification or frozen fitting targets. Na7K10/Na15K5 residuals
remain negative and Ca20 positive. Independent mixed-brine activity/solubility
evidence, complete joint neutral-parameter provenance and independent #4367
review remain necessary before a physical correction. Existing seawater,
ice/precipitation, kinetics and commercial-fluid exclusions still apply.

## CO2-water donor-incidence diagnostic

The Dholabhai assessment reports the molecular association probabilities needed
by the physical-permittivity model without changing the production model. The
definition follows Coelho, Franco and Firoozabadi (2025), equation 22: a
molecule-pair quantity is a selected-site bond incidence. No implementation or
fitted parameter is copied from that work.

NeqSim's existing topology assigns water the 4C site signs `{+,+,-,-}` and
CO2 the acceptor-only 2A signs `{-,-}`. Let `X_D` be the mean of the two
water donor-site fractions and `X_A` the mean of the two water acceptor-site
fractions. Because a water acceptor can bond only a water donor in this
water/CO2 topology, its mass-action balance gives

```text
P_ww = 1 - X_A
P_wc = X_A - X_D
P_w  = P_ww + P_wc = 1 - X_D
```

Here `P_ww` is water-water donor incidence, `P_wc` is water-CO2 donor
incidence, and `P_w` is total water-donor incidence. The assessment records
both site fractions, all three probabilities, and the within-pair donor and
acceptor symmetry residuals for every K10/K15, pure-water, NaCl, CaCl2 and
mixed-salt state. The exact algebraic identity is checked at `1e-14`; this is
an identity check, not an experimental-accuracy tolerance.

These columns isolate the molecular-association input that the future
Maribo-Mogensen physical-permittivity state will consume. They do not calculate
that permittivity, alter Debye-Huckel or Born, introduce a CO2 dipole, change a
parameter, or qualify hydrate predictions.


### Exact-head K10/K15 result and causal boundary

Hosted run [40114797409](https://github.com/equinor/neqsim/actions/runs/40114797409)
executed the producer at PR head `1041835c41c8b9a5e2e664b9d7b65f75673e44ad`
(merge checkout `6ec42cc872630a5a94ce8522c071f11cf4d809b6`).
The 28-row CSV is in `electrolyte-assessment-evidence-0`; shard 3 did
not select this producer. Its manifest records one executed test, zero
failures/errors/skips, complete selected evidence, and the exact source/output
hashes.

The four requested Dholabhai endpoints are:

| Recipe | Pressure / bara | Measured / K | Calculated / K | Error / K | Model suppression from matched salt-free state / K |
|---|---:|---:|---:|---:|---:|
| K10 | 11.30 | 269.02 | 269.317673 | +0.297673 | 3.543717 |
| K10 | 34.85 | 277.86 | 277.775116 | -0.084884 | 3.720364 |
| K15 | 14.15 | 269.03 | 268.958338 | -0.071662 | 5.762735 |
| K15 | 35.75 | 276.01 | 275.661797 | -0.348203 | 5.998445 |

Across those pairs, adding KCl lowers the water-activity proxy by
0.04843–0.07757 and dissolved CO2 mole fraction by 0.00155–0.00467, while
total water-donor incidence increases by only 0.00106–0.00188. The incidence
split changes through a larger water-water increase partly offset by a lower
water-CO2 incidence. CO2 guest fugacity changes by only 0.032–0.540 bara.
These are matched-state model contrasts, not measured contribution
decompositions. Association incidence is therefore not the dominant scalar
change in this diagnostic, although a nonlinear permittivity model could still
amplify it.

The present sub-kelvin K10/K15 agreement is not caused by this diagnostic PR.
It first appears after merged PR
[#4367](https://github.com/equinor/neqsim/pull/4367), which introduced the
CO2/K+ `W0` default. Independent inspection of Onda et al. confirms the
source's base-10, molarity-based additive correlation and the tabulated
components `-0.2277 - 0.0362 + 0.3416 = 0.0777 L/mol` at 25 °C. It also
confirms that those values are a global least-squares gas/cation/anion
decomposition, with an overall reported standard deviation of 0.052, rather
than a unique direct measurement of a CO2/K+ short-range coefficient.

Accordingly, the executable 298.15 K target is source-consistent, but the
mapping to `W0 = -2.2981e-4 m3/mol` is model calibration. Holding the legacy
CO2/Cl− value fixed is an allocation convention, not an independently
identifiable ion-pair result. `W1 = W2 = 0` remains an unsupported
temperature extrapolation. PR #4367 received only COMMENTED reviews (one
automated style review and one author response), no accountable scientific
approval, before merge. Its strong Dholabhai KCl holdout is useful evidence but
does not close the independent provenance gate, especially with the retained
He–Morse/Yasunishi deviations and mixed-brine family/recipe biases.

### He-Morse pressure-driver and solvent-basis repair

Run [40168078608](https://github.com/equinor/neqsim/actions/runs/40168078608)
failed at `HM363-KCL3000`: the old pressure driver returned 0.342881516 bara
against a 0.304988250 bara CO2 target. Independent trials showed that its low
pressure bracket included salt-depleted gas-only states and some intermediate
two-phase states failed molecular fugacity equality. The 10 mol CO2 / 1 kg
feed-water construction also changed aqueous salt molality through evaporation.

The revised diagnostic brackets from conserved GAS+AQUEOUS trials and matches
both gas partial pressure and final aqueous solvent mass. It uses a 1 mol CO2
reservoir and compensates feed water to retain 1 kg aqueous water. An independent
half-reservoir regression at the failing hot concentrated source point checks
that this choice does not control the resulting solubility or pressure. Every
trial verifies component inventory, aqueous-ion closure and molecular fugacity
equality. The CSV records actual KCl molality and numerical residuals, preserving
completed rows if a later case fails. See the
[benchmark notes](../../src/test/resources/data/chemistry_benchmarks/co2_kcl_hydrate_dholabhai1993.md)
for the exact numerical gates. Historical runs using fixed feed water are not
like-for-like evaluations of the final aqueous source molality.

Local validation on NeqSim 3.26.0 with OpenJDK 17 executed all 26 source rows
and the independent reservoir regression (2 tests, no failures/errors/skips).
The maximum partial-pressure error was 9.91e-11 bar, the maximum aqueous KCl
molality error 4.70e-10 mol/kg water, component-inventory residual 7.11e-15 mol,
and molecular log fugacity-ratio residual 3.51e-12. The formerly failing
`HM363-KCL3000` state converged at total pressure 0.935796555 bara and CO2
partial pressure 0.304988250 bara.

These numerical checks do not establish experimental accuracy: calculated CO2
solubility remains 11.4-31.5% below the source values, and the 4.998/5.000 molal
endpoints at 323.15, 348.15 and 363.15 K still show local salting-in. Those model
deviations remain explicit evidence for the independent scientific review.

This corrects the assessment boundary conditions and failure reporting. It does
not change production flash equations, interaction parameters, source values or
experimental-accuracy criteria, and it does not establish model qualification.

## Retained numerical evidence

The Core Build, Test, and Javadoc workflow retains an
`electrolyte-assessment-evidence-<shard>` artifact from every Java 21 slow-test
shard, including after failed test runs. Download the artifacts from the same
workflow run; the eight assessments may be distributed over different shards.
Surefire reports remain in their existing, separate artifacts.

Each bundle includes a `manifest.json` and only the allowlisted assessment CSVs
produced by tests selected for that shard:

| Assessment | CSV data rows (excluding header) |
|---|---:|
| Dholabhai K10/K15 plus pure-water and cross-salt controls | 28 |
| Archer salt-only, pressure-only and CO2-addition sequence | 10 |
| He–Morse dissolved CO2, complete five-temperature series | 26 |
| Yasunishi–Yoshida Ostwald coefficients | 7 |
| Burgass saturated and finite-inventory NaCl controls | 12 |
| Carroll evaluated salt-free CO2, three setup constructions | 57 |
| Full Dholabhai family, all supported measured recipes | 110 |
| Matched measured-state aqueous/guest partition, same Dholabhai recipes | 110 |

The combined bundle has 360 diagnostic rows, not 360 independent measurements.
The two 110-row assessments deliberately use the same 110 measured endpoints.

The manifest records the actual checkout commit separately from the GitHub
event SHA and (when present) PR head SHA, run/attempt/shard context, selected
test classes, producer Surefire counts and JVM properties, and SHA-256 hashes of outputs, producer source,
reference fixture and this provenance page. Archer reference values are inline
in its producer test, rather than in a separate fixture. Retrieve the referenced
sources at the recorded checkout commit to inspect the source/access basis.

CSV bytes, headers, units, residuals and intentional `NaN` fields for
non-measured control states are retained unchanged. Row counts describe the
existing diagnostic matrix; they introduce no accuracy criterion. A selected
producer that passed but did not generate its CSV fails evidence packaging.
Malformed or truncated output is retained with an explicit packaging error.
Missing, skipped, failed and not-yet-completed producer tests cannot establish
complete evidence. Outputs from unselected tests and unrelated files under
`target/` are not collected.

`selected_evidence_complete` applies only to the selected assessments in that
shard, not the full eight-assessment matrix or the rest of CI. A shard with no selected
assessment has a count of zero. Check every producer across all shards and the
original test results before using a combined matrix. Every manifest keeps
`qualification` as `not_assessed`: artifact retention and structural validation
do not supply independent thermodynamic review, a causal contribution split,
agreement within experimental uncertainty, or engineering approval. Artifacts
remain subject to the repository's GitHub Actions retention policy.

## Qualification boundary

The correction also affects CO2 with potassium and counterions other than
chloride; those systems are not qualified by the KCl calibration. Temperature
dependence, high ionic strength, mixed solvents, mixed salts, and hydrate-model
accuracy require independent evidence. Pure-water CO2 bias is not fitted or
compensated here.

## Primary sources

- Solbraa (2002), [*Equilibrium and Non-Equilibrium Thermodynamics of Natural Gas Processing*](https://ntnuopen.ntnu.no/ntnu-xmlui/handle/11250/231326),
  Norwegian University of Science and Technology, Appendix C, equation (C.10).
- Onda et al. (1970), [DOI 10.1252/jcej.3.18](https://doi.org/10.1252/jcej.3.18), Table 3.
- He and Morse (1993), [DOI 10.1016/0016-7037(93)90137-L](https://doi.org/10.1016/0016-7037(93)90137-L),
  [NIST data reproduction](https://srdata.nist.gov/solubility/sol_detail.aspx?sysID=62_229).
- Yasunishi and Yoshida (1979), *J. Chem. Eng. Data* 24, 11–14,
  [NIST data reproduction](https://srdata.nist.gov/solubility/sol_detail.aspx?sysID=62_227).
- Dholabhai et al. (1993), [DOI 10.1021/je00012a045](https://doi.org/10.1021/je00012a045).
- Coelho, Franco and Firoozabadi (2025), *Industrial & Engineering Chemistry Research* 64, 8492–8505,
  [DOI 10.1021/acs.iecr.5c00134](https://doi.org/10.1021/acs.iecr.5c00134), equation 22.
