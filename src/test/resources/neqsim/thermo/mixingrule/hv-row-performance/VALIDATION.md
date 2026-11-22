# Validation scope and receipts

Final production source is exactly masterf1982968's evaluator blobccda7017.
The clean rebuilt evaluator class SHA256 is
`0a5cb86a7b0439c120c5fbda9067c993df78f02f491ec873329893c85354e3ad`,
identical to the independently built baseline. No held runtime patch is shipped.
`validation.json` records exact commands, results, limitations and log hashes.
Original unformatted log streams are retained byte-for-byte inside validation.zip.

- Clean Maven focused/adjacent suite:144 tests, zero failures/errors/skips;
  JaCoCo analyzed6381 classes. This includes existing all-five
  ThermodynamicModelTest assertions and checkNumerically, independent finite
  differences, gas/oil/water, methanol/MEG, changed/repeated states, source profiles,
  selected/malformed EOS sets, clone/serialization/concurrency and pure limits.
- Explicit Java8 API compilation of evaluator/new regression:class major52.
  Local runtime is17. Native Java8 and complete repository suites require CI.
- Detached probes:280 records/4200 numeric values; every field and pure request
  count exactly baseline, numeric values hex-bit identical. Portable JUnit
  comparisons retain the fixed1e-12 mixed band; native identities1e-8 and
  checkNumerically1e-6 and independent derivative tolerances are unchanged.
- New auditor:7 tests pass normally and with Python-O. These reconstruct the
  complete216-JVM held matrix and hashes/parts/source-class identities, and
  reject process-roster, control, runtime-identity, receipt and endpoint corruption.
- Adjacent tooling:73 tests and30 subtests pass; one existing profile-archive
  test is explicitly deselected in that available-input run. The complete attempt
  retained in the logs fails only because old profiles.zip blobca536852 was not
  returned by the GitHub connector's oversized binary read. Its remote blob is
  preserved, and its test plus both CI invocations are unchanged. This is not
  a local pass of all13 previous profile/receipt tests.
- Direct stable Spotless apply/check, documentation-search828 Markdown/1 HTML,
  both available all-files hook stages, regenerated/fresh engineering coverage
  and strict private Javadoc for the new test pass.

An initial final-check attempt mistakenly retained the held selector source and
reused its binary after a stale local restore/build state. Those receipts were
superseded, never accepted as baseline evidence. The owned format/build outputs
were cleared, baseline rewritten with a fresh timestamp, source checked after
formatting, and all3633 main/2776 test sources rebuilt before the final checks.
No tolerance or test harness was changed to obtain acceptance.

Independent raw timing and compiler diagnostics are described in README.md and
HELD.md. They qualify rejection of the proposed runtime changes; they establish
neither a speedup nor additional empirical parameter accuracy. Existing staged
profiles, primary measurements/protocols, uncertainty records, CLASSICAL kij,
alpha/critical inputs and physical-accuracy blockers remain unchanged.

## Publication parent reconciliation

Parent5d0edbdf includes six peer merges after the timing baseline. Its57 changed
files were inspected and exact blobs reconstructed; no HV/thermodynamic-operations,
parameter/alpha database or Maven dependency input changed. Current generated
coverage independently matches masterfaf53fdc. The complete native144 suite was
rerun with clean coverage on that parent:zero failures/errors/skips,6381 classes.
Current adjacent tooling, including updated dependency-retry guards, passes77
tests/66 subtests; the same old oversized archive test is explicitly unavailable.
The separate reconciliation.zip retains these new-parent logs and validation.json
records their hashes. Frozen timing data remains onf198; no new-parent timing
claim or accepted runtime optimization is inferred.
