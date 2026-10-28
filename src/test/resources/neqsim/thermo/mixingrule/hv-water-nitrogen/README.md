# Nitrogen/water HV transfer assessment

This fixture separates numerical consistency from physical accuracy. No database,
alpha implementation or default coefficient is changed. `HuronVidalWaterNitrogenAssessmentTest`
uses the image-verified water-first nitrogen tuple in Yushchenko/Brusilovsky2016
Table1 (DOI10.1016/j.fluid.2015.09.029): D12=-64.5K, DT12=-1.05,
D21=3500K, DT21=-1.0, alpha=.08. Both directions and activation flags are
configured on every allocated phase. The other six PR tuples in the research
catalog have no executable physical acceptance from this fixture.

## Measurements and uncertainty

The public NIST/TRC [water-in-nitrogen record](https://trc.nist.gov/ThermoML/10.1021/je049676q.html)
provides35 gas-water mole fractions at282.86–363.08K and425–4962kPa.
The [nitrogen-in-water record](https://trc.nist.gov/ThermoML/10.1021/je049869d.html)
provides52 liquid-nitrogen mole fractions at274.18–363.02K and915–7160kPa.
The CSVs preserve source row order, literal fractions and absolute pointwise
compiler-expanded95% uncertainties. These are not new uncertainty estimates or
verified original-author uncertainty statements. Original measurement-table
images have not been inspected. All87 JSON/XML numeric quadruples were checked
exactly; these formats represent the same compiler data, not independent experiments.

The [durable source audit](https://github.com/equinor/neqsim/issues/4576#issuecomment-6466609726)
contains download URLs, SHA256s, complete raw rows, units and the executed audit script.
Pressure is absolute kPa; divide by100 for NeqSim bara. Fractions are molar;
there is no ppm, mass or standard-volume conversion. Source compound1 is nitrogen,
compound2 water; the interaction table instead puts water first.

## Frozen comparison protocol

Model0 is default SRK-HV; model1 default PR-HV; model2 the staged PR tuple with
**current library pure properties**. Their exact equivalence to the source's
Tc/Pc/acentric inputs is unresolved. Each report records these actual pure inputs.
The assessment does not reproduce the fitted source configuration and cannot
attribute discrepancies solely to the interaction fit before a pure-water
vapor-pressure/alpha audit. No measured feed is available: every flash uses an
explicit synthetic .5/.5 molar feed. The experiments share zero exact T/P points;
no paired measured tie-line or feed is constructed from nearby observations.

Only6 gas and5 liquid observations satisfy the source's323.15–423.15K,
25–700bara emphasis. Every row is retained, with an explicit in-range flag.
The76 remaining observations are transfer/challenge points. Even323.10,
323.13 and323.14K remain outside a323.15K boundary. Above473.15K the source
reports degradation; this fixture does not qualify that extrapolation.

Numerical gates are two-phase gas/aqueous identity, positive phase amounts,
normalization1e-12, balance1e-10 and log-fugacity equality1e-8 for all261
measurement/model flashes. Selected fresh/repeated/changed/returned states at
353.15/363.02K and50bara cover both component orders and all three models.
All five native `ThermodynamicModelTest` identities are asserted at1e-8;
`checkNumerically()` at1e-6 also preserves byte-identical serialized source state.
Independent fixed-composition liquid T differences use h=.001/.0005K:
step-halving agreement1e-8/K and analytic agreement1e-7/K. This resolution
requirement is stricter than the derivative agreement bound; neither changes
native checker behavior or tolerances. Existing methanol/MEG and gas/oil/water
regressions remain separate adjacent qualification evidence.

## Results and limits

Reports under this directory preserve each prediction, signed error/U95,
relative error and in-range flag. The test regenerates them in
`target/hv-water-nitrogen/`. There is **no empirical accuracy pass threshold**.
The source's fitted5% claim is not a universal acceptance tolerance.

Initial transfer diagnostic AARD (%), including all challenge points:

| Direction | default SRK-HV | default PR-HV | staged PR-HV |
|---|---:|---:|---:|
| Water in nitrogen |19.0556|10.7760|8.4429|
| Nitrogen in water |90.3800|90.6336|32.0231|

Only9/35 and4/52 staged predictions lie within the supplied point uncertainty
intervals. Improvement over defaults therefore does not establish accuracy.
Input/parameter uncertainty has not been propagated. No coefficients are fitted.

This test/data-only increment does not optimize production code. Allocation
reductions from #4660 do not imply throughput acceptance; reliable matched
classical/HV timing, source-pure-input reconciliation, independent scientific
review and physical acceptance remain open. No hydrate, salt/brine, ice,
inhibitor/oil partition, heavy-fraction or general extrapolation claim follows.

## Matched timing characterization (not throughput acceptance)

`HuronVidalWaterNitrogenBenchmark` measures one EOS/model/scenario per JVM,
using the same binary .5/.5 feed,353.15K and50bara. Classical rule2 and default
HV rule4 are measured for SRK and PR, together with the staged PR rule4 tuple.
Scenarios are full-system init1, init3 and complete changed-temperature TP flashes
(353.15/353.35K with init3). Each JVM warms100 operations, then records five
rounds of100 operations. Two independent forks run opposite selection order:
30 JVMs,150 timing rows and15000 measured operations. The raw file records
phase count, both endpoint observables and untimed balance/normalization/fugacity
controls. All cases have two phases; maximum residuals are1.67e-16 balance,
1.12e-16 normalization and6.94e-13 log fugacity.

Timing spread remains several-fold within selections. These short warmups,
two forks and shared-host scheduling do not establish steady-state throughput,
non-regression or a speedup. Source/default/classical models produce different
compositions and flash work; total timings are not isolated implementation
cost. No production algorithm is optimized here. The raw results and environment
are retained to expose this limitation; large-mixture/allocation qualification
and identical-binary controls remain in the separate campaign evidence.

The benchmark can be invoked on the test classpath with arguments model(0/1/2),
rule(2/4), scenario(init1/init3/flash), outputCSV. The assessment fixture defines
model meanings; staged model2 requires rule4. Use separate JVMs for selections,
reverse the selection order in a second fork, and preserve all raw rounds.

The actual pure inputs recorded in every assessment row are water
Tc647.3K/Pc220.89bara/omega.344 and nitrogen
Tc126.1K/Pc33.944bara/omega.0403. All lie in the low-omega branch of the
source PR alpha expression. Exact source critical-property inputs remain unknown.
