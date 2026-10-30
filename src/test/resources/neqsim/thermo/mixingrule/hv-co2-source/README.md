# CO2/water source-configuration diagnostics

This is a staged research fixture. No production database, alpha implementation,
default or public profile API changes. It reuses current setters and separates
native numerical consistency, physical measurement errors and timing.

## Complete declared configurations

The selected pair is CO2/water only. Every allocated phase is configured
independently; all pair flags are first Classic, then both selected directions HV.
A separate forced-phase selection control checks unselected-pair kij behavior;
it does not establish multicomponent phase stability or inhibitor partition.
Amounts are explicitly synthetic 0.5/0.5 molar feed, not measured feed or tie-line.
Temperature is kelvin, pressure absolute bara, compositions mole fractions.
All configurations use linear covolume and infinite-pressure covolume-weighted
modified NRTL, tau = D[K]/T + DT, symmetric alpha=0.03.

Directions in this table are **CO2 to water / water to CO2**. The primary 2016
table instead places water first; do not copy it without reversing the mapping.

| Profile | EOS | D12 / DT12 | D21 / DT21 | Alpha/pure input basis |
|---|---|---|---|---|
|0|PR|5230 / -8|-4127 / 8.9|2016 source alpha; current library critical/acentric inputs and native PR cubic constants|
|1|SRK|5820 / -2.565|-3339 / 0.1390|2017 paired Twu; explicitly declared reference critical inputs|
|2|PR|5831 / -2.559|-3311 / 0.03770|2017 paired Twu; declared reference critical inputs and exact-critical PR constants|

Source 2016: Yushchenko/Brusilovsky, DOI10.1016/j.fluid.2015.09.029 Table1.
Source 2017: Aasen et al., DOI10.1016/j.fluid.2017.02.006 Tables1/5.
Primary table images and directed conventions were verified in the campaign
[source receipts](https://github.com/equinor/neqsim/issues/4576#issuecomment-6517684280).
Critical and cubic inputs below are declared diagnostic assumptions, not a
verified reconstruction of the original authors' complete configuration.

Profile0: CO2 Tc304.19K/Pc73.815bara/omega0.2276; water647.30/220.89/0.344;
PR OmegaA0.45724333333/OmegaB0.077803333. Profiles1/2: CO2
Tc304.1282K/Pc73.773bara; water647.096/220.64. Twu L,M,N respectively:
SRK CO2(0.046115,0.91388,6.8838), water(0.36117,0.87308,2.4389);
PR CO2(0.022762,0.92848,7.7217), water(0.40274,0.87000,1.8869).
Profile2 explicitly uses OmegaA0.4572355289213822/OmegaB0.07779607390388846
to match the previously declared independent diagnostic; not a new fit.
Both earlier native-PR-constant and aligned results are retained in the receipt.
The2016 source emphasizes323.15–423.15K/25–700bara; the2017 mixed data fit
is approximately274–478K and below60.8MPa. Neither statement establishes a
continuous qualified operating range. Individual measured points define the
comparisons below. Original fit covariance/parameter uncertainty and complete
author pure-critical/alpha input reconciliation remain unavailable.
The2017 HV1 label denotes this HV formalism, not MHV1.

## Measurements and uncertainty

The input has120 original observations:58 water-in-CO2 values from
Meyer/Harvey2015 DOI10.1002/aic.14818 Table2,29 CO2-in-water from
Ahmadi/Chapoy2018 DOI10.1016/j.fluid.2018.02.002 Table6 and33 from
Lipinski/Richter2021 DOI10.3390/physchem1020012 TablesA1-A3.
The latter two postdate the 2017 fit. The gas data are in-sample for that fit.
They are separate experiments, not simultaneous bidirectional tie-lines.
Actual, nonrectangular states span283.15-423.48K/4.99480-420.770bara.
These bounds do not establish continuous-range accuracy.

2021 A1row6 pressure is printed `1.0.0180`MPa in both PDF and HTML. Its numeric
input stays blank and every profile records blocked_original_pressure.
No pressure is guessed and no observation is silently discarded.
The reverse author_Ux_k2 column preserves absolute author expanded uncertainty.
Gas author uncertainty is on enhancement factor f, not yw; the field stays blank.
`gas-primary.csv` and `reverse-primary.csv` preserve original U(T)/U(P),
enhancement-factor uncertainties, raw decimals and printed-data inconsistencies.
PDF hashes and table-image audits remain in the linked durable receipts.
`input.csv` is the executable projection, and `primary-assessment.csv` retains
all360 attempted profile/observation rows, including all3 blocked rows.
Repository CSVs use LF; numeric/raw strings are unchanged. The auditor reconstructs
the receipts’ CRLF bytes before checking their exact primary SHA256 hashes,
and also reports the repository LF hashes. It checks decimal/unit/direction
lineage, expanded uncertainty, every residual and each timing protocol separately.
Run `python3 devtools/audit_huron_vidal_co2_source.py` from the repository root;
a newly generated report can be checked with `--assessment target/hv-co2-native/primary-assessment.csv`.
Its experimental errors are outputs, never acceptance thresholds.

## Numerical and physical interpretation

HuronVidalCo2SourceDiagnosticTest asserts all five ThermodynamicModelTest
identities at1e-8 and checkNumerically at1e-6, with byte-identical source
serialization, balance1e-10, normalization1e-12 and log-fugacity equality1e-8.
Fresh/repeated/changed/returned/opposite-order states are compared at353.15
and363.15K/50bara. Independent liquid temperature differences use0.001/0.0005K,
step-halving agreement1e-8/K and analytic agreement1e-7/K, as in the existing
nitrogen assessment. No tolerance or checker repair is included.

The separate selection test performs102 forced-GAS native controls at50bara,
353.15/363.15/returned353.15K with water, CO2, methane, methanol and MEG,
in both component orders and all3 declared profiles. Every ordinary kij is
retained exactly; the methane/methanol marker0.0123 is synthetic test input,
not a calibrated coefficient. Only both CO2/water directions are selected HV.
Perturbing dormant coefficients for every unselected pair leaves log fugacity
unchanged1e-12; disabling the selected pair restores CLASSICAL rule2 within1e-11.
Each control asserts all5 native identities/checkNumerically, source serialization,
normalization1e-12 and component balances1e-10. These forced-phase controls
are distinct from411 complete binary flashes and do not qualify equilibrium
MEG/methanol partition, dense-phase stability or a five-component operating range.

Across357 usable observation/profile flashes, maximum balance/normalization/
log-fugacity errors were9.33e-15/2.22e-16/1.84e-9 on master9975919e.
Dense CO2-rich OIL/AQUEOUS endpoints are retained as well as GAS/AQUEOUS.
Native2017 predictions agree with238 earlier independent values to4.61e-10
relative difference under the same declared inputs.

| Profile | Gas2015 AARD% | Reverse2018 AARD% | Reverse2021 AARD% | Inside author U(x):2018/2021 |
|---|---:|---:|---:|---|
|0|10.22650|75.86518|83.64500|0/29;0/32|
|1|1.70668|7.68627|5.44483|5/29;17/32|
|2|1.61746|6.00732|4.94856|6/29;19/32|

No experimental accuracy threshold is asserted. Most reverse2017 predictions
also exceed author uncertainty. Original pure inputs, pure vapor pressures and
physical acceptance remain unresolved. This fixture does not qualify MEG or
methanol partition, heavy fractions, hydrate dissociation, salts/brines, ice or
extrapolation. Existing validated opt-in profiles remain separate and unchanged.

## Executable performance protocol

HuronVidalCo2SourcePerformanceBenchmark extends the existing nitrogen benchmark
protocol. Arguments are profile0/1/2, rule2/4, scenario(init1/init3/flash),
outputCSV. Classical controls retain the same pure inputs, T/P and feed; the
runner asserts unchanged pure critical/cubic/alpha fingerprints after switching
mixing rule. Flashing the different mixing models changes thermodynamics and
solver work, so their time ratio is not isolated implementation overhead.

Each selection runs in a fresh JVM. Warmup is100000 initialization calls or3000
nearby-temperature TP flashes. Ten measured rounds each contain10000 init calls
or100 flashes. Full flashes alternate353.15/353.35K and include init3. Setup,
first flash and endpoint diagnostics are untimed; after each round init3 and
equilibrium residuals are checked. The output retains temperature/pressure,
actual topology, phase fraction, both solubilities and residuals.

The campaign driver compiled both test sources with --release8 and aborted on
compile failure before starting timing. It ran four independent forks per
selection in serial, alternating rule2/rule4 order and reversing scenario/profile
order on odd forks. Flags are -Xms512m -Xmx512m -Xbatch -XX:CICompilerCount=2.
No EOS/scenario switches inside a JVM. All72 JVMs/720rounds are retained;
forks, not rounds, are the independent replication units. Same-rule fork ratios
provide identical-binary variability controls; no code optimization is implied.

The first launch before final compile was rejected in full and is explicitly
marked invalid-precompile. Pilot/invalid outputs are not pooled with the final
matrix. Shared-host scheduling, JIT/GC sensitivity, small binary scope and absent
solver counters limit interpretation. Larger mixtures/allocation and matched
baseline/candidate code optimization remain distinct campaign gates. No timing
assertion can weaken physical, numerical or state-isolation checks.

## Retained binary characterization on master9975919e

The four-fork rule2/rule4 experiment contains72 JVMs/720 rounds/4824000
measured calls. All endpoint contracts pass: maximum balance5.56e-16,
normalization1.12e-16 and log-fugacity2.93e-12. The table gives the median
of four per-fork HV/CLASSICAL ratios, followed by their range.

| Profile | init1 | init3 | Complete flash |
|---|---|---|---|
|0|2.144 [1.403,2.470]|3.291 [2.944,3.577]|1.612 [1.537,2.205]|
|1|6.432 [6.114,6.506]|5.780 [5.124,6.207]|3.006 [2.702,3.133]|
|2|5.694 [3.366,6.647]|5.646 [5.191,6.548]|3.124 [2.298,3.660]|

Same-rule identical-binary later/earlier controls range0.563-1.805. This
variability is retained, not filtered. Every cross-fork endpoint is reproduced
within1e-12, but CLASSICAL and HV endpoints differ. Timings are not equivalent
solver work or evidence that the source configurations are physically accurate.

A separate SRK2017/HV/init3 JFR run retains114 execution samples, of which103
contain the benchmark operation stack. Top frames include45 in Twu diffdiffaT,
9 in Twu diffdiffalphaT and19 in modified-NRTL getGamma. This sample includes
warmup and measurement, not isolated steady-state percentages. Instrumented
timings are never pooled with the uninstrumented matrix.

Source inspection identifies a narrower removable cost: the first CLASSICAL-pair
loop requested pure first/second T derivatives even at init1, where they are
unused. The proposed candidate moves those calls inside the existing
higher-order branch. No cache/state/alpha equation or init3 calculation changes.
The baseline request-count regression fails with six calls for the first counted
component; candidate requests none at init1 and preserves init3 subclass dispatch.
The357-case experimental report remains byte-identical, and all44 selected
native/component derivative/initialization tests pass. This numerical result
does not itself prove a timing benefit. The separate baseline/candidate/control
experiment retains four init1 forks and two init3/fullflash forks; it is not
pooled with the model-comparison protocol above.

## Candidate outcome: held, not accepted for production

A separate72-JVM/720-round code experiment retains5418000 measured calls.
Median candidate/base and candidate/identical-control ratios at init1 are
0.557/0.593 for SRK2017 and0.588/0.542 for PR2017. PR2016/library init1 is
noisy (0.954/0.943 medians, adverse individual forks retained).
The initial ordinary-alpha PR init3/fullflash observations were adverse.
Two additional fork pairs per adverse scenario (12more JVMs/120rounds) were
run before deciding: across four forks, PR2016 init3 candidate/base median
1.0639 and candidate/control1.0628; complete flash1.0258/1.0174 with wide
individual variation. This does not establish ordinary-alpha nonregression.
The candidate therefore remains a research patch and is not activated.

Separate PrintCompilation runs show getGamma bytecode size4611 on both
baseline and candidate, each reaching tier4; the observed C1 invalidation
accompanies normal tier promotion. No skipped compilation was found. These
logs neither establish a type-switching explanation nor prove absence of
every JIT effect. Instrumented timings are not pooled with either matrix.
No tolerance, physical model or state-isolation gate is relaxed to accept
a favorable subset. Large-mixture/allocation and reliable broader fullflash
nonregression remain required before this optimization can be reconsidered.

The frozen timing artifacts were measured on master9975919e before the
selection-control refactor. They are historical binary evidence, not timing
claims for this delivery head. The retained benchmark is executable using its
main method; fresh acceptance comparisons must compile both source heads in
separate output directories and reproduce the frozen JVM/warmup/order protocol.

To reproduce the CLASSICAL/HV matrix after the normal Maven test compilation,
resolve its test dependencies using `./mvnw dependency:build-classpath
-Dmdep.includeScope=test -Dmdep.outputFile=target/test-classpath.txt`, then run
`python3 devtools/run_huron_vidal_co2_benchmark.py --classpath-file
 target/test-classpath.txt --source-head VERIFIED_40_CHARACTER_COMMIT
 --output target/co2-fresh-matrix`. Join each wrapped command onto one line.
The runner defaults to4 forks/all3 profiles/all3 scenarios, records source/class
hashes, and refuses to overwrite an evidence directory. `--plan-only` verifies
ordering without timing. Restricted `--profiles`, `--scenarios` and `--forks`
are smoke/subset runs and do not reproduce the full acceptance protocol.
