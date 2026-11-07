---
title: "Code health and agentic maintenance"
description: "Measured maintainability checks, reviewed debt baselines, maintenance agents and protected coding-task evaluations."
---

# Code health and agentic maintenance

Use `@code-health` to assess a package, `@refactor` to simplify one responsibility,
and `@code-review` for independent source review. Reuse `@neqsim-test` and
`@documentation`. Task/report review remains with `@review`.
The corresponding skills are `neqsim-code-health`, `neqsim-safe-refactoring` and
`neqsim-code-review`. All tools use the selected Python interpreter; commands below
use the locked repository runner.

## Run the checks

```bash
uv run --project devtools --locked devtools/code_health.py --output target/code-health-source.json
./mvnw -B -ntp -Pcode-health -DskipTests clean compile pmd:pmd
uv run --project devtools --locked devtools/prepare_code_health_input.py
./mvnw -B -ntp -Pcode-health spotbugs:spotbugs
uv run --project devtools --locked devtools/code_health.py --tools source,pmd,spotbugs --pmd target/pmd.xml --spotbugs target/spotbugsXml.xml --output target/code-health.json
```

The opt-in `code-health` Maven profile gives SpotBugs a 2 GiB heap, bounds its
JVM processor count to two, and makes
analyzer execution errors fail Maven; ordinary build defaults are retained.
CI separately caps Maven at 1 GiB so the parent and fork fit a normal hosted runner.
The static job pins Temurin 17.0.21+9. Compile, prepare input, then analyze in that
order. Preparation archives every compiled class in sorted entry order with fixed
ZIP metadata; SpotBugs scans this single nested archive instead of filesystem order.
The manifest records class count and content/archive fingerprints and is retained
with the CI reports. No detector or finding priority is disabled by canonicalization.
The source check requires no Java build or third-party Python package. The full
check requires completed PMD and SpotBugs XML reports. Missing, malformed or failed
analysis is a failed check, including reported missing classes. A successful check
means no **new findings in the selected checks** against the reviewed baseline;
it does not mean the repository has no existing findings.

Source rules reject new console output and JAMA imports in production Java, and
dependencies from `thermo`/`thermodynamicoperations` into `process`, `mcp` or
`util.agentic`. These are lexical import rules, not a complete bytecode architecture
analysis: reflection, fully qualified uses and transitive cycles need separate review.
PMD priorities 1–2 and SpotBugs priorities 1–2 are compared as counted findings.
Analyzer versions are recorded and must match the reviewed baseline.
JDK updates and analysis-input format changes also require an explicit baseline
comparison. SpotBugs 4.10.5 enumerates class directories in filesystem order
([upstream implementation](https://github.com/spotbugs/spotbugs/blob/4.10.5/spotbugs/src/main/java/edu/umd/cs/findbugs/RecursiveFileSearch.java));
its inferred clone-nullness diagnostics can otherwise differ between unchanged
local and hosted builds. Keep the canonical archive preparation in the CI pipeline.
Line numbers are display metadata; normal line shifts do not create new debt.

## Interpret the evidence

| Evidence | Meaning and limit |
|---|---|
| Lines, branch tokens, imports | Triage proxies; branch tokens are not a formal cyclomatic complexity score |
| Duplicate windows | Exact normalized 12-line Java windows across files, including overlaps; inspect examples before calling them duplication |
| Optional `--history 100` | Changes in at most 100 available Git commits; shallow/snapshot history is incomplete |
| Optional `--jacoco target/site/jacoco/jacoco.xml` | Measured coverage counters; omitted coverage is explicitly unmeasured |
| New/resolved findings | Difference from the reviewed multiset, with repeat occurrences counted |
| Existing debt | Recorded findings, not accepted correctness or permission to copy them |

Use change frequency, branch counts, dependency impact, duplication candidates and
regression evidence together. First measured candidates include large process,
distillation and pipeline classes; choose a responsibility only after reading its
callers. Preserve one coherent maintenance PR and coordinate overlap with active
domain campaigns. Do not rank code quality by lines removed, PR count or coverage alone.

## Baseline governance

`.config/code-health-baseline.json` records the source revision and selected tools.
It is generated data: use the CLI summary and retained analyzer reports rather than
loading the entire baseline into an agent context. Normal checks never change it. CI compares against the base branch's copy, so
adding exceptions in a candidate baseline cannot hide a new finding. The first
delivery bootstraps this file and requires its initial contents to be reviewed.

To propose a deliberate baseline change, rerun complete selected analyzers and use
`--write-baseline --revision <tested-commit> --tools source,pmd,spotbugs` with the
report arguments above. Review each added exception, its reason, owner and follow-up.
Remove resolved debt after confirming that the diagnostic actually disappeared.
Never regenerate a baseline merely to make CI green. Analyzer upgrades need a
separate reviewed baseline comparison because rules and diagnostics can change.

Adding a workflow does not make it a required merge check. Administrators should
require **Code health source contracts** and **Code health static analysis** after
the bootstrap PR passes; retain the existing test, formatting and numerical gates.
Fast source checks and focused tests provide early feedback; broader suites still
run according to change risk. Do not install expensive analyzer work in every local
commit hook.

## Preserve engineering and integration contracts

Before refactoring record units, valid ranges, initialization, mutation and clone
ownership, thread safety, serialization, subclass hooks, error behavior and API
compatibility where applicable. Retain facades and migrate callers deliberately.
Follow the [API lifecycle policy](api-lifecycle.md). For proposed JAR-level binary
checks, evaluate japicmp in a separate integration before making it a release gate;
this initial source-maintenance pilot does not add that dependency or claim binary
compatibility qualification.

Use the [numerical model specifications](model_spec_harness.md) for accuracy,
invariants, state refresh and unsupported cases. Use the existing
[paired process benchmark](steady_state_performance_benchmark.md) for hot paths,
including cold, repeated and changed-state runs, convergence, phase topology and
balance checks. Choose a regression budget after measuring noise. Do not use a
noisy wall-clock assertion in ordinary unit tests or change tolerances to conceal
a regression. A recorded output preserves behavior; an independent reference
establishes accuracy.

## Coding-task evaluation

`devtools/code_health_evals/cases.json` starts with one real, pinned maintenance
case: consolidate scalar frontmatter parsing while preserving quote content and
folded descriptions. This is separate from `agent_eval.py` routing/result-schema
evaluation and the Java numerical agent benchmarks.

The evaluator is tool-neutral: it grades an agent-produced candidate; it does not
launch or select an LLM. Use a trusted evaluator checkout and a separate candidate
checkout based on the catalog commit. Derive the changed-path JSON list from the
actual candidate diff using the trusted orchestration, then run:

```bash
uv run --project devtools --locked devtools/coding_eval.py --case frontmatter-scalar-refactor --candidate-root /absolute/candidate --base-root /absolute/pinned-base --base-revision d8c9354ab490564c3c2220b8f735c645cd575c79 --changed-paths /absolute/changed-paths.json --output target/coding-eval.json
```

Acceptance tests stay in the evaluator checkout. Receipts record catalog/test and
candidate-file fingerprints, declared base revision, command, exit status, elapsed
time and output. Base inputs are checked against catalog fingerprints. Run arbitrary
candidate code only in a secret-free disposable job; Python isolation and a reduced
environment are not a security sandbox. Scope information comes from trusted
orchestration, not from a candidate-generated manifest.

Qualification requires a failing original baseline, a passing candidate, and an
independent source review. The CI smoke test protects this grading behavior; it is
not a new LLM evaluation on every PR. Expand with historical numerical bugs, API
additions and representative refactorings after recording their pinned baselines,
acceptance checks and risk-specific performance evidence. One Python case cannot
estimate whole-project agent success rate.

## First pilot

The [recorded pilot evidence](../../devtools/code_health_evals/pilot_20261107.json)
contains the failing original, the fresh-agent candidate, source review and validation
results. Hosted CI remains a separate result from local validation.

The [static-input qualification record](../../devtools/code_health_evals/static_input_20261107.json)
records the initial hosted failure, unchanged class coverage, the canonical-input
baseline migration and the separate local/hosted runtime qualification boundary.

The verifier now delegates scalar parsing to `agent_frontmatter.parse_frontmatter`
through its existing compatibility wrapper. It no longer implements a second
parser. Scalar quote content is preserved and dependency lists remain separate.
This changes developer metadata verification only; no Java equations, solver
thresholds, process execution or public simulation APIs are changed.

Track new/resolved findings, review corrections, focused-check duration, coding-task
pass rate and regression escapes over comparable changes. Establish measured
baselines before setting improvement targets.
