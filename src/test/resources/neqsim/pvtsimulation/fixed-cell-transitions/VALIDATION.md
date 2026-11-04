# Dry fixed-cell qualification receipt — 2026-11-04

Base/default revision: **eb672b7a3d2836393f54326a50d19426d2b7659e**.
The exact publication head and CI/review snapshot are recorded in issue #4740.
This receipt qualifies one bounded dry laboratory protocol increment, not the
complete laboratory-to-field/uncertainty acceptance of that issue.

## Frozen physical and numerical scope

The initial occupied EOS volume defines a constant cell; each lower pressure
removes equilibrium gas only. Oil may appear/disappear between pressure endpoints.
Gas must remain. The existing fixed-cell API retains its unchanged-family contract;
the new named API explicitly opts into dry phase changes and records that choice
in immutable serializable metadata. Existing local beta refinement retains its
1e-12 log-fugacity/relative-volume targets and phase-family/Gibbs/component gates.
No shared flash defaults, EOS parameters, reservoir volumes or source observations
are fitted or changed.

The independently predicted matrix has 27 paths / 243 states at 272.5/275/277.5 K,
methane fractions .94/.95/.96 and three declared schedules. At .001/1/1000 mol
and both checkpoint selections, the Java regression executes **1458 states**,
including **60 oil appearances and 90 disappearances** (counts include scales and
checkpoint repetitions). Every retained/withdrawn separator inventory is checked.

Bounds were frozen before comparison: 1e-8 relative cell volume; 1e-8 mol
absolute phase/retained/withdrawn amounts at every scale; 1e-8 composition;
1e-10*max(1,initial_i) mol component closure; 1e-8 mol separator closure.
The independent implementation reads no NeqSim outputs and fits no parameters.
It uses shared declared SRK constants, so agreement is numerical, not measured
accuracy. No failed state was omitted and no comparison bound was relaxed.

| Quantity | Maximum observed error |
| --- | ---: |
| Phase/retained/withdrawn amount | 1.1289102985756472e-10 mol |
| Phase composition | 7.362443987801726e-13 |
| Occupied volume | 5.795364188543317e-14 relative |

The bounded binary tangent-plane audit minimum is -4.313135182725341e-15,
above its frozen -1e-9 rejection threshold. Six extra two-row experiments approach
the independent lower dew **41.244353348885056 bara** from both sides at
±.1/±.01/±.001 bara. Liquid amount tends to zero from above; below it, oil stays
absent and its properties are not invented. A gas-only pressure-grid refinement
preserves final composition and inventory. Refining two-phase pressure steps
changes the experiment and is compared to its own independent prediction.

## Executed validation

Local runtime: OpenJDK **17.0.20**, Python **3.12.14**. Independent NumPy/SciPy:
**2.3.5 / 1.17.0**. Java 8 API/bytecode targeting is distinct from an actual Java 8
runtime; hosted Java 8/21 CI is reported separately in the campaign ledger.
Maven was bootstrapped with the loaded work-with-neqsim `prepare_maven.py` helper
before Maven/Spotless; no POM/wrapper/settings change is delivered.

- Unmodified-master `./mvnw -q -Dtest=FixedCellDepletionHandoffTest,CvdGasOnlyTailTest test`:
  **exit 0, 11 tests**. The existing late phase-family rejection reproduces the gap.
- Independent generator normal and `python3 -O` runs with `--output` and
  `--limits-output`: **exit 0**; both committed JSON files are byte-identical
  between runs. Predictions preceded candidate comparison.
- Initial focused run: numerical matrix passed, one wet-rejection fixture failed
  because it added a component after creating its mixing rule. The fixture was
  repaired by reinitializing the rule; production tolerances were unchanged.
- `./mvnw -f pomJava8.xml -q clean test -Dtest=<all 79 PVT *Test.java class names>`:
  **exit 0, 667 tests, 77 executed classes, zero failures/errors/skips**.
  Configured `slow,benchmark` tag exclusions remain; two selected classes are
  excluded completely. No full-repository local test claim is made.
- Final production APIs and the new JUnit class were explicitly recompiled using
  `java -m jdk.compiler/com.sun.tools.javac.Main --release 8` against the resolved
  Java 8-profile classpath. **Exit 0; all inspected class major versions are 52**.
  `./mvnw -f pomJava8.xml -q surefire:test -Dtest=<same selection>` then repeated
  the **667 tests successfully on those final compiled APIs**.
- Final `DryFixedCellPhaseTransitionTest`: **6/6 pass**, including literal guide
  extraction, compilation and execution, immutable restart/serialization, both
  checkpoint selections, wet/absent-gas failure and the six boundary approaches.
- Explicit JaCoCo **0.8.15** agent via the Surefire `argLine`, followed by its
  `report` goal: **both exit 0**. All three new public entry/getter methods are
  covered. The shared worker has 47/57 lines and 43/58 branches covered by this
  focused instrumented run; no exhaustive-domain coverage claim is made.
  An earlier `prepare-agent` attempt under the Java 8 POM did not attach an agent;
  only the later explicit-agent report is accepted as coverage evidence.
- `check_handoff.py`: **exit 0**, both distinct synthetic volume bases selected,
  original/tuned input serializations unchanged. Initial reference volumes are
  0.0001680035459741009 and 0.00016694868786045681 m3. This on/off volume-correction
  contrast is a declared integration control, not an experimental tune or fit.
- Locked devtools pytest: `test_engineering_coverage.py`, `test_capability_search.py`,
  `test_check_documentation_search.py`, `test_engineering_coverage_workflow.py`:
  **50 passed**. An initial mistyped test filename ran no tests; the corrected
  command above supplies the evidence.
- Direct `run_spotless.py apply` repeated until stable, direct `check`,
  `check_documentation_search.py`, and installed pre-commit/pre-push all-file
  stages: **exit 0**. Documentation audit covers **833 Markdown + 1 HTML** pages.
  Coverage snapshot regenerated after final Java/agent/skill changes.
- Private Javadoc/doclint via `jdk.javadoc` with `--release 8`: **exit 0**, no
  errors; existing missing-comment warnings are reported (default cap 100).
- Checked-in Checkstyle: **exit 0**, one Java file analyzed, **8 warnings identical
  to the unmodified base**. `util` and test Java files are excluded by repository
  configuration; no file-level Checkstyle pass is claimed for those paths.
- Explicit SpotBugs **4.10.5.0**, scoped to CVD/generator and nested classes with
  `failOnError=true`: **exit 1, 19 findings, 0 analysis errors**. Recompiling the
  exact base sources and analyzing the equivalent base class tree also gives
  **exit 1 with the same 19 finding signatures**; **zero introduced findings**.
  This is not a clean SpotBugs pass. The retained legacy getter/loop/field/QC
  diagnostics are outside this additive protocol change and require owner triage.
  The first unqualified `spotbugs` prefix failed because the Java 8 POM does not
  declare it; the explicit coordinates above resolve that invocation issue.

`validation-results.json` retains quantitative metrics, coverage counters and
normalized static findings. `handoff-results.json` retains the consumer outputs.
The JDK launcher image lacks the `javac` executable; the available `jdk.compiler`
module and `ToolProvider` compile successfully. That environment issue does not
change repository configuration.

## Runtime evidence and limits

`FixedCellRuntimeProbe.java` exercises only the pre-existing unchanged-family
API for methane/decane .6/.4, 350 K, 200/100/50 bara. Exact base source was fetched
through GitHub and separately compiled; candidate and base use the same other
classes, input data and JVM. Every checksum comparison is bit-identical.

Both separate-fork experiments are retained: six alternating baseline/candidate
pairs, eight samples per fork, 128/512 MB heap. With 10 warmups and 10 trials per
sample, geometric mean candidate/base median ratio is **1.0276**, range
**0.5829–2.1376**. Continued within-fork timing decline motivated a predeclared
100-warmup / 50-trial control; its ratio is **1.3429**, range **0.7986–2.8212**.
These fork controls are **inconclusive**, not passed performance guarantees.
No unfavorable observations were discarded and no library parameter was tuned.

A separately frozen diagnostic keeps two complete, verified baseline/candidate
NeqSim worlds in isolated URL classloaders inside one JVM, sharing scheduling/GC
conditions: 128/1024 MB heap, 100 warmups per model, six windows of eight alternating
50-trial samples per model. Candidate/base geometric mean wall-time ratio is
**1.0299**, window range **0.9584–1.0946**; main-thread CPU ratio is **1.0413**,
range **0.9649–1.2164**. Reflection overhead is present equally. Different heap
and same-JVM conditions limit comparison to the fork experiments. This diagnostic
is consistent with a small/no resolved difference in this narrow path, but cannot
establish a general no-regression or speedup claim. The source adds no thermodynamic
solver work to the default route; ordinary legacy CVD/shared flash code is unchanged.

Full raw timings, JVM settings and outputs are in `runtime-short-warmup.json`,
`runtime-long-warmup.json` and `runtime-paired.json`. Drivers preserve all samples;
there is no timing assertion in ordinary CI. More precise performance acceptance
requires an appropriately controlled environment, not a relaxed threshold.

## Documentation impact and remaining acceptance

Updated public/private Javadocs, the indexed depletion guide and literal Java 8
example, PVT index/reference index, PVT agent/EOS-regression handoffs, capability
registry/generated inventory and these reproducible sources/results. There is no
notebook in this delivery. Original/tuned laboratory calibration and whole-experiment
holdouts remain separate from reservoir matching and held-out field observations.

Wet upper-dew inference, wet phase transitions, arbitrary critical states,
multicomponent topology changes during withdrawal, generalized absent-phase
black-oil properties, independent measured laboratory/field accuracy, reservoir
volume/aquifer/Bayesian/multi-tank acceptance and Tran RCVD remain outside this
bounded qualification. Full Tran equations, EOS/relative-permeability/BHP/boundary
inputs and numerical benchmark remain blocked: publisher and SSRN full-text
requests return 403; Elicit API access is denied by the connected account's plan.
No abstract-only method is implemented. Primary NS-1 experimental inconsistencies
remain quarantined as recorded in #4740; no observation or EOS was altered to fit.

Draft publication requires owner and independent maintainer/domain review under
GOVERNANCE.md. CI and independent numerical calculation do not supply that human
approval. This additive opt-in neither replaces an existing public method nor
changes canonical identities/approval semantics; maintainers retain classification
and merge authority. **#4740 remains open.**
