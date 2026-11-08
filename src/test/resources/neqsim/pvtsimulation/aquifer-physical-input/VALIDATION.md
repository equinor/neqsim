# Run71 validation receipt

Base: `bb5c7d45071d5537adce23e2217da28c49776d85`, 8 November 2026.
Production source SHA-256:
`9e3692aa9a3ab5698beba191bee1f9433135d585669c1e1e2812153969e4ca1a`.
The published head/tree and subsequent hosted checks are recorded in
[#4740](https://github.com/equinor/neqsim/issues/4740).

Complete native tables, independent review/replay, failed variants, command/exit
receipts, raw static/coverage reports and a per-file SHA-256 manifest are retained in
[the Run71 evidence archive](https://chatgpt.com/library?file_id=libfile_895a18d400648191870d1095d00caf9d&account_id=personal).
Archive SHA-256: `44967f610e3998ead6ac8f6608460ae2a2b85711fbff420b2d839286bcbc7c50`.
The optional sources here reproduce the qualification without access to that
retained archive. Generated output matrices are intentionally kept outside Git.

## Mathematical and runtime qualification

Inputs are original synthetic physical-parameter controls, not measured PVT or
field data. The oracle evaluates exact binary factors with Python
`Fraction.from_float`; it shares no production scaling or rounding algorithm.
All **340 declared rows pass**: 216 finite positive, 6 physical zeros, 73 invalid
and 45 unrepresentable positive conversions. Master fails 130 rows under this
same contract. Input-identity SHA-256 is
`5a0241d1476d5902fd57b0e4ab313acbbb0b1ff076b8be4e3398bb67cc1a0a33`.
Maximum error/budget fraction is `0.0003601577611807207`, using the frozen
`1e-12*abs(reference) + ulp(reference)` bound. Subnormal outcomes and exact
midpoint classifications additionally require identical binary results.
The matrix grew from 333 to 340 by adding four midpoint and three finite-MAX
controls; no original case or failure was dropped and no bound was loosened.

The first scaled-only implementation misclassified representability. A second,
256-digit decimal quotient also misrounded the exact 1.5-MIN tie. The initial
80-digit JUnit oracle itself misclassified exact half-MIN/overflow ties; analytic
binary expectations replaced only those tie expectations. Both unsuccessful
source variants and their complete failures remain in the archive. The final
algorithm uses exact products and integer half-even rounding in binary ULP units.

An independent automated reviewer reproduced the entire final receipt under
Python `-O`, ran all five integrity-test methods and rejected five additional
positive-subnormal/finite-MAX corruptions. This is automated mathematical/source
review, not human reservoir-domain approval or independent measured accuracy.

Actual Zulu Java 8.0.512-b09 compiled and executed the final production/driver
sources. A final rebuild of formatted sources produced a byte-identical complete
native result table. Four fresh 256 MiB JVMs ran parent/current/current/parent,
three warmups and seven measured samples for each workload:

| JVM | 500,000 conversion pairs, median ms | 20 complete 1,000-interval histories, median ms |
| --- | ---: | ---: |
| Parent A | 1.161432 | 135.985733 |
| Current B | 10.799707 | 127.705398 |
| Current B | 10.754721 | 132.537942 |
| Parent A | 0.848500 | 123.582673 |

Worst current/best-parent complete-history median ratio is **1.0724637911**,
below the pre-measurement **1.20** ceiling. Complete checksums are unchanged:
`8.499281739738533E9` for conversions and `1.1025191541256788E10` for histories.
The standalone conversion slowdown is disclosed; there is no general speedup
claim. Cold first-pair times were 0.665421/0.703847 ms current versus
0.746310/0.664339 ms parent. Shared-host measurements do not establish a cold
benefit. No ordinary timing assertion, EOS path, flash, CT recurrence or
Fetkovich step is added by this change.

## Executed repository gates

The six-class focused selector below spells out the classes in its retained
report; its ordering is immaterial. Every Maven/Spotless shell evaluated the current `prepare_maven.py` helper first.
Analysis/runtime for Maven was the workflow-pinned Temurin 17.0.21+9 with
`MAVEN_OPTS=-Xmx1024m`; optional native checks used actual Java 8 above.
Selected Python was 3.12.14. All following final commands exited **0**:

```sh
./mvnw -B -ntp -Dtest=VanEverdingenHurstAquiferTest,FetkovichAquiferTest test
./mvnw -B -ntp -Dtest=AquiferPhysicalInputTest,CarterTracyScalingTest,FetkovichAquiferTest,RadialInfiniteInfluenceTest,RadialInfiniteInitializationTest,VanEverdingenHurstAquiferTest test
./mvnw -B -ntp '-Dtest=neqsim.pvtsimulation.**,ReservoirSurveillanceTest,GeneralizedReservoirSurveillanceTest,DocExamplesCompilationTest' test
./mvnw -B -ntp -Dtest=AquiferPhysicalInputTest test
python3 devtools/run_spotless.py apply
python3 devtools/run_spotless.py apply
uv run --project devtools --locked devtools/build_engineering_coverage.py
uv tool run --from pre-commit==4.7.0 pre-commit run --all-files --hook-stage pre-commit
python3 devtools/run_spotless.py check
python3 devtools/check_documentation_search.py
uv tool run --from pre-commit==4.7.0 pre-commit run --all-files --hook-stage pre-push
uv run --project devtools --locked devtools/code_health.py --baseline ../code-health-baseline71.json --output target/code-health-final71.json
./mvnw -B -ntp -Pcode-health -DskipTests clean compile pmd:pmd
uv run --project devtools --locked devtools/prepare_code_health_input.py
./mvnw -B -ntp -Pcode-health spotbugs:spotbugs
uv run --project devtools --locked devtools/code_health.py --tools source,pmd,spotbugs --pmd target/pmd.xml --spotbugs target/spotbugsXml.xml --baseline ../code-health-baseline71.json --output target/code-health-static71.json
./mvnw -B -ntp -DskipTests -Dsubpackages=neqsim.pvtsimulation.reservoirproperties.materialbalance javadoc:javadoc
./mvnw -B -ntp -DskipTests checkstyle:check
```

The first test command is unmodified-master evidence: **17 tests** in 46.075 s.
The initial six-method new-test selection on master exited **1**, with three
failures and one error; failed-stage logs are retained. Final focused selection:
**41 tests**. Surrounding selection: **786 tests**, zero failures/errors/skips,
4 min 47 s. After adding the three finite-MAX vectors, all seven new methods
passed again in 31.280 s; production source was identical to the surrounding run.

PMD/SpotBugs ran in an isolated snapshot with identical final production/build
inputs. Combined code-health gate: **zero new finding groups**, retaining selected
existing findings (source 1,028; PMD 9; SpotBugs 11,043). Compile/PMD took 45.067 s
and SpotBugs 3 min. Checkstyle reports zero violations (2 min 25 s). Package
Javadoc succeeds with 11 warnings in adjacent existing classes, none in the
changed class. Direct formatting and both current hooks pass; the stable repeat
does not change Java files. Documentation search passes 842 Markdown pages and
one standalone HTML page. Generated capability coverage was rebuilt, not edited.

JaCoCo shows every line of both converters and all three new helpers executed;
the exact boundary helper covers all 10 branches. Full reports are retained;
this is not a claim of complete repository branch coverage.

Documentation impact: updated input domains, pressure bases, inner-radius and
zero/error contracts in Javadoc, both aquifer guides and field agent/skill.
Existing guide snippets passed `DocExamplesCompilationTest`. No notebook or new
public operation is introduced. Invalid/unrepresentable inputs now throw rather
than leaking nonfinite/negative values or false zero-support scenarios.

Finite-annulus response physics, broader measured laboratory/field holdouts,
aquifer/Bayesian and multi-tank acceptance, and full Tran 2025 source/benchmark
remain open under #4740. This bounded mathematical delivery does not close it.

## Publication-base reconciliation

The final head lease detected master advancing to
`cf4ecf1a59ef9e72904b89b676b9e633d4ab800f` through merged #4928. The stale
write was aborted before branch creation. Its four landing-page guides and
existing documentation test were retrieved through the GitHub plugin; every
blob, complete tree and signed commit hash was verified before local integration.
All production sources, POMs and static-analysis configuration are unchanged.
The exact original aquifer baseline and numerical/static evidence therefore
remain applicable; they are not relabeled as a fresh full-master execution.

On the reconciled base, the following selection passed **90 tests**, zero
failures/errors/skips, in **41.852 s**:

```sh
./mvnw -B -ntp -Dtest=AquiferPhysicalInputTest,VanEverdingenHurstAquiferTest,FetkovichAquiferTest,DocExamplesCompilationTest,LandingPageQuickStartDocumentationTest test
```

Direct apply/stable repeat, generated inventory and source code-health were
repeated successfully. Direct check, documentation search and current hook
stages were repeated before publication. Static production/build inputs still
match the previously analyzed snapshot byte-for-byte.
