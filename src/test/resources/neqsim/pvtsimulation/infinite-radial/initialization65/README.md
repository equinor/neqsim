# Deferred radial-tail initialization — Run65

> Large run outputs and receipts described below are archived. Follow the
> [restore instructions](../README.md#historical-outputs-and-checkout-size) before
> replaying these optional audits. The historical record remains at the
> [pinned original revision](https://github.com/equinor/neqsim/tree/bbb1429b23d8b6210a1e3bb5c7e55a92f8b0b88e/src/test/resources/neqsim/pvtsimulation/infinite-radial/initialization65).


This is the historical Run65 receipt. The current kernel retains its exact
pressure/derivative bits, while [../scaling66/README.md](../scaling66/README.md)
qualifies a scale-safe evaluation of the same CT recurrence. Historical CT
bit-identity and cost statements below refer to Run65, not the later evaluation.

Continues the sole draft #4924 from exact head
`1b5a419b7fc67f3285829e1a7b464d00b4928eaa`, master
`97c03ecf50d16a6dab7bd812364f49d5f4c5c444`. The parent tree
`dfbf50332e1c89abe81dd162d56f181af386b343` was verified against every remote
blob before editing. `SCOPE.txt` freezes the question, concept and criteria
before candidate comparisons. The parent Run63/64 sources, failures and receipts
remain historical evidence at their exact revisions.

## Same spectrum, deferred allocation

The parent eagerly builds all 6,656 positive-tail nodes. The implementation now
builds the first 96 of the original 832 panels (768 nodes). The original global
panel width, node expression, weight expression and sequential accumulation
order are retained. If the prefix cannot certify that all subsequent additions
individually round away, a JVM initialization-on-demand holder constructs the
complete original immutable array once. Evaluation resumes at index 768; the
prefix is never accumulated twice. All times remain supported as before.

Each prefix suffix bound is the maximum of the remaining actual prefix weights
and a conservative bound for every deferred weight. If b is the original
omitted-panel log boundary, each deferred u is at least exp(b). For u>=64,
the unchanged reciprocal-modulus polynomial R(u) is positive and at most one.
The ordinary eight-point Gauss weight is below 0.091 and 2/pi<0.637, so every
deferred pressure weight is below 0.058/u. The deliberately looser exp(-b)
certificate leaves more than a factor of 17 for arithmetic rounding. Native
full-grid evidence checks every weight and suffix: bound
2.268018899555109e-23 versus maximum deferred weight1.0671007868393947e-24,
a factor21.254. It only delays an existing early stop; it cannot stop earlier.
As before, stopping additionally requires the monotonically increasing spectral
argument to be at least80, so all later derivatives are already clipped under
the unchanged Run64 error bounds. This concerns the prescribed sequential
floating sum; it is not a claim that the total deferred exact mass is zero.

## Qualification and retained outputs

`before.csv.gz` and `after.csv.gz` contain every one of the same 51,216 inputs.
Their uncompressed bytes are identical, SHA256
`706acbcd236c496ce00b93379e4271b22796dc9a9b5ab4f4a514ed107a925b41`.
No pressure decrease is allowed, and no output bit changed. All original
14,736 grid/shortcut identities and branch certificates are rechecked using
the immutable Run64 auditor and input vector. Every full-tail node, weight
and suffix maximum is bit-identical to the parent; all 768 prefix nodes and
weights are identical and every prefix bound dominates the original full suffix.

Each of four actual Java8 ABBA `Qualification` runs retains all 20 independent
Laplace pressure/derivative comparisons and all 42 complete CT histories.
The entire numerical payload, including checksum, is exactly unchanged.
The original 25/35-digit inverse transforms are reused, not claimed rerun.
Their original acceptance bounds and the original independent limiting CT ODE
comparisons remain in force. This initialization change adds no new physical
model, measured-data admission, EOS/history fitting or public API.

Three new JUnit methods check isolated initialization order, exact grid identity,
deferred-weight domination, before/after results and eight concurrent first
fallback calls. Actual Java8 `Startup65` additionally runs eight simultaneous
first MIN-time calls in fresh processes. Scaled positive small-time limits
remain unchanged. The isolated loader observes fallback class-loading behavior
on tested JVMs; Java's portable guarantee is initialization on first active use,
not a prohibition on speculative class loading.

The separate auditor rejects a one-ulp response change, NaN time, missing row,
changed prefix frequency, invalid prefix bound, altered deferred weight/suffix,
changed interior CT history/signed zero, missing or mislabeled concurrent
workers, invalid first-query outputs and damaged raw timing samples, substituted output identity and changed container payload hash and conflicting raw/gzip payloads.
`test_audit.py` contains 21 deliberate corruptions plus positive controls. Checks use
explicit exceptions and also run with Python `-O`; no assertion disabling is
used to admit results. Independent review exposed and reproduced initial checker
gaps in mode/first-value validation, infinite prefix bounds, hidden damaged
timing samples and signed-zero identity. These were fixed and covered by the
retained controls; no numerical or runtime acceptance threshold was changed.

## Measured cost and tradeoffs

Actual Zulu8.0.512-b09, same driver and dependency graph, ABBA, three warmups
and seven retained warm samples per JVM. All samples and commands/exits/times
remain in the native JSON files and `runtime-executions.json`. Large qualification
JSON files use lossless gzip containers; both container hashes and original
payload hashes are retained and checked. If a reproduction also leaves a raw
JSON file, it must be byte-identical to its compressed payload; a conflicting
dual representation is rejected. Timings were not regenerated or altered
by containerization.

| Public `Qualification` workload | Parent | Candidate |
|---|---:|---:|
| Cold first query at tD1 |21.244–21.764ms|7.594–8.281ms|
|20,000 separate scalar P/D pairs, median|287.825–295.785ms|305.644–310.153ms|
|1,000 CT intervals, median|7.479–7.556ms|8.284–8.289ms|

Conservative cold speedup2.565x passes the frozen2x target. Warm pair and CT
regressions are at most1.078x/1.109x, passing the frozen<=1.20x limits; these
are real measured costs. Separate direct-kernel startup runs report ordinary
cold20.326–21.100ms versus4.416–4.740ms. The first deferred MIN query after
ordinary startup costs3.581–3.604ms versus the parent's already initialized
1.176–1.379ms. Direct first-MIN costs22.366–26.395ms versus8.384–8.928ms.
Concurrent first-MIN wall time includes coordination/shutdown; it is retained
as a correctness and startup observation, not a pure kernel benchmark. In modes
MIN/concurrent, `deferredTinyNs` is a warm repeat; only mode1.0 measures deferred
initialization. No improvement is asserted for ordinary EOS/flash/PVT/Fetkovich
paths, which acquire no new calls. Residual radial cost versus the inaccurate
master approximation and accountable owner/domain cost acceptance remain open.

## Reproduction and boundaries

Compile the affected production classes and the optional `Grid65`, `Startup65`,
unchanged `../Qualification.java` and `../optimization64/Matrix64.java` in
separate parent/candidate class directories using actual Java8. Obtain parent
source through the GitHub plugin at the exact head above. Run Matrix64 on the
same `../optimization64/common-times.txt`; run Grid65 in parent/candidate mode.
`run_native.py --java <jdk8-java> --before-classes <parent-classes>
--after-classes <candidate-classes> --dependencies <classpath-file>
--output runtime-executions.json` reproduces the ABBA native runs. Then run
selected-Python `audit.py` and `test_audit.py` in this directory. The receipt
`validation.json` records actual local commands, results and generated coverage.
Historical Matrix64 and TailGrid64 remain unchanged; TailGrid64 reflects the
parent's eager field, so use Grid65 for the current deferred representation.

Finite-radius maximum-policy physics remains unqualified, including its initial
right-limit and late-intercept defects. CT refinement converges to its limiting
ODE, not exact pressure-step diffusion; the intrinsic early ratio remains pi/3.
Measured laboratory-to-field, remaining phase paths, broader aquifer/Bayesian,
multi-tank and full Tran2025 equations/benchmark acceptance remain unresolved.
No water mixing, radial/Fetkovich equivalence, continuous posterior or recoverable
reserves claim is made. Whole issue #4740 remains open.
