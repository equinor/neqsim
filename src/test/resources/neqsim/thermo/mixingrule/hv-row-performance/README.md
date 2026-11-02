# Held HV evaluator changes: frozen binary qualification

Frozen timing baseline: `f1982968aac7dd2bf02340fe13a97635462e624f` (NeqSim 3.27.0).
This delivery preserves the production evaluator byte-for-byte. The proposed
row-cache and canonical-selector changes are retained only as held experiments:
all numerical checks could pass while matched full-flash timing still regressed.
There is no new runtime workspace, field, shared cache, equation, pure-function
request change, parameter/alpha selection, default retuning or tolerance change.
Existing merged column reuse and both historical selector comparisons remain.

The new runner implements complete binary-comparison acceptance evidence, and
its corruption tests reject missing/duplicate/reordered processes, changed raw
receipts, invalid identical-binary controls and balanced but different phase
endpoints. Timing has no brittle CI threshold: held results stay visible and
require engineering review. See [held experiments](HELD.md) and the retained
raw manifest/summary; no runtime proposal is accepted merely by this auditor.

## Models, states and acceptance boundary

This experiment reuses the unmodified
`HuronVidalSelectedPairPerformanceBenchmark` and its complete
[selected-pair model/feed protocol](../hv-selected-pair-performance/README.md).
Profiles0/1/2 are PR2016/library, declared SRK2017/Twu and declared PR2017/Twu.
Each explicitly selects only the existing staged CO2/water HV pair; all remaining
pairs, including methanol/MEG, retain ordinary CLASSICAL kij. Rule2 controls keep
the same EOS and pure critical/cubic/alpha inputs as their matched rule4 case.
The [source configurations and physical limitations](../hv-co2-source/README.md)
remain unchanged. This is not additional empirical parameter qualification.

Five/fifteen-component one-mole feeds are at353.15K and50bara with multiphase
stability checking. Complete flashes alternate353.15/353.35K on the same
independently configured worker and include the subsequent init3. Initialization
timings operate on the previously flashed state. Full gas/aqueous or
gas/oil/aqueous phase vectors identify the actual topology and solver outcome.
No transport property is inferred from init3 alone.

Every endpoint requires component balance1e-10, beta/composition normalization
1e-12 and interphase log-fugacity equality1e-8. The inherited auditor reconstructs
these independently from every retained beta/z/x/logphi vector and rejects
nonfinite data, missing/duplicate rounds, phases, components or scenario labels.
The binary-comparison auditor additionally requires the same complete phase
roster and all same-case endpoint values to agree within1e-12 across binaries
and independent forks. It retains every attempted process and rejects an
incomplete/reordered process roster or changed raw CSV/state/log hash.

Native qualification is separate from timing: all five existing
`ThermodynamicModelTest` identities at1e-8 and detached `checkNumerically()` at
1e-6 remain assertions, with serialization preservation. Independent finite
differences, composition Hessian/Gibbs-Duhem, pure/trace/amount/order limits,
repeated/changed states, tuned fixed-T alpha mutation, clones and concurrency
remain in the existing focused/adjacent regression suite. The
[pure-request regression](../hv-row-pure/README.md) also compares240 complete
scalar fingerprints against unmodified master and preserves the exact baseline call budget with
delegating actual EOS components. These numerical gates are not physical error
tolerances or evidence of inhibitor partition accuracy.

## Frozen execution and reproduction

Three profiles × two mixture sizes × init1/init3/full flash × rules2/4 × two
independent forks × baseline/candidate/identical-baseline-control gives216 serial
JVMs. Each JVM has one profile, size, rule and scenario, avoiding cross-scenario
HotSpot type-profile switching. Odd forks reverse both case and binary order.
JVM flags are `-Xms512m -Xmx512m -Xbatch -XX:CICompilerCount=2`. Each process warms
20000 initialization calls or1000 complete flashes, then measures ten rounds of
2000 initializations or50 flashes. Construction, native checks, CSV writing and
endpoint validation are untimed. Allocation-counter overhead remains in absolute
values; unsupported counters remain unavailable, never zero-filled.

The manifest records Java/platform, exact production baseline, component-class
SHA256, ordered full-classpath content fingerprints, driver SHA256, each process
key/exit and all raw receipt hashes. Binaries are checked before each process and
the full classpaths again at completion. The runner refuses an existing output
directory. Profiles/compiler diagnostics run separately; their timings cannot be
pooled with the uninstrumented matrix. The shared host is unreserved, so these
controls do not establish statistical non-inferiority or portable throughput.

Bootstrap Maven with the work-with-neqsim helper before the Maven commands.
Prepare separate verified baseline/candidate production class directories and
one common unchanged benchmark/test class directory. Do not build or run other
deliberate workloads alongside the serial timing process.

```bash
./mvnw -q '-Dtest=HuronVidal*Test,ComponentGENRTLmodifiedHV*Test' test
./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt -Dmdep.includeScope=test
python3 devtools/benchmark_huron_vidal_rows.py --baseline-classes ../baseline/target/classes --candidate-classes target/classes --test-classes target/test-classes --classpath-file target/test-classpath.txt --baseline-head f1982968aac7dd2bf02340fe13a97635462e624f --output target/hv-row-raw --forks 2
python3 devtools/benchmark_huron_vidal_rows.py --audit target/hv-row-raw
python3 -m unittest discover -s devtools -p test_benchmark_huron_vidal_rows.py -v
```

Per-fork medians are the independent observations; rounds are not additional
replications. `summary.json` preserves absolute time/allocation centers and
candidate/baseline, candidate/control and control/baseline ratios for every cell.
CLASSICAL/HV ratios compare different thermodynamics and solver work and cannot
alone identify evaluator overhead. No timing threshold is a CI assertion, and
no noisy ratio is treated as a general speedup or absolute optimality claim.

The raw archive retains every process, including adverse observations. Native
numerical consistency does not establish a continuous physical operating range,
new light-gas/polar fit, inhibitor partition, hydrate equilibrium, ice, brine or
extrapolation qualification.

## Inspect retained evidence without a timing run

The acceptance test reads raw.zip directly. To use the command-line auditor:

```bash
python3 -m zipfile -e src/test/resources/neqsim/thermo/mixingrule/hv-row-performance/raw.zip target/held-hv-raw
python3 devtools/benchmark_huron_vidal_rows.py --audit target/held-hv-raw
```

For the split held/compiler archives, join the parts in the exact order given
in index.json and verify their individual and combined SHA256/byte counts
before opening the ZIP. The seven auditor tests perform those checks. Original
producer drivers are retained with held sources so changes in the present
auditor do not obscure the actual driver hash used for a timing experiment.
See [final local validation and infrastructure limits](VALIDATION.md).

Publication reconciliation: parent5d0edbdf6f9e75a89f8f7337b4769d8cb24b2694
includes six subsequently merged peer deliveries. All57 changed master blobs
were inspected;56 were retrieved and hash-verified locally, and engineering
coverage was regenerated from their exact sources and independently matched
master blobfaf53fdc. Both new HV workflow steps are combined with all existing
gravity-energy and prior HV gates. No HV runtime source or parameter/alpha input
changed in those merges. Timing receipts remain explicitly on the frozenf198
baseline; no timing requalification on a later head is implied.

Source-inventory reconciliation (2026-11-02): the earlier campaign receipt
[6432934660](https://github.com/equinor/neqsim/issues/4573#issuecomment-6432934660)
already records a successful Epelle thesis download and text inspection. That
inspection found a publication-list citation, not the original2020 coefficient
tables. Later failed reader/transfer attempts are route limitations, not evidence
that the thesis was never inspected. The catalog now preserves that distinction
and the earlier reported PDF fingerprint; no new download, table recovery,
parameter activation or physical qualification is claimed.
