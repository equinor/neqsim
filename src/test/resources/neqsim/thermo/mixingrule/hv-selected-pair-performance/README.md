# Selected CO2/water pair: multicomponent cost qualification

This synthetic benchmark characterizes the existing model on master
`ab66ca997b22cf965ee7d6d9f63a8fc7b8559d92`; it introduces no production optimization,
parameter, fit, alpha model, database change or default retuning. Only the
explicitly selected CO2/water pair uses HV. Every other pair retains ordinary
CLASSICAL kij, including all methanol/MEG pairs. These are numerical/performance
fixtures, not measured feeds or inhibitor partition validation.
These salt-free fluid calculations do not qualify ice, brine, hydrate equilibrium
or any continuous temperature/pressure/composition operating domain.

## Model and frozen protocol

Profiles0/1/2 reuse the [staged CO2 source configurations](../hv-co2-source/README.md):
PR2016 with library pure inputs, SRK2017 with declared paired Twu inputs, and
PR2017 with declared paired Twu/exact-critical cubic inputs. The directed
coefficients, kelvin units, infinite-pressure covolume-weighted convention,
source ranges, provenance and unresolved author-input/physical-accuracy limits
remain there. No additional source tuple is selected. The harness verifies exact
pure critical/cubic/alpha and ordinary-kij fingerprints across rule selection.

Both feeds sum to one mole. Temperature is353.15K, absolute pressure50bara,
multiphase stability checking enabled. The flash workload alternates353.15 and
353.35K on the same independently configured worker. Five components give
gas/aqueous equilibrium; fifteen give gas/oil/aqueous equilibrium in the executed
matrix. Topology and complete beta/x/logphi vectors are retained for every round.

| Component | Five-component mole amount | Fifteen-component mole amount |
|---|---:|---:|
|water|0.30|0.30|
|CO2|0.20|0.10|
|methane|0.40|0.30|
|methanol|0.05|0.03|
|MEG|0.05|0.02|
|ethane|0|0.025|
|propane|0|0.025|
|i-butane|0|0.025|
|n-butane|0|0.025|
|i-pentane|0|0.025|
|n-pentane|0|0.025|
|n-hexane|0|0.025|
|n-heptane|0|0.025|
|n-octane|0|0.025|
|nitrogen|0|0.025|

Three profiles × two sizes × init1/init3/complete flash × rules2/4 × four forks
produce144 serial JVMs. Each JVM contains one EOS/profile, size, rule and scenario
to avoid cross-scenario HotSpot type-profile deoptimization. Rule order alternates
2/4 then4/2; case order reverses on odd forks. JVM options are512MiB fixed heap,
`-Xbatch` and two compiler threads. Warmup is20000 initialization calls or1000
complete flashes. Ten measured rounds contain2000 initializations or50 complete
flashes each. Initialization runs operate on a previously flashed equilibrium
state. Flash timings include the subsequent init3, matching the binary protocol.
Construction, native derivative checks, CSV writing and endpoint validation are
outside the timed interval. Thread allocation counters are read outside that
interval; reflection/counter overhead remains in absolute allocation totals.
An unsupported platform records-1 rather than inventing an allocation value.

The timing ratio compares two thermodynamic models, with different phase
compositions and potentially different solver work. There is no public complete
iteration counter here. Matched call counts, full states and equilibrium residuals
are reported; a ratio is not isolated evaluator overhead, an optimization speedup,
equivalent empirical accuracy or universal throughput qualification. Preserve
adverse observations and use forks, not rounds, as independent replications.

## Numerical acceptance and executable reproduction

`HuronVidalSelectedPairPerformanceTest` asserts all five existing
ThermodynamicModelTest identities at1e-8 and detached `checkNumerically()` at1e-6,
with byte-identical serialization before/after. It tests fresh/repeated/changed/
returned states and reversed component order for both sizes/profiles/rules.
Deactivating only CO2/water must recover full CLASSICAL equilibrium. These are
numerical gates inherited from the staged source diagnostic, not empirical error
tolerances. Every timed endpoint separately requires component balance1e-10,
beta/phase normalization1e-12 and interphase log-fugacity equality1e-8. The Python
auditor independently reconstructs these from all retained phase vectors and
rejects missing/duplicate cells, rounds, phases or components and nonfinite data.

Bootstrap Maven with the work-with-neqsim helper before these commands. Run the
benchmark serially without builds or other deliberate workloads. The declared
source SHA identifies the production baseline; manifest source/class/classpath
SHA256 pins identify the actual harness and binaries. Compiler/JFR diagnostics
must run separately and their timings must not enter this matrix.

```bash
./mvnw -q '-Dtest=HuronVidal*Test,ComponentGENRTLmodifiedHV*Test' test
./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt -Dmdep.includeScope=test
python3 devtools/run_huron_vidal_selected_pair_benchmark.py --classpath-file target/test-classpath.txt --source-head ab66ca997b22cf965ee7d6d9f63a8fc7b8559d92 --output target/hv-selected-pair-raw
python3 devtools/audit_huron_vidal_selected_pair.py target/hv-selected-pair-raw --archive target/hv-selected-pair-raw.zip --summary target/hv-selected-pair-summary.json
python3 devtools/audit_huron_vidal_selected_pair.py src/test/resources/neqsim/thermo/mixingrule/hv-selected-pair-performance/raw.zip --summary target/hv-selected-pair-reproduced.json
python3 -m unittest discover -s devtools -p test_run_huron_vidal_selected_pair_benchmark.py -v
```

The raw ZIP preserves every JVM receipt, process log, measured round and complete phase vector
with SHA256 pins in its execution manifest. Fixed ZIP metadata makes repacking
deterministic. The auditor's archive round-trip and corruption controls execute
without Maven or a timing-dependent CI assertion. Exit/failure receipts remain
in the archive. Logs and diagnostic
profiles do not replace the numerical acceptance gates.

## Executed baseline results

The complete archive passes both directory and ZIP audits:144JVMs,1440rounds,
1944000measured operations and39600phase/component rows. No process failed; all
144 retained process logs are empty. Maximum balance/normalization/log-fugacity
errors are4.1633e-15/3.3307e-16/8.7424e-12. Every same-case beta/z/x/logphi
vector is bit-identical across independent JVMs. This establishes the recorded
numerical fixture and receipt reproducibility, not empirical partition accuracy.

Execution used OpenJDK17.0.20+8 on Linux, a shared unreserved host with9logical
CPUs reported. No build, test or other simulation workload was deliberately run
alongside the timing matrix; lightweight source/metadata inspection continued.
These controls do not provide exclusive hardware or statistical non-inferiority.

|Profile|Components|Operation|HV/CLASSICAL median [fork range]|CLASSICAL bytes/call|HV bytes/call|
|---|---:|---|---:|---:|---:|
|0|5|init1|9.186 [7.964, 9.394]|304.02|0.02|
|0|5|init3|13.253 [12.879, 13.573]|416.02|5440.02|
|0|5|flash|3.805 [3.714, 3.882]|583048.96|682400.96|
|0|15|init1|51.149 [48.973, 55.496]|1056.02|0.02|
|0|15|init3|54.508 [52.620, 59.520]|1464.02|175680.02|
|0|15|flash|26.181 [23.805, 28.035]|1588168.96|2673184.96|
|1|5|init1|24.825 [22.818, 25.449]|304.02|0.02|
|1|5|init3|25.343 [24.461, 26.231]|416.02|5440.02|
|1|5|flash|10.388 [7.960, 11.884]|585064.96|682440.96|
|1|15|init1|94.653 [90.515, 99.129]|1056.02|0.02|
|1|15|init3|102.010 [95.895, 106.845]|1464.02|175680.02|
|1|15|flash|51.177 [46.974, 56.857]|1587384.96|2672752.96|
|2|5|init1|22.676 [21.940, 24.495]|304.02|0.02|
|2|5|init3|25.196 [24.492, 26.613]|416.02|5440.02|
|2|5|flash|10.650 [10.338, 11.323]|582984.96|682640.96|
|2|15|init1|81.830 [75.036, 84.864]|1056.02|0.02|
|2|15|init3|83.442 [79.953, 90.570]|1464.02|175680.02|
|2|15|flash|43.548 [41.666, 45.736]|1588456.96|2673472.96|

Same-rule identical-binary later/earlier ratios range0.880–1.216.
The full per-fork absolute elapsed and allocation centers, ratio ranges and endpoint
compositions are in `summary.json`; no round or adverse observation is discarded.
Do not interpret these model-cost ratios as code-change speedups or equivalent
physical-accuracy comparisons. General throughput qualification remains open.

Raw ZIP SHA256:`b53a4cb2af6b96223028508fdf2cacf858d0b25abe1e63d93fe1212f45cbb9b0`.

## Separate hotspot diagnostics

`profiles.zip` retains both complete JFR recordings, full PrintCompilation logs,
unmodified CPU-event JSON exports and native CSV/phase vectors for PR2017/HV with
15 components, init3 and complete flash. `profiles.json` pins every byte stream,
JVM options, source baseline and case configuration, and records all gamma/Twu
compilation lines. Reproduction uses the same benchmark entry point/arguments,
the recorded JFR flags and the JDK JFR JSON export command in that metadata.
The Python receipt suite rechecks all hashes, independently reconstructs the
instrumented endpoints and recomputes the inclusive/self method counts from the
complete CPU-event exports without requiring JFR tooling in CI.

These separate timings never enter the144-JVM matrix. Profiles include startup,
warmup, measured rounds and untimed diagnostics. Execution and native sampling
event types are counted separately; inclusive counts count each method once per
stack and are not wall-time percentages or statement-level attribution.
`getGamma` appears in4262/4366 init3 and3579/3705 flash execution-sample stacks.
Both logs show early gamma recompilation and subsequent tier4 compilation; the
complete logs are retained rather than suppressing the early events. The largest
self counts are in pure alpha/derivative evaluation and string trimming reached
from the modified-NRTL evaluator. No exact isolated cost is assigned to one line.

Source inspection identifies row-side pure terms repeatedly evaluated across
columns; the prior accepted column-side reuse is already present. Further reuse
needs its own matched baseline/candidate and identical-binary controls, allocation
trade-off, lazy HV-only behavior, fixed-T tuned-alpha/history and clone/isolation
proof. This is a new validation boundary, not permission to restore the held
unused-temperature-derivative candidate. The current batch changes no evaluator
or retained cache, and does not clear general HV throughput acceptance.
