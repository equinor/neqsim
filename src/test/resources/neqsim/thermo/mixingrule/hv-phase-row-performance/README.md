# Call-local phase row-attraction reuse

Campaign [#4576](https://github.com/equinor/neqsim/issues/4576), audited base
`bd8a56f694ad095f87f0f990eb4772700a126ccd`.

## Contract

The nested classical modified-NRTL reduction repeatedly requests the same pure
row attraction while evaluating the activities of one cubic phase. The candidate
shares these scalar row energies across that complete activity evaluation. It
retains the expression `-deltaEOS * aT(T) / b`, the pair-selection comparisons,
all sums and their order, and every first/second temperature-derivative request.
There is no approximation, fitted coefficient, EOS/alpha substitution or retained
cache. Two arrays belong to one invocation and do not enter cloned/serialized
fluid state. Pure and all-HV matrices allocate neither array. The standalone
component API retains its original request behavior. Custom activity subclasses
use the original virtual calls for the complete sum.

`HuronVidalPhasePureRequestTest` compares every activity, temperature derivative,
composition derivative and excess-Gibbs sum with independently evaluated
standalone components at zero numerical tolerance. Cases cover both EOS families,
1/5/15 components, init1/init3, selected/classical/all-HV matrices, repeated and
changed temperatures, exact/padded/distinct selectors and subclass dispatch.
The existing consistency, finite-difference, clone, parameter-selection and
gas/oil/aqueous tests remain acceptance checks. Numerical parity is separate from
experimental accuracy.

## Frozen performance protocol

Use the existing `devtools/benchmark_huron_vidal_rows.py` and
`HuronVidalSelectedPairPerformanceBenchmark` unchanged. Compile the baseline and
candidate component **and phase** classes with the same Temurin 17.0.21+9 compiler
and `--release 17`; prepend each directory to the same complete classpath. The
driver hashes the ordered contents of all classpath directories and dependency
JARs before and after measurement, not only the component class.

The matrix has profiles 0 (ordinary PR with retained 2016 source tuple),
1 (SRK with the retained 2017 Twu profile), and 2 (PR with its retained 2017 Twu
profile); 5/15 components; init1/init3/full flash; and classical rule 2/HV rule 4.
Each cell runs in a separate JVM with fixed 512 MiB heap, `-Xbatch` and two compiler
threads. Two forks alternate case and binary order. Each fork includes baseline,
candidate and identical-baseline control. Warmup is 20,000 initializations or
1,000 complete flashes; ten measured rounds use 2,000 initializations or 50 flashes
each. Do not pool earlier trials compiled against a different common classpath.

Retain raw round CSVs, phase/component endpoint CSVs, process logs, execution
manifest and the auditor's summary. The auditor requires the complete ordered
roster, unmodified evidence hashes, identical same-case topology and endpoint
vectors within 1e-12, component balances within 1e-10, normalization within 1e-12
and log-fugacity equality within 1e-8. Compare candidate with both baseline and
identical-binary control; variability is evidence, not a timing acceptance
threshold. Rule 2 and rule 4 have different physical predictions and solver work.
Do not interpret their cost ratio as equivalent-model overhead or accuracy.

## Disposition boundaries

The earlier component-local row-cache trial was rejected: it allocated two arrays
for each activity, with about 35% more complete-flash allocation in the retained
large-mixture pilot. The phase-level trial reduces this to one pair of arrays per
phase evaluation. Its preliminary 24-JVM PR/Twu pilot is not the final matrix.

A separately reproduced legacy-constructor defect is held outside this runtime
change. Superclass initialization reloads database tables; complete explicit HV
inputs must be bound through the existing `setParams` API after construction.
Repairing the constructor also changes the Wong-Sandler adapter's parameter
selection. That requires separate equation/convention, derivative and equilibrium
qualification. A common fixed-root excess-Gibbs probe preserves all 72 numeric
fields with this candidate; that probe does not qualify full Wong-Sandler VLE.

No staged parameter profile is activated here. The retained water/n-butane and
inhibitor partition discrepancies, original source alpha/critical-property
requirements, independent holdouts, hydrate validation and ice/brine limits remain
campaign work. This mathematically equivalent reuse does not resolve those gaps.
