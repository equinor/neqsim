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

Hosted [run 40226079125](https://github.com/equinor/neqsim/actions/runs/40226079125)
retains all 57 rows in `electrolyte-assessment-evidence-1`. Its manifest records
PR head `57ccb4131352cd32b3fd4f44620bfdcdf46d663a`, merge checkout
`be6c8130e01ec69a3252e7d97d67db61c35d45a5`, one executed producer test,
no failure/error/skip, complete selected evidence, and `qualification:
not_assessed`. All rows are GAS+AQUEOUS and pass the frozen numerical gates.
Maximum partial-pressure mismatch is 6.26e-11 bara; molecular log-fugacity
residual 1.85e-9; component-balance residual 3.56e-16; repeat mole-fraction
difference 1.90e-19; and construction spread in calculated CO2 mole fraction
4.23e-18.

The calculated salt-free CO2 mole fraction is below the evaluated reference at
all nineteen temperatures. Signed residuals range from -27.30% at 273.15 K to
-14.59% at 343.15 K; MAPE and signed mean magnitude are 17.99%. The residual
improves through the mid-temperature region, then reaches -16.15% at 363.15 K.
At the matched 323.15, 348.15 and 363.15 K He-Morse temperatures, salt-free
residuals are -15.13%, -15.01% and -16.15%; the corresponding mean residuals
across each KCl block are -16.51%, -16.39% and -18.07%. Thus the dominant
absolute deficit is already present in the salt-free CO2/water baseline. KCl
still controls the separately retained salting slope and high-molality
curvature, including local salting-in; hydrate compensation remains an
application-level possibility.

This comparison is diagnostic rather than a direct residual subtraction:
Carroll reports liquid mole fraction at 1.01325 bara CO2 partial pressure,
whereas He-Morse reports molality at its source partial pressure. It therefore
does not uniquely identify a neutral binary or association parameter. The
salt-free guest fugacity is 1.00985, 1.01353 and 1.01787 bara at the three hot
matched temperatures, respectively; its modest variation does not by itself
separate gas-fugacity from aqueous-model contributions.

Runtime diagnostics identify rule `ClassicSRKT2`, CO2 2A/two sites and water
4C/four sites, cross beta 0.075 and cross energy 8327.5 J/mol. Stored zero cross energy selects the combining rule; it does not
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

### Burgass-Tohidi KCl and mixed-brine observable diagnostic

Tohidi's 1995 Heriot-Watt thesis preserves twenty experimental CO2-brine states
attributed to Burgass (1995). Table 6.4 contains sixteen KCl states: twelve at
5-20 mass percent KCl and 298.1-348.4 K plus four at 7/14 mass percent KCl and
266.5/273.2 K. Table 6.5 contains four NaCl/KCl/CaCl2 states and is explicitly
described as excluded from parameter optimization, making those states
source-defined mixed-salt holdouts. Together they span 0.855-5.130 MPa. They
extend the dissolved-CO2 diagnostic but do not cover the exact Dholabhai
K10/K15 hydrate recipes or establish hydrate-model accuracy.

The accessible table labels the dissolved quantity only as “mole %”; it does
not state whether the denominator includes dissociated ions, salt formula
units, or excludes electrolyte. The assessment therefore retains the source
value and reports three non-equivalent model mappings plus CO2 molality. It does
not choose a preferred mapping, fit a parameter, or impose an accuracy
tolerance. The calculation uses reported total pressure, one final aqueous
kilogram of water, a verified finite gas reservoir, mixing rule 10, and records
water activity, CO2 partial pressure, inventory and fugacity residuals. See the
[benchmark notes](../../src/test/resources/data/chemistry_benchmarks/co2_brine_solubility_burgass_tohidi1995.md)
for the exact source and observable boundary.

This corrects the assessment boundary conditions and failure reporting. It does
not change production flash equations, interaction parameters, source values or
experimental-accuracy criteria, and it does not establish model qualification.

Local validation on OpenJDK 17 executed all twenty source rows plus the gas-
reservoir regression (2 tests, no failures/errors/skips). All sixteen Table 6.4
KCl states are underpredicted for every retained mole-percent mapping: residual
ranges are -0.312 to -0.153 percentage points including ions, -0.232 to -0.084
for the molecular-only mapping, and -0.270 to -0.120 for the pseudo-salt
mapping. The four Table 6.5 holdouts bracket zero and have mean absolute
residuals of 0.110, 0.135 and 0.124 percentage points, respectively. Water
activity spans 0.8758-0.9721; component-balance and molecular log-fugacity
residuals remain below 1.43e-14 mol and 6.64e-12. These are diagnostic results,
not an accuracy gate, because the source mole-percent denominator remains
unresolved.

## Retained numerical evidence

The Core Build, Test, and Javadoc workflow retains an
`electrolyte-assessment-evidence-<shard>` artifact from every Java 21 slow-test
shard, including after failed test runs. Download the artifacts from the same
workflow run; the fourteen assessments may be distributed over different shards.
Surefire reports remain in their existing, separate artifacts.

Each bundle includes a `manifest.json` and only the allowlisted assessment CSVs
produced by tests selected for that shard:

| Assessment | CSV data rows (excluding header) |
|---|---:|
| Dholabhai K10/K15 plus pure-water and cross-salt controls | 28 |
| Archer salt-only, pressure-only and CO2-addition sequence | 10 |
| He–Morse dissolved CO2, complete five-temperature series | 26 |
| Burgass-Tohidi KCl and mixed-brine CO2 observable mappings | 20 |
| Mousavi-Chapoy-Burgass KCl salt-free dissolved CO2 | 26 |
| Mousavi-Chapoy-Burgass four-salt Table A24 holdout | 18 |
| Mousavi-Chapoy-Burgass dense pure-water and NaCl series | 34 |
| Liu independent low-salinity pure-water and NaCl series | 48 |
| Liu independent low-salinity pure-water, CaCl2 and MgCl2 series | 84 |
| Yasunishi–Yoshida Ostwald coefficients | 7 |
| Burgass saturated and finite-inventory NaCl controls | 12 |
| Carroll evaluated salt-free CO2, three setup constructions | 57 |
| Full Dholabhai family, all supported measured recipes | 110 |
| Matched measured-state aqueous/guest partition, same Dholabhai recipes | 110 |

The combined bundle has 590 diagnostic rows, not 590 independent measurements.
The two Liu assessments deliberately reuse the same twelve distilled-water source states.
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
shard, not the full fourteen-assessment matrix or the rest of CI. A shard with no selected
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
- Tohidi Kalorazi (1995), *Gas Hydrate Equilibria in the Presence of Electrolyte
  Solutions*, Heriot-Watt University, Table 6.4; experimental values attributed
  there to Burgass (1995), *Hydrate Phase Equilibria in the Presence of
  Electrolyte Solutions*, MSc thesis, Heriot-Watt University.
- Yasunishi and Yoshida (1979), *J. Chem. Eng. Data* 24, 11–14,
  [NIST data reproduction](https://srdata.nist.gov/solubility/sol_detail.aspx?sysID=62_227).
- Dholabhai et al. (1993), [DOI 10.1021/je00012a045](https://doi.org/10.1021/je00012a045).
- Coelho, Franco and Firoozabadi (2025), *Industrial & Engineering Chemistry Research* 64, 8492–8505,
  [DOI 10.1021/acs.iecr.5c00134](https://doi.org/10.1021/acs.iecr.5c00134), equation 22.
- Mousavi, Chapoy and Burgass (2024), *Greenhouse Gases: Science and Technology* 14, 791-828,
  [DOI 10.1002/ghg.2298](https://doi.org/10.1002/ghg.2298), Tables 5 and A11-A13/A24, CC BY 4.0.
- Liu et al. (2021), *Energies* 14, 7222,
  [DOI 10.3390/en14217222](https://doi.org/10.3390/en14217222), supplementary CSV, CC BY 4.0.
- Mousavi Belfeh Teymouri (2017), *Phase equilibria measurements and modelling of CO2-rich fluids/brine systems*,
  Heriot-Watt University, [handle 10399/3409](http://hdl.handle.net/10399/3409), Chapter 3 section 3.2.1.

## Explicit salt-free KCl solubility holdout (Mousavi-Chapoy-Burgass 2024)

The later Heriot-Watt static-analytical study by Mousavi, Chapoy and Burgass
provides an independent observable boundary that the 1995 source does not. It
defines CO2 solubility as mole fraction in the salt-free aqueous phase and
reports displayed uncertainties. Tables A11-A13 contain 26 KCl measurements at
10, 15 and 22 mass percent, 323.15-423.15 K and 82.07-548.28 bar.

`KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest` retains those
source values under the article's CC BY license and compares the explicit
salt-free observable with NeqSim's molecular water-plus-CO2 mole fraction. It
also records water activity, CO2 molality, ion-inclusive and pseudo-salt
representation controls, component closure, fugacity closure and
finite-reservoir independence. No source point is used to fit a parameter or
select a tolerance.

This high-temperature holdout isolates the dissolved-CO2/water-activity path
from hydrate guest occupancy. It does not replace the Dholabhai K10/K15 hydrate
matrix or establish how the 1995 Burgass MSc converted its measurements. See
the [source and reproduction notes](../../src/test/resources/data/chemistry_benchmarks/co2_kcl_solubility_mousavi_chapoy_burgass2024.md).

## Dense-CO2 root-selection discriminator

The 26-state Mousavi–Chapoy–Burgass assessment evaluates every conserved feed twice without changing model parameters.
The default `TPflash` result remains the reported production-path diagnostic. A parallel
`CO2BrinePhaseEquilibrium` calculation evaluates explicit GAS and OIL EOS roots, confines ions to the aqueous phase,
selects the lower qualified Gibbs-energy split, and requires the existing `1e-8` molecular log-fugacity gate.

The paired columns separate numerical phase/root selection from aqueous-model response. Closure of the constrained
result does not establish experimental accuracy: dissolved CO2, water activity, source residuals and the Dholabhai
hydrate contribution remain independent scientific checks. The assessment does not route ordinary user flashes through
the constrained solver, alter hydrate calculations, fit an interaction parameter, relax a tolerance or apply a
temperature offset.

## Matched pure-water CO2 fugacity decomposition

The same 26-state Mousavi-Chapoy-Burgass producer now also calculates a
model-only no-salt comparator at each source temperature and total pressure.
The comparator retains the same final aqueous water basis and finite CO2
reservoir as the corresponding brine state. It is not an additional measurement
and does not replace the salt-free Carroll partial-pressure benchmark.

For the brine and matched pure-water states, let $x$ be the molecular
water-plus-CO2 aqueous mole fraction, $f$ the CO2 fugacity in the most
CO2-rich phase in bara, and $H_{\mathrm{eff}}=f/x$. The report retains the
exact identity

$
\ln\left(\frac{x_{\mathrm{brine}}}{x_{\mathrm{water}}}\right)
=
\ln\left(\frac{f_{\mathrm{brine}}}{f_{\mathrm{water}}}\right)
-
\ln\left(\frac{H_{\mathrm{eff,brine}}}{H_{\mathrm{eff,water}}}\right).
$

This separates the modeled change in CO2-rich-phase fugacity exposure from the
effective aqueous salting response at the same total pressure. Because these
are finite-composition dense-CO2 states, $H_{\mathrm{eff}}$ is an endpoint
diagnostic with units of bara, not a dilute-limit Henry constant. Neither term
is independently measured or a unique parameter attribution.

Every matched state must retain one aqueous kilogram of water, component
balance at or below $10^{-10}$ mol, molecular log-fugacity residual below
$10^{-8}$, and log-identity residual below $10^{-12}$. The CSV also retains
both water activities and their difference. These are numerical-consistency
gates, not experimental-accuracy tolerances.

The source values remain the CC BY Mousavi, Chapoy and Burgass (2024) Tables
A11-A13 data already pinned by the fixture and reproduction notes. This
decomposition changes no production equation, interaction parameter, source
value, hydrate equation, phase-equilibrium tolerance, or empirical temperature
offset. It remains diagnostic evidence only; physical qualification still
requires independent review and supporting mixed-brine activity/solubility
evidence.

## Four-salt mixed-brine dissolved-CO2 holdout

Mousavi, Chapoy and Burgass (2024) Table A24 supplies 18 CC BY measurements for
one NaCl/KCl/CaCl2/MgCl2 brine at 323.15, 373.15 and 423.15 K and 100.21-571.03
bar. The source reports salt-free aqueous CO2 mole fraction with displayed
uncertainty `0.0001`. These points are independent held-out evidence for the
dissolved-CO2 and water-activity leg; they are not hydrate endpoints.

The article's Table 5 gram recipe is interpreted with the calcium chloride
dihydrate and magnesium chloride hexahydrate documented in the associated
Heriot-Watt experimental thesis. The crystal water is added to the solvent
inventory before the explicit formula-unit inventories are normalized by final
water. This gives 4.303472167068 m NaCl, 0.045481557865 m KCl,
0.265697223919 m CaCl2 and 0.148809209564 m MgCl2, with chloride and positive
charge both 5.177966591898 m and ionic strength 5.592473025381 m. Treating the
reported one-decimal anhydrous mass fractions as exact remains a separate
composition-sensitivity control; it is not the primary preparation basis.

`MixedBrineCO2SolubilityMousaviChapoyBurgass2024AssessmentTest` reuses the
explicit-ion Electrolyte-CPA/mixing-rule-10 path. It retains default and
constrained root calculations, matched pure-water water activity, dissolved
molecular CO2, CO2-rich-phase fugacity, effective aqueous `f/x`, component and
charge closure, ion confinement and the exact log-space contribution identity.
Experimental residuals are reported without fitting or converting the source
uncertainty into an acceptance tolerance. The assessment changes no production
equation, parameter, hydrate term, solver tolerance, empirical offset or
accuracy qualification. See the
[source and reproduction notes](../../src/test/resources/data/chemistry_benchmarks/co2_mixed_brine_solubility_mousavi_chapoy_burgass2024_table_a24.md).

On the pinned benchmark, the default molecular-CO2 AAD is 161.9804% at
323.15 K, 138.1925% at 373.15 K and 106.8022% at 423.15 K, with positive
model-minus-source residuals throughout. Constraining the CO2-rich root closes
the molecular fugacity residual below `1.04e-11`, yet gives AADs of 185.0128%,
138.1925% and 106.8022%, respectively. The three dense-CO2 323.15 K points
therefore contain a separate root-selection contribution, but root closure does
not remove the physical solubility discrepancy.

The constrained matched-pure-water decomposition is also directionally
diagnostic. Mean log dissolved-CO2 brine/pure-water ratios are +0.1362, +0.1899
and +0.1361 across the three temperatures. The corresponding mean log
guest-fugacity ratios are only +0.0015, +0.0041 and +0.0106, while mean log
effective-`f/x` ratios are -0.1347, -0.1858 and -0.1255. Thus the modeled
four-salt aqueous phase salts CO2 in relative to matched water even though its
water-activity proxy is depressed. This localizes the dominant discrepancy to
the high-ionic-strength aqueous CO2/electrolyte response, not the hydrate
occupancy model or guest fugacity. The rule-10 CPA binary table supplies
`cpakij_SRK=1` for K+/CO2 but zero for Na+/CO2, Cl-/CO2, Ca++/CO2 and Mg++/CO2;
the benchmark does not establish replacements for those parameters or a mixed-
salt term. This CPA binary-interaction layer is not the only active ion response:
mixing rule 10 also applies short-range Furst `Wij` terms. Under untouched shared
defaults, CO2 with Na+, Ca++, Mg++ and Cl- uses the legacy NaCl-oriented
`W0=1.27e-4 m3/mol`, while CO2/K+ uses the Onda-backed
`W0=-2.2981e-4 m3/mol` with `W1=W2=0`. Explicit pair or global overrides retain
priority. A zero CPA BIP therefore does not imply zero CO2-ion response.

Electroneutral chloride data identify only salt-level combinations. For NaCl,
KCl, CaCl2 and MgCl2 the observable linear terms are respectively
`W_Na+W_Cl`, `W_K+W_Cl`, `W_Ca+2W_Cl` and `W_Mg+2W_Cl`; their design matrix has
rank four for five ion coefficients, with gauge direction `(1,1,2,2,-1)`.
Table A24 is charge balanced and is invariant along the same direction, so it
cannot justify an absolute allocation to individual ion terms. Independent
parameter provenance and a declared gauge convention are required before a
production model change can be scientifically accepted.

## Dense pure-water and NaCl follow-through

Mousavi, Chapoy and Burgass (2024) Tables A1 and A8-A10 add eight dense
pure-water and 26 NaCl measurements on the same salt-free mole-fraction basis
as the KCl and mixed-brine series. The no-fit
`WaterNaClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest` reports current
Electrolyte-CPA residuals, default/constrained roots, matched pure-water water
activity, guest fugacity and effective aqueous `f/x`. It does not recalibrate
the Onda KCl coefficient or use the already inspected mixed-brine/hydrate
holdouts for selection. See the
[source notes](../../src/test/resources/data/chemistry_benchmarks/co2_water_nacl_solubility_mousavi_chapoy_burgass2024.md).

On the pinned benchmark, the constrained pure-water AAD is 5.6652% (eight A1
rows), with a -2.7607% signed bias. The corresponding NaCl AADs are 21.9058%
at 10 mass%, 34.1426% at 15 mass% and 68.1650% at 22 mass%; every NaCl
residual is positive. Thus the salt-free dense-CO2 reference is comparatively
well reproduced, while the aqueous NaCl response increasingly underpredicts
salting out as concentration rises. The modeled mean log dissolved-CO2 ratios
relative to matched pure water are -0.1248, -0.1676 and -0.1657 over the three
concentrations. Mean guest-fugacity contributions are only +0.0015, +0.0022
and +0.0060, whereas mean effective-`f/x` contributions are +0.1263, +0.1698
and +0.1717. The discrepancy is therefore an aqueous activity/fugacity
response limitation rather than a pure-water or CO2-rich guest-fugacity error.

The default solver reproduces 33 of the 34 states. At the 22 mass%, 323.15 K,
57.241 MPa point it does not retain both an aqueous and a non-aqueous phase;
the constrained root remains closed and is reported explicitly rather than
relaxing a phase or balance check. No production coefficient is fitted here.
The dense NaCl source identifies a NaCl-level response, but cannot allocate it
uniquely between Na+ and Cl-. A production update still requires an independent
NaCl source family for a frozen holdout and an explicit allocation convention
or salt-pair formulation; independent CaCl2/MgCl2 VLE and mixed-ion water
activity are also needed before the Table A24 four-salt response can be
attributed without confounding ion and mixing terms.

These numerical summaries are from the pinned JDK 17 slow-test runtime. A
separate forced slow run on Temurin 8 compiled the complete production and test
source graph, then rejected the default 323.15 K/571.03 bar state at the
unchanged component-inventory gate with a 1.271804 mol residual. The Java 8
product matrix excludes slow assessments, while the required slow matrix runs
on the modern JDK. No value from the rejected Java 8 state contributes to the
AAD or decomposition summaries above; the result is retained as a solver-
portability boundary rather than hidden by a relaxed tolerance.


## Independent low-salinity NaCl discriminator

Liu et al. (2021) provide an independent potentiometric-titration source family for distilled water and NaCl brines at
298-373 K and pressures through 20 MPa. The article and supplement are CC BY 4.0. The article reports 553 total points,
while the supplied CSV contains 554 data rows; that source-record discrepancy is retained explicitly. The present
fixture uses only distilled-water and NaCl blocks, so the duplicated CaCl2 portion cannot affect it.

`LowSalinityNaClCO2SolubilityLiu2021AssessmentTest` fixes a 48-row subset before model execution: four temperatures,
distilled water plus 1000/10000/15000 mass ppm NaCl, and the second, sixth and ninth common pressure positions
(20.4/102.0/170.1 atm). It excludes the near-atmospheric points, where the source reports its largest relative method
error, and the uneven 200 atm endpoint. Source errors of 0.9-7.8% are study-level method errors, not pointwise accuracy
tolerances.

The source observable is mol CO2 per kilogram of titrated solution. The model mapping includes aqueous water, NaCl and
dissolved CO2 mass in that denominator. Each salt state is paired with the independently measured and modeled pure-water
state at the same temperature and pressure. The report retains source/model apparent salt effects, water activity,
CO2-rich-phase fugacity, the exact dissolved-CO2 contribution identity, component closure and constrained-root checks at
the four 15000 ppm/170.1 atm endpoints.

These low-salinity states test the first-order NaCl response independently of the dense Mousavi-Chapoy-Burgass source.
They do not identify high-concentration curvature. Electroneutral NaCl data identify only the salt-level combinations
`Wk_Na + Wk_Cl` for each temperature-polynomial coefficient; they cannot allocate individual Na+ and Cl- terms. They also
cannot separate CPA binary interactions from Furst short-range `Wij` terms without layer-isolation controls. No
coefficient is fitted or selected here. Table A24 and all hydrate measurements remain frozen holdouts. See the
[source and selection notes](../../src/test/resources/data/chemistry_benchmarks/co2_water_nacl_solubility_liu2021.md).

## Divalent single-salt identifiability screen

The independent Liu et al. (2021) supplement also contains low-salinity CaCl2 and MgCl2 blocks on the same observable
and temperature-pressure grid as the NaCl assessment. The predeclared 84-row subset retains twelve distilled-water
references plus 36 states for each divalent salt at 0.1, 1.0 and 1.5 mass%. The source's thirty duplicated CaCl2 rows at
373 K are deduplicated by exact state and value. No reported solubility is averaged or corrected. The mixed
`mgcl2+cacl2` block is excluded because the primary article and supplement do not state the CaCl2/MgCl2 split; an
electroneutral model state cannot be constructed without inventing that composition.

The article also gives 0.99 reagent purity without catalog, lot or hydration state. The executable state mapping
therefore declares an anhydrous CaCl2/MgCl2 formula-mass convention; it is not source verification of divalent ion
inventory. Hydrated reagent forms would change the inferred molality materially. This blocks quantitative parameter
fitting or single-ion attribution until primary clarification is obtained.

Together with NaCl, the single-salt response equations identify only `Wk_Na + Wk_Cl`,
`Wk_Ca + 2 Wk_Cl` and `Wk_Mg + 2 Wk_Cl` for each temperature-polynomial order `k`. Their design matrix has rank
three for four ion coefficients, leaving one gauge degree of freedom. These data therefore cannot allocate an absolute
chloride contribution or distinguish CPA binary interactions from Furst short-range terms. A production update requires
a declared gauge or salt-pair formulation, layer-isolation calculations and a reproducible bounded fit.

The assessment reports all retained source/model values, matched pure-water responses, apparent salt effects, water
activity, guest fugacity, decomposition closure and constrained-root spot checks. It deliberately preserves finite
source records that contradict the aggregate salting-out trend: seven salt rows are salting-in or equal, four of 24
salinity sequences and three of 24 pressure sequences fail the expected monotonic direction. They cannot enter a fit
unless an objective source-only quality rule is declared before model selection. The Table A.24 mixed-brine series and independent hydrate families
remain frozen holdouts. This increment changes no production parameter, equation, tolerance or temperature offset. See
the [source notes](../../src/test/resources/data/chemistry_benchmarks/co2_water_cacl2_mgcl2_solubility_liu2021.md).



## Source-separated complete-block identifiability scan

`CO2ElectrolyteFurstScaleIdentifiabilityAssessmentTest` freezes three roles before
model execution:

- eight Liu et al. (2021) 1.0 and 1.5 mass percent NaCl states at 298 and 373 K
  and 102.0 and 170.1 atm are the low-salinity calibration discriminator;
- nine Mousavi-Chapoy-Burgass NaCl states span 10, 15 and 22 mass percent and
  323.15, 373.15 and 423.15 K as an independent dense-concentration validation
  family;
- nine Mousavi-Chapoy-Burgass KCl states cover the same concentration and
  temperature grid as a salt-identity validation family.

The scan evaluates only the already defined complete CO2-ion Furst block at
`lambda = 0, 0.5, 1`:

$$
W_{\mathrm{CO_2},i}^{(k)}(T;\lambda)
 = \lambda W_{\mathrm{CO_2},i}^{(k)}(T),\qquad
k\in\{0,1,2\}.
$$

All active CO2-ion pairs are scaled together. This preserves the declared
single-ion gauge and leaves the cubic EOS, association, CPA binary
interactions, non-CO2 electrolyte terms, phase-equilibrium tolerances and
source values unchanged. The constrained GAS/OIL root comparison is used at
every scan state so a root-selection failure cannot masquerade as parameter
sensitivity. Each row retains component and molecular-fugacity closure, water
activity, dissolved CO2, guest-side fugacity, effective aqueous
`f/x`, and the exact log-space decomposition.

The three values are an ablation/midpoint/baseline sensitivity matrix, not a
bounded fit and not candidate production parameters. In particular, the scan
cannot identify individual-ion allocations, distinguish Furst terms from CPA
binary interactions, or qualify temperature dependence. The Table A.24
four-salt series, all Dholabhai hydrate families, Archer water activity,
He-Morse, Yasunishi-Yoshida and Burgass-Tohidi evidence remain untouched
holdouts. A production update requires a source-separated improvement on the
calibration discriminator, both single-salt validation families and those
holdouts, with parameter uncertainty, physical consistency, convergence,
non-regression and timing checks.

## KCl CPA-versus-Furst layer-isolation matrix

`CO2KClInteractionLayerIsolationAssessmentTest` uses nine predeclared dense
Mousavi-Chapoy-Burgass KCl states only as a no-fit layer discriminator. It
evaluates the four endpoints formed by the complete CO2-ion Furst block and the
complete K+/CO2 cubic CPA binary-interaction layer being independently enabled
or ablated. No experimental value is added or changed; the source remains the
CC BY 4.0 fixture described above.

The Furst endpoint retains the existing complete-block gauge convention:

$$
W_{\mathrm{CO_2},i}^{(k)}(T;\lambda_F)
=\lambda_F W_{\mathrm{CO_2},i}^{(k)}(T),\qquad
\lambda_F\in\{0,1\},\quad k\in\{0,1,2\}.
$$

Rule 10 supports composition-dependent CPA mixing. Its K+/CO2 database row has
a base term of 1, a zero temperature term, and both directional composition
terms set to 1. Therefore the CPA endpoint removes the symmetric base,
temperature, and both directional composition matrices together when
$\lambda_C=0$ and leaves the initialized database layer untouched when
$\lambda_C=1$. Removing only the symmetric base coefficient would not be a
valid complete-row ablation.

For the log dissolved-CO2 response
$y_{FC}=\ln(x_{\mathrm{CO_2},FC}/x_{\mathrm{CO_2},\mathrm{water}})$,
the retained non-additive layer coupling is

$$
\delta_y=y_{11}-y_{10}-y_{01}+y_{00}.
$$

The same factorial identity is reported for effective aqueous $f/x$. Each of
the 36 endpoint rows also retains source residual, matched pure water, water
activity, guest-side CO2 fugacity, component balance, molecular-fugacity
closure, decomposition identity and phase topology. The cubic EOS, association,
non-CO2 electrolyte terms, source values, solver tolerances and constrained
GAS/OIL root comparison remain unchanged. No hydrate calculation enters this
matrix.

On the pinned nine-state grid, the legacy K+/CO2 CPA row is numerically
inactive: the maximum absolute CPA endpoint effect on log dissolved CO2 is
`3.95e-15`, and the maximum absolute Furst-by-CPA coupling is `4.00e-15`.
This is explained by the current cubic model convention, not by fitting or a
looser tolerance. Ionic components set the cubic attraction parameter to
$a_{K^+}=10^{-35}$, while the cubic cross attraction is proportional to
$\sqrt{a_{\mathrm{CO_2}}a_{K^+}}(1-k_{ij})$. Changing the initialized K+/CO2
row therefore perturbs a deliberately negligible cubic cross term. The
existing row cannot explain the KCl discrepancy and is not an identifiable
parameter-update direction for these states.

The complete Furst block is the active aqueous layer. With it enabled, the
dense-KCl MARE is `8.861%` with `-7.979%` signed bias; disabling it raises both
MARE and positive bias to `100.041%`. Its per-state effect on log dissolved CO2
ranges from `-0.4362` to `-1.0887`. The corresponding guest-fugacity effect is
only `1.57e-6` to `3.28e-4` in log space, while the effective aqueous `f/x`
effect is `+0.4363` to `+1.0888`. The water-activity log effect is much smaller,
`-0.00016` to `-0.00865`. Thus the layer response is overwhelmingly the
aqueous CO2 activity/fugacity term rather than guest fugacity or water
activity. No hydrate model is executed or changed in this matrix; the earlier
independent hydrate comparisons remain frozen transfer holdouts.

These endpoints are ablations, not candidate parameters. The current K+/CO2
CPA values are legacy database values without independent parameter provenance,
and one dense KCl family cannot qualify replacements. Liu low-salinity NaCl and
dense NaCl remain the independent concentration-family constraints. Table A.24,
all Dholabhai and Burgass hydrate families, CaCl2/MgCl2, Archer water activity,
He-Morse, Yasunishi-Yoshida and Burgass-Tohidi evidence remain untouched
holdouts or transfer controls. A production update still requires a declared
gauge or salt-pair formulation, continuous bounded sensitivity or fitting,
independent KCl evidence, uncertainty or covariance, holdout transfer,
convergence, non-regression and timing evidence.

## Chloride salt-pair curvature and gauge diagnostic

`CO2ChlorideSaltPairCurvatureAssessmentTest` follows the merged layer-isolation
result with an equation-level, no-production-change diagnostic. The current
electrolyte-CPA Furst term is pairwise and has no explicit
neutral-cation-anion product. NeqSim's separate Pitzer implementation provides
the physically established candidate topology. For a 1:1 chloride salt MCl,
its neutral-CO2 contribution is

$$
\ln\gamma_{\mathrm{CO_2}}
=2m\lambda_{\mathrm{CO_2},M^+}
 +2m\lambda_{\mathrm{CO_2},\mathrm{Cl^-}}
 +m^2\zeta_{\mathrm{CO_2},M^+,\mathrm{Cl^-}}.
$$

This equation is used only as an identifiability basis. It is not active in
`SystemElectrolyteCPAstatoil`, and the diagnostic does not transplant a Pitzer
coefficient into CPA. Such a change would require a consistent Helmholtz-energy
term, every composition and state derivative, a standard-state mapping and a
double-counting assessment against the existing Furst response.

The He-Morse source-space discriminator fits its reported CO2 activity
coefficients independently at each temperature to the zero-intercept bases
$am$ and $am+bm^2$. The two-term basis reduces log-space RMSE at all five
temperatures, and its quadratic coefficient is negative throughout:

| Temperature / K | Linear RMSE | Linear-plus-quadratic RMSE | $b$ on molality basis |
|---:|---:|---:|---:|
| 273.15 | 0.042721 | 0.017427 | -0.026692 |
| 298.15 | 0.045219 | 0.030092 | -0.015345 |
| 323.15 | 0.020356 | 0.010882 | -0.005499 |
| 348.15 | 0.022117 | 0.008150 | -0.006010 |
| 363.15 | 0.006226 | 0.004053 | -0.001381 |

The independent Yasunishi-Yoshida 298.15 K Ostwald series shows the same
diminishing-salting-out curvature: the log-space RMSE falls from 0.018197 to
0.002824 and the quadratic coefficient is -0.008224. Its concentration
coordinate is mol/L of solution, however, whereas He-Morse uses mol/kg water.
The values are therefore separate shape evidence, not transferable estimates
of one production parameter. The unreviewed Yasunishi-Yoshida 308.15 K block
remains excluded.

The executable composition Jacobian combines only retained He-Morse KCl
molalities and the independent Liu NaCl composition coordinates. Five
individual-ion columns—Na, K and chloride lambda plus NaCl and KCl zeta—have
rank four. The exact null direction is `[1, 1, -1, 0, 0]`: electroneutral
single-salt data cannot choose an absolute allocation between cation and
chloride lambda terms. Reparameterizing to NaCl and KCl salt-level linear and
quadratic combinations gives full rank four. This proves structural
identifiability of salt-pair curvature while preserving the single-ion gauge;
it does not fit a response or select a gauge.

He-Morse is used only as a source-shape discriminator, Yasunishi-Yoshida as an
independent source-shape validation and Liu NaCl only for the composition-rank
audit. Dense Mousavi-Chapoy-Burgass NaCl/KCl results remain adjacent model
controls. Table A.24 mixed brine, all Dholabhai and Burgass hydrate data,
CaCl2/MgCl2, Archer water activity and Burgass-Tohidi remain frozen transfer
holdouts and are not inputs to this diagnostic.

The result supports a salt-pair curvature direction but not a production
change. Before a bounded fit, the primary He-Morse activity-coefficient
standard state and covariance, defensible parameter bounds, the dense-source
pure-water pressure mapping and the complete electrolyte-CPA free-energy
formulation must be frozen. A candidate must then report calibration,
independent source holdouts, parameter covariance/sensitivity, Table A.24 and
hydrate transfer, convergence, non-regression and timing. No empirical
temperature offset, fitted coefficient, relaxed tolerance or hydrate
qualification is introduced here.

## Salt-pair Helmholtz composition kernel

`CO2SaltPairHelmholtzCompositionAssessmentTest` advances the equation-level
candidate without activating a production term or selecting a parameter. The
topology follows the neutral-cation-anion Pitzer `zeta` family already
implemented separately in `PhasePitzer`. The primary Pitzer equation lineage
is documented in the
[Pitzer parameter provenance](pitzer_parameter_provenance.md), including
Pitzer (1973), DOI `10.1021/j100621a026`, and the audited public-domain PHREEQC
implementation. No coefficient is copied from those sources.

For neutral CO2, one cation, one anion and water solvent, the proposed
unit-response composition kernel is

$$
B=\frac{n_{\mathrm{CO_2}}n_+n_-}
        {(M_w n_w)^2},
\qquad
\frac{A^E_{\mathrm{pair}}}{RT}=\zeta(T,P)B.
$$

Here $M_w n_w$ is the water mass in kg and a compatible coefficient
$\zeta$ has units kg$^2$/mol$^2$. The expression is first-degree homogeneous
in all amounts, vanishes when any tuple member is absent, and gives a
quadratic neutral-CO2 chemical-potential response when a 1:1 salt amount is
scaled. Its analytic first and second composition derivatives are retained in
the executable assessment. Centered finite differences, Hessian symmetry,
Euler's identity and the degree-one Hessian homogeneity identity are all
gated without using an experimental point or a fitted value.

On the normalized algebraic state, the maximum absolute first-derivative
residual is `1.03e-11` and the maximum second-derivative residual is
`6.99e-12`. Hessian symmetry and the fourfold neutral chemical-potential
response on doubling both 1:1 salt ions are exact at displayed precision;
Euler and Hessian-homogeneity residuals are at most `1.39e-17`. These are
equation-consistency results, not experimental accuracy statistics.

This kernel addresses only the composition algebra. It does not establish the
required $\zeta(T,P)$ law, standard-state conversion, volume derivative or
partial-molar-volume contribution. The current Furst block also produces
nonlinear concentration response through its denominator and packing terms;
therefore the candidate must be calibrated jointly or with a declared
orthogonalization/ablation, not added on top and called independent. The
He-Morse covariance remains unidentifiable from its five-temperature saturated
basis, and Plummer-Busenberg identifies the adopted pure-water anchor rather
than an independent raw family. Production activation remains blocked until
an independently compatible temperature/pressure family or a predeclared
lower-dimensional physical law supplies bounds and uncertainty, followed by
single-salt calibration and the frozen Table A.24 and hydrate holdouts.

The retained CSV contains seven algebraic checks only. The unit coefficient is
a normalization of the kernel, not an invented physical parameter. No
Electrolyte-CPA result, database row, tolerance, hydrate equation or default is
changed.

## He-Morse standard-state and temperature-covariance audit

`CO2HeMorseStandardStateAuditTest` addresses the first remaining source
dependency without changing the model. He and Morse (1993), DOI
`10.1016/0016-7037(93)90137-L`, report molality-scale CO2 activity coefficients.
The IUPAC-NIST SDS-62 compilation retains CO2 partial pressure, dissolved CO2
molality and the activity coefficient at each KCl state. The source convention
therefore implies, at fixed temperature,

$$
H^0(T)=\frac{p_{\mathrm{CO_2}}}
{m_{\mathrm{CO_2}}\gamma_{\mathrm{CO_2}}}.
$$

The audit reconstructs one geometric-mean $H^0$ for each temperature directly
from every printed row. Maximum absolute log residuals are 0.002234, 0.007976,
0.003079, 0.004806 and 0.022633 at 273.15, 298.15, 323.15, 348.15 and 363.15 K,
respectively. All are inside the compiled source's stated 4% relative
activity-coefficient uncertainty. The corresponding $H^0$ values are 12.8011,
29.3613, 51.3402, 74.8031 and 87.5786 atm kg/mol. This verifies that the
retained columns are internally consistent with one temperature-specific
molality standard state; it does not independently validate the pure-water
anchor used to construct those activity coefficients.

That distinction is material. Spycher and Pruess (2005), DOI
`10.1016/j.gca.2005.01.015`, document that He and Morse combined saline Henry
constants from their measurements with an infinite-dilution value adopted from
Plummer and Busenberg (1982), and caution that inconsistencies in that anchor
may explain deviations from other activity-coefficient sources. The present
audit therefore preserves He-Morse as within-source shape evidence, not an
absolute production calibration.

He and Morse use the five-function temperature basis
$1,T,T^2,1/T,\ln T$. On the five retained temperature blocks its design matrix
has rank five, leaving zero residual degrees of freedom. A five-parameter law
can interpolate the five block estimates but cannot provide a residual
variance or identifiable covariance from this source alone. This is the
numerical cause of the covariance blocker, not a request to relax a tolerance.
An independent temperature family or a predeclared lower-dimensional physical
temperature law is required before bounded calibration.

The six-row retained report records the five reconstructed standard states and
the rank/degree-of-freedom result. No coefficient, gauge, empirical temperature
offset, production equation, solver tolerance or source value changes. Table
A.24, Dholabhai/Burgass hydrate data, dense NaCl/KCl controls, divalent salts,
Archer water activity and Burgass-Tohidi remain frozen holdouts.

## Reduced KCl salt-pair temperature law

`CO2SaltPairTemperatureLawAssessmentTest` addresses the zero-residual-degree-of-freedom blocker in the saturated
He-Morse temperature basis without activating the Helmholtz kernel. In the source's molality convention it fits

$$
\ln \gamma_{\mathrm{CO_2}}(T,m)
  = a_T m + \zeta(T)m^2,
\qquad
\zeta(T)=\zeta_{298}
  +\zeta_{1/T}\left(\frac{298.15\ \mathrm{K}}{T}-1\right).
$$

The five $a_T$ values are nuisance salt-level linear coefficients. The two-parameter curvature law is a bounded,
lower-dimensional alternative to one independent curvature coefficient at every temperature. Its inverse-temperature
coordinate represents a first enthalpic temperature dependence over the measured 273.15--363.15 K interval; it is not
claimed outside that interval and does not identify pressure response.

The 26 He-Morse points give rank 7 with 19 residual degrees of freedom and condition number 65.64. Relative to the
five-parameter linear-per-temperature baseline, log-space RMSE falls from 0.030543 to 0.017359 and AICc from -168.408
to -190.567. The ten-parameter saturated-curvature comparator reaches RMSE 0.015895 but AICc -180.704, so the reduced
law is preferred by 9.86 AICc units despite its lower flexibility. The source-space estimates are
$\zeta_{298}=-0.0150603\pm0.0024605$ kg$^2$/mol$^2$ and
$\zeta_{1/T}=-0.0780702\pm0.0188556$ kg$^2$/mol$^2$ (one standard error), with parameter correlation 0.8200.

Leave-one-temperature-block-out prediction gives aggregate RMSE 0.019481 and maximum absolute log residual 0.041504.
The fitted 363.15 K curvature is -0.0010865 kg$^2$/mol$^2$, but its propagated two-standard-error interval crosses zero.
The high-temperature sign therefore remains uncertain. He-Morse contains only one CO2 partial pressure inside each
temperature block, so no pressure coefficient is identifiable. The independent 298.15 K Yasunishi-Yoshida molarity
series independently retains negative curvature and reduces RMSE from 0.018197 to 0.002824, but its
-0.0082239 coefficient is not transferred across concentration bases.

This is a source-space model-selection result, not a production parameter update. He-Morse activity coefficients retain
their adopted pure-water standard-state dependence, while the existing full-Furst scale scan showed that one global
scale cannot reconcile low-salinity Liu NaCl with the dense NaCl/KCl families. Before the salt-pair Helmholtz term can be
activated, independent direct-molality single-salt data with pressure contrast must bound the pressure and high-temperature
response. The candidate must then be mapped into the current electrolyte-CPA/Furst formulation with a declared joint
fit or ablation and tested on frozen Table A.24 mixed brine, Dholabhai and Burgass hydrate families, divalent salts,
Archer water activity, dense NaCl/KCl and Burgass-Tohidi controls. No empirical temperature offset, production
coefficient, database row, solver tolerance or hydrate equation changes here.

## Independent 323.15 K salt-pair transfer diagnostic

`KClCO2SaltPairTransferAssessmentTest` tests the frozen reduced He-Morse
curvature law against independent direct-solubility data before any production
activation. It selects all nine Mousavi-Chapoy-Burgass KCl states at 323.15 K:
four pressures at 10 mass percent KCl, three at 15 mass percent and two at 22
mass percent. This temperature is inside the fitted 273.15--363.15 K He-Morse
range. The other 17 KCl states at 373.15--423.15 K are excluded because using
them would extrapolate the source law. The source fixture retains the article's
CC BY 4.0 values and explicit salt-free aqueous CO2 mole-percent convention.
No Table A.24, hydrate, divalent-salt or water-activity holdout is selected.

The diagnostic evaluates the current electrolyte-CPA/Furst flash first, then
applies only the exact first-order activity mapping at its calculated CO2
fugacity:

$$
\Delta\ln\gamma_{\mathrm{CO_2}}=\zeta(T)m_{\mathrm{KCl}}^2,
\qquad
x_{\mathrm{CO_2},\mathrm{candidate}}
=x_{\mathrm{CO_2},\mathrm{baseline}}
 \exp[-\zeta(T)m_{\mathrm{KCl}}^2].
$$

At 323.15 K the frozen law gives
$\zeta=-0.00902052\ \mathrm{kg^2\,mol^{-2}}$. KCl molality is reconstructed on
exactly the same final-aqueous-water basis as the independent producer. The
mapping is a tangent response, not a coupled flash, a refit, a pressure law or
a transferable production coefficient. It does not change water activity,
guest fugacity, phase equilibrium, the existing Furst block or any database
row.

The independent results are:

| KCl mass percent | States | Baseline log-RMSE | Candidate log-RMSE | Baseline MAPE / % | Candidate MAPE / % |
|---:|---:|---:|---:|---:|---:|
| 10 | 4 | 0.08852 | 0.06905 | 8.277 | 6.421 |
| 15 | 3 | 0.09912 | 0.05372 | 8.918 | 4.196 |
| 22 | 2 | 0.05199 | 0.08046 | 4.863 | 8.249 |
| All | 9 | 0.08578 | 0.06723 | 7.732 | 6.086 |

The frozen law improves both lower-concentration groups and the aggregate, but
fails the high-concentration transfer. At 22 mass percent (3.7833 mol/kg water)
the current-model residuals of -3.49% and -6.23% reverse to +9.81% and +6.69%.
Because the correction is constant within a concentration group, it cannot
repair pressure-dependent residual structure. The numerical cause is therefore
not a missing constant KCl curvature coefficient alone: direct superposition
of the source-space quadratic term on the already nonlinear Furst response
over-corrects the highest concentration.

This result blocks default activation and an unqualified parameter update. The
smallest defensible next model experiment is an opt-in coupled Helmholtz
implementation of the already-audited composition kernel, evaluated jointly
with the existing Furst response rather than added as an independent correction.
It must preserve the frozen He-Morse coefficients and report whether the 22
mass-percent reversal comes from thermodynamic coupling, double counting or a
missing physically sourced high-ionic-strength dependence. Acceptance requires
improvement without residual-sign reversal across the independent 10/15/22
mass-percent family, exact composition/state derivatives, phase and fugacity
closure, and no regression on pure water, NaCl, CaCl2/MgCl2, Archer water
activity, Table A.24, Dholabhai and Burgass controls. If that experiment cannot
remove the reversal without an extra coefficient, the precise missing evidence
is an independent high-molality KCl temperature/concentration family capable of
identifying a bounded saturation or ionic-strength law. No empirical
temperature offset, invented pressure dependence, relaxed tolerance or hydrate
qualification is introduced here.



## Residual KCl curvature and independent-transfer diagnostic

`KClCO2SaltPairTransferAssessmentTest` now distinguishes the total He-Morse
source curvature from the curvature already produced by the current
electrolyte-CPA/Furst formulation. For every He-Morse temperature block it
calculates a matched pure-water state at the same temperature and CO2 partial
pressure, then defines

$$
\ln\gamma_{\mathrm{CO_2}}^{\mathrm{model}}
=\ln\left(\frac{m_{\mathrm{CO_2}}^{\mathrm{pure,model}}}
                 {m_{\mathrm{CO_2}}^{\mathrm{KCl,model}}}\right),
\qquad
\ln\gamma_{\mathrm{CO_2}}^{\mathrm{residual}}
=\ln\gamma_{\mathrm{CO_2}}^{\mathrm{source}}
 -\ln\gamma_{\mathrm{CO_2}}^{\mathrm{model}}.
$$

Source, model and residual responses use the same five temperature-specific
linear nuisance coefficients and the same reduced inverse-temperature
quadratic law. At 323.15 K the source curvature is
`-0.00902050 +/- 0.00151516 kg2/mol2`, whereas the current model curvature is
`-0.01753862 +/- 0.00012732 kg2/mol2`. The identifiable incremental residual
is therefore positive, `+0.00851812 +/- 0.00149621 kg2/mol2`, more than five
standard errors from zero. This is the numerical cause of the prior dense-KCl
overcorrection: applying the full negative source coefficient on top of the
existing model double-counts curvature.

The reduced law improves the source, model and residual fits over their
linear-only comparators. Their log-space RMSE values are respectively
`0.0305432 -> 0.0173592`, `0.0502774 -> 0.00145868`, and
`0.0399819 -> 0.0171421`; corrected AICc changes are `-168.408 ->
-190.567`, `-142.490 -> -319.349`, and `-154.405 -> -191.221`.
The residual is thus statistically identifiable on the He-Morse design, but
identifiability alone does not establish transferability.

Both first-order mappings are evaluated against the frozen independent
Mousavi-Chapoy-Burgass 323.15 K KCl series. The full-source correction retains
its aggregate improvement in log-RMSE (`0.0857793 -> 0.0672304`) but still
worsens 22 wt% KCl (`0.0519884 -> 0.0804617`) and reverses its residual sign.
The non-double-counted residual correction worsens every concentration family:
aggregate log-RMSE becomes `0.136861` and MAPE becomes `12.3379%`, compared
with baseline `0.0857793` and `7.73204%`. The 10, 15 and 22 wt% residual
log-RMSE values are `0.107098`, `0.145144` and `0.172480`.

Disposition: neither the total source coefficient nor the identified residual
coefficient is activated. The evidence rules out a standalone, pressure-
independent first-order activity correction as the smallest defensible model
change. The next formulation check must resolve the pressure/composition
coupling that differs between the low-pressure He-Morse activity design and
the dense MCB states before fitting any production parameter. Table A.24,
Dholabhai/Burgass hydrate, NaCl/CaCl2/MgCl2, Archer water-activity and pure-water
controls remain frozen holdouts; no temperature offset, parameter-table edit,
tolerance relaxation or hydrate qualification is introduced.

## Dense-KCl residual pressure identifiability

`KClCO2SaltPairTransferAssessmentTest` also tests the next proposed physical
direction without adding a pressure parameter. The nine independent
Mousavi-Chapoy-Burgass states at 323.15 K are grouped by 10, 15 and 22 mass
percent KCl. The nested regressions compare three concentration-specific
intercepts with those same intercepts plus one common pressure slope in the
baseline residual

$$
r=\ln(x_{\mathrm{CO_2},model}/x_{\mathrm{CO_2},source}).
$$

The common slope is `-6.39865e-4 MPa-1` with standard error
`5.65813e-4 MPa-1` and a t statistic of `-1.13088`. It lowers log-RMSE only
from `0.0230877` to `0.0206027`, while corrected AIC increases from
`-57.0322` to `-51.8820`. Separate within-concentration slopes are
`+1.18399e-4`, `-1.37634e-3` and `-1.54139e-3 MPa-1` for 10, 15 and 22 mass
percent KCl, respectively. The opposite sign at 10 mass percent and the
two-point 22 mass percent block prevent a common pressure response from being
identified.

If the same-fugacity Poynting relation
$d\ln x/dP=-\bar V/(RT)$ is imposed only as an interpretation, the fitted
residual slope corresponds to

$$
\Delta\bar V_{app}=\bar V_{model}-\bar V_{source}=-RT\,dr/dP
=+1.71920\ \mathrm{cm^3\,mol^{-1}}.
$$

This is an apparent residual volume, not a fitted partial molar volume or a
production parameter. Total pressure, gas composition and guest fugacity vary
across the dense measurements, so the residual slope cannot be assigned to the
aqueous standard-state pressure term alone. A pressure-law candidate requires
an independent KCl source with at least three matched pressures per
temperature and concentration, an explicit source/model CO2 fugacity mapping,
and a salt-free reference on the same convention. The Table A.24 mixed-brine
series and independent hydrate families remain frozen holdouts. No parameter,
equation, tolerance, hydrate model or empirical temperature offset changes in
this diagnostic.

## Same-study pure-water KCl salt-effect discriminator

`KClCO2SourceMatchedSaltEffectAssessmentTest` removes the absolute pure-water
baseline mismatch from the dense KCl comparison. It combines only factual
values already retained from the CC BY 4.0 Mousavi-Chapoy-Burgass source:
Table A1 pure-water states bracket Tables A11-A13 KCl states at the same
temperature. Linear interpolation of reported liquid CO2 mole percent in
pressure defines the primary source-water value; log-mole-fraction
interpolation is retained as a form-sensitivity check. No extrapolation is
allowed. This selects seven KCl states at 323.15 K and six at 373.15 K. The
remaining high-pressure states and all 423.15 K states are excluded because a
same-temperature A1 bracket is absent.

For each retained state the source and model salting effects are

$$
S_{source}=\ln(x_{water,source}/x_{KCl,source}),\qquad
S_{model}=\ln(x_{water,model}/x_{KCl,model}).
$$

Every source-matched state has positive measured salting-out. The current
model overstates it at all thirteen states. The model-minus-source log-effect
bias, RMSE and maximum absolute residual are `+0.0894094`, `0.0933080` and
`0.124556`, respectively. At 323.15 K they are `+0.104176`, `0.104833` and
`0.124556`; at 373.15 K they are `+0.0721812`, `0.0777317` and `0.120354`.
The maximum difference between linear and log interpolation is `0.0209953`
log units overall and `0.00490401` at 323.15 K, smaller than the observed
model residuals but retained explicitly rather than treated as measurement
uncertainty.

The exact model identity

$$
S_{model}=-\ln(f_{KCl}/f_{water})
+\ln[(f/x)_{KCl}/(f/x)_{water}]
$$

separates guest-side fugacity from the effective aqueous response. The maximum
absolute guest contribution is `0.00202187`, while the minimum absolute
effective-aqueous contribution is `0.172507`. On every retained row the guest
term is at most `0.6769%` of the sum of the two absolute contributions. The
reproducible discrepancy is therefore carried overwhelmingly by the aqueous
CO2 activity/fugacity formulation, not by guest fugacity. This is a model-side
decomposition; the source does not publish a separate aqueous CO2 fugacity and
the result is not an independently measured partial molar property.

The complete He-Morse source-minus-model law, including its temperature-block
linear term and quadratic salt-pair curvature, is transferred without refit to
the seven source-matched 323.15 K states. It improves every point: holdout RMSE
falls from `0.104833` to `0.0502973`, bias from `+0.104176` to `+0.0486454`,
and maximum absolute residual from `0.124556` to `0.0737390`. Propagated fit
covariance is reported for every correction, and each correction exceeds three
standard errors. The remaining positive bias proves that the complete
low-pressure law is still an under-correction; it is a supported formulation
direction, not a qualified parameter update.

The smallest defensible implementation candidate is a thermodynamically
consistent KCl salt-pair linear-plus-quadratic contribution in the electrolyte
CPA residual Helmholtz energy, with all composition and state derivatives,
explicit single-ion gauge, bounded refit and double-counting check against the
existing Furst response. He-Morse remains calibration evidence; these
same-source dense states are holdouts. Table A.24, Dholabhai/Burgass hydrate,
NaCl/CaCl2/MgCl2, Archer water activity, Yasunishi-Yoshida and pure-water
controls remain frozen. No production parameter, equation, tolerance, hydrate
model or empirical temperature offset changes in this diagnostic.


## Smooth residual-law candidate and frozen high-temperature disposition (2026-11-26)

The next dependency gate fits the **source-minus-current-model** KCl response rather than
reusing a total source activity coefficient.  On the He-Morse molality convention, define

$$
q(T) = \frac{298.15\ \mathrm{K}}{T} - 1
$$

and the dimensionless residual log-activity candidate

$$
g(T,m_{\mathrm{KCl}}) =
\left(\lambda_{\mathrm{ref}} + \lambda_{1/T} q(T)\right)m_{\mathrm{KCl}}
+ \zeta_{1/T}q(T)m_{\mathrm{KCl}}^2.
$$

This form is a diagnostic candidate, not an active production term.  It is fitted to the
26 He-Morse states after evaluating a matched current-model pure-water state at each
temperature and CO2 partial pressure.  The retained three-parameter fit is full rank,
has condition number 31.7646, calibration log-RMSE 0.0212144 and AICc -193.269.  Its
coefficients (estimate +/- one standard error) are:

| coefficient | estimate | standard error |
|---|---:|---:|
| $\lambda_{\mathrm{ref}}$ | -0.00975624 | 0.00229335 |
| $\lambda_{1/T}$ | +0.432860 | 0.0486611 |
| $\zeta_{1/T}$ | -0.0922848 | 0.0118244 |

The largest absolute parameter correlation is 0.923475.  Adding an independent
reference-temperature quadratic coefficient produces AICc -193.164 and an unscaled
condition number 173.112; retaining five temperature-specific linear coefficients
produces AICc -191.221.  The smaller law therefore preserves the supported temperature
dependence without the unsupported parameters.  The zero residual curvature at
298.15 K applies only to the source-minus-model increment; it does not assert zero
total KCl/CO2 source curvature.

No-refit transfer to the seven bracketed 323.15 K Mousavi-Chapoy-Burgass states
improves every point: log-RMSE 0.104833 to 0.0501344, bias +0.104176 to
+0.0479878 and maximum absolute residual 0.124556 to 0.0759640.  The six
373.15 K states are outside the He-Morse calibration range and remain an
extrapolative holdout.  Aggregate log-RMSE improves from 0.0777317 to 0.0474229,
but the bias crosses from +0.0721812 to -0.0373790 and only four of six points
improve.  That local and aggregate over-correction blocks activation.

**Disposition.**  No database parameter, residual-Helmholtz term, hydrate equation,
solver tolerance or empirical temperature offset changes.  The smallest defensible
next implementation is an opt-in, zero-default residual-Helmholtz prototype with the
three coefficients above, complete composition and temperature derivatives, an
explicit salt-level gauge, and the calibrated validity range 273.15--363.15 K.
Activation requires derivative/finite-difference closure, a Furst double-counting
audit, bounded covariance/sensitivity, and independent in-range high-temperature KCl
data.  Table A.24, Dholabhai/Burgass hydrate families, NaCl/CaCl2/MgCl2, Archer water
activity, Yasunishi-Yoshida and pure-water controls remain frozen holdouts or adjacent
controls.

## Independent Kamps total-pressure transfer (2026-11-27)

Kamps et al. (2007), DOI `10.1021/je060430q`, supplies the independent
single-salt check required above. The complete 102-row KCl ThermoML dataset is
retained with its exact source checksum and semantics in the
[benchmark notes](../../src/test/resources/data/chemistry_benchmarks/co2_kcl_solubility_kamps2007_thermoml.md).
The reported property is closed-cell **total equilibrium pressure**, not CO2
partial pressure. `KClCO2Kamps2007IndependentTransferAssessmentTest` therefore
imposes the source total pressure and explicit KCl molality on the same one-kilogram
final-aqueous-water basis used by the dense-brine reproducer; water vapor, gas
composition and CO2 fugacity remain model outputs.

Fourteen states were frozen before model evaluation by selecting the lower median
positive CO2 loading in each source temperature and low/high KCl block. No Kamps
value is fitted. For a direct-solubility residual

$$
r=\ln(m_{\mathrm{CO_2},model}/m_{\mathrm{CO_2},source}),
$$

the source-minus-model log-activity candidate implies

$$
m_{candidate}=m_{model}\exp[-g(T,m_{\mathrm{KCl}})],\qquad
r_{candidate}=r-g.
$$

The current model underpredicts twelve of fourteen selected source states. In the
approximately 2 mol/kg KCl family, all seven temperatures improve: log-RMSE falls
from `0.154698` to `0.0391160`, signed bias from `-0.150819` to `-0.0372877`, and
candidate maximum absolute residual is `0.0632243`. In the approximately 4 mol/kg
family, only the 313 and 333 K states improve. Log-RMSE worsens from `0.0346649` to
`0.0847042`, bias crosses from `-0.0196903` to `+0.0677462`, and maximum absolute
residual reaches `0.150368` at 433.05 K. Aggregate log-RMSE improves from `0.112101`
to `0.0659730`, but the family-specific failure blocks activation.

This independent result localizes the remaining discrepancy: the fitted
temperature-dependent quadratic term is too strong at about 4 mol/kg above
333 K, while the same law remains directionally useful around 2 mol/kg through
433 K. A single smooth three-parameter law is therefore not yet a defensible
production formulation across both concentration families. The next candidate must
alter or bound the high-molality temperature curvature and be refitted only on a
declared calibration partition, with independent source-family holdouts and full
derivative/Furst double-counting checks. No database parameter, residual-Helmholtz
term, hydrate equation, solver tolerance or empirical temperature offset changes in
this diagnostic. Table A.24 mixed brine and Dholabhai/Burgass hydrate data remain
frozen downstream holdouts.
