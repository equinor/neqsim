# Nitrogen/water source-version validation protocol

## Frozen contract

Base: `3204f16e009380d41f143686d71b384b01f2912e` (master, 2026-11-03).
A complete isolated validation snapshot was reconstructed from local caches using
all9892 fresh GitHub manifest blob identities; no files were missing. Other
working copies were read-only caches. The local snapshot commit is not a GitHub
commit and is not used as the remote publication parent.

The bounded capability is a read-only, standard-library source audit with complete
raw observations, row comparison, uncertainty/provenance guards, documentation and
both Python CI jobs. No Java, main resource, selected interaction tuple, pure
property/alpha, default, native test tolerance or historical model/performance
receipt is changed. Both original compiler observation CSVs remain byte-identical.
The primary PDF and artwork are not redistributed.

## Scientific evidence

All35 gas rows match exactly after the stated scale and MPa/kPa conversions.
Liquid52 rows give50 exact T/P joins,9 exact T/P/fraction joins and41 concentration
differences at common states. Two explicitly enumerated state-version mappings
preserve 0.02K differences rather than rounding or nearest-state matching.
Maximum absolute liquid fraction difference over these mappings is5e-7. All87
comparisons retain source and compiler row locators and literal values.

The independent check at274.18K/3006kPa is thesisx=.0005112 versus compilerx=.000511,
compiler-minus-thesis=-.0000002. The gas first-row calculation20.40×10^-4=.002040
is checked separately. The source images were visually inspected at the exact
pages in provenance.json; the table number alone is insufficient identification.
The source CSV SHA256 pins are in both provenance and executable code; rewriting
metadata hashes cannot silently replace an observation. Both Python modes reject
changed units/direction, missing/duplicated/nonfinite rows, ambiguous/reused states,
unrecorded nearby states, reordered source bytes, repaired comparison fields,
source-identity changes, promotion and conflated uncertainty semantics.

The gas/liquid measurements are separate state sets and same-author source
versions, not newly paired tie-lines, overall feeds or an independent holdout.
No author confidence level is inferred for ±2.5% nitrogen-mole calibration.
PrintedAD_exp and compiler-expanded95% absolute mole-fraction uncertainty remain
distinct. Journal/version history and complete uncertainty budgets remain open.

## Executable checks

From the repository root:

- `python3 devtools/audit_huron_vidal_nitrogen_sources.py --summary` (omit the flag
  for every row): complete audit and read-only CLI output verified.
- `python3 -m unittest discover -s devtools -p test_audit_huron_vidal_nitrogen_sources.py -v`
  and the same command with `python3 -O`:10 tests pass in each mode.
- `python3 -m unittest discover -s devtools -p 'test_audit_huron_vidal_*.py' -v`
  and optimized mode:19 adjacent provenance tests pass in each mode.
- `./mvnw -B -ntp '-Dtest=HuronVidal*Test,ComponentGENRTLmodifiedHV*Test,PhaseGENRTLmodifiedHVTest' test`:
  136 tests/17 classes, zero failures/errors/skips on Java17. This includes asserted
  native identities and refined numerical checks, gas/oil/water, MEG/methanol,
  component derivatives, selection/legacy compatibility, history and clone tests.
- Regenerated nitrogen assessment reports match the committed reports byte for
  byte:105 gas and156 liquid model/state rows. This is unchanged diagnostic
  behavior, not experimental accuracy acceptance.

The first separate-POM attempt reused default-POM Java17 main classes and failed
49 class-linkage checks (`org.ejml.simple.ConstMatrix` absent in the Java8-POM EJML
version). This is a mixed-build-output error, not an HV residual or tolerance
failure. A clean rebuild with `-f pomJava8.xml -Dmaven.compiler.release=8 clean test`
uses the same 136-test selection and compiles both main and test sources against
the Java8 API. The clean run passes136 tests/17 classes with zero failures/errors/skips;
representative main and test class major versions are52 (Java8). No
source/POM/test-harness repair is made for that local artifact mix.

The work-with-neqsim Maven helper was evaluated before the first Maven/Spotless
command, and freshly in each subsequent shell. It configured a task-private
Maven/settings/cache and current proxy without repository changes. Local tools
are Python3.12.14 and OpenJDK17.0.20. The Python3.11 floor and native Java8/21
platform runs are hosted-CI gates, not local runtime claims.

Direct Spotless apply/check, documentation-search, generated coverage and both
all-files hook stages pass. Coverage regeneration produces the unchanged
current-master snapshot (`d837796a148cc779580fbaa781ee7f8774c002e7`);
documentation search checks828 Markdown and1 HTML page. `git diff --check` passes. No notebook, Java API or Javadoc is introduced. The guide,
source/range catalog and parent fixture protocol link this executable example.

## Performance and remaining acceptance

This source audit executes outside the EOS and does not change a measured runtime
path. No new JVM timing/allocation experiment or speedup is claimed. The frozen
campaign multi-JVM CLASSICAL/HV init1/init3/full-flash evidence, adverse timing
controls and held optimization proposals remain unchanged; their limitations
still apply. Native consistency and byte-identical reports do not establish
physical accuracy or general throughput.

Source-compatible PR pure inputs/alpha and pure-water pressure reconciliation,
original journal table/uncertainty reconciliation, broader light-gas/water and
methanol/MEG bidirectional partition, independent hydrate dissociation and
ice/brine/extrapolation qualification remain open. No unqualified glycol/methanol
or heavy-fraction tuple is activated. Existing validated opt-ins and ordinary
CLASSICAL kij representation outside selected pairs are preserved. This audit
cannot close #4573/#4576; #4575 retains its earlier merged isolation closure.
