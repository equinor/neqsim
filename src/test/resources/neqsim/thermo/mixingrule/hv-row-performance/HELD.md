# Held evaluator experiments

These prototypes are research artifacts, not production implementations or
recommended patches. Numerical consistency and lower pure-function request
counts did not establish performance acceptance. Each candidate was compared
against unmodified master f1982968aac7dd2bf02340fe13a97635462e624f.
No observations from different source/class identities are pooled.

1. Prototype1 added lazy call-local row reuse, including initialization1.
   Ordinary PR pilots cover five/fifteen components and four independent forks.
   Initialization1 allocation increased and timing did not establish non-regression.
2. Prototype2 restricted row reuse to higher-order initialization. Its complete
   216-JVM matrix is retained in raw.zip, including adverse PR2017/Twu results.
   Pure requests decreased but initialization1 and full-flash performance regressed.
3. Prototype3 moved workspace allocation before the reductions. Its separate
   24-JVM pilot still regressed. A trimmed eligibility scan also caused a new
   padded-HV NullPointerException; unmodified master returns finite values.
4. Prototype4 repaired that scan using the nested reduction's exact selector
   semantics and added canonical content comparisons in the primary reduction.
   Forty legacy-selector fingerprints and all native tests passed; its separate
   pilot still showed material initialization1 regressions.
5. Prototype5 removed row reuse and kept only the primary selector content
   shortcut. Its separate24-JVM pilot also confirms material initialization1 and full-flash
   regressions, so it is held. Production retains the original evaluator.

The original new linear-request assertion remains with the held source snapshot;
it must not be represented as a passing assertion on unmodified master. The
production regression instead freezes all existing request counts and values.
Existing finite-difference and ThermodynamicModelTest tolerances are unchanged.

Compiler diagnostics for prototype2 are separate instrumented executions.
Both baseline and candidate reached C2 tier4. Final init1 instruction regions
were 85248 and117888 bytes; flash regions were85248 and118976 bytes. This supports
a code-shape hypothesis, not proof of a particular assembly-level cause.
Inlining changes sampled stack attribution; instrumented timings are excluded
from every uninstrumented timing matrix. CPU-event exports retain complete
recorded sample stacks; compiler XML/PrintCompilation streams retain compilation
and deoptimization evidence. No portable throughput or optimality claim follows.

## Exact fork observations

PR2017/Twu, fifteen components, rule4. Ratios below compare each candidate
to its identical-baseline control in the same fork. Values above1 mean slower.
Other models/sizes/rules and absolute time/allocation centers remain in each
complete raw summary; no cells or adverse forks are removed.

| Proposal | init1 C/K, fork0 | init1 C/K, fork1 | flash C/K, fork0 | flash C/K, fork1 |
|---|---:|---:|---:|---:|
| 2 | 1.406866 | 1.407067 | 1.439915 | 1.428862 |
| 3 | 1.306202 | 1.194371 | 1.327797 | 1.508527 |
| 4 | 1.117865 | 1.102180 | 0.995311 | 1.158447 |
| 5 | 1.194723 | 1.272021 | 1.114129 | 1.189647 |

Original JFR containers also contain115 non-CPU environment/system-property
events in the inspected candidate-init1 recording. They are not distributed.
The archive instead retains the complete CPU-event JSON exports without stack
filtering, original compiler XML/PrintCompilation, measured CSV/full states and
the original diagnostic manifest. The manifest's original JFR hashes identify
the omitted containers; they are not claims that those containers are in the
public archive.

`index.json` records SHA256 and sizes for every archive/part and each held
source/class identity. Reassemble parts in listed order, verify the combined
size/SHA256, then open the ZIP. Each part is below1MB for connector retrieval.
Archived class files identify JDK17 timing binaries and are never loaded by
production or tests. Java sources use Java8 APIs; native8 execution is a separate
CI gate. `raw.zip` is the complete prototype2 matrix; `held.zip` parts retain
other pilots, source/class snapshots, probe values and native logs. `compiler.zip`
parts retain separate instrumented diagnostics. They are not timing replications.

The compiler archive uses ZIP_LZMA for lossless storage of the full exports;
read/extract it with Python3.11+ zipfile. All entries were compared byte-for-byte
with the original DEFLATE archive before repackaging (13425109 to6689646 bytes).
No events, stacks or compiler entries were removed by this recompression.
