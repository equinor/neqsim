# Source-receipt delivery validation

Base: `67ac70182621d43be89f83a2ddef335606cfcfcc` (2026-11-02).
Python3.12.14, OpenJDK17.0.20; no Java edit or native model execution in this batch.
Python3.11 is the CI floor and remains a hosted check.

## Executed

| Command | Result |
| --- | --- |
| `python3 devtools/audit_huron_vidal_meg_receipts.py` | Complete receipt passes;80 rows/75 distinct/7 temperatures;5 duplicate groups and4 phase disagreements preserved |
| `python3 -m unittest discover -s devtools -p test_audit_huron_vidal_meg_receipts.py -v` | 7 tests pass |
| Same unittest command with `python3 -O` | 7 tests pass; no disabled-assert acceptance |
| `python3 -m unittest discover -s devtools -p test_audit_huron_vidal_pure_co2.py -v` | 2 adjacent auditor tests pass |
| `python3 -m unittest discover -s devtools -p test_summarize_huron_vidal_clone.py -v` | 15 adjacent receipt tests pass |
| Direct `devtools/run_spotless.py apply`, repeated apply, then check | Pass; no Java change |
| `pre-commit run --all-files --hook-stage pre-commit` | Pass |
| `pre-commit run --all-files --hook-stage pre-push` | Pass |
| `python3 devtools/check_documentation_search.py` | Pass:828 Markdown/1 HTML |
| `uv run --project devtools --locked devtools/build_engineering_coverage.py`, then check | Pass; exact base blob5fd214e25d733c128337fffccfaf5457c0f74c37 |

The documented CLI is exercised in a separate optimized interpreter and checked
read-only. Mutation tests reject changed bytes (including a self-rehashed CSV),
compound/property direction, units, nonfinite values, compiler uncertainty,
row loss/order/deduplication, invented phase repair and promotion to an accepted
parameter set. Original catalog sections are deeply unchanged. Removing only
the new workflow paths/steps reproduces the base workflow byte-for-byte.

## Environment and scope

An isolated snapshot copied9846 files only after exact authoritative Git-blob
verification, fetched9 changed text files through GitHub, and regenerated the
one missing coverage file exactly. Fifteen unchanged binary fixture files were
unavailable locally; their base-tree entries are preserved remotely. The full
archive-dependent Python suite and Java/platform suites are not claimed run.
No source from an active foreground checkout was edited or trusted by pathname.

Maven was bootstrapped through the work-with-neqsim helper before Spotless.
The first attempt saved proxy exports across tool invocations; the transient
proxy port had expired and Maven reported connection-refused/prefix resolution
failure. Re-evaluating the helper inside the same shell as Maven fixed it.
Stable direct gates and both exact all-files hook stages then passed; no build
configuration, check or resolver endpoint was weakened.

Production Java, database tuples, pure-component inputs, tolerances and the
previous held performance evidence are unchanged. There is no new flash timing,
physical model prediction or speedup claim. Numerical consistency is not tested
by this receipt auditor. Native ThermodynamicModelTest/finite differences and
matched CLASSICAL/HV performance remain mandatory for a later physical batch.
The source's CPA coefficient is not imported. Final-publisher reconciliation,
reverse inhibitor partition and complete EOS/alpha parameter provenance remain
open. No hydrate, ice/brine or extrapolation acceptance follows.
