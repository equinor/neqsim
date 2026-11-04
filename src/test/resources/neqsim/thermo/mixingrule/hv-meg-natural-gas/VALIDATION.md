# Source-data qualification, 2026-11-03

Exact base: `047cb7e4c59101404bcc3aa8f01761630ca1d87f`.
This batch preserves primary MEG/water/natural-gas measurements and their
interpretation limits. It changes no production Java, interaction parameters,
alpha functions, solver, tolerance, clone state or selected pair.

## Independent source evidence

The 46-page institutional accepted manuscript is 464068 bytes, SHA256
`06fb84d7b24cfc92ce79b2bf74b336c4f465c8932bcdf5370510bcf94f9eb0f6`.
The supplement DOCX is 39890 bytes, SHA256
`3aea40a41b06b5abaaa4e24840e9b784e185c2a46cce8ab0d3ce546bb5e75e3b`.
Actual manuscript images at PDF 9/10/11/12/22/23/24 and LibreOffice-rendered
supplement Tables S1/S2 were inspected. PDFs/artwork are not redistributed.

All 11 experiments, 18 loading-gas components and 220 result slots are retained.
Two absent vapor-MEG entries leave 218 observations: 20 vapor concentrations,
99 liquid concentrations and 99 gas y/x ratios. For example, experiment 1
yMEG 0.69 ppm = 0.00000069 mole fraction; xCO2 1375 ppm = 0.001375;
KCO2 = 16.1 remains dimensionless. The audit tests these resolvable conversions.
Loading-gas sum 100.00022 mol% is preserved without normalization. All 11 loading
temperatures and five pressures differ from their nominal result-table labels.
The results are means of duplicate experiments, with no supplied raw replicates;
x and K from the same states are correlated, not independent holdout samples.

The README/provenance preserve missing observations, three rounded-zero RSD
cells, one-sigma repeatability, separate apparatus uncertainties, footnote b,
purity/species conflicts, a printed density-unit inconsistency, the duplicated
gas alias and lumped gas totals. There is no independently measured molar feed
or basis for assigning all MEG impurity to water. Final-publication and full
uncertainty reconciliation remain unresolved.

## Executed local checks for initial publication

* `uv sync --project devtools --locked --group test`: passed; Python 3.12.14.
* `python -m unittest discover -s devtools -p 'test_audit_huron_vidal*.py' -v`
  through locked uv, normal and `-O`: 32 tests each, no failures/errors; includes
  all 13 new tests and the documented read-only CLI.
* `python -m unittest discover -s devtools -p 'test_benchmark_huron_vidal*.py' -v`
  through locked uv: 7 existing performance-receipt guard tests passed. This
  audits retained evidence; it is not a new timing experiment.
* After `prepare_maven.py` bootstrap, `./mvnw -B -f pomJava8.xml
  -Dmaven.compiler.release=8
  -Dtest='HuronVidal*Test,ComponentGENRTLmodifiedHV*Test,PhaseGENRTLmodifiedHVTest,PhaseGEUniquacmodifiedHVTest'
  clean test`: 137 tests/18 classes, no failures/errors/skips, BUILD SUCCESS.
  JDK 17.0.20 compiled Java 8 class-file version 52. This is not a local Java 8 JVM
  execution or a new complete repository suite.
* `python3 devtools/run_spotless.py apply` twice, then `check`, after bootstrap:
  passed and stable; no Java modifications.
* Locked `build_engineering_coverage.py`: passed; generated snapshot unchanged.
* Locked `check_documentation_search.py`: 831 Markdown pages and one standalone
  HTML page passed. Workflow YAML parsing and one new audit step in each Python
  job were checked. `git diff --check` passed.

An unsupported formatter `--help` preflight attempted prefix resolution before
the Maven helper and failed. The listed helper-backed formatter gates were then
run successfully; no source/environment workaround was committed.

Both exact all-files pre-commit and pre-push stages passed with pre-commit 4.7.0,
including formatting and coverage regeneration. The final direct format and
documentation checks also passed; command receipts are retained in the campaign.
Python 3.11 and real Java 8/21 platform CI remain exact-head remote gates.

## Scope and remaining acceptance

The preserved native regression suite asserts ThermodynamicModelTest's five
identities and checkNumerically where applicable, independent derivatives,
initialization history, clone behavior and gas/oil/water closure for its existing
fixtures. These passes do not qualify an HV prediction for this new source data.
No new parameters were activated, so no new native mixture or throughput
acceptance is claimed. Existing held multi-JVM performance evidence is unchanged;
no speedup or optimality claim follows from this source-only batch.

Next physical work requires source-compatible EOS-specific directed HV tuples,
paired pure alpha/critical inputs, reconciled measurement/feed/uncertainty
conventions and both-direction partition data. It must pass native/independent
derivative, strict conservation/history and matched CLASSICAL/HV initialization
and full-flash performance gates. Hydrate dissociation, ice, brine, oil partition
and extrapolation remain outside this qualification.

Documentation impact: the mixing-rule guide and source catalog now distinguish
these receipts and blockers from calibrated/validated HV profiles. No public API,
default or Javadoc behavior changes; the new CLI example is test-executed.

## Interpretation-guard repair

Parent head: `018cd374f7a45fd50b3aeb620ecdcd5287390bf3`; master remains the exact
base above. A new regression reproduced eight accepted metadata contradictions
on that head, without changing any numeric file or the no-activation flag:
zero repeatability claimed exact measurements; footnote b discarded water data;
loading volumes claimed measured moles; prerequisites claimed complete; apparatus
uncertainty claimed 95% coverage; the printed density unit was silently corrected;
and both five-entry limitation/prerequisite lists were replaced with empty strings.

The audit now compares complete uncertainty, feed-limit, prerequisite and apparatus
statements against the frozen primary-source interpretation. List length and a few
numeric fields are insufficient. This repairs source-evidence validation; it does
not change or repair the experimental record, its uncertainty, a thermodynamic
test harness, a physical model or an EOS parameter.

The regression fails eight subcases on the parent. After repair the complete
source-audit suite passes 33 tests in both normal and optimized Python, including
14 natural-gas tests and all eight new contradiction cases. The documented CLI
still runs read-only. Raw CSVs, provenance, catalog, workflow, production Java and
all native-model fixtures are byte-identical to the parent. The 137-test native
result above remains parent evidence, not a new native execution or physical
qualification for these measurements. Publication formatting/hooks/docs/coverage
are rerun on the final repaired files; exact-head remote CI remains required.
